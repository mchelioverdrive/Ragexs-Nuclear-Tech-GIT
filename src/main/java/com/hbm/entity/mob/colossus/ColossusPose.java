package com.hbm.entity.mob.colossus;

import net.minecraft.util.Vec3;

/** Shared skeleton in blocks: +Y up, -Z forward. Rendering and server contact use these same transforms. */
public final class ColossusPose {
    public static final int IDLE = 0, PUNCH = 1, SLAM = 2, STOMP = 3, SWEEP = 4, GRAB = 5, STUN = 6, SHAKE = 7, CLOCK = 8, DEATH = 9;
    public static final int ROOT = 0, TORSO = 1, HEAD = 2, LEFT_ARM = 3, LEFT_HAND = 4, RIGHT_ARM = 5, RIGHT_HAND = 6, LEFT_LEG = 7, LEFT_SHIN = 8, LEFT_FOOT = 9, RIGHT_LEG = 10, RIGHT_SHIN = 11, RIGHT_FOOT = 12;
    public static final int GRAB_LOCK = 18, GRAB_START = 22, GRAB_END = 33, ESCAPE_TICKS = 80;
    public static final int PUNCH_LOCK = 22, PUNCH_START = 24, PUNCH_END = 32;
    public static final int SLAM_START = 33, SLAM_END = 44, STOMP_START = 24, STOMP_END = 31, SWEEP_START = 26, SWEEP_END = 42;
    public static final double STEP_LENGTH = 3.2;
    public static final double HIPS = 9, THIGH = 4.25, SHIN = 3.5, SOLE = 1.25;
    public static final double SHOULDER_X = 4.625, SHOULDER_Y = 5.625, UPPER_ARM = 4.75;
    public static final double FIST_Y = -4, FIST_X_RADIUS = 1.625, FIST_Y_RADIUS = 1.375, FIST_Z_RADIUS = 1.625;
    public static final double RUNE_Y = 2.625, RUNE_Z = 2.5;
    public final Bone[] bones = new Bone[13];
    public ColossusPose() { for(int i = 0; i < bones.length; i++) bones[i] = new Bone(); }
    public static int duration(int state) {
        switch(state) { case PUNCH: return 55; case SLAM: return 80; case STOMP: return 58; case SWEEP: return 65; case GRAB: return GRAB_END + ESCAPE_TICKS + 24; case STUN: return 110; case SHAKE: return 65; case CLOCK: return 100; case DEATH: return 140; default: return 0; }
    }
    public static double smooth(double a) { a = Math.max(0, Math.min(1, a)); return a * a * (3 - 2 * a); }
    private static double ramp(double t, double begin, double end) { return smooth((t - begin) / (end - begin)); }
    public void evaluate(int state, double t, double walk, double moving, double leftGround, double rightGround,
                         double headYaw, double headPitch, double age, double hurt,
                         int armSide, double aimX, double aimY, double aimZ, int capture, int escape) {
        for(Bone b : bones) b.reset();
        Bone root = bones[ROOT], torso = bones[TORSO], head = bones[HEAD];
        torso.set(root, 0, HIPS, 0); head.set(torso, 0, 6.5, 0);
        bones[LEFT_ARM].set(torso, SHOULDER_X, SHOULDER_Y, 0); bones[LEFT_HAND].set(bones[LEFT_ARM], 0, -UPPER_ARM, 0);
        bones[RIGHT_ARM].set(torso, -SHOULDER_X, SHOULDER_Y, 0); bones[RIGHT_HAND].set(bones[RIGHT_ARM], 0, -UPPER_ARM, 0);
        bones[LEFT_LEG].set(root, 2.125, HIPS, 0); bones[LEFT_SHIN].set(bones[LEFT_LEG], 0, -THIGH, 0);
        bones[LEFT_FOOT].set(bones[LEFT_SHIN], 0, -SHIN, 0);
        bones[RIGHT_LEG].set(root, -2.125, HIPS, 0); bones[RIGHT_SHIN].set(bones[RIGHT_LEG], 0, -THIGH, 0);
        bones[RIGHT_FOOT].set(bones[RIGHT_SHIN], 0, -SHIN, 0);
        torso.ry = Math.sin(age * 0.018) * 0.025;
        torso.rz = Math.sin(age * 0.035) * 0.008 + hurt * 0.035;
        if(state == IDLE) {
            double phase = stepPhase(walk, false);
            double weight = Math.cos(phase * 2 * Math.PI) * moving;
            root.y = Math.min(0, Math.max(leftGround, rightGround)) - 0.45 * moving
                - 0.13 * moving * Math.pow(Math.cos(phase * 2 * Math.PI), 2);
            walkLeg(LEFT_LEG, phase, moving, leftGround);
            walkLeg(RIGHT_LEG, stepPhase(walk, true), moving, rightGround);
            double stride = footZ(phase) * moving;
            bones[LEFT_ARM].rx = stride * 0.19; bones[RIGHT_ARM].rx = -stride * 0.19;
            torso.ry -= stride * 0.055;
            torso.rz -= weight * 0.045;
            torso.x = weight * 0.2;
        }
        head.ry = -Math.toRadians(headYaw); head.rx = Math.toRadians(headPitch);
        bones[LEFT_ARM].rz = 0.035; bones[RIGHT_ARM].rz = -0.035;
        double wind, impact, recover;
        if(state == PUNCH) {
            wind = ramp(t, 0, PUNCH_LOCK); impact = ramp(t, PUNCH_LOCK, 31); recover = 1 - ramp(t, 37, 55);
            Bone upper = bones[upperArm(armSide)], lower = bones[hand(armSide)];
            upper.rx = -0.60 * wind * recover;
            lower.rx = 1.45 * wind * recover;
            double low = (1 - smooth((aimY - 2) / 11)) * (0.65 * wind + 0.35 * impact) * recover;
            crouch(low, 1.26); torso.rx = -0.62 * low;
            torso.ry = armSide * (-0.20 * wind + 0.52 * impact) * recover;
            reachArm(armSide, armGoal(torso, aimX, aimY, aimZ), impact * recover);
        } else if(state == SLAM) {
            wind = ramp(t, 0, 32); impact = ramp(t, 32, 44); recover = 1 - ramp(t, 55, 80);
            bones[LEFT_ARM].rx = bones[RIGHT_ARM].rx = (2.75 * wind - 1.92 * impact) * recover;
            bones[LEFT_HAND].rx = bones[RIGHT_HAND].rx = 0.25 * wind * (1 - impact) * recover;
            torso.rx = -0.6 * impact * recover; crouch(impact * recover, 1.13);
        } else if(state == STOMP) {
            wind = ramp(t, 0, 23) * (1 - ramp(t, 23, STOMP_END)); recover = 1 - ramp(t, 36, 58);
            double step = ramp(t, 5, STOMP_END) * recover;
            root.y = -0.45 * recover * ramp(t, 0, 23) + Math.min(0,Math.min(leftGround,rightGround)) * step;
            plantLeg(LEFT_LEG, -2.1 * step, SOLE + leftGround * step + 2.1 * wind);
            plantLeg(RIGHT_LEG, 0, SOLE + rightGround * step);
            torso.rz = -0.09 * wind;
        } else if(state == SWEEP) {
            wind = ramp(t, 0, 25); impact = ramp(t, 25, 42); recover = 1 - ramp(t, 47, 65);
            bones[RIGHT_ARM].rx = 0.8 * wind * recover;
            bones[RIGHT_ARM].rz = (-0.65 * wind + 1.3 * impact) * recover;
            crouch(wind * recover, 1.13); torso.rx = -0.4 * wind * recover;
            torso.ry = (-0.45 * wind + 1.0 * impact) * recover;
        } else if(state == GRAB) {
            double recoil = escape>0 ? ramp(t, escape, escape+24) : 0;
            if(escape>0) t=Math.min(t,escape);
            double lift = capture > 0 ? ramp(t, capture + 3, capture + ESCAPE_TICKS - 5) : 0;
            double reachTime = capture>0 ? Math.min(t,capture) : t;
            double reach = ramp(reachTime, 0, GRAB_START + 4);
            recover = capture > 0 ? 1 - ramp(t, capture + ESCAPE_TICKS, capture + ESCAPE_TICKS + 19)
                                : 1 - ramp(t, GRAB_END, GRAB_END + 19);
            recover *= 1-recoil;
            crouch(reach * (1 - lift) * recover, 1.26);
            torso.rx = -0.62 * reach * (1 - lift) * recover;
            torso.ry = armSide * 0.30 * reach * (1 - lift) * recover;
            torso.rz = torso.rz * (1-ramp(t,0,GRAB_LOCK)) + armSide * 0.23 * Math.sin(Math.PI * recoil);
            head.rx *= 1 - 0.85 * lift * recover;
            head.ry *= 1 - lift * recover;
            Vec3 goal = armGoal(torso, aimX, aimY, aimZ);
            head.compose();
            Vec3 mouth = head.point(armSide * 0.4, 0.35, -3.5);
            mouth = torso.inversePoint(mouth.xCoord, mouth.yCoord, mouth.zCoord);
            double reachBlend = ramp(reachTime, 6, GRAB_START + 4);
            reachArm(armSide, goal.addVector((mouth.xCoord-goal.xCoord)*lift,(mouth.yCoord-goal.yCoord)*lift,
                (mouth.zCoord-goal.zCoord)*lift), (reachBlend + (1-reachBlend)*lift) * recover);
        } else if(state == STUN) {
            double kneel = ramp(t, 0, 16) * (1 - ramp(t, 88, 110));
            crouch(kneel, 0.75); torso.rx = 0.4 * kneel; head.rx = -0.4 * kneel;
            bones[LEFT_ARM].rx = bones[RIGHT_ARM].rx = 0.35 * kneel;
        } else if(state == SHAKE) {
            double shake = Math.sin(t * 0.55) * ramp(t, 10, 22) * (1 - ramp(t, 42, 65));
            torso.rz = shake * 0.15; head.ry += shake * 0.12;
        } else if(state == CLOCK) {
            double lift = ramp(t, 0, 35) * (1 - ramp(t, 70, 100));
            bones[LEFT_ARM].rx = 2.5 * lift; bones[LEFT_HAND].rx = 0.3 * lift;
            head.rx = -0.3 * lift;
        } else if(state == DEATH) {
            double kneel = ramp(t, 0, 50), collapse = ramp(t, 55, 125);
            root.y = -4 * kneel + 5 * collapse; root.rx = -1.45 * collapse;
            torso.rz = Math.sin(t * 0.18) * 0.08 * (1 - collapse);
            leg(LEFT_LEG, -0.6 * kneel, 1.1 * kneel);
            leg(RIGHT_LEG, -0.6 * kneel, 1.1 * kneel);
            head.rx = 0.25 * kneel;
            bones[LEFT_ARM].rz = 0.4 * kneel; bones[RIGHT_ARM].rz = -0.4 * kneel;
        }
        for(Bone b : bones) b.compose();
    }
    public static int upperArm(int side) { return side < 0 ? RIGHT_ARM : LEFT_ARM; }
    public static int hand(int side) { return side < 0 ? RIGHT_HAND : LEFT_HAND; }
    static double stepPhase(double walk, boolean right) {
        double phase = walk / (2 * STEP_LENGTH) + (right ? 0.5 : 0);
        return phase - Math.floor(phase);
    }
    static double footZ(double phase) {
        return STEP_LENGTH * ((phase < 0.5 ? phase * 2 : 1 - smooth(phase * 2 - 1)) - 0.5);
    }
    private void walkLeg(int thigh, double phase, double moving, double ground) {
        double swing = phase < 0.5 ? 0 : Math.sin(Math.PI * (phase * 2 - 1));
        plantLeg(thigh, footZ(phase) * moving, SOLE + ground + 1.45 * swing * swing * moving);
    }
    private void plantLeg(int thigh, double forward, double height) {
        double down = HIPS + bones[ROOT].y - height;
        double distance = Math.min(THIGH + SHIN, Math.hypot(forward, down));
        double knee = Math.acos((distance * distance - THIGH * THIGH - SHIN * SHIN) / (2 * THIGH * SHIN));
        double pitch = Math.atan2(-forward, down) - Math.atan2(SHIN * Math.sin(knee), THIGH + SHIN * Math.cos(knee));
        leg(thigh, pitch, knee);
    }
    private void leg(int thigh, double pitch, double bend) {
        bones[thigh].rx = pitch;
        bones[thigh + 1].rx = bend;
        bones[thigh + 2].rx = -pitch - bend;
    }
    private double soleLift(int thigh) {
        double pitch = bones[thigh].rx, shin = pitch + bones[thigh + 1].rx;
        return THIGH * (1 - Math.cos(pitch)) + SHIN * (1 - Math.cos(shin));
    }
    private void crouch(double amount, double angle) {
        double pitch = -angle * amount;
        leg(LEFT_LEG, pitch, -2 * pitch);
        leg(RIGHT_LEG, pitch, -2 * pitch);
        bones[ROOT].y = -soleLift(LEFT_LEG);
    }
    private Vec3 armGoal(Bone torso, double x, double y, double z) {
        bones[ROOT].compose(); torso.compose();
        return torso.inversePoint(x, y, z);
    }
    private void reachArm(int side, Vec3 goal, double blend) {
        Bone upper = bones[upperArm(side)];
        Bone lower = bones[hand(side)];
        double x = goal.xCoord - upper.x;
        double y = goal.yCoord - upper.y;
        double z = goal.zCoord - upper.z;
        double forearm = -FIST_Y;
        double distance = Math.max(2, Math.min(UPPER_ARM + forearm - 0.05, Math.sqrt(x*x + y*y + z*z)));
        // Solve in the shoulder-to-fist plane. The elbow hangs below the line of reach.
        double elbow = Math.acos((distance*distance - UPPER_ARM*UPPER_ARM - forearm*forearm) / (2 * UPPER_ARM * forearm));
        double shoulder = Math.atan2(Math.hypot(x, z), -y) - Math.atan2(forearm * Math.sin(elbow), UPPER_ARM + forearm * Math.cos(elbow));
        upper.rx += (shoulder - upper.rx) * blend;
        upper.ry = Math.atan2(-x, -z) * blend;
        upper.rz *= 1 - blend;
        lower.rx += (elbow - lower.rx) * blend;
    }
    public Vec3 point(int bone, double x, double y, double z) { return bones[bone].point(x, y, z); }
    public static final class Bone {
        public Bone parent;
        public double x, y, z, rx, ry, rz;
        public final double[] matrix = new double[16];
        public void reset() { parent = null; x = y = z = rx = ry = rz = 0; }
        public void set(Bone p, double a, double b, double c) { parent = p; x = a; y = b; z = c; }
        public void compose() {
            double cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
            double[] m = matrix;
            // Rz * Ry * Rx, in column-major OpenGL order.
            m[0]=cz*cy; m[1]=sz*cy; m[2]=-sy; m[3]=0;
            m[4]=cz*sy*sx-sz*cx; m[5]=sz*sy*sx+cz*cx; m[6]=cy*sx; m[7]=0;
            m[8]=cz*sy*cx+sz*sx; m[9]=sz*sy*cx-cz*sx; m[10]=cy*cx; m[11]=0;
            m[12]=x; m[13]=y; m[14]=z; m[15]=1;
            if(parent != null) {
                double[] p = parent.matrix;
                for(int col=0; col<4; col++) {
                    int i=col*4; double a=m[i], b=m[i+1], c=m[i+2], d=m[i+3];
                    for(int row=0; row<4; row++) m[i+row]=p[row]*a+p[4+row]*b+p[8+row]*c+p[12+row]*d;
                }
            }
        }
        public Vec3 point(double a, double b, double c) { return Vec3.createVectorHelper(matrix[0]*a+matrix[4]*b+matrix[8]*c+matrix[12], matrix[1]*a+matrix[5]*b+matrix[9]*c+matrix[13], matrix[2]*a+matrix[6]*b+matrix[10]*c+matrix[14]); }
        public Vec3 inversePoint(double a, double b, double c) {
            a-=matrix[12]; b-=matrix[13]; c-=matrix[14];
            return Vec3.createVectorHelper(matrix[0]*a+matrix[1]*b+matrix[2]*c, matrix[4]*a+matrix[5]*b+matrix[6]*c, matrix[8]*a+matrix[9]*b+matrix[10]*c);
        }
    }
}
