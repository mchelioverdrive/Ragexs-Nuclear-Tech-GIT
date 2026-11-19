package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** The existing server tick controller owns hunting; vanilla movement is locked on this multipart mob. */
final class GardenerSurfaceHunting {
    private final EntityGardener mob;
    private final Map<UUID, Long> rejected = new HashMap<UUID, Long>();
    private final List<Vec3> recent = new ArrayList<Vec3>();
    private Entity remembered;
    private Vec3 lastSeen, roam, investigation;
    private long seenAt;
    private long progressAt;
    private double bestDistance;
    private int pause, pursuitFailures, recoveries;
    private boolean roaming, dispersing, scouting, visible, committed;
    private double exitX, exitZ, sampleX, sampleZ, displacement, fallSpeed;
    private double sampleY, verticalDisplacement;
    private long sampleAt, nextReport, measurementTicks, retryUntil;
    private final Map<Long, Node> nodes = new HashMap<Long, Node>();
    private final Map<Long, Long> denied = new HashMap<Long, Long>();
    private final Map<Long, TreeSet<Double>> floors = new HashMap<Long, TreeSet<Double>>();
    private final PriorityQueue<Node> open = new PriorityQueue<Node>();
    private final List<Node> route = new ArrayList<Node>();
    private Vec3 goal;
    private Node segment, frontier;
    private double frontierDistance;
    private final List<EntityGardener> neighbors = new ArrayList<EntityGardener>();
    private int step, visited, blockedTicks;
    private boolean searching, failed, finalSegment, preciseRoute;
    private String landingResult = "not checked", pathResult = "idle";
    private static final int LIMIT = 768, SEGMENT_LENGTH = 18;
    private static final int PREY_MEMORY = 160, STALL_TICKS = 60, MAX_PAUSE = 40;
    private static final double DISPERSAL_MIN = 16D, DISPERSAL_MAX = 40D, ROAM_MIN = 24D, ROAM_MAX = 64D;
    private static final double MAX_DROP = 4D, MAX_CLIMB = 3D, MAX_BRACED_LEDGE = 9D, EPS = 0.002D;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    GardenerSurfaceHunting(EntityGardener mob) { this.mob = mob; }
    boolean available(Entity prey) {
        Long until = rejected.get(prey.getUniqueID());
        return until == null || until <= mob.worldObj.getTotalWorldTime();
    }
    void blockedGrab(Entity prey) {
        if(prey == remembered) pursuitFailures++;
    }
    boolean remember(Entity prey) {
        long now = mob.worldObj.getTotalWorldTime();
        if(prey == null) {
            if(remembered != null) lose("prey left detection range");
            return false;
        }
        if(prey != remembered) {
            remembered = prey; lastSeen = Vec3.createVectorHelper(prey.posX, prey.boundingBox.minY, prey.posZ);
            interrupt("prey detected");
            seenAt = now; pursuitFailures = 0; roam = null; roaming = false; investigation = null; scouting = false;
            visible = true; pause = 0; retryUntil = 0;
            progressAt = now; bestDistance = mob.getDistanceSqToEntity(prey);
            mob.huntingDiagnostic("pursuit", "Detected prey " + prey.getEntityId() + "; starting pursuit");
        }
        if((mob.ticksExisted + mob.getEntityId()) % 5 == 0) visible = mob.canSeeSurfacePrey(prey);
        if(visible) {
            // Sample only observations with a fresh line of sight. Between samples
            // the follower keeps its existing route toward the last observation.
            if((mob.ticksExisted + mob.getEntityId()) % 5 == 0) {
                double distance = mob.getDistanceSqToEntity(prey);
                if(distance < bestDistance - 1D) { bestDistance = distance; progressAt = now; pursuitFailures = 0; }
                seenAt = now; lastSeen = Vec3.createVectorHelper(prey.posX, prey.boundingBox.minY, prey.posZ);
            }
            if(now - progressAt <= 320 && pursuitFailures < 3) return true;
        } else {
            lose("lost sight of prey " + prey.getEntityId()); return false;
        }
        reject(prey, now);
        lastSeen = null; remembered = null; interrupt("prey unreachable");
        mob.huntingDiagnostic("abandon", "Abandoned unreachable prey " + prey.getEntityId());
        return false;
    }

