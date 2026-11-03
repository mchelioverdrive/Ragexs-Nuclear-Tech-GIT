package com.hbm.entity.mob.colossus;

import net.minecraft.util.Vec3;

/** Shared skeleton in blocks: +Y up, -Z forward. Rendering and server contact use these same transforms. */
public final class ColossusPose {
    public static final int IDLE = 0, PUNCH = 1, SLAM = 2, STOMP = 3, SWEEP = 4, GRAB = 5, STUN = 6, SHAKE = 7, CLOCK = 8, DEATH = 9;
    public static final int ROOT = 0, TORSO = 1, HEAD = 2, LEFT_ARM = 3, LEFT_HAND = 4, RIGHT_ARM = 5, RIGHT_HAND = 6, LEFT_LEG = 7, LEFT_FOOT = 8, RIGHT_LEG = 9, RIGHT_FOOT = 10;
    public final Bone[] bones = new Bone[11];
    public ColossusPose() { for(int i = 0; i < bones.length; i++) bones[i] = new Bone(); }
    public static int duration(int state) {
        switch(state) { case PUNCH: return 55; case SLAM: return 80; case STOMP: return 58; case SWEEP: return 65; case GRAB: return 105; case STUN: return 110; case SHAKE: return 65; case CLOCK: return 100; case DEATH: return 140; default: return 0; }
    }
    public static double smooth(double a) { a = Math.max(0, Math.min(1, a)); return a * a * (3 - 2 * a); }
    private static double ramp(double t, double begin, double end) { return smooth((t - begin) / (end - begin)); }
    public void evaluate(int state, double t, double walk, double moving, double headYaw, double headPitch, double age, double hurt, double attackHeight) {
        for(Bone b : bones) b.reset();
        Bone root = bones[ROOT], torso = bones[TORSO], head = bones[HEAD];
        torso.set(root, 0, 8, 0); head.set(torso, 0, 7, 0);
        bones[LEFT_ARM].set(torso, 5, 6, 0); bones[LEFT_HAND].set(bones[LEFT_ARM], 0, -4, 0);
        bones[RIGHT_ARM].set(torso, -5, 6, 0); bones[RIGHT_HAND].set(bones[RIGHT_ARM], 0, -4, 0);
        bones[LEFT_LEG].set(root, 2.2, 8, 0); bones[LEFT_FOOT].set(bones[LEFT_LEG], 0, -4, 0);
        bones[RIGHT_LEG].set(root, -2.2, 8, 0); bones[RIGHT_FOOT].set(bones[RIGHT_LEG], 0, -4, 0);
        double phase = walk * 0.65, stride = Math.sin(phase) * 0.20 * moving;
        bones[LEFT_LEG].rx = stride; bones[RIGHT_LEG].rx = -stride;
        bones[LEFT_FOOT].rx = Math.max(0, -stride) * 1.4; bones[RIGHT_FOOT].rx = Math.max(0, stride) * 1.4;
        // Lift the trailing sole, never sink the stance foot through the root plane.
        root.y = Math.abs(Math.sin(phase)) * 0.14 * moving;
        bones[LEFT_ARM].rx = -stride * 0.6; bones[RIGHT_ARM].rx = stride * 0.6;
        torso.ry = Math.sin(age * 0.018) * 0.025 + stride * 0.15;
        torso.rz = Math.sin(age * 0.035) * 0.008 + hurt * 0.035;
        head.ry = -Math.toRadians(headYaw); head.rx = Math.toRadians(headPitch);
        bones[LEFT_ARM].rz = 0.035; bones[RIGHT_ARM].rz = -0.035;
        double wind, impact, recover;
        if(state == PUNCH) {
            wind = ramp(t, 0, 23); impact = ramp(t, 23, 31); recover = 1 - ramp(t, 37, 55);
            bones[RIGHT_ARM].rx = (-0.5 * wind + 1.5 * impact) * recover;
            bones[RIGHT_HAND].rx = (1.1 * wind - 1.0 * impact) * recover;
            torso.ry = (-0.18 * wind + 0.38 * impact) * recover;
            double crouch = impact * recover * (1 - Math.max(0, Math.min(1, attackHeight / 10)));
            crouch(crouch, 4.2); torso.rx = -0.6 * crouch;
        } else if(state == SLAM) {
            wind = ramp(t, 0, 32); impact = ramp(t, 32, 44); recover = 1 - ramp(t, 55, 80);
            bones[LEFT_ARM].rx = bones[RIGHT_ARM].rx = (2.75 * wind - 2.35 * impact) * recover;
            bones[LEFT_HAND].rx = bones[RIGHT_HAND].rx = 0.25 * wind * (1 - impact) * recover;
            torso.rx = -0.6 * impact * recover; crouch(impact * recover, 4.6);
        } else if(state == STOMP) {
            wind = ramp(t, 0, 23) * (1 - ramp(t, 23, 31)); recover = 1 - ramp(t, 36, 58);
            bones[LEFT_LEG].rx = -0.65 * wind; bones[LEFT_FOOT].rx = 1.0 * wind;
            bones[LEFT_LEG].y += 1.7 * wind; torso.rz = -0.09 * wind * recover;
        } else if(state == SWEEP) {
            wind = ramp(t, 0, 25); impact = ramp(t, 25, 42); recover = 1 - ramp(t, 47, 65);
            bones[RIGHT_ARM].rx = 0.8 * wind * recover;
            bones[RIGHT_ARM].rz = (-0.65 * wind + 1.3 * impact) * recover;
            crouch(wind * recover, 4.6); torso.rx = -0.4 * wind * recover;
            torso.ry = (-0.45 * wind + 1.0 * impact) * recover;
        } else if(state == GRAB) {
            wind = ramp(t, 0, 25); impact = ramp(t, 40, 80); recover = 1 - ramp(t, 90, 105);
            bones[RIGHT_ARM].rx = (0.7 * wind + 0.85 * impact) * recover;
            bones[RIGHT_HAND].rx = 1.8 * impact * recover;
            double reach = wind * (1 - impact) * recover;
            crouch(reach,4.2); torso.rx = -0.5 * reach;
        } else if(state == STUN) {
            double kneel = ramp(t, 0, 16) * (1 - ramp(t, 88, 110));
            root.y = -2.1 * kneel; torso.rx = 0.4 * kneel; head.rx = -0.4 * kneel;
            bones[LEFT_LEG].rx = bones[RIGHT_LEG].rx = -0.8 * kneel;
            bones[LEFT_FOOT].rx = bones[RIGHT_FOOT].rx = 1.5 * kneel;
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
            bones[LEFT_LEG].rx = bones[RIGHT_LEG].rx = -0.6 * kneel;
            bones[LEFT_FOOT].rx = bones[RIGHT_FOOT].rx = 1.1 * kneel;
            head.rx = 0.25 * kneel;
            bones[LEFT_ARM].rz = 0.4 * kneel; bones[RIGHT_ARM].rz = -0.4 * kneel;
        }
        for(Bone b : bones) b.compose();
    }
    private void crouch(double amount,double depth) {
        bones[ROOT].y = -depth * amount;
        bones[LEFT_LEG].rx = bones[RIGHT_LEG].rx = -1.2 * amount;
        bones[LEFT_FOOT].rx = bones[RIGHT_FOOT].rx = 2.4 * amount;
    }
    public Vec3 point(int bone, double x, double y, double z) { return bones[bone].point(x, y, z); }
    public static final class Bone {
        public Bone parent;
        public double x, y, z, rx, ry, rz;
        public final double[] matrix = new double[16];
        void reset() { parent = null; x = y = z = rx = ry = rz = 0; }
        void set(Bone p, double a, double b, double c) { parent = p; x = a; y = b; z = c; }
        void compose() {
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
    }
}
