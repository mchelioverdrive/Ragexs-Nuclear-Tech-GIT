package com.hbm.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.world.World;

/** One server-thread-owned logical machine graph and scheduler for one world. */
public final class MachineRuntime {

	private final World world;
	private MachineRuntimeSavedData savedData;
	private final Map<MachineKey, MachineEntry> entries = new HashMap<MachineKey, MachineEntry>();
	private final Map<PositionKey, MachineEntry> entriesByPosition = new HashMap<PositionKey, MachineEntry>();
	private ArrayDeque<MachineEntry> dirtyQueue = new ArrayDeque<MachineEntry>();
	private ArrayDeque<MachineEntry> dirtyExecution = new ArrayDeque<MachineEntry>();
	private final PriorityQueue<ScheduledTransition> scheduled = new PriorityQueue<ScheduledTransition>(11, new ScheduledComparator());
	private final CoarseBucket coarse5 = new CoarseBucket(5);
	private final CoarseBucket coarse20 = new CoarseBucket(20);
	private final CoarseBucket coarse100 = new CoarseBucket(100);
	private final MachineRuntimeDiagnostics diagnostics = new MachineRuntimeDiagnostics();
	private long scheduleSequence;
	private boolean unloaded;

	MachineRuntime(World world) {
		this.world = world;
	}

	public World getWorld() {
		return world;
	}

	void bind(TileEntityLoadedBase tile) {
		if(unloaded || tile == null || tile.getWorldObj() != world || tile.getMachineExecutionStrategies() == MachineExecutionStrategy.LEGACY) return;

		long generation = tile.getMachineLifecycleGeneration();
		if(generation <= 0L) {
			generation = savedData().allocateGeneration();
			tile.adoptMachineLifecycleGeneration(generation);
		} else {
			savedData().observeGeneration(generation);
		}

		String type = tile.getMachineRuntimeType();
		PositionKey position = new PositionKey(tile.xCoord, tile.yCoord, tile.zCoord);
		MachineEntry entry = entriesByPosition.get(position);
		if(entry != null && entry.binding != null && entry.binding != tile) {
			removeEntry(entry);
			generation = savedData().allocateGeneration();
			tile.adoptMachineLifecycleGeneration(generation);
			entry = null;
		}
		if(entry != null && (entry.key.generation != generation || !entry.type.equals(type))) {
			removeEntry(entry);
			entry = null;
		}

		MachineKey key = new MachineKey(world.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, generation);
		if(entry == null) entry = entries.get(key);
		if(entry == null) {
			entry = new MachineEntry(key, position, type, tile.getMachineExecutionStrategies());
			entries.put(key, entry);
			entriesByPosition.put(position, entry);
			registerCoarse(entry);
			diagnostics.registration();
		} else if(entry.binding == null) {
			diagnostics.reloadRebinding();
		}

		if(entry.binding != null && entry.binding != tile) entry.binding.clearMachineRuntimeBinding();
		entry.binding = tile;
		tile.setMachineRuntimeBinding(key);
		diagnostics.bind();

		if((entry.strategies & MachineExecutionStrategy.EVENT_DRIVEN) != 0) {
			entry.dirtyCauses |= MachineDirtyCause.LIFECYCLE;
			if(!entry.dirtyQueued) enqueueDirty(entry);
			diagnostics.dirtySignal(dirtyQueue.size());
		}
		if(entry.dirtyCauses != 0 && !entry.dirtyQueued) enqueueDirty(entry);
		for(ScheduledTransition transition : entry.schedules.values()) {
			if(!transition.queued && !transition.cancelled) {
				transition.queued = true;
				scheduled.add(transition);
			}
		}
	}

	void unbind(TileEntityLoadedBase tile) {
		MachineEntry entry = resolve(tile);
		if(entry == null || entry.binding != tile) return;
		entry.binding = null;
		tile.clearMachineRuntimeBinding();
		diagnostics.unbind();
	}

	void remove(TileEntityLoadedBase tile) {
		MachineEntry entry = resolve(tile);
		if(entry != null && (entry.binding == null || entry.binding == tile)) removeEntry(entry);
		tile.clearMachineRuntimeBinding();
	}

