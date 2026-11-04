package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GiantEndermanConfig {
    public static float health = 1000, damage = 1, range = 96, speed = 0.26F;
    public static int teleportCooldown = 160, radius = 5, budget = 24;
    public static boolean terrain = true, natural = false, bossCombat = true, playerGrab = true;

    private GiantEndermanConfig() { }

    public static void loadFromConfig(Configuration config) {
        String category = CommonConfig.CATEGORY_MOBS + ".giantEnderman";
        health = config.getFloat("maximumHealth", category, 1000, 20, 1024, "Maximum health. Minecraft 1.7.10 caps the health attribute at 1024.");
        damage = config.getFloat("attackDamageMultiplier", category, 1, 0.05F, 10, "Scales damage dealt by the Giant Enderman.");
        range = config.getFloat("detectionRange", category, 96, 24, 192, "Maximum target detection range in blocks.");
        speed = config.getFloat("movementSpeed", category, 0.26F, 0.05F, 0.5F, "Phase one movement speed in blocks per tick.");
        teleportCooldown = config.getInt("teleportCooldownTicks", category, 160, 40, 2400, "Base teleport cooldown. Later phases shorten it.");
        terrain = config.getBoolean("terrainDestruction", category, true, "Allow ripping, impacts and obstacle clearing when mobGriefing is enabled.");
        radius = config.getInt("terrainDestructionRadius", category, 5, 1, 8, "Maximum radius of terrain contacts.");
        natural = config.getBoolean("naturalSpawning", category, false, "Allow rare natural Giant Enderman encounters in the End.");
        bossCombat = config.getBoolean("bossCombat", category, true, "Enable combat against hostile bosses, including Prismarine Colossi.");
        playerGrab = config.getBoolean("playerGrabbing", category, true, "Allow players to be grabbed. Smaller mobs can still be grabbed.");
        budget = config.getInt("terrainBudgetPerTick", category, 24, 1, 128, "Dimension-wide number of terrain positions examined per tick.");
    }
}
