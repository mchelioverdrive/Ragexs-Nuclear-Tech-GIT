package com.hbm.entity.mob.gemini;

import net.minecraft.util.Vec3;

/** Independent hanging skeleton: uneven shoulder positions, delayed currents and no walking cycle. */
public final class OceanGardenerPose {
    private static final double[] ANGLE = {0.12, 1.04, 2.36, 3.25, 4.42, 5.55};
    private static final double[] DROP = {25.0, 23.5, 24.8, 25.4, 22.9, 24.2};
    private static final double[] RADIAL = {1.5, 3.4, 5.6, 7.3, 9.2};
    private static final double[] VERTICAL = {1.2, -3.0, -10.3, -18.0, -25.0};
    private OceanGardenerPose() { }
    public static Vec3 point(EntityGardener boss, int arm, int joint, float partial) {
        double t = joint / 4D, angle = ANGLE[arm];
        double age = boss.isAwake() ? boss.worldObj.getTotalWorldTime() + partial : 0;
        double wave = Math.sin(age * 0.017D - joint * 0.8D + arm * 1.9D);
        double radius = RADIAL[joint] + wave * t * 0.7D;
        if(boss.eyeTarget() != null) radius -= Math.sin(t * Math.PI) * 0.7D;
        double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
        double y = VERTICAL[joint] * DROP[arm] / 25D + boss.bodyShift(partial) * (1D - t)
                + Math.sin(age * 0.021D + arm * 2.3D - joint) * t * 0.3D;
        if(arm == boss.attackArm() && boss.state() != EntityGardener.IDLE) {
            double blend = boss.state() == EntityGardener.REACH ? Math.min(1, boss.stateTime(partial) / 24D)
                    : boss.state() == EntityGardener.RECOVER ? Math.max(0, 1D - boss.stateTime(partial) / 24D) : 1;
            // Keep the shoulder outside the body and use a bowed chain to a live, tracked tip.
            double bend = t * t * blend;
            Vec3 aim = boss.limbAim(partial);
            x += (aim.xCoord - Math.cos(angle) * RADIAL[4]) * bend;
            z += (aim.zCoord - Math.sin(angle) * RADIAL[4]) * bend;
            y += (aim.yCoord + DROP[arm]) * bend;
            if(joint == GardenerPose.SEGMENTS) {
                x = x * (1D - blend) + aim.xCoord * blend;
                y = y * (1D - blend) + aim.yCoord * blend;
                z = z * (1D - blend) + aim.zCoord * blend;
            }
        }
        if(boss.armDisabled(arm)) { x *= 1D - 0.5D * t; z *= 1D - 0.5D * t; y = 1D - t * 7D; }
        double bx = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial;
        double by = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial;
        double bz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial;
        return Vec3.createVectorHelper(bx + x, by + y, bz + z);
    }
}
