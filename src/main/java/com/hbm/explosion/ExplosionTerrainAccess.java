package com.hbm.explosion;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.config.BombConfig;
import com.hbm.main.MainRegistry;
import com.hbm.world.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraft.world.IWorldAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** Server-thread request/capture ownership. No terrain tickets or footprint pinning. */
public final class ExplosionTerrainAccess {
	public static final int MAX_CHUNK_REQUESTS = 128;
	public static final int MAX_SNAPSHOTS = 16384;
	public static final int MAX_CAPTURE_REQUESTS = 128;
	// Primitive arrays plus a conservative allowance for array/object headers.
	private static final long SNAPSHOT_BYTES = 8192L;
	private static final long AIR_SNAPSHOT_BYTES = 2048L, AIR_COLUMN_BYTES = 256L;
	private static final long CAPTURE_SCRATCH_BYTES = 8192L;
	private static final long EXPANDED_BYTES = 4096L * 5L + 64L;
	private static final LinkedHashMap<Key, Request> requests = new LinkedHashMap<Key, Request>();
	private static final LinkedHashMap<Key, Capture> snapshots = new LinkedHashMap<Key, Capture>(16, .75F, true);
	// Worker-visible directory contains only completed immutable data and
	// primitive identity keys. It never exposes chunks, worlds or capture cursors.
	private static final java.util.concurrent.ConcurrentHashMap<WorkerKey, Snapshot> published = new java.util.concurrent.ConcurrentHashMap<WorkerKey, Snapshot>();
	// A section capture already sees which sibling storage sections are null.
	// Retain that exact fact without reloading the same chunk for every altitude.
	private static final LinkedHashMap<Key, Integer> airColumns = new LinkedHashMap<Key, Integer>(16, .75F, true);
	private static final java.util.ArrayDeque<Validation> validations = new java.util.ArrayDeque<Validation>();
	private static final LinkedHashMap<Key, Chunk> terrainLoads = new LinkedHashMap<Key, Chunk>(16, .75F, true);
	private static final int MAX_RECENT_TERRAIN_LOADS = 128;
	private static final java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
	private static long loadTick = Long.MIN_VALUE;
	private static int loads;
	private static int captures;
	private static long evictions, invalidations, unchangedValidations, airSectionReuses, uniformAirCaptures, chunkLoadsStarted, validationNanos, loadingNanos;
	public static String diagnostics() { return "globalSnapshotEvictions=" + evictions + " globalSnapshotInvalidations=" + invalidations + " globalUnchangedValidations=" + unchangedValidations + " globalAirSectionReuses=" + airSectionReuses + " globalUniformAirCaptures=" + uniformAirCaptures + " globalChunkLoads=" + chunkLoadsStarted + " pendingSnapshotValidations=" + validations.size() + " globalValidationActiveElapsedMs=" + validationNanos / 1000000L + " globalChunkLoadActiveElapsedMs=" + loadingNanos / 1000000L + " snapshotBytes=" + bytes.get(); }
	static boolean hasValidationWork() { return !validations.isEmpty(); }
	private static final Map<World, Observer> observers = new java.util.IdentityHashMap<World, Observer>();
	private static final Map<Long, History> histories = new LinkedHashMap<Long, History>();
	private static long retainedBytes;

