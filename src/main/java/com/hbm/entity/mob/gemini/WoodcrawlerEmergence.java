package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.WretchCamps;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Loaded-only hatchling tunneling. All motion uses native collision resolution, never noClip. */
final class WoodcrawlerEmergence {
    private static final int NONE = 0, SEARCH = 1, TRAVEL = 2, RISE = 3;
    private static final int RADIUS = 16, DIAMETER = RADIUS * 2 + 1, NODE_LIMIT = 4096;
    private final EntityWoodcrawler mob;
    private final World world;
    private boolean undergroundOrigin;
    private int state, retry, failures, grace, column, originX, originY, originZ, candidateIndex, visited, step, digTicks;
    private int digX, digY, digZ;
    private Block digging;
    private Node destination;
    private final List<Node> exits = new ArrayList<Node>();
    private final List<Node> route = new ArrayList<Node>();
    private final Map<Long, Node> nodes = new HashMap<Long, Node>();
    private final Map<Long, Boolean> columns = new HashMap<Long, Boolean>();
    private final Map<Long, Long> denied = new HashMap<Long, Long>();
    private final PriorityQueue<Node> open = new PriorityQueue<Node>(64, Comparator.comparingDouble(n -> n.score));

    WoodcrawlerEmergence(EntityWoodcrawler mob) { this.mob = mob; world = mob.worldObj; }
    boolean active() { return state != NONE; }
    boolean initialHunt() { return grace > 0; }

    /** Require geological cover, rather than treating tree canopy as underground terrain. */
    static boolean underground(World world, double px, double py, double pz) {
        int x = MathHelper.floor_double(px), z = MathHelper.floor_double(pz);
        if(world.provider.hasNoSky || !world.blockExists(x, 0, z)) return false;
        int top = world.getTopSolidOrLiquidBlock(x, z);
        for(int y = MathHelper.floor_double(py) + 2; y < top; y++) {
            Block block = world.getBlock(x, y, z);
            if(natural(block) && block != com.hbm.blocks.ModBlocks.gardener_flesh
                    && !block.hasTileEntity(world.getBlockMetadata(x, y, z)) && world.getTileEntity(x, y, z) == null
                    && !WretchCamps.hasPlayerPlacement(world, AxisAlignedBB.getBoundingBox(x, y, z, x, y + 1, z))) return true;
        }
        return false;
    }

    void begin() {
        undergroundOrigin = true; state = SEARCH;
        mob.setEmergenceBurrowTick(71);
        mob.burrowDepth = mob.prevBurrowDepth = 1;
        mob.setCrouching(true);
    }

    static boolean natural(Block block) {
        return block == Blocks.stone || block == Blocks.dirt || block == Blocks.grass || block == Blocks.mycelium
                || block == Blocks.sand || block == Blocks.gravel || block == Blocks.clay || block == Blocks.sandstone
                || block == Blocks.coal_ore || block == Blocks.iron_ore || block == Blocks.gold_ore
                || block == Blocks.redstone_ore || block == Blocks.lit_redstone_ore || block == Blocks.lapis_ore
                || block == Blocks.diamond_ore || block == Blocks.emerald_ore || block == com.hbm.blocks.ModBlocks.gardener_flesh;
    }

