package com.hbm.tileentity.machine;

import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineDetector extends TileEntityLoadedBase implements IEnergyReceiverMK2 {
	
	long energyQuanta;

	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {
			
			this.updateConnections();
			
			int meta = this.getBlockMetadata();
			int state = 0;
			
			if(energyQuanta > 0) {
				state = 1;
				this.setStoredEnergyQuanta(this.energyQuanta - 1);
			}
			
			if(meta != state) {
				worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, state, 3);
				this.markDirty();
			}
		}
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 5;
	}

	@Override
	public ConnectionPriority getPriority() {
		return ConnectionPriority.HIGH;
	}
}
