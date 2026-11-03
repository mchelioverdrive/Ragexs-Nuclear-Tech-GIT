package com.hbm.render.entity.mob;

import com.hbm.entity.mob.sandworm.*;
import com.hbm.render.model.ModelSandworm;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Original articulated shells, organs, energy crystals and eight-legged Tick anatomy. */
@SideOnly(Side.CLIENT)
public final class WormRenderers {
    private WormRenderers() { }
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntitySandworm.class, new Exterior());
        RenderingRegistry.registerEntityRenderingHandler(EntityBrain.class, new Interior());
        RenderingRegistry.registerEntityRenderingHandler(EntityHeart.class, new Interior());
        RenderingRegistry.registerEntityRenderingHandler(EntitySpark.class, new Interior());
        RenderingRegistry.registerEntityRenderingHandler(EntityTick.class, new Interior());
        RenderingRegistry.registerEntityRenderingHandler(EntityWormProjectile.class, new Interior());
    }
    private static final ResourceLocation SHELL = new ResourceLocation("hbm:textures/entity/sandworm/shell.png");
    private static final ResourceLocation ORGAN = new ResourceLocation("hbm:textures/entity/sandworm/organ.png");
    private static final ModelSandworm.Mesh ROUND = new ModelSandworm.Mesh(new float[] {0.02F, 0.7F, 1, 0.7F, 0.02F}, new float[] {-1, -0.7F, 0, 0.7F, 1});
    private static final ModelSandworm.Mesh CRYSTAL = new ModelSandworm.Mesh(new float[] {0.01F, 0.6F, 0.01F}, new float[] {-1, 0, 1});
    private static void lobe(double x, double y, double z, double sx, double sy, double sz) {
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z); GL11.glScaled(sx, sy, sz); ROUND.draw(); GL11.glPopMatrix();
    }
    private static final class Exterior extends Render {
        private final ModelSandworm model = new ModelSandworm();
        @Override protected ResourceLocation getEntityTexture(Entity e) { return SHELL; }
        @Override public void doRender(Entity raw, double x, double y, double z, float yaw, float partial) {
            EntitySandworm worm = (EntitySandworm)raw;
            bindTexture(SHELL);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            GL11.glDisable(GL11.GL_CULL_FACE);
            double rootX = worm.previousPose[0][0] + (worm.pose[0][0] - worm.previousPose[0][0]) * partial;
            double rootY = worm.previousPose[0][1] + (worm.pose[0][1] - worm.previousPose[0][1]) * partial;
            double rootZ = worm.previousPose[0][2] + (worm.pose[0][2] - worm.previousPose[0][2]) * partial;
            for(int i = 0; i < EntitySandworm.SEGMENTS; i++) {
                double[] p = worm.pose[i], old = worm.previousPose[i];
                double px = old[0] + (p[0] - old[0]) * partial, py = old[1] + (p[1] - old[1]) * partial, pz = old[2] + (p[2] - old[2]) * partial;
                float ry = (float)(old[3] + MathHelper.wrapAngleTo180_double(p[3] - old[3]) * partial);
                float rp = (float)(old[4] + MathHelper.wrapAngleTo180_double(p[4] - old[4]) * partial);
                GL11.glPushMatrix(); GL11.glTranslated(x + px - rootX, y + py - rootY, z + pz - rootZ);
                GL11.glRotatef(-ry, 0, 1, 0); GL11.glRotatef(rp, 1, 0, 0);
                if(i == 0) model.head(worm.ticksExisted + partial);
                else if(i == EntitySandworm.SEGMENTS - 1) model.tail(); else model.body();
                GL11.glPopMatrix();
                if(i > 0) {
                    double[] a = worm.pose[i - 1], b = worm.previousPose[i - 1];
                    double ax = b[0] + (a[0] - b[0]) * partial, ay = b[1] + (a[1] - b[1]) * partial, az = b[2] + (a[2] - b[2]) * partial;
                    double dx = ax - px, dy = ay - py, dz = az - pz;
                    GL11.glPushMatrix(); GL11.glTranslated(x + (ax + px) / 2 - rootX, y + (ay + py) / 2 - rootY, z + (az + pz) / 2 - rootZ);
                    GL11.glRotatef((float)(90 - Math.atan2(dz, dx) * 180 / Math.PI), 0, 1, 0);
                    GL11.glRotatef((float)(-Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI), 1, 0, 0);
                    GL11.glScaled(0.82, 0.82, Math.sqrt(dx * dx + dy * dy + dz * dz) / 19); model.body(); GL11.glPopMatrix();
                }
            }
            GL11.glPopAttrib();
        }
    }
    private static final class Interior extends Render {
        private final ModelRenderer leg;
        Interior() { ModelBase base = new ModelBase() { }; base.textureWidth = base.textureHeight = 64; leg = new ModelRenderer(base, 0, 0); leg.addBox(0, -1, -1, 14, 2, 2); shadowSize = 0.8F; }
        @Override protected ResourceLocation getEntityTexture(Entity e) { return ORGAN; }
        @Override public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
            if(e instanceof net.minecraft.entity.boss.IBossDisplayData && renderManager.livingPlayer != null && e.getDistanceSqToEntity(renderManager.livingPlayer) < 24 * 24)
                net.minecraft.entity.boss.BossStatus.setBossStatus((net.minecraft.entity.boss.IBossDisplayData)e, true);
            bindTexture(ORGAN);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
            GL11.glPushMatrix(); GL11.glTranslated(x, y, z); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_NORMALIZE);
            float time = e.ticksExisted + partial;
            if(e instanceof EntityBrain) brain((EntityBrain)e, time);
            else if(e instanceof EntityHeart) heart((EntityHeart)e, partial);
            else if(e instanceof EntityTick) tick((EntityTick)e, time, yaw);
            else if(e instanceof EntitySpark) spark((EntitySpark)e, time);
            else projectile((EntityWormProjectile)e, time);
            GL11.glPopMatrix(); OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY); GL11.glPopAttrib();
        }
        private void brain(EntityBrain b, float time) {
            GL11.glColor3f(0.9F, 0.58F, 0.63F);
            lobe(0, 0.5, 0, 0.5, 0.7, 0.6);
            double pulse = 1 + Math.sin(time * 0.15) * 0.02 + b.attackAnimation() * 0.004;
            GL11.glPushMatrix(); GL11.glTranslated(0, 1.7, 0); GL11.glScaled(pulse, pulse, pulse);
            for(int side = -1; side <= 1; side += 2) for(int i = 0; i < 5; i++) {
                double zz = (i - 2) * 0.6;
                lobe(side * 0.75, 0.2 + Math.sin(i * 0.7) * 0.12, zz, 1, 1.1, 0.55);
                lobe(side * 1.1, -0.3, zz, 0.7, 0.65, 0.45);
            }
            GL11.glPopMatrix();
            if(b.shielded()) {
                glow(); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glEnable(GL11.GL_BLEND); GL11.glDepthMask(false);
                GL11.glColor4f(0.25F, 0.7F, 1, 0.22F); lobe(0, 1.5, 0, 2.8, 2, 2.8);
                GL11.glColor4f(0.45F, 0.85F, 1, 0.8F);
                for(int i = 0; i < 6; i++) { double a = time * 0.04 + i * Math.PI / 3; lobe(Math.cos(a) * 2.6, 1.7 + Math.sin(a * 2) * 0.7, Math.sin(a) * 2.6, 0.12, 0.12, 0.12); }
                GL11.glDepthMask(true); GL11.glDisable(GL11.GL_BLEND);
            }
        }
        private void heart(EntityHeart h, float partial) {
            double pump = Math.sin(h.pumpPhase() + partial * h.pumpRate());
            GL11.glTranslated(0, 1.8, 0); GL11.glScaled(1 + pump * 0.1, 1 - pump * 0.08, 1 + pump * 0.13);
            if(h.invulnerableTicks() > 0) GL11.glColor3f(1, 0.36F, 0.42F); else GL11.glColor3f(0.7F, 0.12F, 0.2F);
            lobe(-0.55, 0.35, 0, 1, 1.2, 1); lobe(0.55, 0.5, 0, 0.9, 1.1, 1);
            lobe(0, -0.6, 0, 1.1, 1.1, 0.9);
            GL11.glColor3f(0.45F, 0.1F, 0.21F);
            for(int i = 0; i < 3; i++) lobe(-0.65 + i * 0.65, 1.25, 0, 0.26, 0.9, 0.28);
            GL11.glColor3f(0.65F, 0.38F, 0.5F);
            for(int i = 0; i < 5; i++) lobe(0.3 - i * 0.12, 0.9 - i * 0.42, -0.82, 0.12, 0.3, 0.1);
        }
        private void tick(EntityTick t, float time, float yaw) {
            GL11.glRotatef(180 - yaw, 0, 1, 0); float scale = t.scale(); GL11.glScalef(scale, scale, scale);
            GL11.glColor3f(0.32F, 0.16F, 0.13F); lobe(0, 0.42, 0.15, 0.55, 0.4, 0.7);
            GL11.glColor3f(0.55F, 0.13F, 0.18F); lobe(0, 0.42, 0.55, 0.49, 0.38, 0.5);
            GL11.glColor3f(0.15F, 0.12F, 0.1F); lobe(0, 0.36, -0.55, 0.35, 0.25, 0.28);
            for(int side = -1; side <= 1; side += 2) for(int i = 0; i < 4; i++) {
                GL11.glPushMatrix(); GL11.glTranslated(side * 0.3, 0.35, (i - 1.5) * 0.26);
                GL11.glRotatef(side < 0 ? 180 : 0, 0, 1, 0); GL11.glRotatef((i - 1.5F) * 22 + (float)Math.sin(time * 0.5 + i * 2 + side) * 12 * Math.min(1, t.limbSwingAmount * 4), 0, 1, 0);
                GL11.glRotatef(-12, 0, 0, 1); leg.render(0.055F); GL11.glTranslatef(0.65F, 0, 0); GL11.glRotatef(-60, 0, 0, 1); leg.render(0.04F); GL11.glPopMatrix();
            }
            GL11.glColor3f(0.9F, 0.75F, 0.5F); lobe(-0.14, 0.25, -0.85, 0.07, 0.07, 0.22); lobe(0.14, 0.25, -0.85, 0.07, 0.07, 0.22);
        }
        private void spark(EntitySpark s, float time) {
            glow(); GL11.glTranslated(0, 0.8 + Math.sin(time * 0.12) * 0.15, 0); GL11.glRotatef(time * 4, 0, 1, 0);
            int color = s.variant() == EntitySpark.ORANGE ? 0xFF8628 : s.variant() == EntitySpark.BLUE ? 0x37C8FF : s.variant() == EntitySpark.GREEN ? 0x5BFF65 : 0xB04EFF;
            GL11.glColor3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
            GL11.glPushMatrix(); GL11.glScalef(0.6F, 0.6F, 0.6F); CRYSTAL.draw(); GL11.glPopMatrix();
            for(int i = 0; i < 6; i++) { GL11.glRotatef(60, 0, 1, 0); lobe(0.7, 0, 0, 0.08, 0.08, 0.3); }
        }
        private void projectile(EntityWormProjectile p, float time) {
            glow(); GL11.glRotatef(time * 12, 0, 1, 1); GL11.glScalef(p.blood() ? 0.4F : 0.65F, 0.4F, 0.4F);
            if(p.blood()) { GL11.glColor3f(0.8F, 0.04F, 0.1F); ROUND.draw(); }
            else { GL11.glColor3f(0.6F, 0.4F, 1); CRYSTAL.draw(); }
        }
        private void glow() { GL11.glDisable(GL11.GL_LIGHTING); OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240); }
    }
}
