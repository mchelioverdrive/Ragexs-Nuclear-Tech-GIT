package com.hbm.entity.mob.witherstorm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/** The Overworld ledger counts unloaded encounters and owns the world challenge clock. */
public final class WitherStormEncounters extends WorldSavedData {
    private static final String NAME = "rntWitherStorms";
    private static final long CHALLENGE_TICKS = 24000L;
    private final Map<UUID, Integer> encounters = new HashMap<UUID, Integer>();
    private final Map<UUID, Entity> loaded = new HashMap<UUID, Entity>();
    private byte challengeOverride;
    private boolean challengeActive, challengeCompleted;
    private long challengeStart;
    private long retryAt;
    private final Map<Integer, WitherStormPursuit.Trails> trails = new HashMap<>();
    WitherStormPursuit.Trails playerTrails(World world) {
        return trails.computeIfAbsent(world.provider.dimensionId, id -> new WitherStormPursuit.Trails());
    }
    public WitherStormEncounters() { super(NAME); }
    public WitherStormEncounters(String name) { super(name); }

    public static WitherStormEncounters get(World world) {
        World root = DimensionManager.getWorld(0);
        if(root == null) root = world;
        WitherStormEncounters data = (WitherStormEncounters)root.mapStorage.loadData(WitherStormEncounters.class, NAME);
        if(data == null) { data = new WitherStormEncounters(); root.mapStorage.setData(NAME, data); }
        return data;
    }
    public boolean contains(UUID id) { return encounters.containsKey(id); }
    public Map<UUID, Integer> entries() { return java.util.Collections.unmodifiableMap(encounters); }
    public boolean admit(EntityWitherStorm boss) {
        UUID id = boss.getUniqueID();
        if(!encounters.containsKey(id)) {
            if(encounters.size() >= WitherStormConfig.maxEncounters) return false;
            encounters.put(id, boss.dimension);
            markDirty();
        }
        Entity other = loaded.get(id);
        if(other != null && other != boss && !other.isDead) return false;
        loaded.put(id, boss);
        return true;
    }
    public boolean room() { return encounters.size() < WitherStormConfig.maxEncounters; }
    EntityWitherStorm nearestThreat(EntityLivingBase mob, double range) {
        EntityWitherStorm nearest = null;
        double best = range * range;
        for(UUID id : encounters.keySet()) {
            Entity entity = loaded.get(id);
            if(!(entity instanceof EntityWitherStorm) || entity.worldObj != mob.worldObj || !entity.isEntityAlive()) continue;
            double distance = mob.getDistanceSqToEntity(entity);
            if(distance <= best) { nearest = (EntityWitherStorm)entity; best = distance; }
        }
        return nearest;
    }

    public void setChallenge(World world, boolean enabled) {
        challengeOverride = (byte)(enabled ? 1 : 2);
        updateChallenge(world);
        markDirty();
    }

    private void updateChallenge(World world) {
        boolean enabled = challengeOverride == 0 ? WitherStormConfig.challengeMode : challengeOverride == 1;
        if(challengeActive == enabled) return;
        challengeActive = enabled;
        if(enabled && !challengeCompleted) {
            challengeStart = world.getTotalWorldTime();
            retryAt = challengeStart;
        }
        if(!challengeCompleted) trails.clear();
        markDirty();
    }

    private long remaining(World world) {
        return Math.max(0, CHALLENGE_TICKS - (world.getTotalWorldTime() - challengeStart));
    }

    public String challengeStatus(World world) {
        updateChallenge(world);
        String prefix = "Wither Storm challenge " + (challengeActive ? "enabled. " : "disabled. ");
        if(challengeCompleted) return prefix + "The automatic spawn has already occurred.";
        if(!challengeActive) return prefix + "No countdown is active.";
        long ticks = remaining(world);
        return prefix + (ticks > 0 ? ticks + " ticks remaining." : "0 ticks remaining. Spawn pending.");
    }

