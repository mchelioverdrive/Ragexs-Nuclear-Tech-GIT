package com.hbm.entity.mob.magmagolem;

import java.util.UUID;

import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Small vanilla-behaving magma cube with an encounter owner and a persistent lifetime. */
public final class EntityGolemMagmaCube extends EntityMagmaCube {
    static final int LIFETIME = 1000;
    private UUID owner;
    private long expires;

    public EntityGolemMagmaCube(World world) {
        super(world);
        setSlimeSize(1);
    }

    void assign(EntityGiantMagmaGolem boss) {
        owner = boss.getUniqueID();
        expires = worldObj.getTotalWorldTime() + LIFETIME;
    }

    boolean belongsTo(UUID boss) { return boss.equals(owner); }

    @Override public void onUpdate() {
        if (!worldObj.isRemote && (owner == null || worldObj.getTotalWorldTime() >= expires)) setDead();
        if (!isDead) super.onUpdate();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (owner != null) {
            tag.setLong("GolemMost", owner.getMostSignificantBits());
            tag.setLong("GolemLeast", owner.getLeastSignificantBits());
        }
        tag.setLong("Expires", expires);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        setSlimeSize(1);
        if (tag.hasKey("GolemMost")) owner = new UUID(tag.getLong("GolemMost"), tag.getLong("GolemLeast"));
        expires = tag.getLong("Expires");
    }
}
