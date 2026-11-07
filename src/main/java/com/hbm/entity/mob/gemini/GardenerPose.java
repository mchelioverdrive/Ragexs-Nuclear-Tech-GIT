package com.hbm.entity.mob.gemini;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

/** Shared world-space skeleton. Rendering, limb damage and attacks use the same segments. */
public final class GardenerPose {
    public static final int ARMS = 6, SEGMENTS = 4;
    public static final double RADIUS = 0.28D;
    private GardenerPose() { }

    private static final double[] RADIAL = {2.9, 5.8, 8.8, 10.5, 13.2};
    private static final double[] HEIGHT = {4.3, 8.8, 7.6, 2.7, 0.35};
    private static final double[] ANGLE = {0.15, 1.10, 2.25, 3.28, 4.24, 5.43};

    public static Vec3 point(EntityGardener boss, int arm, int joint, float partial) {
        if(boss.isOcean()) return OceanGardenerPose.point(boss, arm, joint, partial);
        double t = joint / (double) SEGMENTS, angle = ANGLE[arm];
        double age = boss.isAwake() ? boss.worldObj.getTotalWorldTime() + partial : 0;
        double cycle = age * 0.075D + arm * Math.PI + (arm / 2) * 0.8D;
        double stride = Math.sin(cycle) * boss.crawlAmount(partial);
        double radius = RADIAL[joint] * (1D + (arm % 3 - 1) * 0.035D);
        double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
        double y = HEIGHT[joint] + boss.bodyShift(partial) * (1D - t);
        x -= Math.sin(angle) * stride * t * 0.7D;
        z += Math.cos(angle) * stride * t * 0.7D;
        y += Math.max(0, Math.cos(cycle)) * boss.crawlAmount(partial) * t * 0.8D;
        double sway = Math.sin(age * 0.025D + arm * 1.7D - joint * 0.8D) * 0.18D * t;
        x += Math.sin(angle) * sway; z -= Math.cos(angle) * sway;
        if(arm == boss.attackArm() && boss.state() != EntityGardener.IDLE) {
            double time = boss.stateTime(partial);
            double blend = boss.state() == EntityGardener.REACH ? Math.min(1D, time / 24D)
                    : boss.state() == EntityGardener.RECOVER ? Math.max(0D, 1D - time / 24D) : 1D;
            double bend = t * t * blend;
            Vec3 aim = boss.limbAim(partial);
            x += (aim.xCoord - Math.cos(angle) * RADIAL[4]) * bend;
            z += (aim.zCoord - Math.sin(angle) * RADIAL[4]) * bend;
            y += (aim.yCoord - HEIGHT[4]) * bend;
            if(joint == SEGMENTS) {
                // Remove idle/gait offsets at full extension so the visible tip meets the real captive.
                x = x * (1D - blend) + aim.xCoord * blend;
                y = y * (1D - blend) + aim.yCoord * blend;
                z = z * (1D - blend) + aim.zCoord * blend;
            }
            if(boss.state() == EntityGardener.SLAM)
                y += Math.sin(Math.min(1D, time / 26D) * Math.PI) * 4D * t;
        }
        if(boss.armDisabled(arm)) { x *= 1D - 0.55D * t; z *= 1D - 0.55D * t; y = 4.3D * (1D - t) + 0.3D; }
        double bx = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial;
        double by = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial;
        double bz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial;
        return Vec3.createVectorHelper(bx + x, by + y, bz + z);
    }

    public static AxisAlignedBB bounds(Vec3 a, Vec3 b, double radius) {
        return AxisAlignedBB.getBoundingBox(Math.min(a.xCoord, b.xCoord) - radius,
                Math.min(a.yCoord, b.yCoord) - radius, Math.min(a.zCoord, b.zCoord) - radius,
                Math.max(a.xCoord, b.xCoord) + radius, Math.max(a.yCoord, b.yCoord) + radius,
                Math.max(a.zCoord, b.zCoord) + radius);
    }

    /** Exact closest distance between the attack/projectile ray and a limb segment. */
    public static boolean rayTouches(Vec3 a, Vec3 b, Vec3 from, Vec3 to, double radius) {
        double ax = b.xCoord - a.xCoord, ay = b.yCoord - a.yCoord, az = b.zCoord - a.zCoord;
        double bx = to.xCoord - from.xCoord, by = to.yCoord - from.yCoord, bz = to.zCoord - from.zCoord;
        double rx = a.xCoord - from.xCoord, ry = a.yCoord - from.yCoord, rz = a.zCoord - from.zCoord;
        double aa = ax * ax + ay * ay + az * az, bb = bx * bx + by * by + bz * bz;
        double ab = ax * bx + ay * by + az * bz, ar = ax * rx + ay * ry + az * rz, br = bx * rx + by * ry + bz * rz;
        double s, t;
        if(aa < 1E-9) { s = 0; t = bb < 1E-9 ? 0 : clamp(br / bb); }
        else if(bb < 1E-9) { t = 0; s = clamp(-ar / aa); }
        else {
            double denominator = aa * bb - ab * ab;
            s = denominator > 1E-9 ? clamp((ab * br - ar * bb) / denominator) : 0;
            t = (ab * s + br) / bb;
            if(t < 0) { t = 0; s = clamp(-ar / aa); }
            else if(t > 1) { t = 1; s = clamp((ab - ar) / aa); }
        }
        double dx = rx + ax * s - bx * t, dy = ry + ay * s - by * t, dz = rz + az * s - bz * t;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    /** Convex distance search against the actual limb capsule, beyond its broad-phase bounds. */
    public static boolean touches(Vec3 a, Vec3 b, AxisAlignedBB box, double radius) {
        double lo = 0D, hi = 1D;
        for(int i = 0; i < 20; i++) {
            double l = (lo * 2D + hi) / 3D, r = (lo + hi * 2D) / 3D;
            if(distance(a, b, box, l) < distance(a, b, box, r)) hi = r; else lo = l;
        }
        return Math.min(Math.min(distance(a, b, box, 0), distance(a, b, box, 1)),
                distance(a, b, box, (lo + hi) * 0.5D)) <= radius * radius;
    }
    private static double distance(Vec3 a, Vec3 b, AxisAlignedBB box, double t) {
        double x = a.xCoord + (b.xCoord - a.xCoord) * t;
        double y = a.yCoord + (b.yCoord - a.yCoord) * t;
        double z = a.zCoord + (b.zCoord - a.zCoord) * t;
        double dx = Math.max(box.minX - x, Math.max(0D, x - box.maxX));
        double dy = Math.max(box.minY - y, Math.max(0D, y - box.maxY));
        double dz = Math.max(box.minZ - z, Math.max(0D, z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
