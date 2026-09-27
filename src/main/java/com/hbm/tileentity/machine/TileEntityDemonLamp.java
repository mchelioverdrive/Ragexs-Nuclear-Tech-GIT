package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.config.RadiationConfig;
import com.hbm.hazard.type.HazardTypeNeutron;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.hbm.tileentity.TileEntityLoadedBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TileEntityDemonLamp extends TileEntityLoadedBase {
	private static final int TASK_RADIATE = 0;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 && hasNearbyLivingEntities()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_RADIATE, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		if(hasNearbyLivingEntities()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_RADIATE, 0);
		else cancelMachineTransition(TASK_RADIATE, 0);
	}

	private boolean hasNearbyLivingEntities() {
		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5).expand(25D, 25D, 25D);
		return !worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box).isEmpty();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_RADIATE || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		if(radiate(worldObj, xCoord, yCoord, zCoord) && !isInvalid()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_RADIATE, 0);
	}

	@Override
	public void updateEntity() { }
	
	private boolean radiate(World world, int x, int y, int z) {
		
		float rads = 100000F;
		double range = 25D;
		
		
		List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(x + 0.5, y + 0.5, z + 0.5, x + 0.5, y + 0.5, z + 0.5).expand(range, range, range));
		
		for(EntityLivingBase e : entities) {
			
			Vec3 vec = Vec3.createVectorHelper(e.posX - (x + 0.5), (e.posY + e.getEyeHeight()) - (y + 0.5), e.posZ - (z + 0.5));
			double len = vec.lengthVector();
			vec = vec.normalize();
			
			float res = 0;
			
			for(int i = 1; i < len; i++) {

				int ix = (int)Math.floor(x + 0.5 + vec.xCoord * i);
				int iy = (int)Math.floor(y + 0.5 + vec.yCoord * i);
				int iz = (int)Math.floor(z + 0.5 + vec.zCoord * i);
				
				res += world.getBlock(ix, iy, iz).getExplosionResistance(null);
			}
			
			if(res < 1)
				res = 1;
			
			float eRads = rads;
			eRads /= (float)res;
			eRads /= (float)(len * len);
			
			ContaminationUtil.contaminate(e, HazardType.RADIATION, ContaminationType.CREATIVE, eRads);
			ContaminationUtil.contaminate(e, HazardType.NEUTRON, ContaminationType.CREATIVE, eRads);
			if(e instanceof EntityPlayer && !RadiationConfig.disableNeutron) {
				EntityPlayer player = (EntityPlayer) e;
				for(int i = 0; i < player.inventory.mainInventory.length; i++) {
					HazardTypeNeutron.apply(player.inventory.getStackInSlot(i), eRads);
				}
				for(int i = 0; i < player.inventory.armorInventory.length; i++) {
					HazardTypeNeutron.apply(player.inventory.armorItemInSlot(i), eRads);
				}
			}
			
			if(len < 2) {
				e.attackEntityFrom(DamageSource.inFire, 100);
			}
		}
		return !entities.isEmpty();
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
}
