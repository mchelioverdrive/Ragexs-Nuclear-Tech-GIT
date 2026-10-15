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
	
	private static final com.hbm.realsim.ProcessRecipe HEAVY_WATER_BILL = com.hbm.realsim.ChemistryProcesses.heavyWater();
    private static final com.hbm.realsim.ProcessRecipe HEAVY_TOWER_BILL = com.hbm.realsim.ChemistryProcesses.heavyWaterTower();
    private static final com.hbm.realsim.ProcessRecipe AIR_BILL = com.hbm.realsim.ChemistryProcesses.airSeparation();
    public final com.hbm.realsim.ProcessBatch processBatch = new com.hbm.realsim.ProcessBatch();
    public long energyQuanta = 0;
	public FluidTank[] tanks;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private static final int TASK_PROCESS = 1;

	public TileEntityDeuteriumExtractor() {
		super(0);
		tanks = new FluidTank[5];
		tanks[0] = new FluidTank(Fluids.LIGHT_WATER, 60).withDefaultCapacity().migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.HEAVYWATER, 10).withDefaultCapacity();
		for(int i = 2; i < tanks.length; i++) tanks[i] = new FluidTank(Fluids.NONE, 100);
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
            com.hbm.realsim.ProcessMachine.sizeProcessBuffers(processBatch.active() ? processBatch.recipe() : selectedBill(), new FluidTank[] {tanks[0]}, processOutputs());
            if(this.canProcess()) fluidChanged = com.hbm.realsim.ProcessMachine.step(this, processBatch, selectedBill(),
                new FluidTank[] {tanks[0]}, processOutputs(), null, 0, -1, 0, -1);
            for(FluidTank output : processOutputs()) if(output.getFill() > 0) this.sendFluidToAll(output, this);
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

    private com.hbm.realsim.ProcessRecipe selectedBill() { return this instanceof TileEntityAtmoTower ? AIR_BILL : this instanceof TileEntityDeuteriumTower ? HEAVY_TOWER_BILL : HEAVY_WATER_BILL; }
    public FluidTank[] processOutputs() { return new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]}; }
    private boolean canProcess() {
        return processBatch.active() || com.hbm.realsim.ProcessMachine.inputsReady(selectedBill(), new FluidTank[] {tanks[0]}, null, 0, -1);
    }

    private boolean hasOutputs() { for(FluidTank tank : processOutputs()) if(tank.getFill() > 0) return true; return false; }

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canProcess() || hasOutputs()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, 0);
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
        for(int i = 2; i < tanks.length; i++) tanks[i].readFromNBT(data, "coproduct" + i);
	}

	public boolean hasPower() {
		return energyQuanta >= this.getEnergyCapacityQuanta() / 100;
	}

	public boolean hasEnoughWater() {
		return tanks[0].getFill() >= selectedBill().quantities[0];
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "heavyWater");
        for(int i = 2; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "coproduct" + i);
        processBatch.read(nbt);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "heavyWater");
        for(int i = 2; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "coproduct" + i);
        processBatch.write(nbt);
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
		return processOutputs();
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

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
