package com.hbm.handler.disease;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAISit;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;

/** Removable server-owned movement/look control; never changes ownership, taming or vanilla attack targets. */
final class RabiesBehavior extends EntityAIBase {
	private static final Field BAT_DESTINATION = ReflectionHelper.findField(EntityBat.class, "spawnPosition", "field_82237_a");
	private static final Field SIT_COMMAND = ReflectionHelper.findField(EntityAISit.class, "isSitting", "field_75271_b");
	private static final String SAVED_SIT = "diseaseSittingCommand";
	private final EntityLiving host;
	private final Map<EntityAIBase, Integer> changedMutexes = new IdentityHashMap<EntityAIBase, Integer>();
	private EntityLivingBase target;
	private int nextSearch, nextPath, lastExecution = -1;
	private long nextBite;
	private boolean controlling, flightDirected, sittingControlled;
	private String status = "awaiting behavior execution";

	RabiesBehavior(EntityLiving host, Infection infection) {
		this.host = host;
		setMutexBits(3); // Movement/look only: swimming (4) and door handling (0) must still run.
		restoreSitting(host, infection); // A reload may have saved the temporary standing pose.
		for(Object entry : host.tasks.taskEntries) {
			EntityAIBase action = ((EntityAITaskEntry) entry).action;
			// Vanilla's baby follow task writes navigation without declaring a movement mutex.
			if(action instanceof EntityAIFollowParent) {
				changedMutexes.put(action, action.getMutexBits());
				action.setMutexBits(action.getMutexBits() | 1);
			}
		}
	}

	private Infection infection() {
		Pathogen rabies = DiseaseManager.pathogen(Rabies.ID);
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(infection == null || !rabies.enabled() || !rabies.supports(host) || !host.isEntityAlive()) return null;
		rabies.progress(host, infection, DiseaseManager.now(host));
		return infection;
	}

	private static boolean controlsMovement(Infection infection) {
		return Rabies.furious(infection) || Rabies.stage(infection).ordinal() >= Rabies.Stage.NEUROLOGICAL.ordinal();
	}

	/** LivingUpdateEvent precedes vanilla's movement-blocked check, including horse grazing/rearing. */
	private void prepare(Infection infection) {
		boolean furious = Rabies.furious(infection);
		boolean contactOnly = host instanceof EntityBat && DiseaseManager.pathogen(Rabies.ID).infectious(infection)
				&& Rabies.stage(infection).ordinal() < Rabies.Stage.TERMINAL.ordinal();
		if(furious || contactOnly) selectTarget(furious ? (host instanceof EntityBat ? 6D : 8D) : 1D);
		else clearTarget();

		if(controlsMovement(infection) && host instanceof EntityTameable) {
			EntityTameable tameable = (EntityTameable) host;
			sittingControlled = true;
			saveState();
			if(furious) tameable.setSitting(false); // Preserve the command; override only its active pose.
		} else if(sittingControlled) {
			restoreSitting(host, infection);
			sittingControlled = false;
		}
		if(furious && target != null && host instanceof EntityHorse && host.riddenByEntity == null) {
			EntityHorse horse = (EntityHorse) host;
			horse.setEatingHaystack(false);
			horse.setRearing(false);
		}
		if(host instanceof EntityBat) {
			if(furious && target != null && targetChunksLoaded(target)) {
				EntityBat bat = (EntityBat) host;
				bat.setIsBatHanging(false);
				// Native flight consumes this destination; do not replace it with ground navigation or velocity snaps.
				double dx = target.posX - bat.posX, dz = target.posZ - bat.posZ;
				double distance = Math.sqrt(dx * dx + dz * dz);
				// Native flight discards destinations within two blocks. Aim through close targets so its
				// arrival randomization cannot turn every close approach back into wandering.
				double scale = distance > 0.01D ? Math.max(1D, 5D / distance) : 0D;
				int x = MathHelper.floor_double(bat.posX + (scale == 0D ? 5D : dx * scale));
				int z = MathHelper.floor_double(bat.posZ + dz * scale);
				if(chunksLoaded(host, Math.min(bat.posX, x) - 2D, Math.min(bat.posZ, z) - 2D,
						Math.max(bat.posX, x) + 2D, Math.max(bat.posZ, z) + 2D)) {
					setBatDestination(new ChunkCoordinates(x,
							MathHelper.floor_double((target.boundingBox.minY + target.boundingBox.maxY - bat.height) / 2D), z));
					flightDirected = true;
				} else releaseFlight();
			} else releaseFlight();
		}
	}

	@Override public boolean shouldExecute() {
		Infection infection = infection();
		return infection != null && controlsMovement(infection);
	}

	@Override public boolean continueExecuting() { return shouldExecute(); }

