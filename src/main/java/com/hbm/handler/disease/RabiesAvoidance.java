package com.hbm.handler.disease;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.config.RabiesConfig;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Victim-owned, transient encounter response, independent of infection establishment. */
final class RabiesAvoidance extends EntityAIBase {
	private static final int SEARCH_INTERVAL = 20, MEMORY_SIZE = 8, ABSENCE_TICKS = 100;
	private final EntityLiving host;
	private final Map<EntityAIBase, Integer> changedMutexes = new IdentityHashMap<EntityAIBase, Integer>();
	private final int[] ids = new int[MEMORY_SIZE], absentSince = new int[MEMORY_SIZE];
	private final boolean[] occupied = new boolean[MEMORY_SIZE], recognized = new boolean[MEMORY_SIZE];
	private EntityLivingBase threat;
	private int nextSearch, nextPath, escapeUntil, lastVisible, candidateCursor;
	private double threatX, threatY, threatZ;
	private boolean controlling, biteEscape;
	private ChunkCoordinates flightDestination;

	private RabiesAvoidance(EntityLiving host) {
		this.host = host;
		nextSearch = host.ticksExisted + Math.floorMod(host.getEntityId(), SEARCH_INTERVAL);
		setMutexBits(1); // Movement only; swimming (4), looking (2) and doors (0) remain native.
		for(Object value : host.tasks.taskEntries) {
			EntityAIBase action = ((EntityAITaskEntry) value).action;
			// Vanilla baby following otherwise writes paths concurrently with escape and disease pursuit.
			if(action instanceof EntityAIFollowParent && (action.getMutexBits() & 1) == 0) {
				changedMutexes.put(action, action.getMutexBits());
				action.setMutexBits(action.getMutexBits() | 1);
			}
		}
	}

	private static boolean animal(EntityLivingBase entity) {
		return entity instanceof EntityLiving && !(entity instanceof EntityVillager)
				&& DiseaseManager.pathogen(Rabies.ID).supports(entity);
	}

	static void updateHost(EntityLiving host) {
		DiseaseHost data = DiseaseHost.get(host);
		if(!RabiesConfig.enabled || !animal(host)) {
			if(data.rabiesAvoidance != null) {
				data.rabiesAvoidance.release();
				host.tasks.removeTask(data.rabiesAvoidance);
				data.rabiesAvoidance = null;
			}
			return;
		}
		if(data.rabiesAvoidance == null) {
			data.rabiesAvoidance = new RabiesAvoidance(host);
			if(!(host instanceof EntityBat)) host.tasks.addTask(0, data.rabiesAvoidance);
		}
		data.rabiesAvoidance.prepare();
	}

	static void bitten(EntityLivingBase victim, EntityLivingBase attacker) {
		if(!RabiesConfig.enabled || !animal(victim) || !animal(attacker)) return;
		updateHost((EntityLiving) victim);
		RabiesAvoidance response = DiseaseHost.get(victim).rabiesAvoidance;
		if(!response.canControl()) return;
		if(response.threat != attacker) response.stopMovement();
		response.threat = attacker; // The actual attacker, even when it was silently shedding.
		response.observe();
		response.escapeUntil = victim.ticksExisted + RabiesConfig.biteEscapeTicks;
		response.biteEscape = true;
		response.nextPath = Math.max(response.nextPath, victim.ticksExisted);
	}

	private boolean canControl() {
		if(!RabiesConfig.enabled || !host.isEntityAlive() || host.riddenByEntity != null || host.isRiding()) return false;
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(infection == null) return true;
		DiseaseManager.pathogen(Rabies.ID).progress(host, infection, DiseaseManager.now(host));
		return !RabiesBehavior.controlsMovement(infection);
	}

	private boolean loaded(EntityLivingBase entity) {
		return entity.isEntityAlive() && entity.worldObj == host.worldObj && entity.addedToChunk
				&& host.worldObj.getChunkProvider().chunkExists(entity.chunkCoordX, entity.chunkCoordZ);
	}

	private boolean visible(EntityLivingBase entity) {
		return RabiesBehavior.chunksLoaded(host, Math.min(host.posX, entity.posX) - 1D,
				Math.min(host.posZ, entity.posZ) - 1D, Math.max(host.posX, entity.posX) + 1D,
				Math.max(host.posZ, entity.posZ) + 1D) && host.getEntitySenses().canSee(entity);
	}

	private void observe() {
		threatX = threat.posX; threatY = threat.boundingBox.minY; threatZ = threat.posZ;
		lastVisible = host.ticksExisted;
	}