	public void markDirty(TileEntityLoadedBase tile, int causes) {
		if(causes == 0) return;
		MachineEntry entry = resolve(tile);
		if(entry == null || entry.removed) return;
		entry.dirtyCauses |= causes;
		if(entry.binding != null && !entry.dirtyQueued) enqueueDirty(entry);
		diagnostics.dirtySignal(dirtyQueue.size());
	}

	public boolean schedule(TileEntityLoadedBase tile, long dueTick, int taskType, int taskSlot) {
		MachineEntry entry = resolve(tile);
		if(entry == null || entry.removed || (entry.strategies & MachineExecutionStrategy.SCHEDULED) == 0) return false;
		long owner = scheduleOwner(taskType, taskSlot);
		ScheduledTransition old = entry.schedules.remove(owner);
		if(old != null) cancelTransition(old);
		ScheduledTransition transition = new ScheduledTransition(entry.key, entry.type, dueTick, taskType, taskSlot, scheduleSequence++);
		entry.schedules.put(owner, transition);
		if(entry.binding != null) {
			transition.queued = true;
			scheduled.add(transition);
		}
		diagnostics.scheduledCreated();
		return true;
	}

	public boolean cancel(TileEntityLoadedBase tile, int taskType, int taskSlot) {
		MachineEntry entry = resolve(tile);
		if(entry == null) return false;
		ScheduledTransition transition = entry.schedules.remove(scheduleOwner(taskType, taskSlot));
		if(transition == null) return false;
		cancelTransition(transition);
		return true;
	}

	void tick() {
		if(unloaded) return;
		long now = world.getTotalWorldTime();
		processScheduled(now);
		processDirty();
		coarse5.poll(now, diagnostics);
		coarse20.poll(now, diagnostics);
		coarse100.poll(now, diagnostics);
	}

	void unload() {
		if(unloaded) return;
		unloaded = true;
		for(MachineEntry entry : entries.values()) {
			if(entry.binding != null) entry.binding.clearMachineRuntimeBinding();
			entry.binding = null;
			entry.removed = true;
			for(ScheduledTransition transition : entry.schedules.values()) transition.cancelled = true;
			entry.schedules.clear();
		}
		entries.clear();
		entriesByPosition.clear();
		dirtyQueue.clear();
		dirtyExecution.clear();
		scheduled.clear();
		coarse5.clear();
		coarse20.clear();
		coarse100.clear();
	}

	String[] getReport() {
		if(!MachineRuntimeDiagnostics.isEnabled()) return new String[] {
			"Machine runtime diagnostics are disabled.",
			"Set 1.46_enableMachineRuntimeDiagnostics=true in hbm.cfg and restart the server."
		};

		int loaded = 0;
		int eventDriven = 0;
		int scheduledMachines = 0;
		int coarse = 0;
		int realtime = 0;
		int activeSchedules = 0;
		for(MachineEntry entry : entries.values()) {
			if(entry.binding != null) loaded++;
			if((entry.strategies & MachineExecutionStrategy.EVENT_DRIVEN) != 0) eventDriven++;
			if((entry.strategies & MachineExecutionStrategy.SCHEDULED) != 0) scheduledMachines++;
			if((entry.strategies & MachineExecutionStrategy.COARSE_MASK) != 0) coarse++;
			if((entry.strategies & MachineExecutionStrategy.REALTIME) != 0) realtime++;
			activeSchedules += entry.schedules.size();
		}
		return new String[] {
			"Machine runtime dimension " + world.provider.dimensionId + ":",
			"logical/loaded/unloaded = " + entries.size() + "/" + loaded + "/" + (entries.size() - loaded),
			"event-driven/scheduled/coarse/realtime = " + eventDriven + "/" + scheduledMachines + "/" + coarse + "/" + realtime,
			"dirty queued/signals/processed/max depth = " + dirtyQueue.size() + "/" + diagnostics.dirtySignals + "/" + diagnostics.dirtyProcessed + "/" + diagnostics.maxDirtyDepth,
			"scheduled active/created/executed/cancelled/stale = " + activeSchedules + "/" + diagnostics.scheduledCreated + "/" + diagnostics.scheduledExecuted + "/" + diagnostics.scheduledCancelled + "/" + diagnostics.staleRejected,
			"binds/unbinds/rebindings/removals/registrations = " + diagnostics.binds + "/" + diagnostics.unbinds + "/" + diagnostics.reloadRebindings + "/" + diagnostics.removals + "/" + diagnostics.registrations,
			"coarse polls executed = " + diagnostics.coarsePolls
		};
	}

