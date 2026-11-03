package com.hbm.entity.mob.witherstorm;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A damageable attachment, never an autonomous mob or a separate encounter. */
public class EntityWitherStormPart extends Entity {
    public UUID parentId;
    public EntityWitherStorm parent;
    private int parentChunkX, parentChunkZ;
    public EntityWitherStormPart(World world) { super(world); noClip = true; isImmuneToFire = true; ignoreFrustumCheck = true; setSize(2, 2); }
    @Override protected void entityInit() {
        dataWatcher.addObject(20, Integer.valueOf(0));
        dataWatcher.addObject(21, Byte.valueOf((byte)0));
        dataWatcher.addObject(22, Float.valueOf(0.35F));
    }
    public int slot() { return dataWatcher.getWatchableObjectByte(21); }
    public float scale() { return dataWatcher.getWatchableObjectFloat(22); }
    public void attach(EntityWitherStorm boss, int slot, UUID id) {
        parent = boss; parentId = boss.getUniqueID();
        if(id != null) entityUniqueID = id;
        dataWatcher.updateObject(20, boss.getEntityId()); dataWatcher.updateObject(21, (byte)slot);
    }
    public EntityWitherStorm parent() {
        if(worldObj.isRemote) {
            Entity e = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
            return e instanceof EntityWitherStorm && !e.isDead ? (EntityWitherStorm)e : null;
        }
        if(parent != null && !parent.isDead && parent.worldObj == worldObj && WitherStormMath.loaded(worldObj, parent.posX, parent.posZ)) return parent;
        Entity e = WitherStormEncounters.get(worldObj).find(parentId, worldObj);
        parent = e instanceof EntityWitherStorm ? (EntityWitherStorm)e : null;
        return parent;
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if(worldObj.isRemote) {
            // Client ray selection needs the same growing hitboxes as the server.
            updateBounds(parent());
            return;
        }
        if(slot() < 0 || slot() > 9 || parentId == null) { setDead(); return; }
        EntityWitherStorm boss = parent();
        if(boss != null && boss.isEntityAlive()) {
            if(slot() >= 3 && boss.getPhase() == WitherStormPhase.COMMAND_BLOCK) setDead();
            else followParent();
        }
        else {
            parent = null;
            // Parent chunks may load after their parts. A saved live encounter waits,
            // without attacking or spawning anything; removed encounters clean up here.
            if(ticksExisted > 100 && !WitherStormEncounters.get(worldObj).contains(parentId)) setDead();
        }
    }
    public void followParent() {
        EntityWitherStorm boss = parent(); if(boss == null) return;
        dataWatcher.updateObject(20, boss.getEntityId()); dataWatcher.updateObject(22, boss.getStormScale());
        Vec3 position = boss.partPosition(slot());
        if(!WitherStormMath.loaded(worldObj, position.xCoord, position.zCoord)) return;
        setPosition(position.xCoord, position.yCoord, position.zCoord);
        updateBounds(boss);
        parentChunkX = MathHelper.floor_double(boss.posX) >> 4; parentChunkZ = MathHelper.floor_double(boss.posZ) >> 4;
        if(slot() < 3) {
            Entity target = worldObj.getEntityByID(boss.headTarget(slot()));
            float yaw = boss.rotationYaw, pitch = 0;
            if(WitherStormMath.edible(target, worldObj)) {
                double dx = target.posX - posX, dz = target.posZ - posZ, dy = target.posY + target.height * 0.5D - (posY + height * 0.5D);
                yaw = (float)(Math.atan2(-dx, dz) * 180D / Math.PI); pitch = (float)(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180D / Math.PI);
            }
            rotationYaw += MathHelper.wrapAngleTo180_float(yaw - rotationYaw) * 0.12F;
            rotationPitch += (pitch - rotationPitch) * 0.12F;
        }
    }
    private void updateBounds(EntityWitherStorm boss) {
        if(slot() < 3) {
            double x = posX, y = posY, z = posZ;
            setSize(2.5F * scale(), 2.5F * scale()); setPosition(x, y, z);
        } else if(boss != null) {
            Vec3 root = boss.tentacleRoot(slot()); double padding = (slot() >= 8 ? 1.4D : 0.7D) * scale();
            boundingBox.setBounds(Math.min(posX, root.xCoord) - padding, Math.min(posY, root.yCoord) - padding, Math.min(posZ, root.zCoord) - padding,
                Math.max(posX, root.xCoord) + padding, Math.max(posY, root.yCoord) + padding, Math.max(posZ, root.zCoord) + padding);
        }
    }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getBoundingBox() { return boundingBox; }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        EntityWitherStorm boss = parent();
        if(worldObj.isRemote || boss == null || !boss.isEntityAlive()) return false;
        // One root health pool preserves normal armor, damage events and invulnerability frames.
        return boss.attackEntityFrom(source, damage * (slot() < 3 ? 1F : 0.5F));
    }
    @Override public void setDead() {
        super.setDead();
        if(!worldObj.isRemote) WitherStormEncounters.get(worldObj).untrack(this);
    }
    @Override protected void writeEntityToNBT(NBTTagCompound n) {
        WitherStormMath.uuid(n, "Parent", parentId); WitherStormMath.uuid(n, "Encounter", parentId);
        n.setInteger("Slot", slot()); n.setInteger("ParentChunkX", parentChunkX); n.setInteger("ParentChunkZ", parentChunkZ);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound n) {
        parentId = WitherStormMath.uuid(n, "Parent"); dataWatcher.updateObject(21, (byte)Math.max(0, Math.min(9, n.getInteger("Slot"))));
        parentChunkX = n.getInteger("ParentChunkX"); parentChunkZ = n.getInteger("ParentChunkZ");
    }
}
