package com.hbm.entity.mob.giantghast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hbm.config.GiantGhastConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.world.ExplosionEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Dimension-local ownership survives the boss and its children unloading in any order. */
public final class GiantGhastEncounters extends WorldSavedData {
    private static final String NAME = "rntGiantGhasts";
    private final Map<UUID, Fight> fights = new HashMap<>();

    public GiantGhastEncounters(String name) { super(name); }

    public static GiantGhastEncounters get(World world) {
        GiantGhastEncounters data = (GiantGhastEncounters)world.perWorldStorage.loadData(GiantGhastEncounters.class, NAME);
        if (data == null) {
            data = new GiantGhastEncounters(NAME);
            world.perWorldStorage.setData(NAME, data);
        }
        return data;
    }

    static final class Fight {
        int bossId;
        final Map<UUID, Child> children = new HashMap<>();
    }

    private static final class Child {
        final long expires;
        int entityId;

        Child(long expires) { this.expires = expires; }
    }

    Fight attach(EntityGiantGhast boss, boolean saved) {
        UUID id = boss.getUniqueID();
        Fight fight = fights.get(id);
        if (fight == null) {
            if (saved) return null;
            fight = new Fight();
            fights.put(id, fight);
            markDirty();
        }
        Entity current = boss.worldObj.getEntityByID(fight.bossId);
        if (current != null && current != boss && current.getUniqueID().equals(id)) return null;
        fight.bossId = boss.getEntityId();
        return fight;
    }

    boolean isEmpty() { return fights.isEmpty(); }

    Fight find(UUID boss) { return fights.get(boss); }

    EntityGiantGhast boss(World world, UUID id) {
        Fight fight = find(id);
        Entity entity = fight == null ? null : world.getEntityByID(fight.bossId);
        return entity instanceof EntityGiantGhast && entity.getUniqueID().equals(id) && entity.isEntityAlive()
            ? (EntityGiantGhast)entity : null;
    }

    boolean bindChild(UUID boss, Entity entity, long expires) {
        Fight fight = find(boss);
        Child child = fight == null ? null : fight.children.get(entity.getUniqueID());
        if (child == null || child.expires != expires) return false;
        Entity current = entity.worldObj.getEntityByID(child.entityId);
        if (current != null && current != entity && current.getUniqueID().equals(entity.getUniqueID())) return false;
        child.entityId = entity.getEntityId();
        return true;
    }

    boolean spawnChild(EntityGiantGhast boss, Entity child, long expires) {
        Fight fight = find(boss.getUniqueID());
        if (fight == null || count(boss.worldObj, fight, expires != 0) >=
                (expires == 0 ? GiantGhastConfig.maxSummons : GiantGhastConfig.maxFireballs)) return false;
        Child record = new Child(expires);
        record.entityId = child.getEntityId();
        fight.children.put(child.getUniqueID(), record);
        if (!boss.worldObj.spawnEntityInWorld(child)) {
            fight.children.remove(child.getUniqueID());
            return false;
        }
        markDirty();
        return true;
    }

    int count(World world, Fight fight, boolean projectiles) {
        int count = 0;
        Iterator<Child> entries = fight.children.values().iterator();
        long now = world.getTotalWorldTime();
        while (entries.hasNext()) {
            Child child = entries.next();
            if (child.expires != 0 && child.expires <= now) {
                entries.remove();
                markDirty();
            } else if ((child.expires != 0) == projectiles) {
                count++;
            }
        }
        return count;
    }

    void forget(UUID boss, Entity entity) {
        Fight fight = find(boss);
        Child child = fight == null ? null : fight.children.get(entity.getUniqueID());
        if (child != null && (child.entityId == 0 || child.entityId == entity.getEntityId())) {
            fight.children.remove(entity.getUniqueID());
            markDirty();
        }
    }

    Entity member(World world, UUID boss, UUID id) {
        Fight fight = find(boss);
        if (id == null || fight == null) return null;
        if (boss.equals(id)) return boss(world, id);
        Child child = fight.children.get(id);
        return child == null ? null : loadedChild(world, id, child);
    }

