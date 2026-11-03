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
import net.minecraft.util.MathHelper;
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
        GL11.glEnable(GL11.GL_NORMALIZE);
        if(part.slot() < 3) {
            float s = part.scale();
            boolean upgraded = boss.headTransformed(part.slot());
            float skull = skullScale(boss, part, partial);
            neck(boss, part, partial);
            GL11.glPushMatrix(); GL11.glTranslatef(0, s * 0.6F, 0);
            // Same -Z-forward, +Y-up transform as followParent() and mouth().
            GL11.glRotatef(-part.headYaw(partial), 0, 1, 0);
            GL11.glRotatef(part.headPitch(partial), 1, 0, 0);
            GL11.glScalef(skull, skull, skull);
            boolean beam = boss.beamActive(part.slot());
            if(upgraded) {
                bindTexture(RenderWitherStorm.SKIN);
                head.renderHead(part.ticksExisted + partial, beam);
                teeth(); eye();
            } else {
                bindTexture(RenderWitherStorm.WITHER); head.renderOrdinary();
            }
            GL11.glPopMatrix();
            Entity target = boss.worldObj.getEntityByID(boss.headTarget(part.slot()));
            if(beam && WitherStormMath.targetable(target, boss.worldObj) && mc.renderViewEntity.getDistanceSqToEntity(part) < 128D * 128D) {
                // Match T(pivot) R_y(-yaw) R_x(pitch) S used by the skull and eye.
                Vec3 origin = headPoint(part, partial, ModelWitherStormHead.EYE_Y, ModelWitherStormHead.EYE_Z);
                Vec3 end = WitherStormMath.targetPoint(target, partial);
                Vec3 forward = WitherStormMath.headOffset(0, 0, -1, part.headYaw(partial), part.headPitch(partial));
                double dx = end.xCoord - (part.lastTickPosX + (part.posX - part.lastTickPosX) * partial) - origin.xCoord;
                double dy = end.yCoord - (part.lastTickPosY + (part.posY - part.lastTickPosY) * partial) - origin.yCoord;
                double dz = end.zCoord - (part.lastTickPosZ + (part.posZ - part.lastTickPosZ) * partial) - origin.zCoord;
                // A delayed target/rotation packet must never draw back through the skull.
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if(length > 0.1D && (dx * forward.xCoord + dy * forward.yCoord + dz * forward.zCoord) / length > 0.95D) {
                    GL11.glPushMatrix();
                    GL11.glTranslated(origin.xCoord, origin.yCoord, origin.zCoord);
                    beam(dx, dy, dz, skull, boss.development(), part.ticksExisted + partial); GL11.glPopMatrix();
                }
            }
        } else {
            Vec3 root = boss.tentacleRoot(part.slot());
            double dx = root.xCoord + boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial - boss.posX
                - (part.lastTickPosX + (part.posX - part.lastTickPosX) * partial);
            double dy = root.yCoord + boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial - boss.posY
                - (part.lastTickPosY + (part.posY - part.lastTickPosY) * partial);
            double dz = root.zCoord + boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial - boss.posZ
                - (part.lastTickPosZ + (part.posZ - part.lastTickPosZ) * partial);
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            GL11.glRotatef((float)(Math.atan2(dx, dz) * 180D / Math.PI), 0, 1, 0);
            GL11.glRotatef((float)(Math.atan2(Math.sqrt(dx * dx + dz * dz), dy) * 180D / Math.PI), 1, 0, 0);
            int segments = mc.renderViewEntity.getDistanceSqToEntity(part) < 96D * 96D ? 7 : 3;
            for(int i = 0; i < segments; i++) {
                GL11.glPushMatrix(); GL11.glTranslated(0, length * i / segments, 0);
                float taper = (0.35F + (float)i / segments * (1.2F + boss.development() * 2.4F)) * part.scale() * (part.slot() >= 8 ? 1.5F : 1F);
                // Lateral sway vanishes at the body; neighboring segments overlap generously.
                double sway = Math.sin((part.worldObj.getTotalWorldTime() + partial) * 0.055D + i * 0.6D + part.slot()) * part.scale() * 0.35D * (1D - (double)i / segments);
                GL11.glTranslated(sway, 0, 0);
                GL11.glScalef(taper, (float)(length / segments) * 1.12F, taper); tentacle.renderSegment(); GL11.glPopMatrix();
            }
            if(part.slot() >= 8) {
                GL11.glPushMatrix(); GL11.glScalef(part.scale(), part.scale(), part.scale()); head.renderHead(part.ticksExisted + partial, true); teeth(); GL11.glPopMatrix();
            }
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }
    /** Offset from the interpolated part position, in the same space as doRender. */
    private Vec3 headPoint(EntityWitherStormPart part, float partial, double y, double z) {
        double scale = skullScale(part.parent(), part, partial);
        Vec3 offset = WitherStormMath.headOffset(0, y * scale, z * scale, part.headYaw(partial), part.headPitch(partial));
        return Vec3.createVectorHelper(offset.xCoord, part.scale() * 0.6F + offset.yCoord, offset.zCoord);
    }
    private float skullScale(EntityWitherStorm boss, EntityWitherStormPart part, float partial) {
        if(boss == null) return part.scale();
        float size = boss.headSize(part.slot());
        if(boss.headTransformed(part.slot())) size *= ModelWitherStormHead.HEAD_SCALE * (0.45F + 0.55F * boss.headDevelopment(part.slot(), partial));
        return part.scale() * size;
    }
    private void neck(EntityWitherStorm boss, EntityWitherStormPart part, float partial) {
        float development = boss.development();
        double bodyScale = boss.renderScale(partial);
        float bodyYaw = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
        // These anchors lie inside the shoulders (or the command-phase spine).
        Vec3 root = WitherStormMath.headOffset((part.slot() - 1) * (0.45D + development * 5.8D) * bodyScale,
            (1.85D + development * 3D) * bodyScale, -development * 2.8D * bodyScale, bodyYaw, 0);
        double x = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial + root.xCoord
            - (part.lastTickPosX + (part.posX - part.lastTickPosX) * partial);
        double y = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial + root.yCoord
            - (part.lastTickPosY + (part.posY - part.lastTickPosY) * partial);
        double z = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial + root.zCoord
            - (part.lastTickPosZ + (part.posZ - part.lastTickPosZ) * partial);
        // End inside the shell, rather than at its rear face: yaw/pitch cannot open a seam.
        Vec3 joint = headPoint(part, partial, boss.headTransformed(part.slot()) ? ModelWitherStormHead.NECK_Y : 0, 0);
        double dx = joint.xCoord - x, dy = joint.yCoord - y, dz = joint.zCoord - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(!Double.isFinite(length) || length < 0.001D) return;
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z);
        GL11.glRotatef((float)(Math.atan2(dx, dz) * 180D / Math.PI), 0, 1, 0);
        GL11.glRotatef((float)(Math.atan2(Math.sqrt(dx * dx + dz * dz), dy) * 180D / Math.PI), 1, 0, 0);
        for(int i = 0; i < 5; i++) {
            GL11.glPushMatrix(); GL11.glTranslated(0, length * i / 5D, 0);
            float taper = 1F - i * (boss.headTransformed(part.slot()) ? 0.1F : 0.21F);
            float width = part.scale() * (0.22F + development * 4F * taper);
            GL11.glRotatef((i % 2 == 0 ? 1 : -1) * development * 12F, 0, 1, 0);
            GL11.glScalef(width, (float)(length / 5D + part.scale() * 0.18D), width);
            head.renderNeckSegment(); GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }
    private void teeth() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 210F, 210F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glColor4f(0.68F, 0.96F, 0.91F, 1F);
        head.renderTeeth();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
    private void eye() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance; t.startDrawingQuads(); t.setColorRGBA_F(0.78F, 0.16F, 1F, 1F);
        // Shell front is z=-15/16; the eye sits below the brow and above the upper jaw.
        // One centered square, with front-facing winding and no texture/emissive duplicate.
        double half = ModelWitherStormHead.EYE_HALF_SIZE, y = ModelWitherStormHead.EYE_Y, z = ModelWitherStormHead.EYE_Z;
        t.addVertex(-half, y - half, z); t.addVertex(-half, y + half, z);
        t.addVertex(half, y + half, z); t.addVertex(half, y - half, z);
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GL11.glPopAttrib();
    }
    private void beam(double dx, double dy, double dz, float scale, float development, float age) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(!Double.isFinite(length) || length < 0.1D || length > 48D) return;
        // Rings are perpendicular to the finite eye-to-target segment. No Euler
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
            double opening = Math.min(scale * ModelWitherStormHead.EYE_HALF_SIZE, length * 0.15D);
            double radius = opening * (1D - f) + Math.sin(f * Math.PI) * Math.min(length * 0.06D, scale * (0.5D + development));
            radius *= 1D + Math.sin(age * 0.12D - f * 8D) * 0.045D;
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
