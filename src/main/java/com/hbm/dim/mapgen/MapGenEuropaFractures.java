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

	private long hash(long seed, int id) {
		return seed ^ (id * 341873128712L);
	}
	private static class FractureLine {
		double originX, originZ;
		double dirX, dirZ;
		double width;
	}

	private FractureLine[] fractures;

	public void init(World world) {

		Random rand = new Random(world.getSeed());

		int count = 6 + rand.nextInt(4);

		fractures = new FractureLine[count];

		for(int i = 0; i < count; i++) {

			FractureLine f = new FractureLine();

			f.originX = rand.nextInt(8000) - 4000;
			f.originZ = rand.nextInt(8000) - 4000;

			double angle = rand.nextDouble() * Math.PI * 2;

			f.dirX = Math.cos(angle);
			f.dirZ = Math.sin(angle);

			f.width = 8 + rand.nextDouble() * 14;

			fractures[i] = f;
		}
	}

	public void func_151539_a(
		IChunkProvider provider,
		World world,
		int chunkX,
		int chunkZ,
		Block[] blocks) {

		Random rand = new Random(
			world.getSeed()
				+ chunkX * 341873128712L
				+ chunkZ * 132897987541L);

		// 2–4 global fracture lines affecting this chunk
		int count = 2 + rand.nextInt(3);

		for(int i = 0; i < count; i++) {

			generateLineFracture(
				rand,
				chunkX,
				chunkZ,
				blocks);
		}
	}

	private void generateLineFracture(
		Random rand,
		int chunkX,
		int chunkZ,
		Block[] blocks) {

		// world-space line origin (IMPORTANT: not chunk-local chaos)
		double originX = rand.nextInt(4096) - 2048;
		double originZ = rand.nextInt(4096) - 2048;

		// direction (mostly straight lines)
		double angle = rand.nextDouble() * Math.PI * 2D;

		double dirX = Math.cos(angle);
		double dirZ = Math.sin(angle);

		// wide fracture band like Europa
		double width = 6.0 + rand.nextDouble() * 10.0;

		// loop chunk
		for(int x = 0; x < 16; x++) {
			for(int z = 0; z < 16; z++) {

				int worldX = chunkX * 16 + x;
				int worldZ = chunkZ * 16 + z;

				// distance from point to infinite line
				double dx = worldX - originX;
				double dz = worldZ - originZ;

				double cross =
					Math.abs(dx * dirZ - dz * dirX);

				if(cross > width)
					continue;

				int surfaceY =
					getSurfaceY(x, z, blocks);

				if(surfaceY < 10)
					continue;

				// carve depth (deep center, soft edges)
				double t = 1.0 - (cross / width);

				int depth =
					(int)(8 + t * 18);

				// CARVE DOWN (this is the key fix)
				for(int y = surfaceY; y > surfaceY - depth; y--) {

					setBlock(blocks, x, y, z, Blocks.air);
				}

				// fracture floor
				setBlock(blocks, x, surfaceY - depth, z, crackBlock);

				// subtle edge uplift ONLY at boundary
				if(t > 0.7 && t < 0.85) {

					setBlock(blocks, x, surfaceY, z, ridgeBlock);
				}
			}}
	}

	private void generateFractureBand(
		Random rand,
		int chunkX,
		int chunkZ,
		Block[] blocks) {

		double startX =
			rand.nextInt(16);

		double startZ =
			rand.nextInt(16);

		// mostly straight
		double angle =
			rand.nextDouble()
				* Math.PI * 2D;

		// Europa fractures are LONG
		int length =
			60 + rand.nextInt(120);

		// broad lineae
		float width =
			8F + rand.nextFloat() * 12F;

		for(int step = 0;
			step < length;
			step++) {

			int x =
				(int)Math.round(startX);

			int z =
				(int)Math.round(startZ);

			carveFractureProfile(
				x,
				z,
				width,
				rand,
				blocks);

			// gentle wandering
			angle +=
				(rand.nextDouble() - 0.5D)
					* 0.05D;

			startX +=
				Math.cos(angle);

			startZ +=
				Math.sin(angle);

			if(startX < -16
				|| startX > 32
				|| startZ < -16
				|| startZ > 32) {

				break;
			}
		}
	}

	private void carveFractureProfile(
		int centerX,
		int centerZ,
		float width,
		Random rand,
		Block[] blocks) {

		for(int dx = -(int)width;
			dx <= width;
			dx++) {

			for(int dz = -(int)width;
				dz <= width;
				dz++) {

				int x = centerX + dx;
				int z = centerZ + dz;

				if(x < 0
					|| z < 0
					|| x >= 16
					|| z >= 16)
					continue;

				double dist =
					Math.sqrt(dx * dx + dz * dz);

				if(dist > width)
					continue;

				int surfaceY =
					getSurfaceY(
						x,
						z,
						blocks);

				if(surfaceY < 20)
					continue;

				double normalized =
					dist / width;

				/*
				 * Europa fracture profile:
				 *
				 * subtle ridge  \__deep ravine__/ subtle ridge
				 *
				 * NOT spikes
				 */

				// smooth depression curve
				double curve =
					1.0D - normalized;

				// make ravine deeper in center
				int ravineDepth =
					(int)(
						Math.pow(curve, 2.2D)
							* (12 + rand.nextInt(10))
					);

				// carve INTO terrain
				for(int y = surfaceY;
					y > surfaceY - ravineDepth;
					y--) {

					setBlock(
						blocks,
						x,
						y,
						z,
						Blocks.air);
				}

				// expose packed ice walls/floor
				if(surfaceY - ravineDepth > 0) {

					setBlock(
						blocks,
						x,
						surfaceY - ravineDepth,
						z,
						crackBlock);
				}

				/*
				 * very subtle uplifted ridges
				 * near fracture edges
				 */
				if(normalized > 0.65D
					&& normalized < 0.90D) {

					int ridgeHeight =
						1 + rand.nextInt(2);

					for(int y = surfaceY + 1;
						y <= surfaceY + ridgeHeight;
						y++) {

						setBlock(
							blocks,
							x,
							y,
							z,
							ridgeBlock);
					}
				}

				// disturbed cracked ice
				if(normalized > 0.90D) {

					setBlock(
						blocks,
						x,
						surfaceY,
						z,
						crackBlock);
				}
			}
		}

		// occasional chaos terrain
		if(rand.nextInt(120) == 0) {

			generateChaosTerrain(
				centerX,
				centerZ,
				rand,
				blocks);
		}
	}

	private void generateChaosTerrain(
		int centerX,
		int centerZ,
		Random rand,
		Block[] blocks) {

		int radius =
			4 + rand.nextInt(5);

		for(int dx = -radius;
			dx <= radius;
			dx++) {

			for(int dz = -radius;
				dz <= radius;
				dz++) {

				int x =
					centerX + dx;

				int z =
					centerZ + dz;

				if(x < 0
					|| z < 0
					|| x >= 16
					|| z >= 16)
					continue;

				double dist =
					Math.sqrt(dx * dx + dz * dz);

				if(dist > radius)
					continue;

				int surfaceY =
					getSurfaceY(
						x,
						z,
						blocks);

				if(surfaceY < 10)
					continue;

				// broken uplifted ice chunks
				int offset =
					rand.nextInt(3) - 4;

				for(int y = 0;
					y < 4;
					y++) {

					setBlock(
						blocks,
						x,
						surfaceY + offset + y,
						z,
						ridgeBlock);
				}
			}
		}
	}

	private int getSurfaceY(
		int x,
		int z,
		Block[] blocks) {

		for(int y = 255;
			y > 0;
			y--) {

			Block block =
				getBlock(
					blocks,
					x,
					y,
					z);

			if(block != null
				&& block != Blocks.air) {

				return y;
			}
		}

		return -1;
	}

	private Block getBlock(
		Block[] blocks,
		int x,
		int y,
		int z) {

		int index =
			(z * 16 + x)
				* 256
				+ y;

		if(index < 0
			|| index >= blocks.length)
			return null;

		return blocks[index];
	}

	private void setBlock(
		Block[] blocks,
		int x,
		int y,
		int z,
		Block block) {

		int index =
			(z * 16 + x)
				* 256
				+ y;

		if(index < 0
			|| index >= blocks.length)
			return;

		blocks[index] = block;
	}
}