    private AxisAlignedBB body(double x, double y, double z, double height) {
        double half = height > EntityWoodcrawler.CRAWLING_HEIGHT ? EntityWoodcrawler.BODY_WIDTH / 2D : EntityWoodcrawler.CRAWLING_WIDTH / 2D;
        return AxisAlignedBB.getBoundingBox(x - half, y + 0.001D, z - half, x + half, y + height - 0.001D, z + half);
    }
    private boolean protectedColumn(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Boolean protectedArea = columns.get(key);
        if(protectedArea == null) {
            AxisAlignedBB area = AxisAlignedBB.getBoundingBox(x, 1, z, x + 1, world.getHeight() - 1, z + 1);
            protectedArea = WretchCamps.hasPlayerPlacement(world, area) || GardenerNests.overlapsStructure(world, area);
            columns.put(key, protectedArea);
        }
        return protectedArea;
    }
    private boolean exit(Node node) {
        AxisAlignedBB box = body(node.x + 0.5D, node.y, node.z + 0.5D, EntityWoodcrawler.STANDING_HEIGHT);
        if(!GardenerNests.loaded(world, box.expand(1, 1, 1)) || protectedColumn(node.x, node.z)
                || !natural(world.getBlock(node.x, node.y - 1, node.z))
                || !World.doesBlockHaveSolidTopSurface(world, node.x, node.y - 1, node.z)
                || !world.canBlockSeeTheSky(node.x, node.y, node.z)
                || !world.getCollidingBoundingBoxes(mob, box).isEmpty() || world.isAnyLiquid(box)) return false;
        for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++)
            if(mob.isHazardAt(node.x + dx + 0.5D, node.y, node.z + dz + 0.5D)) return false;
        // A heightmap descends to the cave floor after a shaft is excavated. Require
        // an outdoor landing around the exit, rather than resurfacing at the bottom of that shaft.
        int openSides = 0;
        for(int side = 0; side < 4; side++) {
            int x = node.x + (side == 0 ? 1 : side == 1 ? -1 : 0);
            int z = node.z + (side == 2 ? 1 : side == 3 ? -1 : 0);
            if(world.canBlockSeeTheSky(x, node.y, z) && Math.abs(world.getTopSolidOrLiquidBlock(x, z) - node.y) <= 1) openSides++;
        }
        if(openSides < 2) return false;
        // Include boats, vehicles and other collidable entities, not just spawn-preventing mobs.
        return unoccupied(box);
    }
    private boolean unoccupied(AxisAlignedBB box) {
        for(Object entry : world.getEntitiesWithinAABBExcludingEntity(mob, box)) {
            net.minecraft.entity.Entity other = (net.minecraft.entity.Entity) entry;
            if(!other.isDead && (other.canBeCollidedWith() || other.preventEntitySpawning)) return false;
        }
        return true;
    }
    private int cost(int x, int y, int z) {
        if(y < Math.max(2, originY - 4) || y > Math.min(world.getHeight() - 7, originY + 128)
                || Math.abs(x - originX) > RADIUS || Math.abs(z - originZ) > RADIUS
                || !world.checkChunksExist(x - 1, y - 1, z - 1, x + 1, y + 3, z + 1) || protectedColumn(x, z)) return -1;
        Long until = denied.get(key(x, y, z));
        if(until != null && until > world.getTotalWorldTime()) return -1;
        // Keep the selected exit's floor intact; approach its ledge from the side.
        if(destination != null && x == destination.x && z == destination.z && y < destination.y
                && !(x == originX && y == originY && z == originZ)) return -1;
        int cost = 1;
        for(int dy = 0; dy < 2; dy++) {
            Block block = world.getBlock(x, y + dy, z);
            if(world.getTileEntity(x, y + dy, z) != null || block.hasTileEntity(world.getBlockMetadata(x, y + dy, z))) return -1;
            if(world.isAirBlock(x, y + dy, z)) continue;
            if(!natural(block) || !mob.canExcavateForEmergence(x, y + dy, z)) return -1;
            cost += 3; // Prefer existing passages over excavation when their detour is modest.
        }
        for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) for(int dy = -1; dy <= 2; dy++) {
            Block block = world.getBlock(x + dx, y + dy, z + dz);
            if(block.getMaterial().isLiquid() || block == Blocks.fire || block == Blocks.cactus) return -1;
        }
        return cost;
    }

    void tick() {
        if(grace > 0) grace--;
        if(!active() || !mob.isEntityAlive()) return;
        mob.getNavigator().clearPathEntity(); mob.setClimbing(false);
        mob.motionX = mob.motionY = mob.motionZ = 0; mob.fallDistance = 0;
        if(mob.ticksExisted % 100 == 0) columns.clear();
        if(state == RISE) {
            if(!exit(destination)) { waitAndRetry(); return; }
            int tick = mob.getBurrowTick();
            if(tick >= 60) {
                state = NONE; grace = Math.max(200, GeminiConfig.woodcrawlerStalkSeconds * 20 + 40); failures = 0;
                clearSearch(); mob.finishCocoonEmergence();
            } else mob.setEmergenceBurrowTick(tick + 1);
            return;
        }
        if(retry > 0) { retry--; return; }
        if(state == SEARCH) search();
        else travel();
    }
    private void clearSearch() {
        exits.clear(); route.clear(); nodes.clear(); open.clear(); columns.clear(); destination = null;
        column = candidateIndex = visited = step = 0; digging = null; digTicks = 0;
    }
    private void waitAndRetry() {
        clearSearch(); state = SEARCH;
        retry = Math.min(1200, 100 << Math.min(4, failures++)) + mob.getRNG().nextInt(100);
        mob.setEmergenceBurrowTick(71); mob.setCrouching(true);
    }
    private void search() {
        if(column == 0) {
            originX = MathHelper.floor_double(mob.posX); originY = MathHelper.floor_double(mob.posY); originZ = MathHelper.floor_double(mob.posZ);
        }
        // At most 16 loaded heightmap columns per tick; no full-height world scans.
        for(int budget = 0; budget < 16 && column < DIAMETER * DIAMETER; budget++, column++) {
            int x = originX + column % DIAMETER - RADIUS, z = originZ + column / DIAMETER - RADIUS;
            if(!world.blockExists(x, 0, z)) continue;
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if(y < originY || y > originY + 128 || y > world.getHeight() - 7) continue;
            Node node = new Node(x, y, z, null, 0, 0);
            if(exit(node)) exits.add(node);
        }
        if(column < DIAMETER * DIAMETER) return;
        if(destination == null) {
            Collections.sort(exits, Comparator.comparingDouble(n -> mob.getDistanceSq(n.x + 0.5D, n.y, n.z + 0.5D)));
            if(candidateIndex >= Math.min(8, exits.size())) { waitAndRetry(); return; }
            destination = exits.get(candidateIndex++);
            Node start = new Node(originX, originY, originZ, null, 0, heuristic(originX, originY, originZ));
            nodes.put(start.key(), start); open.add(start); visited = 0;
        }
        // Directed A* runs incrementally, with a hard node/attempt limit and delayed retries.
        for(int budget = 0; budget < 48 && !open.isEmpty() && visited < NODE_LIMIT; budget++) {
            Node current = open.poll();
            if(nodes.get(current.key()) != current) continue;
            visited++;
            if(current.x == destination.x && current.y == destination.y && current.z == destination.z) {
                for(Node node = current; node != null; node = node.parent) route.add(node);
                Collections.reverse(route); state = TRAVEL; step = 0; nodes.clear(); open.clear(); return;
            }
            for(int side = 0; side < 6; side++) {
                int x = current.x + (side == 0 ? 1 : side == 1 ? -1 : 0);
                int y = current.y + (side == 2 ? 1 : side == 3 ? -1 : 0);
                int z = current.z + (side == 4 ? 1 : side == 5 ? -1 : 0);
                int cost = cost(x, y, z);
                if(cost < 0) continue;
                Node next = new Node(x, y, z, current, current.cost + cost, current.cost + cost + heuristic(x, y, z));
                Node old = nodes.get(next.key());
                if(old != null && old.cost <= next.cost) continue;
                if(nodes.size() >= NODE_LIMIT) continue;
                nodes.put(next.key(), next); open.add(next);
            }
        }
        if(open.isEmpty() || visited >= NODE_LIMIT) { destination = null; nodes.clear(); open.clear(); }
    }
    private double heuristic(int x, int y, int z) {
        // Excavatable cells cost up to seven. A unit heuristic floods deep rock sideways
        // before climbing; this bounded, non-optimal search deliberately favors progress to the exit.
        return 6D * (Math.abs(x - destination.x) + Math.abs(y - destination.y) + Math.abs(z - destination.z));
    }
    private void travel() {
        if(destination == null || !exit(destination) || step >= route.size()) { waitAndRetry(); return; }
        Node next = route.get(step);
        if(cost(next.x, next.y, next.z) < 0) { waitAndRetry(); return; }
        double dx = next.x + 0.5D - mob.posX, dy = next.y - mob.posY, dz = next.z + 0.5D - mob.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(distance < 0.015D) {
            if(++step < route.size()) return;
            // The same live entity unfolds at its verified exit, using the existing rise timeline.
            if(!exit(destination)) { waitAndRetry(); return; }
            mob.setCrouching(false); state = RISE; mob.setEmergenceBurrowTick(41); return;
        }
        double speed = Math.min(0.1D, distance);
        double mx = dx / distance * speed, my = dy / distance * speed, mz = dz / distance * speed;
        AxisAlignedBB swept = mob.boundingBox.addCoord(mx, my, mz).contract(0.001D, 0.001D, 0.001D);
        if(!GardenerNests.loaded(world, swept.expand(1, 1, 1)) || !unoccupied(swept) || world.isAnyLiquid(swept)) { waitAndRetry(); return; }
        for(int x = MathHelper.floor_double(swept.minX); x <= MathHelper.floor_double(swept.maxX); x++)
            for(int z = MathHelper.floor_double(swept.minZ); z <= MathHelper.floor_double(swept.maxZ); z++)
                for(int y = MathHelper.floor_double(swept.minY); y <= MathHelper.floor_double(swept.maxY); y++) {
                    if(world.isAirBlock(x, y, z)) continue;
                    if(!natural(world.getBlock(x, y, z)) || !mob.canExcavateForEmergence(x, y, z)) { waitAndRetry(); return; }
                    Block block = world.getBlock(x, y, z);
                    if(digging != block || digX != x || digY != y || digZ != z) {
                        digging = block; digX = x; digY = y; digZ = z; digTicks = 0;
                    }
                    if(++digTicks >= 20 + block.getBlockHardness(world, x, y, z) * 20) {
                        if(!mob.excavateForEmergence(x, y, z)) {
                            // A claim veto should lead to another route, not repeat the same dig forever.
                            if(denied.size() >= 64) denied.clear();
                            long until = world.getTotalWorldTime() + 1200;
                            denied.put(key(x, y, z), until); denied.put(key(x, y - 1, z), until);
                            waitAndRetry(); return;
                        }
                        digging = null; digTicks = 0;
                    }
                    return; // One dig operation per tick; collision remains authoritative while waiting.
                }
        if(!world.getCollidingBoundingBoxes(mob, swept).isEmpty()) { waitAndRetry(); return; }
        double beforeX = mob.posX, beforeY = mob.posY, beforeZ = mob.posZ;
        mob.moveSurfaceBody(mx, my, mz); mob.fallDistance = 0;
        if(mob.getDistanceSq(beforeX, beforeY, beforeZ) < speed * speed * 0.25D) waitAndRetry();
    }
    private static long key(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 34 | ((long) z & 0x3ffffffL) << 8 | y & 255L;
    }

    void write(NBTTagCompound parent) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("UndergroundOrigin", undergroundOrigin); tag.setInteger("State", state);
        tag.setInteger("Retry", retry); tag.setInteger("Failures", failures); tag.setInteger("HuntGrace", grace);
        if(destination != null) { tag.setInteger("X", destination.x); tag.setInteger("Y", destination.y); tag.setInteger("Z", destination.z); }
        parent.setTag("WoodcrawlerEmergence", tag);
    }
    void read(NBTTagCompound parent) {
        if(!parent.hasKey("WoodcrawlerEmergence", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("WoodcrawlerEmergence");
        undergroundOrigin = tag.getBoolean("UndergroundOrigin");
        state = MathHelper.clamp_int(tag.getInteger("State"), NONE, RISE);
        if(!undergroundOrigin) state = NONE;
        grace = MathHelper.clamp_int(tag.getInteger("HuntGrace"), 0, 640);
        failures = MathHelper.clamp_int(tag.getInteger("Failures"), 0, 16);
        retry = MathHelper.clamp_int(tag.getInteger("Retry"), 0, 1300);
        if(!active()) return;
        // Do not persist graph references or live paths. Revalidate/replan from the saved body position.
        if(state == RISE && tag.hasKey("Y")) {
            destination = new Node(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"), null, 0, 0);
            if(mob.getDistanceSq(destination.x + 0.5D, destination.y, destination.z + 0.5D) > 0.01D) destination = null;
        }
        if(destination == null) { state = SEARCH; mob.setCrouching(true); }
        mob.setEmergenceBurrowTick(state == RISE ? MathHelper.clamp_int(parent.getInteger("WoodcrawlerBurrowTick"), 41, 60) : 71);
        mob.burrowDepth = mob.prevBurrowDepth = state == RISE ? (60 - mob.getBurrowTick()) / 20F : 1;
    }
    private static final class Node {
        final int x, y, z, cost;
        final Node parent;
        final double score;
        Node(int x, int y, int z, Node parent, int cost, double score) {
            this.x = x; this.y = y; this.z = z; this.parent = parent; this.cost = cost; this.score = score;
        }
        long key() { return WoodcrawlerEmergence.key(x, y, z); }
    }
}
