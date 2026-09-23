package com.hbm.tileentity.network;

import com.hbm.uninos.UniNodespace;

public class TileEntityCableSwitch extends TileEntityCableBaseNT {
	
	@Override
	public boolean canUpdate() {
		return super.canUpdate();
	}

	public void updateState() {
		this.blockMetadata = -1;
		if(this.getBlockMetadata() == 0 && this.node != null) {
			UniNodespace.destroyNode(this.worldObj, this.node);
			this.node = null;
		}
		this.queueNodeReconciliation();
	}
	
	@Override
	public boolean shouldCreateNode() {
		return this.getBlockMetadata() == 1;
	}
}
