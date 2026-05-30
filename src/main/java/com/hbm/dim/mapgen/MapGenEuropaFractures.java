package com.hbm.dim.mapgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public class MapGenEuropaFractures {

	public Block crackBlock = Blocks.packed_ice;
	public Block ridgeBlock = Blocks.packed_ice;

	public int chancePerChunk = 3;

	public void func_151539_a(IChunkProvider provider, World world, int chunkX, int chunkZ, Block[] blocks) {

		Random rand = new Random(
			world.getSeed()
				+ chunkX * 341873128712L
				+ chunkZ * 132897987541L);

		for(int fracture = 0; fracture < chancePerChunk; fracture++) {

			if(rand.nextInt(3) != 0)
				continue;

			generateFracture(rand, chunkX, chunkZ, blocks);
		}
	}

	private void generateFracture(Random rand, int chunkX, int chunkZ, Block[] blocks) {

		double startX = rand.nextInt(16);
		double startZ = rand.nextInt(16);

		double angle = rand.nextDouble() * Math.PI * 2D;

		int length = 30 + rand.nextInt(80);
		float width = 1.5F + rand.nextFloat() * 2.0F;

		for(int i = 0; i < length; i++) {

			int x = (int)Math.round(startX);
			int z = (int)Math.round(startZ);

			carveFracture(x, z, width, blocks);

			// slight wandering
			angle += (rand.nextDouble() - 0.5D) * 0.18D;

			startX += Math.cos(angle);
			startZ += Math.sin(angle);

			if(startX < -8 || startX > 24
				|| startZ < -8 || startZ > 24)
				break;
		}
	}

	private void carveFracture(int localX, int localZ, float width, Block[] blocks) {

		for(int dx = -(int)width - 1; dx <= width + 1; dx++) {
			for(int dz = -(int)width - 1; dz <= width + 1; dz++) {

				int x = localX + dx;
				int z = localZ + dz;

				if(x < 0 || z < 0 || x >= 16 || z >= 16)
					continue;

				double dist =
					Math.sqrt(dx * dx + dz * dz);

				int surfaceY = getSurfaceY(x, z, blocks);

				if(surfaceY <= 5)
					continue;

				// center crack
				if(dist <= width * 0.45D) {

					for(int depth = 0; depth < 3; depth++) {

						int y = surfaceY - depth;

						setBlock(blocks,
								 x,
								 y,
								 z,
								 Blocks.air);
					}
				}

				// ridge walls
				else if(dist <= width) {

					int y = surfaceY + 1;

					setBlock(blocks,
							 x,
							 y,
							 z,
							 ridgeBlock);
				}

				// dirty ice transition
				else if(dist <= width + 1.2D) {

					setBlock(blocks,
							 x,
							 surfaceY,
							 z,
							 crackBlock);
				}
			}
		}
	}

	private int getSurfaceY(int x, int z, Block[] blocks) {

		for(int y = 255; y > 0; y--) {

			Block block =
				getBlock(blocks, x, y, z);

			if(block != null
				&& block != Blocks.air) {
				return y;
			}
		}

		return -1;
	}

	private Block getBlock(Block[] blocks,
						   int x,
						   int y,
						   int z) {

		int index =
			(x * 16 + z) * 256 + y;

		if(index < 0
			|| index >= blocks.length)
			return null;

		return blocks[index];
	}

	private void setBlock(Block[] blocks,
						  int x,
						  int y,
						  int z,
						  Block block) {

		int index =
			(x * 16 + z) * 256 + y;

		if(index < 0
			|| index >= blocks.length)
			return;

		blocks[index] = block;
	}
}
