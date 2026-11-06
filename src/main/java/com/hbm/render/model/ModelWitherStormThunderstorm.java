package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormThunderstormPose;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Stage D's hooked, stratified mantle. Only exposed half-block faces are retained. */
public final class ModelWitherStormThunderstorm {
    private static final int OX = 58, OY = 4, OZ = 34;
    private static final int[][] FACES = {{5,1,3,7}, {0,4,6,2}, {6,7,3,2}, {0,1,5,4}, {4,5,7,6}, {1,0,2,3}};
    // Branches in front-projected model space. Grooves expose a broken luminous substrate.
    private static final double[][] CRACKS = {
        {-15,44,-10,38}, {-10,38,-12,31}, {-12,31,-5,25}, {-5,25,-7,17},
        {-10,38,-4,35}, {-4,35,-1,30}, {-12,31,-18,29},
        {4,39,1,32}, {1,32,5,27}, {5,27,1,20}, {1,20,5,15},
        {5,27,11,30}, {11,30,14,26}, {1,20,-5,22}, {-5,22,-9,19}
    };
    private final double[] point = new double[3];
    private final FloatBuffer frame = BufferUtils.createFloatBuffer(16);
    private final int[][] arms = new int[7][], distantArms = new int[7][];
    private final float[][] widths = new float[7][], distantWidths = new float[7][];
    private int mantle, fractures;

    public void render(EntityWitherStorm boss, double time, float partial, boolean detailed) {
        if(mantle == 0) compile();
        GL11.glPushMatrix();
        float size = WitherStormThunderstormPose.size(boss.development());
        GL11.glScalef(size, size, size);
        GL11.glCallList(mantle);
        for(int arm = 0; arm < arms.length; arm++) {
            int[] segments = detailed ? arms[arm] : distantArms[arm];
            float[] taper = detailed ? widths[arm] : distantWidths[arm];
            boss.posedArmPoint(arm + 3, 0D, time, partial, point);
            for(int i = 0; i < segments.length; i++) {
                double x = point[0], y = point[1], z = point[2];
                boss.posedArmPoint(arm + 3, (double)(i + 1) / segments.length, time, partial, point);
                WitherStormMesh.renderSegment(frame, segments[i], taper[i], x, y, z, point[0], point[1], point[2]);
            }
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        float pulse = 224F + 16F * (float)Math.sin(time * 0.045D);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, pulse, pulse);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glCallList(fractures);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static double crackDistance(double x, double y) {
        double best = Double.MAX_VALUE;
        for(double[] p : CRACKS) {
            double dx = p[2] - p[0], dy = p[3] - p[1];
            double t = Math.max(0D, Math.min(1D, ((x - p[0]) * dx + (y - p[1]) * dy) / (dx * dx + dy * dy)));
            double a = x - p[0] - t * dx, b = y - p[1] - t * dy;
            best = Math.min(best, a * a + b * b);
        }
        return Math.sqrt(best);
    }

    private void compile() {
        boolean[][][] hull = new boolean[116][140][110];
        boolean[][][] glow = new boolean[116][140][110];
        for(int x = 1; x < 115; x++) for(int y = 1; y < 139; y++) {
            double px = (x - OX + 0.5D) * 0.5D, py = (y - OY + 0.5D) * 0.5D;
            double crack = crackDistance(px, py);
            for(int z = 1; z < 109; z++) {
                double pz = (z - OZ + 0.5D) * 0.5D;
                int h = WitherStormMesh.hash(x / 4, y / 3, z / 4, 0);
                double relief = (h & 7) * 0.22D + (WitherStormMesh.hash(x, y, z, 1) & 3) * 0.09D;
                // Broad lower shoulders sweep back into an offset, hooked upper crest.
                // The unequal sides, sloping roof and recessed waist prevent a column or sphere silhouette.
                double center = 2D - py * 0.18D;
                double width = 16D + 7D * Math.sin(Math.min(1D, py / 65D) * Math.PI) - py * 0.035D;
                double across = (px - center) / width;
                double top = 63D - (px + 16D) * 0.42D - Math.abs(pz - 13D) * 0.12D + relief;
                double front = -10D + py * py * 0.0065D + Math.pow(Math.abs(across), 3D) * 5D - relief;
                double back = 23D + py * 0.12D - Math.pow(Math.abs(across), 3D) * 8D + relief;
                boolean body = py > 7D + Math.abs(px) * 0.13D && py < top
                    && Math.abs(px - center) < width + relief && pz > front && pz < back;
                // Branching cavities have intact dark lips and patchy emissive material at their floors.
                double groove = crack < 1.3D ? (1.3D - crack) * 2.4D : 0D;
                boolean cleft = groove > 0D && pz < front + groove;
                boolean rearCleft = py > 20D && py < 49D && Math.abs(px + py * 0.23D - 7D) < 0.9D && pz > back - 1.8D;
                boolean collar = py > 4D && py < 13D && px > -14D && px < 12D && pz > -8D && pz < 18D;
                hull[x][y][z] = body && !cleft && !rearCleft || collar;
                glow[x][y][z] = body && crack < 0.95D && pz < front + groove + 0.65D
                    && (WitherStormMesh.hash(x, y, z, 9) & 7) < 5;
            }
        }
        mantle = WitherStormMesh.compileHull(hull, OX, OY, OZ);
        fractures = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(fractures, GL11.GL_COMPILE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for(int x = 1; x < 115; x++) for(int y = 1; y < 139; y++) for(int z = 1; z < 109; z++) {
            if(!hull[x][y][z] || !glow[x][y][z]) continue;
            for(int f = 0; f < 6; f++) {
                if(hull[x + WitherStormMesh.DX[f]][y + WitherStormMesh.DY[f]][z + WitherStormMesh.DZ[f]]) continue;
                float intensity = 0.55F + (WitherStormMesh.hash(x,y,z,f) & 7) * 0.06F;
                t.setColorOpaque_F(0.61F * intensity, 0.12F * intensity, intensity);
                t.setNormal(WitherStormMesh.DX[f], WitherStormMesh.DY[f], WitherStormMesh.DZ[f]);
                for(int v : FACES[f]) t.addVertex((x - OX + (v & 1)) * 0.5D + WitherStormMesh.DX[f] * 0.003D,
                    (y - OY + ((v >> 1) & 1)) * 0.5D + WitherStormMesh.DY[f] * 0.003D,
                    (z - OZ + ((v >> 2) & 1)) * 0.5D + WitherStormMesh.DZ[f] * 0.003D);
            }
        }
        t.draw(); GL11.glEndList();
        for(int i = 0; i < arms.length; i++) {
            float[] fine = WitherStormMesh.taper(24), coarse = WitherStormMesh.taper(12);
            arms[i] = WitherStormMesh.compileArm(fine); distantArms[i] = WitherStormMesh.compileArm(coarse);
            float width = WitherStormThunderstormPose.armWidth(i + 3);
            for(int n = 0; n < fine.length; n++) fine[n] *= width;
            for(int n = 0; n < coarse.length; n++) coarse[n] *= width;
            widths[i] = fine; distantWidths[i] = coarse;
        }
    }
}
