package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class WoodchipperConfig {
    private static final String CATEGORY = CommonConfig.CATEGORY_MACHINES;
    public static volatile Values settings = new Values(new Configuration());

    private WoodchipperConfig() {}

    public static final class Values {
        public final boolean enabled, entities, players, bosses, particles;
        public final int durationTicks, energyPerTick;
        public final float damage;

        public Values(Configuration config) {
            enabled = config.get(CATEGORY, "enableWoodchipper", true,
                    "Allow placed woodchippers to operate. Block and recipe registrations are retained.").getBoolean(true);
            durationTicks = config.getInt("woodchipperDurationTicks", CATEGORY, 20, 1, 1200,
                    "Powered ticks to process one material item.");
            energyPerTick = config.getInt("woodchipperEnergyPerTick", CATEGORY, 100, 1, 10000,
                    "Energy per working tick in legacy HE (one half-joule quantum per HE).");
            entities = config.get(CATEGORY, "woodchipperEntityProcessing", true,
                    "Allow woodchipper occupants when restraint devices are enabled.").getBoolean(true);
            players = config.get(CATEGORY, "woodchipperAllowPlayers", true,
                    "Allow player occupants when the shared player restraint setting also allows them.").getBoolean(true);
            bosses = config.get(CATEGORY, "woodchipperAllowBosses", false,
                    "Allow boss occupants that fit and meet the shared restraint and capture rules.").getBoolean(false);
            particles = config.get(CATEGORY, "woodchipperProcessingParticles", true,
                    "Show wood chips and entity hit particles.").getBoolean(true);
            double configuredDamage = config.get(CATEGORY, "woodchipperEntityDamage", 6D,
                    "Damage in health points per 20 powered ticks of entity processing.", 0D, 100D).getDouble(6D);
            damage = Double.isFinite(configuredDamage) ? (float) Math.max(0D, Math.min(100D, configuredDamage)) : 6F;
        }
    }
}
