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

				for(int y = 0; y < 256; y++) {

					int index = (bx * 16 + bz) * 256 + y;

					// === CORE SUN MASS (NO LAVA) ===
					if(y < surface - 10) {

						// mostly solid hot material
						buffer.blocks[index] = ModBlocks.basalt;

						// rare molten pockets (visual only)
						if(rand.nextInt(400) == 0) {
							buffer.blocks[index] = ModBlocks.basalt; // later replace with emissive variant
						}

					}

					// === TRANSITION LAYER (NO FLUIDS) ===
					else if(y < surface) {

						// mixed hot crust
						buffer.blocks[index] = ModBlocks.basalt;

						// optional glow variant later
						if(rand.nextInt(120) == 0) {
							buffer.blocks[index] = ModBlocks.basalt;
						}

					}

					// === CORONA (AIR ONLY) ===
					else {

						buffer.blocks[index] = Blocks.air;
					}
				}

				// === SPARSE FLARE STRUCTURES (NO LAVA) ===
				if(rand.nextInt(30) == 0) {

					int flareHeight = 10 + rand.nextInt(25);

					for(int fy = 0; fy < flareHeight; fy++) {

						int y = surface + fy;
						if(y >= 256) break;

						int index = (bx * 16 + bz) * 256 + y;

						// solid flare column (not fluid!)
						buffer.blocks[index] = ModBlocks.plasma;
					}
				}
			}
		}

		return buffer;
	}
}
