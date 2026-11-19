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
    private Vec3 lastSeen, roam;
    private long seenAt;
    private long progressAt;
    private double bestDistance;
    private int pause, pursuitFailures, recoveries;
    private boolean roaming, dispersing;
    private double exitX, exitZ, sampleX, sampleZ, displacement, fallSpeed;
    private double sampleY;
    private long sampleAt, nextReport, measurementTicks, retryUntil;
    private final Map<Long, Node> nodes = new HashMap<Long, Node>();
    private final Map<Long, Long> denied = new HashMap<Long, Long>();
    private final Map<Long, TreeSet<Double>> floors = new HashMap<Long, TreeSet<Double>>();
    private final PriorityQueue<Node> open = new PriorityQueue<Node>();
    private final List<Node> route = new ArrayList<Node>();
    private Vec3 goal;
    private Node segment;
    private int step, visited, blockedTicks;
    private boolean searching, failed, finalSegment;
    private String landingResult = "not checked", pathResult = "idle";
    private static final int LIMIT = 768, SEGMENT_LENGTH = 18;
    private static final double MAX_DROP = 4D, MAX_CLIMB = 3D, EPS = 0.002D;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

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
        if(distance < bestDistance - 1D) { bestDistance = distance; progressAt = now; pursuitFailures = 0; }
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
        remembered = null; lastSeen = roam = null; roaming = dispersing = false; pause = 0; fallSpeed = 0;
        retryUntil = 0;
    }
    void emerged() {
        stop("emergence completed"); dispersing = true; exitX = mob.posX; exitZ = mob.posZ; recoveries = 0;
    }
    void tick(Entity prey) {
        if(prey != null && lastSeen != null) {
            if(mob.surfaceCaptureReach(prey)) { cancel("prey in capture reach"); settle(); return; }
            int result = travel(lastSeen.xCoord, lastSeen.yCoord, lastSeen.zCoord, 5D, 1.8D);
            if(result < 0) pursuitFailures++;
            // Reaching an unseen last position does not give omniscient tracking through walls.
            return;
        }
        if(dispersing && horizontalSq(mob.posX, mob.posZ, exitX, exitZ) >= 144D) dispersing = false;
        if(pause > 0) { pause--; settle(); return; }
        if(roam == null) {
            double homeDX = mob.posX - mob.huntingHomeX(), homeDZ = mob.posZ - mob.huntingHomeZ();
            double homeDistance = Math.sqrt(homeDX * homeDX + homeDZ * homeDZ);
            boolean outside = homeDistance > GeminiConfig.gardenerHuntingRadius;
            for(int attempt = 0; attempt < 12; attempt++) {
                double angle = mob.getRNG().nextDouble() * Math.PI * 2D + mob.getUniqueID().hashCode() * 2.399963D;
                double distance = dispersing ? 12D + mob.getRNG().nextDouble() * (20D + recoveries * 2D)
                        : 16D + mob.getRNG().nextDouble() * 32D;
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
                if(dispersing && horizontalSq(x, z, exitX, exitZ) < 144D) continue;
                boolean repeated = false;
                for(Vec3 old : recent) if((old.xCoord - x) * (old.xCoord - x) + (old.zCoord - z) * (old.zCoord - z) < 100D) repeated = true;
                if(repeated) continue;
                int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
                double range = 16D + recoveries * 4D;
                Node landing = landing(bx, bz, mob.boundingBox.minY, range, true);
                if(landing != null) roam = landing.point();
                if(roam != null) {
                    recent.add(roam); if(recent.size() > 6) recent.remove(0);
                    report("destination", "Selected " + (dispersing ? "dispersal" : "roaming") + " destination", roam); break;
                }
            }
            if(roam == null) {
                recoveries = Math.min(8, recoveries + 1);
                report("no-destination", "No clear unoccupied landing; expanding destination search", null);
                pause = Math.min(100, 15 + recoveries * 10); settle(); return;
            }
        }
        int result = travel(roam.xCoord, roam.yCoord, roam.zCoord, 1D, 1D);
        if(result != 0) {
            if(result > 0) recoveries = 0;
            roam = null; pause = result > 0 ? mob.getRNG().nextInt(30) : Math.min(100, 10 + recoveries * 10);
        }
    }
    /** 1 arrived, 0 moving/planning, -1 failed. Long trips use bounded local route segments. */
    int travel(double x, double y, double z, double reach, double urgency) {
        if(failed) { failed = false; return -1; }
        long now = mob.worldObj.getTotalWorldTime();
        if(now < retryUntil) { settle(); return 0; }

        Vec3 requested = Vec3.createVectorHelper(x, y, z);
        double y0 = mob.boundingBox.minY, dx = x - mob.posX, dz = z - mob.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if(distance <= reach && Math.abs(y - y0) <= 0.55D && supported(mob.posX, y0, mob.posZ)) {
            report("arrival", "Reached supported destination", requested); clearPath(); recoveries = 0; return 1;
        }
        // Finish a committed edge before reacting to a moving prey target.
        boolean traversing = step < route.size() && route.get(step).motion != Motion.WALK;
        if(goal == null || !traversing && goal.squareDistanceTo(requested) > 16D) {
            clearPath(); goal = requested;
        }
        if(segment == null) {
            // Start from stable ground, including fractional-height slabs or snow.
            if(!supported(mob.posX, y0, mob.posZ)) {
                settle();
                if(++blockedTicks >= 40) fail(null, "no support at route origin");
                return 0;
            }
            blockedTicks = 0;
            finalSegment = distance <= SEGMENT_LENGTH;
            segment = localLanding(requested, distance, reach);
            if(segment == null) { fail(null, "no supported local landing"); return 0; }
            Node start = new Node(mob.posX, y0, mob.posZ, null, Motion.WALK, 0, 0);
            nodes.put(start.key(), start); open.add(start); searching = true; pathResult = "searching";
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
            if(occupied(box, cx, y, cz, reserve)) { landingResult = "occupied or reserved landing"; continue; }
            landingResult = "accepted at Y=" + y;
            return new Node(cx, y, cz, null, Motion.WALK, 0, 0);
        }
        return null;
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

    private boolean supported(double x, double y, double z) {
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

    private boolean separating(double x, double y, double z) {
        for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityGardener.class, mob.boundingBox.expand(8, 2, 8))) {
            EntityGardener other = (EntityGardener) entry;
            if(other == mob || other.isOcean() || !other.isEntityAlive() || Math.abs(other.posY - y) >= mob.height) continue;
            double before = horizontalSq(mob.posX, mob.posZ, other.posX, other.posZ);
            double after = horizontalSq(x, z, other.posX, other.posZ);
            double spacing = (mob.width + other.width) / 2D;
            if(after < spacing * spacing && after < before - 0.000001D) return false;
        }
        return true;
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
            step++; blockedTicks = 0; return;
        }
        if(blockedTicks == 0 && (mob.ticksExisted % 16 == 0 || sampleAt == 0)
                && (!clear(body(next.x, next.y, next.z)) || !supported(next.x, next.y, next.z))) {
            fail(next, "landing changed or lost collision support"); return;
        }
        double speed = mob.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue() * urgency;
        double scale = distance > 0 ? Math.min(1D, speed / distance) : 0;
        double mx = dx * scale, mz = dz * scale;
        if(!separating(x + mx, y, z + mz)) {
            stall(next, "movement blocked by another Gardener"); return;
        }
        if(next.motion == Motion.CLIMB) {
            if(y < next.y - EPS) {
                double rise = Math.min(next.y - y, speed);
                if(!wallContact(body(x, y, z), next.x - from.x, next.z - from.z)
                        || !clear(body(x, y, z).addCoord(0, rise, 0))) {
                    fail(next, "climb lost wall contact or headroom"); return;
                }
                mob.moveGardener(0, rise, 0);
            } else {
                if(!clear(body(x, y, z).addCoord(mx, 0, mz))) {
                    fail(next, "climb crest obstructed"); return;
                }
                mob.moveGardener(mx, 0, mz);
            }
        } else if(next.motion == Motion.DESCEND) {
            // Cross the edge at rim height. Let collision/gravity take over after the
            // entire footprint has cleared the ledge and the checked column is below it.
            if(distance > 0.045D && y >= from.y - EPS) {
                AxisAlignedBB sweep = body(x, y, z).addCoord(mx, 0, mz);
                if(!clear(sweep)) { fail(next, "descent edge obstructed"); return; }
                mob.moveGardener(mx, 0, mz);
            } else {
                AxisAlignedBB column = body(next.x, next.y, next.z).addCoord(0, Math.max(0D, y - next.y), 0);
                if(!clear(column) || !supported(next.x, next.y, next.z) || y < next.y - 0.025D) {
                    fail(next, "descent column or landing became unsafe"); return;
                }
                settle();
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
        blockedTicks = 0;
        if(moved > 0.000004D && roam != null && !dispersing && !roaming) {
            roaming = true; report("roaming", "Moving on a surface roaming route", roam);
        }
        measure(next);
    }

    private void stall(Node next, String reason) {
        if(++blockedTicks >= 20) fail(next, reason);
    }

    private void measure(Node next) {
        long now = mob.worldObj.getTotalWorldTime();
        double y = mob.boundingBox.minY;
        if(sampleAt == 0) { sampleAt = now; sampleX = mob.posX; sampleY = y; sampleZ = mob.posZ; return; }
        if(now - sampleAt < 40) return;
        measurementTicks = now - sampleAt;
        displacement = Math.sqrt(horizontalSq(mob.posX, mob.posZ, sampleX, sampleZ));
        boolean verticalProgress = next.motion != Motion.WALK && Math.abs(y - sampleY) >= 0.25D;
        if(displacement < 0.2D && !verticalProgress) { fail(next, "insufficient measured movement on " + next.motion); return; }
        if(now >= nextReport) { report("movement", "Following surface route: " + next.motion, goal); nextReport = now + 200; }
        sampleAt = now; sampleX = mob.posX; sampleY = y; sampleZ = mob.posZ;
    }

    private void clearPath() {
        nodes.clear(); open.clear(); floors.clear(); route.clear();
        goal = null; segment = null; searching = failed = false; step = visited = blockedTicks = 0; sampleAt = 0;
        displacement = 0D; measurementTicks = 0L;
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
        }
        pathResult = searching || route.isEmpty() ? "search rejected" : "execution rejected";
        report("navigation", "Navigation failed: " + reason + "; recoveryAction="
                + (roam != null ? "choose a different roaming destination" : "exclude failed landing and replan nearby"), goal);
        // A blocked edge does not invalidate the whole destination. Keep the
        // approach available to replanning when another route can reach it.
        if(node != null) deny(node);
        else if(segment != null) deny(segment);
        clearPath(); failed = true; retryUntil = mob.worldObj.getTotalWorldTime() + Math.min(100, recoveries * 10);
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
        double feet = mob.boundingBox.minY;
        Double ground = mob.onGround ? Double.valueOf(feet)
                : floorLevels(mob.posX, mob.posZ, feet - MAX_DROP, feet).floor(feet + EPS);
        double distance = target == null ? 0 : Math.sqrt(horizontalSq(mob.posX, mob.posZ, target.xCoord, target.zCoord));
        String state = mob.surfaceMovementState();
        if("surface".equals(state)) state = remembered != null ? "pursuit" : dispersing ? "dispersal" : "roaming";
        mob.huntingDiagnostic(event, message + "; ai=" + state
                + "; action=" + mob.state() + "; position=" + mob.posX + "," + mob.posY + "," + mob.posZ
                + "; destination=" + target + "; feetY=" + feet + "; groundY=" + (ground == null ? "-" : ground)
                + "; destinationGroundY=" + (segment == null ? target == null ? "-" : target.yCoord : segment.y)
                + "; horizontalDistance=" + distance + "; verticalDifference=" + (target == null ? 0 : target.yCoord - feet)
                + "; landing=" + landingResult + "; path=" + pathResult + "; waypoint=" + step + "/" + route.size()
                + "; horizontalDisplacement=" + displacement + "; measurementTicks=" + measurementTicks + "; recoveries=" + recoveries);
    }

    private Motion edge(Node from, double x, double y, double z) {
        double dx = x - from.x, dz = z - from.z, rise = y - from.y;
        double high = Math.max(y, from.y);
        AxisAlignedBB start = body(from.x, from.y, from.z), end = body(x, y, z);
        if(!clear(body(from.x, high, from.z).addCoord(dx, 0, dz))) return null;
        if(rise < -0.025D) return clear(end.addCoord(0, -rise, 0)) ? Motion.DESCEND : null;
        if(rise <= EPS) return Motion.WALK;
        if(!clear(start.addCoord(0, rise, 0))) return null;
        if(rise <= mob.stepHeight + EPS) return Motion.WALK;
        return wallContact(start, dx, dz) ? Motion.CLIMB : null;
    }

    private boolean wallContact(AxisAlignedBB body, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if(length < EPS) return false;
        AxisAlignedBB wall = body.addCoord(1.05D * dx / length, 0, 1.05D * dz / length);
        return GardenerNests.loaded(mob.worldObj, wall.expand(1, 0, 1)) && !mob.worldObj.func_147461_a(wall).isEmpty();
    }

    private double heuristic(Node node) {
        return 10D * (Math.abs(node.x - segment.x) + Math.abs(node.z - segment.z)) + 5D * Math.abs(node.y - segment.y);
    }

    private void search() {
        for(int budget = 0; budget < 8 && visited < LIMIT && !open.isEmpty(); budget++) {
            Node current = open.poll();
            if(nodes.get(current.key()) != current || current.closed) continue;
            current.closed = true; visited++;
            if(current.key() == segment.key() && current.parent != null) {
                for(Node n = current; n.parent != null; n = n.parent) route.add(n);
                Collections.reverse(route); searching = false; nodes.clear(); open.clear(); floors.clear();
                pathResult = "ready after " + visited + " nodes";
                report("path", "Surface route ready", goal); return;
            }
            for(int side = 0; side < (current.parent == null ? 5 : 4); side++) {
                int x = MathHelper.floor_double(current.x) + (side == 0 ? 1 : side == 1 ? -1 : 0);
                int z = MathHelper.floor_double(current.z) + (side == 2 ? 1 : side == 3 ? -1 : 0);
                double cx = x + 0.5D, cz = z + 0.5D;
                if(horizontalSq(cx, cz, current.x, current.z) < EPS * EPS
                        || Math.abs(cx - mob.posX) > 29D || Math.abs(cz - mob.posZ) > 29D) continue;
                long column = key(x, current.y, z);
                TreeSet<Double> levels = floors.get(column);
                if(levels == null) {
                    levels = floorLevels(cx, cz, current.y - MAX_DROP, current.y + MAX_CLIMB);
                    floors.put(column, levels);
                }
                for(double y : levels) {
                    long key = key(x, y, z);
                    if(key == current.key() && side != 4 || isDenied(x, y, z) || !safeFooting(body(cx, y, cz))) continue;
                    Motion motion = edge(current, cx, y, cz);
                    if(motion == null) continue;
                    double cost = current.cost + 10D + 4D * Math.abs(y - current.y) + (motion == Motion.CLIMB ? 6D : 0D);
                    Node old = nodes.get(key);
                    if(old != null && old != current && old.cost <= cost || nodes.size() >= LIMIT || open.size() >= LIMIT * 2) continue;
                    Node next = new Node(cx, y, cz, current, motion, cost, 0D);
                    next.score = cost + heuristic(next) * 1.25D;
                    nodes.put(key, next); open.add(next);
                }
            }
        }
        if(open.isEmpty() || visited >= LIMIT) fail(null, "no safe route after " + visited + " expanded nodes");
    }

    private static double horizontalSq(double x, double z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        return dx * dx + dz * dz;
    }

    private static long key(int x, double y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | Math.round(y * 16D) & 4095L;
    }

    private enum Motion { WALK, DESCEND, CLIMB }

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
