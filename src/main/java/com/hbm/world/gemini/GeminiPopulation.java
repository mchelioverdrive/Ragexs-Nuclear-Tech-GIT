package com.hbm.world.gemini;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.*;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

/** Server-thread loaded membership. Never scans the world's general entity list or holds chunk tickets. */
public final class GeminiPopulation {
    private static final Map<World, Map<UUID, EntityLiving>> WORLDS = new HashMap<World, Map<UUID, EntityLiving>>();
    private static final Map<World, Map<UUID, Entity>> COCOONS = new HashMap<World, Map<UUID, Entity>>();
    // Only populated around synchronous spawn callbacks, then removed in finally. No saved/source entity references.
    private static final Map<EntityLiving, Entity> HATCHING = new java.util.IdentityHashMap<EntityLiving, Entity>();

    public static boolean hasCocoon(World world, UUID id) {
        Map<UUID, Entity> cocoons = COCOONS.get(world);
        Entity cocoon = cocoons == null ? null : cocoons.get(id);
        return cocoon != null && !cocoon.isDead;
    }

    public static boolean canAdmitCocoon(World world) {
        return canAdmitBiomass(world, true);
    }
    public static boolean canAdmitWoodcrawlerCocoon(World world) { return canAdmitBiomass(world, false); }
    private static boolean canAdmitBiomass(World world, boolean gardener) {
        if(world.isRemote || GeminiConfig.woodcrawlerMaxPending <= 0 || GeminiConfig.maxLoadedCreatures <= 0
                || gardener && GeminiConfig.gardenerCocoonMaxLoaded <= 0) return false;
        int count = 0, total = 0, gardens = 0;
        Map<UUID, Entity> cocoons = COCOONS.get(world);
        if(cocoons != null) for(Entity cocoon : cocoons.values()) if(!cocoon.isDead) {
            count++; if(cocoon instanceof EntityGardenerCocoon) gardens++;
        }
        Map<UUID, EntityLiving> living = WORLDS.get(world);
        if(living != null) for(EntityLiving mob : living.values()) if(!mob.isDead) total++;
        return (!gardener || gardens < GeminiConfig.gardenerCocoonMaxLoaded) && count < GeminiConfig.woodcrawlerMaxPending
                && count + total < GeminiConfig.maxLoadedCreatures;
    }

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

    /** Reuse loaded membership; includes Woodcrawlers, Gardeners, Wretches and Mockeries. */
    static float irisPressure(net.minecraft.entity.player.EntityPlayer player) {
        Map<UUID, EntityLiving> members = WORLDS.get(player.worldObj);
        float pressure = 0;
        if(members != null) for(EntityLiving mob : members.values())
            if(mob.isEntityAlive() && mob.addedToChunk && mob.getDistanceSqToEntity(player) < 128D * 128D) {
                pressure += 0.12F;
                if(pressure >= 0.6F) return 0.6F;
            }
        return pressure;
    }

    static String gardenerAdmissionReason(World world) {
        if(!GardenerNests.censusReady(world)) return "saved Underground Gardener census in progress";
        if(GeminiConfig.gardenerMaxLoaded <= 0) return "Gardener admission disabled (maxLoaded=0)";
        if(GeminiConfig.maxLoadedCreatures <= 0) return "global Gemini admission disabled (maxLoadedCreatures=0)";
        int total = 0, gardeners = gardenerCount(world, null);
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        if(members != null) for(EntityLiving mob : members.values()) if(!mob.isDead && mob.worldObj == world) {
            total++;
        }
        if(total >= GeminiConfig.maxLoadedCreatures) return "global Gemini population limit (" + total + "/" + GeminiConfig.maxLoadedCreatures + ")";
        if(gardeners >= GeminiConfig.gardenerMaxLoaded) return "Underground Gardener population limit (" + gardeners + "/" + GeminiConfig.gardenerMaxLoaded + ")";
        return null;
    }

    /** Union saved identities and synchronous join reservations; never double-count a loaded survivor. */
    static int gardenerCount(World world, EntityLiving candidate) {
        Set<UUID> ids = new HashSet<UUID>(GardenerNests.living(world));
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        if(members != null) for(EntityLiving mob : members.values())
            if(mob instanceof EntityGardener && !(mob instanceof EntityOceanGardener) && mob.isEntityAlive()) ids.add(mob.getUniqueID());
        if(candidate != null) ids.remove(candidate.getUniqueID());
        return ids.size();
    }

    static void forgetFailedSpawn(EntityLiving mob) {
        Map<UUID, EntityLiving> members = WORLDS.get(mob.worldObj);
        if(members != null && members.get(mob.getUniqueID()) == mob && !mob.addedToChunk) members.remove(mob.getUniqueID());
    }