	@Override public void updateTask() {
		lastExecution = host.ticksExisted;
		Infection infection = infection();
		if(infection == null) return;
		if(!Rabies.furious(infection)) {
			controlling = true;
			stopMovement();
			status = "neurological impairment; no forced pursuit";
			return;
		}
		if(target == null) {
			status = "no eligible nearby target";
			return;
		}
		controlling = true;
		if(!targetChunksLoaded(target) || !navigationChunksLoaded()) {
			stopMovement();
			status = "unavailable chunks; navigation paused";
		} else if(host.ticksExisted >= nextPath) {
			nextPath = host.ticksExisted + 10;
			boolean moved = host.getNavigator().tryMoveToEntityLiving(target, 1D);
			status = moved ? "pursuing" : "failed navigation; retry pending";
			if(!moved && host.getNavigator().noPath()) stopMovement();
		} else if(host.getNavigator().noPath()) {
			status = "no navigation path; retry pending";
		}
		host.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
		tryBite(target, DiseaseManager.now(host));
	}

	@Override public void resetTask() { stopMovement(); }

	private void stopMovement() {
		if(controlling) {
			host.getNavigator().clearPathEntity();
			host.getMoveHelper().setMoveTo(host.posX, host.posY, host.posZ, 0D);
			host.setAIMoveSpeed(0F);
			host.moveForward = host.moveStrafing = 0F;
		}
		controlling = false;
	}

	private void clearTarget() {
		stopMovement();
		target = null;
	}

	private boolean validTarget(EntityLivingBase candidate) {
		return candidate != host && candidate.isEntityAlive() && !candidate.isEntityInvulnerable()
				&& candidate.worldObj == host.worldObj && candidate.addedToChunk
				&& !(candidate instanceof EntityPlayer && (((EntityPlayer) candidate).capabilities.isCreativeMode
						|| ((EntityPlayer) candidate).capabilities.disableDamage))
				&& DiseaseManager.pathogen(Rabies.ID).supports(candidate)
				&& host.getDistanceSqToEntity(candidate) <= 100D;
		// Losing sight is not losing a target: navigation can pursue around an obstacle.
	}

	private boolean targetChunksLoaded(EntityLivingBase candidate) {
		return chunksLoaded(host, Math.min(host.posX, candidate.posX) - 2D, Math.min(host.posZ, candidate.posZ) - 2D,
				Math.max(host.posX, candidate.posX) + 2D, Math.max(host.posZ, candidate.posZ) + 2D);
	}

	private boolean navigationChunksLoaded() {
		// World.getPathEntityToEntity creates this exact ChunkCache; never let it load missing chunks.
		float range = host.getNavigator().getPathSearchRange();
		int radius = (int) (range + 16F);
		int x = MathHelper.floor_double(host.posX), z = MathHelper.floor_double(host.posZ);
		return !Float.isNaN(range) && range >= 0F && range <= 32F
				&& chunksLoaded(host, x - radius, z - radius, x + radius, z + radius);
	}

	static boolean chunksLoaded(EntityLivingBase host, double minX, double minZ, double maxX, double maxZ) {
		for(int x = MathHelper.floor_double(minX) >> 4; x <= MathHelper.floor_double(maxX) >> 4; x++) {
			for(int z = MathHelper.floor_double(minZ) >> 4; z <= MathHelper.floor_double(maxZ) >> 4; z++) {
				if(!host.worldObj.getChunkProvider().chunkExists(x, z)) return false;
			}
		}
		return true;
	}

