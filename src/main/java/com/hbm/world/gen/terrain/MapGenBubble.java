package com.hbm.world.gen.terrain;

import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.MapGenBase;

/**
 * Generates wide subsurface bubbles into the chunk primer instead of touching
 * neighboring live chunks during population.
 */
public class MapGenBubble extends MapGenBase {

	private final int frequency;
	private int minSize = 8;
	private int maxSize = 64;

	public int minY = 0;
	public int rangeY = 25;

	public boolean fuzzy;
	public boolean surface;
	public Block block;
	public byte meta;
	public Block replace = Blocks.stone;
	public Predicate<BiomeGenBase> canSpawn;

	private byte[] metas;
	private Chunk chunk;
	private boolean chunkChanged;

	public MapGenBubble(int frequency) {
		this.frequency = frequency;
	}

	public void setSize(int minSize, int maxSize) {
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.range = Math.max(1, (maxSize + 15) / 16);
	}

	public void generate(IChunkProvider chunkProvider, World world, int chunkX, int chunkZ, Block[] blocks, byte[] metas) {
		this.metas = metas;
		try {
			func_151539_a(chunkProvider, world, chunkX, chunkZ, blocks);
		} finally {
			this.metas = null;
			this.worldObj = null;
		}
	}

	/** Applies a bubble directly to a newly generated chunk before population. */
	public void generate(IChunkProvider chunkProvider, World world, Chunk chunk) {
		this.chunk = chunk;
		this.chunkChanged = false;
		try {
			func_151539_a(chunkProvider, world, chunk.xPosition, chunk.zPosition, null);
			if(chunkChanged) chunk.setChunkModified();
		} finally {
			this.chunk = null;
			this.worldObj = null;
		}
	}

	@Override
	protected void func_151538_a(World world, int offsetX, int offsetZ, int chunkX, int chunkZ, Block[] blocks) {
		if(frequency <= 0 || rand.nextInt(frequency) != frequency - 1) return;
		if(canSpawn != null && !canSpawn.test(world.getWorldChunkManager().getBiomeGenAt(offsetX * 16, offsetZ * 16))) return;

		int xCoord = (chunkX - offsetX) * 16 - rand.nextInt(16);
		int zCoord = (chunkZ - offsetZ) * 16 - rand.nextInt(16);
		int yCoord = surface ? 0 : rand.nextInt(rangeY) + minY;

		int radius = rand.nextInt(maxSize - minSize) + minSize;
		int radiusSqr = radius * radius / 2;

		for(int bx = 15; bx >= 0; bx--) {
			for(int bz = 15; bz >= 0; bz--) {
				int columnY = surface ? chunk.getHeightValue(bx, bz) : yCoord;
				int yMin = Math.max(1, MathHelper.floor_double(columnY - radius));
				int yMax = Math.min(256, MathHelper.ceiling_double_int(columnY + radius));
				for(int by = yMin; by < yMax; by++) {
					int index = (bx * 16 + bz) * 256 + by;
					if(chunk != null ? chunk.getBlock(bx, by, bz) != replace : blocks[index] != replace) continue;

					int x = xCoord + bx;
					int z = zCoord + bz;
					int y = columnY - by;
					double distanceSqr = x * x + z * z + y * y * 3;
					if(fuzzy) distanceSqr -= rand.nextDouble() * radiusSqr / 3.0D;

					if(distanceSqr < radiusSqr) {
						if(chunk != null) {
							ExtendedBlockStorage storage = chunk.getBlockStorageArray()[by >> 4];
							if(storage == null) storage = chunk.getBlockStorageArray()[by >> 4] = new ExtendedBlockStorage(by & ~15, !chunk.worldObj.provider.hasNoSky);
							storage.func_150818_a(bx, by & 15, bz, block);
							storage.setExtBlockMetadata(bx, by & 15, bz, meta);
							chunkChanged = true;
						} else {
							blocks[index] = block;
							if(metas != null) metas[index] = meta;
						}
					}
				}
			}
		}
	}
}
