package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityNaturesMockery;
import com.hbm.handler.disease.DeepRootDisease;
import com.hbm.handler.disease.DiseaseHost;
import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.Infection;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/** All new admissions, including eggs and terminal conversions, share this boundary. */
public final class MockerySpawning {
    private static final String CONVERSION = "MockeryConversion";
    public static boolean active(World world) {
        return GeminiInvasionManager.canActivityOccur(world, 2) && GeminiConfig.deepRootEnabled
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }

    public static boolean canSpawn(World world, EntityNaturesMockery candidate) {
        if(GeminiSpawning.manualAdmission(candidate)) return true;
        if(!active(world) || GeminiConfig.mockeryMaxActive == 0) return false;
        return GeminiPopulation.canAdmit(world, EntityNaturesMockery.class, candidate, GeminiConfig.mockeryMaxActive);
    }

    public static boolean active(EntityNaturesMockery mob) {
        return GeminiSpawning.manualAdmission(mob) || active(mob.worldObj);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void join(EntityJoinWorldEvent event) {
        if(!event.world.isRemote && event.entity instanceof EntityNaturesMockery
                && !event.entity.addedToChunk && !canSpawn(event.world, (EntityNaturesMockery) event.entity))
            event.setCanceled(true);
    }

    public static boolean space(EntityNaturesMockery mob, Entity ignored) {
        World world = mob.worldObj;
        int minX = MathHelper.floor_double(mob.boundingBox.minX) >> 4;
        int maxX = MathHelper.floor_double(mob.boundingBox.maxX) >> 4;
        int minZ = MathHelper.floor_double(mob.boundingBox.minZ) >> 4;
        int maxZ = MathHelper.floor_double(mob.boundingBox.maxZ) >> 4;
        for(int x = minX; x <= maxX; x++) for(int z = minZ; z <= maxZ; z++)
            if(!world.getChunkProvider().chunkExists(x, z)) return false;
        if(!world.getCollidingBoundingBoxes(mob, mob.boundingBox).isEmpty() || world.isAnyLiquid(mob.boundingBox)
                || !world.getBlock(MathHelper.floor_double(mob.posX), MathHelper.floor_double(mob.posY) - 1,
                    MathHelper.floor_double(mob.posZ)).getMaterial().blocksMovement()) return false;
        for(Object entry : world.getEntitiesWithinAABBExcludingEntity(mob, mob.boundingBox)) {
            Entity entity = (Entity) entry;
            if(entity != ignored && !entity.isDead && entity.canBeCollidedWith()) return false;
        }
        return true;
    }

    /** Spawn first: canceled/obstructed conversions leave the original host and its infection intact. */
    public static boolean convert(EntityLivingBase host) {
        return convert(host, null, false);
    }

    /** The caller has held this still-infected host for the full assimilation duration. */
    public static boolean assimilate(EntityLivingBase host, EntityNaturesMockery captor) {
        if(host instanceof EntityPlayer || captor == null || !captor.isEntityAlive() || !captor.canCompleteAssimilation(host)
                || !MockerySpawning.active(captor) || !DeepRootDisease.active(host)
                || !host.worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return false;
        Infection infection = DiseaseHost.get(host).infections.get(DeepRootDisease.ID);
        if(infection == null) return false; // Treatment interrupts assimilation; never re-establish infection here.
        DeepRootDisease.selectStage(host, DeepRootDisease.Stage.TERMINAL);
        return convert(host, captor, false);
    }

    /** Called only after the terminal disease's native death event has completed uncanceled. */
    public static boolean convertPlayerDeath(EntityPlayer host) {
        return convert(host, null, true);
    }

    private static boolean eligible(EntityLivingBase host, boolean playerDeath) {
        if(!playerDeath) return GeminiInfections.isConversionEligible(host);
        Infection infection = DiseaseHost.get(host).infections.get(DeepRootDisease.ID);
        return host instanceof EntityPlayer && !host.isEntityAlive() && !host.worldObj.isRemote
                && host.worldObj.getChunkProvider().chunkExists(MathHelper.floor_double(host.posX) >> 4, MathHelper.floor_double(host.posZ) >> 4)
                && DiseaseManager.pathogen(DeepRootDisease.ID).canInfect(host)
                && infection != null && DeepRootDisease.stage(infection) == DeepRootDisease.Stage.TERMINAL;
    }

    private static boolean convert(EntityLivingBase host, EntityNaturesMockery captor, boolean playerDeath) {
        if(!eligible(host, playerDeath) || host.getEntityData().hasKey(CONVERSION) || !canSpawn(host.worldObj, null)
                || (!playerDeath && host.getEntityData().hasKey("MockeryCaptor")
                    && (captor == null || !captor.getUniqueID().toString().equals(host.getEntityData().getString("MockeryCaptor"))))
                || com.hbm.entity.mob.gemini.EntityWretch.isCaptured(host)
                || host.getEntityData().getLong("WoodcrawlerCocoonUntil") > host.worldObj.getTotalWorldTime()
                || host.ridingEntity != null || host.riddenByEntity != null) return false;
        EntityNaturesMockery mob = new EntityNaturesMockery(host.worldObj);
        mob.setLocationAndAngles(host.posX, host.boundingBox.minY, host.posZ, host.rotationYaw, 0);
        if(!space(mob, host)) return false;
        Infection infection = DiseaseHost.get(host).infections.get(DeepRootDisease.ID);
        // Lock before calling external spawn listeners: they can reenter conversion on this same host.
        host.getEntityData().setBoolean(CONVERSION, true);
        boolean converted = false;
        try {
            if(!host.worldObj.spawnEntityInWorld(mob)) return false;
            // Spawn listeners can also cure/kill the host or destroy its captor. Recheck before consuming it.
            if(mob.isDead || DiseaseHost.get(host).infections.get(DeepRootDisease.ID) != infection
                    || !eligible(host, playerDeath) || (captor != null && (!captor.isEntityAlive() || !captor.canCompleteAssimilation(host)))) {
                mob.setDead();
                return false;
            }
            converted = true;
        } finally {
            if(!converted) host.getEntityData().removeTag(CONVERSION);
        }
        if(playerDeath) return true; // Native player death owns inventory, drops and respawn; never remove the player.
        // Conversion bypasses death; explicitly preserve carried player property on vanilla equines.
        if(host instanceof net.minecraft.entity.passive.EntityHorse)
            ((net.minecraft.entity.passive.EntityHorse) host).dropChestItems();
        for(int slot = 0; slot < 5; slot++) {
            net.minecraft.item.ItemStack stack = host.getEquipmentInSlot(slot);
            if(stack != null) {
                host.entityDropItem(stack, 0);
                host.setCurrentItemOrArmor(slot, null);
            }
        }
        host.setDead();
        return true;
    }
}
