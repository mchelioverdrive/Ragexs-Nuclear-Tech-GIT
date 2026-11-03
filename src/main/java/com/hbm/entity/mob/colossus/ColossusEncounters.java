package com.hbm.entity.mob.colossus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.ColossusConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Per-dimension saved admission ledger; unloaded statues still occupy their slot. */
public final class ColossusEncounters extends WorldSavedData {
    private final Set<UUID> encounters = new HashSet<UUID>();
    private long budgetTick = Long.MIN_VALUE;
    private int spent;
    public ColossusEncounters() { super("rntColossi"); }
    public ColossusEncounters(String name) { super(name); }
    public static ColossusEncounters get(World w) {
        ColossusEncounters data = (ColossusEncounters)w.perWorldStorage.loadData(ColossusEncounters.class, "rntColossi");
        if(data == null) { data = new ColossusEncounters(); w.perWorldStorage.setData("rntColossi", data); }
        return data;
    }
    public boolean room() { return ColossusConfig.enabled && encounters.size() < ColossusConfig.maximum; }
    public boolean admit(UUID id) { if(encounters.contains(id)) return true; if(!room()) return false; encounters.add(id); markDirty(); return true; }
    public void remove(UUID id) { if(encounters.remove(id)) markDirty(); }
    public boolean spend(long tick) { if(tick != budgetTick) { budgetTick = tick; spent = 0; } return spent++ < ColossusConfig.blocks; }
    @Override public void readFromNBT(NBTTagCompound n) {
        encounters.clear(); NBTTagList list = n.getTagList("Encounters", 10);
        for(int i=0; i<list.tagCount(); i++) { try { encounters.add(UUID.fromString(list.getCompoundTagAt(i).getString("UUID"))); } catch(IllegalArgumentException ignored) { } }
    }
    @Override public void writeToNBT(NBTTagCompound n) {
        NBTTagList list = new NBTTagList();
        for(UUID id : encounters) { NBTTagCompound tag = new NBTTagCompound(); tag.setString("UUID", id.toString()); list.appendTag(tag); }
        n.setTag("Encounters", list);
    }
}
