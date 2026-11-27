package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class SettlementConfig {

    private static final String CATEGORY = CommonConfig.CATEGORY_STRUCTURES + ".settlements";
    public static volatile Values settings = new Values(new Configuration());

    private SettlementConfig() { }

    public static final class Values {
        public final boolean restaurants, stores, dataCenters, farms, houses, roads, neighborhoods;
        public final boolean diagnostics;
        public final int restaurantRarity, storeRarity, dataCenterRarity, farmRarity, houseRarity;
        public final int separation, neighborhoodSize, roadLength, terrainDepth, workPerTick;

        public Values(Configuration config) {
            restaurants = enabled(config, "restaurants", "McDonald's restaurants");
            stores = enabled(config, "stores", "Walmart stores");
            dataCenters = enabled(config, "dataCenters", "data centers");
            farms = enabled(config, "farms", "farms");
            houses = enabled(config, "houses", "suburban houses");
            roads = enabled(config, "roads", "roads connecting natural sites");
            neighborhoods = enabled(config, "neighborhoods", "multi-house neighborhoods when roads are enabled");
            restaurantRarity = rarity(config, "restaurantFrequency", 384);
            storeRarity = rarity(config, "storeFrequency", 1536);
            dataCenterRarity = rarity(config, "dataCenterFrequency", 896);
            farmRarity = rarity(config, "farmFrequency", 320);
            houseRarity = rarity(config, "houseFrequency", 384);
            separation = config.getInt("minimumSiteDistance", CATEGORY, 96, 0, 1024,
                    "Minimum horizontal gap in blocks between independently generated sites. Lots sharing a road are one site.");
            neighborhoodSize = config.getInt("maximumNeighborhoodSize", CATEGORY, 4, 1, 8,
                    "Maximum number of houses in a natural neighborhood.");
            roadLength = config.getInt("maximumRoadNetworkSize", CATEGORY, 112, 19, 256,
                    "Maximum length in blocks of a natural road spine, including its ends.");
            terrainDepth = config.getInt("maximumTerrainPreparationDepth", CATEGORY, 6, 0, 8,
                    "Maximum surface cut or fill in blocks for natural sites. Structure wands prepare terrain at any depth.");
            workPerTick = config.getInt("maximumWorkPerTick", CATEGORY, 3072, 64, 16384,
                    "Placement work units per server-world tick. A placement or verification uses four units; a terrain column survey uses 64.");
            diagnostics = config.getBoolean("generationDiagnostics", CATEGORY, false,
                    "Log settlement candidates, rejections, terrain preparation, queue transitions and placement results on the server.");
        }

        private static boolean enabled(Configuration config, String key, String description) {
            return config.getBoolean(key, CATEGORY, true, "Allow natural generation of " + description + ".");
        }

        private static int rarity(Configuration config, String key, int fallback) {
            return config.getInt(key, CATEGORY, fallback, 0, 1000000,
                    "One candidate per this many newly populated chunks on average, before biome, terrain and spacing checks. Zero disables it.");
        }
    }
}
