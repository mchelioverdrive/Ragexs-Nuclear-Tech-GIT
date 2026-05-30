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
	private static final int FRACTURE_COUNT = 20; // Reduced from 28 - less chaos

	public void func_151539_a(IChunkProvider provider, World world, int chunkX, int chunkZ, Block[] blocks) {
		long seed = world.getSeed();
		int baseX = chunkX * 16;
		int baseZ = chunkZ * 16;

		// First pass: Carve all fractures
		for (int i = 0; i < FRACTURE_COUNT; i++) {
			long s = seed ^ (i * 0x9E3779B97F4A7C15L);
			Random rand = new Random(s);

			double ax = rand.nextDouble() * 32000 - 16000;
			double az = rand.nextDouble() * 32000 - 16000;
			double bx = rand.nextDouble() * 32000 - 16000;
			double bz = rand.nextDouble() * 32000 - 16000;

			applyFracture(blocks, baseX, baseZ, ax, az, bx, bz);
		}

		// Second pass: Cleanup floating blocks + add ridges
		cleanupFloatingBlocksAndAddRidges(blocks);
	}

	private void applyFracture(Block[] blocks, int baseX, int baseZ,
							   double ax, double az, double bx, double bz) {

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int worldX = baseX + x;
				int worldZ = baseZ + z;

				double dist = pointLineDistance(worldX, worldZ, ax, az, bx, bz);

				if (dist > 29.0) continue;

				// Add small noise to break up diagonal stepping
				double noise = ((worldX * 0.17 + worldZ * 0.23) % 3.0) - 1.5;
				dist += noise;

				double t = Math.max(0.0, 1.0 - (dist / 26.0)); // 0 = edge, 1 = center

				int surfaceY = getSurfaceY(blocks, x, z);
				if (surfaceY <= 6) continue;

				int maxDepth = 9 + (int) (t * 29);

				// Carve with sloped walls for much better diagonal look
				for (int y = surfaceY; y >= surfaceY - maxDepth; y--) {
					if (y < 1) break;

					double depthFactor = (surfaceY - y) / (double) maxDepth;
					double effectiveWidth = 13.5 + (depthFactor * 9.0); // wider at top

					if (dist < effectiveWidth) {
						setBlock(blocks, x, y, z, Blocks.air);
					}
				}

				// Floor
				int floorY = Math.max(1, surfaceY - maxDepth);
				setBlock(blocks, x, floorY, z, crackBlock);
			}
		}
	}

	private void cleanupFloatingBlocksAndAddRidges(Block[] blocks) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int surface = getSurfaceY(blocks, x, z);

				// Remove floating blocks above the new surface
				for (int y = surface + 1; y < 255; y++) {
					if (getBlock(blocks, x, y, z) != Blocks.air) {
						setBlock(blocks, x, y, z, Blocks.air);
					} else {
						break; // stop when we hit air
					}
				}

				// Add ridges on shoulders (optional: make them rarer)
				if (Math.random() < 0.65) { // tweak chance
					setBlock(blocks, x, surface, z, ridgeBlock);
					if (Math.random() < 0.4) {
						setBlock(blocks, x, surface - 1, z, ridgeBlock);
					}
				}
			}
		}
	}

	private double pointLineDistance(double px, double pz, double ax, double az, double bx, double bz) {
		double apx = px - ax, apz = pz - az;
		double abx = bx - ax, abz = bz - az;

		double abLenSq = abx * abx + abz * abz;
		if (abLenSq == 0) return Math.sqrt(apx * apx + apz * apz);

		double t = (apx * abx + apz * abz) / abLenSq;
		t = Math.max(0.0, Math.min(1.0, t));

		double cx = ax + abx * t;
		double cz = az + abz * t;

		double dx = px - cx;
		double dz = pz - cz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private int getSurfaceY(Block[] blocks, int x, int z) {
		for (int y = 255; y > 4; y--) {
			int idx = (z * 16 + x) * 256 + y;
			if (idx < 0 || idx >= blocks.length) continue;

			Block b = blocks[idx];
			if (b != null && b != Blocks.air) {
				return y;
			}
		}
		return 80;
	}

	private Block getBlock(Block[] blocks, int x, int y, int z) {
		int idx = (z * 16 + x) * 256 + y;
		if (idx < 0 || idx >= blocks.length) return Blocks.air;
		return blocks[idx];
	}

	private void setBlock(Block[] blocks, int x, int y, int z, Block block) {
		int index = (z * 16 + x) * 256 + y;
		if (index >= 0 && index < blocks.length) {
			blocks[index] = block;
		}
	}
}