	private void selectTarget(double range) {
		if(target != null && !validTarget(target)) clearTarget();
		if(host.ticksExisted < nextSearch) return;
		nextSearch = host.ticksExisted + 20;
		// Retain a valid player, but check for arriving players while pursuing another mammal.
		if(target instanceof EntityPlayer) return;
		List<EntityLivingBase> nearby = host.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, host.boundingBox.expand(range, range, range));
		EntityLivingBase closest = null;
		double distance = range * range;
		for(EntityLivingBase candidate : nearby) {
			double d = host.getDistanceSqToEntity(candidate);
			if(d > range * range || !validTarget(candidate)) continue;
			boolean player = candidate instanceof EntityPlayer, chosenPlayer = closest instanceof EntityPlayer;
			if(closest == null || player && !chosenPlayer || player == chosenPlayer && d < distance) {
				closest = candidate;
				distance = d;
			}
		}
		if(closest != null && (target == null || closest instanceof EntityPlayer)) {
			clearTarget();
			target = closest;
			nextPath = host.ticksExisted; // Start immediately, not at a global clock modulus.
		}
	}

	private void tryBite(EntityLivingBase victim, long now) {
		if(now < nextBite || !validTarget(victim) || !BiteDamageSource.hasContact(host, victim)) return;
		nextBite = now + 40;
		float damage = host instanceof EntityWolf ? (((EntityWolf) host).isTamed() ? 4F : 2F) : 1F;
		victim.attackEntityFrom(new BiteDamageSource(host), damage);
		// The common post-armor event path owns transmission; never roll again here.
	}

	/** Post-vanilla contact/impairment only; flight consumes the destination prepared before AI. */
	private void tickBat() {
		lastExecution = host.ticksExisted;
		Infection infection = infection();
		if(infection == null) return;
		EntityBat bat = (EntityBat) host;
		if(Rabies.stage(infection).ordinal() >= Rabies.Stage.NEUROLOGICAL.ordinal() && host.ticksExisted % 5 == 0) {
			bat.setIsBatHanging(false);
			bat.motionX *= 0.4D;
			bat.motionZ *= 0.4D;
			bat.motionY = Math.min(bat.motionY, -0.08D);
		}
		if(Rabies.furious(infection) && target != null) bat.setIsBatHanging(false);
		status = !Rabies.furious(infection) && controlsMovement(infection) ? "neurological flight impairment; no forced pursuit"
				: target == null ? "no eligible nearby target" : !targetChunksLoaded(target) || Rabies.furious(infection) && !flightDirected
				? "unavailable chunks; flight pursuit paused" : Rabies.furious(infection) ? "native flight pursuit" : "contact bites only";
		if(target != null && Rabies.stage(infection).ordinal() < Rabies.Stage.TERMINAL.ordinal())
			tryBite(target, DiseaseManager.now(host));
	}

	private void setBatDestination(ChunkCoordinates destination) {
		try { BAT_DESTINATION.set(host, destination); }
		catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot direct native bat flight", ex); }
	}

	private void releaseFlight() {
		if(flightDirected) setBatDestination(null);
		flightDirected = false;
	}

	static void restoreSitting(EntityLivingBase host, Infection infection) {
		if(infection != null && host instanceof EntityTameable && infection.details.hasKey(SAVED_SIT)) {
			EntityTameable tameable = (EntityTameable) host;
			boolean sitting = infection.details.getBoolean(SAVED_SIT);
			tameable.func_70907_r().setSitting(sitting);
			tameable.setSitting(sitting && tameable.isTamed());
			infection.details.removeTag(SAVED_SIT);
		}
	}

	void saveState() {
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(sittingControlled && infection != null) {
			try { infection.details.setBoolean(SAVED_SIT, SIT_COMMAND.getBoolean(((EntityTameable) host).func_70907_r())); }
			catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot preserve sitting command", ex); }
		}
	}

	void release() {
		clearTarget();
		releaseFlight();
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		saveState();
		restoreSitting(host, infection);
		for(Map.Entry<EntityAIBase, Integer> entry : changedMutexes.entrySet()) entry.getKey().setMutexBits(entry.getValue());
		changedMutexes.clear();
	}

	static void updateHost(EntityLiving host, Infection infection) {
		DiseaseHost data = DiseaseHost.get(host);
		RabiesBehavior behavior = (RabiesBehavior) data.behaviors.get(Rabies.ID);
		if(behavior == null) {
			behavior = new RabiesBehavior(host, infection);
			data.behaviors.put(Rabies.ID, behavior);
			if(!(host instanceof EntityBat)) host.tasks.addTask(-1, behavior);
		}
		behavior.prepare(infection);
		if(host instanceof EntityBat) DiseaseEvents.queueBat((EntityBat) host);
	}

	static void updateBat(EntityBat bat) {
		RabiesBehavior behavior = (RabiesBehavior) DiseaseHost.get(bat).behaviors.get(Rabies.ID);
		if(behavior != null) behavior.tickBat();
	}

	static String describe(EntityLivingBase host, Infection infection) {
		Pathogen rabies = DiseaseManager.pathogen(Rabies.ID);
		RabiesBehavior behavior = (RabiesBehavior) DiseaseHost.get(host).behaviors.get(Rabies.ID);
		String target = ", target=" + (behavior == null || behavior.target == null ? "none" : behavior.target.getEntityId());
		if(!rabies.enabled()) return "disabled" + target;
		if(!rabies.supports(host)) return "unsupported host" + target;
		if(!host.isEntityAlive()) return "dead host" + target;
		Rabies.Stage stage = Rabies.stage(infection);
		if(stage == Rabies.Stage.INCUBATING) return "silent incubation; normal behavior" + target;
		if(stage == Rabies.Stage.SHEDDING) return "silent shedding; normal behavior"
				+ (host instanceof EntityBat ? "; contact bites only" : "") + target;
		if(host instanceof EntityPlayer) return "player symptoms only; no autonomous movement or attacks" + target;
		if(!(host instanceof EntityLiving)) return "missing behavior execution; host has no EntityLiving AI" + target;
		String presentation = "paralytic".equals(infection.presentation) ? "paralytic presentation; " : "";
		if(!controlsMovement(infection)) return presentation + "reduced movement; no forced aggression" + target;
		if(behavior == null) return presentation + "missing behavior installation" + target;
		if(host.riddenByEntity != null && host instanceof EntityHorse) return presentation + "rider controls horse movement; disease AI blocked" + target;
		if(behavior.lastExecution < 0 || host.ticksExisted - behavior.lastExecution > 3)
			return presentation + "missing behavior execution (task blocked or custom AI)" + target;
		if(behavior.target != null && behavior.validTarget(behavior.target) && BiteDamageSource.hasContact(host, behavior.target))
			return presentation + "in bite reach; cooldownTicks=" + Math.max(0L, behavior.nextBite - DiseaseManager.now(host)) + target;
		if(behavior.target != null && (!behavior.targetChunksLoaded(behavior.target)
				|| !(host instanceof EntityBat) && !behavior.navigationChunksLoaded()))
			return presentation + "unavailable chunks or unsupported navigation range" + target;
		return presentation + behavior.status + target;
	}
}
