package com.hbm.dim.sun;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class ChunkProviderSun extends ChunkProviderCelestial {

	public ChunkProviderSun(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		stoneBlock = Blocks.lava;
		seaBlock = Blocks.lava;
	}

	@Override
	public BlockMetaBuffer getChunkPrimer(int x, int z) {

		BlockMetaBuffer buffer = super.getChunkPrimer(x, z);

		for(int bx = 0; bx < 16; bx++) {
			for(int bz = 0; bz < 16; bz++) {

				int worldX = x * 16 + bx;
				int worldZ = z * 16 + bz;

				int surface =
					220 +
						(int)(Math.sin(worldX * 0.04D) * 8) +
						(int)(Math.cos(worldZ * 0.04D) * 8);

				int columnIndexBase = (bx * 16 + bz) * 256;

				// =========================
				// CORE + TRANSITION LAYERS
				// (ONLY GENERATE WHERE NEEDED)
				// =========================

				int minY = Math.max(0, surface - 30);
				int maxY = Math.min(255, surface + 40);

				for(int y = minY; y <= maxY; y++) {

					int index = columnIndexBase + y;

					if(y < surface - 10) {
						// core mass
						buffer.blocks[index] = ModBlocks.basalt;
					}
					else if(y < surface) {
						// transition crust
						buffer.blocks[index] = ModBlocks.basalt;

						// rare “hot pocket” visual variation (no extra block types needed)
						if((worldX ^ worldZ ^ y) % 97 == 0) {
							buffer.blocks[index] = ModBlocks.basalt;
						}
					}
					else {
						// corona empty space
						buffer.blocks[index] = Blocks.air;
					}
				}

				// =========================
				// FLARE SYSTEM (SAFE SPAWNING)
				// =========================

				if(rand.nextInt(45) == 0) {

					int flareHeight = 8 + rand.nextInt(18);

					// step to reduce block spam massively
					for(int fy = 0; fy < flareHeight; fy += 1) {
						//for(int fy = 0; fy < flareHeight; fy++)

						int y = surface + fy;
						if(y >= 256) break;

						int index = columnIndexBase + y;

						buffer.blocks[index] = ModBlocks.solar_plasma;

						// tiny branching (very reduced frequency)
						if(rand.nextInt(10) == 0) {

							int bxOff = bx + rand.nextInt(3) - 1;
							int bzOff = bz + rand.nextInt(3) - 1;

							if(bxOff >= 0 && bxOff < 16 && bzOff >= 0 && bzOff < 16) {
								int sideIndex = (bxOff * 16 + bzOff) * 256 + y;
								buffer.blocks[sideIndex] = ModBlocks.solar_plasma;
							}
						}
					}
				}
			}
		}

		return buffer;
	}
}