	private void processScheduled(long now) {
		while(!scheduled.isEmpty() && scheduled.peek().dueTick <= now) {
			ScheduledTransition transition = scheduled.poll();
			transition.queued = false;
			if(transition.cancelled) continue;
			MachineEntry entry = entries.get(transition.key);
			if(entry == null || entry.removed || !entry.type.equals(transition.type)) {
				diagnostics.staleRejected();
				continue;
			}
			long owner = scheduleOwner(transition.taskType, transition.taskSlot);
			if(entry.schedules.get(owner) != transition) continue;
			if(entry.binding == null || !isBindingUsable(entry.binding)) continue;
			entry.schedules.remove(owner);
			diagnostics.scheduledExecuted();
			entry.binding.onMachineScheduledTransition(transition.taskType, transition.taskSlot, transition.dueTick);
		}
	}

	private void processDirty() {
		ArrayDeque<MachineEntry> swap = dirtyExecution;
		dirtyExecution = dirtyQueue;
		dirtyQueue = swap;
		dirtyQueue.clear();
		for(MachineEntry queued : dirtyExecution) {
			queued.dirtyQueued = false;
			queued.executingCauses = queued.dirtyCauses;
			queued.dirtyCauses = 0;
		}

		MachineEntry entry;
		while((entry = dirtyExecution.poll()) != null) {
			int causes = entry.executingCauses;
			entry.executingCauses = 0;
			if(entry.removed || causes == 0) continue;
			if(entry.binding == null || !isBindingUsable(entry.binding)) {
				entry.dirtyCauses |= causes;
				continue;
			}
			diagnostics.dirtyProcessed();
			entry.binding.onMachineRuntimeDirty(causes);
		}
		dirtyExecution.clear();
	}

	private boolean isBindingUsable(TileEntityLoadedBase tile) {
		return tile != null && tile.getWorldObj() == world && !tile.isInvalid() && tile.isLoaded();
	}

	private void enqueueDirty(MachineEntry entry) {
		entry.dirtyQueued = true;
		dirtyQueue.add(entry);
	}

	private MachineEntry resolve(TileEntityLoadedBase tile) {
		if(tile == null || tile.getWorldObj() != world) return null;
		MachineKey key = tile.getMachineRuntimeBinding();
		if(key != null) return entries.get(key);
		long generation = tile.getMachineLifecycleGeneration();
		if(generation <= 0L) return null;
		return entries.get(new MachineKey(world.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, generation));
	}

	private void removeEntry(MachineEntry entry) {
		if(entry.removed) return;
		entry.removed = true;
		entries.remove(entry.key);
		entriesByPosition.remove(entry.position);
		if(entry.binding != null) entry.binding.clearMachineRuntimeBinding();
		entry.binding = null;
		entry.dirtyCauses = 0;
		unregisterCoarse(entry);
		for(ScheduledTransition transition : entry.schedules.values()) cancelTransition(transition);
		entry.schedules.clear();
		diagnostics.removal();
	}

	private void cancelTransition(ScheduledTransition transition) {
		if(transition.cancelled) return;
		transition.cancelled = true;
		diagnostics.scheduledCancelled();
	}

	private void registerCoarse(MachineEntry entry) {
		if((entry.strategies & MachineExecutionStrategy.COARSE_5) != 0) coarse5.add(entry);
		if((entry.strategies & MachineExecutionStrategy.COARSE_20) != 0) coarse20.add(entry);
		if((entry.strategies & MachineExecutionStrategy.COARSE_100) != 0) coarse100.add(entry);
	}

