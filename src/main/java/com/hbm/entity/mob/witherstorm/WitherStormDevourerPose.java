package com.hbm.entity.mob.witherstorm;

import net.minecraft.util.Vec3;

/** Stage C (Small) attachments in the torso's +Y-up, -Z-forward space. */
public final class WitherStormDevourerPose {
    private static final double[][] HEADS = {
        {-4.25, 3.15, -7.55}, {-0.65, 1.55, -8.45}, {3.15, 2.05, -7.40}
    };
    private static final double[][] NECKS = {
        {-4.40, 5.05, -4.10}, {-0.70, 4.00, -4.80}, {3.30, 4.50, -4.15}
    };
    // Art-directed bends. Slot 3 is the long raised arm; slots 8 and 9 retain the devouring contacts.
    private static final double[][] ARMS = {
        {-6.2,7.3,1, -9,6.7,-0.5, -13,5.6,-2.5, -17,5.1,-4, -21.5,5.6,-4.9,
            -25.3,7.2,-5.8, -28.6,9.8,-6.4, -31.2,13.5,-6.5, -32.4,17.4,-6.8,
            -32.0,21.0,-7.5, -30.6,24.1,-8.1, -29,26.7,-8.6},
        {6.3,7.3,5.7, 7.6,3.5,6.3, 8.2,-0.7,6.7, 9.1,-5.4,6.4, 10.7,-9.2,5.9,
            13.2,-10.1,4.8, 15.3,-7.7,3.4, 15.7,-4.4,2.7, 15.0,-1.9,2.3},
        {0.8,2.4,3.6, 0.6,-1.1,4.0, -0.6,-5.5,4.6, -0.3,-9.5,5.2,
            -1.4,-13.3,5.4, -4.3,-16.0,4.9, -7.8,-18.0,3.7, -11.5,-19.5,2.6},
        {4.4,3.1,-0.2, 4.1,0.6,-0.9, 3.6,-2.8,-1.8, 4.0,-5.6,-2.5,
            3.8,-8.2,-2.5, 2.9,-9.8,-2.1},
        {-2.7,2.5,1.2, -3.5,-0.2,1.0, -3.7,-3.5,1.6, -4.0,-6.4,2.8,
            -5.1,-8.7,3.0, -6.2,-9.7,2.1, -6.1,-11.0,1.5},
        {-4.3,3.0,-2.6, -5.3,-0.2,-4.1, -6.7,-3.7,-5.1, -9.1,-6.4,-6.0,
            -12.0,-7.6,-6.4, -14.7,-6.6,-6.8, -16.6,-4.6,-7.5, -18.0,-2.7,-8.7},
        {5.2,3.6,6.4, 6.5,0.9,7.3, 8.3,-2.0,9.0, 11.6,-4.5,10.5,
            14.8,-5.5,11.6, 17.4,-4.4,11.2, 18.5,-2.5,10.5, 18.0,-0.4,9.8}
    };
    private static final float[] ROOT_WIDTHS = {3.8F, 3.1F, 2.35F, 1.15F, 1.55F, 2.55F, 2.30F};

    private WitherStormDevourerPose() {}

    public static float size(float development) { return 1F + (development - 0.55F) * 0.65F; }

    public static float headSize(int slot, float development) {
        return size(development) * (slot == 1 ? 0.96F : slot == 0 ? 1.04F : 0.91F);
    }

    public static Vec3 headBase(int slot, float development, float scale, float yaw) {
        double[] head = HEADS[slot];
        float size = size(development) * scale;
        return WitherStormMath.headOffset(head[0] * size, head[1] * size - 0.6D * scale, head[2] * size, yaw, 0F);
    }

    public static Vec3 neckRoot(int slot, float development, float scale, float yaw) {
        double[] neck = NECKS[slot];
        float size = size(development) * scale;
        return WitherStormMath.headOffset(neck[0] * size, neck[1] * size, neck[2] * size, yaw, 0F);
    }

    public static int armSegments(int slot) { return ARMS[slot - 3].length / 3 - 1; }
    public static float armWidth(int slot) { return ROOT_WIDTHS[slot - 3]; }

    public static void armPoint(int slot, double t, double time, double[] out) {
        double[] points = ARMS[slot - 3];
        int count = armSegments(slot);
        double segment = t * count;
        int start = Math.min((int)segment, count - 1);
        double f = segment - start;
        int p = start * 3;
        out[0] = points[p] + (points[p + 3] - points[p]) * f;
        out[1] = points[p + 1] + (points[p + 4] - points[p + 1]) * f;
        out[2] = points[p + 2] + (points[p + 5] - points[p + 2]) * f;
        WitherStormTentacleMotion.idle(slot, t, time, slot == 3 ? 1.65D : 1.8D, out);
    }
}
