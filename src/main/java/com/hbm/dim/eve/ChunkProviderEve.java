package com.hbm.dim.eve;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.WorldConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.ChunkProviderCelestial;
import com.hbm.world.gen.terrain.MapGenBubble;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;


public class ChunkProviderEve extends ChunkProviderCelestial {

	private final MapGenBubble gas = new MapGenBubble(WorldConfig.eveGasSpawn);

	public ChunkProviderEve(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);
		reclamp = false;
		stoneBlock = ModBlocks.eve_rock;
		seaBlock = Blocks.lava;

		gas.block = ModBlocks.ore_gas;
		gas.meta = (byte)CelestialBody.getMeta(world);
		gas.replace = ModBlocks.eve_rock;
		gas.setSize(10, 17);
	}

	@Override
	public BlockMetaBuffer getChunkPrimer(int x, int z) {
		BlockMetaBuffer buffer = super.getChunkPrimer(x, z);
		gas.generate(this, worldObj, x, z, buffer.blocks, buffer.metas);

		// how many times do I gotta say BEEEEG
		return buffer;
	}
}