	/** Sparse originals belong to a calculation view, not the evictable cache.
	 * Only certified callback-free removals may enter this journal. Each packed
	 * value preserves the exact float, three classification flags and source ID. */
	public static final class History {
		final long id = sequence.incrementAndGet();
		final World world;
		private final LinkedHashMap<Key, Originals> sections = new LinkedHashMap<Key, Originals>();
		private long allocated;
		public History(World world) { this.world = world; histories.put(id, this); }
		public long bytes() { return allocated; }
		private Originals section(int x, int y, int z) { return sections.get(new Key(world, x, y, z)); }
		boolean hasSection(int x, int y, int z) { return section(x, y, z) != null; }
		private long original(int x, int y, int z, int bit) {
			Originals section = section(x, y, z); return section == null ? -1L : section.get(bit);
		}
		public Block source(int x, int y, int z) {
			long value = original(x >> 4, y >> 4, z >> 4, (y & 15) << 8 | (z & 15) << 4 | (x & 15));
			return value == -1L ? Blocks.air : Block.getBlockById((int)(value >>> 35) & 65535);
		}
		public boolean remember(int x, int y, int z, Block block) {
			Key key = new Key(world, x >> 4, y >> 4, z >> 4);
			Originals section = sections.get(key);
			int bit = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
			boolean existing = section != null && section.get(bit) != -1L;
			int capacity = section == null ? 16 : section.values.length;
			long needed = section == null ? 256L + capacity * 8L : !existing && section.size == capacity ? capacity * 8L : 0L;
			// Leave at least half the shared ceiling for calculation/capture buffers.
			if(retainedBytes + needed > (long)BombConfig.explosionSnapshotBudgetMiB * 524288L || !reserve(needed, null)) return false;
			if(section == null) { section = new Originals(capacity); sections.put(key, section); }
			else if(!existing && section.size == capacity) section.values = java.util.Arrays.copyOf(section.values, capacity * 2);
			allocated += needed; retainedBytes += needed; bytes.addAndGet(needed);
			byte flags = (byte)((block != Blocks.air ? 1 : 0) | (block.getMaterial().isLiquid() ? 2 : 0) | (world.isAirBlock(x, y, z) ? 4 : 0));
			long value = (long)bit << 51 | (long)(Block.getIdFromBlock(block) & 65535) << 35 | (long)flags << 32 | (Float.floatToIntBits(ExplosionNukeRayBatched.masqueradeResistance(block)) & 0xffffffffL);
			section.put(bit, value); return true;
		}
		private void replaced(int x, int y, int z, int bit) {
			// A replacement ends the resistance override, but a late tip can still
			// need the removed source ID. The otherwise unused sign bit records this.
			Originals section = section(x, y, z); if(section != null) section.replace(bit);
		}
		public void clear() { sections.clear(); retainedBytes -= allocated; bytes.addAndGet(-allocated); allocated = 0; restored = 0; }
		public void releaseChunk(int x, int z) {
			for(int y = 0; y < 16; y++) {
				Originals data = sections.remove(new Key(world, x, y, z));
				if(data != null) { long freed = 256L + data.values.length * 8L; allocated -= freed; retainedBytes -= freed; bytes.addAndGet(-freed); }
			}
		}
		public void close() { clear(); histories.remove(id); retireView(id); }
		public void write(NBTTagCompound tag) {
			net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
			for(Map.Entry<Key, Originals> entry : sections.entrySet()) {
				NBTTagCompound item = new NBTTagCompound(); Key key = entry.getKey(); Originals data = entry.getValue();
				item.setInteger("x", (int)key.chunk); item.setInteger("y", key.section); item.setInteger("z", (int)(key.chunk >> 32));
				int[] values = new int[data.size * 2];
				for(int i = 0; i < data.size; i++) { values[i * 2] = (int)data.values[i]; values[i * 2 + 1] = (int)(data.values[i] >>> 32); }
				item.setIntArray("values", values); list.appendTag(item);
			}
			tag.setTag("originalTerrain", list);
		}
		/** Restore under the slice deadline. Failure to reserve keeps the NBT
		 * cursor intact and forbids both calculation and excavation. */
		public boolean restore(NBTTagCompound tag, long deadline) {
			net.minecraft.nbt.NBTTagList list = tag.getTagList("originalTerrain", 10);
			while(restored < list.tagCount() && ExplosionWorkScheduler.before(deadline)) {
				NBTTagCompound item = list.getCompoundTagAt(restored); int[] values = item.getIntArray("values");
				long needed = 256L + Math.max(1, values.length / 2) * 8L;
				// Saved dependencies must survive even if the configured ceiling was
				// reduced. Wait instead of silently using the excavated live terrain.
				if(!reserve(needed, null)) return false;
				Originals data = new Originals(values.length / 2);
				for(int i = 0; i + 1 < values.length; i += 2) data.values[data.size++] = (values[i] & 0xffffffffL) | (long)values[i + 1] << 32;
				sections.put(new Key(world, item.getInteger("x"), item.getInteger("y"), item.getInteger("z")), data);
				allocated += needed; retainedBytes += needed; bytes.addAndGet(needed); restored++;
			}
			return restored == list.tagCount();
		}
		private int restored;
	}
	private static final class Originals {
		long[] values; int size;
		Originals(int capacity) { values = new long[Math.max(1, capacity)]; }
		int index(int bit) {
			int low = 0, high = size;
			while(low < high) { int mid = (low + high) >>> 1; if((values[mid] >>> 51 & 4095) < bit) low = mid + 1; else high = mid; }
			return low;
		}
		long get(int bit) { int i = index(bit); return i < size && (values[i] >>> 51 & 4095) == bit ? values[i] : -1L; }
		void put(int bit, long value) { int i = index(bit); if(i < size && (values[i] >>> 51 & 4095) == bit) { values[i] = value; return; } System.arraycopy(values, i, values, i + 1, size - i); values[i] = value; size++; }
		void replace(int bit) { int i = index(bit); if(i < size && (values[i] >>> 51 & 4095) == bit) values[i] |= Long.MIN_VALUE; }
	}
	private static void retireView(long view) {
		Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
		while(iterator.hasNext()) { Map.Entry<Key, Capture> entry = iterator.next(); if(entry.getKey().view == view) { iterator.remove(); retire(entry.getValue().data); } }
	}

	/** One pending request per caller; the authoritative cursor remains with that caller. */
	public static final class Access {
		public final World world;
		public final long id = sequence.incrementAndGet();
		public final long worldLifecycleId;
		final History history;
		final long view;
		private boolean pending;
		private int x, z;
		private long blockedTick = Long.MIN_VALUE;
		public long snapshotCaptures, snapshotReuses;
		public Access(World world) { this(world, null); }
		public Access(World world, History history) { this.world = world; this.history = history; view = history == null ? 0 : history.id; observe(world); worldLifecycleId = worldLifecycleId(world); }
		public boolean ready(long tick) { return blockedTick != tick || pending && loaded(world, x, z) != null; }
		public void defer() { blockedTick = ExplosionWorkScheduler.currentTick(); }
		public String status() { return pending ? "waitingForChunk=" + x + "," + z + " loading=" + BombConfig.explosionTerrainLoading + " generation=" + BombConfig.explosionTerrainGeneration : "terrainReady=true"; }
		public void write(NBTTagCompound tag) {
			tag.setBoolean("chunkRequest", pending); tag.setInteger("requestX", x); tag.setInteger("requestZ", z);
		}
		public void read(NBTTagCompound tag) {
			pending = tag.getBoolean("chunkRequest"); x = tag.getInteger("requestX"); z = tag.getInteger("requestZ"); blockedTick = Long.MIN_VALUE;
		}
		public void cancel() {
			requests.values().removeIf(request -> request.jobId == id);
			Iterator<Capture> iterator = snapshots.values().iterator();
			while(iterator.hasNext()) {
				Capture capture = iterator.next();
				if(capture.jobId == id && capture.cursor < 4096) { iterator.remove(); retire(capture.data); }
			}
			// Retain the scalar pending request in NBT; deduplicated callers whose
			// transient request was removed re-enqueue from their own cursors.
		}
	}

