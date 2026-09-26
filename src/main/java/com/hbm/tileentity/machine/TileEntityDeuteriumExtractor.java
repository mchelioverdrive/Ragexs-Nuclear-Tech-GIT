package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityDeuteriumExtractor extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IFluidCopiable {
	
	public long energyQuanta = 0;
	public FluidTank[] tanks;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private static final int TASK_PROCESS = 1;

	public TileEntityDeuteriumExtractor() {
		super(0);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.LIGHT_WATER, 1000).migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.HEAVYWATER, 100);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.deuterium";
	}

	@Override
	public void updateEntity() {
		// Fluid processing and output transfer are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) this.updateConnections();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean fluidChanged = false;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			if(this.canProcess()) {
				int convert = Math.min(tanks[1].getMaxFill(), tanks[0].getFill()) / 50;
				convert = Math.min(convert, tanks[1].getMaxFill() - tanks[1].getFill());
				tanks[0].setFill(tanks[0].getFill() - convert * 50);
				tanks[1].setFill(tanks[1].getFill() + convert);
				this.setStoredEnergyQuanta(energyQuanta - getEnergyCapacityQuanta() / 100);
				fluidChanged = convert > 0;
			}
			if(tanks[1].getFill() > 0) this.sendFluidToAll(tanks[1], this);
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		if(fluidChanged) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 20) return;
		this.updateConnections();
		this.subscribeToAllAround(tanks[0].getTankType(), this);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(50);
	}

	private boolean canProcess() {
		return this.hasPower() && energyQuanta > 200 && this.hasEnoughWater() && tanks[1].getMaxFill() > tanks[1].getFill();
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canProcess() || tanks[1].getFill() > 0) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, 0);
		else this.cancelMachineTransition(TASK_PROCESS, 0);
	}
	
	protected void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		tanks[0].readFromNBT(data, "water");
		tanks[1].readFromNBT(data, "heavyWater");
	}

	public boolean hasPower() {
		return energyQuanta >= this.getEnergyCapacityQuanta() / 100;
	}

	public boolean hasEnoughWater() {
		return tanks[0].getFill() >= 100;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "heavyWater");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "heavyWater");
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		this.markNetworkDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 10_000;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}
}
