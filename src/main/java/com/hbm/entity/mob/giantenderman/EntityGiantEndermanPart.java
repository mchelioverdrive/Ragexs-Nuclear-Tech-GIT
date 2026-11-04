package com.hbm.entity.mob.giantenderman;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class EntityGiantEndermanPart extends Entity {
    private EntityGiantEnderman owner;

    public EntityGiantEndermanPart(World world) {
        super(world);
        noClip = true;
        ignoreFrustumCheck = true;
    }

    @Override protected void entityInit() {
        dataWatcher.addObject(20, -1);
        dataWatcher.addObject(21, (byte)0);
    }

    void attach(EntityGiantEnderman boss, int region) {
        owner = boss;
        dataWatcher.updateObject(20, boss.getEntityId());
        dataWatcher.updateObject(21, (byte)region);
        follow();
    }

    public EntityGiantEnderman owner() {
        if (owner == null || owner.isDead) {
            Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
            owner = entity instanceof EntityGiantEnderman ? (EntityGiantEnderman)entity : null;
        }
        return owner;
    }

    void follow() {
        EntityGiantEnderman boss = owner();
        if (boss == null) return;
        AxisAlignedBB box = boss.partBox(dataWatcher.getWatchableObjectByte(21));
        setPosition((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5);
        boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Override public void onUpdate() {
        super.onUpdate();
        EntityGiantEnderman boss = owner();
        if (boss != null && boss.isEntityAlive() && (worldObj.isRemote || boss.addedToChunk)) follow();
        else if (!worldObj.isRemote || ticksExisted > 100) setDead();
    }

    @Override public boolean canBeCollidedWith() { return owner() != null && owner().partsVulnerable(); }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        EntityGiantEnderman boss = owner();
        return boss != null && boss.hitPart(dataWatcher.getWatchableObjectByte(21), source, amount);
    }
    @Override public boolean isEntityEqual(Entity entity) { return entity == this || entity == owner(); }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) { setDead(); }
}
