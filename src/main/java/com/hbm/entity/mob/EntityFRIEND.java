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
		this.tasks.addTask(1, new EntityAISwimming(this));
		this.tasks.addTask(2, new EntityAIWander(this, 1.0D));
		this.tasks.addTask(3, new EntityAILookIdle(this));
		this.tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 64.0F));

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
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(28.0D);
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

	@Override
	public void onUpdate() {
		super.onUpdate();
		if (!worldObj.isRemote) {
			//the wholesome
//
			if (this.rand.nextBoolean()) {
//
				double despawnRange = 3;
				List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(despawnRange, despawnRange, despawnRange));
				if (!players.isEmpty())
					timer++;
				if (timer > 3) {
					if (this.rand.nextBoolean()) {
						//teleport away into a safe place, where the player cannot find.
						if (!players.isEmpty()) {
							EntityPlayer target = players.get(0);
							teleportUndergroundNearPlayer(target);
						}
					} else {
						//runaway
						double runawayRange = 20;
						List<EntityPlayer> players2 = worldObj.getEntitiesWithinAABB(
							EntityPlayer.class,
							this.boundingBox.expand(runawayRange, runawayRange, runawayRange)
						);

						if (!players2.isEmpty()) {

							EntityPlayer player = players2.get(0);

							// direction away from player
							double dx = this.posX - player.posX;
							double dy = this.posY - player.posY;
							double dz = this.posZ - player.posZ;

							double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
							if (dist > 0) {
								dx /= dist;
								dy /= dist;
								dz /= dist;
							}

							// ✅ DO NOT clear navigator every tick
							// only redirect movement occasionally
							if (this.getNavigator().noPath()) {
								double targetX = this.posX + dx * 10;
								double targetY = this.posY + dy * 5;
								double targetZ = this.posZ + dz * 10;

								this.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, 1.0D);
							}
						}
						despawnTimer++;
//
						if (despawnTimer >= 5 * 20) { // 5 seconds
							//this.setDead();
							//teleport away into a safe place, where the player cannot find.
							//this.setPosition(this.posX + (rand.nextDouble() - 0.5) * 50, this.posY + (rand.nextDouble() - 0.5) * 50, this.posZ + (rand.nextDouble() - 0.5) * 50);
							if (!players2.isEmpty()) {
								EntityPlayer target = players2.get(rand.nextInt(players2.size()));
								teleportUndergroundNearPlayer(target);
								//reset despawn timer after teleport so it's not going ape shit
								despawnTimer = 0;
							}
						}
					}
				}
			} else {
				//the unwholesome
				double mogrange = 5;
				List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(mogrange, mogrange, mogrange));
				//if there is a player nearby, encircle them in a perfect circle while looking at them, then disappear
				for (EntityPlayer player : players) {
					double x = this.posX - player.posX;
					double y = this.posY - player.posY;
					double z = this.posZ - player.posZ;
					this.motionX = x * 0.1;
					this.motionY = y * 0.1;
					this.motionZ = z * 0.1;
					this.getLookHelper().setLookPosition(player.posX, player.posY + player.getEyeHeight(), player.posZ, 10.0F, 40.0F);
					//if the player is not looking at the entity, then attack them and teleport underground near them
					double lookRange = 10;
					double dx = player.posX - this.posX;
					double dy = (player.posY + player.getEyeHeight()) - (this.posY + this.height / 2);
					double dz = player.posZ - this.posZ;
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (dist < lookRange) {
						double lookX = player.getLookVec().xCoord;
						double lookY = player.getLookVec().yCoord;
						double lookZ = player.getLookVec().zCoord;
						double dot = (dx * lookX + dy * lookY + dz * lookZ) / dist; // cosine of angle between player look and direction to entity
						if (dot < 0.5) { // if player is not looking at entity (less than ~60 degrees)
							//attack player, then teleportUndergroundNearPlayer(player);
							this.attackEntityAsMob(player);
							teleportUndergroundNearPlayer(player);
						}
					}
					//attack player, then teleportUndergroundNearPlayer(player);

				}
			}
		}
	}

	@Override
	public void setHealth(float health) {
		super.setHealth(this.getMaxHealth());
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
