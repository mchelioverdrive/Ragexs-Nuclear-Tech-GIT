package com.hbm.entity.mob.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Incremental, local excavation routes. Live hunts and ordinary entrances remain owned by the controller. */
final class WoodcrawlerBreach {
    private final EntityWoodcrawler mob;
    private final World world;
    private final Map<String, Node> visited = new HashMap<String, Node>();
    private final Map<String, Double> terrain = new HashMap<String, Double>();
    private final Map<String, Long> rejected = new HashMap<String, Long>();
    private final PriorityQueue<Node> frontier = new PriorityQueue<Node>();
    private final List<Node> route = new ArrayList<Node>();
    private List<Vec3> claims = Collections.emptyList();
    private int startX, startY, startZ, expanded, index, retry, age, damage = -1;
    private int digX, digY, digZ, digMeta, digTicks, stuck;
    private double targetX, targetY, targetZ, sampleX, sampleY, sampleZ;
    private Block digging;
    private boolean planning, diverted;

    private static final class Node implements Comparable<Node> {
        final int x, y, z;
        final double cost, estimate;
        final Node parent;
        Node(int x, int y, int z, double cost, double estimate, Node parent) {
            this.x = x; this.y = y; this.z = z; this.cost = cost; this.estimate = estimate; this.parent = parent;
        }
        public int compareTo(Node other) { return Double.compare(cost + estimate, other.cost + other.estimate); }
        String key() { return x + ":" + y + ":" + z; }
    }

