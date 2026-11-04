package com.hbm.render.model;

import com.hbm.entity.mob.giantghast.EntityGiantGhast;
import com.hbm.entity.mob.giantghast.GiantGhastShape;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import static com.hbm.entity.mob.giantghast.GiantGhastShape.*;

public final class ModelGiantGhast {
    private static final Tessellator TESS = Tessellator.instance;
    private static final int CHARCOAL = 0x222222, PANEL = 0x191919, BLACK = 0x0d0d0d;
    private static final int ROOF_COLOR = 0x292929, RIDGE = 0x2d2d2d;
    private static final double MARK_DEPTH = 0.003;

    // Pixel panels in sixteenths of a face, stored as x, y, width, height, and color.
    private static final int[][] CASING = {
        {1, 13, 2, 1, PANEL}, {3, 12, 1, 1, PANEL}, {6, 14, 4, 1, 0x292929},
        {13, 13, 2, 1, PANEL}, {14, 11, 1, 2, PANEL}, {0, 10, 2, 1, PANEL},
        {2, 8, 2, 1, PANEL}, {1, 6, 2, 1, BLACK}, {2, 5, 1, 1, PANEL},
        {7, 7, 2, 1, PANEL}, {12, 7, 3, 1, PANEL}, {13, 5, 3, 1, BLACK},
        {1, 2, 4, 1, PANEL}, {3, 1, 4, 1, PANEL}, {8, 3, 3, 1, PANEL},
        {11, 2, 2, 1, PANEL}, {13, 1, 3, 1, PANEL}, {0, 0, 2, 1, 0x282828}
    };

    private static final int[][] FACE_MARKS = {
        {1, 12, 2, 1, PANEL}, {3, 11, 1, 1, PANEL}, {12, 12, 2, 1, PANEL},
        {13, 11, 1, 1, PANEL}, {0, 9, 2, 1, PANEL}, {1, 7, 2, 1, BLACK},
        {4, 7, 2, 1, BLACK}, {10, 7, 2, 1, BLACK}, {14, 8, 2, 1, PANEL},
        {2, 5, 1, 1, PANEL}, {13, 5, 2, 1, PANEL}, {0, 3, 3, 1, PANEL},
        {5, 2, 2, 1, PANEL}, {7, 1, 3, 1, PANEL}, {11, 3, 4, 1, PANEL},
        {13, 2, 3, 1, BLACK}, {14, 0, 2, 1, PANEL}
    };

    private static final String[] CORE_PIXELS = {
        "   dd  ",
        "  drrd ",
        " drhrrd",
        "drhhrd ",
        " drhrrd",
        "  drrd ",
        "   d   "
    };
    private static final double CORE_PIXEL = 0.32;
    private static final double[] TENTACLE_LENGTHS = {4.75, 5.65, 5.0, 5.40, 5.92, 5.57, 4.92, 5.62, 5.21};
    private static final double[] TENTACLE_RADII = {0.63, 0.68, 0.60, 0.68, 0.70, 0.65, 0.65, 0.69, 0.62};