    private static Entity loadedChild(World world, UUID id, Child child) {
        Entity entity = world.getEntityByID(child.entityId);
        return entity != null && entity.getUniqueID().equals(id) && !entity.isDead ? entity : null;
    }

    List<EntitySummonedGhast> summons(World world, Fight fight) {
        List<EntitySummonedGhast> result = new ArrayList<>();
        for (Map.Entry<UUID, Child> entry : fight.children.entrySet()) {
            if (entry.getValue().expires != 0) continue;
            Entity entity = loadedChild(world, entry.getKey(), entry.getValue());
            if (entity instanceof EntitySummonedGhast && entity.isEntityAlive()) result.add((EntitySummonedGhast)entity);
        }
        return result;
    }

    void retire(World world, UUID id) {
        Fight fight = fights.remove(id);
        if (fight == null) return;
        markDirty();
        for (Map.Entry<UUID, Child> entry : fight.children.entrySet()) {
            Entity entity = loadedChild(world, entry.getKey(), entry.getValue());
            if (entity != null) entity.setDead();
        }
    }

    public int removeAll(World world) {
        int removed = fights.size();
        for (UUID id : new ArrayList<>(fights.keySet())) {
            EntityGiantGhast boss = boss(world, id);
            retire(world, id);
            if (boss != null) boss.setDead();
        }
        // Also include entities spawned or reloaded before their first encounter tick.
        for (Object object : new ArrayList<>(world.loadedEntityList)) {
            if (object instanceof EntityGiantGhast && !((Entity)object).isDead) {
                EntityGiantGhast boss = (EntityGiantGhast)object;
                if (!boss.wasAdmitted()) removed++;
                boss.setDead();
            } else if (object instanceof EntitySummonedGhast || object instanceof EntityGiantGhastFireball) {
                ((Entity)object).setDead();
            }
        }
        return removed;
    }

    static EntityPlayer player(World world, UUID id) {
        if (id == null) return null;
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer)object;
            if (id.equals(player.getUniqueID())) return player;
        }
        return null;
    }

    static void writeId(NBTTagCompound tag, String key, UUID id) {
        if (id != null) {
            tag.setLong(key + "Most", id.getMostSignificantBits());
            tag.setLong(key + "Least", id.getLeastSignificantBits());
        }
    }

    static UUID readId(NBTTagCompound tag, String key) {
        return tag.hasKey(key + "Most", 4) && tag.hasKey(key + "Least", 4)
            ? new UUID(tag.getLong(key + "Most"), tag.getLong(key + "Least")) : null;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        fights.clear();
        NBTTagList entries = tag.getTagList("Fights", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            UUID id = readId(entry, "Boss");
            if (id == null) continue;
            Fight fight = new Fight();
            NBTTagList children = entry.getTagList("Children", 10);
            for (int j = 0; j < children.tagCount(); j++) {
                NBTTagCompound child = children.getCompoundTagAt(j);
                UUID childId = readId(child, "Id");
                if (childId != null) fight.children.put(childId, new Child(child.getLong("Expires")));
            }
            fights.put(id, fight);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<UUID, Fight> fight : fights.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            writeId(entry, "Boss", fight.getKey());
            NBTTagList children = new NBTTagList();
            for (Map.Entry<UUID, Child> child : fight.getValue().children.entrySet()) {
                NBTTagCompound record = new NBTTagCompound();
                writeId(record, "Id", child.getKey());
                record.setLong("Expires", child.getValue().expires);
                children.appendTag(record);
            }
            entry.setTag("Children", children);
            entries.appendTag(entry);
        }
        tag.setTag("Fights", entries);
    }

    public static final class Effects {
        @SubscribeEvent
        public void onExplosion(ExplosionEvent.Detonate event) {
            if (event.explosion.exploder instanceof EntityGiantGhastFireball) {
                EntityGiantGhastFireball ball = (EntityGiantGhastFireball)event.explosion.exploder;
                // The direct hit carries shooter credit and drives retaliation. Splash still hits bystanders.
                event.getAffectedEntities().removeIf(ball::isEncounterMember);
            }
        }
    }
}
