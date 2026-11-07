package com.hbm.entity.mob.gemini;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Feet-based, block-unit skeleton shared by both logical sides. No client classes. */
public final class WretchPose {
    public static final double ARM_RADIUS = 0.38D;
    public static final float HEIGHT = 20F, WIDTH = 3.2F;
    public static final int SLICES = 4, ARM_PARTS = 4 * SLICES, LIMB_PARTS = 8 * SLICES,
            HEAD_END = 9 * SLICES, HAND_END = 11 * SLICES, PARTS = HAND_END + 6;
    public static final int WATCH = 0, WINDOW = 1, SCRATCH = 2, ROOF = 3;
    private final EntityWretch boss;
    private final Vec3[][] joints = new Vec3[4][3], world = new Vec3[4][3];
    private final Vec3[] head = new Vec3[2], worldHead = new Vec3[2];
    private final Vec3[] fingerTips = new Vec3[2], worldFingers = new Vec3[2];
    private final Vec3[][] worldFeet = new Vec3[2][2];
    private final Vec3[] worldTorso = {vector(), vector(), vector()};
    private final Vec3 previous = vector(), target = vector();
    private final Vec3 anchor = vector(), remain = vector();
    public final Vec3 body = vector();
    public float lean, headYaw, headPitch, curl, breath, stride;
    public double age;
    private int cachedTick = Integer.MIN_VALUE;
    private float cachedPartial = -1;
    private double cachedX, cachedY, cachedZ;
    private float cachedYaw;
    private int cachedState, cachedHurt, cachedDeath;
    private final double[] ground = new double[2], oldGround = new double[2];
    public final float[] footPitch = new float[2], footRoll = new float[2];
    private final double[] footX = new double[2], footZ = new double[2], oldFootX = new double[2], oldFootZ = new double[2];
    private final double[] startX = new double[2], startZ = new double[2], goalX = new double[2], goalZ = new double[2], lift = new double[2], oldLift = new double[2];
    private final int[] stepStart = {-100, -100};
    private boolean planted;
    private int groundTick = Integer.MIN_VALUE;
    private final Vec3 sliceA = vector(), sliceB = vector();
    private final int[] stepDuration = {16, 16};

    public WretchPose(EntityWretch boss) {
        this.boss = boss;
        for(int i = 0; i < 4; i++) for(int j = 0; j < 3; j++) { joints[i][j] = vector(); world[i][j] = vector(); }
        for(int i = 0; i < 2; i++) { head[i] = vector(); worldHead[i] = vector(); fingerTips[i] = vector(); worldFingers[i] = vector(); }
        for(int i = 0; i < 2; i++) for(int j = 0; j < 2; j++) worldFeet[i][j] = vector();
    }
    private static Vec3 vector() { return Vec3.createVectorHelper(0, 0, 0); }
    private static void set(Vec3 v, double x, double y, double z) { v.xCoord = x; v.yCoord = y; v.zCoord = z; }
    private static double ease(double t) { t = MathHelper.clamp_double(t, 0, 1); return t * t * (3 - 2 * t); }
    public void invalidate() { cachedTick = Integer.MIN_VALUE; }

