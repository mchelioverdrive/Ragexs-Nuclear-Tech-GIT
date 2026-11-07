package com.hbm.entity.mob.gemini;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import com.hbm.world.gemini.GardenerNests;

/** Disposable tracked collision region; the saved parent owns all limb health and behavior. */
public final class EntityGardenerTentacle extends Entity {
    private EntityGardener parent;
    public EntityGardenerTentacle(World world) { super(world); noClip = true; setSize(0.6F, 0.6F); }
    @Override protected void entityInit() { dataWatcher.addObject(20, -1); dataWatcher.addObject(21, (byte) 0); }
    public void attach(EntityGardener boss, int slot) {
        parent = boss; dataWatcher.updateObject(20, boss.getEntityId()); dataWatcher.updateObject(21, (byte) slot); follow();
    }
    public int slot() { return dataWatcher.getWatchableObjectByte(21); }
    public EntityGardener owner() {
        if(parent == null || parent.isDead) {
            Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
            parent = entity instanceof EntityGardener ? (EntityGardener) entity : null;
        }
        return parent;
    }
    public void follow() {
        EntityGardener boss = owner();
        if(boss == null) return;
        AxisAlignedBB box = boss.segmentBox(slot());
        // Index tall hanging segments by their center rather than their distant lower endpoint.
        setPosition((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
        boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
    @Override public void onUpdate() {
        super.onUpdate();
        EntityGardener boss = owner();
        if(boss == null || !boss.isEntityAlive() || !boss.addedToChunk || worldObj.getEntityByID(boss.getEntityId()) != boss
                || !GardenerNests.loaded(worldObj, boundingBox.expand(1, 0, 1))) {
            if(!worldObj.isRemote || ticksExisted > 100) setDead();
            return;
        }
        follow();
    }
    @Override public boolean canBeCollidedWith() { return owner() != null && owner().isEntityAlive() && !owner().armDisabled(slot() / GardenerPose.SEGMENTS); }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) { return canBeCollidedWith() && owner().hitTentacle(slot(), source, amount); }
    @Override public boolean isEntityEqual(Entity other) { return other == this || other == owner(); }
    @Override public boolean writeToNBTOptional(NBTTagCompound nbt) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) { }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) { setDead(); }
}
