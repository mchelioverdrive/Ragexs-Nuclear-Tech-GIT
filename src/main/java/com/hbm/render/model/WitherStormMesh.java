package com.hbm.render.model;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Retained storm-skin geometry shared by the Stage B and Stage C models. */
final class WitherStormMesh {
    static final int[] DX = {1, -1, 0, 0, 0, 0};
    static final int[] DY = {0, 0, 1, -1, 0, 0};
    static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private WitherStormMesh() {}

    static int compileHull(boolean[][][] hull, int ox, int oy, int oz) {
        int list = begin();
        for(int x = 1; x < hull.length - 1; x++) {
            for(int y = 1; y < hull[x].length - 1; y++) {
                for(int z = 1; z < hull[x][y].length - 1; z++) {
                    if(!hull[x][y][z]) continue;
                    double a = (x - ox) * 0.5D, b = (y - oy) * 0.5D, c = (z - oz) * 0.5D;
                    for(int f = 0; f < 6; f++) if(!hull[x + DX[f]][y + DY[f]][z + DZ[f]])
                        face(f, a, b, c, a + 0.5D, b + 0.5D, c + 0.5D, hash(x, y, z, f));
                }
            }
        }
        end();
        return list;
    }

    static void renderSegment(FloatBuffer frame, int section, float width, double x, double y, double z,
                              double endX, double endY, double endZ) {
        double dx = endX - x, dy = endY - y, dz = endZ - z;
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
            GL11.glScalef(width, (float)len, width);
            GL11.glCallList(section);
            GL11.glPopMatrix();
        }
    }

    static float[] taper(int count) {
        float[] widths = new float[count + 1];
        for(int i = 0; i <= count; i++) widths[i] = 0.038F + 0.962F * (float)Math.pow(1D - (double)i / count, 1.30D);
        return widths;
    }

    static int[] compileArm(float[] taper) {
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

    static int hash(int x, int y, int z, int face) {
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
