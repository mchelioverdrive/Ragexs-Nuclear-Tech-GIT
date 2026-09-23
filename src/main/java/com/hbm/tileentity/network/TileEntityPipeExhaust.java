package com.hbm.tileentity.network;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.uninos.UniNodespace;
import com.hbm.uninos.IDeferredConductor;

import api.hbm.fluid.IFluidConductor;
import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.FluidNode;
import api.hbm.fluidmk2.IFluidPipeMK2;
import api.hbm.energymk2.PowerNetDiagnostics;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/** Non-ticking multi-fluid exhaust conductor backed by three compact UNINOS nodes. */
public class TileEntityPipeExhaust extends TileEntity implements IFluidConductor, IFluidPipeMK2, IDeferredConductor {

	private final FluidNode[] nodes = new FluidNode[3];
	private boolean loaded;

	public FluidType[] getSmokes() { return new FluidType[] {Fluids.SMOKE, Fluids.SMOKE_LEADED, Fluids.SMOKE_POISON}; }

	@Override
	public boolean canUpdate() { return false; }

	@Override
	public void validate() {
		super.validate();
		if(this.worldObj != null && !this.worldObj.isRemote && !this.loaded) PowerNetDiagnostics.recordChunkAttachment();
		this.loaded = true;
		UniNodespace.queueConductor(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public void updateEntity() { UniNodespace.queueConductor(this.worldObj, this.xCoord, this.yCoord, this.zCoord); }

	@Override
	public void reconcileConductorNode() {
		if(this.worldObj == null || this.worldObj.isRemote || this.isInvalid()) return;
		FluidType[] types = this.getSmokes();
		for(int i = 0; i < types.length; i++) {
			if(this.nodes[i] != null && !this.nodes[i].expired) continue;
			this.nodes[i] = (FluidNode) UniNodespace.getNode(this.worldObj, this.xCoord, this.yCoord, this.zCoord, types[i].getNetworkProvider());
			if(this.nodes[i] == null || this.nodes[i].expired) {
				this.nodes[i] = this.createNode(types[i]);
				UniNodespace.createNode(this.worldObj, this.nodes[i]);
			}
		}
	}

	@Override
	public FluidNetMK2 getFluidNet(FluidType type) {
		FluidType[] types = this.getSmokes();
		for(int i = 0; i < types.length; i++) if(type == types[i] && this.nodes[i] != null && this.nodes[i].hasValidNet()) return this.nodes[i].net;
		return null;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection direction) {
		return direction != ForgeDirection.UNKNOWN && (type == Fluids.SMOKE || type == Fluids.SMOKE_LEADED || type == Fluids.SMOKE_POISON);
	}

	@Override
	public long getDemand(FluidType type, int pressure) { return 0; }

	@Override
	public void invalidate() {
		this.recordUnload();
		if(this.worldObj != null && !this.worldObj.isRemote) for(FluidNode node : this.nodes) if(node != null) UniNodespace.destroyNode(this.worldObj, node);
		for(int i = 0; i < this.nodes.length; i++) this.nodes[i] = null;
		super.invalidate();
	}

	@Override
	public boolean isLoaded() { return this.loaded; }

	@Override
	public void onChunkUnload() {
		this.recordUnload();
		super.onChunkUnload();
		for(int i = 0; i < this.nodes.length; i++) this.nodes[i] = null;
	}

	private void recordUnload() {
		if(this.worldObj != null && !this.worldObj.isRemote && this.loaded) PowerNetDiagnostics.recordChunkDetachment();
		this.loaded = false;
	}
}