	private static final class Key {
		final World world;
		final long chunk;
		final int section;
		final long view;
		Key(World world, int x, int y, int z) { this(world, x, y, z, 0); }
		Key(World world, int x, int y, int z, long view) { this.world = world; chunk = ChunkCoordIntPair.chunkXZ2Int(x, z); section = y; this.view = view; }
		public int hashCode() { return System.identityHashCode(world) * 31 + (int)(chunk ^ (chunk >>> 32)) * 17 + section + (int)view * 31; }
		public boolean equals(Object other) {
			if(!(other instanceof Key)) return false;
			Key key = (Key)other; return world == key.world && chunk == key.chunk && section == key.section && view == key.view;
		}
	}
	private static final class WorkerKey {
		final long lifecycle, chunk, view;
		final int section;
		WorkerKey(long lifecycle, int x, int y, int z, long view) { this.lifecycle = lifecycle; chunk = ChunkCoordIntPair.chunkXZ2Int(x, z); section = y; this.view = view; }
		public int hashCode() { return (int)(lifecycle ^ lifecycle >>> 32) * 31 + (int)(chunk ^ chunk >>> 32) * 17 + section + (int)view * 31; }
		public boolean equals(Object other) {
			if(!(other instanceof WorkerKey)) return false;
			WorkerKey key = (WorkerKey)other; return lifecycle == key.lifecycle && chunk == key.chunk && section == key.section && view == key.view;
		}
	}
	private static final class Request {
		final Key key;
		final long id = sequence.incrementAndGet();
		final long jobId;
		final long worldLifecycleId;
		long requested, attempted = Long.MIN_VALUE;
		Request(Key key, long jobId) { this.key = key; this.jobId = jobId; worldLifecycleId = worldLifecycleId(key.world); requested = ExplosionWorkScheduler.currentTick(); }
	}
	/** Published arrays are never subsequently modified, even after invalidation. */
	public static final class Snapshot {
		public final long id;
		public final long captureJobId, worldLifecycleId;
		final long view;
		public final int chunkX, sectionY, chunkZ;
		public final boolean zeroAirSection;
		// Typical sections contain only a few exact resistance/flag pairs. Keep
		// their full float values in a palette, never quantize custom resistance.
		private float[] paletteResistance;
		private byte[] paletteFlags, indices;
		private float[] expandedResistance;
		private byte[] expandedFlags;
		private int paletteSize;
		private long allocatedBytes;
		private final java.util.BitSet solids = new java.util.BitSet();
		// Dirty notifications are not evidence that resistance changed (lighting
		// sends render notifications too). Validate under the server work deadline
		// before publication/adoption, instead of throwing away useful ray work.
		private final java.util.BitSet dirty = new java.util.BitSet(4096);
		public volatile boolean valid = true;
		private volatile int readers;
		private boolean retired;
		private boolean complete;
		private boolean validationQueued;
		private boolean uniformAir;
		private Snapshot(Key key, boolean zeroAirSection, long captureJobId) {
			id = sequence.incrementAndGet(); this.captureJobId = captureJobId; worldLifecycleId = worldLifecycleId(key.world);
			view = key.view;
			chunkX = (int)key.chunk; chunkZ = (int)(key.chunk >> 32); sectionY = key.section; this.zeroAirSection = zeroAirSection;
			paletteResistance = zeroAirSection ? null : new float[256]; paletteFlags = zeroAirSection ? null : new byte[256]; indices = zeroAirSection ? null : new byte[4096];
			allocatedBytes = zeroAirSection ? AIR_SNAPSHOT_BYTES : SNAPSHOT_BYTES + CAPTURE_SCRATCH_BYTES;
		}
		public boolean isZeroAir() { return zeroAirSection || uniformAir; }
		public float resistance(int bit) { return isZeroAir() ? 0F : expandedResistance == null ? paletteResistance[indices[bit] & 255] : expandedResistance[bit]; }
		private byte flags(int bit) { return isZeroAir() ? (byte)4 : expandedFlags == null ? paletteFlags[indices[bit] & 255] : expandedFlags[bit]; }
		public boolean liquid(int bit) { return (flags(bit) & 2) != 0; }
		public boolean vanillaAir(int bit) { return (flags(bit) & 1) == 0; }
		public boolean air(int bit) { return (flags(bit) & 4) != 0; }
		public java.util.BitSet solids() { return (java.util.BitSet)solids.clone(); }
		public boolean contains(int x, int y, int z) { return x >> 4 == chunkX && y >> 4 == sectionY && z >> 4 == chunkZ; }
	}
	private static final class Capture {
		final Snapshot data;
		Chunk source; // Released after capture; cached primitive data must not pin chunks.
		int cursor;
		long requested;
		final long jobId;
		private long[] paletteKeys;
		private short[] paletteCodes;
		Capture(Key key, Chunk source, long jobId, boolean air) {
			data = new Snapshot(key, air, jobId);
			if(!air) { paletteKeys = new long[512]; paletteCodes = new short[512]; }
			this.source = source; this.jobId = jobId;
		}
		boolean put(int bit, float resistance, byte flags) {
			if(data.expandedResistance == null) {
				long key = (long)Float.floatToIntBits(resistance) << 3 | flags;
				int slot = (int)((key ^ key >>> 32) * 0x9e3779b97f4a7c15L >>> 55);
				while(paletteCodes[slot] != 0 && paletteKeys[slot] != key) slot = (slot + 1) & 511;
				int code = paletteCodes[slot] - 1;
				if(code < 0 && data.paletteSize < 256) {
					code = data.paletteSize++; data.paletteResistance[code] = resistance; data.paletteFlags[code] = flags;
					paletteKeys[slot] = key; paletteCodes[slot] = (short)(code + 1);
				}
				if(code >= 0) { data.indices[bit] = (byte)code; return true; }
				// Unusual custom sections can have more than 256 values. Grow only
				// within the same global byte ceiling, retaining the capture cursor.
				if(!reserve(EXPANDED_BYTES, data)) return false;
				data.expandedResistance = new float[4096]; data.expandedFlags = new byte[4096];
				data.allocatedBytes += EXPANDED_BYTES; bytes.addAndGet(EXPANDED_BYTES);
				for(int i = 0; i < 4096; i++) {
					int index = data.indices[i] & 255;
					data.expandedResistance[i] = data.paletteResistance[index]; data.expandedFlags[i] = data.paletteFlags[index];
				}
			}
			data.expandedResistance[bit] = resistance; data.expandedFlags[bit] = flags; return true;
		}
	}
	private static final class Validation {
		final World world;
		final Chunk source;
		final Snapshot data;
		Validation(World world, Chunk source, Snapshot data) { this.world = world; this.source = source; this.data = data; }
	}
	private static void queueValidation(World world, Chunk source, Snapshot data) {
		if(source == null || !data.complete || data.validationQueued || validations.size() >= MAX_CAPTURE_REQUESTS || !tryRetain(data)) return;
		data.validationQueued = true; validations.addLast(new Validation(world, source, data));
	}
	private static void finishValidation(Validation validation) {
		validation.data.validationQueued = false; release(validation.data);
	}
	/** Compare while the chunk is resident, rather than loading it again when a
	 * worker eventually returns. This queue never loads terrain or runs off-thread.
	 * One short slice per dispatch leaves the owning jobs their normal turn. */
	private static void serviceValidations(long deadline) {
		if(validations.isEmpty()) return;
		long start = System.nanoTime();
		long slice = Math.min(deadline, System.nanoTime() + 1_000_000L);
		try {
			while(!validations.isEmpty() && ExplosionWorkScheduler.before(slice)) {
				Validation validation = validations.peekFirst();
				Snapshot data = validation.data;
				if(!data.valid || data.dirty.isEmpty() || loaded(validation.world, data.chunkX, data.chunkZ) != validation.source) {
					validations.removeFirst(); finishValidation(validation); continue;
				}
				if(!compare(validation.world, data, slice)) return;
				validations.removeFirst(); finishValidation(validation); continue;
			}
		} finally { validationNanos += System.nanoTime() - start; }
	}
	private static boolean reserve(long needed, Snapshot keep) {
		long budget = (long)BombConfig.explosionSnapshotBudgetMiB * 1024L * 1024L;
		Iterator<Capture> iterator = snapshots.values().iterator();
		while(bytes.get() + needed > budget && iterator.hasNext()) {
			Capture old = iterator.next();
			if(old.data != keep && (old.data.complete || old.requested < ExplosionWorkScheduler.currentTick() - 1) && evict(old.data)) { iterator.remove(); evictions++; }
		}
		while(bytes.get() + needed > budget && !airColumns.isEmpty()) {
			Iterator<Key> columns = airColumns.keySet().iterator(); columns.next(); columns.remove(); bytes.addAndGet(-AIR_COLUMN_BYTES);
		}
		return bytes.get() + needed <= budget;
	}
	private static void rememberAir(Chunk chunk) {
		if(ExplosionNukeRayBatched.masqueradeResistance(Blocks.air) != 0F) return;
		int mask = 0;
		for(int y = 0; y < 16; y++) if(chunk.getBlockStorageArray()[y] == null) mask |= 1 << y;
		Key key = new Key(chunk.worldObj, chunk.xPosition, -2, chunk.zPosition);
		if(airColumns.containsKey(key)) { airColumns.put(key, mask); return; }
		if(airColumns.size() >= MAX_SNAPSHOTS) {
			Iterator<Key> iterator = airColumns.keySet().iterator(); iterator.next(); iterator.remove(); bytes.addAndGet(-AIR_COLUMN_BYTES);
		}
		if(reserve(AIR_COLUMN_BYTES, null)) { airColumns.put(key, mask); bytes.addAndGet(AIR_COLUMN_BYTES); }
	}
	private static void forgetAir(World world, int x, int z, int minY, int maxY) {
		Key key = new Key(world, x, -2, z);
		Integer mask = airColumns.get(key);
		if(mask == null) return;
		int remaining = mask;
		for(int y = Math.max(0, minY); y <= Math.min(15, maxY); y++) remaining &= ~(1 << y);
		airColumns.put(key, remaining);
	}
	private static void rememberVerifiedAir(World world, Snapshot data) {
		if(!data.isZeroAir()) return;
		Key key = new Key(world, data.chunkX, -2, data.chunkZ);
		Integer mask = airColumns.get(key);
		if(mask != null) airColumns.put(key, mask | 1 << data.sectionY);
	}

