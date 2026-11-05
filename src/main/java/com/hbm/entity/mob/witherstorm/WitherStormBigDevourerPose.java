package com.hbm.entity.mob.witherstorm;

import net.minecraft.util.Vec3;

/** Stage C (Big), +Y up and -Z forward. Slots 3-9 retain the existing contacts. */
public final class WitherStormBigDevourerPose {
    private static final double[][] HEADS = {{-5.6, 4.4, -7.8}, {-0.8, 2.8, -9.0}, {4.0, 3.7, -8.0}};
    private static final double[][] NECKS = {{-5.4, 7.0, -3.0}, {-0.8, 6.0, -3.7}, {4.2, 6.5, -2.8}};
    // One heavy lateral hook, six contact arms and three smaller rear hanging curls.
    private static final double[][] ARMS = {
        {-11,10,3, -18,7,0, -30,6,-3, -40,13,-5, -43,23,-4, -40,32,-2},
        {10,8,5, 14,2,6, 15,-7,7, 20,-13,5, 24,-11,3, 23,-5,1},
        {0,4,4, 1,-2,5, -1,-10,7, -5,-17,8, -11,-22,6, -16,-22,4},
        {5,5,0, 6,-1,-1, 7,-7,-3, 5,-14,-4, 0,-17,-4, -3,-15,-5},
        {-5,5,2, -7,-2,3, -8,-9,5, -12,-14,6, -17,-14,4, -19,-10,2},
        {-7,5,-2, -10,0,-4, -14,-5,-6, -19,-8,-8, -24,-5,-9, -25,0,-8},
        {7,6,9, 10,0,12, 13,-6,15, 18,-10,15, 22,-8,12, 23,-3,10},
        {-9,7,10, -11,1,13, -10,-6,16, -13,-12,17, -18,-15,14, -21,-12,12},
        {1,5,12, 3,-1,15, 2,-8,17, -2,-13,18, -6,-13,16, -7,-10,14},
        {10,9,11, 13,4,14, 15,-2,18, 14,-7,21, 10,-10,21, 7,-8,19}
    };
    private static final float[] WIDTHS = {5.1F, 3.6F, 2.9F, 2.0F, 2.2F, 3.0F, 2.8F, 1.8F, 1.5F, 2.1F};
    public static final int ARM_COUNT = ARMS.length;
    private WitherStormBigDevourerPose() {}
    public static float size(float development) { return 1F + (development - 0.775F) * 0.6F; }
    public static float headSize(int slot, float development) {
        return size(development) * (slot == 1 ? 1.18F : slot == 0 ? 1.10F : 1.05F);
    }
    public static Vec3 headBase(int slot, float development, float scale, float yaw) {
        double[] p = HEADS[slot];
        float s = size(development) * scale;
        return WitherStormMath.headOffset(p[0] * s, p[1] * s - 0.6D * scale, p[2] * s, yaw, 0F);
    }
    public static Vec3 neckRoot(int slot, float development, float scale, float yaw) {
        double[] p = NECKS[slot];
        float s = size(development) * scale;
        return WitherStormMath.headOffset(p[0] * s, p[1] * s, p[2] * s, yaw, 0F);
    }
    public static float armWidth(int slot) { return WIDTHS[slot - 3]; }
    public static int armSegments(int slot) { return slot == 3 ? 20 : 14; }
    public static void armPoint(int slot, double t, double time, double[] out) {
        double[] p = ARMS[slot - 3];
        int count = p.length / 3 - 1;
        double u = t * count;
        int i = Math.min((int)u, count - 1);
        double f = u - i, f2 = f * f, f3 = f2 * f;
        // Interpolate the directed bends, including their tangents, rather than rigid rods.
        for(int axis = 0; axis < 3; axis++) {
            double a = p[Math.max(0, i - 1) * 3 + axis], b = p[i * 3 + axis];
            double c = p[(i + 1) * 3 + axis], d = p[Math.min(count, i + 2) * 3 + axis];
            out[axis] = 0.5D * (2D * b + (c - a) * f + (2D * a - 5D * b + 4D * c - d) * f2
                + (-a + 3D * b - 3D * c + d) * f3);
        }
        WitherStormTentacleMotion.idle(slot, t, time, slot == 3 ? 1.8D : 1.4D, out);
    }
}
