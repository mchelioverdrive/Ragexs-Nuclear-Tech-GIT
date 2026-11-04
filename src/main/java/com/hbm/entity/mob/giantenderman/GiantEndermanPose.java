package com.hbm.entity.mob.giantenderman;

import com.hbm.entity.mob.colossus.ColossusPose.Bone;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Local coordinates are in blocks, with positive Y up and positive Z forward. */
public final class GiantEndermanPose {
    public static final int HIPS = 0, CHEST = 1, HEAD = 2, JAW = 3;
    public static final int LEFT_ARM = 4, LEFT_FOREARM = 5, LEFT_HAND = 6;
    public static final int RIGHT_ARM = 7, RIGHT_FOREARM = 8, RIGHT_HAND = 9;
    public static final int LEFT_LEG = 10, LEFT_SHIN = 11, LEFT_FOOT = 12;
    public static final int RIGHT_LEG = 13, RIGHT_SHIN = 14, RIGHT_FOOT = 15;
    public static final float HEIGHT = 30, WIDTH = 8.5F;
    private static final double UPPER = 8.25, LOWER = 8.25, THIGH = 7.25, SHIN = 7.5;

    public enum Action {
        IDLE(0, 0), SCREAM(30, 0), SWIPE(46, 25), STOMP(48, 28), SMASH(57, 33),
        THROW(67, 48), GRAB(88, 25), VANISH(14, 0), ARRIVE(18, 0), STAGGER(28, 0),
        FINISH(80, 53), DEATH(82, 0);

        final int length, impact;
        Action(int length, int impact) { this.length = length; this.impact = impact; }

        static Action fromId(int id) {
            return id >= 0 && id < ALL.length ? ALL[id] : IDLE;
        }
        private static final Action[] ALL = values();
    }

    public final Bone[] bones = new Bone[16];
    public double mouth;

    public GiantEndermanPose() {
        for (int i = 0; i < bones.length; i++) bones[i] = new Bone();
    }

    public void evaluate(Action action, double frame, int hand, double stride, double moving,
                         double age, double lookYaw, double lookPitch, Vec3 aim) {
        for (Bone bone : bones) bone.reset();
        double breath = Math.sin(age * 0.08);
        double wind = ease(frame / Math.max(1, action.impact - 6));
        double hit = ease((frame - action.impact + 6) / 6);
        double recover = ease((frame - action.impact - 3) / Math.max(1, action.length - action.impact - 3));
        double active = wind * (1 - recover);
        double strideWave = Math.sin(stride);
        double crouch = 0;
        double lean = breath * 0.012;
        mouth = 0;

        switch (action) {
            case SCREAM:
                mouth = pulse(frame, 7, 19, 30);
                lean += mouth * 0.13;
                break;
            case SWIPE:
                double low = 1 - ease((aim.yCoord - 3) / 7);
                crouch = low * active * hit * 4.3;
                lean += active * (0.08 - hit * (0.23 + low * 0.22));
                mouth = active * 0.47;
                break;
            case STOMP:
                crouch = hit * (1 - recover) * 1.6;
                break;
            case SMASH:
                crouch = hit * (1 - recover) * 4.5;
                lean -= hit * (1 - recover) * 0.29;
                mouth = active;
                break;
            case THROW:
                crouch = pulse(frame, 16, 25, 38) * 4.8;
                lean -= pulse(frame, 16, 25, 39) * 0.30;
                mouth = pulse(frame, 33, 48, 62) * 0.85;
                break;
            case GRAB:
                crouch = pulse(frame, 24, 30, 46) * 5.1;
                lean -= pulse(frame, 21, 30, 47) * 0.34;
                mouth = pulse(frame - 44, 12, 25, 43);
                break;
            case FINISH:
                lean -= pulse(frame, 25, 52, 80) * 0.21;
                mouth = pulse(frame - 42, 12, 23, 38);
                break;
            case VANISH:
                crouch = ease(frame / 14) * 2;
                break;
            case ARRIVE:
                crouch = (1 - ease(frame / 18)) * 2;
                break;
            case STAGGER:
                crouch = pulse(frame, 4, 9, 28) * 2.2;
                lean += pulse(frame, 4, 8, 28) * 0.34;
                mouth = pulse(frame, 3, 9, 28) * 0.66;
                break;
            case DEATH:
                crouch = ease(frame / 49) * 8;
                lean -= ease(frame / 75) * 1.48;
                mouth = 1 - ease((frame - 42) / 29);
                break;
            default:
                break;
        }

        Bone hips = bones[HIPS], chest = bones[CHEST], head = bones[HEAD], jaw = bones[JAW];
        hips.set(null, 0, 15.5 - crouch, 0);
        hips.compose();
        chest.set(hips, 0, 0.3 + breath * 0.07, 0);
        chest.rx = lean;
        chest.rz = strideWave * moving * 0.035;
        chest.compose();
        head.set(chest, 0, 8.1, 0.15);
        head.ry = lookYaw * (1 - mouth * 0.55);
        head.rx = lookPitch * (1 - mouth * 0.6) - mouth * 0.12;
        head.compose();
        jaw.set(head, 0, -0.1, -2.5);
        jaw.rx = mouth * 0.60;
        jaw.compose();

        for (int side = -1; side <= 1; side += 2) {
            double gait = strideWave * side * moving;
            leg(side, stride, moving, crouch, action == Action.STOMP && side == hand ? active * (1 - hit) * 3.4 : 0);
            Bone arm = bones[arm(side)], fore = bones[arm(side) + 1], palm = bones[hand(side)];
            arm.set(chest, side * 3.55, 6.7, 0);
            arm.rx = gait * 0.32;
            arm.rz = side * (0.13 + breath * 0.015);
            fore.set(arm, 0, -UPPER, 0);
            fore.rx = -0.11;
            palm.set(fore, 0, -LOWER, 0);

            if (action == Action.SWIPE && side == hand) {
                Vec3 start = Vec3.createVectorHelper(side * 11, 13, -3);
                Vec3 end = Vec3.createVectorHelper(aim.xCoord - side * (frame > action.impact ? 4 * ease((frame - action.impact) / 5) : 0),
                    aim.yCoord + 0.7, Math.max(2, Math.min(18, aim.zCoord)));
                reach(arm, fore, mix(start, end, hit), active);
            } else if (action == Action.SMASH) {
                Vec3 start = Vec3.createVectorHelper(side * 4.8, 32, 3);
                Vec3 end = Vec3.createVectorHelper(side * 3.4, 1.6, 6);
                reach(arm, fore, mix(start, end, hit), active);
            } else if (action == Action.THROW && side == hand) {
                Vec3 dig = Vec3.createVectorHelper(side * 5, 2.6, 6.5);
                Vec3 carry = Vec3.createVectorHelper(side * 6.7, 22, -4.5);
                Vec3 release = Vec3.createVectorHelper(side * 2.5, 20, 13.5);
                Vec3 point = mix(mix(dig, carry, ease((frame - 25) / 14)), release, ease((frame - 42) / 6));
                reach(arm, fore, point, pulse(frame, 18, 50, 67));
            } else if (action == Action.GRAB && side == hand) {
                Vec3 reach = aim.addVector(0, 1.2, 0);
                Vec3 face = bones[HEAD].point(side * 0.85, -0.8, 4.1);
                reach(arm, fore, mix(reach, face, ease((frame - 31) / 24)), pulse(frame, 24, 68, 88));
                palm.rx = -0.25 * pulse(frame, 27, 66, 83);
            } else if (action == Action.FINISH && side == hand) {
                Vec3 lifted = Vec3.createVectorHelper(side * 9, Math.max(21, aim.yCoord + 2.5), 4.3);
                reach(arm, fore, mix(aim, lifted, ease((frame - 51) / 13)), pulse(frame, 35, 69, 80));
            } else if (action == Action.SCREAM || action == Action.STAGGER || action == Action.DEATH) {
                arm.rz += side * (mouth * 0.28 + (action == Action.DEATH ? ease(frame / 58) * 0.29 : 0));
                arm.rx -= mouth * 0.40;
            }
            arm.compose();
            fore.compose();
            palm.compose();
        }
    }