    void tickChallenge(World world) {
        updateChallenge(world);
        if(!challengeActive || challengeCompleted) return;
        playerTrails(world).sample(world);
        long now = world.getTotalWorldTime();
        if(remaining(world) != 0 || now < retryAt || !room()) return;
        boolean online = false;
        for(Object value : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if(value instanceof EntityPlayerMP && WitherStormPursuit.connected((EntityPlayerMP)value)) {
                online = true;
                break;
            }
        }
        if(!online) return;
        retryAt = now + 20;
        ChunkCoordinates spawn = world.getSpawnPoint();
        // Search the loaded spawn area for open space above the local surface.
        for(int radius = 0; radius <= 16; radius += 8) {
            for(int dx = -radius; dx <= radius; dx += 8) for(int dz = -radius; dz <= radius; dz += 8) {
                if(Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = spawn.posX + dx, z = spawn.posZ + dz;
                if(!WitherStormMath.areaLoaded(world, x + 0.5D, z + 0.5D, 3)) continue;
                int y = Math.max(world.getTopSolidOrLiquidBlock(x, z), world.getHeightValue(x, z)) + 7;
                AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 0.5D, y, z - 0.5D, x + 1.5D, y + 4, z + 1.5D);
                if(!world.func_147461_a(box).isEmpty() || !world.checkNoEntityCollision(box) || world.isAnyLiquid(box)) continue;
                if(WitherStormEvents.spawnInitial(world, x + 0.5D, y, z + 0.5D, 0, true)) {
                    challengeCompleted = true;
                    markDirty();
                }
                return;
            }
        }
    }
    public void track(Entity e) {
        if(e instanceof EntityWitherStormSkull) {
            EntityWitherStormSkull incoming = (EntityWitherStormSkull)e, oldest = null;
            if(incoming.parentId == null || !contains(incoming.parentId) || incoming.expired()) { e.setDead(); return; }
            int count = 0;
            for(Entity value : loaded.values()) if(value instanceof EntityWitherStormSkull) {
                EntityWitherStormSkull skull = (EntityWitherStormSkull)value;
                if(incoming.parentId != null && incoming.parentId.equals(skull.parentId)) {
                    count++;
                    if(oldest == null || skull.birthTime() < oldest.birthTime()) oldest = skull;
                }
            }
            if(count >= 24 && oldest != null) oldest.setDead();
        }
        loaded.put(e.getUniqueID(), e);
    }
    public void pruneProjectiles(EntityWitherStorm boss) {
        java.util.Iterator<Entity> it = loaded.values().iterator();
        while(it.hasNext()) {
            Entity e = it.next();
            if(!(e instanceof EntityWitherStormSkull)) continue;
            EntityWitherStormSkull skull = (EntityWitherStormSkull)e;
            // Vanilla can suspend individual entity ticks near unloaded chunks.
            // Root-owned expiry and a hard cap also cover those suspended skulls.
            if(boss.getUniqueID().equals(skull.parentId) && (skull.expired() || !WitherStormMath.areaLoaded(skull.worldObj, skull.posX, skull.posZ, 34))) {
                it.remove(); skull.setDead();
            }
        }
    }
    public void untrack(Entity e) { if(loaded.get(e.getUniqueID()) == e) loaded.remove(e.getUniqueID()); }
    public Entity find(UUID id, World world) {
        Entity e = loaded.get(id);
        return e != null && !e.isDead && e.worldObj == world && WitherStormMath.loaded(world, e.posX, e.posZ) ? e : null;
    }
    public void unload(World world) { loaded.values().removeIf(e -> e.worldObj == world); trails.remove(world.provider.dimensionId); }
    public boolean remove(UUID id) {
        if(encounters.remove(id) == null) return false;
        Entity e = loaded.remove(id);
        if(e != null) e.setDead();
        // Unloaded components and bosses consult this ledger before resuming.
        for(Entity part : loaded.values().toArray(new Entity[loaded.size()])) {
            if(part instanceof EntityWitherStormPart && id.equals(((EntityWitherStormPart)part).parentId)) part.setDead();
            if(part instanceof EntityWitherStormSkull && id.equals(((EntityWitherStormSkull)part).parentId)) part.setDead();
        }
        markDirty();
        return true;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        challengeOverride = tag.getByte("ChallengeOverride");
        challengeActive = tag.getBoolean("ChallengeActive");
        challengeCompleted = tag.getBoolean("ChallengeCompleted");
        challengeStart = tag.getLong("ChallengeStart");
        encounters.clear(); loaded.clear();
        NBTTagList list = tag.getTagList("Encounters", 10);
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            UUID id = WitherStormMath.uuid(n, "UUID");
            if(id != null) encounters.put(id, n.getInteger("Dimension"));
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setByte("ChallengeOverride", challengeOverride);
        tag.setBoolean("ChallengeActive", challengeActive);
        tag.setBoolean("ChallengeCompleted", challengeCompleted);
        tag.setLong("ChallengeStart", challengeStart);
        NBTTagList list = new NBTTagList();
        for(Map.Entry<UUID, Integer> entry : encounters.entrySet()) {
            NBTTagCompound n = new NBTTagCompound();
            WitherStormMath.uuid(n, "UUID", entry.getKey()); n.setInteger("Dimension", entry.getValue()); list.appendTag(n);
        }
        tag.setTag("Encounters", list);
    }
}
