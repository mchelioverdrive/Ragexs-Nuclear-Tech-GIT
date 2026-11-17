package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

import com.hbm.world.gemini.GardenerNests;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Woodcrawler-only, server-thread surface graph. Positions are feet positions;
 * the folded body stays vertical on walls (the existing articulated climbing pose)
 * and inverts inside the same collision envelope on ceilings. */
final class WoodcrawlerSurfaceTraversal {
    private static final int GROUND = 0, CEILING = 1, WEST = 2, EAST = 3, NORTH = 4, SOUTH = 5, LADDER = 6;
    private static final double HALF = EntityWoodcrawler.CRAWLING_WIDTH / 2D;
    private static final double HEIGHT = EntityWoodcrawler.CRAWLING_HEIGHT, GAP = 0.003D, CONTACT = 0.10D;
    private static final int MAX_NODES = 4096, MAX_SITES = 16384;
    // A shared cap bounds aggregate search work, without sharing routes or entity state.
    private static final Map<World, Budget> BUDGETS = new WeakHashMap<World, Budget>();
    private final EntityWoodcrawler mob;
    private final World world;
    private final List<Node> route = new ArrayList<Node>();
    private final List<Failure> failures = new ArrayList<Failure>();
    private Search search;
    private int step, retryAt, stalled, face = GROUND, validateAt, failedSearches;
    private double goalX, goalY, goalZ, vx, vy, vz, bestDistance = Double.MAX_VALUE;
    private boolean attached, returning, huntingRoute;

    private static final class Budget { long tick = Long.MIN_VALUE; int left; }
    private static final class Failure {
        final Node node;
        final int until;
        Failure(Node node, int until) { this.node = node; this.until = until; }
    }
    private static final class Node {
        final double x, y, z;
        final int face;
        Node parent;
        List<Node> link;
        double cost, score;
        boolean closed;
        Node(double x, double y, double z, int face) { this.x = x; this.y = y; this.z = z; this.face = face; }
        double distance(double x, double y, double z) {
            return Math.sqrt(square(this.x - x) + square(this.y - y) + square(this.z - z));
        }
    }

