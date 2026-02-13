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
	public boolean continueExecuting() {
		return target != null && !target.isDead
			&& entity.getDistanceSqToEntity(target) <= range * range;
	}

	@Override
	public boolean shouldExecute() {

		EntityPlayer player = entity.worldObj.getClosestVulnerablePlayerToEntity(entity, range);

		if (player == null)
			return false;

		this.target = player;
		return true;
	}

	@Override
	public void updateTask() {

		System.out.println("DIGGING TOWARD PLAYER");


		if (target == null)
			return;

		entity.getNavigator().tryMoveToEntityLiving(target, speed);

		breakBlocksTowardTarget();
	}

	private void breakBlocksTowardTarget() {

		int x = (int)Math.floor(entity.posX);
		int y = (int)Math.floor(entity.posY);
		int z = (int)Math.floor(entity.posZ);

		// Block in front of entity
		int dx = (int)Math.signum(target.posX - entity.posX);
		int dz = (int)Math.signum(target.posZ - entity.posZ);

		int bx = x + dx;
		int by = y;
		int bz = z + dz;

		Block block = entity.worldObj.getBlock(bx, by, bz);

		if (block != Blocks.air && block.getBlockHardness(entity.worldObj, bx, by, bz) >= 0) {
			entity.worldObj.func_147480_a(bx, by, bz, true); // destroy block
		}
	}
}

