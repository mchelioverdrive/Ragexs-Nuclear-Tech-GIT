package com.hbm.tileentity;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

//can be used as a soruce too since the core TE handles that anyway
public class TileEntityProxyEnergy extends TileEntityProxyBase implements IEnergyReceiverMK2 {
	
	public boolean canUpdate() {
		return false;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		
		TileEntity te = getTE();
		
		if(te instanceof IEnergyReceiverMK2) {
			((IEnergyReceiverMK2)te).setStoredEnergyQuanta(i);
		}
	}

	@Override
	public long getStoredEnergyQuanta() {
		
		TileEntity te = getTE();
		
		if(te instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)te).getStoredEnergyQuanta();
		}
		
		return 0;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		
		TileEntity te = getTE();
		
		if(te instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)te).getEnergyCapacityQuanta();
		}
		
		return 0;
	}

	@Override
	public long receiveEnergyQuanta(long power) {
		
		if(getTE() instanceof IEnergyReceiverMK2) {
			long remainder = ((IEnergyReceiverMK2)getTE()).receiveEnergyQuanta(power);
			if(remainder != power) this.markPowerNetDirty();
			return remainder;
		}
		
		return 0;
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		
		TileEntity te = getTE();
		if(te instanceof IEnergyReceiverMK2) {
			return ((IEnergyReceiverMK2)te).canConnect(dir); //for some reason two consecutive getTE calls return different things?
		}
		
		return false;
	}
}
