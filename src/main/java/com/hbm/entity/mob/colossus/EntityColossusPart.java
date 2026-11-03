package com.hbm.entity.mob.colossus;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Only eight functional contact regions are entities; decorative model pieces never tick. */
public final class EntityColossusPart extends Entity {
    private EntityPrismarineColossus owner;
    public EntityColossusPart(World w) { super(w); noClip=true; isImmuneToFire=true; setSize(1,1); }
    @Override protected void entityInit() { dataWatcher.addObject(20,0); dataWatcher.addObject(21,(byte)0); }
    public void attach(EntityPrismarineColossus boss, int slot) { owner=boss; dataWatcher.updateObject(20,boss.getEntityId()); dataWatcher.updateObject(21,(byte)slot); follow(); }
    public int slot() { return dataWatcher.getWatchableObjectByte(21); }
    public EntityPrismarineColossus owner() {
        if(owner == null || owner.isDead) { Entity e=worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20)); owner=e instanceof EntityPrismarineColossus ? (EntityPrismarineColossus)e : null; }
        return owner;
    }
    public void follow() {
        EntityPrismarineColossus b=owner(); if(b==null) return;
        AxisAlignedBB box=b.partBox(slot());
        setPosition((box.minX+box.maxX)/2,box.minY,(box.minZ+box.maxZ)/2);
        boundingBox.setBounds(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
    }
    @Override public void onUpdate() {
        super.onUpdate(); EntityPrismarineColossus b=owner();
        if(b==null || b.isDead || b.worldObj != worldObj) { if(!worldObj.isRemote || ticksExisted>100) setDead(); return; }
        follow();
    }
    @Override public boolean canBeCollidedWith() { return owner()!=null && owner().isEntityAlive(); }
    @Override public AxisAlignedBB getBoundingBox() { return canBeCollidedWith() && slot()!=2 ? boundingBox : null; }
    @Override public boolean attackEntityFrom(DamageSource source,float damage) { return owner()!=null && owner().hit(slot(),source,damage); }
    @Override public boolean interactFirst(EntityPlayer player) { return owner()!=null && owner().interactFirst(player); }
    @Override public boolean isEntityEqual(Entity e) { return e==this || e==owner(); }
    @Override public boolean writeToNBTOptional(NBTTagCompound n) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound n) { }
    @Override protected void readEntityFromNBT(NBTTagCompound n) { setDead(); }
}
