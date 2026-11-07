package com.hbm.entity.mob.gemini;

import net.minecraft.util.Vec3;

/** Body-rooted legs share their exact joints with rendering, collision and attacks. */
public final class OceanGardenerPose {
    private static final double[] ANGLE = {0.12, 1.04, 2.36, 3.25, 4.42, 5.55};
    private static final double[] DROP = {25.0, 23.5, 24.8, 25.4, 22.9, 24.2};
    private static final double[] RADIAL = {1.2, 3.4, 5.6, 7.3, 9.2};
    private static final double[] HEIGHT = {27.4, 23.0, 15.7, 8.0, GardenerPose.RADIUS};
    private OceanGardenerPose() { }
    public static Vec3 point(EntityGardener boss, int arm, int joint, float partial) {
        double t = joint / 4D, angle = ANGLE[arm];
        double age = boss.isAwake() ? boss.worldObj.getTotalWorldTime() + partial : 0;
        double wave = Math.sin(age * 0.017D - joint * 0.8D + arm * 1.9D);
        boolean submerged = boss.isInWater();
        double stride = Math.sin(age * 0.075D + arm * Math.PI + (arm / 2) * 0.8D) * boss.crawlAmount(partial);
        double radius = RADIAL[joint] + wave * t * (submerged ? 0.7D : 0.18D);
        if(boss.eyeTarget() != null) radius -= Math.sin(t * Math.PI) * 0.7D;
        // The shoulder sits inside the body's 1.4-high elliptical ring, including its folds.
        // Every animation has zero weight here, except the identical body translation.
        double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius * (joint == 0 ? 0.85D : 1D);
        double y = HEIGHT[joint] + Math.sin(t * Math.PI) * (25D - DROP[arm]) * 0.4D
                + boss.bodyShift(partial) * (1D - t);
        if(submerged) y += Math.sin(age * 0.021D + arm * 2.3D - joint) * t * 0.2D;
        else {
            x -= Math.sin(angle) * stride * t * 0.7D;
            z += Math.cos(angle) * stride * t * 0.7D;
            y += Math.max(0, Math.cos(age * 0.075D + arm * Math.PI + (arm / 2) * 0.8D)) * boss.crawlAmount(partial) * t * 0.65D;
        }
        if(arm == boss.attackArm() && boss.state() != EntityGardener.IDLE) {
            double blend = boss.state() == EntityGardener.REACH ? Math.min(1, boss.stateTime(partial) / 24D)
                    : boss.state() == EntityGardener.RECOVER ? Math.max(0, 1D - boss.stateTime(partial) / 24D) : 1;
            // Only downstream joints bend; the root remains embedded in the moving body.
            double bend = t * t * blend;
            Vec3 aim = boss.limbAim(partial);
            x += (aim.xCoord - Math.cos(angle) * RADIAL[4]) * bend;
            z += (aim.zCoord - Math.sin(angle) * RADIAL[4]) * bend;
            y += (aim.yCoord - HEIGHT[4]) * bend;
            if(joint == GardenerPose.SEGMENTS) {
                x = x * (1D - blend) + aim.xCoord * blend;
                y = y * (1D - blend) + aim.yCoord * blend;
                z = z * (1D - blend) + aim.zCoord * blend;
            }
        }
        if(boss.armDisabled(arm)) {
            x *= 1D - 0.5D * t; z *= 1D - 0.5D * t;
            y = HEIGHT[0] + boss.bodyShift(partial) - t * 7D;
        }
        double bx = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial;
        double by = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial;
        double bz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial;
        return Vec3.createVectorHelper(bx + x, by + y, bz + z);
    }
}
