package com.hbm.entity.mob.ai;

import java.util.List;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.GeminiPopulation;
import com.hbm.world.gemini.WoodcrawlerSpawning;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.ChunkCache;

/** One local controller; no changes to the shared mob AI or global pathfinder. */
public final class EntityAIWoodcrawler extends EntityAIBase {

	private final EntityWoodcrawler mob;
	private final World world;
	private int scanDelay, pathDelay, stalkTicks, memoryTicks, wanderDelay, observeTicks, observeDelay;
	private int entranceCursor, doorX, doorY, doorZ, doorTicks, doorBackoff, knockDelay;
	private int entranceDoorX, entranceDoorY, entranceDoorZ, entranceDelay, doorDamage = -1;
	private int doorwayTicks;

	private int huntTicks, pathFailures;
	private int retaliationTicks;
	private double knownX, knownY, knownZ, goalX, goalY, goalZ;
	private double entranceDistance = Double.MAX_VALUE;
	private double entranceX, entranceY, entranceZ;
	private boolean hasGoal, hasEntrance, entranceDoor, breakingDoor, pursuing;
	private double bestHuntDistance = Double.MAX_VALUE;
	private int routeAttempt;
	private double currentSpeed = 0.6D;
	private int shelterDelay;
	private int crowdDelay, disperseTicks, disperseDelay;
	private double crowdedX, crowdedY, crowdedZ;

	public EntityAIWoodcrawler(EntityWoodcrawler mob) {
		this.mob = mob;
		this.world = mob.worldObj;
		setMutexBits(3);
	}

	@Override
	public boolean shouldExecute() { return mob.getHealth() > 0 && GeminiSpawning.canAct(mob, 1); }
	@Override
	public boolean continueExecuting() { return shouldExecute(); }

	@Override
	public void resetTask() {
		forget();
		mob.setClimbing(false);
		pathDelay = scanDelay = wanderDelay = doorBackoff = knockDelay = 0;
		crowdDelay = disperseTicks = disperseDelay = 0;
		retaliationTicks = 0;
		entranceCursor = 0;
		entranceDistance = Double.MAX_VALUE;
	}

