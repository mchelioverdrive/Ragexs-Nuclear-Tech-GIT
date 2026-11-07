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

	void recordMarsVisit() {
		if(geminiMarsVisited) return;
		geminiMarsVisited = true;
		phase = 1;
		phaseTicks = 0;
		markDirty();
	}

	int getPhase() { return phase; }

	/** Loaded, enabled Overworld time only; never infer elapsed time from /time or wall clocks. */
	void advancePhase() {
		if(!geminiMarsVisited || phase >= 4) return;
		phaseTicks += 20L;
		int days = phase == 1 ? com.hbm.config.GeminiConfig.phase2DelayDays
				: phase == 2 ? com.hbm.config.GeminiConfig.phase3DelayDays : com.hbm.config.GeminiConfig.phase4DelayDays;
		if(phaseTicks >= Math.max(1L, days) * 24000L) {
			phase++;
			phaseTicks = 0L; // One transition at most; each phase has its own full delay.
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
		// Missing keys in existing worlds mean locked and not yet scheduled.
		geminiMarsVisited = nbt.getBoolean("geminiMarsVisited");
		// Existing Mars-unlocked saves enter phase 1, rather than unlocking every threat.
		phase = geminiMarsVisited ? Math.max(1, Math.min(4, nbt.getInteger("phase"))) : 0;
		phaseTicks = Math.max(0L, Math.min(24000L * 10000L, nbt.getLong("phaseTicks")));
		eventSecondsRemaining = Math.max(0, nbt.getInteger("eventSecondsRemaining"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setBoolean("geminiMarsVisited", geminiMarsVisited);
		nbt.setInteger("phase", phase);
		nbt.setLong("phaseTicks", phaseTicks);
		nbt.setInteger("eventSecondsRemaining", eventSecondsRemaining);
	}
}
