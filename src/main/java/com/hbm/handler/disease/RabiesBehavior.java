package com.hbm.handler.disease;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.hbm.config.RabiesConfig;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAISit;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.block.material.Material;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Removable server-owned movement/look control; never changes ownership, taming or vanilla attack targets. */
final class RabiesBehavior extends EntityAIBase {
	private static final Field BAT_DESTINATION = ReflectionHelper.findField(EntityBat.class, "spawnPosition", "field_82237_a");
	private static final Field SIT_COMMAND = ReflectionHelper.findField(EntityAISit.class, "isSitting", "field_75271_b");
	private static final Field PATH_RANGE = ReflectionHelper.findField(PathNavigate.class, "pathSearchRange", "field_75512_e");
	private static final String SAVED_SIT = "diseaseSittingCommand";
	private final EntityLiving host;
	private final Map<EntityAIBase, Integer> changedMutexes = new IdentityHashMap<EntityAIBase, Integer>();
	private EntityLivingBase target;
	private int nextSearch, nextPath, lastExecution = -1;
	private int lastSeen, nextSwitch, pathFailures, nextRoam, roamUntil, progressAt;
	private double seenX, seenY, seenZ, progressX, progressZ, roamHeading;
	private boolean switchRequested, waterControlled, savedAvoidsWater, waterFlight;
	private ChunkCoordinates roamDestination;
	private final int[] recentIds = new int[3], recentUntil = new int[3];
	private int recentIndex;
	private final IAttributeInstance diseaseRange;
	private long nextBite;
	private boolean controlling, flightDirected, sittingControlled;
	private String status = "awaiting behavior execution";

