package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GiantGhastConfig {
    public static final int MAX_HEALTH = 100000;
    public static float health = 2000, damage = 8, range = 88;
    public static float summonThreshold = 0.65F, enrageThreshold = 0.30F;
    public static int attackCooldown = 54, maxFireballs = 32, maxSummons = 12, summonCooldown = 480;
    public static int weakPointStrikes = 8;
    public static boolean natural, griefing, explosionBlocks;

    private GiantGhastConfig() { }

    public static void loadFromConfig(Configuration config) {
        String category = CommonConfig.CATEGORY_MOBS + ".giantGhast";
        health = number(config, category, "health", 2000, 20, MAX_HEALTH, "Maximum health for new Giant Ghasts.");
        damage = number(config, category, "fireballDamage", 8, 1, 40, "Direct damage before attack and difficulty scaling.");
        range = number(config, category, "attackRange", 88, 32, 120, "Maximum targeting and firing range in blocks.");
        weakPointStrikes = ticks(config, category, "weakPointStrikes", 8, 1, 64, "Successful melee hits on the exposed top core required to kill the Giant Ghast.");
        summonThreshold = number(config, category, "summonPhaseHealth", 0.65F, 0.10F, 0.95F, "Health fraction that unlocks summon waves.");
        enrageThreshold = number(config, category, "enragePhaseHealth", 0.30F, 0.05F, summonThreshold - 0.05F, "Health fraction below which the boss may target retaliating summons.");
        attackCooldown = ticks(config, category, "attackCooldownTicks", 54, 18, 600, "Base recovery between attacks. Later phases recover faster.");
        maxFireballs = ticks(config, category, "maxActiveFireballs", 32, 1, 80, "Combined active fireballs per encounter, including summoned Ghasts and reflected shots.");
        maxSummons = ticks(config, category, "maxSummonedGhasts", 12, 0, 32, "Maximum summoned Ghasts per boss, including unloaded summons.");
        summonCooldown = ticks(config, category, "summonCooldownTicks", 480, 100, 3600, "Minimum time between summon waves.");
        natural = CommonConfig.createConfigBool(config, category, "naturalSpawning", "Allow rare natural encounters in the Nether.", false);
        griefing = CommonConfig.createConfigBool(config, category, "bossGriefing", "Allow terrain damage from this encounter when mobGriefing is also enabled.", false);
        explosionBlocks = CommonConfig.createConfigBool(config, category, "fireballExplosionBlockDamage", "Allow fireballs to break blocks when bossGriefing is enabled.", false);
    }

    private static float number(Configuration c, String category, String key, float value, float min, float max, String comment) {
        double n = CommonConfig.createConfigDouble(c, category, key, comment, value);
        return Double.isFinite(n) ? (float)Math.max(min, Math.min(max, n)) : Math.min(max, value);
    }

    private static int ticks(Configuration c, String category, String key, int value, int min, int max, String comment) {
        return Math.max(min, Math.min(max, CommonConfig.createConfigInt(c, category, key, comment, value)));
    }
}
