package com.hbm.render.entity.mob;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** The arrival is a floating aperture drawn from luminous geometry. */
public final class RenderStaplerArrival extends Render {
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTick) {
        double age = entity.ticksExisted + partialTick;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glTranslated(x, y + 2.6D + Math.sin(age * 0.045D) * 0.55D, z);
            GL11.glRotatef(-yaw, 0F, 1F, 0F);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0.015F, 0.006F, 0.012F, 0.98F);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0D, 0D, 0D);
            for(int i = 0; i <= 48; i++) {
                double angle = Math.PI * i / 24D;
                GL11.glVertex3d(Math.cos(angle) * 1.0D, Math.sin(angle) * 2.55D, 0D);
            }
            GL11.glEnd();
            GL11.glLineWidth(2F);
            GL11.glColor4f(0.88F, 0.36F, 0.22F, 0.94F);
            ring(1.06D, 2.58D, 0.035D);
            ring(1.21D + Math.sin(age * 0.1D) * 0.08D, 2.87D, 0.04D);
            GL11.glColor4f(1F, 0.89F, 0.63F, 0.98F);
            ring(0.52D, 0.27D, 0.075D);
            ring(0.14D, 0.25D, 0.08D);
            for(int side : new int[] {-1, 1}) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex3d(side * 0.38D, 1.1D, 0.06D);
                GL11.glVertex3d(side * 1.55D, 2.1D, 0.06D);
                GL11.glVertex3d(side * 1.78D, 0.75D, 0.06D);
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void ring(double width, double height, double depth) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i = 0; i < 48; i++) {
            double angle = Math.PI * i / 24D;
            GL11.glVertex3d(Math.cos(angle) * width, Math.sin(angle) * height, depth);
        }
        GL11.glEnd();
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return null; }
}
