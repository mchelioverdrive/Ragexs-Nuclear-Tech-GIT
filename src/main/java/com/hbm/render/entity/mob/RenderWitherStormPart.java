package com.hbm.render.entity.mob;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.EntityWitherStormPart;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import com.hbm.render.model.ModelWitherStormHead;
import com.hbm.render.model.ModelWitherStormTentacle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStormPart extends Render {
    private final ModelWitherStormHead head = new ModelWitherStormHead();
    private final ModelWitherStormTentacle tentacle = new ModelWitherStormTentacle();
    @Override protected ResourceLocation getEntityTexture(Entity e) { return RenderWitherStorm.SKIN; }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityWitherStormPart part = (EntityWitherStormPart)entity;
        EntityWitherStorm boss = part.parent();
        Minecraft mc = Minecraft.getMinecraft();
        if(boss == null || mc.renderViewEntity == null || mc.renderViewEntity.getDistanceSqToEntity(boss) > 256D * 256D) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z); bindTexture(RenderWitherStorm.SKIN);
        if(part.slot() < 3) {
            float s = part.scale();
            GL11.glPushMatrix(); GL11.glTranslatef(0, s * 0.6F, 0);
            // Same -Z-forward, +Y-up transform as followParent() and mouth().
            GL11.glRotatef(-part.headYaw(partial), 0, 1, 0);
            GL11.glRotatef(part.headPitch(partial), 1, 0, 0);
            GL11.glScalef(s * 1.5F, s * 1.5F, s * 1.5F);
            boolean beam = boss.beamActive(part.slot());
            head.renderHead(part.ticksExisted + partial, beam);
            eyes(); GL11.glPopMatrix();
            Entity target = boss.worldObj.getEntityByID(boss.headTarget(part.slot()));
            if(beam && WitherStormMath.targetable(target, boss.worldObj) && mc.renderViewEntity.getDistanceSqToEntity(part) < 128D * 128D) {
                Vec3 origin = part.mouth(partial), end = WitherStormMath.targetPoint(target, partial);
                Vec3 forward = WitherStormMath.headOffset(0, 0, -1, part.headYaw(partial), part.headPitch(partial));
                double dx = end.xCoord - origin.xCoord, dy = end.yCoord - origin.yCoord, dz = end.zCoord - origin.zCoord;
                // A delayed target/rotation packet must never draw back through the skull.
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if(length > 0.1D && (dx * forward.xCoord + dy * forward.yCoord + dz * forward.zCoord) / length > 0.95D) {
                    GL11.glPushMatrix();
                    GL11.glTranslated(origin.xCoord - (part.lastTickPosX + (part.posX - part.lastTickPosX) * partial),
                        origin.yCoord - (part.lastTickPosY + (part.posY - part.lastTickPosY) * partial),
                        origin.zCoord - (part.lastTickPosZ + (part.posZ - part.lastTickPosZ) * partial));
                    beam(dx, dy, dz, s); GL11.glPopMatrix();
                }
            }
        } else {
            Vec3 root = boss.tentacleRoot(part.slot());
            double dx = root.xCoord - part.posX, dy = root.yCoord - part.posY, dz = root.zCoord - part.posZ;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            GL11.glRotatef((float)(Math.atan2(dx, dz) * 180D / Math.PI), 0, 1, 0);
            GL11.glRotatef((float)(Math.atan2(Math.sqrt(dx * dx + dz * dz), dy) * 180D / Math.PI), 1, 0, 0);
            int segments = mc.renderViewEntity.getDistanceSqToEntity(part) < 96D * 96D ? 7 : 3;
            for(int i = 0; i < segments; i++) {
                GL11.glPushMatrix(); GL11.glTranslated(0, length * i / segments, 0);
                float taper = (0.45F + i * 0.1F) * part.scale() * (part.slot() >= 8 ? 2F : 1F);
                GL11.glRotatef((float)Math.sin((part.worldObj.getTotalWorldTime() + partial) * 0.07D + i + part.slot()) * 7F, 0, 0, 1);
                GL11.glScalef(taper, (float)(length / segments) * 1.05F, taper); tentacle.renderSegment(); GL11.glPopMatrix();
            }
            if(part.slot() >= 8) {
                GL11.glPushMatrix(); GL11.glScalef(part.scale(), part.scale(), part.scale()); head.renderHead(part.ticksExisted + partial, true); GL11.glPopMatrix();
            }
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }
    private void eyes() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance; t.startDrawingQuads(); t.setColorRGBA_F(0.7F, 0.12F, 0.95F, 0.95F);
        // Shell front is z=-12/16, below the brow (y=17/16).
        // Front-facing winding and depth testing hide the two eyes from behind.
        for(int side = -1; side <= 1; side += 2) {
            double left = side < 0 ? -0.65D : 0.22D, right = side < 0 ? -0.22D : 0.65D;
            t.addVertex(left, 0.65D, -0.755D); t.addVertex(left, 0.9D, -0.755D);
            t.addVertex(right, 0.9D, -0.755D); t.addVertex(right, 0.65D, -0.755D);
        }
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
    private void beam(double dx, double dy, double dz, float scale) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(!Double.isFinite(length) || length < 0.1D || length > 48D) return;
        // Rings are perpendicular to the finite mouth-to-target segment. No Euler
        // rotations or triangle-strip carryover; vertical beams use the same basis.
        double nx = dx / length, ny = dy / length, nz = dz / length;
        double horizontal = Math.sqrt(nx * nx + nz * nz);
        double ux = horizontal < 0.001D ? 1D : nz / horizontal, uz = horizontal < 0.001D ? 0D : -nx / horizontal;
        double vx = ny * uz, vy = nz * ux - nx * uz, vz = -ny * ux;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance; t.startDrawingQuads();
        for(int ring = 0; ring < 6; ring++) for(int side = 0; side < 12; side++) for(int corner = 0; corner < 4; corner++) {
            double f = (ring + (corner >= 2 ? 1D : 0D)) / 6D;
            double a = (side + (corner == 1 || corner == 2 ? 1D : 0D)) * Math.PI * 2D / 12D;
            double radius = Math.min(scale * 0.55D, length * 0.15D) * (1D - f);
            double c = Math.cos(a) * radius, s = Math.sin(a) * radius;
            t.setColorRGBA_F(0.6F, 0.1F, 0.95F, (float)(0.24D * (1D - f)));
            // 0 <= f <= 1; at f=1 radius is zero and the vertex is exactly the target.
            t.addVertex(dx * f + ux * c + vx * s, dy * f + vy * s, dz * f + uz * c + vz * s);
        }
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
}
