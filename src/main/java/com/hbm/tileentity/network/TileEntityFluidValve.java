package com.hbm.tileentity.network;

import com.hbm.uninos.UniNodespace;
import net.minecraft.block.Block;
import net.minecraft.world.World;

public class TileEntityFluidValve extends TileEntityPipeBaseNT {
	
	@Override
	protected boolean shouldCreateNode() {
		return this.worldObj != null && this.getBlockMetadata() == 1 && !this.isInvalid();
	}

	public void updateState() {
		
		this.blockMetadata = -1; // delete cache
		
		if(this.getBlockMetadata() == 0 && this.node != null) {
			UniNodespace.destroyNode(this.worldObj, this.node);
			this.node = null;
		}
		this.queueNodeReconciliation();
	}
	
	@Override
	public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y, int z) {
		return oldBlock != newBlock;
	}
}
