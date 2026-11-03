package com.hbm.entity.mob.witherstorm;

import java.util.UUID;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
    private long lastFollowTick = Long.MIN_VALUE;
    private int interpolationTicks;
    private double nextX, nextY, nextZ;
    private float nextYaw, nextPitch;
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
        dataWatcher.updateObject(22, boss.getStormScale());
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
            // Interpolation advances once per tick, never from rendering/parent queries.
            if(interpolationTicks > 0) {
                double steps = interpolationTicks--;
                setPosition(posX + (nextX - posX) / steps, posY + (nextY - posY) / steps, posZ + (nextZ - posZ) / steps);
                rotationYaw += MathHelper.wrapAngleTo180_float(nextYaw - rotationYaw) / steps;
                rotationPitch += (nextPitch - rotationPitch) / steps;
            }
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
        long tick = worldObj.getTotalWorldTime();
        if(worldObj.isRemote || lastFollowTick == tick) return;
        lastFollowTick = tick;
        dataWatcher.updateObject(20, boss.getEntityId()); dataWatcher.updateObject(22, boss.getStormScale());
        Vec3 position = boss.partPosition(slot());
        if(!WitherStormMath.loaded(worldObj, position.xCoord, position.zCoord)) return;
        setPosition(position.xCoord, position.yCoord, position.zCoord);
        updateBounds(boss);
        parentChunkX = MathHelper.floor_double(boss.posX) >> 4; parentChunkZ = MathHelper.floor_double(boss.posZ) >> 4;
        if(slot() < 3) {
            Entity target = worldObj.getEntityByID(boss.headTarget(slot()));
            float yaw = boss.rotationYaw, pitch = 0;
            if(WitherStormMath.targetable(target, worldObj)) {
                Vec3 aim = WitherStormMath.targetPoint(target, 1F);
                double dx = aim.xCoord - posX, dz = aim.zCoord - posZ, dy = aim.yCoord - (posY + scale() * 0.6D);
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                yaw = horizontal < 0.001D ? rotationYaw : (float)(Math.atan2(dx, -dz) * 180D / Math.PI);
                pitch = (float)(Math.atan2(dy, horizontal) * 180D / Math.PI);
                if(boss.headTransformed(slot())) {
                    // Aim from the elevated eye, not the jaw pivot. The forward eye offset
                    // cancels; the perpendicular offset requires this angular correction.
                    double eyeHeight = WitherStormMath.EYE_Y * WitherStormMath.HEAD_SCALE * scale() * boss.headSize(slot())
                        * (0.45D + 0.55D * boss.headDevelopment(slot(), 0));
                    double range = Math.sqrt(horizontal * horizontal + dy * dy);
                    pitch -= (float)(Math.asin(Math.min(0.95D, eyeHeight / Math.max(0.01D, range))) * 180D / Math.PI);
                }
            }
            rotationYaw += MathHelper.wrapAngleTo180_float(yaw - rotationYaw) * 0.12F;
            rotationPitch += (pitch - rotationPitch) * 0.12F;
        }
    }
    @SideOnly(Side.CLIENT)
    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int ticks) {
        nextX = x; nextY = y; nextZ = z; nextYaw = yaw; nextPitch = pitch;
        interpolationTicks = Math.max(1, ticks);
    }
    public boolean ready() {
        EntityWitherStorm boss = parent();
        if(boss == null || isDead) return false;
        Vec3 expected = boss.partPosition(slot());
        return WitherStormMath.loaded(worldObj, posX, posZ) && getDistanceSq(expected.xCoord, expected.yCoord, expected.zCoord) < 4D;
    }
    public float headYaw(float partial) { return prevRotationYaw + MathHelper.wrapAngleTo180_float(rotationYaw - prevRotationYaw) * partial; }
    public float headPitch(float partial) { return prevRotationPitch + (rotationPitch - prevRotationPitch) * partial; }
    public Vec3 mouth(float partial) {
        EntityWitherStorm boss = parent();
        double size = boss == null ? scale() : scale() * boss.headSize(slot());
        Vec3 offset = WitherStormMath.headOffset(0, 0, -(boss != null && boss.headTransformed(slot()) ? 1.65D : 0.51D) * size, headYaw(partial), headPitch(partial));
        return Vec3.createVectorHelper(lastTickPosX + (posX - lastTickPosX) * partial + offset.xCoord,
            lastTickPosY + (posY - lastTickPosY) * partial + scale() * 0.6D + offset.yCoord,
            lastTickPosZ + (posZ - lastTickPosZ) * partial + offset.zCoord);
    }
    public Vec3 eye(float partial) {
        EntityWitherStorm boss = parent();
        double size = scale() * WitherStormMath.HEAD_SCALE * (boss == null ? 1F : boss.headSize(slot()));
        Vec3 offset = WitherStormMath.headOffset(0, WitherStormMath.EYE_Y * size, WitherStormMath.EYE_Z * size, headYaw(partial), headPitch(partial));
        return Vec3.createVectorHelper(lastTickPosX + (posX - lastTickPosX) * partial + offset.xCoord,
            lastTickPosY + (posY - lastTickPosY) * partial + scale() * 0.6D + offset.yCoord,
            lastTickPosZ + (posZ - lastTickPosZ) * partial + offset.zCoord);
    }
    private void updateBounds(EntityWitherStorm boss) {
        if(slot() < 3) {
            double x = posX, y = posY, z = posZ;
            float size = boss == null ? 1F : boss.headSize(slot());
            float extent = boss != null && boss.headTransformed(slot()) ? 2.7F : 1F;
            setSize(extent * size * scale(), extent * size * scale()); setPosition(x, y, z);
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
