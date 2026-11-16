package com.hbm.entity.mob.ai;

import java.util.List;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.world.gemini.GardenerNests;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Bounded, server-owned leap planning. Flight itself uses ordinary living-entity physics. */
final class WoodcrawlerLeap {
    private static final int WINDUP = 10, MAX_FLIGHT = 64;
    // Native dry airborne drag and gravity (EntityLivingBase.moveEntityWithHeading).
    private static final double DRAG = (double) 0.91F, VERTICAL_DRAG = (double) 0.98F, GRAVITY = 0.08D;
    private final EntityWoodcrawler mob;
    private final EntityAIWoodcrawler hunt;
    private final World world;
    private final double[] failedX = new double[6], failedY = new double[6], failedZ = new double[6];
    private final double[] failedFromX = new double[6], failedFromY = new double[6], failedFromZ = new double[6];
    private final int[] failedUntil = new int[6];
    private int cooldown, decisionDelay, windup, flightTicks, failureCursor, failures, launchCursor;
    private int trajectoryBudget;
    private boolean airborne, hit, collided;
    private EntityLivingBase prey;
    private Plan plan;
    private double aimX, aimY, aimZ, velocityX, velocityY, velocityZ;
    private double startX, startY, startZ;
    private double approachX, approachY, approachZ, approachAimX, approachAimY, approachAimZ;
    private int approachUntil;
    private EntityLivingBase approachPrey;

    private static final class Plan {
        double x, y, z, vx, vy, vz;
        int ticks;
    }

    WoodcrawlerLeap(EntityWoodcrawler mob, EntityAIWoodcrawler hunt) {
        this.mob = mob; this.hunt = hunt; world = mob.worldObj;
    }

    boolean active() { return windup > 0 || airborne; }
    void cooldown() {
        if(cooldown > 0) cooldown--;
        if(decisionDelay > 0) decisionDelay--;
    }
    void reset() {
        if(active()) cooldown = Math.max(cooldown, GeminiConfig.woodcrawlerLeapCooldownSeconds * 20);
        windup = flightTicks = 0; airborne = false; prey = null; plan = null;
        approachUntil = 0; approachPrey = null;
        mob.setLeapAnimation(0, false);
    }
    void write(NBTTagCompound tag) {
        tag.setInteger("WoodcrawlerLeapCooldown", Math.max(cooldown, active() ? GeminiConfig.woodcrawlerLeapCooldownSeconds * 20 : 0));
        tag.setBoolean("WoodcrawlerLeapInFlight", airborne);
    }
    void read(NBTTagCompound tag) {
        reset();
        cooldown = MathHelper.clamp_int(tag.getInteger("WoodcrawlerLeapCooldown"), 0, 1200);
        // Retain ordinary saved Motion, but never resume a windup, live prey or attack on reload.
        if(tag.getBoolean("WoodcrawlerLeapInFlight")) mob.suspendSurfaceForLeap();
    }