    public void render(EntityGiantGhast boss, float age, float death) {
        int pose = boss.pose();
        boolean hit = (pose & 16) != 0;
        boolean exposed = boss.state() == EntityGiantGhast.State.EXPOSED;
        boolean dying = boss.state() == EntityGiantGhast.State.DYING;
        boolean agitated = (pose & 8) != 0 || hit || dying;
        float alpha = 1 - Math.max(0, death - 0.25F) / 0.75F;
        float savedX = OpenGlHelper.lastBrightnessX, savedY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
            if (death > 0) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            TESS.startDrawingQuads();
            body(hit, alpha);
            roof(alpha);
            for (int face = 0; face < 3; face++) face(face, pose, age, agitated, hit, alpha);
            core(false, age, exposed, hit, dying, alpha);
            TESS.draw();

            for (int i = 0; i < TENTACLE_LENGTHS.length; i++) tentacle(i, age, agitated, exposed || dying, alpha);

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            TESS.startDrawingQuads();
            for (int face = 0; face < 3; face++) eyes(face, boss.getEntityId(), pose, age, agitated, hit, alpha);
            if (!dying) core(true, age, exposed, hit, false, alpha);
            TESS.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, savedX, savedY);
            GL11.glPopAttrib();
        }
    }

    private static void body(boolean hit, float alpha) {
        double top = EntityGiantGhast.ROOF;
        int color = hit ? 0x302126 : 0x1e1e1e;
        wall(Surface.BACK, HEAD.minX, HEAD.minY, HEAD.maxX, top, HEAD.minZ, color, alpha);
        quad(Surface.BOTTOM, HEAD.minX, HEAD.minZ, HEAD.maxX, HEAD.maxZ, HEAD.minY, PANEL, alpha);
        flank(LEFT, Surface.LEFT, HEAD.minX, hit ? 0x302126 : 0x1f1f1f, alpha);
        flank(RIGHT, Surface.RIGHT, HEAD.maxX, hit ? 0x2a1d21 : 0x1b1b1b, alpha);
    }

    private static void flank(GiantGhastShape head, Surface side, double seam, int color, float alpha) {
        double top = EntityGiantGhast.ROOF;
        quad(side, HEAD.minZ, HEAD.minY, head.minZ, top, seam, color, alpha);
        quad(side, head.maxZ, HEAD.minY, HEAD.maxZ, top, seam, color, alpha);
        quad(side, head.minZ, HEAD.minY, head.maxZ, head.minY, seam, PANEL, alpha);
        quad(side, head.minZ, head.maxY, head.maxZ, top, seam, color, alpha);
        flankEnd(head, Surface.FRONT, head.maxZ, color, alpha);
        flankEnd(head, Surface.BACK, head.minZ, color, alpha);
        quad(Surface.BOTTOM, head.minX, head.minZ, head.maxX, head.maxZ, head.minY, PANEL, alpha);
        double x0 = head.minX + 0.38, x1 = head.maxX - 0.38;
        double z0 = head.minZ + 0.65, z1 = head.maxZ - 0.45;
        quad(Surface.TOP, head.minX, head.minZ, head.maxX, z0, head.maxY, ROOF_COLOR, alpha);
        quad(Surface.TOP, head.minX, z1, head.maxX, head.maxZ, head.maxY, ROOF_COLOR, alpha);
        quad(Surface.TOP, head.minX, z0, x0, z1, head.maxY, ROOF_COLOR, alpha);
        quad(Surface.TOP, x1, z0, head.maxX, z1, head.maxY, ROOF_COLOR, alpha);
        inset(Surface.TOP, x0, z0, x1, z1, head.maxY, head.maxY - 0.09, PANEL, alpha);
        quad(Surface.TOP, x0, z0, x1, z1, head.maxY - 0.09, CHARCOAL, alpha);
    }

    private static void flankEnd(GiantGhastShape head, Surface surface, double plane, int color, float alpha) {
        quad(surface, head.minX, head.minY, head.maxX, head.maxY, plane, color, alpha);
        double detail = plane + surface.sign * MARK_DEPTH;
        quad(surface, head.minX + 0.32, head.maxY - 1.18, head.maxX - 0.60, head.maxY - 0.85, detail, PANEL, alpha);
        quad(surface, head.minX + 0.82, head.minY + 3.08, head.maxX - 0.37, head.minY + 3.42, detail, BLACK, alpha);
        quad(surface, head.minX + 0.40, head.minY + 1.36, head.maxX - 0.35, head.minY + 1.69, detail, PANEL, alpha);
    }

    private static void roof(float alpha) {
        double top = EntityGiantGhast.ROOF, floor = HEAD.maxY;
        double rim = ROOF_RIGHT.minX;
        roofPlate(ROOF_FRONT, alpha);
        roofPlate(ROOF_BACK, alpha);
        roofPlate(ROOF_LEFT, alpha);
        roofPlate(ROOF_RIGHT, alpha);
        quad(Surface.TOP, -rim, -rim, rim, rim, floor, BLACK, alpha);
        inset(Surface.TOP, -rim, -rim, rim, rim, top, floor, PANEL, alpha);

        // A shallow ledge at the foot of the cavity wall leaves the red core clear.
        double lip = rim - 0.14, lipY = floor + 0.13;
        quad(Surface.TOP, -rim, -rim, rim, -lip, lipY, CHARCOAL, alpha);
        quad(Surface.TOP, -rim, lip, rim, rim, lipY, CHARCOAL, alpha);
        quad(Surface.TOP, -rim, -lip, -lip, lip, lipY, CHARCOAL, alpha);
        quad(Surface.TOP, lip, -lip, rim, lip, lipY, CHARCOAL, alpha);
        inset(Surface.TOP, -lip, -lip, lip, lip, lipY, floor, PANEL, alpha);

        for (int sign = -1; sign <= 1; sign += 2) {
            double x = sign * 2.55;
            quad(Surface.TOP, x - 0.36, -4.9, x + 0.36, 4.85, top + MARK_DEPTH, PANEL, alpha);
            x = sign * 4.15;
            quad(Surface.TOP, x - 0.48, -4.55, x + 0.48, 4.1, top + MARK_DEPTH, CHARCOAL, alpha);
            for (int i = 0; i < 3; i++) {
                double z = -3.85 + i * 3.55;
                quad(Surface.TOP, x - 0.88, z, x - 0.52, z + 1.42, top + MARK_DEPTH, PANEL, alpha);
            }
        }
        quad(Surface.TOP, -1.9, -4.65, 1.9, -4.25, top + MARK_DEPTH, RIDGE, alpha);
        quad(Surface.TOP, -1.9, 2.75, 1.9, 3.15, top + MARK_DEPTH, PANEL, alpha);
        quad(Surface.TOP, -1.9, 4.55, 1.9, 4.90, top + MARK_DEPTH, CHARCOAL, alpha);
    }

    private static void roofPlate(GiantGhastShape plate, float alpha) {
        quad(Surface.TOP, plate.minX, plate.minZ, plate.maxX, plate.maxZ, plate.maxY, ROOF_COLOR, alpha);
    }

    private static void face(int face, int pose, float age, boolean agitated, boolean hit, float alpha) {
        boolean center = face == 1;
        GiantGhastShape shape = center ? HEAD : face == 0 ? LEFT : RIGHT;
        Surface surface = faceSurface(face);
        double plane = facePlane(face);
        double u0 = center ? shape.minX : shape.minZ, u1 = center ? shape.maxX : shape.maxZ;
        double y0 = shape.minY, y1 = center ? EntityGiantGhast.ROOF : shape.maxY;
        double u = (u0 + u1) * 0.5, eyeY = EntityGiantGhast.eyeY(face), mouthY = EntityGiantGhast.mouthY(face);
        double spacing = eyeSpacing(face), eyeW = center ? 0.52 : 0.43, eyeH = center ? 0.39 : 0.33;
        double mouthW = center ? 0.85 : 0.65, mouthH = center ? 0.33 : 0.26;
        double inner = plane - surface.sign * 0.14;
        int skin = surface == Surface.RIGHT ? (hit ? 0x2a1d21 : 0x1b1b1b)
            : hit ? 0x302126 : center ? CHARCOAL : 0x1f1f1f;

        quad(surface, u0, y0, u1, mouthY - mouthH, plane, skin, alpha);
        quad(surface, u0, mouthY - mouthH, u - mouthW, mouthY + mouthH, plane, skin, alpha);
        quad(surface, u + mouthW, mouthY - mouthH, u1, mouthY + mouthH, plane, skin, alpha);
        quad(surface, u0, mouthY + mouthH, u1, eyeY - eyeH, plane, skin, alpha);
        quad(surface, u0, eyeY - eyeH, u - spacing - eyeW, eyeY + eyeH, plane, skin, alpha);
        quad(surface, u - spacing + eyeW, eyeY - eyeH, u + spacing - eyeW, eyeY + eyeH, plane, skin, alpha);
        quad(surface, u + spacing + eyeW, eyeY - eyeH, u1, eyeY + eyeH, plane, skin, alpha);
        quad(surface, u0, eyeY + eyeH, u1, y1, plane, skin, alpha);
        marks(surface, u0, y0, u1, y1, plane, FACE_MARKS, alpha);

        for (int sign = -1; sign <= 1; sign += 2) {
            double eye = u + sign * spacing;
            inset(surface, eye - eyeW, eyeY - eyeH, eye + eyeW, eyeY + eyeH, plane, inner, PANEL, alpha);
            quad(surface, eye - eyeW, eyeY - eyeH, eye + eyeW, eyeY + eyeH, inner, BLACK, alpha);
        }
        inset(surface, u - mouthW, mouthY - mouthH, u + mouthW, mouthY + mouthH, plane, inner, PANEL, alpha);
        quad(surface, u - mouthW, mouthY - mouthH, u + mouthW, mouthY + mouthH, inner, PANEL, alpha);
        double opening = 0.06 + (agitated ? 0.12 + Math.sin(age * 0.75 + face) * 0.04
            : (pose & (1 << face)) != 0 ? ((pose >> 8) & 15) * 0.009 : 0);
        quad(surface, u - mouthW * 0.84, mouthY - opening, u + mouthW * 0.84, mouthY + opening,
            inner + surface.sign * MARK_DEPTH, BLACK, alpha);
    }

    private static void eyes(int face, int id, int pose, float age, boolean agitated, boolean hit, float alpha) {
        Surface surface = faceSurface(face);
        double inner = facePlane(face) - surface.sign * (0.14 - MARK_DEPTH);
        double y = EntityGiantGhast.eyeY(face);
        boolean blink = !agitated && Math.floorMod((int)age + id + face * 17, 113) < 4;
        boolean charging = (pose & (1 << face)) != 0;
        double w = face == 1 ? 0.33 : 0.27;
        double h = blink ? 0.035 : charging || agitated ? 0.20 : 0.16;
        for (int sign = -1; sign <= 1; sign += 2) {
            double x = (face == 1 ? 0 : (LEFT.minZ + LEFT.maxZ) * 0.5) + sign * eyeSpacing(face);
            quad(surface, x - w, y - h, x + w, y + h, inner, hit ? 0xff5145 : 0xf51d24, alpha);
            quad(surface, x - w, y - h, x - w * 0.25, y + h, inner + surface.sign * MARK_DEPTH, 0xd3121b, alpha);
        }
    }

    private static Surface faceSurface(int face) { return face == 1 ? Surface.FRONT : face == 0 ? Surface.LEFT : Surface.RIGHT; }
    private static double facePlane(int face) { return face == 1 ? HEAD.maxZ : face == 0 ? LEFT.minX : RIGHT.maxX; }
    private static double eyeSpacing(int face) { return face == 1 ? 1.72 : 1.32; }

    private static void core(boolean glow, float age, boolean exposed, boolean hit, boolean dying, float alpha) {
        double bottom = CORE.minY, top = CORE.maxY, start = -CORE_PIXELS.length * CORE_PIXEL / 2;
        int red = hit ? 0xff3b37 : exposed ? 0xf92429 : 0xe52125;
        int highlight = (int)(51 + 8 * Math.sin(age * (exposed ? 0.32 : 0.13)));
        int light = 0xff0000 | highlight << 8 | highlight;
        for (int z = 0; z < CORE_PIXELS.length; z++) {
            for (int x = 0; x < CORE_PIXELS[z].length(); x++) {
                char pixel = CORE_PIXELS[z].charAt(x);
                if (pixel == ' ') continue;
                double x0 = start + x * CORE_PIXEL, x1 = start + (x + 1) * CORE_PIXEL;
                double z0 = start + z * CORE_PIXEL, z1 = start + (z + 1) * CORE_PIXEL;
                if (glow || dying) {
                    quad(Surface.TOP, x0, z0, x1, z1, top, dying ? 0x371417 : pixel == 'h' ? light : pixel == 'd' ? 0xb81b22 : red, alpha);
                }
                if (glow) continue;
                int edge = 0x531619;
                if (!corePixel(x, z + 1)) quad(Surface.FRONT, x0, bottom, x1, top, z1, edge, alpha);
                if (!corePixel(x, z - 1)) quad(Surface.BACK, x0, bottom, x1, top, z0, edge, alpha);
                if (!corePixel(x + 1, z)) quad(Surface.RIGHT, z0, bottom, z1, top, x1, edge, alpha);
                if (!corePixel(x - 1, z)) quad(Surface.LEFT, z0, bottom, z1, top, x0, edge, alpha);
            }
        }
    }

    private static boolean corePixel(int x, int z) {
        return z >= 0 && z < CORE_PIXELS.length && x >= 0 && x < CORE_PIXELS[z].length() && CORE_PIXELS[z].charAt(x) != ' ';
    }

    private static void tentacle(int index, float age, boolean agitated, boolean resting, float alpha) {
        double swing = Math.sin(age * (resting ? 0.06 : 0.115) + index * 1.48);
        double twist = Math.cos(age * 0.092 + index * 2.43);
        double length = TENTACLE_LENGTHS[index], r = TENTACLE_RADII[index];
        GL11.glPushMatrix();
        GL11.glTranslated((index % 3 - 1) * 3.38, HEAD.minY + 0.33, (index / 3 - 1) * 3.70);
        GL11.glRotated(swing * (resting ? 2 : agitated ? 7 : 4.5), 1, 0, 0);
        GL11.glRotated(twist * (resting ? 1.5 : 3.5), 0, 0, 1);
        TESS.startDrawingQuads();
        quad(Surface.FRONT, -r, -length, r, 0, r, CHARCOAL, alpha);
        quad(Surface.BACK, -r, -length, r, 0, -r, CHARCOAL, alpha);
        quad(Surface.LEFT, -r, -length, r, 0, -r, 0x1e1e1e, alpha);
        quad(Surface.RIGHT, -r, -length, r, 0, r, 0x1e1e1e, alpha);
        quad(Surface.BOTTOM, -r, -r, r, r, -length, PANEL, alpha);
        quad(Surface.FRONT, -r * 0.65, -length * 0.86, -r * 0.22, -length * 0.42, r + MARK_DEPTH, PANEL, alpha);
        quad(Surface.BACK, r * 0.20, -length * 0.82, r * 0.68, -length * 0.36, -r - MARK_DEPTH, PANEL, alpha);
        quad(Surface.RIGHT, -r * 0.58, -length * 0.72, r * 0.40, -length * 0.61, r + MARK_DEPTH, PANEL, alpha);
        quad(Surface.LEFT, -r * 0.40, -length * 0.76, r * 0.58, -length * 0.65, -r - MARK_DEPTH, PANEL, alpha);
        TESS.draw();
        GL11.glPopMatrix();
    }

    private static void wall(Surface surface, double u0, double v0, double u1, double v1, double plane, int color, float alpha) {
        quad(surface, u0, v0, u1, v1, plane, color, alpha);
        marks(surface, u0, v0, u1, v1, plane, CASING, alpha);
    }

    private static void marks(Surface surface, double u0, double v0, double u1, double v1, double plane, int[][] marks, float alpha) {
        double unitU = (u1 - u0) / 16, unitV = (v1 - v0) / 16;
        for (int[] mark : marks) {
            quad(surface, u0 + mark[0] * unitU, v0 + mark[1] * unitV, u0 + (mark[0] + mark[2]) * unitU,
                v0 + (mark[1] + mark[3]) * unitV, plane + surface.sign * MARK_DEPTH, mark[4], alpha);
        }
    }

    private static void quad(Surface surface, double u0, double v0, double u1, double v1, double plane, int color, float alpha) {
        TESS.setColorRGBA_I(color, (int)(alpha * 255));
        surface.vertex(u0, v0, plane);
        surface.vertex(surface.reverse ? u0 : u1, surface.reverse ? v1 : v0, plane);
        surface.vertex(u1, v1, plane);
        surface.vertex(surface.reverse ? u1 : u0, surface.reverse ? v0 : v1, plane);
    }

    private static void inset(Surface surface, double u0, double v0, double u1, double v1, double outer, double inner,
                              int color, float alpha) {
        TESS.setColorRGBA_I(color, (int)(alpha * 255));
        double a = surface.reverse ? inner : outer, b = surface.reverse ? outer : inner;
        surface.vertex(u0, v0, a); surface.vertex(u1, v0, a); surface.vertex(u1, v0, b); surface.vertex(u0, v0, b);
        surface.vertex(u1, v1, a); surface.vertex(u0, v1, a); surface.vertex(u0, v1, b); surface.vertex(u1, v1, b);
        surface.vertex(u0, v1, a); surface.vertex(u0, v0, a); surface.vertex(u0, v0, b); surface.vertex(u0, v1, b);
        surface.vertex(u1, v0, a); surface.vertex(u1, v1, a); surface.vertex(u1, v1, b); surface.vertex(u1, v0, b);
    }

    private enum Surface {
        FRONT(1, false), BACK(-1, true), LEFT(-1, false), RIGHT(1, true), TOP(1, true), BOTTOM(-1, false);

        final int sign;
        final boolean reverse;

        Surface(int sign, boolean reverse) {
            this.sign = sign;
            this.reverse = reverse;
        }

        void vertex(double u, double v, double plane) {
            switch (this) {
                case LEFT:
                case RIGHT: TESS.addVertex(plane, v, u); break;
                case TOP:
                case BOTTOM: TESS.addVertex(u, plane, v); break;
                default: TESS.addVertex(u, v, plane); break;
            }
        }
    }
}
