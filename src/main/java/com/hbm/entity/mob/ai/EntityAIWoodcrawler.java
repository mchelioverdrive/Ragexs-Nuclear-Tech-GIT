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
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;

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
		if(mob.isBurrowing() || mob.isGrabbing()) {
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
		EntityLivingBase target = mob.getAttackTarget();
		if(target != null && (++huntTicks > 1200 || pathFailures >= 5
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
			mob.setHuntState(EntityWoodcrawler.WANDER);
			mob.setCrouching(false);
			wander();
			updateClimbing();
			return;
		}

		boolean visible = mob.canEntityBeSeen(target);
		if(visible) remember(target);
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
		boolean ready = stalkTicks >= GeminiConfig.woodcrawlerStalkSeconds * 20;
		double distance = mob.getDistanceSq(knownX, knownY, knownZ);
		if(visible) {
			clearDoor();
			if(ready && mob.ticksExisted % 20 == 0 && mob.tryBurrow(target)) return;
			if(ready && distance < 81) {
				mob.setHuntState(EntityWoodcrawler.CHASE);
				moveTo(knownX, knownY, knownZ, 1.55D);
				mob.beginStrike(target);
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
			searchEntrances();
			moveTo(hasEntrance ? entranceX : knownX, hasEntrance ? entranceY : knownY,
					hasEntrance ? entranceZ : knownZ, ready ? 1.0D : 0.6D);
			interactWithDoor(ready);
		}
		updateClimbing();
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
					&& (mob.canEntityBeSeen(candidate) || indoors(candidate.posX, candidate.posY, candidate.posZ))) {
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
		goalX = x;
		goalY = y;
		goalZ = z;
		hasGoal = true;
		mob.getNavigator().setSpeed(speed);
		if(pathDelay > 0) return;
		pathDelay = 20 + mob.getRNG().nextInt(10);
		int ix = MathHelper.floor_double(x), iz = MathHelper.floor_double(z);
		int radius = GeminiConfig.woodcrawlerDetectionRange + 10;
		int mx = MathHelper.floor_double(mob.posX), mz = MathHelper.floor_double(mob.posZ);
		// Native PathNavigate uses followRange + 8; do not let ChunkCache load missing chunks.
		if(!world.checkChunksExist(mx - radius, 0, mz - radius, mx + radius, 255, mz + radius)
				|| !world.blockExists(ix, MathHelper.floor_double(y), iz)) { pathFailures++; return; }
		if(mob.getNavigator().tryMoveToXYZ(x, y, z, speed)) pathFailures = 0;
		else pathFailures++;
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
		return world.blockExists(ix, iy - 1, iz) && world.getBlock(ix, iy - 1, iz).isNormalCube()
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
		if(knockDelay == 0) {
			world.playAuxSFX(1010, doorX, doorY, doorZ, 0);
			knockDelay = 120 + mob.getRNG().nextInt(160);
		}
		if(!ready || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")) { clearDoor(); return; }
		mob.getNavigator().clearPathEntity();
		if(++doorTicks % 12 == 0) world.destroyBlockInWorldPartially(mob.getEntityId(), doorX, doorY, doorZ, doorTicks / 12 - 1);
		if(doorTicks < 120) return;
		Block block = world.getBlock(doorX, doorY, doorZ);
		int meta = world.getBlockMetadata(doorX, doorY, doorZ);
		EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
		boolean allowed = block.canEntityDestroy(world, doorX, doorY, doorZ, mob)
				&& !block.hasTileEntity(meta) && world.canMineBlock(actor, doorX, doorY, doorZ)
				&& world.canMineBlock(actor, doorX, doorY + 1, doorZ)
				&& !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(doorX, doorY, doorZ, world, block, meta, actor))
				&& !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(doorX, doorY + 1, doorZ, world,
						world.getBlock(doorX, doorY + 1, doorZ), world.getBlockMetadata(doorX, doorY + 1, doorZ), actor));
		if(allowed && GeminiSpawning.canAct(mob, 1) && isClosedWoodenDoor(doorX, doorY, doorZ)
				&& world.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
			world.func_147480_a(doorX, doorY, doorZ, true);
			world.playAuxSFX(1012, doorX, doorY, doorZ, 0);
			hasEntrance = false;
			pathDelay = 0;
		}
		clearDoor();
		doorBackoff = 100;
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
		if(breakingDoor) world.destroyBlockInWorldPartially(mob.getEntityId(), doorX, doorY, doorZ, -1);
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
