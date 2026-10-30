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

public class ExplosionNukeRayBatched {
	private static final int WORK_VERSION = 3;
	// Optional range checkpoints extend the stage 3 mask payload. Old scalar
	// ray checkpoints enter the same allocator without rebuilding committed masks.
	// x occupies the low signed int, z the high signed int, as in chunkXZ2Int.
	private LongHashMap chunks = new LongHashMap();
	private ChunkMask firstChunk, lastChunk;
	private ChunkMask cleanupCursor;
	private final TreeMap<Integer, DistanceBucket> ordering = new TreeMap<Integer, DistanceBucket>();
	int posX, posY, posZ;
	World world;
	int strength, length;
	private int rayLength;
	private boolean contained;
	private boolean balancedDirections = true;
	private PressureWork pressure;
	private long memoryWaits, surfaceCallbackNanos;
	private final long[] removedBands = new long[3], completedDirections = new long[3];
	private final java.util.LinkedHashMap<String, Long> deferralNames = new java.util.LinkedHashMap<String, Long>();
	private final java.util.ArrayList<String> skippedSamples = new java.util.ArrayList<String>();
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
	private final Access removalTerrain;
	private final ExplosionTerrainAccess.History originals;
	private long pendingBlocks, pendingEffects;
	private int queuedChunks, nextDistance;
	private long excavationTick = Long.MIN_VALUE;
	private int excavationAttempts;
	private boolean excavationFirst;
	private String excavationWait = "no committed masks";
	private ExplosionRayWorker.Lifecycle lifecycle;
	private static final java.util.Set<ExplosionNukeRayBatched> workers = new java.util.HashSet<ExplosionNukeRayBatched>();
	private final ArrayList<RayRange> ranges = new ArrayList<RayRange>();
	// Server-only coordinates, not retained snapshot references. Successor ranges
	// reuse the explosion's working set without pinning buffers outside the cap.
	private final ArrayList<int[]> corridor = new ArrayList<int[]>();
	private RayRange allocating;
	private int rangeCursor;
	private long completedRays, submittedBatches, acceptedBatches, snapshotRejections, workerFailures, repeatedOperations, workerNanos, sectionMisses, fallbackActivations;
	private long traversalOperations, reconstructionOperations, reusedSamples, skippedReconstruction, attenuationHits, workerCpuNanos, sharedSnapshotHits;
	private long startedAt = System.nanoTime(), collectionFinishedAt, firstRemovalAt, collectionServerNanos, removalServerNanos;
	private long collectionStartedAt = startedAt;
	private String waitReason = "ready";
	private static final int INVALIDATION_RETRIES = 3;

	/** Each disjoint range owns its checkpoint; workers receive a private copy.
	 * The main cursor reserves the saved direction sequence independently of workers. */
	private final class RayRange {
		ExplosionRayWorker.State state;
		final int end;
		final Access access = new Access(world, originals);
		ArrayList<Long> qualified = new ArrayList<Long>();
		ExplosionRayWorker.Batch batch;
		ExplosionRayWorker.Scratch scratch = new ExplosionRayWorker.Scratch();
		final ArrayList<int[]> workingSet = new ArrayList<int[]>(corridor);
		boolean sealed, fallback, pending;
		int x, y, z, rejections, fallbackEnd;
		RayRange(ExplosionRayWorker.State state, int end) { this.state = state; this.end = end; fallbackEnd = end; }
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
	private long deferredBlocks, deferredEffects, fastRemovals, normalRemovals, sectionsAdvanced, chunksAdvanced, preservationNanos;
	private long diagnosticRemoved;
	private final int[] columnBits = new int[16];
	private final BitSet columnChanged = new BitSet(4096);
	public void includeInitializationDelay(long nanos) { startedAt -= Math.max(0L, nanos); }

	public void logStatus(String status) {
		if(isAusf3Complete && selectedBlocks == 0) status += "; no solid samples passed reach/resistance checks";
		if(!GeneralConfig.enableExtendedLogging) return;
		long captures = terrain.snapshotCaptures, reuse = terrain.snapshotReuses;
		for(RayRange range : ranges) { captures += range.access.snapshotCaptures; reuse += range.access.snapshotReuses; }
		long now = System.nanoTime();
		MainRegistry.logger.info("[NUKE TERRAIN] dim=" + world.provider.dimensionId + " at=" + posX + "," + posY + "," + posZ + " strength=" + strength + " reach=" + rayLength + " limit=" + length + " rays=" + gspNumMax + " status=" + status
				+ " completedRays=" + completedRays + " remainingRays=" + Math.max(0, gspNumMax - completedRays) + " selected=" + selectedBlocks + " removed=" + destroyedBlocks
				+ " pendingExcavation=" + pendingBlocks + " pendingTerminalEffects=" + pendingEffects + " deferredBlocks=" + deferredBlocks + " deferredTerminalEffects=" + deferredEffects + " retainedOriginalBytes=" + originals.bytes() + " collectionComplete=" + isAusf3Complete + " excavationWait=" + excavationWait
				+ " readyExcavation=" + (pendingBlocks - deferredBlocks) + " removedLowerHorizontalUpper=" + java.util.Arrays.toString(removedBands) + " completedLowerHorizontalUpper=" + java.util.Arrays.toString(completedDirections) + " directionOrder=" + (balancedDirections ? "interleaved thirds" : "legacy") + " pressure=" + (pressure == null ? "none" : pressure.status()) + " suppressedDrops=" + NuclearDropSuppression.suppressed() + " originalMemoryWaits=" + memoryWaits + " surfaceMaintenanceMs=" + surfaceCallbackNanos / 1000000L + " deferredByName=" + deferralNames + " skippedSamples=" + skippedSamples
				+ " fastPathRemoved=" + fastRemovals + " normalPathRemoved=" + normalRemovals + " intervalRemoved=" + (destroyedBlocks - diagnosticRemoved) + " sectionsAdvanced=" + sectionsAdvanced + " chunksAdvanced=" + chunksAdvanced + " preservationActiveMs=" + preservationNanos / 1000000L
				+ " submitted=" + submittedBatches + " accepted=" + acceptedBatches + " rejected=" + rejectedBatches + " snapshotRejections=" + snapshotRejections + " workerFailures=" + workerFailures
				+ " captures=" + captures + " reuse=" + reuse + " sectionMisses=" + sectionMisses + " repeatedOperations=" + repeatedOperations + " fallbacks=" + fallbackActivations + " wait=" + waitReason
				+ " traversalOps=" + traversalOperations + " reconstructionOps=" + reconstructionOperations + " reusedTraversalSamples=" + reusedSamples + " skippedReconstructionSamples=" + skippedReconstruction + " attenuationCacheHits=" + attenuationHits
				+ " sharedSnapshotHits=" + sharedSnapshotHits
				+ " elapsedMs=" + (now - startedAt) / 1000000L + " collectionElapsedMs=" + ((collectionFinishedAt == 0 ? now : collectionFinishedAt) - collectionStartedAt) / 1000000L
				+ " firstRemovalMs=" + (firstRemovalAt == 0 ? -1 : (firstRemovalAt - startedAt) / 1000000L) + " applicationElapsedMs=" + (firstRemovalAt == 0 ? 0 : (now - firstRemovalAt) / 1000000L)
				+ " collectionServerActiveElapsedMs=" + collectionServerNanos / 1000000L + " applicationServerActiveElapsedMs=" + removalServerNanos / 1000000L + " workerActiveElapsedMs=" + workerNanos / 1000000L + " workerCpuMs=" + (workerCpuNanos < 0 ? -1 : workerCpuNanos / 1000000L) + " " + ExplosionTerrainAccess.diagnostics() + " " + terrain.status());
		diagnosticRemoved = destroyedBlocks;
	}

	// New saves decode a section at a time. Saving during decoding retains the original snapshot.
	private NBTTagCompound restoring;
	private int restorePhase, restoreIndex, restoreSection;
	// Endpoint NBT exists only at the old-save boundary, never as the live terrain engine.
	private NBTTagCompound legacy;
	private int legacyPhase, legacyIndex, legacyPoint;
	private boolean legacyChunkStarted;

