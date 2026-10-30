package com.hbm.explosion;

import java.util.BitSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.hbm.config.BombConfig;
import com.hbm.explosion.ExplosionTerrainAccess.Snapshot;

/** Pure bounded computation. Inputs contain no Minecraft world objects or callbacks. */
final class ExplosionRayWorker {
	static final int MAX_BATCH_STEPS = 32768;
	// Cheap rays must not spend most of their lifetime waiting for the next
	// server tick. The unchanged operation cap still bounds expensive batches.
	static final int MAX_BATCH_RAYS = 8192;
	static final int MAX_BATCH_SNAPSHOTS = 128;
	static final int MAX_JOB_BATCHES = 4;
	static final int MAX_OUTSTANDING_BATCHES = 64;
	static final int RESUME_BATCH_RESERVE = 16;
	static final int EXECUTOR_QUEUE_CAPACITY = 64;
	static final int CANCELLATION_INTERVAL = 32;
	private static final java.lang.management.ThreadMXBean cpu = java.lang.management.ManagementFactory.getThreadMXBean();
	private static final AtomicInteger outstanding = new AtomicInteger();
	private static final AtomicInteger threadIds = new AtomicInteger();
	private static final java.util.concurrent.atomic.AtomicLong lifecycles = new java.util.concurrent.atomic.AtomicLong();
	private static ThreadPoolExecutor pool;
	private static boolean accepting = true;

	static final class Lifecycle {
		final long jobId;
		final long worldLifecycleId;
		final long id = lifecycles.incrementAndGet();
		volatile boolean cancelled;
		Lifecycle(long jobId, long worldLifecycleId) { this.jobId = jobId; this.worldLifecycleId = worldLifecycleId; }
	}
	static final class State implements Cloneable {
		int posX, posY, posZ, length, rayLength, surfaceY, step, buildStep, tipX, tipY, tipZ, initialStrength, gspNum, gspNumMax, rayLimit;
		boolean contained, finishing, hasTip, building, active, recorded;
		double dx, dy, dz, buildLength, bx, by, bz, gx, gy;
		float strength, tx, ty, tz, airResistance;
		State copy() {
			try { return (State)clone(); } catch(CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
		}
	}
	static final class Result {
		final State state;
		final long jobId, lifecycleId, worldLifecycleId;
		final java.util.ArrayList<Mask> masks = new java.util.ArrayList<Mask>();
		final java.util.HashMap<Long, Mask> versions = new java.util.HashMap<Long, Mask>();
		boolean missing;
		int chunkX, sectionY, chunkZ;
		int completedRays;
		int operations, sectionMisses;
		long calculationNanos, cpuNanos, traversalOperations, reconstructionOperations, reusedSamples, skippedReconstruction, attenuationHits, sharedSnapshotHits;
		final java.util.HashSet<Long> qualified;
		Result(State state, Lifecycle lifecycle, Snapshot[] snapshots, BitSet[] covered, long[] eligible) {
			this.state = state; jobId = lifecycle.jobId; lifecycleId = lifecycle.id; worldLifecycleId = lifecycle.worldLifecycleId;
			for(int i = 0; i < snapshots.length; i++) { Mask mask = new Mask(snapshots[i], covered[i]); masks.add(mask); versions.put(snapshots[i].id, mask); }
			qualified = new java.util.HashSet<Long>();
			for(long key : eligible) qualified.add(key);
		}
	}
	static final class Mask {
		final Snapshot snapshot;
		final BitSet destruction = new BitSet(), tips = new BitSet();
		final BitSet remaining;
		boolean used;
		Mask(Snapshot snapshot, BitSet covered) {
			this.snapshot = snapshot;
			remaining = snapshot.solids(); remaining.andNot(covered);
		}
		void record(int bit) { destruction.set(bit); remaining.clear(bit); }
	}

