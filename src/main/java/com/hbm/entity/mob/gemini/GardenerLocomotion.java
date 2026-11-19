package com.hbm.entity.mob.gemini;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import com.hbm.world.gemini.GardenerNests;

/** Distance-driven pose shared by rendering and the existing damageable limb segments. */
final class GardenerLocomotion {
    static final int WALK = 0, APPROACH = 1, GRIP = 2, CLIMB = 3, CREST = 4, BURROW = 5, PURSUE = 6, CARRY = 7;
    private static final double TAU = Math.PI * 2D, STANCE = 0.64D;
    // The six shoulders go around the body. Neighboring legs are on opposing tripods.
    private static final double[] PHASE = {0.00D, 0.54D, 0.08D, 0.50D, 0.04D, 0.58D};
    private final EntityGardener mob;
    private final double[] ground = new double[GardenerPose.ARMS];
    private final double[] previousGround = new double[GardenerPose.ARMS];
    private final double[] gripReach = new double[GardenerPose.ARMS];
    private final double[] gripHeight = new double[GardenerPose.ARMS];
    private final double[] previousGripReach = new double[GardenerPose.ARMS];
    private final double[] previousGripHeight = new double[GardenerPose.ARMS];
    private int mode, facing, checkpoint = -1;
    private double phase, previousPhase, phaseCorrection, speed, previousSpeed, burrowSpeed;
    private double heading, previousHeading, walkX, walkZ = -1D, previousWalkX, previousWalkZ = -1D;
    private double wallBlend, previousWallBlend, pitch, previousPitch, verticalSign = 1D;

    GardenerLocomotion(EntityGardener mob) {
        this.mob = mob;
        java.util.Arrays.fill(gripReach, 2.6D);
        java.util.Arrays.fill(gripHeight, 3D);
    }

    void command(int mode, double x, double z) {
        this.mode = mode;
        if(x != 0 || z != 0) facing = (int) Math.round(Math.atan2(z, x) * 256D / TAU) & 255;
    }

    boolean fullClimbPose() { return mode == GRIP || mode == CLIMB || mode == CREST; }

    void read(int packed) {
        mode = packed & 7;
        facing = packed >> 3 & 255;
        if(packed != checkpoint) {
            double observed = (packed >> 11 & 255) / 256D;
            if(checkpoint == -1) phase = observed;
            checkpoint = packed;
            double error = observed - phase;
            phaseCorrection = error - Math.rint(error);
        }
    }

    int snapshot(int previous) {
        int pose = mode | facing << 3;
        if((previous & 2047) != pose || (mob.ticksExisted + mob.getEntityId()) % 8 == 0)
            return pose | ((int) (unit(phase) * 256D) << 11);
        return previous;
    }

