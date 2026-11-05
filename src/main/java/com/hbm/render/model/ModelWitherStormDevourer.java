package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.WitherStormDevourerPose;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** The Stage C (Small) mantle and its articulated arms. Heads are drawn by the part renderer. */
public final class ModelWitherStormDevourer {
    // Centers and radii of overlapping masses. The lower collar leans forward, the bulk
    // sweeps back and right, and the upper ridge hooks up and left.
    private static final double[][] LOBES = {
        {0, 5.4, 0.2, 7.4, 4.8, 6.7}, {1.4, 9.5, 5.1, 8.7, 7.1, 7.5},
        {2.9, 15.2, 8.4, 9.4, 10.8, 8.8}, {6.2, 14.1, 10.4, 6.4, 7.3, 7.7},
        {-0.6, 21.3, 8.1, 9.1, 9.8, 8.1}, {-4.7, 26.5, 5.6, 5.7, 5.7, 5.7},
        {-6.1, 27.7, 2.9, 3.3, 4.3, 4.1}, {4.6, 22.8, 9.4, 5.5, 5.8, 6.9},
        {-6.5, 17.3, 7.1, 3.8, 6.9, 4.8}, {8.6, 17.7, 7.4, 4.0, 5.7, 4.6},
        {-4.8, 6.6, -2.4, 3.7, 4.1, 4.8}, {3.6, 5.9, -2.5, 4.3, 4.0, 4.4},
        {-0.6, 4.4, -3.8, 3.0, 3.7, 3.9}, {4.2, 7.5, 8.8, 5.7, 4.7, 6.8}
    };
    // Clefts open onto the surface between the upper banks and on the back flank.
    private static final double[][] CLEFTS = {
        {-5.5, 14.2, 0.0, 3.9, 4.4, 3.4}, {5.8, 19.3, -0.9, 2.3, 5.1, 3.9},
        {-0.2, 28.8, 0.0, 2.2, 5.6, 3.8}, {9.4, 22.2, 15.6, 3.8, 4.6, 3.4}
    };
    private final int[][] sections = new int[10][];
    private final float[][] tapers = new float[10][];
    private final double[] point = new double[3];
    private final FloatBuffer frame = BufferUtils.createFloatBuffer(16);
    private int mantle;

    public void render(float development, double time) {
        if(mantle == 0) compile();
        float size = WitherStormDevourerPose.size(development);
        GL11.glPushMatrix();
        GL11.glScalef(size, size, size);
        GL11.glCallList(mantle);
        for(int slot = 3; slot < 10; slot++) {
            WitherStormDevourerPose.armPoint(slot, 0D, time, point);
            int[] arm = sections[slot];
            for(int i = 0; i < arm.length; i++) {
                double x = point[0], y = point[1], z = point[2];
                WitherStormDevourerPose.armPoint(slot, (double)(i + 1) / arm.length, time, point);
                WitherStormMesh.renderSegment(frame, arm[i], tapers[slot][i], x, y, z, point[0], point[1], point[2]);
            }
        }
        GL11.glPopMatrix();
    }

    private void compile() {
        int ox = 25, oy = 4, oz = 20;
        boolean[][][] hull = new boolean[56][74][63];
        for(int x = 1; x < hull.length - 1; x++) {
            for(int y = 1; y < hull[x].length - 1; y++) {
                for(int z = 1; z < hull[x][y].length - 1; z++) {
                    // Coherent steps give the crown ledges and the flanks broken vertical ridges.
                    // Fine relief breaks up their edges without generating detached cubes.
                    int terrace = WitherStormMesh.hash(x / 3, y / 2, z / 3, 0);
                    double threshold = 0.88D + (terrace & 7) * 0.043D
                        + (WitherStormMesh.hash(x, y, z, 1) & 3) * 0.023D;
                    double px = (x - ox + 0.5D) * 0.5D;
                    double py = (y - oy + 0.5D) * 0.5D;
                    double pz = (z - oz + 0.5D) * 0.5D;
                    hull[x][y][z] = inside(LOBES, px, py, pz, threshold) && !inside(CLEFTS, px, py, pz, threshold);
                }
            }
        }
        mantle = WitherStormMesh.compileHull(hull, ox, oy, oz);
        for(int slot = 3; slot < 10; slot++) {
            float[] taper = WitherStormMesh.taper(WitherStormDevourerPose.armSegments(slot));
            sections[slot] = WitherStormMesh.compileArm(taper);
            float width = WitherStormDevourerPose.armWidth(slot);
            for(int i = 0; i < taper.length; i++) taper[i] *= width;
            tapers[slot] = taper;
        }
    }

    private static boolean inside(double[][] shapes, double x, double y, double z, double threshold) {
        for(double[] shape : shapes) {
            double dx = (x - shape[0]) / shape[3];
            double dy = (y - shape[1]) / shape[4];
            double dz = (z - shape[2]) / shape[5];
            if(dx * dx + dy * dy + dz * dz < threshold) return true;
        }
        return false;
    }
}
