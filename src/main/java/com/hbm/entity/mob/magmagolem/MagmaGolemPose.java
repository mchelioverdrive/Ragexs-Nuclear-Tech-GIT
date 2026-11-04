package com.hbm.entity.mob.magmagolem;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

/** Block-space skeleton shared by combat, water contact and the client model. Forward is local +Z. */
public final class MagmaGolemPose {
    public static final int ROOT = 0, BODY = 1, HEAD = 2, JAW = 3;
    public static final int LEFT = 4, RIGHT = 9, SHOULDER = 0, FOREARM = 1, HIP = 2, SHIN = 3, FOOT = 4;
    public static final float STEP = (float)Math.PI;

    public enum Action {
        IDLE(0, 0), PUNCH_LEFT(36, 20), PUNCH_RIGHT(36, 20), SLAM(52, 33),
        FIREBALL(48, 17), BREATH(63, 24), SUMMON(45, 26),
        LEAP(62, 21), STOMP(38, 21), STAGGER(28, 1), DOWN(28, 1), DYING(76, 1);

        public final int length, strike;
        Action(int length, int strike) { this.length = length; this.strike = strike; }
    }

    public static final class Joint {
        public final int parent;
        public double x, y, z, rx, ry, rz;

        Joint(int parent, double x, double y, double z) {
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public final Joint[] joints = new Joint[14];

    public MagmaGolemPose() {
        joints[ROOT] = new Joint(-1, 0, 0, 0);
        joints[BODY] = new Joint(ROOT, 0, 4.65, 0);
        joints[HEAD] = new Joint(BODY, 0, 4.98, 0.08);
        joints[JAW] = new Joint(HEAD, 0, 0.51, 0.48);
        for (int side : new int[] {LEFT, RIGHT}) {
            int sign = side == LEFT ? 1 : -1;
            joints[side + SHOULDER] = new Joint(BODY, sign * 2.95, 4.02, 0);
            joints[side + FOREARM] = new Joint(side + SHOULDER, 0, -2.92, 0);
            joints[side + HIP] = new Joint(ROOT, sign * 1.16, 4.74, 0);
            joints[side + SHIN] = new Joint(side + HIP, 0, -2.19, 0);
            joints[side + FOOT] = new Joint(side + SHIN, 0, -2.07, 0);
        }
    }

    public void sample(Action action, float age, float time, float stride, float pace, float headYaw,
            float headPitch, boolean left, boolean right, int phase, boolean landed) {
        float cycle = (float)Math.sin(stride);
        double sway = Math.sin(time * 0.073);
        Joint body = joints[BODY], root = joints[ROOT], head = joints[HEAD], jaw = joints[JAW];
        root.y = 0;
        root.rx = 0;
        root.rz = 0;
        body.y = 4.65 + 0.08 * sway + 0.09 * Math.abs(cycle) * pace;
        body.rx = 0.04 + 0.12 * pace;
        body.rz = cycle * pace * 0.06 + (left == right ? 0 : (left ? -1 : 1) * (phase == 3 ? 0.15 : 0.07));
        if (phase == 3) body.rz += Math.sin(time * 0.17) * 0.05;
        head.rx = headPitch;
        head.ry = headYaw;
        jaw.rx = 0;
        double crouch = 0;
        double armLeft = 0, armRight = 0, elbow = 0;
        double windup = ease(age / Math.max(1, action.strike - 5));
        double recover = 1 - ease((age - action.strike) / Math.max(1, action.length - action.strike));
        switch (action) {
            case PUNCH_LEFT:
            case PUNCH_RIGHT:
                double swing = age < action.strike - 5 ? windup * 0.94
                    : blend(0.94, -1.14, ease((age - action.strike + 5) / 5)) * recover;
                if (action == Action.PUNCH_LEFT) armLeft = swing; else armRight = swing;
                double lunge = ease((age - action.strike + 6) / 6) * recover;
                body.rx += lunge * 0.47;
                body.rz += lunge * (action == Action.PUNCH_LEFT ? -0.08 : 0.08);
                crouch = 0.75 * lunge;
                break;
            case SLAM:
                double downswing = ease((age - action.strike + 8) / 8);
                armLeft = armRight = blend(-2.68 * windup, -0.87, downswing) * recover;
                body.rx += (0.72 * downswing - 0.14 * windup * (1 - downswing)) * recover;
                crouch = 1.27 * downswing * recover;
                break;
            case FIREBALL:
            case BREATH:
                double breath = ease(age / 12) * (1 - ease((age - action.length + 9) / 9));
                body.rx += 0.21 * breath;
                head.rx -= 0.12 * breath;
                jaw.rx = (action == Action.BREATH ? 0.74 : 0.42 + 0.18 * Math.sin(age * 0.49)) * breath;
                armLeft = armRight = 0.32 * breath;
                break;
            case SUMMON:
                armLeft = armRight = -1.85 * windup * recover;
                head.rx -= 0.29 * windup * recover;
                jaw.rx = 0.62 * windup * recover;
                break;
            case LEAP:
                double coil = age < action.strike ? windup : landed ? recover * 0.55 : 0.27;
                crouch = coil;
                body.rx += 0.36 * coil;
                armLeft = armRight = 0.6 * coil;
                elbow = -0.34 * coil;
                break;
            case STOMP:
                body.rz -= 0.16 * windup * recover;
                crouch = 0.44 * ease((age - action.strike + 4) / 4) * recover;
                break;
            case STAGGER:
                body.rx -= Math.sin(age / action.length * Math.PI) * 0.58;
                body.rz += Math.sin(age * 0.24) * 0.21 * recover;
                break;
            case DOWN:
            case DYING:
                double fall = action == Action.DYING ? 1 : ease(age / action.length);
                root.rx = -1.43 * fall;
                root.rz = 0.16 * fall;
                root.y = 0.93 * fall;
                if (action == Action.DYING) {
                    double crumble = ease((age - 35) / 35);
                    body.y -= 1.3 * crumble;
                    head.rz = -0.6 * crumble;
                    jaw.rx = 0.28 * (1 - crumble);
                }
                break;
            default:
                break;
        }
        if (action != Action.DYING) head.rz = 0;
        root.y -= crouch;
        for (int side = LEFT; side <= RIGHT; side += RIGHT - LEFT) {
            int sign = side == LEFT ? 1 : -1;
            Joint upper = joints[side + SHOULDER], lower = joints[side + FOREARM];
            Joint hip = joints[side + HIP], shin = joints[side + SHIN], foot = joints[side + FOOT];
            upper.rx = sign * cycle * pace * 0.64 + sway * 0.045 + (side == LEFT ? armLeft : armRight);
            upper.rz = sign * (0.075 + 0.03 * sway);
            lower.rx = -0.13 - Math.max(0, sign * cycle) * pace * 0.17 + elbow;
            double lift = action == Action.STOMP && side == LEFT
                ? windup * (1 - ease((age - action.strike + 5) / 5)) : 0;
            hip.rx = -sign * cycle * pace * 0.47 - crouch * 0.52 - lift * 0.7;
            shin.rx = Math.max(0, sign * cycle) * pace * 0.37 + crouch * 0.94 + lift * 1.25;
            foot.rx = -(hip.rx + shin.rx) * 0.76;
        }
    }

    public Vec3 point(int index, double x, double y, double z, float yaw, double px, double py, double pz) {
        for (; index >= 0; index = joints[index].parent) {
            Joint j = joints[index];
            double sin = Math.sin(j.rx), cos = Math.cos(j.rx);
            double ny = y * cos - z * sin;
            z = y * sin + z * cos;
            y = ny;
            sin = Math.sin(j.ry); cos = Math.cos(j.ry);
            double nx = x * cos + z * sin;
            z = z * cos - x * sin;
            x = nx;
            sin = Math.sin(j.rz); cos = Math.cos(j.rz);
            nx = x * cos - y * sin;
            y = x * sin + y * cos + j.y;
            x = nx + j.x;
            z += j.z;
        }
        double angle = Math.toRadians(yaw);
        return Vec3.createVectorHelper(px + x * Math.cos(angle) - z * Math.sin(angle), py + y,
            pz + x * Math.sin(angle) + z * Math.cos(angle));
    }

    public AxisAlignedBB box(int bone, double x, double y, double z, double rx, double ry, double rz,
            float yaw, double px, double py, double pz) {
        Vec3 center = point(bone, x, y, z, yaw, px, py, pz);
        Vec3 a = point(bone, x + rx, y, z, yaw, px, py, pz);
        Vec3 b = point(bone, x, y + ry, z, yaw, px, py, pz);
        Vec3 c = point(bone, x, y, z + rz, yaw, px, py, pz);
        double dx = extent(center.xCoord, a.xCoord, b.xCoord, c.xCoord);
        double dy = extent(center.yCoord, a.yCoord, b.yCoord, c.yCoord);
        double dz = extent(center.zCoord, a.zCoord, b.zCoord, c.zCoord);
        return AxisAlignedBB.getBoundingBox(center.xCoord - dx, center.yCoord - dy, center.zCoord - dz,
            center.xCoord + dx, center.yCoord + dy, center.zCoord + dz);
    }

    private static double extent(double center, double a, double b, double c) {
        return Math.abs(a - center) + Math.abs(b - center) + Math.abs(c - center);
    }

    private static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static double blend(double from, double to, double progress) { return from + (to - from) * progress; }
}
