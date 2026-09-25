package com.hbm.world.gen.terrain;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.config.WorldConfig;
import com.hbm.dim.WorldProviderCelestial;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

/** Deposits large blobs in the generated chunk before ordinary population. */
public class OverworldLargeTerrain {

	private final MapGenBubble oil = new MapGenBubble(WorldConfig.oilSpawn);
	private final MapGenBubble oilSand = new MapGenBubble(200);
	private final MapGenSellafield sellafield = new MapGenSellafield();
	private final MapGenBedrockOil bedrockOil = new MapGenBedrockOil();

	public OverworldLargeTerrain() {
		oil.block = ModBlocks.ore_oil;
		oil.replace = Blocks.stone;
		oil.setSize(10, 17);

		oilSand.block = ModBlocks.ore_oil_sand;
		oilSand.replace = Blocks.sand;
		oilSand.surface = true;
		oilSand.fuzzy = true;
		oilSand.setSize(15, 46);
		oilSand.canSpawn = biome -> !biome.canSpawnLightningBolt() && biome.temperature >= 1.5F;
	}

	@SubscribeEvent
	public void onChunkLoad(ChunkEvent.Load event) {
		World world = event.world;
		Chunk chunk = event.getChunk();
		if(world.isRemote || chunk.isTerrainPopulated || world.provider instanceof WorldProviderHell || world.provider instanceof WorldProviderEnd) return;
		boolean celestial = world.provider instanceof WorldProviderCelestial;
		if(!celestial && world.provider.dimensionId != 0 && !GeneralConfig.enableMDOres) return;

		bedrockOil.generate(world.getChunkProvider(), world, chunk);
		if(celestial) return;
		oil.generate(world.getChunkProvider(), world, chunk);

		boolean dungeons = GeneralConfig.enableDungeons == 1
			|| (GeneralConfig.enableDungeons != 0 && world.getWorldInfo().isMapFeaturesEnabled());
		if(world.provider.dimensionId == 0 && dungeons) {
			oilSand.generate(world.getChunkProvider(), world, chunk);
			if(GeneralConfig.enableRad) sellafield.generate(world.getChunkProvider(), world, chunk);
		}
	}
}
