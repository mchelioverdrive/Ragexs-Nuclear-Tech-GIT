package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class SquirrelStaplerConfig {
    public static final String CATEGORY = CommonConfig.CATEGORY_MOBS + ".squirrelstapler";
    public static volatile Values settings = new Values(new Configuration());

    private SquirrelStaplerConfig() {}

    public static final class Values {
        public final boolean enabled, ghosts, bears, events, suppressHostiles, suppressStructures, suppressGemini, fog;
        public final int days, normalRequired, largeRequired, finaleSeconds, fogDistance;

        public Values(Configuration config) {
            enabled = flag(config, "enabled", "Enable the campaign in Squirrel Stapler worlds.");
            days = config.getInt("days", CATEGORY, 5, 1, 30, "Campaign length for new or reset campaigns.");
            normalRequired = config.getInt("normalSquirrelsPerDay", CATEGORY, 5, 1, 64, "Normal trophies required on each new day.");
            largeRequired = config.getInt("largeSquirrelsAfterDayOne", CATEGORY, 1, 0, 16, "Large trophies required on each new day after day one.");
            ghosts = flag(config, "enableGhostSquirrels", "Allow ghost squirrel encounters.");
            bears = flag(config, "enableSquirrelBears", "Allow squirrel bear encounters from day four.");
            events = flag(config, "enableParanormalEvents", "Allow random paranormal encounters during hunting.");
            finaleSeconds = config.getInt("finalCountdownSeconds", CATEGORY, 180, 1, 3600, "Countdown duration for the next finale.");
            suppressHostiles = flag(config, "disableUnrelatedHostiles", "Suppress unrelated hostile mobs in the campaign Overworld.");
            suppressStructures = flag(config, "disableUnrelatedStructures", "Suppress unrelated RTM structures in the campaign Overworld.");
            suppressGemini = flag(config, "disableGHEInvasion", "Pause Gemini invasion in Squirrel Stapler saves.");
            fog = flag(config, "enableAtmosphericFog", "Enable campaign distance fog on clients.");
            fogDistance = config.getInt("fogDistance", CATEGORY, 96, 24, 256, "Day-one fog distance in blocks. Later days reduce it.");
        }

        private static boolean flag(Configuration config, String key, String description) {
            return config.getBoolean(key, CATEGORY, true, description);
        }
    }
}
