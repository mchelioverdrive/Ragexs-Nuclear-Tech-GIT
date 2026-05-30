package com.hbm.dim.laythe.GenLayerLaythe;

import com.hbm.dim.laythe.biome.BiomeGenBaseLaythe;

import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

public class GenLayerDiversifyLaythe extends GenLayer {

	public GenLayerDiversifyLaythe(long seed,
								   GenLayer parent) {

		super(seed);
		this.parent = parent;
	}

	@Override
	public int[] getInts(int x,
						 int z,
						 int width,
						 int depth) {

		int[] input =
			this.parent.getInts(
				x,
				z,
				width,
				depth);

		int[] output =
			IntCache.getIntCache(
				width * depth);

		for(int zOut = 0;
			zOut < depth;
			zOut++) {

			for(int xOut = 0;
				xOut < width;
				xOut++) {

				int index =
					xOut + zOut * width;

				initChunkSeed(
					xOut + x,
					zOut + z);

				int roll =
					nextInt(100);

				// Weighted Europa biomes
				if(roll < 70) {

					output[index] =
						BiomeGenBaseLaythe
							.europaPlains
							.biomeID;
				}
				else if(roll < 88) {

					output[index] =
						BiomeGenBaseLaythe
							.europaFracture
							.biomeID;
				}
				else if(roll < 98) {

					output[index] =
						BiomeGenBaseLaythe
							.europaChaos
							.biomeID;
				}
				else {

					output[index] =
						BiomeGenBaseLaythe
							.laythePolar
							.biomeID;
				}
			}
		}

		return output;
	}
}
