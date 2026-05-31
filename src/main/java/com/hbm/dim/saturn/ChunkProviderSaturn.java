package com.hbm.dim.saturn;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.List;

public class ChunkProviderSaturn extends ChunkProviderCelestial {

	public ChunkProviderSaturn(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		stoneBlock = Blocks.hardened_clay;
		seaBlock = Blocks.air;
	}
	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		return null;
	}

	//TODO the hexagon storm thing also rings?

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

				// smoother, calmer cloud variation
				int cloudTop =
					185 +
						(int)(Math.sin(worldZ * 0.004D) * 6) +
						(int)(Math.cos(worldX * 0.005D) * 5);

				int columnIndexBase =
					(bx * 16 + bz) * 256;

				for(int y = 0; y < 256; y++) {

					int index =
						columnIndexBase + y;

					// ==========================
					// THIN UPPER ATMOSPHERE
					// sparse haze
					// ==========================
					if(y > cloudTop + 35) {

						if(rand.nextInt(20) == 0) {
							buffer.blocks[index] =
								ModBlocks.cloud;
						} else {
							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// AMMONIA ICE CLOUDS
					// pale upper deck
					// ==========================
					else if(y > cloudTop + 10) {

						if(rand.nextInt(4) == 0) {
							buffer.blocks[index] =
								ModBlocks.cloud;
						} else {
							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// MAIN CLOUD DECK
					// thick but softer than Jupiter
					// ==========================
					else if(y > cloudTop - 15) {

						if(rand.nextInt(6) == 0) {
							buffer.blocks[index] =
								ModBlocks.cloud;
						} else {
							buffer.blocks[index] =
								ModBlocks.cloud_dense;
						}
					}

					// ==========================
					// DEEP ATMOSPHERE
					// dense hydrogen/helium
					// ==========================
					else if(y > 120) {

						if(rand.nextInt(8) == 0) {

							buffer.blocks[index] =
								ModBlocks.saturn_storm;

						} else {

							buffer.blocks[index] =
								ModBlocks.cloud_dense;
						}
					}

					// ==========================
					// HELIUM RAIN ZONE
					// speculative layer
					// ==========================
					else if(y > 60) {

						buffer.blocks[index] =
							ModBlocks.supercritical_hydrogen;
					}

					// ==========================
					// METALLIC HYDROGEN
					// deep interior
					// ==========================
					else {

						buffer.blocks[index] =
							ModBlocks.metallic_hydrogen;
					}
				}

				// ==========================
				// OCCASIONAL STORM COLUMNS
				// MUCH rarer than Jupiter
				// ==========================

				if(rand.nextInt(350) == 0) {

					int stormHeight =
						15 + rand.nextInt(30);

					for(int sy = 0; sy < stormHeight; sy++) {

						int y =
							cloudTop + sy;

						if(y >= 256)
							break;

						int index =
							columnIndexBase + y;

						buffer.blocks[index] =
							ModBlocks.saturn_storm;
					}
				}
			}
		}

		return buffer;
	}


}
