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
    private static final double STORM_MOUTH_Y = 5D / 16D, STORM_MOUTH_Z = -15D / 16D;
    private static final double WITHER_MOUTH_Y = -2D / 16D, WITHER_MOUTH_Z = -0.5D;
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
        dataWatcher.addObject(23, Byte.valueOf((byte)0));
    }
    public int slot() { return dataWatcher.getWatchableObjectByte(21); }
    public float scale() { return dataWatcher.getWatchableObjectFloat(22); }
    public boolean skullWindingUp() { return dataWatcher.getWatchableObjectByte(23) != 0; }
    void setSkullWindingUp(boolean windingUp) { dataWatcher.updateObject(23, (byte)(windingUp ? 1 : 0)); }
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
            EntityWitherStorm boss = parent();
            if(boss != null && (slot() < 3 || boss.getStage().hasPosedTentacles())) {
                // Match the client anatomy while head rotation still follows the tracked part.
                Vec3 position = boss.partPosition(slot());
                setPosition(position.xCoord, position.yCoord, position.zCoord);
            }
            updateBounds(boss);
            return;
        }
        if(slot() < 0 || slot() > 9 || parentId == null) { setDead(); return; }
        EntityWitherStorm boss = parent();
        if(boss != null && boss.isEntityAlive()) {
            if(!boss.isPartActive(slot())) boss.retirePart(this);
            else if(slot() >= 3) followParent(); // The root moves and aims heads together before its attack update.
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
        parentChunkX = MathHelper.floor_double(boss.posX) >> 4; parentChunkZ = MathHelper.floor_double(boss.posZ) >> 4;
        if(slot() < 3) {
            Entity target = worldObj.getEntityByID(boss.headTarget(slot()));
            Vec3 shotAim = boss.windingUpSkullTarget(slot()) == target && WitherStormMath.targetable(target, worldObj)
                ? skullAim(target, boss) : null;
            float yaw = boss.rotationYaw, pitch = boss.getStage().hasShallowHeads() ? WitherStormDestroyerPose.REST_PITCH : 0F;
            if(shotAim != null) {
                yaw = (float)shotAim.xCoord;
                pitch = (float)shotAim.yCoord;
            } else if(WitherStormMath.targetable(target, worldObj)) {
                Vec3 aim = WitherStormMath.targetPoint(target, 1F);
                double dx = aim.xCoord - posX, dz = aim.zCoord - posZ, dy = aim.yCoord - (posY + headPivotHeight(boss));
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                yaw = horizontal < 0.001D ? rotationYaw : (float)(Math.atan2(dx, -dz) * 180D / Math.PI);
                pitch = (float)(Math.atan2(dy, horizontal) * 180D / Math.PI);
                if(boss.headTransformed(slot())) {
                    // Aim from the elevated eye, not the jaw pivot. The forward eye offset
                    // cancels; the perpendicular offset requires this angular correction.
                    double eyeHeight = boss.getStage().eyeY() * boss.headModelScale(slot(), 0F, scale());
                    double range = Math.sqrt(horizontal * horizontal + dy * dy);
                    pitch -= (float)(Math.asin(Math.min(0.95D, eyeHeight / Math.max(0.01D, range))) * 180D / Math.PI);
                }
            }
            float turn = shotAim == null ? 0.12F : 0.65F;
            if(isCommandHead(boss)) {
                rotationYaw += MathHelper.wrapAngleTo180_float(yaw - rotationYaw) * turn;
                rotationPitch += (pitch - rotationPitch) * turn;
            } else {
                // Smooth within the shoulder's travel, including when the body turns under the head.
                float currentYaw = limitStormYaw(boss.rotationYaw, rotationYaw);
                rotationYaw = currentYaw + (limitStormYaw(boss.rotationYaw, yaw) - currentYaw) * turn;
                float currentPitch = limitStormPitch(rotationPitch);
                rotationPitch = currentPitch + (limitStormPitch(pitch) - currentPitch) * turn;
            }
        }
        updateBounds(boss);
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
    private static float limitStormYaw(float bodyYaw, float yaw) {
        return bodyYaw + MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw - bodyYaw), -48F, 48F);
    }
    private static float limitStormPitch(float pitch) { return MathHelper.clamp_float(pitch, -72F, 10F); }

    public float headYaw(float partial, EntityWitherStorm boss) {
        float yaw = prevRotationYaw + MathHelper.wrapAngleTo180_float(rotationYaw - prevRotationYaw) * partial;
        return boss == null || isCommandHead(boss) ? yaw : limitStormYaw(boss.bodyYaw(partial), yaw);
    }
    public float headPitch(float partial, EntityWitherStorm boss) {
        float pitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * partial;
        return boss == null || isCommandHead(boss) ? pitch : limitStormPitch(pitch);
    }
    private boolean isCommandHead(EntityWitherStorm boss) {
        return slot() < 3 && boss != null && boss.getPhase() == WitherStormPhase.COMMAND_BLOCK;
    }
    private double headPivotHeight(EntityWitherStorm boss) {
        return isCommandHead(boss) ? 0.5D * EntityWitherStorm.commandHeadSize(slot()) * boss.getStormScale() : 0.6D * scale();
    }

    private double skullMouthHeight(EntityWitherStorm boss) {
        if(boss.getStage().hasShallowHeads()) return WitherStormDestroyerPose.MOUTH_Y;
        return !isCommandHead(boss) && boss.headTransformed(slot()) ? STORM_MOUTH_Y : WITHER_MOUTH_Y;
    }

    private double skullMouthDepth(EntityWitherStorm boss) {
        if(boss.getStage().hasShallowHeads()) return WitherStormDestroyerPose.MOUTH_Z;
        return !isCommandHead(boss) && boss.headTransformed(slot()) ? STORM_MOUTH_Z : WITHER_MOUTH_Z;
    }

    private double skullMouthScale(EntityWitherStorm boss) {
        double size = boss.headModelScale(slot(), 0F, boss.getStormScale());
        // The ordinary side skull's mesh is three quarters the size of the center mesh.
        return isCommandHead(boss) ? size * EntityWitherStorm.commandHeadSize(slot())
            : boss.headTransformed(slot()) ? size : size * EntityWitherStorm.commandHeadSize(0);
    }

    Vec3 skullMouth(EntityWitherStorm boss) {
        double size = skullMouthScale(boss);
        return headPoint(1F, boss, skullMouthHeight(boss) * size, skullMouthDepth(boss) * size);
    }

    Vec3 skullAim(Entity target, EntityWitherStorm boss) {
        Vec3 point = WitherStormMath.targetPoint(target, 1F);
        double dx = point.xCoord - posX, dz = point.zCoord - posZ, dy = point.yCoord - posY - headPivotHeight(boss);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(horizontal * horizontal + dy * dy);
        double mouthHeight = skullMouthHeight(boss) * skullMouthScale(boss);
        if(horizontal < 0.001D || distance <= Math.abs(mouthHeight)) return null;
        float yaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
        // Solve for a forward ray through the mouth, which sits above or below the pivot.
        float pitch = (float)Math.toDegrees(Math.atan2(dy, horizontal) - Math.asin(mouthHeight / distance));
        float relativeYaw = MathHelper.wrapAngleTo180_float(yaw - boss.rotationYaw);
        if(isCommandHead(boss) ? Math.abs(relativeYaw) > 70F || Math.abs(pitch) >= 85F
            : Math.abs(relativeYaw) > 48F || pitch != limitStormPitch(pitch)) return null;
        return Vec3.createVectorHelper(yaw, pitch, 0);
    }

    boolean skullPathClearsBody(EntityWitherStorm boss, Vec3 mouth, Vec3 spawn, Vec3 direction) {
        Vec3 localSpawn = bossLocal(boss, spawn);
        Vec3 end = bossLocal(boss, spawn.addVector(direction.xCoord, direction.yCoord, direction.zCoord));
        if(localSpawn.zCoord >= 0D || end.zCoord >= localSpawn.zCoord) return false;
        if(boss.getStage().hasTentacles()) return true; // Evolved heads sit ahead of the mantle and aim outward.

        // The compact skeleton's center skull can look down through its own chest.
        // Test in body space because the root's world AABB also encloses empty space.
        Vec3 start = bossLocal(boss, mouth);
        AxisAlignedBB chest = AxisAlignedBB.getBoundingBox(-0.57D, 0, -1.04D, 0.57D, 1.76D, 0.33D);
        AxisAlignedBB shoulders = AxisAlignedBB.getBoundingBox(-1.23D, 1.10D, -0.16D, 1.23D, 1.76D, 0.36D);
        return !chest.isVecInside(start) && chest.calculateIntercept(start, end) == null
            && !shoulders.isVecInside(start) && shoulders.calculateIntercept(start, end) == null;
    }

    private Vec3 bossLocal(EntityWitherStorm boss, Vec3 point) {
        float scale = boss.getStormScale();
        return WitherStormMath.headOffset((point.xCoord - boss.posX) / scale, (point.yCoord - boss.posY) / scale,
            (point.zCoord - boss.posZ) / scale, -boss.rotationYaw, 0F);
    }

    public Vec3 mouth(float partial) {
        EntityWitherStorm boss = parent();
        if(boss != null && boss.getStage().hasShallowHeads()) {
            double size = skullMouthScale(boss);
            return headPoint(partial, boss, skullMouthHeight(boss) * size, skullMouthDepth(boss) * size);
        }
        boolean commandHead = isCommandHead(boss);
        double size = commandHead ? EntityWitherStorm.commandHeadSize(slot()) * boss.getStormScale()
            : boss == null ? scale() : scale() * boss.headSize(slot());
        Vec3 offset = WitherStormMath.headOffset(0, 0, -(boss != null && !commandHead && boss.headTransformed(slot()) ? 1.65D : 0.51D) * size,
            headYaw(partial, boss), headPitch(partial, boss));
        return Vec3.createVectorHelper(lastTickPosX + (posX - lastTickPosX) * partial + offset.xCoord,
            lastTickPosY + (posY - lastTickPosY) * partial + headPivotHeight(boss) + offset.yCoord,
            lastTickPosZ + (posZ - lastTickPosZ) * partial + offset.zCoord);
    }
    public Vec3 eye(float partial) {
        EntityWitherStorm boss = parent();
        double size = boss == null ? scale() * WitherStormMath.HEAD_SCALE : boss.headModelScale(slot(), 0F, scale());
        double y = boss == null ? WitherStormMath.EYE_Y : boss.getStage().eyeY();
        double z = boss == null ? WitherStormMath.EYE_Z : boss.getStage().eyeZ();
        return headPoint(partial, boss, y * size, z * size);
    }
    private Vec3 headPoint(float partial, EntityWitherStorm boss, double y, double z) {
        Vec3 offset = WitherStormMath.headOffset(0, y, z, headYaw(partial, boss), headPitch(partial, boss));
        return Vec3.createVectorHelper(lastTickPosX + (posX - lastTickPosX) * partial + offset.xCoord,
            lastTickPosY + (posY - lastTickPosY) * partial + scale() * 0.6D + offset.yCoord,
            lastTickPosZ + (posZ - lastTickPosZ) * partial + offset.zCoord);
    }
    private void updateBounds(EntityWitherStorm boss) {
        if(isCommandHead(boss)) {
            double x = posX, y = posY, z = posZ;
            double half = headPivotHeight(boss);
            float size = (float)(2D * half);
            if(width != size || height != size) {
                setSize(size, size);
                setPosition(x, y, z);
            }
            // Enclose the pitched and yawed cube about its center, not the base of the entity.
            double yaw = Math.toRadians(rotationYaw), pitch = Math.toRadians(rotationPitch);
            double cosYaw = Math.abs(Math.cos(yaw)), sinYaw = Math.abs(Math.sin(yaw));
            double pitchExtent = Math.abs(Math.cos(pitch)) + Math.abs(Math.sin(pitch));
            double dx = half * (cosYaw + sinYaw * pitchExtent);
            double dy = half * pitchExtent;
            double dz = half * (sinYaw + cosYaw * pitchExtent);
            boundingBox.setBounds(x - dx, y + half - dy, z - dz, x + dx, y + half + dy, z + dz);
        } else if(slot() < 3 && boss != null && boss.getStage().hasShallowHeads()) {
            updateShallowHeadBounds(boss);
        } else if(slot() < 3) {
            double x = posX, y = posY, z = posZ;
            float size = boss == null ? 1F : boss.headSize(slot());
            float extent = boss != null && boss.headTransformed(slot()) ? 2.7F : 1F;
            float height = boss != null && boss.getStage() == WitherStormStage.A_TRANSFORMED && slot() == 1
                ? 2.05F * scale() : extent * size * scale();
            setSize(extent * size * scale(), height); setPosition(x, y, z);
        } else if(boss != null) {
            boolean posed = boss.getStage().hasPosedTentacles();
            // Use the striking end of the curved arm for contact.
            Vec3 root = posed ? boss.posedArmPosition(slot(), 0.82D) : boss.tentacleRoot(slot());
            double padding = (boss.getStage() == WitherStormStage.B ? 0.45D : slot() >= 8 ? 1.4D : 0.7D) * scale();
            boundingBox.setBounds(Math.min(posX, root.xCoord) - padding, Math.min(posY, root.yCoord) - padding, Math.min(posZ, root.zCoord) - padding,
                Math.max(posX, root.xCoord) + padding, Math.max(posY, root.yCoord) + padding, Math.max(posZ, root.zCoord) + padding);
        }
    }
    private void updateShallowHeadBounds(EntityWitherStorm boss) {
        double size = boss.headModelScale(slot(), 0F, boss.getStormScale());
        float yaw = headYaw(1F, boss), pitch = headPitch(1F, boss);
        // Enclose the shallow crown and the hinged jaw through its full windup.
        double minY = -1.20D, maxY = 0.875D, minZ = -1.09D, maxZ = 0.69D;
        Vec3 center = WitherStormMath.headOffset(0D, (minY + maxY) * 0.5D * size, (minZ + maxZ) * 0.5D * size, yaw, pitch);
        double ax = 13D / 16D * size, ay = (maxY - minY) * 0.5D * size, az = (maxZ - minZ) * 0.5D * size;
        double s = Math.abs(Math.sin(Math.toRadians(pitch))), c = Math.abs(Math.cos(Math.toRadians(pitch)));
        double sy = Math.abs(Math.sin(Math.toRadians(yaw))), cy = Math.abs(Math.cos(Math.toRadians(yaw)));
        double rz = az * c + ay * s;
        double dx = ax * cy + rz * sy, dy = ay * c + az * s, dz = ax * sy + rz * cy;
        double x = posX + center.xCoord, y = posY + headPivotHeight(boss) + center.yCoord, z = posZ + center.zCoord;
        boundingBox.setBounds(x - dx, y - dy, z - dz, x + dx, y + dy, z + dz);
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
