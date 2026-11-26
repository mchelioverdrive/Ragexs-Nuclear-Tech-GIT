package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class SquirrelConfig {
    private static final String CATEGORY = CommonConfig.CATEGORY_MOBS + ".squirrels";
    public static volatile Values settings = new Values(new Configuration());

    private SquirrelConfig() {}

    public static final class Values {
        public final boolean enabled, naturalSpawning, stapling;
        public final int spawnWeight, minGroupSize, maxGroupSize, localLimit, staplesPerAttachment;
        public final double health;

        public Values(Configuration config) {
            enabled = config.getBoolean("enabled", CATEGORY, true,
                    "Allow new squirrels and player capture. Existing squirrels and attachments stay in the save.");
            naturalSpawning = config.getBoolean("naturalSpawning", CATEGORY, true,
                    "Allow natural squirrel packs in Overworld forests.");
            stapling = config.getBoolean("stapling", CATEGORY, true,
                    "Allow the stapler to attach a carried squirrel to a log.");
            spawnWeight = config.getInt("spawnWeight", CATEGORY, 5, 0, 100, "Passive spawn weight. Zero disables new packs.");
            minGroupSize = config.getInt("minGroupSize", CATEGORY, 1, 1, 4, "Minimum rolled pack size.");
            maxGroupSize = config.getInt("maxGroupSize", CATEGORY, 3, minGroupSize, 4, "Maximum rolled pack size.");
            localLimit = config.getInt("localLimit", CATEGORY, 12, 0, 64, "Maximum loaded squirrels within 48 horizontal blocks of a natural spawn. Zero disables new packs.");
            staplesPerAttachment = config.getInt("staplesPerAttachment", CATEGORY, 1, 1, 64, "Staples spent on each successful attachment.");
            double value = config.get(CATEGORY, "health", 4D, "Squirrel maximum health in HP.", 1D, 40D).getDouble(4D);
            health = Double.isFinite(value) ? Math.max(1D, Math.min(40D, value)) : 4D;
        }
    }
}
