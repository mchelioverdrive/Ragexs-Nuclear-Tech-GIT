package com.hbm.entity.mob.ai;

import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.world.gemini.GeminiSpawning;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/** Local imitation/stalking controller; native wandering runs whenever no target remains. */
public final class EntityAIFakePerson extends EntityAIBase {

	private final EntityFakePerson mob;
	private EntityLivingBase observed;
	private EntityLivingBase movementObserved;
	private double previousObservedX, previousObservedZ;
	private int retreatTicks;
	private EntityPlayer imitatedPlayer;
	private double previousPlayerY;
	private int previousSwingProgress;
	private boolean playerWasSwinging, playerWasGrounded;
	private int pathDelay, stareTicks, burstTicks, burstCooldown, unseenTicks;
	private long nextScan;

	public EntityAIFakePerson(EntityFakePerson mob) {
		this.mob = mob;
		setMutexBits(3);
	}

	@Override
	public boolean shouldExecute() {
		if(mob.isRecovering() || mob.getHealth() <= 0 || !GeminiSpawning.canAct(mob, 1)) return false;
		EntityLivingBase attacker = mob.getAttackTarget();
		if(mob.canHunt(attacker) && mob.getDistanceSqToEntity(attacker) <= 1024D) observed = attacker;
		else if(mob.worldObj.getTotalWorldTime() >= nextScan) {
			nextScan = mob.worldObj.getTotalWorldTime() + 20;
			observed = null;
			double best = 24D * 24D;
			for(Object entry : mob.worldObj.playerEntities) {
				EntityPlayer player = (EntityPlayer) entry;
				double distance = mob.getDistanceSqToEntity(player);
				if(distance < best && mob.canHunt(player) && mob.canEntityBeSeen(player)) {
					observed = player;
					best = distance;
				}
			}
		}
		return mob.canHunt(observed) && mob.getDistanceSqToEntity(observed) <= 1024D;
	}

	@Override
	public boolean continueExecuting() {
		return !mob.isRecovering() && mob.getHealth() > 0 && mob.canHunt(observed)
				&& mob.getDistanceSqToEntity(observed) <= 1024D && unseenTicks < 200
				&& GeminiSpawning.canAct(mob, 1);
	}

	@Override
	public void startExecuting() { pathDelay = stareTicks = burstTicks = burstCooldown = unseenTicks = 0; }

	@Override
	public void resetTask() {
		mob.setAttackTarget(null);
		mob.getNavigator().clearPathEntity();
		mob.setSneaking(false);
		if(mob.isSprinting()) mob.setSprinting(false);
		observed = null;
		movementObserved = null;
		retreatTicks = 0;
		imitatedPlayer = null;
		nextScan = mob.worldObj.getTotalWorldTime() + 60;
	}

