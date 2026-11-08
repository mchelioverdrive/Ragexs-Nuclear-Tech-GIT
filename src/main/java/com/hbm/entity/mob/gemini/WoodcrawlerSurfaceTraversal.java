package com.hbm.entity.mob.gemini;

import java.util.ArrayList;
import com.hbm.world.gemini.GardenerNests;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Collision-resolved wall/ladder attachment, including ledge entry and supported exits. */
final class WoodcrawlerSurfaceTraversal {
    private static final int NONE = 0, UP = 1, TOP = 2, DOWN = 3, LADDER = 4, ROOF = 5, DISMOUNT = 6;
    private final EntityWoodcrawler mob;
    private final World world;
    private int mode, nx, nz, ticks, blockedTicks, climbDelay, ladderDelay, landingTicks;
    private int ladderX, ladderZ, ladderBottom, ladderTop;
    private boolean ladderFound, barrierContact;
    private double plane, vx, vy, vz, exitX, exitY, exitZ, descentStartY;
    private double savedGoalX, savedGoalY, savedGoalZ;
    private double progressX, progressY, progressZ, climbX, climbY, climbZ;
    private int stalledTicks, climbNX, climbNZ, retryTicks;
    private boolean climbEpisode;
    private int approachScan, approachNX, approachNZ, dismountDelay;
    private boolean approachFound;
    private double approachX, approachY, approachZ, approachGoalX, approachGoalZ;

    WoodcrawlerSurfaceTraversal(EntityWoodcrawler mob) { this.mob = mob; world = mob.worldObj; }
    boolean active() { return mode != NONE; }

