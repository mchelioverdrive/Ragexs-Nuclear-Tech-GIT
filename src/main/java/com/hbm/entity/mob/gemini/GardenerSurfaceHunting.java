package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** The existing server tick controller owns hunting; vanilla movement is locked on this multipart mob. */
final class GardenerSurfaceHunting {
    private final EntityGardener mob;
    private final Map<UUID, Long> rejected = new HashMap<UUID, Long>();
    private final List<Vec3> recent = new ArrayList<Vec3>();
    private Entity remembered;
    private Vec3 lastSeen, roam;
    private long seenAt;
    private long progressAt;
    private double bestDistance;
    private int pause, pursuitFailures, recoveries;
    private boolean roaming, dispersing;
    private double exitX, exitZ, sampleX, sampleZ, displacement, fallSpeed;
    private long sampleAt, nextReport, measurementTicks;
    private final Map<Long, Node> nodes = new HashMap<Long, Node>();
    private final Map<Long, Boolean> clearance = new HashMap<Long, Boolean>();
    private final Map<Long, Long> denied = new HashMap<Long, Long>();
    private final PriorityQueue<Node> open = new PriorityQueue<Node>();
    private final List<Node> route = new ArrayList<Node>();
    private Vec3 goal, segment;
    private int step, visited, retry;
    private boolean searching, failed;
    private static final int LIMIT = 1024;
    // This mob already ignores fall damage. A raised nest apron must not become an
    // island: plan supported landings below it, then fall using native collisions.
    private static final int MAX_DROP = 64;

