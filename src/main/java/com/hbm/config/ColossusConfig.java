package com.hbm.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

public final class ColossusConfig {
    private ColossusConfig() { }
    public static boolean enabled = true, terrain = false, climbing = true, gauntletRequired = true;
    public static boolean natural = false, clock = true, clockTime = false, permanentTime = false, creative = false;
    public static boolean attacks = true, proximityActivation = false, bypassArmor = false;
    public static int blocks = 32, maximum = 2, cooldown = 65, particles = 12, escapePresses = 5;
    public static float health = 600, damage = 24, speed = 0.16F, range = 64, scale = 1, weakMultiplier = 8, volume = 2;
    public static Set<String> protectedBlocks = new HashSet<String>();
    public static void loadFromConfig(Configuration c) {
        String s = CommonConfig.CATEGORY_MOBS + ".prismarineColossus";
        enabled = bool(c, s, "enabled", "Allow new Colossus encounters. Saved bosses remain loadable.", true);
        health = number(c, s, "health", "Boss health; respects the native 1.7.10 health attribute limit.", 600, 20, 1024);
        damage = number(c, s, "attackDamage", "Base punch damage; slam, stomp, sweep and consume scale from this.", 24, 1, 200);
        cooldown = integer(c, s, "attackCooldownTicks", "Recovery between attacks; Romeo uses three quarters of this.", 65, 20, 1200);
        speed = number(c, s, "movementSpeed", "Blocks per tick, with bounded giant steering and obstacle probes.", 0.16F, 0.02F, 0.35F);
        range = number(c, s, "targetRange", "Loaded-chunk target search range.", 64, 16, 96);
        scale = number(c, s, "modelScale", "Whole anatomy scale, including hit regions, climbing and reach. One is 20 blocks tall.", 1, 0.5F, 2);
        weakMultiplier = number(c, s, "weakPointMultiplier", "Normal-weapon damage multiplier when gauntletRequired is false.", 8, 1, 100);
        gauntletRequired = bool(c, s, "gauntletRequired", "Require a held Prismarine Gauntlet to finish the rear-head symbol. False permits normal weapons.", true);
        bypassArmor = bool(c, s, "allowBodyDamage", "Allow highly reduced body damage to kill the boss instead of stopping at one health.", false);
        climbing = bool(c, s, "climbing", "Right-click the lower body to climb; W/S move, Space braces, Sneak releases.", true);
        escapePresses = integer(c, s, "grabEscapePresses", "Distinct Sneak presses required within four seconds to escape a grab.", 5, 1, 16);
        terrain = bool(c, s, "terrainDestruction", "Contact-only limb destruction; also requires mobGriefing. Containers and machines are always protected.", false);
        blocks = integer(c, s, "blocksPerTick", "Dimension-wide maximum terrain coordinates examined across all Colossi, including air and protected blocks.", 32, 0, 256);
        maximum = integer(c, s, "maxActivePerDimension", "Maximum saved encounters, including dormant and unloaded bosses. Zero prevents new encounters.", 2, 0, 16);
        particles = integer(c, s, "particleIntensity", "Maximum particles per local impact/death tick; respects client particle settings.", 12, 0, 48);
        volume = number(c, s, "soundVolume", "Scale of existing Minecraft stone, golem and rumbling sounds; zero mutes.", 2, 0, 4);
        attacks = bool(c, s, "attacks", "Enable physical combat independently of terrain destruction.", true);
        creative = bool(c, s, "targetCreativePlayers", "Allow Creative players as combat targets. The glitched variant still cannot damage invulnerable players.", false);
        proximityActivation = bool(c, s, "proximityActivation", "Wake dormant statues when a survival player approaches within eight blocks.", false);
        natural = bool(c, s, "naturalSpawning", "Rare, capped loaded-ocean spawn attempt every 24000 ticks near a survival player. No monument modification.", false);
        clock = bool(c, s, "giantClock", "Romeo visibly raises a giant clock once during the encounter.", true);
        clockTime = bool(c, s, "clockChangesTime", "Explicitly permit the giant clock to change dimension time; disabled by default, including multiplayer.", false);
        permanentTime = bool(c, s, "permanentClockTime", "Keep the clock's time change after the encounter. Otherwise restore elapsed original time after ten seconds or unloading.", false);
        protectedBlocks = new HashSet<String>(Arrays.asList(c.get(s, "protectedBlocks", new String[] {"minecraft:chest", "minecraft:ender_chest", "minecraft:enchanting_table"}, "Additional registry names protected from limb destruction.").getStringList()));
    }
    private static boolean bool(Configuration c, String s, String key, String comment, boolean value) { return CommonConfig.createConfigBool(c, s, key, comment, value); }
    private static float number(Configuration c, String s, String key, String comment, float value, float min, float max) {
        double n = CommonConfig.createConfigDouble(c, s, key, comment, value);
        return Double.isFinite(n) ? (float)Math.max(min, Math.min(max, n)) : value;
    }
    private static int integer(Configuration c, String s, String key, String comment, int value, int min, int max) { return Math.max(min, Math.min(max, CommonConfig.createConfigInt(c, s, key, comment, value))); }
}