	private void prepare() {
		if(!canControl()) { endEncounter(); return; }
		if(threat != null) {
			boolean escaping = host.ticksExisted < escapeUntil;
			double area = RabiesConfig.avoidanceDetectionDistance + 4D; // Hysteresis at the encounter boundary.
			if(!loaded(threat) || biteEscape && !escaping || !escaping && (host.getDistanceSqToEntity(threat) > area * area
					|| host.ticksExisted - lastVisible >= 60)) endEncounter();
		}
		if(host.ticksExisted >= nextSearch) {
			nextSearch = host.ticksExisted + SEARCH_INTERVAL;
			search();
		}
		if(threat != null && host instanceof EntityHorse) {
			((EntityHorse) host).setEatingHaystack(false);
			((EntityHorse) host).setRearing(false);
		}
		// Bats have no task scheduler. Direct their existing flight destination, never their velocity.
		if(host instanceof EntityBat) {
			if(threat != null) {
				((EntityBat) host).setIsBatHanging(false);
				updateTask();
				if(flightDestination != null) RabiesBehavior.setBatDestination((EntityBat) host, flightDestination);
			}
		}
	}

	static boolean active(EntityLiving host) {
		RabiesAvoidance response = DiseaseHost.get(host).rabiesAvoidance;
		return response != null && response.shouldExecute();
	}

	static void applyFlight(EntityLiving host) {
		RabiesAvoidance response = DiseaseHost.get(host).rabiesAvoidance;
		if(host instanceof EntityBat && response != null && response.threat != null && response.flightDestination != null)
			RabiesBehavior.setBatDestination((EntityBat) host, response.flightDestination);
	}

	@SuppressWarnings("unchecked")
	private void search() {
		double area = RabiesConfig.avoidanceDetectionDistance + 4D;
		// Retain decisions throughout an encounter, including ignored threats. No eviction of nearby memories.
		for(int i = 0; i < MEMORY_SIZE; i++) {
			if(!occupied[i]) continue;
			Object entity = host.worldObj.getEntityByID(ids[i]);
			boolean present = entity instanceof EntityLivingBase && loaded((EntityLivingBase) entity)
					&& host.getDistanceSqToEntity((EntityLivingBase) entity) <= area * area
					&& visible((EntityLivingBase) entity);
			if(present) absentSince[i] = 0;
			else if(absentSince[i] == 0) absentSince[i] = host.ticksExisted;
			else if(host.ticksExisted - absentSince[i] >= ABSENCE_TICKS) occupied[i] = false;
		}
		if(threat != null && loaded(threat) && visible(threat)) observe();
		if(threat != null) return; // Commit to one threat; do not switch every search.
		double range = RabiesConfig.avoidanceDetectionDistance;
		List<EntityLiving> nearby = host.worldObj.getEntitiesWithinAABB(EntityLiving.class,
				host.boundingBox.expand(range, range, range)); // Native query visits existing chunks only.
		int count = Math.min(16, nearby.size());
		for(int n = 0; n < count; n++) {
			EntityLiving candidate = nearby.get(Math.floorMod(candidateCursor++, nearby.size()));
			if(candidate == host || !loaded(candidate) || host.getDistanceSqToEntity(candidate) > range * range
					|| !animal(candidate) || !visible(candidate)) continue;
			Infection infection = DiseaseHost.get(candidate).infections.get(Rabies.ID);
			if(infection == null) continue;
			DiseaseManager.pathogen(Rabies.ID).progress(candidate, infection, DiseaseManager.now(host));
			Rabies.Stage stage = Rabies.stage(infection);
			if(stage != Rabies.Stage.CLINICAL && stage != Rabies.Stage.NEUROLOGICAL) continue;
			int slot = -1;
			for(int i = 0; i < MEMORY_SIZE; i++) if(occupied[i] && ids[i] == candidate.getEntityId()) { slot = i; break; }
			if(slot < 0) {
				for(int i = 0; i < MEMORY_SIZE; i++) if(!occupied[i]) { slot = i; break; }
				if(slot < 0) continue; // A full encounter memory defers new decisions instead of rerolling old ones.
				occupied[slot] = true; ids[slot] = candidate.getEntityId(); absentSince[slot] = 0;
				recognized[slot] = host.getRNG().nextDouble() < RabiesConfig.recognitionChance;
			}
			if(!recognized[slot]) continue; // No navigation changes for an ignored threat.
			threat = candidate;
			observe();
			nextPath = host.ticksExisted;
			return;
		}
	}

	@Override public boolean shouldExecute() { return threat != null && canControl(); }
	@Override public boolean continueExecuting() { return shouldExecute(); }
	@Override public void startExecuting() {
		controlling = true;
		host.getNavigator().clearPathEntity(); // Ordinary task resets have finished before the first escape path.
	}
	@Override public void resetTask() { stopMovement(); }