	@Override
	public void updateTask() {
		if(world.isRemote) return;
		if(mob.isBurrowing() || mob.isGrabbing() || mob.isProbing()) {
			mob.getNavigator().clearPathEntity();
			mob.setClimbing(false);
			clearDoor();
			return;
		}
		if(mob.isCaptureBusy()) {
			clearDoor();
			if(mob.getCaptureState() == EntityWoodcrawler.CaptureState.RECOVERING) wander();
			else mob.getNavigator().clearPathEntity();
			mob.setClimbing(false);
			return;
		}
		if(pathDelay > 0) pathDelay--;
		if(scanDelay > 0) scanDelay--;
		if(doorBackoff > 0) doorBackoff--;
		if(knockDelay > 0) knockDelay--;
		if(shelterDelay > 0) shelterDelay--;
		if(crowdDelay > 0) crowdDelay--;
		if(disperseTicks > 0) disperseTicks--;
		if(disperseDelay > 0) disperseDelay--;
		if(retaliationTicks > 0) retaliationTicks--;
		if(observeDelay > 0) observeDelay--;
		if(entranceDelay > 0) entranceDelay--;
		if(doorwayTicks > 0) doorwayTicks--;
		if(mob.wantsShelter() || mob.avoidsExposure()) {
			// Strong exposure overrides pursuit; milder light is handled by route weighting.
			clearDoor(); mob.setHuntState(EntityWoodcrawler.RETREAT); mob.setClimbing(false);
			if(mob.getAttackTarget() != null && --memoryTicks <= 0) { forget(); scanDelay = 80; }
			seekShelter();
			return;
		}
		EntityLivingBase target = mob.getAttackTarget();
		if(target != null && (++huntTicks > Math.max(600, GeminiConfig.woodcrawlerStalkSeconds * 20 + 200)
				|| !world.blockExists(MathHelper.floor_double(target.posX), MathHelper.floor_double(target.posY), MathHelper.floor_double(target.posZ)))) {
			forget();
			scanDelay = 100;
			target = null;
		}
		if(mob.hasCapturedVillager() || !mob.isPrey(target)) {
			if(target != null) forget();
			target = null;
		}
		if(target != null && crowdDelay == 0) {
			crowdDelay = 20;
			if(!GeminiPopulation.canWoodcrawlerHunt(mob, target)) { beginDispersal(target); forget(); target = null; scanDelay = 0; }
		}
		if(mob.isSurfaceTraversal()) {
			if(target != null && mob.canEntityBeSeen(target)) remember(target);
			mob.getNavigator().clearPathEntity();
			return; // Finish the physical wall/ladder transition before selecting another entrance.
		}
		if(!mob.hasCapturedVillager() && scanDelay == 0 && (target == null || !pursuing && !breakingDoor
				&& retaliationTicks == 0 && mob.preyPriority(target) > 0)) {
			scanDelay = 20 + mob.getRNG().nextInt(10);
			EntityLivingBase preferred = findTarget(target == null ? 3 : mob.preyPriority(target));
			if(preferred != null) {
				mob.setAttackTarget(preferred);
				if(mob.getAttackTarget() == preferred) {
					clearDoor(); onTargetAcquired(preferred);
					target = preferred;
				} else {
					// A vetoed promotion must not discard a valid incumbent hunt.
					mob.setAttackTarget(target);
					if(target == null) beginDispersal(preferred);
				}
			}
		}
		if(target == null) {
			if(disperseTicks > 0) { disperse(); return; }
			mob.mimicVillager(false);
			mob.setHuntState(EntityWoodcrawler.WANDER);
			mob.setCrouching(false);
			wander();
			updateClimbing();
			return;
		}

		boolean visible = mob.getDistanceSqToEntity(target) <= square(GeminiConfig.woodcrawlerDetectionRange)
				&& mob.canEntityBeSeen(target);
		if(visible || mob.ticksExisted % 10 == 0 && sensesPrey(target)) remember(target);
		else if(--memoryTicks <= 0) {
			forget();
			scanDelay = 80;
			return;
		}
		stalkTicks = Math.min(stalkTicks + 1, GeminiConfig.woodcrawlerStalkSeconds * 20);
		double distance = mob.getDistanceSq(knownX, knownY, knownZ);
		if(distance + 1 < bestHuntDistance) { bestHuntDistance = distance; huntTicks = 0; }
		boolean ready = stalkTicks >= GeminiConfig.woodcrawlerStalkSeconds * 20;
		boolean escaping = visible && (target.posX - target.prevPosX) * (knownX - mob.posX)
				+ (target.posZ - target.prevPosZ) * (knownZ - mob.posZ) > Math.sqrt(distance) * 0.04D;
		if(ready || distance < 16 || visible && noticedBy(target) || escaping || retaliationTicks > 0) pursuing = true;
		mob.setCrouching(breakingDoor || !visible || indoors(knownX, knownY, knownZ));
		mob.getLookHelper().setLookPosition(knownX, knownY + 1, knownZ, 20, 20);
		if(interactWithDoor()) return;

		mob.mimicVillager(mob.getHuntState() == EntityWoodcrawler.STALK || indoors(knownX, knownY, knownZ));
		if(visible) {
			if(pursuing && mob.ticksExisted % 20 == 0 && mob.tryBurrow(target)) return;
			if(pursuing) {
				mob.setHuntState(EntityWoodcrawler.CHASE);
				mob.beginStrike(target);
				if(!mob.isGrabbing()) {
					double fleeing = (target.motionX * (target.posX - mob.posX) + target.motionZ * (target.posZ - mob.posZ)) / Math.max(1, Math.sqrt(distance));
					if(pathFailures >= 2) searchEntrances();
					moveTo(hasEntrance ? entranceX : knownX, hasEntrance ? entranceY : knownY, hasEntrance ? entranceZ : knownZ,
							distance < 9 ? 0.9D : fleeing > 0.04D ? 3.875D : 3.5D);
				}
			} else {
				mob.setHuntState(EntityWoodcrawler.STALK);
				// Short observation pauses are bounded; continue closing or choose a dark flank.
				if(observeTicks > 0) { observeTicks--; mob.getNavigator().clearPathEntity(); }
				else if(distance < 144 && observeDelay == 0) {
					observeTicks = 12 + mob.getRNG().nextInt(13); observeDelay = 65;
					mob.getNavigator().clearPathEntity();
				} else {
					double dx = mob.posX - knownX, dz = mob.posZ - knownZ;
					double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
					double desired = Math.max(3, 8 - stalkTicks / 20D);
					moveTo(knownX + dx / length * desired, knownY, knownZ + dz / length * desired, 0.60D);
				}
			}
		} else {
			mob.setHuntState(pursuing ? EntityWoodcrawler.SEARCH : EntityWoodcrawler.STALK);
			searchEntrances();
			if(ready && doorwayTicks == 0 && !entranceDoor && mob.ticksExisted % 20 == 0 && !woodenDoorBetween()
					&& distance <= 36 && memoryTicks > 0 && target.getDistanceSq(knownX, knownY, knownZ) <= 2.25D
					&& mob.beginProbe(target)) { clearDoor(); return; }
			if(!breakingDoor) moveTo(hasEntrance ? entranceX : knownX, hasEntrance ? entranceY : knownY,
					hasEntrance ? entranceZ : knownZ, pursuing ? 2.5D : 0.6D);
		}
		updateClimbing();
	}

