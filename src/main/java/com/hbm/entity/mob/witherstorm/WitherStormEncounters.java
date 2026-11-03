package com.hbm.entity.mob.witherstorm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/** The overworld ledger counts unloaded encounters too; it never owns chunk tickets. */
public final class WitherStormEncounters extends WorldSavedData {
    private static final String NAME = "rntWitherStorms";
    private final Map<UUID, Integer> encounters = new HashMap<UUID, Integer>();
    private final Map<UUID, Entity> loaded = new HashMap<UUID, Entity>();
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
    public void unload(World world) { loaded.values().removeIf(e -> e.worldObj == world); }
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
        encounters.clear(); loaded.clear();
        NBTTagList list = tag.getTagList("Encounters", 10);
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            UUID id = WitherStormMath.uuid(n, "UUID");
            if(id != null) encounters.put(id, n.getInteger("Dimension"));
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for(Map.Entry<UUID, Integer> entry : encounters.entrySet()) {
            NBTTagCompound n = new NBTTagCompound();
            WitherStormMath.uuid(n, "UUID", entry.getKey()); n.setInteger("Dimension", entry.getValue()); list.appendTag(n);
        }
        tag.setTag("Encounters", list);
    }
}
