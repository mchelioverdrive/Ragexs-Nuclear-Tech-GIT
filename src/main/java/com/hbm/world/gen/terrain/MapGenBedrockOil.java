package com.hbm.world.gen.terrain;

import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.WorldConfig;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.MapGenBase;

/** Bedrock oil and its porous veins are written to the owned chunk only. */
public class MapGenBedrockOil extends MapGenBase {
	private Chunk chunk;
	private boolean changed;

	public MapGenBedrockOil() { range = 2; }

	public static long sourceSeed(World world, int chunkX, int chunkZ) {
		long value = world.getSeed() ^ (long)world.provider.dimensionId * 0x9E3779B97F4A7C15L;
		value += (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + 0x51EAD00DL;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		return value ^ value >>> 33;
	}

	public static boolean selected(World world, int chunkX, int chunkZ) {
		int frequency = WorldConfig.bedrockOilSpawn;
		return frequency > 0 && (sourceSeed(world, chunkX, chunkZ) & Long.MAX_VALUE) % frequency == 0;
	}

	public void generate(IChunkProvider provider, World world, Chunk target) {
		chunk = target;
		changed = false;
		try {
			func_151539_a(provider, world, target.xPosition, target.zPosition, null);
			if(changed) target.setChunkModified();
		} finally {
			chunk = null;
			worldObj = null;
		}
	}

	@Override
	protected void func_151538_a(World world, int sourceX, int sourceZ, int targetX, int targetZ, Block[] unused) {
		if(!selected(world, sourceX, sourceZ)) return;
		Random random = new Random(sourceSeed(world, sourceX, sourceZ));
		int centerX = (sourceX << 4) + random.nextInt(16);
		int centerZ = (sourceZ << 4) + random.nextInt(16);
		for(int x = Math.max(targetX << 4, centerX - 4); x <= Math.min((targetX << 4) + 15, centerX + 4); x++) {
			for(int z = Math.max(targetZ << 4, centerZ - 4); z <= Math.min((targetZ << 4) + 15, centerZ + 4); z++) {
				for(int y = 0; y <= 4; y++) {
					if(Math.abs(x - centerX) + y + Math.abs(z - centerZ) > 6) continue;
					Block current = chunk.getBlock(x & 15, y, z & 15);
					if(current.isReplaceableOreGen(world, x, y, z, Blocks.stone) || current.isReplaceableOreGen(world, x, y, z, Blocks.bedrock))
						set(x, y, z, ModBlocks.ore_bedrock_oil);
				}
			}
		}

		// Same sixteen eight-step ellipsoid veins as WorldGenMinable, clipped to this chunk.
		for(int vein = 0; vein < 16; vein++) {
			int x = (sourceX << 4) + random.nextInt(16);
			int y = 10 + random.nextInt(50);
			int z = (sourceZ << 4) + random.nextInt(16);
			generateVein(random, x, y, z, targetX, targetZ);
		}
	}

	private void generateVein(Random random, int x, int y, int z, int targetX, int targetZ) {
		float angle = random.nextFloat() * (float)Math.PI;
		double x0 = x + 8 + MathHelper.sin(angle);
		double x1 = x + 8 - MathHelper.sin(angle);
		double z0 = z + 8 + MathHelper.cos(angle);
		double z1 = z + 8 - MathHelper.cos(angle);
		double y0 = y + random.nextInt(3) - 2;
		double y1 = y + random.nextInt(3) - 2;
		for(int step = 0; step <= 8; step++) {
			double cx = x0 + (x1 - x0) * step / 8.0D;
			double cy = y0 + (y1 - y0) * step / 8.0D;
			double cz = z0 + (z1 - z0) * step / 8.0D;
			double size = (MathHelper.sin(step * (float)Math.PI / 8.0F) + 1.0D) * random.nextDouble() * 0.5D + 1.0D;
			int minX = Math.max(targetX << 4, MathHelper.floor_double(cx - size / 2));
			int maxX = Math.min((targetX << 4) + 15, MathHelper.floor_double(cx + size / 2));
			int minZ = Math.max(targetZ << 4, MathHelper.floor_double(cz - size / 2));
			int maxZ = Math.min((targetZ << 4) + 15, MathHelper.floor_double(cz + size / 2));
			for(int bx = minX; bx <= maxX; bx++) for(int by = MathHelper.floor_double(cy - size / 2); by <= MathHelper.floor_double(cy + size / 2); by++) {
				if(by < 0 || by >= 256) continue;
				double dx = (bx + 0.5D - cx) / (size / 2.0D);
				double dy = (by + 0.5D - cy) / (size / 2.0D);
				if(dx * dx + dy * dy >= 1.0D) continue;
				for(int bz = minZ; bz <= maxZ; bz++) {
					double dz = (bz + 0.5D - cz) / (size / 2.0D);
					if(dx * dx + dy * dy + dz * dz < 1.0D) {
						Block current = chunk.getBlock(bx & 15, by, bz & 15);
						if(current.isReplaceableOreGen(worldObj, bx, by, bz, Blocks.stone)) set(bx, by, bz, ModBlocks.stone_porous);
					}
				}
			}
		}
	}

	private void set(int x, int y, int z, Block block) {
		ExtendedBlockStorage storage = chunk.getBlockStorageArray()[y >> 4];
		if(storage == null) storage = chunk.getBlockStorageArray()[y >> 4] = new ExtendedBlockStorage(y & ~15, !chunk.worldObj.provider.hasNoSky);
		storage.func_150818_a(x & 15, y & 15, z & 15, block);
		storage.setExtBlockMetadata(x & 15, y & 15, z & 15, 0);
		changed = true;
	}
}
