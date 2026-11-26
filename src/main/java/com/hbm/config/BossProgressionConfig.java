package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class BossProgressionConfig {
    public static final String CATEGORY = CommonConfig.CATEGORY_MOBS + ".bossProgression";
    public static volatile Values settings = new Values(new Configuration());

    private BossProgressionConfig() { }

    public static final class Values {
        public final boolean enabled, requirePrecedingBoss, enableStructures, locationRewards, repeatEncounters;
        public final boolean enderDragon, witherStorm, prismarineColossus, giantGhast, giantMagmaGolem, giantEnderman;
        public final int maximumActive, minimumSeparation;

        public Values(Configuration config) {
            enabled = flag(config, "enabled", "Enable the shared Story Mode gates and arena activations.", true);
            requirePrecedingBoss = flag(config, "requirePrecedingBoss", "Require the preceding enabled boss's saved defeat.", true);
            enableStructures = flag(config, "enableStructures", "Reserve new Giant Ghast, Giant Magma Golem and Giant Enderman arenas.", true);
            locationRewards = flag(config, "locationRewards", "Drop a survey journal on the first eligible defeat when the next boss has a structure.", true);
            repeatEncounters = flag(config, "repeatEncounters", "Allow new giant encounters after their world milestone is completed.", false);
            maximumActive = config.getInt("maximumActive", CATEGORY, 1, 0, 16,
                "World-wide cap per giant for the new arena encounters, including unloaded fights. Zero prevents activation.");
            minimumSeparation = config.getInt("minimumSeparation", CATEGORY, 2048, 128, 65536,
                "Minimum distance in blocks between new giant arenas in the same dimension.");
            enderDragon = flag(config, "enderDragon", "Require the Dragon milestone in the chain. Vanilla End spawning is unchanged.", true);
            witherStorm = flag(config, "witherStorm", "Enable ordinary Wither Storm rituals and require its milestone in the chain.", true);
            prismarineColossus = flag(config, "prismarineColossus", "Enable ordinary Colossus encounters and require its milestone in the chain.", true);
            giantGhast = flag(config, "giantGhast", "Enable Giant Ghast encounters and require their milestone in the chain.", true);
            giantMagmaGolem = flag(config, "giantMagmaGolem", "Enable Giant Magma Golem encounters and require their milestone in the chain.", true);
            giantEnderman = flag(config, "giantEnderman", "Enable Giant Enderman encounters and require their milestone in the chain.", true);
        }

        private static boolean flag(Configuration config, String key, String comment, boolean defaultValue) {
            return config.get(CATEGORY, key, defaultValue, comment).getBoolean(defaultValue);
        }
    }
}