    /** Called with observed/remembered coordinates, never omniscient hidden-target coordinates. */
    boolean pursue(EntityLivingBase target, double x, double y, double z, double vx, double vy, double vz,
            boolean allowApproach) {
        if(airborne) return true;
        if(windup > 0) {
            if(target != prey || !eligible(target) || mob.avoidsExposure() || !launchable(mob.posX, mob.posY, mob.posZ)) {
                fail(); return false;
            }
            aimX = x; aimY = y; aimZ = z; velocityX = vx; velocityY = vy; velocityZ = vz;
            mob.getNavigator().clearPathEntity();
            mob.getLookHelper().setLookPosition(x, y + 1, z, 30, 30);
            mob.setLeapAnimation(windup, false);
            if(windup++ < WINDUP) return true;
            // Terrain, prey motion, and loaded chunks may all change during the visible preparation.
            trajectoryBudget = 12;
            Plan updated = solve(mob.posX, mob.posY, mob.posZ);
            if(updated == null) { fail(); return false; }
            plan = updated;
            mob.suspendSurfaceForLeap();
            windup = 0; airborne = true; hit = collided = false; flightTicks = 0;
            startX = mob.posX; startY = mob.posY; startZ = mob.posZ;
            mob.setLeapAnimation(0, true);
            mob.setClimbing(false); mob.onGround = false;
            mob.motionX = plan.vx; mob.motionY = plan.vy; mob.motionZ = plan.vz;
            mob.isAirBorne = mob.velocityChanged = true;
            cooldown = GeminiConfig.woodcrawlerLeapCooldownSeconds * 20;
            return true;
        }
        if(approachUntil > mob.ticksExisted) {
            if(!allowApproach || target != approachPrey || !eligible(target)
                    || square(x - approachAimX) + square(y - approachAimY) + square(z - approachAimZ) > 9) {
                approachUntil = 0; approachPrey = null;
            } else if(mob.getDistanceSq(approachX, approachY, approachZ) < 0.4D) {
                approachUntil = 0; decisionDelay = 0;
            } else if(!mob.getNavigator().noPath()) {
                mob.setMovementGoal(approachX, approachY, approachZ);
                return true;
            } else approachUntil = 0;
        }
        if(cooldown > 0 || decisionDelay > 0 || !eligible(target) || mob.canStrike(target)) return false;
        decisionDelay = 20;
        double horizontal = Math.hypot(x - mob.posX, z - mob.posZ);
        if(horizontal > GeminiConfig.woodcrawlerLeapMaxHorizontal + 3 || y - mob.posY > GeminiConfig.woodcrawlerLeapMaxUp + 2
                || mob.posY - y > GeminiConfig.woodcrawlerLeapMaxDown + 2
                || mob.getDistanceSq(x, y, z) < square(GeminiConfig.woodcrawlerLeapMinDistance)) return false;
        // Do not replace a short, complete ground route with an expensive or unnecessary leap.
        PathEntity walking = hunt.findLoadedPath(x, y, z);
        if(hunt.reaches(walking, x, y, z) && !hunt.pathHasClosedDoor(walking)
                && walking.getCurrentPathLength() <= Math.max(6, horizontal * 1.6D + 2)) return false;
        aimX = x; aimY = y; aimZ = z; velocityX = vx; velocityY = vy; velocityZ = vz;
        trajectoryBudget = 12;
        Plan direct = launchable(mob.posX, mob.posY, mob.posZ) ? solve(mob.posX, mob.posY, mob.posZ) : null;
        if(direct != null) {
            prey = target; plan = direct; windup = 1;
            startX = mob.posX; startY = mob.posY; startZ = mob.posZ;
            mob.onDoorInteraction(); mob.setCrouching(true); mob.setHuntState(EntityWoodcrawler.CHASE);
            mob.getNavigator().clearPathEntity(); mob.setLeapAnimation(1, false);
            return true;
        }
        if(!allowApproach || mob.isSurfaceTraversal() || !mob.onGround) return false;
        // One nearby takeoff per decision; rotate deterministically around the current position.
        int side = launchCursor++ % 8;
        double angle = Math.atan2(z - mob.posZ, x - mob.posX) + (side == 0 ? 0 : (side + 1) / 2 * (side % 2 == 0 ? -1 : 1) * Math.PI / 4);
        double lx = MathHelper.floor_double(mob.posX + Math.cos(angle) * 2) + 0.5D;
        double lz = MathHelper.floor_double(mob.posZ + Math.sin(angle) * 2) + 0.5D;
        double ly = floor(lx, mob.posY + 1, lz, 2);
        if(Double.isNaN(ly) || mob.getDistanceSq(lx, ly, lz) < 0.5D || !clear(body(lx, ly, lz))) return false;
        // Keep a separate small budget for approach evaluation, never scan a surrounding volume.
        trajectoryBudget = 6;
        if(solve(lx, ly, lz) == null) return false;
        PathEntity approach = hunt.findLoadedPath(lx, ly, lz);
        if(!hunt.reaches(approach, lx, ly, lz) || hunt.pathHasClosedDoor(approach) || approach.getCurrentPathLength() > 8) return false;
        mob.setMovementGoal(lx, ly, lz); mob.setCrouching(true);
        mob.getNavigator().setPath(approach, 2.5D);
        approachX = lx; approachY = ly; approachZ = lz;
        approachAimX = x; approachAimY = y; approachAimZ = z; approachPrey = target;
        approachUntil = mob.ticksExisted + 40;
        return true;
    }

    private boolean eligible(EntityLivingBase target) {
        return (target instanceof EntityPlayer || target instanceof EntityVillager) && mob.isPrey(target)
                && !mob.isBurrowing() && !mob.isCaptureBusy() && !mob.hasCapturedVillager() && !mob.isGrabbing()
                && !mob.isProbing() && !mob.isInWater() && !mob.handleLavaMovement() && mob.ridingEntity == null;
    }
    private boolean launchable(double x, double y, double z) {
        return clear(body(x, y, z)) && !mob.isHazardAt(x, y, z)
                && (mob.isSurfaceTraversal() && mob.hasLeapWallContact() || !Double.isNaN(floor(x, y, z, 0.2D)));
    }
    private boolean rejected(double x, double y, double z, double sx, double sy, double sz) {
        for(int i = 0; i < failedUntil.length; i++) if(failedUntil[i] > mob.ticksExisted
                && square(x - failedX[i]) + square(y - failedY[i]) + square(z - failedZ[i]) < 2.25D
                && square(sx - failedFromX[i]) + square(sy - failedFromY[i]) + square(sz - failedFromZ[i]) < 2.25D) return true;
        return false;
    }
    private void reject(double x, double y, double z, double sx, double sy, double sz) {
        int i = failureCursor++ % failedUntil.length;
        failedX[i] = x; failedY[i] = y; failedZ[i] = z; failedUntil[i] = mob.ticksExisted + 160;
        failedFromX[i] = sx; failedFromY[i] = sy; failedFromZ[i] = sz;
    }
    private void fail() {
        if(plan != null) reject(plan.x, plan.y, plan.z, startX, startY, startZ);
        failures = Math.min(4, failures + 1);
        reset(); cooldown = Math.max(cooldown, GeminiConfig.woodcrawlerLeapCooldownSeconds * 20 + failures * 20);
        hunt.onLeapFinished(false);
    }

