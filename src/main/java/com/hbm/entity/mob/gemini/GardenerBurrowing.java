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
import net.minecraft.world.World;

/** Server-owned nest commute. The full existing body excavates and moves progressively, with native collisions. */
final class GardenerBurrowing {
    private static final int UNDERGROUND = 0, EMERGING = 1, SURFACE = 2, RETURNING = 3;
    private static final int APPROACH = 0, SHAFT = 1, RIM = 2, LANDING = 3;
    private static final int NODE_LIMIT = 2048, RADIUS = 24;
    private final EntityGardener mob;
    private final World world;
    private boolean enabled, retreat, searching, hasHuntingGround, headingToEntrance, cachedTravel;
    private int state, retry, failures, column, exitCandidate, visited, step, originX, originY, originZ, recoveryProbe;
    private double huntingX, huntingZ;
    private boolean pitTravel, recoveringEmergence;
    private int pitStage, gripTicks;
    private boolean delivery;
    private int deliveryStall;
    private double deliveryX, deliveryY, deliveryZ;
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
    String movementState() { return delivery ? "carrying" : state == UNDERGROUND ? "underground" : state == EMERGING ? "emerging" : state == RETURNING ? "returning" : "surface"; }
    void carryHome() {
        if(!enabled) return;
        delivery = true; deliveryStall = 0;
        deliveryX = mob.posX; deliveryY = mob.posY; deliveryZ = mob.posZ;
        retreat = true; start(RETURNING);
    }
    double patrolX() { return hasHuntingGround && surface() ? huntingX : mob.nestX; }
    double patrolZ() { return hasHuntingGround && surface() ? huntingZ : mob.nestZ; }
    void enable() { enabled = true; }
    void resetHome() { clear(); tunnel.clear(); if(state != UNDERGROUND || Math.abs(mob.posY - mob.nestY) > 4) { state = RETURNING; retreat = true; } }
    void huntingGround(double x, double z) { huntingX = x; huntingZ = z; hasHuntingGround = true; }

