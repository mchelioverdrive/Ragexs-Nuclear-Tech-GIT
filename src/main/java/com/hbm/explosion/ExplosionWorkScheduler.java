package com.hbm.explosion;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import com.hbm.config.BombConfig;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Transient, server-thread-only round robin. Entities enroll from their update paths. */
public final class ExplosionWorkScheduler {

	public interface Job {
		Entity owner();
		long activeTick();
		boolean ready(long tick);
		void run(long deadline, long tick);
		default void suspend() { }
	}

	private static final LinkedList<Job> queue = new LinkedList<Job>();
	private static final Set<Job> registered = new HashSet<Job>();
	private static long tick;
	private static long lastRun = Long.MIN_VALUE;
	private static final long SLICE_NANOS = 1_000_000L;

	private ExplosionWorkScheduler() { }
	public static long currentTick() { return tick; }
	public static void beginTick() { tick++; }
	public static boolean before(long deadline) { return deadline == Long.MAX_VALUE || System.nanoTime() - deadline < 0L; }

	public static void register(Job job) {
		if(!job.owner().worldObj.isRemote && !job.owner().isDead && registered.add(job)) queue.addLast(job);
	}

	public static void remove(Job job) {
		if(registered.remove(job)) queue.remove(job);
	}

	public static void remove(Entity entity) {
		Iterator<Job> iterator = queue.iterator();
		while(iterator.hasNext()) {
			Job job = iterator.next();
			if(job.owner() == entity) { job.suspend(); iterator.remove(); registered.remove(job); }
		}
		// The currently dispatched job is temporarily outside the queue.
		registered.removeIf(job -> { if(job.owner() != entity) return false; job.suspend(); return true; });
	}

	public static void runTick() {
		if(lastRun == tick) return;
		lastRun = tick;
		long deadline = System.nanoTime() + Math.max(1, BombConfig.explosionWorkBudgetMs) * 1_000_000L;
		int idle = 0;
		while(!queue.isEmpty() && before(deadline)) {
			ExplosionTerrainAccess.service(deadline);
			if(!before(deadline)) break;
			Job job = queue.removeFirst();
			if(job.owner().isDead || job.activeTick() != tick) {
				job.suspend();
				registered.remove(job); // Unloaded/inactive entities re-enroll on their next update.
				continue;
			}
			if(!job.ready(tick)) {
				queue.addLast(job);
				// Cached validation is useful server work even while every ray
				// worker is running. Drain it under the deadline, never poll workers.
				if(++idle >= queue.size() && !ExplosionTerrainAccess.hasValidationWork()) break;
				continue;
			}
			idle = 0;
			long slice = System.nanoTime() + SLICE_NANOS;
			if(slice - deadline > 0L) slice = deadline;
			try {
				job.run(slice, tick);
			} finally {
				// Rotate even when a single synchronous world operation overruns the tick.
				if(registered.contains(job)) queue.addLast(job);
			}
		}
	}

	public static void unload(World world) {
		ExplosionNukeRayBatched.unloadWorkers(world);
		ExplosionTerrainAccess.unload(world);
		Iterator<Job> iterator = queue.iterator();
		while(iterator.hasNext()) {
			Job job = iterator.next();
			if(job.owner().worldObj == world) { job.suspend(); iterator.remove(); registered.remove(job); }
		}
		registered.removeIf(job -> { if(job.owner().worldObj != world) return false; job.suspend(); return true; });
	}

	public static void clear() {
		for(Job job : registered) job.suspend();
		ExplosionNukeRayBatched.clearJobs();
		ExplosionTerrainAccess.clear();
		queue.clear(); registered.clear(); tick = 0; lastRun = Long.MIN_VALUE;
	}
}