    /** Three landing columns and at most twelve full sweeps per decision, fewer after algebraic rejection. */
    private Plan solve(double sx, double sy, double sz) {
        for(int candidate = 0; candidate < 3; candidate++) {
            // Clamp leading to a few blocks: uncertainty grows faster than useful prediction.
            double lead = candidate == 1 ? 0 : MathHelper.clamp_double(Math.hypot(aimX - sx, aimZ - sz) / 0.6D, 8, 24);
            double tx = aimX + MathHelper.clamp_double(velocityX * lead, -3, 3);
            double tz = aimZ + MathHelper.clamp_double(velocityZ * lead, -3, 3);
            if(candidate == 2) {
                double length = Math.max(0.1D, Math.hypot(tx - sx, tz - sz));
                tx -= (tx - sx) / length; tz -= (tz - sz) / length;
            }
            double ty = floor(tx, aimY + MathHelper.clamp_double(velocityY * lead, -1, 1) + 1, tz, 3);
            if(Double.isNaN(ty) || rejected(tx, ty, tz, sx, sy, sz) || !clear(body(tx, ty, tz)) || mob.isHazardAt(tx, ty, tz)) continue;
            double distance = Math.hypot(tx - sx, tz - sz), rise = ty - sy;
            if(distance > GeminiConfig.woodcrawlerLeapMaxHorizontal || rise > GeminiConfig.woodcrawlerLeapMaxUp
                    || -rise > GeminiConfig.woodcrawlerLeapMaxDown
                    || distance * distance + rise * rise < square(GeminiConfig.woodcrawlerLeapMinDistance)) continue;
            for(int ticks = 12; ticks <= 48; ticks += 4) {
                double horizontalSum = 0, verticalSum = 0, verticalOffset = 0, h = 1, v = 1, offset = 0;
                for(int t = 0; t < ticks; t++) {
                    horizontalSum += h; verticalSum += v; verticalOffset += offset;
                    h *= DRAG; v *= VERTICAL_DRAG; offset = (offset - GRAVITY) * VERTICAL_DRAG;
                }
                double vx = (tx - sx) / horizontalSum, vz = (tz - sz) / horizontalSum;
                // Aim slightly into the floor so the final sweep resolves a real downward contact.
                double vy = (rise - 0.08D - verticalOffset) / verticalSum;
                if(vy < 0.15D || vy > 1.6D || Math.hypot(vx, vz) > 1.6D || v * vy + offset >= -0.03D) continue;
                double y = 0, velocity = vy, apex = 0;
                for(int t = 0; t < ticks; t++) { y += velocity; apex = Math.max(apex, y); velocity = (velocity - GRAVITY) * VERTICAL_DRAG; }
                if(apex > GeminiConfig.woodcrawlerLeapMaxUp) continue;
                if(trajectoryBudget-- <= 0) return null;
                Plan result = new Plan(); result.x = tx; result.y = ty; result.z = tz;
                result.vx = vx; result.vy = vy; result.vz = vz; result.ticks = ticks;
                if(trajectory(sx, sy, sz, result)) return result;
            }
            reject(tx, ty, tz, sx, sy, sz);
        }
        return null;
    }

