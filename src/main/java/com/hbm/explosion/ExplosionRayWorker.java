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
	static final int MAX_BATCH_RAYS = 256;
	static final int MAX_BATCH_SNAPSHOTS = 64;
	static final int MAX_JOB_BATCHES = 4;
	static final int MAX_OUTSTANDING_BATCHES = 64;
	static final int RESUME_BATCH_RESERVE = 16;
	static final int EXECUTOR_QUEUE_CAPACITY = 64;
	static final int CANCELLATION_INTERVAL = 32;
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
		final Mask[] masks;
		boolean missing;
		int chunkX, sectionY, chunkZ;
		int completedRays;
		int operations, sectionMisses;
		long calculationNanos;
		final java.util.HashSet<Long> qualified;
		Result(State state, Lifecycle lifecycle, Snapshot[] snapshots, long[] eligible) {
			this.state = state; jobId = lifecycle.jobId; lifecycleId = lifecycle.id; worldLifecycleId = lifecycle.worldLifecycleId;
			masks = new Mask[snapshots.length];
			for(int i = 0; i < masks.length; i++) masks[i] = new Mask(snapshots[i]);
			qualified = new java.util.HashSet<Long>();
			for(long key : eligible) qualified.add(key);
		}
	}
	static final class Mask {
		final Snapshot snapshot;
		final BitSet destruction = new BitSet(), tips = new BitSet();
		boolean used;
		Mask(Snapshot snapshot) { this.snapshot = snapshot; }
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
		private Snapshot snapshot;
		private Mask mask;
		Batch(State state, Snapshot[] snapshots, long[] eligible, Lifecycle lifecycle) {
			this.state = state; this.snapshots = snapshots; this.eligible = eligible; this.lifecycle = lifecycle;
		}
		public void run() {
			if(!started.compareAndSet(false, true)) return;
			Result output = null;
			long startedAt = System.nanoTime();
			try { output = new Result(state, lifecycle, snapshots, eligible); calculate(output); }
			catch(Throwable failed) { failure = failed; }
			finally {
				if(output != null) output.calculationNanos = System.nanoTime() - startedAt;
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
							if(!snapshot.air(tip) && !snapshot.liquid(tip)) { mask.destruction.set(tip); mask.tips.set(tip); }
						}
						s.building = false; s.recorded = true; continue;
					}
					int x = (int)Math.floor(s.posX + s.bx * s.buildStep), y = (int)Math.floor(s.posY + s.by * s.buildStep), z = (int)Math.floor(s.posZ + s.bz * s.buildStep);
					long chunk = chunkKey(x >> 4, z >> 4);
					int entry = Math.max(0, Math.min(Math.abs(s.posX - (x >> 4 << 4)), Math.abs(s.posZ - (z >> 4 << 4))) - 16);
					if(!output.qualified.contains(chunk) || s.buildStep < entry) { s.buildStep++; continue; }
					if(y >= 0 && y < 256 && x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
						if(!available(output, x, y, z)) return;
						int bit = bit(x, y, z);
						if(!snapshot.air(bit) && !snapshot.liquid(bit)) {
							mask.destruction.set(bit);
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
				boolean stored = iy >= 0 && iy < 256 && ix >= -30000000 && iz >= -30000000 && ix < 30000000 && iz < 30000000;
				if(stored && !available(output, ix, iy, iz)) return;
				if(stored && snapshot.zeroAirSection) {
					// Stage 3's binary search over the original integer samples, not DDA.
					int low = s.step + 1, high = Math.min(s.rayLength, Math.max(1, s.length));
					while(low < high) {
						int middle = low + (high - low) / 2;
						float xx = (float)(s.posX + s.dx * middle), yy = (float)(s.posY + s.dy * middle), zz = (float)(s.posZ + s.dz * middle);
						if(snapshot.contains((int)Math.floor(xx), (int)Math.floor(yy), (int)Math.floor(zz)) && (!s.contained || yy < s.surfaceY)) low = middle + 1;
						else high = middle;
					}
					s.step = low;
					if(s.step >= s.rayLength || s.step >= Math.max(1, s.length)) s.finishing = true;
					continue;
				}
				int bit = bit(ix, iy, iz);
				boolean liquid = stored && snapshot.liquid(bit);
				double fac = (100 - (double)s.step / s.rayLength * 100) * .07D;
				if(!liquid) s.strength -= Math.pow(stored ? snapshot.resistance(bit) : s.airResistance, 7.5D - fac);
				if(s.strength > 0 && stored && !snapshot.vanillaAir(bit) && !liquid) {
					s.tx = x; s.ty = y; s.tz = z; s.hasTip = true; output.qualified.add(chunkKey(ix >> 4, iz >> 4));
				}
				if(s.strength <= 0 || s.step + 1 >= s.length || s.step == s.rayLength - 1) s.finishing = true;
				s.step++;
			}
		}
		private boolean available(Result result, int x, int y, int z) {
			if(snapshot != null && snapshot.contains(x, y, z)) return true;
			for(Mask item : result.masks) {
				if(item.snapshot.contains(x, y, z)) { snapshot = item.snapshot; mask = item; item.used = true; return true; }
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
	static Batch submit(State state, Snapshot[] snapshots, long[] eligible, Lifecycle lifecycle) {
		if(!available(state.active || state.building) || lifecycle.cancelled) return null;
		if(pool == null) {
			int count = BombConfig.explosionWorkerThreads;
			pool = new ThreadPoolExecutor(count, count, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(EXECUTOR_QUEUE_CAPACITY), task -> {
				Thread thread = new Thread(task, "RNT-explosion-" + threadIds.incrementAndGet()); thread.setDaemon(true); return thread;
			}, new ThreadPoolExecutor.AbortPolicy());
		}
		Batch batch = new Batch(state, snapshots, eligible, lifecycle);
		for(Snapshot snapshot : snapshots) ExplosionTerrainAccess.retain(snapshot);
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
}