    public WretchPose update(float partial) {
        if(cachedTick == boss.ticksExisted && cachedPartial == partial && cachedX == boss.posX && cachedY == boss.posY
                && cachedZ == boss.posZ && cachedYaw == boss.rotationYaw && cachedState == boss.state()
                && cachedHurt == boss.hurtTime && cachedDeath == boss.deathTime) return this;
        cachedTick = boss.ticksExisted; cachedPartial = partial; cachedX = boss.posX; cachedY = boss.posY;
        cachedZ = boss.posZ; cachedYaw = boss.rotationYaw; cachedState = boss.state(); cachedHurt = boss.hurtTime; cachedDeath = boss.deathTime;
        age = boss.worldObj.getTotalWorldTime() + partial;
        double awake = boss.awakeAmount(partial), death = ease((boss.deathTime + (boss.deathTime > 0 ? partial : 0)) / 20D);
        stride = Math.min(1F, boss.prevLimbSwingAmount + (boss.limbSwingAmount - boss.prevLimbSwingAmount) * partial);
        if(!boss.isAwake() || boss.state() != EntityWretch.IDLE && boss.state() != EntityWretch.SWIPE && boss.state() != EntityWretch.GRAB || death > 0) stride = 0;
        breath = (float) Math.sin(age * (boss.isAwake() ? 0.045 : 0.023));
        double change = ease(boss.behaviorTime(partial) / 8D);
        double charge = (boss.previousBehavior() == WretchBrain.CHARGING ? 1 - change : 0) + (boss.behavior() == WretchBrain.CHARGING ? change : 0);
        lean = (float) (0.10 + Math.sin(age * 0.019) * 0.015 + stride * 0.08 + charge * 0.11 + death * 0.48);
        double recoil = boss.hurtTime > 0 ? Math.sin((boss.hurtTime - partial) * 0.55) * 0.15 : 0;
        set(body, Math.sin(age * 0.021) * 0.07 + recoil, 12.8 - (1 - awake) * 5.2 - death * 7.5 + breath * 0.06, -0.2);
        headYaw = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(boss.prevRotationYawHead
                + MathHelper.wrapAngleTo180_float(boss.rotationYawHead - boss.prevRotationYawHead) * partial
                - facing(partial)), -42, 42) * (float) Math.PI / 180F;
        headPitch = MathHelper.clamp_float(boss.prevRotationPitch + (boss.rotationPitch - boss.prevRotationPitch) * partial, -30, 45) * (float) Math.PI / 180F;
        headPitch += (float) (Math.sin(age * 0.017) * 0.035 + death * 0.6);
        bodyPoint(head[0], 0.12, 5.9, 1.05);
        double hy = head[0].yCoord;
        set(head[1], head[0].xCoord - Math.sin(headYaw) * Math.cos(headPitch) * 2.9, hy - Math.sin(headPitch) * 2.9, head[0].zCoord + Math.cos(headYaw) * Math.cos(headPitch) * 2.9);
        float time = boss.stateTime(partial);
        double blend = ease(time / (boss.state() == EntityWretch.RECOVER ? 14D : 7D));
        curl = (float) (curl(boss.previousState(), boss.previousStateTime()) * (1 - blend) + curl(boss.state(), time) * blend);
        for(int arm = 0; arm < 2; arm++) {
            double side = arm == 0 ? -1 : 1;
            bodyPoint(joints[arm][0], side * (arm == 0 ? 1.45 : 1.7), arm == 0 ? 4.4 : 3.8, 0.2);
            handTarget(arm, boss.state(), time, target);
            if(blend < 1) {
                handTarget(arm, boss.previousState(), boss.previousStateTime(), previous);
                set(target, previous.xCoord + (target.xCoord - previous.xCoord) * blend,
                        previous.yCoord + (target.yCoord - previous.yCoord) * blend, previous.zCoord + (target.zCoord - previous.zCoord) * blend);
            }
            set(joints[arm][2], target.xCoord, target.yCoord, target.zCoord);
            solve(joints[arm], 9.3, 9.6, side, -0.65);
            Vec3 hand = joints[arm][2];
            double fingerAngle = Math.toRadians(arm == 0 ? -13 : 19), extension = 2.25 * Math.cos(curl * 0.8);
            set(fingerTips[arm], hand.xCoord + Math.sin(fingerAngle) * extension,
                    hand.yCoord - Math.sin(curl * 0.8) * 0.65, hand.zCoord + Math.cos(fingerAngle) * extension);
        }
        sampleGround();
        for(int leg = 0; leg < 2; leg++) {
            Vec3[] limb = joints[leg + 2];
            double side = leg == 0 ? -1 : 1;
            bodyPoint(limb[0], side * 1.0, -3.0, -0.25);
            double wx = oldFootX[leg] + (footX[leg] - oldFootX[leg]) * partial - (boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial);
            double wz = oldFootZ[leg] + (footZ[leg] - oldFootZ[leg]) * partial - (boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial);
            double angle = Math.toRadians(facing(partial));
            set(limb[2], wx * Math.cos(angle) + wz * Math.sin(angle), oldGround[leg] + (ground[leg] - oldGround[leg]) * partial + 0.28
                    + oldLift[leg] + (lift[leg] - oldLift[leg]) * partial, -wx * Math.sin(angle) + wz * Math.cos(angle));
            solve(limb, 5.6, 5.3, side * 0.18, 1);
        }
        for(int i = 0; i < 4; i++) for(int j = 0; j < 3; j++) toWorld(joints[i][j], world[i][j], partial);
        for(int i = 0; i < 2; i++) { toWorld(head[i], worldHead[i], partial); toWorld(fingerTips[i], worldFingers[i], partial); }
        for(int i = 0; i < 2; i++) for(int j = 0; j < 2; j++) {
            Vec3 ankle = joints[i + 2][2];
            set(target, ankle.xCoord, ankle.yCoord - 0.04, ankle.zCoord + (j == 0 ? -0.35 : 0.85)); toWorld(target, worldFeet[i][j], partial);
        }
        for(int i = 0; i < 3; i++) { bodyPoint(target, 0, -3.2 + i * 3.7, 0); toWorld(target, worldTorso[i], partial); }
        return this;
    }

    /** All torso attachments use this same transform, including the rendered flesh hierarchy. */
    public void bodyPoint(Vec3 out, double x, double y, double z) {
        set(out, body.xCoord + x, body.yCoord + y * Math.cos(lean) - z * Math.sin(lean), body.zCoord + y * Math.sin(lean) + z * Math.cos(lean));
    }
    private float facing(float partial) { return boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial; }
    private void toWorld(Vec3 p, Vec3 out, float partial) {
        double angle = Math.toRadians(facing(partial)), c = Math.cos(angle), s = Math.sin(angle);
        set(out, boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial + p.xCoord * c - p.zCoord * s,
                boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial + p.yCoord,
                boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial + p.xCoord * s + p.zCoord * c);
    }
    private void handTarget(int arm, int state, double time, Vec3 out) {
        double side = arm == 0 ? -1 : 1;
        double gait = (boss.limbSwing - boss.limbSwingAmount * (1 - cachedPartial)) * 0.32 + arm * Math.PI;
        double x = side * (2.6 + Math.sin(age * 0.027 + arm * 2) * 0.15),
                y = 0.95 + Math.max(0, Math.sin(gait)) * stride * 0.7, z = 1.25 + Math.cos(gait) * stride * 0.9;
        double yaw = Math.toRadians(facing(cachedPartial)), ax = boss.aimX() * Math.cos(yaw) + boss.aimZ() * Math.sin(yaw),
                az = -boss.aimX() * Math.sin(yaw) + boss.aimZ() * Math.cos(yaw);
        boolean used = arm == boss.attackArm() || state == EntityWretch.SLAM;
        if(used) {
            if(state == EntityWretch.SWIPE) {
                double p = ease((time - 22) / 10D), angle = Math.atan2(ax, az) + (p * 2 - 1) * 1.15 * side;
                double radius = Math.max(3, Math.min(10, Math.sqrt(ax * ax + az * az))), wind = ease(time / 22D);
                x += (Math.sin(angle) * radius - x) * wind; z += (Math.cos(angle) * radius - z) * wind;
                y = boss.aimY() + Math.sin(Math.min(1, time / 22D) * Math.PI) * 5.5;
            } else if(state == EntityWretch.BREACH) {
                double reach = ease((time - 20) / 8D), wind = ease(time / 20D);
                x += (ax - x) * reach; z += (az - z) * reach;
                y = (0.95 + wind * 11) * (1 - reach) + boss.aimY() * reach;
            } else if(state == EntityWretch.SLAM) {
                double wind = ease(time / 26D), impact = ease((time - 26) / 8D);
                // Both palms meet the existing authoritative slam centre at tick 34.
                x = side * 2.6 * (1 - impact) + ax * impact; z = 1.25 * (1 - impact) + az * impact;
                y = (0.95 + wind * 13.5) * (1 - impact) + 0.38 * impact;
            } else if(state == EntityWretch.GRAB) {
                double reach = ease((time - 20) / 8D);
                x += (ax - x) * reach; z += (az - z) * reach; y += (boss.aimY() - y) * reach;
            } else if(state == EntityWretch.PULL || state == EntityWretch.ASSIMILATE) {
                double pull = state == EntityWretch.PULL ? ease(time / 60D) : 1;
                bodyPoint(out, side * 0.8, -1.3, 3 - (state == EntityWretch.ASSIMILATE ? ease(time / 100D) * 0.35 : 0));
                x = ax + (out.xCoord - ax) * pull; y = boss.aimY() + (out.yCoord - boss.aimY()) * pull; z = az + (out.zCoord - az) * pull;
            } else if(state == EntityWretch.HOLD || state == EntityWretch.THROW || state == EntityWretch.FEED) {
                x = ax; y = boss.aimY(); z = az;
                if(state == EntityWretch.THROW) y += Math.sin(Math.min(1, time / 16D) * Math.PI) * 1.3;
            } else if(state == EntityWretch.IDLE && boss.presentation() != WATCH) {
                double reach = Math.sin(Math.PI * boss.presentationTime(cachedPartial)) * Math.sin(Math.PI * boss.presentationTime(cachedPartial));
                x += (ax - x) * reach; z += (az - z) * reach;
                y += (boss.aimY() - y) * reach;
                if(boss.presentation() == SCRATCH) y += Math.sin(age * 0.24) * 0.22 * reach;
            }
        }
        if(state == EntityWretch.RECOVER) { y = 0.6; z = 1.5; }
        set(out, x, y, z);
    }
    private double curl(int state, double time) {
        return state == EntityWretch.HOLD || state == EntityWretch.PULL || state == EntityWretch.ASSIMILATE || state == EntityWretch.FEED ? 1
                : state == EntityWretch.GRAB ? ease((time - 25) / 10D) : 0.18 + Math.sin(age * 0.031) * 0.08;
    }
    /** Two bone IK with a deterministic bend plane; bone lengths remain constant. */
    private static void solve(Vec3[] limb, double upper, double lower, double bendX, double bendZ) {
        Vec3 a = limb[0], b = limb[1], c = limb[2];
        double dx = c.xCoord - a.xCoord, dy = c.yCoord - a.yCoord, dz = c.zCoord - a.zCoord;
        double distance = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double d = MathHelper.clamp_double(distance, Math.abs(upper - lower) + 0.001, upper + lower - 0.001);
        dx /= distance; dy /= distance; dz /= distance;
        set(c, a.xCoord + dx * d, a.yCoord + dy * d, a.zCoord + dz * d);
        double along = (upper * upper - lower * lower + d * d) / (2 * d), height = Math.sqrt(Math.max(0, upper * upper - along * along));
        double dot = bendX * dx + bendZ * dz, px = bendX - dot * dx, py = -dot * dy, pz = bendZ - dot * dz;
        double length = Math.sqrt(px * px + py * py + pz * pz);
        if(length < 0.001) { px = -dy; py = dx; pz = 0; length = Math.sqrt(px * px + py * py); }
        set(b, a.xCoord + dx * along + px / length * height, a.yCoord + dy * along + py / length * height, a.zCoord + dz * along + pz / length * height);
    }
    private void sampleGround() {
        if(groundTick == boss.ticksExisted) return;
        if(!boss.isAwake() && planted && boss.ticksExisted - groundTick < 20) return;
        groundTick = boss.ticksExisted;
        double yaw = Math.toRadians(boss.rotationYaw), c = Math.cos(yaw), s = Math.sin(yaw);
        for(int leg = 0; leg < 2; leg++) {
            oldGround[leg] = ground[leg];
            oldFootX[leg] = footX[leg]; oldFootZ[leg] = footZ[leg]; oldLift[leg] = lift[leg];
            double x = leg == 0 ? -1.25 : 1.25, z = -0.15;
            double wx = boss.posX + x * c - z * s, wz = boss.posZ + x * s + z * c;
            double dx = wx - footX[leg], dz = wz - footZ[leg];
            if(!planted || dx * dx + dz * dz > 36) {
                footX[leg] = oldFootX[leg] = wx; footZ[leg] = oldFootZ[leg] = wz; stepStart[leg] = boss.ticksExisted - 20;
            }
            int elapsed = boss.ticksExisted - stepStart[leg];
            if(elapsed >= stepDuration[leg] && boss.ticksExisted - stepStart[1 - leg] >= stepDuration[1 - leg] && dx * dx + dz * dz > 0.7) {
                stepStart[leg] = boss.ticksExisted; elapsed = 0; startX[leg] = footX[leg]; startZ[leg] = footZ[leg];
                double speed = Math.sqrt(boss.motionX * boss.motionX + boss.motionZ * boss.motionZ);
                stepDuration[leg] = MathHelper.clamp_int((int) (1.8 / Math.max(0.04, speed)), 5, 16);
                double ahead = Math.min(1.4, speed * 9);
                goalX[leg] = wx - s * ahead; goalZ[leg] = wz + c * ahead;
            }
            if(elapsed < stepDuration[leg]) {
                double progress = ease(elapsed / (double) stepDuration[leg]);
                footX[leg] = startX[leg] + (goalX[leg] - startX[leg]) * progress;
                footZ[leg] = startZ[leg] + (goalZ[leg] - startZ[leg]) * progress;
                lift[leg] = Math.sin(Math.PI * elapsed / stepDuration[leg]) * 0.8;
            } else lift[leg] = 0;
            wx = footX[leg]; wz = footZ[leg];
            if(!boss.worldObj.blockExists(MathHelper.floor_double(wx), MathHelper.floor_double(boss.posY), MathHelper.floor_double(wz))) continue;
            MovingObjectPosition hit = boss.worldObj.func_147447_a(Vec3.createVectorHelper(wx, boss.posY + 1.6, wz), Vec3.createVectorHelper(wx, boss.posY - 2.5, wz), false, true, false);
            double desired = hit == null ? 0 : hit.hitVec.yCoord - boss.posY;
            ground[leg] += MathHelper.clamp_double(desired - ground[leg], -0.25, 0.25);
            double front = terrain(wx - s * 0.55, wz + c * 0.55, desired), back = terrain(wx + s * 0.55, wz - c * 0.55, desired);
            double left = terrain(wx - c * 0.3, wz - s * 0.3, desired), right = terrain(wx + c * 0.3, wz + s * 0.3, desired);
            footPitch[leg] = (float) MathHelper.clamp_double(-Math.atan2(front - back, 1.1), -0.5, 0.5);
            footRoll[leg] = (float) MathHelper.clamp_double(Math.atan2(right - left, 0.6), -0.5, 0.5);
        }
        planted = true;
    }
    private double terrain(double x, double z, double fallback) {
        if(!boss.worldObj.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(boss.posY), MathHelper.floor_double(z))) return fallback;
        MovingObjectPosition hit = boss.worldObj.func_147447_a(Vec3.createVectorHelper(x, boss.posY + 1.6, z), Vec3.createVectorHelper(x, boss.posY - 2.5, z), false, true, false);
        return hit == null ? fallback : hit.hitVec.yCoord - boss.posY;
    }
    public Vec3 joint(int limb, int joint) { return joints[limb][joint]; }
    public Vec3 head(int end) { return head[end]; }
    public Vec3 worldHead() { return worldHead[0]; }
    /** Centre of a held human, just inside the curled fingers, never inside the torso. */
    public Vec3 captureAnchor() {
        Vec3 hand = joints[boss.attackArm()][2];
        set(anchor, hand.xCoord, hand.yCoord + 0.15, hand.zCoord + 0.45);
        toWorld(anchor, anchor, cachedPartial); return anchor;
    }
    public void remainLocal(Vec3 out, int seed) {
        double angle = (seed % 1009) * (Math.PI * 2 / 1009);
        bodyPoint(out, Math.sin(angle) * 1.12, -3.1 + ((seed >>> 10) % 997) * (6.3 / 997), Math.cos(angle) * 1.22);
    }
    public Vec3 remainPoint(int seed) { remainLocal(remain, seed); toWorld(remain, remain, cachedPartial); return remain; }
    public static Vec3 local(EntityWretch boss, int arm, int joint, float partial) { return boss.pose(partial).joints[arm][joint]; }
    public static Vec3 point(EntityWretch boss, int arm, int joint, float partial) { return boss.pose(partial).world[arm][joint]; }
    public Vec3 fingerTip(int arm) { return worldFingers[arm]; }
    public double radius(int slot) {
        return slot < LIMB_PARTS ? (slot < ARM_PARTS ? ARM_RADIUS : 0.32) : slot < HEAD_END ? 0.48
                : slot < HAND_END ? 0.3 : slot < HAND_END + 2 ? 0.62 : slot < HAND_END + 4 ? 0.38 : 1.25;
    }
    private void slice(int slot) {
        Vec3 a, b;
        if(slot >= HAND_END + 4) { int i = slot - HAND_END - 4; a = worldTorso[i]; b = worldTorso[i + 1]; }
        else if(slot >= HAND_END + 2) { int i = slot - HAND_END - 2; a = worldFeet[i][0]; b = worldFeet[i][1]; }
        else if(slot >= HAND_END) { a = b = world[slot - HAND_END][2]; }
        else if(slot >= HEAD_END) { int arm = (slot - HEAD_END) / SLICES; a = world[arm][2]; b = worldFingers[arm]; }
        else if(slot >= LIMB_PARTS) { a = worldHead[0]; b = worldHead[1]; }
        else { int bone = slot / SLICES; a = world[bone / 2][bone % 2]; b = world[bone / 2][bone % 2 + 1]; }
        double lo = (slot % SLICES) / (double) SLICES, hi = lo + 1D / SLICES;
        if(slot >= HAND_END) { lo = 0; hi = 1; }
        set(sliceA, a.xCoord + (b.xCoord - a.xCoord) * lo, a.yCoord + (b.yCoord - a.yCoord) * lo, a.zCoord + (b.zCoord - a.zCoord) * lo);
        set(sliceB, a.xCoord + (b.xCoord - a.xCoord) * hi, a.yCoord + (b.yCoord - a.yCoord) * hi, a.zCoord + (b.zCoord - a.zCoord) * hi);
    }
    public AxisAlignedBB bounds(int slot) { slice(slot); return GardenerPose.bounds(sliceA, sliceB, radius(slot)); }
    public boolean rayTouches(int slot, Vec3 from, Vec3 to, double extra) { slice(slot); return GardenerPose.rayTouches(sliceA, sliceB, from, to, radius(slot) + extra); }
    public boolean touches(int slot, AxisAlignedBB box) { slice(slot); return GardenerPose.touches(sliceA, sliceB, box, radius(slot)); }
}