    GardenerSurfaceHunting(EntityGardener mob) { this.mob = mob; }
    boolean available(Entity prey) {
        Long until = rejected.get(prey.getUniqueID());
        return until == null || until <= mob.worldObj.getTotalWorldTime();
    }
    boolean remember(Entity prey) {
        if(prey == null) {
            if(remembered != null) { cancel("prey lost"); roam = null; }
            remembered = null; lastSeen = null; return false;
        }
        long now = mob.worldObj.getTotalWorldTime();
        if(prey != remembered) {
            remembered = prey; lastSeen = Vec3.createVectorHelper(prey.posX, prey.boundingBox.minY, prey.posZ);
            cancel("prey detected");
            seenAt = now; pursuitFailures = 0; roam = null; roaming = false;
            progressAt = now; bestDistance = mob.getDistanceSqToEntity(prey);
            mob.huntingDiagnostic("pursuit", "Detected prey " + prey.getEntityId() + "; starting pursuit");
        }
        if(mob.getEntitySenses().canSee(prey)) {
            seenAt = now; lastSeen = Vec3.createVectorHelper(prey.posX, prey.boundingBox.minY, prey.posZ);
        }
        double distance = mob.getDistanceSqToEntity(prey);
        if(distance < bestDistance - 1D) { bestDistance = distance; progressAt = now; }
        if(now - seenAt <= 200 && now - progressAt <= 600 && pursuitFailures < 4) return true;
        if(rejected.size() >= 32) rejected.clear();
        rejected.put(prey.getUniqueID(), now + 600);
        mob.huntingDiagnostic("abandon", "Abandoned unseen or unreachable prey " + prey.getEntityId());
        remembered = null; lastSeen = null; cancel("prey abandoned"); return false;
    }
    void stop() {
        stop("movement ownership changed");
    }
    void stop(String reason) {
        cancel(reason);
        remembered = null; lastSeen = roam = null; roaming = false; pause = 0; fallSpeed = 0;
    }
    void emerged() {
        stop("emergence completed"); dispersing = true; exitX = mob.posX; exitZ = mob.posZ; recoveries = 0;
        tick(null); // Request the first real route at the dismount, without relocating the body.
    }
    void tick(Entity prey) {
        if(prey != null && lastSeen != null) {
            if(mob.surfaceCaptureReach(prey)) { cancel("prey in capture reach"); settle(); return; }
            int result = travel(lastSeen.xCoord, lastSeen.yCoord, lastSeen.zCoord, 5D, 1.8D);
            if(result < 0) pursuitFailures++;
            // Reaching an unseen last position does not give omniscient tracking through walls.
            return;
        }
        if(!roaming) { roaming = true; mob.huntingDiagnostic("roaming", "Entering surface roaming"); }
        if(pause > 0) { pause--; settle(); return; }
        if(roam == null) {
            double homeDX = mob.posX - mob.huntingHomeX(), homeDZ = mob.posZ - mob.huntingHomeZ();
            double homeDistance = Math.sqrt(homeDX * homeDX + homeDZ * homeDZ);
            boolean outside = homeDistance > GeminiConfig.gardenerHuntingRadius;
            for(int attempt = 0; attempt < 12; attempt++) {
                double angle = mob.getRNG().nextDouble() * Math.PI * 2D + mob.getUniqueID().hashCode() * 2.399963D;
                double distance = dispersing ? 12D + mob.getRNG().nextDouble() * 20D : 16D + mob.getRNG().nextDouble() * 32D;
                double x = mob.posX + Math.cos(angle) * distance, z = mob.posZ + Math.sin(angle) * distance;
                // Occasionally head toward the existing settlement hint without orbiting it.
                if(outside) {
                    // A chase (or live radius reduction) can leave us farther out than
                    // one roaming leg. Walk inward in reachable legs instead of idling.
                    angle = Math.atan2(-homeDZ, -homeDX) + (mob.getRNG().nextDouble() - 0.5D) * 1.2D;
                    distance = Math.min(24D, homeDistance - GeminiConfig.gardenerHuntingRadius + 12D);
                    x = mob.posX + Math.cos(angle) * distance; z = mob.posZ + Math.sin(angle) * distance;
                } else if(attempt == 0 && mob.getRNG().nextInt(4) == 0) {
                    double dx = mob.huntingX() - mob.posX, dz = mob.huntingZ() - mob.posZ;
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if(length > 16) { x = mob.posX + dx / length * distance; z = mob.posZ + dz / length * distance; }
                }
                double dx = x - mob.huntingHomeX(), dz = z - mob.huntingHomeZ();
                double radius = outside ? homeDistance - 4D : GeminiConfig.gardenerHuntingRadius;
                if(dx * dx + dz * dz > radius * radius || dx * dx + dz * dz < 144D) continue;
                if(dispersing && (x - exitX) * (x - exitX) + (z - exitZ) * (z - exitZ) < 144D) continue;
                boolean repeated = false;
                for(Vec3 old : recent) if((old.xCoord - x) * (old.xCoord - x) + (old.zCoord - z) * (old.zCoord - z) < 100D) repeated = true;
                if(repeated) continue;
                int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
                if(!mob.worldObj.blockExists(bx, 0, bz)) continue;
                // Heightmap is just a starting point: search below foliage for actual body clearance.
                int top = Math.min(mob.worldObj.getTopSolidOrLiquidBlock(bx, bz), MathHelper.floor_double(mob.posY) + 12);
                for(int y = top; y >= Math.max(1, mob.posY - MAX_DROP); y--) if(supported(bx, y, bz, 2)
                        && !occupied(bx + 0.5D, y, bz + 0.5D)) {
                    roam = Vec3.createVectorHelper(bx + 0.5D, y, bz + 0.5D); break;
                }
                if(roam != null) {
                    recent.add(roam); if(recent.size() > 6) recent.remove(0);
                    report("destination", "Selected " + (dispersing ? "dispersal" : "roaming") + " destination", roam); break;
                }
            }
            if(roam == null) { report("no-destination", "No clear unoccupied landing; retrying", null); pause = 20; settle(); return; }
        }
        int result = travel(roam.xCoord, roam.yCoord, roam.zCoord, 1D, 1D);
        if(result != 0) {
            if(result > 0) { dispersing = false; recoveries = 0; }
            roam = null; pause = result > 0 ? mob.getRNG().nextInt(30) : Math.min(100, 10 + recoveries * 10);
        }
    }
    /** 1 arrived, 0 moving/planning, -1 failed. Long trips use bounded local route segments. */
    int travel(double x, double y, double z, double reach, double urgency) {
        if(retry > 0) { retry--; settle(); return 0; }
        if(failed) { failed = false; return -1; }
        Vec3 requested = Vec3.createVectorHelper(x, y, z);
        double dx = x - mob.posX, dz = z - mob.posZ;
        if(dx * dx + dz * dz <= reach * reach && Math.abs(y - mob.boundingBox.minY) <= 0.3D) {
            report("arrival", "Reached destination", requested); clearPath(); recoveries = 0; return 1;
        }
        if(goal == null || goal.squareDistanceTo(requested) > 16D) {
            cancel("destination changed"); goal = requested;
        }
        if(segment == null) {
            double distance = Math.sqrt(dx * dx + dz * dz), scale = Math.min(1D, 24D / distance);
            // Intermediate heights follow terrain, rather than an interpolated line
            // from an elevated exit to ground level that no surface node can reach.
            int sx = MathHelper.floor_double(mob.posX + dx * scale), sz = MathHelper.floor_double(mob.posZ + dz * scale);
            int sy = scale == 1D ? MathHelper.floor_double(y) : landingY(sx, sz);
            if(sy < 1) { fail(null, "no supported local landing"); return 0; }
            segment = Vec3.createVectorHelper(scale == 1D ? x : sx + 0.5D, sy, scale == 1D ? z : sz + 0.5D);
            Node start = new Node(MathHelper.floor_double(mob.posX), MathHelper.floor_double(mob.boundingBox.minY + 0.05D), MathHelper.floor_double(mob.posZ), null, 0, 0);
            nodes.put(start.key(), start); open.add(start); searching = true;
        }
        // Leave room for physical waypoint tolerance. Otherwise a node exactly at
        // arrival range can finish 0.04 blocks short and replan an empty route forever.
        if(searching) search(Math.max(0.1D, Math.min(reach - 0.15D, 3D)));
        if(searching || failed) { settle(); return 0; }
        if(step >= route.size()) {
            if(remembered != null && !route.isEmpty()) { progressAt = mob.worldObj.getTotalWorldTime(); pursuitFailures = 0; }
            // At the last horizontal point we may still be physically falling onto
            // the planned landing. Preserve the goal until native collision settles it.
            if(dx * dx + dz * dz <= reach * reach) {
                settle(); measure(null); return 0;
            }
            clearPath(); return 0;
        }
        Node next = route.get(step);
        double nx = next.x + 0.5D - mob.posX, nz = next.z + 0.5D - mob.posZ;
        double distance = Math.sqrt(nx * nx + nz * nz);
        // The trailing part of a wide body can still rest on a ledge after its
        // center has crossed a descending node. Continue horizontally off that ledge.
        if(distance < 0.06D && next.y <= mob.boundingBox.minY + 1.01D) { step++; settle(); return 0; }
        double speed = Math.min(distance, mob.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue() * urgency);
        double mx = distance > 0 ? nx / distance * speed : 0, mz = distance > 0 ? nz / distance * speed : 0;
        AxisAlignedBB sweep = mob.boundingBox.addCoord(mx, 0, mz).addCoord(0, mob.stepHeight, 0).contract(0.002D, 0.002D, 0.002D);
        // The route and execution use the same actual body, not the cave steering
        // support probes or decorative limb envelope. Native movement resolves solids/steps.
        if(!com.hbm.world.gemini.GardenerNests.loaded(mob.worldObj, sweep.expand(19, 2, 19))
                || mob.worldObj.isAnyLiquid(sweep) || !separating(mob.posX + mx, mob.posY, mob.posZ + mz)) {
            fail(next, "unloaded, liquid or occupied movement edge"); return 0;
        }
        fallSpeed = mob.onGround ? 0.2D : Math.min(1D, fallSpeed + 0.08D);
        mob.moveGardener(mx, -fallSpeed, mz);
        if(mob.worldObj.getTotalWorldTime() % 20 == 0 && !standable(next.x, next.y, next.z)) fail(next, "landing changed");
        else measure(next);
        return 0;
    }
    private void clearPath() {
        nodes.clear(); open.clear(); clearance.clear(); route.clear();
        goal = segment = null; searching = failed = false; step = visited = retry = 0; sampleAt = 0;
    }
    private void cancel(String reason) {
        if(goal != null) report("cancel", "Navigation canceled: " + reason, goal);
        clearPath();
    }
    private void fail(Node node, String reason) {
        recoveries = Math.min(8, recoveries + 1);
        report("navigation", "Navigation failed: " + reason + "; recovery=" + recoveries, goal);
        if(node != null) { if(denied.size() >= 64) denied.clear(); denied.put(node.key(), mob.worldObj.getTotalWorldTime() + 200); }
        clearPath(); failed = true; retry = Math.min(100, recoveries * 10);
    }
    private AxisAlignedBB body(int x, int y, int z) {
        double half = mob.width / 2D - 0.002D;
        return AxisAlignedBB.getBoundingBox(x + 0.5D - half, y + 0.002D, z + 0.5D - half,
                x + 0.5D + half, y + mob.height - 0.002D, z + 0.5D + half);
    }
    private boolean clear(AxisAlignedBB box) {
        return com.hbm.world.gemini.GardenerNests.loaded(mob.worldObj, box.expand(19, 2, 19))
                && !mob.worldObj.isAnyLiquid(box) && mob.worldObj.func_147461_a(box).isEmpty();
    }
    private boolean standable(int x, int y, int z) {
        return supported(x, y, z, MAX_DROP);
    }
    private boolean supported(int x, int y, int z, int drop) {
        if(y < 1 || y + mob.height >= mob.worldObj.getHeight()) return false;
        Long blocked = denied.get(key(x, y, z));
        if(blocked != null && blocked > mob.worldObj.getTotalWorldTime()) return false;
        AxisAlignedBB box = body(x, y, z);
        if(!com.hbm.world.gemini.GardenerNests.loaded(mob.worldObj, box.expand(19, 2, 19))) return false;
        int contacts = 0;
        for(int corner = 0; corner < 4; corner++) {
            int bx = MathHelper.floor_double(corner % 2 == 0 ? box.minX : box.maxX);
            int bz = MathHelper.floor_double(corner < 2 ? box.minZ : box.maxZ);
            if(mob.worldObj.getBlock(bx, y - 1, bz).getMaterial().blocksMovement()) contacts |= 1 << corner;
        }
        if(contacts == 0) return false;
        for(int corner = 0; corner < 4; corner++) {
            // Use the real footprint: the 5.8-block body can contact a step before an
            // inset probe reaches it. Insetting leaves an unwalkable gap in the grid.
            int bx = MathHelper.floor_double(corner % 2 == 0 ? box.minX : box.maxX);
            int bz = MathHelper.floor_double(corner < 2 ? box.minZ : box.maxZ);
            boolean floor = (contacts & 1 << corner) != 0;
            int depth = 1;
            while(!floor && depth < drop && y - depth > 1) {
                depth++; floor = mob.worldObj.getBlock(bx, y - depth, bz).getMaterial().blocksMovement();
            }
            if(!floor) return false;
        }
        return clear(box);
    }
    private int landingY(int x, int z) {
        if(!mob.worldObj.blockExists(x, 0, z)) return -1;
        int top = Math.min(mob.worldObj.getTopSolidOrLiquidBlock(x, z), MathHelper.floor_double(mob.boundingBox.minY) + 12);
        for(int y = top; y >= Math.max(1, mob.boundingBox.minY - MAX_DROP); y--)
            if(supported(x, y, z, 2)) return y;
        return -1;
    }
    private boolean occupied(double x, double y, double z) {
        AxisAlignedBB box = body(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, box.expand(50, 16, 50))) {
            EntityGardener other = (EntityGardener) entry;
            if(other == mob || other.isOcean() || !other.isEntityAlive()) continue;
            if(other.boundingBox.intersectsWith(box.expand(2, 0, 2))) return true;
            Vec3 reserved = other.surfaceHuntingDestination();
            if(reserved != null && Math.abs(reserved.yCoord - y) < mob.height
                    && (reserved.xCoord - x) * (reserved.xCoord - x) + (reserved.zCoord - z) * (reserved.zCoord - z) < 100D) return true;
        }
        return false;
    }
    Vec3 destination() { return roam != null ? roam : goal; }
    private boolean separating(double x, double y, double z) {
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, mob.boundingBox.expand(8, 2, 8))) {
            EntityGardener other = (EntityGardener) entry;
            if(other == mob || other.isOcean() || !other.isEntityAlive() || Math.abs(other.posY - y) >= mob.height) continue;
            double before = (mob.posX - other.posX) * (mob.posX - other.posX) + (mob.posZ - other.posZ) * (mob.posZ - other.posZ);
            double after = (x - other.posX) * (x - other.posX) + (z - other.posZ) * (z - other.posZ);
            double spacing = (mob.width + other.width) / 2D + 1D;
            if(after < spacing * spacing && after < before - 0.000001D) return false;
        }
        return true;
    }
    private void settle() {
        fallSpeed = mob.onGround ? 0.08D : Math.min(1D, fallSpeed + 0.08D);
        mob.moveGardener(0, -fallSpeed, 0);
    }
    private void measure(Node next) {
        long now = mob.worldObj.getTotalWorldTime();
        if(sampleAt == 0) { sampleAt = now; sampleX = mob.posX; sampleZ = mob.posZ; return; }
        if(now - sampleAt < 40) return;
        measurementTicks = now - sampleAt;
        displacement = Math.sqrt((mob.posX - sampleX) * (mob.posX - sampleX) + (mob.posZ - sampleZ) * (mob.posZ - sampleZ));
        // Vertical falling is intentional; it must not be diagnosed as horizontal progress.
        boolean falling = !mob.onGround && goal != null && mob.boundingBox.minY > goal.yCoord + 1D;
        if(displacement < 0.5D && !falling) { fail(next, "horizontal progress=" + displacement + " over " + (now - sampleAt) + " ticks"); return; }
        recoveries = 0;
        if(now >= nextReport) { report("movement", falling ? "Descending to landing" : "Following surface path", goal); nextReport = now + 200; }
        sampleAt = now; sampleX = mob.posX; sampleZ = mob.posZ;
    }
    private void report(String event, String message, Vec3 destination) {
        double distance = destination == null ? 0 : Math.sqrt(mob.getDistanceSq(destination.xCoord, destination.yCoord, destination.zCoord));
        mob.huntingDiagnostic(event, message + "; ai=" + mob.surfaceMovementState() + "/" + (remembered != null ? "pursuit" : dispersing ? "dispersal" : "roaming")
                + "; action=" + mob.state() + "; position=" + mob.posX + "," + mob.posY + "," + mob.posZ
                + "; destination=" + destination + "; distance=" + distance + "; path=" + !route.isEmpty()
                + "; following=" + (!searching && step < route.size()) + "; planning=" + searching
                + "; waypoint=" + step + "/" + route.size() + "; horizontalDisplacement=" + displacement
                + "; measurementTicks=" + measurementTicks
                + "; speed=" + mob.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue()
                + "; recovery=" + recoveries);
    }
    private boolean walkable(int x, int y, int z) {
        double dx = x + 0.5D - mob.huntingHomeX(), dz = z + 0.5D - mob.huntingHomeZ();
        // Roaming endpoints stay inside the configured radius. Detours and the trip home
        // must also work after a pursuit has taken the body beyond that circle.
        double homeDX = mob.posX - mob.huntingHomeX(), homeDZ = mob.posZ - mob.huntingHomeZ();
        double radius = Math.max(GeminiConfig.gardenerHuntingRadius + 48D, Math.sqrt(homeDX * homeDX + homeDZ * homeDZ) + 2D);
        if(dx * dx + dz * dz > radius * radius) return false;
        long key = key(x, y, z);
        Boolean cached = clearance.get(key);
        if(cached == null) {
            cached = standable(x, y, z) && separating(x + 0.5D, y, z + 0.5D);
            if(clearance.size() < LIMIT * 8) clearance.put(key, cached);
        }
        return cached;
    }
    private void search(double reach) {
        for(int budget = 0; budget < 8 && !open.isEmpty() && visited < LIMIT; budget++) {
            Node current = open.poll();
            if(nodes.get(current.key()) != current || current.closed) continue;
            current.closed = true; visited++;
            double dx = current.x + 0.5D - segment.xCoord, dz = current.z + 0.5D - segment.zCoord;
            if(dx * dx + dz * dz <= reach * reach && Math.abs(current.y - segment.yCoord) <= 0.5D) {
                for(Node n = current; n.parent != null; n = n.parent) route.add(n);
                Collections.reverse(route); searching = false; nodes.clear(); open.clear(); clearance.clear();
                report("path", "Surface path ready", goal); return;
            }
            for(int side = 0; side < 4; side++) {
                int x = current.x + (side == 0 ? 1 : side == 1 ? -1 : 0), z = current.z + (side == 2 ? 1 : side == 3 ? -1 : 0);
                if(Math.abs(x + 0.5D - mob.posX) > 32 || Math.abs(z + 0.5D - mob.posZ) > 32) continue;
                for(int level = 0; level <= MAX_DROP + 1; level++) {
                    int y = current.y + (level == 0 ? 0 : level == 1 ? 1 : 1 - level);
                    if(y < 1) break;
                    if(!walkable(x, y, z)) continue;
                    // Check the whole edge, including headroom for native one-block stepping.
                    int high = Math.max(y, current.y);
                    AxisAlignedBB sweep = body(current.x, high, current.z).addCoord(x - current.x, 0, z - current.z);
                    if(!clear(sweep)) continue;
                    // Falling is allowed only after the complete collision body has
                    // cleared the rim, with a solid-free, dry column down to the landing.
                    if(y < current.y && !clear(body(x, y, z).addCoord(0, current.y - y, 0))) continue;
                    int cost = current.cost + 10 + Math.abs(y - current.y) * 4;
                    Node old = nodes.get(key(x, y, z));
                    if(old != null && old.cost <= cost || nodes.size() >= LIMIT || open.size() >= LIMIT * 2) break;
                    double heuristic = 10D * (Math.abs(x + 0.5D - segment.xCoord) + Math.abs(z + 0.5D - segment.zCoord))
                            + 4D * Math.abs(y - segment.yCoord);
                    Node next = new Node(x, y, z, current, cost, cost + heuristic);
                    nodes.put(next.key(), next); open.add(next); break;
                }
            }
        }
        if(open.isEmpty() || visited >= LIMIT) fail(null, "no route after " + visited + " expanded nodes");
    }
    private static long key(int x, int y, int z) { return ((long) x & 0x3ffffffL) << 34 | ((long) z & 0x3ffffffL) << 8 | y & 255L; }
    private static final class Node implements Comparable<Node> {
        final int x, y, z, cost;
        final double score;
        final Node parent;
        boolean closed;
        Node(int x, int y, int z, Node parent, int cost, double score) {
            this.x = x; this.y = y; this.z = z; this.parent = parent; this.cost = cost; this.score = score;
        }
        long key() { return GardenerSurfaceHunting.key(x, y, z); }
        @Override public int compareTo(Node other) { return Double.compare(score, other.score); }
    }
}
