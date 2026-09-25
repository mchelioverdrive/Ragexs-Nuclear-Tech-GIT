package com.hbm.dim.eve.GenLayerEve;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.eve.biome.BiomeGenEveOcean;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;

/** Builds rare Eve volcanoes into each target chunk's own primer. */
public class MapGenEveVolcano extends MapGenBase {
	private byte[] metas;

	public MapGenEveVolcano() { range = 3; }

	public void generate(IChunkProvider provider, World world, int x, int z, Block[] blocks, byte[] metas) {
		this.metas = metas;
		try {
			func_151539_a(provider, world, x, z, blocks);
		} finally {
			this.metas = null;
			worldObj = null;
		}
	}

	@Override
	protected void func_151538_a(World world, int sourceX, int sourceZ, int targetX, int targetZ, Block[] blocks) {
		if(rand.nextInt(1000) != 0) return;
		int centerX = (sourceX << 4) + 8 + rand.nextInt(16);
		int centerZ = (sourceZ << 4) + 8 + rand.nextInt(16);
		BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(centerX, centerZ);
		if(biome instanceof BiomeGenEveOcean) return;
		int height = 30 + rand.nextInt(4);
		int radius = height / 4 + rand.nextInt(22);
		if(radius < 1) return;
		for(int x = 0; x < 16; x++) {
			int dx = (targetX << 4) + x - centerX;
			if(Math.abs(dx) > radius) continue;
			for(int z = 0; z < 16; z++) {
				int dz = (targetZ << 4) + z - centerZ;
				double distance = Math.sqrt((double)dx * dx + (double)dz * dz);
				if(distance > radius) continue;
				int baseY = surfaceY(blocks, x, z);
				if(baseY < 2) continue;
				int cone = (int)((1.0D - distance / radius) * height);
				for(int offset = 0; offset <= cone; offset++) {
					int above = baseY + offset;
					if(above >= 256) break;
					if(dx != 0 || dz != 0) replace(blocks, x, above, z, ModBlocks.basalt);
					int below = baseY - offset;
					if(offset > 0 && below >= 1) replace(blocks, x, below, z, ModBlocks.ore_depth_nether_neodymium);
				}
				if(dx == 0 && dz == 0 && baseY + 5 < 256) {
					for(int y = baseY; y < Math.min(256, baseY + height); y++) set(blocks, x, y, z, Blocks.air);
					set(blocks, x, baseY + 5, z, ModBlocks.geysir_electric);
				}
			}
		}
	}

	private static int index(int x, int y, int z) { return (x * 16 + z) * 256 + y; }

	private int surfaceY(Block[] blocks, int x, int z) {
		for(int y = 255; y > 1; y--) {
			Block block = blocks[index(x, y, z)];
			if(block == ModBlocks.eve_silt || block == ModBlocks.eve_rock) return y;
			if(block != null && block != Blocks.air && block != Blocks.lava) return -1;
		}
		return -1;
	}

	private void replace(Block[] blocks, int x, int y, int z, Block replacement) {
		Block old = blocks[index(x, y, z)];
		if(old == null || old == Blocks.air || old == ModBlocks.eve_silt || old == ModBlocks.eve_rock) set(blocks, x, y, z, replacement);
	}

	private void set(Block[] blocks, int x, int y, int z, Block replacement) {
		int index = index(x, y, z);
		blocks[index] = replacement;
		if(metas != null) metas[index] = 0;
	}
}
