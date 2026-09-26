package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMixer;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMixer;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.MixerRecipes.MixerRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.*;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class TileEntityMachineMixer extends TileEntityMachineBase implements INBTPacketReceiver, IControlReceiver, IGUIProvider, IEnergyReceiverMK2, IFluidStandardTransceiver, IUpgradeInfoProvider, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public long energyQuanta;
	public static final long maxPower = 10_000;
	public int progress;
	public int processTime;
	public int recipeIndex;
	
	public float rotation;
	public float prevRotation;
	public boolean wasOn = false;

	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(50L);
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeStateInitialized;
	private boolean runtimeEnergyMutation;
	private long nextRuntimeTick = -1L;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int runtimeConnectionPolls;
	
	public FluidTank[] tanks;

	public TileEntityMachineMixer() {
		super(5);
		this.tanks = new FluidTank[3];
		this.tanks[0] = new FluidTank(Fluids.NONE, 16_000);
		this.tanks[1] = new FluidTank(Fluids.NONE, 16_000);
		this.tanks[2] = new FluidTank(Fluids.NONE, 24_000);
		for(FluidTank tank : this.tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineMixer";
	}

	@Override
	public void updateEntity() {
		
		if(worldObj.isRemote) {
			
			this.prevRotation = this.rotation;
			
			if(this.wasOn) {
				this.rotation += 20F;
			}
			
			if(this.rotation >= 360) {
				this.rotation -= 360;
				this.prevRotation -= 360;
			}
		}
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRuntimeSettings(false);
		this.runtimeStateInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeStateInitialized) return;
		nextRuntimeTick = -1L;
		long oldPower = energyQuanta;
		int oldProgress = progress;
		int oldProcessTime = processTime;
		boolean oldWasOn = wasOn;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int overLevel = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		wasOn = this.canProcess();
		if(wasOn) {
			progress++;
			this.setRuntimePower(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
			processTime -= processTime * speedLevel / 4;
			processTime /= (overLevel + 1);
			if(processTime <= 0) processTime = 1;
			if(progress >= processTime) {
				this.process();
				progress = 0;
			}
		} else {
			progress = 0;
		}
		if(oldPower != energyQuanta || oldProgress != progress || oldProcessTime != processTime || oldWasOn != wasOn) {
			this.markDirty();
			this.markNetworkDirty();
			this.networkPackNTIfDirty(50);
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean outputTypeChanged = tanks[2].setType(2, slots);
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(outputTypeChanged || inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			for(DirPos pos : getConPos()) if(tanks[2].getFill() > 0) this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		if(recipeChanged) observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if(recipeChanged || settingsChanged) this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | (settingsChanged ? MachineDirtyCause.UPGRADE : 0));
		if(++runtimeConnectionPolls >= 3) {
			runtimeConnectionPolls = 0;
			for(DirPos pos : getConPos()) {
				this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				if(tanks[0].getTankType() != Fluids.NONE) this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				if(tanks[1].getTankType() != Fluids.NONE) this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
		}
		this.networkPackNTIfDirty(50);
	}

	private boolean refreshRuntimeSettings(boolean contentAware) {
		long oldPower = operatingPowerWatts;
		if(contentAware) this.upgradeManager.checkSlots(slots, 3, 4);
		else this.upgradeManager.checkSlotsIfDirty(slots, 3, 4);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overLevel = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		int quantaPerTick = 50;
		quantaPerTick += speedLevel * 150;
		quantaPerTick -= quantaPerTick * powerLevel * 0.25;
		quantaPerTick *= (overLevel * 3 + 1);
		operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(quantaPerTick);
		return oldPower != operatingPowerWatts;
	}

	private void evaluateAndSchedule(long now) {
		boolean shouldRun = this.canProcess() || progress > 0 || this.hasBatteryWork();
		if(!shouldRun) {
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			nextRuntimeTick = -1L;
			return;
		}
		nextRuntimeTick = now + 1L;
		this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof api.hbm.energymk2.IBatteryItem)) return false;
		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}
	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(processTime);
		buf.writeInt(progress);
		buf.writeInt(recipeIndex);
		buf.writeBoolean(wasOn);
		
		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		processTime = buf.readInt();
		progress = buf.readInt();
		recipeIndex = buf.readInt();
		wasOn = buf.readBoolean();
		
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.processTime = nbt.getInteger("processTime");
		this.progress = nbt.getInteger("progress");
		this.recipeIndex = nbt.getInteger("recipe");
		this.wasOn = nbt.getBoolean("wasOn");
		for(int i = 0; i < 3; i++) {
			tanks[i].readFromNBT(nbt, i + "");
		}
	}
	
	public boolean canProcess() {

		MixerRecipe[] recipes = MixerRecipes.getOutput(tanks[2].getTankType());
		if(recipes == null || recipes.length <= 0) {
			this.recipeIndex = 0;
			return false;
		}
		
		this.recipeIndex = this.recipeIndex % recipes.length;
		MixerRecipe recipe = recipes[this.recipeIndex];
		if(recipe == null) {
			this.recipeIndex = 0;
			return false;
		}
		
		tanks[0].setTankType(recipe.input1 != null ? recipe.input1.type : Fluids.NONE);
		tanks[1].setTankType(recipe.input2 != null ? recipe.input2.type : Fluids.NONE);

		if(recipe.input1 != null && tanks[0].getFill() < recipe.input1.fill) return false;
		if(recipe.input2 != null && tanks[1].getFill() < recipe.input2.fill) return false;
		
		/* simplest check would usually go first, but fluid checks also do the setup and we want that to happen even without power */
		if(this.energyQuanta < EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts)) return false;
		
		if(recipe.output + tanks[2].getFill() > tanks[2].getMaxFill()) return false;
		
		if(recipe.solidInput != null) {
			
			if(slots[1] == null) return false;
			
			if(!recipe.solidInput.matchesRecipe(slots[1], true) || recipe.solidInput.stacksize > slots[1].stackSize) return false; 
		}
		
		this.processTime = recipe.processTime;
		return true;
	}
	
	protected void process() {
		
		MixerRecipe[] recipes = MixerRecipes.getOutput(tanks[2].getTankType());
		MixerRecipe recipe = recipes[this.recipeIndex % recipes.length];

		if(recipe.input1 != null) tanks[0].setFill(tanks[0].getFill() - recipe.input1.fill);
		if(recipe.input2 != null) tanks[1].setFill(tanks[1].getFill() - recipe.input2.fill);
		if(recipe.solidInput != null) this.decrStackSize(1, recipe.solidInput.stacksize);
		tanks[2].setFill(tanks[2].getFill() + recipe.output);
	}
	
	public int getConsumption() {
		return (int) EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts);
	}

	public long getOperatingPowerWatts() { return operatingPowerWatts; }
	
	protected DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y),
				new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z),
		};
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 1 };
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		
		MixerRecipe[] recipes = MixerRecipes.getOutput(tanks[2].getTankType());
		if(recipes == null || recipes.length <= 0) return false;
		
		MixerRecipe recipe = recipes[this.recipeIndex % recipes.length];
		if(recipe == null || recipe.solidInput == null) return false;
			
		return recipe.solidInput.matchesRecipe(itemStack, true);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		this.processTime = nbt.getInteger("processTime");
		this.recipeIndex = nbt.getInteger("recipe");
		this.runtimeStateInitialized = false;
		nextRuntimeTick = -1L;
		for(int i = 0; i < 3; i++) this.tanks[i].readFromNBT(nbt, i + "");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
		nbt.setInteger("processTime", processTime);
		nbt.setInteger("recipe", recipeIndex);
		for(int i = 0; i < 3; i++) this.tanks[i].writeToNBT(nbt, i + "");
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[2]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0], tanks[1]};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMixer(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMixer(player.inventory, this);
	}
	
	AxisAlignedBB aabb;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(aabb != null)
			return aabb;
		
		aabb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 3, zCoord + 1);
		return aabb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistance(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 16;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("toggle")) {
			this.recipeIndex++;
			this.markMachineDirty(MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION);
		}
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		if(slot == 3 || slot == 4) this.upgradeManager.invalidate();
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_mixer));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 300) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 25) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 6;
		return 0;
	}

	@Override
	public FluidTank getTankToPaste() {
		return this.tanks[2];
	}

}