    /** Returns true while the commute owns movement. Captured prey always finishes on the existing controller. */
    boolean tick() {
        if(!enabled) return false;
        long time = (world.getWorldTime() % 24000L + 24000L) % 24000L;
        boolean night = !world.provider.hasNoSky && time >= 13000 && time < 23000;
        if(!night && state != UNDERGROUND && !mob.pursuingPrey()) retreat = true;
        if(delivery) {
            if(!mob.captureState()) delivery = false;
            else {
                if(mob.getDistanceSq(deliveryX, deliveryY, deliveryZ) >= 1D) {
                    deliveryStall = 0; deliveryX = mob.posX; deliveryY = mob.posY; deliveryZ = mob.posZ;
                } else deliveryStall++;
                if(deliveryStall >= 1200) {
                    // Destroyed/blocked nests cannot hold a live victim forever. Keep the existing
                    // slot attached and let its original local feeding fallback finish safely.
                    delivery = false; state = SURFACE; retreat = !night; clear(); mob.stopSurfaceTravel();
                    mob.huntingDiagnostic("delivery-fallback", "Nest return stalled; retaining captive for local feeding");
                }
            }
        }
        if(mob.state() != EntityGardener.IDLE || mob.captureState() && (!delivery || !mob.readyToCarry())) return false;
        GardenerNests.Access access = GardenerNests.access(mob);
        if(night && state == RETURNING && !delivery && !recoveringEmergence && failures >= 4
                && (access != null ? mob.posY >= access.top - 2D && mob.posX < access.x + 47 && mob.onGround
                    : world.canBlockSeeTheSky(MathHelper.floor_double(mob.posX), MathHelper.floor_double(mob.posY), MathHelper.floor_double(mob.posZ)))) {
            // A ruined entrance must not suppress every subsequent night's hunt. Stay
            // physically aboveground and retry the same associated nest on the next retreat.
            retreat = false; arrived(SURFACE);
            mob.huntingDiagnostic("return-recovery", "Nest entrance unavailable; resuming nighttime surface hunting");
            return false;
        }
        if(access != null) return pitTick(access, night);
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
        column = exitCandidate = visited = step = 0; searching = headingToEntrance = cachedTravel = false;
    }
    private void start(int next) {
        mob.stopSurfaceTravel(next == EMERGING ? "starting emergence" : delivery ? "carrying prey home" : "daytime retreat");
        clear(); state = next; retry = 0;
        pitStage = APPROACH; gripTicks = 0;
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
    /** Registered residents use the constructed pit, never a second excavated commute. */
    private boolean pitTick(GardenerNests.Access access, boolean night) {
        if(!access.ready) return true;
        if(!pitTravel) { clear(); tunnel.clear(); pitTravel = true; pitStage = APPROACH; }
        if(state == UNDERGROUND && night) {
            if(retry > 0) { retry--; return false; }
            start(EMERGING);
        } else if((state == SURFACE || state == EMERGING) && retreat && !mob.crossingSurfaceEdge()) start(RETURNING);
        if(state == UNDERGROUND || state == SURFACE) return false;
        double shaftX = access.x + 51.5D, exitX = access.x + 43.5D, z = access.laneZ;
        double rim = access.top + (access.recessed ? 0D : 0.1D);
        double wallX = access.recessed ? -1D : 0D, wallZ = access.recessed ? 0D : -1D;
        double feet = mob.boundingBox.minY;
        boolean emerging = state == EMERGING;
        if(pitStage == APPROACH) {
            boolean aligned = Math.abs(mob.posZ - z) < 0.2D;
            boolean atShaft = aligned && Math.abs(mob.posX - shaftX) < 0.2D;
            boolean crossing = aligned && mob.posX >= exitX - 0.15D && mob.posX <= shaftX + 0.15D && Math.abs(feet - rim) < 0.15D;
            boolean atPassage = mob.posX >= access.x && mob.posX <= access.x + 63D
                    && Math.abs(mob.posZ - (access.z + 0.5D)) <= 7D;
            if(emerging) {
                if(crossing) pitStage = RIM;
                else if(atShaft && feet > access.y + 0.05D) { pitStage = SHAFT; gripTicks = 12; }
            } else if(atShaft && feet > access.y + 0.05D && feet < access.top - 0.05D) pitStage = RIM;
            else if(crossing && mob.posX > exitX + 0.1D) pitStage = SHAFT;
            else if(Math.abs(feet - access.y) <= 0.1D && (atPassage
                    || GardenerNests.inHabitat(mob.posX - access.x, mob.posZ - access.z, 0))) pitStage = LANDING;
        }
        // Chamber walks can run in parallel. Keep the queue outside the shared
        // throat so a returning resident does not wait on a distant approach.
        boolean entering = emerging && (pitStage > APPROACH
                || Math.abs(mob.posX - shaftX) < 12D && Math.abs(mob.posZ - z) < 10D);
        boolean leaving = !emerging && (pitStage == SHAFT || pitStage == RIM);
        if((entering || leaving) && !access.claim(mob)) return true;
        if(emerging && pitStage == SHAFT) mob.traversalPose(gripTicks < 12 ? GardenerLocomotion.GRIP : GardenerLocomotion.CLIMB, wallX, wallZ);
        if(emerging && pitStage >= RIM) mob.traversalPose(GardenerLocomotion.CREST, -1, 0);
        if(retry > 0) { retry--; return true; }
        if(emerging) {
            if(pitStage == APPROACH) {
                if(Math.abs(mob.posX - shaftX) < 2D && Math.abs(mob.posZ - z) < 1D)
                    mob.traversalPose(GardenerLocomotion.APPROACH, wallX, wallZ);
                pitWalk(shaftX, access.y, z);
            } else if(pitStage == SHAFT) {
                if(!shaftContact(wallX, wallZ)) {
                    // A changed wall cannot support a hanging body. Let native
                    // collision bring it down, then retry from a grounded approach.
                    if(feet > access.y + 0.05D && pitMove(0, -Math.min(0.35D, feet - access.y), 0, 0.35D))
                        return true;
                    if(feet <= access.y + 0.05D) {
                        pitStage = APPROACH; gripTicks = 0; access.release(mob);
                        failed("no climbable face within limb reach");
                    }
                } else if(gripTicks < 12) {
                    if(gripTicks++ == 0) mob.huntingDiagnostic("grip", "Establishing limb contact at the nest wall");
                } else {
                    if(gripTicks == 12) {
                        gripTicks++;
                        mob.huntingDiagnostic("climb", "Starting limb-assisted nest ascent");
                    }
                    if(pitLeg(shaftX, rim, z, 0.10D))
                        mob.huntingDiagnostic("opening", "Reached nest opening; crossing the supported rim");
                }
            } else if(pitStage == RIM) {
                pitLeg(exitX, rim, z, 0.08D);
            } else {
                AxisAlignedBB sole = mob.boundingBox.contract(0.002D, 0, 0.002D);
                sole = AxisAlignedBB.getBoundingBox(sole.minX, access.top - 0.025D, sole.minZ, sole.maxX, access.top + 0.002D, sole.maxZ);
                if(world.func_147461_a(sole).isEmpty()) { failed("exit lost its dismount support"); return true; }
                mob.moveGardener(0, -0.16D, 0);
                if(!mob.onGround) { failed("native exit landing did not settle"); return true; }
                access.release(mob); arrived(SURFACE);
            }
        } else if(pitStage == APPROACH) {
            int result = mob.surfaceTravel(exitX, access.top, z);
            if(result > 0) { mob.stopSurfaceTravel(); pitStage = SHAFT; }
            else if(result < 0) failures = Math.min(16, failures + 1);
        } else if(pitStage == SHAFT) {
            mob.traversalPose(GardenerLocomotion.CREST, -1, 0);
            if(feet < rim - 0.008D) pitMove(0, rim - feet, 0, 0.08D);
            else pitLeg(shaftX, rim, z, 0.08D);
        } else if(pitStage == RIM) {
            mob.traversalPose(GardenerLocomotion.CLIMB, wallX, wallZ);
            // Finish horizontal alignment before lowering the entire footprint
            // under the lip. The reverse commute uses the same shaft clearance.
            if(Math.abs(mob.posX - shaftX) > 0.015D || Math.abs(mob.posZ - z) > 0.015D) {
                double dx = shaftX - mob.posX, dz = z - mob.posZ;
                pitMove(dx, 0, dz, 0.08D);
            } else if(shaftContact(wallX, wallZ) || feet <= access.y + 0.1D) pitLeg(shaftX, access.y, z, 0.10D);
            else pitMove(0, -Math.min(0.35D, feet - access.y), 0, 0.35D);
        } else if(pitStage == LANDING) {
            access.release(mob);
            pitWalk(mob.nestX, mob.nestY, mob.nestZ);
        } else arrived(UNDERGROUND);
        return true;
    }

    private boolean shaftContact(double x, double z) {
        AxisAlignedBB box = mob.boundingBox;
        double faceX = mob.posX + x * mob.width / 2D, faceZ = mob.posZ + z * mob.width / 2D;
        AxisAlignedBB grip = AxisAlignedBB.getBoundingBox(
                x == 0 ? box.minX + 0.25D : Math.min(faceX, faceX + 1.8D * x),
                Math.max(0D, box.minY - 0.9D),
                z == 0 ? box.minZ + 0.25D : Math.min(faceZ, faceZ + 1.8D * z),
                x == 0 ? box.maxX - 0.25D : Math.max(faceX, faceX + 1.8D * x),
                box.maxY + 2.4D,
                z == 0 ? box.maxZ - 0.25D : Math.max(faceZ, faceZ + 1.8D * z));
        return GardenerNests.loaded(world, grip.expand(1, 0, 1)) && !world.func_147461_a(grip).isEmpty();
    }
    private boolean pitLeg(double x, double y, double z, double speed) {
        double dx = x - mob.posX, dy = y - mob.boundingBox.minY, dz = z - mob.posZ;
        if(dx * dx + dy * dy + dz * dz < 0.00000025D) { pitStage++; return true; }
        pitMove(dx, dy, dz, speed);
        return false;
    }
    private void pitWalk(double x, double y, double z) {
        if(destination == null) plan(new Node(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), null, 0, 0));
        else if(searching) search(); else travel();
    }
    private boolean pitMove(double dx, double dy, double dz, double speed) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(length < 1E-8D) return true;
        speed = Math.min(speed, length);
        double mx = dx / length * speed, my = dy / length * speed, mz = dz / length * speed;
        AxisAlignedBB sweep = mob.boundingBox.addCoord(mx, my, mz).contract(0.002, 0.002, 0.002);
        if(!GardenerNests.loaded(world, sweep.expand(1, 1, 1))) { failed("unloaded pit movement bounds"); return false; }
        if(world.isAnyLiquid(sweep)) { failed("liquid in pit movement bounds"); return false; }
        if(!world.getCollidingBoundingBoxes(mob, sweep).isEmpty()) {
            failed("collision on pit step " + mx + "," + my + "," + mz + "; body=" + mob.boundingBox); return false;
        }
        double beforeX = mob.posX, beforeY = mob.posY, beforeZ = mob.posZ;
        mob.moveGardener(mx, my, mz); mob.fallDistance = 0;
        if(mob.getDistanceSq(beforeX, beforeY, beforeZ) >= speed * speed * 0.25) return true;
        failed("native pit movement made insufficient progress");
        return false;
    }
    private void failed() {
        failed("no usable nest route or obstructed passage");
    }
    private void failed(String reason) {
        mob.huntingDiagnostic("commute-failure", "Nest navigation failed in state " + state + " stage " + pitStage
                + "; position=" + mob.posX + "," + mob.posY + "," + mob.posZ + "; reason=" + reason + "; retrying");
        if(cachedTravel) tunnel.clear(); // Changed terrain or a protection veto invalidates this corridor, not the home.
        clear(); failures = Math.min(16, failures + 1);
        retry = pitTravel ? Math.min(100, 20 * failures) : Math.min(1200, 40 << Math.min(5, failures));
        if(pitTravel && state == EMERGING && pitStage >= SHAFT && failures >= 3) {
            state = RETURNING;
            recoveringEmergence = true;
            // A blocked crest can be retraced at rim height before lowering into the pit.
            pitStage = pitStage == SHAFT ? RIM : SHAFT;
            mob.huntingDiagnostic("climb-recovery", "Nest exit blocked repeatedly; returning to the chamber before retrying");
        }
    }
    private void arrived(int next) {
        boolean emerged = state == EMERGING && next == SURFACE;
        state = next; failures = 0; retry = 0; clear(); mob.stopSurfaceTravel("nest commute completed");
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
        if(knownTunnel() && failures < 3) {
            Node entrance = tunnel.get(tunnel.size() - 1);
            if(landing(entrance)) { plan(entrance); headingToEntrance = true; return; }
        }
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
        if(pitTravel) return !world.isAnyLiquid(box) && world.getCollidingBoundingBoxes(mob, box).isEmpty()
                && !world.func_147461_a(box.getOffsetBoundingBox(0, -1, 0)).isEmpty() ? 1 : -1;
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
            for(int side = 0; side < (pitTravel ? 12 : 6); side++) {
                int direction = pitTravel ? side % 4 : side;
                int x = current.x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                int z = current.z + (direction == (pitTravel ? 2 : 4) ? 1 : direction == (pitTravel ? 3 : 5) ? -1 : 0);
                int y = current.y + (pitTravel ? side / 4 - 1 : side == 2 ? 1 : side == 3 ? -1 : 0);
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
            if(pitTravel) { pitStage++; clear(); return; }
            if(!landing(destination)) { failed(); return; }
            if(headingToEntrance && knownTunnel()) {
                clear(); destination = tunnel.get(0); cachedTravel = true;
                for(int i = tunnel.size() - 2; i >= 0; i--) route.add(tunnel.get(i));
                return;
            }
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
        if(pitTravel) { pitMove(dx, dy, dz, Math.min(0.08D, distance)); return; }
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
