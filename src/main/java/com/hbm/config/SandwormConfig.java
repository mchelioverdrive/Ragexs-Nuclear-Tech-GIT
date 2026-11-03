package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

/** Startup configuration, deliberately isolated from RNT's other fluids and bosses. */
public final class SandwormConfig {
    private SandwormConfig() { }
    public static int dimension = -38, maxEncounters = 8, naturalInterval = 24000;
    public static boolean natural = false, swallowCreative = false;
    public static int returnGrace = 200;
    public static double speed = 0.7, targetRange = 128, swallowRadius = 6;
    public static int brainHealth = 200, brainDamage = 10, brainInterval = 100;
    public static int waveInterval = 600, waveMin = 3, waveMax = 6, sparkLimit = 24;
    public static int heartHealth = 200, heartInvulnerability = 100;
    public static int sparkHealth = 20, sparkDamage = 10, sparkHeal = 20, sparkInterval = 20, blindness = 100;
    public static int tickHealth = 40, tickDamage = 10, bloodGrowthInterval = 20;
    public static double tickGrowth = 0.2;
    public static int rows = 7, blocksPerTick = 512, chunksPerTick = 2;
    public static int bloodDamage = 10, acidDamage = 10, gasDamage = 10;
    public static int cleanupTicks = 12000, abandonTicks = 1728000;

    public static void loadFromConfig(Configuration c) {
        dimension = integer(c, "dimensionID", -38, -4096, 4096, "Dedicated interior dimension. Must be unused; never change for an existing save.");
        if(dimension == 0 || dimension == 1 || dimension == -1) throw new IllegalArgumentException("Sandworm dimension must not replace a vanilla dimension");
        natural = c.get(category(), "naturalSpawning", false, "Enable rare desert encounters in the overworld. Manual summoning is independent.").getBoolean(false);
        naturalInterval = integer(c, "naturalSpawnInterval", 24000, 1200, 24000000, "Ticks between one bounded natural spawn attempt.");
        maxEncounters = integer(c, "maxActiveEncounters", 8, 1, 64, "Includes unloaded and generating encounters.");
        speed = decimal(c, "movementSpeed", 0.7, 0.1, 1.5, "Blocks per tick; turn rate preserves continuous body curves.");
        targetRange = decimal(c, "targetRange", 128, 24, 192, "Player and dropped worm bait attraction range.");
        swallowRadius = decimal(c, "mouthRadius", 6, 2, 10, "Swallow radius around the forward mouth, once the interior is ready.");
        swallowCreative = c.get(category(), "swallowCreativePlayers", false).getBoolean(false);
        returnGrace = integer(c, "returnSwallowCooldownTicks", 200, 20, 12000, "Saved grace after returning or respawning, preventing immediate swallowing at the original mouth.");
        brainHealth = integer(c, "brainHealth", 200, 1, 1024, "Brain maximum health; bounded by the native attribute limit.");
        brainDamage = integer(c, "brainDamage", 10, 0, 10000, "Radial energy damage.");
        brainInterval = integer(c, "brainAttackTicks", 100, 20, 12000, "Interval between 12-20 radial shots.");
        waveInterval = integer(c, "sparkWaveTicks", 600, 40, 24000, "Brain Spark wave interval.");
        waveMin = integer(c, "sparkWaveMin", 3, 1, 24, "Inclusive minimum wave size.");
        waveMax = integer(c, "sparkWaveMax", 6, waveMin, 24, "Inclusive maximum wave size.");
        sparkLimit = integer(c, "maxSparksPerBrain", 24, waveMax, 64, "Local live Spark cap, including room spawns.");
        heartHealth = integer(c, "heartHealth", 200, 1, 1024, "Heart maximum health; bounded by the native attribute limit.");
        heartInvulnerability = integer(c, "heartInvulnerabilityTicks", 100, 0, 12000, "After a successful wound; blood defense runs during this interval.");
        sparkHealth = integer(c, "sparkHealth", 20, 1, 1024, "Spark maximum health.");
        sparkDamage = integer(c, "sparkDamage", 10, 0, 10000, "Orange Spark damage.");
        sparkHeal = integer(c, "sparkHealing", 20, 0, 10000, "Green healing, only for allied interior monsters of the same encounter.");
        sparkInterval = integer(c, "sparkEffectTicks", 20, 5, 12000, "Effect interval for all variants.");
        blindness = integer(c, "sparkBlindnessTicks", 100, 1, 12000, "Purple blindness duration.");
        tickHealth = integer(c, "tickHealth", 40, 1, 1024, "Tick base maximum health; growth also respects the native 1024 health cap.");
        tickDamage = integer(c, "tickDamage", 10, 0, 10000, "Tick melee damage.");
        tickGrowth = decimal(c, "tickHealthGrowth", 0.2, 0, 2, "Additional base health fraction per stage, capped at five stages.");
        bloodGrowthInterval = integer(c, "bloodGrowthTicks", 20, 1, 1200, "Minimum ticks between blood-triggered growth stages.");
        rows = integer(c, "dungeonRows", 7, 3, 12, "Three rooms across; saved per encounter. Every room is connected.");
        blocksPerTick = integer(c, "dungeonBlocksPerTick", 512, 32, 4096, "Global placement/cleanup coordinate budget, shared across encounters.");
        chunksPerTick = integer(c, "dungeonChunksPerTick", 2, 1, 4, "Maximum chunks brought in for construction in one tick. No tickets.");
        bloodDamage = integer(c, "bloodDamage", 10, 0, 10000, "Blood contact damage per second.");
        acidDamage = integer(c, "acidDamage", 10, 0, 10000, "Acid contact damage per second.");
        gasDamage = integer(c, "gasDamage", 10, 0, 10000, "Gas contact damage per second.");
        cleanupTicks = integer(c, "cleanupDelayTicks", 12000, 200, 24000000, "Delay before bounded cleanup of completed interiors. Offline occupants retain their interior.");
        abandonTicks = integer(c, "abandonedTicks", 1728000, 12000, 24000000, "Idle empty encounter expiration; saved return dependencies prevent expiration. All progress is always saved.");
    }
    private static String category() { return CommonConfig.CATEGORY_MOBS + ".sandworm"; }
    private static int integer(Configuration c, String k, int d, int min, int max, String help) {
        return Math.max(min, Math.min(max, c.get(category(), k, d, help).getInt(d)));
    }
    private static double decimal(Configuration c, String k, double d, double min, double max, String help) {
        double n = c.get(category(), k, d, help).getDouble(d);
        return Double.isNaN(n) || Double.isInfinite(n) ? d : Math.max(min, Math.min(max, n));
    }
}