    WoodcrawlerSurfaceTraversal(EntityWoodcrawler mob) { this.mob = mob; world = mob.worldObj; }
    boolean active() { return attached || search != null || step < route.size(); }
    boolean leapContact() { return attached && contact(new Node(mob.posX, mob.posY, mob.posZ, face), null); }
    void suspendForLeap() { clear(); retryAt = mob.ticksExisted + 20; }
    void returnToGround() {
        if(!active()) return;
        returning = true;
        route.clear(); step = 0; search = null; retryAt = 0;
    }
    // Approaches, ladders and fence contacts are vertices of the same graph now.
    Vec3 waypoint(double x, double y, double z) {
        if(step >= route.size()) return null;
        Node next = route.get(step);
        return Vec3.createVectorHelper(next.x, next.y, next.z);
    }
    boolean standingOnBarrier() {
        AxisAlignedBB feet = body(mob.posX, mob.posY, mob.posZ).addCoord(0, -0.12D, 0);
        for(AxisAlignedBB box : boxes(feet, null)) if(box.maxY <= mob.posY + GAP && box.maxY > mob.posY - 0.12D
                && (box.maxX - box.minX < 0.6D || box.maxZ - box.minZ < 0.6D)) return true;
        return false;
    }
    private void clear() {
        route.clear(); step = 0; search = null; attached = returning = huntingRoute = false;
        face = GROUND; vx = vy = vz = 0;
        mob.setClimbing(false); mob.setSurfaceFace(GROUND);
    }
    private void finish() {
        clear(); retryAt = mob.ticksExisted + 30;
        mob.motionX = mob.motionY = mob.motionZ = 0;
        mob.onSurfaceTraversalFinished();
    }
    private void fail(Node blocked) {
        if(blocked != null) {
            if(failures.size() == 12) failures.remove(0);
            failures.add(new Failure(blocked, mob.ticksExisted + 160));
        }
        route.clear(); step = 0; search = null;
        if(blocked == null) failedSearches = Math.min(8, failedSearches + 1);
        retryAt = mob.ticksExisted + 30 + mob.getEntityId() % 20 + failedSearches * 20;
        vx = vy = vz = 0; stalled = 0;
        // A failed surface search must release grounded hunters to their existing
        // door/entrance/breach controller during backoff, rather than starving it.
        if(contact(new Node(mob.posX, mob.posY, mob.posZ, GROUND), null)) {
            attached = false; face = GROUND;
            mob.setClimbing(false); mob.setSurfaceFace(GROUND);
        }
    }
    private boolean rejected(Node node) {
        for(int i = failures.size() - 1; i >= 0; i--) {
            Failure failure = failures.get(i);
            if(failure.until <= mob.ticksExisted) failures.remove(i);
            else if(node.face == failure.node.face && node.distance(failure.node.x, failure.node.y, failure.node.z) < 0.4D) return true;
        }
        return false;
    }
    private AxisAlignedBB body(double x, double y, double z) {
        return AxisAlignedBB.getBoundingBox(x - HALF, y, z - HALF, x + HALF, y + HEIGHT, z + HALF);
    }
    private boolean loaded(AxisAlignedBB box) {
        return box.minY >= 0 && box.maxY < world.getHeight() && GardenerNests.loaded(world, box.expand(1, 0, 1));
    }
    /** Block collision geometry only: entities are obstacles during real movement, never attachment surfaces. */
    private List<AxisAlignedBB> boxes(AxisAlignedBB area, Search cache) {
        List<AxisAlignedBB> result = new ArrayList<AxisAlignedBB>();
        if(!loaded(area)) return result;
        for(int x = MathHelper.floor_double(area.minX); x <= MathHelper.floor_double(area.maxX); x++)
            for(int y = Math.max(0, MathHelper.floor_double(area.minY) - 1); y <= MathHelper.floor_double(area.maxY); y++)
                for(int z = MathHelper.floor_double(area.minZ); z <= MathHelper.floor_double(area.maxZ); z++) {
                    long key = ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | y;
                    List<AxisAlignedBB> cell = cache == null ? null : cache.geometry.get(key);
                    if(cell == null) {
                        cell = new ArrayList<AxisAlignedBB>();
                        Block block = world.getBlock(x, y, z);
                        AxisAlignedBB volume = AxisAlignedBB.getBoundingBox(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2);
                        block.addCollisionBoxesToList(world, x, y, z, volume, cell, mob);
                        // Some mod blocks reuse mutable collision boxes.
                        for(int i = 0; i < cell.size(); i++) cell.set(i, cell.get(i).copy());
                        if(cache != null) cache.geometry.put(key, cell);
                    }
                    for(AxisAlignedBB box : cell) if(box.intersectsWith(area)) result.add(box);
                }
        return result;
    }
    private boolean clearBody(Node node, Search cache) {
        AxisAlignedBB box = body(node.x, node.y, node.z).contract(0.001D, 0.001D, 0.001D);
        return loaded(box) && boxes(box, cache).isEmpty() && !world.isAnyLiquid(box);
    }
    private boolean ladder(Node node) {
        int x = MathHelper.floor_double(node.x), z = MathHelper.floor_double(node.z);
        for(int y = MathHelper.floor_double(node.y); y <= MathHelper.floor_double(node.y + HEIGHT - GAP); y++)
            if(world.blockExists(x, y, z) && world.getBlock(x, y, z).isLadder(world, x, y, z, mob)) return true;
        return false;
    }
    private boolean contact(Node node, Search cache) {
        AxisAlignedBB body = body(node.x, node.y, node.z);
        if(!loaded(body.expand(CONTACT, CONTACT, CONTACT))) return false;
        if(node.face == LADDER) return ladder(node);
        return contactGeometry(node, boxes(body.expand(CONTACT, CONTACT, CONTACT), cache));
    }
    private boolean contactGeometry(Node node, List<AxisAlignedBB> geometry) {
        AxisAlignedBB body = body(node.x, node.y, node.z);
        AxisAlignedBB probe = body.copy();
        // Probe only the selected side, so a nearby floor cannot validate a missing ceiling.
        if(node.face == GROUND) { probe.maxY = body.minY + GAP; probe.minY -= CONTACT; }
        if(node.face == CEILING) { probe.minY = body.maxY - GAP; probe.maxY += CONTACT; }
        if(node.face == WEST) { probe.maxX = body.minX + GAP; probe.minX -= CONTACT; }
        if(node.face == EAST) { probe.minX = body.maxX - GAP; probe.maxX += CONTACT; }
        if(node.face == NORTH) { probe.maxZ = body.minZ + GAP; probe.minZ -= CONTACT; }
        if(node.face == SOUTH) { probe.minZ = body.maxZ - GAP; probe.maxZ += CONTACT; }
        // Hands/feet retain a small contact patch around a lip while the complete
        // body clears it. Require the actual outward face, never a block interior.
        if(node.face <= CEILING) probe = probe.expand(CONTACT, 0, CONTACT);
        else if(node.face <= EAST) probe = probe.expand(0, CONTACT, CONTACT);
        else probe = probe.expand(CONTACT, CONTACT, 0);
        for(AxisAlignedBB box : geometry) {
            boolean outward = node.face == GROUND ? box.maxY <= body.minY + 0.01D
                    : node.face == CEILING ? box.minY >= body.maxY - 0.01D
                    : node.face == WEST ? box.maxX <= body.minX + 0.01D
                    : node.face == EAST ? box.minX >= body.maxX - 0.01D
                    : node.face == NORTH ? box.maxZ <= body.minZ + 0.01D : box.minZ >= body.maxZ - 0.01D;
            if(outward && box.intersectsWith(probe)) return true;
        }
        return false;
    }
    private int support(Node node, Search cache) {
        if(contact(node, cache)) return node.face;
        for(int side = 0; side <= LADDER; side++) if(side != node.face
                && contact(new Node(node.x, node.y, node.z, side), cache)) return side;
        return -1;
    }
    private boolean goal(Node node) {
        if(returning) return node.face == GROUND;
        if(Math.abs(node.y - goalY) > 0.8D || square(node.x - goalX) + square(node.z - goalZ) > 2.25D) return false;
        Vec3 from = Vec3.createVectorHelper(node.x, node.y + HEIGHT * 0.5D, node.z);
        Vec3 to = Vec3.createVectorHelper(goalX, goalY + 0.9D, goalZ);
        return loaded(body(node.x, node.y, node.z).addCoord(goalX - node.x, goalY - node.y, goalZ - node.z))
                && world.func_147447_a(from, to, false, true, false) == null;
    }

