package com.hbm.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Persists only the next lifetime generation for this world's machine graph. */
final class MachineRuntimeSavedData extends WorldSavedData {

	private static final String DATA_NAME = "hbm_machine_runtime";
	private static final int DATA_VERSION = 1;
	private long nextGeneration = 1L;

	public MachineRuntimeSavedData() {
		super(DATA_NAME);
	}

	public MachineRuntimeSavedData(String name) {
		super(name);
	}

	static MachineRuntimeSavedData get(World world) {
		MachineRuntimeSavedData data = (MachineRuntimeSavedData) world.perWorldStorage.loadData(MachineRuntimeSavedData.class, DATA_NAME);
		if(data == null) {
			data = new MachineRuntimeSavedData();
			world.perWorldStorage.setData(DATA_NAME, data);
			data.markDirty();
		}
		return data;
	}

	long allocateGeneration() {
		long generation = nextGeneration++;
		if(nextGeneration <= 0L) nextGeneration = 1L;
		markDirty();
		return generation;
	}

	void observeGeneration(long generation) {
		if(generation > 0L && generation >= nextGeneration) {
			nextGeneration = generation + 1L;
			if(nextGeneration <= 0L) nextGeneration = 1L;
			markDirty();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		nextGeneration = nbt.getLong("nextGeneration");
		if(nextGeneration <= 0L) nextGeneration = 1L;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setInteger("version", DATA_VERSION);
		nbt.setLong("nextGeneration", nextGeneration);
	}
}
