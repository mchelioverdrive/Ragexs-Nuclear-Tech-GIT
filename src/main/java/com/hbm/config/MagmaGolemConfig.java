package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class MagmaGolemConfig {
    private MagmaGolemConfig() { }

    public static float health = 600, melee = 24, ranged = 12, speed = 0.22F;
    public static int recovery = 38, summonCooldown = 360, cubeLimit = 8, coolingTicks = 20, armDurability = 24;
    public static boolean natural, griefing = true;

    public static void loadFromConfig(Configuration config) {
        String category = CommonConfig.CATEGORY_MOBS + ".magmaGolem";
        health = number(config, category, "health", 600, 40, 1024, "Maximum health of a new golem.");
        melee = number(config, category, "meleeDamage", 24, 1, 100, "Base damage of physical attacks.");
        ranged = number(config, category, "rangedDamage", 12, 1, 100, "Base damage of a fireball.");
        speed = number(config, category, "movementSpeed", 0.22F, 0.06F, 0.65F, "Phase-one walking speed in blocks per tick.");
        recovery = config.getInt("attackCooldownTicks", category, 38, 12, 400, "Recovery between attacks before phase scaling.");
        summonCooldown = config.getInt("summonCooldownTicks", category, 360, 100, 3600, "Minimum delay between magma cube waves.");
        cubeLimit = config.getInt("maxSummonedCubes", category, 8, 0, 20, "Maximum living summons per golem. Set to zero to disable waves.");
        coolingTicks = config.getInt("waterCoolingTicks", category, 20, 1, 200, "Ticks of water contact needed to cool an arm.");
        armDurability = config.getInt("armDurability", category, 24, 1, 200, "Mining work required per cooled arm. Better pickaxes remove more per hit.");
        griefing = CommonConfig.createConfigBool(config, category, "bossGriefing",
            "Allow terrain damage and ignition when mobGriefing also permits them.", true);
        natural = CommonConfig.createConfigBool(config, category, "naturalSpawning",
            "Allow rare natural spawns in Overworld plains and deserts.", false);
    }

    private static float number(Configuration config, String category, String key, float value, float min, float max, String comment) {
        double raw = CommonConfig.createConfigDouble(config, category, key, comment, value);
        return Double.isFinite(raw) ? (float)Math.max(min, Math.min(max, raw)) : value;
    }
}