    private final class Search {
        final Map<Long, List<AxisAlignedBB>> geometry = new HashMap<Long, List<AxisAlignedBB>>();
        final Map<Long, Node> sites = new HashMap<Long, Node>();
        final PriorityQueue<Node> open = new PriorityQueue<Node>(64, Comparator.comparingDouble(n -> n.score));
        final int ox = MathHelper.floor_double(mob.posX), oy = MathHelper.floor_double(mob.posY), oz = MathHelper.floor_double(mob.posZ);
        int expanded;
        Search() {
            Node start = new Node(mob.posX, mob.posY, mob.posZ, face);
            start.score = heuristic(start); sites.put(key(start), start); open.add(start);
        }
        long key(Node node) {
            return ((long) Math.round((node.x - ox) * 1024) & 0x3FFFFL)
                    | ((long) Math.round((node.z - oz) * 1024) & 0x3FFFFL) << 18
                    | ((long) Math.round((node.y - oy) * 1024) & 0x3FFFFL) << 36 | (long) node.face << 54;
        }
        double heuristic(Node node) { return returning ? 0 : node.distance(goalX, goalY, goalZ); }
        boolean usefulFrontier(Node node) {
            // A distant hunt can exceed one local graph. Exit onto a supported
            // shelf that makes substantial progress, then let pursuit continue.
            if(returning || node.face != GROUND || Math.abs(goalX - ox) <= 22 && Math.abs(goalZ - oz) <= 22) return false;
            return node.distance(goalX, goalY, goalZ) < Math.sqrt(square(goalX - ox) + square(goalY - oy) + square(goalZ - oz)) - 6
                    && Math.abs(node.y - goalY) < Math.abs(oy - goalY) - 1;
        }
        void candidate(Node from, Node next, Set<Long> considered) {
            if(Math.abs(next.x - ox) > 24 || Math.abs(next.z - oz) > 24 || Math.abs(next.y - oy) > 32
                    || next.y < 1 || next.y + HEIGHT >= world.getHeight()) return;
            double distance = next.distance(from.x, from.y, from.z);
            if(distance < 0.03D || distance > 1.55D) return;
            long key = key(next);
            if(!considered.add(key)) return;
            Node old = sites.get(key);
            double cost = from.cost + distance + (next.face == from.face ? 0 : 0.2D) + (next.face == LADDER ? 0 : 0.02D);
            if(old != null && (old.closed || old.cost <= cost)) return;
            if(rejected(next) || !clearBody(next, this) || !contact(next, this) || mob.isHazardAt(next.x, next.y, next.z)) return;
            List<Node> link = link(from, next, this);
            if(link == null) return;
            cost += Math.max(0, mob.lightAt(MathHelper.floor_double(next.x), MathHelper.floor_double(next.y + 1), MathHelper.floor_double(next.z)) - 7) * 0.04D;
            if(old != null && old.cost <= cost) return;
            if(old != null) open.remove(old);
            next.parent = from; next.link = link; next.cost = cost; next.score = cost + heuristic(next) * 1.15D;
            sites.put(key, next); open.add(next);
        }
        void expand(Node from) {
            double sy = Math.rint(from.y * 2) / 2;
            Set<Long> considered = new HashSet<Long>();
            AxisAlignedBB area = body(from.x, from.y, from.z).expand(1.1D, 0.75D, 1.1D);
            for(AxisAlignedBB box : boxes(area, this)) {
                double x = (box.minX + box.maxX) * 0.5D, z = (box.minZ + box.maxZ) * 0.5D;
                candidate(from, new Node(x, box.maxY + GAP, z, GROUND), considered);
                candidate(from, new Node(x, box.minY - HEIGHT - GAP, z, CEILING), considered);
                for(int a = -1; a <= 1; a++) {
                    double y = sy + a * 0.5D;
                    if(y + HEIGHT > box.minY + 0.04D && y < box.maxY + CONTACT) {
                        candidate(from, new Node(box.minX - HALF - GAP, y, z, EAST), considered);
                        candidate(from, new Node(box.maxX + HALF + GAP, y, z, WEST), considered);
                        candidate(from, new Node(x, y, box.minZ - HALF - GAP, SOUTH), considered);
                        candidate(from, new Node(x, y, box.maxZ + HALF + GAP, NORTH), considered);
                    }
                }
            }
            for(int x = MathHelper.floor_double(from.x) - 1; x <= MathHelper.floor_double(from.x) + 1; x++)
                for(int z = MathHelper.floor_double(from.z) - 1; z <= MathHelper.floor_double(from.z) + 1; z++)
                    for(int y = MathHelper.floor_double(from.y) - 1; y <= MathHelper.floor_double(from.y + HEIGHT); y++) {
                        if(!world.blockExists(x, y, z) || !world.getBlock(x, y, z).isLadder(world, x, y, z, mob)) continue;
                        for(int dy = -1; dy <= 1; dy++) candidate(from, new Node(x + 0.5D, sy + dy * 0.5D, z + 0.5D, LADDER), considered);
                    }
        }
    }
    private static double square(double value) { return value * value; }

