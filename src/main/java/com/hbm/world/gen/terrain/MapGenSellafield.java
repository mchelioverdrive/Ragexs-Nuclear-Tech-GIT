package com.hbm.world.gen.terrain;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.WorldConfig;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.MapGenBase;

/** Generates a radiation crater against one chunk's storage, without loading its neighbors. */
public class MapGenSellafield extends MapGenBase {

	private Chunk chunk;
	private boolean changed;

	public MapGenSellafield() {
		range = 4;
	}

	public void generate(IChunkProvider provider, World world, Chunk target) {
		chunk = target;
		changed = false;
		try {
			func_151539_a(provider, world, target.xPosition, target.zPosition, null);
			if(changed) {
				target.generateSkylightMap();
				target.setChunkModified();
			}
		} finally {
			chunk = null;
			worldObj = null;
		}
	}

	@Override
	protected void func_151538_a(World world, int sourceX, int sourceZ, int targetX, int targetZ, Block[] unused) {
		if(WorldConfig.radfreq <= 0 || rand.nextInt(WorldConfig.radfreq) != 0
			|| world.getWorldChunkManager().getBiomeGenAt(sourceX * 16, sourceZ * 16) != BiomeGenBase.desert) return;

		int centerX = rand.nextInt(16);
		int centerZ = rand.nextInt(16);
		double radius = rand.nextInt(15) + 10;
		if(rand.nextInt(50) == 0) radius = 50;
		double depth = radius * 0.35D;
		long noiseSeed = rand.nextLong();

		for(int bx = 0; bx < 16; bx++) {
			int dx = (targetX - sourceX) * 16 + bx - centerX;
			if(Math.abs(dx) > radius + 5) continue;
			for(int bz = 0; bz < 16; bz++) {
				int dz = (targetZ - sourceZ) * 16 + bz - centerZ;
				if(Math.abs(dz) > radius + 5) continue;
				double distance = Math.sqrt((double)dx * dx + (double)dz * dz);
				if(distance - jitter(noiseSeed, dx, dz, 0) > radius) continue;

				int craterDepth = Math.max(0, (int)((1.0D - distance * distance / (radius * radius)) * depth));
				int top = topBlock(bx, bz);
				Block existing = chunk.getBlock(bx, top, bz);
				if(existing == ModBlocks.sellafield || existing == ModBlocks.sellafield_slaked) continue;
				if(top >= craterDepth * 2) {
					for(int i = 0; i < craterDepth; i++) set(bx, top - i, bz, Blocks.air, 0);
					top -= craterDepth;
				}

				Block deposit;
				int meta = 0;
				if(distance + jitter(noiseSeed, dx, dz, 1) <= radius / 3.0D) {
					deposit = ModBlocks.sellafield;
					meta = 1;
				} else if(distance - jitter(noiseSeed, dx, dz, 2) <= radius * 2.0D / 3.0D) {
					deposit = ModBlocks.sellafield;
				} else {
					deposit = ModBlocks.sellafield_slaked;
				}
				for(int i = 0; i < 3; i++) set(bx, top - i, bz, deposit, meta);
			}
		}
	}

	private static int jitter(long seed, int x, int z, int channel) {
		long value = seed + (long)x * 341873128712L + (long)z * 132897987541L + channel * 42317861L;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		return (int)((value & Long.MAX_VALUE) % 3L);
	}

	private int topBlock(int x, int z) {
		for(int y = 255; y >= 0; y--) if(chunk.getBlock(x, y, z) != Blocks.air) return y;
		return 0;
	}

	private void set(int x, int y, int z, Block block, int meta) {
		if(y < 0 || y >= 256) return;
		ExtendedBlockStorage storage = chunk.getBlockStorageArray()[y >> 4];
		if(storage == null) {
			if(block == Blocks.air) return;
			storage = chunk.getBlockStorageArray()[y >> 4] = new ExtendedBlockStorage(y & ~15, !chunk.worldObj.provider.hasNoSky);
		}
		storage.func_150818_a(x, y & 15, z, block);
		storage.setExtBlockMetadata(x, y & 15, z, meta);
		changed = true;
	}
}
