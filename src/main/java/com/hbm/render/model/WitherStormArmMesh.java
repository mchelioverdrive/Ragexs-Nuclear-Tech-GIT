package com.hbm.render.model;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.Tessellator;

/** Immutable Stage D section templates shared by all seven arms and transformed into one batch. */
final class WitherStormArmMesh {
    private static final int[][] FACES = {{5,1,3,7}, {0,4,6,2}, {6,7,3,2}, {0,1,5,4}, {4,5,7,6}, {1,0,2,3}};
    final float[] taper;
    final float[][] sections;

    WitherStormArmMesh(int count) {
        taper = WitherStormMesh.taper(count);
        sections = new float[count][];
        for(int i = 0; i < count; i++) {
            FloatBuffer b = FloatBuffer.allocate(576);
            double r = 0.5D * taper[i + 1] / taper[i], end = i == count - 1 ? 1D : 1.075D;
            double a = -0.5D, c = 0.5D, y = -0.075D, slope = (c - r) / (end - y);
            float n = (float)(1D / Math.sqrt(1D + slope * slope)), up = (float)slope * n;
            quad(b,n,up,0,i, c,y,c, c,y,a, r,end,-r, r,end,r);
            quad(b,-n,up,0,i+1, a,y,a, a,y,c, -r,end,r, -r,end,-r);
            quad(b,0,up,n,i+2, a,y,c, c,y,c, r,end,r, -r,end,r);
            quad(b,0,up,-n,i+3, c,y,a, a,y,a, -r,end,-r, r,end,-r);
            boxFace(b,2,-r,y,-r,r,end,r,i+4);
            boxFace(b,3,a,y,a,c,end,c,i+5);
            if(i < count - 2) {
                double offset = i % 2 == 0 ? -0.52D : 0.07D;
                box(b,offset,0.08,-0.61,offset+0.43,0.52,-0.26,i+77);
                if(i % 3 != 2) box(b,-0.28,0.36,0.24,0.17,0.78,0.53,i+159);
                if(i % 3 == 0) box(b,0.28,0.24,-0.22,0.61,0.59,0.18,i+199);
            }
            sections[i] = new float[b.position()];
            b.flip(); b.get(sections[i]);
        }
    }

    void emit(Tessellator t, int section, float width, double[] points, int offset) {
        double x = points[offset], y = points[offset + 1], z = points[offset + 2];
        double dx = points[offset + 3] - x, dy = points[offset + 4] - y, dz = points[offset + 5] - z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(len < 0.0001D || width < 0.0001F) return;
        double xy = Math.sqrt(dx * dx + dy * dy);
        double ax = xy > 0.0001D ? dy / xy : 1D, ay = xy > 0.0001D ? -dx / xy : 0D;
        double bx = dx / len, by = dy / len, bz = dz / len;
        double cx = ay * bz, cy = -ax * bz, cz = ax * by - ay * bx;
        float[] data = sections[section];
        for(int q = 0; q < data.length; q += 24) {
            double nx = data[q] / width, ny = data[q + 1] / len, nz = data[q + 2] / width;
            double tx = nx * ax + ny * bx + nz * cx, ty = nx * ay + ny * by + nz * cy, tz = ny * bz + nz * cz;
            double normal = 1D / Math.sqrt(tx * tx + ty * ty + tz * tz);
            t.setNormal((float)(tx * normal), (float)(ty * normal), (float)(tz * normal));
            float shade = data[q + 3]; t.setColorOpaque_F(shade, shade, shade);
            for(int j = q + 4; j < q + 24; j += 5) {
                double vx = data[j] * width, vy = data[j + 1] * len, vz = data[j + 2] * width;
                t.addVertexWithUV(x + ax * vx + bx * vy + cx * vz, y + ay * vx + by * vy + cy * vz,
                    z + bz * vy + cz * vz, data[j + 3], data[j + 4]);
            }
        }
    }

    private static void box(FloatBuffer b, double x, double y, double z, double a, double c, double d, int seed) {
        for(int f = 0; f < 6; f++) boxFace(b,f,x,y,z,a,c,d,seed+13*f);
    }
    private static void boxFace(FloatBuffer b, int f, double x, double y, double z, double a, double c, double d, int seed) {
        double[] coordinates = new double[12];
        for(int i = 0; i < 4; i++) {
            int v = FACES[f][i];
            coordinates[i*3] = (v & 1) == 0 ? x : a;
            coordinates[i*3+1] = (v & 2) == 0 ? y : c;
            coordinates[i*3+2] = (v & 4) == 0 ? z : d;
        }
        quad(b,WitherStormMesh.DX[f],WitherStormMesh.DY[f],WitherStormMesh.DZ[f],seed,coordinates);
    }
    private static void quad(FloatBuffer b, float nx, float ny, float nz, int seed, double... coordinates) {
        float u = ((seed >>> 3) & 3) * 0.25F, v = ((seed >>> 7) & 3) * 0.25F;
        float shade = (0.90F + ((seed >>> 11) & 7) * 0.014F)
            * (ny < -0.5F ? 0.77F : nx > 0.5F ? 0.84F : nz < -0.5F ? 0.93F : 1F);
        b.put(nx).put(ny).put(nz).put(shade);
        for(int i = 0; i < 4; i++) b.put((float)coordinates[i*3]).put((float)coordinates[i*3+1]).put((float)coordinates[i*3+2])
            .put(u + (i == 0 || i == 3 ? 0 : 0.25F)).put(v + (i < 2 ? 0.25F : 0));
    }
}