    /** Check the complete folded body and continuous contact, including the corner
     * between two faces. Axis-order alternatives carry it around exposed lips;
     * they cannot cut diagonally through the corner of a solid block. */
    private List<Node> link(Node from, Node to, Search cache) {
        if(segment(from, to, cache)) return Collections.singletonList(to);
        int[][] orders = {{1, 0, 2}, {1, 2, 0}, {0, 2, 1}, {2, 0, 1}, {0, 1, 2}, {2, 1, 0}};
        for(int[] order : orders) {
            List<Node> link = new ArrayList<Node>();
            Node previous = from;
            boolean valid = true;
            for(int axis : order) {
                Node next = new Node(axis == 0 ? to.x : previous.x, axis == 1 ? to.y : previous.y,
                        axis == 2 ? to.z : previous.z, previous.face);
                if(next.distance(previous.x, previous.y, previous.z) < 0.001D) continue;
                int side = support(next, cache);
                if(side < 0) { valid = false; break; }
                next = new Node(next.x, next.y, next.z, side);
                if(!segment(previous, next, cache)) { valid = false; break; }
                link.add(next); previous = next;
            }
            if(valid && !link.isEmpty()) {
                link.set(link.size() - 1, to); return link;
            }
        }
        return null;
    }
    private boolean segment(Node from, Node to, Search cache) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        AxisAlignedBB swept = body(from.x, from.y, from.z).addCoord(dx, dy, dz);
        if(!loaded(swept.expand(CONTACT, CONTACT, CONTACT))) return false;
        List<AxisAlignedBB> geometry = boxes(swept.expand(CONTACT, CONTACT, CONTACT), cache);
        // Exact swept-AABB intersection (Minkowski expansion), not point rays.
        Vec3 start = Vec3.createVectorHelper(from.x, from.y, from.z);
        Vec3 end = Vec3.createVectorHelper(to.x, to.y, to.z);
        for(AxisAlignedBB box : geometry) {
            AxisAlignedBB obstacle = AxisAlignedBB.getBoundingBox(box.minX - HALF + 0.001D, box.minY - HEIGHT + 0.001D,
                    box.minZ - HALF + 0.001D, box.maxX + HALF - 0.001D, box.maxY - 0.001D, box.maxZ + HALF - 0.001D);
            if(obstacle.isVecInside(start) || obstacle.isVecInside(end) || obstacle.calculateIntercept(start, end) != null) return false;
        }
        int samples = Math.max(1, MathHelper.ceiling_double_int(from.distance(to.x, to.y, to.z) / 0.12D));
        for(int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Node point = new Node(from.x + dx * t, from.y + dy * t, from.z + dz * t, from.face);
            boolean supported = false;
            for(int side = 0; side < LADDER && !supported; side++)
                supported = contactGeometry(new Node(point.x, point.y, point.z, side), geometry);
            if(!supported && !ladder(point) || world.isAnyLiquid(body(point.x, point.y, point.z))) return false;
        }
        return true;
    }
    private void searchTick() {
        // Stagger hunters, and cap total expansions in a world tick as well as per attempt.
        long slot = world.getTotalWorldTime() * 0x9E3779B97F4A7C15L ^ mob.getEntityId() * 0xBF58476D1CE4E5B9L;
        if(((slot ^ slot >>> 27) & 3) != 0) return;
        Budget budget = BUDGETS.get(world);
        if(budget == null) { budget = new Budget(); BUDGETS.put(world, budget); }
        if(budget.tick != world.getTotalWorldTime()) { budget.tick = world.getTotalWorldTime(); budget.left = 96; }
        int count = Math.min(16, budget.left); budget.left -= count;
        while(search != null && count-- > 0) {
            Node next = search.open.poll();
            if(next == null || search.expanded++ >= MAX_NODES || search.sites.size() > MAX_SITES) { fail(null); return; }
            if(next.closed) continue;
            next.closed = true;
            if(goal(next) || search.usefulFrontier(next)) {
                List<Node> chain = new ArrayList<Node>();
                for(Node node = next; node.parent != null; node = node.parent) chain.add(node);
                Collections.reverse(chain); route.clear();
                for(Node node : chain) route.addAll(node.link);
                step = 0; search = null; bestDistance = Double.MAX_VALUE; stalled = failedSearches = 0;
                if(route.isEmpty()) finish();
                return;
            }
            search.expand(next);
        }
    }

    boolean tick(double gx, double gy, double gz, boolean hasGoal, boolean allowed) {
        vx = vy = vz = 0;
        if((mob.isGrabbing() || mob.isProbing()) && attached && mob.isEntityAlive()
                && contact(new Node(mob.posX, mob.posY, mob.posZ, face), null)) {
            mob.setClimbing(face != GROUND); mob.setSurfaceFace(face);
            return true;
        }
        if(!allowed || mob.isInWater() || mob.handleLavaMovement()) { if(active()) clear(); return false; }
        if(active() && !returning && (huntingRoute && !mob.hasActiveHunt() || !hasGoal)) returnToGround();
        Node here = new Node(mob.posX, mob.posY, mob.posZ, face);
        if(attached && (!clearBody(here, null) || !contact(here, null))) {
            int nextFace = support(here, null);
            if(nextFace < 0 || !clearBody(here, null)) {
                fail(step < route.size() ? route.get(step) : null);
                attached = false; face = GROUND;
                mob.setSurfaceFace(GROUND); mob.setClimbing(false);
            }
            else face = nextFace;
        }
        boolean movedGoal = square(gx - goalX) + square(gy - goalY) + square(gz - goalZ) > 4;
        boolean useful = hasGoal && (mob.hasActiveHunt() ? mob.allowsHuntClimbing() : gy > mob.posY + 1);
        if((active() || useful) && movedGoal && !returning && mob.ticksExisted >= validateAt) {
            goalX = gx; goalY = gy; goalZ = gz;
            validateAt = mob.ticksExisted + 20;
            failedSearches = 0;
            search = null;
            if(!route.isEmpty() && !goal(route.get(route.size() - 1))) { route.clear(); step = 0; retryAt = 0; }
        }
        if(!active() && useful || attached && step >= route.size() && search == null) {
            if(attached && goal(here) && contact(new Node(here.x, here.y, here.z, GROUND), null)) {
                finish(); return false;
            }
            if(attached && goal(here) && !returning || mob.ticksExisted < retryAt) {
                mob.setSurfaceFace(attached ? face : GROUND); mob.setClimbing(attached && face != GROUND);
                return attached;
            }
            goalX = gx; goalY = gy; goalZ = gz;
            mob.setCrouching(true);
            here = new Node(mob.posX, mob.posY, mob.posZ, face);
            int initial = support(here, null);
            if(initial < 0 || !clearBody(here, null)) return false;
            face = initial; attached = true;
            huntingRoute = mob.hasActiveHunt();
            search = new Search();
            mob.getNavigator().clearPathEntity();
        }
        if(search != null) searchTick();
        if(step < route.size()) {
            Node next = route.get(step);
            double distance = next.distance(mob.posX, mob.posY, mob.posZ);
            // Advance by route distance, never by orientation changes or horizontal distance alone.
            // Corner links are axis-ordered for full-body clearance. Reaching a
            // lip a few centimetres early would cut the next leg through it.
            if(distance < 0.001D) {
                face = next.face; step++; bestDistance = Double.MAX_VALUE; stalled = 0;
                if(step == route.size()) {
                    if(face == GROUND) { finish(); return false; }
                    // Hold an ambush/attack position while it still reaches the remembered prey.
                    if(goal(next)) { route.clear(); step = 0; retryAt = mob.ticksExisted + 40; }
                    else { route.clear(); step = 0; retryAt = 0; }
                }
            } else {
                if(distance < bestDistance - 0.015D) { bestDistance = distance; stalled = 0; } else stalled++;
                if(stalled > 45 || !clearBody(next, null) || !contact(next, null)) fail(next);
                else {
                    double speed = Math.min(distance, face == GROUND ? 0.24D : 0.16D);
                    vx = (next.x - mob.posX) / distance * speed;
                    vy = (next.y - mob.posY) / distance * speed;
                    vz = (next.z - mob.posZ) / distance * speed;
                }
            }
        }
        if(active()) {
            mob.setCrouching(true); mob.getNavigator().clearPathEntity();
            attached = support(new Node(mob.posX, mob.posY, mob.posZ, face), null) >= 0;
            mob.setClimbing(face != GROUND); mob.setSurfaceFace(face);
            mob.motionX = vx; mob.motionY = vy; mob.motionZ = vz; mob.fallDistance = 0;
            // Wall pose keeps the body upright; ceiling yaw follows world motion unchanged.
            double dx = vx, dz = vz;
            if(face >= WEST && face <= SOUTH) { dx = face == WEST ? -1 : face == EAST ? 1 : 0; dz = face == NORTH ? -1 : face == SOUTH ? 1 : 0; }
            if(dx * dx + dz * dz > 0.0001D) {
                float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI - 90);
                mob.rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw - mob.rotationYaw), -20, 20);
                mob.renderYawOffset = mob.rotationYaw;
            }
        }
        return active();
    }
    void move(double x, double y, double z) {
        if(!active()) { mob.moveSurfaceBody(x, y, z); return; }
        if(mob.isGrabbing() || mob.isProbing()) vx = vy = vz = 0;
        Node from = new Node(mob.posX, mob.posY, mob.posZ, face);
        Node next = new Node(mob.posX + vx, mob.posY + vy, mob.posZ + vz, face);
        if(!segment(from, next, null) || mob.isHazardAt(next.x, next.y, next.z)) {
            if(step < route.size()) fail(route.get(step));
            vx = vy = vz = 0;
        }
        // Native collision resolution remains authoritative, including other entities.
        mob.moveSurfaceBody(vx, vy, vz); mob.fallDistance = 0;
    }
    void afterPhysics() {
        if(active()) { mob.motionX = vx; mob.motionY = vy; mob.motionZ = vz; mob.fallDistance = 0; }
    }
    void write(NBTTagCompound parent) {
        if(!active()) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 2); tag.setInteger("Face", face);
        tag.setDouble("GoalX", goalX); tag.setDouble("GoalY", goalY); tag.setDouble("GoalZ", goalZ);
        parent.setTag("WoodcrawlerSurface", tag);
    }
    void read(NBTTagCompound parent) {
        if(!parent.hasKey("WoodcrawlerSurface", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("WoodcrawlerSurface");
        // Old wall/ladder episodes safely reacquire real support; routes and prey are transient.
        face = tag.getInteger("Version") == 2 ? MathHelper.clamp_int(tag.getInteger("Face"), GROUND, LADDER) : GROUND;
        attached = tag.getInteger("Version") == 2 ? face != GROUND : tag.getInteger("Mode") != 0;
        goalX = tag.getDouble("GoalX"); goalY = tag.getDouble("GoalY"); goalZ = tag.getDouble("GoalZ");
        if(attached) {
            returning = true;
            mob.setCrouching(true); mob.setSurfaceFace(face); mob.setClimbing(face != GROUND);
        }
    }
}
