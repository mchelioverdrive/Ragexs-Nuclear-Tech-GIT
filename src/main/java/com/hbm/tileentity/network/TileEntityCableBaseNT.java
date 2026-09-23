package com.hbm.tileentity.network;

import api.hbm.energymk2.IEnergyConductorMK2;
import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import api.hbm.energymk2.PowerNetDiagnostics;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityCableBaseNT extends TileEntity implements IEnergyConductorMK2 {
	
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
		this.attachNode();
	}
	
	@Override
	public void updateEntity() {
		this.attachNode();
	}

	protected void attachNode() {
		if(this.worldObj == null || this.worldObj.isRemote || this.isInvalid() || !this.shouldCreateNode()) return;
		if(this.node != null && !this.node.expired) return;
		this.node = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
		if(this.node == null || this.node.expired) {
			this.node = this.createNode();
			Nodespace.createNode(worldObj, this.node);
		}
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
			if(this.node != null) {
				Nodespace.destroyNode(worldObj, xCoord, yCoord, zCoord);
			}
		}
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
