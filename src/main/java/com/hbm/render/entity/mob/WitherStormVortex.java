package com.hbm.render.entity.mob;

import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormThunderstormPose;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;

/** Stateless, entity-centered Stage D debris. One opaque cube batch, no simulated entities or frame collections. */
@SideOnly(Side.CLIENT)
public final class WitherStormVortex {
    private static final double TAU = Math.PI * 2D;
    private static final double[] RADII = {43D, 64D, 88D, 112D, 138D};
    private static final double[] HEIGHTS = {22D, 31D, 40D, 30D, 43D};
    private static final int[][] FACES = {{5,1,3,7}, {0,4,6,2}, {6,7,3,2}, {0,1,5,4}, {4,5,7,6}, {1,0,2,3}};
    private static final float[] SHADE = {0.83F, 0.67F, 1F, 0.48F, 0.75F, 0.91F};
    private final Frustrum frustum = new Frustrum();
    private final double[] point = new double[3], corners = new double[24];

    private static double random(int i, int salt) {
        int h = (i * 73428767 ^ salt * 912931);
        h = (h ^ h >>> 13) * 1274126177;
        return (h & 0x7fffffff) / 2147483648D;
    }

    /** Shared orbit for decorative cubes and real absorption records. Never uses body yaw. */
    public static void orbit(int ring, double angle, double radialOffset, double time, double[] out) {
        double r = RADII[ring] + radialOffset + Math.sin(angle * 3D + ring + time * 0.008D) * 2.5D;
        out[0] = -3D + Math.cos(angle) * r;
        out[1] = HEIGHTS[ring] + Math.sin(angle + ring * 1.7D) * r * 0.10D
            + Math.sin(angle * 2D - time * 0.017D + ring) * 2D;
        out[2] = 10D + Math.sin(angle) * r * (0.88D + ring * 0.02D);
    }

    public static double speed(int ring) { return 0.012D / (1D + ring * 0.42D); }

    public void render(EntityWitherStorm boss, double x, double y, double z, float partial, double distance) {
        if(!boss.isEntityAlive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int maximum = Math.max(0, Math.min(8192, WitherStormConfig.maxVortexFragments));
        double quality = mc.gameSettings.particleSetting == 0 ? 1D : mc.gameSettings.particleSetting == 1 ? 0.6D : 0.28D;
        if(!mc.gameSettings.fancyGraphics) quality *= 0.75D;
        double lod = Math.max(0.16D, Math.min(1D, 180D / Math.max(180D, Math.sqrt(distance))));
        int count = (int)(maximum * quality * lod);
        if(count == 0) return;
        double time = boss.worldObj.getTotalWorldTime() + (double)partial;
        double scale = boss.renderScale(partial) * WitherStormThunderstormPose.size(boss.development());
        double wx = x + RenderManager.renderPosX, wy = y + RenderManager.renderPosY, wz = z + RenderManager.renderPosZ;
        ClippingHelperImpl.getInstance();
        frustum.setPosition(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(true);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for(int i = 0; i < count; i++) {
            // Interleave all five rings at every detail level; thin by index, never by ring.
            int ring = i % RADII.length;
            double phase = random(i, 1) * TAU;
            double angle = phase + time * speed(ring) * (0.90D + random(i, 2) * 0.20D)
                + Math.sin(phase * 2D) * 0.22D;
            double radial = (random(i, 3) - 0.5D) * (7D + ring * 2D);
            double extent = (0.17D + Math.pow(random(i, 4), 2D) * 0.9D) * scale;
            orbit(ring, angle, radial, time, point);
            point[1] += (random(i, 5) - 0.5D) * 5D + Math.sin(time * 0.024D + phase) * random(i, 6) * 3D;
            if(i % 19 == 0) {
                // A few streams continuously peel inward; fade their ends before recycling.
                double cycle = time * 0.0014D + random(i, 7);
                double progress = cycle - Math.floor(cycle);
                double weight = progress * progress;
                point[0] = point[0] * (1D - weight) - 3D * weight;
                point[1] += (33D - point[1]) * weight;
                point[2] = point[2] * (1D - weight) + 10D * weight;
                extent *= Math.min(1D, Math.min(progress * 12D, (1D - progress) * 12D));
            }
            boolean mote = i % 61 == 0;
            if(mote) {
                point[0] = -3D + Math.cos(angle) * (18D + random(i, 8) * 6D);
                point[1] = 13D + random(i, 9) * 37D + Math.sin(time * 0.03D + phase);
                point[2] = 8D + Math.sin(angle) * 21D;
                extent = 0.09D * scale;
            }
            double px = point[0] * scale, py = point[1] * scale, pz = point[2] * scale;
            if(!frustum.isBoxInFrustum(wx + px - extent * 2D, wy + py - extent * 2D, wz + pz - extent * 2D,
                wx + px + extent * 2D, wy + py + extent * 2D, wz + pz + extent * 2D)) continue;
            float highlight = mote ? 1F : i % 29 == 0 ? 0.48F : (float)(0.12D + random(i, 10) * 0.12D);
            cube(t, x + px, y + py, z + pz, extent, phase + time * (0.013D + random(i, 11) * 0.024D),
                phase * 0.7D + time * (0.007D + random(i, 12) * 0.016D), highlight, mote);
        }
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glPopAttrib();
    }

    private void cube(Tessellator t, double px, double py, double pz, double size, double a, double b, float color, boolean mote) {
        double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
        for(int v = 0; v < 8; v++) {
            double x = ((v & 1) - 0.5D) * size, y = (((v >> 1) & 1) - 0.5D) * size * 0.8D;
            double z = (((v >> 2) & 1) - 0.5D) * size;
            double yy = y * cb - z * sb, zz = y * sb + z * cb;
            corners[v * 3] = px + x * ca + zz * sa;
            corners[v * 3 + 1] = py + yy;
            corners[v * 3 + 2] = pz - x * sa + zz * ca;
        }
        for(int f = 0; f < 6; f++) {
            float c = color * SHADE[f];
            t.setColorOpaque_F(c * (mote ? 0.65F : 0.62F), c * (mote ? 0.10F : 0.44F), c);
            for(int v : FACES[f]) t.addVertex(corners[v * 3], corners[v * 3 + 1], corners[v * 3 + 2]);
        }
    }
}
