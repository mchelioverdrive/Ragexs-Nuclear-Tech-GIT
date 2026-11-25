package com.hbm.entity.mob.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.world.gemini.GeminiSpawning;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Idle surface calls for both disguises, subordinate to the existing stalking AI. */
public final class EntityAIFakePersonHilltop extends EntityAIBase {

	private static final int VIEW_DISTANCE = 10;
	private static final int SCREAM_TICK = 12, STAND_TICKS = 84;
	private static final int TRAVEL_TICKS = 400, REJECT_TICKS = 6000;
	private static final double PREY_RANGE_SQ = 32D * 32D;
	private static final int[][] VIEW_DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

	private final EntityFakePerson mob;
	private final World world;
	private final Map<ChunkCoordinates, Long> rejected = new HashMap<>();
	private long nextAttempt = -1, travelUntil;
	private Hilltop destination;
	private PathEntity route;
	private int routeMinX, routeMinZ, routeMaxX, routeMaxZ;
	private int standTicks;
	private EntityPlayer lookTarget;

	public EntityAIFakePersonHilltop(EntityFakePerson mob) {
		this.mob = mob;
		this.world = mob.worldObj;
		setMutexBits(3);
	}

	@Override
	public boolean shouldExecute() {
		if(!canAct()) return false;
		long now = world.getTotalWorldTime();
		if(nextAttempt < 0) scheduleAttempt(now);
		if(now < nextAttempt || !mob.onGround) return false;
		scheduleAttempt(now);
		long time = Math.floorMod(world.getWorldTime(), 24000L);
		boolean night = !world.provider.hasNoSky && time >= 13000L && time < 23000L;
		float chance = GeminiConfig.fakePersonScreamNightProbability * (night ? 1F : 0.25F);
		if(mob.getRNG().nextFloat() >= chance) return false;
		rejected.values().removeIf(until -> until <= now);
		return findDestination();
	}

	@Override
	public void startExecuting() {
		mob.getNavigator().clearPathEntity();
		if(route != null) mob.getNavigator().setPath(route, 0.6D);
		travelUntil = world.getTotalWorldTime() + TRAVEL_TICKS;
		standTicks = 0;
	}

	@Override
	public boolean continueExecuting() {
		if(!canAct() || standTicks >= STAND_TICKS) return false;
		if(atDestination(destination.position)) return true;
		long now = world.getTotalWorldTime();
		if(standTicks > 0) return false;
		if(now < travelUntil && mob.getNavigator().getPath() == route
				&& !mob.getNavigator().noPath() && routeLoaded()) return true;
		reject(destination.position);
		return false;
	}

	@Override
	public void resetTask() {
		if(route != null && mob.getNavigator().getPath() == route) mob.getNavigator().clearPathEntity();
		route = null;
		destination = null;
		lookTarget = null;
	}

	@Override
	public void updateTask() {
		ChunkCoordinates position = destination.position;
		if(!atDestination(position)) return;
		if(standTicks == 0) mob.getNavigator().clearPathEntity();
		if(standTicks % 10 == 0 || standTicks == SCREAM_TICK) lookTarget = findLookTarget();
		if(lookTarget != null) mob.getLookHelper().setLookPositionWithEntity(lookTarget, 30F, 30F);
		else mob.getLookHelper().setLookPosition(destination.viewX, position.posY + mob.getEyeHeight(), destination.viewZ, 30F, 30F);
		if(standTicks++ != SCREAM_TICK) return;
		if(!isStandable(position.posX, position.posY, position.posZ)) {
			reject(position);
			standTicks = STAND_TICKS;
			return;
		}
		boolean player = mob.isPlayerMimic();
		String sound = player ? "hbm:gemini.fake_person.scream_player" : "hbm:gemini.fake_person.scream_villager";
		float pitch = (player ? 0.88F : 0.52F) + mob.getRNG().nextFloat() * 0.16F;
		world.playSoundEffect(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, sound,
				GeminiConfig.fakePersonScreamAudibleDistance / 16F, pitch);
		scheduleAttempt(world.getTotalWorldTime());
	}