    private void lose(String reason) {
        investigation = lastSeen;
        lastSeen = null; remembered = null; scouting = false;
        // Keep following an existing route to the observation when possible.
        if(investigation != null) mob.huntingDiagnostic("investigation", "Investigating last seen position " + investigation + "; " + reason);
    }
    void discard(Entity prey) {
        if(prey == remembered) {
            lastSeen = null; remembered = null; investigation = null;
            interrupt("prey no longer eligible");
        }
    }
    private void reject(Entity prey, long now) {
        if(rejected.size() >= 32) rejected.entrySet().removeIf(entry -> entry.getValue() <= now);
        if(rejected.size() >= 32) rejected.remove(rejected.keySet().iterator().next());
        rejected.put(prey.getUniqueID(), now + 600);
    }
    boolean crossingEdge() { return committed; }
    void finishEdge() {
        if(committed && step < route.size()) follow(route.get(step), 1D);
    }
    private void interrupt(String reason) {
        if(!committed) cancel(reason);
    }
    void stop() {
        stop("movement ownership changed");
    }
    void stop(String reason) {
        cancel(reason);
        remembered = null; lastSeen = roam = investigation = null; roaming = dispersing = scouting = false; pause = 0; fallSpeed = 0;
        recent.clear();
        retryUntil = 0;
    }
    void emerged() {
        stop("emergence completed"); dispersing = true; exitX = mob.posX; exitZ = mob.posZ; recoveries = 0;
    }
    void tick(Entity prey) {
        if(prey != null && lastSeen != null) {
            if(!committed && mob.surfaceCaptureReach(prey)) { interrupt("prey in capture reach"); settle(); progressAt = mob.worldObj.getTotalWorldTime(); return; }
            // Keep going until the body's articulated capture check succeeds.
            // Arrival at an unreachable prey position is a failed approach.
            int result = travel(lastSeen.xCoord, lastSeen.yCoord, lastSeen.zCoord, 0.85D, 1.8D);
            if(result != 0 && !committed) {
                pursuitFailures++;
                retryUntil = mob.worldObj.getTotalWorldTime() + 12;
            }
            return;
        }
        if(investigation != null) {
            if(investigate()) return;
        }
        if(dispersing && horizontalSq(mob.posX, mob.posZ, exitX, exitZ) >= DISPERSAL_MIN * DISPERSAL_MIN) dispersing = false;
        if(pause > 0) {
            if(--pause == 0) mob.huntingDiagnostic("pause-end", "Idle pause ended; resuming surface search");
            settle(); return;
        }
        if(!committed && mob.worldObj.getTotalWorldTime() < retryUntil) { settle(); return; }
        if(roam == null) selectRoam();
        if(roam == null) { settle(); return; }
        int result = travel(roam.xCoord, roam.yCoord, roam.zCoord, 1D, 1D);
        if(result != 0) {
            roam = null; roaming = false;
            if(result > 0) {
                recoveries = 0;
                if(!dispersing && mob.getRNG().nextInt(4) == 0) startPause(mob.getRNG().nextInt(MAX_PAUSE + 1));
            }
        }
    }

    private boolean investigate() {
        long now = mob.worldObj.getTotalWorldTime();
        if(now - seenAt > PREY_MEMORY && committed) { finishEdge(); return true; }
        if(now - seenAt <= PREY_MEMORY) {
            int result = travel(investigation.xCoord, investigation.yCoord, investigation.zCoord, 2D, 1.25D);
            if(result == 0) return true;
            if(result > 0 && !scouting) {
                scouting = true;
                int start = mob.getRNG().nextInt(DIRECTIONS.length);
                for(int attempt = 0; attempt < 4; attempt++) {
                    int[] direction = DIRECTIONS[(start + attempt) % DIRECTIONS.length];
                    Node landing = landing(MathHelper.floor_double(mob.posX) + direction[0] * 6,
                            MathHelper.floor_double(mob.posZ) + direction[1] * 6, mob.boundingBox.minY, MAX_DROP, true);
                    if(landing != null) { investigation = landing.point(); return true; }
                }
            }
        }
        investigation = null; interrupt("prey investigation finished");
        mob.huntingDiagnostic("investigation-end", "No prey found at the last observation; resuming roaming");
        return false;
    }

    private void startPause(int ticks) {
        pause = ticks;
        if(ticks > 0) mob.huntingDiagnostic("pause", "Taking a " + ticks + "-tick surface pause");
    }