    void advance(double dx, double dy, double dz) {
        previousSpeed = speed;
        previousWallBlend = wallBlend;
        previousPitch = pitch;
        previousWalkX = walkX;
        previousWalkZ = walkZ;
        previousHeading = heading;
        heading += MathHelper.wrapAngleTo180_double(facing * 360D / 256D - heading) * (pitch < 0.2D ? 1D : 0.28D);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // Ignore placement/teleport corrections. The renderer follows native entity interpolation.
        if(horizontal > 0.003D && horizontal <= 0.65D) {
            walkX += (dx / horizontal - walkX) * 0.6D;
            walkZ += (dz / horizontal - walkZ) * 0.6D;
        }
        double strideSpeed = horizontal > 0.003D && horizontal <= 0.65D ? horizontal : 0;
        speed += (strideSpeed - speed) * 0.35D;
        if(speed < 0.002D) speed = 0;
        boolean movingVertically = mode == CLIMB || mode == BURROW;
        double vertical = movingVertically && Math.abs(dy) <= 0.65D ? Math.abs(dy) : 0;
        if(vertical > 0.003D) verticalSign = Math.signum(dy);
        burrowSpeed += ((mode == BURROW ? vertical : 0) - burrowSpeed) * 0.3D;
        phase = unit(phase);
        previousPhase = phase;
        double advance = strideSpeed / strideLength(speed) + vertical / 2.0D;
        double correction = MathHelper.clamp_double(phaseCorrection * 0.15D, -advance * 0.3D, advance * 0.3D);
        phase += advance + correction;
        phaseCorrection -= correction;

        double bracing = mode == APPROACH ? 0.45D : mode == GRIP || mode == CLIMB ? 1D : mode == CREST ? 0.72D : 0;
        wallBlend += (bracing - wallBlend) * 0.22D;
        double wantedPitch = mode == CLIMB ? 19D : mode == GRIP ? 12D : mode == CREST ? 7D : mode == APPROACH ? 4D : 0D;
        pitch += (wantedPitch - pitch) * 0.22D;
        // Stagger local collision probes across limbs and residents. Render frames only read the cache.
        int first = 2 * ((mob.ticksExisted + mob.getEntityId()) % 3);
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) {
            previousGround[arm] = ground[arm];
            previousGripReach[arm] = gripReach[arm];
            previousGripHeight[arm] = gripHeight[arm];
            if(arm < first || arm >= first + 2 || !mob.isAwake() || mob.armDisabled(arm)) continue;
            double angle = GardenerPose.angle(arm);
            double extension = mob.limbExtension(arm);
            double stride = forwardOffset(arm, 1F);
            double radius = GardenerPose.tipRadius(arm);
            double x = mob.posX + Math.cos(angle) * (2.9D + (radius - 2.9D) * extension) + walkX * stride * extension;
            double z = mob.posZ + Math.sin(angle) * (2.9D + (radius - 2.9D) * extension) + walkZ * stride * extension;
            double floor = floor(x, z, mob.boundingBox.minY, 2.1D, 2.6D);
            ground[arm] += ((Double.isNaN(floor) ? 0 : floor) - ground[arm]) * 0.55D;
            if(bracing > 0) sampleGrip(arm);
        }
    }

    private double floor(double x, double z, double feet, double above, double below) {
        double top = feet - below;
        AxisAlignedBB column = AxisAlignedBB.getBoundingBox(x - 0.15D, top, z - 0.15D, x + 0.15D, feet + above, z + 0.15D);
        if(!GardenerNests.loaded(mob.worldObj, column)) return Double.NaN;
        for(Object entry : mob.worldObj.func_147461_a(column)) {
            double y = ((AxisAlignedBB) entry).maxY;
            if(y <= feet + above && y > top) top = y;
        }
        return top > feet - below ? top - feet : Double.NaN;
    }

    private double front(int arm) {
        return Math.cos(GardenerPose.angle(arm) - facing * TAU / 256D);
    }

    private double lateral(int arm) {
        return Math.sin(GardenerPose.angle(arm) - facing * TAU / 256D) * 2.8D;
    }

    private void sampleGrip(int arm) {
        double fx = wallX(), fz = wallZ(), side = lateral(arm);
        double x = mob.posX - fz * side, z = mob.posZ + fx * side;
        double y = 3.7D + front(arm) * 2.5D + verticalSign * cycleStride(arm, 1F) * 0.9D;
        double forward = 3.8D + front(arm) * 1.3D;
        if(mode == CREST) {
            double landing = floor(x + fx * forward, z + fz * forward, mob.boundingBox.minY, 1.6D, 0.8D);
            updateGrip(arm, Double.isNaN(landing) ? 2.6D : forward, Double.isNaN(landing) ? 2.2D : landing + 0.35D);
            return;
        }
        double reach = Math.max(2.5D, 6.2D + front(arm) * 1.4D);
        double startY = mob.posY + y;
        double endY = startY + (mode == CLIMB ? 0.6D : 3.8D);
        MovingObjectPosition hit = null;
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(x, mob.posY - 0.75D, z, x, endY + 4D, z).addCoord(fx * reach, 0, fz * reach);
        if(GardenerNests.loaded(mob.worldObj, probe)) {
            hit = mob.worldObj.func_147447_a(Vec3.createVectorHelper(x, startY, z),
                    Vec3.createVectorHelper(x + fx * reach, endY, z + fz * reach), false, true, false);
            if(hit == null && mode == CLIMB) {
                hit = mob.worldObj.func_147447_a(Vec3.createVectorHelper(x, startY, z),
                        Vec3.createVectorHelper(x + fx * reach, endY + 4D, z + fz * reach), false, true, false);
            }
            if(hit == null && mode == CLIMB) {
                // The last pull still grips the rim beneath the torso.
                hit = mob.worldObj.func_147447_a(Vec3.createVectorHelper(x, mob.posY + 1.2D, z),
                        Vec3.createVectorHelper(x + fx * reach, mob.posY - 0.75D, z + fz * reach), false, true, false);
            }
        }
        updateGrip(arm, hit == null ? Math.min(forward, 2.65D) : Math.max(0.6D,
                (hit.hitVec.xCoord - x) * fx + (hit.hitVec.zCoord - z) * fz - GardenerPose.RADIUS),
                hit == null ? y : hit.hitVec.yCoord - mob.posY + 0.12D);
    }

    private void updateGrip(int arm, double reach, double height) {
        gripReach[arm] += (reach - gripReach[arm]) * 0.65D;
        gripHeight[arm] += (height - gripHeight[arm]) * 0.65D;
    }

    private static double unit(double value) { return value - Math.floor(value); }
    private double cycle(int arm, float partial) { return unit(lerp(previousPhase, phase, partial) + PHASE[arm]); }
    private static double strideLength(double speed) { return 2.5D + Math.min(0.8D, speed * 5D); }
    private double cycleStride(int arm, float partial) {
        double cycle = cycle(arm, partial);
        return cycle < STANCE ? 1D - 2D * cycle / STANCE : -Math.cos(Math.PI * (cycle - STANCE) / (1D - STANCE));
    }
    private double lift(int arm, float partial) {
        double swing = Math.max(0D, (cycle(arm, partial) - STANCE) / (1D - STANCE));
        double arch = Math.sin(Math.PI * swing);
        return arch * arch;
    }
    private double intensity(float partial) { return Math.min(1D, lerp(previousSpeed, speed, partial) / 0.055D); }
    private double forwardOffset(int arm, float partial) {
        return cycleStride(arm, partial) * strideLength(lerp(previousSpeed, speed, partial)) * STANCE / 2D * intensity(partial);
    }
    private double wallX() { return Math.cos(facing * TAU / 256D); }
    private double wallZ() { return Math.sin(facing * TAU / 256D); }
    double tiltX(float partial) { return Math.cos(Math.toRadians(lerp(previousHeading, heading, partial))); }
    double tiltZ(float partial) { return Math.sin(Math.toRadians(lerp(previousHeading, heading, partial))); }
    double pitch(float partial) { return lerp(previousPitch, pitch, partial); }
    double bodyBob(float partial) {
        return Math.cos(lerp(previousPhase, phase, partial) * TAU * 2D) * 0.15D * intensity(partial);
    }

    Vec3 joint(int arm, double t, double x, double y, double z, float partial, double extension) {
        double turn = Math.toRadians(pitch(partial));
        double rootY = 4.3D + mob.bodyShift(partial);
        double tipWeight = t * t;
        double wall = lerp(previousWallBlend, wallBlend, partial);
        if(turn > 0.0002D && t < 1D) {
            double fx = tiltX(partial), fz = tiltZ(partial);
            double forward = x * fx + z * fz;
            double shoulderTurn = (1D - t) * (forward * (Math.cos(turn) - 1D) - (y - rootY) * Math.sin(turn));
            x += fx * shoulderTurn;
            z += fz * shoulderTurn;
            y += (1D - t) * (forward * Math.sin(turn) + (y - rootY) * (Math.cos(turn) - 1D));
        }
        if(t == 0) return Vec3.createVectorHelper(x, y, z);

        double stride = forwardOffset(arm, partial);
        double swing = lift(arm, partial);
        double mx = lerp(previousWalkX, walkX, partial), mz = lerp(previousWalkZ, walkZ, partial);
        double angle = GardenerPose.angle(arm);
        double front = mode == PURSUE ? Math.max(0D, Math.cos(angle) * mx + Math.sin(angle) * mz) : 0D;
        double advance = (stride + front * 0.65D * intensity(partial)) * tipWeight * extension;
        double height = lerp(previousGround[arm], ground[arm], partial) * tipWeight
                + swing * intensity(partial) * (0.85D + Math.sin(Math.PI * t) * 0.9D) * t;
        if(mode == CARRY) height *= 0.85D;
        double walk = 1D - wall;
        x += mx * advance * walk;
        z += mz * advance * walk;
        y += (height + Math.min(0.7D, burrowSpeed * 8D) * tipWeight * swing) * walk * extension;
        if(wall > 0.002D) {
            double fx = wallX(), fz = wallZ();
            double brace = wall * (mode == APPROACH ? 0.45D + Math.max(0D, front(arm)) * 0.55D : 1D);
            double side = lateral(arm);
            double reach = lerp(previousGripReach[arm], gripReach[arm], partial) - (mode == CLIMB ? 0.8D * swing : 0D);
            double tx = fx * reach - fz * side;
            double tz = fz * reach + fx * side;
            double arch = Math.sin(Math.PI * t);
            double hx = Math.cos(angle) * 2.7D * (1D - t) + tx * t - fz * side * 0.24D * arch;
            double hz = Math.sin(angle) * 2.3D * (1D - t) + tz * t + fx * side * 0.24D * arch;
            double hy = rootY * (1D - t) + (lerp(previousGripHeight[arm], gripHeight[arm], partial)
                    + (mode == CLIMB ? swing * 0.8D : 0D)) * t + arch * 1.8D;
            x += (hx - x) * brace;
            z += (hz - z) * brace;
            y += (hy - y) * brace;
        }
        return Vec3.createVectorHelper(x, y, z);
    }

    private static double lerp(double before, double after, float partial) { return before + (after - before) * partial; }
}
