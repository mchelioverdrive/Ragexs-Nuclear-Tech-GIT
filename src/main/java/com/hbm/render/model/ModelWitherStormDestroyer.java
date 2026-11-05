package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.WitherStormDestroyerPose;
import com.hbm.entity.mob.witherstorm.WitherStormStage;
import java.nio.FloatBuffer;
import java.util.Random;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Retained Stage B mantle, broken block fragments and articulated arms. */
public final class ModelWitherStormDestroyer {
    // Half-block cells. Solid banks overlap inside the hull; only the exposed faces are compiled.
    private static final int X = 44, Y = 34, Z = 36;
    private static final int OX = 22, OY = 4, OZ = 15;
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};
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
    private static final float[] FINE_TAPER = taper(FINE), COARSE_TAPER = taper(COARSE);
    private final double[] point = new double[3];
    private final FloatBuffer frame = BufferUtils.createFloatBuffer(16);
    private int mantle, debris, outerDebris;
    private int[] fineSegments, coarseSegments;

    public void render(float development, double time, boolean detailed) {
        if(mantle == 0) compile();
        float size = WitherStormDestroyerPose.size(development);
        GL11.glPushMatrix();
        GL11.glScalef(size, size, size);
        GL11.glCallList(mantle);
        for(int slot = 3; slot < 10; slot++) {
            boolean fine = detailed && WitherStormStage.B.hasRaisedTentacle(slot);
            renderArm(slot, time, fine ? fineSegments : coarseSegments, fine ? FINE_TAPER : COARSE_TAPER);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(0, (float)Math.sin(time * 0.043D) * 0.40F, 0);
        GL11.glRotatef((float)Math.sin(time * 0.021D) * 8F, 0, 1, 0);
        GL11.glCallList(debris);
        GL11.glPopMatrix();
        if(detailed) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, (float)Math.cos(time * 0.038D) * 0.50F, 0);
            GL11.glRotatef((float)Math.cos(time * 0.026D) * 9F, 0, 1, 0);
            GL11.glCallList(outerDebris);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    private void renderArm(int slot, double time, int[] segments, float[] taper) {
        WitherStormDestroyerPose.armPoint(slot, 0, time, point);
        double x = point[0], y = point[1], z = point[2];
        float rootWidth = WitherStormDestroyerPose.armWidth(slot);
        for(int i = 0; i < segments.length; i++) {
            WitherStormDestroyerPose.armPoint(slot, (double)(i + 1) / segments.length, time, point);
            double dx = point[0] - x, dy = point[1] - y, dz = point[2] - z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if(len > 0.0001D) {
                // Use the body depth axis to fix roll, including where the tip turns nearly vertical.
                double xy = Math.sqrt(dx * dx + dy * dy);
                float ax = xy > 0.0001D ? (float)(dy / xy) : 1F, ay = xy > 0.0001D ? (float)(-dx / xy) : 0F;
                float bx = (float)(dx / len), by = (float)(dy / len), bz = (float)(dz / len);
                frame.clear();
                frame.put(ax).put(ay).put(0F).put(0F);
                frame.put(bx).put(by).put(bz).put(0F);
                frame.put(ay * bz).put(-ax * bz).put(ax * by - ay * bx).put(0F);
                frame.put((float)x).put((float)y).put((float)z).put(1F);
                frame.flip();
                GL11.glPushMatrix();
                GL11.glMultMatrix(frame);
                float width = rootWidth * taper[i];
                GL11.glScalef(width, (float)len, width);
                GL11.glCallList(segments[i]);
                GL11.glPopMatrix();
            }
            x = point[0]; y = point[1]; z = point[2];
        }
    }

    private static float[] taper(int count) {
        float[] widths = new float[count + 1];
        for(int i = 0; i <= count; i++) widths[i] = 0.038F + 0.962F * (float)Math.pow(1D - (double)i / count, 1.30D);
        return widths;
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
                int h = hash(x, y, z, f);
                if(hull[xx][yy][zz] || (h & 31) > 3) continue;
                skin[xx][yy][zz] = true;
                if((h & 32) != 0 && f >= 2 && hull[x + 1][y][z]) skin[xx + 1][yy][zz] = true;
            }
        }
        for(int x = 0; x < X; x++) for(int y = 0; y < Y; y++) for(int z = 0; z < Z; z++) hull[x][y][z] |= skin[x][y][z];
        mantle = begin();
        for(int x = 1; x < X - 1; x++) for(int y = 1; y < Y - 1; y++) for(int z = 1; z < Z - 1; z++) {
            if(!hull[x][y][z]) continue;
            double a = (x - OX) * 0.5D, b = (y - OY) * 0.5D, c = (z - OZ) * 0.5D;
            for(int f = 0; f < 6; f++) if(!hull[x + DX[f]][y + DY[f]][z + DZ[f]])
                face(f, a, b, c, a + 0.5D, b + 0.5D, c + 0.5D, hash(x, y, z, f));
        }
        end();
        fineSegments = compileArm(FINE_TAPER);
        coarseSegments = compileArm(COARSE_TAPER);
        debris = compileDebris(36, 0x57A6EBL);
        outerDebris = compileDebris(44, 0xB10C5L);
    }

    private static int[] compileArm(float[] taper) {
        int count = taper.length - 1;
        int[] lists = new int[count];
        for(int i = 0; i < count; i++) {
            lists[i] = begin();
            double top = 0.5D * taper[i + 1] / taper[i];
            double end = i == count - 1 ? 1D : 1.075D;
            armSection(top, end, i);
            if(i < count - 2) {
                double offset = i % 2 == 0 ? -0.52D : 0.07D;
                box(offset, 0.08, -0.61, offset + 0.43, 0.52, -0.26, i + 77);
                if(i % 3 != 2) box(-0.28, 0.36, 0.24, 0.17, 0.78, 0.53, i + 159);
                if(i % 3 == 0) box(0.28, 0.24, -0.22, 0.61, 0.59, 0.18, i + 199);
            }
            end();
        }
        return lists;
    }

    private static void armSection(double r, double end, int seed) {
        double a = -0.5D, b = 0.5D, y = -0.075D;
        double slope = (b - r) / (end - y);
        float normal = (float)(1D / Math.sqrt(1D + slope * slope)), up = (float)slope * normal;
        quad(normal, up, 0, seed, b,y,b, b,y,a, r,end,-r, r,end,r);
        quad(-normal, up, 0, seed+1, a,y,a, a,y,b, -r,end,r, -r,end,-r);
        quad(0, up, normal, seed+2, a,y,b, b,y,b, r,end,r, -r,end,r);
        quad(0, up, -normal, seed+3, b,y,a, a,y,a, -r,end,-r, r,end,-r);
        face(2, -r, y, -r, r, end, r, seed+4);
        face(3, a, y, a, b, end, b, seed+5);
    }

    private static int compileDebris(int count, long seed) {
        Random random = new Random(seed);
        int list = begin();
        for(int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            boolean low = i % 3 != 0;
            double radius = low ? 4.5D + random.nextDouble() * 7.5D : 10.8D + random.nextDouble() * 3.5D;
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius + 1.0D;
            double y = low ? -9D + random.nextDouble() * 8D : 0.5D + random.nextDouble() * 13D;
            double s = 0.13D + random.nextDouble() * 0.36D;
            box(x, y, z, x + s, y + s, z + s, random.nextInt());
        }
        end();
        return list;
    }

    private static int hash(int x, int y, int z, int face) {
        int h = x * 73428767 ^ y * 912931 ^ z * 438289 ^ face * 105943;
        return (h ^ h >>> 13) * 1274126177;
    }

    private static int begin() {
        int list = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator.instance.startDrawingQuads();
        return list;
    }

    private static void end() {
        Tessellator.instance.draw();
        GL11.glEndList();
    }

    private static void box(double x, double y, double z, double a, double b, double c, int seed) {
        for(int f = 0; f < 6; f++) face(f, x, y, z, a, b, c, seed + 13 * f);
    }

    private static void face(int f, double x, double y, double z, double a, double b, double c, int seed) {
        switch(f) {
            case 0: quad(1,0,0,seed, a,y,c, a,y,z, a,b,z, a,b,c); break;
            case 1: quad(-1,0,0,seed, x,y,z, x,y,c, x,b,c, x,b,z); break;
            case 2: quad(0,1,0,seed, x,b,c, a,b,c, a,b,z, x,b,z); break;
            case 3: quad(0,-1,0,seed, x,y,z, a,y,z, a,y,c, x,y,c); break;
            case 4: quad(0,0,1,seed, x,y,c, a,y,c, a,b,c, x,b,c); break;
            case 5: quad(0,0,-1,seed, a,y,z, x,y,z, x,b,z, a,b,z); break;
        }
    }

    private static void quad(float nx, float ny, float nz, int seed,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3) {
        Tessellator t = Tessellator.instance;
        double u = ((seed >>> 3) & 3) * 0.25D, v = ((seed >>> 7) & 3) * 0.25D;
        float shade = (0.90F + ((seed >>> 11) & 7) * 0.014F)
            * (ny < -0.5F ? 0.77F : nx > 0.5F ? 0.84F : nz < -0.5F ? 0.93F : 1F);
        t.setColorOpaque_F(shade, shade, shade);
        t.setNormal(nx, ny, nz);
        t.addVertexWithUV(x0, y0, z0, u, v + 0.25D);
        t.addVertexWithUV(x1, y1, z1, u + 0.25D, v + 0.25D);
        t.addVertexWithUV(x2, y2, z2, u + 0.25D, v);
        t.addVertexWithUV(x3, y3, z3, u, v);
    }
}
