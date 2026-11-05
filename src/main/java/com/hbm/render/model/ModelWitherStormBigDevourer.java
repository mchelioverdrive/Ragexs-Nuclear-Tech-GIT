package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormBigDevourerPose;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Retained, deep sloping wall of corrupted blocks for Stage C (Big). */
public final class ModelWitherStormBigDevourer {
    private final double[] point = new double[3];
    private final FloatBuffer frame = BufferUtils.createFloatBuffer(16);
    private final int[][] arms = new int[WitherStormBigDevourerPose.ARM_COUNT][];
    private final float[][] widths = new float[WitherStormBigDevourerPose.ARM_COUNT][];
    private int mantle;

    public void render(EntityWitherStorm boss, double time, float partial) {
        if(mantle == 0) compile();
        float size = WitherStormBigDevourerPose.size(boss.development());
        GL11.glPushMatrix();
        GL11.glScalef(size, size, size);
        GL11.glCallList(mantle);
        for(int arm = 0; arm < arms.length; arm++) {
            int slot = arm + 3;
            boss.posedArmPoint(slot, 0D, time, partial, point);
            for(int i = 0; i < arms[arm].length; i++) {
                double x = point[0], y = point[1], z = point[2];
                boss.posedArmPoint(slot, (double)(i + 1) / arms[arm].length, time, partial, point);
                WitherStormMesh.renderSegment(frame, arms[arm][i], widths[arm][i], x, y, z, point[0], point[1], point[2]);
            }
        }
        GL11.glPopMatrix();
    }

    private void compile() {
        int ox = 36, oy = 4, oz = 22;
        boolean[][][] hull = new boolean[74][99][86];
        for(int x = 1; x < hull.length - 1; x++) for(int y = 1; y < hull[x].length - 1; y++)
            for(int z = 1; z < hull[x][y].length - 1; z++) {
                double px = (x - ox + 0.5D) * 0.5D, py = (y - oy + 0.5D) * 0.5D, pz = (z - oz + 0.5D) * 0.5D;
                int terrace = WitherStormMesh.hash(x / 4, y / 3, z / 4, 0);
                double relief = (terrace & 7) * 0.19D + (WitherStormMesh.hash(x, y, z, 1) & 3) * 0.08D;
                // A tall oblique sail: uneven high left crest, descending right ridge,
                // broad shoulders and a thick rear bank. The front slopes away from the skulls.
                double center = -0.075D * py;
                double halfWidth = 14.7D - py * 0.075D + relief;
                double top = 43D - (px + 12D) * 0.40D + relief
                    + (WitherStormMesh.hash(x / 6, 0, z / 8, 2) & 7) * 0.35D;
                double front = -5.3D + py * 0.30D - relief - (x / 6 % 3 == 0 ? 1.5D : 0D);
                double back = 13.3D + py * 0.20D + relief + (x / 7 % 3 == 1 ? 1.2D : 0D);
                boolean body = py > 5.5D + Math.abs(px) * 0.10D && py < top
                    && Math.abs(px - center) < halfWidth && pz > front && pz < back;
                // Open grooves cut into the sloping face and rear. Never cut through the slab.
                double ridge = px + py * 0.16D;
                boolean cleft = py > 14D && py < 36D && ((ridge > -5.2D && ridge < -3.8D)
                    || (ridge > 5.4D && ridge < 6.5D)) && pz < front + 2.0D;
                boolean rearCleft = py > 20D && py < 32D && px > 4D && px < 7D && pz > back - 2.4D;
                // Heavy sockets join the body to the head/arm roots below its ragged skirt.
                boolean collar = py > 3D && py < 10D && px > -10D && px < 10D && pz > -4.5D && pz < 10D;
                hull[x][y][z] = (body && !cleft && !rearCleft) || collar;
            }
        mantle = WitherStormMesh.compileHull(hull, ox, oy, oz);
        for(int i = 0; i < arms.length; i++) {
            float[] taper = WitherStormMesh.taper(WitherStormBigDevourerPose.armSegments(i + 3));
            arms[i] = WitherStormMesh.compileArm(taper);
            float width = WitherStormBigDevourerPose.armWidth(i + 3);
            for(int n = 0; n < taper.length; n++) taper[n] *= width;
            widths[i] = taper;
        }
    }
}