	RabiesBehavior(EntityLiving host, Infection infection) {
		this.host = host;
		diseaseRange = new ServersideAttributeMap().registerAttribute(new RangedAttribute("rabies.navigationRange",
				RabiesConfig.groundPursuitDistance, 1D, 64D));
		nextSearch = host.ticksExisted + Math.floorMod(host.getEntityId(), 20);
		roamHeading = host.getRNG().nextDouble() * Math.PI * 2D;
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
		updateWaterAvoidance(infection);
		boolean contactOnly = host instanceof EntityBat && DiseaseManager.pathogen(Rabies.ID).infectious(infection)
				&& Rabies.stage(infection).ordinal() < Rabies.Stage.TERMINAL.ordinal();
		if(furious || contactOnly) selectTarget(furious ? detectionRange() : 1D, furious);
		else if(target != null) clearTarget();

		if(controlsMovement(infection) && host instanceof EntityTameable) {
			EntityTameable tameable = (EntityTameable) host;
			sittingControlled = true;
			saveState();
			if(furious) tameable.setSitting(false); // Preserve the command; override only its active pose.
		} else if(sittingControlled) {
			restoreSitting(host, infection);
			sittingControlled = false;
		}
		if(furious && host instanceof EntityHorse && host.riddenByEntity == null) {
			EntityHorse horse = (EntityHorse) host;
			horse.setEatingHaystack(false);
			horse.setRearing(false);
		}
		if(host instanceof EntityBat) prepareFlight(furious);
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
			if(host.isInWater()) roam(true); // Impairment must not suppress swimming/escape to safe land.
			else stopMovement();
			status = "neurological impairment; no forced pursuit";
			return;
		}
		if(target == null) {
			roam(false);
			return;
		}
		controlling = true;
		if(!navigationChunksLoaded()) {
			stopMovement();
			status = "unavailable chunks; navigation paused";
		} else if(host.ticksExisted >= nextPath) {
			PathEntity path = pathTo(seenX, seenY, seenZ, false);
			boolean moved = path != null && host.getNavigator().setPath(path, 1D);
			nextPath = host.ticksExisted + (moved ? 20 : Math.min(80, 20 * ++pathFailures));
			status = moved ? "pursuing last observed location" : "failed navigation; retry delayed";
			if(moved) pathFailures = 0;
			else if(pathFailures >= 3) { forgetTarget(); return; }
		} else if(host.getNavigator().noPath()) {
			status = "no navigation path; retry pending";
		}
		host.getLookHelper().setLookPosition(seenX, seenY + target.getEyeHeight(), seenZ, 30F, 30F);
		if(host.ticksExisted >= progressAt) {
			if(host.getDistanceSq(progressX, host.posY, progressZ) < 1D && !BiteDamageSource.hasContact(host, target)) {
				forgetTarget();
				return;
			}
			progressX = host.posX; progressZ = host.posZ; progressAt = host.ticksExisted + 100;
		}
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
		switchRequested = false;
		pathFailures = 0;
		releaseFlight();
	}

	private boolean validTarget(EntityLivingBase candidate) {
		return candidate != host && candidate.isEntityAlive() && !candidate.isEntityInvulnerable()
				&& candidate.worldObj == host.worldObj && candidate.addedToChunk
				&& !(candidate instanceof EntityPlayer && (((EntityPlayer) candidate).capabilities.isCreativeMode
						|| ((EntityPlayer) candidate).capabilities.disableDamage))
				&& DiseaseManager.pathogen(Rabies.ID).supports(candidate)
				&& host.getDistanceSqToEntity(candidate) <= pursuitRange() * pursuitRange()
				&& chunksLoaded(host, candidate.posX - 1D, candidate.posZ - 1D, candidate.posX + 1D, candidate.posZ + 1D);
	}

	private boolean targetChunksLoaded(EntityLivingBase candidate) {
		return chunksLoaded(host, Math.min(host.posX, candidate.posX) - 2D, Math.min(host.posZ, candidate.posZ) - 2D,
				Math.max(host.posX, candidate.posX) + 2D, Math.max(host.posZ, candidate.posZ) + 2D);
	}

	private boolean navigationChunksLoaded() {
		// All disease paths use XYZ, whose native ChunkCache radius is range + 8 (entity paths use +16).
		float range = (float) diseaseRange.getAttributeValue();
		int radius = (int) (range + 8F);
		int x = MathHelper.floor_double(host.posX), z = MathHelper.floor_double(host.posZ);
		return !Float.isNaN(range) && range >= 1F && range <= 64F
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

	private double detectionRange() { return host instanceof EntityBat ? RabiesConfig.batDetectionDistance : RabiesConfig.groundDetectionDistance; }
	private double pursuitRange() { return host instanceof EntityBat ? RabiesConfig.batPursuitDistance : RabiesConfig.groundPursuitDistance; }

	private boolean visible(EntityLivingBase candidate) {
		return targetChunksLoaded(candidate) && host.getEntitySenses().canSee(candidate);
	}

	private void observeTarget() {
		seenX = target.posX; seenY = target.boundingBox.minY; seenZ = target.posZ;
		lastSeen = host.ticksExisted;
	}

	private void remember(EntityLivingBase entity) {
		recentIds[recentIndex] = entity.getEntityId(); recentUntil[recentIndex] = host.ticksExisted + 100;
		recentIndex = (recentIndex + 1) % recentIds.length;
	}

	private boolean recent(EntityLivingBase entity) {
		for(int i = 0; i < recentIds.length; i++)
			if(recentIds[i] == entity.getEntityId() && host.ticksExisted < recentUntil[i]) return true;
		return false;
	}

	private void forgetTarget() {
		if(target != null) remember(target);
		clearTarget();
		nextSearch = Math.max(nextSearch, host.ticksExisted + 20);
	}

	@SuppressWarnings("unchecked")
	private void selectTarget(double range, boolean furious) {
		if(target != null) {
			if(!validTarget(target)) clearTarget();
			else if(visible(target)) observeTarget();
			else if(!furious || host.ticksExisted - lastSeen >= RabiesConfig.lostSightTicks
					|| host.getDistanceSq(seenX, seenY, seenZ) < 2D) forgetTarget();
		}
		if(host.ticksExisted < nextSearch) return;
		nextSearch = host.ticksExisted + 20;
		if(target != null && host.ticksExisted < nextSwitch) return;
		final boolean switching = target != null;
		boolean afterBite = switchRequested;
		switchRequested = false;
		if(switching && afterBite) nextSwitch = host.ticksExisted + RabiesConfig.targetSwitchCooldownTicks;
		List<EntityLivingBase> choices = new ArrayList<EntityLivingBase>();
		// Native AABB query visits existing chunks only; no world entity list, village scan or infection lookup.
		List<EntityLivingBase> nearby = host.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, host.boundingBox.expand(range, range, range));
		for(EntityLivingBase candidate : nearby) {
			double d = host.getDistanceSqToEntity(candidate);
			if(candidate == target || d > range * range || !validTarget(candidate) || recent(candidate) || !visible(candidate)) continue;
			if(switching && !afterBite && (d > 9D || d * 2D >= host.getDistanceSqToEntity(target))) continue;
			choices.add(candidate);
		}
		Collections.sort(choices, new Comparator<EntityLivingBase>() {
			@Override public int compare(EntityLivingBase a, EntityLivingBase b) {
				return Double.compare(host.getDistanceSqToEntity(a), host.getDistanceSqToEntity(b));
			}
		});
		// At most three reachability checks per staggered search, never one per nearby entity.
		for(int i = 0; i < Math.min(3, choices.size()); i++) {
			EntityLivingBase candidate = choices.get(i);
			PathEntity path = null;
			if(!(host instanceof EntityBat) && !BiteDamageSource.hasContact(host, candidate)) {
				path = pathTo(candidate.posX, candidate.boundingBox.minY, candidate.posZ, true);
				if(path == null) { remember(candidate); continue; }
			}
			if(target != null) remember(target);
			clearTarget();
			target = candidate;
			roamDestination = null;
			observeTarget();
			progressX = host.posX; progressZ = host.posZ; progressAt = host.ticksExisted + 100;
			nextSwitch = host.ticksExisted + RabiesConfig.targetSwitchCooldownTicks;
			nextPath = host.ticksExisted;
			if(path != null) {
				controlling = true;
				host.getNavigator().setPath(path, 1D);
				nextPath += 20;
			}
			return;
		}
	}

	static void confirmedBite(EntityLivingBase source, EntityLivingBase victim) {
		Object installed = DiseaseHost.get(source).behaviors.get(Rabies.ID);
		if(!(installed instanceof RabiesBehavior)) return;
		RabiesBehavior behavior = (RabiesBehavior) installed;
		Infection infection = behavior.infection();
		if(infection != null && Rabies.furious(infection) && behavior.target == victim
				&& source.ticksExisted >= behavior.nextSwitch && source.getRNG().nextDouble() < RabiesConfig.targetSwitchChance)
			behavior.switchRequested = true; // Search on the owning host's next staggered tick; transmission is already complete.
	}

	private void updateWaterAvoidance(Infection infection) {
		if(host instanceof EntityBat) return; // Flight uses air destinations, never a ground water flag.
		boolean avoid = RabiesConfig.animalWaterAvoidance && Rabies.symptomatic(infection);
		if(avoid) {
			if(!waterControlled) savedAvoidsWater = host.getNavigator().getAvoidsWater();
			waterControlled = true;
			// WHO says hydrophobia is not a typical dog sign. This dry-route preference is optional gameplay.
			host.getNavigator().setAvoidsWater(!host.isInWater());
		} else restoreWaterAvoidance();
	}

	private void restoreWaterAvoidance() {
		if(waterControlled) host.getNavigator().setAvoidsWater(savedAvoidsWater);
		waterControlled = false;
	}

	private boolean reaches(PathEntity path, double x, double y, double z) {
		if(path == null || path.getCurrentPathLength() == 0) return false;
		PathPoint end = path.getFinalPathPoint();
		double dx = end.xCoord + host.width / 2D - x, dz = end.zCoord + host.width / 2D - z;
		return dx * dx + dz * dz <= 4D && Math.abs(end.yCoord - y) <= 2D;
	}

	private PathEntity pathTo(double x, double y, double z, boolean complete) {
		if(!navigationChunksLoaded() || host.getDistanceSq(x, y, z) > pursuitRange() * pursuitRange()) return null;
		PathNavigate navigator = host.getNavigator();
		boolean avoids = navigator.getAvoidsWater();
		Object original = null;
		try {
			original = PATH_RANGE.get(navigator);
			// Navigator normally aliases followRange (often 16). Give only this request its configured range,
			// without changing native attack attributes, saved modifiers, doors, swimming or other tasks.
			PATH_RANGE.set(navigator, diseaseRange);
			PathEntity path = navigator.getPathToXYZ(x, y, z);
			if(avoids && !reaches(path, x, y, z)) {
				// Prefer dry paths, but allow safe native water traversal if no dry route reaches the goal.
				navigator.setAvoidsWater(false);
				PathEntity fallback = navigator.getPathToXYZ(x, y, z);
				if(reaches(fallback, x, y, z) || path == null) path = fallback;
			}
			return complete && !reaches(path, x, y, z) ? null : path;
		} catch(IllegalAccessException ex) {
			throw new IllegalStateException("Cannot configure disease navigation range", ex);
		} finally {
			navigator.setAvoidsWater(avoids);
			if(original != null) {
				try { PATH_RANGE.set(navigator, original); }
				catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot restore native navigation range", ex); }
			}
		}
	}

	private boolean waterAt(int x, int y, int z) {
		return host.worldObj.getBlock(x, y, z).getMaterial() == Material.water;
	}

	private ChunkCoordinates groundDestination(double heading) {
		double length = Math.min(pursuitRange() * 0.75D, 6D + host.getRNG().nextDouble() * 6D);
		int x = MathHelper.floor_double(host.posX + Math.cos(heading) * length);
		int z = MathHelper.floor_double(host.posZ + Math.sin(heading) * length);
		double margin = host.width + 2D;
		if(!chunksLoaded(host, x - margin, z - margin, x + margin, z + margin)) return null;
		int baseY = MathHelper.floor_double(host.boundingBox.minY);
		// Local, bounded checks only. Native pathfinding still owns steps, falls, doors and swimming.
		for(int y = Math.min(253, baseY + 1); y >= Math.max(1, baseY - 3); y--) {
			if(!World.doesBlockHaveSolidTopSurface(host.worldObj, x, y - 1, z)) continue;
			AxisAlignedBB body = host.boundingBox.getOffsetBoundingBox(x + 0.5D - host.posX,
					y - host.boundingBox.minY, z + 0.5D - host.posZ);
			if(host.worldObj.getCollidingBoundingBoxes(host, body).isEmpty()
					&& !host.worldObj.isAnyLiquid(body)) return new ChunkCoordinates(x, y, z);
		}
		return null;
	}

	private void roam(boolean escapeOnly) {
		controlling = true;
		if(roamDestination != null && !host.getNavigator().noPath() && host.ticksExisted < roamUntil) {
			status = escapeOnly ? "seeking safe land" : "restless local roaming";
			return;
		}
		if(host.ticksExisted < nextRoam) { status = "roaming retry delayed"; return; }
		stopMovement();
		roamDestination = null;
		nextRoam = host.ticksExisted + 40 + host.getRNG().nextInt(21);
		if(!navigationChunksLoaded()) { status = "unavailable chunks; roaming paused"; return; }
		// Merck describes extensive roaming, reduced caution and attacks on moving objects.
		// Segments start from the current position, never an infection anchor or village destination.
		// The story of one wolf biting over 200 people is unverified, not evidence of planned spread.
		int paths = 0;
		for(int i = 0; i < 8 && paths < 2; i++) {
			double heading = roamHeading + (host.getRNG().nextDouble() - 0.5D) * (i < 4 ? 1.8D : Math.PI * 2D);
			ChunkCoordinates destination = groundDestination(heading);
			if(destination == null) continue;
			paths++;
			PathEntity path = pathTo(destination.posX + 0.5D, destination.posY, destination.posZ + 0.5D, true);
			if(path == null) continue;
			controlling = true;
			host.getNavigator().setPath(path, 1D);
			roamDestination = destination;
			roamHeading = heading;
			roamUntil = host.ticksExisted + 160;
			status = escapeOnly ? "seeking safe land" : "restless local roaming";
			return;
		}
		roamHeading += 1D + host.getRNG().nextDouble();
		status = "no safe roaming route; retry delayed";
	}

	private boolean safeFlight(ChunkCoordinates destination) {
		if(destination.posY < 1 || destination.posY > 253 || !chunksLoaded(host,
				Math.min(host.posX, destination.posX) - 2D, Math.min(host.posZ, destination.posZ) - 2D,
				Math.max(host.posX, destination.posX) + 2D, Math.max(host.posZ, destination.posZ) + 2D)) return false;
		return host.worldObj.isAirBlock(destination.posX, destination.posY, destination.posZ)
				&& (!RabiesConfig.animalWaterAvoidance || !waterAt(destination.posX, destination.posY - 1, destination.posZ));
	}

	private void prepareFlight(boolean furious) {
		EntityBat bat = (EntityBat) host;
		if(!furious) {
			Infection infection = infection();
			if(infection != null && Rabies.symptomatic(infection) && RabiesConfig.animalWaterAvoidance) {
				if(waterFlight && flightDirected && roamDestination != null && host.ticksExisted < roamUntil
						&& safeFlight(roamDestination)) { setBatDestination(roamDestination); return; }
				releaseFlight();
				avoidBatWater();
			} else releaseFlight();
			return;
		}
		waterFlight = false;
		bat.setIsBatHanging(false);
		if(target != null) {
			double dx = seenX - bat.posX, dz = seenZ - bat.posZ;
			double distance = Math.sqrt(dx * dx + dz * dz);
			// Native flight discards destinations within two blocks: aim through close visible targets.
			double scale = distance > 0.01D ? Math.max(1D, 5D / distance) : 0D;
			ChunkCoordinates destination = new ChunkCoordinates(
					MathHelper.floor_double(bat.posX + (scale == 0D ? 5D : dx * scale)),
					MathHelper.floor_double(seenY + (target.height - bat.height) / 2D),
					MathHelper.floor_double(bat.posZ + dz * scale));
			if(safeFlight(destination)) {
				setBatDestination(destination); flightDirected = true;
				status = "native flight toward last observed location";
			} else { releaseFlight(); status = "flight obstacle or water; native escape retained"; }
			return;
		}
		if(roamDestination != null && host.ticksExisted < roamUntil
				&& host.getDistanceSq(roamDestination.posX, roamDestination.posY, roamDestination.posZ) > 6D
				&& safeFlight(roamDestination)) {
			setBatDestination(roamDestination); flightDirected = true;
			return;
		}
		releaseFlight();
		if(host.ticksExisted < nextRoam) return;
		nextRoam = host.ticksExisted + 30;
		for(int i = 0; i < 8; i++) {
			double heading = roamHeading + (host.getRNG().nextDouble() - 0.5D) * Math.PI;
			ChunkCoordinates destination = new ChunkCoordinates(MathHelper.floor_double(host.posX + Math.cos(heading) * 6D),
					MathHelper.floor_double(host.posY) + host.getRNG().nextInt(5) - 2,
					MathHelper.floor_double(host.posZ + Math.sin(heading) * 6D));
			if(!safeFlight(destination)) continue;
			roamDestination = destination; roamHeading = heading; roamUntil = host.ticksExisted + 80;
			setBatDestination(destination); flightDirected = true;
			status = "restless native flight";
			return;
		}
		roamDestination = null;
		roamHeading += 1D;
		status = "no safe flight destination; native escape retained";
	}

	private void avoidBatWater() {
		EntityBat bat = (EntityBat) host;
		if(!RabiesConfig.animalWaterAvoidance || bat.getIsBatHanging() || host.ticksExisted < nextRoam) return;
		ChunkCoordinates destination;
		try { destination = (ChunkCoordinates) BAT_DESTINATION.get(bat); }
		catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot inspect native bat destination", ex); }
		if(!host.isInWater() && (destination == null || !chunksLoaded(host, destination.posX - 1D, destination.posZ - 1D,
				destination.posX + 1D, destination.posZ + 1D) || !waterAt(destination.posX, destination.posY - 1, destination.posZ))) return;
		nextRoam = host.ticksExisted + 30;
		for(int i = 0; i < 8; i++) {
			ChunkCoordinates dry = new ChunkCoordinates(MathHelper.floor_double(host.posX) + host.getRNG().nextInt(9) - 4,
					MathHelper.floor_double(host.posY) + 1 + host.getRNG().nextInt(3),
					MathHelper.floor_double(host.posZ) + host.getRNG().nextInt(9) - 4);
			if(safeFlight(dry)) {
				roamDestination = dry; roamUntil = host.ticksExisted + 30;
				setBatDestination(dry); flightDirected = waterFlight = true;
				return;
			}
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
		if(!Rabies.furious(infection)) status = controlsMovement(infection)
				? "neurological flight impairment; no forced pursuit" : "contact bites only";
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
		restoreWaterAvoidance();
		roamDestination = null;
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
		if(host instanceof EntityPlayer) return "player symptoms only; no autonomous movement or attacks; swallowingSpasms="
				+ ("furious".equals(infection.presentation) && Rabies.symptomatic(infection))
				+ "; waterContactPenalty=" + (Rabies.symptomatic(infection) && RabiesConfig.playerWaterContactPenalty && host.isInWater()) + target;
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
		return presentation + behavior.status + "; detectionBlocks=" + behavior.detectionRange()
				+ "; pursuitBlocks=" + behavior.pursuitRange()
				+ (behavior.target == null ? "" : "; unseenTicks=" + (host.ticksExisted - behavior.lastSeen)) + target;
	}
}
