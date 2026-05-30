package com.hbm.dim.laythe.GenLayerLaythe;

import com.hbm.dim.laythe.biome.BiomeGenBaseLaythe;

import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

public class GenLayerLaytheBiomes extends GenLayer {

	public GenLayerLaytheBiomes(long seed) {
		super(seed);
	}

	@Override
	public int[] getInts(int x, int z, int width, int depth) {

		int[] dest =
			IntCache.getIntCache(
				width * depth);

		for(int dz = 0;
			dz < depth;
			dz++) {

			for(int dx = 0;
				dx < width;
				dx++) {

				initChunkSeed(
					x + dx,
					z + dz);

				int roll =
					nextInt(100);

				/*
				 * Weighted Europa biome distribution
				 *
				 * 70% Plains
				 * 18% Fractures
				 * 10% Chaos
				 * 2% Polar
				 */

				if(roll < 70) {

					dest[dx + dz * width] =
						BiomeGenBaseLaythe
							.europaPlains
							.biomeID;
				}
				else if(roll < 88) {

					dest[dx + dz * width] =
						BiomeGenBaseLaythe
							.europaFracture
							.biomeID;
				}
				else if(roll < 98) {

					dest[dx + dz * width] =
						BiomeGenBaseLaythe
							.europaChaos
							.biomeID;
				}
				else {

					dest[dx + dz * width] =
						BiomeGenBaseLaythe
							.laythePolar
							.biomeID;
				}
			}
		}

		return dest;
	}
}
