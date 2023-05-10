package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public class WorldConfig {

	public static boolean overworldOre = true;
	public static boolean netherOre = true;
	public static boolean endOre = true;
	
	public static int uraniumSpawn = 6;
	public static int thoriumSpawn = 7;
	public static int titaniumSpawn = 8;
	public static int sulfurSpawn = 5;
	public static int aluminiumSpawn = 7;
	public static int copperSpawn = 12;
	public static int nickelSpawn = 9;
	public static int zincSpawn = 8;
	public static int mineralSpawn = 5;
	public static int fluoriteSpawn = 6;
	public static int niterSpawn = 6;
	public static int tungstenSpawn = 10;
	public static int leadSpawn = 6;
	public static int berylliumSpawn = 6;
	public static int ligniteSpawn = 2;
	public static int asbestosSpawn = 4;
	public static int rareSpawn = 6;
	public static int lithiumSpawn = 6;
	public static int cinnebarSpawn = 1;
	public static int oilcoalSpawn = 128;
	public static int gassshaleSpawn = 5;
	public static int gasbubbleSpawn = 4;
	public static int explosivebubbleSpawn = 8;
	public static int cobaltSpawn = 2;
	public static int oilSpawn = 100;
	public static int bedrockOilSpawn = 200;
	public static int meteoriteSpawn = 500;

	public static int dunaoilSpawn = 100;
	
	public static int bedrockIronSpawn = 200;
	public static int bedrockCopperSpawn = 200;
	public static int bedrockBoraxSpawn = 300;
	public static int bedrockAsbestosSpawn = 300;
	public static int bedrockNiobiumSpawn = 300;
	public static int bedrockTitaniumSpawn = 400;
	public static int bedrockTungstenSpawn = 300;
	public static int bedrockGoldSpawn = 500;
	public static int bedrockBismuthSpawn = 400;
	public static int bedrockCadmiumSpawn = 300;

	public static int ironClusterSpawn = 4;
	public static int titaniumClusterSpawn = 2;
	public static int aluminiumClusterSpawn = 3;
	public static int copperClusterSpawn = 4;
	public static int alexandriteSpawn = 100;

	public static int malachiteSpawn = 1;
	public static int limestoneSpawn = 1;

	public static int netherUraniumuSpawn = 8;
	public static int netherTungstenSpawn = 10;
	public static int netherSulfurSpawn = 26;
	public static int netherPhosphorusSpawn = 24;
	public static int netherCoalSpawn = 8;
	public static int netherPlutoniumSpawn = 8;
	public static int netherCobaltSpawn = 2;

	public static int endTikiteSpawn = 8;

	public static boolean enableRandom = false;
	public static int randomSpawn = 0;

	public static int radioStructure = 500;
	public static int antennaStructure = 250;
	public static int atomStructure = 500;
	public static int vertibirdStructure = 500;
	public static int dungeonStructure = 64;
	public static int relayStructure = 500;
	public static int satelliteStructure = 500;
	public static int bunkerStructure = 1000;
	public static int siloStructure = 1000;
	public static int factoryStructure = 1000;
	public static int dudStructure = 500;
	public static int spaceshipStructure = 1000;
	public static int barrelStructure = 5000;
	public static int geyserWater = 3000;
	public static int geyserChlorine = 3000;
	public static int geyserVapor = 500;
	public static int meteorStructure = 15000;
	public static int capsuleStructure = 100;
	public static int arcticStructure = 500;
	public static int jungleStructure = 2000;
	public static int pyramidStructure = 4000;

	public static int broadcaster = 5000;
	public static int minefreq = 64;
	public static int radfreq = 5000;
	public static int vaultfreq = 2500;

	public static boolean enableMeteorStrikes = true;
	public static boolean enableMeteorShowers = true;
	public static boolean enableMeteorTails = true;
	public static boolean enableSpecialMeteors = true;
	public static int meteorStrikeChance = 20 * 60 * 180;
	public static int meteorShowerChance = 20 * 60 * 5;
	public static int meteorShowerDuration = 6000;
	
	public static int moonDimension = 15;
	public static int dunaDimension = 16;
	public static int ikeDimension = 17;
	public static int eveDimension = 18;
	public static int mohoDimension = 20;
	
	public static int moonBiome = 111;
	public static int dunaBiome = 112;
	public static int dunaLowlandsBiome = 113;
	public static int dunaPolarBiome = 114;
	public static int dunaHillsBiome = 115;
	public static int dunaPolarHillsBiome = 116;
	public static int eveBiome = 117;
	public static int eveMountainsBiome = 118;
	public static int eveOceanBiome = 119;
	public static int ikeBiome = 145;
	public static int ikecfreq = 90;
	public static int drescfreq = 90;

	
	public static int DresBiome = 120;
	public static int dreBasins = 121;
	public static int dresDimension = 19;
	
	public static int mohoBiome = 122;
	
	public static void loadFromConfig(Configuration config) {

		final String CATEGORY_OREGEN = CommonConfig.CATEGORY_ORES;
		
		overworldOre = CommonConfig.createConfigBool(config, CATEGORY_OREGEN, "2.D00_overworldOres", "General switch for whether overworld ores should be generated. Does not include special structures like oil.", true);
		netherOre = CommonConfig.createConfigBool(config, CATEGORY_OREGEN, "2.D01_netherOres", "General switch for whether nether ores should be generated.", true);
		endOre = CommonConfig.createConfigBool(config, CATEGORY_OREGEN, "2.D02_endOres", "General switch for whether end ores should be generated. Does not include special structures like trixite crystals.", true);
		
		uraniumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.00_uraniumSpawnrate", "Amount of uranium ore veins per chunk", 7);
		titaniumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.01_titaniumSpawnrate", "Amount of titanium ore veins per chunk", 8);
		sulfurSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.02_sulfurSpawnrate", "Amount of sulfur ore veins per chunk", 5);
		aluminiumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.03_aluminiumSpawnrate", "Amount of aluminium ore veins per chunk", 7);
		copperSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.04_copperSpawnrate", "Amount of copper ore veins per chunk", 12);
		fluoriteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.05_fluoriteSpawnrate", "Amount of fluorite ore veins per chunk", 6);
		niterSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.06_niterSpawnrate", "Amount of niter ore veins per chunk", 6);
		tungstenSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.07_tungstenSpawnrate", "Amount of tungsten ore veins per chunk", 10);
		leadSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.08_leadSpawnrate", "Amount of lead ore veins per chunk", 6);
		berylliumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.09_berylliumSpawnrate", "Amount of beryllium ore veins per chunk", 6);
		thoriumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.10_thoriumSpawnrate", "Amount of thorium ore veins per chunk", 7);
		ligniteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.11_ligniteSpawnrate", "Amount of lignite ore veins per chunk", 2);
		asbestosSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.12_asbestosSpawnRate", "Amount of asbestos ore veins per chunk", 2);
		lithiumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.13_lithiumSpawnRate", "Amount of schist lithium ore veins per chunk", 6);
		rareSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.14_rareEarthSpawnRate", "Amount of rare earth ore veins per chunk", 6);
		oilcoalSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.15_oilCoalSpawnRate", "Spawns an oily coal vein every nTH chunk", 128);
		gassshaleSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.16_gasShaleSpawnRate", "Amount of oil shale veins per chunk", 5);
		gasbubbleSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.17_gasBubbleSpawnRate", "Spawns a gas bubble every nTH chunk", 4);
		cinnebarSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.18_cinnebarSpawnRate", "Amount of cinnebar ore veins per chunk", 1);
		cobaltSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.18_cobaltSpawnRate", "Amount of cobalt ore veins per chunk", 2);
		explosivebubbleSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.19_explosiveBubbleSpawnRate", "Spawns an explosive gas bubble every nTH chunk", 8);
		alexandriteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.20_alexandriteSpawnRate", "Spawns an alexandrite vein every nTH chunk", 100);
		oilSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.21_oilSpawnRate", "Spawns an oil bubble every nTH chunk", 100);
		bedrockOilSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.22_bedrockOilSpawnRate", "Spawns a bedrock oil node every nTH chunk", 200);
		meteoriteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.23_meteoriteSpawnRate", "Spawns a fallen meteorite every nTH chunk", 200);
		nickelSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.24_nickelSpawnrate", "Amount of nickel ore veins per chunk", 12);
		zincSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.25_zincSpawnrate", "Amount of zinc ore veins per chunk", 8);
		mineralSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.26_mineralSpawnrate", "Amount of mineral ore veins per chunk", 4);
		dunaoilSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.27S_oilSpawnRate", "Spawns an oil bubble every nTH chunk (on duna)", 100);

		bedrockIronSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B00_bedrockIronSpawn", "Spawns a bedrock iron deposit every nTH chunk", 200);
		bedrockCopperSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B01_bedrockCopperSpawn", "Spawns a bedrock copper deposit every nTH chunk", 200);
		bedrockBoraxSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B02_bedrockBoraxSpawn", "Spawns a bedrock borax deposit every nTH chunk", 300);
		bedrockAsbestosSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B03_bedrockAsbestosSpawn", "Spawns a bedrock asbestos deposit every nTH chunk", 300);
		bedrockNiobiumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B04_bedrockNiobiumSpawn", "Spawns a bedrock niobium deposit every nTH chunk", 300);
		bedrockTitaniumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B05_bedrockTitaniumSpawn", "Spawns a bedrock titanium deposit every nTH chunk", 500);
		bedrockTungstenSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B06_bedrockTungstenSpawn", "Spawns a bedrock tungsten deposit every nTH chunk", 300);
		bedrockGoldSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B07_bedrockGoldSpawn", "Spawns a bedrock gold deposit every nTH chunk", 500);
		bedrockBismuthSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B08_bedrockBismuthSpawn", "Spawns a bedrock bismuth deposit every nTH chunk", 400);
		bedrockCadmiumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.B09_bedrockCadmiumSpawn", "Spawns a bedrock cadmium deposit every nTH chunk", 400);


		ironClusterSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.C00_ironClusterSpawn", "Amount of iron cluster veins per chunk", 4);
		titaniumClusterSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.C01_titaniumClusterSpawn", "Amount of titanium cluster veins per chunk", 2);
		aluminiumClusterSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.C02_aluminiumClusterSpawn", "Amount of aluminium cluster veins per chunk", 3);
		copperClusterSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.C03_copperClusterSpawn", "Amount of copper cluster veins per chunk", 4);

		malachiteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.L01_malachiteSpawn", "Amount of malachite block veins per chunk", 1);
		limestoneSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.L02_limestoneSpawn", "Amount of limestone block veins per chunk", 1);

		netherUraniumuSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N00_uraniumSpawnrate", "Amount of nether uranium per chunk", 8);
		netherTungstenSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N01_tungstenSpawnrate", "Amount of nether tungsten per chunk", 10);
		netherSulfurSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N02_sulfurSpawnrate", "Amount of nether sulfur per chunk", 26);
		netherPhosphorusSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N03_phosphorusSpawnrate", "Amount of nether phosphorus per chunk", 24);
		netherCoalSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N04_coalSpawnrate", "Amount of nether coal per chunk", 8);
		netherPlutoniumSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N05_plutoniumSpawnrate", "Amount of nether plutonium per chunk, if enabled", 8);
		netherCobaltSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.N06_cobaltSpawnrate", "Amount of nether cobalt per chunk", 2);

		endTikiteSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.E00_tikiteSpawnrate", "Amount of end trixite per chunk", 8);

		enableRandom = CommonConfig.createConfigBool(config, CATEGORY_OREGEN, "2.R00_enableRandomOre", "Amount of random ore per chunk", false);
		randomSpawn = CommonConfig.createConfigInt(config, CATEGORY_OREGEN, "2.R01_randomOreSpawnrate", "Amount of random ore per chunk", 0);

		final String CATEGORY_DUNGEON = CommonConfig.CATEGORY_DUNGEONS;
		radioStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.00_radioSpawn", "Spawn radio station on every nTH chunk", 500);
		antennaStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.01_antennaSpawn", "Spawn antenna on every nTH chunk", 250);
		atomStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.02_atomSpawn", "Spawn power plant on every nTH chunk", 500);
		vertibirdStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.03_vertibirdSpawn", "Spawn vertibird on every nTH chunk", 500);
		dungeonStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.04_dungeonSpawn", "Spawn library dungeon on every nTH chunk", 64);
		relayStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.05_relaySpawn", "Spawn relay on every nTH chunk", 500);
		satelliteStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.06_satelliteSpawn", "Spawn satellite dish on every nTH chunk", 500);
		bunkerStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.07_bunkerSpawn", "Spawn bunker on every nTH chunk", 1000);
		siloStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.08_siloSpawn", "Spawn missile silo on every nTH chunk", 1000);
		factoryStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.09_factorySpawn", "Spawn factory on every nTH chunk", 1000);
		dudStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.10_dudSpawn", "Spawn dud on every nTH chunk", 500);
		spaceshipStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.11_spaceshipSpawn", "Spawn spaceship on every nTH chunk", 1000);
		barrelStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.12_barrelSpawn", "Spawn waste tank on every nTH chunk", 5000);
		broadcaster = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.13_broadcasterSpawn", "Spawn corrupt broadcaster on every nTH chunk", 5000);
		minefreq = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.14_landmineSpawn", "Spawn AP landmine on every nTH chunk", 64);
		radfreq = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.15_radHotspotSpawn", "Spawn radiation hotspot on every nTH chunk", 5000);
		vaultfreq = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.16_vaultSpawn", "Spawn locked safe on every nTH chunk", 2500);
		geyserWater = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.17_geyserWaterSpawn", "Spawn water geyser on every nTH chunk", 3000);
		geyserChlorine = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.18_geyserChlorineSpawn", "Spawn poison geyser on every nTH chunk", 3000);
		geyserVapor = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.19_geyserVaporSpawn", "Spawn vapor geyser on every nTH chunk", 500);
		meteorStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.20_meteorSpawn", "Spawn meteor dungeon on every nTH chunk", 15000);
		capsuleStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.21_capsuleSpawn", "Spawn landing capsule on every nTH chunk", 100);
		arcticStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.22_arcticVaultSpawn", "Spawn arctic code vault on every nTH chunk", 500);
		jungleStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.23_jungleDungeonSpawn", "Spawn jungle dungeon on every nTH chunk", 2000);
		pyramidStructure = CommonConfig.createConfigInt(config, CATEGORY_DUNGEON, "4.24_pyramidSpawn", "Spawn pyramid on every nTH chunk", 4000);

		final String CATEGORY_METEOR = CommonConfig.CATEGORY_METEORS;
		enableMeteorStrikes = CommonConfig.createConfigBool(config, CATEGORY_METEOR, "5.00_enableMeteorStrikes", "Toggles the spawning of meteors", true);
		enableMeteorShowers = CommonConfig.createConfigBool(config, CATEGORY_METEOR, "5.01_enableMeteorShowers", "Toggles meteor showers, which start with a 1% chance for every spawned meteor", true);
		enableMeteorTails = CommonConfig.createConfigBool(config, CATEGORY_METEOR, "5.02_enableMeteorTails", "Toggles the particle effect created by falling meteors", true);
		enableSpecialMeteors = CommonConfig.createConfigBool(config, CATEGORY_METEOR, "5.03_enableSpecialMeteors", "Toggles rare, special meteor types with different impact effects", true);
		meteorStrikeChance = CommonConfig.createConfigInt(config, CATEGORY_METEOR, "5.03_meteorStrikeChance", "The probability of a meteor spawning (an average of once every nTH ticks)", 20 * 60 * 60 * 5);
		meteorShowerChance = CommonConfig.createConfigInt(config, CATEGORY_METEOR, "5.04_meteorShowerChance", "The probability of a meteor spawning during meteor shower (an average of once every nTH ticks)", 20 * 60 * 15);
		meteorShowerDuration = CommonConfig.createConfigInt(config, CATEGORY_METEOR, "5.05_meteorShowerDuration", "Max duration of meteor shower in ticks", 20 * 60 * 30);

		final String CATEGORY_BIOME = CommonConfig.CATEGORY_BIOMES;
		moonDimension = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.00_moonDimension", "Mun Dimension ID", 15);
		dunaDimension = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.01_dunaDimension", "Duna Dimension ID", 16);
		ikeDimension = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.01_ikeDimension", "Ike Dimension ID", 17);
		eveDimension = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.01_eveDimension", "Eve Dimension ID", 18);
		dresDimension = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.01_dresDimension", "Dres Dimension ID", 19);
		moonBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.02_moonBiome", "Mun Biome ID", 111);
		dunaBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.03_dunaBiome", "Duna Biome ID", 112);
		dunaLowlandsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.04_dunaLowlandsBiome", "Duna Lowlands Biome ID", 113);
		dunaPolarBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.05_dunaPolarBiome", "Duna Polar Biome ID", 114);
		dunaHillsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.06_dunaHillsBiome", "Duna Hills Biome ID", 115);
		dunaPolarHillsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.07_dunaPolarHillsBiome", "Duna Polar Hills Biome ID", 116);
		eveBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.08_eveBiome", "Eve Biome ID", 117);
		eveMountainsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.09_eveMountainsBiome", "Eve Mountains Biome ID", 118);
		eveOceanBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.10_eveOceanBiome", "Eve Ocean Biome ID", 119);
		ikeBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.11_IkeBiome", "Ike Biome ID", 145);
		
		
		radioStructure = CommonConfig.setDefZero(radioStructure, 1000);
		antennaStructure = CommonConfig.setDefZero(antennaStructure, 1000);
		atomStructure = CommonConfig.setDefZero(atomStructure, 1000);
		vertibirdStructure = CommonConfig.setDefZero(vertibirdStructure, 1000);
		dungeonStructure = CommonConfig.setDefZero(dungeonStructure, 1000);
		relayStructure = CommonConfig.setDefZero(relayStructure, 1000);
		satelliteStructure = CommonConfig.setDefZero(satelliteStructure, 1000);
		bunkerStructure = CommonConfig.setDefZero(bunkerStructure, 1000);
		siloStructure = CommonConfig.setDefZero(siloStructure, 1000);
		factoryStructure = CommonConfig.setDefZero(factoryStructure, 1000);
		dudStructure = CommonConfig.setDefZero(dudStructure, 1000);
		spaceshipStructure = CommonConfig.setDefZero(spaceshipStructure, 1000);
		barrelStructure = CommonConfig.setDefZero(barrelStructure, 1000);
		geyserWater = CommonConfig.setDefZero(geyserWater, 1000);
		geyserChlorine = CommonConfig.setDefZero(geyserChlorine, 1000);
		geyserVapor = CommonConfig.setDefZero(geyserVapor, 1000);
		broadcaster = CommonConfig.setDefZero(broadcaster, 1000);
		minefreq = CommonConfig.setDefZero(minefreq, 1000);
		radfreq = CommonConfig.setDefZero(radfreq, 1000);
		vaultfreq = CommonConfig.setDefZero(vaultfreq, 1000);
		meteorStructure = CommonConfig.setDefZero(meteorStructure, 15000);
		jungleStructure = CommonConfig.setDefZero(jungleStructure, 1000);
		capsuleStructure = CommonConfig.setDefZero(capsuleStructure, 100);
		arcticStructure = CommonConfig.setDefZero(arcticStructure, 500);
		
		ikecfreq = CommonConfig.setDefZero(ikecfreq, 90);
		drescfreq = CommonConfig.setDefZero(drescfreq, 90);

		
		meteorStrikeChance = CommonConfig.setDef(meteorStrikeChance, 1000);
		meteorShowerChance = CommonConfig.setDef(meteorShowerChance, 1000);
	}

}