	private boolean audible(EntityLivingBase target) {
		if(mob.getDistanceSqToEntity(target) > 64 || target.isSneaking()) return false;
		double dx = target.posX - target.prevPosX, dz = target.posZ - target.prevPosZ;
		return dx * dx + dz * dz > 0.0025D || target.hurtTime > 0;
	}
	/** Nearby doorway cues or activity through at most one thin wall; never a wall-penetrating global scan. */
	private boolean sensesPrey(EntityLivingBase target) {
		if(mob.getDistanceSqToEntity(target) > 64) return false;
		boolean activity = audible(target);
		Vec3 from = Vec3.createVectorHelper(mob.posX, mob.posY + 1, mob.posZ);
		Vec3 to = Vec3.createVectorHelper(target.posX, target.boundingBox.minY + 1, target.posZ);
		MovingObjectPosition hit = world.func_147447_a(from, to, false, true, false);
		if(hit == null) return activity;
		Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
		if(!(block instanceof BlockDoor && block.getMaterial() == Material.wood) && !activity) return false;
		double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
		double length = Math.max(0.01D, from.distanceTo(to));
		// Advance past this one obstruction's cell, then require a clear remaining ray.
		Vec3 past = hit.hitVec;
		for(int step = 0; step < 12; step++) {
			past = past.addVector(dx / length * 0.15D, dy / length * 0.15D, dz / length * 0.15D);
			if(from.distanceTo(past) >= length) return true;
			if(MathHelper.floor_double(past.xCoord) != hit.blockX || MathHelper.floor_double(past.yCoord) != hit.blockY
					|| MathHelper.floor_double(past.zCoord) != hit.blockZ)
				return world.func_147447_a(past, to, false, true, false) == null;
		}
		return false;
	}
	private boolean noticedBy(EntityLivingBase target) {
		if(!target.canEntityBeSeen(mob)) return false;
		Vec3 look = target.getLookVec();
		double dx = mob.posX - target.posX, dz = mob.posZ - target.posZ;
		return look != null && look.xCoord * dx + look.zCoord * dz > Math.sqrt(dx * dx + dz * dz) * 0.65D;
	}
	public boolean hasActiveHunt() { return mob.getAttackTarget() != null && (memoryTicks > 0 || breakingDoor); }
	public boolean isEnteringDoorway() { return doorwayTicks > 0; }
	private void seekShelter() {
		if(shelterDelay > 0) return;
		shelterDelay = 40;
		double best = mob.shelterScore(mob.posX, mob.posY, mob.posZ);
		double bx = mob.posX, by = mob.posY, bz = mob.posZ;
		for(int i = 0; i < 16; i++) {
			double angle = mob.getRNG().nextDouble() * Math.PI * 2, radius = 3 + mob.getRNG().nextInt(7);
			double x = MathHelper.floor_double(mob.posX + Math.cos(angle) * radius) + 0.5D;
			double z = MathHelper.floor_double(mob.posZ + Math.sin(angle) * radius) + 0.5D;
			double y = MathHelper.floor_double(mob.posY) + mob.getRNG().nextInt(5) - 2;
			if(!standable(x, y, z)) continue;
			double score = mob.shelterScore(x, y, z);
			if(score > best) { best = score; bx = x; by = y; bz = z; }
		}
		pathDelay = 0;
		if(bx != mob.posX || bz != mob.posZ) moveTo(bx, by, bz, 2.5D);
		else mob.getNavigator().clearPathEntity();
	}

	/** Invalidate paths and entrance probes without restarting stalking or forgetting live prey. */
	public void onBurrowFinished() {
		pathDelay = pathFailures = 0;
		hasGoal = hasEntrance = false;
		entranceCursor = 0;
		entranceDistance = Double.MAX_VALUE;
		clearDoor();
	}
	public void onSurfaceTraversalFinished() {
		onBurrowFinished();
	}

