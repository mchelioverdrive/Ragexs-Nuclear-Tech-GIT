package com.hbm.entity.mob.ai;

import java.util.List;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.WoodcrawlerSpawning;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** One local controller; no changes to the shared mob AI or global pathfinder. */
public final class EntityAIWoodcrawler extends EntityAIBase {

	private final EntityWoodcrawler mob;
	private final World world;
	private int scanDelay, pathDelay, stalkTicks, memoryTicks, retreatTicks, wanderDelay;
	private int entranceCursor, doorX, doorY, doorZ, doorTicks, doorBackoff, knockDelay;
	private int climbTicks;
	private int huntTicks, pathFailures;
	private double knownX, knownY, knownZ, goalX, goalY, goalZ, climbStartY;
	private double entranceDistance = Double.MAX_VALUE;
	private double entranceX, entranceY, entranceZ;
	private boolean sawTarget, hasGoal, hasEntrance, breakingDoor;
	private double currentSpeed = 0.6D;
	private int shelterDelay;

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
		pathDelay = scanDelay = wanderDelay = climbTicks = doorBackoff = knockDelay = 0;
		entranceCursor = 0;
		entranceDistance = Double.MAX_VALUE;
	}

	@Override
	public void updateTask() {
		if(mob.isBurrowing() || mob.isGrabbing() || mob.isProbing()) {
			mob.getNavigator().clearPathEntity();
			mob.setClimbing(false);
			clearDoor();
			return;
		}
		if(mob.isCaptureBusy()) {
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
		if(mob.wantsShelter() || mob.avoidsExposure()) {
			// Strong exposure overrides pursuit; milder light is handled by route weighting.
			clearDoor(); mob.setHuntState(EntityWoodcrawler.RETREAT); mob.setClimbing(false);
			seekShelter();
			return;
		}
		EntityLivingBase target = mob.getAttackTarget();
		if(target != null && (++huntTicks > 1200 || pathFailures >= 8 && !breakingDoor
				|| !world.blockExists(MathHelper.floor_double(target.posX), MathHelper.floor_double(target.posY), MathHelper.floor_double(target.posZ)))) {
			forget();
			scanDelay = 100;
			target = null;
		}
		if(mob.hasCapturedVillager() || !mob.isPrey(target)
				|| mob.getDistanceSqToEntity(target) > square(GeminiConfig.woodcrawlerDetectionRange + 8)) {
			if(target != null) forget();
			target = null;
		}
		if(target == null && !mob.hasCapturedVillager() && scanDelay == 0) {
			scanDelay = 20 + mob.getRNG().nextInt(10);
			target = findTarget();
			if(target != null) {
				mob.setAttackTarget(target);
				remember(target);
				stalkTicks = 0;
				retreatTicks = 0;
				sawTarget = mob.canEntityBeSeen(target);
				if(sawTarget) com.hbm.world.gemini.GeminiPopulation.alert(mob, target);
			}
		}
		if(target == null) {
			mob.mimicVillager(false);
			mob.setHuntState(EntityWoodcrawler.WANDER);
			mob.setCrouching(false);
			wander();
			updateClimbing();
			return;
		}

		boolean visible = mob.canEntityBeSeen(target);
		if(visible || mob.ticksExisted % 10 == 0 && audible(target)) remember(target);
		else if(--memoryTicks <= 0) {
			forget();
			scanDelay = 80;
			return;
		}
		if(sawTarget && !visible) {
			retreatTicks = 40;
			double dx = mob.posX - knownX, dz = mob.posZ - knownZ;
			double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
			goalX = mob.posX + dx / length * 4;
			goalY = mob.posY;
			goalZ = mob.posZ + dz / length * 4;
			pathDelay = 0;
			clearDoor();
		}
		sawTarget = visible;
		mob.setCrouching(!visible && indoors(knownX, knownY, knownZ));
		mob.getLookHelper().setLookPosition(knownX, knownY + 1, knownZ, 20, 20);
		if(retreatTicks > 0) {
			retreatTicks--;
			mob.setHuntState(EntityWoodcrawler.RETREAT);
			moveTo(goalX, goalY, goalZ, 0.9D);
			mob.setClimbing(false);
			return;
		}

		stalkTicks = Math.min(stalkTicks + 1, GeminiConfig.woodcrawlerStalkSeconds * 20);
		mob.mimicVillager(mob.getHuntState() == EntityWoodcrawler.STALK || indoors(knownX, knownY, knownZ));
		boolean ready = stalkTicks >= GeminiConfig.woodcrawlerStalkSeconds * 20;
		double distance = mob.getDistanceSq(knownX, knownY, knownZ);
		if(visible) {
			clearDoor();
			if(ready && mob.ticksExisted % 20 == 0 && mob.tryBurrow(target)) return;
			if(ready && distance < 81) {
				mob.setHuntState(EntityWoodcrawler.CHASE);
				mob.beginStrike(target);
				if(!mob.isGrabbing()) {
					double fleeing = (target.motionX * (target.posX - mob.posX) + target.motionZ * (target.posZ - mob.posZ)) / Math.max(1, Math.sqrt(distance));
					moveTo(knownX, knownY, knownZ, distance < 9 ? 0.9D : fleeing > 0.04D ? 3.875D : 3.5D);
				}
			} else {
				mob.setHuntState(EntityWoodcrawler.STALK);
				// Follow at a distance, then close deliberately after the initial stalking period.
				double desired = ready ? 5 : 10;
				if(distance > square(desired)) {
					double dx = mob.posX - knownX, dz = mob.posZ - knownZ;
					double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
					moveTo(knownX + dx / length * desired, knownY, knownZ + dz / length * desired, 0.60D);
				} else mob.getNavigator().clearPathEntity();
			}
		} else {
			mob.setHuntState(ready ? EntityWoodcrawler.SEARCH : EntityWoodcrawler.STALK);
			if(ready && distance <= 36 && memoryTicks > 0 && target.getDistanceSq(knownX, knownY, knownZ) <= 2.25D
					&& mob.beginProbe(target)) { clearDoor(); return; }
			searchEntrances();
			if(!breakingDoor) moveTo(hasEntrance ? entranceX : knownX, hasEntrance ? entranceY : knownY,
					hasEntrance ? entranceZ : knownZ, ready ? 2.5D : 0.6D);
			interactWithDoor(ready);
		}
		updateClimbing();
	}

	private boolean audible(EntityLivingBase target) {
		if(mob.getDistanceSqToEntity(target) > 64 || target.isSneaking()) return false;
		double dx = target.posX - target.prevPosX, dz = target.posZ - target.prevPosZ;
		return dx * dx + dz * dz > 0.0025D || target.hurtTime > 0;
	}
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
		pathDelay = pathFailures = climbTicks = 0;
		hasGoal = hasEntrance = false;
		entranceCursor = 0;
		entranceDistance = Double.MAX_VALUE;
		clearDoor();
	}

	private EntityLivingBase findTarget() {
		double range = GeminiConfig.woodcrawlerDetectionRange;
		List<EntityLivingBase> candidates = world.selectEntitiesWithinAABB(EntityLivingBase.class,
				mob.boundingBox.expand(range, range, range), entity -> mob.isPrey(entity));
		EntityLivingBase best = null;
		double bestDistance = range * range;
		// A bounded local query, at most 64 detailed occupancy/visibility checks per scan.
		for(int i = 0; i < candidates.size() && i < 64; i++) {
			EntityLivingBase candidate = candidates.get(i);
			double distance = mob.getDistanceSqToEntity(candidate);
			boolean preferred = candidate instanceof EntityVillager && !(best instanceof EntityVillager);
			if(distance <= range * range && (preferred || distance < bestDistance && (!(best instanceof EntityVillager) || candidate instanceof EntityVillager))
					&& (mob.canEntityBeSeen(candidate) || audible(candidate))) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private void remember(EntityLivingBase target) {
		if(square(knownX - target.posX) + square(knownZ - target.posZ) > 4) {
			entranceCursor = 0;
			hasEntrance = false;
			entranceDistance = Double.MAX_VALUE;
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
		memoryTicks = stalkTicks = retreatTicks = huntTicks = pathFailures = 0;
		hasEntrance = hasGoal = sawTarget = false;
	}

	private void moveTo(double x, double y, double z, double speed) {
		if(mob.isHazardAt(x, y, z)) return;
		currentSpeed += MathHelper.clamp_double(speed - currentSpeed, -0.14D, 0.08D);
		speed = currentSpeed;
		goalX = x;
		goalY = y;
		goalZ = z;
		hasGoal = true;
		mob.getNavigator().setSpeed(speed);
		if(pathDelay > 0) return;
		pathDelay = 20 + mob.getRNG().nextInt(10);
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
		int radius = GeminiConfig.woodcrawlerDetectionRange + 10;
		int mx = MathHelper.floor_double(mob.posX), mz = MathHelper.floor_double(mob.posZ);
		// Native PathNavigate uses followRange + 8; do not let ChunkCache load missing chunks.
		if(!world.checkChunksExist(mx - radius, 0, mz - radius, mx + radius, 255, mz + radius)
				|| !world.blockExists(ix, MathHelper.floor_double(y), iz)) { pathFailures++; return; }
		if(mob.getNavigator().tryMoveToXYZ(x, y, z, speed)) pathFailures = 0;
		else pathFailures++;
		net.minecraft.pathfinding.PathEntity path = mob.getNavigator().getPath();
		if(path != null) for(int i = path.getCurrentPathIndex(); i < Math.min(path.getCurrentPathLength(), path.getCurrentPathIndex() + 6); i++) {
			net.minecraft.pathfinding.PathPoint point = path.getPathPointFromIndex(i);
			if(mob.isHazardAt(point.xCoord + 0.5D, point.yCoord, point.zCoord + 0.5D)) { mob.getNavigator().clearPathEntity(); return; }
		}
		if(mob.getNavigator().noPath() && mob.getDistanceSq(x, y, z) < 100) {
			mob.getMoveHelper().setMoveTo(x, y, z, speed);
		}
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
		for(int budget = 0; budget < 32 && entranceCursor < 17 * 17 * 3; budget++) {
			int index = entranceCursor++;
			int x = MathHelper.floor_double(knownX) + index % 17 - 8;
			int z = MathHelper.floor_double(knownZ) + index / 17 % 17 - 8;
			int y = MathHelper.floor_double(knownY) + index / (17 * 17) - 1;
			if(!world.checkChunksExist(x - 1, y, z - 1, x + 1, y + 3, z + 1)) continue;
			Block block = world.getBlock(x, y, z);
			boolean door = block instanceof BlockDoor && block.getMaterial() == Material.wood
					&& (world.getBlockMetadata(x, y, z) & 8) == 0;
			if(door && doorBackoff > 0 && x == doorX && y == doorY && z == doorZ) continue;
			// Native paths already handle open passages; these candidates also recover partial paths at facades.
			boolean opening = block == Blocks.air && world.getBlock(x, y + 1, z) == Blocks.air
					&& world.getBlock(x, y + 2, z).getMaterial().blocksMovement()
					&& (world.getBlock(x - 1, y, z).isNormalCube() || world.getBlock(x, y, z - 1).isNormalCube());
			if(!door && !opening) continue;
			for(int side = 0; side < 4; side++) {
				double sx = x + 0.5D + (side == 0 ? -1 : side == 1 ? 1 : 0);
				double sz = z + 0.5D + (side == 2 ? -1 : side == 3 ? 1 : 0);
				double distance = mob.getDistanceSq(sx, y, sz);
				if(distance >= entranceDistance || !standable(sx, y, sz)) continue;
				entranceDistance = distance;
				entranceX = sx;
				entranceY = y;
				entranceZ = sz;
				hasEntrance = true;
			}
		}
		// Once inside an opening, allow the native path to carry on toward the remembered occupant.
		if(hasEntrance && mob.getDistanceSq(entranceX, entranceY, entranceZ) < 1
				&& !mob.isCollidedHorizontally) hasEntrance = false;
	}

	private boolean standable(double x, double y, double z) {
		int ix = MathHelper.floor_double(x), iy = MathHelper.floor_double(y), iz = MathHelper.floor_double(z);
		return world.checkChunksExist(ix - 1, iy - 1, iz - 1, ix + 1, iy + (int) mob.height + 1, iz + 1)
				&& !mob.isHazardAt(x, y, z) && world.getBlock(ix, iy - 1, iz).isNormalCube()
				&& world.getCollidingBoundingBoxes(mob, AxisAlignedBB.getBoundingBox(x - 0.43D, y, z - 0.43D,
						x + 0.43D, y + mob.height, z + 0.43D)).isEmpty();
	}

	private void interactWithDoor(boolean ready) {
		if(doorBackoff > 0) return;
		if(!breakingDoor) {
			if(mob.ticksExisted % 10 != 0) return;
			int mx = MathHelper.floor_double(mob.posX), my = MathHelper.floor_double(mob.posY), mz = MathHelper.floor_double(mob.posZ);
			for(int i = 0; i < 27; i++) {
				int x = mx + i % 3 - 1, z = mz + i / 3 % 3 - 1, y = my + i / 9 - 1;
				if(!isClosedWoodenDoor(x, y, z) || mob.getDistanceSq(x + 0.5D, y, z + 0.5D) > 4 || !canReachDoor(x, y, z)) continue;
				doorX = x; doorY = y; doorZ = z;
				breakingDoor = true;
				break;
			}
		}
		if(!breakingDoor) return;
		if(!isClosedWoodenDoor(doorX, doorY, doorZ) || mob.getDistanceSq(doorX + 0.5D, doorY, doorZ + 0.5D) > 6
				|| !canReachDoor(doorX, doorY, doorZ)) {
			clearDoor();
			return;
		}
		mob.setHuntState(EntityWoodcrawler.BANG);
		mob.getNavigator().clearPathEntity();
		mob.getLookHelper().setLookPosition(doorX + 0.5D, doorY + 1D, doorZ + 0.5D, 30, 30);
		if(knockDelay == 0) {
			world.playAuxSFX(1010, doorX, doorY, doorZ, 0);
			knockDelay = 12 + mob.getRNG().nextInt(32);
		}
		// Harassment never destroys a door, even with mobGriefing enabled.
		if(++doorTicks < 160) return;
		clearDoor();
		doorBackoff = 200;
		hasEntrance = false; entranceCursor = 0; entranceDistance = Double.MAX_VALUE; pathDelay = 0;
	}

	private boolean isClosedWoodenDoor(int x, int y, int z) {
		if(!world.blockExists(x, y, z)) return false;
		Block block = world.getBlock(x, y, z);
		return block instanceof BlockDoor && block.getMaterial() == Material.wood
				&& (world.getBlockMetadata(x, y, z) & 8) == 0 && !((BlockDoor) block).func_150015_f(world, x, y, z);
	}

	private boolean canReachDoor(int x, int y, int z) {
		MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(mob.posX, mob.posY + 1, mob.posZ),
				Vec3.createVectorHelper(x + 0.5D, y + 1, z + 0.5D), false, true, false);
		return hit == null || hit.blockX == x && hit.blockZ == z && (hit.blockY == y || hit.blockY == y + 1);
	}

	private void clearDoor() {
		breakingDoor = false;
		doorTicks = 0;
	}

	private void updateClimbing() {
		boolean climbing = false;
		if(hasGoal && mob.isCollidedHorizontally && !breakingDoor && climbTicks < 80) {
			double dx = goalX - mob.posX, dz = goalZ - mob.posZ;
			double length = Math.sqrt(dx * dx + dz * dz);
			if(length > 0.1D) {
				int x = MathHelper.floor_double(mob.posX + dx / length * 0.8D);
				int y = MathHelper.floor_double(mob.posY + 0.5D);
				int z = MathHelper.floor_double(mob.posZ + dz / length * 0.8D);
				if(world.blockExists(x, y, z)) {
					Block wall = world.getBlock(x, y, z);
					if(climbTicks == 0) climbStartY = mob.posY;
					climbing = wall.isNormalCube() && !(wall instanceof BlockDoor)
							&& wall.getMaterial() != Material.leaves && mob.posY < climbStartY + 6;
					if(climbing) {
						climbTicks++;
						mob.motionY = 0.18D;
						mob.motionX += dx / length * 0.025D;
						mob.motionZ += dz / length * 0.025D;
						mob.fallDistance = 0;
					}
				}
			}
		}
		if(!mob.isCollidedHorizontally && mob.onGround) climbTicks = 0;
		mob.setClimbing(climbing);
	}

	private static double square(double value) { return value * value; }
}
