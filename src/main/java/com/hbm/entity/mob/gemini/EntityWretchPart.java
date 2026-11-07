package com.hbm.entity.mob.gemini;

import com.hbm.world.gemini.GardenerNests;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Unsaved short bone slices and palms. Embedded people are model children, never entities. */
public final class EntityWretchPart extends Entity {
    private EntityWretch parent;
    public EntityWretchPart(World world) { super(world); noClip = true; setSize(0.6F, 0.6F); }
    @Override protected void entityInit() { dataWatcher.addObject(20, -1); dataWatcher.addObject(21, (byte) 0); }
    public void attach(EntityWretch boss, int slot) {
        parent = boss; dataWatcher.updateObject(20, boss.getEntityId()); dataWatcher.updateObject(21, (byte) slot); follow();
    }
    public int slot() { return dataWatcher.getWatchableObjectByte(21); }
    public EntityWretch owner() {
        if(parent == null || parent.isDead) {
            Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
            parent = entity instanceof EntityWretch ? (EntityWretch) entity : null;
        }
        return parent;
    }
    public void follow() {
        EntityWretch boss = owner();
        if(boss == null) return;
        if(slot() < 0 || slot() >= WretchPose.PARTS) { setDead(); return; }
        AxisAlignedBB box = boss.partBox(slot());
        setPosition((box.minX + box.maxX) * 0.5D, box.minY, (box.minZ + box.maxZ) * 0.5D);
        boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
    @Override public void onUpdate() {
        super.onUpdate();
        EntityWretch boss = owner();
        if(boss == null || !boss.isEntityAlive() || !boss.addedToChunk || !boss.isOperational()
                || !GardenerNests.loaded(worldObj, boundingBox.expand(1, 0, 1))) {
            if(!worldObj.isRemote || ticksExisted > 100) setDead();
            return;
        }
        follow();
    }
    @Override public boolean canBeCollidedWith() { return owner() != null && owner().isOperational(); }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) { return canBeCollidedWith() && owner().hitPart(slot(), source, damage); }
    @Override public boolean isEntityEqual(Entity other) { return other == this || other == owner(); }
    @Override public boolean writeToNBTOptional(NBTTagCompound nbt) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) { }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) { setDead(); }
}
