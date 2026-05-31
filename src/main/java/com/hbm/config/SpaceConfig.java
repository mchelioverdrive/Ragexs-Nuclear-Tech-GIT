package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public class SpaceConfig {

	//thanks minecraft, for making the biome id limit 127(!!) because apparently anything after that spawns in the overworld.
	//THANKS MOJANG..

	//infinite ids mod exists

	//hey instead of blaming people how about we shift the dimensions ID if a dimension id is already taken by say another mod or a error occurs

	public static int dunaoilSpawn = 100;

	//xradar compat
	//some idiot somehow managed to fuck this up, I don't know how, I don't know why, I don't even know when
	//If you can't figure out how to change a dimension ID via config you're absolutely tiktok rotmaxxing
	//seek fucking help

	//DIM:
	public static int moonDimension = 16;
	public static int dunaDimension = 17;
	public static int ikeDimension = 18;
	public static int eveDimension = 19;
	public static int dresDimension = 20;
	public static int mohoDimension = 21;
	public static int laytheDimension = 23;
	public static int orbitDimension = 24;
	public static int tektoDimension = 25;
	public static int jupiterDimension = 22;
	public static int sunDimension = 103;
	public static int saturnDimension = 102;
	public static int uranusDimension = 107;


	//BIOME:
	public static int orbitBiome = 42;
	public static int moonBiome = 111;
	public static int dunaBiome = 112;
	public static int dunaLowlandsBiome = 113;
	public static int dunaPolarBiome = 114;
	public static int dunaHillsBiome = 115;
	public static int dunaPolarHillsBiome = 116;
	public static int eveBiome = 117;
	public static int eveMountainsBiome = 118;
	public static int eveOceanBiome = 119;
	public static int eveSeismicBiome = 125;
	public static int eveRiverBiome = 110;
	public static int dresBiome = 120;
	public static int dresBasins = 121;
	public static int mohoBiome = 122;
	public static int mohoBasaltBiome = 43;
	public static int laytheBiome = 123;
	public static int laytheOceanBiome = 124;
	public static int laythePolarBiome = 126;
	public static int ikeBiome = 127;
	public static int sunBiome = 104;
	public static int jupiterBiome = 105;
	public static int saturnBiome = 106;
	public static int uranusBiome = 108;
	//public static int laytheFractureBiome = 106;












	public static boolean allowNetherPortals = false;

	public static boolean enableVolcanoGen = true;

	public static int maxProbeDistance = 32_000;

	public static void loadFromConfig(Configuration config) {

		final String CATEGORY_DIM = CommonConfig.CATEGORY_DIMS;
		allowNetherPortals = CommonConfig.createConfigBool(config, CATEGORY_DIM, "17.00_allowNetherPortals", "Should Nether portals function on other celestial bodies?", false);

		moonDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.01_moonDimension", "Mun dimension ID", moonDimension);
		dunaDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.02_dunaDimension", "Duna dimension ID", dunaDimension);
		ikeDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.03_ikeDimension", "Ike dimension ID", ikeDimension);
		eveDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.04_eveDimension", "Eve dimension ID", eveDimension);
		dresDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.05_dresDimension", "Dres dimension ID", dresDimension);
		mohoDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.06_mohoDimension", "Moho dimension ID", mohoDimension);
		//minmusDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.07_minmusDimension", "Minmus dimension ID", minmusDimension);
		laytheDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.08_laytheDimension", "Laythe dimension ID", laytheDimension);
		orbitDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.09_orbitDimension", "Orbital dimension ID", orbitDimension);
		tektoDimension = CommonConfig.createConfigInt(config, CATEGORY_DIM, "17.10_tektoDimension", "Tekto dimension ID", tektoDimension);
		sunDimension = CommonConfig.createConfigInt(
			config,
			CATEGORY_DIM,
			"17.11_sunDimension",
			"Sun dimension ID",
			sunDimension
		);
		jupiterDimension = CommonConfig.createConfigInt(
			config,
			CATEGORY_DIM,
			"17.12_jupiterDimension",
			"Jupiter dimension ID",
			jupiterDimension
		);
		saturnDimension = CommonConfig.createConfigInt(
			config,
			CATEGORY_DIM,
			"17.13_saturnDimension",
			"Saturn dimension ID",
			saturnDimension
		);
		uranusDimension = CommonConfig.createConfigInt(
			config,
			CATEGORY_DIM,
			"17.14_uranusDimension",
			"Uranus dimension ID",
			uranusDimension
		);

		final String CATEGORY_GENERAL = CommonConfig.CATEGORY_GENERAL;
		maxProbeDistance = CommonConfig.createConfigInt(config, CATEGORY_GENERAL, "1.90_maxProbeDistance", "How far from the center of the dimension can probes generate landing coordinates", maxProbeDistance);
		enableVolcanoGen = CommonConfig.createConfigBool(config, CATEGORY_GENERAL, "1.91_enableVolcanoGen", "Should volcanoes be active when spawning, disabling will prevent natural volcanoes from spewing lava and growing", enableVolcanoGen);

		final String CATEGORY_BIOME = CommonConfig.CATEGORY_BIOMES;
		moonBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.02_moonBiome", "Mun Biome ID", moonBiome);
		dunaBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.03_dunaBiome", "Duna Biome ID", dunaBiome);
		dunaLowlandsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.04_dunaLowlandsBiome", "Duna Lowlands Biome ID", dunaLowlandsBiome);
		dunaPolarBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.05_dunaPolarBiome", "Duna Polar Biome ID", dunaPolarBiome);
		dunaHillsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.06_dunaHillsBiome", "Duna Hills Biome ID", dunaHillsBiome);
		dunaPolarHillsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.07_dunaPolarHillsBiome", "Duna Polar Hills Biome ID", dunaPolarHillsBiome);
		eveBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.08_eveBiome", "Eve Biome ID", eveBiome);
		eveMountainsBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.09_eveMountainsBiome", "Eve Mountains Biome ID", eveMountainsBiome);
		eveOceanBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.10_eveOceanBiome", "Eve Ocean Biome ID", eveOceanBiome);
		eveSeismicBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.12_eveSeismicBiome", "Eve Seismic Biome ID", eveSeismicBiome);
		eveRiverBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.24_eveRiverBiome", "Eve River Biome ID", eveRiverBiome);
		ikeBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.13_ikeBiome", "Ike Biome ID", ikeBiome);
		laytheBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.14_laytheBiome", "Laythe Biome ID", laytheBiome);
		laytheOceanBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.15_laytheOceanBiome", "Laythe Ocean Biome ID", laytheOceanBiome);
		laythePolarBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.16_laythePolarBiome", "Laythe Polar Biome ID", laythePolarBiome);
		//minmusBasins = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.17_minmusBasinsBiome", "Minmus Basins Biome ID", minmusBasins);
		//minmusBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.18_minmusBiome", "Minmus Biome ID", minmusBiome);
		mohoBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.19_mohoBiome", "Moho Biome ID", mohoBiome);
		dresBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.20_dresBiome", "Dres Biome ID", dresBiome);
		dresBasins = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.21_dresBasinsBiome", "Dres Basins Biome ID", dresBasins);
		mohoBasaltBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.22_mohoBasaltBiome", "Moho Basalt Biome ID", mohoBasaltBiome);
		orbitBiome = CommonConfig.createConfigInt(config, CATEGORY_BIOME, "16.23_orbitBiome", "Space Biome ID", orbitBiome);
		sunBiome = CommonConfig.createConfigInt(
			config,
			CATEGORY_BIOME,
			"16.25_sunBiome",
			"Sun Biome ID",
			sunBiome
		);
		jupiterBiome = CommonConfig.createConfigInt(
			config,
			CATEGORY_BIOME,
			"16.26_jupiterBiome",
			"Jupiter Biome ID",
			jupiterBiome
		);
		saturnBiome = CommonConfig.createConfigInt(
			config,
			CATEGORY_BIOME,
			"16.27_saturnBiome",
			"Saturn Biome ID",
			saturnBiome
		);
		//laytheFractureBiome = CommonConfig.createConfigInt(
		//	config,
		//	CATEGORY_BIOME,
		//	"16.26_laytheFractureBiome",
		//	"Europa Fracture Biome ID",
		//	laytheFractureBiome
		//);

		java.util.Set<Integer> used =
			new java.util.HashSet<Integer>();

		moonDimension =
			findFreeDimensionId(
				moonDimension,
				used
			);

		dunaDimension =
			findFreeDimensionId(
				dunaDimension,
				used
			);

		ikeDimension =
			findFreeDimensionId(
				ikeDimension,
				used
			);

		eveDimension =
			findFreeDimensionId(
				eveDimension,
				used
			);

		dresDimension =
			findFreeDimensionId(
				dresDimension,
				used
			);

		mohoDimension =
			findFreeDimensionId(
				mohoDimension,
				used
			);

		laytheDimension =
			findFreeDimensionId(
				laytheDimension,
				used
			);

		orbitDimension =
			findFreeDimensionId(
				orbitDimension,
				used
			);

		tektoDimension =
			findFreeDimensionId(
				tektoDimension,
				used
			);

		jupiterDimension =
			findFreeDimensionId(
				jupiterDimension,
				used
			);

		sunDimension =
			findFreeDimensionId(
				sunDimension,
				used
			);

		saturnDimension =
			findFreeDimensionId(
				saturnDimension,
				used
			);

		uranusDimension =
			findFreeDimensionId(
				uranusDimension,
				used
			);

	}

	public static int findFreeDimensionId(
		int preferredId,
		java.util.Set<Integer> used
	) {

		int id =
			preferredId;

		while(
			used.contains(id)
				||
				net.minecraftforge.common.DimensionManager
					.isDimensionRegistered(id)
		) {
			id++;
		}

		used.add(id);

		return id;
	}

}
