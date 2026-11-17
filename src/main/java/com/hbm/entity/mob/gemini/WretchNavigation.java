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
    static final double FOOT_HEIGHT = 0.8, EPSILON = 0.0001, MAX_DROP = 1.6;
    private final EntityWretch boss;
    private final ArrayList<Node> path = new ArrayList<Node>();
    private int cursor, nextPlan, progressTick, collisions, nextDiagnostic;
    int failures;
    private double goalX, goalY, goalZ, speed, vx, vz, progressX, progressY, progressZ, displacement, steerX, steerZ;
    private String blockedBy = "navigation";
    private String routeBlock = "no ground support";
    private double requestedX, requestedY, requestedZ, actualX, actualY, actualZ, destinationY;
    private boolean moving, noRoute;
    private final long[] avoided = new long[16];
    private final int[] avoidUntil = new int[16];
    private int avoidCursor;
    private int routeVersion, vaultTick, vaultDuration, vaultForward, vaultEnd, nextVault, nextAlternate;
    private double vaultX, vaultZ, vaultLandingX, vaultLandingY, vaultLandingZ, vaultRise;
    private boolean vaulting;
    private final long[] failedVaults = new long[12];
    private final int[] failedVaultUntil = new int[12];
    private int failedVaultCursor;
    private long vaultKey;
    private static final class Node implements Comparable<Node> {
        final int x, z; final double y;
        double g, h; Node parent; boolean closed;
        Node(int x, double y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int compareTo(Node other) { return Double.compare(g + h, other.g + other.h); }
    }
    WretchNavigation(EntityWretch boss) { this.boss = boss; }
    void goal(double x, double y, double z, double speed) {
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !boss.inTerritory(x, z, boss.territoryRadius())) { halt(); return; }
        if(!moving || square(goalX - x, goalZ - z) > 9 || Math.abs(goalY - y) > 1.25) {
            noRoute = false;
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
    boolean arrived(double distance) { return moving && square(goalX - boss.posX, goalZ - boss.posZ) <= distance * distance && Math.abs(goalY - boss.posY) <= 1.25; }
    boolean isMoving() { return moving; }
    int routeVersion() { return routeVersion; }
    double remainingPath() {
        if(cursor >= path.size() || noRoute) return Double.POSITIVE_INFINITY;
        double x = boss.posX, y = boss.posY, z = boss.posZ, remaining = 0;
        for(int i = cursor; i < path.size(); i++) {
            Node n = path.get(i);
            remaining += Math.sqrt(square(n.x + 0.5 - x, n.z + 0.5 - z) + (n.y - y) * (n.y - y));
            x = n.x + 0.5; y = n.y; z = n.z + 0.5;
        }
        return remaining;
    }
    void obstructionRemoved() {
        noRoute = false;
        path.clear(); cursor = 0; nextPlan = boss.ticksExisted + 1;
        collisions = 0;
        // A changed corridor invalidates both the route and its temporary avoidance entries.
        java.util.Arrays.fill(avoidUntil, 0);
    }
    void collision(String reason, double requestedX, double requestedY, double requestedZ, double actualX, double actualY, double actualZ) {
        if(!moving || square(requestedX, requestedZ) < 0.0001) return;
        this.requestedX = requestedX; this.requestedY = requestedY; this.requestedZ = requestedZ;
        this.actualX = actualX; this.actualY = actualY; this.actualZ = actualZ;
        if(reason != null || square(actualX, actualZ) < square(requestedX, requestedZ) * 0.1) {
            blockedBy = reason == null ? feetBlock(boss.posX, boss.boundingBox.minY, boss.posZ, requestedX, requestedZ) : reason;
            collisions++;
        } else { collisions = 0; blockedBy = "none"; }
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
        if(vaulting) { tickVault(); return; }
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
                            + " currentY=" + boss.posY + " destinationY=" + (cursor < path.size() ? destinationY : goalY)
                            + " groundY=" + surface(boss.posX, boss.boundingBox.minY, boss.posZ)
                            + " request=" + requestedX + "," + requestedY + "," + requestedZ
                            + " direction=" + steerX + "," + steerZ
                            + " actual=" + actualX + "," + actualY + "," + actualZ
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
            if(!noRoute && collisions == 0) blockedBy = "waiting for path search";
            vx = vz = 0; boss.motionX = boss.motionZ = 0; return;
        }
        Node node = path.get(cursor);
        if(square(node.x + 0.5 - boss.posX, node.z + 0.5 - boss.posZ) < 0.09 && Math.abs(node.y - boss.boundingBox.minY) < 0.6) {
            if(++cursor >= path.size()) { vx = vz = 0; return; }
            node = path.get(cursor);
        }
        double dx = node.x + 0.5 - boss.posX, dz = node.z + 0.5 - boss.posZ;
        destinationY = node.y;
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
        // Count actual failed native movement attempts, not a pre-step anatomy guess or
        // last frame's horizontal-collision flag. Give stepping several frames to resolve.
        if(collisions >= 6) { failCurrent(); return; }
        double limit = Math.min(1, length / Math.max(0.001, Math.sqrt(square(vx, vz))));
        vx *= limit; vz *= limit;
        boss.motionX = vx; boss.motionZ = vz;
    }
    private void failCurrent() {
        if(cursor < path.size()) {
            Node node = path.get(cursor); avoided[avoidCursor] = key(node.x, node.y, node.z); avoidUntil[avoidCursor] = boss.ticksExisted + 200;
            avoidCursor = (avoidCursor + 1) % avoided.length;
        }
        failures = Math.min(12, failures + 1); path.clear(); cursor = 0;
        collisions = 0; vx = vz = 0; boss.motionX = boss.motionZ = 0;
        nextPlan = boss.ticksExisted + 8;
    }
    private boolean avoided(long key) {
        for(int i = 0; i < avoided.length; i++) if(avoided[i] == key && avoidUntil[i] > boss.ticksExisted) return true;
        return false;
    }
    private void plan() {
        routeVersion++;
        nextPlan = boss.ticksExisted + 20 + Math.min(20, failures * 4);
        path.clear(); cursor = 0;
        int sx = MathHelper.floor_double(boss.posX), sz = MathHelper.floor_double(boss.posZ);
        double dx = goalX - boss.posX, dz = goalZ - boss.posZ, range = Math.sqrt(dx * dx + dz * dz);
        double scale = range > 14 ? 14 / range : 1;
        double tx = boss.posX + dx * scale, tz = boss.posZ + dz * scale;
        // Prefer a fully sampled direct segment before the bounded surface search.
        double directY = boss.boundingBox.minY;
        boolean direct = true;
        int samples = Math.max(1, (int) Math.ceil(Math.min(range, 14) / 0.75));
        for(int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double px = Math.floor(boss.posX + (tx - boss.posX) * t) + 0.5, pz = Math.floor(boss.posZ + (tz - boss.posZ) * t) + 0.5;
            double previousX = path.isEmpty() ? boss.posX : path.get(path.size() - 1).x + 0.5;
            double previousZ = path.isEmpty() ? boss.posZ : path.get(path.size() - 1).z + 0.5;
            double py = segmentSurface(previousX, directY, previousZ, px, pz);
            if(!Double.isFinite(py)) { direct = false; break; }
            if(avoided(key(MathHelper.floor_double(px), py, MathHelper.floor_double(pz)))) {
                routeBlock = "temporarily avoided failed node"; direct = false; break;
            }
            if(path.isEmpty() || px != previousX || pz != previousZ) path.add(new Node(MathHelper.floor_double(px), py, MathHelper.floor_double(pz)));
            directY = py;
        }
        if(direct && (range > 14 || Math.abs(directY - goalY) <= 1.25)) { noRoute = false; blockedBy = "none"; return; }
        String directBlock = routeBlock;
        path.clear();
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        Map<Long, Node> nodes = new HashMap<Long, Node>();
        Node start = new Node(sx, boss.boundingBox.minY, sz), best = start;
        start.h = routeError(tx - sx - 0.5, goalY - start.y, tz - sz - 0.5);
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
                double y = segmentSurface(current == start ? boss.posX : current.x + 0.5, current.y,
                        current == start ? boss.posZ : current.z + 0.5, x + 0.5, z + 0.5);
                if(!Double.isFinite(y)) continue;
                long key = key(x, y, z); Node node = nodes.get(key);
                if(avoided(key)) continue;
                if(node != null && node.closed) continue;
                if(node == null) {
                    node = new Node(x, y, z); nodes.put(key, node);
                    node.g = Double.POSITIVE_INFINITY;
                }
                double cost = current.g + Math.sqrt(ox * ox + oz * oz) + Math.abs(current.y - node.y) * 2;
                if(cost >= node.g) continue;
                open.remove(node); node.g = cost; node.parent = current;
                node.h = routeError(tx - x - 0.5, goalY - y, tz - z - 0.5); open.add(node);
            }
        }
        if(best == start || best.h > start.h - 0.5) {
            noRoute = true;
            blockedBy = "no valid path (" + directBlock + ")";
            failures = Math.min(12, failures + 1); return;
        }
        for(Node n = best; n.parent != null; n = n.parent) path.add(n);
        Collections.reverse(path);
        noRoute = false; blockedBy = "none";
    }
    private static double routeError(double x, double y, double z) {
        return Math.sqrt(square(x, z) + y * y);
    }
    /** A short flank uses the existing surface search, including native steps and exclusions. */
    boolean alternate(double x, double y, double z) {
        if(boss.ticksExisted < nextAlternate) return false;
        nextAlternate = boss.ticksExisted + 40;
        failCurrent();
        double angle = Math.atan2(z - boss.posZ, x - boss.posX);
        int first = (boss.getEntityId() + failures) % 2 == 0 ? 1 : -1;
        for(int i = 0; i < 2; i++) {
            double a = angle + first * (i == 0 ? 1 : -1) * Math.PI / 2;
            double px = boss.posX + Math.cos(a) * 6, pz = boss.posZ + Math.sin(a) * 6;
            double py = surface(px, boss.posY, pz);
            if(!Double.isFinite(py) || !canStand(px, py, pz) || !boss.inTerritory(px, pz, boss.territoryRadius() - 2)) continue;
            goal(px, py, pz, com.hbm.config.GeminiConfig.wretchHuntSpeed);
            plan();
            if(!noRoute && !path.isEmpty() && remainingPath() >= 2) return true;
            break; // At most one expensive surface search per alternate-route decision.
        }
        goal(x, y, z, com.hbm.config.GeminiConfig.wretchHuntSpeed);
        return false;
    }
    boolean vaulting() { return vaulting; }
    void cancelVault() {
        vaulting = false; boss.syncVault(0, 0, 0); boss.motionX = boss.motionZ = 0;
        // Never cancel gravity or restore an airborne position.
    }
    /** Bounded ballistic climb/vault. One impulse; vanilla gravity owns every later Y step. */
    boolean tryVault(double tx, double ty, double tz, double maxRise) {
        if(vaulting || !boss.onGround || boss.ticksExisted < nextVault || boss.state() != EntityWretch.IDLE) return false;
        nextVault = boss.ticksExisted + 10;
        double dx = tx - boss.posX, dz = tz - boss.posZ, length = Math.sqrt(square(dx, dz));
        if(length < 0.5) { dx = -Math.sin(Math.toRadians(boss.rotationYaw)); dz = Math.cos(Math.toRadians(boss.rotationYaw)); length = 1; }
        dx /= length; dz /= length;
        float facing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // Align through ordinary navigation first; collision proxies and pose share this yaw.
        float turn = MathHelper.wrapAngleTo180_float(facing - boss.rotationYaw);
        boss.rotationYaw += MathHelper.clamp_float(turn, -16, 16);
        if(Math.abs(MathHelper.wrapAngleTo180_float(facing - boss.rotationYaw)) > 30) return false;
        for(int candidate = 0; candidate < 3; candidate++) {
            double distance = 2.5 + candidate * 2, lx = boss.posX + dx * distance, lz = boss.posZ + dz * distance;
            if(!boss.inTerritory(lx, lz, boss.territoryRadius() - 2)) continue;
            double ly = surface(lx, boss.posY + maxRise - boss.stepHeight, lz);
            if(!Double.isFinite(ly) || ly < boss.posY - MAX_DROP || ly > boss.posY + maxRise + EPSILON || !landingSpace(lx, ly, lz)) continue;
            long pair = key(MathHelper.floor_double(boss.posX), boss.posY, MathHelper.floor_double(boss.posZ))
                    ^ Long.rotateLeft(key(MathHelper.floor_double(lx), ly, MathHelper.floor_double(lz)), 23);
            boolean avoided = false;
            for(int i = 0; i < failedVaults.length; i++) if(failedVaults[i] == pair && failedVaultUntil[i] > boss.ticksExisted) avoided = true;
            if(avoided) continue;
            AxisAlignedBB sweep = feet(boss.posX, boss.posY + EPSILON, boss.posZ).addCoord(dx * distance, maxRise, dz * distance);
            if(!GardenerNests.loaded(boss.worldObj, sweep.expand(1, 21, 1))) continue;
            double crest = Math.max(boss.posY, ly);
            for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, sweep)) crest = Math.max(crest, ((AxisAlignedBB) entry).maxY);
            double rise = crest - boss.posY;
            if(rise <= boss.stepHeight || rise > maxRise + EPSILON) continue;
            boolean found = false;
            for(double launch = 0.48; launch <= 1.081; launch += 0.08) {
                double sy = boss.posY, vy = launch; int start = -1, end = -1, land = -1;
                for(int t = 0; t < 48; t++) {
                    sy += vy; vy = (vy - 0.08) * 0.98;
                    if(start < 0 && sy >= crest + 0.08) start = t;
                    if(start >= 0 && end < 0 && vy < 0 && sy < crest + 0.12) end = t - 1;
                    if(vy < 0 && sy <= ly) { land = t + 1; break; }
                }
                if(start < 0 || end <= start + 1 || land < 0 || distance / (end - start) > 0.48) continue;
                double sx = boss.posX, sz = boss.posZ; sy = boss.posY; vy = launch;
                double vx = dx * distance / (end - start), vz = dz * distance / (end - start);
                boolean clear = true;
                for(int t = 0; t < land; t++) {
                    double mx = t >= start && t < end ? vx : 0, mz = t >= start && t < end ? vz : 0;
                    if(!vaultSegment(sx, sy, sz, mx, vy, mz, t == land - 1)) { clear = false; break; }
                    sx += mx; sz += mz; sy += vy; vy = (vy - 0.08) * 0.98;
                }
                if(!clear) continue;
                halt(); vaulting = true; vaultTick = 0; vaultDuration = land; vaultForward = start; vaultEnd = end;
                vaultX = vx; vaultZ = vz; vaultLandingX = lx; vaultLandingY = ly; vaultLandingZ = lz;
                vaultRise = rise; vaultKey = pair;
                boss.clearPresentation(); boss.motionY = launch; boss.velocityChanged = true;
                boss.syncVault(1, vaultDuration, vaultRise); found = true; break;
            }
            if(found) return true;
            failedVaults[failedVaultCursor] = pair; failedVaultUntil[failedVaultCursor] = boss.ticksExisted + 200;
            failedVaultCursor = (failedVaultCursor + 1) % failedVaults.length;
        }
        return false;
    }
    private boolean landingSpace(double x, double y, double z) {
        if(!canStand(x, y, z)) return false;
        double angle = Math.toRadians(boss.rotationYaw);
        for(int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -1.15 : 1.15, lx = x + side * Math.cos(angle), lz = z + side * Math.sin(angle);
            AxisAlignedBB sole = AxisAlignedBB.getBoundingBox(lx - 0.28, y - 0.12, lz - 0.28, lx + 0.28, y + EPSILON, lz + 0.28);
            if(boss.worldObj.getCollidingBoundingBoxes(boss, sole).isEmpty()) return false;
        }
        return true;
    }
    private boolean vaultSegment(double x, double y, double z, double dx, double dy, double dz, boolean landing) {
        AxisAlignedBB swept = feet(x, y, z).addCoord(dx, dy, dz).expand(1, 21, 1);
        if(!GardenerNests.loaded(boss.worldObj, swept)) return false;
        Motion move = nativeMovement(x, y, z, dx, dy, dz, 0, false);
        return Math.abs(move.x - dx) < EPSILON && Math.abs(move.z - dz) < EPSILON
                && (Math.abs(move.y - dy) < EPSILON || landing && dy < 0)
                && anatomyMovement(x, y, z, move) == null && !boss.worldObj.isAnyLiquid(feet(x + dx, y + move.y, z + dz));
    }
    private void tickVault() {
        if(vaultTick > 0 && boss.onGround || vaultTick > vaultDuration + 10) {
            boolean success = square(boss.posX - vaultLandingX, boss.posZ - vaultLandingZ) < 1
                    && Math.abs(boss.posY - vaultLandingY) < 0.6;
            if(!success) {
                failedVaults[failedVaultCursor] = vaultKey; failedVaultUntil[failedVaultCursor] = boss.ticksExisted + 240;
                failedVaultCursor = (failedVaultCursor + 1) % failedVaults.length;
            }
            cancelVault(); nextVault = boss.ticksExisted + 40; obstructionRemoved();
            if(success) boss.brain.traversalProgress();
            return;
        }
        double dx = vaultTick >= vaultForward && vaultTick < vaultEnd ? vaultX : 0;
        double dz = vaultTick >= vaultForward && vaultTick < vaultEnd ? vaultZ : 0;
        // Revalidate against changed blocks/chunks. A rejected flight falls naturally;
        // its takeoff/landing pair is not immediately retried.
        if(!vaultSegment(boss.posX, boss.posY, boss.posZ, dx, boss.motionY, dz, boss.motionY < 0)) {
            failedVaults[failedVaultCursor] = vaultKey; failedVaultUntil[failedVaultCursor] = boss.ticksExisted + 240;
            failedVaultCursor = (failedVaultCursor + 1) % failedVaults.length;
            cancelVault(); nextVault = boss.ticksExisted + 40; obstructionRemoved(); return;
        }
        boss.motionX = dx; boss.motionZ = dz;
        boss.syncVault(Math.min(++vaultTick, vaultDuration), vaultDuration, vaultRise);
    }
    boolean canStand(double x, double y, double z) {
        return y >= 1 && y + WretchPose.HEIGHT < 256 && anatomyClear(x, y, z)
                && clear(feet(x, y + EPSILON, z))
                && !boss.worldObj.getCollidingBoundingBoxes(boss, feet(x, y, z).addCoord(0, -0.05, 0)).isEmpty();
    }
    private double segmentSurface(double x, double y, double z, double tx, double tz) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(square(tx - x, tz - z)) / 0.25));
        double dx = (tx - x) / steps, dz = (tz - z) / steps;
        for(int i = 1; i <= steps; i++) {
            double nx = x + dx, nz = z + dz, ny = surface(nx, y, nz);
            if(!Double.isFinite(ny)) return Double.NaN;
            if(y - ny > MAX_DROP + EPSILON) { routeBlock = "elevation exceeds safe drop"; return Double.NaN; }
            if(!canStand(nx, ny, nz)) {
                String reason = anatomyBlock(nx, ny, nz, 0, 0, 0);
                routeBlock = reason == null ? feetBlock(x, y, z, dx, dz) : reason; return Double.NaN;
            }
            double step = Math.max(0, verticalMovement(x, y, z, boss.stepHeight));
            Motion move = nativeMovement(x, y, z, dx, -0.08, dz, step, true);
            if(Math.abs(move.x - dx) > EPSILON || Math.abs(move.z - dz) > EPSILON) {
                routeBlock = ny > y + EPSILON ? "elevation exceeds stepping capability" : "feet collision"; return Double.NaN;
            }
            String reason = anatomyMovement(x, y, z, move);
            if(reason == null && ny < y + move.y)
                reason = anatomyBlock(nx, y + move.y, nz, 0, ny - y - move.y, 0);
            if(reason != null) { routeBlock = reason; return Double.NaN; }
            x = nx; y = ny; z = nz;
        }
        return y;
    }
    /** Identical upper-body clearance for planning and physical movement, including foliage. */
    boolean anatomyClear(double x, double y, double z) {
        return anatomyBlock(x, y, z, 0, 0, 0) == null;
    }
    private String anatomyBlock(double x, double y, double z, double dx, double dy, double dz) {
        if(y + Math.min(0, dy) < 0 || y + Math.max(0, dy) + WretchPose.HEIGHT > 256) return "world height boundary";
        AxisAlignedBB[] boxes = upperBoxes(x, y, z);
        for(int i = 0; i < boxes.length; i++) {
            AxisAlignedBB swept = boxes[i].addCoord(dx, dy, dz);
            if(!GardenerNests.loaded(boss.worldObj, swept.expand(1, 0, 1))) return "unloaded terrain";
            if(boss.worldObj.isAnyLiquid(swept) || !boss.worldObj.getCollidingBoundingBoxes(boss, swept).isEmpty())
                return i == 0 ? "torso collision" : "leg collision";
        }
        return null;
    }
    private AxisAlignedBB[] upperBoxes(double x, double y, double z) {
        AxisAlignedBB[] boxes = new AxisAlignedBB[3];
        double half = boss.width * 0.5;
        boxes[0] = AxisAlignedBB.getBoundingBox(x - half, y + 8.3, z - half, x + half, y + 20, z + half);
        double angle = Math.toRadians(boss.rotationYaw);
        for(int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -1.15 : 1.15, lx = x + side * Math.cos(angle), lz = z + side * Math.sin(angle);
            boxes[leg + 1] = AxisAlignedBB.getBoundingBox(lx - 0.28, y + 0.8, lz - 0.28, lx + 0.28, y + 9, lz + 0.28);
        }
        return boxes;
    }
    /** Clip vertical travel to contact rather than zeroing every fall near an upper limb. */
    double verticalMovement(double dy) {
        return verticalMovement(boss.posX, boss.boundingBox.minY, boss.posZ, dy);
    }
    private double verticalMovement(double x, double y, double z, double dy) {
        for(AxisAlignedBB box : upperBoxes(x, y, z))
            for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, box.addCoord(0, dy, 0)))
                dy = ((AxisAlignedBB) entry).calculateYOffset(box, dy);
        return dy;
    }
    String movementBlock(double dx, double dy, double dz, double step) {
        return anatomyMovement(boss.posX, boss.boundingBox.minY, boss.posZ,
                nativeMovement(boss.posX, boss.boundingBox.minY, boss.posZ, dx, dy, dz, step, boss.onGround));
    }
    private String anatomyMovement(double x, double y, double z, Motion move) {
        // Vanilla resolves Y, X, Z; a step instead rises, advances X/Z, then settles.
        double up = move.rise > 0 ? move.rise : move.y;
        String reason = anatomyBlock(x, y, z, 0, up, 0);
        if(reason == null) reason = anatomyBlock(x, y + up, z, move.x, 0, 0);
        if(reason == null) reason = anatomyBlock(x + move.x, y + up, z, 0, 0, move.z);
        if(reason == null && move.rise > 0) reason = anatomyBlock(x + move.x, y + up, z + move.z, 0, move.y - up, 0);
        return reason;
    }
    private AxisAlignedBB feet(double x, double y, double z) {
        double half = boss.width * 0.5;
        return AxisAlignedBB.getBoundingBox(x - half, y, z - half, x + half, y + FOOT_HEIGHT, z + half);
    }
    private String feetBlock(double x, double y, double z, double dx, double dz) {
        double rise = 0;
        for(Object entry : boss.worldObj.getCollidingBoundingBoxes(boss, feet(x, y, z).addCoord(dx, boss.stepHeight, dz)))
            rise = Math.max(rise, ((AxisAlignedBB) entry).maxY - y);
        if(rise > boss.stepHeight + EPSILON) return "elevation exceeds stepping capability (feet collision)";
        if(rise > EPSILON) {
            String upper = anatomyBlock(x, y, z, 0, boss.stepHeight, 0);
            if(upper != null) return upper + " prevents foot step";
        }
        return "feet collision";
    }
    private static final class Motion {
        double x, y, z, rise;
    }
    /** Read-only preview of Forge 1.7.10 Entity.moveEntity's native foot resolution. */
    private Motion nativeMovement(double x, double y, double z, double dx, double dy, double dz, double step, boolean grounded) {
        AxisAlignedBB origin = feet(x, y, z), box = origin.copy();
        java.util.List<?> collisions = boss.worldObj.getCollidingBoundingBoxes(boss, box.addCoord(dx, dy, dz));
        Motion normal = resolve(box, collisions, dx, dy, dz);
        if(step <= 0 || !(grounded || dy < 0 && normal.y != dy)
                || Math.abs(normal.x - dx) < EPSILON && Math.abs(normal.z - dz) < EPSILON) return normal;
        box = origin.copy();
        collisions = boss.worldObj.getCollidingBoundingBoxes(boss, box.addCoord(dx, step, dz));
        Motion raised = resolve(box, collisions, dx, step, dz);
        raised.rise = raised.y;
        double down = -step;
        for(Object entry : collisions) down = ((AxisAlignedBB) entry).calculateYOffset(box, down);
        raised.y += down;
        return square(raised.x, raised.z) > square(normal.x, normal.z) ? raised : normal;
    }
    private Motion resolve(AxisAlignedBB box, java.util.List<?> collisions, double dx, double dy, double dz) {
        Motion move = new Motion();
        for(Object entry : collisions) dy = ((AxisAlignedBB) entry).calculateYOffset(box, dy);
        box.offset(0, dy, 0);
        for(Object entry : collisions) dx = ((AxisAlignedBB) entry).calculateXOffset(box, dx);
        box.offset(dx, 0, 0);
        for(Object entry : collisions) dz = ((AxisAlignedBB) entry).calculateZOffset(box, dz);
        box.offset(0, 0, dz);
        move.x = dx; move.y = dy; move.z = dz; return move;
    }
    private boolean clear(AxisAlignedBB box) {
        if(!GardenerNests.loaded(boss.worldObj, box.expand(1, 0, 1)) || boss.worldObj.isAnyLiquid(box)) return false;
        return boss.worldObj.getCollidingBoundingBoxes(boss, box).isEmpty();
    }
    double surface(double x, double y, double z) {
        // Support belongs to the entire native footprint, not the centre column. Ask
        // each block for its actual shapes (stairs have multiple collision boxes).
        AxisAlignedBB column = feet(x, y, z).addCoord(0, boss.stepHeight, 0).addCoord(0, -12, 0);
        if(!GardenerNests.loaded(boss.worldObj, column.expand(1, 0, 1))) { routeBlock = "unloaded terrain"; return Double.NaN; }
        ArrayList<AxisAlignedBB> shapes = new ArrayList<AxisAlignedBB>(4);
        for(int by = Math.min(254, MathHelper.floor_double(y + boss.stepHeight)); by >= Math.max(0, MathHelper.floor_double(y - 12) - 1); by--) {
            double highest = Double.NEGATIVE_INFINITY;
            for(int bx = MathHelper.floor_double(column.minX); bx <= MathHelper.floor_double(column.maxX); bx++)
                for(int bz = MathHelper.floor_double(column.minZ); bz <= MathHelper.floor_double(column.maxZ); bz++) {
                    net.minecraft.block.Block block = boss.worldObj.getBlock(bx, by, bz);
                    if(block.isLeaves(boss.worldObj, bx, by, bz)) continue;
                    if(block.getMaterial().isLiquid()) { routeBlock = "liquid terrain"; return Double.NaN; }
                    shapes.clear(); block.addCollisionBoxesToList(boss.worldObj, bx, by, bz, column, shapes, boss);
                    for(AxisAlignedBB support : shapes)
                        if(support.maxY <= y + boss.stepHeight + EPSILON && support.maxY >= y - 12)
                            highest = Math.max(highest, support.maxY);
                }
            if(Double.isFinite(highest)) return highest;
        }
        routeBlock = "no ground support";
        return Double.NaN;
    }
    boolean walkingUnavailable() { return moving && noRoute && failures >= 2; }
    private static double square(double x, double z) { return x * x + z * z; }
    private static long key(int x, double y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (Math.round(y * 2) & 0xFFF); }
}
