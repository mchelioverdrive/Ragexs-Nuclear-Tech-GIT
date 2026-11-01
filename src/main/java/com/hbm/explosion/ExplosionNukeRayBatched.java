package com.hbm.explosion;

import java.util.BitSet;
import java.util.TreeMap;

import com.hbm.config.GeneralConfig;
import com.hbm.main.MainRegistry;
import com.hbm.explosion.ExplosionTerrainAccess.Access;
import com.hbm.explosion.ExplosionTerrainAccess.Snapshot;
import com.hbm.explosion.nuclear.BurstType;
import com.hbm.explosion.nuclear.NuclearDetonationSpec;
import com.hbm.explosion.nuclear.NuclearEffectsProfile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.LongHashMap;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

/** Geometric nuclear excavation. The historical public name is kept for integrations. */
public class ExplosionNukeRayBatched {
	private static final int WORK_VERSION = 4;
	private static final int MAX_PENDING_BLOCKS = 32768, MAX_MASK_CHUNKS = 128;
	private LongHashMap chunks = new LongHashMap();
	private ChunkMask firstChunk, lastChunk, cleanupCursor;

	private final TreeMap<Integer, DistanceBucket> ordering = new TreeMap<Integer, DistanceBucket>();
	int posX, posY, posZ;
	World world;
	int strength, length;
	private final Access terrain, removalTerrain;
	private final ExplosionTerrainAccess.History originals;
	private CraterWork crater;
	private PressureWork pressure;
	private final CraterRemnantCleanup remnants = new CraterRemnantCleanup(this);
	// Compatibility field: it now reports geometric enumeration completion.
	public boolean isAusf3Complete;
	private long pendingBlocks, pendingEffects;
	private int queuedChunks, maskChunks, nextDistance;
	private long excavationTick = Long.MIN_VALUE;
	private int excavationAttempts;
	private boolean excavationFirst;
	private String excavationWait = "no committed masks";
	private boolean removalActive;
	private long removalKey;
	private int removalSection, removalBit;
	private long selectedBlocks, destroyedBlocks, lastDiagnosticTick = Long.MIN_VALUE;
	private boolean removalFailureReported, savedDiagnostics;
	private boolean materialReleased;
	private long fastRemovals, normalRemovals, sectionsAdvanced, chunksAdvanced, preservationNanos;
	private long memoryWaits, surfaceCallbackNanos, surfaceSelected, surfaceRemoved, firstSurfaceRemovalAt;
	private long startedAt = System.nanoTime(), firstRemovalAt, removalServerNanos;
	private final long[] removedBands = new long[3];
	private final int[] columnBits = new int[16];
	private final Block[] columnSources = new Block[16];
	private final int[] columnMetadata = new int[16];
	private final BitSet columnChanged = new BitSet(4096);
	private final BitSet columnMaterial = new BitSet(4096);
	private static final java.util.Set<ExplosionNukeRayBatched> active = new java.util.HashSet<ExplosionNukeRayBatched>();
	private NBTTagCompound restoring;
	private int restorePhase, restoreIndex, restoreSection;
	public void includeInitializationDelay(long nanos) { startedAt -= Math.max(0L, nanos); }
	public void logStatus(String status) {
		if(!GeneralConfig.enableExtendedLogging) return;
		MainRegistry.logger.info("[NUKE TERRAIN] dim=" + world.provider.dimensionId + " at=" + posX + "," + posY + "," + posZ
				+ " status=" + status + " craterComplete=" + isAusf3Complete + " selected=" + selectedBlocks + " removed=" + destroyedBlocks
				+ " pending=" + pendingBlocks + " masks=" + maskChunks + " effects=" + pendingEffects
				+ " pressure=" + (pressure == null ? "none" : pressure.status()) + " retainedOriginalBytes=" + originals.bytes()
				+ " fastRemoved=" + fastRemovals + " normalRemoved=" + normalRemovals + " wait=" + excavationWait
				+ " surfaceSelected=" + surfaceSelected + " surfaceRemoved=" + surfaceRemoved + " memoryWaits=" + memoryWaits
				+ " preservationMs=" + preservationNanos / 1000000L + " callbackMs=" + surfaceCallbackNanos / 1000000L
				+ " craterShieldSamples=" + crater.shield.traversal + " craterShieldReuse=" + crater.shield.reused
				+ " elapsedMs=" + (System.nanoTime() - startedAt) / 1000000L + " " + ExplosionTerrainAccess.diagnostics());
	}
	private static class SectionMask {
		// Coverage survives pending-bit clearing. Late results can add new work
		// or upgrade a completed normal hit to one terminal notification.
		final BitSet destruction = new BitSet();
		final BitSet pending = new BitSet(), completed = new BitSet(), notified = new BitSet();
		final BitSet effects = new BitSet();
		final BitSet surface = new BitSet();
		BitSet tips;
	}
	private static class ChunkMask {
		final long key;
		final int distance;
		final SectionMask[] sections = new SectionMask[16];
		ChunkMask previous, next, orderPrevious, orderNext;
		boolean queued;
		int cursorSection, cursorBit;
		long pending, effects, blockedTick = Long.MIN_VALUE;
		ChunkMask(long key, int distance) { this.key = key; this.distance = distance; }
	}
	private static class DistanceBucket {
		ChunkMask first, last;
	}

	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length) {
		this(world, x, y, z, strength, speed, length, false, y, strength);
	}
	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length, boolean contained, int surfaceY) {
		this(world, x, y, z, strength, speed, length, contained, surfaceY, strength);
	}
	public ExplosionNukeRayBatched(World world, int x, int y, int z, int strength, int speed, int length, boolean contained, int surfaceY, int reach) {
		this.world = world; posX = x; posY = y; posZ = z; this.strength = strength; this.length = length;
		originals = new ExplosionTerrainAccess.History(world, x, y, z);
		terrain = new Access(world, originals); removalTerrain = new Access(world);
		double radius = Math.max(0D, strength * .5D);
		crater = new CraterWork(radius, radius * .30D, radius * .15D, contained ? y : surfaceY);
		isAusf3Complete = crater.complete;
		active.add(this);
	}
	/** Authoritative yield/coupling dimensions come from the effects solver, never the fireball. */
	public void configureCrater(NuclearDetonationSpec spec, NuclearEffectsProfile effects, double surfaceY) {
		boolean buried = spec.burstType == BurstType.SUBSURFACE;
		double radius = buried ? effects.cavityRadius : effects.craterRadius;
		if(spec.burstType == BurstType.AIR || spec.burstType == BurstType.VACUUM) radius = 0D;
		crater = new CraterWork(radius, buried ? effects.cavityRadius : effects.craterDepth,
				buried ? effects.cavityRadius : effects.craterDepth * .5D, buried ? posY + .5D : surfaceY);
		isAusf3Complete = crater.complete;
	}
	public boolean hasPendingWork() { return restoring != null || !crater.complete || pressure != null && !pressure.complete || firstChunk != null || originals.bytes() != 0 || remnants.pending(); }
	public void awaitFalloutCleanup() { remnants.awaitFallout(); }
	public void bindFalloutCleanup(java.util.UUID id) { remnants.bindFallout(id); }
	public void configureFalloutCleanup(com.hbm.entity.effect.EntityFalloutRain rain) {
		if(remnants.pending()) rain.setCraterCleanup(posX, posZ, crater.radius, crater.depth, crater.upper, crater.centerY);
	}
	public void noFalloutCleanup() { remnants.noFallout(); }
	public static void falloutFinished(World world, java.util.UUID id) {
		for(ExplosionNukeRayBatched job : active) if(job.world == world) job.remnants.finishedFallout(id);
	}
	static void terrainChanged(World world, int x, int y, int z, int x2, int y2, int z2) {
		for(ExplosionNukeRayBatched job : active) if(job.world == world) job.remnants.changed(x, y, z, x2, y2, z2);
	}
	static void terrainRenderChanged(World world, int x, int y, int z, int x2, int y2, int z2) {
		for(ExplosionNukeRayBatched job : active) if(job.world == world) job.remnants.renderChanged(x, y, z, x2, y2, z2);
	}
	public boolean craterFinished() { return restoring == null && crater.complete && pendingBlocks == 0 && pendingEffects == 0; }
	public boolean hasMaterialRelease() { return materialReleased; }
	private boolean selectionCapacity() { return pendingBlocks + pendingEffects < MAX_PENDING_BLOCKS && maskChunks < MAX_MASK_CHUNKS; }
	private boolean craterCapacity() {
		// Reserve admission space for the pressure cursor. Excavation waiting on
		// retained-history memory must not prevent pressure from advancing and
		// releasing that history behind its local shielding window.
		return pendingBlocks + pendingEffects < MAX_PENDING_BLOCKS / 2 && maskChunks < MAX_MASK_CHUNKS / 2;
	}
	public boolean canWork(long tick, int count) {
		if(lastDiagnosticTick == Long.MIN_VALUE || tick - lastDiagnosticTick >= 200) { logStatus("geometric excavation"); lastDiagnosticTick = tick; }
		return restoring != null || cleanupCursor != null || excavationReady(tick) || selectorsComplete() && originals.releasePending()
				|| selectorsComplete() && firstChunk == null && remnants.ready(tick)
				|| craterCapacity() && !crater.complete && terrain.ready(tick) || selectionCapacity() && pressure != null && pressure.ready(tick);
	}
	/** Each resumable phase receives a bounded opportunity within the shared scheduler slice. */
	public void advance(long deadline, long tick) {
		if(!restore(deadline)) return;
		if(excavationFirst) processChunk(Math.min(deadline, System.nanoTime() + 250_000L));
		if(!crater.complete && craterCapacity() && terrain.ready(tick)) crater.advance(Math.min(deadline, System.nanoTime() + 250_000L));
		if(pressure != null && pressure.ready(tick) && selectionCapacity()) pressure.advance(Math.min(deadline, System.nanoTime() + 250_000L));
		if(ExplosionWorkScheduler.before(deadline)) processChunk(deadline);
		if(selectorsComplete() && ExplosionWorkScheduler.before(deadline)) originals.releaseUnowned(chunks, deadline);
		else if(ExplosionWorkScheduler.before(deadline)) originals.releaseUnowned(chunks, (cx, cz) -> needsOriginals(cx, cz), deadline);
		if(selectorsComplete() && firstChunk == null) originals.clear();
		if(selectorsComplete() && firstChunk == null && remnants.ready(tick) && ExplosionWorkScheduler.before(deadline)) remnants.advance(deadline, tick);
		excavationFirst = !excavationFirst;
	}
	private boolean excavationReady(long tick) { return queuedChunks > 0 && (removalActive || excavationTick != tick || excavationAttempts < queuedChunks); }
	private boolean needsOriginals(int x, int z) {
		// Both selectors stream outward. Six completed chunk rings exceed the
		// 64-block local shielding window plus cell/chunk boundary rounding.
		int ring = Math.max(Math.abs(x - (posX >> 4)), Math.abs(z - (posZ >> 4)));
		if(!crater.complete && ring + 6 >= (int)Math.ceil((Math.sqrt(crater.chunkIndex + 1D) - 1D) / 2D)) return true;
		return pressure != null && pressure.needsOriginals(x, z);
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
		chunk = new ChunkMask(key, distance(key)); chunks.add(key, chunk); maskChunks++;
		chunk.previous = lastChunk;
		if(lastChunk == null) firstChunk = chunk; else lastChunk.next = chunk;
		lastChunk = chunk;
		return chunk;
	}
	private void enqueue(ChunkMask chunk) {
		if(chunk.queued || chunk.pending + chunk.effects == 0) return;
		chunk.queued = true; queuedChunks++;
		if(excavationTick == ExplosionWorkScheduler.currentTick() && chunk.blockedTick == excavationTick) excavationAttempts++;
		DistanceBucket bucket = ordering.get(chunk.distance);
		if(bucket == null) { bucket = new DistanceBucket(); ordering.put(chunk.distance, bucket); }
		chunk.orderPrevious = bucket.last;
		if(bucket.last == null) bucket.first = chunk; else bucket.last.orderNext = chunk;
		bucket.last = chunk;
	}
	private void removeChunk(ChunkMask chunk) {
		chunks.remove(chunk.key); maskChunks--;
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
		// Another effect can select a cell before the active cursor, including in an earlier
		// section. Revisit only pending work; completed bits still prevent repeats.
		if(removalActive && removalKey == chunk.key) { removalSection = removalBit = 0; }
	}
	private void block(ChunkMask chunk, long tick) {
		if(chunk.queued && chunk.blockedTick != tick) excavationAttempts++;
		chunk.blockedTick = tick; removalActive = false;
	}
	private void addEffect(ChunkMask chunk, SectionMask section, int bit) {
		if(section.effects.get(bit) || section.notified.get(bit)) return;
		chunk.effects++; pendingEffects++;
		section.effects.set(bit);
	}
	private boolean selectorsComplete() { return restoring == null && crater.complete && (pressure == null || pressure.complete); }
	private void beginCleanup() { cleanupCursor = firstChunk; }
	private void releaseOriginals(ChunkMask chunk) {
		// Geometry-edge shielding and pressure can still need excavated overburden.
		if(selectorsComplete()) originals.releaseChunk((int)chunk.key, (int)(chunk.key >> 32));
	}
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
		try { return clearAttachments(x, y, z, deadline, 0, false); }
		finally { surfaceCallbackNanos += System.nanoTime() - start; }
	}
	private boolean clearAttachments(int x, int y, int z, long deadline, int depth, boolean remnant) {
		if(!ExplosionWorkScheduler.before(deadline)) return false;
		if(remnant) {
			if(!remnants.valid()) return false;
			for(int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) for(int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++)
				if(ExplosionTerrainAccess.loaded(world, cx, cz) == null) return false;
		} else if(!ExplosionTerrainAccess.neighbors(removalTerrain, x, z, deadline)) return false;
		if(!NuclearDropSuppression.prepareGravity(world, x, y, z)) return false;
		for(net.minecraftforge.common.util.ForgeDirection dir : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
			int xx = x + dir.offsetX, yy = y + dir.offsetY, zz = z + dir.offsetZ;
			if(yy < 0 || yy > 255 || !NuclearBlockBehavior.attachedTo(world, xx, yy, zz, x, y, z)) continue;
			if(remnant && !insideCrater(xx, yy, zz)) return false;
			if(!ExplosionWorkScheduler.before(deadline) || depth >= 256) return false;
			Block child = world.getBlock(xx, yy, zz);
			int childMeta = remnant ? world.getBlockMetadata(xx, yy, zz) : 0;
			float resistance = NuclearBlockBehavior.cost(world, child, xx, yy, zz, posX, posY, posZ);
			if(Float.isInfinite(resistance)) continue;

			if(!clearAttachments(xx, yy, zz, deadline, depth + 1, remnant)) return false;
			if(remnant && (!remnants.valid() || world.getBlock(xx, yy, zz) != child || world.getBlockMetadata(xx, yy, zz) != childMeta
					|| Float.isInfinite(NuclearBlockBehavior.cost(world, child, xx, yy, zz, posX, posY, posZ)))) return false;
			if(!selectorsComplete() && !remember(xx, yy, zz, child)) return false;
			if(!ExplosionWorkScheduler.before(deadline)) return false;
			// Preserve and remove the attachment before its support. Its ordinary
			// scheduled support check later sees air and cannot manufacture drops.
			long previousChange = remnant ? remnants.changingAt(xx, yy, zz) : Long.MIN_VALUE;
			try {
				try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) {
					NuclearDropSuppression.clearInventory(world, xx, yy, zz);
					world.setBlock(xx, yy, zz, Blocks.air, 0, 2);
				}
				if(!world.isAirBlock(xx, yy, zz)) return false;
				if(remnant) remnants.removed(xx, yy, zz);
				// Geometry and pressure both skip live air. No extra completed mask is
				// needed for an attachment; any existing pending bit will retire as air.
				destroyedBlocks++; normalRemovals++; removedBands[band(yy)]++;
				materialReleased |= NuclearBlockBehavior.ordinaryTerrain(child) || NuclearBlockBehavior.constructionBarrier(child);
				try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) { world.notifyBlockChange(xx, yy, zz, child); }
			} finally { if(remnant) remnants.finishChange(previousChange); }
		}
		return ExplosionWorkScheduler.before(deadline);
	}
	boolean insideCrater(int x, int y, int z) {
		if(y < 0 || y > 255 || crater.radiusSquared <= 0D) return false;
		double dx = x - (double)posX, dz = z - (double)posZ, radial = (dx * dx + dz * dz) / crater.radiusSquared;
		if(radial > 1D) return false;
		double height = Math.sqrt(1D - radial);
		return y >= Math.ceil(crater.centerY - crater.depth * height - .5D)
				&& y <= Math.floor(crater.centerY + crater.upper * height - .5D);
	}
	boolean removeRemnant(int x, int y, int z, Block expected, int meta, long deadline) {
		Block live = world.getBlock(x, y, z);
		if(live == Blocks.air) return true; // An attachment callback already removed it.
		if(live != expected || world.getBlockMetadata(x, y, z) != meta || !insideCrater(x, y, z)
				|| Float.isInfinite(NuclearBlockBehavior.cost(world, live, x, y, z, posX, posY, posZ))) return false;
		if(!clearAttachments(x, y, z, deadline, 0, true) || !remnants.valid() || !NuclearDropSuppression.prepareMutation(world, x, y, z)
				|| !ExplosionWorkScheduler.before(deadline)) return false;
		live = world.getBlock(x, y, z);
		if(live != expected || world.getBlockMetadata(x, y, z) != meta
				|| Float.isInfinite(NuclearBlockBehavior.cost(world, live, x, y, z, posX, posY, posZ))) return false;
		try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) {
			NuclearDropSuppression.clearInventory(world, x, y, z);
			world.setBlock(x, y, z, Blocks.air, 0, 3);
		}
		if(!world.isAirBlock(x, y, z)) return false;
		NuclearDropSuppression.removed(world, x, y, z);
		destroyedBlocks++; normalRemovals++;
		return true;
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
		long chunkIndex;
		int sectionY = -1, entry, targetX, targetY, targetZ;
		int chunkX, chunkZ;
		int legacyColumn, legacyY = -1;
		final BitSet blockedColumns = new BitSet(256);
		ExplosionTerrainAccess.PressureSection section;
		final Shielding shield;
		boolean candidate, complete, chunkStarted;
		long frontWait = Long.MIN_VALUE, inspected, selected, shielded, skippedSections, inspectedSections, skippedChunks, traversal, reused, activeNanos;
		PressureWork(double x, double y, double z, double maximum, double fivePsi, double factor) {
			this.x = x; this.y = y; this.z = z; this.maximum = maximum; this.fivePsi = fivePsi; this.factor = factor; shield = new Shielding(access, x, y, z);
			complete = !finite(x) || !finite(y) || !finite(z) || !finite(maximum) || !finite(fivePsi) || !finite(factor) || maximum <= 0D || fivePsi <= 0D;
		}
		PressureWork(NBTTagCompound t) {
			this(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getDouble("maximum"), t.getDouble("fivePsi"), t.getDouble("factor"));
			reached = t.getDouble("reached"); chunkIndex = t.getLong("chunk");
			if(t.getInteger("selectorVersion") == 1) {
				sectionY = t.getInteger("sectionY"); entry = t.getInteger("entry"); chunkStarted = t.getBoolean("chunkStarted");
				byte[] blocked = t.getByteArray("blocked");
				blockedColumns.or(BitSet.valueOf(blocked.length <= 32 ? blocked : java.util.Arrays.copyOf(blocked, 32))); legacyColumn = t.getInteger("legacyColumn"); legacyY = t.getInteger("legacyY");
			} else {
				// Old column sweeps resume only their unvisited cells. Do not reinterpret
				// a saved column/Y cursor as a new section cursor or repeat callbacks.
				legacyColumn = t.getInteger("column"); legacyY = t.getInteger("scanY");
			}
			complete |= t.getBoolean("complete"); inspected = t.getLong("inspected"); selected = t.getLong("selected"); shielded = t.getLong("shielded");
			skippedSections = t.getLong("skippedSections"); inspectedSections = t.getLong("inspectedSections"); skippedChunks = t.getLong("skippedChunks");
			traversal = t.getLong("traversal"); reused = t.getLong("reused"); activeNanos = t.getLong("activeNanos");
			shield.traversal = traversal; shield.reused = reused;
			long extent = (long)Math.ceil(maximum / 16D) + 1L;
			if(!t.hasKey("chunk") || !finite(reached) || reached < 0D || chunkIndex < 0L || chunkIndex > (2L * extent + 1L) * (2L * extent + 1L)
					|| sectionY < -1 || sectionY > 15 || entry < 0 || entry > 4096 || legacyColumn < 0 || legacyColumn > 256 || legacyY < -1 || legacyY > 255) complete = true;
		}
		boolean ready(long tick) { return !complete && frontWait != tick && access.ready(tick); }
		String status() { return "[chunk=" + chunkIndex + ",section=" + sectionY + ",entry=" + entry + ",inspected=" + inspected + ",selected=" + selected + ",shielded=" + shielded + ",sectionsSkipped=" + skippedSections + ",sectionsInspected=" + inspectedSections + ",chunksRejected=" + skippedChunks + ",traversalOps=" + traversal + ",reusedSamples=" + reused + ",activeMs=" + activeNanos / 1000000L + ",complete=" + complete + "]"; }
		void closeSection() { if(section != null) { section.close(); section = null; } }
		void cancel() { closeSection(); access.cancel(); shield.cancel(); }
		void coordinates() {
			int[] offset = spiral(chunkIndex);
			chunkX = (MathHelper.floor_double(x) >> 4) + offset[0]; chunkZ = (MathHelper.floor_double(z) >> 4) + offset[1];
		}
		double minimumDistanceSquared(int cx, int cz) {
			double dx = Math.max(0D, Math.max((cx << 4) + .5D - x, x - ((cx << 4) + 15.5D)));
			double dz = Math.max(0D, Math.max((cz << 4) + .5D - z, z - ((cz << 4) + 15.5D)));
			return dx * dx + dz * dz;
		}
		boolean needsOriginals(int cx, int cz) {
			int ring = Math.max(Math.abs(cx - (MathHelper.floor_double(x) >> 4)), Math.abs(cz - (MathHelper.floor_double(z) >> 4)));
			return !complete && ring + 6 >= (int)Math.ceil((Math.sqrt(chunkIndex + 1D) - 1D) / 2D);
		}
		void advance(long deadline) {
			if(!ready(ExplosionWorkScheduler.currentTick())) return;
			long start = System.nanoTime();
			try { select(deadline); } finally { activeNanos += System.nanoTime() - start; traversal = shield.traversal; reused = shield.reused; }
		}
		void select(long deadline) {
			long tick = ExplosionWorkScheduler.currentTick();
			int extent = (int)Math.ceil(maximum / 16D) + 1;
			int minimum = Math.max(0, MathHelper.floor_double(y - ExplosionNukeGeneric.getBlastHeightBelow(maximum)));
			int maximumY = Math.min(255, MathHelper.floor_double(y + ExplosionNukeGeneric.getBlastHeightAbove(maximum)));
			// The shared scheduler deadline is the governor, including discovery and
			// shielding. Every cursor is resumable; there is no per-tick operation cap.
			while(selectionCapacity() && ExplosionWorkScheduler.before(deadline)) {
				if(candidate) { if(!selectCandidate(deadline)) return; continue; }
				if(chunkIndex >= (2L * extent + 1) * (2L * extent + 1)) {
					complete = true; cancel(); beginCleanup(); return;
				}
				coordinates();
				if(!chunkStarted) {
					// Reject the entire chunk before loading it or visiting any columns.
					double near = minimumDistanceSquared(chunkX, chunkZ);
					if(near > maximum * maximum || chunkX < -1875000 || chunkX >= 1875000 || chunkZ < -1875000 || chunkZ >= 1875000) { skippedChunks++; nextChunk(); continue; }
					if(near > reached * reached) { frontWait = tick; return; }
				}
				net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.chunk(access, chunkX, chunkZ, deadline);
				if(live == null) return;
				if(!chunkStarted) {
					// Top-filled storage includes transparent cover which opaque heightmaps
					// can omit. Retained originals may extend above today's excavated top.
					sectionY = Math.min(maximumY >> 4, Math.max(live.getTopFilledSegment() >> 4, originals.highestSection(chunkX, chunkZ)));
					skippedSections += Math.max(0, (maximumY >> 4) - sectionY);
					chunkStarted = true;
				}
				if(sectionY < minimum >> 4 || blockedColumns.cardinality() == 256) { nextChunk(); continue; }
				if(section == null) {
					net.minecraft.world.chunk.storage.ExtendedBlockStorage storage = live.getBlockStorageArray()[sectionY];
					if(!originals.hasSection(chunkX, sectionY, chunkZ) && (storage == null || storage.getClass() == net.minecraft.world.chunk.storage.ExtendedBlockStorage.class && storage.isEmpty())) { skippedSections++; sectionY--; entry = 0; continue; }
					section = ExplosionTerrainAccess.pressureSection(originals, chunkX, sectionY, chunkZ);
					if(section == null) { access.defer(); return; }
					inspectedSections++;
				}
				if(!section.refresh(access, blockedColumns, minimum, maximumY, legacyColumn, legacyY, entry, deadline)) return;
				int next = section.interesting.nextSetBit(entry);
				if(next < 0) { closeSection(); sectionY--; entry = 0; continue; }
				entry = next;
				int column = entry >> 4, yy = (sectionY << 4) + 15 - (entry & 15);
				if(blockedColumns.get(column) || column < legacyColumn || column == legacyColumn && legacyY >= 0 && yy > legacyY) { entry = (column + 1) << 4; continue; }
				int xx = (chunkX << 4) + (column & 15), zz = (chunkZ << 4) + (column >> 4);
				double distance = Math.sqrt((xx + .5D - x) * (xx + .5D - x) + (zz + .5D - z) * (zz + .5D - z));
				if(yy < minimum || yy > maximumY || distance > maximum) { entry++; continue; }
				if(distance > reached) { frontWait = tick; return; }
				inspected++;
				if(!section.targets.get(entry)) { blockedColumns.set(column); entry = (column + 1) << 4; continue; }
				if(Math.abs(yy + .5D - y) > (yy >= y ? ExplosionNukeGeneric.getBlastHeightAbove(maximum) : ExplosionNukeGeneric.getBlastHeightBelow(maximum))) { entry++; continue; }
				if(Math.abs(yy + .5D - y) > (yy >= y ? ExplosionNukeGeneric.getBlastHeightAbove(reached) : ExplosionNukeGeneric.getBlastHeightBelow(reached))) { frontWait = tick; return; }
				Block block = live.getBlock(xx & 15, yy, zz & 15);
				float resistance = NuclearBlockBehavior.cost(world, block, xx, yy, zz, x, y, z);
				energy = Math.min(4096D, ExplosionNukeGeneric.getOverpressurePsi(distance / fivePsi) * factor);
				if(!NuclearBlockBehavior.pressureTarget(block) || Float.isInfinite(resistance) || energy <= resistance) { entry++; continue; }
				targetX = xx; targetY = yy; targetZ = zz; candidate = true;
			}
		}
		private boolean selectCandidate(long deadline) {
			// Reuse one bounded thickness/overburden estimate across nearby targets.
			// Top-down section discovery independently prevents deep caves becoming
			// exposed just because ordinary stone no longer uses resistance rays.
			if(ExplosionTerrainAccess.chunk(access, targetX >> 4, targetZ >> 4, deadline) == null) return false;
			Block target = world.getBlock(targetX, targetY, targetZ);
			float threshold = NuclearBlockBehavior.cost(world, target, targetX, targetY, targetZ, x, y, z);
			if(energy <= threshold || !NuclearBlockBehavior.pressureTarget(target)) { finishCandidate(); return true; }
			if(!shield.prepare(targetX, targetY, targetZ, energy - threshold, deadline)) return false;
			if(NuclearBlockBehavior.pressureTarget(target) && energy > threshold + shield.cost) { record(targetX, targetY, targetZ, true); selected++; }
			else shielded++;
			finishCandidate(); return true;
		}
		private void finishCandidate() { candidate = false; entry++; }
		private void nextChunk() {
			closeSection(); chunkIndex++; chunkStarted = false; sectionY = -1; entry = 0;
			blockedColumns.clear(); legacyColumn = 0; legacyY = -1;
		}
		NBTTagCompound write() {
			NBTTagCompound t = new NBTTagCompound();
			t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("maximum", maximum); t.setDouble("fivePsi", fivePsi); t.setDouble("factor", factor); t.setDouble("reached", reached);
			t.setInteger("selectorVersion", 1); t.setLong("chunk", chunkIndex); t.setBoolean("chunkStarted", chunkStarted); t.setInteger("sectionY", sectionY); t.setInteger("entry", entry); t.setByteArray("blocked", blockedColumns.toByteArray());
			t.setInteger("legacyColumn", legacyColumn); t.setInteger("legacyY", legacyY); t.setBoolean("complete", complete);
			t.setLong("inspected", inspected); t.setLong("selected", selected); t.setLong("shielded", shielded);
			t.setLong("skippedSections", skippedSections); t.setLong("inspectedSections", inspectedSections); t.setLong("skippedChunks", skippedChunks);
			t.setLong("traversal", traversal); t.setLong("reused", reused); t.setLong("activeNanos", activeNanos); return t;
		}
	}
	private void record(int x, int y, int z, boolean tip) {
		if(y < 0 || y >= 256) return;
		ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4));
		SectionMask section = chunk.sections[y >> 4];
		if(section == null) chunk.sections[y >> 4] = section = new SectionMask();
		int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
		if(!section.destruction.get(bit)) {
			section.destruction.set(bit); selectedBlocks++;
			net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.loaded(world, x >> 4, z >> 4);
			if(live != null) {
				Block source = ExplosionTerrainAccess.pressureSource(originals, x, y, z, live.getBlock(x & 15, y, z & 15));
				if(source == Blocks.grass || !source.isAir(world, x, y, z) && !source.getMaterial().isLiquid() && y == live.getHeightValue(x & 15, z & 15) - 1) { section.surface.set(bit); surfaceSelected++; }
			}
		}
		boolean added = !section.completed.get(bit) && !section.pending.get(bit);
		if(added) { section.pending.set(bit); chunk.pending++; pendingBlocks++; }
		if(tip) {
			if(section.tips == null) section.tips = new BitSet();
			if(!section.tips.get(bit) && section.completed.get(bit)) { addEffect(chunk, section, bit); added = true; }
			section.tips.set(bit);
		}
		if(added) wake(chunk);
	}
	/** Compatibility entry points now enumerate geometry, regardless of weapon size. */
	public void collectTip(int count) { collectTip(count, Long.MAX_VALUE, ExplosionWorkScheduler.currentTick()); }
	public void collectTip(int count, long deadline, long tick) { if(restore(deadline) && craterCapacity()) crater.advance(deadline); }
	public void cancelWorkers() { terrain.cancel(); removalTerrain.cancel(); crater.shield.cancel(); if(pressure != null) pressure.cancel(); }
	public void dispose() { cancelWorkers(); active.remove(this); remnants.close(); originals.close(); chunks = new LongHashMap(); firstChunk = lastChunk = cleanupCursor = null; ordering.clear(); }
	public static void unloadWorkers(World world) {
		for(ExplosionNukeRayBatched job : active.toArray(new ExplosionNukeRayBatched[0])) if(job.world == world) { job.cancelWorkers(); active.remove(job); }
	}
	/** Compatibility hook: synchronous phases hold no worker-owned chunk inputs. */
	public static void unloadChunkWorkers(World world, int x, int z) {
		for(ExplosionNukeRayBatched job : active) if(job.world == world) job.remnants.unloaded(x, z);
	}
	public static void shutdownWorkers() {
		// FMLServerStopping precedes the final world save. Keep committed masks
		// and retained cover serializable; only transient calculation is canceled.
		for(ExplosionNukeRayBatched job : active.toArray(new ExplosionNukeRayBatched[0])) job.cancelWorkers();
	}
	static void clearJobs() { for(ExplosionNukeRayBatched job : active.toArray(new ExplosionNukeRayBatched[0])) job.dispose(); }
	public static void startWorkers() { }

	public void processChunk() { processChunk(Long.MAX_VALUE); }
	public void processChunk(long deadline) {
		long start = System.nanoTime();
		try { applyMasks(deadline); }
		finally { removalServerNanos += System.nanoTime() - start; }
	}
	private void applyMasks(long deadline) {
		if(!restore(deadline) || !ExplosionWorkScheduler.before(deadline)) return;
		long tick = ExplosionWorkScheduler.currentTick();
		if(excavationTick != tick) { excavationTick = tick; excavationAttempts = 0; }
		// Completed masks retire independently of still-active effect jobs.
		while(cleanupCursor != null && ExplosionWorkScheduler.before(deadline)) {
			ChunkMask done = cleanupCursor; cleanupCursor = done.next;
			if(done.pending + done.effects == 0) { releaseOriginals(done); removeChunk(done); }
		}
		if(firstChunk == null) { if(selectorsComplete()) { originals.clear(); excavationWait = "complete"; } return; }
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
					boolean change = !block.getMaterial().isLiquid() && !world.isAirBlock(x, y, z)
							&& !Float.isInfinite(NuclearBlockBehavior.cost(world, block, x, y, z, posX, posY, posZ));
					if(!ExplosionWorkScheduler.before(deadline)) return;
					if(change) {
						boolean terminalReady = tip;
						if(getClass() == ExplosionNukeRayBatched.class && ExplosionTerrainAccess.canRemoveSection(world, live, sectionAt(removalSection))
								&& safeProgressiveBlock(block) && (!tip || !selectorsComplete())) {
							if(applySectionColumn(chunk, section, live, bit, deadline)) { removalBit = bit + 1; continue; }
							if(!ExplosionWorkScheduler.before(deadline)) return;
						}
						if(!clearAttachments(x, y, z, deadline)) { block(chunk, tick); break; }
						block = world.getBlock(x, y, z);
						if(block.getMaterial().isLiquid() || world.isAirBlock(x, y, z) || Float.isInfinite(NuclearBlockBehavior.cost(world, block, x, y, z, posX, posY, posZ))) {
							section.pending.clear(bit); section.completed.set(bit); section.notified.set(bit); chunk.pending--; pendingBlocks--; removalBit = bit + 1; continue;
						}
						if(!selectorsComplete()) {
							// Retry this cursor when bounded history memory becomes available.
							if(!remember(x, y, z, block)) { excavationWait = "original terrain memory ceiling"; block(chunk, tick); break; }
						}
						if(localLeafCallback(block) && !NuclearDropSuppression.prepareLeaves(world, x, y, z, block instanceof net.minecraft.block.BlockLog ? 4 : 1)) { excavationWait = "leaf attribution ceiling"; block(chunk, tick); break; }
						if(terminalReady && !ExplosionTerrainAccess.neighbors(removalTerrain, x, z, deadline)) { block(chunk, tick); break; }
						if(!ExplosionWorkScheduler.before(deadline)) return;
						try(NuclearDropSuppression.Scope scope = NuclearDropSuppression.enter(world)) {
							NuclearDropSuppression.clearInventory(world, x, y, z);
							if(terminalReady) handleTip(x, y, z);
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
						destroyedBlocks++; normalRemovals++; removedBands[band(y)]++; surfaceRemoved(section, bit);
						materialReleased |= NuclearBlockBehavior.ordinaryTerrain(block) || NuclearBlockBehavior.constructionBarrier(block);
						if(terminalReady) section.notified.set(bit);
						if(firstRemovalAt == 0) { firstRemovalAt = System.nanoTime(); logStatus("first successful block removal"); }
						removalFailureReported = false;
						if(tip && !terminalReady) addEffect(chunk, section, bit);
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
					if(chunk.pending + chunk.effects == 0) {
						dequeue(chunk);
						if(chunk.pending + chunk.effects == 0) { releaseOriginals(chunk); if(cleanupCursor == chunk) cleanupCursor = chunk.next; removeChunk(chunk); }
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
	}
	private boolean remember(int x, int y, int z, Block block) {
		long start = System.nanoTime();
		try { boolean remembered = originals.remember(x, y, z, block); if(!remembered) memoryWaits++; return remembered; }
		finally { preservationNanos += System.nanoTime() - start; }
	}
	private void surfaceRemoved(SectionMask section, int bit) {
		if(!section.surface.get(bit)) return;
		surfaceRemoved++;
		if(firstSurfaceRemovalAt == 0) firstSurfaceRemovalAt = System.nanoTime();
	}
	private boolean applySectionColumn(ChunkMask chunk, SectionMask section, net.minecraft.world.chunk.Chunk live, int first, long deadline) {
		int count = 0;
		columnMaterial.clear();
		for(int bit = first; bit < 4096 && ExplosionWorkScheduler.before(deadline); bit += 256) {
			if(!section.pending.get(bit) || selectorsComplete() && section.tips != null && section.tips.get(bit)) continue;
			int x = (live.xPosition << 4) + (bit & 15), y = (sectionAt(removalSection) << 4) + (bit >> 8), z = (live.zPosition << 4) + (bit >> 4 & 15);
			Block block = live.getBlock(bit & 15, y, bit >> 4 & 15);
			// Positive opacity makes the highest native removal schedule the same
			// skylight gap work as every lower removal. Zero-opacity cells fall back.
			if(!safeProgressiveBlock(block) || localLeafCallback(block) || block.getClass() == com.hbm.blocks.generic.BlockOutgas.class || block.getMaterial().isLiquid() || world.isAirBlock(x, y, z)
					|| Float.isInfinite(NuclearBlockBehavior.cost(world, block, x, y, z, posX, posY, posZ))
					|| block.hasTileEntity(live.getBlockMetadata(bit & 15, y, bit >> 4 & 15)) || world.getTileEntity(x, y, z) != null
					|| block.getLightOpacity(world, x, y, z) <= 0) continue;
			if(!clearAttachments(x, y, z, deadline)) break;
			if(!selectorsComplete() && !remember(x, y, z, block)) break;
			columnSources[count] = block; columnMetadata[count] = live.getBlockMetadata(bit & 15, y, bit >> 4 & 15); columnBits[count++] = bit;
			if(NuclearBlockBehavior.ordinaryTerrain(block) || NuclearBlockBehavior.constructionBarrier(block)) columnMaterial.set(bit);
		}
		if(count < 2 || columnBits[0] != first || !ExplosionWorkScheduler.before(deadline)) return false;
		// Attachment notifications can call arbitrary mod code. Recheck every
		// certified cell after gathering, before any counted storage writes.
		for(int i = 0; i < count; i++) {
			int bit = columnBits[i], y = (sectionAt(removalSection) << 4) + (bit >> 8);
			int x = (live.xPosition << 4) + (bit & 15), z = (live.zPosition << 4) + (bit >> 4 & 15);
			Block current = live.getBlock(bit & 15, y, bit >> 4 & 15);
			if(!ExplosionWorkScheduler.before(deadline) || current != columnSources[i] || live.getBlockMetadata(bit & 15, y, bit >> 4 & 15) != columnMetadata[i]
					|| current.hasTileEntity(columnMetadata[i]) || world.getTileEntity(x, y, z) != null || current.getLightOpacity(world, x, y, z) <= 0
					|| Float.isInfinite(NuclearBlockBehavior.cost(world, current, x, y, z, posX, posY, posZ))) return false;
		}
		columnChanged.clear();
		// At most sixteen cells form one indivisible column transaction. Finish
		// its lighting/sync even if a native callback overruns; start no next batch.
		ExplosionTerrainAccess.removeSectionColumn(world, live, sectionAt(removalSection), columnBits, count, columnChanged);
		materialReleased |= columnChanged.intersects(columnMaterial);
		for(int bit = columnChanged.nextSetBit(0); bit >= 0; bit = columnChanged.nextSetBit(bit + 1)) {
			section.pending.clear(bit); section.completed.set(bit); chunk.pending--; pendingBlocks--;
			destroyedBlocks++; fastRemovals++; removedBands[band((sectionAt(removalSection) << 4) + (bit >> 8))]++; surfaceRemoved(section, bit);
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
	/** External compatibility helper only; geometric terrain never calls it. */
	@Deprecated public static float masqueradeResistance(Block block) {
		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3F;
		return block.getExplosionResistance(null);
	}
	private static int[] spiral(long index) {
		int ring = (int)Math.ceil((Math.sqrt(index + 1D) - 1D) / 2D), dx = 0, dz = 0;
		if(ring > 0) {
			long d = (2L * ring + 1) * (2L * ring + 1) - 1 - index;
			int side = (int)(d / (2L * ring)), offset = (int)(d % (2L * ring));
			if(side == 0) { dx = ring - offset; dz = -ring; }
			else if(side == 1) { dx = -ring; dz = -ring + offset; }
			else if(side == 2) { dx = -ring + offset; dz = ring; }
			else { dx = ring; dz = ring - offset; }
		}
		return new int[] {dx, dz};
	}
	private static boolean finite(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }
	private final class CraterWork {
		final double radius, radiusSquared, depth, upper, centerY;
		final int extent;
		long chunkIndex;
		int column, scanY = -1, bottom;
		boolean columnStarted, complete;
		final Shielding shield = new Shielding(terrain, posX + .5D, posY + .5D, posZ + .5D);
		CraterWork(double radius, double depth, double upper, double centerY) {
			this.radius = radius; radiusSquared = radius * radius; this.depth = depth; this.upper = upper; this.centerY = centerY;
			extent = (int)Math.ceil(radius / 16D) + 1;
			complete = !finite(radius) || !finite(depth) || !finite(upper) || !finite(centerY) || radius <= 0D || depth <= 0D || centerY + upper < .5D || centerY - depth > 255.5D;
		}
		void advance(long deadline) {
			while(!complete && craterCapacity() && ExplosionWorkScheduler.before(deadline)) {
				if(chunkIndex >= (2L * extent + 1) * (2L * extent + 1)) {
					complete = isAusf3Complete = true; shield.cancel(); beginCleanup(); return;
				}
				int[] offset = spiral(chunkIndex);
				int cx = (posX >> 4) + offset[0], cz = (posZ >> 4) + offset[1];
				double nearX = Math.max(0D, Math.max((cx << 4) + .5D - (posX + .5D), posX + .5D - ((cx << 4) + 15.5D)));
				double nearZ = Math.max(0D, Math.max((cz << 4) + .5D - (posZ + .5D), posZ + .5D - ((cz << 4) + 15.5D)));
				if(cx < -1875000 || cx >= 1875000 || cz < -1875000 || cz >= 1875000 || nearX * nearX + nearZ * nearZ > radiusSquared) { nextChunk(); continue; }
				net.minecraft.world.chunk.Chunk live = ExplosionTerrainAccess.chunk(terrain, cx, cz, deadline);
				if(live == null) return;
				while(column < 256 && craterCapacity() && ExplosionWorkScheduler.before(deadline)) {
					int x = (cx << 4) + (column & 15), z = (cz << 4) + (column >> 4);
					double dx = x - (double)posX, dz = z - (double)posZ, radial = (dx * dx + dz * dz) / radiusSquared;
					if(radial > 1D) { nextColumn(); continue; }
					if(!columnStarted) {
						double height = Math.sqrt(1D - radial);
						bottom = Math.min(256, Math.max(0, (int)Math.ceil(centerY - depth * height - .5D)));
						scanY = Math.min(255, Math.min(live.getTopFilledSegment() + 15, (int)Math.floor(centerY + upper * height - .5D)));
						columnStarted = true;
					}
					while(scanY >= bottom && craterCapacity() && ExplosionWorkScheduler.before(deadline)) {
						int y = scanY, sy = y >> 4;
						net.minecraft.world.chunk.storage.ExtendedBlockStorage storage = live.getBlockStorageArray()[sy];
						if(storage == null || storage.getClass() == net.minecraft.world.chunk.storage.ExtendedBlockStorage.class && storage.isEmpty()) { scanY = (sy << 4) - 1; continue; }
						remnants.affect(cx, sy, cz);
						Block b = live.getBlock(x & 15, y, z & 15);
						if(b == Blocks.air || b.getMaterial().isLiquid() || b.isAir(world, x, y, z)) { scanY--; continue; }
						float cost = NuclearBlockBehavior.cost(world, b, x, y, z, posX, posY, posZ);
						if(!Float.isInfinite(cost)) {
							// The volume is authoritative for ordinary terrain: no crater rays,
							// resistance attenuation, random omissions or endpoint reconstruction.
							boolean ordinary = NuclearBlockBehavior.ordinaryExcavation(world, b, x, y, z);
							double vertical = (y + .5D - centerY) / (y + .5D < centerY ? depth : Math.max(.5D, upper));
							double zone = radial + vertical * vertical;
							boolean destroy = ordinary || zone <= .36D;
							if(!destroy) {
								// One local thickness estimate per four-block cell, only for barriers
								// whose survival can change at the crater edge. The core ignores it.
								double available = 24D + 160D * Math.max(0D, 1D - zone) - cost;
								if(available > 0D) {
									if(!shield.prepare(x, y, z, available, deadline)) return;
									destroy = available > shield.cost;
								}
							}
							if(destroy) record(x, y, z, false);
						}
						scanY--;
					}
					if(scanY >= bottom) return;
					nextColumn();
				}
				if(column < 256) return;
				nextChunk();
			}
		}
		void nextColumn() { column++; columnStarted = false; scanY = -1; }
		void nextChunk() { chunkIndex++; column = 0; columnStarted = false; scanY = -1; }
		boolean validColumnCursor() {
			if(!columnStarted) return true;
			if(column >= 256) return false;
			int[] offset = spiral(chunkIndex);
			double dx = (((posX >> 4) + offset[0]) * 16D + (column & 15)) - posX;
			double dz = (((posZ >> 4) + offset[1]) * 16D + (column >> 4)) - posZ;
			double radial = (dx * dx + dz * dz) / radiusSquared;
			if(!finite(radial) || radial > 1D) return false;
			double height = Math.sqrt(1D - radial);
			int expectedBottom = Math.min(256, Math.max(0, (int)Math.ceil(centerY - depth * height - .5D)));
			int maximum = Math.min(255, (int)Math.floor(centerY + upper * height - .5D));
			return bottom == expectedBottom && scanY >= bottom && scanY <= maximum;
		}
		NBTTagCompound write() {
			NBTTagCompound t = new NBTTagCompound();
			t.setDouble("radius", radius); t.setDouble("depth", depth); t.setDouble("upper", upper); t.setDouble("centerY", centerY);
			t.setLong("chunk", chunkIndex); t.setInteger("column", column); t.setInteger("y", scanY); t.setInteger("bottom", bottom);
			t.setBoolean("columnStarted", columnStarted); t.setBoolean("complete", complete); return t;
		}
	}
	/** Bounded local shielding approximation, not a crater selector. A four-by-one-by-four
	 * cell shares one approach through at most 64 blocks / 128 half-block samples.
	 * Ordinary solids accumulate overburden; structural costs retain context.
	 * Costs saturate at 4096: no decision here can penetrate that much cover.
	 * Remote barriers beyond the local window are deliberately not surveyed.
	 * The 256 direct-mapped entries expire on world changes and each server tick.
	 * Inputs are immutable section summaries; all captures and decisions run on
	 * the owning server thread. No worker receives a World or Chunk reference. */
	private final class Shielding {
		final Access access;
		final double sx, sy, sz;
		final ShieldCell[] cells = new ShieldCell[256];
		int gx, gy, gz, step, steps, lastX, lastY, lastZ;
		double dx, dy, dz, span, sum, cost;
		long revision = Long.MIN_VALUE, tick = Long.MIN_VALUE;
		long traversal, reused;
		boolean running;
		Shielding(Access access, double sx, double sy, double sz) { this.access = access; this.sx = sx; this.sy = sy; this.sz = sz; }
		void cancel() { java.util.Arrays.fill(cells, null); running = false; }
		boolean prepare(int x, int y, int z, double limit, long deadline) {
			long now = ExplosionTerrainAccess.revision(world), currentTick = ExplosionWorkScheduler.currentTick();
			if(revision != now || tick != currentTick) {
				// Keep an unfinished traversal across ticks; only actual terrain changes
				// invalidate its partial sum. Disposable finished entries expire each tick.
				java.util.Arrays.fill(cells, null); if(revision != now) running = false;
				revision = now; tick = currentTick;
			}
			int cx = x >> 2, cy = y, cz = z >> 2, slot = (cx * 73428767 ^ cy * 912931 ^ cz) & 255;
			ShieldCell saved = cells[slot];
			boolean matching = saved != null && saved.x == cx && saved.y == cy && saved.z == cz;
			if(matching && (saved.exact || saved.cost >= limit)) { reused++; cost = saved.cost; return true; }
			if(!running || gx != cx || gy != cy || gz != cz) {
				gx = cx; gy = cy; gz = cz; dx = sx - (cx * 4D + 2D); dy = sy - (cy + .5D); dz = sz - (cz * 4D + 2D);
				span = Math.sqrt(dx * dx + dy * dy + dz * dz);
				steps = Math.min(128, (int)Math.ceil(span * 2D)); step = 1; sum = 0D; lastX = Integer.MIN_VALUE; running = true;
				if(matching) { step = saved.step; sum = saved.cost; lastX = saved.lastX; lastY = saved.lastY; lastZ = saved.lastZ; }
			}
			while(step < steps && sum < limit && ExplosionWorkScheduler.before(deadline)) {
				double f = step * .5D / Math.max(.5D, span);
				int xx = MathHelper.floor_double(gx * 4D + 2D + dx * f), yy = MathHelper.floor_double(gy + .5D + dy * f), zz = MathHelper.floor_double(gz * 4D + 2D + dz * f);
				if(yy < 0 || yy >= 256 || xx < -30000000 || xx >= 30000000 || zz < -30000000 || zz >= 30000000 || xx == lastX && yy == lastY && zz == lastZ) { step++; continue; }
				Snapshot sample = ExplosionTerrainAccess.snapshot(access, xx >> 4, yy >> 4, zz >> 4, deadline);
				if(sample == null) return false;
				traversal++;
				int bit = (yy & 15) << 8 | (zz & 15) << 4 | (xx & 15);
				if(!sample.air(bit)) {
					float resistance = sample.resistance(bit);
					if(resistance < 0F) {
						if(ExplosionTerrainAccess.chunk(access, xx >> 4, zz >> 4, deadline) == null) return false;
						Block b = world.getBlock(xx, yy, zz);
						resistance = NuclearBlockBehavior.cost(world, b, xx, yy, zz, sx, sy, sz);
					}
					sum += resistance;
				}
				lastX = xx; lastY = yy; lastZ = zz; step++;
			}
			if(step < steps && sum < limit) return false;
			cost = Math.min(4096D, sum); cells[slot] = new ShieldCell(cx, cy, cz, cost, step, step >= steps, lastX, lastY, lastZ); running = false; return true;
		}
	}
	private static final class ShieldCell {
		final int x, y, z; final double cost;
		final int step, lastX, lastY, lastZ; final boolean exact;
		ShieldCell(int x, int y, int z, double cost, int step, boolean exact, int lastX, int lastY, int lastZ) {
			this.x = x; this.y = y; this.z = z; this.cost = cost; this.step = step; this.exact = exact;
			this.lastX = lastX; this.lastY = lastY; this.lastZ = lastZ;
		}
	}
	public void readFromNBT(NBTTagCompound tag) {
		cancelWorkers(); originals.clear(); chunks = new LongHashMap(); firstChunk = lastChunk = cleanupCursor = null; ordering.clear();
		pendingBlocks = pendingEffects = 0L; queuedChunks = maskChunks = nextDistance = 0;
		removalActive = false; excavationTick = Long.MIN_VALUE;
		// Additive checkpoint: pre-cleanup saves keep their excavation cursors and
		// never rescan completed terrain to invent missing affected-section data.
		if(tag.hasKey("remnants")) remnants.read(tag.getCompoundTag("remnants")); else remnants.close();
		terrain.read(tag); pressure = tag.hasKey("pressureWork") ? new PressureWork(tag.getCompoundTag("pressureWork")) : null;
		savedDiagnostics = tag.hasKey("selectedBlocks"); selectedBlocks = tag.getLong("selectedBlocks"); destroyedBlocks = tag.getLong("destroyedBlocks");
		materialReleased = tag.hasKey("materialReleased") ? tag.getBoolean("materialReleased") : destroyedBlocks > 0;
		fastRemovals = tag.getLong("fastRemovals"); normalRemovals = tag.getLong("normalRemovals");
		startedAt = System.nanoTime() - Math.max(0L, tag.getLong("elapsedNanos"));
		if(tag.getInteger("workVersion") == WORK_VERSION && tag.hasKey("crater")) {
			NBTTagCompound t = tag.getCompoundTag("crater");
			double radius = t.getDouble("radius"), depth = t.getDouble("depth"), upper = t.getDouble("upper"), center = t.getDouble("centerY");
			// Reject malformed cursors/dimensions instead of restarting a large crater.
			boolean valid = t.hasKey("chunk") && t.hasKey("column") && t.hasKey("y") && t.hasKey("bottom") && t.hasKey("columnStarted") && t.hasKey("complete")
					&& finite(radius) && finite(depth) && finite(upper) && finite(center) && radius >= 0D && radius <= Math.max(1, length)
					&& depth >= 0D && depth <= Math.max(1, length) && upper >= 0D && upper <= Math.max(1, length) && Math.abs(center) <= 30000000D;
			if(valid) {
				CraterWork loaded = new CraterWork(radius, depth, upper, center);
				loaded.chunkIndex = t.getLong("chunk"); loaded.column = t.getInteger("column"); loaded.scanY = t.getInteger("y"); loaded.bottom = t.getInteger("bottom");
				loaded.columnStarted = t.getBoolean("columnStarted");
				long limit = (2L * loaded.extent + 1) * (2L * loaded.extent + 1);
				valid = loaded.chunkIndex >= 0 && loaded.chunkIndex <= limit && loaded.column >= 0 && loaded.column <= 256 && loaded.scanY >= -1 && loaded.scanY <= 255 && loaded.bottom >= 0 && loaded.bottom <= 256 && loaded.validColumnCursor();
				if(valid) { loaded.complete |= t.getBoolean("complete") || loaded.chunkIndex == limit; crater = loaded; }
			}
			if(!valid) { crater.complete = true; remnants.close(); logStatus("invalid crater checkpoint; retaining committed terrain work only"); }
		} else {
			// Ray ordinals/endpoints have no geometric cursor equivalent. Preserve
			// materialized masks/remaining positions, retire unfinished rays, and do
			// not restart completed terrain or reconstruct rays in old saves.
			crater.complete = true;
			remnants.close();
			logStatus("legacy checkpoint: applying committed work; unfinished rays retired");
		}
		isAusf3Complete = crater.complete;
		restoring = tag; restorePhase = restoreIndex = restoreSection = 0;
	}
	private boolean restore(long deadline) {
		if(restoring == null) return true;
		if(!remnants.restore(deadline)) return false;
		if(!originals.restore(restoring, deadline)) { excavationWait = "restoring shielding history"; return false; }
		while(restoring != null && ExplosionWorkScheduler.before(deadline)) {
			if(restorePhase == 0) {
				NBTTagList savedChunks = restoring.getTagList("chunks", 10);
				if(restoreIndex >= savedChunks.tagCount()) { restorePhase++; restoreIndex = 0; continue; }
				NBTTagCompound item = savedChunks.getCompoundTagAt(restoreIndex);
				NBTTagList sections = item.getTagList("sections", 10);
				if(restoreSection >= sections.tagCount()) { restoreIndex++; restoreSection = 0; continue; }
				NBTTagCompound data = sections.getCompoundTagAt(restoreSection++);
				int sy = data.getInteger("y"), cx = item.getInteger("x"), cz = item.getInteger("z");
				if(sy < 0 || sy >= 16 || cx < -1875000 || cx >= 1875000 || cz < -1875000 || cz >= 1875000) continue;
				SectionMask section = new SectionMask();
				section.destruction.or(bits(data, "destruction"));
				if(section.destruction.isEmpty()) continue;
				ChunkMask chunk = chunkMask(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
				if(chunk.sections[sy] != null) continue;
				section.surface.or(bits(data, "surface")); section.surface.and(section.destruction);
				section.pending.or(restoring.getBoolean("progressive") ? bits(data, "pending") : section.destruction);
				section.pending.and(section.destruction);
				section.completed.or(bits(data, "completed")); section.completed.and(section.destruction); section.pending.andNot(section.completed);
				section.notified.or(bits(data, "notified")); section.notified.and(section.completed);
				section.tips = bits(data, "tips"); section.tips.and(section.destruction);
				section.effects.or(bits(data, "effects")); section.effects.or(section.tips); section.effects.and(section.completed); section.effects.andNot(section.notified);
				chunk.sections[sy] = section;
				// Resume pending bits directly. The small application cursor is derived
				// from them rather than trusting an old or malformed section ordinal.
				chunk.pending += section.pending.cardinality(); pendingBlocks += section.pending.cardinality();
				chunk.effects += section.effects.cardinality(); pendingEffects += section.effects.cardinality();
				if(!savedDiagnostics) selectedBlocks += section.destruction.cardinality();
				enqueue(chunk);
			} else if(restorePhase <= 2) {
				// Pre-mask saves can retain explicit remaining positions. Endpoint-only
				// chunks are intentionally retired rather than converted back to rays.
				NBTTagCompound source = restorePhase == 1 ? restoring : restoring.getCompoundTag("legacy");
				NBTTagList remaining = source.getTagList("remaining", 10);
				if(restoreIndex >= remaining.tagCount()) { restorePhase++; restoreIndex = 0; continue; }
				NBTTagCompound p = remaining.getCompoundTagAt(restoreIndex++);
				int x = p.getInteger("x"), y = p.getInteger("y"), z = p.getInteger("z");
				if(x >= -30000000 && x < 30000000 && z >= -30000000 && z < 30000000) record(x, y, z, false);
			} else {
				restoring = null; beginCleanup();
			}
		}
		return restoring == null;
	}
	private static BitSet bits(NBTTagCompound t, String key) {
		byte[] data = t.getByteArray(key);
		return BitSet.valueOf(data.length <= 512 ? data : java.util.Arrays.copyOf(data, 512));
	}
	public void writeToNBT(NBTTagCompound tag) {
		if(restoring != null) {
			// No mutations run until restore finishes, so forwarding the untouched
			// terrain checkpoint is lossless. Fallout can finish independently.
			for(Object key : restoring.func_150296_c()) tag.setTag((String)key, restoring.getTag((String)key).copy());
			tag.setTag("remnants", remnants.write());
			return;
		}
		tag.setInteger("workVersion", WORK_VERSION); tag.setTag("crater", crater.write()); tag.setBoolean("complete", crater.complete);
		tag.setBoolean("progressive", true); tag.setBoolean("balancedSections", true);
		tag.setLong("selectedBlocks", selectedBlocks); tag.setLong("destroyedBlocks", destroyedBlocks);
		tag.setBoolean("materialReleased", materialReleased);
		tag.setTag("remnants", remnants.write());
		tag.setLong("fastRemovals", fastRemovals); tag.setLong("normalRemovals", normalRemovals); tag.setLong("elapsedNanos", System.nanoTime() - startedAt);
		terrain.write(tag); originals.write(tag);
		if(pressure != null) tag.setTag("pressureWork", pressure.write());
		NBTTagList savedChunks = new NBTTagList();
		for(ChunkMask chunk = firstChunk; chunk != null; chunk = chunk.next) {
			NBTTagCompound item = new NBTTagCompound(); item.setInteger("x", (int)chunk.key); item.setInteger("z", (int)(chunk.key >> 32));
			NBTTagList sections = new NBTTagList();
			for(int sy = 0; sy < 16; sy++) {
				SectionMask section = chunk.sections[sy]; if(section == null || section.destruction.isEmpty()) continue;
				NBTTagCompound data = new NBTTagCompound(); data.setInteger("y", sy);
				data.setByteArray("destruction", section.destruction.toByteArray()); data.setByteArray("surface", section.surface.toByteArray());
				data.setByteArray("pending", section.pending.toByteArray());
				data.setByteArray("effects", section.effects.toByteArray());
				data.setByteArray("completed", section.completed.toByteArray()); data.setByteArray("notified", section.notified.toByteArray());
				if(section.tips != null) data.setByteArray("tips", section.tips.toByteArray()); sections.appendTag(data);
			}
			item.setTag("sections", sections); savedChunks.appendTag(item);
		}
		tag.setTag("chunks", savedChunks);
	}
}
