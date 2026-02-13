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

		if (entity.worldObj.isRemote) return false;


		target = (EntityPlayer) entity.getAttackTarget();
		if (target == null) return false;

		if (target == null) {
			System.out.println("No player found");
			return false;
		}

		System.out.println("Player found: " + target.getCommandSenderName());
		this.target = target;
		return true;
	}


	@Override
	public void updateTask() {

		entity.setAttackTarget(target);
		entity.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);

		//System.out.println("DIGGING TOWARD PLAYER");

		boolean pathing = entity.getNavigator().tryMoveToEntityLiving(target, speed);
		//System.out.println("Pathing result: " + pathing);


		if (target == null)
			return;

		//entity.getNavigator().tryMoveToEntityLiving(target, speed);
		entity.motionX = (target.posX - entity.posX) * 0.01;
		entity.motionZ = (target.posZ - entity.posZ) * 0.01;

		breakBlocksTowardTarget();
	}

	private void breakBlocksTowardTarget() {

		int dx = (int)Math.signum(target.posX - entity.posX);
		int dy = (int)Math.signum(target.posY - entity.posY);
		int dz = (int)Math.signum(target.posZ - entity.posZ);

		int bx = (int)Math.floor(entity.posX + dx);
		int by = (int)Math.floor(entity.posY + dy);
		int bz = (int)Math.floor(entity.posZ + dz);

		for (int yOffset = 0; yOffset < 2; yOffset++) { // break 2 blocks tall
			Block block = entity.worldObj.getBlock(bx, by + yOffset, bz);

			if (block != Blocks.air && block.getBlockHardness(entity.worldObj, bx, by + yOffset, bz) >= 0) {
				entity.worldObj.func_147480_a(bx, by + yOffset, bz, true);
			}
		}
	}

}

