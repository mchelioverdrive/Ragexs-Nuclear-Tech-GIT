package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.WretchCamps;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Server-owned nest commute. The full existing body excavates and moves progressively, with native collisions. */
final class GardenerBurrowing {
    private static final int UNDERGROUND = 0, EMERGING = 1, SURFACE = 2, RETURNING = 3;
    private static final int APPROACH = 0, OPENING = 1, UPPER = 2, SHAFT = 3, PASSAGE = 4, CHAMBER = 5, DISMOUNT = 6, BASE = 7;
    private static final int[][] APPROACH_OFFSETS = {
            {-9, 0}, {-10, -9}, {-10, 9}, {-16, -4}, {-16, 4}, {-8, -15}, {-8, 15}, {-18, -13}, {-18, 13},
            {-4, -13}, {-4, 13}, {3, -16}, {3, 16}};
    private static final int[] RIM_OFFSETS = {0, 1, 2, -1};
    private static final int[][] CHAMBER_APPROACHES = {
            {31, 3}, {31, -3}, {23, 3}, {23, -3}, {24, 11}, {24, -11}, {17, 3}, {17, -3}};
    private static final int NODE_LIMIT = 2048, RADIUS = 24;
    private final EntityGardener mob;
    private final World world;
    private boolean enabled, retreat, searching, hasHuntingGround, cachedTravel;
    private int state, retry, failures, column, exitCandidate, visited, step, originX, originY, originZ, recoveryProbe;
    private double huntingX, huntingZ;
    private boolean pitTravel, recoveringEmergence, passageBlocked;
    private int pitStage, gripTicks, approachCursor, openingCursor, approachIndex = -1;
    private double openingX = Double.NaN, pitHorizontal, pitVertical;
    private Vec3 pitApproach;
    private final long[] failedApproaches = new long[APPROACH_OFFSETS.length];
    private boolean delivery;
    private Node destination;
    private final List<Node> route = new ArrayList<Node>();
    private final List<Node> tunnel = new ArrayList<Node>();
    private final Map<Long, Node> nodes = new HashMap<Long, Node>();
    private final Map<Long, Integer> costs = new HashMap<Long, Integer>();
    private final Map<Long, Boolean> protection = new HashMap<Long, Boolean>();
    private final Map<Long, Long> denied = new HashMap<Long, Long>();
    private final PriorityQueue<Node> open = new PriorityQueue<Node>(64, Comparator.comparingDouble(n -> n.score));

    GardenerBurrowing(EntityGardener mob) { this.mob = mob; world = mob.worldObj; }
    boolean enabled() { return enabled; }
    boolean surface() { return enabled && (state == SURFACE || state == RETURNING); }
    boolean travelling() { return enabled && state != UNDERGROUND; }
    boolean hunting() { return !enabled || state == UNDERGROUND || state == SURFACE && !retreat; }
    boolean delivering() { return delivery; }
    String movementState() { return state == UNDERGROUND ? "underground" : state == EMERGING ? "emerging" : state == RETURNING ? "returning" : "surface"; }
    Vec3 approachDestination() { return pitApproach; }
    void carryHome() {
        if(!enabled) return;
        delivery = true;
        retreat = true; start(RETURNING);
    }
    double patrolX() { return hasHuntingGround && surface() ? huntingX : mob.nestX; }
    double patrolZ() { return hasHuntingGround && surface() ? huntingZ : mob.nestZ; }
    void enable() { enabled = true; }
    void resetHome() {
        clear(); tunnel.clear(); resetPit();
        if(state != UNDERGROUND || Math.abs(mob.posY - mob.nestY) > 4) { state = RETURNING; retreat = true; }
    }
    void huntingGround(double x, double z) { huntingX = x; huntingZ = z; hasHuntingGround = true; }

    /** Returns true while the commute owns movement. Captured prey always finishes on the existing controller. */
    boolean tick(boolean harvestHunting) {
        if(!enabled) return false;
        long time = (world.getWorldTime() % 24000L + 24000L) % 24000L;
        boolean night = !world.provider.hasNoSky && (time >= 13000 && time < 23000 || harvestHunting);
        if(!night && state != UNDERGROUND && !mob.pursuingPrey()) retreat = true;
        if(delivery && !mob.captureState()) delivery = false;
        if(mob.state() != EntityGardener.IDLE || mob.captureState() && (!delivery || !mob.readyToCarry())) return false;
        GardenerNests.Access access = GardenerNests.access(mob);
        if(access != null) return pitTick(access, night);
        if(night && state == RETURNING && failures >= 6 && !recoveringEmergence
                && mob.onGround && world.canBlockSeeTheSky(MathHelper.floor_double(mob.posX),
                        MathHelper.floor_double(mob.posY), MathHelper.floor_double(mob.posZ))) {
            delivery = false; retreat = false; arrived(SURFACE);
            mob.huntingDiagnostic("return-recovery", "Unregistered nest route unavailable; resuming nighttime surface hunting");
            return false;
        }
        pitTravel = false;
        if(state == UNDERGROUND && night) start(EMERGING);
        else if((state == SURFACE || state == EMERGING) && retreat && !mob.crossingSurfaceEdge()) start(RETURNING);
        if(state == UNDERGROUND || state == SURFACE) return false;
        if(retry > 0) { retry--; return true; }
        if(world.getTotalWorldTime() % 100 == 0) { protection.clear(); costs.clear(); }
        if(destination == null) {
            if(state == EMERGING) findExit(); else findHome();
            return true;
        }
        if(searching) search(); else travel();
        return true;
    }
    private void clear() {
        destination = null; route.clear(); nodes.clear(); costs.clear(); protection.clear(); open.clear();
        column = exitCandidate = visited = step = 0; searching = cachedTravel = false;
    }
    private void start(int next) {
        mob.stopSurfaceTravel(next == EMERGING ? "starting emergence" : delivery ? "carrying prey home" : "daytime retreat");
        clear(); state = next; retry = 0;
        resetPit();
        mob.huntingDiagnostic(next == EMERGING ? "emerging" : "returning",
                next == EMERGING ? "Starting emergence" : "Returning underground toward nest");
        if(next == EMERGING) {
            retreat = false; failures = 0;
            if(knownTunnel() && mob.getDistanceSq(tunnel.get(0).x + 0.5D, tunnel.get(0).y, tunnel.get(0).z + 0.5D) < 0.01D) {
                Node exit = tunnel.get(tunnel.size() - 1);
                if(landing(exit) && world.canBlockSeeTheSky(exit.x, exit.y, exit.z)) {
                    destination = exit; cachedTravel = true;
                    for(int i = 1; i < tunnel.size(); i++) route.add(tunnel.get(i));
                }
            }
        }
    }

