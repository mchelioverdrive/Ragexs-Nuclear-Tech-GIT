package com.hbm.entity.mob;

import com.hbm.dim.laythe.WorldProviderLaythe;
import com.hbm.entity.mob.ai.EntityAIDigToPlayer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.List;

public class EntityFRIEND extends EntityCreature {

	public EntityFRIEND(World world) {
		super(world);

		this.tasks.addTask(0, new EntityAIDigToPlayer(this, 0.1D, 160.0D));
		this.tasks.addTask(1, new EntityAIWatchClosest(this, EntityPlayer.class, 64.0F));
		this.tasks.addTask(2, new EntityAISwimming(this));
		this.tasks.addTask(3, new EntityAIWander(this, 1.0D));
		this.tasks.addTask(4, new EntityAILookIdle(this));

		this.targetTasks.addTask(1, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));

		this.renderDistanceWeight *= 10;
		this.setSize(0.6F, 1.8F);
	}

	//	@Override
//	public boolean getCanSpawnHere() {
//
//		// Only in Laythe (europa)
//		if (!(this.worldObj.provider instanceof WorldProviderLaythe)) return false;
//
//		// Only below Y = 50
//		if (this.posY >= 50) return false;
//
//		// 1% chance
//		//if (this.rand.nextInt(100) != 0) return false;
//
//		// Check for other FRIEND entities
//		//double range = 64;
//		//List<EntityFRIEND> friends = this.worldObj.getEntitiesWithinAABB(
//		//	EntityFRIEND.class,
//		//	this.boundingBox.expand(range, range, range)
//		//);
////
//		//for (EntityFRIEND f : friends) {
//		//	if (f != this) return false;
//		//}
//		//done in ntmworldgenerator
//
//		return super.getCanSpawnHere();
//	}

	@Override
	public boolean getCanSpawnHere() {
		return true;
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(50.0D);
		this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
	}

	@Override
	protected boolean isAIEnabled() {
		return true;
	}

	// ❌ IMPORTANT: do NOTHING here anymore
	@Override
	public void onUpdate() {
		super.onUpdate();
	}

	// ✅ FORCE DAMAGE (fixes “no damage” issue in 1.7 sometimes)
	@Override
	public boolean attackEntityAsMob(Entity entity) {
		return entity.attackEntityFrom(DamageSource.causeMobDamage(this), 6.0F);
	}

	public boolean teleportUndergroundNearPlayer(EntityPlayer player) {

		int range = 32;

		for (int i = 0; i < 32; i++) {

			int x = (int)(player.posX + (rand.nextDouble() - 0.5) * range);
			int z = (int)(player.posZ + (rand.nextDouble() - 0.5) * range);

			int topY = worldObj.getTopSolidOrLiquidBlock(x, z);
			int y = topY - (10 + rand.nextInt(20));
			if (y < 5) y = 5;

			if (isSafeTeleportSpot(x, y, z)) {
				this.setPosition(x + 0.5, y, z + 0.5);
				return true;
			}

			if (placeUnderground(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	private boolean isSafeTeleportSpot(int x, int y, int z) {

		if(y <= 0 || y >= worldObj.getHeight()) return false;

		if(!worldObj.isAirBlock(x, y, z)) return false;
		if(!worldObj.isAirBlock(x, y + 1, z)) return false;

		if(!worldObj.getBlock(x, y - 1, z).getMaterial().isSolid()) return false;

		return true;
	}

	private boolean placeUnderground(int x, int y, int z) {

		if (y <= 1 || y >= worldObj.getHeight() - 2) return false;

		if (!worldObj.getBlock(x, y - 1, z).getMaterial().isSolid()) return false;

		if (!worldObj.isAirBlock(x, y, z)) {
			worldObj.setBlockToAir(x, y, z);
		}
		if (!worldObj.isAirBlock(x, y + 1, z)) {
			worldObj.setBlockToAir(x, y + 1, z);
		}

		this.setPosition(x + 0.5, y, z + 0.5);
		return true;
	}

	@Override
	public void setHealth(float health) {
		super.setHealth(50);
	}

	@Override
	public boolean isEntityInvulnerable() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return distance < 500000;
	}
}