	@Override
	public void updateTask() {
		if(mob.isTyping()) return;
		mob.setSneaking(false);
		EntityLivingBase attacker = mob.getAttackTarget();
		if(mob.canHunt(attacker) && attacker != observed) {
			observed = attacker;
			pathDelay = unseenTicks = 0;
		}
		boolean visible = mob.canEntityBeSeen(observed);
		if(visible && mob.isPlayerMimic() && observed instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) observed;
			boolean samePlayer = imitatedPlayer == player;
			if(player.isSwingInProgress && (!samePlayer || !playerWasSwinging || player.swingProgressInt < previousSwingProgress))
				mob.swingItem();
			// Copy a single takeoff, using actual position changes rather than packet-stale motionY.
			if(samePlayer && playerWasGrounded && !player.onGround && player.posY - previousPlayerY > 0.1D
					&& !mob.getNavigator().noPath() && mob.onGround) mob.getJumpHelper().setJumping();
			imitatedPlayer = player;
			previousPlayerY = player.posY;
			previousSwingProgress = player.swingProgressInt;
			playerWasSwinging = player.isSwingInProgress;
			playerWasGrounded = player.onGround;
		} else imitatedPlayer = null;
		unseenTicks = visible ? 0 : unseenTicks + 1;
		if(pathDelay > 0) pathDelay--;
		if(burstCooldown > 0) burstCooldown--;
		if(burstTicks > 0) burstTicks--;
		double distance = mob.getDistanceSqToEntity(observed);
		if(!mob.isHostile() && visible && distance < 2.5D * 2.5D) mob.expose(observed);
		if(mob.isHostile()) {
			mob.setAttackTarget(observed);
			// Native player sprinting supplies the 1.3 attribute multiplier throughout
			// pursuit. Villager mimics retain their original short bursts.
			if(mob.isPlayerMimic() && !mob.isSprinting()) mob.setSprinting(true);
			mob.getNavigator().setSpeed(mob.isPlayerMimic() ? 1D : burstTicks > 0 ? 1.3D : 0.75D);
			// Never track a hidden target's updated position through walls.
			if(!visible) return;
			mob.getLookHelper().setLookPositionWithEntity(observed, 40F, 30F);
			if(!mob.isPlayerMimic() && burstCooldown == 0 && distance < 100D) {
				burstTicks = 12;
				burstCooldown = 65 + mob.getRNG().nextInt(25);
				pathDelay = 0;
			}
			double speed = mob.isPlayerMimic() ? 1D : burstTicks > 0 ? 1.3D : 0.75D;
			if(pathDelay == 0) {
				pathDelay = 10;
				mob.getNavigator().tryMoveToEntityLiving(observed, speed);
			}
			mob.getNavigator().setSpeed(speed);
			mob.attackEntityAsMob(observed);
			return;
		}
		if(!visible) {
			mob.getNavigator().clearPathEntity();
			movementObserved = null;
			retreatTicks = 0;
			return;
		}
		mob.getLookHelper().setLookPositionWithEntity(observed, 8F, 8F);
		if(mob.isPlayerMimic()) {
			mob.setSneaking(observed instanceof EntityPlayer && !isPlayerMovingAway());
			double speed = mob.isSneaking() ? 0.18D : 0.6D;
			if(pathDelay == 0) {
				pathDelay = 20;
				mob.getNavigator().tryMoveToEntityLiving(observed, speed);
			}
			// Change pace immediately even while reusing the current path.
			mob.getNavigator().setSpeed(speed);
			return;
		}
		if(stareTicks > 0) {
			stareTicks--;
			mob.getNavigator().clearPathEntity();
			return;
		}
		if(mob.getRNG().nextInt(180) == 0) { stareTicks = 40 + mob.getRNG().nextInt(60); return; }
		if(distance <= 64D) { mob.getNavigator().clearPathEntity(); return; }
		if(pathDelay == 0) {
			pathDelay = 20;
			double dx = mob.posX - observed.posX, dz = mob.posZ - observed.posZ;
			double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
			mob.getNavigator().tryMoveToXYZ(observed.posX + dx / length * 8D, observed.posY,
					observed.posZ + dz / length * 8D, 0.6D);
		}
	}

	private boolean isPlayerMovingAway() {
		if(movementObserved != observed) {
			movementObserved = observed;
			retreatTicks = 0;
		} else {
			// Sample actual server positions: player motion fields need not reflect walking packets.
			double dx = observed.posX - previousObservedX, dz = observed.posZ - previousObservedZ;
			double awayX = observed.posX - mob.posX, awayZ = observed.posZ - mob.posZ;
			double distance = Math.sqrt(awayX * awayX + awayZ * awayZ);
			if(distance > 0 && dx * awayX + dz * awayZ > 0.02D * distance) retreatTicks = 5;
			else if(retreatTicks > 0) retreatTicks--;
		}
		previousObservedX = observed.posX;
		previousObservedZ = observed.posZ;
		// Bridge brief gaps between movement packets without flickering the crouch pose.
		return retreatTicks > 0;
	}
}
