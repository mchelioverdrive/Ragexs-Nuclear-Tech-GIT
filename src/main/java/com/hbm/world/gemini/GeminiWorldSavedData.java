package com.hbm.world.gemini;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** One record in the Overworld's save, shared by every player and dimension. */
public final class GeminiWorldSavedData extends WorldSavedData {

	private static final String DATA_NAME = "GeminiInvasion";
	private boolean geminiMarsVisited;
	private int eventSecondsRemaining;
	private int phase;
	private long phaseTicks;
	private long elapsedTicks;
	private boolean debug;
    final GeminiIris iris = new GeminiIris();
    final GeminiLights lights = new GeminiLights();
	final java.util.Map<String, Long> debugMessages = new java.util.HashMap<String, Long>();

	public GeminiWorldSavedData(String name) {
		super(name);
	}

	static GeminiWorldSavedData get(World world) {
		if(!(world instanceof WorldServer) || world.isRemote) return null;
		WorldServer overworld = DimensionManager.getWorld(0);
		if(overworld == null) return null;

		GeminiWorldSavedData data = (GeminiWorldSavedData) overworld.mapStorage.loadData(GeminiWorldSavedData.class, DATA_NAME);
		if(data == null) {
			data = new GeminiWorldSavedData(DATA_NAME);
			overworld.mapStorage.setData(DATA_NAME, data);
		}
		return data;
	}

	boolean hasMarsVisited() {
		return geminiMarsVisited;
	}

	void setMarsVisited(boolean visited) {
		if(geminiMarsVisited == visited) return;
		geminiMarsVisited = visited;
		markDirty();
	}

	/** Phase 0 denotes a world whose invasion has never initialized. */
	boolean initialize() {
		if(phase != 0) return false;
		phase = 1;
		phaseTicks = 0;
		markDirty();
		return true;
	}

	int getPhase() { return phase; }
	long getElapsedTicks() { return elapsedTicks; }
	long getRemainingTicks() { return getPhase() < 1 || phase >= 4 ? -1 : Math.max(0L, phaseDuration(phase) - phaseTicks); }
	boolean isDebug() { return debug; }
	void setDebug(boolean value) { debug = value; debugMessages.clear(); markDirty(); }

	static long phaseDuration(int phase) {
		int days = phase == 1 ? com.hbm.config.GeminiConfig.phase2DelayDays
				: phase == 2 ? com.hbm.config.GeminiConfig.phase3DelayDays : com.hbm.config.GeminiConfig.phase4DelayDays;
		return Math.max(1L, days) * 24000L;
	}

	static long phaseStart(int phase) {
		long ticks = 0;
		for(int p = 1; p < phase; p++) ticks += phaseDuration(p);
		return ticks;
	}

	/** No event replay: at most three ordered unlock transitions, regardless of the jump. */
	void advanceTicks(World world, long ticks) {
		elapsedTicks += Math.min(ticks, Long.MAX_VALUE - elapsedTicks);
		while(phase < 4) {
			long remaining = Math.max(0L, phaseDuration(phase) - phaseTicks);
			if(ticks < remaining) { phaseTicks += ticks; break; }
			ticks -= remaining;
			int previous = phase++;
			phaseTicks = 0;
			GeminiInvasionManager.phaseChanged(world, previous, phase);
		}
		markDirty();
	}

	void setPhase(World world, int previous, int target) {
		if(previous == target) return;
		phase = Math.max(1, Math.min(phase, target));
		if(previous != phase) GeminiInvasionManager.phaseChanged(world, previous, phase);
		while(phase < target) { int from = phase++; GeminiInvasionManager.phaseChanged(world, from, phase); }
		phaseTicks = 0;
		elapsedTicks = phaseStart(target);
		eventSecondsRemaining = 0;
		markDirty();
	}

	void setElapsedTicks(World world, long ticks) {
		int previous = phase;
		int target = 1;
		elapsedTicks = ticks;
		while(target < 4 && ticks >= phaseDuration(target)) { ticks -= phaseDuration(target); target++; }
		if(target < phase) { phase = target; GeminiInvasionManager.phaseChanged(world, previous, phase); }
		while(phase < target) { int from = phase++; GeminiInvasionManager.phaseChanged(world, from, phase); }
		phaseTicks = phase == 4 ? 0 : ticks;
		if(target < previous) eventSecondsRemaining = 0;
		markDirty();
	}

	void reset(World world, int previous) {
		phase = previous > 0 ? 1 : 0;
		phaseTicks = elapsedTicks = 0;
		eventSecondsRemaining = 0;
		if(previous != getPhase()) GeminiInvasionManager.phaseChanged(world, previous, getPhase());
		markDirty();
	}

	/** Loaded, enabled Overworld time only; never infer elapsed time from /time or wall clocks. */
	void advancePhase(World world) {
		// Preserve the natural one-transition/full-delay rule, including after config reload.
		elapsedTicks += Math.min(20L, Long.MAX_VALUE - elapsedTicks);
		if(phase < 4 && (phaseTicks += 20L) >= phaseDuration(phase)) {
			int previous = phase++;
			phaseTicks = 0;
			GeminiInvasionManager.phaseChanged(world, previous, phase);
		}
		markDirty();
	}

	int getEventSecondsRemaining() {
		return eventSecondsRemaining;
	}

	void setEventSecondsRemaining(int seconds) {
		eventSecondsRemaining = seconds;
		markDirty();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		geminiMarsVisited = nbt.getBoolean("geminiMarsVisited");
		// Saves from before phases existed only recorded the Mars milestone.
		phase = nbt.hasKey("phase") ? Math.max(0, Math.min(4, nbt.getInteger("phase"))) : geminiMarsVisited ? 1 : 0;
		phaseTicks = Math.max(0L, Math.min(24000L * 10000L, nbt.getLong("phaseTicks")));
		eventSecondsRemaining = Math.max(0, nbt.getInteger("eventSecondsRemaining"));
		// Older saves retain their exact current phase and phase-local progress. Past custom
		// durations cannot be reconstructed; use today's configured delays for that prefix.
		elapsedTicks = nbt.hasKey("elapsedTicks") ? Math.max(0L, nbt.getLong("elapsedTicks"))
				: phaseStart(Math.max(1, phase)) + phaseTicks;
		debug = nbt.getBoolean("debug");
        iris.read(nbt.getCompoundTag("iris"));
		debugMessages.clear();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setBoolean("geminiMarsVisited", geminiMarsVisited);
		nbt.setInteger("phase", phase);
		nbt.setLong("phaseTicks", phaseTicks);
		nbt.setInteger("eventSecondsRemaining", eventSecondsRemaining);
		nbt.setLong("elapsedTicks", elapsedTicks);
		nbt.setBoolean("debug", debug);
        nbt.setTag("iris", iris.write());
	}
}
