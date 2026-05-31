package com.hbm.dim.uranus;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.List;

public class ChunkProviderUranus extends ChunkProviderCelestial {

	public ChunkProviderUranus(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		stoneBlock = Blocks.packed_ice;
		seaBlock = Blocks.ice;
	}

	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		return null;
	} //hmm, maybe change later as water world hellscape basically

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

				int columnIndexBase =
					(bx * 16 + bz) * 256;

				// ====================================
				// SMOOTH URANUS ATMOSPHERIC BANDS
				// Uranus is calm + layered
				// ====================================

				double bandNoise =
					Math.sin(worldZ * 0.0025D) * 0.6D +
						Math.cos(worldX * 0.002D) * 0.4D +
						Math.sin(worldX * 0.0008D) * 0.2D;

				int cloudTop =
					165 + (int)(bandNoise * 8D);

				int upperCloudTop =
					cloudTop + 22;

				int hazeTop =
					cloudTop + 48;

				for(int y = 0; y < 256; y++) {

					int index =
						columnIndexBase + y;

					// ==========================
					// EMPTY UPPER ATMOSPHERE
					// almost entirely empty
					// ==========================
					// ==========================
					// EMPTY UPPER ATMOSPHERE
					// ==========================
					if(y > hazeTop) {

						buffer.blocks[index] =
							Blocks.air;
					}

					// ==========================
					// THIN METHANE HAZE
					// ==========================
					else if(y > upperCloudTop) {

						double hazeBand =
							Math.sin(
								worldZ * 0.008D
									+ y * 0.12D
							);

						if(hazeBand > 0.65D) {

							buffer.blocks[index] =
								ModBlocks.uranus_cloud;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// MAIN CLOUD DECK
					// visible Uranus bands
					// ==========================
					else if(y > cloudTop - 10) {

						double cloudBand =
							Math.sin(worldZ * 0.015D)
								+ Math.cos(worldX * 0.008D);

						if(cloudBand > 0.2D) {

							buffer.blocks[index] =
								ModBlocks.uranus_cloud_dense;

						} else if(cloudBand > -0.4D) {

							buffer.blocks[index] =
								ModBlocks.uranus_cloud;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// DENSE ATMOSPHERE
					// compressed gases
					// ==========================
					else if(y > 90) {

						buffer.blocks[index] =
							ModBlocks.uranus_atmosphere;
					}

					// ==========================
					// ICY MANTLE
					// water/ammonia/methane
					// ==========================
					else if(y > 45) {

						buffer.blocks[index] =
							ModBlocks.ammonia_water;
					}

					// ==========================
					// SUPERCRITICAL WATER
					// dense fluid region
					// ==========================
					else if(y > 18) {

						buffer.blocks[index] =
							ModBlocks.supercritical_water;
					}

					// ==========================
					// DIAMOND ZONE
					// methane compression
					// ==========================
					else if(y > 8) {

						if(
							Math.abs(
								worldX * 31
									+ worldZ * 17
									+ y
							) % 90 == 0
						) {

							buffer.blocks[index] =
								Blocks.diamond_ore;

						} else {

							buffer.blocks[index] =
								ModBlocks.supercritical_water;
						}
					}

					// ==========================
					// COMPRESSED CORE
					// ==========================
					else {

						buffer.blocks[index] =
							Blocks.obsidian;
					}
				}

				// ==========================
				// EXTREMELY RARE STORMS
				// Uranus is quiet
				// ==========================
				long stormSeed =
					(worldX * 341873128712L)
						^ (worldZ * 132897987541L);

				if((stormSeed & 1023L) == 0L) {

					int stormHeight =
						10 + (int)(
							Math.abs(
								Math.sin(worldX)
							) * 16
						);

					for(int sy = 0; sy < stormHeight; sy++) {

						int y =
							cloudTop + sy;

						if(y >= 256)
							break;

						int index =
							columnIndexBase + y;

						buffer.blocks[index] =
							ModBlocks.uranus_cloud_dense;
					}
				}
			}
		}

		return buffer;
	}

}
