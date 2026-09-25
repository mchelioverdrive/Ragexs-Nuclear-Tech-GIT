package com.hbm.world.gen.structure;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.hbm.world.generator.TimedGenerator.ITimedJob;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.SaveHandlerMP;

/** In-memory destination for legacy schematic builders; it never asks the real world for blocks. */
public class RecordingStructureWorld extends World {

	private final World realWorld;
	private final int groundY;
	private final Block groundBlock;
	private final boolean underground;
	private final Map<Long, RecordedBlock> placed = new LinkedHashMap<Long, RecordedBlock>();
	private final ArrayDeque<ITimedJob> jobs = new ArrayDeque<ITimedJob>();

	public void queue(ITimedJob job) { jobs.addLast(job); }

	public void runQueuedJobs() {
		while(!jobs.isEmpty()) jobs.removeFirst().work();
	}

	public RecordingStructureWorld(World realWorld, long seed, int groundY, Block groundBlock, boolean underground) {
		super(new SaveHandlerMP(), "RTMStructureRecording", new WorldProviderSurface(), new WorldSettings(realWorld.getWorldInfo()), new Profiler());
		this.realWorld = realWorld;
		this.groundY = groundY;
		this.groundBlock = groundBlock;
		this.underground = underground;
		this.rand.setSeed(seed);
	}

	private static long key(int x, int y, int z) {
		return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
	}

	@Override
	protected IChunkProvider createChunkProvider() {
		return null;
	}

	@Override
	protected int func_152379_p() {
		return 0;
	}

	@Override
	public Entity getEntityByID(int id) {
		return null;
	}

	@Override
	public WorldChunkManager getWorldChunkManager() {
		return realWorld.getWorldChunkManager();
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z) {
		return realWorld.getWorldChunkManager().getBiomeGenAt(x, z);
	}

	@Override
	public int getHeightValue(int x, int z) {
		return groundY;
	}

	@Override
	public Block getBlock(int x, int y, int z) {
		if(y < 0 || y >= 256) return Blocks.air;
		RecordedBlock record = placed.get(key(x, y, z));
		if(record != null) return record.block;
		if(underground) return y < groundY ? Blocks.stone : Blocks.air;
		return y >= groundY ? Blocks.air : y == groundY - 1 ? groundBlock : Blocks.stone;
	}

	@Override
	public int getBlockMetadata(int x, int y, int z) {
		RecordedBlock record = placed.get(key(x, y, z));
		return record == null ? 0 : record.meta;
	}

	@Override
	public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flags) {
		RecordedBlock record = placed.get(key(x, y, z));
		if(record == null) return false;
		record.meta = meta;
		return true;
	}

	@Override
	public void notifyBlocksOfNeighborChange(int x, int y, int z, Block block) {
		// The real placement pass uses the same non-notifying flags as the old builders.
	}

	@Override
	public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {
		// Tile data is captured in snapshot(), not in a backing chunk.
	}

	@Override
	public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
		if(y < 0 || y >= 256) return false;
		RecordedBlock record = new RecordedBlock(x, y, z, block, meta);
		if(block.hasTileEntity(meta)) {
			record.tile = block.createTileEntity(this, meta);
			if(record.tile != null) {
				record.tile.setWorldObj(this);
				record.tile.xCoord = x;
				record.tile.yCoord = y;
				record.tile.zCoord = z;
			}
		}
		long key = key(x, y, z);
		placed.remove(key);
		placed.put(key, record);
		return true;
	}

	@Override
	public TileEntity getTileEntity(int x, int y, int z) {
		RecordedBlock record = placed.get(key(x, y, z));
		return record == null ? null : record.tile;
	}

	@Override
	public void setTileEntity(int x, int y, int z, TileEntity tile) {
		RecordedBlock record = placed.get(key(x, y, z));
		if(record != null) {
			tile.setWorldObj(this);
			tile.xCoord = x;
			tile.yCoord = y;
			tile.zCoord = z;
			record.tile = tile;
		}
	}

	@Override
	public boolean spawnEntityInWorld(Entity entity) {
		throw new IllegalStateException("Legacy structure attempted unsupported entity spawning: " + entity.getClass().getName());
	}

	public List<Placement> snapshot() {
		List<Placement> snapshot = new ArrayList<Placement>(placed.size());
		for(RecordedBlock record : placed.values()) {
			NBTTagCompound nbt = null;
			if(record.tile != null) {
				nbt = new NBTTagCompound();
				record.tile.writeToNBT(nbt);
				nbt.setInteger("x", record.x);
				nbt.setInteger("y", record.y);
				nbt.setInteger("z", record.z);
			}
			snapshot.add(new Placement(record.x, record.y, record.z, record.block, record.meta, nbt));
		}
		return snapshot;
	}

	public static void placeSlice(World world, StructureBoundingBox slice, List<Placement> blocks) {
		for(Placement record : blocks) {
			if(!slice.isVecInside(record.x, record.y, record.z)) continue;
			world.setBlock(record.x, record.y, record.z, record.block, record.meta, 2);
			if(record.tile != null) {
				TileEntity tile = TileEntity.createAndLoadEntity((NBTTagCompound)record.tile.copy());
				if(tile != null) world.setTileEntity(record.x, record.y, record.z, tile);
			}
		}
	}

	public static Map<Long, List<Placement>> partition(List<Placement> blocks) {
		Map<Long, List<Placement>> slices = new LinkedHashMap<Long, List<Placement>>();
		for(Placement block : blocks) {
			long key = ((long)((block.x - 8) >> 4) << 32) ^ (((block.z - 8) >> 4) & 0xFFFFFFFFL);
			List<Placement> slice = slices.get(key);
			if(slice == null) slices.put(key, slice = new ArrayList<Placement>());
			slice.add(block);
		}
		return slices;
	}

	public static void placeSlice(World world, StructureBoundingBox slice, Map<Long, List<Placement>> blocks) {
		long key = ((long)((slice.minX - 8) >> 4) << 32) ^ (((slice.minZ - 8) >> 4) & 0xFFFFFFFFL);
		List<Placement> selected = blocks.get(key);
		if(selected != null) placeSlice(world, slice, selected);
	}

	public static class Placement {
		final int x;
		final int y;
		final int z;
		final Block block;
		final int meta;
		final NBTTagCompound tile;

		Placement(int x, int y, int z, Block block, int meta, NBTTagCompound tile) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.block = block;
			this.meta = meta;
			this.tile = tile;
		}
	}

	private static class RecordedBlock {
		final int x;
		final int y;
		final int z;
		final Block block;
		int meta;
		TileEntity tile;

		RecordedBlock(int x, int y, int z, Block block, int meta) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.block = block;
			this.meta = meta;
		}
	}
}
