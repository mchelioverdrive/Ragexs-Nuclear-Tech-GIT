package com.hbm.lib;

import com.hbm.world.gen.MapGenNTMFeatures;
import com.hbm.world.gen.NBTStructure;
import com.hbm.world.gen.NTMWorldGenerator;
import com.hbm.world.gen.component.BunkerComponents;
import com.hbm.world.gen.component.BunkerComponents.BunkerStart;
import com.hbm.world.gen.component.CivilianFeatures;
import com.hbm.world.gen.component.OfficeFeatures;
import com.hbm.world.gen.component.RuinFeatures;
import com.hbm.world.gen.component.SiloComponent;
import com.hbm.world.gen.terrain.OverworldLargeTerrain;
import com.hbm.world.gen.terrain.BedrockOilSurface;
import com.hbm.world.gen.structure.LegacyStructureGenerator;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.MinecraftForge;

public class HbmWorld {
	
	public static void mainRegistry() {
		initWorldGen();
	}
	
	public static NTMWorldGenerator worldGenerator;
	
	public static void initWorldGen() {

		//MapGenStructureIO.registerStructure(StructureStartTest.class, "HFR_STRUCTURE");
		//MapGenStructureIO.func_143031_a(StructureComponentTest.class, "HFR_COMPONENT");
		MapGenStructureIO.registerStructure(MapGenNTMFeatures.Start.class, "NTMFeatures");
		MapGenStructureIO.registerStructure(BunkerStart.class, "NTMBunker");
		NBTStructure.register();
		LegacyStructureGenerator.register();
		registerNTMFeatures();
		
		registerWorldGen(new HbmWorldGen(), 1);
		registerWorldGen(new LegacyStructureGenerator(), 1);
		registerWorldGen(new BedrockOilSurface(), 1);
		MinecraftForge.EVENT_BUS.register(new OverworldLargeTerrain());
		
		worldGenerator = new NTMWorldGenerator();
		registerWorldGen(worldGenerator, 1); //Ideally, move everything over from HbmWorldGen to NTMWorldGenerator
		MinecraftForge.EVENT_BUS.register(worldGenerator);
		//registerWorldGen(new WorldGenTest(), 1);
	}
	
	public static void registerWorldGen(IWorldGenerator nukerWorldGen, int weightedProbability) {
		GameRegistry.registerWorldGenerator(nukerWorldGen, weightedProbability);
	}
	
	/** Register structures in MapGenStructureIO */
	public static void registerNTMFeatures() {
		CivilianFeatures.registerComponents();
		OfficeFeatures.registerComponents();
		RuinFeatures.registerComponents();
		BunkerComponents.registerComponents();
		MapGenStructureIO.func_143031_a(SiloComponent.class, "NTMSiloComponent");
	}
}