    private void resetPit() {
        openingX = Double.NaN; pitApproach = null; pitStage = APPROACH; gripTicks = 0;
        passageBlocked = false; approachIndex = -1; pitHorizontal = pitVertical = 0;
    }
    /** Both directions use the saved entrance and the same checked horizontal and vertical legs. */
    private boolean pitTick(GardenerNests.Access access, boolean night) {
        if(!access.ready) return state != UNDERGROUND && state != SURFACE;
        if(!pitTravel) { clear(); tunnel.clear(); pitTravel = true; }
        if(state == UNDERGROUND && night) {
            if(retry > 0) { retry--; return false; }
            start(EMERGING);
        } else if(state == SURFACE && retreat && !mob.crossingSurfaceEdge()) start(RETURNING);
        if(state == UNDERGROUND || state == SURFACE) return false;

        boolean emerging = state == EMERGING;
        double feet = mob.boundingBox.minY;
        if(retry > 0) {
            if((pitStage >= OPENING && pitStage <= PASSAGE) || pitStage == BASE)
                access.renew(mob);
            retry--;
            return true;
        }
        if(Double.isNaN(openingX)) {
            boolean aligned = Math.abs(mob.posZ - access.laneZ) < 0.2D;
            boolean inShaft = aligned && Math.abs(mob.posX - access.shaftX) < 0.2D;
            boolean atRim = aligned && mob.posX >= access.openingX - 1.15D && mob.posX <= access.shaftX + 0.15D
                    && Math.abs(feet - access.top) < 0.12D;
            if(pitStage == APPROACH && inShaft && feet > access.y + 0.05D) pitStage = atRim ? UPPER : SHAFT;
            else if(pitStage == APPROACH && atRim && mob.posX > access.openingX + 1.1D) pitStage = UPPER;
            else if(pitStage == APPROACH && !emerging && Math.abs(feet - access.y) <= 0.15D
                    && (GardenerNests.inHabitat(mob.posX - access.x, mob.posZ - access.z, 0)
                    || mob.posX >= access.passageX && mob.posX <= access.shaftX + 1D
                        && (aligned || Math.abs(mob.posZ - access.passageZ) < 0.2D)))
                pitStage = mob.posX < access.passageX ? CHAMBER : inShaft ? BASE : PASSAGE;
            if(pitStage != CHAMBER && !selectOpening(access)) {
                boolean unloaded = !GardenerNests.loaded(world, body(access.shaftX, access.top, access.laneZ)
                        .addCoord(-12, 0, 0).expand(1, 0, 1));
                pitFailed(access, unloaded ? "entrance chunks unloaded" : "invalid entrance: no clear supported rim, shaft or passage",
                        unloaded ? "wait for loaded terrain" : "recheck saved opening");
                if(!unloaded && failures >= 6 && !emerging && pitStage == APPROACH && mob.onGround
                        && (delivery || night)) {
                    access.release(mob);
                    delivery = false;
                    arrived(SURFACE);
                    retreat = !night;
                    mob.huntingDiagnostic("invalid-nest", "Nest entrance is unusable; " + (mob.captureState()
                            ? "retaining captive for local feeding" : "resuming nighttime hunting"));
                }
                return true;
            }
        }

        if(emerging && pitStage == APPROACH) {
            // Leave the bottom of the shared passage free while waiting for an inbound resident.
            if(inPassage(access, mob.boundingBox)) {
                pitStage = PASSAGE;
                return true;
            }
            if(pitApproach == null) {
                int first = Math.floorMod(mob.getUniqueID().hashCode() + approachCursor, CHAMBER_APPROACHES.length);
                for(int i = 0; i < CHAMBER_APPROACHES.length && pitApproach == null; i++) {
                    int[] offset = CHAMBER_APPROACHES[(first + i) % CHAMBER_APPROACHES.length];
                    pitApproach = mob.surfaceApproach(access.x + offset[0], access.z + offset[1], access.y, 0D);
                }
                if(pitApproach == null) {
                    pitFailed(access, "no clear chamber waiting position", "wait for the chamber approach to clear");
                    return true;
                }
            }
            int result = walkPit(access, pitApproach.xCoord, pitApproach.yCoord, pitApproach.zCoord);
            if(result != 0) {
                pitApproach = null;
                if(result > 0) pitStage = PASSAGE;
                else approachCursor++;
            }
            return true;
        }
        if(!emerging && pitStage == APPROACH) {
            if(pitApproach == null) pitApproach = findApproach(access, false);
            if(pitApproach == null) {
                pitFailed(access, "no valid exterior approach", "try different approach positions");
                return true;
            }
            int result = mob.surfaceTravel(pitApproach.xCoord, pitApproach.yCoord, pitApproach.zCoord);
            if(result < 0) rejectApproach(access);
            else if(result > 0 && enterPassage(access, false)) stage(access, OPENING, "Exterior approach reached");
            return true;
        }
        if(pitStage == CHAMBER) {
            access.release(mob);
            if(walkPit(access, mob.nestX, mob.nestY, mob.nestZ) > 0) arrived(UNDERGROUND);
            return true;
        }
        if(pitStage != DISMOUNT && !enterPassage(access, true)) return true;

        double wallX = access.recessed ? -1D : 0D, wallZ = access.recessed ? 0D : -1D;
        double z = access.laneZ;
        if(emerging) {
            if(pitStage == PASSAGE) {
                if(walkPit(access, access.shaftX, access.y, z) > 0) stage(access, SHAFT, "Reached the base of the entrance");
            } else if(pitStage == SHAFT) {
                if(grip(access, wallX, wallZ, GardenerLocomotion.GRIP, 12)) {
                    mob.traversalPose(GardenerLocomotion.CLIMB, wallX, wallZ);
                    if(pitLeg(access, access.shaftX, access.top, z, 0.10D))
                        stage(access, UPPER, "Climbed to the upper entrance");
                }
            } else if(pitStage == UPPER) {
                mob.traversalPose(GardenerLocomotion.CREST, -1, 0);
                if(pitLeg(access, openingX, access.top, z, 0.10D))
                    stage(access, DISMOUNT, "Crossed the opening onto the rim");
            } else {
                if(pitApproach == null) pitApproach = findApproach(access, true);
                if(pitApproach == null) {
                    pitFailed(access, "no clear exterior dismount", "try another dismount");
                    return true;
                }
                if(inPassage(access, mob.boundingBox)) access.renew(mob);
                else access.release(mob);
                int result = mob.surfaceTravel(pitApproach.xCoord, pitApproach.yCoord, pitApproach.zCoord);
                if(result < 0) rejectApproach(access);
                else if(result > 0) { access.release(mob); arrived(SURFACE); }
            }
        } else if(pitStage == OPENING) {
            if(mob.getDistanceSq(openingX, access.top, z) < 9D)
                mob.traversalPose(GardenerLocomotion.APPROACH, 1, 0);
            int result = mob.surfaceTravel(openingX, access.top, z);
            if(result > 0) stage(access, UPPER, "Aligned at the opening");
            else if(result < 0) {
                access.release(mob); rejectApproach(access);
                pitStage = APPROACH; openingX = Double.NaN; openingCursor++;
            }
        } else if(pitStage == UPPER) {
            mob.traversalPose(GardenerLocomotion.ENTRY, 1, 0);
            if(gripTicks++ >= 12 && pitLeg(access, access.shaftX, access.top, z, 0.09D))
                stage(access, SHAFT, "Crossed the rim and aligned over the shaft");
        } else if(pitStage == SHAFT) {
            if(grip(access, wallX, wallZ, GardenerLocomotion.LOWER, 6)) {
                if(pitLeg(access, access.shaftX, access.y, z, 0.10D))
                    stage(access, BASE, "Descended to the passage floor");
            }
        } else if(pitStage == BASE) {
            if(pitLeg(access, access.shaftX, access.y, access.passageZ, 0.10D))
                stage(access, PASSAGE, "Aligned at the lower doorway");
        } else if(pitLeg(access, access.passageX, access.y, access.passageZ, 0.10D)) {
            access.release(mob);
            stage(access, CHAMBER, "Cleared the passage; walking to assigned chamber");
        }
        return true;
    }