	private boolean canAct() {
		if(world.isRemote || !GeminiConfig.fakePersonHilltopScreaming || mob.isRecovering() || mob.isTyping()
				|| !mob.isEntityAlive() || mob.isInWater() || mob.isRiding()) return false;
		EntityLivingBase prey = mob.getAttackTarget();
		return !(mob.canHunt(prey) && mob.getDistanceSqToEntity(prey) <= PREY_RANGE_SQ) && GeminiSpawning.canAct(mob, 1);
	}

	private void scheduleAttempt(long now) {
		int min = GeminiConfig.fakePersonScreamMinIntervalSeconds * 20;
		int max = GeminiConfig.fakePersonScreamMaxIntervalSeconds * 20;
		nextAttempt = now + min + mob.getRNG().nextInt(max - min + 1);
	}

	private void reject(ChunkCoordinates position) {
		long now = world.getTotalWorldTime();
		rejected.put(position, now + REJECT_TICKS);
	}

	private boolean findDestination() {
		int radius = GeminiConfig.fakePersonHilltopSearchRadius;
		int x = MathHelper.floor_double(mob.posX), z = MathHelper.floor_double(mob.posZ);
		Random random = mob.getRNG();
		List<Hilltop> candidates = new ArrayList<>();
		consider(candidates, x, z, radius);
		for(int ring = 1; ring <= 3; ring++) {
			double angle = random.nextDouble() * Math.PI * 2D;
			double distance = radius * (ring - 0.25D) / 3D;
			for(int direction = 0; direction < 4; direction++, angle += Math.PI / 2D) {
				consider(candidates, x + (int)Math.round(Math.cos(angle) * distance),
						z + (int)Math.round(Math.sin(angle) * distance), radius);
			}
		}
		candidates.sort(Comparator.comparingDouble((Hilltop hill) -> hill.score).reversed());
		int pathAttempts = 0;
		for(Hilltop hill : candidates) {
			if(atDestination(hill.position)) {
				destination = hill;
				return true;
			}
			if(pathAttempts >= 2 || rejected.containsKey(hill.position)) continue;
			pathAttempts++;
			PathEntity path = findPath(hill.position);
			if(path == null) {
				reject(hill.position);
				continue;
			}
			destination = hill;
			route = path;
			return true;
		}
		return false;
	}

	private void consider(List<Hilltop> candidates, int x, int z, int radius) {
		int y = world.getHeightValue(x, z);
		if(y < 1) return;
		double distanceSq = mob.getDistanceSq(x + 0.5D, y, z + 0.5D);
		ChunkCoordinates position = new ChunkCoordinates(x, y, z);
		if(distanceSq > radius * radius || rejected.containsKey(position) || !isStandable(x, y, z)) return;
		double eye = y + mob.getEyeHeight();
		int open = 0, downhill = 0, bestDrop = Integer.MIN_VALUE;
		int bestX = x, bestZ = z;
		for(int[] direction : VIEW_DIRECTIONS) {
			int vx = x + VIEW_DISTANCE * direction[0], vz = z + VIEW_DISTANCE * direction[1];
			if(!loadedBetween(x, z, vx, vz, 1)) continue;
			int drop = y - world.getHeightValue(vx, vz);
			if(drop >= 3) downhill++;
			if(world.func_147447_a(Vec3.createVectorHelper(x + 0.5D, eye, z + 0.5D),
					Vec3.createVectorHelper(vx + 0.5D, eye - 0.5D, vz + 0.5D), false, true, false) != null) continue;
			open++;
			if(drop > bestDrop) {
				bestDrop = drop;
				bestX = vx;
				bestZ = vz;
			}
		}
		double climb = y - mob.boundingBox.minY;
		if(open == 0 || bestDrop < 2 || (climb < 3 && downhill < 2)) return;
		double score = open * 3 + downhill * 2 + Math.min(bestDrop, 10) + Math.min(climb, 8) - Math.sqrt(distanceSq) * 0.18D;
		candidates.add(new Hilltop(position, bestX + 0.5D, bestZ + 0.5D, score));
	}

	private boolean isStandable(int x, int y, int z) {
		if(y < 1 || y + mob.height >= world.getActualHeight() || !loadedBetween(x, z, x, z, 1)
				|| !world.canBlockSeeTheSky(x, y, z)) return false;
		Block support = world.getBlock(x, y - 1, z);
		if(!world.isSideSolid(x, y - 1, z, ForgeDirection.UP) || support.isLeaves(world, x, y - 1, z)
				|| support.isWood(world, x, y - 1, z)) return false;
		AxisAlignedBB body = mob.boundingBox.getOffsetBoundingBox(x + 0.5D - mob.posX,
				y - mob.boundingBox.minY, z + 0.5D - mob.posZ);
		return world.func_147461_a(body).isEmpty() && !world.isAnyLiquid(body);
	}