    private void leg(int side, double stride, double moving, double crouch, double stomp) {
        int index = side < 0 ? LEFT_LEG : RIGHT_LEG;
        Bone leg = bones[index], shin = bones[index + 1], foot = bones[index + 2];
        double step = stride + (side < 0 ? Math.PI : 0);
        double z = Math.sin(step) * moving * 3.25;
        double y = Math.max(0, Math.cos(step)) * moving * 1.25 + stomp;
        double down = THIGH + SHIN - crouch - y;
        double length = Math.min(THIGH + SHIN, Math.hypot(down, z));
        leg.set(bones[HIPS], side * 1.45, 0, 0);
        leg.rx = -Math.atan2(z, down) - angle(THIGH, length, SHIN);
        shin.set(leg, 0, -THIGH, 0);
        shin.rx = Math.PI - angle(THIGH, SHIN, length);
        foot.set(shin, 0, -SHIN, 0);
        foot.rx = -leg.rx - shin.rx;
        leg.compose();
        shin.compose();
        foot.compose();
    }

    private void reach(Bone arm, Bone fore, Vec3 point, double amount) {
        Vec3 local = bones[CHEST].inversePoint(point.xCoord, point.yCoord, point.zCoord);
        double x = local.xCoord - arm.x, y = local.yCoord - arm.y, z = local.zCoord - arm.z;
        double horizontal = Math.hypot(x, z);
        double distance = MathHelper.clamp_double(Math.hypot(horizontal, y), 0.1, UPPER + LOWER - 0.01);
        arm.ry = Math.atan2(x, z) * amount;
        arm.rx += (-Math.atan2(horizontal, -y) + angle(UPPER, distance, LOWER) - arm.rx) * amount;
        arm.rz *= 1 - amount;
        fore.rx += (angle(UPPER, LOWER, distance) - Math.PI - fore.rx) * amount;
    }

    private static double angle(double a, double b, double opposite) {
        return Math.acos(MathHelper.clamp_double((a*a + b*b - opposite*opposite) / (2*a*b), -1, 1));
    }

    public static int arm(int side) { return side < 0 ? LEFT_ARM : RIGHT_ARM; }
    public static int hand(int side) { return arm(side) + 2; }
    public static double ease(double x) { return com.hbm.entity.mob.colossus.ColossusPose.smooth(x); }

    private static double pulse(double t, double up, double hold, double end) {
        return ease(t / up) * (1 - ease((t - hold) / (end - hold)));
    }

    private static Vec3 mix(Vec3 a, Vec3 b, double f) {
        return Vec3.createVectorHelper(a.xCoord + (b.xCoord-a.xCoord)*f,
            a.yCoord + (b.yCoord-a.yCoord)*f, a.zCoord + (b.zCoord-a.zCoord)*f);
    }
}