    private boolean selectOpening(GardenerNests.Access access) {
        double z = access.laneZ;
        AxisAlignedBB base = body(access.shaftX, access.y, z);
        AxisAlignedBB doorway = body(access.shaftX, access.y, access.passageZ);
        // Connect the shaft to the passage through its actual door, including the offset in legacy pits.
        if(!pitClear(base.addCoord(0, access.top - access.y, 0))
                || !pitClear(base.addCoord(0, 0, access.passageZ - z))
                || !pitClear(doorway.addCoord(access.passageX - access.shaftX, 0, 0))
                || !mob.gardenerFooting(access.shaftX, access.y, z)
                || !mob.gardenerFooting(access.shaftX, access.y, access.passageZ)
                || !mob.gardenerFooting(access.passageX, access.y, access.passageZ)) return false;
        double wallX = access.recessed ? -1D : 0D, wallZ = access.recessed ? 0D : -1D;
        for(int y = access.y; y <= access.top; y += 2)
            if(!shaftContact(body(access.shaftX, y, z), wallX, wallZ)) return false;
        for(int i = 0; i < RIM_OFFSETS.length; i++) {
            int offset = RIM_OFFSETS[Math.floorMod(i + openingCursor, RIM_OFFSETS.length)];
            double x = access.openingX + offset;
            if(pitClear(body(x, access.top, z).addCoord(access.shaftX - x, 0, 0))
                    && mob.gardenerFooting(x, access.top, z)) {
                openingX = x; return true;
            }
        }
        return false;
    }

    private Vec3 findApproach(GardenerNests.Access access, boolean emerging) {
        long now = world.getTotalWorldTime();
        int first = Math.floorMod(mob.getUniqueID().hashCode() + approachCursor, APPROACH_OFFSETS.length);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for(int i = 0; i < APPROACH_OFFSETS.length; i++) {
            int index = (first + i) % APPROACH_OFFSETS.length;
            if(failedApproaches[index] > now) continue;
            int[] offset = APPROACH_OFFSETS[index];
            Vec3 point = mob.surfaceApproach(MathHelper.floor_double(openingX) + offset[0],
                    MathHelper.floor_double(access.laneZ) + offset[1], access.top, 6D);
            if(point == null) continue;
            double score = emerging ? Math.abs(offset[0]) + Math.abs(offset[1]) : mob.getDistanceSq(point.xCoord, point.yCoord, point.zCoord);
            if(score < bestScore) { best = point; bestScore = score; approachIndex = index; }
        }
        if(best != null) pitReport(access, "approach", "Selected exterior approach " + best);
        return best;
    }