    private AxisAlignedBB body(double x, double y, double z) {
        double half = mob.width / 2D - 0.001D;
        return AxisAlignedBB.getBoundingBox(x - half, y + 0.001D, z - half, x + half, y + mob.height - 0.001D, z + half);
    }
    private boolean clear(AxisAlignedBB box) {
        return GardenerNests.loaded(world, box) && world.getCollidingBoundingBoxes(mob, box).isEmpty() && !world.isAnyLiquid(box);
    }
    private double floor(double x, double y, double z, double drop) {
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(x - 0.1D, y - drop, z - 0.1D, x + 0.1D, y + 0.08D, z + 0.1D);
        if(!GardenerNests.loaded(world, probe)) return Double.NaN;
        double top = Double.NaN;
        for(Object entry : world.getCollidingBoundingBoxes(mob, probe)) {
            AxisAlignedBB box = (AxisAlignedBB) entry;
            if(box.maxY <= y + 0.08D && (Double.isNaN(top) || box.maxY > top)) top = box.maxY;
        }
        return top;
    }
    private boolean ladder(int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z).isLadder(world, x, y, z, mob);
    }
    private void findLadder(double gx, double gy, double gz) {
        if(ladderDelay > 0) return;
        ladderDelay = 40; ladderFound = false;
        if(Math.abs(gy - mob.posY) < 1) return;
        int mx = MathHelper.floor_double(mob.posX), my = MathHelper.floor_double(mob.posY), mz = MathHelper.floor_double(mob.posZ);
        double best = Double.MAX_VALUE;
        for(int x = mx - 4; x <= mx + 4; x++) for(int z = mz - 4; z <= mz + 4; z++) for(int y = my - 2; y <= my + 2; y++) {
            if(!ladder(x, y, z)) continue;
            int low = y, high = y;
            while(low > y - 32 && ladder(x, low - 1, z)) low--;
            while(high < y + 32 && ladder(x, high + 1, z)) high++;
            if(gy > mob.posY ? high + 1 <= mob.posY + 0.5D : low >= mob.posY - 0.5D) continue;
            double score = mob.getDistanceSq(x + 0.5D, MathHelper.clamp_double(mob.posY, low, high + 1D), z + 0.5D)
                    + ((x + 0.5D - gx) * (x + 0.5D - gx) + (z + 0.5D - gz) * (z + 0.5D - gz)) * 0.1D;
            if(score < best) { best = score; ladderX = x; ladderZ = z; ladderBottom = low; ladderTop = high; ladderFound = true; }
            break;
        }
    }
    Vec3 waypoint(double gx, double gy, double gz) {
        findLadder(gx, gy, gz);
        Vec3 column = ladderFound && ladder(ladderX, MathHelper.clamp_int(MathHelper.floor_double(mob.posY), ladderBottom, ladderTop), ladderZ)
                && Math.abs(gy - mob.posY) >= 0.5D ? Vec3.createVectorHelper(ladderX + 0.5D,
                        MathHelper.clamp_double(mob.posY, ladderBottom, ladderTop + 1D), ladderZ + 0.5D) : null;
        Vec3 barrier = barrierWaypoint(column == null ? gx : column.xCoord, column == null ? gz : column.zCoord);
        return barrier != null ? barrier : column;
    }
    private boolean isBarrier(Block block) {
        return block instanceof BlockFence || block instanceof BlockFenceGate || block instanceof BlockWall;
    }
    boolean standingOnBarrier() {
        return barrierSupport(mob.posX, mob.boundingBox.minY, mob.posZ);
    }
    private boolean barrierSupport(double x, double feet, double z) {
        double half = mob.width / 2D - 0.001D;
        AxisAlignedBB support = AxisAlignedBB.getBoundingBox(x - half, feet - 0.15D, z - half, x + half, feet + 0.001D, z + half);
        if(!GardenerNests.loaded(world, support)) return false;
        for(Object entry : world.getCollidingBoundingBoxes(mob, support)) {
            AxisAlignedBB box = (AxisAlignedBB) entry;
            if(Math.abs(box.maxY - feet) < 0.08D && isBarrier(world.getBlock(MathHelper.floor_double((box.minX + box.maxX) * 0.5D),
                    MathHelper.floor_double((box.minY + box.maxY) * 0.5D), MathHelper.floor_double((box.minZ + box.maxZ) * 0.5D)))) return true;
        }
        return false;
    }
    private boolean dismount(double gx, double gz) {
        if(dismountDelay > 0) return false;
        dismountDelay = 20;
        double bearing = Math.atan2(gz - mob.posZ, gx - mob.posX), best = Double.MAX_VALUE;
        boolean found = false;
        for(int side = 0; side < 8; side++) {
            int turn = side == 7 ? 4 : (side + 1) / 2 * (side % 2 == 1 ? 1 : -1);
            double angle = bearing + turn * Math.PI / 4D;
            for(int radius = 0; radius < 3; radius++) {
                double distance = 0.8D + radius * 0.5D;
                double x = mob.posX + Math.cos(angle) * distance, z = mob.posZ + Math.sin(angle) * distance;
                double y = floor(x, mob.posY, z, 3.5D);
                if(Double.isNaN(y) || y > mob.posY - 0.2D || barrierSupport(x, y, z) || mob.isHazardAt(x, y, z)
                        || !clear(body(x, y, z)) || !dismountPathClear(x, z)) continue;
                double score = distance * distance + (mob.posY - y) * 0.25D + side * 0.75D
                        + ((gx - x) * (gx - x) + (gz - z) * (gz - z)) * 0.02D;
                if(score < best) { best = score; exitX = x; exitY = y; exitZ = z; found = true; }
            }
        }
        if(found) attach(DISMOUNT);
        return found;
    }
    private double overlap(AxisAlignedBB a, AxisAlignedBB b) {
        return Math.max(0, Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX))
                * Math.max(0, Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY))
                * Math.max(0, Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ));
    }
    private boolean dismountPathClear(double x, double z) {
        AxisAlignedBB start = body(mob.posX, mob.posY + 0.02D, mob.posZ);
        AxisAlignedBB route = start.addCoord(x - mob.posX, 0, z - mob.posZ);
        if(!GardenerNests.loaded(world, route)) return false;
        for(Object entry : world.getCollidingBoundingBoxes(mob, route)) {
            AxisAlignedBB obstacle = (AxisAlignedBB) entry;
            double previous = overlap(start, obstacle);
            if(previous == 0) return false;
            // A rail under an eave can already overlap the folded body's head.
            // Permit native movement out of that overlap, never deeper into it
            // or into a new obstacle, and require the whole body to end clear.
            for(int step = 1; step <= 4; step++) {
                double fraction = step / 4D;
                double next = overlap(body(mob.posX + (x - mob.posX) * fraction, mob.posY + 0.02D,
                        mob.posZ + (z - mob.posZ) * fraction), obstacle);
                if(next > previous + 0.000001D) return false;
                previous = next;
            }
            if(previous > 0.000001D) return false;
        }
        return true;
    }
    private Vec3 barrierWaypoint(double gx, double gz) {
        if((gx - approachGoalX) * (gx - approachGoalX) + (gz - approachGoalZ) * (gz - approachGoalZ) > 1D
                || Math.abs(mob.posY - approachY) > 0.5D) approachScan = 0;
        if(approachScan == 0) {
            approachScan = 10; approachFound = false;
            approachGoalX = gx; approachGoalZ = gz; approachY = mob.posY;
            double dx = gx - mob.posX, dz = gz - mob.posZ, distance = Math.sqrt(dx * dx + dz * dz);
            if(distance > 0.2D) {
                dx /= distance; dz /= distance;
                Vec3 from = Vec3.createVectorHelper(mob.posX, mob.posY + 0.5D, mob.posZ);
                Vec3 to = from.addVector(dx * Math.min(distance, 4D), 0, dz * Math.min(distance, 4D));
                AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(from.xCoord - 0.01D, from.yCoord - 0.01D, from.zCoord - 0.01D,
                        from.xCoord + 0.01D, from.yCoord + 0.01D, from.zCoord + 0.01D).addCoord(to.xCoord - from.xCoord, 0, to.zCoord - from.zCoord);
                if(GardenerNests.loaded(world, probe)) {
                    MovingObjectPosition closest = null;
                    Block obstacle = null;
                    double best = Double.MAX_VALUE;
                    for(Object entry : world.getCollidingBoundingBoxes(mob, probe)) {
                        AxisAlignedBB box = (AxisAlignedBB) entry;
                        MovingObjectPosition hit = box.calculateIntercept(from, to);
                        if(hit == null || from.squareDistanceTo(hit.hitVec) >= best) continue;
                        best = from.squareDistanceTo(hit.hitVec); closest = hit;
                        obstacle = world.getBlock(MathHelper.floor_double((box.minX + box.maxX) * 0.5D),
                                MathHelper.floor_double((box.minY + box.maxY) * 0.5D), MathHelper.floor_double((box.minZ + box.maxZ) * 0.5D));
                    }
                    // Ordinary native paths stop outside fence cells. Approach the first
                    // actual rail, while keeping solid walls and doors in charge of their routes.
                    if(closest != null && isBarrier(obstacle)) {
                        double advance = Math.min(Math.sqrt(best) + 0.1D, 2.2D);
                        approachX = mob.posX + dx * advance; approachZ = mob.posZ + dz * advance;
                        approachNX = closest.sideHit == 4 ? -1 : closest.sideHit == 5 ? 1 : 0;
                        approachNZ = closest.sideHit == 2 ? -1 : closest.sideHit == 3 ? 1 : 0;
                        approachFound = !mob.isHazardAt(approachX, approachY, approachZ)
                                && !mob.isHazardAt((mob.posX + approachX) * 0.5D, approachY, (mob.posZ + approachZ) * 0.5D);
                        for(double step = 0.4D; approachFound && step < Math.sqrt(best); step += 0.4D) {
                            double x = mob.posX + dx * step, z = mob.posZ + dz * step;
                            approachFound = !Double.isNaN(floor(x, mob.posY, z, 1.25D)) && !mob.isHazardAt(x, mob.posY, z);
                        }
                    }
                }
            }
        }
        if(!approachFound || !GardenerNests.loaded(world, body(mob.posX, mob.posY, mob.posZ)
                .addCoord(approachX - mob.posX, 0, approachZ - mob.posZ))) return null;
        return Vec3.createVectorHelper(approachX, approachY, approachZ);
    }

    private boolean wall(double reach, double bottom, double height) {
        AxisAlignedBB probe = body(mob.posX, bottom, mob.posZ);
        probe.minY = bottom; // Keep contact until the actual feet, not an inset probe, clear the lip.
        probe.maxY = bottom + height;
        probe = probe.addCoord(-nx * reach, 0, -nz * reach);
        if(!GardenerNests.loaded(world, probe)) return false;
        double closest = Double.MAX_VALUE, selected = 0;
        boolean selectedBarrier = false;
        // World reuses its collision list; the exposed-face checks below query it again.
        for(Object entry : new ArrayList<AxisAlignedBB>(world.getCollidingBoundingBoxes(mob, probe))) {
            AxisAlignedBB box = (AxisAlignedBB) entry;
            if(box.maxY <= bottom || box.minY >= bottom + height - 0.001D) continue;
            int bx = MathHelper.floor_double((box.minX + box.maxX) * 0.5D), by = MathHelper.floor_double((box.minY + box.maxY) * 0.5D);
            int bz = MathHelper.floor_double((box.minZ + box.maxZ) * 0.5D);
            Block block = world.getBlock(bx, by, bz);
            if(block instanceof BlockDoor) continue;
            boolean barrier = isBarrier(block);
            double face = nx > 0 ? box.maxX : nx < 0 ? box.minX : nz > 0 ? box.maxZ : box.minZ;
            double distance = (nx != 0 ? mob.posX - face : mob.posZ - face) * (nx + nz);
            if(distance < -reach || distance > reach + mob.width / 2D) continue;
            // Adjacent cubes have internal faces too. Attaching to one would pin the
            // creature beneath an eave instead of carrying it around the outside edge.
            AxisAlignedBB outside = box.contract(0.001D, 0.001D, 0.001D);
            if(nx > 0) { outside.minX = box.maxX + 0.001D; outside.maxX = box.maxX + 0.025D; }
            else if(nx < 0) { outside.minX = box.minX - 0.025D; outside.maxX = box.minX - 0.001D; }
            else if(nz > 0) { outside.minZ = box.maxZ + 0.001D; outside.maxZ = box.maxZ + 0.025D; }
            else { outside.minZ = box.minZ - 0.025D; outside.maxZ = box.minZ - 0.001D; }
            if(barrier) {
                // Connected rails overlap at corners. Only the local contact patch
                // needs to be exposed, not the whole face of every intersecting rail.
                if(nx != 0) {
                    double z = MathHelper.clamp_double(mob.posZ, box.minZ + 0.001D, box.maxZ - 0.001D);
                    outside.minZ = Math.max(box.minZ + 0.001D, z - 0.005D);
                    outside.maxZ = Math.min(box.maxZ - 0.001D, z + 0.005D);
                } else {
                    double x = MathHelper.clamp_double(mob.posX, box.minX + 0.001D, box.maxX - 0.001D);
                    outside.minX = Math.max(box.minX + 0.001D, x - 0.005D);
                    outside.maxX = Math.min(box.maxX - 0.001D, x + 0.005D);
                }
            }
            if(!GardenerNests.loaded(world, outside) || !world.getCollidingBoundingBoxes(mob, outside).isEmpty()) continue;
            if(distance < closest - 0.01D) { closest = distance; selected = face; selectedBarrier = barrier; }
        }
        if(closest == Double.MAX_VALUE) return false;
        plane = selected; barrierContact = selectedBarrier; return true;
    }
    private boolean topExit() {
        // Full blocks allow a broad landing. Fence rails and posts may only be
        // a quarter block wide, so retain support before crossing their far side.
        for(int sample = 0; sample < 3; sample++) {
            double inset = sample == 0 ? mob.width / 2D + 0.08D : sample == 1 ? 0.125D : 0.0625D;
            double x = nx == 0 ? mob.posX : plane - nx * inset;
            double z = nz == 0 ? mob.posZ : plane - nz * inset;
            if(!Double.isNaN(floor(x, mob.posY, z, 0.5D)) && clear(body(x, mob.posY, z))) {
                exitX = x; exitZ = z; return true;
            }
        }
        return false;
    }
    private void faceWall() {
        float yaw = (float) (Math.atan2(-nz, -nx) * 180D / Math.PI - 90D);
        mob.rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw - mob.rotationYaw), -20, 20);
        mob.renderYawOffset = mob.rotationYaw;
    }
    private void attach(int state) {
        if(mode == NONE) {
            climbEpisode = state == UP;
            if(climbEpisode) {
                climbX = mob.posX; climbY = mob.posY; climbZ = mob.posZ; climbNX = nx; climbNZ = nz;
            }
        }
        mode = state; ticks = blockedTicks = 0; mob.setCrouching(true); mob.getNavigator().clearPathEntity();
        progressX = mob.posX; progressY = mob.posY; progressZ = mob.posZ; stalledTicks = 0;
        if(state == DOWN) descentStartY = mob.posY;
        mob.setClimbing(true); mob.fallDistance = 0;
    }
    private void finish() {
        if(climbEpisode) retryTicks = 300;
        climbEpisode = false;
        mode = NONE; ladderFound = false; ladderDelay = 40; landingTicks = 4;
        approachFound = false; approachScan = 0;
        mob.motionX = mob.motionY = mob.motionZ = 0; mob.fallDistance = 0; mob.setClimbing(false);
        mob.getNavigator().clearPathEntity();
        mob.onSurfaceTraversalFinished();
    }
    private boolean repeatedClimb() {
        double dx = mob.posX - climbX, dz = mob.posZ - climbZ;
        return retryTicks > 0 && nx == climbNX && nz == climbNZ
                && Math.abs(mob.posY - climbY) < 1.25D && dx * dx + dz * dz < 9D;
    }

    boolean tick(double gx, double gy, double gz, boolean hasGoal, boolean allowed) {
        if(active()) { gx = savedGoalX; gy = savedGoalY; gz = savedGoalZ; }
        else if(hasGoal) { savedGoalX = gx; savedGoalY = gy; savedGoalZ = gz; }
        if(retryTicks > 0) retryTicks--;
        if(approachScan > 0) approachScan--;
        if(dismountDelay > 0) dismountDelay--;
        if(climbDelay > 0) climbDelay--;
        if(ladderDelay > 0) ladderDelay--;
        if(landingTicks > 0) { landingTicks--; mob.fallDistance = 0; }
        if(!allowed || mob.isInWater() || mob.handleLavaMovement()) { if(active()) finish(); return false; }
        if(mode == NONE && hasGoal) {
            findLadder(gx, gy, gz);
            if(ladderFound && Math.abs(gy - mob.posY) > 0.5D
                    && mob.getDistanceSq(ladderX + 0.5D, mob.posY, ladderZ + 0.5D) < 0.3D
                    && mob.posY >= ladderBottom - 0.2D && mob.posY <= ladderTop + 1.2D) attach(LADDER);
            if(mode == NONE && climbDelay == 0) {
                double dx = gx - mob.posX, dz = gz - mob.posZ;
                if(dx * dx + dz * dz > 0.1D) {
                    nx = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? -1 : 1) : 0;
                    nz = nx == 0 ? (dz > 0 ? -1 : 1) : 0;
                    if(approachFound && approachScan > 0 && Math.abs(approachNX) + Math.abs(approachNZ) == 1) {
                        nx = approachNX; nz = approachNZ;
                    }
                    if(!repeatedClimb() && wall(0.35D, mob.posY, mob.height)
                            && (gy >= mob.posY - 1D || barrierContact)
                            && clear(body(mob.posX, mob.posY + 0.25D, mob.posZ))) attach(UP);
                }
            }
        }
        if(mode == NONE) return false;
        vx = vy = vz = 0;
        double progress = (mob.posX - progressX) * (mob.posX - progressX) + (mob.posY - progressY) * (mob.posY - progressY)
                + (mob.posZ - progressZ) * (mob.posZ - progressZ);
        if(progress > 0.01D) { progressX = mob.posX; progressY = mob.posY; progressZ = mob.posZ; stalledTicks = 0; }
        else stalledTicks++;
        if(++ticks > 2400 || stalledTicks > 100 && (mode == UP || mode == TOP || mode == ROOF)) {
            if(mode == UP || mode == TOP || mode == ROOF) {
                climbDelay = 100;
                if(mob.onGround || !Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.18D))) { finish(); return false; }
                attach(DOWN);
            }
            gy = ladderBottom;
        }
        if(mode == ROOF) {
            // A roof shelf is part of the climb, not a chance for the ground path to reverse it.
            double dx = gx - mob.posX, dz = gz - mob.posZ, distance = Math.sqrt(dx * dx + dz * dz);
            if(distance < 0.6D && !Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.2D))) { finish(); return false; }
            if(distance > 0.01D) {
                vx = dx / distance * 0.12D; vz = dz / distance * 0.12D; vy = -0.08D;
                nx = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? -1 : 1) : 0;
                nz = nx == 0 ? (dz > 0 ? -1 : 1) : 0;
                if(wall(0.35D, mob.posY, mob.height) && clear(body(mob.posX, mob.posY + 0.18D, mob.posZ))) attach(UP);
            }
            if(mode == ROOF && Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.3D))) {
                nx = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0;
                nz = nx == 0 ? (dz > 0 ? 1 : -1) : 0;
                if(wall(1.35D, mob.posY - 0.5D, mob.height + 0.5D)) attach(DOWN);
                else { finish(); return false; }
            }
        }
        if(mode == DOWN && mob.onGround && stalledTicks > 8 && standingOnBarrier()) dismount(gx, gz);
        if(mode == DISMOUNT) {
            // A corner can support both sides of the body. Clear the complete
            // rail footprint toward a verified nearby landing, not just one face.
            vx = MathHelper.clamp_double(exitX - mob.posX, -0.1D, 0.1D);
            vz = MathHelper.clamp_double(exitZ - mob.posZ, -0.1D, 0.1D); vy = -0.12D;
            if(ticks > 100 || stalledTicks > 40 || mob.getDistanceSq(exitX, exitY, exitZ) < 0.04D && mob.onGround) {
                finish(); return false;
            }
        }
        if(mode == LADDER) {
            ladderMotion(gx, gy, gz);
        } else if(mode == DOWN) {
            double ground = floor(mob.posX, mob.posY, mob.posZ, 0.18D);
            if(!Double.isNaN(ground) && mob.posY < descentStartY - 0.1D
                    || mob.onGround && stalledTicks > 20 && !standingOnBarrier()) { finish(); return false; }
            if(!wall(1.35D, mob.posY - 0.5D, mob.height + 0.5D)) { finish(); return false; }
            vx = nx == 0 ? 0 : MathHelper.clamp_double(plane + nx * (mob.width / 2D + 0.025D) - mob.posX, -0.1D, 0.1D);
            vz = nz == 0 ? 0 : MathHelper.clamp_double(plane + nz * (mob.width / 2D + 0.025D) - mob.posZ, -0.1D, 0.1D);
            vy = -0.12D; faceWall();
        } else if(mode == UP) {
            if(wall(0.4D, mob.posY, mob.height)) {
                if(clear(body(mob.posX, mob.posY + 0.18D, mob.posZ))) {
                    blockedTicks = 0; vy = 0.16D;
                    vx = nx == 0 ? 0 : MathHelper.clamp_double(plane + nx * (mob.width / 2D + 0.015D) - mob.posX, -0.06D, 0.06D);
                    vz = nz == 0 ? 0 : MathHelper.clamp_double(plane + nz * (mob.width / 2D + 0.015D) - mob.posZ, -0.06D, 0.06D);
                } else if(++blockedTicks >= 80) { attach(DOWN); climbDelay = 100; }
                faceWall();
            } else {
                if(!topExit()) { attach(DOWN); }
                else { mode = TOP; ticks = 0; }
            }
        }
        if(mode == TOP) {
            vx = MathHelper.clamp_double(exitX - mob.posX, -0.1D, 0.1D);
            vz = MathHelper.clamp_double(exitZ - mob.posZ, -0.1D, 0.1D); vy = 0;
            if(mob.getDistanceSq(exitX, mob.posY, exitZ) < 0.01D && !Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.2D))) {
                mode = ROOF; ticks = 0; vx = vz = 0; vy = -0.08D;
            }
        }
        if(active()) { mob.motionX = vx; mob.motionY = vy; mob.motionZ = vz; mob.fallDistance = 0; mob.setClimbing(true); }
        return active();
    }
    private void ladderMotion(double gx, double gy, double gz) {
        double level = MathHelper.clamp_double(gy, ladderBottom, ladderTop + 1D);
        double target = level + (gy > mob.posY ? 0.04D : 0);
        if(Math.abs(target - mob.posY) > 0.05D) {
            if(!ladder(ladderX, MathHelper.clamp_int(MathHelper.floor_double(mob.posY), ladderBottom, ladderTop), ladderZ)) { finish(); return; }
            vx = MathHelper.clamp_double(ladderX + 0.5D - mob.posX, -0.08D, 0.08D);
            vz = MathHelper.clamp_double(ladderZ + 0.5D - mob.posZ, -0.08D, 0.08D);
            vy = MathHelper.clamp_double(target - mob.posY, -0.12D, 0.16D);
            if(!clear(body(mob.posX + vx, mob.posY + vy, mob.posZ + vz))) { if(++blockedTicks > 40) { finish(); climbDelay = 100; } }
            else blockedTicks = 0;
            return;
        }
        double best = Double.MAX_VALUE;
        boolean found = false;
        for(int side = 0; side < 4; side++) {
            double x = ladderX + 0.5D + (side == 0 ? 1 : side == 1 ? -1 : 0);
            double z = ladderZ + 0.5D + (side == 2 ? 1 : side == 3 ? -1 : 0);
            if(Double.isNaN(floor(x, mob.posY, z, 0.25D)) || !clear(body(x, mob.posY, z))) continue;
            double score = (x - gx) * (x - gx) + (z - gz) * (z - gz);
            if(score < best) { best = score; exitX = x; exitZ = z; found = true; }
        }
        if(!found) { if(++blockedTicks > 40) finish(); return; }
        vx = MathHelper.clamp_double(exitX - mob.posX, -0.1D, 0.1D);
        vz = MathHelper.clamp_double(exitZ - mob.posZ, -0.1D, 0.1D); vy = 0;
        if(mob.getDistanceSq(ladderX + 0.5D, mob.posY, ladderZ + 0.5D) > 0.5D
                && !Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.25D))) finish();
    }

    /** Called for the actual proposed native movement, after movement-helper acceleration. */
    void move(double x, double y, double z) {
        if(active() && mode != ROOF) {
            mob.moveSurfaceBody(vx, vy, vz); mob.fallDistance = 0; return;
        }
        if(mode == ROOF) { x = vx; y = vy; z = vz; }
        if(!mob.isBurrowing() && !mob.isGrabbing() && !mob.isProbing() && !mob.isInWater()
                && !(mob.hurtTime > 0 && y > 0.25D) && x * x + z * z > 0.0001D
                && !Double.isNaN(floor(mob.posX, mob.posY, mob.posZ, 0.2D))) {
            int steps = Math.max(1, MathHelper.ceiling_double_int(Math.sqrt(x * x + z * z) / 0.15D));
            double safeX = mob.posX, safeZ = mob.posZ;
            for(int step = 1; step <= steps; step++) {
                double px = mob.posX + x * step / steps, pz = mob.posZ + z * step / steps;
                if(!Double.isNaN(floor(px, mob.posY, pz, 1.25D))) { safeX = px; safeZ = pz; continue; }
                if(ladderFound && Math.abs(px - ladderX - 0.5D) < 0.5D && Math.abs(pz - ladderZ - 0.5D) < 0.5D
                        && ladder(ladderX, MathHelper.clamp_int(MathHelper.floor_double(mob.posY - 1), ladderBottom, ladderTop), ladderZ)) {
                    attach(LADDER); vx = MathHelper.clamp_double(ladderX + 0.5D - mob.posX, -0.1D, 0.1D);
                    vz = MathHelper.clamp_double(ladderZ + 0.5D - mob.posZ, -0.1D, 0.1D); vy = -0.12D;
                } else {
                    nx = Math.abs(x) >= Math.abs(z) ? (x > 0 ? 1 : -1) : 0;
                    nz = nx == 0 ? (z > 0 ? 1 : -1) : 0;
                    if(wall(1.35D, mob.posY - 2.5D, 1.5D)) {
                        // Verify a lower wall, but first get the whole body outside the actual
                        // roof edge. The wall may be recessed beneath a one-block eave.
                        double lowerPlane = plane;
                        if(!wall(1.35D, mob.posY - 0.5D, 0.5D)) plane = lowerPlane;
                        attach(DOWN);
                        vx = nx == 0 ? 0 : MathHelper.clamp_double(plane + nx * (mob.width / 2D + 0.025D) - mob.posX, -0.1D, 0.1D);
                        vz = nz == 0 ? 0 : MathHelper.clamp_double(plane + nz * (mob.width / 2D + 0.025D) - mob.posZ, -0.1D, 0.1D); vy = -0.12D;
                    } else {
                        mob.motionX = mob.motionZ = 0;
                        mob.moveSurfaceBody(safeX - mob.posX, Math.min(0, y), safeZ - mob.posZ); return;
                    }
                }
                mob.moveSurfaceBody(vx, vy, vz); mob.fallDistance = 0; return;
            }
        }
        mob.moveSurfaceBody(x, y, z);
    }
    void afterPhysics() {
        if(active()) { mob.motionX = vx; mob.motionY = vy; mob.motionZ = vz; mob.fallDistance = 0; }
        else if(landingTicks > 0) mob.fallDistance = 0;
    }
    void write(NBTTagCompound parent) {
        if(!active() && retryTicks == 0) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Mode", mode); tag.setInteger("NX", nx); tag.setInteger("NZ", nz);
        tag.setDouble("Plane", plane); tag.setDouble("StartY", descentStartY);
        tag.setDouble("ExitX", exitX); tag.setDouble("ExitY", exitY); tag.setDouble("ExitZ", exitZ);
        tag.setDouble("GoalX", savedGoalX); tag.setDouble("GoalY", savedGoalY); tag.setDouble("GoalZ", savedGoalZ);
        tag.setInteger("LadderX", ladderX); tag.setInteger("LadderZ", ladderZ);
        tag.setInteger("Bottom", ladderBottom); tag.setInteger("Top", ladderTop);
        tag.setBoolean("ClimbEpisode", climbEpisode); tag.setInteger("RetryTicks", retryTicks);
        tag.setDouble("ClimbX", climbX); tag.setDouble("ClimbY", climbY); tag.setDouble("ClimbZ", climbZ);
        tag.setInteger("ClimbNX", climbNX); tag.setInteger("ClimbNZ", climbNZ);
        parent.setTag("WoodcrawlerSurface", tag);
    }
    void read(NBTTagCompound parent) {
        if(!parent.hasKey("WoodcrawlerSurface", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("WoodcrawlerSurface");
        int state = tag.getInteger("Mode"), x = tag.getInteger("NX"), z = tag.getInteger("NZ");
        if(state < NONE || state > DISMOUNT || state != NONE && state != LADDER && Math.abs(x) + Math.abs(z) != 1) return;
        mode = state; nx = x; nz = z; plane = tag.getDouble("Plane"); descentStartY = tag.getDouble("StartY");
        exitX = tag.getDouble("ExitX"); exitY = tag.getDouble("ExitY"); exitZ = tag.getDouble("ExitZ");
        savedGoalX = tag.getDouble("GoalX"); savedGoalY = tag.getDouble("GoalY"); savedGoalZ = tag.getDouble("GoalZ");
        ladderX = tag.getInteger("LadderX"); ladderZ = tag.getInteger("LadderZ");
        ladderBottom = MathHelper.clamp_int(tag.getInteger("Bottom"), 0, 255);
        ladderTop = MathHelper.clamp_int(tag.getInteger("Top"), ladderBottom, 255);
        ladderFound = mode == LADDER;
        retryTicks = MathHelper.clamp_int(tag.getInteger("RetryTicks"), 0, 300);
        climbEpisode = tag.hasKey("ClimbEpisode") ? tag.getBoolean("ClimbEpisode") : mode == UP || mode == TOP || mode == ROOF;
        climbX = tag.hasKey("ClimbX") ? tag.getDouble("ClimbX") : mob.posX;
        climbY = tag.hasKey("ClimbY") ? tag.getDouble("ClimbY") : mob.posY;
        climbZ = tag.hasKey("ClimbZ") ? tag.getDouble("ClimbZ") : mob.posZ;
        climbNX = tag.hasKey("ClimbNX") ? tag.getInteger("ClimbNX") : nx;
        climbNZ = tag.hasKey("ClimbNZ") ? tag.getInteger("ClimbNZ") : nz;
        progressX = mob.posX; progressY = mob.posY; progressZ = mob.posZ;
        if(active()) { mob.setCrouching(true); mob.setClimbing(true); }
    }
}
