package com.hbm.dim.mapgen;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public class MapGenEuropaFractures {

	public Block crackBlock = Blocks.packed_ice;
	public Block ridgeBlock = Blocks.packed_ice;

	private static final int FRACTURE_COUNT = 22;
	private static final int ASSUMED_SURFACE = 95;   // <--- Important: tune this

	public void func_151539_a(IChunkProvider provider, World world, int chunkX, int chunkZ, Block[] blocks) {
		long seed = world.getSeed();
		int baseX = chunkX * 16;
		int baseZ = chunkZ * 16;

		for (int i = 0; i < FRACTURE_COUNT; i++) {
			long s = seed ^ (i * 0x9E3779B97F4A7C15L);
			Random rand = new Random(s);

			double ax = rand.nextDouble() * 40000 - 20000;
			double az = rand.nextDouble() * 40000 - 20000;
			double bx = rand.nextDouble() * 40000 - 20000;
			double bz = rand.nextDouble() * 40000 - 20000;

			applyFracture(blocks, baseX, baseZ, ax, az, bx, bz);
		}

		cleanupFloatingBlocks(blocks);
	}

	private void applyFracture(Block[] blocks, int baseX, int baseZ,
							   double ax, double az, double bx, double bz) {

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int worldX = baseX + x;
				int worldZ = baseZ + z;

				double dist = pointLineDistance(worldX, worldZ, ax, az, bx, bz);
				if (dist > 32.0) continue;

				// Noise to reduce diagonal grid artifacts
				double noise = ((worldX * 0.19 + worldZ * 0.27) % 4.0) - 2.0;
				dist += noise * 0.7;

				double t = Math.max(0.0, 1.0 - (dist / 27.0));

				int surfaceY = ASSUMED_SURFACE;
				int maxDepth = 12 + (int)(t * 34);

				// Carve
				for (int y = surfaceY; y >= surfaceY - maxDepth; y--) {
					if (y < 4) break;

					double depthFactor = (surfaceY - y) / (double) maxDepth;
					double effectiveWidth = 14.5 + depthFactor * 11.0; // wider at top

					if (dist < effectiveWidth) {
						setBlock(blocks, x, y, z, Blocks.air);
					}
				}

				// Floor
				int floorY = Math.max(4, surfaceY - maxDepth);
				setBlock(blocks, x, floorY, z, crackBlock);
			}
		}
	}

	private void cleanupFloatingBlocks(Block[] blocks) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				// Remove anything floating above reasonable height
				boolean foundSolid = false;
				for (int y = 120; y > 4; y--) {
					Block b = getBlock(blocks, x, y, z);
					if (b != null && b != Blocks.air) {
						foundSolid = true;
					} else if (foundSolid) {
						// air below solid = floating, remove it
						setBlock(blocks, x, y, z, Blocks.air);
					}
				}

				// Optional ridges
				if (Math.random() < 0.55) {
					setBlock(blocks, x, ASSUMED_SURFACE, z, ridgeBlock);
				}
			}
		}
	}

	private double pointLineDistance(double px, double pz, double ax, double az, double bx, double bz) {
		double apx = px - ax, apz = pz - az;
		double abx = bx - ax, abz = bz - az;

		double abLenSq = abx * abx + abz * abz;
		if (abLenSq < 1e-8) return Math.hypot(apx, apz);

		double t = (apx * abx + apz * abz) / abLenSq;
		t = Math.max(0.0, Math.min(1.0, t));

		double cx = ax + abx * t;
		double cz = az + abz * t;

		return Math.hypot(px - cx, pz - cz);
	}

	private Block getBlock(Block[] blocks, int x, int y, int z) {
		int idx = (z * 16 + x) * 256 + y;
		if (idx < 0 || idx >= blocks.length) return Blocks.air;
		return blocks[idx] == null ? Blocks.air : blocks[idx];
	}

	private void setBlock(Block[] blocks, int x, int y, int z, Block block) {
		int idx = (z * 16 + x) * 256 + y;
		if (idx >= 0 && idx < blocks.length) {
			blocks[idx] = block;
		}
	}
}