	private void onTargetAcquired(EntityLivingBase target) {
		disperseTicks = huntTicks = pathFailures = 0;
		resetEntrances();
		remember(target); stalkTicks = observeTicks = 0;
		pursuing = false; bestHuntDistance = Double.MAX_VALUE;
		if(mob.canEntityBeSeen(target)) GeminiPopulation.alert(mob, target);
	}

	/** Damage establishes a short retaliation commitment without bypassing prey/admission rules. */
	public void onAttacked(EntityLivingBase attacker) {
		if(mob.isBurrowing() || mob.isCaptureBusy() || !mob.isPrey(attacker)) return;
		boolean changed = mob.getAttackTarget() != attacker;
		mob.setAttackTarget(attacker);
		if(mob.getAttackTarget() != attacker) return;
		if(changed) { clearDoor(); onTargetAcquired(attacker); }
		else remember(attacker);
		retaliationTicks = 200;
		pursuing = true;
	}

	private EntityLivingBase findTarget(int priorityLimit) {
		double range = GeminiConfig.woodcrawlerDetectionRange;
		// Separate local tiers keep a crowded animal pen from hiding a nearby player behind the budget.
		for(int priority = 0; priority < priorityLimit; priority++) {
			Class<? extends EntityLivingBase> type = priority == 0 ? EntityPlayer.class
					: priority == 1 ? EntityVillager.class : net.minecraft.entity.passive.EntityAnimal.class;
			List<? extends EntityLivingBase> candidates = world.selectEntitiesWithinAABB(type,
					mob.boundingBox.expand(range, range, range), entity -> mob.isPrey(entity));
			EntityLivingBase best = null;
			double bestDistance = range * range;
			for(int i = 0; i < candidates.size() && i < 64; i++) {
				EntityLivingBase candidate = candidates.get(i);
				double distance = mob.getDistanceSqToEntity(candidate);
				if(distance > bestDistance || disperseTicks > 0 && candidate.getDistanceSq(crowdedX, crowdedY, crowdedZ) < 256D) continue;
				if(!mob.canEntityBeSeen(candidate) && !sensesPrey(candidate)) continue;
				if(!GeminiPopulation.canWoodcrawlerHunt(mob, candidate)) { beginDispersal(candidate); continue; }
				best = candidate; bestDistance = distance;
			}
			if(best != null) return best;
		}
		return null;
	}
	private void beginDispersal(EntityLivingBase crowded) {
		if(disperseTicks > 0) return;
		crowdedX = crowded.posX; crowdedY = crowded.posY; crowdedZ = crowded.posZ;
		disperseTicks = 300; disperseDelay = 0;
	}
	private void disperse() {
		mob.setHuntState(EntityWoodcrawler.SEARCH);
		if(disperseDelay > 0) return;
		disperseDelay = 40;
		// Keep each hunter's search sector stable while it walks; recomputing the
		// bearing from its moving position would rotate the destination every retry.
		double base = mob.getEntityId() * 2.399963D;
		double best = -Double.MAX_VALUE, bx = mob.posX, by = mob.posY, bz = mob.posZ;
		for(int i = 0; i < 8; i++) {
			double angle = base + i * Math.PI / 4D;
			double x = MathHelper.floor_double(crowdedX + Math.cos(angle) * 18) + 0.5D;
			double z = MathHelper.floor_double(crowdedZ + Math.sin(angle) * 18) + 0.5D;
			for(int dy = -2; dy <= 2; dy++) {
				double y = MathHelper.floor_double(mob.posY) + dy;
				if(!standable(x, y, z)) continue;
				double score = mob.shelterScore(x, y, z) * 0.1D - mob.getDistanceSq(x, y, z) * 0.005D - i * 12;
				if(score > best) { best = score; bx = x; by = y; bz = z; }
			}
		}
		if(best > -Double.MAX_VALUE) { pathDelay = 0; moveTo(bx, by, bz, 1.2D); }
	}

	private void remember(EntityLivingBase target) {
		if(square(knownX - target.posX) + square(knownZ - target.posZ) > 4) {
			huntTicks = 0; bestHuntDistance = Double.MAX_VALUE;
			// Finish a committed entrance even if the occupant walks deeper indoors.
			if(!breakingDoor && !hasEntrance) resetEntrances();
		}
		knownX = target.posX;
		knownY = target.posY;
		knownZ = target.posZ;
		memoryTicks = GeminiConfig.woodcrawlerMemorySeconds * 20;
	}