    private boolean trajectory(double x, double y, double z, Plan result) {
        AxisAlignedBB box = body(x, y, z);
        double vx = result.vx, vy = result.vy, vz = result.vz;
        for(int tick = 0; tick < result.ticks + 2; tick++) {
            AxisAlignedBB swept = box.addCoord(vx, vy, vz);
            if(!loaded(swept) || world.isAnyLiquid(swept)) return false;
            List<?> collisions = world.getCollidingBoundingBoxes(mob, swept);
            double dy = vy, dx = vx, dz = vz;
            for(Object entry : collisions) dy = ((AxisAlignedBB) entry).calculateYOffset(box, dy);
            box.offset(0, dy, 0);
            for(Object entry : collisions) dx = ((AxisAlignedBB) entry).calculateXOffset(box, dx);
            box.offset(dx, 0, 0);
            for(Object entry : collisions) dz = ((AxisAlignedBB) entry).calculateZOffset(box, dz);
            box.offset(0, 0, dz);
            if(Math.abs(dx - vx) > 0.00001D || Math.abs(dz - vz) > 0.00001D) return false;
            double px = (box.minX + box.maxX) * 0.5D, pz = (box.minZ + box.maxZ) * 0.5D;
            if(mob.isHazardAt(px, box.minY, pz)) return false;
            if(Math.abs(dy - vy) > 0.00001D) {
                return vy < 0 && tick >= result.ticks - 3 && Math.abs(box.minY - result.y) < 0.15D
                        && square(px - result.x) + square(pz - result.z) < 0.64D
                        && !Double.isNaN(floor(px, box.minY, pz, 0.15D));
            }
            vx *= DRAG; vz *= DRAG; vy = (vy - GRAVITY) * VERTICAL_DRAG;
            // Native living updates zero very small velocities before moving.
            if(Math.abs(vx) < 0.005D) vx = 0;
            if(Math.abs(vy) < 0.005D) vy = 0;
            if(Math.abs(vz) < 0.005D) vz = 0;
        }
        return false;
    }

    /** After the real native movement: attack once, detect impacts, release navigation on landing. */
    void afterPhysics() {
        if(!airborne) return;
        flightTicks++;
        if(!hit && prey == mob.getAttackTarget() && mob.isPrey(prey) && (mob.motionY <= 0.1D || flightTicks + 6 >= plan.ticks)) {
            // Capture/protection callbacks can reset the hunt (and this leap) synchronously.
            // Reserve contact first so callbacks cannot trigger another hit in this flight.
            hit = true;
            if(!mob.tryLeapStrike(prey)) hit = false;
            if(!airborne || plan == null) return;
        }
        if(mob.isCollidedHorizontally || mob.isCollidedVertically && !mob.onGround) collided = true;
        // A chunk can unload or a wall can be placed after planning. Stop at the loaded boundary.
        AxisAlignedBB next = mob.boundingBox.addCoord(mob.motionX, mob.motionY, mob.motionZ);
        if(!loaded(next)) { mob.motionX = mob.motionZ = 0; collided = true; }
        if(mob.onGround || mob.isInWater() || mob.handleLavaMovement() || flightTicks >= MAX_FLIGHT || !mob.isEntityAlive()) {
            boolean success = !collided && mob.onGround && mob.getDistanceSq(plan.x, plan.y, plan.z) < 2.25D;
            // No grab/capture cleanup here: its existing timeline owns the prey after contact.
            if(!success) { fail(); return; }
            failures = 0; reset(); hunt.onLeapFinished(true);
        } else if(Math.hypot(mob.posX - startX, mob.posZ - startZ) > GeminiConfig.woodcrawlerLeapMaxHorizontal + 1
                || mob.posY < startY - GeminiConfig.woodcrawlerLeapMaxDown - 1) {
            collided = true; mob.motionX = mob.motionZ = 0;
        }
    }

    boolean allowsMovement(double x, double y, double z) {
        if(GardenerNests.loaded(world, mob.boundingBox.addCoord(x, y, z).expand(1, 0, 1))) return true;
        collided = true;
        return false;
    }

    private AxisAlignedBB body(double x, double y, double z) {
        double half = EntityWoodcrawler.CRAWLING_WIDTH / 2D;
        return AxisAlignedBB.getBoundingBox(x - half, y, z - half, x + half, y + EntityWoodcrawler.CRAWLING_HEIGHT, z + half);
    }
    private boolean loaded(AxisAlignedBB box) {
        // Native collision queries include a one-block neighbor border (fences/modded geometry).
        return box.minY >= 0 && box.maxY < world.getHeight() && GardenerNests.loaded(world, box.expand(1, 0, 1));
    }
    private boolean clear(AxisAlignedBB box) {
        return loaded(box) && world.getCollidingBoundingBoxes(mob, box).isEmpty() && !world.isAnyLiquid(box);
    }
    private double floor(double x, double y, double z, double drop) {
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(x - 0.1D, y - drop, z - 0.1D, x + 0.1D, y + 0.05D, z + 0.1D);
        if(!loaded(probe)) return Double.NaN;
        double top = Double.NaN;
        for(Object entry : world.getCollidingBoundingBoxes(mob, probe)) {
            AxisAlignedBB box = (AxisAlignedBB) entry;
            if(box.maxY <= y + 0.05D && (Double.isNaN(top) || box.maxY > top)) top = box.maxY;
        }
        return top;
    }
    private static double square(double value) { return value * value; }
}