    private void rejectApproach(GardenerNests.Access access) {
        if(approachIndex >= 0) failedApproaches[approachIndex] = world.getTotalWorldTime() + 400;
        approachCursor++;
        pitFailed(access, "approach path rejected or movement failed", "exclude approach and route from another direction");
        pitApproach = null;
    }

    private boolean enterPassage(GardenerNests.Access access, boolean inside) {
        passageBlocked = false;
        if(state == RETURNING && pitStage == UPPER && residentAhead(access)) {
            // Older saves can already contain overlapping bodies on the rim.
            // Let the resident closest to the shaft clear the lane first.
            access.release(mob);
            passageBlocked = true;
            pitReport(access, "blocked-entrance", "Blocked entrance: yielding to a Gardener ahead in the crossing");
            return false;
        }
        if(!access.renew(mob)) {
            AxisAlignedBB bounds = passageBounds(access);
            if(!inside || !inPassage(access, mob.boundingBox)) {
                for(Object entry : world.getEntitiesWithinAABB(EntityGardener.class, bounds)) {
                    EntityGardener other = (EntityGardener) entry;
                    if(other != mob && !other.isOcean() && other.isEntityAlive() && inPassage(access, other.boundingBox)) {
                        passageBlocked = true;
                        pitReport(access, "blocked-entrance", "Blocked entrance: Gardener " + other.getEntityId() + " occupies the passage; waiting");
                        return false;
                    }
                }
            }
            if(!access.claim(mob)) {
                passageBlocked = true;
                pitReport(access, "blocked-entrance", "Blocked entrance: another Gardener is traversing; waiting");
                return false;
            }
        }
        return true;
    }

    private boolean residentAhead(GardenerNests.Access access) {
        for(Object entry : world.getEntitiesWithinAABB(EntityGardener.class, mob.boundingBox)) {
            EntityGardener other = (EntityGardener) entry;
            if(other != mob && !other.isOcean() && other.isEntityAlive() && "returning".equals(other.surfaceMovementState())
                    && Math.abs(other.posZ - access.laneZ) < 0.2D
                    && Math.abs(other.boundingBox.minY - access.top) < 0.13D
                    && Math.abs(access.shaftX - other.posX) + 1E-6D < Math.abs(access.shaftX - mob.posX)) return true;
        }
        return false;
    }

    private AxisAlignedBB passageBounds(GardenerNests.Access access) {
        double z = access.laneZ;
        return AxisAlignedBB.getBoundingBox(access.passageX - mob.width / 2D, access.y, Math.min(z, access.passageZ) - mob.width / 2D,
                access.shaftX + mob.width / 2D, access.top + mob.height, Math.max(z, access.passageZ) + mob.width / 2D);
    }

    private boolean inPassage(GardenerNests.Access access, AxisAlignedBB box) {
        double z = access.laneZ;
        AxisAlignedBB base = body(access.shaftX, access.y, z);
        return box.intersectsWith(base.addCoord(0, access.top - access.y, 0))
                || box.intersectsWith(base.addCoord(0, 0, access.passageZ - z))
                || box.intersectsWith(body(access.shaftX, access.y, access.passageZ).addCoord(access.passageX - access.shaftX, 0, 0))
                || box.intersectsWith(body(access.shaftX, access.top, z).addCoord(openingX - access.shaftX, 0, 0));
    }

    private int walkPit(GardenerNests.Access access, double x, double y, double z) {
        int result = mob.surfaceTravel(x, y, z);
        if(result < 0) pitFailed(access, "passage or chamber navigation rejected", "retry connected route");
        return result;
    }

    private void stage(GardenerNests.Access access, int next, String reason) {
        pitStage = next; gripTicks = 0; failures = 0;
        pitReport(access, "commute-" + next, reason);
    }

    private boolean grip(GardenerNests.Access access, double x, double z, int pose, int duration) {
        mob.traversalPose(pose, x, z);
        if(!shaftContact(mob.boundingBox, x, z)) {
            pitFailed(access, "failed vertical transition: lost wall contact", "wait and recheck climbing face");
            return false;
        }
        return gripTicks++ >= duration;
    }

    private boolean shaftContact(AxisAlignedBB box, double x, double z) {
        double faceX = x < 0 ? box.minX : box.maxX, faceZ = z < 0 ? box.minZ : box.maxZ;
        AxisAlignedBB grip = AxisAlignedBB.getBoundingBox(
                x == 0 ? box.minX + 0.25D : Math.min(faceX, faceX + 1.8D * x),
                Math.max(0D, box.minY - 0.9D),
                z == 0 ? box.minZ + 0.25D : Math.min(faceZ, faceZ + 1.8D * z),
                x == 0 ? box.maxX - 0.25D : Math.max(faceX, faceX + 1.8D * x),
                box.maxY + 2.4D,
                z == 0 ? box.maxZ - 0.25D : Math.max(faceZ, faceZ + 1.8D * z));
        return GardenerNests.loaded(world, grip.expand(1, 0, 1)) && !world.func_147461_a(grip).isEmpty();
    }

    private boolean pitClear(AxisAlignedBB box) {
        return box.minY >= 0 && box.maxY <= world.getActualHeight() && GardenerNests.loaded(world, box.expand(1, 0, 1))
                && !world.isAnyLiquid(box) && world.func_147461_a(box).isEmpty();
    }