	private void forget() {
		mob.setAttackTarget(null);
		mob.setHuntState(EntityWoodcrawler.WANDER);
		mob.getNavigator().clearPathEntity();
		clearDoor();
		memoryTicks = stalkTicks = huntTicks = pathFailures = observeTicks = 0;
		doorwayTicks = 0;
		retaliationTicks = 0;
		hasEntrance = hasGoal = pursuing = entranceDoor = false;
		bestHuntDistance = Double.MAX_VALUE;
		resetEntrances();
	}

	private void moveTo(double x, double y, double z, double speed) {
		if(mob.isHazardAt(x, y, z)) return;
		currentSpeed += MathHelper.clamp_double(speed - currentSpeed, -0.14D, 0.08D);
		speed = currentSpeed;
		goalX = x;
		goalY = y;
		goalZ = z;
		hasGoal = true;
		mob.setMovementGoal(x, y, z);
		Vec3 approach = mob.getClimbWaypoint(x, y, z);
		if(approach != null) { x = approach.xCoord; y = approach.yCoord; z = approach.zCoord; mob.setCrouching(true); }
		if(approach != null && mob.getDistanceSq(x, y, z) < 6.25D) {
			// Native paths stop short of deep ladder shafts and fence cells.
			// Let collision-resolved attachment guide the final approach to the obstacle.
			mob.getNavigator().clearPathEntity();
			mob.getMoveHelper().setMoveTo(x, y, z, speed);
			pathFailures = 0;
			return;
		}
		mob.getNavigator().setSpeed(speed);
		if(pathDelay > 0) return;
		pathDelay = 20 + mob.getRNG().nextInt(10) + Math.min(40, pathFailures * 5);
		// Short dark detours guide native navigation; a hunt can still cross a lit patch.
		double dx = x - mob.posX, dz = z - mob.posZ, distance = Math.sqrt(dx * dx + dz * dz);
		if(distance > 5 && Math.abs(y - mob.posY) < 2) {
			double nx = mob.posX + dx / distance * 4, nz = mob.posZ + dz / distance * 4;
			int best = mob.lightAt(MathHelper.floor_double(nx), MathHelper.floor_double(mob.posY + 1), MathHelper.floor_double(nz));
			for(int side = -1; side <= 1; side += 2) {
				double sx = nx - dz / distance * 3 * side, sz = nz + dx / distance * 3 * side;
				int light = mob.lightAt(MathHelper.floor_double(sx), MathHelper.floor_double(mob.posY + 1), MathHelper.floor_double(sz));
				if(light + 2 < best && standable(sx, mob.posY, sz)) { x = sx; y = mob.posY; z = sz; best = light; }
			}
		}
		int ix = MathHelper.floor_double(x), iz = MathHelper.floor_double(z);
		int mx = MathHelper.floor_double(mob.posX), mz = MathHelper.floor_double(mob.posZ);
		if(!world.blockExists(ix, MathHelper.floor_double(y), iz)) {
			pathFailures = Math.min(20, pathFailures + 1); return;
		}
		// Alternate bounded flank probes after failures rather than driving directly into a solid wall.
		if(pathFailures >= 2 && !hasEntrance) {
			double angle = Math.atan2(z - mob.posZ, x - mob.posX) + (++routeAttempt % 2 == 0 ? 1 : -1) * Math.PI / 3;
			double sx = mob.posX + Math.cos(angle) * 4, sz = mob.posZ + Math.sin(angle) * 4;
			if(standable(sx, mob.posY, sz)) { x = sx; y = mob.posY; z = sz; }
		}
		if(!mob.onGround && !mob.isInWater()) return;
		int radius = GeminiConfig.woodcrawlerDetectionRange + 8;
		PathEntity path = world.checkChunksExist(mx - radius, 0, mz - radius, mx + radius, 255, mz + radius)
				? world.getEntityPathToXYZ(mob, MathHelper.floor_double(x), (int) y, MathHelper.floor_double(z),
						GeminiConfig.woodcrawlerDetectionRange, true, true, true, true)
				: findLoadedPath(x, y, z);
		mob.getNavigator().setPath(path, speed);
		PathPoint end = path == null ? null : path.getFinalPathPoint();
		boolean complete = end != null && square(end.xCoord + 0.5D - x) + square(end.zCoord + 0.5D - z) < 4
				&& Math.abs(end.yCoord - y) < 2;
		if(complete) pathFailures = 0; else pathFailures = Math.min(20, pathFailures + 1);
		if(path != null) for(int i = path.getCurrentPathIndex(); i < Math.min(path.getCurrentPathLength(), path.getCurrentPathIndex() + 6); i++) {
			PathPoint point = path.getPathPointFromIndex(i);
			if(mob.isHazardAt(point.xCoord + 0.5D, point.yCoord, point.zCoord + 0.5D)) { mob.getNavigator().clearPathEntity(); return; }
		}
		if(hasEntrance && pathFailures >= 2 && !complete) {
			// Reject the inaccessible face, retain the hunt and try another entrance on the next sweep.
			hasEntrance = false; entranceDoor = false; entranceDistance = Double.MAX_VALUE;
		}
	}
	/** Same native pathfinder, but a loaded corridor cache can be asymmetric at a chunk edge. */
	private PathEntity findLoadedPath(double x, double y, double z) {
		int mx = MathHelper.floor_double(mob.posX), mz = MathHelper.floor_double(mob.posZ);
		int tx = MathHelper.floor_double(x), tz = MathHelper.floor_double(z);
		int radius = GeminiConfig.woodcrawlerDetectionRange + 8;
		for(int margin = 8; margin >= 0; margin -= 2) {
			int minX = Math.min(mx, tx) - margin, maxX = Math.max(mx, tx) + margin;
			int minZ = Math.min(mz, tz) - margin, maxZ = Math.max(mz, tz) + margin;
			if(!world.checkChunksExist(minX, 0, minZ, maxX, 255, maxZ)) continue;
			ChunkCache cache = new ChunkCache(world, minX, MathHelper.floor_double(mob.posY) - radius, minZ,
					maxX, MathHelper.floor_double(mob.posY) + radius, maxZ, 0);
			return new PathFinder(cache, true, true, true, true).createEntityPathTo(mob, tx, (int) y, tz,
					GeminiConfig.woodcrawlerDetectionRange);
		}
		return null;
	}