	private static class SectionMask {
		// Coverage survives pending-bit clearing. Late results can add new work
		// or upgrade a completed normal hit to one terminal notification.
		final BitSet destruction = new BitSet();
		final BitSet pending = new BitSet(), completed = new BitSet(), notified = new BitSet();
		final BitSet deferred = new BitSet(), effects = new BitSet(), deferredEffects = new BitSet();
		BitSet tips;
	}
	private static class ChunkMask {
		final long key;
		final int distance;
		final SectionMask[] sections = new SectionMask[16];
		ChunkMask previous, next, orderPrevious, orderNext;
		boolean queued;
		int cursorSection, cursorBit;
		long pending, effects, deferred, deferredEffects, blockedTick = Long.MIN_VALUE;
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
		originals = new ExplosionTerrainAccess.History(world);
		terrain = new Access(world, originals);
		removalTerrain = new Access(world);
		this.strength = strength; this.length = length; this.contained = contained; this.surfaceY = surfaceY;
		// Resistance energy and ray density still use strength. A weakly coupled
		// surface burst must nevertheless traverse the air gap to its contact point.
		rayLength = Math.max(strength, reach);
		gspNumMax = (int)(2.5 * Math.PI * Math.pow(strength, 2));
		gspNum = 1; gspX = ExplosionRayWorker.latitude(1, gspNumMax); gspY = ExplosionRayWorker.longitude(1, gspNumMax);
	}

	private void generateGspUp() {
		if(balancedDirections && gspNum < gspNumMax) {
			gspX = ExplosionRayWorker.latitude(gspNum + 1, gspNumMax); gspY = ExplosionRayWorker.longitude(gspNum + 1, gspNumMax);
		} else if(gspNum < gspNumMax) {
			int k = gspNum + 1;
			double hk = -1.0 + 2.0 * (k - 1.0) / (gspNumMax - 1.0);
			gspX = Math.acos(hk);
			// Longitude is undefined at the pole. Dividing by zero here produces
			// NaN directions (even NaN * step zero) and reads chunk 0,0.
			gspY = hk >= 1D ? 0D : (gspY + 3.6 / Math.sqrt(gspNumMax) / Math.sqrt(1.0 - hk * hk)) % (Math.PI * 2);
		} else { gspX = 0.0; gspY = 0.0; }
		gspNum++;
	}

	public boolean hasPendingWork() { return pressure != null && !pressure.complete || restoring != null || legacy != null || !isAusf3Complete || !ranges.isEmpty() || rayActive || buildActive || pendingBlocks != 0 || pendingEffects != 0 || firstChunk != null; }
	/** Bounded opportunities, then donate unused time to the remaining ready stage. */
	public void advance(long deadline, long tick) {
		if(!restore(deadline) || !migrate(deadline)) return;
		if(pressure != null && !pressure.complete) pressure.advance(Math.min(deadline, System.nanoTime() + 250_000L));
		boolean collect = collectionReady(tick, Integer.MAX_VALUE), excavate = excavationReady(tick) || cleanupCursor != null || inputsComplete() && firstChunk == null && originals.bytes() != 0;
		boolean firstExcavate = excavate && (excavationFirst || !collect);
		long turn = collect && excavate ? Math.min(deadline, System.nanoTime() + 250_000L) : deadline;
		if(firstExcavate) processChunk(turn); else if(collect) collectTip(Integer.MAX_VALUE, turn, tick);
		if(ExplosionWorkScheduler.before(deadline)) {
			if(firstExcavate && collectionReady(tick, Integer.MAX_VALUE)) collectTip(Integer.MAX_VALUE, Math.min(deadline, System.nanoTime() + 250_000L), tick);
			else if(!firstExcavate && (excavationReady(tick) || cleanupCursor != null)) processChunk(Math.min(deadline, System.nanoTime() + 250_000L));
		}
		// Never spin waiting for worker results. Each stage is tried at most twice
		// per dispatch; global round-robin ownership and deadline remain unchanged.
		if(ExplosionWorkScheduler.before(deadline) && (excavationReady(tick) || cleanupCursor != null)) processChunk(deadline);
		if(ExplosionWorkScheduler.before(deadline) && collectionReady(tick, Integer.MAX_VALUE)) collectTip(Integer.MAX_VALUE, deadline, tick);
		if(inputsComplete() && firstChunk == null) originals.clear();
		excavationFirst = !excavationFirst;
	}
	private int reservedRays() {
		int reserved = 0;
		for(RayRange range : ranges) if(range.batch != null) reserved += range.batch.state.rayLimit;
		return reserved;
	}
	public boolean canWork(long tick, int count) {
		if(lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200) { logStatus("progressive collection/excavation"); lastDiagnosticTick = tick; }
		if(restoring != null || legacy != null || pressure != null && pressure.ready(tick)) return true;
		if(cleanupCursor != null || inputsComplete() && firstChunk == null && originals.bytes() != 0 || excavationReady(tick)) return true;
		return collectionReady(tick, count);
	}
	private boolean excavationReady(long tick) { return queuedChunks > 0 && (removalActive || excavationTick != tick || excavationAttempts < queuedChunks); }
	private boolean collectionReady(long tick, int count) {
		if(isAusf3Complete) return false;
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
		return chunk;
	}
	private void enqueue(ChunkMask chunk) {
		if(chunk.queued || chunk.pending - chunk.deferred + chunk.effects - chunk.deferredEffects == 0) return;
		chunk.queued = true; queuedChunks++;
		if(excavationTick == ExplosionWorkScheduler.currentTick() && chunk.blockedTick == excavationTick) excavationAttempts++;
		DistanceBucket bucket = ordering.get(chunk.distance);
		if(bucket == null) { bucket = new DistanceBucket(); ordering.put(chunk.distance, bucket); }
		chunk.orderPrevious = bucket.last;
		if(bucket.last == null) bucket.first = chunk; else bucket.last.orderNext = chunk;
		bucket.last = chunk;
	}
	private void removeChunk(ChunkMask chunk) {
		chunks.remove(chunk.key);
		if(chunk.previous == null) firstChunk = chunk.next; else chunk.previous.next = chunk.next;
		if(chunk.next == null) lastChunk = chunk.previous; else chunk.next.previous = chunk.previous;
		if(chunk.queued) dequeue(chunk);
	}
	private void dequeue(ChunkMask chunk) {
		if(!chunk.queued) return;
		chunk.queued = false; queuedChunks--;
		if(excavationTick == ExplosionWorkScheduler.currentTick() && chunk.blockedTick == excavationTick) excavationAttempts--;
		DistanceBucket bucket = ordering.get(chunk.distance);
		if(chunk.orderPrevious == null) bucket.first = chunk.orderNext; else chunk.orderPrevious.orderNext = chunk.orderNext;
		if(chunk.orderNext == null) bucket.last = chunk.orderPrevious; else chunk.orderNext.orderPrevious = chunk.orderPrevious;
		if(bucket.first == null) ordering.remove(chunk.distance);
		chunk.orderPrevious = chunk.orderNext = null;
	}
	private void rotate(DistanceBucket bucket) {
		ChunkMask chunk = bucket.first;
		if(chunk == bucket.last) return;
		// Rotation preserves queue ownership even if the last ready cell was
		// consumed just before a deadline. Only retirement may dequeue that chunk.
		bucket.first = chunk.orderNext; bucket.first.orderPrevious = null;
		chunk.orderPrevious = bucket.last; chunk.orderNext = null;
		bucket.last.orderNext = chunk; bucket.last = chunk;
	}
	private void wake(ChunkMask chunk) {
		if(chunk.queued && excavationTick == ExplosionWorkScheduler.currentTick() && chunk.blockedTick == excavationTick) excavationAttempts--;
		chunk.blockedTick = Long.MIN_VALUE;
		chunk.cursorSection = chunk.cursorBit = 0;
		enqueue(chunk);
		// Late worker hits may precede the active cursor, including in an earlier
		// section. Revisit only pending work; completed bits still prevent repeats.
		if(removalActive && removalKey == chunk.key) { removalSection = removalBit = 0; }
	}
	private void block(ChunkMask chunk, long tick) {
		if(chunk.queued && chunk.blockedTick != tick) excavationAttempts++;
		chunk.blockedTick = tick; removalActive = false;
	}
	private void addEffect(ChunkMask chunk, SectionMask section, int bit) {
		if(section.effects.get(bit) || section.deferredEffects.get(bit) || section.notified.get(bit)) return;
		chunk.effects++; pendingEffects++;
		if(inputsComplete()) section.effects.set(bit);
		else { section.deferredEffects.set(bit); chunk.deferredEffects++; deferredEffects++; }
	}
	private void deferBlock(ChunkMask chunk, SectionMask section, int bit) {
		section.pending.clear(bit); section.deferred.set(bit); chunk.deferred++; deferredBlocks++;
		if(!GeneralConfig.enableExtendedLogging) return;
		int x = ((int)chunk.key << 4) + (bit & 15), y = (sectionAt(removalSection) << 4) + (bit >> 8), z = ((int)(chunk.key >> 32) << 4) + (bit >> 4 & 15);
		Block b = world.getBlock(x, y, z);
		String reason = Block.blockRegistry.getNameForObject(b) + (b.hasTileEntity(world.getBlockMetadata(x, y, z)) ? ": tile callback" : ": unaudited removal callback");
		if(deferralNames.containsKey(reason) || deferralNames.size() < 32) deferralNames.put(reason, deferralNames.containsKey(reason) ? deferralNames.get(reason) + 1 : 1L);
		if(skippedSamples.size() < 8) skippedSamples.add(x + "," + y + "," + z + " " + reason);
	}
	private void addRayChunk(long key) {
		if(!rayChunks.containsItem(key)) { rayChunks.add(key, Boolean.TRUE); rayChunkKeys.add(key); }
	}
	private boolean inputsComplete() { return isAusf3Complete && (pressure == null || pressure.complete); }
	private int band(int y) { return y < posY - 16 ? 0 : y > posY + 16 ? 2 : 1; }
	private int sectionAt(int ordinal) {
		// Visit the source/surface section, then alternate above and below it.
		int center = MathHelper.clamp_int(posY >> 4, 0, 15), seen = 0;
		for(int offset = 0; offset < 16; offset++) {
			int up = center + offset;
			if(up < 16 && seen++ == ordinal) return up;
			int down = center - offset;
			if(offset != 0 && down >= 0 && seen++ == ordinal) return down;
		}
		throw new IllegalArgumentException("section ordinal " + ordinal);
	}