    private boolean pitLeg(GardenerNests.Access access, double x, double y, double z, double speed) {
        double dx = x - mob.posX, dy = y - mob.boundingBox.minY, dz = z - mob.posZ;
        double horizontal = Math.hypot(dx, dz);
        // Native horizontal collision treats even a tiny shortfall below the
        // rim as a wall. Finish each axis before starting the perpendicular leg.
        if((pitStage == SHAFT || pitStage == UPPER && state == RETURNING && dy <= 0) && horizontal >= 1E-7D) dy = 0;
        else if(dy != 0) dx = dz = 0;
        else if(horizontal < 1E-7D) return true;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = Math.min(1D, speed / length);
        double mx = dx * scale, my = dy * scale, mz = dz * scale;
        AxisAlignedBB sweep = mob.boundingBox.addCoord(mx, my, mz).contract(0.002D, 0.002D, 0.002D);
        if(!pitClear(sweep)) {
            pitFailed(access, Math.abs(my) > 0.01D ? "failed vertical transition: shaft obstructed" : "collision obstruction on entrance leg",
                    "wait and recheck passage");
            return false;
        }
        // When an old save already overlaps another resident at the rim, a
        // short leveling motion over the shaft lets the front resident move down and separate.
        boolean leveling = state == RETURNING && pitStage == UPPER && horizontal < 1E-7D
                && Math.abs(dy) <= 0.13D;
        if(!mob.gardenerSeparation(mob.posX + mx, mob.boundingBox.minY + my, mob.posZ + mz, leveling)) {
            passageBlocked = true;
            pitReport(access, "blocked-entrance", "Blocked entrance: Gardener on the active leg; waiting for clearance");
            return false;
        }
        double beforeX = mob.posX, beforeY = mob.boundingBox.minY, beforeZ = mob.posZ;
        mob.moveGardener(mx, my, mz);
        mob.fallDistance = 0;
        pitHorizontal = Math.hypot(mob.posX - beforeX, mob.posZ - beforeZ);
        pitVertical = mob.boundingBox.minY - beforeY;
        if(Math.hypot(pitHorizontal, pitVertical) < length * scale * 0.5D)
            pitFailed(access, "movement failed despite a valid path", "recheck native collision and retry");
        return false;
    }

    private void pitFailed(GardenerNests.Access access, String reason, String action) {
        failures = Math.min(16, failures + 1);
        retry = Math.min(160, 15 + 12 * failures);
        pitReport(access, "commute-failure", reason + "; path=rejected; recoveryAction=" + action);
        if(state == EMERGING && (pitStage == SHAFT || pitStage == UPPER) && failures >= 3) {
            state = RETURNING;
            recoveringEmergence = true;
            // Retreat along the shaft after re-aligning above it, if the crest was interrupted.
            pitStage = pitStage == UPPER ? UPPER : SHAFT;
            gripTicks = 0;
        }
    }

    private void pitReport(GardenerNests.Access access, String event, String message) {
        if(!mob.huntingDebugEnabled()) return;
        mob.huntingDiagnostic(event, message + diagnosticContext(access) + "; ai=" + movementState()
                + "; position=" + mob.posX + "," + mob.boundingBox.minY + "," + mob.posZ
                + "; destination=" + (state == EMERGING ? access.openingX + "," + access.top + "," + access.laneZ
                        : mob.nestX + "," + mob.nestY + "," + mob.nestZ)
                + "; horizontalDisplacement=" + pitHorizontal + "; verticalDisplacement=" + pitVertical
                + "; recoveries=" + failures + "; carryingPrey=" + mob.captureState());
    }

    String diagnosticContext() {
        return diagnosticContext(GardenerNests.access(mob));
    }

