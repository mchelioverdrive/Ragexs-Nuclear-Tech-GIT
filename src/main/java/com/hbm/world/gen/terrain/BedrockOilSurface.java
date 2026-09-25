package com.hbm.world.gen.terrain;

import java.util.Random;

import com.hbm.config.GeneralConfig;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.world.feature.OilSpot;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.chunk.IChunkProvider;

/** Applies each oil-spot point only while its owning population slice is active. */
public class BedrockOilSurface implements IWorldGenerator {
	@Override
	public void generate(Random ignored, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider provider) {
		if(world.provider instanceof WorldProviderHell || world.provider instanceof WorldProviderEnd
			|| !(world.provider instanceof WorldProviderCelestial) && world.provider.dimensionId != 0 && !GeneralConfig.enableMDOres) return;
		int minX = (chunkX << 4) + 8;
		int minZ = (chunkZ << 4) + 8;
		for(int sx = chunkX - 4; sx <= chunkX + 4; sx++) for(int sz = chunkZ - 4; sz <= chunkZ + 4; sz++) {
			if(!MapGenBedrockOil.selected(world, sx, sz)) continue;
			long seed = MapGenBedrockOil.sourceSeed(world, sx, sz);
			Random center = new Random(seed);
			int x = (sx << 4) + center.nextInt(16);
			int z = (sz << 4) + center.nextInt(16);
			Random points = new Random(seed ^ 0x6F696C53706F744CL);
			for(int i = 0; i < 50; i++) {
				int px = x + Math.max(-32, Math.min(32, (int)(points.nextGaussian() * 5)));
				int pz = z + Math.max(-32, Math.min(32, (int)(points.nextGaussian() * 5)));
				if(px >= minX && px < minX + 16 && pz >= minZ && pz < minZ + 16)
					OilSpot.generatePoint(world, new Random(seed + i * 0x9E3779B97F4A7C15L), px, pz);
			}
		}
	}
}