	public static Chunk loaded(World world, int x, int z) {
		if(!(world instanceof WorldServer)) return null;
		return (Chunk)((WorldServer)world).theChunkProviderServer.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
	}
	public static Chunk chunk(Access access, int x, int z, long deadline) {
		Chunk chunk = loaded(access.world, x, z);
		Key key = new Key(access.world, x, -1, z);
		if(chunk != null) {
			access.pending = false;
			// Refresh the bounded transient residency cache, without adding a ticket.
			terrainLoads.get(key);
			return chunk;
		}
		access.pending = true; access.x = x; access.z = z;
		if(ExplosionWorkScheduler.before(deadline)) {
			Request request = requests.get(key);
			if(request != null) request.requested = ExplosionWorkScheduler.currentTick();
			else if(requests.size() < MAX_CHUNK_REQUESTS) requests.put(key, new Request(key, access.id));
		}
		access.defer(); return null;
	}
	/** Known neighbor reads in ignition/fallout placement and vanilla callbacks. */
	public static boolean neighbors(Access access, int x, int z, long deadline) {
		for(int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) for(int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
			if((long)cx * 16L < -30000000L || (long)cx * 16L >= 30000000L || (long)cz * 16L < -30000000L || (long)cz * 16L >= 30000000L) continue;
			if(!ExplosionWorkScheduler.before(deadline) || chunk(access, cx, cz, deadline) == null) return false;
		}
		return true;
	}
	public static final class Sight {
		private static final int MAX_SIGHT_CHUNKS = 128;
		private final long[] chunks = new long[MAX_SIGHT_CHUNKS];
		private int count, step;
		public void reset() { count = step = 0; }
	}
	/** Preflight the chunk corridor of vanilla's at most 201 block-boundary
	 * visibility iterations. This does not replace its collision traversal. A
	 * half-block dominant-axis sample with a one-block margin includes corner
	 * reads, including ties that advance several voxel coordinates at once.
	 * The preflight cursor is disposable: restarting it never repeats ignition. */
	public static boolean sight(Access access, Sight sight, double x, double y, double z, double tx, double ty, double tz, long deadline) {
		for(int i = 0; i < sight.count; i++) {
			long key = sight.chunks[i];
			if(!ExplosionWorkScheduler.before(deadline) || chunk(access, (int)key, (int)(key >> 32), deadline) == null) return false;
		}
		double span = Math.max(Math.abs(tx - x), Math.max(Math.abs(ty - y), Math.abs(tz - z)));
		int steps = (int)Math.ceil(Math.min(208D, span) * 2D);
		while(sight.step <= steps && ExplosionWorkScheduler.before(deadline)) {
			double fraction = span == 0D ? 0D : Math.min(1D, sight.step * .5D / span);
			int bx = (int)Math.floor(x + (tx - x) * fraction), bz = (int)Math.floor(z + (tz - z) * fraction);
			for(int cx = (bx - 1) >> 4; cx <= (bx + 1) >> 4; cx++) for(int cz = (bz - 1) >> 4; cz <= (bz + 1) >> 4; cz++) {
				if((long)cx * 16L < -30000000L || (long)cx * 16L >= 30000000L || (long)cz * 16L < -30000000L || (long)cz * 16L >= 30000000L) continue;
				if(chunk(access, cx, cz, deadline) == null) return false;
				long key = ChunkCoordIntPair.chunkXZ2Int(cx, cz);
				boolean seen = false;
				for(int i = 0; i < sight.count; i++) if(sight.chunks[i] == key) { seen = true; break; }
				if(!seen) {
					if(sight.count == sight.chunks.length) { access.defer(); return false; }
					sight.chunks[sight.count++] = key;
				}
			}
			sight.step++;
		}
		return sight.step > steps;
	}

