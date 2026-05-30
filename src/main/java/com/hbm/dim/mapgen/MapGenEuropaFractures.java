package com.hbm.dim.mapgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public class MapGenEuropaFractures {

	public Block crackBlock = Blocks.packed_ice;
	public Block ridgeBlock = Blocks.packed_ice;

	private static final int SEA_LEVEL = 63;

	// FIX: global deterministic fracture set (NOT per chunk)
	private static final int FRACTURE_COUNT = 28;

	public void func_151539_a(IChunkProvider provider, World world, int chunkX, int chunkZ, Block[] blocks) {

		long seed = world.getSeed();

		int baseX = chunkX * 16;
		int baseZ = chunkZ * 16;

		// FIX: stable global fracture set (NOT re-rolled per chunk call)
		for (int i = 0; i < FRACTURE_COUNT; i++) {

			long s =
				seed
					^ (i * 0x9E3779B97F4A7C15L);

			Random rand = new Random(s);

			// FIX: TRUE global endpoints (deterministic world-space lines)
			double ax = rand.nextDouble() * 16000 - 8000;
			double az = rand.nextDouble() * 16000 - 8000;

			double bx = rand.nextDouble() * 16000 - 8000;
			double bz = rand.nextDouble() * 16000 - 8000;

			applyFractureLine(blocks, baseX, baseZ, ax, az, bx, bz);
		}
	}

	private void applyFractureLine(Block[] blocks, int baseX, int baseZ,
								   double ax, double az,
								   double bx, double bz) {

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {

				int worldX = baseX + x;
				int worldZ = baseZ + z;

				double dist = pointLineDistance(worldX, worldZ, ax, az, bx, bz);

				// width controls crack thickness
				double width = 26.0;

				if (dist > width * 1.25)
					continue;

				double t = 1.0 - (dist / width);

				int surfaceY = getSurfaceYSafe(x, z, blocks);
				if (surfaceY <= 1)
					continue;

				int depth = 6 + (int)(t * 22);

				int bottom = surfaceY - depth;
				if (bottom < 1)
					bottom = 1;

				// carve ravine
				for (int y = surfaceY; y > bottom; y--) {
					setBlock(blocks, x, y, z, Blocks.air);
				}

				// floor ice
				setBlock(blocks, x, bottom, z, crackBlock);

				// ridge shoulders
				if (t > 0.72 && t < 0.88) {

					// ONLY place on surface, never above it
					setBlock(blocks, x, surfaceY, z, ridgeBlock);
				}
			}
		}
	}

	// TRUE line distance (this is what makes intersections work correctly)
	private double pointLineDistance(double px, double pz,
									 double ax, double az,
									 double bx, double bz) {

		double apx = px - ax;
		double apz = pz - az;

		double abx = bx - ax;
		double abz = bz - az;

		double abLenSq = abx * abx + abz * abz;
		double t = (apx * abx + apz * abz) / abLenSq;

		t = Math.max(0.0, Math.min(1.0, t));

		double cx = ax + abx * t;
		double cz = az + abz * t;

		double dx = px - cx;
		double dz = pz - cz;

		return Math.sqrt(dx * dx + dz * dz);
	}

	private int getSurfaceYSafe(int x, int z, Block[] blocks) {

		for (int y = 255; y > 30; y--) {

			int idx = (z * 16 + x) * 256 + y;

			if (idx < 0 || idx >= blocks.length)
				continue;

			Block b = blocks[idx];

			if (b != null && b != Blocks.air) {
				return y;
			}
		}

		return 80;
	}

	private void setBlock(Block[] blocks, int x, int y, int z, Block block) {

		int index = (z * 16 + x) * 256 + y;

		if (index < 0 || index >= blocks.length)
			return;

		blocks[index] = block;
	}
}
