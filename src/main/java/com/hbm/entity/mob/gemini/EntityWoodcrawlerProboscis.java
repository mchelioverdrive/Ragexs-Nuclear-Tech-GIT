package com.hbm.entity.mob.gemini;

import com.hbm.world.gemini.GardenerNests;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Disposable exposed contact segments; the saved Woodcrawler owns the attack. */
public final class EntityWoodcrawlerProboscis extends Entity {
    private EntityWoodcrawler parent;
    public EntityWoodcrawlerProboscis(World world) { super(world); noClip = true; setSize(0.16F, 0.16F); }
    @Override protected void entityInit() { dataWatcher.addObject(20, -1); dataWatcher.addObject(21, (byte) 0); }
    public void attach(EntityWoodcrawler mob, int segment) {
        parent = mob; dataWatcher.updateObject(20, mob.getEntityId()); dataWatcher.updateObject(21, (byte) segment); follow();
    }
    private EntityWoodcrawler owner() {
        Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
        if(parent == null || entity != parent) parent = entity instanceof EntityWoodcrawler ? (EntityWoodcrawler) entity : null;
        return parent;
    }
    public void follow() {
        EntityWoodcrawler mob = owner();
        if(mob == null) return;
        AxisAlignedBB box = mob.proboscisBox(dataWatcher.getWatchableObjectByte(21));
        setPosition((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
        boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
    @Override public void onUpdate() {
        super.onUpdate();
        EntityWoodcrawler mob = owner();
        if(mob == null || !mob.isEntityAlive() || !mob.isProbing() || !mob.addedToChunk
                || !GardenerNests.loaded(worldObj, boundingBox)) {
            if(!worldObj.isRemote || ticksExisted > 100) setDead();
            return;
        }
        follow();
    }
    @Override public boolean canBeCollidedWith() { return owner() != null && owner().isProbing(); }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        return owner() != null && owner().hitProboscis(dataWatcher.getWatchableObjectByte(21), source, damage);
    }
    @Override public boolean writeToNBTOptional(NBTTagCompound nbt) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) { }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) { setDead(); }
}