	/** Called between round-robin dispatches, against the shared (not slice) deadline. */
	public static void service(long deadline) {
		serviceValidations(deadline);
		if(!ExplosionWorkScheduler.before(deadline)) return;
		long tick = ExplosionWorkScheduler.currentTick();
		if(loadTick != tick) { loadTick = tick; loads = 0; }
		if(loads >= BombConfig.explosionChunkLoadsPerTick) return;
		int attempts = requests.size();
		while(attempts-- > 0 && !requests.isEmpty() && ExplosionWorkScheduler.before(deadline)) {
			Iterator<Map.Entry<Key, Request>> iterator = requests.entrySet().iterator();
			Map.Entry<Key, Request> first = iterator.next();
			Key key = first.getKey(); Request request = first.getValue(); iterator.remove();
			if(request.worldLifecycleId != worldLifecycleId(key.world) || request.requested < tick - 1 || loaded(key.world, (int)key.chunk, (int)(key.chunk >> 32)) != null) continue;
			requests.put(key, request); // FIFO rotation includes denied and failed requests.
			if(request.attempted == tick || loads >= BombConfig.explosionChunkLoadsPerTick) continue;
			request.attempted = tick;
			if(!BombConfig.explosionTerrainLoading) continue;
			WorldServer world = (WorldServer)key.world;
			int x = (int)key.chunk, z = (int)(key.chunk >> 32);
			// chunkExists on ChunkProviderServer means LOADED, not present on disk.
			boolean anvil = world.theChunkProviderServer.currentChunkLoader instanceof AnvilChunkLoader;
			boolean existing = anvil && ((AnvilChunkLoader)world.theChunkProviderServer.currentChunkLoader).chunkExists(world, x, z);
			if(!ExplosionWorkScheduler.before(deadline)) return;
			if(!existing && !BombConfig.explosionTerrainGeneration && (anvil || world.theChunkProviderServer.currentChunkLoader == null)) continue;
			loads++; // A failed synchronous attempt also consumes the global load allowance.
			chunkLoadsStarted++;
			long loadStarted = System.nanoTime();
			try {
				Chunk chunk = !BombConfig.explosionTerrainGeneration ? WorldUtil.loadExistingChunk(world, x, z) : existing ? WorldUtil.provideChunk(world, x, z) : world.theChunkProviderServer.loadChunk(x, z);
				if(chunk != null && loaded(world, x, z) == chunk) {
					requests.remove(key); terrainLoads.put(key, chunk);
					trimTerrainLoads();
				}
			} catch(Throwable failure) { MainRegistry.logger.error("Explosion terrain chunk request failed in dimension " + world.provider.dimensionId + " at " + x + "," + z, failure); }
			finally { loadingNanos += System.nanoTime() - loadStarted; }
			// Never start another load or job after an indivisible load overruns the tick.
			if(!ExplosionWorkScheduler.before(deadline)) return;
		}
	}
	private static void trimTerrainLoads() {
		while(terrainLoads.size() > MAX_RECENT_TERRAIN_LOADS) {
			Iterator<Map.Entry<Key, Chunk>> iterator = terrainLoads.entrySet().iterator();
			Map.Entry<Key, Chunk> first = iterator.next(); iterator.remove();
			if(loaded(first.getKey().world, first.getValue().xPosition, first.getValue().zPosition) == first.getValue()) ((WorldServer)first.getKey().world).theChunkProviderServer.unloadChunksIfNotNearSpawn(first.getValue().xPosition, first.getValue().zPosition);
		}
	}

