package com.hbm.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

/** Gameplay values only; simplified ketamine sedation and fictional terminal effects. */
public final class RestraintConfig {
    public static boolean enabled = true, players = false, devices = true;
    public static int sedationTicks = 1200, onsetTicks = 60, recoveryTicks = 200;
    public static int injectionTicks = 200, bullMaxTemperature = 600, bullSafeTemperature = 60;
    public static float bladeDamage = 10000F, electricDamage = 12F, bullDamage = 2F, injectionDamage = 4F;
    public static long chairQuantaPerTick = 2000L;
    public static final Set<String> humanoids = new HashSet<String>(), excluded = new HashSet<String>();

    private RestraintConfig() { }

    public static void loadFromConfig(Configuration c) {
        String category = "27_restraints";
        c.setCategoryComment(category, "Restraints, simplified ketamine sedation and fictional terminal effects. Durations are loaded simulation ticks (20/second); reload/repeated hits do not extend sedation. Restart to apply.");
        enabled = c.get(category, "enabled", true).getBoolean(true);
        players = c.get(category, "allowPlayerRestraint", false, "Explicitly opt in to player restraint, sedation and device captivity.").getBoolean(false);
        devices = c.get(category, "enableDevices", true).getBoolean(true);
        sedationTicks = c.getInt("sedationTicks", category, 1200, 20, 72000, "Unconscious duration after onset.");
        onsetTicks = c.getInt("sedationOnsetTicks", category, 60, 1, 1200, "Slowing before unconsciousness.");
        recoveryTicks = c.getInt("sedationRecoveryTicks", category, 200, 20, 72000, "Immunity to further tranquilizers after recovery.");
        injectionTicks = c.getInt("injectionEffectTicks", category, 200, 40, 72000, "Fictional terminal damage-over-time duration; damage occurs every 20 loaded ticks.");
        bladeDamage = c.getFloat("guillotineDamage", category, 10000F, 0F, 1000000F, "Damage on blade impact.");
        electricDamage = c.getFloat("chairDamage", category, 12F, 0F, 10000F, "Electrical damage per second to the captive only.");
        bullDamage = c.getFloat("bullDamage", category, 2F, 0F, 10000F, "Fire damage per second per 100 degrees above ambient.");
        injectionDamage = c.getFloat("injectionDamage", category, 4F, 0F, 10000F, "Constant damage every 20 loaded ticks; zero disables damage. No final burst.");
        chairQuantaPerTick = c.getInt("chairQuantaPerTick", category, 2000, 1, 1000000, "Half-joule quanta/tick drawn from RNT power; capacity holds 100 ticks.");
        bullMaxTemperature = c.getInt("bullMaxTemperature", category, 600, 100, 2000, "Maximum gameplay temperature.");
        bullSafeTemperature = c.getInt("bullSafeTemperature", category, 60, 20, 99, "Hatch cannot open above this temperature.");
        humanoids.clear();
        humanoids.addAll(Arrays.asList(c.get(category, "additionalHumanoidTypes", new String[0], "Entity registry names for other bipedal mobs. Zombie/skeleton/villager subclasses and tall, narrow EntityCreature shapes are detected automatically; creepers and blazes are excluded from shape detection. Size and boss exclusions always apply.").getStringList()));
        excluded.clear();
        excluded.addAll(Arrays.asList(c.get(category, "excludedEntityTypes", new String[0], "Entity registry names to exclude, in addition to bosses and oversized entities.").getStringList()));
    }
}
