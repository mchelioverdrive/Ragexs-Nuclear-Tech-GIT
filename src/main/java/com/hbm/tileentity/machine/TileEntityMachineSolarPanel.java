package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineSolarPanel extends TileEntityLoadedBase implements api.hbm.energymk2.IRatedGenerator, com.hbm.realsim.IMachineInfo {
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return Math.max(1000L, (long) Math.ceil(1000D * sunPower())); }

	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;

	private long energyQuanta;
	private long maxpwr = 200;

	@Override
	public void updateEntity() {
		// Generation and export are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		try {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
				this.providePowerToDirectReceiver(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			this.setStoredEnergyQuanta(com.hbm.realsim.GeneratorContract.admit(energyQuanta, getEnergyCapacityQuanta(), getOutput()));
		} finally {
			runtimeEnergyMutation = false;
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void updatePowerConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.registerPowerConnection(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(energyQuanta > 0 || this.getOutput() > 0) this.scheduleMachineTransition(now + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

    private float sunPower() {
        if(worldObj == null) return 1;
        return worldObj.provider instanceof WorldProviderOrbit ? ((WorldProviderOrbit) worldObj.provider).getSunPower() : CelestialBody.getBody(worldObj).getSunPower();
    }
    public long getOutput() {
        if(worldObj == null || (!(worldObj.provider instanceof WorldProviderOrbit) && !worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord))) return 0;
        return com.hbm.realsim.GeneratorContract.solarQuanta(worldObj.getCelestialAngle(1F), sunPower());
    }

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.getOutput());
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
        this.markDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return com.hbm.realsim.GeneratorContract.buffer(getRatedOutputWatts());
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        outputBudget.read(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		// Old saved capacity was not intentional electrical storage.
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        outputBudget.write(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setLong("maxpwr", maxpwr);
	}
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Photovoltaic specialist: approximately 1 kW peak at unit sunlight");
        lines.add("Sky/daylight and celestial irradiation apply; external storage is needed at night");
    }
}