	public static Snapshot snapshot(Access access, int x, int y, int z, long deadline) {
		Key key = new Key(access.world, x, y, z, access.view);
		Capture capture = snapshots.get(key);
		if(capture != null && capture.data.complete && capture.data.valid) {
			if(!validate(access, capture.data, deadline)) return null;
			if(capture.data.valid) {
				// Validation can finish in a later slice than the capture itself.
				published.put(new WorkerKey(capture.data.worldLifecycleId, x, y, z, access.view), capture.data);
				rememberVerifiedAir(access.world, capture.data);
				access.pending = false; access.snapshotReuses++; return capture.data;
			}
		}
		Integer knownAir = airColumns.get(new Key(access.world, x, -2, z));
		boolean air = knownAir != null && (knownAir & 1 << y) != 0 && ExplosionNukeRayBatched.masqueradeResistance(Blocks.air) == 0F;
		boolean original = access.history != null && access.history.hasSection(x, y, z);
		if(original) air = false;
		if(air) { airSectionReuses++; access.snapshotReuses++; }
		Chunk chunk = air ? null : chunk(access, x, z, deadline);
		if(!air && chunk == null || !ExplosionWorkScheduler.before(deadline)) return null;
		if(chunk != null) {
			rememberAir(chunk);
			air = !original && chunk.getBlockStorageArray()[y] == null && ExplosionNukeRayBatched.masqueradeResistance(Blocks.air) == 0F;
		}
		if(capture != null && (!capture.data.valid || !capture.data.complete && capture.source != chunk)) { snapshots.remove(key); retire(capture.data); capture = null; }
		if(capture == null) {
			if(captures >= MAX_CAPTURE_REQUESTS) {
				Iterator<Capture> stale = snapshots.values().iterator();
				while(stale.hasNext() && captures >= MAX_CAPTURE_REQUESTS) {
					Capture old = stale.next();
					if(old.cursor < 4096 && old.requested < ExplosionWorkScheduler.currentTick() - 1) { stale.remove(); retire(old.data); }
				}
			}
			if(captures >= MAX_CAPTURE_REQUESTS) { access.defer(); return null; }
			long budget = (long)BombConfig.explosionSnapshotBudgetMiB * 1024L * 1024L;
			long requiredBytes = air ? AIR_SNAPSHOT_BYTES : SNAPSHOT_BYTES + CAPTURE_SCRATCH_BYTES;
			Iterator<Capture> iterator = snapshots.values().iterator();
			while((bytes.get() + requiredBytes > budget || snapshots.size() >= MAX_SNAPSHOTS) && iterator.hasNext()) {
				Capture old = iterator.next();
				if((old.cursor == 4096 || old.requested < ExplosionWorkScheduler.currentTick() - 1) && evict(old.data)) { iterator.remove(); evictions++; }
			}
			if(!reserve(requiredBytes, null) || snapshots.size() >= MAX_SNAPSHOTS) { access.defer(); return null; }
			capture = new Capture(key, chunk, access.id, air); snapshots.put(key, capture); bytes.addAndGet(capture.data.allocatedBytes); captures++; access.snapshotCaptures++;
		}
		else if(capture.data.complete) access.snapshotReuses++;
		capture.requested = ExplosionWorkScheduler.currentTick();
		// A null storage section is vanilla air throughout; custom air still takes
		// the callback path. No need to perform 4096 identical world reads.
		if(capture.data.zeroAirSection && capture.cursor == 0) {
			capture.cursor = 4096;
		}
		while(capture.cursor < 4096 && ExplosionWorkScheduler.before(deadline)) {
			int bit = capture.cursor;
			int bx = (x << 4) + (bit & 15), by = (y << 4) + (bit >> 8), bz = (z << 4) + ((bit >> 4) & 15);
			Block block = access.world.getBlock(bx, by, bz);
			// Both callbacks are evaluated here, never by a worker. Do not approximate
			// resistance or replace custom isAir with material/ID comparisons.
			boolean liquid = block.getMaterial().isLiquid();
			float resistance = liquid ? 0F : ExplosionNukeRayBatched.masqueradeResistance(block);
			byte flags = (byte)((block != Blocks.air ? 1 : 0) | (liquid ? 2 : 0) | (access.world.isAirBlock(bx, by, bz) ? 4 : 0));
			long retained = original(access.history, x, y, z, bit, block);
			if(retained != -1L) { resistance = Float.intBitsToFloat((int)retained); flags = (byte)(retained >>> 32 & 7); }
			if(!capture.put(bit, resistance, flags)) { access.defer(); return null; }
			if((flags & 6) == 0) capture.data.solids.set(bit);
			capture.cursor++;
			if(!capture.data.valid) return null;
		}
		if(capture.cursor != 4096) return null;
		if(!capture.data.complete) {
			// Allocated storage can also be entirely vanilla air (for example after
			// lighting creates it). Prove this from every sampled callback result;
			// isEmpty()/material alone would incorrectly skip custom air resistance.
			if(!capture.data.zeroAirSection && capture.data.paletteSize == 1 && capture.data.paletteFlags[0] == 4 && Float.floatToIntBits(capture.data.paletteResistance[0]) == 0) {
				capture.data.uniformAir = true; capture.data.paletteResistance = null; capture.data.paletteFlags = null; capture.data.indices = null;
				capture.data.allocatedBytes -= SNAPSHOT_BYTES - AIR_SNAPSHOT_BYTES; bytes.addAndGet(-(SNAPSHOT_BYTES - AIR_SNAPSHOT_BYTES)); uniformAirCaptures++;
			}
			capture.source = null; capture.paletteKeys = null; capture.paletteCodes = null;
			if(!capture.data.zeroAirSection) { capture.data.allocatedBytes -= CAPTURE_SCRATCH_BYTES; bytes.addAndGet(-CAPTURE_SCRATCH_BYTES); }
			capture.data.complete = true;
			captures--;
		}
		if(!validate(access, capture.data, deadline) || !capture.data.valid) return null;
		published.put(new WorkerKey(capture.data.worldLifecycleId, x, y, z, access.view), capture.data);
		rememberVerifiedAir(access.world, capture.data);
		access.pending = false;
		return capture.data;
	}
	/** False means validation is still waiting; a completed comparison can retire
	 * the version, which the caller rejects without advancing its checkpoint. */
	static boolean validate(Access access, Snapshot snapshot, long deadline) {
		if(!snapshot.valid || snapshot.dirty.isEmpty()) return true;
		if(chunk(access, snapshot.chunkX, snapshot.chunkZ, deadline) == null) return false;
		return compare(access.world, snapshot, deadline);
	}
	private static boolean compare(World world, Snapshot snapshot, long deadline) {
		int bit;
		while((bit = snapshot.dirty.nextSetBit(0)) >= 0 && ExplosionWorkScheduler.before(deadline)) {
			int x = (snapshot.chunkX << 4) + (bit & 15), y = (snapshot.sectionY << 4) + (bit >> 8), z = (snapshot.chunkZ << 4) + ((bit >> 4) & 15);
			Block block = world.getBlock(x, y, z);
			boolean liquid = block.getMaterial().isLiquid();
			float resistance = liquid ? 0F : ExplosionNukeRayBatched.masqueradeResistance(block);
			byte flags = (byte)((block != Blocks.air ? 1 : 0) | (liquid ? 2 : 0) | (world.isAirBlock(x, y, z) ? 4 : 0));
			long retained = original(histories.get(snapshot.view), snapshot.chunkX, snapshot.sectionY, snapshot.chunkZ, bit, block);
			if(retained != -1L) { resistance = Float.intBitsToFloat((int)retained); flags = (byte)(retained >>> 32 & 7); }
			if(flags != snapshot.flags(bit) || Float.floatToIntBits(resistance) != Float.floatToIntBits(snapshot.resistance(bit))) {
				snapshot.valid = false; invalidations++; return true;
			}
			snapshot.dirty.clear(bit); unchangedValidations++;
		}
		if(!snapshot.dirty.isEmpty()) return false;
		rememberVerifiedAir(world, snapshot); return true;
	}
	private static long original(History history, int x, int y, int z, int bit, Block live) {
		if(history == null) return -1L;
		// Only our post-removal vanilla air is overlaid. Other world changes still
		// invalidate snapshots, including replacements at a previously removed cell.
		if(live != Blocks.air) { history.replaced(x, y, z, bit); return -1L; }
		long value = history.original(x, y, z, bit);
		return value < 0 ? -1L : value;
	}
	public static void retain(Snapshot snapshot) {
		if(snapshot != null && !tryRetain(snapshot)) throw new IllegalStateException("Cannot retain a retired explosion snapshot");
	}
	static boolean tryRetain(Snapshot snapshot) {
		synchronized(snapshot) {
			if(snapshot.retired || !snapshot.valid) return false;
			snapshot.readers++; return true;
		}
	}
	/** Acquire only existing immutable data. Retirement and retaining synchronize
	 * on the same buffer, so no worker can resurrect an evicted allocation. */
	static Snapshot acquire(long lifecycle, long view, int x, int y, int z) {
		Snapshot snapshot = published.get(new WorkerKey(lifecycle, x, y, z, view));
		if(snapshot == null) return null;
		return tryRetain(snapshot) ? snapshot : null;
	}
	/** Reuse already captured nearby sections in one bounded worker handoff. A ray
	 * can cross and revisit section boundaries without waiting another server tick.
	 * Missing sections still use the normal budgeted request/capture path. */
	static Snapshot[] workerSnapshots(Access access, Snapshot required, java.util.List<int[]> corridor, long deadline) {
		java.util.ArrayList<Snapshot> batch = new java.util.ArrayList<Snapshot>();
		if(required != null) batch.add(required);
		// Look up only the bounded working set actually read by this range. Do
		// not scan the global cache or capture speculative sections in the blast.
		for(int i = corridor.size() - 1; i >= 0 && batch.size() < ExplosionRayWorker.MAX_BATCH_SNAPSHOTS && ExplosionWorkScheduler.before(deadline); i--) {
			int[] coord = corridor.get(i);
			Capture capture = snapshots.get(new Key(access.world, coord[0], coord[1], coord[2], access.view));
			if(capture != null && capture.data != required && capture.data.valid && capture.data.complete) {
				batch.add(capture.data); access.snapshotReuses++;
			}
		}
		return batch.toArray(new Snapshot[batch.size()]);
	}
	public static void release(Snapshot snapshot) {
		if(snapshot != null) synchronized(snapshot) { if(--snapshot.readers == 0 && snapshot.retired) bytes.addAndGet(-snapshot.allocatedBytes); }
	}
	private static void retire(Snapshot snapshot) {
		synchronized(snapshot) {
			snapshot.valid = false;
			published.remove(new WorkerKey(snapshot.worldLifecycleId, snapshot.chunkX, snapshot.sectionY, snapshot.chunkZ, snapshot.view), snapshot);
			if(!snapshot.retired) {
				snapshot.retired = true; if(!snapshot.complete) captures--;
				if(snapshot.readers == 0) bytes.addAndGet(-snapshot.allocatedBytes);
			}
		}
	}
	private static boolean evict(Snapshot snapshot) {
		synchronized(snapshot) {
			// A worker can acquire through the directory between a server-side
			// reader-count check and retirement. Make that decision atomic too.
			if(snapshot.readers != 0) return false;
			retire(snapshot); return true;
		}
	}
	@SubscribeEvent public void load(ChunkEvent.Load event) { invalidate(event.getChunk()); }
	@SubscribeEvent public void unload(ChunkEvent.Unload event) {
		Chunk chunk = event.getChunk();
		if(chunk.worldObj.isRemote) return;
		ExplosionNukeRayBatched.unloadChunkWorkers(chunk.worldObj, chunk.xPosition, chunk.zPosition);
		// Completed immutable data remains useful without keeping the chunk loaded.
		// A later load requires validation before new results can be adopted.
		for(Capture capture : snapshots.values()) if(capture.source == chunk) capture.data.valid = false;
		terrainLoads.remove(new Key(chunk.worldObj, chunk.xPosition, -1, chunk.zPosition));
	}
	@SubscribeEvent public void loadWorld(WorldEvent.Load event) { observe(event.world); }
	private static void observe(World world) {
		if(!world.isRemote && !observers.containsKey(world)) {
			Observer observer = new Observer(world); observers.put(world, observer); world.addWorldAccess(observer);
		}
	}
	private static long worldLifecycleId(World world) {
		Observer observer = observers.get(world); return observer == null ? 0L : observer.id;
	}
	private static void invalidate(Chunk chunk) {
		if(chunk.worldObj.isRemote) return;
		boolean known = airColumns.containsKey(new Key(chunk.worldObj, chunk.xPosition, -2, chunk.zPosition));
		Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
		long coord = ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition);
		while(iterator.hasNext()) {
			Map.Entry<Key, Capture> entry = iterator.next();
			if(entry.getKey().world == chunk.worldObj && entry.getKey().chunk == coord) {
				known = true;
				Capture capture = entry.getValue();
				if(capture.data.complete) { capture.data.dirty.set(0, 4096); queueValidation(chunk.worldObj, chunk, capture.data); }
				else { iterator.remove(); retire(capture.data); }
			}
		}
		terrainLoads.remove(new Key(chunk.worldObj, chunk.xPosition, -1, chunk.zPosition));
		if(known) rememberAir(chunk);
	}
	public static void unload(World world) {
		for(History history : histories.values().toArray(new History[0])) if(history.world == world) history.close();
		Iterator<Validation> pending = validations.iterator();
		while(pending.hasNext()) {
			Validation validation = pending.next();
			if(validation.world == world) { pending.remove(); finishValidation(validation); }
		}
		Observer observer = observers.remove(world);
		if(observer != null) world.removeWorldAccess(observer);
		requests.keySet().removeIf(key -> key.world == world);
		Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
		while(iterator.hasNext()) {
			Map.Entry<Key, Capture> entry = iterator.next();
			if(entry.getKey().world == world) { iterator.remove(); retire(entry.getValue().data); }
		}
		terrainLoads.keySet().removeIf(key -> key.world == world);
		Iterator<Key> columns = airColumns.keySet().iterator();
		while(columns.hasNext()) if(columns.next().world == world) { columns.remove(); bytes.addAndGet(-AIR_COLUMN_BYTES); }
	}
	public static void clear() {
		for(History history : histories.values().toArray(new History[0])) history.close();
		while(!validations.isEmpty()) finishValidation(validations.removeFirst());
		for(Map.Entry<World, Observer> entry : observers.entrySet()) entry.getKey().removeWorldAccess(entry.getValue());
		observers.clear();
		requests.clear();
		for(Capture capture : snapshots.values()) retire(capture.data);
		bytes.addAndGet(-AIR_COLUMN_BYTES * airColumns.size()); airColumns.clear();
		snapshots.clear(); terrainLoads.clear(); loadTick = Long.MIN_VALUE; loads = 0;
	}
	/** Invalidate changed sections without making workers touch mutable storage. */
	private static final class Observer implements IWorldAccess {
		final World world;
		final long id = sequence.incrementAndGet();
		Observer(World world) { this.world = world; }
		private void dirty(Capture capture, Key key, int x, int y, int z, int x2, int y2, int z2) {
			int bx = (int)key.chunk << 4, by = key.section << 4, bz = (int)(key.chunk >> 32) << 4;
			int minX = Math.max(0, x - bx), maxX = Math.min(15, x2 - bx);
			int minY = Math.max(0, y - by), maxY = Math.min(15, y2 - by);
			int minZ = Math.max(0, z - bz), maxZ = Math.min(15, z2 - bz);
			if(minX == 0 && maxX == 15 && minY == 0 && maxY == 15 && minZ == 0 && maxZ == 15) { capture.data.dirty.set(0, 4096); return; }
			for(int yy = minY; yy <= maxY; yy++) for(int zz = minZ; zz <= maxZ; zz++) {
				int row = yy << 8 | zz << 4; capture.data.dirty.set(row + minX, row + maxX + 1);
			}
		}
		public void markBlockForUpdate(int x, int y, int z) {
			forgetAir(world, x >> 4, z >> 4, y >> 4, y >> 4);
			if(snapshots.isEmpty()) return;
			markPoint(x, y, z, 0);
			for(History history : histories.values()) if(history.world == world) markPoint(x, y, z, history.id);
		}
		private void markPoint(int x, int y, int z, long view) {
			Capture capture = snapshots.get(new Key(world, x >> 4, y >> 4, z >> 4, view));
			if(capture != null) {
				capture.data.dirty.set((y & 15) << 8 | (z & 15) << 4 | (x & 15));
				queueValidation(world, loaded(world, x >> 4, z >> 4), capture.data);
			}
		}
		public void markBlockForRenderUpdate(int x, int y, int z) { markBlockForUpdate(x, y, z); }
		public void markBlockRangeForRenderUpdate(int x, int y, int z, int x2, int y2, int z2) {
			int minX = x >> 4, maxX = x2 >> 4, minZ = z >> 4, maxZ = z2 >> 4, minY = Math.max(0, y >> 4), maxY = Math.min(15, y2 >> 4);
			if(minX > maxX || minY > maxY || minZ > maxZ) return;
			if(((long)maxX - minX + 1) * ((long)maxZ - minZ + 1) <= 64) {
				for(int cx = minX; cx <= maxX; cx++) for(int cz = minZ; cz <= maxZ; cz++) forgetAir(world, cx, cz, minY, maxY);
			} else {
				for(Map.Entry<Key, Integer> column : airColumns.entrySet()) {
					Key key = column.getKey();
					if(key.world != world || (int)key.chunk < minX || (int)key.chunk > maxX || (int)(key.chunk >> 32) < minZ || (int)(key.chunk >> 32) > maxZ) continue;
					int mask = column.getValue();
					for(int sy = minY; sy <= maxY; sy++) mask &= ~(1 << sy);
					column.setValue(mask);
				}
			}
			if(snapshots.isEmpty()) return;
			if(((long)maxX - minX + 1) * ((long)maxZ - minZ + 1) * (maxY - minY + 1) <= 64) {
				for(int cx = minX; cx <= maxX; cx++) for(int cz = minZ; cz <= maxZ; cz++) for(int sy = minY; sy <= maxY; sy++) {
					markRange(new Key(world, cx, sy, cz), x, y, z, x2, y2, z2);
					for(History history : histories.values()) if(history.world == world) markRange(new Key(world, cx, sy, cz, history.id), x, y, z, x2, y2, z2);
				}
				return;
			}
			Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
			while(iterator.hasNext()) {
				Map.Entry<Key, Capture> entry = iterator.next(); Key key = entry.getKey();
				if(key.world == world && (int)key.chunk >= x >> 4 && (int)key.chunk <= x2 >> 4 && (int)(key.chunk >> 32) >= z >> 4 && (int)(key.chunk >> 32) <= z2 >> 4 && key.section >= y >> 4 && key.section <= y2 >> 4) {
					dirty(entry.getValue(), key, x, y, z, x2, y2, z2); queueValidation(world, loaded(world, (int)key.chunk, (int)(key.chunk >> 32)), entry.getValue().data);
				}
			}
		}
		private void markRange(Key key, int x, int y, int z, int x2, int y2, int z2) {
			Capture capture = snapshots.get(key);
			if(capture != null) { dirty(capture, key, x, y, z, x2, y2, z2); queueValidation(world, loaded(world, (int)key.chunk, (int)(key.chunk >> 32)), capture.data); }
		}
		public void onEntityCreate(Entity entity) { }
		public void onEntityDestroy(Entity entity) {
			if(entity instanceof com.hbm.entity.logic.EntityNukeExplosionMK5) ((com.hbm.entity.logic.EntityNukeExplosionMK5)entity).detachTerrain();
		}
		public void playSound(String sound, double x, double y, double z, float volume, float pitch) { }
		public void playSoundToNearExcept(EntityPlayer player, String sound, double x, double y, double z, float volume, float pitch) { }
		public void spawnParticle(String particle, double x, double y, double z, double vx, double vy, double vz) { }
		public void playRecord(String record, int x, int y, int z) { }
		public void broadcastSound(int sound, int x, int y, int z, int data) { }
		public void playAuxSFX(EntityPlayer player, int effect, int x, int y, int z, int data) { }
		public void destroyBlockPartially(int entity, int x, int y, int z, int progress) { }
		public void onStaticEntitiesChanged() { }
	}
}