	/** Per-range scratch is handed to only one worker at a time. It owns no world
	 * objects or pinned snapshots and is disposable on cancellation or reload. */
	static final class Scratch {
		final Prefix[] prefixes = new Prefix[6];
		final long[] attenuationKeys = new long[4096];
		final double[] attenuationValues = new double[4096];
		final BitSet attenuationPresent = new BitSet(4096);
		double attenuation(float resistance, int step, int length, Result output) {
			long key = (long)Float.floatToIntBits(resistance) << 32 | (step & 0xffffffffL);
			int slot = (int)((key ^ (key >>> 33)) * 0xff51afd7ed558ccdL >>> 52);
			if(attenuationPresent.get(slot) && attenuationKeys[slot] == key) { output.attenuationHits++; return attenuationValues[slot]; }
			double fac = (100 - (double)step / length * 100) * .07D;
			double value = Math.pow(resistance, 7.5D - fac);
			attenuationKeys[slot] = key; attenuationValues[slot] = value; attenuationPresent.set(slot);
			return value;
		}
	}

	/** A beam proves identical sampled voxels or uniform zero-air intervals;
	 * it is independent of destruction masks and retains exact float energy.
	 * Conservative direction intervals exclude float rounding boundaries; every
	 * input section ID must still be present before a checkpoint can be reused. */
	private static final class Prefix {
		final double[] low = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		final double[] high = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		final long[] sections = new long[32];
		int sectionCount, step, tipStep = -1;
		float strength;
		boolean finishing;
		final long[] qualified = new long[32];
		int qualifiedCount;
		void reset() {
			java.util.Arrays.fill(low, -Double.MAX_VALUE); java.util.Arrays.fill(high, Double.MAX_VALUE);
			sectionCount = step = qualifiedCount = 0; tipStep = -1; finishing = false;
		}
		void copyFrom(Prefix other) {
			System.arraycopy(other.low, 0, low, 0, 3); System.arraycopy(other.high, 0, high, 0, 3);
			System.arraycopy(other.sections, 0, sections, 0, other.sectionCount); sectionCount = other.sectionCount;
			step = other.step; tipStep = other.tipStep; strength = other.strength; finishing = other.finishing;
			qualifiedCount = other.qualifiedCount; System.arraycopy(other.qualified, 0, qualified, 0, qualifiedCount);
		}
		boolean sample(State s, Snapshot snapshot, int x, int y, int z) {
			if(!include(snapshot)) return false;
			if(s.step == 0) return true;
			bound(0, s.posX, x, s.step); bound(1, s.posY, y, s.step); bound(2, s.posZ, z, s.step);
			return true;
		}
		private boolean include(Snapshot snapshot) {
			int i = 0; while(i < sectionCount && sections[i] != snapshot.id) i++;
			if(i == sectionCount) { if(sectionCount == sections.length) return false; sections[sectionCount++] = snapshot.id; }
			return true;
		}
		boolean airSection(State s, Snapshot snapshot, int end) {
			if(!include(snapshot)) return false;
			// Monotone coordinates between these endpoints stay in the same
			// proven zero-resistance air section; individual voxels do not matter.
			for(int endpoint = 0; endpoint < 2; endpoint++) {
				int step = endpoint == 0 ? s.step : end;
				if(step == 0) continue;
				bound(0, s.posX, snapshot.chunkX << 4, (snapshot.chunkX << 4) + 16, step);
				bound(1, s.posY, snapshot.sectionY << 4, s.contained ? Math.min((snapshot.sectionY << 4) + 16, s.surfaceY) : (snapshot.sectionY << 4) + 16, step);
				bound(2, s.posZ, snapshot.chunkZ << 4, (snapshot.chunkZ << 4) + 16, step);
			}
			return true;
		}
		private void bound(int axis, int origin, int block, int step) {
			bound(axis, origin, block, block + 1, step);
		}
		private void bound(int axis, int origin, int block, int end, int step) {
			// A full float ULP on both sides also covers double multiply/add rounding
			// throughout Minecraft's coordinate range. Ambiguous cells simply miss.
			low[axis] = Math.max(low[axis], Math.nextUp(((double)block + Math.ulp((float)block) - origin) / step));
			high[axis] = Math.min(high[axis], Math.nextDown(((double)end - Math.ulp((float)end) - origin) / step));
		}
		boolean fits(State s) { return s.dx > low[0] && s.dx < high[0] && s.dy > low[1] && s.dy < high[1] && s.dz > low[2] && s.dz < high[2]; }
	}
	static final class Batch implements Runnable {
		final Lifecycle lifecycle;
		final State state;
		final long[] eligible;
		final AtomicBoolean started = new AtomicBoolean(), disposed = new AtomicBoolean();
		volatile boolean cancelled;
		volatile Result result;
		volatile Throwable failure;
		Snapshot[] snapshots;
		final BitSet[] covered;
		final Scratch scratch;
		private final java.util.ArrayList<Snapshot> acquired = new java.util.ArrayList<Snapshot>();
		private Snapshot snapshot;
		private Mask mask;
		private Prefix prefix;
		private final Prefix prefixState = new Prefix();
		Batch(State state, Snapshot[] snapshots, BitSet[] covered, long[] eligible, Lifecycle lifecycle, Scratch scratch) {
			this.state = state; this.snapshots = snapshots; this.covered = covered; this.eligible = eligible; this.lifecycle = lifecycle; this.scratch = scratch;
		}
		public void run() {
			if(!started.compareAndSet(false, true)) return;
			Result output = null;
			long startedAt = System.nanoTime();
			long cpuStart = cpuTime();
			try { output = new Result(state, lifecycle, snapshots, covered, eligible); calculate(output); }
			catch(Throwable failed) { failure = failed; }
			finally {
				if(output != null) { output.calculationNanos = System.nanoTime() - startedAt; output.cpuNanos = cpuStart < 0 ? -1 : cpuTime() - cpuStart; }
				result = output; // Volatile publication includes all primitive state and masks.
				if(cancelled || lifecycle.cancelled) dispose();
			}
		}
		private void calculate(Result output) {
			State s = state;
			for(int operations = 0; operations < MAX_BATCH_STEPS; operations++) {
				output.operations++;
				if(operations % CANCELLATION_INTERVAL == 0 && (cancelled || lifecycle.cancelled || Thread.currentThread().isInterrupted())) return;
				if(s.gspNum > s.gspNumMax || output.completedRays >= s.rayLimit) return;
				if(!s.active) {
					s.dx = Math.sin(s.gx) * Math.cos(s.gy); s.dz = Math.sin(s.gx) * Math.sin(s.gy); s.dy = Math.cos(s.gx);
					s.step = 0; s.strength = s.initialStrength; s.hasTip = false; s.active = true; s.finishing = s.recorded = false;
					prefix = prefixState; prefix.reset();
					for(int i = scratch.prefixes.length - 1; i >= 0; i--) {
						Prefix saved = scratch.prefixes[i];
						if(saved == null || !saved.fits(s) || !prefixAvailable(saved, output)) continue;
						prefix.copyFrom(saved); s.step = saved.step; s.strength = saved.strength; s.finishing = saved.finishing;
						if(saved.tipStep >= 0) {
							s.tx = (float)(s.posX + s.dx * saved.tipStep); s.ty = (float)(s.posY + s.dy * saved.tipStep); s.tz = (float)(s.posZ + s.dz * saved.tipStep); s.hasTip = true;
						}
						for(int q = 0; q < saved.qualifiedCount; q++) output.qualified.add(saved.qualified[q]);
						output.reusedSamples += s.step; break;
					}
				}
				if(s.finishing && !s.building) {
					if(s.hasTip && !s.recorded) {
						// Float subtraction and float-rounded sqrt match Vec3 in 1.7.10.
						double dx = s.tx - s.posX, dy = s.ty - s.posY, dz = s.tz - s.posZ;
						s.buildLength = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
						s.bx = dx / s.buildLength; s.by = dy / s.buildLength; s.bz = dz / s.buildLength;
						s.tipX = (int)Math.floor(s.tx); s.tipY = (int)Math.floor(s.ty); s.tipZ = (int)Math.floor(s.tz);
						s.buildStep = 0; s.building = true;
					} else {
						if(s.gspNum < s.gspNumMax) {
							int k = s.gspNum + 1;
							double hk = -1.0 + 2.0 * (k - 1.0) / (s.gspNumMax - 1.0);
							s.gx = Math.acos(hk);
							// The pole has no longitude; keep its Cartesian direction finite.
							s.gy = hk >= 1D ? 0D : (s.gy + 3.6 / Math.sqrt(s.gspNumMax) / Math.sqrt(1.0 - hk * hk)) % (Math.PI * 2);
						} else { s.gx = 0.0; s.gy = 0.0; }
						s.gspNum++; output.completedRays++;
						s.active = s.finishing = s.recorded = s.hasTip = false;
						output.qualified.clear();
						continue;
					}
				}
				if(s.building) {
					if(!(s.buildStep < s.buildLength)) {
						// Include the last positive sample, including a source-only hit.
						if(s.tipY >= 0 && s.tipY < 256) {
							if(!available(output, s.tipX, s.tipY, s.tipZ)) return;
							int tip = bit(s.tipX, s.tipY, s.tipZ);
							if(!snapshot.air(tip) && !snapshot.liquid(tip)) { mask.record(tip); mask.tips.set(tip); }
						}
						s.building = false; s.recorded = true; continue;
					}
					int x = (int)Math.floor(s.posX + s.bx * s.buildStep), y = (int)Math.floor(s.posY + s.by * s.buildStep), z = (int)Math.floor(s.posZ + s.bz * s.buildStep);
					output.reconstructionOperations++;
					long chunk = chunkKey(x >> 4, z >> 4);
					int entry = Math.max(0, Math.min(Math.abs(s.posX - (x >> 4 << 4)), Math.abs(s.posZ - (z >> 4 << 4))) - 16);
					if(!output.qualified.contains(chunk) || s.buildStep < entry) { s.buildStep++; continue; }
					if(y >= 0 && y < 256 && x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
						if(!available(output, x, y, z)) return;
						// Only reconstruction may skip previously represented solids. Ray
						// resistance sampling above/below never consults destruction masks.
						if(mask.remaining.isEmpty()) {
							int low = s.buildStep + 1, high = (int)Math.ceil(s.buildLength);
							while(low < high) {
								int middle = low + (high - low) / 2;
								if(snapshot.contains((int)Math.floor(s.posX + s.bx * middle), (int)Math.floor(s.posY + s.by * middle), (int)Math.floor(s.posZ + s.bz * middle))) low = middle + 1;
								else high = middle;
							}
							// Tip priority is independent of normal destruction coverage.
							if(snapshot.contains(s.tipX, s.tipY, s.tipZ)) {
								int tip = bit(s.tipX, s.tipY, s.tipZ);
								if(!snapshot.air(tip) && !snapshot.liquid(tip)) { mask.record(tip); mask.tips.set(tip); }
							}
							output.skippedReconstruction += low - s.buildStep; s.buildStep = low; continue;
						}
						int bit = bit(x, y, z);
						if(!snapshot.air(bit) && !snapshot.liquid(bit)) {
							mask.record(bit);
							if(x == s.tipX && y == s.tipY && z == s.tipZ) mask.tips.set(bit);
						}
					}
					s.buildStep++;
					continue;
				}
				if(s.finishing) continue;
				if(s.step >= s.rayLength || s.step > s.length) { s.finishing = true; continue; }
				float x = (float)(s.posX + s.dx * s.step), y = (float)(s.posY + s.dy * s.step), z = (float)(s.posZ + s.dz * s.step);
				if(s.contained && y >= s.surfaceY || y < 0 && (s.posY >= 0 || s.dy <= 0) || y >= 256 && (s.posY < 256 || s.dy >= 0)) { s.finishing = true; continue; }
				int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
				output.traversalOperations++;
				boolean stored = iy >= 0 && iy < 256 && ix >= -30000000 && iz >= -30000000 && ix < 30000000 && iz < 30000000;
				if(stored && !available(output, ix, iy, iz)) return;
				if(stored && snapshot.isZeroAir()) {
					// Stage 3's binary search over the original integer samples, not DDA.
					int low = s.step + 1, high = Math.min(s.rayLength, Math.max(1, s.length));
					while(low < high) {
						int middle = low + (high - low) / 2;
						float xx = (float)(s.posX + s.dx * middle), yy = (float)(s.posY + s.dy * middle), zz = (float)(s.posZ + s.dz * middle);
						if(snapshot.contains((int)Math.floor(xx), (int)Math.floor(yy), (int)Math.floor(zz)) && (!s.contained || yy < s.surfaceY)) low = middle + 1;
						else high = middle;
					}
					if(prefix != null && !prefix.airSection(s, snapshot, low - 1)) prefix = null;
					s.step = low;
					if(s.step >= s.rayLength || s.step >= Math.max(1, s.length)) s.finishing = true;
					rememberPrefix(output);
					continue;
				}
				int bit = bit(ix, iy, iz);
				boolean liquid = stored && snapshot.liquid(bit);
				if(prefix != null && (!stored || !prefix.sample(s, snapshot, ix, iy, iz))) prefix = null;
				if(!liquid) s.strength -= scratch.attenuation(stored ? snapshot.resistance(bit) : s.airResistance, s.step, s.rayLength, output);
				if(s.strength > 0 && stored && !snapshot.vanillaAir(bit) && !liquid) {
					s.tx = x; s.ty = y; s.tz = z; s.hasTip = true; output.qualified.add(chunkKey(ix >> 4, iz >> 4));
					if(prefix != null) prefix.tipStep = s.step;
				}
				if(s.strength <= 0 || s.step + 1 >= s.length || s.step == s.rayLength - 1) s.finishing = true;
				s.step++;
				if(prefix != null && s.step <= 256 && (s.finishing || s.step == 8 || s.step == 16 || s.step == 32 || s.step == 64 || s.step == 128 || s.step == 256)) {
					rememberPrefix(output);
				}
				if(s.step >= 256) prefix = null;
			}
		}
		private void rememberPrefix(Result output) {
			State s = state;
			if(prefix == null) return;
			if(s.step > 256) { prefix = null; return; }
			prefix.step = s.step; prefix.strength = s.strength; prefix.finishing = s.finishing;
			prefix.qualifiedCount = 0;
			for(long key : output.qualified) prefix.qualified[prefix.qualifiedCount++] = key;
			int slot = Math.max(0, 31 - Integer.numberOfLeadingZeros(s.step) - 3);
			if(scratch.prefixes[slot] == null) scratch.prefixes[slot] = new Prefix();
			scratch.prefixes[slot].copyFrom(prefix);
		}
		private boolean prefixAvailable(Prefix saved, Result output) {
			for(int i = 0; i < saved.sectionCount; i++) {
				if(!output.versions.containsKey(saved.sections[i])) return false;
			}
			for(int i = 0; i < saved.sectionCount; i++) output.versions.get(saved.sections[i]).used = true;
			return true;
		}
		private boolean available(Result result, int x, int y, int z) {
			if(snapshot != null && snapshot.contains(x, y, z)) return true;
			for(Mask item : result.masks) {
				if(item.snapshot.contains(x, y, z)) { snapshot = item.snapshot; mask = item; item.used = true; return true; }
			}
			// Returning merely because a cached section was not in the initial
			// corridor turns a long ray into dozens of server-tick round trips.
			// This lookup performs no world access or capture. Extra retained inputs
			// are bounded separately from the initial window and share its byte cap.
			if(acquired.size() < MAX_BATCH_SNAPSHOTS) {
				Snapshot shared = ExplosionTerrainAccess.acquire(lifecycle.worldLifecycleId, x >> 4, y >> 4, z >> 4);
				if(shared != null) {
					acquired.add(shared); snapshot = shared; mask = new Mask(shared, new BitSet()); mask.used = true;
					result.masks.add(mask); result.versions.put(shared.id, mask); result.sharedSnapshotHits++; return true;
				}
			}
			result.sectionMisses++;
			result.missing = true; result.chunkX = x >> 4; result.sectionY = y >> 4; result.chunkZ = z >> 4; return false;
		}
		void cancel() {
			cancelled = true;
			if(pool != null && pool.remove(this) && started.compareAndSet(false, true)) dispose();
			else if(result != null || failure != null) {
				started.compareAndSet(false, true); dispose();
			}
		}
		void dispose() {
			if(disposed.compareAndSet(false, true)) {
				for(Snapshot item : snapshots) ExplosionTerrainAccess.release(item);
				for(Snapshot item : acquired) ExplosionTerrainAccess.release(item);
				acquired.clear();
				snapshots = null; snapshot = null; mask = null; outstanding.decrementAndGet();
			}
		}
	}
	private static int bit(int x, int y, int z) { return (y & 15) << 8 | (z & 15) << 4 | (x & 15); }
	private static long chunkKey(int x, int z) { return (long)x & 0xffffffffL | ((long)z & 0xffffffffL) << 32; }
	static boolean available(boolean resuming) {
		int limit = resuming ? MAX_OUTSTANDING_BATCHES : MAX_OUTSTANDING_BATCHES - RESUME_BATCH_RESERVE;
		return accepting && outstanding.get() < limit;
	}
	static Batch submit(State state, Snapshot[] snapshots, BitSet[] covered, long[] eligible, Lifecycle lifecycle, Scratch scratch) {
		if(!available(state.active || state.building) || lifecycle.cancelled) return null;
		if(pool == null) {
			int count = BombConfig.explosionWorkerThreads;
			pool = new ThreadPoolExecutor(count, count, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(EXECUTOR_QUEUE_CAPACITY), task -> {
				Thread thread = new Thread(task, "RNT-explosion-" + threadIds.incrementAndGet()); thread.setDaemon(true); return thread;
			}, new ThreadPoolExecutor.AbortPolicy());
		}
		Batch batch = new Batch(state, snapshots, covered, eligible, lifecycle, scratch);
		for(int i = 0; i < snapshots.length; i++) {
			if(!ExplosionTerrainAccess.tryRetain(snapshots[i])) {
				// Server-side preparation may have evicted an unpinned input while
				// making room for a continuation. Never resurrect its byte charge.
				for(int j = 0; j < i; j++) ExplosionTerrainAccess.release(snapshots[j]);
				return null;
			}
		}
		outstanding.incrementAndGet();
		try { pool.execute(batch); return batch; }
		catch(java.util.concurrent.RejectedExecutionException full) { batch.dispose(); return null; }
		catch(Throwable failed) { batch.failure = failed; return batch; }
	}
	static void shutdown() {
		accepting = false;
		if(pool != null) {
			for(Runnable task : pool.shutdownNow()) {
				Batch batch = (Batch)task; batch.cancelled = true;
				if(batch.started.compareAndSet(false, true)) batch.dispose();
			}
			pool = null;
		}
	}
	static void startServer() { accepting = true; }
	private static long cpuTime() { return cpu.isCurrentThreadCpuTimeSupported() && cpu.isThreadCpuTimeEnabled() ? cpu.getCurrentThreadCpuTime() : -1L; }
}
