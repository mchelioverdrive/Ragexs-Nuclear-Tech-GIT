package com.hbm.tileentity.machine.storage;

import com.hbm.lib.Library;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineFENSU extends TileEntityMachineBattery {

	public float prevRotation = 0F;
	public float rotation = 0F;
	
	public static final long maxTransfer = 10_000_000_000_000_000L;

	@Override public long getMaxOutputQuantaPerTick() {
		int mode = this.getRelevantMode(true);
		return mode == mode_output || mode == mode_buffer ? maxTransfer : 0;
	}
	
	@Override public long getMaxInputQuantaPerTick() {
		int mode = this.getRelevantMode(true);
		return mode == mode_input || mode == mode_buffer ? maxTransfer : 0;
	}
	
	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {

			short previousMode = this.modeCache;
			int mode = this.getRelevantMode(false);
			if(previousMode != mode) this.markPowerNetworkDirty();
			
			if(this.node == null || this.node.expired) {
				
				this.node = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
				
				if(this.node == null || this.node.expired) {
					this.node = this.createNode();
					Nodespace.createNode(worldObj, this.node);
				}
			}
			
			long prevPower = this.energyQuanta;
			
			this.setStoredEnergyQuanta(Library.chargeItemsFromTE(slots, 1, energyQuanta, getEnergyCapacityQuanta()));
			
			if(mode == mode_output || mode == mode_buffer) {
				this.tryProvide(worldObj, xCoord, yCoord - 1, zCoord, ForgeDirection.DOWN);
			}
			
			byte comp = this.getComparatorPower();
			if(comp != this.lastRedstone)
				this.markDirty();
			this.lastRedstone = comp;
			
			this.updatePersistentReceiver(mode == mode_input || mode == mode_buffer);
			
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, getEnergyCapacityQuanta()));

			long avg = (energyQuanta / 2 + prevPower / 2);
			this.delta = avg - this.log[0];
			
			for(int i = 1; i < this.log.length; i++) {
				this.log[i - 1] = this.log[i];
			}
			
			this.log[19] = avg;
			if(this.energyQuanta != prevPower) this.markPowerNetworkDirty();
			
			this.networkPackNT(20);
		}
		
		if(worldObj.isRemote) {
			this.prevRotation = this.rotation;
			this.rotation += this.getSpeed();
			
			if(rotation >= 360) {
				rotation -= 360;
				prevRotation -= 360;
			}
		}
	}

	@Override
	public PowerNode createNode() {
		return new PowerNode(new BlockPos(xCoord, yCoord, zCoord)).setConnections(new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y));
	}

	@Override
	public long getPowerRemainingScaled(long i) {
		double powerScaled = (double)energyQuanta / (double)getEnergyCapacityQuanta();
		return (long)(i * powerScaled);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return Long.MAX_VALUE;
	}
	
	public float getSpeed() {
		return (float) Math.pow(Math.log(energyQuanta * 0.75 + 1) * 0.05F, 5);
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
	
	@Override
	public long receiveEnergyQuanta(long energyQuanta) {
		
		long overshoot = 0;
		
		// if power exceeds our transfer limit, truncate
		if(energyQuanta > maxTransfer) {
			overshoot += energyQuanta - maxTransfer;
			energyQuanta = maxTransfer;
		}
		
		// this check is in essence the same as the default implementation, but re-arranged to never overflow the int64 range
		// if the remaining power exceeds the power cap, truncate again
		long freespace = this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta();
		
		if(freespace < energyQuanta) {
			overshoot += energyQuanta - freespace;
			energyQuanta = freespace;
		}
		
		// what remains is sure to not exceed the transfer limit and the power cap (and therefore the int64 range)
		this.setStoredEnergyQuanta(this.getStoredEnergyQuanta() + energyQuanta);
		
		return overshoot;
	}

	@Override
	public void onChunkUnload() {
		if(!worldObj.isRemote && this.node != null && this.node.hasValidNet()) this.node.net.removeProvider(this);
		super.onChunkUnload();
	}
}
