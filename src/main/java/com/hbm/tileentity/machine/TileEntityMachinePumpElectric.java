package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.nbt.NBTTagCompound;

public class TileEntityMachinePumpElectric extends TileEntityMachinePumpBase implements IEnergyReceiverMK2, com.hbm.realsim.IMachineInfo {
	
	public long energyQuanta;
	public static final long maxPower = 10_000;
	public static final long operatingPowerWatts = com.hbm.realsim.Groundwater.ELECTRIC_WATTS;
	
	public TileEntityMachinePumpElectric() {
		super();
		water = new FluidTank(Fluids.WATER, com.hbm.realsim.Groundwater.ELECTRIC_BUFFER).withDefaultCapacity();
	}
	
	public void updateEntity() {
		if(worldObj.isRemote) super.updateEntity();
	}

	@Override protected void updatePumpConnections() {
		for(DirPos pos : getConPos()) this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}
	
	protected NBTTagCompound getSync() {
		NBTTagCompound data = super.getSync();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		return data;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
	}

	@Override
	protected boolean canOperate() {
		return electricSpeed > 0 && electricLitersPerMinute > 0 && energyQuanta >= EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) && water.getFill() < water.getMaxFill();
	}

	@Override
	protected void operate() {
		this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
		produceWater(com.hbm.realsim.Groundwater.microlitersPerTick(operatingPowerWatts, electricLitersPerMinute / 60D));
	}

	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
	}
	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
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
		this.markDirty();
		if(!runtimeMachineMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

    @Override public long getMachineDemandWatts() { return operatingPowerWatts; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Groundwater ceiling: " + electricLitersPerMinute + " L/min");
        lines.add("Requires groundwater/terrain eligibility; full tank pauses pumping");
    }
}
