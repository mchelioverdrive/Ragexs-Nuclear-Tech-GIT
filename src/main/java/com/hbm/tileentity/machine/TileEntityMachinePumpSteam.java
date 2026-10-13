package com.hbm.tileentity.machine;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.fauxpointtwelve.DirPos;

import net.minecraft.nbt.NBTTagCompound;

public class TileEntityMachinePumpSteam extends TileEntityMachinePumpBase {

	public FluidTank steam;
	public FluidTank lps;
	
	public TileEntityMachinePumpSteam() {
		super();
		water = new FluidTank(Fluids.WATER, com.hbm.realsim.Groundwater.STEAM_BUFFER).withDefaultCapacity();
		steam = new FluidTank(Fluids.STEAM, 1_000);
		lps = new FluidTank(Fluids.SPENTSTEAM, 10);
	}
	
	public void updateEntity() {
		if(worldObj.isRemote) super.updateEntity();
	}

	@Override protected void updatePumpConnections() {
		for(DirPos pos : getConPos()) this.trySubscribe(steam.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	@Override protected void sendAdditionalFluids() {
		if(lps.getFill() <= 0) return;
		for(DirPos pos : getCachedConnections()) this.sendFluid(lps, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	@Override protected boolean hasAdditionalFluidToSend() {
		return lps.getFill() > 0;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {water, steam, lps};
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {water, lps};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {steam};
	}
	
	protected NBTTagCompound getSync() {
		NBTTagCompound data = super.getSync();
		steam.writeToNBT(data, "s");
		lps.writeToNBT(data, "l");
		return data;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		steam.readFromNBT(nbt, "s");
		lps.readFromNBT(nbt, "l");
	}

	@Override
	protected boolean canOperate() {
		return steamSpeed > 0 && steamLitersPerMinute > 0 && (shaftWorkJoules >= com.hbm.realsim.Groundwater.STEAM_SHAFT_WATTS / 20
				|| steam.getFill() >= 100 && lps.getFill() < lps.getMaxFill()) && water.getFill() < water.getMaxFill();
	}

	@Override
	protected void operate() {
		if(shaftWorkJoules < com.hbm.realsim.Groundwater.STEAM_SHAFT_WATTS / 20) {
			steam.setFill(steam.getFill() - 100);
			lps.setFill(lps.getFill() + 1);
			shaftWorkJoules += com.hbm.realsim.Groundwater.STEAM_PARCEL_SHAFT_JOULES;
		}
		shaftWorkJoules -= com.hbm.realsim.Groundwater.STEAM_SHAFT_WATTS / 20;
		produceWater(com.hbm.realsim.Groundwater.microlitersPerTick(com.hbm.realsim.Groundwater.STEAM_SHAFT_WATTS, steamLitersPerMinute / 60D));
	}

	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		steam.writeToNBT(nbt, "s");
		lps.writeToNBT(nbt, "l");
	}
	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if(nbt.hasKey("s")) steam.readFromNBT(nbt, "s");
		if(nbt.hasKey("l")) lps.readFromNBT(nbt, "l");
	}
}
