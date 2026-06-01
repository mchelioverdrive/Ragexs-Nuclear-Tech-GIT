package com.hbm.dim.neptune;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.List;

public class ChunkProviderNeptune extends ChunkProviderCelestial {

	public ChunkProviderNeptune(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		stoneBlock = Blocks.hardened_clay;
		seaBlock = Blocks.air;
	}

	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		return null;
	}

	@Override
	public BlockMetaBuffer getChunkPrimer(int x, int z) {

		BlockMetaBuffer buffer =
			super.getChunkPrimer(x, z);

		for(int bx = 0; bx < 16; bx++) {
			for(int bz = 0; bz < 16; bz++) {

				int worldX =
					x * 16 + bx;

				int worldZ =
					z * 16 + bz;

				// Neptune is turbulent but smoother than Jupiter
				int cloudTop =
					185 +
						(int)(Math.sin(worldX * 0.007D) * 7) +
						(int)(Math.cos(worldZ * 0.006D) * 6);

				double stormNoise =
					Math.sin(worldX * 0.03D) *
						Math.cos(worldZ * 0.03D);

				int columnIndexBase =
					(bx * 16 + bz) * 256;

				for(int y = 0; y < 256; y++) {

					int index =
						columnIndexBase + y;

					// ==========================
					// THIN UPPER HAZE
					// methane haze
					// ==========================
					if(y > cloudTop + 35) {

						if(rand.nextInt(18) == 0) {

							buffer.blocks[index] =
								ModBlocks.neptune_cloud_thin;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// HIGH METHANE CLOUDS
					// wispy upper clouds
					// ==========================
					else if(y > cloudTop + 10) {

						if(rand.nextInt(4) == 0) {

							buffer.blocks[index] =
								ModBlocks.neptune_cloud_thin;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// MAIN CLOUD DECK
					// thicker blue clouds
					// ==========================
					else if(y > cloudTop - 15) {

						if(rand.nextInt(5) == 0) {

							buffer.blocks[index] =
								ModBlocks.neptune_cloud;

						} else {

							buffer.blocks[index] =
								ModBlocks.neptune_cloud_dense;
						}
					}

					// ==========================
					// DEEP ATMOSPHERE
					// violent storm region
					// ==========================
					else if(y > 100) {

						if(stormNoise > 0.65D
							&& rand.nextInt(7) == 0) {

							buffer.blocks[index] =
								ModBlocks.neptune_storm;

						} else {

							buffer.blocks[index] =
								ModBlocks.neptune_atmosphere_dense;
						}
					}

					// ==========================
					// SUPERCRITICAL OCEAN
					// water/ammonia/methane soup
					// ==========================
					else if(y > 35) {

						buffer.blocks[index] =
							ModBlocks.supercritical_water;
					}

					// ==========================
					// ICE MANTLE
					// compressed exotic ice
					// ==========================
					else if(y > 6) {

						buffer.blocks[index] =
							Blocks.packed_ice;
					}

					// ==========================
					// CORE
					// placeholder
					// ==========================
					else {

						buffer.blocks[index] =
							Blocks.bedrock;
					}
				}

				// ==========================
				// MASSIVE STORM COLUMNS
				// rarer but stronger than Uranus
				// ==========================

				if(rand.nextInt(180) == 0) {

					int stormHeight =
						25 + rand.nextInt(45);

					for(int sy = 0; sy < stormHeight; sy++) {

						int y =
							cloudTop + sy;

						if(y >= 256)
							break;

						int index =
							columnIndexBase + y;

						buffer.blocks[index] =
							ModBlocks.neptune_storm;
					}
				}
			}
		}

		return buffer;
	}


}
