package com.hbm.tileentity.network;

import api.hbm.energymk2.IEnergyConductorMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import api.hbm.energymk2.PowerNetDiagnostics;
import com.hbm.uninos.IDeferredConductor;
import com.hbm.uninos.UniNodespace;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityCableBaseNT extends TileEntity implements IEnergyConductorMK2, IDeferredConductor {
	
	protected PowerNode node;
	private boolean conductorLoaded;

	@Override
	public boolean canUpdate() {
		return false;
	}

	@Override
	public void validate() {
		super.validate();
		if(this.worldObj != null && !this.worldObj.isRemote && !this.conductorLoaded) {
			this.conductorLoaded = true;
			PowerNetDiagnostics.recordChunkAttachment();
		}
		this.queueNodeReconciliation();
	}
	
	@Override
	public void updateEntity() {
		this.queueNodeReconciliation();
	}

	@Override
	public void reconcileConductorNode() {
		if(this.worldObj == null || this.worldObj.isRemote || this.isInvalid()) return;
		if(!this.shouldCreateNode()) {
			PowerNode registered = Nodespace.getNode(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
			if(registered != null) UniNodespace.destroyNode(this.worldObj, registered);
			this.node = null;
			return;
		}
		if(this.node != null && !this.node.expired) return;
		this.node = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
		if(this.node == null || this.node.expired) {
			this.node = this.createNode();
			Nodespace.createNode(worldObj, this.node);
		}
	}

	protected final void queueNodeReconciliation() {
		UniNodespace.queueConductor(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}
	
	public boolean shouldCreateNode() {
		return true;
	}
	
	public void onNodeDestroyedCallback() {
		this.node = null;
	}

	@Override
	public void invalidate() {
		this.recordUnload();
		super.invalidate();
		
		if(worldObj != null && !worldObj.isRemote) {
			if(this.node != null) UniNodespace.destroyNode(this.worldObj, this.node);
		}
		this.node = null;
	}

	@Override
	public void onChunkUnload() {
		this.recordUnload();
		super.onChunkUnload();
		// The compact logical node is world-owned and remains dormant while the chunk is unloaded.
		this.node = null;
	}

	private void recordUnload() {
		if(this.worldObj != null && !this.worldObj.isRemote && this.conductorLoaded) {
			this.conductorLoaded = false;
			PowerNetDiagnostics.recordChunkDetachment();
		}
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN;
	}
}