    public static boolean canAdmit(World world, Class<? extends EntityLiving> type, EntityLiving candidate, int limit) {
        return canAdmit(world, type, candidate, limit, HATCHING.get(candidate));
    }
    /** The source reservation is exchanged for its output, so full pending/global caps cannot deadlock hatching. */
    public static boolean canAdmitHatch(World world, Class<? extends EntityLiving> type, int limit, Entity source) {
        return canAdmit(world, type, null, limit, source);
    }
    private static boolean canAdmit(World world, Class<? extends EntityLiving> type, EntityLiving candidate, int limit, Entity source) {
        // Underground eggs also respect the absolute ceiling. Ocean Gardeners retain their own rules.
        if(type == EntityGardener.class && (world.isRemote || !GardenerNests.censusReady(world)
                || gardenerCount(world, candidate) >= Math.min(16, limit))) return false;
        if(GeminiSpawning.manualAdmission(candidate)) return true;
        if(world.isRemote || limit <= 0 || GeminiConfig.maxLoadedCreatures <= 0) return false;
        int total = 0, same = 0;
        // Apply pending biomass to Woodcrawler reproduction admissions without changing unrelated creature caps.
        boolean woodcrawler = type == EntityWoodcrawler.class;
        Map<UUID, Entity> cocoons = COCOONS.get(world);
        if(woodcrawler && cocoons != null) for(Entity cocoon : cocoons.values())
            if(cocoon != source && !cocoon.isDead) total++;
        if(total >= GeminiConfig.maxLoadedCreatures) return false;
        Map<UUID, EntityLiving> members = WORLDS.get(world);
        if(members == null) return true;
        for(EntityLiving mob : members.values()) {
            if(mob == candidate || mob.isDead || mob.worldObj != world) continue;
            // Newly admitted entities count immediately, before spawnEntityInWorld adds them to a chunk.
            if(++total >= GeminiConfig.maxLoadedCreatures || (type == EntityGardener.class ? mob.getClass() == type : type.isInstance(mob)) && ++same >= limit) return false;
        }
        return true;
    }
    public static boolean spawnFromCocoon(EntityLiving output, Entity source) {
        HATCHING.put(output, source);
        try { return output.worldObj.spawnEntityInWorld(output); }
        finally { HATCHING.remove(output); }
    }

    private static int limit(EntityLiving mob) {
        if(mob instanceof EntityWoodcrawler) return GeminiConfig.woodcrawlerMaxLoaded;
        if(mob instanceof EntityFakePerson) return GeminiConfig.fakePersonMaxLoaded;
        if(mob instanceof EntityNaturesMockery) return GeminiConfig.mockeryMaxActive;
        if(mob instanceof EntityOceanGardener) return GeminiConfig.oceanGardenerMaxLoaded;
        if(mob instanceof EntityGardener) return GeminiConfig.gardenerMaxLoaded;
        if(mob instanceof EntityWretch) return GeminiConfig.wretchMaxLoaded;
        return 0;
    }

