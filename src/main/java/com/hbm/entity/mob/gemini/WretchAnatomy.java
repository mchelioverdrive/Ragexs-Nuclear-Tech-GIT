package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Visual remains only: never stores an entity, inventory, infection or player profile. */
public final class WretchAnatomy {
    public static final int LIMIT = 12;
    private final ArrayList<Remain> remains = new ArrayList<Remain>(LIMIT);
    private static final class Remain {
        final UUID id; final int seed, kind;
        Remain(UUID id, int seed, int kind) { this.id = id; this.seed = seed; this.kind = kind; }
    }
    public int size() { return remains.size(); }
    public int seed(int index) { return remains.get(index).seed; }
    public int kind(int index) { return remains.get(index).kind; }
    boolean incorporate(EntityLivingBase victim) {
        UUID id = victim.getUniqueID();
        for(Remain r : remains) if(r.id.equals(id)) return false;
        // Mature anatomy has a fixed budget; older remains continue to be visible at the cap.
        if(remains.size() >= LIMIT) return false;
        int seed = id.hashCode() & 0x7FFFFFFF;
        remains.add(new Remain(id, seed, victim instanceof EntityPlayer ? 1 : victim instanceof EntityVillager ? 0 : 2));
        return true;
    }
    void write(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for(Remain r : remains) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Id", r.id.toString());
            tag.setInteger("Seed", r.seed); tag.setInteger("Kind", r.kind); list.appendTag(tag);
        }
        nbt.setTag("WretchRemains", list);
    }
    void read(NBTTagCompound nbt) {
        remains.clear(); NBTTagList list = nbt.getTagList("WretchRemains", 10);
        for(int i = 0; i < Math.min(LIMIT, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i); UUID id;
            try { id = UUID.fromString(tag.getString("Id")); } catch(IllegalArgumentException e) { continue; }
            boolean duplicate = false; for(Remain r : remains) if(r.id.equals(id)) duplicate = true;
            if(!duplicate) remains.add(new Remain(id, tag.getInteger("Seed") & 0x7FFFFFFF, Math.max(0, Math.min(2, tag.getInteger("Kind")))));
        }
    }
    public void writeVisual(ByteBuf buf) {
        buf.writeByte(remains.size());
        for(Remain r : remains) { buf.writeInt(r.seed); buf.writeByte(r.kind); }
    }
    public void readVisual(ByteBuf buf) {
        int count = buf.readUnsignedByte();
        if(count > LIMIT || buf.readableBytes() < count * 5) throw new IllegalArgumentException("Invalid Wretch anatomy");
        remains.clear();
        for(int i = 0; i < count; i++) {
            int seed = buf.readInt() & 0x7FFFFFFF, kind = buf.readUnsignedByte();
            if(kind > 2) throw new IllegalArgumentException("Invalid Wretch remain kind");
            remains.add(new Remain(new UUID(0, i), seed, kind));
        }
    }
    public void copyVisual(WretchAnatomy source) {
        remains.clear(); remains.addAll(source.remains);
    }
}