    private String diagnosticContext(GardenerNests.Access access) {
        return "; nest=" + (access == null ? mob.nestX + "," + mob.nestY + "," + mob.nestZ
                : access.x + "," + access.y + "," + access.z)
                + "; entrance=" + (access == null ? "-" : (Double.isNaN(openingX) ? access.openingX : openingX)
                        + "," + access.top + "," + access.laneZ)
                + "; approach=" + pitApproach + "; entranceWaypoint=" + pitStage
                + "; entranceObstructed=" + passageBlocked;
    }
    private void failed() {
        failed("no usable nest route or obstructed passage");
    }
    private void failed(String reason) {
        mob.huntingDiagnostic("commute-failure", "Nest navigation failed in state " + state + " stage " + pitStage
                + "; position=" + mob.posX + "," + mob.posY + "," + mob.posZ + "; reason=" + reason + "; retrying");
        if(cachedTravel) tunnel.clear(); // Changed terrain or a protection veto invalidates this corridor, not the home.
        clear(); failures = Math.min(16, failures + 1);
        retry = Math.min(1200, 40 << Math.min(5, failures));
    }
    private void arrived(int next) {
        boolean emerged = state == EMERGING && next == SURFACE;
        state = next; failures = 0; retry = 0; clear(); resetPit(); mob.stopSurfaceTravel("nest commute completed");
        if(next == UNDERGROUND) {
            retreat = false; delivery = false;
            if(recoveringEmergence) retry = 100;
            recoveringEmergence = false;
        }
        if(emerged) mob.emergedOnSurface();
        mob.huntingDiagnostic(next == SURFACE ? emerged ? "emerged" : "resumed" : "underground",
                next == SURFACE ? emerged ? "Successful emergence; surface hunting active" : "Resuming surface hunting" : "Returned underground");
    }
    private AxisAlignedBB body(double x, double y, double z) {
        return AxisAlignedBB.getBoundingBox(x - mob.width / 2D + 0.002D, y + 0.002D, z - mob.width / 2D + 0.002D,
                x + mob.width / 2D - 0.002D, y + mob.height - 0.002D, z + mob.width / 2D - 0.002D);
    }
    private boolean protectedAt(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Boolean value = protection.get(key);
        if(value == null) {
            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x, 1, z, x + 1, world.getHeight() - 1, z + 1);
            value = WretchCamps.hasPlayerPlacement(world, box) || GardenerNests.overlapsStructure(world, box);
            protection.put(key, value);
        }
        return value;
    }
    private boolean supported(AxisAlignedBB box) {
        int y = MathHelper.floor_double(box.minY - 0.01D);
        for(int corner = 0; corner < 4; corner++) {
            int x = MathHelper.floor_double(corner % 2 == 0 ? box.minX : box.maxX);
            int z = MathHelper.floor_double(corner < 2 ? box.minZ : box.maxZ);
            if(!World.doesBlockHaveSolidTopSurface(world, x, y, z)) return false;
        }
        return true;
    }
    private boolean landing(Node node) {
        AxisAlignedBB box = body(node.x + 0.5D, node.y, node.z + 0.5D);
        return GardenerNests.loaded(world, box.expand(2, 1, 2)) && supported(box) && !world.isAnyLiquid(box)
                && world.getCollidingBoundingBoxes(mob, box).isEmpty();
    }
    private boolean coveredHome(Node home) {
        int covered = 0;
        // Our own exit shaft can expose the center to the sky without destroying the underground room.
        for(int side = 0; side < 4; side++) {
            int x = home.x + (side == 0 ? 8 : side == 1 ? -8 : 0);
            int z = home.z + (side == 2 ? 8 : side == 3 ? -8 : 0);
            if(world.blockExists(x, 0, z) && world.getTopSolidOrLiquidBlock(x, z) >= home.y + 12) covered++;
        }
        return covered >= 2;
    }
    private void findExit() {
        // Center-out square rings prefer short routes. Four heightmap columns per tick, at most 2401 per search.
        for(int budget = 0; budget < 4 && column < 2401; budget++, column++) {
            int ring = (int) Math.ceil((Math.sqrt(column + 1D) - 1D) / 2D);
            int side = Math.max(1, ring * 2), offset = column - (ring == 0 ? 0 : (ring * 2 - 1) * (ring * 2 - 1));
            int edge = offset / side, along = offset % side;
            int dx = ring == 0 ? 0 : edge == 0 ? -ring + along : edge == 1 ? ring : edge == 2 ? ring - along : -ring;
            int dz = ring == 0 ? 0 : edge == 0 ? -ring : edge == 1 ? -ring + along : edge == 2 ? ring : ring - along;
            int x = MathHelper.floor_double(mob.nestX) + dx, z = MathHelper.floor_double(mob.nestZ) + dz;
            if(!world.blockExists(x, 0, z) || protectedAt(x, z)) continue;
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if(y < mob.nestY + 8 || y > Math.min(world.getHeight() - 10, mob.nestY + 128)) continue;
            Node node = new Node(x, y, z, null, 0, 0);
            if(!landing(node) || !world.canBlockSeeTheSky(x, y, z)) continue;
            // A dug shaft changes the heightmap. Its bottom is not a new surface landing.
            int outdoor = 0;
            for(int sideIndex = 0; sideIndex < 4; sideIndex++) {
                int sx = x + (sideIndex == 0 ? 4 : sideIndex == 1 ? -4 : 0);
                int sz = z + (sideIndex == 2 ? 4 : sideIndex == 3 ? -4 : 0);
                if(world.blockExists(sx, 0, sz) && Math.abs(world.getTopSolidOrLiquidBlock(sx, sz) - y) <= 2) outdoor++;
            }
            if(outdoor >= 2 && exitCandidate++ >= failures % 8) { plan(node); return; }
        }
        if(column >= 2401) failed();
    }
    private void findHome() {
        int x = MathHelper.floor_double(mob.nestX), y = MathHelper.floor_double(mob.nestY), z = MathHelper.floor_double(mob.nestZ);
        if(!world.blockExists(x, y, z)) { retry = 100; return; }
        if(knownTunnel()) {
            for(int i = tunnel.size() - 2; i > 0; i--) {
                Node point = tunnel.get(i);
                if(mob.getDistanceSq(point.x + 0.5D, point.y, point.z + 0.5D) <= 0.36D) {
                    reverseTunnel(i);
                    return;
                }
            }
            Node entrance = tunnel.get(tunnel.size() - 1);
            if(landing(entrance)) {
                int result = mob.surfaceTravel(entrance.x + 0.5D, entrance.y, entrance.z + 0.5D);
                if(result > 0) reverseTunnel(tunnel.size() - 1);
                else if(result < 0) failed("no connected surface path to saved tunnel opening");
                return;
            }
        }
        if(mob.boundingBox.minY > mob.nestY + 4D) { failed("invalid entrance: no usable saved tunnel"); return; }
        Node home = new Node(x, y, z, null, 0, 0);
        if((mob.hasSharedNest() || failures < 6) && landing(home) && coveredHome(home)) { plan(home); return; }
        if(mob.hasSharedNest()) { failed(); return; } // Shared residents retain their own reserved home, even while occupied.
        // The old room may have been destroyed or protected. Establish a loaded, natural replacement nearby.
        // Reuse the same assigned home on subsequent mornings; no world-generation job or second boss is created.
        for(int budget = 0; budget < 4; budget++) {
            int probe = recoveryProbe++ % 128;
            double angle = probe * 2.399963D;
            int radius = 6 + probe % 4 * 5;
            Node shelter = new Node(x + (int) (Math.cos(angle) * radius), MathHelper.clamp_int(y - probe % 3 * 3, 12, 42),
                    z + (int) (Math.sin(angle) * radius), null, 0, 0);
            AxisAlignedBB box = body(shelter.x + 0.5D, shelter.y, shelter.z + 0.5D);
            if(GardenerNests.loaded(world, box.expand(2, 1, 2)) && supported(box)
                    && !world.canBlockSeeTheSky(shelter.x, shelter.y + 8, shelter.z) && volumeCost(box) >= 0) {
                plan(shelter); return;
            }
        }
        if(recoveryProbe % 128 == 0) failed();
    }
    private void reverseTunnel(int from) {
        destination = tunnel.get(0); cachedTravel = true;
        for(int i = from - 1; i >= 0; i--) route.add(tunnel.get(i));
    }
    private void plan(Node goal) {
        destination = goal; searching = true;
        originX = MathHelper.floor_double(mob.posX); originY = MathHelper.floor_double(mob.posY); originZ = MathHelper.floor_double(mob.posZ);
        Node start = new Node(originX, originY, originZ, null, 0, heuristic(originX, originY, originZ));
        nodes.put(start.key(), start); open.add(start);
    }
    private double heuristic(int x, int y, int z) {
        // Maximum cell cost is seven. Favor direct progress instead of flooding cheap surface air above deep goals.
        return 8D * (Math.abs(x - destination.x) + Math.abs(y - destination.y) + Math.abs(z - destination.z));
    }
    private boolean passable(Block block, int x, int y, int z, AxisAlignedBB box) {
        if(block == Blocks.air) return true;
        if(block.getMaterial().isLiquid() || block.getMaterial() == net.minecraft.block.material.Material.portal
                || block == Blocks.fire || block == Blocks.web || block.hasTileEntity(world.getBlockMetadata(x, y, z))
                || world.getTileEntity(x, y, z) != null) return false;
        AxisAlignedBB collision = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        return collision == null || !collision.intersectsWith(box);
    }
    private int volumeCost(AxisAlignedBB box) {
        if(box.minY < 6 || box.maxY >= world.getHeight() - 1 || !GardenerNests.loaded(world, box.expand(1, 1, 1))) return -1;
        int solids = 0;
        for(int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
            for(int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++) {
                for(int y = MathHelper.floor_double(box.minY); y <= MathHelper.floor_double(box.maxY); y++) {
                    Block block = world.getBlock(x, y, z);
                    if(passable(block, x, y, z, box)) continue;
                    if(protectedAt(x, z)) return -1;
                    if(!GardenerNests.canExcavate(world, mob, x, y, z)) return -1;
                    // Never open a tunnel into a fluid pool beside its walls, floor or roof.
                    for(int side = 0; side < 6; side++) {
                        int sx = x + (side == 0 ? 1 : side == 1 ? -1 : 0), sy = y + (side == 2 ? 1 : side == 3 ? -1 : 0);
                        int sz = z + (side == 4 ? 1 : side == 5 ? -1 : 0);
                        if(world.getBlock(sx, sy, sz).getMaterial().isLiquid()) return -1;
                    }
                    solids++;
                }
            }
        return 1 + Math.min(6, (solids + 31) / 32);
    }
    private int cost(int x, int y, int z) {
        long key = key(x, y, z);
        Long blocked = denied.get(key);
        if(blocked != null && blocked > world.getTotalWorldTime()) return -1;
        if(Math.abs(x - originX) > Math.abs(destination.x - originX) + RADIUS
                || Math.abs(z - originZ) > Math.abs(destination.z - originZ) + RADIUS
                || y < Math.min(originY, destination.y) - 8 || y > Math.max(originY, destination.y) + 8) return -1;
        // Preserve the whole destination floor; arrive from the side instead of hollowing out the landing.
        if(y < destination.y && y + mob.height > destination.y - 1
                && Math.abs(x - destination.x) < mob.width && Math.abs(z - destination.z) < mob.width) return -1;
        int homeY = MathHelper.floor_double(mob.nestY);
        if(y < homeY && y + mob.height > homeY - 1 && Math.abs(x - MathHelper.floor_double(mob.nestX)) < mob.width
                && Math.abs(z - MathHelper.floor_double(mob.nestZ)) < mob.width) return -1;
        Integer cached = costs.get(key);
        if(cached != null) return cached;
        int value = volumeCost(body(x + 0.5D, y, z + 0.5D));
        if(costs.size() < NODE_LIMIT * 6) costs.put(key, value);
        return value;
    }
    private void search() {
        // Four expanded nodes per tick (24 burrowing or 48 walking probes). Graph and queue are finite.
        for(int budget = 0; budget < 4 && !open.isEmpty() && visited < NODE_LIMIT; budget++) {
            Node current = open.poll();
            if(nodes.get(current.key()) != current) continue;
            visited++;
            if(current.x == destination.x && current.y == destination.y && current.z == destination.z) {
                for(Node n = current; n.parent != null; n = n.parent) route.add(n);
                Collections.reverse(route); searching = false; nodes.clear(); open.clear(); costs.clear(); return;
            }
            for(int side = 0; side < 6; side++) {
                int x = current.x + (side == 0 ? 1 : side == 1 ? -1 : 0);
                int z = current.z + (side == 4 ? 1 : side == 5 ? -1 : 0);
                int y = current.y + (side == 2 ? 1 : side == 3 ? -1 : 0);
                int cost = cost(x, y, z);
                if(cost < 0) continue;
                Node old = nodes.get(key(x, y, z));
                int total = current.cost + cost;
                if(old != null && old.cost <= total || nodes.size() >= NODE_LIMIT || open.size() >= NODE_LIMIT * 2) continue;
                Node next = new Node(x, y, z, current, total, total + heuristic(x, y, z));
                nodes.put(next.key(), next); open.add(next);
            }
        }
        if(open.isEmpty() || visited >= NODE_LIMIT) failed();
    }
    private void travel() {
        if(step >= route.size()) {
            if(!landing(destination)) { failed(); return; }
            if(state == RETURNING) {
                mob.nestX = destination.x + 0.5D; mob.nestY = destination.y; mob.nestZ = destination.z + 0.5D;
                retreat = false;
                if(!knownTunnel()) tunnel.clear();
            } else {
                if(!cachedTravel && route.size() < 512) {
                    tunnel.clear(); tunnel.add(new Node(originX, originY, originZ, null, 0, 0));
                    for(Node n : route) tunnel.add(new Node(n.x, n.y, n.z, null, 0, 0));
                }
            }
            arrived(state == RETURNING ? UNDERGROUND : SURFACE); return;
        }
        Node next = route.get(step);
        double dx = next.x + 0.5D - mob.posX, dy = next.y - mob.posY, dz = next.z + 0.5D - mob.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(distance < 0.015D) { step++; return; }
        double speed = Math.min(0.08D, distance), mx = dx / distance * speed, my = dy / distance * speed, mz = dz / distance * speed;
        AxisAlignedBB swept = mob.boundingBox.addCoord(mx, my, mz).contract(0.002D, 0.002D, 0.002D);
        if(volumeCost(swept) < 0) { deny(next); return; }
        int removed = 0;
        for(int x = MathHelper.floor_double(swept.minX); x <= MathHelper.floor_double(swept.maxX); x++)
            for(int z = MathHelper.floor_double(swept.minZ); z <= MathHelper.floor_double(swept.maxZ); z++)
                for(int y = MathHelper.floor_double(swept.minY); y <= MathHelper.floor_double(swept.maxY); y++) {
                    if(passable(world.getBlock(x, y, z), x, y, z, swept)) continue;
                    if(!GardenerNests.excavate(world, mob, x, y, z)) { deny(next); return; }
                    if(world.getTotalWorldTime() % 8 == 0) world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(Blocks.stone));
                    if(++removed >= 4) return; // Four protected mutations per tick, never a whole shaft at once.
                }
        if(!world.getCollidingBoundingBoxes(mob, swept).isEmpty() || world.isAnyLiquid(swept)) { deny(next); return; }
        if(Math.abs(my) > 0.005D) mob.traversalPose(GardenerLocomotion.BURROW, mx, mz);
        double beforeX = mob.posX, beforeY = mob.posY, beforeZ = mob.posZ;
        mob.moveGardener(mx, my, mz); mob.fallDistance = 0;
        if(mob.getDistanceSq(beforeX, beforeY, beforeZ) < speed * speed * 0.25D) deny(next);
    }
    private void deny(Node node) {
        if(denied.size() >= 128) denied.clear();
        denied.put(node.key(), world.getTotalWorldTime() + 2400L); failed();
    }
    private boolean knownTunnel() {
        if(tunnel.size() < 2) return false;
        Node home = tunnel.get(0);
        return home.x == MathHelper.floor_double(mob.nestX) && home.y == MathHelper.floor_double(mob.nestY)
                && home.z == MathHelper.floor_double(mob.nestZ);
    }
    private static long key(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 34 | ((long) z & 0x3ffffffL) << 8 | y & 255L;
    }
    void write(NBTTagCompound nbt) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setBoolean("Enabled", enabled); tag.setInteger("State", state);
        tag.setBoolean("Retreat", retreat); tag.setInteger("Retry", retry); tag.setInteger("Failures", failures);
        if(pitTravel) tag.setInteger("EntranceStage", pitStage);
        tag.setBoolean("HasHuntingGround", hasHuntingGround); tag.setDouble("HuntingX", huntingX); tag.setDouble("HuntingZ", huntingZ);
        NBTTagList corridor = new NBTTagList();
        for(Node cell : tunnel) {
            NBTTagCompound point = new NBTTagCompound(); point.setInteger("X", cell.x); point.setInteger("Y", cell.y); point.setInteger("Z", cell.z);
            corridor.appendTag(point);
        }
        tag.setTag("Tunnel", corridor);
        nbt.setTag("NestCommute", tag);
    }
    void read(NBTTagCompound nbt) {
        NBTTagCompound tag = nbt.getCompoundTag("NestCommute"); enabled = tag.getBoolean("Enabled");
        state = MathHelper.clamp_int(tag.getInteger("State"), UNDERGROUND, RETURNING); retreat = tag.getBoolean("Retreat");
        retry = MathHelper.clamp_int(tag.getInteger("Retry"), 0, 1200); failures = MathHelper.clamp_int(tag.getInteger("Failures"), 0, 16);
        resetPit();
        if(state == RETURNING || state == EMERGING)
            pitStage = MathHelper.clamp_int(tag.getInteger("EntranceStage"), APPROACH, BASE);
        hasHuntingGround = tag.getBoolean("HasHuntingGround"); huntingX = tag.getDouble("HuntingX"); huntingZ = tag.getDouble("HuntingZ");
        tunnel.clear();
        NBTTagList corridor = tag.getTagList("Tunnel", 10);
        if(corridor.tagCount() <= 512) for(int i = 0; i < corridor.tagCount(); i++) {
            NBTTagCompound point = corridor.getCompoundTagAt(i);
            Node cell = new Node(point.getInteger("X"), point.getInteger("Y"), point.getInteger("Z"), null, 0, 0);
            Node before = tunnel.isEmpty() ? null : tunnel.get(tunnel.size() - 1);
            if(cell.y < 6 || cell.y > world.getHeight() - 9 || Math.abs((long) cell.x) > 29999900 || Math.abs((long) cell.z) > 29999900
                    || before != null && Math.abs(cell.x - before.x) + Math.abs(cell.y - before.y) + Math.abs(cell.z - before.z) != 1) {
                tunnel.clear(); break;
            }
            tunnel.add(cell);
        }
        clear(); // Re-plan active searches; saved corridor cells are revalidated during physical traversal.
    }
    private static final class Node {
        final int x, y, z, cost; final Node parent; final double score;
        Node(int x, int y, int z, Node parent, int cost, double score) {
            this.x = x; this.y = y; this.z = z; this.parent = parent; this.cost = cost; this.score = score;
        }
        long key() { return GardenerBurrowing.key(x, y, z); }
    }
}
