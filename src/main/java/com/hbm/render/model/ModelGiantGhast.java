package com.hbm.render.model;

import java.util.Random;

import com.hbm.entity.mob.giantghast.EntityGiantGhast;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public final class ModelGiantGhast {
    private static final double EDGE = EntityGiantGhast.WIDTH / 2;
    private static final double BASE = EntityGiantGhast.BODY_BASE;
    private static final double ROOF = EntityGiantGhast.ROOF;
    private static final double CORE = EntityGiantGhast.CORE_WIDTH / 2;
    private static final double CORE_TOP = ROOF + EntityGiantGhast.CORE_RISE;
    private static final int ASH = 0x414049, SIDE = 0x39383f, TOP = 0x51505a;
    private static final int SOCKET = 0x272229, SHADOW = 0x171317, JAW = 0x55515a;
    private static final Tessellator TESS = Tessellator.instance;

    private final double[][] flecks = new double[5 * 32][];
    private final float[] tentacleLengths = { 3.1F, 3.85F, 3.5F, 3.62F, 3.92F, 3.1F, 3.8F, 3.36F, 3.68F };

    public ModelGiantGhast() {
        Random random = new Random(192817);
        for (int i = 0; i < flecks.length; i++) {
            flecks[i] = new double[] { random.nextInt(29) * 0.375 - 5.6, random.nextInt(17) * 0.375 + 4.32,
                0.18 + random.nextInt(4) * 0.19, 0.16 + random.nextInt(3) * 0.22, random.nextInt(5) };
        }
    }

    public void render(EntityGiantGhast boss, float age, float death) {
        int pose = boss.pose();
        boolean hit = (pose & 16) != 0;
        boolean screaming = (pose & 8) != 0 || death > 0 || hit;
        float alpha = 1 - Math.max(0, death - 0.25F) / 0.75F;
        float savedX = OpenGlHelper.lastBrightnessX, savedY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (death > 0) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            TESS.startDrawingQuads();
            box(-EDGE, BASE, -EDGE, EDGE, ROOF, EDGE, hit ? 0x60424b : ASH, alpha);
            shellDetails(alpha);
            for (int face = 0; face < 3; face++) {
                double open = opening(pose, face, age, screaming, hit);
                drawFace(face, open, blink(age, boss.getEntityId(), face), screaming, hit, alpha, false);
            }
            box(-CORE, ROOF, -CORE, CORE, ROOF + 0.28, CORE, 0x5b242b, alpha);
            box(-CORE + 0.19, ROOF + 0.28, -CORE + 0.19, CORE - 0.19, CORE_TOP,
                CORE - 0.19, 0x8e2530, alpha);
            TESS.draw();
            for (int i = 0; i < tentacleLengths.length; i++) tentacle(i, age, screaming, alpha);

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            TESS.startDrawingQuads();
            for (int face = 0; face < 3; face++) {
                drawFace(face, opening(pose, face, age, screaming, hit), blink(age, boss.getEntityId(), face),
                    screaming, hit, alpha, true);
            }
            coreGlow(age, boss.phase(), hit, alpha);
            TESS.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, savedX, savedY);
            GL11.glPopAttrib();
        }
    }

    private static boolean blink(float age, int id, int face) {
        return ((int)age + id + face * 17) % 113 < 4;
    }

    private static double opening(int pose, int face, float age, boolean scream, boolean hit) {
        if (scream) return (hit ? 0.82 : 0.92) + Math.sin(age * 0.83 + face) * 0.07;
        if ((pose & (1 << face)) != 0) return 0.23 + ((pose >> 8) & 15) * 0.052;
        return 0.06 + Math.sin(age * 0.10 + face) * 0.026;
    }

    private void shellDetails(float alpha) {
        for (int side = 0; side < 5; side++) {
            for (int i = 0; i < 32; i++) {
                double[] mark = flecks[side * 32 + i];
                double a = mark[0], b = mark[1], w = mark[2], h = mark[3];
                int color = (int)mark[4] < 2 ? 0x47464e : (int)mark[4] == 2 ? 0x575660 : 0x393840;
                switch (side) {
                    case 0: front(a, b, a + w, b + h, EDGE + 0.004, color, alpha); break;
                    case 1: front(a, b, a + w, b + h, -EDGE - 0.004, color, alpha); break;
                    case 2: flank(a, b, a + w, b + h, -EDGE - 0.004, color, alpha); break;
                    case 3: flank(a, b, a + w, b + h, EDGE + 0.004, color, alpha); break;
                    default:
                        double z = (b - 4.32) * 1.84 - 5.6;
                        flat(a, z, a + w, z + h, ROOF + 0.004, color, alpha);
                        break;
                }
            }
        }
        front(-EDGE, ROOF - 0.34, EDGE, ROOF - 0.08, EDGE + 0.009, TOP, alpha);
        front(-EDGE, BASE + 0.06, EDGE, BASE + 0.32, EDGE + 0.009, SIDE, alpha);
        flank(-EDGE, BASE + 0.06, EDGE, BASE + 0.32, -EDGE - 0.009, SIDE, alpha);
        flank(-EDGE, BASE + 0.06, EDGE, BASE + 0.32, EDGE + 0.009, SIDE, alpha);
    }

    private void drawFace(int face, double opening, boolean blink, boolean scream, boolean hit, float alpha, boolean glow) {
        double x = 0, y = EntityGiantGhast.mouthY(face);
        double scale = face == 1 ? 1.16 : 0.74;
        double z = glow ? 0.027 : 0.018;
        double halfMouth = (0.82 + opening * 0.35) * scale;
        double mouthHeight = (0.20 + 1.38 * opening) * scale;
        double eyeY = y + 1.71 * scale;
        for (int sign = -1; sign <= 1; sign += 2) {
            double eyeX = x + sign * 1.20 * scale;
            if (!glow) {
                faceQuad(face, eyeX - 0.55 * scale, eyeY - 0.30 * scale, eyeX + 0.55 * scale, eyeY + 0.40 * scale, z, SOCKET, alpha);
                faceQuad(face, eyeX - 0.56 * scale, eyeY + (scream ? 0.20 : 0.35) * scale, eyeX + 0.55 * scale,
                    eyeY + 0.51 * scale, z + 0.003, JAW, alpha);
            } else {
                double eyeHeight = (blink && !scream ? 0.04 : scream ? 0.28 : 0.15 + opening * 0.40) * scale;
                faceQuad(face, eyeX - 0.43 * scale, eyeY - eyeHeight / 2, eyeX + 0.43 * scale,
                    eyeY + eyeHeight / 2, z, hit ? 0xff9884 : 0xff2530, alpha);
                faceQuad(face, eyeX - 0.22 * scale, eyeY - eyeHeight / 2, eyeX + 0.20 * scale,
                    eyeY, z + 0.002, 0xff7257, alpha);
            }
        }
        if (!glow) {
            faceQuad(face, x - halfMouth - 0.20 * scale, y - mouthHeight * 0.58 - 0.15 * scale, x + halfMouth + 0.20 * scale,
                y + mouthHeight * 0.62 + 0.18 * scale, z, SOCKET, alpha);
            faceQuad(face, x - halfMouth, y - mouthHeight * 0.57, x + halfMouth, y + mouthHeight * 0.55, z + 0.003, SHADOW, alpha);
            faceQuad(face, x - halfMouth * 0.81, y - mouthHeight * 0.70 - 0.11 * scale, x + halfMouth * 0.81,
                y - mouthHeight * 0.58, z + 0.003, JAW, alpha);
        } else {
            faceQuad(face, x - halfMouth * 0.79, y - mouthHeight * 0.38, x + halfMouth * 0.79,
                y + mouthHeight * (0.16 + opening * 0.15), z, opening > 0.5 ? 0xe82727 : 0x982032, alpha);
            if (opening > 0.4) {
                faceQuad(face, x - halfMouth * 0.37, y - mouthHeight * 0.26, x + halfMouth * 0.43,
                    y + mouthHeight * 0.12, z + 0.002, scream ? 0xff5c4b : 0xffad65, alpha);
            }
        }
    }

    private static void faceQuad(int face, double a0, double y0, double a1, double y1, double depth, int color, float alpha) {
        if (face == 1) front(a0, y0, a1, y1, EDGE + depth, color, alpha);
        else flank(EntityGiantGhast.SIDE_FACE_Z + a0, y0, EntityGiantGhast.SIDE_FACE_Z + a1, y1,
            (face - 1) * (EDGE + depth), color, alpha);
    }

    private void tentacle(int index, float age, boolean scream, float alpha) {
        double swing = Math.sin(age * (scream ? 0.26 : 0.13) + index * 1.48);
        double twist = Math.cos(age * 0.1 + index * 2.43);
        double length = tentacleLengths[index];
        GL11.glPushMatrix();
        GL11.glTranslated((index % 3 - 1) * 3.1, BASE + 0.08, (index / 3 - 1) * 3.1);
        GL11.glRotated(swing * (scream ? 17 : 9), 1, 0, 0);
        GL11.glRotated(twist * 8, 0, 0, 1);
        TESS.startDrawingQuads();
        box(-0.68, -length * 0.58, -0.68, 0.68, 0.08, 0.68, 0x35343c, alpha);
        TESS.draw();
        GL11.glTranslated(0, -length * 0.54, 0);
        GL11.glRotated(swing * 13 + 5, 1, 0, 0);
        TESS.startDrawingQuads();
        box(-0.52, -length * 0.44, -0.52, 0.52, 0, 0.52, 0x2a2830, alpha);
        TESS.draw();
        GL11.glPopMatrix();
    }

    private void coreGlow(float age, int phase, boolean hit, float alpha) {
        int color = hit ? 0xffc6b3 : phase == 3 ? 0xff3532 : 0xd52b32;
        double pulse = 0.49 + 0.10 * Math.sin(age * (phase == 3 ? 0.35 : 0.15));
        flat(-0.97, -0.73, 0.97, 0.73, CORE_TOP + 0.006, color, alpha);
        flat(-0.73, -0.97, 0.73, 0.97, CORE_TOP + 0.008, color, alpha);
        flat(-pulse, -pulse, pulse, pulse, CORE_TOP + 0.013, hit || phase == 3 ? 0xffb582 : 0xff5d4a, alpha);
        front(-0.68, ROOF + 0.36, 0.68, ROOF + 0.52, CORE - 0.183, color, alpha);
        front(-0.68, ROOF + 0.36, 0.68, ROOF + 0.52, -CORE + 0.183, color, alpha);
        flank(-0.68, ROOF + 0.36, 0.68, ROOF + 0.52, CORE - 0.183, color, alpha);
        flank(-0.68, ROOF + 0.36, 0.68, ROOF + 0.52, -CORE + 0.183, color, alpha);
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, int color, float alpha) {
        front(x0, y0, x1, y1, z1, color, alpha);
        front(x0, y0, x1, y1, z0, shade(color, 0.80), alpha);
        flank(z0, y0, z1, y1, x0, shade(color, 0.87), alpha);
        flank(z0, y0, z1, y1, x1, shade(color, 0.93), alpha);
        flat(x0, z0, x1, z1, y0, shade(color, 0.68), alpha);
        flat(x0, z0, x1, z1, y1, shade(color, 1.20), alpha);
    }

    private static int shade(int color, double amount) {
        return (int)((color >> 16 & 255) * amount) << 16 | (int)((color >> 8 & 255) * amount) << 8 | (int)((color & 255) * amount);
    }

    private static void front(double x0, double y0, double x1, double y1, double z, int rgb, float alpha) {
        TESS.setColorRGBA_I(rgb, (int)(alpha * 255));
        TESS.addVertex(x0, y0, z); TESS.addVertex(x1, y0, z); TESS.addVertex(x1, y1, z); TESS.addVertex(x0, y1, z);
    }

    private static void flank(double z0, double y0, double z1, double y1, double x, int rgb, float alpha) {
        TESS.setColorRGBA_I(rgb, (int)(alpha * 255));
        TESS.addVertex(x, y0, z0); TESS.addVertex(x, y0, z1); TESS.addVertex(x, y1, z1); TESS.addVertex(x, y1, z0);
    }

    private static void flat(double x0, double z0, double x1, double z1, double y, int rgb, float alpha) {
        TESS.setColorRGBA_I(rgb, (int)(alpha * 255));
        TESS.addVertex(x0, y, z0); TESS.addVertex(x1, y, z0); TESS.addVertex(x1, y, z1); TESS.addVertex(x0, y, z1);
    }
}
