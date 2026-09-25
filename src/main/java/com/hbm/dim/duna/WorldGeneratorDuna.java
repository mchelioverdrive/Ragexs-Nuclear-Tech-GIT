package com.hbm.dim.duna;

import java.lang.ref.WeakReference;
import java.util.Random;

import com.hbm.blocks.BlockEnums;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.SpaceConfig;
import com.hbm.config.WorldConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.WorldTypeTeleport;
import com.hbm.lib.RefStrings;
import com.hbm.world.gen.NBTStructure.Definition;
import com.hbm.world.gen.NBTStructure.HeightStrategy;
import com.hbm.world.gen.NBTStructure.RegisteredStructureGenerator;
import com.hbm.world.generator.DungeonToolbox;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.util.ResourceLocation;

public class WorldGeneratorDuna implements IWorldGenerator {

	private RegisteredStructureGenerator martianBase;
	private WeakReference<World> martianWorld = new WeakReference<World>(null);

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		if(world.provider.dimensionId == SpaceConfig.dunaDimension) {
			if(world.getWorldInfo().getTerrainType() == WorldTypeTeleport.martian) {
				if(martianBase == null || martianWorld.get() != world) {
					Definition definition = new Definition("RTMMartianBase", new ResourceLocation(RefStrings.MODID, "structures/martian-base.nbt"),
						SpaceConfig.dunaDimension, 1, 0, 0, HeightStrategy.AVERAGE_SURFACE, 0, null,
						(w, x, z) -> x == 0 && z == 0 && w.getWorldInfo().getTerrainType() == WorldTypeTeleport.martian);
					martianBase = new RegisteredStructureGenerator(definition);
					martianWorld = new WeakReference<World>(world);
				}
				martianBase.generateStructures(world, random, chunkProvider, chunkX, chunkZ);
			}
			generateDuna(world, random, chunkX * 16, chunkZ * 16);
		}
	}

	private void generateDuna(World world, Random rand, int i, int j) {
		int meta = CelestialBody.getMeta(world);

		//if(WorldConfig.dunaOilSpawn > 0 && rand.nextInt(WorldConfig.dunaOilSpawn) == 0) {
		//	int randPosX = i + rand.nextInt(16);
		//	int randPosY = rand.nextInt(25);
		//	int randPosZ = j + rand.nextInt(16);
//
		//	OilBubble.spawnOil(world, randPosX, randPosY, randPosZ, 10 + rand.nextInt(7), ModBlocks.ore_oil, meta, ModBlocks.duna_rock);
		//}
		//fake


		//iron
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.nickelSpawn, 8, 1, 43, ModBlocks.ore_iron, meta, ModBlocks.duna_rock);
		//zinc
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.titaniumSpawn, 9, 4, 27, ModBlocks.ore_zinc, meta, ModBlocks.duna_rock);
		//titanium
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.titaniumSpawn, 9, 4, 27, ModBlocks.ore_titanium, meta, ModBlocks.duna_rock);
		//Aluminum
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.aluminiumSpawn, 8, 4, 27, ModBlocks.ore_aluminium, meta, ModBlocks.duna_rock);
		//Copper
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.copperSpawn, 8, 4, 27, ModBlocks.ore_copper, meta, ModBlocks.duna_rock);
		//Cobalt
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.cobaltSpawn, 8, 4, 17, ModBlocks.ore_cobalt, meta, ModBlocks.duna_rock);
		//Lithium
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.lithiumSpawn, 2, 60, 17, ModBlocks.ore_lithium, meta, ModBlocks.duna_rock);
		//Gold
		DungeonToolbox.generateOre(world, rand, i, j, WorldConfig.cobaltSpawn, 2, 4, 12, ModBlocks.ore_gold, meta, ModBlocks.duna_rock);

		//Hematite
		DungeonToolbox.generateOre(world, rand, i, j, 16, 12, 25, 30, ModBlocks.stone_resource, BlockEnums.EnumStoneType.HEMATITE.ordinal(), ModBlocks.duna_rock);

		// Basalt rich in minerals, but only in basaltic caves!
		//THERES NO FUCKING FLUORITE OR ASBESTOS ON MARS YOU OAF
		DungeonToolbox.generateOre(world, rand, i, j, 12, 6, 0, 16, ModBlocks.ore_basalt, 0, ModBlocks.basalt);
		//DungeonToolbox.generateOre(world, rand, i, j, 8, 8, 0, 16, ModBlocks.ore_basalt, 1, ModBlocks.basalt);
		//DungeonToolbox.generateOre(world, rand, i, j, 8, 9, 0, 16, ModBlocks.ore_basalt, 2, ModBlocks.basalt);
		DungeonToolbox.generateOre(world, rand, i, j, 2, 4, 0, 16, ModBlocks.ore_basalt, 3, ModBlocks.basalt);
		DungeonToolbox.generateOre(world, rand, i, j, 6, 10, 0, 16, ModBlocks.ore_basalt, 4, ModBlocks.basalt);


	}

}
