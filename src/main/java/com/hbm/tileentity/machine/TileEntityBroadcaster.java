package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.lib.ModDamageSource;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.LoopedSoundPacket;
import com.hbm.tileentity.TileEntityLoadedBase;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;


public class TileEntityBroadcaster extends TileEntityLoadedBase {
	private static final int TASK_BROADCAST = 0;
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 && hasLivingTargets()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BROADCAST, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5 && hasLivingTargets()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BROADCAST, 0);
		if(cadence == 20) PacketDispatcher.wrapper.sendToAllAround(new LoopedSoundPacket(xCoord, yCoord, zCoord), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 150));
	}

	private AxisAlignedBB getBroadcastArea() {
		return AxisAlignedBB.getBoundingBox(xCoord + 0.5 - 25, yCoord + 0.5 - 25, zCoord + 0.5 - 25, xCoord + 0.5 + 25, yCoord + 0.5 + 25, zCoord + 0.5 + 25);
	}

	private boolean hasLivingTargets() {
		List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, getBroadcastArea());
		for(Entity entity : list) if(entity instanceof EntityLivingBase) return true;
		return false;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_BROADCAST || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		runBroadcastStep();
		if(!isInvalid() && hasLivingTargets()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BROADCAST, 0);
	}

	private void runBroadcastStep() {
		List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(null, getBroadcastArea());
		for(int i = 0; i < list.size(); i++) {
			if(list.get(i) instanceof EntityLivingBase) {
				EntityLivingBase e = (EntityLivingBase)list.get(i);
				double d = Math.sqrt(Math.pow(e.posX - (xCoord + 0.5), 2) + Math.pow(e.posY - (yCoord + 0.5), 2) + Math.pow(e.posZ - (zCoord + 0.5), 2));
				
				if(d <= 25) {
					if(e.getActivePotionEffect(Potion.confusion) == null || e.getActivePotionEffect(Potion.confusion).getDuration() < 100)
						e.addPotionEffect(new PotionEffect(Potion.confusion.id, 300, 0));
				}
				
				if(d <= 15) {
					double t = (15 - d) / 15 * 10;
					e.attackEntityFrom(ModDamageSource.broadcast, (float) t);
				}
			}
		}

	}

	@Override
	public void updateEntity() {
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return 65536.0D;
	}

}
