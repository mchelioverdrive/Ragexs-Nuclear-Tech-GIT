package com.hbm.entity.mob.sandworm;

import java.util.List;
import com.hbm.config.SandwormConfig;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.SandwormPosePacket;
import com.hbm.world.sandworm.WormContent;
import com.hbm.world.sandworm.WormEncounter;
import com.hbm.world.sandworm.WormEncounters;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** One tracked entity; eleven native multipart hitboxes and one server-owned articulated pose. */
public final class EntitySandworm extends Entity implements IEntityMultiPart, IEntityAdditionalSpawnData {
    public static final int SEGMENTS = 11;
    public static final double SPACING = 18;
    public final double[][] pose = new double[SEGMENTS][5];
    public final double[][] previousPose = new double[SEGMENTS][5];
    private final double[][] targetPose = new double[SEGMENTS][5];
    private final EntityDragonPart[] parts = new EntityDragonPart[SEGMENTS];
    private boolean initialized;
    private int lerpTicks;
    private double targetX, targetY, targetZ;
    private double homeX, homeY, homeZ;
    private boolean homeSet, surface;
    public boolean registered;
    public boolean naturalSpawn;
    private int emergenceTick = -1, retreatUntil;
    private int emergenceX, emergenceY, emergenceZ;
    public EntitySandworm(World w) {
        super(w); setSize(16, 16); noClip = true; isImmuneToFire = true; ignoreFrustumCheck = true;
        for(int i = 0; i < SEGMENTS; i++) parts[i] = new EntityDragonPart(this, i == 0 ? "mouth" : i == SEGMENTS - 1 ? "tail" : "body" + i, i == SEGMENTS - 1 ? 10 : 16, i == SEGMENTS - 1 ? 10 : 16);
    }
    @Override protected void entityInit() { }
    @Override public World func_82194_d() { return worldObj; }
    @Override public Entity[] getParts() { return isDead ? null : parts; }
    @Override public boolean attackEntityFromPart(EntityDragonPart part, DamageSource source, float damage) { return false; }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) { return false; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 384 * 384; }
    private void initializePose() {
        double yaw = Math.toRadians(rotationYaw), pitch = Math.toRadians(rotationPitch);
        for(int i = 0; i < SEGMENTS; i++) {
            pose[i][0] = posX + Math.sin(yaw) * Math.cos(pitch) * SPACING * i;
            pose[i][1] = posY + Math.sin(pitch) * SPACING * i;
            pose[i][2] = posZ - Math.cos(yaw) * Math.cos(pitch) * SPACING * i;
            pose[i][3] = rotationYaw; pose[i][4] = rotationPitch;
            System.arraycopy(pose[i], 0, previousPose[i], 0, 5);
        }
        initialized = true;
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if(!initialized) initializePose();
        for(int i = 0; i < SEGMENTS; i++) System.arraycopy(pose[i], 0, previousPose[i], 0, 5);
        if(worldObj.isRemote) {
            if(lerpTicks > 0) {
                for(int i = 0; i < SEGMENTS; i++) for(int j = 0; j < 5; j++) {
                    double difference = targetPose[i][j] - pose[i][j];
                    if(j >= 3) difference = MathHelper.wrapAngleTo180_double(difference);
                    pose[i][j] += difference / lerpTicks;
                }
                lerpTicks--;
            }
            setPosition(pose[0][0], pose[0][1], pose[0][2]); rotationYaw = (float)pose[0][3]; rotationPitch = (float)pose[0][4];
        } else {
            WormEncounters data = WormEncounters.get(worldObj);
            WormEncounter record = data.admit(this);
            if(record == null || record.retired()) {
                if(record != null) { record.exteriorRetired = true; data.markDirty(); }
                setDead(); return;
            }
            boolean newHome = !homeSet;
            if(newHome) {
                homeSet = true; homeX = posX; homeY = posY; homeZ = posZ;
                if(record.natural) { targetX = homeX; targetY = homeY; targetZ = homeZ; }
            }
            if(record.natural) {
                if(newHome || ticksExisted % 20 == 0) chooseNaturalTarget(record, data);
            } else if(newHome || ticksExisted % 20 == 0 || getDistanceSq(targetX, targetY, targetZ) < 100) chooseTarget();
            float desiredYaw = (float)(Math.atan2(targetZ - posZ, targetX - posX) * 180 / Math.PI) - 90;
            double horizontal = Math.hypot(targetX - posX, targetZ - posZ);
            float desiredPitch = (float)(-Math.atan2(targetY - posY, horizontal) * 180 / Math.PI);
            rotationYaw = approach(rotationYaw, desiredYaw, 2);
            rotationPitch = approach(rotationPitch, desiredPitch, 1);
            double yaw = Math.toRadians(rotationYaw), pitch = Math.toRadians(rotationPitch);
            // Slow tight approaches so bounded steering can actually reach a stationary player instead of orbiting forever.
            double speed = SandwormConfig.speed * Math.max(0.15, Math.min(1, Math.sqrt(getDistanceSq(targetX, targetY, targetZ)) / 48));
            motionX = -Math.sin(yaw) * Math.cos(pitch) * speed;
            motionY = -Math.sin(pitch) * speed;
            motionZ = Math.cos(yaw) * Math.cos(pitch) * speed;
            if(loaded(posX + motionX, posZ + motionZ, 10)) setPosition(posX + motionX, Math.max(4, Math.min(230, posY + motionY)), posZ + motionZ);
            else { targetX = homeX; targetY = homeY; targetZ = homeZ; motionX = motionY = motionZ = 0; }
            pose[0][0] = posX; pose[0][1] = posY; pose[0][2] = posZ; pose[0][3] = rotationYaw; pose[0][4] = rotationPitch;
            // Follow the prior child position at a fixed distance, producing a continuous spatial curve.
            for(int i = 1; i < SEGMENTS; i++) {
                double dx = pose[i - 1][0] - pose[i][0], dy = pose[i - 1][1] - pose[i][1], dz = pose[i - 1][2] - pose[i][2];
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if(distance < 0.0001) continue;
                pose[i][0] = pose[i - 1][0] - dx / distance * SPACING;
                pose[i][1] = pose[i - 1][1] - dy / distance * SPACING;
                pose[i][2] = pose[i - 1][2] - dz / distance * SPACING;
                pose[i][3] = Math.atan2(dz, dx) * 180 / Math.PI - 90;
                pose[i][4] = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
            }
            if(ticksExisted % 5 == 0) {
                if(!record.natural || emergenceTick >= 0 && ticksExisted >= emergenceTick) mouth(record, data);
                PacketDispatcher.wrapper.sendToAllAround(new SandwormPosePacket(this), new TargetPoint(dimension, posX, posY, posZ, 384));
            }
        }
        for(int i = 0; i < SEGMENTS; i++) parts[i].setLocationAndAngles(pose[i][0], pose[i][1] - parts[i].height / 2, pose[i][2], (float)pose[i][3], (float)pose[i][4]);
    }
    private void mouth(WormEncounter e, WormEncounters data) {
        double yaw = Math.toRadians(rotationYaw), pitch = Math.toRadians(rotationPitch);
        double x = posX - Math.sin(yaw) * Math.cos(pitch) * 10, y = posY - Math.sin(pitch) * 10, z = posZ + Math.cos(yaw) * Math.cos(pitch) * 10;
        double radius = SandwormConfig.swallowRadius;
        for(Object o : worldObj.playerEntities) {
            if(!(o instanceof EntityPlayerMP)) continue;
            EntityPlayerMP p = (EntityPlayerMP)o;
            if(e.natural && !data.surface.mayPursue(p, data.time())) continue;
            if(p.getDistanceSq(x, y - 1, z) <= radius * radius) data.swallow(p, e);
            else if(p.ticksExisted % 20 == 0) {
                // Native body parts are not independently ticking entities; collision effects stay on the server.
                for(int i = 1; i < SEGMENTS; i++) if(p.getDistanceSq(pose[i][0], pose[i][1], pose[i][2]) < 9 * 9) {
                    if(!p.capabilities.isCreativeMode) {
                        double dx = p.posX - pose[i][0], dz = p.posZ - pose[i][2], length = Math.max(1, Math.hypot(dx, dz));
                        p.addVelocity(dx / length, 1, dz / length); p.velocityChanged = true;
                    }
                    break;
                }
            }
        }
        List<EntityItem> bait = worldObj.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
        for(EntityItem item : bait) if(item.getEntityItem() != null && item.getEntityItem().getItem() == WormContent.bait) item.setDead();
    }
    private EntityItem nearestBait(double range) {
        List<EntityItem> bait = worldObj.getEntitiesWithinAABB(EntityItem.class, boundingBox.expand(range, range, range));
        EntityItem nearest = null; double best = range * range;
        for(EntityItem item : bait) if(item.getEntityItem() != null && item.getEntityItem().getItem() == WormContent.bait && getDistanceSqToEntity(item) < best) { best = getDistanceSqToEntity(item); nearest = item; }
        return nearest;
    }

    private void chooseNaturalTarget(WormEncounter record, WormEncounters data) {
        if(emergenceTick >= 0) {
            if(ticksExisted >= emergenceTick + 120 || !loaded(emergenceX, emergenceZ, 2)) {
                emergenceTick = -1;
                retreatUntil = ticksExisted + 120;
            } else {
                targetX = emergenceX + 0.5;
                targetZ = emergenceZ + 0.5;
                targetY = emergenceY + (ticksExisted < emergenceTick ? -10 : ticksExisted - emergenceTick < 80 ? 4 : -15);
                if(ticksExisted <= emergenceTick) {
                    Block sand = worldObj.getBlock(emergenceX, emergenceY - 1, emergenceZ);
                    ((WorldServer)worldObj).func_147487_a("blockcrack_" + Block.getIdFromBlock(sand) + "_0",
                            targetX, emergenceY + 0.15, targetZ, 18, 2.2, 0.12, 2.2, 0.15);
                    if(ticksExisted == emergenceTick - 60 || ticksExisted == emergenceTick)
                        worldObj.playSoundEffect(targetX, emergenceY, targetZ, "step.sand", 3F, 0.5F);
                }
                return;
            }
        }

        double range = SandwormConfig.targetRange;
        Entity nearest = nearestBait(range);
        if(nearest == null && ticksExisted >= retreatUntil) {
            double best = range * range;
            for(Object entity : worldObj.playerEntities) {
                if(!(entity instanceof EntityPlayerMP)) continue;
                EntityPlayerMP player = (EntityPlayerMP)entity;
                if(!data.surface.mayPursue(player, data.time()) || !data.surface.isOnSand(player)) continue;
                double dx = player.posX - posX, dz = player.posZ - posZ;
                double score = (dx * dx + dz * dz) * (SandwormConfig.surface.movementDetection && data.surface.vibrates(player) ? 0.55 : 1);
                if(score < best) { best = score; nearest = player; }
            }
        }
        if(nearest != null && loaded(nearest.posX, nearest.posZ, 2)) {
            int x = MathHelper.floor_double(nearest.posX), z = MathHelper.floor_double(nearest.posZ);
            int ground = worldObj.getHeightValue(x, z);
            if(ground > 8 && Math.abs(nearest.posY - ground) <= 2.5
                    && worldObj.getBlock(x, ground - 1, z).getMaterial() == Material.sand) {
                targetX = nearest.posX;
                targetZ = nearest.posZ;
                targetY = Math.max(8, ground - 20);
                if(record.ready() && ticksExisted >= retreatUntil && Math.hypot(posX - targetX, posZ - targetZ) < 22) {
                    emergenceX = x;
                    emergenceY = ground;
                    emergenceZ = z;
                    emergenceTick = (ticksExisted / 20 + 4) * 20;
                }
                return;
            }
        }

        if(getDistanceSq(targetX, targetY, targetZ) < 100 || targetY > posY - 8 && ticksExisted < retreatUntil) {
            for(int attempt = 0; attempt < 4; attempt++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double x = homeX + Math.sin(angle) * 48, z = homeZ + Math.cos(angle) * 48;
                if(!loaded(x, z, 10)) continue;
                targetX = x;
                targetZ = z;
                targetY = Math.max(8, worldObj.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z)) - 30);
                return;
            }
        }
    }

    private void chooseTarget() {
        double range = SandwormConfig.targetRange;
        Entity nearest = nearestBait(range);
        double best = range * range;
        if(nearest == null) for(Object o : worldObj.playerEntities) {
            EntityPlayerMP p = (EntityPlayerMP)o;
            if(p.isEntityAlive() && (SandwormConfig.swallowCreative || !p.capabilities.isCreativeMode) && getDistanceSqToEntity(p) < best) { best = getDistanceSqToEntity(p); nearest = p; }
        }
        if(nearest != null) { targetX = nearest.posX; targetY = nearest.posY + 1; targetZ = nearest.posZ; return; }
        if(getDistanceSq(targetX, targetY, targetZ) >= 100 && ticksExisted > 1) return;
        surface = !surface;
        for(int attempt = 0; attempt < 8; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2, distance = 60 + rand.nextDouble() * 80;
            double x = homeX + Math.sin(angle) * distance, z = homeZ + Math.cos(angle) * distance;
            if(!loaded(x, z, 10)) continue;
            targetX = x; targetZ = z;
            int ground = worldObj.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z));
            targetY = surface ? Math.min(210, ground + 25) : Math.max(8, ground - 48); return;
        }
        targetX = homeX; targetY = Math.max(8, homeY - (surface ? 0 : 40)); targetZ = homeZ;
    }
    private boolean loaded(double x, double z, int margin) {
        return worldObj.checkChunksExist(MathHelper.floor_double(x) - margin, 0, MathHelper.floor_double(z) - margin, MathHelper.floor_double(x) + margin, 255, MathHelper.floor_double(z) + margin);
    }
    private static float approach(float from, float to, float max) { return from + MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(to - from), -max, max); }
    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int ticks) {
        if(!initialized) { setLocationAndAngles(x, y, z, yaw, pitch); initializePose(); }
        // Once spawned, snapshots own client pose, including the root; vanilla's byte pitch is insufficient for dives.
    }
    public void writePose(ByteBuf b) {
        if(!initialized) initializePose();
        b.writeDouble(pose[0][0]); b.writeDouble(pose[0][1]); b.writeDouble(pose[0][2]);
        for(int i = 0; i < SEGMENTS; i++) {
            for(int j = 0; j < 3; j++) b.writeFloat((float)(pose[i][j] - pose[0][j]));
            b.writeFloat((float)pose[i][3]); b.writeFloat((float)pose[i][4]);
        }
    }
    public void acceptPose(double[][] incoming, boolean immediate) {
        for(int i = 0; i < SEGMENTS; i++) {
            System.arraycopy(incoming[i], 0, targetPose[i], 0, 5);
            if(immediate || !initialized) {
                System.arraycopy(incoming[i], 0, pose[i], 0, 5); System.arraycopy(incoming[i], 0, previousPose[i], 0, 5);
            }
        }
        initialized = true; lerpTicks = immediate ? 0 : 5;
        if(immediate) setPosition(pose[0][0], pose[0][1], pose[0][2]);
    }
    public static double[][] readPose(ByteBuf b) {
        double[][] result = new double[SEGMENTS][5]; double[] origin = {b.readDouble(), b.readDouble(), b.readDouble()};
        for(int i = 0; i < SEGMENTS; i++) for(int j = 0; j < 5; j++) {
            double value = b.readFloat();
            if(!Double.isFinite(value) || (j < 3 && Math.abs(value) > 256)) throw new IllegalArgumentException("Invalid Sandworm pose");
            result[i][j] = j < 3 ? origin[j] + value : value;
        }
        return result;
    }
    @Override public void writeSpawnData(ByteBuf b) { writePose(b); }
    @Override public void readSpawnData(ByteBuf b) { acceptPose(readPose(b), true); }
    @Override protected void writeEntityToNBT(NBTTagCompound n) {
        n.setBoolean("Registered", registered);
        n.setBoolean("HomeSet", homeSet); n.setDouble("HomeX", homeX); n.setDouble("HomeY", homeY); n.setDouble("HomeZ", homeZ);
        n.setDouble("TargetX", targetX); n.setDouble("TargetY", targetY); n.setDouble("TargetZ", targetZ); n.setBoolean("Surface", surface);
        if(!initialized) initializePose();
        NBTTagList list = new NBTTagList();
        for(int i = 0; i < SEGMENTS; i++) { NBTTagCompound p = new NBTTagCompound(); for(int j = 0; j < 5; j++) p.setDouble("v" + j, pose[i][j]); list.appendTag(p); }
        n.setTag("Pose", list);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound n) {
        registered = n.getBoolean("Registered") || n.getBoolean("HomeSet");
        homeSet = n.getBoolean("HomeSet"); homeX = n.getDouble("HomeX"); homeY = n.getDouble("HomeY"); homeZ = n.getDouble("HomeZ");
        targetX = n.getDouble("TargetX"); targetY = n.getDouble("TargetY"); targetZ = n.getDouble("TargetZ"); surface = n.getBoolean("Surface");
        NBTTagList list = n.getTagList("Pose", 10);
        if(list.tagCount() == SEGMENTS) {
            for(int i = 0; i < SEGMENTS; i++) for(int j = 0; j < 5; j++) pose[i][j] = list.getCompoundTagAt(i).getDouble("v" + j);
            initialized = true;
        }
    }
    @Override public void setDead() { super.setDead(); for(EntityDragonPart part : parts) if(part != null) part.setDead(); }
}
