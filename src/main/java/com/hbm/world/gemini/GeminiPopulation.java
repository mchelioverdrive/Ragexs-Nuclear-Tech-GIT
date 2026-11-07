package com.hbm.world.gemini;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.*;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkEvent;

/** Server-thread loaded membership. Never scans the world's general entity list or holds chunk tickets. */
public final class GeminiPopulation {
    private static final Map<World, Map<UUID, EntityLiving>> WORLDS = new HashMap<World, Map<UUID, EntityLiving>>();

    private static Map<UUID, EntityLiving> members(World world) {
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        if(members == null) { members = new HashMap<UUID, EntityLiving>(); WORLDS.put(world, members); }
        return members;
    }

    public static EntityLiving find(World world, UUID id) {
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        EntityLiving mob = members == null ? null : members.get(id);
        return mob == null || mob.isDead || mob.worldObj != world ? null : mob;
    }

    public static boolean canAdmit(World world, Class<? extends EntityLiving> type, EntityLiving candidate, int limit) {
        if(world.isRemote || limit <= 0 || GeminiConfig.maxLoadedCreatures <= 0) return false;
        int total = 0, same = 0;
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        if(members == null) return true;
        for(EntityLiving mob : members.values()) {
            if(mob == candidate || mob.isDead || mob.worldObj != world) continue;
            // Newly admitted entities count immediately, before spawnEntityInWorld adds them to a chunk.
            if(++total >= GeminiConfig.maxLoadedCreatures || type.isInstance(mob) && ++same >= limit) return false;
        }
        return true;
    }

    private static int limit(EntityLiving mob) {
        if(mob instanceof EntityWoodcrawler) return GeminiConfig.woodcrawlerMaxLoaded;
        if(mob instanceof EntityFakePerson) return GeminiConfig.fakePersonMaxLoaded;
        if(mob instanceof EntityNaturesMockery) return GeminiConfig.mockeryMaxActive;
        if(mob instanceof EntityGardener) return GeminiConfig.gardenerMaxLoaded;
        if(mob instanceof EntityWretch) return GeminiConfig.wretchMaxLoaded;
        return 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void join(EntityJoinWorldEvent event) {
        if(event.world.isRemote || !(event.entity instanceof IGeminiCreature) || !(event.entity instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) event.entity;
        EntityLiving duplicate = find(event.world, mob.getUniqueID());
        // Chunk.addEntity sets addedToChunk before saved entities join the world. NBT on /summon does not.
        if(duplicate != null && duplicate != mob || !mob.addedToChunk
                && !canAdmit(event.world, mob.getClass(), mob, limit(mob))) {
            event.setCanceled(true); return;
        }
        members(event.world).put(mob.getUniqueID(), mob);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(event.phase != TickEvent.Phase.END || event.world.isRemote || event.world.getTotalWorldTime() % 20 != 0) return;
        Map<UUID, EntityLiving> members = WORLDS.get(event.world);
        if(members == null) return;
        for(Iterator<EntityLiving> it = members.values().iterator(); it.hasNext();) {
            EntityLiving mob = it.next();
            if(mob.isDead || !mob.addedToChunk || mob.worldObj != event.world
                    || event.world.getEntityByID(mob.getEntityId()) != mob
                    || !event.world.getChunkProvider().chunkExists(mob.chunkCoordX, mob.chunkCoordZ)) it.remove();
        }
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) { WORLDS.remove(event.world); }

    @SubscribeEvent public void unloadChunk(ChunkEvent.Unload event) {
        if(event.world.isRemote) return;
        Map<UUID, EntityLiving> members = WORLDS.get(event.world);
        if(members == null) return;
        // Release membership immediately: a fast reload must not mistake the old instance for a duplicate.
        for(Iterator<EntityLiving> it = members.values().iterator(); it.hasNext();) {
            EntityLiving mob = it.next();
            if(mob.chunkCoordX == event.getChunk().xPosition && mob.chunkCoordZ == event.getChunk().zPosition) it.remove();
        }
    }

    /** Local phase-3 alarm: at most four mobile allies, each using its existing prey and AI rules. */
    public static void alert(EntityLiving caller, EntityLivingBase target) {
        if(!GeminiInvasionManager.canActivityOccur(caller.worldObj, 3) || target == null || !target.isEntityAlive()
                || target instanceof IGeminiCreature || target.worldObj != caller.worldObj) return;
        Map<UUID, EntityLiving> members = WORLDS.get(caller.worldObj);
        if(members == null) return;
        int alerted = 0;
        for(EntityLiving ally : members.values()) {
            if(ally == caller || !ally.isEntityAlive() || !ally.addedToChunk || ally.getAttackTarget() != null
                    || ally.getDistanceSqToEntity(caller) > 256D || ally.getDistanceSqToEntity(target) > 256D
                    || !GardenerNests.loaded(caller.worldObj, ally.boundingBox.addCoord(target.posX - ally.posX,
                            target.posY - ally.posY, target.posZ - ally.posZ).expand(1, 1, 1))) continue;
            if(ally instanceof EntityWoodcrawler && !((EntityWoodcrawler) ally).hasCapturedVillager()
                    && ((EntityWoodcrawler) ally).isPrey(target) && ally.canEntityBeSeen(target)) ally.setAttackTarget(target);
            else if(ally instanceof EntityFakePerson && !((EntityFakePerson) ally).isRecovering()
                    && ((EntityFakePerson) ally).canHunt(target) && ally.canEntityBeSeen(target)) ally.setAttackTarget(target);
            else continue;
            if(++alerted >= 4) break;
        }
    }
}
