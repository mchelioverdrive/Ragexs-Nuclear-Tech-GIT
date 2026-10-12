package com.hbm.explosion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.TreeMap;

import com.hbm.config.BombConfig;
import com.hbm.main.MainRegistry;
import com.hbm.explosion.ExplosionTerrainAccess.Access;
import com.hbm.explosion.ExplosionTerrainAccess.Snapshot;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.LongHashMap;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class ExplosionNukeRayBatched {
	private static final int WORK_VERSION = 3;
	// Stage 5 adds optional request/checkpoint fields to the stage 3 mask payload.
	// x occupies the low signed int, z the high signed int, as in chunkXZ2Int.
	private LongHashMap chunks = new LongHashMap();
	private ChunkMask firstChunk, lastChunk;
	private final TreeMap<Integer, DistanceBucket> ordering = new TreeMap<Integer, DistanceBucket>();
	int posX, posY, posZ;
	World world;
	int strength, length;
	private final int rayLength;
	private boolean contained;
	private int surfaceY;
	int gspNumMax, gspNum;
	double gspX, gspY;
	public boolean isAusf3Complete;

	private boolean rayActive, rayFinishing, rayRecorded, hasTip;
	private double rayX, rayY, rayZ;
	private int rayStep;
	private float rayStrength, tipX, tipY, tipZ;
	// Only the unfinished ray retains qualifying chunks; completed rays retain no endpoints.
	private LongHashMap rayChunks = new LongHashMap();
	private ArrayList<Long> rayChunkKeys = new ArrayList<Long>();
	private long collectionTick = Long.MIN_VALUE;
	private int raysThisTick;
	private final Access terrain;
	private ExplosionRayWorker.Lifecycle lifecycle;
	private ExplosionRayWorker.Batch batch;
	private boolean snapshotPending, workerFailed;
	private int snapshotX, snapshotY, snapshotZ;
	private static final java.util.Set<ExplosionNukeRayBatched> workers = new java.util.HashSet<ExplosionNukeRayBatched>();

	private boolean buildActive, buildRestricted, buildInChunk;
	private long buildChunk;
	private int buildStep, buildTipX, buildTipY, buildTipZ;
	private double buildLength, buildX, buildY, buildZ;

	private boolean removalActive;
	private long removalKey;
	private int removalSection, removalBit;

	// New saves decode a section at a time. Saving during decoding retains the original snapshot.
	private NBTTagCompound restoring;
	private int restorePhase, restoreIndex, restoreSection;
	// Endpoint NBT exists only at the old-save boundary, never as the live terrain engine.
	private NBTTagCompound legacy;
	private int legacyPhase, legacyIndex, legacyPoint;
	private boolean legacyChunkStarted;

	private static class SectionMask {
		final BitSet destruction = new BitSet();
		BitSet tips;
	}
	private static class ChunkMask {
		final long key;
		final int distance;
		final SectionMask[] sections = new SectionMask[16];
		ChunkMask previous, next, orderPrevious, orderNext;
		ChunkMask(long key, int distance) { this.key = key; this.distance = distance; }
	}
	private static class DistanceBucket {
		ChunkMask first, last;
	}

	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length) {
		this(world, x, y, z, strength, speed, length, false, 256);
	}
	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length, boolean contained, int surfaceY) {
		this.world = world; posX = x; posY = y; posZ = z;
		terrain = new Access(world);
		this.strength = strength; this.length = length; this.contained = contained; this.surfaceY = surfaceY;
		rayLength = (int)Math.ceil(strength);
		gspNumMax = (int)(2.5 * Math.PI * Math.pow(strength, 2));
		gspNum = 1; gspX = Math.PI;
	}

	private void generateGspUp() {
		if(gspNum < gspNumMax) {
			int k = gspNum + 1;
			double hk = -1.0 + 2.0 * (k - 1.0) / (gspNumMax - 1.0);
			gspX = Math.acos(hk);
			double lon = gspY + 3.6 / Math.sqrt(gspNumMax) / Math.sqrt(1.0 - hk * hk);
			gspY = lon % (Math.PI * 2);
		} else { gspX = 0.0; gspY = 0.0; }
		gspNum++;
	}

	public boolean hasPendingWork() { return restoring != null || legacy != null || !isAusf3Complete || firstChunk != null; }
	public boolean canWork(long tick, int count) {
		if(!terrain.ready(tick) || batch != null && batch.result == null && batch.failure == null) return false;
		return restoring != null || legacy != null || isAusf3Complete || gspNum > gspNumMax || collectionTick != tick || raysThisTick < count;
	}
	private int distance(long key) {
		return Math.abs((posX >> 4) - (int)key) + Math.abs((posZ >> 4) - (int)(key >> 32));
	}
	private int enter(long key) {
		return Math.max(0, Math.min(Math.abs(posX - ((int)key << 4)), Math.abs(posZ - ((int)(key >> 32) << 4))) - 16);
	}
	private ChunkMask chunkMask(long key) {
		ChunkMask chunk = (ChunkMask)chunks.getValueByKey(key);
		if(chunk != null) return chunk;
		chunk = new ChunkMask(key, distance(key)); chunks.add(key, chunk);
		chunk.previous = lastChunk;
		if(lastChunk == null) firstChunk = chunk; else lastChunk.next = chunk;
		lastChunk = chunk;
		DistanceBucket bucket = ordering.get(chunk.distance);
		if(bucket == null) { bucket = new DistanceBucket(); ordering.put(chunk.distance, bucket); }
		chunk.orderPrevious = bucket.last;
		if(bucket.last == null) bucket.first = chunk; else bucket.last.orderNext = chunk;
		bucket.last = chunk;
		return chunk;
	}
	private void removeChunk(ChunkMask chunk) {
		chunks.remove(chunk.key);
		if(chunk.previous == null) firstChunk = chunk.next; else chunk.previous.next = chunk.next;
		if(chunk.next == null) lastChunk = chunk.previous; else chunk.next.previous = chunk.previous;
		DistanceBucket bucket = ordering.get(chunk.distance);
		if(chunk.orderPrevious == null) bucket.first = chunk.orderNext; else chunk.orderPrevious.orderNext = chunk.orderNext;
		if(chunk.orderNext == null) bucket.last = chunk.orderPrevious; else chunk.orderNext.orderPrevious = chunk.orderPrevious;
		if(bucket.first == null) ordering.remove(chunk.distance);
	}
	private void addRayChunk(long key) {
		if(!rayChunks.containsItem(key)) { rayChunks.add(key, Boolean.TRUE); rayChunkKeys.add(key); }
	}
	private void record(int x, int y, int z, boolean tip) {
		if(y < 0 || y >= 256) return;
		ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4));
		SectionMask section = chunk.sections[y >> 4];
		if(section == null) chunk.sections[y >> 4] = section = new SectionMask();
		int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
		section.destruction.set(bit);
		if(tip) {
			if(section.tips == null) section.tips = new BitSet();
			section.tips.set(bit); // OR preserves tip priority over every normal hit.
		}
	}

	/** Legacy API retained for callers outside the governed entity path. */
	public void collectTip(int count) { collectTip(count, Long.MAX_VALUE, collectionTick + 1); }
	public void collectTip(int count, long deadline, long tick) {
		if(!restore(deadline) || !migrate(deadline) || isAusf3Complete) return;
		if(collectionTick != tick) { collectionTick = tick; raysThisTick = 0; }
		if(batch != null || BombConfig.explosionWorkerThreads > 0 && !workerFailed) {
			collectWorker(count, deadline);
			return;
		}
		// Cache chunk reads only within this slice: another job/tick may change storage.
		Chunk sampledChunk = null;
		while(gspNum <= gspNumMax && raysThisTick < count && ExplosionWorkScheduler.before(deadline)) {
			if(!rayActive) {
				rayX = Math.sin(gspX) * Math.cos(gspY); rayZ = Math.sin(gspX) * Math.sin(gspY); rayY = Math.cos(gspX);
				rayStep = 0; rayStrength = strength; hasTip = false; rayActive = true; rayFinishing = rayRecorded = false;
			}
			while(!rayFinishing && ExplosionWorkScheduler.before(deadline)) {
				if(rayStep >= rayLength || rayStep > length) { rayFinishing = true; break; }
				float x = (float)(posX + rayX * rayStep), y = (float)(posY + rayY * rayStep), z = (float)(posZ + rayZ * rayStep);
				if(contained && y >= surfaceY) { rayFinishing = true; break; }
				// Stop on leaving storage, but permit a source outside it to approach it.
				if((y < 0 && (posY >= 0 || rayY <= 0)) || (y >= 256 && (posY < 256 || rayY >= 0))) { rayFinishing = true; break; }
				int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
				// Null sections contain only vanilla air. Do not trust isEmpty(): custom air
				// blocks can have resistance. Keep the world border's air behavior as well.
				if(iy >= 0 && iy < 256 && ix >= -30000000 && iz >= -30000000 && ix < 30000000 && iz < 30000000) {
					if(sampledChunk == null || sampledChunk.xPosition != ix >> 4 || sampledChunk.zPosition != iz >> 4) {
						sampledChunk = ExplosionTerrainAccess.chunk(terrain, ix >> 4, iz >> 4, deadline);
						if(sampledChunk == null) return;
						if(!ExplosionWorkScheduler.before(deadline)) return;
					}
					if(sampledChunk.getBlockStorageArray()[iy >> 4] == null && masqueradeResistance(Blocks.air) == 0F) {
						rayStep = endOfEmptySection(ix >> 4, iy >> 4, iz >> 4);
						// The old post-sample length check stops after length - 1.
						if(rayStep >= rayLength || rayStep >= Math.max(1, length)) rayFinishing = true;
						continue;
					}
				}
				double fac = (100 - (double)rayStep / rayLength * 100) * 0.07D;
				Block block = world.getBlock(ix, iy, iz);
				if(!ExplosionWorkScheduler.before(deadline)) return;
				if(!block.getMaterial().isLiquid()) rayStrength -= Math.pow(masqueradeResistance(block), 7.5D - fac);
				// Resistance callbacks are indivisible. Commit their sample before yielding.
				if(rayStrength > 0 && block != Blocks.air && !block.getMaterial().isLiquid()) {
					tipX = x; tipY = y; tipZ = z; hasTip = true;
					addRayChunk(ChunkCoordIntPair.chunkXZ2Int(ix >> 4, iz >> 4));
				}
				if(rayStrength <= 0 || rayStep + 1 >= length || rayStep == rayLength - 1) rayFinishing = true;
				rayStep++;
			}
			if(!rayFinishing || !ExplosionWorkScheduler.before(deadline)) return;
			if(hasTip && !rayRecorded) {
				if(!buildActive) beginBuild(tipX, tipY, tipZ, 0, false, 0L, false);
				if(!buildMask(deadline)) return;
			}
			rayRecorded = true;
			// Advance only after the entire reconstruction is represented by masks.
			while(!rayChunkKeys.isEmpty() && ExplosionWorkScheduler.before(deadline)) {
				rayChunks.remove(rayChunkKeys.remove(rayChunkKeys.size() - 1));
			}
			if(!rayChunkKeys.isEmpty() || !ExplosionWorkScheduler.before(deadline)) return;
			rayActive = rayFinishing = rayRecorded = hasTip = false;
			generateGspUp(); raysThisTick++;
		}
		// Distance buckets were maintained on insertion; no final large sort/queue copy.
		if(gspNum > gspNumMax && ExplosionWorkScheduler.before(deadline)) isAusf3Complete = true;
	}

	private void collectWorker(int count, long deadline) {
		if(!ExplosionWorkScheduler.before(deadline)) return;
		if(lifecycle == null || lifecycle.cancelled) {
			lifecycle = new ExplosionRayWorker.Lifecycle(terrain.id, terrain.worldLifecycleId); workers.add(this);
		}
		if(batch != null) {
			ExplosionRayWorker.Result result = batch.result;
			Throwable failure = batch.failure;
			if(result == null && failure == null) return;
			Snapshot snapshot = batch.snapshot;
			if(failure != null) {
				MainRegistry.logger.error("Explosion terrain worker failed; retaining checkpoint and using server-thread fallback", failure);
				workerFailed = true; lifecycle.cancelled = true;
				batch.cancel(); batch = null; snapshotPending = false; workers.remove(this); return;
			}
			if(lifecycle.cancelled || result.jobId != lifecycle.jobId || result.lifecycleId != lifecycle.id || result.worldLifecycleId != terrain.worldLifecycleId || snapshot != null && (!snapshot.valid || result.snapshotId != snapshot.id || snapshot.worldLifecycleId != terrain.worldLifecycleId)) {
				// The input checkpoint has never advanced. Retry all rejected traversal
				// and mask work, including a result that reports an unfinished ray.
				batch.dispose(); batch = null; snapshotPending = false; terrain.defer(); return;
			}
			if(snapshot != null) {
				if(result.completedRays > 0) { rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>(); }
				if(result.qualified) addRayChunk(ChunkCoordIntPair.chunkXZ2Int(snapshot.chunkX, snapshot.chunkZ));
				if(!result.destruction.isEmpty()) {
					ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(snapshot.chunkX, snapshot.chunkZ));
					SectionMask section = chunk.sections[snapshot.sectionY];
					if(section == null) chunk.sections[snapshot.sectionY] = section = new SectionMask();
					section.destruction.or(result.destruction);
					if(!result.tips.isEmpty()) {
						if(section.tips == null) section.tips = new BitSet();
						section.tips.or(result.tips);
					}
				}
			}
			ExplosionRayWorker.State state = result.state;
			if(snapshot == null && result.completedRays > 0) { rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>(); }
			gspNum = state.gspNum; gspX = state.gx; gspY = state.gy; raysThisTick += result.completedRays;
			rayActive = state.active; rayRecorded = state.recorded; rayX = state.dx; rayY = state.dy; rayZ = state.dz;
			rayStep = state.step; rayStrength = state.strength; rayFinishing = state.finishing;
			hasTip = state.hasTip; tipX = state.tx; tipY = state.ty; tipZ = state.tz;
			buildStep = state.buildStep; buildActive = state.building; buildLength = state.buildLength;
			buildX = state.bx; buildY = state.by; buildZ = state.bz;
			buildTipX = state.tipX; buildTipY = state.tipY; buildTipZ = state.tipZ;
			buildRestricted = buildInChunk = false;
			snapshotPending = result.missing || snapshot != null;
			if(result.missing) { snapshotX = result.chunkX; snapshotY = result.sectionY; snapshotZ = result.chunkZ; }
			else if(snapshot != null) { snapshotX = snapshot.chunkX; snapshotY = snapshot.sectionY; snapshotZ = snapshot.chunkZ; }
			// Mask OR and scalar cursor adoption form one bounded server checkpoint.
			batch.dispose(); batch = null;
		}
		if(!ExplosionWorkScheduler.before(deadline)) return;
		if(BombConfig.explosionWorkerThreads == 0 || workerFailed) return;
		if(gspNum > gspNumMax) { isAusf3Complete = true; workers.remove(this); return; }
		if(raysThisTick >= count) return;
		if(!ExplosionRayWorker.available(rayActive || buildActive)) { terrain.defer(); return; }
		Snapshot snapshot = null;
		if(snapshotPending) {
			snapshot = ExplosionTerrainAccess.snapshot(terrain, snapshotX, snapshotY, snapshotZ, deadline);
			if(snapshot == null) return;
		}
		if(!ExplosionWorkScheduler.before(deadline)) return;
		ExplosionRayWorker.State state = new ExplosionRayWorker.State();
		state.posX = posX; state.posY = posY; state.posZ = posZ; state.length = length; state.rayLength = rayLength;
		state.initialStrength = strength; state.gspNum = gspNum; state.gspNumMax = gspNumMax; state.gx = gspX; state.gy = gspY;
		state.active = rayActive; state.recorded = rayRecorded; state.rayLimit = Math.min(ExplosionRayWorker.MAX_BATCH_RAYS, count - raysThisTick);
		state.surfaceY = surfaceY; state.contained = contained; state.step = rayStep; state.strength = rayStrength;
		state.dx = rayX; state.dy = rayY; state.dz = rayZ; state.finishing = rayFinishing; state.hasTip = hasTip;
		state.tx = tipX; state.ty = tipY; state.tz = tipZ; state.airResistance = masqueradeResistance(Blocks.air);
		state.building = buildActive; state.buildStep = buildStep; state.buildLength = buildLength;
		state.bx = buildX; state.by = buildY; state.bz = buildZ; state.tipX = buildTipX; state.tipY = buildTipY; state.tipZ = buildTipZ;
		boolean eligible = snapshot != null && rayChunks.containsItem(ChunkCoordIntPair.chunkXZ2Int(snapshot.chunkX, snapshot.chunkZ));
		if(!ExplosionWorkScheduler.before(deadline)) return;
		batch = ExplosionRayWorker.submit(state, snapshot, eligible, lifecycle);
		if(batch == null) terrain.defer();
	}

	public void cancelWorkers() {
		if(lifecycle != null) lifecycle.cancelled = true;
		if(batch != null) { batch.cancel(); batch = null; }
		terrain.cancel(); workers.remove(this);
		// All uncommitted inputs remain in the original ray/build fields for saving.
	}
	public static void unloadWorkers(World world) {
		for(ExplosionNukeRayBatched engine : workers.toArray(new ExplosionNukeRayBatched[0])) if(engine.world == world) engine.cancelWorkers();
	}
	public static void unloadChunkWorkers(World world, int x, int z) {
		for(ExplosionNukeRayBatched engine : workers.toArray(new ExplosionNukeRayBatched[0])) {
			if(engine.world == world && engine.posX >> 4 == x && engine.posZ >> 4 == z) engine.cancelWorkers();
		}
	}
	public static void shutdownWorkers() {
		for(ExplosionNukeRayBatched engine : workers.toArray(new ExplosionNukeRayBatched[0])) engine.cancelWorkers();
		ExplosionRayWorker.shutdown();
	}
	public static void startWorkers() { ExplosionRayWorker.startServer(); }

	/** Binary search integer sample indices, not voxel/DDA boundaries. Float rounding
	 * and the original integer steps are unchanged; zero-resistance air has no effect. */
	private int endOfEmptySection(int chunkX, int sectionY, int chunkZ) {
		int low = rayStep + 1, high = Math.min(rayLength, Math.max(1, length));
		while(low < high) {
			int middle = low + (high - low) / 2;
			float x = (float)(posX + rayX * middle), y = (float)(posY + rayY * middle), z = (float)(posZ + rayZ * middle);
			if((int)Math.floor(x) >> 4 == chunkX && (int)Math.floor(y) >> 4 == sectionY && (int)Math.floor(z) >> 4 == chunkZ && (!contained || y < surfaceY)) low = middle + 1;
			else high = middle;
		}
		return low;
	}

	private void beginBuild(float x, float y, float z, int step, boolean restricted, long chunk, boolean inChunk) {
		// Preserve float subtraction before promotion, exactly as old Vec3 construction.
		double dx = x - posX, dy = y - posY, dz = z - posZ;
		// Vec3.lengthVector uses MathHelper's float-rounded square root in 1.7.10.
		buildLength = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
		buildX = dx / buildLength; buildY = dy / buildLength; buildZ = dz / buildLength;
		buildTipX = (int)Math.floor(x); buildTipY = (int)Math.floor(y); buildTipZ = (int)Math.floor(z);
		buildStep = step; buildRestricted = restricted; buildChunk = chunk; buildInChunk = inChunk; buildActive = true;
	}
	private boolean buildMask(long deadline) {
		long sampledKey = 0L;
		boolean sampled = false, eligible = false;
		int entryStep = 0;
		while(buildStep < buildLength && ExplosionWorkScheduler.before(deadline)) {
			int x = (int)Math.floor(posX + buildX * buildStep), y = (int)Math.floor(posY + buildY * buildStep), z = (int)Math.floor(posZ + buildZ * buildStep);
			long key = ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4);
			if(buildRestricted) {
				// Old-save conversion alone must honor per-chunk reconstruction cursors.
				if(key != buildChunk) {
					if(buildInChunk) { buildStep = (int)Math.ceil(buildLength); break; }
					buildStep++; continue;
				}
				buildInChunk = true;
			} else {
				if(!sampled || sampledKey != key) {
					sampledKey = key; sampled = true; eligible = rayChunks.containsItem(key); entryStep = enter(key);
				}
				// A positive collection sample is not itself the old destruction set.
				if(!eligible || buildStep < entryStep) { buildStep++; continue; }
			}
			if(y >= 0 && y < 256) {
				if(ExplosionTerrainAccess.chunk(terrain, x >> 4, z >> 4, deadline) == null) return false;
				Block block = world.getBlock(x, y, z);
				if(!ExplosionWorkScheduler.before(deadline)) return false;
				if(!world.isAirBlock(x, y, z) && !block.getMaterial().isLiquid()) {
					record(x, y, z, x == buildTipX && y == buildTipY && z == buildTipZ);
				}
			}
			buildStep++;
		}
		if(buildStep < buildLength) return false;
		buildActive = false;
		return true;
	}

	public void processChunk() { processChunk(Long.MAX_VALUE); }
	public void processChunk(long deadline) {
		if(!restore(deadline) || !migrate(deadline) || !isAusf3Complete || firstChunk == null || !ExplosionWorkScheduler.before(deadline)) return;
		ChunkMask chunk = removalActive ? (ChunkMask)chunks.getValueByKey(removalKey) : null;
		if(chunk == null) {
			chunk = ordering.firstEntry().getValue().first;
			removalKey = chunk.key; removalSection = removalBit = 0; removalActive = true;
		}
		if(ExplosionTerrainAccess.chunk(terrain, (int)chunk.key, (int)(chunk.key >> 32), deadline) == null) return;
		while(removalSection < 16 && ExplosionWorkScheduler.before(deadline)) {
			SectionMask section = chunk.sections[removalSection];
			if(section == null) { removalSection++; removalBit = 0; continue; }
			int bit = section.destruction.nextSetBit(removalBit);
			if(bit < 0) { chunk.sections[removalSection++] = null; removalBit = 0; continue; }
			removalBit = bit;
			int x = ((int)chunk.key << 4) + (bit & 15), y = (removalSection << 4) + (bit >> 8), z = ((int)(chunk.key >> 32) << 4) + ((bit >> 4) & 15);
			Block block = world.getBlock(x, y, z);
			if(!ExplosionWorkScheduler.before(deadline)) return;
			boolean change = !block.getMaterial().isLiquid() && !world.isAirBlock(x, y, z);
			if(!ExplosionWorkScheduler.before(deadline)) return;
			if(change) {
				if(section.tips != null && section.tips.get(bit)) handleTip(x, y, z);
				else world.setBlock(x, y, z, Blocks.air, 0, 2);
			}
			// Callbacks, tile cleanup, lighting and sync finish before the bit/cursor
			// advances, even if the indivisible world removal overruns the slice.
			section.destruction.clear(bit);
			if(section.tips != null) section.tips.clear(bit);
			removalBit = bit + 1;
			if(section.destruction.isEmpty()) { chunk.sections[removalSection++] = null; removalBit = 0; }
		}
		if(removalSection == 16) { removeChunk(chunk); removalActive = false; }
	}

	protected void handleTip(int x, int y, int z) { world.setBlock(x, y, z, Blocks.air, 0, 3); }
	public static float masqueradeResistance(Block block) {
		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3;
		return block.getExplosionResistance(null);
	}

	public void readFromNBT(NBTTagCompound tag) {
		cancelWorkers(); terrain.read(tag);
		snapshotPending = tag.getBoolean("snapshotPending"); snapshotX = tag.getInteger("snapshotX"); snapshotY = tag.getInteger("snapshotY"); snapshotZ = tag.getInteger("snapshotZ");
		chunks = new LongHashMap(); firstChunk = lastChunk = null; ordering.clear();
		rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>();
		gspNum = tag.getInteger("gspNum"); gspX = tag.getDouble("gspX"); gspY = tag.getDouble("gspY");
		if(tag.hasKey("contained")) contained = tag.getBoolean("contained");
		if(tag.hasKey("surfaceY")) surfaceY = tag.getInteger("surfaceY");
		rayActive = tag.getBoolean("rayActive"); rayFinishing = tag.getBoolean("rayFinishing");
		rayX = tag.getDouble("rayX"); rayY = tag.getDouble("rayY"); rayZ = tag.getDouble("rayZ");
		rayStep = tag.getInteger("rayStep"); rayStrength = tag.getFloat("rayStrength");
		collectionTick = Long.MIN_VALUE; raysThisTick = 0; isAusf3Complete = false;
		restoring = legacy = null; restorePhase = restoreIndex = restoreSection = 0;
		legacyPhase = legacyIndex = legacyPoint = 0; legacyChunkStarted = false;
		buildActive = removalActive = rayRecorded = hasTip = false;
		if(tag.getInteger("workVersion") < WORK_VERSION) {
			legacy = tag;
			if(tag.hasKey("lastPos")) {
				NBTTagCompound tip = tag.getCompoundTag("lastPos");
				tipX = tip.getFloat("x"); tipY = tip.getFloat("y"); tipZ = tip.getFloat("z"); hasTip = true;
			}
		} else {
			restoring = tag;
			rayRecorded = tag.getBoolean("rayRecorded");
			hasTip = tag.getBoolean("hasTip"); tipX = tag.getFloat("tipX"); tipY = tag.getFloat("tipY"); tipZ = tag.getFloat("tipZ");
			buildActive = tag.getBoolean("buildActive"); buildRestricted = tag.getBoolean("buildRestricted"); buildInChunk = tag.getBoolean("buildInChunk");
			buildChunk = tag.getLong("buildChunk"); buildStep = tag.getInteger("buildStep"); buildLength = tag.getDouble("buildLength");
			buildX = tag.getDouble("buildX"); buildY = tag.getDouble("buildY"); buildZ = tag.getDouble("buildZ");
			buildTipX = tag.getInteger("buildTipX"); buildTipY = tag.getInteger("buildTipY"); buildTipZ = tag.getInteger("buildTipZ");
			removalActive = tag.getBoolean("removalActive"); removalKey = tag.getLong("removalKey");
			removalSection = tag.getInteger("removalSection"); removalBit = tag.getInteger("removalBit");
			if(tag.hasKey("legacy")) {
				legacy = tag.getCompoundTag("legacy"); legacyPhase = tag.getInteger("legacyPhase"); legacyIndex = tag.getInteger("legacyIndex");
				legacyPoint = tag.getInteger("legacyPoint"); legacyChunkStarted = tag.getBoolean("legacyChunkStarted");
			}
		}
	}

	private boolean restore(long deadline) {
		while(restoring != null && ExplosionWorkScheduler.before(deadline)) {
			if(restorePhase == 0) {
				NBTTagList savedChunks = restoring.getTagList("chunks", 10);
				if(restoreIndex == savedChunks.tagCount()) { restorePhase++; restoreIndex = 0; continue; }
				NBTTagCompound item = savedChunks.getCompoundTagAt(restoreIndex);
				NBTTagList sections = item.getTagList("sections", 10);
				if(restoreSection == sections.tagCount()) { restoreIndex++; restoreSection = 0; continue; }
				NBTTagCompound data = sections.getCompoundTagAt(restoreSection++);
				int sectionY = data.getInteger("y");
				if(sectionY < 0 || sectionY >= 16) continue;
				SectionMask section = new SectionMask();
				section.destruction.or(BitSet.valueOf(data.getByteArray("destruction")));
				if(section.destruction.length() > 4096) section.destruction.clear(4096, section.destruction.length());
				if(section.destruction.isEmpty()) continue;
				if(data.hasKey("tips")) {
					section.tips = BitSet.valueOf(data.getByteArray("tips")); section.tips.and(section.destruction);
				}
				chunkMask(readCoord(item)).sections[sectionY] = section;
			} else if(restorePhase == 1) {
				int[] keys = restoring.getIntArray("rayChunks");
				if(restoreIndex < keys.length / 2) { addRayChunk(ChunkCoordIntPair.chunkXZ2Int(keys[restoreIndex * 2], keys[restoreIndex * 2 + 1])); restoreIndex++; }
				else restorePhase++;
			} else {
				isAusf3Complete = legacy == null && restoring.getBoolean("complete"); restoring = null;
			}
		}
		return restoring == null;
	}

	/** Budgeted old-save boundary. Completed removals are never re-expanded from endpoints. */
	private boolean migrate(long deadline) {
		while(legacy != null && ExplosionWorkScheduler.before(deadline)) {
			if(legacyPhase < 2) {
				NBTTagList positions = legacy.getTagList(legacyPhase == 0 ? "remaining" : "tips", 10);
				if(legacyIndex == positions.tagCount()) { legacyPhase++; legacyIndex = 0; continue; }
				NBTTagCompound pos = positions.getCompoundTagAt(legacyIndex++);
				int x = pos.getInteger("x"), y = pos.getInteger("y"), z = pos.getInteger("z");
				if(legacyPhase == 0) record(x, y, z, false);
				else if(y >= 0 && y < 256) {
					ChunkMask chunk = (ChunkMask)chunks.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4));
					SectionMask section = chunk == null ? null : chunk.sections[y >> 4];
					int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
					if(section != null && section.destruction.get(bit)) record(x, y, z, true);
				}
			} else if(legacyPhase == 2) {
				NBTTagList savedChunks = legacy.getTagList("chunks", 10);
				if(legacyIndex == savedChunks.tagCount()) { legacyPhase++; legacyIndex = 0; continue; }
				NBTTagCompound item = savedChunks.getCompoundTagAt(legacyIndex);
				long key = readCoord(item);
				boolean current = legacy.hasKey("removalChunk") && readCoord(legacy.getCompoundTag("removalChunk")) == key;
				NBTTagList points = item.getTagList("points", 10);
				if(!legacyChunkStarted) {
					legacyPoint = current ? (legacy.getBoolean("reconstructed") ? points.tagCount() : legacy.getInteger("removalRay")) : 0;
					legacyChunkStarted = true;
				}
				if(legacyPoint >= points.tagCount()) { legacyIndex++; legacyPoint = 0; legacyChunkStarted = false; continue; }
				if(!buildActive) {
					NBTTagCompound tip = points.getCompoundTagAt(legacyPoint);
					boolean partial = current && legacyPoint == legacy.getInteger("removalRay");
					int step = partial && legacy.hasKey("removalStep") ? legacy.getInteger("removalStep") : -1;
					beginBuild(tip.getFloat("x"), tip.getFloat("y"), tip.getFloat("z"), step < 0 ? enter(key) : step, true, key, partial && step >= 0 && legacy.getBoolean("removalInChunk"));
				}
				if(!buildMask(deadline)) return false;
				legacyPoint++;
			} else if(legacyPhase == 3) {
				NBTTagList keys = legacy.getTagList("rayChunks", 10);
				if(legacyIndex < keys.tagCount()) addRayChunk(readCoord(keys.getCompoundTagAt(legacyIndex++)));
				else legacyPhase++;
			} else {
				isAusf3Complete = legacy.getBoolean("complete") && !rayActive;
				// The current old chunk contains only remaining positions and unfinished rays.
				if(legacy.hasKey("removalChunk")) {
					removalKey = readCoord(legacy.getCompoundTag("removalChunk"));
					removalActive = chunks.containsItem(removalKey); removalSection = removalBit = 0;
				}
				legacy = null;
			}
		}
		return legacy == null;
	}

	public void writeToNBT(NBTTagCompound tag) {
		if(restoring != null) {
			for(Object key : restoring.func_150296_c()) tag.setTag((String)key, restoring.getTag((String)key).copy());
			return;
		}
		tag.setInteger("workVersion", WORK_VERSION);
		terrain.write(tag);
		tag.setBoolean("snapshotPending", snapshotPending); tag.setInteger("snapshotX", snapshotX); tag.setInteger("snapshotY", snapshotY); tag.setInteger("snapshotZ", snapshotZ);
		tag.setInteger("gspNum", gspNum); tag.setDouble("gspX", gspX); tag.setDouble("gspY", gspY);
		tag.setBoolean("complete", isAusf3Complete); tag.setBoolean("contained", contained); tag.setInteger("surfaceY", surfaceY);
		tag.setBoolean("rayActive", rayActive); tag.setBoolean("rayFinishing", rayFinishing);
		tag.setBoolean("rayRecorded", rayRecorded);
		tag.setDouble("rayX", rayX); tag.setDouble("rayY", rayY); tag.setDouble("rayZ", rayZ);
		tag.setInteger("rayStep", rayStep); tag.setFloat("rayStrength", rayStrength);
		tag.setBoolean("hasTip", hasTip); tag.setFloat("tipX", tipX); tag.setFloat("tipY", tipY); tag.setFloat("tipZ", tipZ);
		int[] keys = new int[rayChunkKeys.size() * 2];
		for(int i = 0; i < rayChunkKeys.size(); i++) { long key = rayChunkKeys.get(i); keys[i * 2] = (int)key; keys[i * 2 + 1] = (int)(key >> 32); }
		tag.setIntArray("rayChunks", keys);
		tag.setBoolean("buildActive", buildActive); tag.setBoolean("buildRestricted", buildRestricted); tag.setBoolean("buildInChunk", buildInChunk);
		tag.setLong("buildChunk", buildChunk); tag.setInteger("buildStep", buildStep); tag.setDouble("buildLength", buildLength);
		tag.setDouble("buildX", buildX); tag.setDouble("buildY", buildY); tag.setDouble("buildZ", buildZ);
		tag.setInteger("buildTipX", buildTipX); tag.setInteger("buildTipY", buildTipY); tag.setInteger("buildTipZ", buildTipZ);
		tag.setBoolean("removalActive", removalActive); tag.setLong("removalKey", removalKey);
		tag.setInteger("removalSection", removalSection); tag.setInteger("removalBit", removalBit);
		NBTTagList savedChunks = new NBTTagList();
		for(ChunkMask chunk = firstChunk; chunk != null; chunk = chunk.next) {
			NBTTagCompound item = new NBTTagCompound(); item.setInteger("x", (int)chunk.key); item.setInteger("z", (int)(chunk.key >> 32));
			NBTTagList sections = new NBTTagList();
			for(int y = 0; y < 16; y++) {
				SectionMask section = chunk.sections[y];
				if(section == null || section.destruction.isEmpty()) continue;
				NBTTagCompound data = new NBTTagCompound(); data.setInteger("y", y);
				data.setByteArray("destruction", section.destruction.toByteArray());
				if(section.tips != null && !section.tips.isEmpty()) data.setByteArray("tips", section.tips.toByteArray());
				sections.appendTag(data);
			}
			item.setTag("sections", sections); savedChunks.appendTag(item);
		}
		tag.setTag("chunks", savedChunks);
		if(legacy != null) {
			// Keep old input only until migration completes, with its exact cursor and
			// already-produced masks. Re-saving before any update is lossless too.
			tag.setTag("legacy", legacy.copy()); tag.setInteger("legacyPhase", legacyPhase); tag.setInteger("legacyIndex", legacyIndex);
			tag.setInteger("legacyPoint", legacyPoint); tag.setBoolean("legacyChunkStarted", legacyChunkStarted);
		}
	}
	private static long readCoord(NBTTagCompound tag) { return ChunkCoordIntPair.chunkXZ2Int(tag.getInteger("x"), tag.getInteger("z")); }
}
