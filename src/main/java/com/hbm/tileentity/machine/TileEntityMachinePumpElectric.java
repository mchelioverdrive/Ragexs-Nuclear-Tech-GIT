package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.nbt.NBTTagCompound;

public class TileEntityMachinePumpElectric extends TileEntityMachinePumpBase implements IEnergyReceiverMK2 {
	
	public long energyQuanta;
	public static final long maxPower = 10_000;
	private static final long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(1_000L);
	
	public TileEntityMachinePumpElectric() {
		super();
		water = new FluidTank(Fluids.WATER, electricSpeed * 100);
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
		return energyQuanta >= EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) && water.getFill() < water.getMaxFill();
	}

	@Override
	protected void operate() {
		this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
		int pumpSpeed = water.getTankType() == Fluids.WATER ? electricSpeed : electricSpeed / nonWaterDebuff;
		water.setFill(Math.min(water.getFill() + pumpSpeed, water.getMaxFill()));
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
}