    WoodcrawlerBreach(EntityWoodcrawler mob) { this.mob = mob; world = mob.worldObj; }
    boolean active() { return planning || !route.isEmpty(); }
    void cooldown() { if(retry > 0) retry--; }
    Vec3 claim() {
        if(digging != null) return Vec3.createVectorHelper(digX + 0.5D, digY, digZ + 0.5D);
        for(int i = index; i < route.size(); i++) {
            Node n = route.get(i);
            if(cellCost(n.x, n.y, n.z) > 0 || cellCost(n.x, n.y + 1, n.z) > 0)
                return Vec3.createVectorHelper(n.x + 0.5D, n.y, n.z + 0.5D);
        }
        return null;
    }
    void clear() {
        clearDig(); planning = false; route.clear(); frontier.clear(); visited.clear(); terrain.clear(); claims = Collections.emptyList();
        age = stuck = index = 0;
    }
    private void failed() { clear(); retry = 100; }
    private void clearDig() {
        if(damage >= 0) world.destroyBlockInWorldPartially(mob.getEntityId(), digX, digY, digZ, -1);
        digging = null; digTicks = 0; damage = -1;
    }
    private double heuristic(int x, int y, int z) {
        return (Math.abs(x + 0.5D - targetX) + Math.abs(z + 0.5D - targetZ) + Math.abs(y - targetY)) * 4;
    }
    boolean start(double x, double y, double z, List<Vec3> occupied) {
        if(active() || retry > 0 || !mob.onGround || mob.getDistanceSq(x, y, z) > 400) return false;
        clear();
        diverted = false;
        targetX = x; targetY = y; targetZ = z; claims = occupied;
        startX = MathHelper.floor_double(mob.posX); startY = MathHelper.floor_double(mob.posY); startZ = MathHelper.floor_double(mob.posZ);
        Node first = new Node(startX, startY, startZ, 0, heuristic(startX, startY, startZ), null);
        frontier.add(first); visited.put(first.key(), first); expanded = 0; planning = true;
        sampleX = mob.posX; sampleY = mob.posY; sampleZ = mob.posZ;
        return true;
    }
    /** Empty cells cost nothing; mining is priced in ticks, so openings and soil beat hard walls. */
    private double cellCost(int x, int y, int z) {
        if(!planning) return inspectCell(x, y, z);
        String key = x + ":" + y + ":" + z;
        Double cached = terrain.get(key);
        if(cached != null) return cached;
        double cost = inspectCell(x, y, z); terrain.put(key, cost); return cost;
    }
    private double inspectCell(int x, int y, int z) {
        if(y < 1 || y > 253 || !world.blockExists(x, y, z)) return -1;
        Long until = rejected.get(x + ":" + y + ":" + z);
        if(until != null && until > world.getTotalWorldTime()) return -1;
        Block block = world.getBlock(x, y, z);
        if(block == Blocks.air) return 0;
        if(block.getMaterial().isLiquid() || block == Blocks.fire || block == Blocks.web || block == Blocks.portal) return -1;
        if(block.getCollisionBoundingBoxFromPool(world, x, y, z) == null) return 0;
        // Doors use the controller's protected two-half interaction instead of generic excavation.
        if(block instanceof BlockDoor) return -1;
        return mob.canBreachBlock(x, y, z) ? 30 + block.getBlockHardness(world, x, y, z) * 30 : -1;
    }
    private double columnCost(int x, int y, int z) {
        if(!world.checkChunksExist(x - 1, y - 1, z - 1, x + 1, y + 2, z + 1)
                || !world.getBlock(x, y - 1, z).isNormalCube() || mob.isHazardAt(x + 0.5D, y, z + 0.5D)) return -1;
        double low = cellCost(x, y, z), high = cellCost(x, y + 1, z);
        return low < 0 || high < 0 ? -1 : low + high;
    }
    private void expand() {
        for(int budget = 0; budget < 48 && !frontier.isEmpty() && expanded < 768; budget++) {
            Node node = frontier.poll();
            if(visited.get(node.key()) != node) continue;
            expanded++;
            if(Math.abs(node.x + 0.5D - targetX) < 1.5D && Math.abs(node.z + 0.5D - targetZ) < 1.5D
                    && Math.abs(node.y - targetY) < 1.1D) {
                for(Node n = node; n.parent != null; n = n.parent) route.add(n);
                Collections.reverse(route); planning = false; frontier.clear(); visited.clear(); terrain.clear();
                if(route.isEmpty()) failed();
                return;
            }
            for(int side = 0; side < 4; side++) for(int dy = -1; dy <= 1; dy++) {
                int x = node.x + (side == 0 ? -1 : side == 1 ? 1 : 0);
                int z = node.z + (side == 2 ? -1 : side == 3 ? 1 : 0), y = node.y + dy;
                if(Math.abs(x - startX) > 16 || Math.abs(z - startZ) > 16 || y < startY - 3 || y > startY + 6) continue;
                double cost = columnCost(x, y, z);
                // A step up also needs clearance over the current column.
                double head = dy > 0 ? cellCost(node.x, node.y + 2, node.z) : 0;
                if(cost < 0 || head < 0) continue;
                double crowd = 0;
                for(Vec3 claim : claims) if(claim.squareDistanceTo(x + 0.5D, y, z + 0.5D) < 4) crowd += 160;
                Node next = new Node(x, y, z, node.cost + 8 + Math.abs(dy) * 6 + cost + head + crowd, heuristic(x, y, z), node);
                Node old = visited.get(next.key());
                if(old == null || next.cost < old.cost) { visited.put(next.key(), next); frontier.add(next); }
            }
        }
        if(frontier.isEmpty() || expanded >= 768) failed();
    }
    /** One route step or one progressively damaged block per tick; never teleport through terrain. */
    boolean tick(double x, double y, double z, List<Vec3> occupied) {
        if(!active()) return false;
        if(square(x - targetX) + square(y - targetY) + square(z - targetZ) > 9 || ++age > 1200) { failed(); return false; }
        if(!planning && !diverted) {
            Vec3 entry = claim();
            for(Vec3 other : occupied) if(entry != null && entry.squareDistanceTo(other) < 4) {
                // One bounded reassignment covers routes chosen simultaneously; a sole useful route may still be shared.
                clear();
                boolean started = start(x, y, z, new ArrayList<Vec3>(occupied)); diverted = true;
                if(!started) return false;
                break;
            }
        }
        mob.getNavigator().clearPathEntity(); mob.setClimbing(false); mob.setCrouching(true);
        if(planning) {
            mob.getMoveHelper().setMoveTo(mob.posX, mob.posY, mob.posZ, 0);
            mob.setHuntState(EntityWoodcrawler.SEARCH); expand(); return active();
        }
        if(index >= route.size()) { clear(); retry = 40; return false; }
        Node n = route.get(index);
        if(mob.ticksExisted % 20 == 0) {
            double progress = mob.getDistanceSq(sampleX, sampleY, sampleZ);
            stuck = digging == null && progress < 0.04D ? stuck + 20 : 0;
            sampleX = mob.posX; sampleY = mob.posY; sampleZ = mob.posZ;
            if(stuck >= 100) { failed(); return false; }
        }
        if(mob.getDistanceSq(n.x + 0.5D, n.y, n.z + 0.5D) < 0.16D) { index++; clearDig(); return true; }
        if(columnCost(n.x, n.y, n.z) < 0) { failed(); return false; }
        int bx = n.x, by = n.y, bz = n.z;
        if(n.parent != null && n.y > n.parent.y && cellCost(n.parent.x, n.parent.y + 2, n.parent.z) > 0) {
            bx = n.parent.x; by = n.parent.y + 2; bz = n.parent.z;
        } else if(cellCost(bx, by, bz) == 0) by++;
        if(cellCost(bx, by, bz) > 0) return dig(bx, by, bz);
        clearDig(); mob.setHuntState(EntityWoodcrawler.BREACH);
        mob.getMoveHelper().setMoveTo(n.x + 0.5D, n.y, n.z + 0.5D, 1.5D);
        return true;
    }
    private boolean dig(int x, int y, int z) {
        if(mob.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 6.25D) { failed(); return false; }
        MovingObjectPosition hit = world.func_147447_a(Vec3.createVectorHelper(mob.posX, mob.posY + 1, mob.posZ),
                Vec3.createVectorHelper(x + 0.5D, y + 0.5D, z + 0.5D), false, true, false);
        if(hit != null && (hit.blockX != x || hit.blockY != y || hit.blockZ != z)) { failed(); return false; }
        Block block = world.getBlock(x, y, z); int meta = world.getBlockMetadata(x, y, z);
        if(digging != block || digMeta != meta || x != digX || y != digY || z != digZ) {
            clearDig(); digging = block; digMeta = meta; digX = x; digY = y; digZ = z;
        }
        mob.onDoorInteraction(); mob.setHuntState(EntityWoodcrawler.BANG);
        mob.getMoveHelper().setMoveTo(mob.posX, mob.posY, mob.posZ, 0);
        mob.getLookHelper().setLookPosition(x + 0.5D, y + 0.5D, z + 0.5D, 30, 30);
        int duration = Math.max(30, (int) (30 + block.getBlockHardness(world, x, y, z) * 30));
        int stage = Math.min(9, ++digTicks * 10 / duration);
        if(stage != damage) { damage = stage; world.destroyBlockInWorldPartially(mob.getEntityId(), x, y, z, stage); }
        if(digTicks % 12 == 0) world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) | meta << 12);
        if(digTicks >= duration) {
            if(!mob.breakBreachBlock(x, y, z)) {
                // Bound failed protection/changed-block memories and try another face on the next plan.
                if(rejected.size() >= 16) rejected.clear();
                rejected.put(x + ":" + y + ":" + z, world.getTotalWorldTime() + 600);
                failed(); return false;
            }
            clearDig(); mob.setHuntState(EntityWoodcrawler.BREACH); age = 0;
        }
        return true;
    }
    private static double square(double d) { return d * d; }
}
