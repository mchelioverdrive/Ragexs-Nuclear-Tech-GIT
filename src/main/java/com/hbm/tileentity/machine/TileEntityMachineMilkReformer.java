package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.inventory.container.ContainerMachineMilkReformer;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMilkReformer;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;


public class TileEntityMachineMilkReformer extends TileEntityMachineBase implements IGUIProvider, IFluidStandardTransceiver, IEnergyReceiverMK2 {

	public FluidTank tanks[];
	public long energyQuanta;
	public static final long maxPower = 100_000_000;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;
	private static final int TASK_REFINE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	
	public TileEntityMachineMilkReformer() {
		super(11);
		
		this.tanks = new FluidTank[4];
		this.tanks[0] = new FluidTank(Fluids.MILK, 64_000);
		this.tanks[1] = new FluidTank(Fluids.EMILK, 32_000);
		this.tanks[2] = new FluidTank(Fluids.CMILK, 32_000);
		this.tanks[3] = new FluidTank(Fluids.CREAM, 32_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.milkreformer";
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[2], tanks[3]};	
		}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineMilkReformer(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMilkReformer(player.inventory, this);
	}
	
	@Override
	public void updateEntity() {
		// Server processing is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType inputType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(150);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_REFINE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int oldInputFill = tanks[0].getFill();
		int oldOutput1 = tanks[1].getFill();
		int oldOutput2 = tanks[2].getFill();
		int oldOutput3 = tanks[3].getFill();
		int oldInventoryFingerprint = this.inventoryFingerprint();
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
			this.refineBatch();
			this.unloadOutputContainers();
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
		boolean inventoryChanged = oldInventoryFingerprint != observedInventoryFingerprint;
		if(inventoryChanged) this.markNetworkDirty();
		if(oldEnergy != energyQuanta || inventoryChanged || oldInputFill != tanks[0].getFill() || oldOutput1 != tanks[1].getFill() || oldOutput2 != tanks[2].getFill() || oldOutput3 != tanks[3].getFill()) this.markDirty();
		this.sendOutputFluids();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(150);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.ENERGY);
		} else if(cadence == 20) {
			this.updateConnections();
		}
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
		energyQuanta = buf.readLong();
		for(int i = 0; i < 4; i++) tanks[i].deserialize(buf);
	}
	
	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].loadTank(1, 2, slots);
			this.unloadOutputContainers();
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private boolean canRefine() {
		return energyQuanta >= 10_000 && tanks[0].getFill() >= 100
				&& tanks[1].getFill() + 50 <= tanks[1].getMaxFill()
				&& tanks[2].getFill() + 35 <= tanks[2].getMaxFill()
				&& tanks[3].getFill() + 15 <= tanks[3].getMaxFill();
	}

	private void refineBatch() {
		if(!this.canRefine()) return;
		this.setStoredEnergyQuanta(energyQuanta - 10_000);
		tanks[0].setFill(tanks[0].getFill() - 100);
		tanks[1].setFill(tanks[1].getFill() + 50);
		tanks[2].setFill(tanks[2].getFill() + 35);
		tanks[3].setFill(tanks[3].getFill() + 15);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasFluidOutput() {
		return tanks[1].getFill() > 0 || tanks[2].getFill() > 0 || tanks[3].getFill() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canRefine() || this.hasBatteryWork() || this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_REFINE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_REFINE, TASK_SLOT_MAIN);
	}

	private void unloadOutputContainers() {
		tanks[1].unloadTank(3, 4, slots);
		tanks[2].unloadTank(5, 6, slots);
		tanks[3].unloadTank(7, 8, slots);
	}

	private void sendOutputFluids() {
		for(DirPos pos : getConPos()) {
			for(int i = 1; i < 4; i++) if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(net.minecraft.item.ItemStack stack : slots) {
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
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "m1");
		tanks[2].readFromNBT(nbt, "m2");
		tanks[3].readFromNBT(nbt, "m3");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "m1");
		tanks[2].writeToNBT(nbt, "m2");
		tanks[3].writeToNBT(nbt, "m3");
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
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

}
