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
	public static final int MAX_SNAPSHOTS = 4096;
	public static final int MAX_CAPTURE_REQUESTS = 128;
	// Primitive arrays plus a conservative allowance for array/object headers.
	private static final long SNAPSHOT_BYTES = 4096L * 5L + 512L;
	private static final LinkedHashMap<Key, Request> requests = new LinkedHashMap<Key, Request>();
	private static final LinkedHashMap<Key, Capture> snapshots = new LinkedHashMap<Key, Capture>(16, .75F, true);
	private static final LinkedHashMap<Key, Chunk> terrainLoads = new LinkedHashMap<Key, Chunk>(16, .75F, true);
	private static final int MAX_RECENT_TERRAIN_LOADS = 128;
	private static final java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
	private static long loadTick = Long.MIN_VALUE;
	private static int loads;
	private static int captures;
	private static final Map<World, Observer> observers = new java.util.IdentityHashMap<World, Observer>();

	/** One pending request per caller; the authoritative cursor remains with that caller. */
	public static final class Access {
		public final World world;
		public final long id = sequence.incrementAndGet();
		public final long worldLifecycleId;
		private boolean pending;
		private int x, z;
		private long blockedTick = Long.MIN_VALUE;
		public long snapshotCaptures, snapshotReuses;
		public Access(World world) { this.world = world; observe(world); worldLifecycleId = worldLifecycleId(world); }
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
		Key(World world, int x, int y, int z) { this.world = world; chunk = ChunkCoordIntPair.chunkXZ2Int(x, z); section = y; }
		public int hashCode() { return System.identityHashCode(world) * 31 + (int)(chunk ^ (chunk >>> 32)) * 17 + section; }
		public boolean equals(Object other) {
			if(!(other instanceof Key)) return false;
			Key key = (Key)other; return world == key.world && chunk == key.chunk && section == key.section;
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
		public final int chunkX, sectionY, chunkZ;
		public final boolean zeroAirSection;
		private final float[] resistance = new float[4096];
		private final byte[] flags = new byte[4096];
		public volatile boolean valid = true;
		private volatile int readers;
		private boolean retired;
		private boolean complete;
		private Snapshot(Key key, boolean zeroAirSection, long captureJobId) {
			id = sequence.incrementAndGet(); this.captureJobId = captureJobId; worldLifecycleId = worldLifecycleId(key.world);
			chunkX = (int)key.chunk; chunkZ = (int)(key.chunk >> 32); sectionY = key.section; this.zeroAirSection = zeroAirSection;
		}
		public float resistance(int bit) { return resistance[bit]; }
		public boolean liquid(int bit) { return (flags[bit] & 2) != 0; }
		public boolean vanillaAir(int bit) { return (flags[bit] & 1) == 0; }
		public boolean air(int bit) { return (flags[bit] & 4) != 0; }
		public boolean contains(int x, int y, int z) { return x >> 4 == chunkX && y >> 4 == sectionY && z >> 4 == chunkZ; }
	}
	private static final class Capture {
		final Snapshot data;
		final Chunk source; // Server-only; never included in worker inputs.
		int cursor;
		long requested;
		final long jobId;
		Capture(Key key, Chunk source, long jobId) {
			data = new Snapshot(key, source.getBlockStorageArray()[key.section] == null && ExplosionNukeRayBatched.masqueradeResistance(Blocks.air) == 0F, jobId);
			this.source = source; this.jobId = jobId;
		}
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
			try {
				Chunk chunk = !BombConfig.explosionTerrainGeneration ? WorldUtil.loadExistingChunk(world, x, z) : existing ? WorldUtil.provideChunk(world, x, z) : world.theChunkProviderServer.loadChunk(x, z);
				if(chunk != null && loaded(world, x, z) == chunk) {
					requests.remove(key); terrainLoads.put(key, chunk);
					trimTerrainLoads();
				}
			} catch(Throwable failure) { MainRegistry.logger.error("Explosion terrain chunk request failed in dimension " + world.provider.dimensionId + " at " + x + "," + z, failure); }
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
		Chunk chunk = chunk(access, x, z, deadline);
		if(chunk == null || !ExplosionWorkScheduler.before(deadline)) return null;
		Key key = new Key(access.world, x, y, z);
		Capture capture = snapshots.get(key);
		if(capture != null && (capture.source != chunk || !capture.data.valid)) { snapshots.remove(key); retire(capture.data); capture = null; }
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
			Iterator<Capture> iterator = snapshots.values().iterator();
			while((bytes.get() + SNAPSHOT_BYTES > budget || snapshots.size() >= MAX_SNAPSHOTS) && iterator.hasNext()) {
				Capture old = iterator.next();
				if(old.data.readers == 0 && (old.cursor == 4096 || old.requested < ExplosionWorkScheduler.currentTick() - 1)) { iterator.remove(); retire(old.data); }
			}
			if(bytes.get() + SNAPSHOT_BYTES > budget || snapshots.size() >= MAX_SNAPSHOTS) { access.defer(); return null; }
			capture = new Capture(key, chunk, access.id); snapshots.put(key, capture); bytes.addAndGet(SNAPSHOT_BYTES); captures++; access.snapshotCaptures++;
		}
		else if(capture.data.complete) access.snapshotReuses++;
		capture.requested = ExplosionWorkScheduler.currentTick();
		// A null storage section is vanilla air throughout; custom air still takes
		// the callback path. No need to perform 4096 identical world reads.
		if(capture.data.zeroAirSection && capture.cursor == 0) {
			java.util.Arrays.fill(capture.data.flags, (byte)4); capture.cursor = 4096;
		}
		while(capture.cursor < 4096 && ExplosionWorkScheduler.before(deadline)) {
			int bit = capture.cursor;
			int bx = (x << 4) + (bit & 15), by = (y << 4) + (bit >> 8), bz = (z << 4) + ((bit >> 4) & 15);
			Block block = access.world.getBlock(bx, by, bz);
			// Both callbacks are evaluated here, never by a worker. Do not approximate
			// resistance or replace custom isAir with material/ID comparisons.
			boolean liquid = block.getMaterial().isLiquid();
			capture.data.resistance[bit] = liquid ? 0F : ExplosionNukeRayBatched.masqueradeResistance(block);
			capture.data.flags[bit] = (byte)((block != Blocks.air ? 1 : 0) | (liquid ? 2 : 0) | (access.world.isAirBlock(bx, by, bz) ? 4 : 0));
			capture.cursor++;
			if(!capture.data.valid) return null;
		}
		if(capture.cursor != 4096) return null;
		if(!capture.data.complete) { capture.data.complete = true; captures--; }
		return capture.data;
	}
	public static void retain(Snapshot snapshot) { if(snapshot != null) synchronized(snapshot) { snapshot.readers++; } }
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
			Capture capture = snapshots.get(new Key(access.world, coord[0], coord[1], coord[2]));
			if(capture != null && capture.data != required && capture.data.valid && capture.data.complete) {
				batch.add(capture.data); access.snapshotReuses++;
			}
		}
		return batch.toArray(new Snapshot[batch.size()]);
	}
	public static void release(Snapshot snapshot) {
		if(snapshot != null) synchronized(snapshot) { if(--snapshot.readers == 0 && snapshot.retired) bytes.addAndGet(-SNAPSHOT_BYTES); }
	}
	private static void retire(Snapshot snapshot) {
		synchronized(snapshot) {
			snapshot.valid = false;
			if(!snapshot.retired) {
				snapshot.retired = true; if(!snapshot.complete) captures--;
				if(snapshot.readers == 0) bytes.addAndGet(-SNAPSHOT_BYTES);
			}
		}
	}
	@SubscribeEvent public void load(ChunkEvent.Load event) { invalidate(event.getChunk()); }
	@SubscribeEvent public void unload(ChunkEvent.Unload event) { invalidate(event.getChunk()); }
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
		ExplosionNukeRayBatched.unloadChunkWorkers(chunk.worldObj, chunk.xPosition, chunk.zPosition);
		Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
		long coord = ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition);
		while(iterator.hasNext()) {
			Map.Entry<Key, Capture> entry = iterator.next();
			if(entry.getKey().world == chunk.worldObj && entry.getKey().chunk == coord) { iterator.remove(); retire(entry.getValue().data); }
		}
		terrainLoads.remove(new Key(chunk.worldObj, chunk.xPosition, -1, chunk.zPosition));
	}
	public static void unload(World world) {
		Observer observer = observers.remove(world);
		if(observer != null) world.removeWorldAccess(observer);
		requests.keySet().removeIf(key -> key.world == world);
		Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
		while(iterator.hasNext()) {
			Map.Entry<Key, Capture> entry = iterator.next();
			if(entry.getKey().world == world) { iterator.remove(); retire(entry.getValue().data); }
		}
		terrainLoads.keySet().removeIf(key -> key.world == world);
	}
	public static void clear() {
		for(Map.Entry<World, Observer> entry : observers.entrySet()) entry.getKey().removeWorldAccess(entry.getValue());
		observers.clear();
		requests.clear();
		for(Capture capture : snapshots.values()) retire(capture.data);
		snapshots.clear(); terrainLoads.clear(); loadTick = Long.MIN_VALUE; loads = 0;
	}
	/** Invalidate changed sections without making workers touch mutable storage. */
	private static final class Observer implements IWorldAccess {
		final World world;
		final long id = sequence.incrementAndGet();
		Observer(World world) { this.world = world; }
		public void markBlockForUpdate(int x, int y, int z) {
			if(snapshots.isEmpty()) return;
			Capture capture = snapshots.remove(new Key(world, x >> 4, y >> 4, z >> 4));
			if(capture != null) retire(capture.data);
		}
		public void markBlockForRenderUpdate(int x, int y, int z) { markBlockForUpdate(x, y, z); }
		public void markBlockRangeForRenderUpdate(int x, int y, int z, int x2, int y2, int z2) {
			if(snapshots.isEmpty()) return;
			int minX = x >> 4, maxX = x2 >> 4, minZ = z >> 4, maxZ = z2 >> 4, minY = Math.max(0, y >> 4), maxY = Math.min(15, y2 >> 4);
			if(minX > maxX || minY > maxY || minZ > maxZ) return;
			if(((long)maxX - minX + 1) * ((long)maxZ - minZ + 1) * (maxY - minY + 1) <= 64) {
				for(int cx = minX; cx <= maxX; cx++) for(int cz = minZ; cz <= maxZ; cz++) for(int sy = minY; sy <= maxY; sy++) {
					Capture capture = snapshots.remove(new Key(world, cx, sy, cz)); if(capture != null) retire(capture.data);
				}
				return;
			}
			Iterator<Map.Entry<Key, Capture>> iterator = snapshots.entrySet().iterator();
			while(iterator.hasNext()) {
				Map.Entry<Key, Capture> entry = iterator.next(); Key key = entry.getKey();
				if(key.world == world && (int)key.chunk >= x >> 4 && (int)key.chunk <= x2 >> 4 && (int)(key.chunk >> 32) >= z >> 4 && (int)(key.chunk >> 32) <= z2 >> 4 && key.section >= y >> 4 && key.section <= y2 >> 4) { iterator.remove(); retire(entry.getValue().data); }
			}
		}
		public void onEntityCreate(Entity entity) { }
		public void onEntityDestroy(Entity entity) { }
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
