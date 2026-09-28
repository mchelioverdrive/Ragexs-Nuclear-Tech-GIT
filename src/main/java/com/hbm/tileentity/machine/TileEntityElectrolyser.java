package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerElectrolyserFluid;
import com.hbm.inventory.container.ContainerElectrolyserMetal;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.gui.GUIElectrolyserFluid;
import com.hbm.inventory.gui.GUIElectrolyserMetal;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes.ElectrolysisRecipe;
import com.hbm.inventory.recipes.ElectrolyserMetalRecipes;
import com.hbm.inventory.recipes.ElectrolyserMetalRecipes.ElectrolysisMetalRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.tileentity.*;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.util.BobMathUtil;
import com.hbm.util.CrucibleUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityElectrolyser extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IControlReceiver, IGUIProvider, IUpgradeInfoProvider, IFluidCopiable, IMetalCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta;
	public static final long maxPower = 20000000;
	public static final int usageOreBase = 10_000;
	public static final int usageFluidBase = 10_000;
	public int usageOre;
	public int usageFluid;

	public int progressFluid;
	public int processFluidTime = 100;
	public int progressOre;
	public int processOreTime = 600;

	public MaterialStack leftStack;
	public MaterialStack rightStack;
	public int maxMaterial = MaterialShapes.BLOCK.q(16);

	public FluidTank[] tanks;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private ElectrolysisRecipe cachedFluidRecipe;
	private ElectrolysisMetalRecipe cachedMetalRecipe;
	private FluidType cachedFluidInput;
	private ItemStack cachedMetalInput;
	private int cachedMetalInputMeta;
	private int cachedMetalInputTag;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long operatingPowerFluidWatts;
	private long operatingPowerMetalWatts;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_CAST = 3;
	private static final int TASK_SLOT_SHARED = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_CAST = 2;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long batteryTransitionDueTick = Long.MIN_VALUE;
	private boolean runtimeAccountingMutation;
	private boolean runtimeFluidActive;
	private boolean runtimeMetalActive;

	public TileEntityElectrolyser() {
		//0: Battery
		//1-2: Upgrades
		//// FLUID
		//3-4: Fluid ID
		//5-10: Fluid IO
		//11-13: Byproducts
		//// METAL
		//14: Crystal
		//15-20: Outputs
		super(21);
		tanks = new FluidTank[4];
		tanks[0] = new FluidTank(Fluids.FRESH_WATER, 16000).migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.HYDROGEN, 16000);
		tanks[2] = new FluidTank(Fluids.OXYGEN, 16000);
		tanks[3] = new FluidTank(Fluids.NITRIC_ACID, 16000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		if(i == 14) return ElectrolyserMetalRecipes.getRecipe(itemStack) != null;
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i != 14;
	}

	@Override
	public String getName() {
		return "container.machineElectrolyser";
	}

	@Override
	public void updateEntity() {
		// Production, fluid logistics, and casting are driven by MachineRuntime.
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		batteryTransitionDueTick = Long.MIN_VALUE;
		runtimeFluidActive = false;
		runtimeMetalActive = false;
		super.onChunkUnload();
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = runtimeFluidActive || runtimeMetalActive;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		this.refreshCachedState();
		this.observeInventoryFingerprint();
		runtimeInitialized = true;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) this.updateConnections();
		if(wasActive && !this.hasBatteryWork()) this.settleProgressThrough(now);
		else if(!wasActive) lastAccountingTick = now;
		this.evaluateAndSchedule(now, wasActive && this.hasBatteryWork());
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			batteryTransitionDueTick = Long.MIN_VALUE;
			this.settleProgressThrough(now - 1L);
			if(this.observeInventoryFingerprint()) {
				this.upgradeManager.invalidate();
				this.refreshCachedState();
			}
			long beforeEnergy = energyQuanta;
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower)); }
			finally { runtimeEnergyMutation = false; }
			if(beforeEnergy != energyQuanta) { this.markDirty(); this.markNetworkDirty(); }
			this.settleProgressThrough(now);
			this.evaluateAndSchedule(now, false);
			this.networkPackNTIfDirty(50);
			return;
		}
		if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_SHARED) {
			if(this.observeInventoryFingerprint()) {
				this.settleProgressThrough(now - 1L);
				this.upgradeManager.invalidate();
				this.refreshCachedState();
			}
			this.settleProgressThrough(now);
			this.evaluateAndSchedule(now, false);
			this.networkPackNTIfDirty(50);
			return;
		}
		if(taskType == TASK_CAST && taskSlot == TASK_SLOT_CAST) {
			if(batteryTransitionDueTick <= now) {
				this.scheduleMachineTransition(now, TASK_CAST, TASK_SLOT_CAST);
				return;
			}
			if(this.observeInventoryFingerprint()) {
				this.settleProgressThrough(now - 1L);
				this.upgradeManager.invalidate();
				this.refreshCachedState();
				if((runtimeFluidActive || runtimeMetalActive) && !this.hasBatteryWork()) this.settleProgressThrough(now);
			}
			this.settleProgressThrough(now);
			int beforeLeftAmount = leftStack == null ? 0 : leftStack.amount;
			int beforeRightAmount = rightStack == null ? 0 : rightStack.amount;
			boolean poured = this.pourStack(true) | this.pourStack(false);
			if(poured || beforeLeftAmount != (leftStack == null ? 0 : leftStack.amount) || beforeRightAmount != (rightStack == null ? 0 : rightStack.amount)) {
				this.markDirty();
				this.markNetworkDirty();
			}
			this.evaluateAndSchedule(now, false);
			this.networkPackNTIfDirty(50);
		}
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			long now = worldObj.getTotalWorldTime();
			boolean inventoryChanged = this.observeInventoryFingerprint();
			boolean wasActive = runtimeFluidActive || runtimeMetalActive;
			if(inventoryChanged) this.settleProgressThrough(now - 1L);
			else this.settleProgressThrough(now);
			if(inventoryChanged) {
				this.upgradeManager.invalidate();
				this.refreshCachedState();
				if(wasActive && !this.hasBatteryWork()) this.settleProgressThrough(now);
			}
			this.beginMachineFluidMutation();
			boolean changed;
			try {
			changed = tanks[0].setType(3, 4, slots);
				changed |= tanks[0].loadTank(5, 6, slots);
				changed |= tanks[1].unloadTank(7, 8, slots);
				changed |= tanks[2].unloadTank(9, 10, slots);
			} finally { this.endMachineFluidMutation(); }
			changed |= this.observeInventoryFingerprint();
			if(changed) {
				this.refreshCachedState();
				this.markDirty();
				this.markNetworkDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			}
			return;
		}
		if(cadence != 20) return;
		this.settleProgressThrough(worldObj.getTotalWorldTime());
		for(DirPos pos : this.getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[3].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tanks[1].getFill() > 0) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(tanks[2].getFill() > 0) this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
		this.networkPackNTIfDirty(50);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime(), false);
	}

	private void updateConnections() {
		for(DirPos pos : this.getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[3].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private void refreshCachedState() {
		FluidType fluidInput = tanks[0].getTankType();
		if(cachedFluidInput != fluidInput) {
			cachedFluidInput = fluidInput;
			cachedFluidRecipe = ElectrolyserFluidRecipes.getRecipe(fluidInput);
		}
		ItemStack metalInput = slots[14];
		int meta = metalInput == null ? 0 : metalInput.getItemDamage();
		int tagHash = metalInput == null || metalInput.getTagCompound() == null ? 0 : metalInput.getTagCompound().hashCode();
		if(cachedMetalInput != metalInput || cachedMetalInputMeta != meta || cachedMetalInputTag != tagHash) {
			cachedMetalInput = metalInput;
			cachedMetalInputMeta = meta;
			cachedMetalInputTag = tagHash;
			cachedMetalRecipe = ElectrolyserMetalRecipes.getRecipe(metalInput);
		}
		this.upgradeManager.checkSlotsIfDirty(slots, 1, 2);
		int speedLevel = Math.min(upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(upgradeManager.getLevel(UpgradeType.POWER), 3);
		usageOre = usageOreBase - usageOreBase * powerLevel / 4 + usageOreBase * speedLevel;
		usageFluid = usageFluidBase - usageFluidBase * powerLevel / 4 + usageFluidBase * speedLevel;
		operatingPowerFluidWatts = EnergyUnits.quantaPerTickToWatts(usageFluid);
		operatingPowerMetalWatts = EnergyUnits.quantaPerTickToWatts(usageOre);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof api.hbm.energymk2.IBatteryItem)) return false;
		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) {
				hash = 31 * hash + stack.stackSize;
				hash = 31 * hash + stack.getItemDamage();
				if(i != 0 || !(stack.getItem() instanceof api.hbm.energymk2.IBatteryItem)) hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
		}
		return hash;
	}

	private boolean observeInventoryFingerprint() {
		int current = this.inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private boolean pourStack(boolean left) {
		MaterialStack stack = left ? leftStack : rightStack;
		if(stack == null) return false;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		if(left) dir = dir.getOpposite();
		List<MaterialStack> toCast = new ArrayList();
		toCast.add(stack);
		Vec3 impact = Vec3.createVectorHelper(0, 0, 0);
		MaterialStack didPour = CrucibleUtil.pourFullStack(worldObj, xCoord + 0.5D + dir.offsetX * 5.875D, yCoord + 2D, zCoord + 0.5D + dir.offsetZ * 5.875D, 6, true, toCast, MaterialShapes.NUGGET.q(3) * Math.max(getCycleCount() * Math.min(upgradeManager.getLevel(UpgradeType.SPEED), 3), 1), impact);
		if(didPour == null) return false;
		NBTTagCompound data = new NBTTagCompound();
		data.setString("type", "foundry");
		data.setInteger("color", didPour.material.moltenColor);
		data.setByte("dir", (byte) dir.ordinal());
		data.setFloat("off", 0.625F);
		data.setFloat("base", 0.625F);
		data.setFloat("len", Math.max(1F, yCoord - (float) (Math.ceil(impact.yCoord) - 0.875) + 2));
		PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(data, xCoord + 0.5D + dir.offsetX * 5.875D, yCoord + 2, zCoord + 0.5D + dir.offsetZ * 5.875D), new TargetPoint(worldObj.provider.dimensionId, xCoord + 0.5, yCoord + 1, zCoord + 0.5, 50));
		if(stack.amount <= 0) {
			if(left) leftStack = null;
			else rightStack = null;
		}
		return true;
	}

	private void evaluateAndSchedule(long now, boolean batteryDueNow) {
		if(!runtimeInitialized) return;
		boolean battery = this.hasBatteryWork();
		if(battery) {
			long due = now + (batteryDueNow ? 0L : 1L);
			this.scheduleMachineTransition(due, TASK_BATTERY, TASK_SLOT_BATTERY);
			if(batteryTransitionDueTick == Long.MIN_VALUE || due < batteryTransitionDueTick) batteryTransitionDueTick = due;
		} else {
			this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
			batteryTransitionDueTick = Long.MIN_VALUE;
		}
		boolean fluid = this.canProcessFluid();
		boolean metal = this.canProcessMetal();
		runtimeFluidActive = fluid;
		runtimeMetalActive = metal;
		if(!battery && (fluid || metal)) {
			long cycleCount = Math.max(1, this.getCycleCount());
			long ticks = Long.MAX_VALUE;
			if(fluid) ticks = Math.min(ticks, ceilDiv((long) this.getDurationFluid() - progressFluid, cycleCount));
			if(metal) ticks = Math.min(ticks, ceilDiv((long) this.getDurationMetal() - progressOre, cycleCount));
			long costFluid = EnergyUnits.wattsToQuantaPerTick(operatingPowerFluidWatts);
			long costMetal = EnergyUnits.wattsToQuantaPerTick(operatingPowerMetalWatts);
			long costPerOperation = fluid ? costFluid : costMetal;
			long operationsPerTick = cycleCount * ((fluid ? 1L : 0L) + (metal ? 1L : 0L));
			if(costPerOperation > 0L && operationsPerTick > 0L) ticks = Math.min(ticks, ceilDiv(energyQuanta / costPerOperation, operationsPerTick));
			if(ticks != Long.MAX_VALUE) this.scheduleMachineTransition(now + Math.max(1L, ticks), TASK_ACCOUNTING, TASK_SLOT_SHARED);
		} else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_SHARED);
		if(leftStack != null || rightStack != null) this.scheduleMachineTransition(now + 1L, TASK_CAST, TASK_SLOT_CAST);
		else this.cancelMachineTransition(TASK_CAST, TASK_SLOT_CAST);
	}

	private static long ceilDiv(long value, long divisor) {
		return value <= 0L ? 1L : 1L + (value - 1L) / divisor;
	}

	private void settleProgressThrough(long targetTick) {
		if(worldObj == null || worldObj.isRemote || runtimeAccountingMutation || lastAccountingTick == Long.MIN_VALUE || targetTick <= lastAccountingTick) return;
		long remaining = targetTick - lastAccountingTick;
		long beforeEnergy = energyQuanta;
		int beforeFluid = progressFluid;
		int beforeMetal = progressOre;
		int beforeLeft = leftStack == null ? 0 : leftStack.amount;
		int beforeRight = rightStack == null ? 0 : rightStack.amount;
		runtimeAccountingMutation = true;
		this.beginMachineFluidMutation();
		try {
			while(remaining > 0L) {
				boolean fluid = this.canProcessFluid();
				boolean metal = this.canProcessMetal();
				if(!fluid && !metal) break;
				int cycles = Math.max(1, this.getCycleCount());
				long costFluid = EnergyUnits.wattsToQuantaPerTick(operatingPowerFluidWatts);
				long costMetal = EnergyUnits.wattsToQuantaPerTick(operatingPowerMetalWatts);
				long operationsPerTick = cycles * ((fluid ? 1L : 0L) + (metal ? 1L : 0L));
				long costPerOperation = fluid ? costFluid : costMetal;
				long completionTick = Long.MAX_VALUE;
				if(fluid) completionTick = Math.min(completionTick, ceilDiv((long) this.getDurationFluid() - progressFluid, cycles));
				if(metal) completionTick = Math.min(completionTick, ceilDiv((long) this.getDurationMetal() - progressOre, cycles));
				long energyTick = costPerOperation <= 0L || operationsPerTick <= 0L ? Long.MAX_VALUE : ceilDiv(energyQuanta / costPerOperation, operationsPerTick);
				long boundaryTick = Math.min(completionTick, energyTick);
				long fullTicks = Math.min(remaining, boundaryTick == Long.MAX_VALUE ? remaining : boundaryTick - 1L);
				if(fullTicks > 0L) {
					if(fluid) progressFluid += (int) Math.min(Integer.MAX_VALUE, fullTicks * cycles);
					if(metal) progressOre += (int) Math.min(Integer.MAX_VALUE, fullTicks * cycles);
					long cost = fullTicks * cycles * ((fluid ? costFluid : 0L) + (metal ? costMetal : 0L));
					this.setStoredEnergyQuanta(energyQuanta - cost);
					remaining -= fullTicks;
				}
				if(remaining <= 0L) break;
				for(int i = 0; i < cycles; i++) {
					if(this.canProcessFluid()) {
						progressFluid++;
						this.setStoredEnergyQuanta(energyQuanta - costFluid);
						if(progressFluid >= this.getDurationFluid()) { this.processFluids(); progressFluid = 0; }
					}
					if(this.canProcessMetal()) {
						progressOre++;
						this.setStoredEnergyQuanta(energyQuanta - costMetal);
						if(progressOre >= this.getDurationMetal()) { this.processMetal(); progressOre = 0; }
					}
				}
				remaining--;
			}
		} finally {
			this.endMachineFluidMutation();
			runtimeAccountingMutation = false;
			lastAccountingTick = targetTick;
		}
		if(beforeEnergy != energyQuanta || beforeFluid != progressFluid || beforeMetal != progressOre || beforeLeft != (leftStack == null ? 0 : leftStack.amount) || beforeRight != (rightStack == null ? 0 : rightStack.amount)) {
			this.markDirty();
			this.markNetworkDirty();
		}
	}

	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
			new DirPos(xCoord - dir.offsetX * 6, yCoord, zCoord - dir.offsetZ * 6, dir.getOpposite()),
			new DirPos(xCoord - dir.offsetX * 6 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 6 + rot.offsetZ, dir.getOpposite()),
			new DirPos(xCoord - dir.offsetX * 6 - rot.offsetX, yCoord, zCoord - dir.offsetZ * 6 - rot.offsetZ, dir.getOpposite()),
			new DirPos(xCoord + dir.offsetX * 6, yCoord, zCoord + dir.offsetZ * 6, dir),
			new DirPos(xCoord + dir.offsetX * 6 + rot.offsetX, yCoord, zCoord + dir.offsetZ * 6 + rot.offsetZ, dir),
			new DirPos(xCoord + dir.offsetX * 6 - rot.offsetX, yCoord, zCoord + dir.offsetZ * 6 - rot.offsetZ, dir)
		};
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progressFluid = nbt.getInteger("progressFluid");
		this.progressOre = nbt.getInteger("progressOre");
		this.usageOre = nbt.getInteger("usageOre");
		this.usageFluid = nbt.getInteger("usageFluid");
		this.processFluidTime = nbt.getInteger("processFluidTime");
		this.processOreTime = nbt.getInteger("processOreTime");
		if(nbt.hasKey("leftType")) this.leftStack = new MaterialStack(Mats.matById.get(nbt.getInteger("leftType")), nbt.getInteger("leftAmount"));
		else this.leftStack = null;
		if(nbt.hasKey("rightType")) this.rightStack = new MaterialStack(Mats.matById.get(nbt.getInteger("rightType")), nbt.getInteger("rightAmount"));
		else this.rightStack = null;
		for(int i = 0; i < 4; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	public boolean canProcessFluid() {

		if(this.energyQuanta < EnergyUnits.wattsToQuantaPerTick(operatingPowerFluidWatts)) return false;

		ElectrolysisRecipe recipe = cachedFluidRecipe;

		if(recipe == null) return false;
		if(recipe.amount > tanks[0].getFill()) return false;
		if(recipe.output1.type == tanks[1].getTankType() && recipe.output1.fill + tanks[1].getFill() > tanks[1].getMaxFill()) return false;
		if(recipe.output2.type == tanks[2].getTankType() && recipe.output2.fill + tanks[2].getFill() > tanks[2].getMaxFill()) return false;

		if(recipe.byproduct != null) {

			for(int i = 0; i < recipe.byproduct.length; i++) {
				ItemStack slot = slots[11 + i];
				ItemStack byproduct = recipe.byproduct[i];

				if(slot == null) continue;
				if(!slot.isItemEqual(byproduct)) return false;
				if(slot.stackSize + byproduct.stackSize > slot.getMaxStackSize()) return false;
			}
		}

		return true;
	}

	public void processFluids() {

		ElectrolysisRecipe recipe = cachedFluidRecipe;
		tanks[0].setFill(tanks[0].getFill() - recipe.amount);
		tanks[1].setTankType(recipe.output1.type);
		tanks[2].setTankType(recipe.output2.type);
		tanks[1].setFill(tanks[1].getFill() + recipe.output1.fill);
		tanks[2].setFill(tanks[2].getFill() + recipe.output2.fill);

		if(recipe.byproduct != null) {

			for(int i = 0; i < recipe.byproduct.length; i++) {
				ItemStack slot = slots[11 + i];
				ItemStack byproduct = recipe.byproduct[i];

				if(slot == null) {
					slots[11 + i] = byproduct.copy();
				} else {
					slots[11 + i].stackSize += byproduct.stackSize;
				}
			}
		}
	}

	public boolean canProcessMetal() {

		if(slots[14] == null) return false;
		if(this.energyQuanta < EnergyUnits.wattsToQuantaPerTick(operatingPowerMetalWatts)) return false;
		if(this.tanks[3].getFill() < 100) return false;

		ElectrolysisMetalRecipe recipe = cachedMetalRecipe;
		if(recipe == null) return false;

		if(leftStack != null && recipe.output1 != null) {
			if(recipe.output1.material != leftStack.material) return false;
			if(recipe.output1.amount + leftStack.amount > this.maxMaterial) return false;
		}

		if(rightStack != null && recipe.output2 != null) {
			if(recipe.output2.material != rightStack.material) return false;
			if(recipe.output2.amount + rightStack.amount > this.maxMaterial) return false;
		}

		if(recipe.byproduct != null) {

			for(int i = 0; i < recipe.byproduct.length; i++) {
				ItemStack slot = slots[15 + i];
				ItemStack byproduct = recipe.byproduct[i];

				if(slot == null) continue;
				if(!slot.isItemEqual(byproduct)) return false;
				if(slot.stackSize + byproduct.stackSize > slot.getMaxStackSize()) return false;
			}
		}

		return true;
	}

	public void processMetal() {

		ElectrolysisMetalRecipe recipe = cachedMetalRecipe;
		if(recipe.output1 != null)
			if(leftStack == null) {
				leftStack = new MaterialStack(recipe.output1.material, recipe.output1.amount);
			} else {
				leftStack.amount += recipe.output1.amount;
			}

		if(recipe.output2 != null)
			if(rightStack == null ) {
				rightStack = new MaterialStack(recipe.output2.material, recipe.output2.amount);
			} else {
				rightStack.amount += recipe.output2.amount;
			}

		if(recipe.byproduct != null) {

			for(int i = 0; i < recipe.byproduct.length; i++) {
				ItemStack slot = slots[15 + i];
				ItemStack byproduct = recipe.byproduct[i];

				if(slot == null) {
					slots[15 + i] = byproduct.copy();
				} else {
					slots[15 + i].stackSize += byproduct.stackSize;
				}
			}
		}

		this.tanks[3].setFill(this.tanks[3].getFill() - 100);
		this.decrStackSize(14, 1);
	}

	public int getDurationMetal() {
		ElectrolysisMetalRecipe result = cachedMetalRecipe;
		if(!runtimeInitialized && result == null) result = ElectrolyserMetalRecipes.getRecipe(slots[14]);
		int base = result != null ? result.duration : 600;
		int speed = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3) - Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 1);
		return (int) Math.ceil((base * Math.max(1F - 0.25F * speed, 0.2)));
	}
	public int getDurationFluid() {
		ElectrolysisRecipe result = cachedFluidRecipe;
		if(!runtimeInitialized && result == null) result = ElectrolyserFluidRecipes.getRecipe(tanks[0].getTankType());
		int base = result != null ? result.duration : 100;
		int speed = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3) - Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 1);
		return (int) Math.ceil((base * Math.max(1F - 0.25F * speed, 0.2)));

	}

	public int getCycleCount() {
		int speed = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		return Math.min(1 + speed * 2, 7);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progressFluid = nbt.getInteger("progressFluid");
		this.progressOre = nbt.getInteger("progressOre");
		this.processFluidTime = nbt.getInteger("processFluidTime");
		this.processOreTime = nbt.getInteger("processOreTime");
		this.runtimeInitialized = false;
		this.lastAccountingTick = Long.MIN_VALUE;
		this.batteryTransitionDueTick = Long.MIN_VALUE;
		this.runtimeFluidActive = false;
		this.runtimeMetalActive = false;
		if(nbt.hasKey("leftType")) this.leftStack = new MaterialStack(Mats.matById.get(nbt.getInteger("leftType")), nbt.getInteger("leftAmount"));
		else this.leftStack = null;
		if(nbt.hasKey("rightType")) this.rightStack = new MaterialStack(Mats.matById.get(nbt.getInteger("rightType")), nbt.getInteger("rightAmount"));
		else this.rightStack = null;
		for(int i = 0; i < 4; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, this.energyQuanta);
		nbt.setInteger("progressFluid", this.progressFluid);
		nbt.setInteger("progressOre", this.progressOre);
		nbt.setInteger("processFluidTime", getDurationFluid());
		nbt.setInteger("processOreTime", getDurationMetal());
		if(this.leftStack != null) {
			nbt.setInteger("leftType", leftStack.material.id);
			nbt.setInteger("leftAmount", leftStack.amount);
		}
		if(this.rightStack != null) {
			nbt.setInteger("rightType", rightStack.material.id);
			nbt.setInteger("rightAmount", rightStack.amount);
		}
		for(int i = 0; i < 4; i++) tanks[i].writeToNBT(nbt, "t" + i);

	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 5,
				yCoord - 0,
				zCoord - 5,
				xCoord + 6,
				yCoord + 4,
				zCoord + 6
			);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		if(!runtimeEnergyMutation && !runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		this.markNetworkDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		if(slot == 1 || slot == 2) this.upgradeManager.invalidate();
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(progressFluid);
		buf.writeInt(progressOre);
		buf.writeInt(usageFluid);
		buf.writeInt(usageOre);
		buf.writeInt(getDurationFluid());
		buf.writeInt(getDurationMetal());
		buf.writeBoolean(leftStack != null);
		if(leftStack != null) { buf.writeInt(leftStack.material.id); buf.writeInt(leftStack.amount); }
		buf.writeBoolean(rightStack != null);
		if(rightStack != null) { buf.writeInt(rightStack.material.id); buf.writeInt(rightStack.amount); }
		for(FluidTank tank : tanks) tank.serialize(buf);
	}

	@Override public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progressFluid = buf.readInt();
		progressOre = buf.readInt();
		usageFluid = buf.readInt();
		usageOre = buf.readInt();
		processFluidTime = buf.readInt();
		processOreTime = buf.readInt();
		leftStack = buf.readBoolean() ? new MaterialStack(Mats.matById.get(buf.readInt()), buf.readInt()) : null;
		rightStack = buf.readBoolean() ? new MaterialStack(Mats.matById.get(buf.readInt()), buf.readInt()) : null;
		for(FluidTank tank : tanks) tank.deserialize(buf);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[2]};
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0], tanks[3]};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		if(ID == 0) return new ContainerElectrolyserFluid(player.inventory, this);
		return new ContainerElectrolyserMetal(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		if(ID == 0) return new GUIElectrolyserFluid(player.inventory, this);
		return new GUIElectrolyserMetal(player.inventory, this);
	}

	@Override
	public void receiveControl(NBTTagCompound data) { }

	@Override
	public void receiveControl(EntityPlayer player, NBTTagCompound data) {

		if(data.hasKey("sgm")) FMLNetworkHandler.openGui(player, MainRegistry.instance, 1, worldObj, xCoord, yCoord, zCoord);
		if(data.hasKey("sgf")) FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, worldObj, xCoord, yCoord, zCoord);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_electrolyser));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_DELAY, "+" + (25) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 3;
		return 0;
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		if(getFluidIDToCopy().length > 0)
			tag.setIntArray("fluidID", getFluidIDToCopy());
		if(getMatsToCopy().length > 0)
			tag.setIntArray("matFilter", getMatsToCopy());
		return tag;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		IFluidCopiable.super.pasteSettings(nbt, index, world, player, x, y, z);
	}

	@Override
	public String[] infoForDisplay(World world, int x, int y, int z) {
		ArrayList<String> names = new ArrayList<>();
		int[] fluidIDs = getFluidIDToCopy();
		int[] matIDs = getMatsToCopy();

		for (int fluidID : fluidIDs) {
			names.add(Fluids.fromID(fluidID).getUnlocalizedName());
		}
		for (int matID : matIDs) {
			names.add(Mats.matById.get(matID).getUnlocalizedName());
		}

		return names.toArray(new String[0]);
	}

	@Override
	public int[] getMatsToCopy() {
		ArrayList<Integer> types = new ArrayList<>();
		if(leftStack != null)	types.add(leftStack.material.id);
		if(rightStack != null)	types.add(rightStack.material.id);
		return BobMathUtil.intCollectionToArray(types);
	}
}
