package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.WitherStormDestroyerPose;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormStage;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import static com.hbm.render.model.WitherStormMesh.DX;
import static com.hbm.render.model.WitherStormMesh.DY;
import static com.hbm.render.model.WitherStormMesh.DZ;

/** Retained Stage B mantle and articulated arms. */
public final class ModelWitherStormDestroyer {
    // Half-block cells. Solid banks overlap inside the hull; only the exposed faces are compiled.
    private static final int X = 44, Y = 34, Z = 36;
    private static final int OX = 22, OY = 4, OZ = 15;
    private static final int[][] BANKS = {
        // Broad shoulders, a deep back and two uneven banks that rise behind the faces.
        {-16, 8, -7, 16, 18, 12}, {-14, 12, -5, 15, 22, 14}, {-12, 8, 8, 13, 19, 16},
        {-15, 13, -8, -4, 22, 8}, {-11, 19, -5, 2, 26, 10}, {-7, 24, -2, 1, 28, 7},
        {-3, 15, -8, 12, 23, 12}, {2, 20, -2, 11, 27, 10}, {5, 23, 3, 13, 26, 11},
        {-18, 9, -3, -12, 17, 8}, {12, 9, -2, 18, 16, 12}, {14, 13, 2, 19, 17, 9},
        {-15, 17, -1, -8, 21, 5}, {9, 18, 2, 15, 22, 8},
        // Overlapping frontal ridges and the three heavy neck sockets.
        {-14, 11, -9, -6, 15, -5}, {-11, 15, -9, -7, 19, -6}, {-5, 13, -9, 0, 18, -5},
        {2, 12, -10, 7, 16, -6}, {7, 15, -9, 11, 19, -6}, {10, 10, -8, 16, 14, -4},
        {-15, 14, -10, -10, 16, -6}, {-13, 17, -10, -9, 18, -6}, {-10, 20, -8, -5, 22, -4},
        {-5, 22, -6, -1, 24, -3}, {-6, 24, -4, -3, 27, -1}, {-6, 27, 0, -3, 29, 4},
        {-4, 16, -10, -2, 20, -5}, {1, 17, -9, 5, 21, -4}, {3, 21, -5, 8, 23, -1},
        {8, 13, -10, 12, 15, -6}, {9, 17, -9, 13, 19, -3}, {12, 14, -7, 15, 17, -3},
        {-14, 5, -9, -6, 12, -2}, {-5, 3, -12, 5, 11, -4}, {6, 5, -9, 14, 12, -2},
        // Broken tiers under the body. The gaps here are visible from the ground.
        {-12, 5, -3, 12, 10, 12}, {-10, 2, 1, 9, 7, 11}, {-8, 0, 4, -2, 5, 11},
        {4, -1, -1, 10, 5, 5}, {-3, 0, -7, 3, 5, 1}, {2, 3, 10, 9, 9, 15},
        {-15, 7, 4, -8, 13, 11}, {10, 6, 3, 16, 12, 8}
    };
    private static final int FINE = 12, COARSE = 7;
    private static final float[] FINE_TAPER = WitherStormMesh.taper(FINE), COARSE_TAPER = WitherStormMesh.taper(COARSE);
    private final double[] point = new double[3];
    private final FloatBuffer frame = BufferUtils.createFloatBuffer(16);
    private int mantle;
    private int[] fineSegments, coarseSegments;

    public void render(EntityWitherStorm boss, double time, float partial, boolean detailed) {
        if(mantle == 0) compile();
        float size = WitherStormDestroyerPose.size(boss.development());
        GL11.glPushMatrix();
        GL11.glScalef(size, size, size);
        GL11.glCallList(mantle);
        for(int slot = 3; slot < 10; slot++) {
            boolean fine = detailed && WitherStormStage.B.hasRaisedTentacle(slot);
            renderArm(boss, slot, time, partial, fine ? fineSegments : coarseSegments, fine ? FINE_TAPER : COARSE_TAPER);
        }
        GL11.glPopMatrix();
    }

    private void renderArm(EntityWitherStorm boss, int slot, double time, float partial, int[] segments, float[] taper) {
        boss.posedArmPoint(slot, 0, time, partial, point);
        double x = point[0], y = point[1], z = point[2];
        float rootWidth = WitherStormDestroyerPose.armWidth(slot);
        for(int i = 0; i < segments.length; i++) {
            boss.posedArmPoint(slot, (double)(i + 1) / segments.length, time, partial, point);
            WitherStormMesh.renderSegment(frame, segments[i], rootWidth * taper[i], x, y, z,
                point[0], point[1], point[2]);
            x = point[0]; y = point[1]; z = point[2];
        }
    }

    private void compile() {
        boolean[][][] hull = new boolean[X][Y][Z];
        boolean[][][] skin = new boolean[X][Y][Z];
        for(int[] box : BANKS)
            for(int x = box[0]; x < box[3]; x++) for(int y = box[1]; y < box[4]; y++) for(int z = box[2]; z < box[5]; z++)
                hull[x + OX][y + OY][z + OZ] = true;
        // Grow sparse chips and short runs on the exposed banks, with no interior faces or detached body cells.
        for(int x = 2; x < X - 2; x++) for(int y = 2; y < Y - 2; y++) for(int z = 2; z < Z - 2; z++) {
            if(!hull[x][y][z]) continue;
            for(int f = 0; f < 6; f++) {
                int xx = x + DX[f], yy = y + DY[f], zz = z + DZ[f];
                int h = WitherStormMesh.hash(x, y, z, f);
                if(hull[xx][yy][zz] || (h & 31) > 3) continue;
                skin[xx][yy][zz] = true;
                if((h & 32) != 0 && f >= 2 && hull[x + 1][y][z]) skin[xx + 1][yy][zz] = true;
            }
        }
        for(int x = 0; x < X; x++) for(int y = 0; y < Y; y++) for(int z = 0; z < Z; z++) hull[x][y][z] |= skin[x][y][z];
        mantle = WitherStormMesh.compileHull(hull, OX, OY, OZ);
        fineSegments = WitherStormMesh.compileArm(FINE_TAPER);
        coarseSegments = WitherStormMesh.compileArm(COARSE_TAPER);
    }
}