	private boolean clearAttachments(int x, int y, int z, long deadline) {
		long start = System.nanoTime();
		try { return clearAttachments(x, y, z, deadline, 0); }
		finally { surfaceCallbackNanos += System.nanoTime() - start; }
	}
	private boolean clearAttachments(int x, int y, int z, long deadline, int depth) {
		if(!ExplosionTerrainAccess.neighbors(removalTerrain, x, z, deadline)) return false;
		if(!NuclearDropSuppression.prepareGravity(world, x, y, z)) return false;
		for(net.minecraftforge.common.util.ForgeDirection dir : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
			int xx = x + dir.offsetX, yy = y + dir.offsetY, zz = z + dir.offsetZ;
			if(yy < 0 || yy > 255 || !NuclearBlockBehavior.attachedTo(world, xx, yy, zz, x, y, z)) continue;
			if(!ExplosionWorkScheduler.before(deadline) || depth >= 256) return false;
			Block child = world.getBlock(xx, yy, zz);
			float resistance = child.getExplosionResistance(null);
			if(resistance < 0F || resistance >= ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD) continue;
			if(!inputsComplete() && (!safeProgressiveBlock(child) || child.hasTileEntity(world.getBlockMetadata(xx, yy, zz)))) return false;
			if(!clearAttachments(xx, yy, zz, deadline, depth + 1)) return false;
			if(!inputsComplete() && !remember(xx, yy, zz, child)) return false;
			if(!ExplosionWorkScheduler.before(deadline)) return false;
			// Preserve and remove the attachment before its support. Its ordinary
			// scheduled support check later sees air and cannot manufacture drops.
			try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) {
				NuclearDropSuppression.clearInventory(world, xx, yy, zz);
				world.setBlock(xx, yy, zz, Blocks.air, 0, 2);
			}
			if(!world.isAirBlock(xx, yy, zz)) return false;
			boolean active = removalActive; removalActive = false;
			record(xx, yy, zz, false); removalActive = active;
			ChunkMask owner = (ChunkMask)chunks.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(xx >> 4, zz >> 4));
			SectionMask section = owner.sections[yy >> 4]; int bit = (yy & 15) << 8 | (zz & 15) << 4 | (xx & 15);
			if(section.pending.get(bit)) { section.pending.clear(bit); owner.pending--; pendingBlocks--; }
			if(section.deferred.get(bit)) { section.deferred.clear(bit); owner.deferred--; deferredBlocks--; owner.pending--; pendingBlocks--; }
			section.completed.set(bit); destroyedBlocks++; normalRemovals++; removedBands[band(yy)]++;
			if(inputsComplete()) {
				try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) { world.notifyBlockChange(xx, yy, zz, child); }
				section.notified.set(bit);
			} else addEffect(owner, section, bit);
		}
		return ExplosionWorkScheduler.before(deadline);
	}

	/** The entity front supplies reach; this is selection into the SAME masks,
	 * completion boundary and original-terrain view as crater excavation. */
	public void reachPressureFront(double x, double y, double z, double reached, double maximum, double fivePsi, double factor) {
		if(maximum <= 0D || fivePsi <= 0D) return;
		if(pressure == null) pressure = new PressureWork(x, y, z, maximum, fivePsi, factor);
		pressure.reached = Math.max(pressure.reached, reached);
	}
	private final class PressureWork {
		final Access access = new Access(world, originals);
		double x, y, z, maximum, fivePsi, factor, reached, energy;
		int chunkIndex, column, scanY = -1, targetX, targetY, targetZ, sightStep, sightSteps;
		int lastX = Integer.MIN_VALUE, lastY, lastZ, operations;
		long workTick = Long.MIN_VALUE, frontWait = Long.MIN_VALUE, inspected, selected, shielded;
		boolean candidate, complete;
		PressureWork(double x, double y, double z, double maximum, double fivePsi, double factor) {
			this.x = x; this.y = y; this.z = z; this.maximum = maximum; this.fivePsi = fivePsi; this.factor = factor;
		}
		PressureWork(NBTTagCompound t) {
			this(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getDouble("maximum"), t.getDouble("fivePsi"), t.getDouble("factor"));
			reached = t.getDouble("reached"); chunkIndex = t.getInteger("chunk"); column = t.getInteger("column"); scanY = t.getInteger("scanY");
			complete = t.getBoolean("complete"); inspected = t.getLong("inspected"); selected = t.getLong("selected"); shielded = t.getLong("shielded");
			// Re-evaluate an unfinished candidate against restored originals. No
			// removal is repeated: the cursor has not committed that candidate yet.
		}
		boolean ready(long tick) { return !complete && frontWait != tick && access.ready(tick) && (workTick != tick || operations < 1024); }
		String status() { return "[columns=" + ((long)chunkIndex * 256 + column) + ",inspected=" + inspected + ",selected=" + selected + ",shielded=" + shielded + ",complete=" + complete + "]"; }
		void advance(long deadline) {
			long tick = ExplosionWorkScheduler.currentTick();
			if(!ready(tick)) return;
			if(workTick != tick) { workTick = tick; operations = 0; }
			int extent = (int)Math.ceil(maximum / 16D) + 1;
			while(operations < 1024 && ExplosionWorkScheduler.before(deadline)) {
				operations++;
				if(candidate) { if(!selectCandidate(deadline)) return; continue; }
				if(chunkIndex >= (2L * extent + 1) * (2L * extent + 1)) {
					complete = true; access.cancel(); if(isAusf3Complete) cleanupCursor = firstChunk; return;
				}
				// Square spiral of chunks; columns and vertical cursors survive every
				// deadline/chunk wait. Only already reached columns may select blocks.
				int ring = (int)Math.ceil((Math.sqrt(chunkIndex + 1D) - 1D) / 2D), dx = 0, dz = 0;
				if(ring > 0) {
					int d = (2 * ring + 1) * (2 * ring + 1) - 1 - chunkIndex, side = d / (2 * ring), offset = d % (2 * ring);
					if(side == 0) { dx = ring - offset; dz = -ring; }
					else if(side == 1) { dx = -ring; dz = -ring + offset; }
					else if(side == 2) { dx = -ring + offset; dz = ring; }
					else { dx = ring; dz = ring - offset; }
				}
				int xx = ((MathHelper.floor_double(x) >> 4) + dx) * 16 + (column & 15), zz = ((MathHelper.floor_double(z) >> 4) + dz) * 16 + (column >> 4);
				double distance = Math.sqrt((xx + .5D - x) * (xx + .5D - x) + (zz + .5D - z) * (zz + .5D - z));
				if(distance > maximum || xx < -30000000 || xx >= 30000000 || zz < -30000000 || zz >= 30000000) { nextColumn(); continue; }
				if(distance > reached) { frontWait = tick; return; }
				net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.chunk(access, xx >> 4, zz >> 4, deadline);
				if(live == null) return;
				if(scanY == -1) scanY = Math.min(255, Math.min(live.getTopFilledSegment() + 15, MathHelper.floor_double(y + ExplosionNukeGeneric.getBlastHeightAbove(maximum))));
				int minimum = Math.max(0, MathHelper.floor_double(y - ExplosionNukeGeneric.getBlastHeightBelow(maximum)));
				if(scanY < minimum) { nextColumn(); continue; }
				Block block = live.getBlock(xx & 15, scanY, zz & 15); inspected++;
				if(!NuclearBlockBehavior.pressureTarget(block)) {
					// Pressure visits exposed cover and structures, not another solid
					// terrain volume. Geological ground/liquid ends this column; crater
					// rays alone excavate below it using the original resistance view.
					if(block.getMaterial().blocksMovement() || block.getMaterial().isLiquid()) nextColumn(); else descend();
					continue;
				}
				if(Math.abs(scanY + .5D - y) > (scanY >= y ? ExplosionNukeGeneric.getBlastHeightAbove(maximum) : ExplosionNukeGeneric.getBlastHeightBelow(maximum))) { descend(); continue; }
				if(Math.abs(scanY + .5D - y) > (scanY >= y ? ExplosionNukeGeneric.getBlastHeightAbove(reached) : ExplosionNukeGeneric.getBlastHeightBelow(reached))) { frontWait = tick; return; }
				float resistance = block.getExplosionResistance(null);
				energy = ExplosionNukeGeneric.getOverpressurePsi(distance / fivePsi) * factor;
				if(resistance < 0F || resistance >= ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD || energy <= resistance + 1D) { descend(); continue; }
				targetX = xx; targetY = scanY; targetZ = zz; sightStep = 0; lastX = Integer.MIN_VALUE;
				sightSteps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(xx + .5D - x), Math.max(Math.abs(scanY + .5D - y), Math.abs(zz + .5D - z))) * 2D));
				candidate = true;
			}
		}
		private boolean selectCandidate(long deadline) {
			while(sightStep < sightSteps && operations < 1024 && ExplosionWorkScheduler.before(deadline)) {
				double f = sightStep / (double)sightSteps;
				int xx = MathHelper.floor_double(x + (targetX + .5D - x) * f), yy = MathHelper.floor_double(y + (targetY + .5D - y) * f), zz = MathHelper.floor_double(z + (targetZ + .5D - z) * f);
				if(yy < 0 || yy > 255 || xx == lastX && yy == lastY && zz == lastZ || xx == targetX && yy == targetY && zz == targetZ) { sightStep++; continue; }
				Snapshot sample = ExplosionTerrainAccess.snapshot(access, xx >> 4, yy >> 4, zz >> 4, deadline);
				if(sample == null) return false;
				net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.chunk(access, xx >> 4, zz >> 4, deadline);
				if(live == null) return false;
				Block source = live.getBlock(xx & 15, yy, zz & 15);
				if(source == Blocks.air) source = originals.source(xx, yy, zz);
				float protection = source.getExplosionResistance(null);
				if(protection < 0F || protection >= ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD) { shielded++; candidate = false; descend(); return true; }
				int bit = (yy & 15) << 8 | (zz & 15) << 4 | (xx & 15); float r = sample.resistance(bit);
				if(!sample.air(bit)) energy -= sample.liquid(bit) ? 2D : 1D + Math.max(0F, r);
				lastX = xx; lastY = yy; lastZ = zz; sightStep++; operations++;
				if(r < 0F || r >= ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD || energy <= 0D) { shielded++; candidate = false; descend(); return true; }
			}
			if(sightStep < sightSteps) return false;
			if(ExplosionTerrainAccess.chunk(access, targetX >> 4, targetZ >> 4, deadline) == null) return false;
			Block target = world.getBlock(targetX, targetY, targetZ);
			float resistance = target.getExplosionResistance(null);
			if(NuclearBlockBehavior.pressureTarget(target) && resistance >= 0F && resistance < ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD && energy > resistance + 1D) { record(targetX, targetY, targetZ, true); selected++; }
			else shielded++;
			candidate = false; descend(); return true;
		}
		private void descend() { if(scanY == 0) nextColumn(); else scanY--; }
		private void nextColumn() { scanY = -1; if(++column == 256) { column = 0; chunkIndex++; } }
		NBTTagCompound write() {
			NBTTagCompound t = new NBTTagCompound();
			t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("maximum", maximum); t.setDouble("fivePsi", fivePsi); t.setDouble("factor", factor); t.setDouble("reached", reached);
			t.setInteger("chunk", chunkIndex); t.setInteger("column", column); t.setInteger("scanY", scanY); t.setBoolean("complete", complete);
			t.setLong("inspected", inspected); t.setLong("selected", selected); t.setLong("shielded", shielded); return t;
		}
	}
	private void record(int x, int y, int z, boolean tip) {
		if(y < 0 || y >= 256) return;
		ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4));
		SectionMask section = chunk.sections[y >> 4];
		if(section == null) chunk.sections[y >> 4] = section = new SectionMask();
		int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
		if(!section.destruction.get(bit)) { section.destruction.set(bit); selectedBlocks++; }
		boolean added = !section.completed.get(bit) && !section.pending.get(bit) && !section.deferred.get(bit);
		if(added) { section.pending.set(bit); chunk.pending++; pendingBlocks++; }
		if(tip) {
			if(section.tips == null) section.tips = new BitSet();
			if(!section.tips.get(bit) && section.completed.get(bit)) { addEffect(chunk, section, bit); added = true; }
			section.tips.set(bit);
		}
		if(added) wake(chunk);
	}
	private void merge(ChunkMask chunk, SectionMask section, BitSet hits, BitSet tips) {
		int previous = section.destruction.cardinality(); section.destruction.or(hits);
		selectedBlocks += section.destruction.cardinality() - previous;
		int pending = section.pending.cardinality();
		section.pending.or(hits); section.pending.andNot(section.completed); section.pending.andNot(section.deferred);
		int added = section.pending.cardinality() - pending; chunk.pending += added; pendingBlocks += added;
		if(section.tips == null) section.tips = new BitSet();
		BitSet late = (BitSet)tips.clone(); late.andNot(section.tips); late.and(section.completed); late.andNot(section.notified);
		for(int bit = late.nextSetBit(0); bit >= 0; bit = late.nextSetBit(bit + 1)) addEffect(chunk, section, bit);
		section.tips.or(tips);
		if(added > 0 || !late.isEmpty()) wake(chunk);
	}

	/** Legacy API retained for callers outside the governed entity path. */
	public void collectTip(int count) { collectTip(count, Long.MAX_VALUE, collectionTick + 1); }
	public void collectTip(int count, long deadline, long tick) {
		long start = System.nanoTime();
		try {
			if(!restore(deadline) || !migrate(deadline) || isAusf3Complete) return;
			if(!collectionReady(tick, count)) return;
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
		Snapshot sampled = null;
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
				traversalOperations++;
				// Null sections contain only vanilla air. Do not trust isEmpty(): custom air
				// blocks can have resistance. Keep the world border's air behavior as well.
				if(iy >= 0 && iy < 256 && ix >= -30000000 && iz >= -30000000 && ix < 30000000 && iz < 30000000) {
					if(sampled == null || !sampled.contains(ix, iy, iz)) {
						sampled = ExplosionTerrainAccess.snapshot(access, ix >> 4, iy >> 4, iz >> 4, deadline);
						if(sampled == null) return;
					}
					if(sampled.isZeroAir()) {
						rayStep = endOfEmptySection(ix >> 4, iy >> 4, iz >> 4);
						// The old post-sample length check stops after length - 1.
						if(rayStep >= rayLength || rayStep >= Math.max(1, length)) rayFinishing = true;
						continue;
					}
				}
				double fac = (100 - (double)rayStep / rayLength * 100) * 0.07D;
				boolean stored = sampled != null && sampled.contains(ix, iy, iz);
				int bit = (iy & 15) << 8 | (iz & 15) << 4 | (ix & 15);
				boolean liquid = stored && sampled.liquid(bit);
				if(!liquid) rayStrength -= Math.pow(stored ? sampled.resistance(bit) : masqueradeResistance(Blocks.air), 7.5D - fac);
				// Resistance callbacks are indivisible. Commit their sample before yielding.
				if(rayStrength > 0 && stored && !sampled.vanillaAir(bit) && !liquid) {
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
			completedDirections[rayY < -1D / 3D ? 0 : rayY > 1D / 3D ? 2 : 1]++;
			generateGspUp(); raysThisTick++;
		}
	}

	private void finishCollection() {
		// Collection completion releases the resistance dependency; pending masks
		// and terminal callbacks still own the terrain/fallout completion boundary.
		isAusf3Complete = true; collectionFinishedAt = System.nanoTime();
		cleanupCursor = firstChunk;
		workers.remove(this); waitReason = "mask application"; logStatus("collection complete");
	}

	private void collectWorker(int count, long deadline, long tick) {
		if(lifecycle == null || lifecycle.cancelled) {
			lifecycle = new ExplosionRayWorker.Lifecycle(originals.id, terrain.worldLifecycleId); workers.add(this);
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
			if(range.batch != null) adopt(range, deadline);
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
				remember(range, required);
				prepareCorridor(range, deadline);
			} else if(!range.state.active && posY >= 0 && posY < 256 && posX >= -30000000 && posX < 30000000 && posZ >= -30000000 && posZ < 30000000) {
				// Avoid submitting an empty batch just to discover the source section.
				required = ExplosionTerrainAccess.snapshot(range.access, posX >> 4, posY >> 4, posZ >> 4, deadline);
				if(required == null) { waitReason = "source snapshot"; continue; }
			}
			Snapshot[] inputs = ExplosionTerrainAccess.workerSnapshots(range.access, required, range.workingSet, deadline);
			if(!ExplosionWorkScheduler.before(deadline)) continue;
			ExplosionRayWorker.State state = range.state.copy();
			// Outstanding batches reserve from the same ray allowance as fallback;
			// parallelism must not multiply the caller's per-tick completed-ray cap.
			state.rayLimit = Math.min(count - raysThisTick - reserved, range.end - state.gspNum);
			BitSet[] covered = new BitSet[inputs.length];
			for(int i = 0; i < inputs.length; i++) {
				Snapshot input = inputs[i];
				ChunkMask chunk = (ChunkMask)chunks.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(input.chunkX, input.chunkZ));
				SectionMask section = chunk == null ? null : chunk.sections[input.sectionY];
				covered[i] = section == null ? new BitSet() : (BitSet)section.destruction.clone();
			}
			range.batch = ExplosionRayWorker.submit(state, inputs, covered, qualifiedKeys(range), lifecycle, range.scratch);
			if(range.batch == null) { range.access.defer(); waitReason = "worker queue"; }
			else { submittedBatches++; waitReason = "worker calculation"; }
		}
		if(gspNum > gspNumMax && ranges.isEmpty() && ExplosionWorkScheduler.before(deadline)) finishCollection();
	}

	/** Prepare a short geometric continuation in already-loaded terrain. This
	 * amortizes section handoffs without speculative chunk loading, unbounded
	 * capture queues or world reads from workers. Resistance still decides reach. */
	private void prepareCorridor(RayRange range, long deadline) {
		ExplosionRayWorker.State s = range.state;
		boolean build = s.building;
		int step = build ? s.buildStep : s.step;
		int limit = build ? (int)Math.ceil(s.buildLength) : Math.min(s.rayLength, Math.max(1, s.length));
		double dx = build ? s.bx : s.dx, dy = build ? s.by : s.dy, dz = build ? s.bz : s.dz;
		for(int sections = 0; sections < 4 && step < limit && ExplosionWorkScheduler.before(deadline); sections++) {
			int x = corridorCoordinate(posX, dx, step, build), y = corridorCoordinate(posY, dy, step, build), z = corridorCoordinate(posZ, dz, step, build);
			if(y < 0 || y >= 256 || x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000 || !build && s.contained && y >= s.surfaceY) return;
			if(ExplosionTerrainAccess.loaded(world, x >> 4, z >> 4) == null) return;
			Snapshot snapshot = ExplosionTerrainAccess.snapshot(range.access, x >> 4, y >> 4, z >> 4, deadline);
			if(snapshot == null) return;
			remember(range, snapshot);
			int low = step + 1, high = limit;
			while(low < high) {
				int middle = low + (high - low) / 2;
				if(snapshot.contains(corridorCoordinate(posX, dx, middle, build), corridorCoordinate(posY, dy, middle, build), corridorCoordinate(posZ, dz, middle, build))) low = middle + 1;
				else high = middle;
			}
			step = low;
		}
	}
	private static int corridorCoordinate(int origin, double direction, int step, boolean build) {
		double value = origin + direction * step;
		return (int)Math.floor(build ? value : (double)(float)value);
	}

	private long[] qualifiedKeys(RayRange range) {
		long[] keys = new long[range.qualified.size()];
		for(int i = 0; i < keys.length; i++) keys[i] = range.qualified.get(i);
		return keys;
	}

	private void adopt(RayRange range, long deadline) {
		ExplosionRayWorker.Batch batch = range.batch;
		int batchInputStep = range.state.step, batchInputBuildStep = range.state.buildStep;
		ExplosionRayWorker.Result result = batch.result;
		if(result == null && batch.failure == null) return;
		if(batch.failure != null) {
			workerFailures++; rejectedBatches++;
			MainRegistry.logger.error("Explosion terrain worker failed; retaining range checkpoint for bounded server fallback", batch.failure);
			batch.cancel(); range.batch = null; activateFallback(range, "worker failure", true); return;
		}
		boolean valid = !lifecycle.cancelled && !batch.cancelled && result.jobId == lifecycle.jobId && result.lifecycleId == lifecycle.id && result.worldLifecycleId == terrain.worldLifecycleId;
		boolean invalidSnapshot = false;
		if(valid) for(ExplosionRayWorker.Mask mask : result.masks) {
			// Cached inputs not actually read cannot invalidate useful work.
			if(!mask.used) continue;
			if(mask.snapshot.worldLifecycleId != terrain.worldLifecycleId) { invalidSnapshot = true; break; }
			if(!ExplosionTerrainAccess.validate(range.access, mask.snapshot, deadline)) { waitReason = "snapshot validation: " + range.access.status(); return; }
			// Once rejection is certain, loading/validating the remaining sections
			// only delays retry/fallback and can create another global wait barrier.
			if(!mask.snapshot.valid) { invalidSnapshot = true; break; }
		}
		workerNanos += result.calculationNanos; sectionMisses += result.sectionMisses;
		if(workerCpuNanos >= 0) workerCpuNanos = result.cpuNanos < 0 ? -1 : workerCpuNanos + result.cpuNanos;
		traversalOperations += result.traversalOperations; reconstructionOperations += result.reconstructionOperations;
		reusedSamples += result.reusedSamples; skippedReconstruction += result.skippedReconstruction; attenuationHits += result.attenuationHits;
		sharedSnapshotHits += result.sharedSnapshotHits;
		if(!valid || invalidSnapshot) {
			rejectedBatches++; repeatedOperations += result.operations;
			batch.dispose(); range.batch = null;
			if(invalidSnapshot) {
				snapshotRejections++; range.rejections++;
				if(range.rejections >= INVALIDATION_RETRIES) activateFallback(range, "repeated snapshot invalidation without committed progress", false);
			}
			if(!range.fallback) waitReason = "snapshot retry"; return;
		}
		// All input versions are checked before this bounded, indivisible OR/cursor
		// commit. No world callbacks run here and only the server owns these masks.
		for(int i = result.masks.size() - 1; i >= 0; i--) {
			ExplosionRayWorker.Mask mask = result.masks.get(i);
			Snapshot snapshot = mask.snapshot;
			if(mask.used) remember(range, snapshot);
			if(mask.destruction.isEmpty()) continue;
			ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(snapshot.chunkX, snapshot.chunkZ));
			SectionMask section = chunk.sections[snapshot.sectionY];
			if(section == null) chunk.sections[snapshot.sectionY] = section = new SectionMask();
			merge(chunk, section, mask.destruction, mask.tips);
		}
		range.state = result.state;
		range.qualified.clear(); range.qualified.addAll(result.qualified);
		range.pending = result.missing;
		if(result.missing) { range.x = result.chunkX; range.y = result.sectionY; range.z = result.chunkZ; }
		completedRays += result.completedRays; raysThisTick += result.completedRays;
		for(int i = 0; i < 3; i++) completedDirections[i] += result.directions[i];
		acceptedBatches++;
		if(result.completedRays > 0 || result.state.step != batchInputStep || result.state.buildStep != batchInputBuildStep) range.rejections = 0;
		batch.dispose(); range.batch = null;
	}

	private void remember(RayRange range, Snapshot snapshot) {
		remember(range.workingSet, snapshot); remember(corridor, snapshot);
	}
	private static void remember(ArrayList<int[]> workingSet, Snapshot snapshot) {
		for(int i = 0; i < workingSet.size(); i++) {
			int[] coord = workingSet.get(i);
			if(coord[0] == snapshot.chunkX && coord[1] == snapshot.sectionY && coord[2] == snapshot.chunkZ) { workingSet.remove(i); workingSet.add(coord); return; }
		}
		if(workingSet.size() == ExplosionRayWorker.MAX_BATCH_SNAPSHOTS) workingSet.remove(0);
		workingSet.add(new int[] { snapshot.chunkX, snapshot.sectionY, snapshot.chunkZ });
	}

	private void activateFallback(RayRange range, String reason, boolean failed) {
		if(range.batch != null) { range.batch.cancel(); range.batch = null; }
		range.scratch = new ExplosionRayWorker.Scratch();
		// A changing section must not demote thousands of unrelated future rays.
		// Finish this ray against live terrain, then resume parallel calculation.
		// An actual worker exception still keeps the failed range off that path.
		range.fallbackEnd = failed ? range.end : Math.min(range.end, range.state.gspNum + 1);
		waitReason = "bounded server fallback";
		if(!range.fallback) {
			range.fallback = true; fallbackActivations++;
			long tick = ExplosionWorkScheduler.currentTick();
			if(fallbackActivations == 1 || lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200) {
				logStatus("range fallback: " + reason); lastDiagnosticTick = tick;
			}
		}
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
		try { collectServer(count, deadline, range.access, range.fallback ? range.fallbackEnd : range.end); }
		finally {
			range.state = captureState(); range.qualified.clear(); range.qualified.addAll(rayChunkKeys);
			completedRays += gspNum - previous;
			applyState(allocator); rayChunks = savedChunks; rayChunkKeys = savedKeys;
		}
		if(range.state.gspNum >= range.fallbackEnd && range.fallbackEnd < range.end) {
			range.fallback = false; range.rejections = 0;
			waitReason = "worker resume after fallback ray";
		} else waitReason = "bounded server fallback: " + range.access.status();
	}

	private ExplosionRayWorker.State captureState() {
		ExplosionRayWorker.State state = new ExplosionRayWorker.State();
		state.posX = posX; state.posY = posY; state.posZ = posZ; state.length = length; state.rayLength = rayLength;
		state.initialStrength = strength; state.gspNum = gspNum; state.gspNumMax = gspNumMax; state.gx = gspX; state.gy = gspY;
		state.active = rayActive; state.recorded = rayRecorded; state.balanced = balancedDirections;
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
			range.scratch = new ExplosionRayWorker.Scratch();
			range.access.cancel();
		}
		terrain.cancel(); removalTerrain.cancel(); if(pressure != null) pressure.access.cancel(); workers.remove(this);
	}
	public void dispose() { cancelWorkers(); originals.close(); }
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
			reconstructionOperations++;
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
				Snapshot snapshot = ExplosionTerrainAccess.snapshot(access, x >> 4, y >> 4, z >> 4, deadline);
				if(snapshot == null) return false;
				int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
				if(!snapshot.air(bit) && !snapshot.liquid(bit)) {
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
		if(!restore(deadline) || !migrate(deadline) || !ExplosionWorkScheduler.before(deadline)) return;
		long tick = ExplosionWorkScheduler.currentTick();
		if(excavationTick != tick) { excavationTick = tick; excavationAttempts = 0; }
		// Coverage is only disposable after every result has committed. Release
		// originals together with the last possible late terminal notification.
		while(cleanupCursor != null && ExplosionWorkScheduler.before(deadline)) {
			ChunkMask done = cleanupCursor; cleanupCursor = done.next;
			// Collection is the only blocker for these entries. Activate once at
			// that transition rather than rediscovering them on every mask pass.
			if(inputsComplete()) {
				for(SectionMask section : done.sections) if(section != null) {
					section.pending.or(section.deferred); section.deferred.clear();
					section.effects.or(section.deferredEffects); section.deferredEffects.clear();
				}
				deferredBlocks -= done.deferred; deferredEffects -= done.deferredEffects;
				done.deferred = done.deferredEffects = 0; wake(done);
			}
			if(inputsComplete() && done.pending + done.effects == 0) {
				originals.releaseChunk((int)done.key, (int)(done.key >> 32)); removeChunk(done);
				if(removalActive && removalKey == done.key) removalActive = false;
			}
		}
		if(firstChunk == null) { if(inputsComplete()) { originals.clear(); excavationWait = "complete"; } return; }
		while(excavationReady(tick) && ExplosionWorkScheduler.before(deadline)) {
			ChunkMask chunk = removalActive ? (ChunkMask)chunks.getValueByKey(removalKey) : null;
			if(chunk == null || !chunk.queued) {
				int searched = 0;
				do {
					java.util.Map.Entry<Integer, DistanceBucket> entry = ordering.ceilingEntry(nextDistance);
					if(entry == null) entry = ordering.firstEntry();
					if(entry == null) return;
					chunk = entry.getValue().first; nextDistance = entry.getKey() + 1;
					rotate(entry.getValue());
				} while(chunk.blockedTick == tick && ++searched < queuedChunks && ExplosionWorkScheduler.before(deadline));
				if(chunk.blockedTick == tick || !ExplosionWorkScheduler.before(deadline)) return;
				removalKey = chunk.key; removalSection = chunk.cursorSection; removalBit = chunk.cursorBit; removalActive = true;
			}
			try {
				net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.chunk(removalTerrain, (int)chunk.key, (int)(chunk.key >> 32), deadline);
				if(live == null) {
					block(chunk, tick); excavationWait = removalTerrain.status(); continue;
				}
				while(removalSection < 16 && ExplosionWorkScheduler.before(deadline)) {
					SectionMask section = chunk.sections[sectionAt(removalSection)];
					if(section == null) { removalSection++; removalBit = 0; continue; }
					int bit = section.pending.nextSetBit(removalBit);
					int terminal = section.effects.nextSetBit(removalBit);
					if(terminal >= 0 && (bit < 0 || terminal < bit)) bit = terminal;
					if(bit < 0) { sectionsAdvanced++; removalSection++; removalBit = 0; continue; }
					removalBit = bit;
					int x = ((int)chunk.key << 4) + (bit & 15), y = (sectionAt(removalSection) << 4) + (bit >> 8), z = ((int)(chunk.key >> 32) << 4) + ((bit >> 4) & 15);
					boolean tip = section.tips != null && section.tips.get(bit);
					if(section.completed.get(bit)) {
						if(!ExplosionTerrainAccess.neighbors(removalTerrain, x, z, deadline)) { block(chunk, tick); break; }
						if(!ExplosionWorkScheduler.before(deadline)) return;
						// A late tip upgrades the effect, never repeats the removal. Preserve
						// the old block argument used by Forge's flag-3 notification path.
						long callbackStart = System.nanoTime();
						try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) { world.notifyBlockChange(x, y, z, originals.source(x, y, z)); }
						finally { surfaceCallbackNanos += System.nanoTime() - callbackStart; }
						section.notified.set(bit); section.effects.clear(bit); chunk.effects--; pendingEffects--; removalBit++; continue;
					}
					Block block = world.getBlock(x, y, z);
					if(!ExplosionWorkScheduler.before(deadline)) return;
					boolean change = !block.getMaterial().isLiquid() && !world.isAirBlock(x, y, z);
					if(!ExplosionWorkScheduler.before(deadline)) return;
					if(change) {
						if(getClass() == ExplosionNukeRayBatched.class && ExplosionTerrainAccess.canRemoveSection(world, live, sectionAt(removalSection))
								&& safeProgressiveBlock(block) && (!tip || !inputsComplete())) {
							if(applySectionColumn(chunk, section, live, bit, deadline)) { removalBit = bit + 1; continue; }
							if(!ExplosionWorkScheduler.before(deadline)) return;
						}
						if(!clearAttachments(x, y, z, deadline)) { block(chunk, tick); break; }
						if(!inputsComplete()) {
							// Unknown breakBlock/TE callbacks can mutate arbitrarily far away,
							// including uncaptured sections. Defer only that cell; do not run
							// such callbacks and then pretend a local snapshot covered them.
							if(!safeProgressiveBlock(block) || block.hasTileEntity(world.getBlockMetadata(x, y, z)) || world.getTileEntity(x, y, z) != null || getClass() != ExplosionNukeRayBatched.class) { excavationWait = "special callbacks await calculations"; deferBlock(chunk, section, bit); removalBit++; continue; }
							// Memory can be freed by another calculation before ours ends.
							// Retry this cursor next tick, without rescanning deferred callbacks.
							if(!remember(x, y, z, block)) { excavationWait = "original terrain memory ceiling"; block(chunk, tick); break; }
						}
						if(localLeafCallback(block) && !NuclearDropSuppression.prepareLeaves(world, x, y, z, block instanceof net.minecraft.block.BlockLog ? 4 : 1)) { excavationWait = "leaf attribution ceiling"; block(chunk, tick); break; }
						if(tip && inputsComplete() && !ExplosionTerrainAccess.neighbors(removalTerrain, x, z, deadline)) { block(chunk, tick); break; }
						if(!ExplosionWorkScheduler.before(deadline)) return;
						try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) {
							NuclearDropSuppression.clearInventory(world, x, y, z);
							if(tip && inputsComplete()) handleTip(x, y, z);
							else world.setBlock(x, y, z, Blocks.air, 0, 2);
						}
						if(!world.isAirBlock(x, y, z)) {
							// A denied/failed world update is not completed destruction work.
							block(chunk, tick);
							excavationWait = "world rejected block removal";
							if(!removalFailureReported && (lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200)) {
								logStatus("waiting; world rejected block removal"); lastDiagnosticTick = tick;
							}
							removalFailureReported = true;
							break;
						}
						if(block instanceof net.minecraft.block.BlockLeaves) NuclearDropSuppression.removed(world, x, y, z);
						destroyedBlocks++; normalRemovals++; removedBands[band(y)]++;
						if(tip && inputsComplete()) section.notified.set(bit);
						if(firstRemovalAt == 0) { firstRemovalAt = System.nanoTime(); logStatus("first successful block removal"); }
						removalFailureReported = false;
						if(tip && !inputsComplete()) addEffect(chunk, section, bit);
					}
					// Callbacks, tile cleanup, lighting and sync finish before the bit/cursor
					// advances, even if the indivisible world removal overruns the slice.
					section.pending.clear(bit); section.completed.set(bit); chunk.pending--; pendingBlocks--;
					if(!change) section.notified.set(bit); // No removal means no later terminal effect.
					removalBit = bit + 1;
					excavationWait = "progressing";
				}
				if(removalSection == 16) {
					removalActive = false; chunksAdvanced++;
					if(chunk.pending - chunk.deferred + chunk.effects - chunk.deferredEffects == 0) {
						dequeue(chunk);
						if(inputsComplete() && chunk.pending + chunk.effects == 0) { originals.releaseChunk((int)chunk.key, (int)(chunk.key >> 32)); removeChunk(chunk); }
					} else {
						// Late insertion before the cursor remains ready in the next pass.
						removalSection = removalBit = 0;
					}
				}
			} finally {
				chunk.cursorSection = removalSection; chunk.cursorBit = removalBit;
				// A large partial chunk yields to the next queued chunk on the next
				// dispatch. Its own cursor survives independently of queue rotation.
				removalActive = false;
			}
		}
		if(queuedChunks == 0 && (deferredBlocks != 0 || deferredEffects != 0)) excavationWait = "deferred callbacks await calculations";
	}
	private boolean remember(int x, int y, int z, Block block) {
		long start = System.nanoTime();
		try { boolean remembered = originals.remember(x, y, z, block); if(!remembered) memoryWaits++; return remembered; }
		finally { preservationNanos += System.nanoTime() - start; }
	}
	private boolean applySectionColumn(ChunkMask chunk, SectionMask section, net.minecraft.world.chunk.Chunk live, int first, long deadline) {
		int count = 0;
		for(int bit = first; bit < 4096 && ExplosionWorkScheduler.before(deadline); bit += 256) {
			if(!section.pending.get(bit) || inputsComplete() && section.tips != null && section.tips.get(bit)) continue;
			int x = (live.xPosition << 4) + (bit & 15), y = (sectionAt(removalSection) << 4) + (bit >> 8), z = (live.zPosition << 4) + (bit >> 4 & 15);
			Block block = live.getBlock(bit & 15, y, bit >> 4 & 15);
			// Positive opacity makes the highest native removal schedule the same
			// skylight gap work as every lower removal. Zero-opacity cells fall back.
			if(!safeProgressiveBlock(block) || localLeafCallback(block) || block.getClass() == com.hbm.blocks.generic.BlockOutgas.class || block.getMaterial().isLiquid() || world.isAirBlock(x, y, z)
					|| block.hasTileEntity(live.getBlockMetadata(bit & 15, y, bit >> 4 & 15)) || world.getTileEntity(x, y, z) != null
					|| block.getLightOpacity(world, x, y, z) <= 0) continue;
			if(!clearAttachments(x, y, z, deadline)) break;
			if(!inputsComplete() && !remember(x, y, z, block)) break;
			columnBits[count++] = bit;
		}
		if(count < 2 || columnBits[0] != first || !ExplosionWorkScheduler.before(deadline)) return false;
		columnChanged.clear();
		// At most sixteen cells form one indivisible column transaction. Finish
		// its lighting/sync even if a native callback overruns; start no next batch.
		ExplosionTerrainAccess.removeSectionColumn(world, live, sectionAt(removalSection), columnBits, count, columnChanged);
		for(int bit = columnChanged.nextSetBit(0); bit >= 0; bit = columnChanged.nextSetBit(bit + 1)) {
			section.pending.clear(bit); section.completed.set(bit); chunk.pending--; pendingBlocks--;
			destroyedBlocks++; fastRemovals++; removedBands[band((sectionAt(removalSection) << 4) + (bit >> 8))]++;
			if(section.tips != null && section.tips.get(bit)) addEffect(chunk, section, bit);
		}
		if(!columnChanged.isEmpty()) {
			excavationWait = "progressing section batches";
			if(firstRemovalAt == 0) { firstRemovalAt = System.nanoTime(); logStatus("first successful section removal"); }
		}
		return columnChanged.get(first);
	}
	private static boolean safeProgressiveBlock(Block block) { return NuclearBlockBehavior.progressive(block) || localLeafCallback(block); }
	private static boolean localLeafCallback(Block block) {
		Class<?> c = block.getClass();
		return c == net.minecraft.block.BlockOldLog.class || c == net.minecraft.block.BlockNewLog.class
				|| c == net.minecraft.block.BlockOldLeaf.class || c == net.minecraft.block.BlockNewLeaf.class;
	}

	protected void handleTip(int x, int y, int z) { world.setBlock(x, y, z, Blocks.air, 0, 3); }
	public static float masqueradeResistance(Block block) {
		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3;
		return block.getExplosionResistance(null);
	}

	public void readFromNBT(NBTTagCompound tag) {
		cancelWorkers(); terrain.read(tag);
		originals.clear(); pendingBlocks = pendingEffects = deferredBlocks = deferredEffects = 0; queuedChunks = 0; nextDistance = 0; excavationTick = Long.MIN_VALUE; excavationAttempts = 0;
		ranges.clear(); corridor.clear(); allocating = null; rangeCursor = 0;
		savedDiagnostics = tag.hasKey("selectedBlocks"); selectedBlocks = tag.getLong("selectedBlocks"); destroyedBlocks = tag.getLong("destroyedBlocks"); rejectedBatches = tag.getLong("rejectedBatches");
		chunks = new LongHashMap(); firstChunk = lastChunk = cleanupCursor = null; ordering.clear();
		rayChunks = new LongHashMap(); rayChunkKeys = new ArrayList<Long>();
		balancedDirections = tag.getBoolean("balancedDirections");
		pressure = tag.hasKey("pressureWork") ? new PressureWork(tag.getCompoundTag("pressureWork")) : null;
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
			removalActive = tag.getBoolean("balancedSections") && tag.getBoolean("removalActive"); removalKey = tag.getLong("removalKey");
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
			range.fallbackEnd = !range.fallback ? range.end : item.hasKey("fallbackEnd") ? Math.max(range.state.gspNum, Math.min(range.end, item.getInteger("fallbackEnd"))) : Math.min(range.end, range.state.gspNum + 1);
			int[] keys = item.getIntArray("qualified");
			for(int k = 0; k + 1 < keys.length; k += 2) range.qualified.add(ChunkCoordIntPair.chunkXZ2Int(keys[k], keys[k + 1]));
			ranges.add(range); if(!range.sealed) allocating = range;
		}
		completedRays = tag.hasKey("completedRays") ? tag.getLong("completedRays") : Math.max(0, gspNum - 1);
		submittedBatches = tag.getLong("submittedBatches"); acceptedBatches = tag.getLong("acceptedBatches"); snapshotRejections = tag.getLong("snapshotRejections");
		workerFailures = tag.getLong("workerFailures"); repeatedOperations = tag.getLong("repeatedOperations"); workerNanos = tag.getLong("workerNanos");
		sectionMisses = tag.getLong("sectionMisses"); fallbackActivations = tag.getLong("fallbackActivations");
		traversalOperations = tag.getLong("traversalOperations"); reconstructionOperations = tag.getLong("reconstructionOperations");
		reusedSamples = tag.getLong("reusedSamples"); skippedReconstruction = tag.getLong("skippedReconstruction"); attenuationHits = tag.getLong("attenuationHits"); workerCpuNanos = tag.getLong("workerCpuNanos");
		sharedSnapshotHits = tag.getLong("sharedSnapshotHits");
		collectionServerNanos = tag.getLong("collectionServerNanos"); removalServerNanos = tag.getLong("removalServerNanos");
		fastRemovals = tag.getLong("fastRemovals"); normalRemovals = tag.getLong("normalRemovals");
		sectionsAdvanced = tag.getLong("sectionsAdvanced"); chunksAdvanced = tag.getLong("chunksAdvanced"); preservationNanos = tag.getLong("preservationNanos"); diagnosticRemoved = destroyedBlocks;
		for(int i = 0; i < 3; i++) { removedBands[i] = tag.getLong("removedBand" + i); completedDirections[i] = tag.getLong("directionBand" + i); }
		memoryWaits = tag.getLong("memoryWaits"); surfaceCallbackNanos = tag.getLong("surfaceCallbackNanos");
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
		if(restoring != null && !originals.restore(restoring, deadline)) { waitReason = "restoring required original terrain"; return false; }
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
				boolean progressive = restoring.getBoolean("progressive");
				section.pending.or(progressive ? BitSet.valueOf(data.getByteArray("pending")) : section.destruction);
				section.pending.and(section.destruction);
				if(progressive) { section.completed.or(BitSet.valueOf(data.getByteArray("completed"))); section.completed.and(section.destruction); section.notified.or(BitSet.valueOf(data.getByteArray("notified"))); section.notified.and(section.completed); }
				section.pending.andNot(section.completed);
				// Re-audit saved deferrals once through the bounded application path.
				// Eligibility may have expanded since this checkpoint was written;
				// genuine special callbacks will defer again without executing them.
				if(data.hasKey("tips")) {
					section.tips = BitSet.valueOf(data.getByteArray("tips")); section.tips.and(section.destruction);
				}
				ChunkMask chunk = chunkMask(readCoord(item)); chunk.sections[sectionY] = section;
				chunk.cursorSection = restoring.getBoolean("balancedSections") ? item.getInteger("cursorSection") : 0;
				chunk.cursorBit = !restoring.getBoolean("balancedSections") ? 0 : item.hasKey("cursorBit") ? item.getInteger("cursorBit") : removalActive && removalKey == chunk.key ? removalBit : 0;
				int effects = 0;
				if(section.tips != null || data.hasKey("effects")) {
					BitSet late = section.tips == null ? new BitSet() : (BitSet)section.tips.clone();
					late.or(BitSet.valueOf(data.getByteArray("effects"))); late.and(section.completed); late.andNot(section.notified); effects = late.cardinality();
					if(restoring.getBoolean("complete") && (pressure == null || pressure.complete)) section.effects.or(late);
					else { section.deferredEffects.or(late); chunk.deferredEffects += effects; deferredEffects += effects; }
				}
				int deferred = section.deferred.cardinality(), pending = section.pending.cardinality() + deferred;
				chunk.deferred += deferred; deferredBlocks += deferred;
				chunk.pending += pending; pendingBlocks += pending; chunk.effects += effects; pendingEffects += effects;
				if(chunk.pending + chunk.effects > 0) enqueue(chunk);
			} else if(restorePhase == 1) {
				int[] keys = restoring.getIntArray("rayChunks");
				if(restoreIndex < keys.length / 2) { addRayChunk(ChunkCoordIntPair.chunkXZ2Int(keys[restoreIndex * 2], keys[restoreIndex * 2 + 1])); restoreIndex++; }
				else restorePhase++;
			} else {
				isAusf3Complete = legacy == null && restoring.getBoolean("complete"); restoring = null; removalActive = false;
				if(isAusf3Complete) cleanupCursor = firstChunk;
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
				if(isAusf3Complete) cleanupCursor = firstChunk;
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
		tag.setBoolean("balancedDirections", balancedDirections);
		for(int i = 0; i < 3; i++) { tag.setLong("removedBand" + i, removedBands[i]); tag.setLong("directionBand" + i, completedDirections[i]); }
		tag.setLong("memoryWaits", memoryWaits); tag.setLong("surfaceCallbackNanos", surfaceCallbackNanos);
		if(pressure != null) tag.setTag("pressureWork", pressure.write());
		tag.setBoolean("progressive", true); tag.setBoolean("balancedSections", true);
		originals.write(tag);
		tag.setLong("selectedBlocks", selectedBlocks); tag.setLong("destroyedBlocks", destroyedBlocks); tag.setLong("rejectedBatches", rejectedBatches);
		tag.setLong("completedRays", completedRays); tag.setLong("submittedBatches", submittedBatches); tag.setLong("acceptedBatches", acceptedBatches);
		tag.setLong("snapshotRejections", snapshotRejections); tag.setLong("workerFailures", workerFailures); tag.setLong("repeatedOperations", repeatedOperations);
		tag.setLong("workerNanos", workerNanos); tag.setLong("sectionMisses", sectionMisses); tag.setLong("fallbackActivations", fallbackActivations);
		tag.setLong("traversalOperations", traversalOperations); tag.setLong("reconstructionOperations", reconstructionOperations);
		tag.setLong("reusedSamples", reusedSamples); tag.setLong("skippedReconstruction", skippedReconstruction); tag.setLong("attenuationHits", attenuationHits); tag.setLong("workerCpuNanos", workerCpuNanos);
		tag.setLong("sharedSnapshotHits", sharedSnapshotHits);
		tag.setLong("collectionServerNanos", collectionServerNanos); tag.setLong("removalServerNanos", removalServerNanos);
		tag.setLong("fastRemovals", fastRemovals); tag.setLong("normalRemovals", normalRemovals); tag.setLong("sectionsAdvanced", sectionsAdvanced); tag.setLong("chunksAdvanced", chunksAdvanced); tag.setLong("preservationNanos", preservationNanos);
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
			item.setInteger("fallbackEnd", range.fallbackEnd);
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
			item.setInteger("cursorSection", chunk.cursorSection); item.setInteger("cursorBit", chunk.cursorBit);
			NBTTagList sections = new NBTTagList();
			for(int y = 0; y < 16; y++) {
				SectionMask section = chunk.sections[y];
				if(section == null || section.destruction.isEmpty()) continue;
				NBTTagCompound data = new NBTTagCompound(); data.setInteger("y", y);
				data.setByteArray("destruction", section.destruction.toByteArray());
				// Keep the v3 pending union for older progressive saves/readers; the
				// additional classification prevents rescans in this implementation.
				BitSet pending = (BitSet)section.pending.clone(); pending.or(section.deferred);
				BitSet effects = (BitSet)section.effects.clone(); effects.or(section.deferredEffects); data.setByteArray("effects", effects.toByteArray());
				data.setByteArray("pending", pending.toByteArray()); data.setByteArray("deferred", section.deferred.toByteArray()); data.setByteArray("completed", section.completed.toByteArray()); data.setByteArray("notified", section.notified.toByteArray());
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
