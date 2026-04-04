package com.hbm.entity.mob;

import com.hbm.dim.laythe.WorldProviderLaythe;
import com.hbm.entity.mob.ai.EntityAIDigToPlayer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

public class EntityFRIEND extends EntityCreature {

	private int despawnTimer = 0;

	private int timer = 0;
	//let him in.
	//(*is actually a god awful thing that will cause you psychological distress)
	//do not go to space, idiot.

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

	//temp
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

	private boolean teleportUndergroundNearPlayer(EntityPlayer player) {

		int range = 32;

		for (int i = 0; i < 32; i++) {

			int x = (int)(player.posX + (rand.nextDouble() - 0.5) * range);
			int z = (int)(player.posZ + (rand.nextDouble() - 0.5) * range);

			int topY = worldObj.getTopSolidOrLiquidBlock(x, z);

			int y = topY - (10 + rand.nextInt(20));
			if (y < 5) y = 5;

			// ✅ FIRST: try natural safe spot
			if (isSafeTeleportSpot(x, y, z)) {
				this.setPosition(x + 0.5, y, z + 0.5);
				return true;
			}

			// ✅ SECOND: fallback → carve space
			if (placeUnderground(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	private boolean isSafeTeleportSpot(int x, int y, int z) {

		if(y <= 0 || y >= worldObj.getHeight()) return false;

		// feet + head must be air
		if(!worldObj.isAirBlock(x, y, z)) return false;
		if(!worldObj.isAirBlock(x, y + 1, z)) return false;

		// must stand on something solid
		if(!worldObj.getBlock(x, y - 1, z).getMaterial().isSolid()) return false;

		return true;
	}

	private boolean placeUnderground(int x, int y, int z) {

		if (y <= 1 || y >= worldObj.getHeight() - 2) return false;

		// must have ground BELOW first
		if (!worldObj.getBlock(x, y - 1, z).getMaterial().isSolid()) return false;

		// carve space AFTER validation
		if (!worldObj.isAirBlock(x, y, z)) {
			worldObj.setBlockToAir(x, y, z);
		}
		if (!worldObj.isAirBlock(x, y + 1, z)) {
			worldObj.setBlockToAir(x, y + 1, z);
		}

		this.setPosition(x + 0.5, y, z + 0.5);
		return true;
	}

	private boolean digMode = true;
	private int modeCooldown = 0;

	private EntityPlayer getClosestPlayer(double range) {
		List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(
			EntityPlayer.class,
			this.boundingBox.expand(range, range, range)
		);

		EntityPlayer closest = null;
		double closestDist = Double.MAX_VALUE;

		for (EntityPlayer player : players) {
			double dist = this.getDistanceSqToEntity(player);
			if (dist < closestDist) {
				closestDist = dist;
				closest = player;
			}
		}

		return closest;
	}

//	@Override
//	public void onUpdate() {
//		super.onUpdate();
//
//		if (worldObj.isRemote) return;
//
//		EntityPlayer target = getClosestPlayer(32.0D);
//		if (target == null) return;
//
//		boolean canSeeTarget = this.canEntityBeSeen(target);
//
//		if (modeCooldown <= 0) {
//			boolean newDigMode = !canSeeTarget;
//
//			if (newDigMode != digMode) {
//				digMode = newDigMode;
//				modeCooldown = 20; // 1 second of stability
//				this.getNavigator().clearPathEntity();
//			}
//		} else {
//			modeCooldown--;
//		}
//
//		if (digMode) {
//			// dig mode: target player, let AI do the work
//			this.setAttackTarget(target);
//			this.getNavigator().tryMoveToEntityLiving(target, 1.0D);
//			return;
//		}
//
//		// visible mode: react to being seen
//		this.setAttackTarget(null);
//
//		double dx = this.posX - target.posX;
//		double dy = this.posY - target.posY;
//		double dz = this.posZ - target.posZ;
//
//		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
//		if (dist > 0.0D) {
//			dx /= dist;
//			dy /= dist;
//			dz /= dist;
//		}
//
//		this.motionX += dx * 0.08D;
//		this.motionY += dy * 0.02D;
//		this.motionZ += dz * 0.08D;
//
//		this.getLookHelper().setLookPosition(
//			target.posX,
//			target.posY + target.getEyeHeight(),
//			target.posZ,
//			10.0F,
//			40.0F
//		);
//
//		if (target.getDistanceSqToEntity(this) < 100.0D) {
//			this.attackEntityAsMob(target);
//			teleportUndergroundNearPlayer(target);
//		}
//	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		if (worldObj.isRemote) return;

		EntityPlayer player = getClosestPlayer(32.0D);

		if (player == null) return;

		double distSq = this.getDistanceSqToEntity(player);

		// 👻 vanish instead of dying
		if (distSq < 9.0D) { // 3 blocks
			if (this.rand.nextInt(40) == 0) { // not instant
				teleportUndergroundNearPlayer(player);
			}
		}
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

	//same attrib as ghost, will have a different model and will appear in space when you are ALONE.


}
