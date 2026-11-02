package com.hbm.handler.disease;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.MathHelper;

/** Removable behavior, never changes taming, ownership, sitting commands, or vanilla target/anger state. */
final class RabiesBehavior extends EntityAIBase {
	private final EntityLiving host;
	private EntityLivingBase target;
	private long nextSearch, nextBite, chaseUntil;
	private boolean controlling;

	RabiesBehavior(EntityLiving host) {
		this.host = host;
		setMutexBits(3); // Own movement/look while ill; vanilla tasks are retained and resume after removal.
	}

	private Infection infection() {
		Pathogen rabies = DiseaseManager.pathogen(Rabies.ID);
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(infection == null || !rabies.enabled() || !rabies.supports(host) || !host.isEntityAlive()) return null;
		rabies.progress(host, infection, DiseaseManager.now(host));
		return infection;
	}

	@Override public boolean shouldExecute() {
		Infection infection = infection();
		return infection != null && Rabies.stage(infection).ordinal() >= Rabies.Stage.CLINICAL.ordinal();
	}

	@Override public boolean continueExecuting() { return shouldExecute(); }

	@Override public void updateTask() {
		Infection infection = infection();
		if(infection == null) return;
		long now = DiseaseManager.now(host);
		if(!Rabies.furious(infection)) {
			// Paralytic illness is quiet; no forced aggression, and late paralysis suppresses normal pursuit.
			if(Rabies.stage(infection).ordinal() >= Rabies.Stage.NEUROLOGICAL.ordinal()) {
				host.getNavigator().clearPathEntity();
				host.getMoveHelper().setMoveTo(host.posX, host.posY, host.posZ, 0D);
				controlling = true;
			}
			return;
		}
		if(target != null && (!validTarget(target) || now >= chaseUntil)) resetTask();
		if(target == null && now >= nextSearch) {
			nextSearch = now + 20;
			target = findTarget(8D);
			chaseUntil = now + 100; // Bounded five-second local pursuit, no global target search.
		}
		if(target != null) {
			controlling = true;
			// Vanilla constructs a ChunkCache covering followRange + 16 blocks and can load its chunks.
			// Check that exact footprint before both creating and following disease-owned paths.
			float range = host.getNavigator().getPathSearchRange();
			int radius = (int) (range + 16F);
			int x = MathHelper.floor_double(host.posX), z = MathHelper.floor_double(host.posZ);
			if(!Float.isNaN(range) && range >= 0F && range <= 32F && chunksLoaded(x - radius, z - radius, x + radius, z + radius)) {
				if(now % 10 == 0) host.getNavigator().tryMoveToEntityLiving(target, 1D);
			} else {
				host.getNavigator().clearPathEntity();
			}
			host.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
			tryBite(target, now);
		}
	}

	@Override public void resetTask() {
		if(controlling) host.getNavigator().clearPathEntity();
		controlling = false;
		target = null;
	}

	private boolean validTarget(EntityLivingBase candidate) {
		return candidate != host && candidate.isEntityAlive() && candidate.worldObj == host.worldObj
				&& DiseaseManager.pathogen(Rabies.ID).supports(candidate)
				&& host.getDistanceSqToEntity(candidate) <= 100D
				&& chunksLoaded(Math.min(host.posX, candidate.posX) - 2D, Math.min(host.posZ, candidate.posZ) - 2D,
						Math.max(host.posX, candidate.posX) + 2D, Math.max(host.posZ, candidate.posZ) + 2D)
				&& host.canEntityBeSeen(candidate);
	}

	private boolean chunksLoaded(double minX, double minZ, double maxX, double maxZ) {
		for(int x = MathHelper.floor_double(minX) >> 4; x <= MathHelper.floor_double(maxX) >> 4; x++) {
			for(int z = MathHelper.floor_double(minZ) >> 4; z <= MathHelper.floor_double(maxZ) >> 4; z++) {
				if(!host.worldObj.getChunkProvider().chunkExists(x, z)) return false;
			}
		}
		return true;
	}

	private EntityLivingBase findTarget(double range) {
		List<EntityLivingBase> nearby = host.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, host.boundingBox.expand(range, range, range));
		EntityLivingBase closest = null;
		double distance = range * range;
		for(EntityLivingBase candidate : nearby) {
			double d = host.getDistanceSqToEntity(candidate);
			if(d < distance && validTarget(candidate)) {
				closest = candidate;
				distance = d;
			}
		}
		return closest;
	}

	private void tryBite(EntityLivingBase victim, long now) {
		if(now < nextBite || !validTarget(victim)) return;
		// An actual close-range wounding attack is required, never ordinary AABB overlap alone.
		double reach = host instanceof EntityBat ? 1D : Math.min(2D, 0.6D + (host.width + victim.width) / 2D);
		if(host.getDistanceSq(victim.posX, victim.boundingBox.minY + victim.height / 2D, victim.posZ) > reach * reach) return;
		nextBite = now + 40;
		float damage = host instanceof EntityWolf ? (((EntityWolf) host).isTamed() ? 4F : 2F) : 1F;
		victim.attackEntityFrom(new BiteDamageSource(host), damage);
		// The common post-armor event path owns transmission; never roll again here.
	}

	/** Runs after vanilla's custom bat flight update, at most once per five loaded ticks. */
	void tickBat() {
		Infection infection = infection();
		if(infection == null || !DiseaseManager.pathogen(Rabies.ID).infectious(infection)) return;
		EntityBat bat = (EntityBat) host;
		long now = DiseaseManager.now(host);
		if(Rabies.stage(infection).ordinal() >= Rabies.Stage.NEUROLOGICAL.ordinal()) {
			bat.setIsBatHanging(false);
			bat.motionX *= 0.4D;
			bat.motionZ *= 0.4D;
			bat.motionY = Math.min(bat.motionY, -0.08D); // Loss of flight coordination, not a new ground AI.
		}
		boolean furious = Rabies.furious(infection);
		if(target != null && (!validTarget(target) || now >= chaseUntil)) target = null;
		if(target == null && now >= nextSearch) {
			nextSearch = now + 20;
			target = findTarget(furious ? 6D : 1D);
			chaseUntil = now + (furious ? 60 : 20);
		}
		if(target == null) return;
		if(furious && Rabies.stage(infection) == Rabies.Stage.CLINICAL) {
			bat.setIsBatHanging(false);
			double dx = target.posX - bat.posX, dy = target.posY + target.height * 0.5D - bat.posY, dz = target.posZ - bat.posZ;
			double length = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
			if(length > 0.01D) {
				bat.motionX = dx / length * 0.35D;
				bat.motionY = dy / length * 0.25D;
				bat.motionZ = dz / length * 0.35D;
				bat.velocityChanged = true;
			}
		}
		if(Rabies.stage(infection).ordinal() < Rabies.Stage.TERMINAL.ordinal()) tryBite(target, now);
	}

	static void updateBat(EntityBat bat) {
		DiseaseHost data = DiseaseHost.get(bat);
		if(!data.infections.containsKey(Rabies.ID)) return;
		RabiesBehavior behavior = (RabiesBehavior) data.behaviors.get(Rabies.ID);
		if(behavior == null) {
			behavior = new RabiesBehavior(bat);
			data.behaviors.put(Rabies.ID, behavior);
		}
		behavior.tickBat();
	}

}
