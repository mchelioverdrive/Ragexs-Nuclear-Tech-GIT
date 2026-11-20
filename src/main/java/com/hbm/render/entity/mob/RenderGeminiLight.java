package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityGeminiLight;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class RenderGeminiLight extends Render {
    private static final int SIDES = 12;
    private static final double[] RING_X = new double[SIDES + 1], RING_Y = new double[SIDES + 1];
    static {
        for(int i = 0; i <= SIDES; i++) {
            double angle = i * Math.PI * 2D / SIDES;
            RING_X[i] = Math.cos(angle);
            RING_Y[i] = Math.sin(angle);
        }
    }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGeminiLight light = (EntityGeminiLight)entity;
        float glow = light.glow(partial);
        if(glow < 0.015F) return;
        int variant = light.variant();
        boolean harvest = (variant & 8) != 0;
        double phase = (light.ticksExisted + partial) * (0.09D + (variant & 7) * 0.012D) + variant * 2.5D;
        float pulse = (float)(0.83D + 0.12D * Math.sin(phase) + 0.05D * Math.sin(phase * 4.31D));
        float intensity = Math.min(1F, glow * pulse * (harvest ? 1.25F : 1.15F));
        double size = (0.82D + (variant & 7) * 0.045D) * (0.87D + glow * 0.35D);
        float red = harvest ? 1F : 0.63F, green = harvest ? 0.51F : 0.84F, blue = harvest ? 0.27F : 1F;
        y += 0.35D;

        double cameraYaw = Math.toRadians(renderManager.playerViewY), pitch = Math.toRadians(renderManager.playerViewX);
        double rx = Math.cos(cameraYaw), rz = Math.sin(cameraYaw);
        double ux = -rz * Math.sin(pitch), uy = Math.cos(pitch), uz = rx * Math.sin(pitch);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);

            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_TRIANGLES);
            for(int i = 0; i < SIDES; i++) {
                t.setColorRGBA_F(1F, 1F, 0.94F, intensity);
                t.addVertex(x, y, z);
                t.setColorRGBA_F(1F, harvest ? 0.83F : 0.97F, harvest ? 0.65F : 1F, intensity * 0.86F);
                ring(t, i, 0.23D * size, x, y, z, rx, rz, ux, uy, uz);
                ring(t, i + 1, 0.23D * size, x, y, z, rx, rz, ux, uy, uz);
                t.setColorRGBA_F(red, green, blue, intensity * 0.72F);
                ring(t, i, 0.23D * size, x, y, z, rx, rz, ux, uy, uz);
                ring(t, i + 1, 0.23D * size, x, y, z, rx, rz, ux, uy, uz);
                t.setColorRGBA_F(red, green, blue, 0F);
                ring(t, i + 1, size, x, y, z, rx, rz, ux, uy, uz);
                t.setColorRGBA_F(red, green, blue, intensity * 0.72F);
                ring(t, i, 0.23D * size, x, y, z, rx, rz, ux, uy, uz);
                t.setColorRGBA_F(red, green, blue, 0F);
                ring(t, i + 1, size, x, y, z, rx, rz, ux, uy, uz);
                ring(t, i, size, x, y, z, rx, rz, ux, uy, uz);
            }
            if(light.mode() == EntityGeminiLight.HARVEST) {
                Entity victim = light.worldObj.getEntityByID(light.captiveId());
                if(victim != null && victim.isEntityAlive()) {
                    double tx = x + interpolate(victim.lastTickPosX, victim.posX, partial)
                            - interpolate(light.lastTickPosX, light.posX, partial);
                    double ty = y + interpolate(victim.lastTickPosY, victim.posY, partial)
                            - interpolate(light.lastTickPosY, light.posY, partial)
                            + victim.boundingBox.maxY - victim.posY - 0.55D;
                    double tz = z + interpolate(victim.lastTickPosZ, victim.posZ, partial)
                            - interpolate(light.lastTickPosZ, light.posZ, partial);
                    double width = harvest ? 0.26D : 0.18D;
                    t.setColorRGBA_F(red, green, blue, intensity * 0.52F);
                    t.addVertex(x - rx * width, y, z - rz * width);
                    t.addVertex(x + rx * width, y, z + rz * width);
                    t.setColorRGBA_F(red, green, blue, intensity * 0.24F);
                    t.addVertex(tx + rx * width, ty, tz + rz * width);
                    t.setColorRGBA_F(red, green, blue, intensity * 0.52F);
                    t.addVertex(x - rx * width, y, z - rz * width);
                    t.setColorRGBA_F(red, green, blue, intensity * 0.24F);
                    t.addVertex(tx + rx * width, ty, tz + rz * width);
                    t.addVertex(tx - rx * width, ty, tz - rz * width);
                }
            }
            t.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void ring(Tessellator t, int point, double radius, double x, double y, double z,
            double rx, double rz, double ux, double uy, double uz) {
        double horizontal = RING_X[point] * radius, vertical = RING_Y[point] * radius * 0.78D;
        t.addVertex(x + rx * horizontal + ux * vertical, y + uy * vertical, z + rz * horizontal + uz * vertical);
    }

    private static double interpolate(double previous, double current, float partial) {
        return previous + (current - previous) * partial;
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return null; }
}