    private void selectRoam() {
        double homeDX = mob.posX - mob.huntingHomeX(), homeDZ = mob.posZ - mob.huntingHomeZ();
        double homeDistance = Math.sqrt(homeDX * homeDX + homeDZ * homeDZ);
        boolean outside = homeDistance > GeminiConfig.gardenerHuntingRadius;
        for(int attempt = 0; attempt < 8; attempt++) {
            double angle = mob.getRNG().nextDouble() * Math.PI * 2D + mob.getUniqueID().hashCode() * 2.399963D;
            double min = dispersing ? DISPERSAL_MIN : ROAM_MIN, max = dispersing ? DISPERSAL_MAX : ROAM_MAX;
            // After failed long legs try a shorter staging destination, then
            // resume the normal range as soon as a route succeeds.
            if(recoveries >= 3 && attempt >= 4) { min *= 0.5D; max = min + 8D; }
            double distance = min + mob.getRNG().nextDouble() * (max - min);
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
            if(dispersing && recoveries < 3 && horizontalSq(x, z, exitX, exitZ) < DISPERSAL_MIN * DISPERSAL_MIN) continue;
            boolean repeated = false;
            for(Vec3 old : recent) if((old.xCoord - x) * (old.xCoord - x) + (old.zCoord - z) * (old.zCoord - z) < 100D) repeated = true;
            // A narrow loaded corridor may have just one viable destination.
            // Let recovery revisit it after the landing exclusion has expired.
            if(repeated && recoveries < 4) continue;
            int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
            double range = 10D + Math.min(4, recoveries) * 3D;
            Node landing = landing(bx, bz, mob.boundingBox.minY, range, true);
            if(landing != null) roam = landing.point();
            if(roam != null) {
                recent.add(roam); if(recent.size() > 6) recent.remove(0);
                report("destination", "Selected " + (dispersing ? "dispersal" : "roaming") + " destination", roam); return;
            }
        }
        recoveries = Math.min(8, recoveries + 1);
        report("no-destination", "No clear unoccupied landing; retrying with different destinations", null);
        retryUntil = mob.worldObj.getTotalWorldTime() + Math.min(MAX_PAUSE, 10 + recoveries * 5);
    }
    /** 1 arrived, 0 moving/planning, -1 failed. Long trips use bounded local route segments. */
    int travel(double x, double y, double z, double reach, double urgency) {
        if(failed) { failed = false; return -1; }
        long now = mob.worldObj.getTotalWorldTime();
        if(now < retryUntil) { settle(); return 0; }

        Vec3 requested = Vec3.createVectorHelper(x, y, z);
        double y0 = mob.boundingBox.minY, dx = x - mob.posX, dz = z - mob.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if(!committed && distance <= reach && Math.abs(y - y0) <= (reach < 0.5D ? 0.025D : 0.55D)
                && supported(mob.posX, y0, mob.posZ) && clear(body(mob.posX, y0, mob.posZ))) {
            report("arrival", "Reached supported destination", requested); clearPath(); recoveries = 0; return 1;
        }
        // Finish a committed edge before reacting to a moving prey target.
        if(goal == null || !committed && goal.squareDistanceTo(requested) > 16D) {
            clearPath(); goal = requested;
        }
        if(segment == null) {
            if(!clear(body(mob.posX, y0, mob.posZ))) {
                fail(null, "invalid starting node: body obstructed, wet or outside loaded terrain");
                return 0;
            }
            // Start from stable ground, including fractional-height slabs or snow.
            if(!supported(mob.posX, y0, mob.posZ)) {
                settle();
                if(++blockedTicks >= STALL_TICKS) fail(null, "invalid starting node: no terrain support");
                return 0;
            }
            blockedTicks = 0;
            finalSegment = distance <= SEGMENT_LENGTH;
            preciseRoute = reach < 0.5D;
            segment = localLanding(requested, distance, reach);
            if(segment == null) { fail(null, "no supported local landing"); return 0; }
            Node start = new Node(mob.posX, y0, mob.posZ, null, Motion.WALK, 0, 0);
            // The live position is not necessarily centered in its grid cell.
            // Keep its identity separate so the same cell's center remains a valid connector.
            frontierDistance = remaining(start);
            for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, mob.boundingBox.expand(34, 40, 34))) {
                EntityGardener other = (EntityGardener) entry;
                if(other != mob && !other.isOcean() && other.isEntityAlive()) neighbors.add(other);
            }
            open.add(start); searching = true; pathResult = "searching";
        }
        if(searching) search();
        if(searching || failed) { settle(); return 0; }
        if(step >= route.size()) {
            if(finalSegment && goal.squareDistanceTo(requested) <= 16D) {
                fail(segment, "reachable staging point did not reach the destination"); return 0;
            }
            clearPath(); return 0;
        }
        follow(route.get(step), urgency);
        return 0;
    }

    private Node localLanding(Vec3 requested, double distance, double reach) {
        double scale = distance <= SEGMENT_LENGTH ? 1D : SEGMENT_LENGTH / distance;
        double x = mob.posX + (requested.xCoord - mob.posX) * scale;
        double z = mob.posZ + (requested.zCoord - mob.posZ) * scale;
        double feet = mob.boundingBox.minY;
        double y = scale == 1D ? requested.yCoord : feet;
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        if(scale == 1D && reach < 0.5D) {
            return exactLanding(x, y, z);
        }
        Node best = landing(bx, bz, y, scale == 1D ? Math.max(0.55D, reach / 3D) : 24D, false);
        if(best != null && (scale < 1D || horizontalSq(best.x, best.z, x, z) <= reach * reach)) return best;
        best = null;
        double bestScore = Double.MAX_VALUE;
        int radius = 3 + Math.min(2, recoveries / 2) * 2;
        for(int spread = 1; spread <= radius; spread += 2) {
            for(int[] direction : DIRECTIONS) {
                Node alternative = landing(bx + direction[0] * spread, bz + direction[1] * spread, y, 16D + recoveries * 2D, false);
                if(alternative == null || horizontalSq(alternative.x, alternative.z, mob.posX, mob.posZ) < 1D) continue;
                // Prefer the requested direction and height. When a landing is rejected, stage nearby.
                double score = horizontalSq(alternative.x, alternative.z, x, z) + Math.abs(alternative.y - y) * 1.5D;
                if(score < bestScore) { bestScore = score; best = alternative; }
            }
            if(best != null) break;
        }
        if(best != null) landingResult = "accepted at Y=" + best.y;
        return best;
    }

    Vec3 approach(int x, int z, double preferredY, double range) {
        Node node = landing(x, z, preferredY, range, true);
        return node == null ? null : node.point();
    }

    private Node exactLanding(double x, double y, double z) {
        AxisAlignedBB box = body(x, y, z);
        if(!clear(box) || !supported(x, y, z)) {
            landingResult = "requested waypoint has no clear supported landing";
            return null;
        }
        if(isDenied(MathHelper.floor_double(x), y, MathHelper.floor_double(z))) {
            landingResult = "recently rejected waypoint";
            return null;
        }
        if(occupied(box, x, y, z, false)) {
            landingResult = "waypoint occupied by another Gardener";
            return null;
        }
        landingResult = "accepted at Y=" + y;
        return new Node(x, y, z, null, Motion.WALK, 0, 0);
    }

    private Node landing(int x, int z, double preferredY, double range, boolean reserve) {
        double cx = x + 0.5D, cz = z + 0.5D;
        AxisAlignedBB footprint = body(cx, preferredY, cz);
        if(!GardenerNests.loaded(mob.worldObj, footprint.expand(1, 0, 1))) {
            landingResult = "unloaded footprint"; return null;
        }
        TreeSet<Double> levels = floorLevels(cx, cz, preferredY - range, preferredY + range);
        if(levels.isEmpty()) { landingResult = "no collision support in searched elevation band"; return null; }
        while(!levels.isEmpty()) {
            Double above = levels.ceiling(preferredY), below = levels.floor(preferredY);
            double y = above == null || below != null && preferredY - below <= above - preferredY ? below : above;
            levels.remove(y);
            if(isDenied(x, y, z)) { landingResult = "recently rejected landing"; continue; }
            AxisAlignedBB box = body(cx, y, cz);
            if(!clear(box)) { landingResult = "body clearance obstructed or wet"; continue; }
            if(!safeFooting(box)) { landingResult = "unsafe material at footing"; continue; }
            if(reserve && canopy(cx, y, cz)) { landingResult = "tree canopy at landing"; continue; }
            if(occupied(box, cx, y, cz, reserve)) { landingResult = "occupied or reserved landing"; continue; }
            landingResult = "accepted at Y=" + y;
            return new Node(cx, y, cz, null, Motion.WALK, 0, 0);
        }
        return null;
    }

    private boolean canopy(double x, double y, double z) {
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y - EPS), bz = MathHelper.floor_double(z);
        Block block = mob.worldObj.getBlock(bx, by, bz);
        return block.isWood(mob.worldObj, bx, by, bz) || block.isLeaves(mob.worldObj, bx, by, bz);
    }

    /** The block collision shapes under the entire body set candidate foot heights. */
    private TreeSet<Double> floorLevels(double x, double z, double low, double high) {
        TreeSet<Double> levels = new TreeSet<Double>();
        low = Math.max(1, low); high = Math.min(mob.worldObj.getActualHeight() - mob.height, high);
        if(high < low) return levels;
        AxisAlignedBB box = body(x, low, z);
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(box.minX, low - EPS, box.minZ, box.maxX, high + EPS, box.maxZ);
        if(!GardenerNests.loaded(mob.worldObj, probe.expand(1, 0, 1))) return levels;
        for(Object entry : mob.worldObj.func_147461_a(probe)) {
            AxisAlignedBB floor = (AxisAlignedBB) entry;
            if(floor.maxY >= low - EPS && floor.maxY <= high + EPS) levels.add(floor.maxY);
        }
        return levels;
    }

    private AxisAlignedBB body(double x, double y, double z) {
        return mob.boundingBox.getOffsetBoundingBox(x - mob.posX, y - mob.boundingBox.minY, z - mob.posZ)
                .contract(EPS, EPS, EPS);
    }

    private boolean clear(AxisAlignedBB box) {
        return box.minY >= 0 && box.maxY <= mob.worldObj.getActualHeight()
                && GardenerNests.loaded(mob.worldObj, box.expand(1, 0, 1))
                && !mob.worldObj.isAnyLiquid(box) && mob.worldObj.func_147461_a(box).isEmpty();
    }

    boolean supported(double x, double y, double z) {
        AxisAlignedBB box = body(x, y, z);
        if(y < 1 || !GardenerNests.loaded(mob.worldObj, box.expand(1, 0, 1))) return false;
        AxisAlignedBB sole = AxisAlignedBB.getBoundingBox(box.minX, y - 0.025D, box.minZ, box.maxX, y + EPS, box.maxZ);
        return !mob.worldObj.func_147461_a(sole).isEmpty() && safeFooting(box);
    }

    private boolean safeFooting(AxisAlignedBB box) {
        int low = MathHelper.floor_double(box.minY - 0.025D), high = MathHelper.floor_double(box.minY + 0.1D);
        for(int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
            for(int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++)
                for(int y = low; y <= high; y++) {
                    Block block = mob.worldObj.getBlock(x, y, z);
                    if(block == Blocks.fire || block == Blocks.cactus || block.getMaterial().isLiquid()) return false;
                }
        return true;
    }

    private boolean occupied(AxisAlignedBB center, double x, double y, double z, boolean reserve) {
        AxisAlignedBB spacing = reserve ? center.expand(2, 0, 2) : center;
        AxisAlignedBB query = reserve ? center.expand(50, 16, 50) : center.expand(1, 1, 1);
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, query)) {
            EntityGardener other = (EntityGardener) entry;
            if(other == mob || other.isOcean() || !other.isEntityAlive()) continue;
            if(other.boundingBox.intersectsWith(spacing)) return true;
            if(!reserve) continue;
            Vec3 reserved = other.surfaceHuntingDestination();
            if(reserved != null && Math.abs(reserved.yCoord - y) < mob.height
                    && horizontalSq(reserved.xCoord, reserved.zCoord, x, z) < 100D) return true;
        }
        return false;
    }

    Vec3 destination() { return roam != null ? roam : goal; }

    boolean separating(double x, double y, double z, boolean levelCorrection) {
        AxisAlignedBB from = mob.boundingBox, to = body(x, y, z);
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, from.addCoord(x - mob.posX,
                y - from.minY, z - mob.posZ).expand(1, 1, 1))) {
            EntityGardener other = (EntityGardener) entry;
            if(other != mob && !other.isOcean() && other.isEntityAlive() && crowds(from, to, other.boundingBox)
                    && !(levelCorrection && from.intersectsWith(other.boundingBox))) return false;
        }
        return true;
    }

    private boolean crowds(AxisAlignedBB from, AxisAlignedBB to, AxisAlignedBB other) {
        if(!to.intersectsWith(other)) return false;
        return separation(to, other) <= separation(from, other) + 1E-7D;
    }

    private double separation(AxisAlignedBB a, AxisAlignedBB b) {
        double dx = (a.minX + a.maxX - b.minX - b.maxX) * 0.5D;
        double dy = (a.minY + a.maxY - b.minY - b.maxY) * 0.5D;
        double dz = (a.minZ + a.maxZ - b.minZ - b.maxZ) * 0.5D;
        return dx * dx + dy * dy + dz * dz;
    }

    private void settle() {
        fallSpeed = mob.onGround ? 0.08D : Math.min(1D, fallSpeed + 0.08D);
        mob.moveGardener(0, -fallSpeed, 0);
    }

    private void follow(Node next, double urgency) {
        Node from = next.parent;
        double x = mob.posX, y = mob.boundingBox.minY, z = mob.posZ;
        double dx = next.x - x, dz = next.z - z, distance = Math.sqrt(dx * dx + dz * dz);
        if(distance <= 0.045D && Math.abs(y - next.y) <= 0.025D && supported(x, y, z)) {
            step++; blockedTicks = 0; committed = false; return;
        }
        if(blockedTicks == 0 && (mob.ticksExisted % 16 == 0 || sampleAt == 0)
                && (!clear(body(next.x, next.y, next.z)) || !supported(next.x, next.y, next.z))) {
            fail(next, "landing changed or lost collision support"); return;
        }
        double speed = mob.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue() * urgency;
        double scale = distance > 0 ? Math.min(1D, speed / distance) : 0;
        double mx = dx * scale, mz = dz * scale;
        if((next.motion == Motion.WALK || distance > 0.045D && y >= next.y)
                && !separating(x + mx, y, z + mz, false)) {
            stall(next, "movement blocked by another Gardener"); return;
        }
        if(next.motion == Motion.CLIMB) {
            double climbX = next.x - from.x, climbZ = next.z - from.z;
            // Native horizontal collision needs the entire sole at or above the
            // ledge. Even a subpixel shortfall still overlaps the block face.
            if(y < next.y) {
                double rise = Math.min(next.y - y, speed);
                if(!separating(x, y + rise, z, false)) { stall(next, "vertical movement blocked by another Gardener"); return; }
                if(!wallContact(body(x, y, z), climbX, climbZ)
                        || !clear(body(x, y, z).addCoord(0, rise, 0))) {
                    fail(next, "failed vertical transition: climb lost wall contact or headroom"); return;
                }
                mob.traversalPose(GardenerLocomotion.CLIMB, climbX, climbZ);
                mob.moveGardener(0, rise, 0);
            } else {
                if(!clear(body(x, y, z).addCoord(mx, 0, mz))) {
                    fail(next, "climb crest obstructed"); return;
                }
                mob.traversalPose(GardenerLocomotion.CREST, climbX, climbZ);
                mob.moveGardener(mx, 0, mz);
            }
        } else if(next.motion == Motion.DESCEND || next.motion == Motion.LOWER) {
            // Cross the edge at rim height. Let collision/gravity take over after the
            // entire footprint has cleared the ledge and the checked column is below it.
            if(distance > 0.045D && y >= from.y - EPS) {
                AxisAlignedBB sweep = body(x, y, z).addCoord(mx, 0, mz);
                if(!clear(sweep)) { fail(next, "descent edge obstructed"); return; }
                if(next.motion == Motion.LOWER)
                    mob.traversalPose(GardenerLocomotion.ENTRY, next.x - from.x, next.z - from.z);
                mob.moveGardener(mx, 0, mz);
            } else {
                AxisAlignedBB column = body(next.x, next.y, next.z).addCoord(0, Math.max(0D, y - next.y), 0);
                if(!clear(column) || !supported(next.x, next.y, next.z) || y < next.y - 0.025D) {
                    fail(next, "failed vertical transition: descent column or landing became unsafe"); return;
                }
                double drop = next.motion == Motion.LOWER ? Math.min(speed, y - next.y)
                        : Math.min(Math.max(0.08D, fallSpeed), y - next.y);
                if(!separating(x, y - drop, z, false)) {
                    stall(next, "vertical movement blocked by another Gardener"); return;
                }
                if(next.motion == Motion.LOWER) {
                    double wallX = from.x - next.x, wallZ = from.z - next.z;
                    if(!wallContact(body(x, y, z), wallX, wallZ)) {
                        fail(next, "failed vertical transition: descent lost wall contact"); return;
                    }
                    mob.traversalPose(GardenerLocomotion.LOWER, wallX, wallZ);
                    mob.moveGardener(0, -drop, 0);
                } else settle();
            }
        } else {
            AxisAlignedBB sweep = body(x, y, z).addCoord(mx, 0, mz);
            if(!GardenerNests.loaded(mob.worldObj, sweep.expand(1, 0, 1)) || mob.worldObj.isAnyLiquid(sweep)) {
                fail(next, "walking edge entered unloaded or wet terrain"); return;
            }
            fallSpeed = mob.onGround ? 0.08D : Math.min(1D, fallSpeed + 0.08D);
            mob.moveGardener(mx, -fallSpeed, mz);
        }
        double moved = horizontalSq(x, z, mob.posX, mob.posZ);
        if(moved < 0.000004D && Math.abs(mob.boundingBox.minY - y) < EPS) {
            stall(next, "native movement made no progress (" + next.motion + ")"); return;
        }
        committed = next.motion != Motion.WALK;
        blockedTicks = 0;
        if(moved > 0.000004D && roam != null && !roaming) {
            roaming = true; report("roaming", "Moving on a surface roaming route", roam);
        }
        measure(next);
    }

    private void stall(Node next, String reason) {
        if(++blockedTicks >= STALL_TICKS) fail(next, reason);
    }

    private void measure(Node next) {
        long now = mob.worldObj.getTotalWorldTime();
        double y = mob.boundingBox.minY;
        if(sampleAt == 0) { sampleAt = now; sampleX = mob.posX; sampleY = y; sampleZ = mob.posZ; return; }
        if(now - sampleAt < STALL_TICKS) return;
        measurementTicks = now - sampleAt;
        displacement = Math.sqrt(horizontalSq(mob.posX, mob.posZ, sampleX, sampleZ));
        verticalDisplacement = y - sampleY;
        boolean verticalProgress = next.motion != Motion.WALK && Math.abs(verticalDisplacement) >= 0.25D;
        if(displacement < 0.2D && !verticalProgress) { fail(next, "active " + next.motion + " route made insufficient measured movement"); return; }
        if(now >= nextReport) { report("movement", "Following surface route: " + next.motion, goal); nextReport = now + 200; }
        sampleAt = now; sampleX = mob.posX; sampleY = y; sampleZ = mob.posZ;
    }

    private void clearPath() {
        nodes.clear(); open.clear(); floors.clear(); route.clear(); neighbors.clear();
        goal = null; segment = frontier = null; searching = failed = committed = false; step = visited = blockedTicks = 0; sampleAt = 0;
        displacement = verticalDisplacement = 0D; measurementTicks = 0L;
        pathResult = "idle"; landingResult = "not checked";
    }

    private void cancel(String reason) {
        if(goal != null) report("cancel", "Navigation canceled: " + reason, goal);
        clearPath();
    }

    private void fail(Node node, String reason) {
        recoveries = Math.min(8, recoveries + 1);
        if(sampleAt != 0) {
            measurementTicks = mob.worldObj.getTotalWorldTime() - sampleAt;
            displacement = Math.sqrt(horizontalSq(mob.posX, mob.posZ, sampleX, sampleZ));
            verticalDisplacement = mob.boundingBox.minY - sampleY;
        }
        pathResult = searching || route.isEmpty() ? "search rejected" : "execution rejected";
        report("navigation", "Navigation failed: " + reason + "; recoveryAction="
                + (roam != null ? "choose a different roaming destination" : "exclude failed landing and replan nearby"), goal);
        // An execution failure can be bypassed via another edge to the goal.
        if(node != null) deny(node);
        else if(segment != null) deny(segment);
        clearPath(); failed = true; retryUntil = mob.worldObj.getTotalWorldTime() + Math.min(MAX_PAUSE, recoveries * 5);
    }

    private boolean isDenied(int x, double y, int z) {
        Long until = denied.get(key(x, y, z));
        return until != null && until > mob.worldObj.getTotalWorldTime();
    }

    private void deny(Node node) {
        if(denied.size() >= 64) denied.entrySet().removeIf(entry -> entry.getValue() <= mob.worldObj.getTotalWorldTime());
        if(denied.size() >= 64) denied.remove(denied.keySet().iterator().next());
        denied.put(node.key(), mob.worldObj.getTotalWorldTime() + 240);
    }

    private void report(String event, String message, Vec3 target) {
        if(!mob.huntingDebugEnabled()) return;
        double feet = mob.boundingBox.minY;
        Double ground = mob.onGround ? Double.valueOf(feet)
                : floorLevels(mob.posX, mob.posZ, feet - MAX_DROP, feet).floor(feet + EPS);
        double distance = target == null ? 0 : Math.sqrt(horizontalSq(mob.posX, mob.posZ, target.xCoord, target.zCoord));
        String state = mob.surfaceMovementState();
        if("surface".equals(state)) state = remembered != null ? "pursuit" : investigation != null ? "investigating" : dispersing ? "dispersal" : "roaming";
        mob.huntingDiagnostic(event, message + "; ai=" + state
                + "; action=" + mob.state() + "; position=" + mob.posX + "," + mob.posY + "," + mob.posZ
                + "; destination=" + target + "; feetY=" + feet + "; groundY=" + (ground == null ? "-" : ground)
                + "; destinationGroundY=" + (segment == null ? target == null ? "-" : target.yCoord : segment.y)
                + "; horizontalDistance=" + distance + "; verticalDifference=" + (target == null ? 0 : target.yCoord - feet)
                + "; landing=" + landingResult + "; path=" + pathResult + "; waypoint=" + step + "/" + route.size()
                + "; horizontalDisplacement=" + displacement + "; verticalDisplacement=" + verticalDisplacement
                + "; measurementTicks=" + measurementTicks + "; recoveries=" + recoveries + "; carryingPrey=" + mob.captureState()
                + mob.nestNavigationContext());
    }

    private Motion edge(Node from, double x, double y, double z) {
        double dx = x - from.x, dz = z - from.z, rise = y - from.y;
        double high = Math.max(y, from.y);
        AxisAlignedBB start = body(from.x, from.y, from.z), end = body(x, y, z);
        if(!clear(body(from.x, high, from.z).addCoord(dx, 0, dz))) return null;
        if(rise < -0.025D) {
            if(!clear(end.addCoord(0, -rise, 0))) return null;
            if(rise >= -MAX_DROP - EPS) return Motion.DESCEND;
            for(double feet = high; feet > y; feet -= 1D)
                if(!wallContact(body(x, feet, z), -dx, -dz)) return null;
            return Motion.LOWER;
        }
        if(rise <= EPS) return Motion.WALK;
        if(!clear(start.addCoord(0, rise, 0))) return null;
        if(rise <= mob.stepHeight + EPS) return Motion.WALK;
        for(double feet = from.y; feet < y; feet += 1D)
            if(!wallContact(body(from.x, feet, from.z), dx, dz)) return null;
        return Motion.CLIMB;
    }

    private boolean wallContact(AxisAlignedBB body, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if(length < EPS) return false;
        double x = 1.05D * dx / length, z = 1.05D * dz / length;
        AxisAlignedBB wall = body.addCoord(x, 0, z);
        // Probe only the leading face. The last few centimeters still brace
        // against the terrain under the lip, after the torso has cleared it.
        if(Math.abs(x) >= Math.abs(z)) {
            if(x > 0) wall.minX = body.maxX - EPS;
            else wall.maxX = body.minX + EPS;
        } else if(z > 0) wall.minZ = body.maxZ - EPS;
        else wall.maxZ = body.minZ + EPS;
        wall.minY -= 0.12D;
        return GardenerNests.loaded(mob.worldObj, wall.expand(1, 0, 1)) && !mob.worldObj.func_147461_a(wall).isEmpty();
    }

    private double heuristic(Node node) {
        return 10D * (Math.abs(node.x - segment.x) + Math.abs(node.z - segment.z)) + 5D * Math.abs(node.y - segment.y);
    }

    private double remaining(Node node) {
        return Math.sqrt(horizontalSq(node.x, node.z, goal.xCoord, goal.zCoord)) + 0.5D * Math.abs(node.y - goal.yCoord);
    }

    private void routeTo(Node end, boolean staging) {
        for(Node node = end; node.parent != null; node = node.parent) route.add(node);
        Collections.reverse(route); searching = false; nodes.clear(); open.clear(); floors.clear(); neighbors.clear();
        pathResult = "ready after " + visited + " nodes";
        report("path", staging ? "Connected route to an alternate staging point" : "Surface route ready", goal);
        if(staging) finalSegment = false;
    }

    private void search() {
        for(int budget = 0; budget < 8 && visited < LIMIT && !open.isEmpty(); budget++) {
            Node current = open.poll();
            if((current.parent != null && nodes.get(current.key()) != current) || current.closed) continue;
            current.closed = true; visited++;
            if(current.parent != null) {
                if(current.key() == segment.key()) { routeTo(current, false); return; }
                double distance = remaining(current);
                if(distance < frontierDistance - (frontier == null ? 3D : 0D)) {
                    frontier = current; frontierDistance = distance;
                }
            }
            int x = MathHelper.floor_double(current.x), z = MathHelper.floor_double(current.z);
            for(int[] direction : DIRECTIONS) connect(current, x + direction[0], z + direction[1]);
            if(current.parent == null) {
                connect(current, x, z);
                // A broad body resting on a rim may have no aligned point one
                // cell away. Probe a second ring using the same swept edge checks.
                if(open.isEmpty()) for(int[] direction : DIRECTIONS) connect(current, x + 2 * direction[0], z + 2 * direction[1]);
            }
        }
        if(open.isEmpty() || visited >= LIMIT) {
            if(!finalSegment && frontier != null) {
                deny(segment);
                routeTo(frontier, true);
            } else fail(null, "no connected path after " + visited + " expanded nodes"
                    + (visited == 1 ? " (supported origin has no clear walking, climbing or descent edge)" : ""));
        }
    }

    private void connect(Node from, int x, int z) {
        double cx = x + 0.5D, cz = z + 0.5D;
        double horizontal = Math.sqrt(horizontalSq(cx, cz, from.x, from.z));
        if(horizontal < EPS || Math.abs(cx - mob.posX) > 29D || Math.abs(cz - mob.posZ) > 29D) return;
        long column = key(x, from.y, z);
        TreeSet<Double> levels = floors.get(column);
        if(levels == null) {
            levels = floorLevels(cx, cz, from.y - MAX_BRACED_LEDGE, from.y + (preciseRoute ? MAX_BRACED_LEDGE : MAX_CLIMB));
            floors.put(column, levels);
        }
        AxisAlignedBB start = body(from.x, from.y, from.z);
        for(double y : levels) {
            long key = key(x, y, z);
            if(isDenied(x, y, z)) continue;
            AxisAlignedBB end = body(cx, y, cz);
            if(!safeFooting(end)) continue;
            double cost = from.cost + 10D * horizontal + 4D * Math.abs(y - from.y);
            Node old = nodes.get(key);
            if(old != null && old.cost <= cost || nodes.size() >= LIMIT || open.size() >= LIMIT * 2) continue;
            boolean crowded = false;
            for(EntityGardener other : neighbors) {
                if(other.isEntityAlive() && crowds(start, end, other.boundingBox)) { crowded = true; break; }
            }
            if(crowded) continue;
            Motion motion = edge(from, cx, y, cz);
            if(motion == null) continue;
            if(motion == Motion.CLIMB) cost += 6D;
            if(old != null && old.cost <= cost) continue;
            Node next = new Node(cx, y, cz, from, motion, cost, 0D);
            next.score = cost + heuristic(next) * 1.25D;
            nodes.put(key, next); open.add(next);
        }
    }

    private static double horizontalSq(double x, double z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        return dx * dx + dz * dz;
    }

    private static long key(int x, double y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | Math.round(y * 16D) & 4095L;
    }

    private enum Motion { WALK, DESCEND, CLIMB, LOWER }

    private static final class Node implements Comparable<Node> {
        final double x, y, z, cost;
        final Motion motion;
        final Node parent;
        double score;
        boolean closed;

        Node(double x, double y, double z, Node parent, Motion motion, double cost, double score) {
            this.x = x; this.y = y; this.z = z; this.parent = parent; this.motion = motion; this.cost = cost; this.score = score;
        }

        Vec3 point() { return Vec3.createVectorHelper(x, y, z); }
        long key() { return GardenerSurfaceHunting.key(MathHelper.floor_double(x), y, MathHelper.floor_double(z)); }
        @Override public int compareTo(Node other) { return Double.compare(score, other.score); }
    }
}
