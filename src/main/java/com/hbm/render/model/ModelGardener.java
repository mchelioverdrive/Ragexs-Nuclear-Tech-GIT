package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityGardener;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/** Ring-built organic carapace and a circular, full-bright eye; no cuboid torso or eye. */
public class ModelGardener extends ModelBase {
    private static final double[][] BODY = {{0.8, 0.7}, {2.6, 2.4}, {3.8, 3.0}, {4.7, 2.8}, {5.5, 1.9}, {6.6, 1.25}, {7.6, 0.15}};
    protected double[][] rings() { return BODY; }
    protected double depth() { return 0.72D; }
    protected double eyeHeight() { return 6.8D; }
    protected double eyeRadius() { return 0.95D; }
    public void renderBody(EntityGardener boss, float partial) {
        GL11.glPushMatrix();
        GL11.glTranslated(0, boss.bodyShift(partial), 0);
        double[][] rings = rings();
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for(int row = 0; row < rings.length - 1; row++) for(int side = 0; side < 16; side++) {
            double angle = side * Math.PI / 8D, next = (side + 1) * Math.PI / 8D;
            float tone = 0.72F + (float) Math.sin(side * 2.8D + row * 3.1D) * 0.12F;
            tess.setColorOpaque_F(0.29F * tone, 0.22F * tone, 0.19F * tone);
            bodyVertex(tess, rings, row, angle, side / 4D);
            bodyVertex(tess, rings, row + 1, angle, side / 4D);
            bodyVertex(tess, rings, row + 1, next, (side + 1) / 4D);
            bodyVertex(tess, rings, row, next, (side + 1) / 4D);
        }
        tess.draw();
        renderEye(boss, partial);
        GL11.glPopMatrix();
    }
    private void bodyVertex(Tessellator tess, double[][] rings, int row, double angle, double u) {
        double fold = 1D + Math.sin(angle * 5D + row * 1.4D) * 0.085D;
        tess.setNormal((float) Math.cos(angle), 0.2F, (float) Math.sin(angle));
        tess.addVertexWithUV(Math.cos(angle) * rings[row][1] * fold,
                rings[row][0], Math.sin(angle) * rings[row][1] * depth() * fold, u, row * 0.65D);
    }
    private void renderEye(EntityGardener boss, float partial) {
        Entity target = boss.eyeTarget();
        double age = boss.isAwake() ? boss.worldObj.getTotalWorldTime() + partial : 0;
        double yaw = Math.sin(age * 0.009D) * 0.6D, pitch = 0;
        if(target != null) {
            double dx = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial
                    - boss.lastTickPosX - (boss.posX - boss.lastTickPosX) * partial;
            double dz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial
                    - boss.lastTickPosZ - (boss.posZ - boss.lastTickPosZ) * partial;
            double dy = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial + target.height * 0.7D
                    - boss.lastTickPosY - (boss.posY - boss.lastTickPosY) * partial - eyeHeight();
            yaw = Math.atan2(-dx, -dz);
            pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        }
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(0, eyeHeight(), 0);
            GL11.glRotated(Math.toDegrees(yaw), 0, 1, 0);
            GL11.glRotated(Math.toDegrees(pitch), 1, 0, 0);
            GL11.glTranslated(0, 0, -eyeRadius() - 0.35D);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            disc(eyeRadius(), false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDepthMask(false);
            GL11.glTranslated(0, 0, -0.015D);
            disc(eyeRadius() * 2.2D, true);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }
    private static void disc(double radius, boolean halo) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(1F, 0.98F, 0.76F, halo ? 0.3F : 1F);
        GL11.glVertex3d(0, 0, halo ? 0 : -0.15D);
        GL11.glColor4f(1F, 0.78F, 0.28F, halo ? 0F : 1F);
        for(int side = 0; side <= 48; side++) {
            double angle = side * Math.PI / 24D;
            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
        }
        GL11.glEnd();
    }
}
