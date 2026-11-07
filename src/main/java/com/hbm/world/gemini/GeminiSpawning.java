package com.hbm.world.gemini;

import com.hbm.entity.mob.gemini.EntityWretch;
import com.hbm.entity.mob.gemini.IGeminiCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;

/** Per-creature manual spawning, independent of the world's natural invasion progression. */
public final class GeminiSpawning {
    private static final String MANUAL = "GeminiManualSpawn", NATURAL = "GeminiNaturalSpawn";

    private GeminiSpawning() { }

    public static void markNatural(EntityLivingBase mob) {
        mob.getEntityData().setBoolean(NATURAL, true);
    }

    public static boolean initialize(EntityLivingBase mob) {
        if(mob.worldObj.isRemote) return false;
        boolean natural = mob.getEntityData().getBoolean(NATURAL);
        if(!natural) mob.getEntityData().setBoolean(MANUAL, true);
        mob.getEntityData().removeTag(NATURAL);
        return !natural;
    }

    public static boolean isManual(EntityLivingBase mob) {
        // Preserve the origin of Wretches saved by the original spawn egg implementation.
        return mob instanceof IGeminiCreature && (mob.getEntityData().getBoolean(MANUAL)
                || mob instanceof EntityWretch && ((EntityWretch) mob).isEggSpawned());
    }

    public static boolean manualAdmission(EntityLivingBase mob) {
        return mob != null && !mob.worldObj.isRemote && mob.worldObj instanceof WorldServer
                && mob.worldObj.difficultySetting != EnumDifficulty.PEACEFUL && isManual(mob);
    }

    public static boolean canAct(EntityLivingBase mob, int phase) {
        return manualAdmission(mob) || GeminiInvasionManager.canActivityOccur(mob.worldObj, phase);
    }
}