	private void wander() {
		if(wanderDelay > 0) { wanderDelay--; return; }
		wanderDelay = 80 + mob.getRNG().nextInt(100);
		for(int i = 0; i < 6; i++) {
			int x = MathHelper.floor_double(mob.posX) + mob.getRNG().nextInt(17) - 8;
			int z = MathHelper.floor_double(mob.posZ) + mob.getRNG().nextInt(17) - 8;
			if(!world.blockExists(x, 0, z) || !WoodcrawlerSpawning.isForest(world, x, z)) continue;
			int y = MathHelper.floor_double(mob.posY) + mob.getRNG().nextInt(5) - 2;
			if(!standable(x + 0.5D, y, z + 0.5D)) continue;
			pathDelay = 0;
			moveTo(x + 0.5D, y, z + 0.5D, 0.65D);
			break;
		}
	}

	private boolean indoors(double x, double y, double z) {
		int ix = MathHelper.floor_double(x), iy = MathHelper.floor_double(y) + 2, iz = MathHelper.floor_double(z);
		if(!world.blockExists(ix, iy, iz) || world.canBlockSeeTheSky(ix, iy, iz)) return false;
		// Leaves alone are forest cover, not an occupied building. Look for a nearby solid roof.
		for(int dy = 0; dy < 5; dy++) {
			Block roof = world.getBlock(ix, iy + dy, iz);
			if(roof.getMaterial().blocksMovement() && roof.getMaterial() != Material.leaves
					&& !roof.isWood(world, ix, iy + dy, iz)) return true;
		}
		return false;
	}

