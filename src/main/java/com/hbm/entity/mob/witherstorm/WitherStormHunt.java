package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Complete local census; the bounded debris/head sample must not bias flight toward sparse chunks. */
final class WitherStormHunt {
    static final double RADIUS = 96D;
    private static final double CLUSTER_RADIUS = 16D;
    final List<EntityLivingBase> living = new ArrayList<>();
    private final Map<Long, List<EntityLivingBase>> cells = new HashMap<>();
    private long nextRefresh;
    private long nextSelection;
    private EntityLivingBase center;
    private EntityPlayer player;

    @SuppressWarnings("unchecked")
    void refresh(EntityWitherStorm boss) {
        long now = boss.worldObj.getTotalWorldTime();
        if(now < nextRefresh) return;
        nextRefresh = now + 20;
        living.clear(); cells.clear(); center = null; player = null; nextSelection = 0;
        List<EntityLivingBase> found = boss.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
            boss.boundingBox.expand(RADIUS, RADIUS, RADIUS));
        for(EntityLivingBase entity : found) {
            if(!boss.validTarget(entity)) continue;
            if(entity instanceof EntityPlayer && (player == null || boss.getDistanceSqToEntity(entity) < boss.getDistanceSqToEntity(player)))
                player = (EntityPlayer)entity;
            if(!WitherStormMath.edible(entity, boss.worldObj)) continue;
            living.add(entity);
            if(entity instanceof EntityPlayer) continue;
            long key = key(cell(entity.posX), cell(entity.posY), cell(entity.posZ));
            List<EntityLivingBase> bucket = cells.get(key);
            if(bucket == null) { bucket = new ArrayList<>(); cells.put(key, bucket); }
            bucket.add(entity);
        }
    }

    Vec3 destination(EntityWitherStorm boss, Vec3 trail) {
        Vec3 priority = boss.validTarget(player) ? Vec3.createVectorHelper(player.posX, player.posY, player.posZ) : trail;
        if(priority != null && boss.getDistanceSq(priority.xCoord, boss.posY, priority.zCoord) < 24D * 24D) return priority;
        if(boss.worldObj.getTotalWorldTime() >= nextSelection) {
            nextSelection = boss.worldObj.getTotalWorldTime() + 20;
            selectCluster(boss, priority);
        }
        Vec3 cluster = clusterPosition(boss);
        return cluster == null ? priority : cluster;
    }

    private void selectCluster(EntityWitherStorm boss, Vec3 priority) {
        center = null;
        int largest = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for(EntityLivingBase entity : living) {
            if(entity instanceof EntityPlayer || !boss.validTarget(entity) || !alongRoute(boss, entity, priority)) continue;
            int count = 0, x = cell(entity.posX), y = cell(entity.posY), z = cell(entity.posZ);
            for(int dx = -1; dx <= 1; dx++) for(int dy = -1; dy <= 1; dy++) for(int dz = -1; dz <= 1; dz++) {
                List<EntityLivingBase> bucket = cells.get(key(x + dx, y + dy, z + dz));
                if(bucket != null) for(EntityLivingBase other : bucket)
                    if(entity.getDistanceSqToEntity(other) <= CLUSTER_RADIUS * CLUSTER_RADIUS) count++;
            }
            double distance = boss.getDistanceSqToEntity(entity);
            if(count > largest || count == largest && distance < nearest) {
                largest = count; nearest = distance; center = entity;
            }
        }
    }

    private Vec3 clusterPosition(EntityWitherStorm boss) {
        if(!boss.validTarget(center) || !WitherStormMath.edible(center, boss.worldObj)) return null;
        double x = 0, y = 0, z = 0;
        int count = 0;
        for(EntityLivingBase entity : living) if(!(entity instanceof EntityPlayer) && boss.validTarget(entity) && WitherStormMath.edible(entity, boss.worldObj)
            && center.getDistanceSqToEntity(entity) <= CLUSTER_RADIUS * CLUSTER_RADIUS) {
            x += entity.posX; y += entity.posY; z += entity.posZ; count++;
        }
        return count == 0 ? null : Vec3.createVectorHelper(x / count, y / count, z / count);
    }

    private static boolean alongRoute(EntityWitherStorm boss, EntityLivingBase entity, Vec3 priority) {
        if(priority == null) return true;
        double dx = priority.xCoord - boss.posX, dz = priority.zCoord - boss.posZ;
        double length = dx * dx + dz * dz;
        double ex = entity.posX - boss.posX, ez = entity.posZ - boss.posZ;
        double progress = length < 1D ? 0D : (ex * dx + ez * dz) / length;
        // Only intercept groups ahead, close to the player's route. Never reverse course or linger below the Storm.
        double lateralX = ex - progress * dx, lateralZ = ez - progress * dz;
        return progress > 0D && progress < 1D && ex * ex + ez * ez > 12D * 12D
            && lateralX * lateralX + lateralZ * lateralZ <= 16D * 16D;
    }

    private static int cell(double value) { return MathHelper.floor_double(value / CLUSTER_RADIUS); }
    private static long key(int x, int y, int z) {
        return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
}
