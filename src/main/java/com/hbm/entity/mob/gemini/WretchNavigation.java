package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import com.hbm.world.gemini.GardenerNests;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Bounded surface A*, anatomical clearance and accelerated steering. Native movement owns steps/gravity. */
final class WretchNavigation {
    private final EntityWretch boss;
    private final ArrayList<Node> path = new ArrayList<Node>();
    private int cursor, nextPlan, progressTick, collisions, nextDiagnostic;
    int failures;
    private double goalX, goalY, goalZ, speed, vx, vz, progressX, progressY, progressZ, displacement, steerX, steerZ;
    private String blockedBy = "navigation";
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
            if(!moving) {
                if(progressTick == 0) {
                    progressX = boss.posX; progressY = boss.posY; progressZ = boss.posZ; progressTick = boss.ticksExisted + 40;
                }
                steerX = x - boss.posX; steerZ = z - boss.posZ;
            }
        }
        goalX = x; goalY = y; goalZ = z; this.speed = speed; moving = true;
    }
    void halt() {
        moving = false; path.clear(); cursor = 0; vx = vz = 0;
        boss.motionX = boss.motionZ = 0; boss.moveForward = boss.moveStrafing = 0;
    }
    boolean arrived(double distance) { return moving && square(goalX - boss.posX, goalZ - boss.posZ) <= distance * distance; }
    boolean isMoving() { return moving; }
    void obstructionRemoved() {
        path.clear(); cursor = 0; nextPlan = boss.ticksExisted + 1;
        collisions = 0;
        // A changed corridor invalidates both the route and its temporary avoidance entries.
        java.util.Arrays.fill(avoidUntil, 0);
    }
    void collision(boolean anatomy, double requestedX, double requestedZ, double actualX, double actualZ) {
        if(!moving || square(requestedX, requestedZ) < 0.0001) return;
        if(anatomy || square(actualX, actualZ) < square(requestedX, requestedZ) * 0.1) {
            blockedBy = anatomy ? "anatomical collision" : "native feet collision";
            collisions++;
        }
    }
    Vec3 obstruction() {
        double dx = steerX, dz = steerZ;
        if(dx * dx + dz * dz < 0.001) { dx = goalX - boss.posX; dz = goalZ - boss.posZ; }
        return boss.brain.building.movementObstruction(corridor(dx, dz));
    }
    /** Same torso, leg columns and native foot envelope that constrain moveEntity. */
    AxisAlignedBB[] corridor(double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        double scale = length > 0.001 ? Math.min(1.25, length) / length : 0;
        dx *= scale; dz *= scale;
        double x = boss.posX, y = boss.posY, z = boss.posZ, angle = Math.toRadians(boss.rotationYaw);
        AxisAlignedBB[] boxes = new AxisAlignedBB[4];
        boxes[0] = AxisAlignedBB.getBoundingBox(x - 1.6, y - 0.25, z - 1.6, x + 1.6, y + 0.8, z + 1.6).addCoord(dx, 0, dz);
        boxes[1] = AxisAlignedBB.getBoundingBox(x - 1.6, y + 8.05, z - 1.6, x + 1.6, y + 20, z + 1.6).addCoord(dx, 0, dz);
        for(int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -1.15 : 1.15, lx = x + side * Math.cos(angle), lz = z + side * Math.sin(angle);
            boxes[leg + 2] = AxisAlignedBB.getBoundingBox(lx - 0.28, y + 0.55, lz - 0.28, lx + 0.28, y + 9, lz + 0.28).addCoord(dx, 0, dz);
        }
        return boxes;
    }
    double obstacleX() { return boss.posX - Math.sin(Math.toRadians(boss.rotationYaw)) * 2.3; }
    double obstacleZ() { return boss.posZ + Math.cos(Math.toRadians(boss.rotationYaw)) * 2.3; }
    void tick() {
        boss.moveForward = boss.moveStrafing = 0;
        if(!moving || arrived(0.8)) { halt(); return; }
        // Sample resumed movement after anchored attacks too. Halts/goals must not erase
        // the displacement history: breach/recovery transitions used to restart it indefinitely.
        if((boss.state() == EntityWretch.IDLE || boss.state() == EntityWretch.SWIPE) && boss.ticksExisted >= progressTick) {
            displacement = Math.sqrt(square(boss.posX - progressX, boss.posZ - progressZ) + (boss.posY - progressY) * (boss.posY - progressY));
            if(displacement < 0.5 && speed > 0) {
                if(boss.ticksExisted >= nextDiagnostic && com.hbm.world.gemini.GeminiInvasionManager.isDebug(boss.worldObj)) {
                    nextDiagnostic = boss.ticksExisted + 200;
                    // Observe physical blockers even when griefing/protection disables breaches.
                    obstruction();
                    net.minecraft.entity.EntityLivingBase target = boss.getAttackTarget();
                    com.hbm.world.gemini.GeminiInvasionManager.debug(boss.worldObj, "wretch-movement",
                            "Wretch at " + boss.posX + "," + boss.posY + "," + boss.posZ
                            + " target=" + (target == null ? goalX + "," + goalY + "," + goalZ : target.posX + "," + target.posY + "," + target.posZ)
                            + " state=" + boss.behavior() + "/" + boss.state() + " navigation=" + (cursor < path.size() ? "following" : "waiting for route")
                            + " path=" + cursor + "/" + path.size()
                            + " failures=" + failures + " displacement=" + displacement + " preventedBy=" + blockedBy
                            + " obstruction=" + boss.brain.building.lastObstruction() + " rejection=" + boss.brain.building.lastRejection());
                }
                failCurrent();
            } else { failures = 0; collisions = 0; java.util.Arrays.fill(avoidUntil, 0); boss.brain.movementProgress(); }
            progressX = boss.posX; progressY = boss.posY; progressZ = boss.posZ; progressTick = boss.ticksExisted + 40;
        }
        if((path.isEmpty() || cursor >= path.size()) && boss.ticksExisted >= nextPlan) plan();
        if(cursor >= path.size()) {
            steerX = goalX - boss.posX; steerZ = goalZ - boss.posZ;
            blockedBy = collisions > 0 ? blockedBy : "navigation (no route)";
            vx = vz = 0; boss.motionX = boss.motionZ = 0; return;
        }
        Node node = path.get(cursor);
        if(square(node.x + 0.5 - boss.posX, node.z + 0.5 - boss.posZ) < 0.5) {
            if(++cursor >= path.size()) { vx = vz = 0; return; }
            node = path.get(cursor);
        }
        double dx = node.x + 0.5 - boss.posX, dz = node.z + 0.5 - boss.posZ;
        steerX = dx; steerZ = dz;
        double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float turn = boss.behavior() == WretchBrain.CHARGING ? 9F : speed > 0.15 ? 16F : 7F;
        boss.rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired - boss.rotationYaw), -turn, turn);
        double alignment = Math.max(0.12, Math.cos(Math.toRadians(MathHelper.wrapAngleTo180_float(desired - boss.rotationYaw))));
        double wantedX = dx / length * speed * alignment, wantedZ = dz / length * speed * alignment;
        double acceleration = speed > 0.3 ? 0.065 : speed > 0.15 ? 0.04 : 0.012;
        vx += MathHelper.clamp_double(wantedX - vx, -acceleration, acceleration);
        vz += MathHelper.clamp_double(wantedZ - vz, -acceleration, acceleration);
        if(!movementClear(boss.posX + vx, boss.posY, boss.posZ + vz)) {
            vx = vz = 0; collisions++; blockedBy = "anatomical collision"; failCurrent();
        } else {
            if(boss.isCollidedHorizontally) { collisions++; if(collisions >= 3) failCurrent(); }
            else collisions = Math.max(0, collisions - 1);
        }
        boss.motionX = vx; boss.motionZ = vz;
    }
    private void failCurrent() {
        if(cursor < path.size()) {
            Node node = path.get(cursor); avoided[avoidCursor] = key(node.x, node.y, node.z); avoidUntil[avoidCursor] = boss.ticksExisted + 200;
            avoidCursor = (avoidCursor + 1) % avoided.length;
        }
        failures = Math.min(12, failures + 1); path.clear(); cursor = 0;
        nextPlan = Math.min(nextPlan, boss.ticksExisted + 8);
    }
    private boolean avoided(long key) {
        for(int i = 0; i < avoided.length; i++) if(avoided[i] == key && avoidUntil[i] > boss.ticksExisted) return true;
        return false;
    }
    private void plan() {
        nextPlan = boss.ticksExisted + 20 + Math.min(20, failures * 4);
        path.clear(); cursor = 0;
        int sx = MathHelper.floor_double(boss.posX), sz = MathHelper.floor_double(boss.posZ);
        double dx = goalX - boss.posX, dz = goalZ - boss.posZ, range = Math.sqrt(dx * dx + dz * dz);
        double scale = range > 14 ? 14 / range : 1;
        double tx = boss.posX + dx * scale, tz = boss.posZ + dz * scale;
        // Prefer a fully sampled direct segment before the bounded surface search.
        double directY = boss.posY;
        boolean direct = true;
        int samples = Math.max(1, (int) Math.ceil(Math.min(range, 14) / 0.75));
        for(int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double px = Math.floor(boss.posX + (tx - boss.posX) * t) + 0.5, pz = Math.floor(boss.posZ + (tz - boss.posZ) * t) + 0.5;
            double py = surface(px, directY, pz);
            double previousX = path.isEmpty() ? boss.posX : path.get(path.size() - 1).x + 0.5;
            double previousZ = path.isEmpty() ? boss.posZ : path.get(path.size() - 1).z + 0.5;
            if(!Double.isFinite(py) || !canStand(px, py, pz)
                    || !segmentClear(previousX, directY, previousZ, px, py, pz)
                    || avoided(key(MathHelper.floor_double(px), py, MathHelper.floor_double(pz)))) { direct = false; break; }
            if(path.isEmpty() || px != previousX || pz != previousZ) path.add(new Node(MathHelper.floor_double(px), py, MathHelper.floor_double(pz)));
            directY = py;
        }
        if(direct) return;
        path.clear();
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
                int ox = side < 4 ? (side == 0 ? 1 : side == 1 ? -1 : 0) : (side < 6 ? 1 : -1);
                int oz = side < 4 ? (side == 2 ? 1 : side == 3 ? -1 : 0) : (side % 2 == 0 ? 1 : -1);
                int x = current.x + ox, z = current.z + oz;
                if(Math.abs(x - sx) > 16 || Math.abs(z - sz) > 16 || !boss.inTerritory(x + 0.5, z + 0.5, boss.territoryRadius())) continue;
                double y = surface(x + 0.5, current.y, z + 0.5);
                if(!Double.isFinite(y) || y - current.y > boss.stepHeight || current.y - y > 1.6) continue;
                long key = key(x, y, z); Node node = nodes.get(key);
                if(avoided(key)) continue;
                if(node != null && node.closed) continue;
                if(node == null) {
                    node = new Node(x, y, z); nodes.put(key, node);
                    if(!canStand(x + 0.5, y, z + 0.5)) { node.closed = true; continue; }
                    node.g = Double.POSITIVE_INFINITY;
                }
                if(!segmentClear(current.x + 0.5, current.y, current.z + 0.5, x + 0.5, y, z + 0.5)) continue;
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
    private boolean segmentClear(double x, double y, double z, double tx, double ty, double tz) {
        if(ty - y > boss.stepHeight || y - ty > 1.6) return false;
        if(ty > y && !anatomyClear(x, y + boss.stepHeight, z)) return false;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(square(tx - x, tz - z)) / 0.4));
        for(int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            if(!canStand(x + (tx - x) * t, Math.max(y, ty), z + (tz - z) * t)) return false;
            if(ty > y && !anatomyClear(x + (tx - x) * t, y + boss.stepHeight, z + (tz - z) * t)) return false;
        }
        // A lower destination is only reachable if gravity can settle the same colliders
        // through the intermediate elevations, not just fit at the two endpoints.
        int drops = (int) Math.ceil((y - ty) / 0.4);
        for(int i = 1; i < drops; i++) if(!canStand(tx, y + (ty - y) * i / drops, tz)) return false;
        return true;
    }
    /** Identical upper-body clearance for planning and physical movement, including foliage. */
    boolean anatomyClear(double x, double y, double z) {
        if(y < 0 || y + WretchPose.HEIGHT > 256) return false;
        for(AxisAlignedBB box : upperBoxes(x, y, z)) if(!clear(box)) return false;
        return true;
    }
    private AxisAlignedBB[] upperBoxes(double x, double y, double z) {
        AxisAlignedBB[] boxes = new AxisAlignedBB[3];
        boxes[0] = AxisAlignedBB.getBoundingBox(x - 1.6, y + 8.3, z - 1.6, x + 1.6, y + 20, z + 1.6);
        double angle = Math.toRadians(boss.rotationYaw);
        for(int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -1.15 : 1.15, lx = x + side * Math.cos(angle), lz = z + side * Math.sin(angle);
            boxes[leg + 1] = AxisAlignedBB.getBoundingBox(lx - 0.28, y + 0.8, lz - 0.28, lx + 0.28, y + 9, lz + 0.28);
        }
        return boxes;
    }
    /** Clip vertical travel to contact rather than zeroing every fall near an upper limb. */
    double verticalMovement(double dy) {
        for(AxisAlignedBB box : upperBoxes(boss.posX, boss.posY, boss.posZ))
            for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, box.addCoord(0, dy, 0)))
                dy = ((AxisAlignedBB) entry).calculateYOffset(box, dy);
        return dy;
    }
    boolean movementClear(double x, double y, double z) {
        boolean upperClear = anatomyClear(x, y, z);
        if(!boss.onGround) return upperClear;
        // Native stepping rises, advances and settles on the highest foot collision. Check
        // that actual support height, not an unrelated hard-coded one-block upper pose.
        AxisAlignedBB feet = AxisAlignedBB.getBoundingBox(x - 1.6, boss.posY, z - 1.6, x + 1.6, boss.posY + 0.8, z + 1.6);
        double rise = 0;
        for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, feet.addCoord(0, boss.stepHeight, 0))) {
            AxisAlignedBB box = (AxisAlignedBB) entry;
            if(box.maxY > boss.posY && box.minY < boss.posY + 0.8) rise = Math.max(rise, box.maxY - boss.posY);
        }
        if(rise == 0) return upperClear;
        if(rise > boss.stepHeight) return upperClear; // Native feet stop at an unsteppable wall.
        return anatomyClear(x, boss.posY + rise, z)
                && anatomyClear(boss.posX, boss.posY + boss.stepHeight, boss.posZ)
                && anatomyClear(x, boss.posY + boss.stepHeight, z);
    }
    private boolean clear(AxisAlignedBB box) {
        if(!GardenerNests.loaded(boss.worldObj, box.expand(1, 0, 1)) || boss.worldObj.isAnyLiquid(box)) return false;
        return boss.worldObj.getCollidingBoundingBoxes(boss, box).isEmpty();
    }
    double surface(double x, double y, double z) {
        if(!boss.worldObj.getChunkProvider().chunkExists(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4)) return Double.NaN;
        // Foliage is physical, but never a permanent destination. Search below it without
        // promising a large drop: segmentClear still enforces native step/drop limits.
        for(int by = Math.min(254, MathHelper.floor_double(y + boss.stepHeight)); by >= Math.max(1, y - 12); by--) {
            int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
            net.minecraft.block.Block block = boss.worldObj.getBlock(bx, by, bz);
            if(block.isLeaves(boss.worldObj, bx, by, bz)) continue;
            AxisAlignedBB support = block.getCollisionBoundingBoxFromPool(boss.worldObj, bx, by, bz);
            if(support != null && support.maxY <= y + boss.stepHeight + 0.001 && support.maxY >= y - 12
                    && x >= support.minX && x <= support.maxX && z >= support.minZ && z <= support.maxZ) return support.maxY;
            if(block.getMaterial().isLiquid()) return Double.NaN;
        }
        return Double.NaN;
    }
    private static double square(double x, double z) { return x * x + z * z; }
    private static long key(int x, double y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (Math.round(y * 2) & 0xFFF); }
}
