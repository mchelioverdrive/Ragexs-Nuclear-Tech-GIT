package com.hbm.entity.mob.witherstorm;

import net.minecraft.util.Vec3;

/** Stage B anatomy in unscaled body space (+Y up, -Z forward). */
public final class WitherStormDestroyerPose {
    public static final double EYE_Y = 7D / 16D, EYE_Z = -1.009D;
    public static final double MOUTH_Y = -2.5D / 16D, MOUTH_Z = -1.075D;
    public static final float REST_PITCH = -12F;
    private static final double[][] HEADS = {
        {-5.25, 3.45, -6.90}, {0.05, 2.25, -8.45}, {5.20, 3.00, -6.75}
    };
    private static final double[][] NECKS = {
        {-4.95, 4.35, -3.35}, {0.05, 3.65, -4.65}, {4.95, 4.10, -3.15}
    };
    // Cubic control points, indexed by the existing multipart slots 3 through 9.
    private static final double[][] ARMS = {
        {4.3, 10.1, 2.8,   7.9, 11.8, 3.5,   9.9, 19.9, 7.7,   15.6, 23.2, 8.5},
        {7.0, 5.9, 1.7,    10.0, 4.5, 1.8,   8.0, 0.7, 3.3,   9.2, -2.6, 2.0},
        {-2.4, 1.8, 3.3,   -3.1, -0.8, 3.6, -4.7, -2.0, 3.5, -4.2, -5.4, 4.2},
        {-5.4, 9.3, 0.7,   -12.2, 8.6, 1.2, -23.8, 8.9, 0.9, -20.7, 17.3, -1.2},
        {-6.9, 4.5, -0.2,  -10.0, 2.5, -1.5, -11.7, 3.5, -3.8, -12.0, 6.1, -3.9},
        {3.7, 2.0, 0.5,    3.2, -1.0, 1.0,   5.1, -3.8, 0.6,   4.0, -7.4, -0.4},
        {0.7, 1.2, -2.2,   0.1, -0.7, -2.6, -0.4, -3.4, -2.4, 0.9, -4.1, -1.8}
    };
    private static final float[] ROOT_WIDTHS = {2.8F, 2.25F, 1.85F, 2.65F, 1.65F, 2.30F, 1.75F};

    private WitherStormDestroyerPose() {}

    public static float size(float development) { return 0.78F + 0.58F * development; }

    public static float headSize(int slot, float development) {
        return size(development) * (slot == 1 ? 1.33F : slot == 0 ? 1.20F : 1.17F);
    }

    public static Vec3 headBase(int slot, float development, float scale, float yaw) {
        double[] head = HEADS[slot];
        float size = size(development) * scale;
        return WitherStormMath.headOffset(head[0] * size, head[1] * size - 0.6D * scale, head[2] * size, yaw, 0F);
    }

    public static Vec3 neckRoot(int slot, float development, float scale, float yaw) {
        double[] root = NECKS[slot];
        float size = size(development) * scale;
        return WitherStormMath.headOffset(root[0] * size, root[1] * size, root[2] * size, yaw, 0F);
    }

    public static float armWidth(int slot) { return ROOT_WIDTHS[slot - 3]; }

    // The same continuous curve drives the visible segments and the tracked contact point.
    // A renderer can reuse its output array for every sample.
    public static void armPoint(int slot, double t, double time, double[] out) {
        double[] p = ARMS[slot - 3];
        double q = 1D - t, a = q * q * q, b = 3D * q * q * t, c = 3D * q * t * t, d = t * t * t;
        out[0] = a * p[0] + b * p[3] + c * p[6] + d * p[9];
        out[1] = a * p[1] + b * p[4] + c * p[7] + d * p[10];
        out[2] = a * p[2] + b * p[5] + c * p[8] + d * p[11];
        WitherStormTentacleMotion.idle(slot, t, time, WitherStormStage.B.hasRaisedTentacle(slot) ? 1.8D : 1.2D, out);
    }
}
