package com.hbm.world.gemini;

import java.util.Map;
import java.util.WeakHashMap;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.EntityMappings;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Automatic admissions only. Never filter EntityJoinWorldEvent: it also loads saves and manual spawns. */
public final class GeminiHostileSpawning {
    public static final String RULE = "geminionly";
    // Some spawners query CheckSpawn more than once for the same candidate. Reuse its roll,
    // without persistent entity tags or retaining rejected candidates/worlds.
    private static final Map<Entity, Float> ROLLS = new WeakHashMap<Entity, Float>();

    public static void initializeRule(World world) {
        if(world.isRemote) return;
        GameRules rules = world.getGameRules();
        if(!rules.hasRule(RULE)) rules.addGameRule(RULE, Boolean.toString(GeminiConfig.geminiOnly));
    }

    @SubscribeEvent
    public void load(WorldEvent.Load event) {
        initializeRule(event.world);
    }

    /** Vanilla's monster classification plus explicit neutral exceptions; never consult biome lists. */
    private static boolean hostile(EntityLiving mob) {
        if(mob instanceof EntityPigZombie)
            return mob.getAttackTarget() != null || ((EntityPigZombie) mob).getEntityToAttack() != null;
        if(mob instanceof EntityEnderman)
            return ((EntityEnderman) mob).isScreaming() || mob.getAttackTarget() != null
                    || ((EntityEnderman) mob).getEntityToAttack() != null;
        return mob instanceof IMob || mob.isCreatureType(EnumCreatureType.monster, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if(event.world.isRemote || event.getResult() == Result.DENY
                || !(event.entityLiving instanceof EntityLiving)
                || EntityMappings.isGeminiEntity(event.entityLiving.getClass())) return;
        EntityLiving mob = (EntityLiving) event.entityLiving;
        if(!hostile(mob)) return;
        if(!allowAutomaticHostile(mob)) event.setResult(Result.DENY);
    }

    /** For known hostile custom natural encounter producers, before insertion/reserving world data.
     * Do not call from generic join, egg, command or saved-entity loading paths. */
    public static boolean allowAutomaticHostile(Entity mob) {
        World world = mob.worldObj;
        if(world.isRemote || EntityMappings.isGeminiEntity(mob.getClass())) return true;
        initializeRule(world);
        if(world.getGameRules().getGameRuleBooleanValue(RULE)) return false;
        // The invasion is explicitly Overworld-only. Other dimensions retain normal rates.
        if(!GeminiConfig.geminiStageSpawnSuppression || world.provider.dimensionId != 0) return true;
        int phase = GeminiInvasionManager.getPhase(world);
        float multiplier = phase <= 0 ? 1F : phase >= 4 ? GeminiConfig.geminiFinalStageHostileSpawnMultiplier
                : GeminiConfig.geminiIntermediateStageHostileSpawnMultipliers[phase - 1];
        if(multiplier >= 1F) return true;
        if(multiplier <= 0F) return false;
        Float roll = ROLLS.get(mob);
        if(roll == null) { roll = world.rand.nextFloat(); ROLLS.put(mob, roll); }
        return roll < multiplier;
    }
}
