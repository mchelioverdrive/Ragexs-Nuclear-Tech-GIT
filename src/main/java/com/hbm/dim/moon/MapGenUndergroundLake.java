package com.hbm.dim.moon;

import com.hbm.blocks.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;

/** Moon ice pockets generated in the chunk buffer, with no adjacent chunk reads. */
public class MapGenUndergroundLake extends MapGenBase {
	private byte[] metas;

	public MapGenUndergroundLake() { range = 2; }

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
		if(rand.nextInt(10) >= 2) return;
		int centerX = (sourceX << 4) + rand.nextInt(16);
		int centerZ = (sourceZ << 4) + rand.nextInt(16);
		int centerY = 20 + rand.nextInt(20);
		int radius = 4 + rand.nextInt(4);
		int depth = 2 + rand.nextInt(3);
		long seed = rand.nextLong();
		for(int x = Math.max(targetX << 4, centerX - radius); x <= Math.min((targetX << 4) + 15, centerX + radius); x++) {
			for(int z = Math.max(targetZ << 4, centerZ - radius); z <= Math.min((targetZ << 4) + 15, centerZ + radius); z++) {
				for(int y = centerY - depth; y <= centerY; y++) {
					int dx = x - centerX, dz = z - centerZ, dy = y - centerY;
					long hash = seed + (long)x * 341873128712L + (long)z * 132897987541L + y * 42317861L;
					hash ^= hash >>> 33;
					hash *= 0xff51afd7ed558ccdL;
					hash ^= hash >>> 33;
					double edge = ((hash >>> 11) & 0x1FFFFFL) / 2097152.0D;
					if(Math.sqrt(dx * dx + dz * dz + dy * dy * 0.5D) > radius - edge) continue;
					int index = ((x & 15) * 16 + (z & 15)) * 256 + y;
					if(blocks[index] == ModBlocks.moon_rock) {
						blocks[index] = (hash & 1L) == 0 ? Blocks.ice : Blocks.packed_ice;
						if(metas != null) metas[index] = 0;
					}
				}
			}
		}
	}
}