	/** Scan a 17x17x3 shell at the remembered occupied position, 32 cells/tick, once per search. */
	private void searchEntrances() {
		if(hasEntrance) {
			if(entranceDoor && isClosedWoodenDoor(entranceDoorX, entranceDoorY, entranceDoorZ)) return;
			if(mob.getDistanceSq(entranceX, entranceY, entranceZ) >= 1) return;
			hasEntrance = entranceDoor = false;
		}
		if(entranceCursor >= 17 * 17 * 3) {
			if(entranceDelay > 0) return;
			resetEntrances();
		}
		for(int budget = 0; budget < 32 && entranceCursor < 17 * 17 * 3; budget++) {
			int index = entranceCursor++;
			int x = MathHelper.floor_double(knownX) + index % 17 - 8;
			int z = MathHelper.floor_double(knownZ) + index / 17 % 17 - 8;
			int y = MathHelper.floor_double(knownY) + index / (17 * 17) - 1;
			if(!world.checkChunksExist(x - 1, y, z - 1, x + 1, y + 3, z + 1)) continue;
			Block block = world.getBlock(x, y, z);
			boolean door = isClosedWoodenDoor(x, y, z);
			if(door && doorBackoff > 0 && x == doorX && y == doorY && z == doorZ) continue;
			// Native paths already handle open passages; these candidates also recover partial paths at facades.
			boolean opening = block == Blocks.air && world.getBlock(x, y + 1, z) == Blocks.air
					&& world.getBlock(x, y + 2, z).getMaterial().blocksMovement()
					&& (world.getBlock(x - 1, y, z).isNormalCube() || world.getBlock(x, y, z - 1).isNormalCube());
			if(!door && !opening) continue;
			for(int side = 0; side < 4; side++) {
				double sx = x + 0.5D + (side == 0 ? -1 : side == 1 ? 1 : 0);
				double sz = z + 0.5D + (side == 2 ? -1 : side == 3 ? 1 : 0);
				// Favor the near face and entrances that lead toward the remembered occupant.
				double distance = mob.getDistanceSq(sx, y, sz) + (square(x + 0.5D - knownX) + square(z + 0.5D - knownZ)) * 0.2D;
				if(distance >= entranceDistance || !standable(sx, y, sz)) continue;
				if(door && !reachesDoorFrom(sx, y, sz, x, y, z)) continue;
				entranceDistance = distance;
				entranceX = sx;
				entranceY = y;
				entranceZ = sz;
				hasEntrance = true;
				entranceDoor = door;
				entranceDoorX = x; entranceDoorY = y; entranceDoorZ = z;
			}
		}
		if(entranceCursor >= 17 * 17 * 3) entranceDelay = 80;
	}
	private void resetEntrances() {
		hasEntrance = entranceDoor = false;
		entranceCursor = entranceDelay = 0;
		entranceDistance = Double.MAX_VALUE;
	}

	private boolean standable(double x, double y, double z) {
		int ix = MathHelper.floor_double(x), iy = MathHelper.floor_double(y), iz = MathHelper.floor_double(z);
		return world.checkChunksExist(ix - 1, iy - 1, iz - 1, ix + 1, iy + 2, iz + 1)
				&& !mob.isHazardAt(x, y, z) && world.getBlock(ix, iy - 1, iz).isNormalCube()
				&& world.getCollidingBoundingBoxes(mob, AxisAlignedBB.getBoundingBox(x - EntityWoodcrawler.CRAWLING_WIDTH / 2D, y, z - EntityWoodcrawler.CRAWLING_WIDTH / 2D,
						x + EntityWoodcrawler.CRAWLING_WIDTH / 2D, y + EntityWoodcrawler.CRAWLING_HEIGHT, z + EntityWoodcrawler.CRAWLING_WIDTH / 2D)).isEmpty();
	}

