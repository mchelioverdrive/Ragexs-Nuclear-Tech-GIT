package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineMiniRTG extends TileEntityLoadedBase implements IEnergyProviderMK2, IInfoProviderEC {

	public long energyQuanta;
	boolean tact = false;
	
	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {

			this.setStoredEnergyQuanta(this.energyQuanta + this.getOutput());
			
			if(energyQuanta > getEnergyCapacityQuanta())
				this.setStoredEnergyQuanta(getEnergyCapacityQuanta());

			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}
		}
	}
	
	public long getOutput() {
		if(this.getBlockType() == ModBlocks.machine_powerrtg) return 2_500;
		return 700;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		if(this.getBlockType() == ModBlocks.machine_powerrtg) return 50_000;
		return 1_400;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
	}


	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, true);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.getOutput()));
	}
}
