package com.hbm.tileentity.network;

import api.hbm.energymk2.EnergyUnits;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.calc.Location;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.IOException;

public class TileEntityConverterHeRf extends TileEntityLoadedBase implements IEnergyReceiverMK2, IEnergyHandler, IConfigurableMachine {
	
	//Thanks to the great people of Fusion Warfare for helping me with the original implementation of the RF energy API
	
	public long energyQuanta;
	public final long maxPower = 5_000_000;
	public static long inputQuantaPerBatch = 5;
	public static long outputRfPerBatch = 1;
	public static double inputDecay = 0.0;
	public EnergyStorage storage = new EnergyStorage(1_000_000, 1_000_000, 1_000_000);

	@Override
	public void updateEntity() {
		
		if (!worldObj.isRemote) {
			
			// Legacy config specifies input quanta per RF output batch. The default 5:1 is lossless.
			long outputRf = Math.max(outputRfPerBatch, 1);
			long minimumInputQuanta = outputRf > Long.MAX_VALUE / EnergyUnits.QUANTA_PER_RF ? Long.MAX_VALUE : EnergyUnits.rfToQuanta(outputRf);
			long inputQuanta = Math.max(inputQuantaPerBatch, minimumInputQuanta);
			long batches = Math.min(this.energyQuanta / inputQuanta, (storage.getMaxEnergyStored() - storage.getEnergyStored()) / outputRf);
			long rfCreated = batches * outputRf;
			this.setStoredEnergyQuanta(this.energyQuanta - batches * inputQuanta);
			this.storage.setEnergyStored((int) (storage.getEnergyStored() + rfCreated));
			if(energyQuanta > 0) this.setStoredEnergyQuanta((long) (this.energyQuanta * (1D - inputDecay)));
			if(rfCreated > 0) this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
			
			for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
				
				Location loc = new Location(worldObj, xCoord, yCoord, zCoord).add(dir);
				TileEntity entity = loc.getTileEntity();
			
				if (entity != null && entity instanceof IEnergyReceiver) {
					IEnergyReceiver receiver = (IEnergyReceiver) entity;
					
					int maxExtract = storage.getMaxExtract();
					int maxAvailable = storage.extractEnergy(maxExtract, true);
					int energyTransferred = receiver.receiveEnergy(dir.getOpposite(), maxAvailable, false);

					storage.extractEnergy(energyTransferred, false);
				}
			}
		}
	}

	@Override public boolean canConnectEnergy(ForgeDirection from) { return true; }
	@Override public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) { return 0; }
	@Override public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) { return storage.extractEnergy(maxExtract, simulate); }
	@Override public int getEnergyStored(ForgeDirection from) { return storage.getEnergyStored(); }
	@Override public int getMaxEnergyStored(ForgeDirection from) { return storage.getMaxEnergyStored(); }

	@Override public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
	}
	@Override public long getStoredEnergyQuanta() { return energyQuanta; }
	@Override public long getEnergyCapacityQuanta() { return maxPower; }
	@Override public ConnectionPriority getPriority() { return ConnectionPriority.LOW; }
	
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
		return "HEToRFConverter";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		inputQuantaPerBatch = IConfigurableMachine.grabEnergyQuanta(obj, "L:inputQuantaPerBatch", "L:HE_Used", inputQuantaPerBatch);
		outputRfPerBatch = IConfigurableMachine.grab(obj, "L:outputRfPerBatch", IConfigurableMachine.grab(obj, "L:RF_Created", outputRfPerBatch));
		inputDecay = IConfigurableMachine.grab(obj, "D:inputDecay2", inputDecay);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:inputQuantaPerBatch").value(inputQuantaPerBatch);
		writer.name("L:outputRfPerBatch").value(outputRfPerBatch);
		writer.name("D:inputDecay2").value(inputDecay);
	}
}
