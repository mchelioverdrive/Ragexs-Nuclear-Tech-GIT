package com.hbm.entity.mob.gemini;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** One articulated skeleton for the visible arms, collision regions and strike contacts. */
public final class WretchPose {
    public static final double ARM_RADIUS = 0.38D;
    private WretchPose() { }

    public static Vec3 local(EntityWretch boss, int arm, int joint, float partial) {
        double side = arm == 0 ? -1 : 1;
        if(joint == 0) return Vec3.createVectorHelper(side * 1.35D, 3.25D, 0.35D);
        double age = boss.isAwake() ? boss.ticksExisted + partial : 0;
        double hx = side * 1.85D, hy = 0.6D, hz = 1.7D + Math.sin(age * 0.08D + arm * Math.PI) * 0.22D;
        double lift = 0D, time = boss.stateTime(partial);
        int state = boss.state();
        boolean used = arm == boss.attackArm() || state == EntityWretch.SLAM;
        if(used && state != EntityWretch.IDLE && state != EntityWretch.RECOVER) {
            double angle = Math.toRadians(boss.rotationYaw);
            double ax = boss.aimX() * Math.cos(angle) + boss.aimZ() * Math.sin(angle);
            double az = -boss.aimX() * Math.sin(angle) + boss.aimZ() * Math.cos(angle);
            if(state == EntityWretch.SWIPE) {
                double progress = MathHelper.clamp_double((time - 22D) / 10D, 0D, 1D);
                double arc = (progress * 2D - 1D) * 1.15D * side;
                double length = Math.max(3D, Math.min(5.7D, Math.sqrt(ax * ax + az * az)));
                double aim = Math.atan2(ax, az) + arc;
                hx = Math.sin(aim) * length; hz = Math.cos(aim) * length;
                hy = 1.5D + Math.sin(Math.min(1D, time / 22D) * Math.PI) * 2.2D;
            } else if(state == EntityWretch.SLAM) {
                double wind = Math.min(1D, time / 26D), impact = MathHelper.clamp_double((time - 26D) / 8D, 0D, 1D);
                hx = side * 1.1D + ax * impact; hz = 0.5D + az * impact;
                hy = (0.6D + wind * 4.8D) * (1D - impact) + 0.3D * impact;
                lift = wind * (1D - impact) * 0.8D;
            } else if(state == EntityWretch.GRAB) {
                double reach = MathHelper.clamp_double((time - 20D) / 8D, 0D, 1D);
                hx += (ax - hx) * reach; hz += (az - hz) * reach;
                hy = 2.7D * (1D - reach) + boss.aimY() * reach;
            } else if(state == EntityWretch.HOLD || state == EntityWretch.THROW) {
                hx = ax; hz = az; hy = boss.aimY();
                if(state == EntityWretch.THROW) hy += Math.sin(Math.min(1D, time / 16D) * Math.PI) * 1.3D;
            } else if(state == EntityWretch.FEED) {
                hx = side * 1.2D; hy = 1.5D; hz = 2.7D;
            }
        }
        if(state == EntityWretch.RECOVER) { hy = 0.25D; hz += 0.5D; }
        if(joint == 2) return Vec3.createVectorHelper(hx, hy, hz);
        return Vec3.createVectorHelper(side * 1.9D + hx * 0.15D, (3.25D + hy) * 0.5D + lift, (0.35D + hz) * 0.5D - 0.35D);
    }

    public static Vec3 point(EntityWretch boss, int arm, int joint, float partial) {
        Vec3 p = local(boss, arm, joint, partial);
        float yaw = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
        double angle = Math.toRadians(yaw), c = Math.cos(angle), s = Math.sin(angle);
        return Vec3.createVectorHelper(boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial + p.xCoord * c - p.zCoord * s,
                boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial + p.yCoord,
                boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial + p.xCoord * s + p.zCoord * c);
    }
}
