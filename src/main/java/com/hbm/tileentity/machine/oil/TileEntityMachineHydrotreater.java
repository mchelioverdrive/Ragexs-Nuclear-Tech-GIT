package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.container.ContainerMachineHydrotreater;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineHydrotreater;
import com.hbm.inventory.recipes.HydrotreatingRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.Tuple.Triplet;
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
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineHydrotreater extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, IGUIProvider, IFluidCopiable {
	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public FluidTank[] tanks;
	private static final int TASK_BATCH = 1;
	private static final int TASK_BATTERY = 2;
	private boolean runtimeEnergyMutation;
	private boolean runtimeInitialized;
	private long observedPower;
	private long observedRecipeRevision = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long nextBatchTick = -1L;
	private FluidType cachedFeedType;
	private Triplet<FluidStack, FluidStack, FluidStack> cachedRecipe;

	public TileEntityMachineHydrotreater() {
		super(11);
		
		this.tanks = new FluidTank[4];
		this.tanks[0] = new FluidTank(Fluids.OIL, 64_000);
		this.tanks[1] = new FluidTank(Fluids.HYDROGEN, 64_000).withPressure(1);
		this.tanks[2] = new FluidTank(Fluids.OIL_DS, 24_000);
		this.tanks[3] = new FluidTank(Fluids.SOURGAS, 24_000);
		for(FluidTank tank : this.tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.hydrotreater";
	}

	@Override
	public void updateEntity() {
		// Recipe execution and fluid logistics are owned by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshCachedRecipe();
		this.configureRecipeTanks();
		this.runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime(), 1L);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == -1) {
			long before = energyQuanta;
			this.runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower)); }
			finally { this.runtimeEnergyMutation = false; }
			if(before != energyQuanta) {
				this.observedPower = energyQuanta;
				this.markDirty();
				this.markNetworkDirty();
				this.evaluateAndSchedule(now, 1L);
			}
			if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, -1);
			return;
		}
		if(taskType != TASK_BATCH || taskSlot != 0) return;
		this.nextBatchTick = -1L;
		if(this.canProcessBatch()) {
			this.processBatch();
			this.nextBatchTick = now + 2L;
			this.evaluateAndSchedule(now, 2L);
		} else {
			this.cancelMachineTransition(TASK_BATCH, 0);
			this.evaluateAndSchedule(now, 2L);
		}
		this.observedPower = energyQuanta;
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean fluidChanged = tanks[0].setType(9, slots);
			fluidChanged |= tanks[0].loadTank(1, 2, slots);
			fluidChanged |= tanks[1].loadTank(3, 4, slots);
			fluidChanged |= tanks[2].unloadTank(5, 6, slots);
			fluidChanged |= tanks[3].unloadTank(7, 8, slots);
			boolean inventoryChanged = this.observeInventoryFingerprint();
			if(fluidChanged || inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			for(DirPos pos : getConPos()) {
				for(int i = 2; i < 4; i++) if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		boolean powerChanged = observedPower != energyQuanta;
		if(recipeChanged) {
			this.observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.cachedFeedType = null;
			this.refreshCachedRecipe();
		}
		if(recipeChanged || powerChanged) this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | (powerChanged ? MachineDirtyCause.ENERGY : 0));
		this.updateConnections();
		this.networkPackNTIfDirty(25);
	}

	private void refreshCachedRecipe() {
		FluidType feed = tanks[0].getTankType();
		long revision = SerializableRecipe.getRegistryRevision();
		if(cachedFeedType != feed || observedRecipeRevision != revision) {
			cachedFeedType = feed;
			observedRecipeRevision = revision;
			cachedRecipe = HydrotreatingRecipes.getOutput(feed);
		}
	}

	private void configureRecipeTanks() {
		this.beginMachineFluidMutation();
		try {
			if(cachedRecipe == null) {
				tanks[2].setTankType(Fluids.NONE);
				tanks[3].setTankType(Fluids.NONE);
				return;
			}
			tanks[1].withPressure(cachedRecipe.getX().pressure).setTankType(cachedRecipe.getX().type);
			tanks[2].setTankType(cachedRecipe.getY().type);
			tanks[3].setTankType(cachedRecipe.getZ().type);
		} finally { this.endMachineFluidMutation(); }
	}

	private boolean canProcessBatch() {
		if(cachedRecipe == null || energyQuanta < 20_000L || tanks[0].getFill() < 100 || tanks[1].getFill() < cachedRecipe.getX().fill) return false;
		if(slots[10] == null || slots[10].getItem() != ModItems.catalytic_converter) return false;
		return tanks[2].getFill() + cachedRecipe.getY().fill <= tanks[2].getMaxFill() && tanks[3].getFill() + cachedRecipe.getZ().fill <= tanks[3].getMaxFill();
	}

	private void processBatch() {
		if(!this.canProcessBatch()) return;
		this.beginMachineFluidMutation();
		this.runtimeEnergyMutation = true;
		try {
			tanks[0].setFill(tanks[0].getFill() - 100);
			tanks[1].setFill(tanks[1].getFill() - cachedRecipe.getX().fill);
			tanks[2].setFill(tanks[2].getFill() + cachedRecipe.getY().fill);
			tanks[3].setFill(tanks[3].getFill() + cachedRecipe.getZ().fill);
			this.setStoredEnergyQuanta(energyQuanta - 20_000L);
		} finally {
			this.runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
		this.markNetworkDirty();
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof api.hbm.energymk2.IBatteryItem)) return false;
		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void evaluateAndSchedule(long now, long delay) {
		if(this.canProcessBatch()) {
			long due = Math.max(now + delay, nextBatchTick);
			nextBatchTick = due;
			this.scheduleMachineTransition(due, TASK_BATCH, 0);
		} else {
			this.cancelMachineTransition(TASK_BATCH, 0);
			nextBatchTick = -1L;
		}
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, -1);
		else this.cancelMachineTransition(TASK_BATTERY, -1);
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

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		for(int i = 0; i < 4; i++) tanks[i].serialize(buf);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.energyQuanta = buf.readLong();
		for(int i = 0; i < 4; i++) tanks[i].deserialize(buf);
	}
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	public DirPos[] getConPos() {
		
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				new DirPos(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
				new DirPos(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				new DirPos(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "t0");
		tanks[1].readFromNBT(nbt, "t1");
		tanks[2].readFromNBT(nbt, "t2");
		tanks[3].readFromNBT(nbt, "t3");
		runtimeInitialized = false;
		cachedFeedType = null;
		nextBatchTick = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "t0");
		tanks[1].writeToNBT(nbt, "t1");
		tanks[2].writeToNBT(nbt, "t2");
		tanks[3].writeToNBT(nbt, "t3");
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
					yCoord + 7,
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

	@Override public long getStoredEnergyQuanta() { return energyQuanta; }
	@Override public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}
	@Override public long getEnergyCapacityQuanta() { return maxPower; }
	@Override public FluidTank[] getAllTanks() { return tanks; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] {tanks[2], tanks[3]}; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tanks[0], tanks[1]}; }
	@Override public boolean canConnect(ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }
	@Override public boolean canConnect(FluidType type, ForgeDirection dir) { return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN; }

	@Override
	public void writeNBT(NBTTagCompound nbt) {
		if(tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && tanks[2].getFill() == 0 && tanks[3].getFill() == 0) return;
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 4; i++) this.tanks[i].writeToNBT(data, "" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < 4; i++) this.tanks[i].readFromNBT(data, "" + i);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineHydrotreater(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineHydrotreater(player.inventory, this);
	}

	@Override
	public FluidTank getTankToPaste() {
		return tanks[0];
	}
}
