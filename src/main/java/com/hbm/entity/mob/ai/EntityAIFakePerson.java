package com.hbm.entity.mob.ai;

import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.world.gemini.GeminiInvasionManager;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/** Local imitation/stalking controller; native wandering runs whenever no target remains. */
public final class EntityAIFakePerson extends EntityAIBase {

	private final EntityFakePerson mob;
	private EntityLivingBase observed;
	private int pathDelay, stareTicks, burstTicks, burstCooldown, unseenTicks;
	private long nextScan;

	public EntityAIFakePerson(EntityFakePerson mob) {
		this.mob = mob;
		setMutexBits(3);
	}

	@Override
	public boolean shouldExecute() {
		if(mob.isRecovering() || mob.getHealth() <= 0 || !GeminiInvasionManager.canActivityOccur(mob.worldObj)) return false;
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
				&& GeminiInvasionManager.canActivityOccur(mob.worldObj);
	}

	@Override
	public void startExecuting() { pathDelay = stareTicks = burstTicks = burstCooldown = unseenTicks = 0; }

	@Override
	public void resetTask() {
		mob.setAttackTarget(null);
		mob.getNavigator().clearPathEntity();
		observed = null;
		nextScan = mob.worldObj.getTotalWorldTime() + 60;
	}

	@Override
	public void updateTask() {
		EntityLivingBase attacker = mob.getAttackTarget();
		if(mob.canHunt(attacker) && attacker != observed) {
			observed = attacker;
			pathDelay = unseenTicks = 0;
		}
		boolean visible = mob.canEntityBeSeen(observed);
		unseenTicks = visible ? 0 : unseenTicks + 1;
		if(pathDelay > 0) pathDelay--;
		if(burstCooldown > 0) burstCooldown--;
		if(burstTicks > 0) burstTicks--;
		double distance = mob.getDistanceSqToEntity(observed);
		if(!mob.isHostile() && visible && distance < 2.5D * 2.5D) mob.expose(observed);
		if(mob.isHostile()) {
			mob.setAttackTarget(observed);
			mob.getNavigator().setSpeed(burstTicks > 0 ? 1.3D : 0.75D);
			// Never track a hidden target's updated position through walls.
			if(!visible) return;
			mob.getLookHelper().setLookPositionWithEntity(observed, 40F, 30F);
			if(burstCooldown == 0 && distance < 100D) {
				burstTicks = 12;
				burstCooldown = 65 + mob.getRNG().nextInt(25);
				pathDelay = 0;
			}
			if(pathDelay == 0) {
				pathDelay = 10;
				mob.getNavigator().tryMoveToEntityLiving(observed, burstTicks > 0 ? 1.3D : 0.75D);
			}
			mob.getNavigator().setSpeed(burstTicks > 0 ? 1.3D : 0.75D);
			mob.attackEntityAsMob(observed);
			return;
		}
		if(!visible) { mob.getNavigator().clearPathEntity(); return; }
		mob.getLookHelper().setLookPositionWithEntity(observed, 8F, 8F);
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
}