	private boolean atDestination(ChunkCoordinates position) {
		double dx = mob.posX - (position.posX + 0.5D), dz = mob.posZ - (position.posZ + 0.5D);
		return mob.onGround && Math.abs(mob.boundingBox.minY - position.posY) < 0.5D && dx * dx + dz * dz < 0.64D;
	}

	private PathEntity findPath(ChunkCoordinates position) {
		LoadedPathFinder finder = new LoadedPathFinder();
		float distance = (float)mob.getDistance(position.posX + 0.5D, position.posY, position.posZ + 0.5D);
		PathEntity path = finder.createEntityPathTo(mob, position.posX, position.posY, position.posZ, distance + 6F);
		PathPoint end = path == null ? null : path.getFinalPathPoint();
		if(finder.checksRemaining <= 0 || end == null || end.xCoord != position.posX || end.yCoord != position.posY
				|| end.zCoord != position.posZ) return null;
		routeMinX = routeMaxX = MathHelper.floor_double(mob.posX);
		routeMinZ = routeMaxZ = MathHelper.floor_double(mob.posZ);
		for(int i = 0; i < path.getCurrentPathLength(); i++) {
			PathPoint point = path.getPathPointFromIndex(i);
			routeMinX = Math.min(routeMinX, point.xCoord);
			routeMinZ = Math.min(routeMinZ, point.zCoord);
			routeMaxX = Math.max(routeMaxX, point.xCoord);
			routeMaxZ = Math.max(routeMaxZ, point.zCoord);
		}
		return routeLoaded() ? path : null;
	}

	private boolean routeLoaded() {
		// The native navigator may follow a straight shortcut between path nodes.
		int x = MathHelper.floor_double(mob.posX), z = MathHelper.floor_double(mob.posZ);
		return loadedBetween(Math.min(routeMinX, x), Math.min(routeMinZ, z),
				Math.max(routeMaxX, x), Math.max(routeMaxZ, z), 2);
	}

	private boolean loadedBetween(int x, int z, int otherX, int otherZ, int margin) {
		return world.checkChunksExist(Math.min(x, otherX) - margin, 0, Math.min(z, otherZ) - margin,
				Math.max(x, otherX) + margin, 0, Math.max(z, otherZ) + margin);
	}

	private EntityPlayer findLookTarget() {
		EntityPlayer closest = null;
		double best = PREY_RANGE_SQ;
		int x = MathHelper.floor_double(mob.posX), z = MathHelper.floor_double(mob.posZ);
		for(Object entry : world.playerEntities) {
			EntityPlayer player = (EntityPlayer)entry;
			double distance = mob.getDistanceSqToEntity(player);
			if(distance < best && mob.canHunt(player)
					&& loadedBetween(x, z, MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ), 1)
					&& mob.getEntitySenses().canSee(player)) {
				closest = player;
				best = distance;
			}
		}
		return closest;
	}

	private final class LoadedPathFinder extends PathFinder {
		private int checksRemaining = 12000;

		LoadedPathFinder() {
			super(world, true, mob.getNavigator().getCanBreakDoors(), mob.getNavigator().getAvoidsWater(), false);
		}

		@Override
		public int getVerticalOffset(Entity entity, int x, int y, int z, PathPoint size) {
			// Forge 1.7.10 reads the entity's world directly for these tests, even with a ChunkCache.
			if(--checksRemaining <= 0 || !loadedBetween(x, z, x + size.xCoord - 1, z + size.zCoord - 1, 1)) return -3;
			return super.getVerticalOffset(entity, x, y, z, size);
		}
	}

	private static final class Hilltop {
		final ChunkCoordinates position;
		final double viewX, viewZ, score;

		Hilltop(ChunkCoordinates position, double viewX, double viewZ, double score) {
			this.position = position;
			this.viewX = viewX;
			this.viewZ = viewZ;
			this.score = score;
		}
	}
}
