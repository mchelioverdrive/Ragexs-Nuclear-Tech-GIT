package com.hbm.world.gen.structure;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

/** No backing storage: every recording owns its world info and in-memory map data. */
final class RecordingSaveHandler implements ISaveHandler {

	@Override
	public WorldInfo loadWorldInfo() { return null; }

	@Override
	public void checkSessionLock() { }

	@Override
	public IChunkLoader getChunkLoader(WorldProvider provider) { return null; }

	@Override
	public void saveWorldInfoWithPlayer(WorldInfo info, NBTTagCompound player) { }

	@Override
	public void saveWorldInfo(WorldInfo info) { }

	@Override
	public IPlayerFileData getSaveHandler() { return null; }

	@Override
	public void flush() { }

	@Override
	public File getWorldDirectory() { return null; }

	@Override
	public File getMapFileFromName(String name) { return null; }

	@Override
	public String getWorldDirectoryName() { return "none"; }
}