    /** At most two Woodcrawlers per living prey or nearby prey cluster. Uses existing loaded membership. */
    public static boolean canWoodcrawlerHunt(EntityWoodcrawler seeker, EntityLivingBase target) {
        if(target == null || !seeker.isPrey(target)) return false;
        Map<UUID, EntityLiving> members = WORLDS.get(seeker.worldObj);
        if(members == null) return true;
        boolean incumbent = seeker.getAttackTarget() == target;
        int ahead = 0;
        for(EntityLiving entry : members.values()) {
            if(!(entry instanceof EntityWoodcrawler) || entry == seeker || !entry.isEntityAlive() || !entry.addedToChunk
                    || entry.worldObj != seeker.worldObj || seeker.worldObj.getEntityByID(entry.getEntityId()) != entry) continue;
            EntityWoodcrawler other = (EntityWoodcrawler) entry;
            EntityLivingBase prey = other.getAttackTarget();
            if(other.isDormant() || other.hasCapturedVillager() || !other.isPrey(prey)) continue;
            double dx = prey.posX - target.posX, dz = prey.posZ - target.posZ;
            if(prey != target && (dx * dx + dz * dz > 144D || Math.abs(prey.posY - target.posY) > 8D)) continue;
            // Existing hunts win admissions. A deterministic order resolves clusters that merge later.
            if((!incumbent || other.getEntityId() < seeker.getEntityId()) && ++ahead >= 2) return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void naturalSpawn(LivingSpawnEvent.SpecialSpawn event) {
        if(!event.world.isRemote && event.entityLiving instanceof IGeminiCreature)
            GeminiSpawning.markNatural(event.entityLiving);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void feedingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if(!event.entityLiving.worldObj.isRemote && event.source.getEntity() instanceof EntityGardener)
            ((EntityGardener) event.source.getEntity()).recordFeedingDeath(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void clearOrphanedGrip(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        EntityGardener.clearOrphanedGrip(event.entityLiving);
    }

    @SubscribeEvent
    public void captiveSuffocation(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if(event.source == net.minecraft.util.DamageSource.inWall && EntityGardener.preventsDraggingSuffocation(event.entityLiving))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void join(EntityJoinWorldEvent event) {
        if(!event.world.isRemote && !(event.entity instanceof EntityLivingBase))
            EntityGardener.clearOrphanedGrip(event.entity);
        if(!event.world.isRemote && (event.entity instanceof EntityGardenerCocoon || event.entity instanceof EntityWoodcrawlerCocoon)) {
            Entity cocoon = event.entity;
            Map<UUID, Entity> cocoons = COCOONS.get(event.world);
            if(cocoons == null) { cocoons = new HashMap<UUID, Entity>(); COCOONS.put(event.world, cocoons); }
            Entity duplicate = cocoons.get(cocoon.getUniqueID());
            if(duplicate != null && duplicate != cocoon && !duplicate.isDead
                    || !cocoon.addedToChunk && !canAdmitBiomass(event.world, cocoon instanceof EntityGardenerCocoon)) { event.setCanceled(true); return; }
            cocoons.put(cocoon.getUniqueID(), cocoon);
            return;
        }
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
        if(event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        if(event.world instanceof net.minecraft.world.WorldServer) GardenerNests.census((net.minecraft.world.WorldServer) event.world);
        if(event.world.getTotalWorldTime() % 20 != 0) return;
        Map<UUID, Entity> cocoons = COCOONS.get(event.world);
        if(cocoons != null) for(Iterator<Entity> it = cocoons.values().iterator(); it.hasNext();) {
            Entity cocoon = it.next();
            if(cocoon.isDead || !cocoon.addedToChunk || event.world.getEntityByID(cocoon.getEntityId()) != cocoon) it.remove();
        }
        Map<UUID, EntityLiving> members = WORLDS.get(event.world);
        if(members == null) return;
        for(Iterator<EntityLiving> it = members.values().iterator(); it.hasNext();) {
            EntityLiving mob = it.next();
            if(mob.isDead || !mob.addedToChunk || mob.worldObj != event.world
                    || event.world.getEntityByID(mob.getEntityId()) != mob
                    || !event.world.getChunkProvider().chunkExists(mob.chunkCoordX, mob.chunkCoordZ)) {
                if(mob instanceof EntityGardener) ((EntityGardener) mob).releaseForWorldUnload();
                it.remove();
            }
        }
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        Map<UUID, EntityLiving> members = WORLDS.remove(event.world);
        if(!event.world.isRemote && members != null) for(EntityLiving mob : members.values())
            if(mob instanceof EntityGardener) ((EntityGardener) mob).releaseForWorldUnload();
        COCOONS.remove(event.world);
    }

    @SubscribeEvent public void unloadChunk(ChunkEvent.Unload event) {
        if(event.world.isRemote) return;
        Map<UUID, Entity> cocoons = COCOONS.get(event.world);
        if(cocoons != null) for(Iterator<Entity> it = cocoons.values().iterator(); it.hasNext();) {
            Entity cocoon = it.next();
            if(cocoon.chunkCoordX == event.getChunk().xPosition && cocoon.chunkCoordZ == event.getChunk().zPosition) it.remove();
        }
        Map<UUID, EntityLiving> members = WORLDS.get(event.world);
        if(members == null) return;
        // Release membership immediately: a fast reload must not mistake the old instance for a duplicate.
        for(Iterator<EntityLiving> it = members.values().iterator(); it.hasNext();) {
            EntityLiving mob = it.next();
            if(mob instanceof EntityGardener) ((EntityGardener) mob).releaseForChunkUnload(event.getChunk().xPosition, event.getChunk().zPosition);
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
                    && ((EntityWoodcrawler) ally).isPrey(target) && ally.canEntityBeSeen(target)) {
                ally.setAttackTarget(target);
                if(ally.getAttackTarget() != target) continue;
            }
            else if(ally instanceof EntityFakePerson && !((EntityFakePerson) ally).isRecovering()
                    && ((EntityFakePerson) ally).canHunt(target) && ally.canEntityBeSeen(target)) ally.setAttackTarget(target);
            else continue;
            if(++alerted >= 4) break;
        }
    }
}
