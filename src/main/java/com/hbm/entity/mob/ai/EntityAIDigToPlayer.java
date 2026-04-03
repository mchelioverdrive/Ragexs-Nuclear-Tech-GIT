package com.hbm.entity.mob.ai;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

public class EntityAIDigToPlayer extends EntityAIBase {

	private final EntityCreature entity;
	private final double speed;
	private final double range;
	private EntityPlayer target;

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

	@Override
	public void updateTask() {

		if (target == null) return;

		entity.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);

		boolean canSee = entity.canEntityBeSeen(target);

		if (canSee) {
			entity.getNavigator().tryMoveToEntityLiving(target, speed);
			return;
		}

		// DIG MODE

		entity.getNavigator().clearPathEntity();

		double dx = target.posX - entity.posX;
		double dy = (target.posY + target.getEyeHeight()) - entity.posY;
		double dz = target.posZ - entity.posZ;

		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist == 0) return;

		dx /= dist;
		dy /= dist;
		dz /= dist;

		// ✅ carve FIRST
		carveTunnel(dx, dy, dz);

		// ✅ force movement AFTER carving (this is the key fix)
		double moveSpeed = 0.3D;

		entity.setPosition(
			entity.posX + dx * moveSpeed,
			entity.posY + dy * moveSpeed * 0.5,
			entity.posZ + dz * moveSpeed
		);

		// ❌ kill leftover physics interference
		entity.motionX = 0;
		entity.motionY = 0;
		entity.motionZ = 0;
	}

	private void carveTunnel(double dx, double dy, double dz) {

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

						if (block != Blocks.air &&
							block.getBlockHardness(entity.worldObj, x, y, z) >= 0) {

							entity.worldObj.func_147480_a(x, y, z, true);
						}
					}
				}
			}
		}
	}
}
