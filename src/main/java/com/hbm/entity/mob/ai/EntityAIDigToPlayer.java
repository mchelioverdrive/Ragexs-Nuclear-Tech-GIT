package com.hbm.entity.mob.ai;

import com.hbm.entity.mob.EntityFRIEND;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;

public class EntityAIDigToPlayer extends EntityAIBase {

	private final EntityCreature entity;
	private final double speed;
	private final double range;
	private EntityPlayer target;

	private int attackCooldown = 0;
	private int teleportDelay = 0;

	public EntityAIDigToPlayer(EntityCreature entity, double speed, double range) {
		this.entity = entity;
		this.speed = speed;
		this.range = range;
		this.setMutexBits(1);
	}

	@Override
	public boolean shouldExecute() {

		if (entity.worldObj.isRemote) return false;

		EntityPlayer player = entity.worldObj.getClosestPlayerToEntity(entity, range);
		if (player == null || player.isDead) return false;

		this.target = player;
		return true;
	}

	@Override
	public boolean continueExecuting() {
		return target != null
			&& !target.isDead
			&& entity.getDistanceSqToEntity(target) <= range * range;
	}

	private int teleportCooldown = 0;

	@Override
	public void updateTask() {

		if (target == null) return;

		entity.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);

		boolean hasLOS = entity.canEntityBeSeen(target);
		boolean playerLooking = isPlayerLookingAtEntity(target);

		double distSq = entity.getDistanceSqToEntity(target);

		applyEffects(target, distSq);

		// FREEZE when looked at
		if (hasLOS && playerLooking) {
			entity.getNavigator().clearPathEntity();
			entity.motionX = 0;
			//entity.motionY = 0;
			//don't do that, it causes weird vertical stuttering when the player looks away and it tries to move again
			entity.motionZ = 0;
			teleportCooldown--;
			return;
		}

		if (!playerLooking && entity.getRNG().nextInt(5) == 0) {
			//no line of sight or player not looking - DIG and TELEPORT
			EntityPlayer player = (EntityPlayer) target;

			// 20% chance + cooldown check
			if (teleportCooldown <= 0 && entity.getRNG().nextInt(5) == 0) {

				if (!entity.worldObj.isRemote) {
					if (((EntityFRIEND) entity).teleportUndergroundNearPlayer(player)) {
						teleportCooldown = 60; // 3 seconds (tweak as needed)
					}
				}
			}
		}


		// MOVE toward player
		entity.getNavigator().clearPathEntity();

		double dx = target.posX - entity.posX;
		double dy = (target.posY + target.getEyeHeight()) - entity.posY;
		double dz = target.posZ - entity.posZ;

		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist == 0) return;

		dx /= dist;
		dy /= dist;
		dz /= dist;

		if (!hasLOS) {
			carveTunnel(dx, dy, dz);
		}

		double moveSpeed = 0.45D;

		entity.setPosition(
			entity.posX + dx * moveSpeed,
			entity.posY + dy * moveSpeed * 0.5,
			entity.posZ + dz * moveSpeed
		);

		// ✅ ATTACK SYSTEM (FIXED)
		if (distSq < 4.0D) {

			if (!playerLooking) {



				if (attackCooldown > 0) attackCooldown--;

				if (attackCooldown <= 0) {
					entity.attackEntityAsMob(target);


					//boolean hit = entity.attackEntityAsMob(target);
					attackCooldown = 20;

					//if (hit && target instanceof EntityPlayer) {
					//
					//
					//}

					teleportDelay = 30 + entity.getRNG().nextInt(20);
				}
			}

			if (teleportDelay > 0) {
				teleportDelay--;

				if (teleportDelay == 0) {
					((EntityFRIEND)entity).teleportUndergroundNearPlayer(target);
				}
			}
		}
	}

	private boolean isPlayerLookingAtEntity(EntityPlayer player) {

		double dx = entity.posX - player.posX;
		double dy = (entity.posY + entity.height / 2.0) - (player.posY + player.getEyeHeight());
		double dz = entity.posZ - player.posZ;

		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist == 0) return true;

		dx /= dist;
		dy /= dist;
		dz /= dist;

		double lookX = player.getLookVec().xCoord;
		double lookY = player.getLookVec().yCoord;
		double lookZ = player.getLookVec().zCoord;

		double dot = dx * lookX + dy * lookY + dz * lookZ;

		return dot > 0.85D; // stricter
	}

	private void applyEffects(EntityPlayer player, double distSq) {

		if (distSq < 36) {
			player.addPotionEffect(new PotionEffect(Potion.confusion.id, 40, 0));
		}

		if (distSq < 144) {
			player.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0));
		}

		if (distSq < 900) {
			player.addPotionEffect(new PotionEffect(Potion.hunger.id, 60, 0));
		}
	}

	private final Map<String, Float> breakProgress = new HashMap<>();

	private boolean canMoveForward(double dx, double dy, double dz) {

		int bx = (int)Math.floor(entity.posX + dx);
		int by = (int)Math.floor(entity.posY + dy);
		int bz = (int)Math.floor(entity.posZ + dz);

		for (int xOff = -1; xOff <= 1; xOff++) {
			for (int zOff = -1; zOff <= 1; zOff++) {
				for (int yOff = 0; yOff < 2; yOff++) {

					int x = bx + xOff;
					int y = by + yOff;
					int z = bz + zOff;

					Block block = entity.worldObj.getBlock(x, y, z);

					if (block != Blocks.air &&
						block.getMaterial().blocksMovement()) {

						return false; // blocked
					}
				}
			}
		}

		return true; // clear
	}

	private void carveTunnel(double dx, double dy, double dz) {

		//todo cooldown on this and/or limit how many blocks it can break per second
		//also maybe add some randomness to the tunnel shape instead of a straight line?

		int steps = 2;

		for (int i = 0; i <= steps; i++) {

			int bx = (int)Math.floor(entity.posX + dx * i);
			int by = (int)Math.floor(entity.posY + dy * i);
			int bz = (int)Math.floor(entity.posZ + dz * i);

			for (int xOff = -1; xOff <= 1; xOff++) {
				for (int zOff = -1; zOff <= 1; zOff++) {
					for (int yOff = 0; yOff < 2; yOff++) {

						int x = bx + xOff;
						int y = by + yOff;
						int z = bz + zOff;

						Block block = entity.worldObj.getBlock(x, y, z);

						// FIXED condition
						if (block != Blocks.air &&
							block != Blocks.bedrock &&
							block.getBlockHardness(entity.worldObj, x, y, z) >= 0) {

							String key = x + "," + y + "," + z;

							float hardness = block.getBlockHardness(entity.worldObj, x, y, z);
							float progress = breakProgress.containsKey(key) ? breakProgress.get(key) : 0F;

							// Dig speed (tune this)
							float speed = 0.02F / (hardness + 0.1F);

							progress += speed;

							if (progress >= 1.0F) {
								entity.worldObj.func_147480_a(x, y, z, true);
								breakProgress.remove(key);

								// Clear crack animation
								entity.worldObj.destroyBlockInWorldPartially(entity.getEntityId(), x, y, z, -1);
							} else {
								breakProgress.put(key, progress);

								// Crack animation (0–9)
								int stage = (int)(progress * 10F);
								entity.worldObj.destroyBlockInWorldPartially(entity.getEntityId(), x, y, z, stage);
							}
						}
					}
				}
			}
		}
	}
}
