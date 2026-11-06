package com.hbm.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

/** Startup settings; all work limits have hard bounds even for hand-edited configs. */
public final class WitherStormConfig {
    private WitherStormConfig() { }
    public static int transform = 12500, devourer = 50000, thunder = 250000;
    public static int minimumStageBTicks = 24000;
    public static int blocksPerTick = 4096, debrisPerTick = 4, maxDebris = 128;
    public static int terrainMicros = 2000, terrainChecksPerTick = 16384;
    public static int maxVortexFragments = 2000;
    public static int attackTicks = 80, maxEncounters = 1, queryBudget = 512, maxRadius = 48;
    public static int skullAttackRange = 96;
    public static int maxVisibilityDistance = 1024, idleWanderingRadius = 64;
    public static float explosionStrength = 2F;
    public static boolean terrain = true, tileEntities = false, protectedBlocks = false, survivalSummon = true;
    public static boolean challengeMode = false;
    public static int fearRange = 128, fearEscapeDistance = 160, fearInterval = 10;
    public static double passiveFleeSpeed = 1.5D, hostileFleeSpeed = 1.7D;
    public static Set<String> blacklist = new HashSet<String>();

    public static void loadFromConfig(Configuration config) {
        String c = CommonConfig.CATEGORY_MOBS + ".witherStorm";
        transform = integer(config, c, "transformGrowth", "Mass required to evolve from Command Block Wither (inclusive).", 12500, 1, 100000000);
        devourer = integer(config, c, "devourerGrowth", "Enter Devourer above this mass; must exceed transformGrowth.", 50000, transform + 1, 200000000);
        thunder = integer(config, c, "thunderStormGrowth", "Enter ThunderStorm above this mass; must exceed devourerGrowth.", 250000, devourer + 1, 500000000);
        minimumStageBTicks = integer(config, c, "minimumStageBTicks", "Minimum active ticks in Stage B before automatic evolution. The growth threshold still applies. Zero disables the timer.", 24000, 0, Integer.MAX_VALUE);
        blocksPerTick = integer(config, c, "blocksPerTick", "Maximum blocks successfully consumed per encounter per server tick, including skull and tentacle impacts. Stage limits: 32, 64, 256, 1024, 2048, 2048, 4096. Zero disables consumption; existing explicit limits remain authoritative.", 4096, 0, 4096);
        terrainChecksPerTick = integer(config, c, "terrainChecksPerTick", "Maximum terrain scan steps per encounter per tick, including air, protection checks, skipped empty sections and impacts. Separate from successful block removals; elapsed time also limits work.", 16384, 64, 65536);
        terrainMicros = integer(config, c, "terrainProcessingMicros", "Maximum elapsed terrain-processing time per encounter per tick in microseconds. A single block callback can exceed this limit.", 2000, 100, 10000);
        debrisPerTick = integer(config, c, "debrisPerTick", "Maximum visual debris records per encounter per tick; batched every five ticks. No server debris entities.", 4, 0, 16);
        maxDebris = integer(config, c, "maxClientDebris", "Maximum live Wither Storm debris particles per client, across all encounters.", 128, 0, 512);
        maxVortexFragments = integer(config, c, "maxVortexFragments", "Maximum visible decorative Stage D vortex fragments per Storm. Client-only; reduced by distance, particle and graphics settings. Zero hides the vortex.", 2000, 0, 8192);
        attackTicks = integer(config, c, "skullAttackTicks", "Base cooldown after a skull volley. Each phase reduces it by one eighth of the base, with a 40-tick minimum.", 80, 40, 12000);
        skullAttackRange = integer(config, c, "skullAttackRange", "Maximum distance in blocks from both the Storm and the firing mouth to a skull target.", 96, 8, 96);
        maxVisibilityDistance = integer(config, c, "maxVisibilityDistance", "Maximum Storm render/tracking distance in blocks, scaled down for earlier stages. Use matching client/server settings. Normal watched/loaded chunks, fog and camera clipping still apply; never loads chunks for visibility.", 1024, 64, 4096);
        idleWanderingRadius = integer(config, c, "idleWanderingRadius", "Maximum horizontal distance of an idle flight destination. Only loaded, terrain-cleared routes are chosen. Zero disables wandering. Eligible players anywhere in the dimension interrupt wandering.", 64, 0, 256);
        maxEncounters = integer(config, c, "maxEncounters", "Maximum encounters across all dimensions, including saved unloaded encounters. Zero disables new summons.", 1, 0, 32);
        queryBudget = integer(config, c, "entityQueryBudget", "Maximum entity entries examined per cached head/debris search; at most 64 candidates retained, refreshed every 20 ticks. Player pursuit scans the dimension's player list; tentacles use a separate local living-entity census.", 512, 64, 4096);
        maxRadius = integer(config, c, "maxConsumptionRadius", "Maximum body/head/tentacle-scaled horizontal feeding radius. Does not limit vertical reach from flight altitude. Chunks are never force-loaded; existing explicit caps remain authoritative.", 48, 4, 48);
        double strength = CommonConfig.createConfigDouble(config, c, "skullExplosionStrength", "Non-nuclear impact strength, scaled by phase; bounded to 0-8. Terrain shares blocksPerTick and protection rules.", 2D);
        explosionStrength = (float)(Double.isNaN(strength) || Double.isInfinite(strength) ? 2D : Math.max(0D, Math.min(8D, strength)));
        terrain = CommonConfig.createConfigBool(config, c, "terrainDestruction", "Allow consumption and skull terrain damage; also requires mobGriefing.", true);
        tileEntities = CommonConfig.createConfigBool(config, c, "consumeTileEntities", "Explicitly allow tile entity destruction, including stored contents. Default protects machines and containers.", false);
        protectedBlocks = CommonConfig.createConfigBool(config, c, "consumeProtectedBlocks", "Explicitly override block canEntityDestroy vetoes and the blacklist. Never overrides negative hardness, bedrock, portals or command blocks.", false);
        survivalSummon = CommonConfig.createConfigBool(config, c, "survivalSummoning", "Allow the Fake Command Block structure and the legacy euphemium ritual.", true);
        challengeMode = CommonConfig.createConfigBool(config, c, "challengeMode", "Start a one-day countdown to an automatic Overworld Wither Storm. Worlds can override this by command.", false);
        fearRange = integer(config, c, "fearDetectionRange", "Distance at which mobs notice a Wither Storm.", 128, 8, 256);
        fearEscapeDistance = integer(config, c, "fearEscapeDistance", "Desired distance from the storm. Mobs plan shorter navigation legs toward this distance.", 160, fearRange, 320);
        fearInterval = integer(config, c, "fearDetectionInterval", "Ticks between mob threat searches and minimum interval between escape path attempts.", 10, 1, 100);
        passiveFleeSpeed = speed(config, c, "passiveFleeSpeed", 1.5D);
        hostileFleeSpeed = speed(config, c, "hostileFleeSpeed", 1.7D);
        blacklist = new HashSet<String>(Arrays.asList(config.get(c, "protectedBlocks", new String[] {"minecraft:chest", "minecraft:ender_chest", "minecraft:enchanting_table"}, "Registry names protected from all Wither Storm terrain processing.").getStringList()));
    }

    private static int integer(Configuration c, String category, String key, String comment, int value, int min, int max) {
        return Math.max(min, Math.min(max, CommonConfig.createConfigInt(c, category, key, comment, value)));
    }

    private static double speed(Configuration config, String category, String key, double fallback) {
        double value = CommonConfig.createConfigDouble(config, category, key, "Movement speed multiplier while fleeing a Wither Storm.", fallback);
        return Double.isFinite(value) ? Math.max(1D, Math.min(4D, value)) : fallback;
    }
}
