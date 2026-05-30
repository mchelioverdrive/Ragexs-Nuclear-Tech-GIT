package com.hbm.dim.jupiter;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class ChunkProviderJupiter extends ChunkProviderCelestial {

	public ChunkProviderJupiter(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		stoneBlock = Blocks.hardened_clay;
		seaBlock = Blocks.air;
	}

	@Override
	public BlockMetaBuffer getChunkPrimer(int x, int z) {

		BlockMetaBuffer buffer = super.getChunkPrimer(x, z);

		for(int bx = 0; bx < 16; bx++) {
			for(int bz = 0; bz < 16; bz++) {

				int worldX = x * 16 + bx;
				int worldZ = z * 16 + bz;

				// turbulent cloud-top variation
				int cloudTop =
					190 +
						(int)(Math.sin(worldZ * 0.01D) * 12) +
						(int)(Math.cos(worldX * 0.008D) * 8);

				int columnIndexBase =
					(bx * 16 + bz) * 256;

				for(int y = 0; y < 256; y++) {

					int index =
						columnIndexBase + y;

					// ====================================
					// UPPER ATMOSPHERE
					// thin gas
					// ====================================
					if(y > cloudTop + 35) {

						buffer.blocks[index] =
							Blocks.air;
					}

					// ====================================
					// AMMONIA CLOUDS
					// first "landing" layer
					// ====================================
					else if(y > cloudTop + 10) {

						if(rand.nextInt(3) == 0) {

							buffer.blocks[index] =
								ModBlocks.cloud;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ====================================
					// CLOUD DECK
					// closest thing to surface
					// ====================================
					else if(y > cloudTop - 10) {

						if(rand.nextInt(4) == 0) {

							buffer.blocks[index] =
								ModBlocks.cloud;

						} else {

							buffer.blocks[index] =
								ModBlocks.cloud_dense;
						}
					}

					// ====================================
					// STORM ATMOSPHERE
					// dense gases
					// ====================================
					else if(y > 120) {

						if(rand.nextInt(6) == 0) {

							buffer.blocks[index] =
								ModBlocks.jupiter_storm;

						} else {

							buffer.blocks[index] =
								ModBlocks.cloud_dense;
						}
					}

					// ====================================
					// SUPERCRITICAL HYDROGEN
					// liquid-like gas
					// ====================================
					else if(y > 50) {

						buffer.blocks[index] =
							ModBlocks.supercritical_hydrogen;
					}

					// ====================================
					// METALLIC HYDROGEN OCEAN
					// absurd pressure
					// ====================================
					else {

						buffer.blocks[index] =
							ModBlocks.metallic_hydrogen;
					}
				}

				// ====================================
				// STORM COLUMNS
				// Jupiter turbulence
				// ====================================

				if(rand.nextInt(35) == 0) {

					int stormHeight =
						20 + rand.nextInt(40);

					for(int sy = 0; sy < stormHeight; sy++) {

						int y =
							cloudTop + sy;

						if(y >= 256)
							break;

						int index =
							columnIndexBase + y;

						buffer.blocks[index] =
							ModBlocks.jupiter_storm;
					}
				}
			}
		}

		return buffer;
	}
}
