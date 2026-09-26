package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerSolidifier;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUISolidifier;
import com.hbm.inventory.recipes.SolidificationRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.I18nUtil;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class TileEntityMachineSolidifier extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardReceiver, IGUIProvider, IUpgradeInfoProvider, IInfoProviderEC, IFluidCopiable {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();


	public long energyQuanta;
	public static final long maxPower = 100000;
	public static final int usageBase = 500;
	public int usage;
	public int progress;
	public static final int processTimeBase = 100;
	public int processTime;
	
	public FluidTank tank;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeMaterialEligible;
	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(usageBase);
	private long observedPower;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private com.hbm.util.Tuple.Pair<Integer, ItemStack> cachedRecipe;
	private com.hbm.inventory.fluid.FluidType cachedRecipeFluid;
	private boolean cachedRecipeResolved;

	public TileEntityMachineSolidifier() {
		super(5);
		tank = new FluidTank(Fluids.NONE, 24_000);
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineSolidifier";
	}

	@Override
	public void updateEntity() {
		// Processing and logistics are scheduled by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRuntimeSettings(false);
		this.resolveCachedRecipe();
		this.runtimeMaterialEligible = this.canProcessMaterials();
		this.runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		long beforePower = energyQuanta;
		int beforeProgress = progress;
		this.setRuntimePower(Library.chargeTEFromItems(slots, 1, energyQuanta, maxPower));
		boolean canRun = runtimeMaterialEligible && this.hasOperatingPower();
		if(canRun && progress + 1 >= processTime) canRun = this.canProcess();
		if(canRun) {
			this.setRuntimePower(energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
			progress++;
			if(progress >= processTime) this.completeOperation();
		} else {
			progress = 0;
		}
		if(beforePower != energyQuanta || beforeProgress != progress) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.observedPower = energyQuanta;
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean tankChanged = tank.setType(4, slots);
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(tankChanged || inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			return;
		}
		if(cadence != 20) return;
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		if(recipeChanged) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			cachedRecipeResolved = false;
			this.resolveCachedRecipe();
		}
		if(settingsChanged || recipeChanged || observedPower != energyQuanta) this.markMachineDirty((settingsChanged ? MachineDirtyCause.UPGRADE : 0) | (recipeChanged ? MachineDirtyCause.RECIPE : 0) | MachineDirtyCause.ENERGY);
		this.updateConnections();
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("progress", progress);
		data.setInteger("usage", usage);
		data.setInteger("processTime", processTime);
		tank.writeToNBT(data, "t");
		this.networkPack(data, 50);
	}

	private boolean refreshRuntimeSettings(boolean contentAware) {
		int oldUsage = usage;
		int oldTime = processTime;
		if(contentAware) upgradeManager.checkSlots(slots, 2, 3);
		else upgradeManager.checkSlotsIfDirty(slots, 2, 3);
		int speed = Math.min(upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int power = Math.min(upgradeManager.getLevel(UpgradeType.POWER), 3);
		processTime = processTimeBase - (processTimeBase / 4) * speed;
		usage = (usageBase + usageBase * speed) / (power + 1);
		operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(usage);
		return oldUsage != usage || oldTime != processTime;
	}

	private void resolveCachedRecipe() {
		com.hbm.inventory.fluid.FluidType fluid = tank.getTankType();
		long revision = SerializableRecipe.getRegistryRevision();
		if(!cachedRecipeResolved || cachedRecipeFluid != fluid || observedRecipeRevision != revision) {
			cachedRecipeFluid = fluid;
			observedRecipeRevision = revision;
			cachedRecipe = SolidificationRecipes.getOutput(fluid);
			cachedRecipeResolved = true;
		}
	}

	private boolean canProcessMaterials() {
		this.resolveCachedRecipe();
		if(cachedRecipe == null || cachedRecipe.getKey() > tank.getFill()) return false;
		ItemStack output = cachedRecipe.getValue();
		return slots[0] == null || (slots[0].getItem() == output.getItem() && slots[0].getItemDamage() == output.getItemDamage() && slots[0].stackSize + output.stackSize <= slots[0].getMaxStackSize());
	}

	private boolean hasOperatingPower() {
		return energyQuanta >= EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[1] == null) return false;
		if(slots[1].getItem() == com.hbm.items.ModItems.battery_creative || slots[1].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[1].getItem() instanceof api.hbm.energymk2.IBatteryItem)) return false;
		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) slots[1].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[1]) > 0;
	}

	private void setRuntimePower(long value) {
		runtimeEnergyMutation = true;
		try { setStoredEnergyQuanta(value); } finally { runtimeEnergyMutation = false; }
	}

	private void evaluateAndSchedule(long now) {
		if((runtimeMaterialEligible && this.hasOperatingPower()) || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int tag = stack == null || stack.getItem() instanceof api.hbm.energymk2.IBatteryItem || stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode();
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + tag;
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private void completeOperation() {
		if(!this.canProcessMaterials() || cachedRecipe == null) { progress = 0; return; }
		this.beginMachineFluidMutation();
		try { tank.setFill(tank.getFill() - cachedRecipe.getKey()); } finally { this.endMachineFluidMutation(); }
		if(slots[0] == null) slots[0] = cachedRecipe.getValue().copy();
		else slots[0].stackSize += cachedRecipe.getValue().stackSize;
		progress = 0;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	private DirPos[] getConPos() {
		return new DirPos[] {
			new DirPos(xCoord, yCoord + 4, zCoord, Library.POS_Y),
			new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y),
			new DirPos(xCoord + 2, yCoord + 1, zCoord, Library.POS_X),
			new DirPos(xCoord - 2, yCoord + 1, zCoord, Library.NEG_X),
			new DirPos(xCoord, yCoord + 1, zCoord + 2, Library.POS_Z),
			new DirPos(xCoord, yCoord + 1, zCoord - 2, Library.NEG_Z)
		};
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot == 0;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 0 };
	}
	
	public boolean canProcess() {
		
		if(this.energyQuanta < usage)
			return false;
		
		Pair<Integer, ItemStack> out = SolidificationRecipes.getOutput(tank.getTankType());
		
		if(out == null)
			return false;
		
		int req = out.getKey();
		ItemStack stack = out.getValue();
		
		if(req > tank.getFill())
			return false;
		
		if(slots[0] != null) {
			
			if(slots[0].getItem() != stack.getItem())
				return false;
			
			if(slots[0].getItemDamage() != stack.getItemDamage())
				return false;
			
			if(slots[0].stackSize + stack.stackSize > slots[0].getMaxStackSize())
				return false;
		}
		
		return true;
	}
	
	public void process() {
		
		this.setStoredEnergyQuanta(this.energyQuanta - usage);
		
		progress++;
		
		if(progress >= processTime) {
			
			Pair<Integer, ItemStack> out = SolidificationRecipes.getOutput(tank.getTankType());
			int req = out.getKey();
			ItemStack stack = out.getValue();
			tank.setFill(tank.getFill() - req);
			
			if(slots[0] == null) {
				slots[0] = stack.copy();
			} else {
				slots[0].stackSize += stack.stackSize;
			}
			
			progress = 0;
			
			this.markDirty();
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		this.usage = nbt.getInteger("usage");
		this.processTime = nbt.getInteger("processTime");
		tank.readFromNBT(nbt, "t");
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		this.usage = nbt.getInteger("usage");
		this.processTime = nbt.getInteger("processTime");
		tank.readFromNBT(nbt, "tank");
		this.runtimeInitialized = false;
		this.cachedRecipeResolved = false;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, this.energyQuanta);
		nbt.setInteger("progress", this.progress);
		nbt.setInteger("usage", this.usage);
		nbt.setInteger("processTime", this.processTime);
		tank.writeToNBT(nbt, "tank");
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 1,
					yCoord,
					zCoord - 1,
					xCoord + 2,
					yCoord + 4,
					zCoord + 2
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
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerSolidifier(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUISolidifier(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_solidifier));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (100 - 100 / (level + 1)) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		return 0;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.progress > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_HE, EnergyUnits.quantaToLegacyHe(this.usage));
	}

	@Override
	public FluidTank getTankToPaste() {
		return tank;
	}
}