	@Override public void updateTask() {
		if(!shouldExecute() || host.ticksExisted < nextPath) return;
		nextPath = host.ticksExisted + 20;
		double heading = Math.atan2(host.posZ - threatZ, host.posX - threatX);
		if(host.getDistanceSq(threatX, host.posY, threatZ) < 0.01D) heading = host.getRNG().nextDouble() * Math.PI * 2D;
		int paths = 0;
		for(int i = 0; i < 8 && paths < 2; i++) {
			double angle = heading + (host.getRNG().nextDouble() - 0.5D) * (i < 4 ? 1.5D : 3D);
			double length = 6D + host.getRNG().nextDouble() * 6D;
			ChunkCoordinates destination = destination(angle, length);
			if(destination == null) continue;
			if(host instanceof EntityBat) {
				flightDestination = destination;
				controlling = true;
				nextPath = host.ticksExisted + 40;
				return;
			}
			if(!navigationLoaded()) return;
			paths++;
			PathEntity path = host.getNavigator().getPathToXYZ(destination.posX + 0.5D, destination.posY, destination.posZ + 0.5D);
			if(!safePath(path)) continue;
			controlling = true;
			if(host.getNavigator().setPath(path, 1.25D)) {
				nextPath = host.ticksExisted + 40;
				return;
			}
		}
		// Hold movement ownership while retrying: no alternating wander/escape tasks on failed paths.
	}

	private boolean navigationLoaded() {
		float range = host.getNavigator().getPathSearchRange();
		if(Float.isNaN(range) || range < 1F || range > 64F) return false;
		int radius = (int) (range + 8F);
		int x = MathHelper.floor_double(host.posX), z = MathHelper.floor_double(host.posZ);
		return RabiesBehavior.chunksLoaded(host, x - radius, z - radius, x + radius, z + radius);
	}

	private double separation(double x, double y, double z) {
		double dx = x - threatX, dy = y - threatY, dz = z - threatZ;
		return dx * dx + dy * dy + dz * dz;
	}

	private boolean safePath(PathEntity path) {
		if(path == null || path.getCurrentPathLength() == 0) return false;
		PathPoint end = path.getFinalPathPoint();
		double current = separation(host.posX, host.boundingBox.minY, host.posZ);
		if(separation(end.xCoord + 0.5D, end.yCoord, end.zCoord + 0.5D) <= current + 1D) return false;
		for(int i = 0; i < path.getCurrentPathLength(); i++) {
			PathPoint point = path.getPathPointFromIndex(i);
			if(separation(point.xCoord + 0.5D, point.yCoord, point.zCoord + 0.5D) < current * 0.75D) return false;
		}
		return true;
	}

	private ChunkCoordinates destination(double angle, double length) {
		int x = MathHelper.floor_double(host.posX + Math.cos(angle) * length);
		int z = MathHelper.floor_double(host.posZ + Math.sin(angle) * length);
		double margin = host.width + 2D;
		if(!RabiesBehavior.chunksLoaded(host, Math.min(host.posX, x) - margin, Math.min(host.posZ, z) - margin,
				Math.max(host.posX, x) + margin, Math.max(host.posZ, z) + margin)) return null;
		int baseY = MathHelper.floor_double(host.boundingBox.minY);
		for(int y = Math.min(253, baseY + 1); y >= Math.max(1, baseY - 3); y--) {
			if(separation(x + 0.5D, y, z + 0.5D) <= separation(host.posX, host.boundingBox.minY, host.posZ) + 1D) continue;
			AxisAlignedBB body = host.boundingBox.getOffsetBoundingBox(x + 0.5D - host.posX,
					y - host.boundingBox.minY, z + 0.5D - host.posZ);
			if(!host.worldObj.getCollidingBoundingBoxes(host, body).isEmpty()) continue;
			if(host instanceof EntityBat) {
				if(host.worldObj.isAnyLiquid(body) || host.worldObj.rayTraceBlocks(
						Vec3.createVectorHelper(host.posX, host.posY, host.posZ),
						Vec3.createVectorHelper(x + 0.5D, y, z + 0.5D)) != null) continue;
			} else {
				boolean water = host.worldObj.getBlock(x, y, z).getMaterial() == Material.water;
				if(!World.doesBlockHaveSolidTopSurface(host.worldObj, x, y - 1, z) && !water) continue;
				if(host.worldObj.isAnyLiquid(body) && !water) continue;
			}
			return new ChunkCoordinates(x, y, z);
		}
		return null;
	}

	private void stopMovement() {
		if(controlling) {
			if(host instanceof EntityBat) RabiesBehavior.setBatDestination((EntityBat) host, null);
			else {
				host.getNavigator().clearPathEntity();
				host.getMoveHelper().setMoveTo(host.posX, host.posY, host.posZ, 0D);
				host.moveForward = host.moveStrafing = 0F;
			}
		}
		controlling = false;
		flightDestination = null;
	}

	private void endEncounter() {
		stopMovement();
		threat = null;
		escapeUntil = 0;
		biteEscape = false;
	}

	private void release() {
		endEncounter();
		for(Map.Entry<EntityAIBase, Integer> entry : changedMutexes.entrySet()) entry.getKey().setMutexBits(entry.getValue());
		changedMutexes.clear();
	}

	void saveCommand(NBTTagCompound hostData) {
		// Vanilla saves the active pose as the command. Preserve the real command while AI temporarily stands.
		if(threat != null && host instanceof EntityTameable)
			hostData.setBoolean("sittingCommand", RabiesBehavior.sittingCommand((EntityTameable) host));
	}
}
