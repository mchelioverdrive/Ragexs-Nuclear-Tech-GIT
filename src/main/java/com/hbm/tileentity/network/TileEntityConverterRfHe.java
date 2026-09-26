package com.hbm.tileentity.network;

import api.hbm.energymk2.EnergyUnits;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyProviderMK2;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.IOException;

public class TileEntityConverterRfHe extends TileEntityLoadedBase implements IEnergyProviderMK2, IEnergyHandler, IConfigurableMachine {

	public long energyQuanta;
	public final long maxPower = 5_000_000;
	public static long inputRfPerBatch = 2;
	public static long outputQuantaPerBatch = 5;
	public static double inputDecay = 0.0;

	public EnergyStorage storage = new EnergyStorage(1_000_000, 1_000_000, 1_000_000);

	@Override
	public void updateEntity() {
		
		if (!worldObj.isRemote) {
			
			// The historical default consumes 2 RF for 5 quanta: a 50% converter loss.
			// Configured output is capped at the physical RF energy represented by each batch.
			long inputRf = Math.max(inputRfPerBatch, 1);
			long outputQuanta = Math.min(Math.max(outputQuantaPerBatch, 1), EnergyUnits.rfToQuanta(Math.min(inputRf, storage.getMaxEnergyStored())));
			long batches = Math.min(storage.getEnergyStored() / inputRf, (maxPower - energyQuanta) / outputQuanta);
			long rfConsumed = batches * inputRf;
			storage.setEnergyStored((int) (storage.getEnergyStored() - rfConsumed));
			this.setStoredEnergyQuanta(this.energyQuanta + batches * outputQuanta);
			if(storage.getEnergyStored() > 0) storage.extractEnergy((int) Math.ceil(storage.getEnergyStored() * inputDecay), false);
			if(rfConsumed > 0) this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
			
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}
		}
	}
	
	@Override public boolean canConnectEnergy(ForgeDirection from) { return true; }
	@Override public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) { return storage.receiveEnergy(maxReceive, simulate); }
	@Override public int getEnergyStored(ForgeDirection from) { return storage.getEnergyStored(); }
	@Override public int getMaxEnergyStored(ForgeDirection from) { return storage.getMaxEnergyStored(); }
	@Override public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) { return 0; }

	@Override public long getStoredEnergyQuanta() { return energyQuanta; }
	@Override public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
	}
	@Override public long getEnergyCapacityQuanta() { return maxPower; }
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		storage.readFromNBT(nbt);
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		storage.writeToNBT(nbt);
	}

	@Override
	public String getConfigName() {
		return "RFToHEConverter";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		inputRfPerBatch = IConfigurableMachine.grab(obj, "L:inputRfPerBatch", IConfigurableMachine.grab(obj, "L:RF_Used2", inputRfPerBatch));
		outputQuantaPerBatch = IConfigurableMachine.grabEnergyQuanta(obj, "L:outputQuantaPerBatch", "L:HE_Created2", outputQuantaPerBatch);
		inputDecay = IConfigurableMachine.grab(obj, "D:inputDecay2", inputDecay);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:inputRfPerBatch").value(inputRfPerBatch);
		writer.name("L:outputQuantaPerBatch").value(outputQuantaPerBatch);
		writer.name("D:inputDecay2").value(inputDecay);
	}
}
