package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class MaceConfig {
    private static final String CATEGORY = CommonConfig.CATEGORY_WEAPONS;
    public static volatile Values settings = new Values(new Configuration());

    private MaceConfig() {}

    private static double bounded(Configuration config, String key, double fallback, double min, double max, String comment) {
        double value = config.get(CATEGORY, key, fallback, comment, min, max).getDouble(fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public static final class Values {
        public final Spray mace, bearMace;
        public final boolean playerSusceptibility, bossImmunity;
        public final float protectionEffectiveness;

        public Values(Configuration config) {
            mace = new Spray(config, "Mace", "mace", 16, 3, 25, 100, 20);
            bearMace = new Spray(config, "BearMace", "bearMace", 8, 6, 45, 160, 30);
            playerSusceptibility = config.get(CATEGORY, "macePlayerSusceptibility", true,
                    "Allow both sprays to affect players where PvP is permitted.").getBoolean(true);
            bossImmunity = config.get(CATEGORY, "maceBossImmunity", true,
                    "Protect otherwise susceptible bosses from both sprays.").getBoolean(true);
            protectionEffectiveness = (float) bounded(config, "maceProtectionEffectiveness", 1D, 0D, 1D,
                    "Fraction of spray effect duration blocked by compatible headgear. 1 blocks all effects.");
        }
    }

    public static final class Spray {
        public final boolean enabled;
        public final int capacity, duration, cooldown;
        public final double range, halfAngle;

        private Spray(Configuration config, String name, String prefix, int capacity, double range, double angle,
                int duration, int cooldown) {
            enabled = config.get(CATEGORY, "enable" + name, true,
                    "Allow this spray to be used.").getBoolean(true);
            this.capacity = config.getInt(prefix + "Capacity", CATEGORY, capacity, 1, 1024,
                    "Maximum uses that can be filled into one sprayer.");
            this.range = bounded(config, prefix + "Range", range, 0.5D, 16D,
                    "Maximum spray reach in blocks.");
            double configuredAngle = bounded(config, prefix + "Angle", angle, 1D, 120D,
                    "Full opening angle of the cone in degrees.");
            halfAngle = Math.toRadians(configuredAngle / 2D);
            this.duration = config.getInt(prefix + "DurationTicks", CATEGORY, duration, 1, 1200,
                    "Effect duration in ticks.");
            this.cooldown = config.getInt(prefix + "CooldownTicks", CATEGORY, cooldown, 1, 1200,
                    "Minimum ticks before this player can use either sprayer again.");
        }
    }
}