	private boolean interactWithDoor() {
		if(!breakingDoor) {
			if(mob.ticksExisted % 5 != 0) return false;
			int mx = MathHelper.floor_double(mob.posX), my = MathHelper.floor_double(mob.posY), mz = MathHelper.floor_double(mob.posZ);
			for(int i = 0; i < 75; i++) {
				int x = mx + i % 5 - 2, z = mz + i / 5 % 5 - 2, y = my + i / 25 - 1;
				if(!isClosedWoodenDoor(x, y, z) || doorBackoff > 0 && x == doorX && y == doorY && z == doorZ
						|| mob.getDistanceSq(x + 0.5D, y, z + 0.5D) > 4 || !canReachDoor(x, y, z)
						|| !(entranceDoor && x == entranceDoorX && y == entranceDoorY && z == entranceDoorZ)
						&& !doorOnPath(x, y, z) && !doorSeparatesPrey(x, y, z)) continue;
				doorX = x; doorY = y; doorZ = z;
				breakingDoor = true; doorTicks = 0; doorDamage = -1; knockDelay = 0;
				memoryTicks = Math.max(memoryTicks, 120);
				mob.onDoorInteraction();
				pursuing = true;
				break;
			}
		}
		if(!breakingDoor) return false;
		if(!isClosedWoodenDoor(doorX, doorY, doorZ)) {
			clearDoor(); resetEntrances(); pathDelay = 0; doorwayTicks = 100;
			return false;
		}
		if(mob.getDistanceSq(doorX + 0.5D, doorY, doorZ + 0.5D) > 6
				|| !canReachDoor(doorX, doorY, doorZ)) {
			clearDoor();
			resetEntrances(); pathDelay = 0;
			return false;
		}
		mob.setCrouching(true);
		mob.setHuntState(EntityWoodcrawler.BANG);
		mob.setClimbing(false);
		mob.getNavigator().clearPathEntity();
		mob.getLookHelper().setLookPosition(doorX + 0.5D, doorY + 1D, doorZ + 0.5D, 30, 30);
		if(knockDelay == 0) {
			world.playAuxSFX(1010, doorX, doorY, doorZ, 0);
			knockDelay = doorTicks < 36 ? 12 : 20;
		}
		++doorTicks;
		boolean griefing = world.getGameRules().getGameRuleBooleanValue("mobGriefing");
		int damage = griefing && doorTicks >= 36 ? Math.min(9, (doorTicks - 36) * 10 / 64) : -1;
		if(damage != doorDamage) {
			world.destroyBlockInWorldPartially(mob.getEntityId(), doorX, doorY, doorZ, damage);
			doorDamage = damage;
		}
		if(doorTicks >= 100 && griefing) {
			Block door = world.getBlock(doorX, doorY, doorZ);
			int meta = world.getBlockMetadata(doorX, doorY, doorZ);
			if(mob.breakHuntDoor(doorX, doorY, doorZ)) {
				world.playAuxSFX(1012, doorX, doorY, doorZ, 0);
				world.playAuxSFX(2001, doorX, doorY, doorZ, Block.getIdFromBlock(door) | meta << 12);
				huntTicks = pathFailures = 0;
				doorwayTicks = 100; // Enter the new opening before considering a stationary wall probe.
			} else doorBackoff = 100;
			clearDoor(); resetEntrances(); pathDelay = 0;
			mob.setHuntState(EntityWoodcrawler.SEARCH);
			moveTo(knownX, knownY, knownZ, 2.5D);
			return true;
		}
		// Harass without damage when griefing is disabled, then investigate another route.
		if(doorTicks >= 200) {
			clearDoor(); doorBackoff = 100; resetEntrances(); pathDelay = 0;
			mob.setHuntState(EntityWoodcrawler.SEARCH);
		}
		return true;
	}
	private boolean doorOnPath(int x, int y, int z) {
		PathEntity path = mob.getNavigator().getPath();
		if(path == null) return false;
		for(int i = path.getCurrentPathIndex(); i < Math.min(path.getCurrentPathLength(), path.getCurrentPathIndex() + 6); i++) {
			PathPoint point = path.getPathPointFromIndex(i);
			if(point.xCoord == x && point.zCoord == z && Math.abs(point.yCoord - y) <= 1) return true;
		}
		return false;
	}

	private boolean isClosedWoodenDoor(int x, int y, int z) {
		if(!world.blockExists(x, y, z)) return false;
		Block block = world.getBlock(x, y, z);
		return block instanceof BlockDoor && block.getMaterial() == Material.wood
				&& (world.getBlockMetadata(x, y, z) & 8) == 0 && !((BlockDoor) block).func_150015_f(world, x, y, z);
	}

	private boolean canReachDoor(int x, int y, int z) {
		return reachesDoorFrom(mob.posX, mob.posY, mob.posZ, x, y, z);
	}
	private boolean reachesDoorFrom(double sx, double sy, double sz, int x, int y, int z) {
		MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(sx, sy + 1, sz),
				Vec3.createVectorHelper(x + 0.5D, y + 1, z + 0.5D), false, true, false);
		return hit == null || hit.blockX == x && hit.blockZ == z && (hit.blockY == y || hit.blockY == y + 1);
	}
	private boolean doorSeparatesPrey(int x, int y, int z) {
		MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(mob.posX, mob.posY + 1, mob.posZ),
				Vec3.createVectorHelper(knownX, knownY + 1, knownZ), false, true, false);
		return hit != null && hit.blockX == x && hit.blockZ == z && (hit.blockY == y || hit.blockY == y + 1);
	}
	private boolean woodenDoorBetween() {
		MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(mob.posX, mob.posY + 1, mob.posZ),
				Vec3.createVectorHelper(knownX, knownY + 1, knownZ), false, true, false);
		if(hit == null) return false;
		Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
		return block instanceof BlockDoor && block.getMaterial() == Material.wood;
	}

	private void clearDoor() {
		if(breakingDoor && doorDamage >= 0 && !world.isRemote)
			world.destroyBlockInWorldPartially(mob.getEntityId(), doorX, doorY, doorZ, -1);
		breakingDoor = false;
		doorTicks = 0; doorDamage = -1;
		if(mob.getHuntState() == EntityWoodcrawler.BANG) mob.setHuntState(EntityWoodcrawler.SEARCH);
	}

	private void updateClimbing() {
		if(hasGoal) mob.setMovementGoal(goalX, goalY, goalZ);
	}

	private static double square(double value) { return value * value; }
}
