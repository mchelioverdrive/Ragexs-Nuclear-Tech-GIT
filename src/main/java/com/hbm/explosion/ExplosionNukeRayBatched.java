package com.hbm.explosion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.TreeMap;

import com.hbm.config.BombConfig;
import com.hbm.config.GeneralConfig;
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
	// Optional range checkpoints extend the stage 3 mask payload. Old scalar
	// ray checkpoints enter the same allocator without rebuilding committed masks.
	// x occupies the low signed int, z the high signed int, as in chunkXZ2Int.
	private LongHashMap chunks = new LongHashMap();
	private ChunkMask firstChunk, lastChunk;
	private final TreeMap<Integer, DistanceBucket> ordering = new TreeMap<Integer, DistanceBucket>();
	int posX, posY, posZ;
	World world;
	int strength, length;
	private int rayLength;
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
	private static final java.util.Set<ExplosionNukeRayBatched> workers = new java.util.HashSet<ExplosionNukeRayBatched>();
	private final ArrayList<RayRange> ranges = new ArrayList<RayRange>();
	// Server-only coordinates, not retained snapshot references. Successor ranges
	// reuse the explosion's working set without pinning buffers outside the cap.
	private final ArrayList<int[]> corridor = new ArrayList<int[]>();
	private RayRange allocating;
	private int rangeCursor;
	private long completedRays, submittedBatches, acceptedBatches, snapshotRejections, workerFailures, repeatedOperations, workerNanos, sectionMisses, fallbackActivations;
	private long startedAt = System.nanoTime(), collectionFinishedAt, firstRemovalAt, collectionServerNanos, removalServerNanos;
	private long collectionStartedAt = startedAt;
	private String waitReason = "ready";
	private static final int INVALIDATION_RETRIES = 3;

	/** Each disjoint range owns its checkpoint; workers receive a private copy.
	 * The main cursor reserves directions using the original longitude recurrence. */
	private final class RayRange {
		ExplosionRayWorker.State state;
		final int end;
		final Access access = new Access(world);
		ArrayList<Long> qualified = new ArrayList<Long>();
		ExplosionRayWorker.Batch batch;
		boolean sealed, fallback, pending;
		int x, y, z, rejections;
		RayRange(ExplosionRayWorker.State state, int end) { this.state = state; this.end = end; }
	}

	private boolean buildActive, buildRestricted, buildInChunk;
	private long buildChunk;
	private int buildStep, buildTipX, buildTipY, buildTipZ;
	private double buildLength, buildX, buildY, buildZ;

	private boolean removalActive;
	private long removalKey;
	private int removalSection, removalBit;
	private long selectedBlocks, destroyedBlocks, rejectedBatches, lastDiagnosticTick = Long.MIN_VALUE;
	private boolean savedDiagnostics;
	private boolean removalFailureReported;
	public void includeInitializationDelay(long nanos) { startedAt -= Math.max(0L, nanos); }

	public void logStatus(String status) {
		if(isAusf3Complete && selectedBlocks == 0) status += "; no solid samples passed reach/resistance checks";
		if(!GeneralConfig.enableExtendedLogging) return;
		long captures = terrain.snapshotCaptures, reuse = terrain.snapshotReuses;
		for(RayRange range : ranges) { captures += range.access.snapshotCaptures; reuse += range.access.snapshotReuses; }
		long now = System.nanoTime();
		MainRegistry.logger.info("[NUKE TERRAIN] dim=" + world.provider.dimensionId + " at=" + posX + "," + posY + "," + posZ + " strength=" + strength + " reach=" + rayLength + " limit=" + length + " status=" + status
				+ " completedRays=" + completedRays + " remainingRays=" + Math.max(0, gspNumMax - completedRays) + " selected=" + selectedBlocks + " removed=" + destroyedBlocks
				+ " submitted=" + submittedBatches + " accepted=" + acceptedBatches + " rejected=" + rejectedBatches + " snapshotRejections=" + snapshotRejections + " workerFailures=" + workerFailures
				+ " captures=" + captures + " reuse=" + reuse + " sectionMisses=" + sectionMisses + " repeatedOperations=" + repeatedOperations + " fallbacks=" + fallbackActivations + " wait=" + waitReason
				+ " elapsedMs=" + (now - startedAt) / 1000000L + " collectionElapsedMs=" + ((collectionFinishedAt == 0 ? now : collectionFinishedAt) - collectionStartedAt) / 1000000L
				+ " firstRemovalMs=" + (firstRemovalAt == 0 ? -1 : (firstRemovalAt - startedAt) / 1000000L) + " applicationElapsedMs=" + (collectionFinishedAt == 0 ? 0 : (now - collectionFinishedAt) / 1000000L)
				+ " collectionServerWorkMs=" + collectionServerNanos / 1000000L + " applicationServerWorkMs=" + removalServerNanos / 1000000L + " workerCalculationMs=" + workerNanos / 1000000L + " " + terrain.status());
	}

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
		this(world, x, y, z, strength, speed, length, contained, surfaceY, strength);
	}
	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length, boolean contained, int surfaceY, int reach) {
		this.world = world; posX = x; posY = y; posZ = z;
		terrain = new Access(world);
		this.strength = strength; this.length = length; this.contained = contained; this.surfaceY = surfaceY;
		// Resistance energy and ray density still use strength. A weakly coupled
		// surface burst must nevertheless traverse the air gap to its contact point.
		rayLength = Math.max(strength, reach);
		gspNumMax = (int)(2.5 * Math.PI * Math.pow(strength, 2));
		gspNum = 1; gspX = Math.PI;
	}

	private void generateGspUp() {
		if(gspNum < gspNumMax) {
			int k = gspNum + 1;
			double hk = -1.0 + 2.0 * (k - 1.0) / (gspNumMax - 1.0);
			gspX = Math.acos(hk);
			// Longitude is undefined at the pole. Dividing by zero here produces
			// NaN directions (even NaN * step zero) and reads chunk 0,0.
			gspY = hk >= 1D ? 0D : (gspY + 3.6 / Math.sqrt(gspNumMax) / Math.sqrt(1.0 - hk * hk)) % (Math.PI * 2);
		} else { gspX = 0.0; gspY = 0.0; }
		gspNum++;
	}

	public boolean hasPendingWork() { return restoring != null || legacy != null || !isAusf3Complete || !ranges.isEmpty() || rayActive || buildActive || firstChunk != null; }
	private int reservedRays() {
		int reserved = 0;
		for(RayRange range : ranges) if(range.batch != null) reserved += range.batch.state.rayLimit;
		return reserved;
	}
	public boolean canWork(long tick, int count) {
		if(lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200) { logStatus(isAusf3Complete ? "applying destruction masks" : "collecting; destruction waits for ray collection"); lastDiagnosticTick = tick; }
		if(restoring != null || legacy != null || isAusf3Complete) return terrain.ready(tick);
		if(collectionTick == tick && raysThisTick >= count) { waitReason = "ray allowance"; return false; }
		if(ranges.isEmpty() && (BombConfig.explosionWorkerThreads == 0 || gspNum > gspNumMax)) return terrain.ready(tick);
		int slots = Math.max(1, Math.min(ExplosionRayWorker.MAX_JOB_BATCHES, BombConfig.explosionWorkerThreads));
		if(allocating != null || gspNum <= gspNumMax && ranges.size() < slots) return true;
		for(RayRange range : ranges) {
			if(range.batch != null) { if(range.batch.result != null || range.batch.failure != null) return true; }
			else if(range.state.gspNum >= range.end || range.access.ready(tick) && count - (collectionTick == tick ? raysThisTick : 0) > reservedRays()) return true;
		}
		waitReason = "workers or terrain"; return false;
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
		if(!section.destruction.get(bit)) selectedBlocks++;
		section.destruction.set(bit);
		if(tip) {
			if(section.tips == null) section.tips = new BitSet();
			section.tips.set(bit); // OR preserves tip priority over every normal hit.
		}
	}

	/** Legacy API retained for callers outside the governed entity path. */
	public void collectTip(int count) { collectTip(count, Long.MAX_VALUE, collectionTick + 1); }
	public void collectTip(int count, long deadline, long tick) {
		long start = System.nanoTime();
		try {
			if(!restore(deadline) || !migrate(deadline) || isAusf3Complete) return;
			if(collectionTick != tick) { collectionTick = tick; raysThisTick = 0; }
			if(!ranges.isEmpty() || BombConfig.explosionWorkerThreads > 0) collectWorker(count, deadline, tick);
			else {
				int previous = gspNum;
				collectServer(count, deadline, terrain, gspNumMax + 1);
				completedRays += gspNum - previous;
				if(gspNum > gspNumMax && ExplosionWorkScheduler.before(deadline)) finishCollection();
			}
		} finally { collectionServerNanos += System.nanoTime() - start; }
	}

	private void collectServer(int count, long deadline, Access access, int end) {
		// Cache chunk reads only within this slice: another job/tick may change storage.
		Chunk sampledChunk = null;
		while(gspNum < end && raysThisTick < count && ExplosionWorkScheduler.before(deadline)) {
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
						sampledChunk = ExplosionTerrainAccess.chunk(access, ix >> 4, iz >> 4, deadline);
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
				if(!buildMask(deadline, access)) return;
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
	}

	private void finishCollection() {
		// No excavation until all disjoint ranges have committed their masks.
		isAusf3Complete = true; collectionFinishedAt = System.nanoTime();
		workers.remove(this); waitReason = "mask application"; logStatus("collection complete");
	}

	private void collectWorker(int count, long deadline, long tick) {
		if(lifecycle == null || lifecycle.cancelled) {
			lifecycle = new ExplosionRayWorker.Lifecycle(terrain.id, terrain.worldLifecycleId); workers.add(this);
		}
		int slots = Math.max(1, Math.min(ExplosionRayWorker.MAX_JOB_BATCHES, BombConfig.explosionWorkerThreads));
		if(allocating == null && gspNum <= gspNumMax && ranges.size() < slots && ExplosionWorkScheduler.before(deadline)) {
			allocating = new RayRange(captureState(), Math.min(gspNumMax + 1, gspNum + ExplosionRayWorker.MAX_BATCH_RAYS));
			allocating.qualified.addAll(rayChunkKeys);
			ranges.add(allocating);
			rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>();
			rayActive = rayFinishing = rayRecorded = hasTip = buildActive = false;
		}
		// Reserving directions is resumable and preserves the exact serial spiral
		// recurrence, including floating-point longitude rounding at range boundaries.
		while(allocating != null && gspNum < allocating.end && ExplosionWorkScheduler.before(deadline)) generateGspUp();
		if(allocating != null && gspNum == allocating.end) { allocating.sealed = true; allocating = null; }
		int attempts = ranges.size();
		while(attempts-- > 0 && !ranges.isEmpty() && ExplosionWorkScheduler.before(deadline)) {
			if(rangeCursor >= ranges.size()) rangeCursor = 0;
			RayRange range = ranges.get(rangeCursor++);
			if(!range.sealed) continue;
			if(range.batch != null) adopt(range);
			if(range.batch != null) continue;
			if(range.state.gspNum >= range.end) {
				terrain.snapshotCaptures += range.access.snapshotCaptures; terrain.snapshotReuses += range.access.snapshotReuses;
				range.access.cancel(); ranges.remove(--rangeCursor); continue;
			}
			int reserved = reservedRays();
			if(!range.access.ready(tick) || raysThisTick + reserved >= count || !ExplosionWorkScheduler.before(deadline)) continue;
			if(range.fallback || BombConfig.explosionWorkerThreads == 0) { collectFallback(range, count - reserved, deadline); continue; }
			if(!ExplosionRayWorker.available(range.state.active || range.state.building)) { range.access.defer(); waitReason = "worker queue"; continue; }
			Snapshot required = null;
			if(range.pending) {
				required = ExplosionTerrainAccess.snapshot(range.access, range.x, range.y, range.z, deadline);
				if(required == null) { waitReason = "snapshot capture or terrain: " + range.access.status(); continue; }
			}
			Snapshot[] inputs = ExplosionTerrainAccess.workerSnapshots(range.access, required, corridor, deadline);
			if(!ExplosionWorkScheduler.before(deadline)) continue;
			ExplosionRayWorker.State state = range.state.copy();
			// Outstanding batches reserve from the same ray allowance as fallback;
			// parallelism must not multiply the caller's per-tick completed-ray cap.
			state.rayLimit = Math.min(count - raysThisTick - reserved, range.end - state.gspNum);
			range.batch = ExplosionRayWorker.submit(state, inputs, qualifiedKeys(range), lifecycle);
			if(range.batch == null) { range.access.defer(); waitReason = "worker queue"; }
			else { submittedBatches++; waitReason = "worker calculation"; }
		}
		if(gspNum > gspNumMax && ranges.isEmpty() && ExplosionWorkScheduler.before(deadline)) finishCollection();
	}

	private long[] qualifiedKeys(RayRange range) {
		long[] keys = new long[range.qualified.size()];
		for(int i = 0; i < keys.length; i++) keys[i] = range.qualified.get(i);
		return keys;
	}

	private void adopt(RayRange range) {
		ExplosionRayWorker.Batch batch = range.batch;
		int batchInputStep = range.state.step, batchInputBuildStep = range.state.buildStep;
		ExplosionRayWorker.Result result = batch.result;
		if(result == null && batch.failure == null) return;
		if(batch.failure != null) {
			workerFailures++; rejectedBatches++;
			MainRegistry.logger.error("Explosion terrain worker failed; retaining range checkpoint for bounded server fallback", batch.failure);
			batch.cancel(); range.batch = null; activateFallback(range, "worker failure"); return;
		}
		workerNanos += result.calculationNanos; sectionMisses += result.sectionMisses;
		boolean valid = !lifecycle.cancelled && !batch.cancelled && result.jobId == lifecycle.jobId && result.lifecycleId == lifecycle.id && result.worldLifecycleId == terrain.worldLifecycleId;
		boolean invalidSnapshot = false;
		for(ExplosionRayWorker.Mask mask : result.masks) {
			// Cached inputs not actually read cannot invalidate useful work.
			if(mask.used && (!mask.snapshot.valid || mask.snapshot.worldLifecycleId != terrain.worldLifecycleId)) invalidSnapshot = true;
		}
		if(!valid || invalidSnapshot) {
			rejectedBatches++; repeatedOperations += result.operations;
			batch.dispose(); range.batch = null;
			if(invalidSnapshot) {
				snapshotRejections++; range.rejections++;
				if(range.rejections >= INVALIDATION_RETRIES) activateFallback(range, "repeated snapshot invalidation without committed progress");
			}
			if(!range.fallback) waitReason = "snapshot retry"; return;
		}
		// All input versions are checked before this bounded, indivisible OR/cursor
		// commit. No world callbacks run here and only the server owns these masks.
		for(int i = result.masks.length - 1; i >= 0; i--) {
			ExplosionRayWorker.Mask mask = result.masks[i];
			Snapshot snapshot = mask.snapshot;
			if(mask.used) remember(snapshot);
			if(mask.destruction.isEmpty()) continue;
			ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(snapshot.chunkX, snapshot.chunkZ));
			SectionMask section = chunk.sections[snapshot.sectionY];
			if(section == null) chunk.sections[snapshot.sectionY] = section = new SectionMask();
			int previousCount = section.destruction.cardinality();
			section.destruction.or(mask.destruction);
			selectedBlocks += section.destruction.cardinality() - previousCount;
			if(!mask.tips.isEmpty()) {
				if(section.tips == null) section.tips = new BitSet();
				section.tips.or(mask.tips);
			}
		}
		range.state = result.state;
		range.qualified.clear(); range.qualified.addAll(result.qualified);
		range.pending = result.missing;
		if(result.missing) { range.x = result.chunkX; range.y = result.sectionY; range.z = result.chunkZ; }
		completedRays += result.completedRays; raysThisTick += result.completedRays;
		acceptedBatches++;
		if(result.completedRays > 0 || result.state.step != batchInputStep || result.state.buildStep != batchInputBuildStep) range.rejections = 0;
		batch.dispose(); range.batch = null;
	}

	private void remember(Snapshot snapshot) {
		for(int i = 0; i < corridor.size(); i++) {
			int[] coord = corridor.get(i);
			if(coord[0] == snapshot.chunkX && coord[1] == snapshot.sectionY && coord[2] == snapshot.chunkZ) { corridor.remove(i); break; }
		}
		if(corridor.size() == ExplosionRayWorker.MAX_BATCH_SNAPSHOTS) corridor.remove(0);
		corridor.add(new int[] { snapshot.chunkX, snapshot.sectionY, snapshot.chunkZ });
	}

	private void activateFallback(RayRange range, String reason) {
		if(range.batch != null) { range.batch.cancel(); range.batch = null; }
		waitReason = "bounded server fallback";
		if(!range.fallback) { range.fallback = true; fallbackActivations++; logStatus("range fallback: " + reason); }
		range.pending = false;
	}

	private void collectFallback(RayRange range, int count, long deadline) {
		// Reuse the canonical bounded server algorithm, with only this range's
		// calculation cursor swapped in. Already committed masks remain shared.
		ExplosionRayWorker.State allocator = captureState();
		LongHashMap savedChunks = rayChunks; ArrayList<Long> savedKeys = rayChunkKeys;
		int previous = range.state.gspNum;
		applyState(range.state); rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>();
		for(long key : range.qualified) addRayChunk(key);
		try { collectServer(count, deadline, range.access, range.end); }
		finally {
			range.state = captureState(); range.qualified.clear(); range.qualified.addAll(rayChunkKeys);
			completedRays += gspNum - previous;
			applyState(allocator); rayChunks = savedChunks; rayChunkKeys = savedKeys;
		}
		waitReason = "bounded server fallback: " + range.access.status();
	}

	private ExplosionRayWorker.State captureState() {
		ExplosionRayWorker.State state = new ExplosionRayWorker.State();
		state.posX = posX; state.posY = posY; state.posZ = posZ; state.length = length; state.rayLength = rayLength;
		state.initialStrength = strength; state.gspNum = gspNum; state.gspNumMax = gspNumMax; state.gx = gspX; state.gy = gspY;
		state.active = rayActive; state.recorded = rayRecorded;
		state.surfaceY = surfaceY; state.contained = contained; state.step = rayStep; state.strength = rayStrength;
		state.dx = rayX; state.dy = rayY; state.dz = rayZ; state.finishing = rayFinishing; state.hasTip = hasTip;
		state.tx = tipX; state.ty = tipY; state.tz = tipZ; state.airResistance = masqueradeResistance(Blocks.air);
		state.building = buildActive; state.buildStep = buildStep; state.buildLength = buildLength;
		state.bx = buildX; state.by = buildY; state.bz = buildZ; state.tipX = buildTipX; state.tipY = buildTipY; state.tipZ = buildTipZ;
		return state;
	}

	private void applyState(ExplosionRayWorker.State state) {
		gspNum = state.gspNum; gspX = state.gx; gspY = state.gy;
		rayActive = state.active; rayRecorded = state.recorded; rayX = state.dx; rayY = state.dy; rayZ = state.dz;
		rayStep = state.step; rayStrength = state.strength; rayFinishing = state.finishing;
		hasTip = state.hasTip; tipX = state.tx; tipY = state.ty; tipZ = state.tz;
		buildStep = state.buildStep; buildActive = state.building; buildLength = state.buildLength;
		buildX = state.bx; buildY = state.by; buildZ = state.bz;
		buildTipX = state.tipX; buildTipY = state.tipY; buildTipZ = state.tipZ;
		buildRestricted = buildInChunk = false;
	}

	public void cancelWorkers() {
		if(lifecycle != null) lifecycle.cancelled = true;
		for(RayRange range : ranges) {
			if(range.batch != null) { range.batch.cancel(); range.batch = null; }
			range.access.cancel();
		}
		terrain.cancel(); workers.remove(this);
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
	private boolean buildMask(long deadline) { return buildMask(deadline, terrain); }
	private boolean buildMask(long deadline, Access access) {
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
				if(ExplosionTerrainAccess.chunk(access, x >> 4, z >> 4, deadline) == null) return false;
				Block block = world.getBlock(x, y, z);
				if(!ExplosionWorkScheduler.before(deadline)) return false;
				if(!world.isAirBlock(x, y, z) && !block.getMaterial().isLiquid()) {
					record(x, y, z, x == buildTipX && y == buildTipY && z == buildTipZ);
				}
			}
			buildStep++;
		}
		if(buildStep < buildLength) return false;
		// Include the last positive sample, also for source-only hits and builds
		// restored mid-reconstruction from an existing save.
		if(!buildRestricted || ChunkCoordIntPair.chunkXZ2Int(buildTipX >> 4, buildTipZ >> 4) == buildChunk) record(buildTipX, buildTipY, buildTipZ, true);
		buildActive = false;
		return true;
	}

	public void processChunk() { processChunk(Long.MAX_VALUE); }
	public void processChunk(long deadline) {
		long start = System.nanoTime();
		try { applyMasks(deadline); }
		finally { removalServerNanos += System.nanoTime() - start; }
	}
	private void applyMasks(long deadline) {
		if(!restore(deadline) || !migrate(deadline) || !isAusf3Complete || firstChunk == null || !ExplosionWorkScheduler.before(deadline)) return;
		long tick = ExplosionWorkScheduler.currentTick();
		if(lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200) { logStatus("applying destruction masks"); lastDiagnosticTick = tick; }
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
				if(!world.isAirBlock(x, y, z)) {
					// A denied/failed world update is not completed destruction work.
					terrain.defer();
					if(!removalFailureReported) { logStatus("waiting; world rejected block removal"); removalFailureReported = true; }
					return;
				}
				destroyedBlocks++;
				if(firstRemovalAt == 0) { firstRemovalAt = System.nanoTime(); logStatus("first successful block removal"); }
				removalFailureReported = false;
			}
			// Callbacks, tile cleanup, lighting and sync finish before the bit/cursor
			// advances, even if the indivisible world removal overruns the slice.
			section.destruction.clear(bit);
			if(section.tips != null) section.tips.clear(bit);
			removalBit = bit + 1;
			if(section.destruction.isEmpty()) { chunk.sections[removalSection++] = null; removalBit = 0; }
		}
		if(removalSection == 16) {
			removeChunk(chunk); removalActive = false;
			if(firstChunk == null) logStatus(selectedBlocks == 0 ? "completed; no solid blocks passed resistance/reach checks" : "completed");
		}
	}

	protected void handleTip(int x, int y, int z) { world.setBlock(x, y, z, Blocks.air, 0, 3); }
	public static float masqueradeResistance(Block block) {
		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3;
		return block.getExplosionResistance(null);
	}

	public void readFromNBT(NBTTagCompound tag) {
		cancelWorkers(); terrain.read(tag);
		ranges.clear(); corridor.clear(); allocating = null; rangeCursor = 0;
		savedDiagnostics = tag.hasKey("selectedBlocks"); selectedBlocks = tag.getLong("selectedBlocks"); destroyedBlocks = tag.getLong("destroyedBlocks"); rejectedBatches = tag.getLong("rejectedBatches");
		chunks = new LongHashMap(); firstChunk = lastChunk = null; ordering.clear();
		rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>();
		gspNum = tag.getInteger("gspNum"); gspX = tag.getDouble("gspX"); gspY = tag.getDouble("gspY");
		if(tag.hasKey("contained")) contained = tag.getBoolean("contained");
		if(tag.hasKey("surfaceY")) surfaceY = tag.getInteger("surfaceY");
		rayLength = tag.hasKey("rayLength") ? tag.getInteger("rayLength") : strength;
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
		if(Double.isNaN(gspY) || Double.isInfinite(gspY)) {
			gspY = 0D;
			// Old saves can be paused on the invalid last ray, including a queued
			// request for the unrelated origin. Retry that ray at the real source.
			rayActive = rayFinishing = rayRecorded = hasTip = buildActive = false;
			terrain.read(new NBTTagCompound());
			logStatus("repaired non-finite legacy polar ray checkpoint");
		}
		NBTTagList savedRanges = tag.getTagList("rayRanges", 10);
		for(int i = 0; i < savedRanges.tagCount(); i++) {
			NBTTagCompound item = savedRanges.getCompoundTagAt(i);
			RayRange range = new RayRange(readState(item), item.getInteger("end"));
			range.sealed = item.getBoolean("sealed"); range.fallback = item.getBoolean("fallback"); range.rejections = item.getInteger("rejections");
			int[] keys = item.getIntArray("qualified");
			for(int k = 0; k + 1 < keys.length; k += 2) range.qualified.add(ChunkCoordIntPair.chunkXZ2Int(keys[k], keys[k + 1]));
			ranges.add(range); if(!range.sealed) allocating = range;
		}
		completedRays = tag.hasKey("completedRays") ? tag.getLong("completedRays") : Math.max(0, gspNum - 1);
		submittedBatches = tag.getLong("submittedBatches"); acceptedBatches = tag.getLong("acceptedBatches"); snapshotRejections = tag.getLong("snapshotRejections");
		workerFailures = tag.getLong("workerFailures"); repeatedOperations = tag.getLong("repeatedOperations"); workerNanos = tag.getLong("workerNanos");
		sectionMisses = tag.getLong("sectionMisses"); fallbackActivations = tag.getLong("fallbackActivations");
		collectionServerNanos = tag.getLong("collectionServerNanos"); removalServerNanos = tag.getLong("removalServerNanos");
		terrain.snapshotCaptures = tag.getLong("snapshotCaptures"); terrain.snapshotReuses = tag.getLong("snapshotReuses");
		long now = System.nanoTime(); startedAt = now - tag.getLong("elapsedNanos");
		collectionStartedAt = startedAt + tag.getLong("collectionOffsetNanos");
		collectionFinishedAt = tag.getBoolean("collectionTimed") ? startedAt + tag.getLong("collectionElapsedNanos") : 0;
		firstRemovalAt = tag.getBoolean("firstRemovalTimed") ? startedAt + tag.getLong("firstRemovalElapsedNanos") : 0;
	}

	private NBTTagCompound writeState(ExplosionRayWorker.State s) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInteger("gspNum", s.gspNum); tag.setDouble("gx", s.gx); tag.setDouble("gy", s.gy);
		tag.setBoolean("active", s.active); tag.setBoolean("finishing", s.finishing); tag.setBoolean("recorded", s.recorded); tag.setBoolean("hasTip", s.hasTip);
		tag.setInteger("step", s.step); tag.setFloat("strength", s.strength); tag.setDouble("dx", s.dx); tag.setDouble("dy", s.dy); tag.setDouble("dz", s.dz);
		tag.setFloat("tx", s.tx); tag.setFloat("ty", s.ty); tag.setFloat("tz", s.tz);
		tag.setBoolean("building", s.building); tag.setInteger("buildStep", s.buildStep); tag.setDouble("buildLength", s.buildLength);
		tag.setDouble("bx", s.bx); tag.setDouble("by", s.by); tag.setDouble("bz", s.bz);
		tag.setInteger("tipX", s.tipX); tag.setInteger("tipY", s.tipY); tag.setInteger("tipZ", s.tipZ);
		return tag;
	}
	private ExplosionRayWorker.State readState(NBTTagCompound tag) {
		ExplosionRayWorker.State s = captureState();
		s.gspNum = tag.getInteger("gspNum"); s.gx = tag.getDouble("gx"); s.gy = tag.getDouble("gy");
		s.active = tag.getBoolean("active"); s.finishing = tag.getBoolean("finishing"); s.recorded = tag.getBoolean("recorded"); s.hasTip = tag.getBoolean("hasTip");
		s.step = tag.getInteger("step"); s.strength = tag.getFloat("strength"); s.dx = tag.getDouble("dx"); s.dy = tag.getDouble("dy"); s.dz = tag.getDouble("dz");
		s.tx = tag.getFloat("tx"); s.ty = tag.getFloat("ty"); s.tz = tag.getFloat("tz");
		s.building = tag.getBoolean("building"); s.buildStep = tag.getInteger("buildStep"); s.buildLength = tag.getDouble("buildLength");
		s.bx = tag.getDouble("bx"); s.by = tag.getDouble("by"); s.bz = tag.getDouble("bz");
		s.tipX = tag.getInteger("tipX"); s.tipY = tag.getInteger("tipY"); s.tipZ = tag.getInteger("tipZ");
		return s;
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
				if(!savedDiagnostics) selectedBlocks += section.destruction.cardinality();
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
		tag.setLong("selectedBlocks", selectedBlocks); tag.setLong("destroyedBlocks", destroyedBlocks); tag.setLong("rejectedBatches", rejectedBatches);
		tag.setLong("completedRays", completedRays); tag.setLong("submittedBatches", submittedBatches); tag.setLong("acceptedBatches", acceptedBatches);
		tag.setLong("snapshotRejections", snapshotRejections); tag.setLong("workerFailures", workerFailures); tag.setLong("repeatedOperations", repeatedOperations);
		tag.setLong("workerNanos", workerNanos); tag.setLong("sectionMisses", sectionMisses); tag.setLong("fallbackActivations", fallbackActivations);
		tag.setLong("collectionServerNanos", collectionServerNanos); tag.setLong("removalServerNanos", removalServerNanos);
		tag.setLong("elapsedNanos", System.nanoTime() - startedAt); tag.setBoolean("collectionTimed", collectionFinishedAt != 0);
		tag.setLong("collectionOffsetNanos", collectionStartedAt - startedAt);
		tag.setLong("collectionElapsedNanos", collectionFinishedAt == 0 ? 0 : collectionFinishedAt - startedAt);
		tag.setBoolean("firstRemovalTimed", firstRemovalAt != 0); tag.setLong("firstRemovalElapsedNanos", firstRemovalAt == 0 ? 0 : firstRemovalAt - startedAt);
		long captures = terrain.snapshotCaptures, reuse = terrain.snapshotReuses;
		NBTTagList savedRanges = new NBTTagList();
		for(RayRange range : ranges) {
			// Save only committed checkpoints. In-flight results can be discarded and
			// recomputed after reload without duplicating ownership or losing masks.
			NBTTagCompound item = writeState(range.state);
			item.setInteger("end", range.end); item.setBoolean("sealed", range.sealed); item.setBoolean("fallback", range.fallback); item.setInteger("rejections", range.rejections);
			int[] qualified = new int[range.qualified.size() * 2];
			for(int i = 0; i < range.qualified.size(); i++) { long key = range.qualified.get(i); qualified[i * 2] = (int)key; qualified[i * 2 + 1] = (int)(key >> 32); }
			item.setIntArray("qualified", qualified); savedRanges.appendTag(item);
			captures += range.access.snapshotCaptures; reuse += range.access.snapshotReuses;
		}
		tag.setTag("rayRanges", savedRanges); tag.setLong("snapshotCaptures", captures); tag.setLong("snapshotReuses", reuse);
		terrain.write(tag);
		tag.setInteger("gspNum", gspNum); tag.setDouble("gspX", gspX); tag.setDouble("gspY", gspY);
		tag.setBoolean("complete", isAusf3Complete); tag.setBoolean("contained", contained); tag.setInteger("surfaceY", surfaceY);
		tag.setInteger("rayLength", rayLength);
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
