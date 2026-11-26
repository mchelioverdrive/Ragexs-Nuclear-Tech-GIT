package com.hbm.world.squirrelstapler;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** A transient actor. The saved controller owns the encounter and its remaining duration. */
public final class EntityStaplerArrival extends Entity {
    public EntityStaplerArrival(World world) {
        super(world);
        setSize(2.4F, 5.5F);
        noClip = true;
    }

    @Override protected void entityInit() {}
    @Override public boolean attackEntityFrom(DamageSource source, float amount) { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isBurning() { return false; }
    @Override public int getBrightnessForRender(float partialTick) { return 0xF000F0; }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override public boolean writeMountToNBT(NBTTagCompound tag) { return false; }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
    @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
}
