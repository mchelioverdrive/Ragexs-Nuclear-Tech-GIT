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
		eventSecondsRemaining = Math.max(0, nbt.getInteger("eventSecondsRemaining"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setBoolean("geminiMarsVisited", geminiMarsVisited);
		nbt.setInteger("eventSecondsRemaining", eventSecondsRemaining);
	}
}