	private void unregisterCoarse(MachineEntry entry) {
		coarse5.remove(entry);
		coarse20.remove(entry);
		coarse100.remove(entry);
	}

	private static long scheduleOwner(int taskType, int taskSlot) {
		return (long) taskType << 32 | taskSlot & 0xFFFFFFFFL;
	}

	private MachineRuntimeSavedData savedData() {
		if(savedData == null) savedData = MachineRuntimeSavedData.get(world);
		return savedData;
	}

	private static final class MachineEntry {
		final MachineKey key;
		final PositionKey position;
		final String type;
		final int strategies;
		final Map<Long, ScheduledTransition> schedules = new HashMap<Long, ScheduledTransition>();
		TileEntityLoadedBase binding;
		int dirtyCauses;
		int executingCauses;
		boolean dirtyQueued;
		boolean removed;

		MachineEntry(MachineKey key, PositionKey position, String type, int strategies) {
			this.key = key;
			this.position = position;
			this.type = type;
			this.strategies = strategies;
		}
	}

	private static final class PositionKey {
		final int x;
		final int y;
		final int z;

		PositionKey(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public int hashCode() {
			int result = x;
			result = 31 * result + y;
			return 31 * result + z;
		}

		@Override
		public boolean equals(Object object) {
			if(this == object) return true;
			if(!(object instanceof PositionKey)) return false;
			PositionKey other = (PositionKey) object;
			return x == other.x && y == other.y && z == other.z;
		}
	}

	private static final class ScheduledTransition {
		final MachineKey key;
		final String type;
		final long dueTick;
		final int taskType;
		final int taskSlot;
		final long sequence;
		boolean queued;
		boolean cancelled;

		ScheduledTransition(MachineKey key, String type, long dueTick, int taskType, int taskSlot, long sequence) {
			this.key = key;
			this.type = type;
			this.dueTick = dueTick;
			this.taskType = taskType;
			this.taskSlot = taskSlot;
			this.sequence = sequence;
		}
	}

	private static final class ScheduledComparator implements Comparator<ScheduledTransition> {
		@Override
		public int compare(ScheduledTransition left, ScheduledTransition right) {
			if(left.dueTick < right.dueTick) return -1;
			if(left.dueTick > right.dueTick) return 1;
			return left.sequence < right.sequence ? -1 : left.sequence == right.sequence ? 0 : 1;
		}
	}

	private static final class CoarseBucket {
		final int cadence;
		final List<MachineEntry>[] slots;
		final List<MachineEntry> execution = new ArrayList<MachineEntry>();

		@SuppressWarnings("unchecked")
		CoarseBucket(int cadence) {
			this.cadence = cadence;
			this.slots = new List[cadence];
			for(int i = 0; i < cadence; i++) slots[i] = new ArrayList<MachineEntry>();
		}

		void add(MachineEntry entry) {
			slots[spread(entry.key, cadence)].add(entry);
		}

		void remove(MachineEntry entry) {
			slots[spread(entry.key, cadence)].remove(entry);
		}

		void poll(long worldTick, MachineRuntimeDiagnostics diagnostics) {
			List<MachineEntry> active = slots[(int) Math.floorMod(worldTick, cadence)];
			execution.clear();
			execution.addAll(active);
			for(int i = 0; i < execution.size(); i++) {
				MachineEntry entry = execution.get(i);
				if(entry.removed || entry.binding == null || entry.binding.isInvalid() || !entry.binding.isLoaded()) continue;
				diagnostics.coarsePoll();
				entry.binding.onMachineCoarsePoll(cadence);
			}
			execution.clear();
		}

		void clear() {
			for(List<MachineEntry> slot : slots) slot.clear();
			execution.clear();
		}

		private static int spread(MachineKey key, int cadence) {
			int hash = key.hashCode();
			hash ^= hash >>> 16;
			return Math.floorMod(hash, cadence);
		}
	}
}
