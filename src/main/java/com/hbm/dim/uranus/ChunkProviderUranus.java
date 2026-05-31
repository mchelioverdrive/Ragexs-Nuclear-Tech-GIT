package com.hbm.dim.uranus;

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

				// Uranus is smooth and visually bland
				// slight atmospheric banding only
				int cloudTop =
					172 +
						(int)(Math.sin(worldZ * 0.0025D) * 4) +
						(int)(Math.cos(worldX * 0.003D) * 3);

				int columnIndexBase =
					(bx * 16 + bz) * 256;

				for(int y = 0; y < 256; y++) {

					int index =
						columnIndexBase + y;

					// ==========================
					// UPPER METHANE HAZE
					// very sparse
					// ==========================
					if(y > cloudTop + 45) {

						if(rand.nextInt(22) == 0) {

							buffer.blocks[index] =
								Blocks.ice;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// HIGH CLOUDS
					// wispy methane ice
					// ==========================
					else if(y > cloudTop + 18) {

						if(rand.nextInt(6) == 0) {

							buffer.blocks[index] =
								Blocks.ice;

						} else {

							buffer.blocks[index] =
								Blocks.air;
						}
					}

					// ==========================
					// MAIN CLOUD DECK
					// smooth + quiet
					// ==========================
					else if(y > cloudTop - 10) {

						if(rand.nextInt(5) == 0) {

							buffer.blocks[index] =
								Blocks.ice;

						} else {

							buffer.blocks[index] =
								Blocks.packed_ice;
						}
					}

					// ==========================
					// DEEP ATMOSPHERE
					// compressed volatiles
					// ==========================
					else if(y > 95) {

						buffer.blocks[index] =
							Blocks.packed_ice;
					}

					// ==========================
					// SUPERCRITICAL ICE MANTLE
					// water/ammonia/methane
					// ==========================
					else if(y > 40) {

						if(rand.nextInt(10) == 0) {

							buffer.blocks[index] =
								Blocks.ice;

						} else {

							buffer.blocks[index] =
								Blocks.packed_ice;
						}
					}

					// ==========================
					// COMPRESSED INTERIOR
					// ==========================
					else {

						buffer.blocks[index] =
							Blocks.packed_ice;
					}
				}

				// ==========================
				// EXTREMELY RARE STORMS
				// Uranus is calm
				// ==========================
				if(rand.nextInt(900) == 0) {

					int stormHeight =
						8 + rand.nextInt(18);

					for(int sy = 0; sy < stormHeight; sy++) {

						int y =
							cloudTop + sy;

						if(y >= 256)
							break;

						int index =
							columnIndexBase + y;

						buffer.blocks[index] =
							Blocks.ice;
					}
				}
			}
		}

		return buffer;
	}

}
