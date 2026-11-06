package com.hbm.entity.mob.witherstorm;

import net.minecraft.util.Vec3;

/** Stage D anatomy, +Y up and -Z forward. The seven existing contacts remain authoritative. */
public final class WitherStormThunderstormPose {
    private static final double[][] HEADS = {{-9.0, 7.2, -12.0}, {-1.6, 4.0, -14.2}, {6.2, 6.1, -12.3}};
    private static final double[][] NECKS = {{-8.7, 10.2, -7.5}, {-1.6, 8.4, -8.5}, {6.0, 9.4, -6.8}};
    private static final double[][] ARMS = {
        {-10,8,0, -17,0,-3, -30,-10,-7, -37,-7,-8, -40,3,-5, -37,10,-3},
        {11,8,2, 18,0,3, 28,-6,4, 36,-3,3, 39,5,0, 35,12,-1},
        {-4,5,2, -6,-4,1, -7,-12,-1, -13,-18,-4, -23,-20,-9, -29,-17,-11},
        {3,5,-2, 4,-4,-4, 3,-12,-7, -3,-20,-9, -13,-25,-11, -21,-27,-13},
        {-12,9,9, -17,1,12, -22,-9,15, -28,-15,14, -32,-10,10, -31,-3,7},
        {8,6,10, 11,-4,14, 14,-12,18, 10,-19,20, 1,-24,17, -5,-21,14},
        {0,6,15, -2,-3,19, -5,-10,22, -2,-18,24, 6,-21,22, 12,-17,19}
    };
    private static final float[] WIDTHS = {4.4F, 4.0F, 3.3F, 3.7F, 3.2F, 3.5F, 3.0F};
    private WitherStormThunderstormPose() {}
    public static float size(float development) { return 1F + Math.max(0F, development - 0.9F) * 0.8F; }
    public static float headSize(int slot, float development) {
        return size(development) * (slot == 1 ? 1.55F : slot == 0 ? 1.35F : 1.28F);
    }
    public static Vec3 headBase(int slot, float development, float scale, float yaw) {
        double[] p = HEADS[slot]; float s = size(development) * scale;
        return WitherStormMath.headOffset(p[0] * s, p[1] * s - 0.6D * scale, p[2] * s, yaw, 0F);
    }
    public static Vec3 neckRoot(int slot, float development, float scale, float yaw) {
        double[] p = NECKS[slot]; float s = size(development) * scale;
        return WitherStormMath.headOffset(p[0] * s, p[1] * s, p[2] * s, yaw, 0F);
    }
    public static float armWidth(int slot) { return WIDTHS[slot - 3]; }
    public static void armPoint(int slot, double t, double time, double[] out) {
        double[] p = ARMS[slot - 3];
        int count = p.length / 3 - 1;
        double u = t * count;
        int i = Math.min((int)u, count - 1);
        double f = u - i, f2 = f * f, f3 = f2 * f;
        for(int axis = 0; axis < 3; axis++) {
            double a = p[Math.max(0, i - 1) * 3 + axis], b = p[i * 3 + axis];
            double c = p[(i + 1) * 3 + axis], d = p[Math.min(count, i + 2) * 3 + axis];
            out[axis] = 0.5D * (2D * b + (c - a) * f + (2D * a - 5D * b + 4D * c - d) * f2
                + (-a + 3D * b - 3D * c + d) * f3);
        }
        WitherStormTentacleMotion.idle(slot, t, time, 1.7D, out);
    }
}
