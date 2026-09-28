package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.container.ContainerMachineCatalyticReformer;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineCatalyticReformer;
import com.hbm.inventory.recipes.ReformingRecipes;
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
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCatalyticReformer extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, IGUIProvider, IFluidCopiable {
	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public FluidTank[] tanks;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_OUTPUT = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_OUTPUT = 2;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeAccountingMutation;
	private boolean runtimeOutputTransfer;
	private boolean runtimeActive;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long batteryTransitionDueTick = Long.MIN_VALUE;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;

	public TileEntityMachineCatalyticReformer() {
		super(11);
		
		this.tanks = new FluidTank[4];
		this.tanks[0] = new FluidTank(Fluids.NAPHTHA, 64_000);
		this.tanks[1] = new FluidTank(Fluids.REFORMATE, 24_000);
		this.tanks[2] = new FluidTank(Fluids.PETROLEUM, 24_000);
		this.tanks[3] = new FluidTank(Fluids.HYDROGEN, 24_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.catalyticReformer";
	}

	@Override
	public void updateEntity() {
		// Server work is driven by MachineRuntime; client synchronization remains packet-based.
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleReformsThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		batteryTransitionDueTick = Long.MIN_VALUE;
		runtimeActive = false;
		super.onChunkUnload();
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = runtimeActive;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleReformsThrough(now - 1L);
		runtimeInitialized = true;
		this.refreshRuntimeState();
		if(wasActive && !this.hasBatteryWork()) this.settleReformsThrough(now);
		else if(!wasActive) lastAccountingTick = now;
		this.evaluateAndSchedule(now, wasActive && this.hasBatteryWork());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			batteryTransitionDueTick = Long.MIN_VALUE;
			this.settleReformsThrough(now - 1L);
			long oldEnergy = energyQuanta;
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower)); }
			finally { runtimeEnergyMutation = false; }
			if(oldEnergy != energyQuanta) { this.markDirty(); this.markNetworkDirty(); }
			this.settleReformsThrough(now);
			this.evaluateAndSchedule(now, false);
			this.sendRuntimeState();
			return;
		}
		if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_MAIN) {
			this.settleReformsThrough(now);
			this.evaluateAndSchedule(now, false);
			this.sendRuntimeState();
			return;
		}
		if(taskType == TASK_OUTPUT && taskSlot == TASK_SLOT_OUTPUT) {
			if(batteryTransitionDueTick <= now) {
				this.scheduleMachineTransition(now, TASK_OUTPUT, TASK_SLOT_OUTPUT);
				return;
			}
			runtimeOutputTransfer = true;
			try { this.sendOutputFluids(); }
			finally { runtimeOutputTransfer = false; }
			this.evaluateAndSchedule(now, false);
			this.sendRuntimeState();
		}
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			this.settleReformsThrough(worldObj.getTotalWorldTime());
			boolean inventoryChanged = this.observeInventoryFingerprint();
			this.refreshRuntimeState();
			this.sendRuntimeState();
			if(inventoryChanged) {
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID);
			}
			this.evaluateAndSchedule(worldObj.getTotalWorldTime(), false);
		} else if(cadence == 20) {
			this.updateConnections();
		}
	}
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < 4; i++) tanks[i].readFromNBT(nbt, "" + i);
	}
	
	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].setType(9, slots);
			tanks[0].loadTank(1, 2, slots);
			Triplet<FluidStack, FluidStack, FluidStack> output = ReformingRecipes.getOutput(tanks[0].getTankType());
			if(output == null) {
				tanks[1].setTankType(Fluids.NONE);
				tanks[2].setTankType(Fluids.NONE);
				tanks[3].setTankType(Fluids.NONE);
			} else {
				tanks[1].setTankType(output.getX().type);
				tanks[2].setTankType(output.getY().type);
				tanks[3].setTankType(output.getZ().type);
			}
			tanks[1].unloadTank(3, 4, slots);
			tanks[2].unloadTank(5, 6, slots);
			tanks[3].unloadTank(7, 8, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private boolean canReform(Triplet<FluidStack, FluidStack, FluidStack> out) {
		return energyQuanta >= 20_000L && tanks[0].getFill() >= 100 && slots[10] != null && slots[10].getItem() == ModItems.catalytic_converter
				&& tanks[1].getFill() + out.getX().fill <= tanks[1].getMaxFill()
				&& tanks[2].getFill() + out.getY().fill <= tanks[2].getMaxFill()
				&& tanks[3].getFill() + out.getZ().fill <= tanks[3].getMaxFill();
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasFluidOutput() {
		return tanks[1].getFill() > 0 || tanks[2].getFill() > 0 || tanks[3].getFill() > 0;
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
		Triplet<FluidStack, FluidStack, FluidStack> output = ReformingRecipes.getOutput(tanks[0].getTankType());
		boolean canRun = output != null && this.canReform(output);
		runtimeActive = canRun;
		if(canRun && !battery) {
			long steps = Math.min(tanks[0].getFill() / 100L, energyQuanta / 20_000L);
			if(output.getX().fill > 0) steps = Math.min(steps, (tanks[1].getMaxFill() - tanks[1].getFill()) / output.getX().fill);
			if(output.getY().fill > 0) steps = Math.min(steps, (tanks[2].getMaxFill() - tanks[2].getFill()) / output.getY().fill);
			if(output.getZ().fill > 0) steps = Math.min(steps, (tanks[3].getMaxFill() - tanks[3].getFill()) / output.getZ().fill);
			if(steps > 0L) this.scheduleMachineTransition(now + steps, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
		if(this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_OUTPUT, TASK_SLOT_OUTPUT);
		else this.cancelMachineTransition(TASK_OUTPUT, TASK_SLOT_OUTPUT);
	}

	private void settleReformsThrough(long targetTick) {
		if(worldObj == null || worldObj.isRemote || runtimeAccountingMutation || lastAccountingTick == Long.MIN_VALUE || targetTick <= lastAccountingTick) return;
		long elapsed = targetTick - lastAccountingTick;
		lastAccountingTick = targetTick;
		Triplet<FluidStack, FluidStack, FluidStack> output = ReformingRecipes.getOutput(tanks[0].getTankType());
		if(!runtimeInitialized || output == null || !this.canReform(output)) return;
		long steps = Math.min(elapsed, Math.min(tanks[0].getFill() / 100L, energyQuanta / 20_000L));
		if(output.getX().fill > 0) steps = Math.min(steps, (tanks[1].getMaxFill() - tanks[1].getFill()) / output.getX().fill);
		if(output.getY().fill > 0) steps = Math.min(steps, (tanks[2].getMaxFill() - tanks[2].getFill()) / output.getY().fill);
		if(output.getZ().fill > 0) steps = Math.min(steps, (tanks[3].getMaxFill() - tanks[3].getFill()) / output.getZ().fill);
		if(steps <= 0L) return;
		runtimeAccountingMutation = true;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			tanks[0].setFill(tanks[0].getFill() - (int) (steps * 100L));
			tanks[1].setFill(tanks[1].getFill() + (int) (steps * output.getX().fill));
			tanks[2].setFill(tanks[2].getFill() + (int) (steps * output.getY().fill));
			tanks[3].setFill(tanks[3].getFill() + (int) (steps * output.getZ().fill));
			this.setStoredEnergyQuanta(energyQuanta - steps * 20_000L);
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
			runtimeAccountingMutation = false;
		}
		this.markDirty();
		this.markNetworkDirty();
	}

	private void sendOutputFluids() {
		for(DirPos pos : getConPos()) {
			for(int i = 1; i < 4; i++) {
				if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
		}
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, this.energyQuanta);
		for(int i = 0; i < 4; i++) tanks[i].writeToNBT(data, "" + i);
		this.networkPack(data, 150);
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) {
				hash = 31 * hash + stack.stackSize;
				hash = 31 * hash + stack.getItemDamage();
				hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
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
	
	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		
		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX * 2 + rot.offsetX, yCoord, zCoord + dir.offsetZ * 2 + rot.offsetZ, dir),
				new DirPos(xCoord + dir.offsetX * 2 - rot.offsetX, yCoord, zCoord + dir.offsetZ * 2 - rot.offsetZ, dir),
				new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 3, yCoord, zCoord + rot.offsetZ * 3, rot),
				new DirPos(xCoord - rot.offsetX * 3, yCoord, zCoord - rot.offsetZ * 3, rot.getOpposite())
		};
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "o1");
		tanks[2].readFromNBT(nbt, "o2");
		tanks[3].readFromNBT(nbt, "o3");
		runtimeInitialized = false;
		runtimeActive = false;
		lastAccountingTick = Long.MIN_VALUE;
		batteryTransitionDueTick = Long.MIN_VALUE;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleReformsThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "o1");
		tanks[2].writeToNBT(nbt, "o2");
		tanks[3].writeToNBT(nbt, "o3");
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + 7,
					zCoord + 3
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
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		if(!runtimeEnergyMutation && !runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleReformsThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleReformsThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) {
			this.settleReformsThrough(worldObj.getTotalWorldTime() - (runtimeOutputTransfer ? 0L : 1L));
		}
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
		return new FluidTank[] {tanks[1], tanks[2], tanks[3]};
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

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
		return new ContainerMachineCatalyticReformer(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCatalyticReformer(player.inventory, this);
	}
}
