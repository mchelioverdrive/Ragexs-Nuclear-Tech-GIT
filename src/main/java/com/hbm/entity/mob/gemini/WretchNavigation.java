package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import com.hbm.world.gemini.GardenerNests;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Bounded surface A*, anatomical clearance and accelerated steering. Native movement owns steps/gravity. */
final class WretchNavigation {
    private final EntityWretch boss;
    private final ArrayList<Node> path = new ArrayList<Node>();
    private int cursor, nextPlan, progressTick;
    int failures;
    private double goalX, goalY, goalZ, speed, vx, vz, progressX, progressZ;
    private boolean moving;
    private final long[] avoided = new long[16];
    private final int[] avoidUntil = new int[16];
    private int avoidCursor;
    private static final class Node implements Comparable<Node> {
        final int x, z; final double y;
        double g, h; Node parent; boolean closed;
        Node(int x, double y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int compareTo(Node other) { return Double.compare(g + h, other.g + other.h); }
    }
    WretchNavigation(EntityWretch boss) { this.boss = boss; }
    void goal(double x, double y, double z, double speed) {
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !boss.inTerritory(x, z, boss.territoryRadius())) { halt(); return; }
        if(!moving || square(goalX - x, goalZ - z) > 9) {
            path.clear(); cursor = 0; nextPlan = Math.min(nextPlan, boss.ticksExisted + 5);
            progressTick = boss.ticksExisted + 25; progressX = boss.posX; progressZ = boss.posZ;
        }
        goalX = x; goalY = y; goalZ = z; this.speed = speed; moving = true;
    }
    void halt() {
        moving = false; path.clear(); cursor = 0; vx = vz = 0;
        boss.motionX = boss.motionZ = 0; boss.moveForward = boss.moveStrafing = 0;
    }
    boolean arrived(double distance) { return moving && square(goalX - boss.posX, goalZ - boss.posZ) <= distance * distance; }
    boolean isMoving() { return moving; }
    double obstacleX() { return boss.posX - Math.sin(Math.toRadians(boss.rotationYaw)) * 2.3; }
    double obstacleZ() { return boss.posZ + Math.cos(Math.toRadians(boss.rotationYaw)) * 2.3; }
    void tick() {
        boss.moveForward = boss.moveStrafing = 0;
        if(!moving || arrived(0.8)) { halt(); return; }
        if(boss.ticksExisted >= progressTick) {
            if(square(boss.posX - progressX, boss.posZ - progressZ) < 0.12 && speed > 0) {
                failCurrent();
            } else failures = Math.max(0, failures - 1);
            progressX = boss.posX; progressZ = boss.posZ; progressTick = boss.ticksExisted + 25;
        }
        if((path.isEmpty() || cursor >= path.size()) && boss.ticksExisted >= nextPlan) plan();
        if(cursor >= path.size()) { vx *= 0.5; vz *= 0.5; boss.motionX = vx; boss.motionZ = vz; return; }
        Node node = path.get(cursor);
        if(square(node.x + 0.5 - boss.posX, node.z + 0.5 - boss.posZ) < 0.5) {
            if(++cursor >= path.size()) { vx = vz = 0; return; }
            node = path.get(cursor);
        }
        double dx = node.x + 0.5 - boss.posX, dz = node.z + 0.5 - boss.posZ;
        double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float turn = boss.behavior() == WretchBrain.CHARGING ? 9F : speed > 0.15 ? 16F : 7F;
        boss.rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired - boss.rotationYaw), -turn, turn);
        double alignment = Math.max(0.12, Math.cos(Math.toRadians(MathHelper.wrapAngleTo180_float(desired - boss.rotationYaw))));
        double wantedX = dx / length * speed * alignment, wantedZ = dz / length * speed * alignment;
        double acceleration = speed > 0.3 ? 0.065 : speed > 0.15 ? 0.04 : 0.012;
        vx += MathHelper.clamp_double(wantedX - vx, -acceleration, acceleration);
        vz += MathHelper.clamp_double(wantedZ - vz, -acceleration, acceleration);
        if(!anatomyClear(boss.posX + vx, boss.posY, boss.posZ + vz)
                && !(boss.onGround && anatomyClear(boss.posX + vx, boss.posY + 1, boss.posZ + vz))) {
            vx = vz = 0; failCurrent();
        }
        boss.motionX = vx; boss.motionZ = vz;
    }
    private void failCurrent() {
        if(cursor < path.size()) {
            Node node = path.get(cursor); avoided[avoidCursor] = key(node.x, node.y, node.z); avoidUntil[avoidCursor] = boss.ticksExisted + 200;
            avoidCursor = (avoidCursor + 1) % avoided.length;
        }
        failures = Math.min(12, failures + 1); path.clear(); cursor = 0;
    }
    private boolean avoided(long key) {
        for(int i = 0; i < avoided.length; i++) if(avoided[i] == key && avoidUntil[i] > boss.ticksExisted) return true;
        return false;
    }
    private void plan() {
        nextPlan = boss.ticksExisted + 30 + Math.min(60, failures * 8);
        path.clear(); cursor = 0;
        int sx = MathHelper.floor_double(boss.posX), sz = MathHelper.floor_double(boss.posZ);
        double dx = goalX - boss.posX, dz = goalZ - boss.posZ, range = Math.sqrt(dx * dx + dz * dz);
        double scale = range > 14 ? 14 / range : 1;
        double tx = boss.posX + dx * scale, tz = boss.posZ + dz * scale;
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Map<Long, Node> nodes = new HashMap<Long, Node>();
        Node start = new Node(sx, boss.posY, sz), best = start;
        start.h = Math.sqrt(square(tx - sx - 0.5, tz - sz - 0.5));
        open.add(start); nodes.put(key(sx, boss.posY, sz), start);
        int expanded = 0;
        while(!open.isEmpty() && expanded++ < 112 && nodes.size() < 256) {
            Node current = open.poll(); if(current.closed) continue; current.closed = true;
            if(current.h < best.h) best = current;
            if(current.h < 1.8) { best = current; break; }
            for(int side = 0; side < 8; side++) {
                int ox = side < 4 ? (side == 0 ? 2 : side == 1 ? -2 : 0) : (side < 6 ? 2 : -2);
                int oz = side < 4 ? (side == 2 ? 2 : side == 3 ? -2 : 0) : (side % 2 == 0 ? 2 : -2);
                int x = current.x + ox, z = current.z + oz;
                if(Math.abs(x - sx) > 16 || Math.abs(z - sz) > 16 || !boss.inTerritory(x + 0.5, z + 0.5, boss.territoryRadius())) continue;
                double y = surface(x + 0.5, current.y, z + 0.5);
                if(!Double.isFinite(y) || y - current.y > 1.05 || current.y - y > 1.6) continue;
                long key = key(x, y, z); Node node = nodes.get(key);
                if(avoided(key)) continue;
                if(node != null && node.closed) continue;
                if(node == null) {
                    node = new Node(x, y, z); nodes.put(key, node);
                    if(!canStand(x + 0.5, y, z + 0.5)) { node.closed = true; continue; }
                    // Sample the swept midpoint as well, preventing diagonal corner cutting.
                    if(!canStand((current.x + x) * 0.5 + 0.5, Math.max(current.y, y), (current.z + z) * 0.5 + 0.5)) { node.closed = true; continue; }
                    node.g = Double.POSITIVE_INFINITY;
                }
                double cost = current.g + Math.sqrt(ox * ox + oz * oz) + Math.abs(current.y - node.y) * 2;
                if(cost >= node.g) continue;
                open.remove(node); node.g = cost; node.parent = current;
                node.h = Math.sqrt(square(tx - x - 0.5, tz - z - 0.5)); open.add(node);
            }
        }
        if(best == start || best.h > start.h - 0.5) { failures = Math.min(12, failures + 1); return; }
        for(Node n = best; n.parent != null; n = n.parent) path.add(n);
        Collections.reverse(path);
    }
    boolean canStand(double x, double y, double z) {
        return y >= 1 && y + WretchPose.HEIGHT < 256 && anatomyClear(x, y, z)
                && clear(AxisAlignedBB.getBoundingBox(x - 1.6, y + 0.02, z - 1.6, x + 1.6, y + 0.8, z + 1.6));
    }
    /** Leaves beneath the raised torso do not become an imaginary solid 20-block wall. */
    boolean anatomyClear(double x, double y, double z) {
        if(y < 0 || y + WretchPose.HEIGHT > 256) return false;
        if(!clear(AxisAlignedBB.getBoundingBox(x - 1.6, y + 8.3, z - 1.6, x + 1.6, y + 20, z + 1.6))) return false;
        double angle = Math.toRadians(boss.rotationYaw);
        for(int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -1.15 : 1.15, lx = x + side * Math.cos(angle), lz = z + side * Math.sin(angle);
            if(!clear(AxisAlignedBB.getBoundingBox(lx - 0.28, y + 0.9, lz - 0.28, lx + 0.28, y + 9, lz + 0.28))) return false;
        }
        return true;
    }
    private boolean clear(AxisAlignedBB box) {
        if(!GardenerNests.loaded(boss.worldObj, box) || boss.worldObj.isAnyLiquid(box)) return false;
        for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, box)) {
            AxisAlignedBB collision = (AxisAlignedBB) entry;
            int x = MathHelper.floor_double(collision.minX + 0.001), y = MathHelper.floor_double(collision.minY + 0.001), z = MathHelper.floor_double(collision.minZ + 0.001);
            // Foliage can yield around thin limbs; trunks and actual construction remain solid.
            if(!boss.worldObj.getBlock(x, y, z).isLeaves(boss.worldObj, x, y, z)) return false;
        }
        return true;
    }
    double surface(double x, double y, double z) {
        if(!boss.worldObj.getChunkProvider().chunkExists(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4)) return Double.NaN;
        MovingObjectPosition hit = boss.worldObj.func_147447_a(Vec3.createVectorHelper(x, y + 1.15, z), Vec3.createVectorHelper(x, y - 2.1, z), false, true, false);
        return hit == null || hit.sideHit != 1 ? Double.NaN : hit.hitVec.yCoord;
    }
    private static double square(double x, double z) { return x * x + z * z; }
    private static long key(int x, double y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (Math.round(y * 2) & 0xFFF); }
}
