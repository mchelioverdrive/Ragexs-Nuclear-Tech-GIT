package com.hbm.render.entity.mob;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.EntityWitherStormPart;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import com.hbm.entity.mob.witherstorm.WitherStormStage;
import com.hbm.render.model.ModelWitherStormHead;
import com.hbm.render.model.ModelWitherStormTentacle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
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
        WitherStormStage stage = boss.getStage();
        if(stage == WitherStormStage.A) {
            if(part.slot() < 3) renderCommandHead(boss, part, partial);
            return;
        }
        if(part.slot() >= 3 && !stage.hasTentacles()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z); bindTexture(RenderWitherStorm.SKIN);
        GL11.glEnable(GL11.GL_NORMALIZE);
        if(part.slot() < 3) {
            float s = part.scale();
            boolean upgraded = boss.headTransformed(part.slot());
            float skull = skullScale(boss, part, partial);
            if(stage != WitherStormStage.A_TRANSFORMED || part.slot() == 1) neck(boss, part, partial, stage);
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
                bindTexture(RenderWitherStorm.WITHER);
                head.renderWitherSkull(false);
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
            int segments = mc.renderViewEntity.getDistanceSqToEntity(part) < 96D * 96D ? 7 : 3;
            renderTentacle(boss, part, partial, dx, dy, dz, segments, stage);
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }

    private void renderTentacle(EntityWitherStorm boss, EntityWitherStormPart part, float partial,
                                double dx, double dy, double dz, int segments, WitherStormStage stage) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(!Double.isFinite(length) || length < 0.001D) return;
        orientAlong(dx, dy, dz);

        // Bend between the tracked tip and the mantle attachment. Both endpoints stay fixed.
        double wave = (part.ticksExisted + partial) * 0.085D + part.slot() * 1.7D;
        double bendX = (0.45D + 0.55D * Math.sin(wave)) * 0.09D * length;
        double bendZ = Math.cos(wave) * 0.07D * length;
        boolean heavy = part.slot() >= 8 && stage != WitherStormStage.B;
        float scale = part.scale();
        double x = 0D, z = 0D, step = length / segments;
        for(int i = 0; i < segments; i++) {
            double arc = i + 1 == segments ? 0D : Math.sin(Math.PI * (i + 1) / segments);
            double nextX = bendX * arc, nextZ = bendZ * arc;
            double sx = nextX - x, sz = nextZ - z;
            GL11.glPushMatrix();
            GL11.glTranslated(x, step * i, z);
            orientAlong(sx, step, sz);
            float width = (0.42F + (i + 0.5F) / segments * (1.7F + boss.development() * 2F)) * scale;
            if(heavy) width *= 1.25F;
            GL11.glScalef(width, (float)Math.sqrt(sx * sx + step * step + sz * sz) * 1.20F, width);
            tentacle.renderSegment();
            GL11.glPopMatrix();
            x = nextX;
            z = nextZ;
        }
    }

    private static void orientAlong(double x, double y, double z) {
        GL11.glRotatef((float)Math.toDegrees(Math.atan2(x, z)), 0, 1, 0);
        GL11.glRotatef((float)Math.toDegrees(Math.atan2(Math.sqrt(x * x + z * z), y)), 1, 0, 0);
    }

    private void renderCommandHead(EntityWitherStorm boss, EntityWitherStormPart part, float partial) {
        float scale = boss.renderScale(partial);
        float bodyYaw = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
        Vec3 center = EntityWitherStorm.commandHeadOffset(part.slot(), bodyYaw, scale);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glPushMatrix();
        // Use the torso's interpolated transform so network updates cannot separate the skulls from their joints.
        GL11.glTranslated(
            boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial - RenderManager.renderPosX + center.xCoord,
            boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial - RenderManager.renderPosY + center.yCoord,
            boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial - RenderManager.renderPosZ + center.zCoord);
        GL11.glRotatef(-part.headYaw(partial), 0, 1, 0);
        GL11.glRotatef(part.headPitch(partial), 1, 0, 0);
        GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL11.GL_NORMALIZE);
        bindTexture(RenderWitherStorm.WITHER);
        head.renderWitherSkull(part.slot() == 1);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
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
    private void neck(EntityWitherStorm boss, EntityWitherStormPart part, float partial, WitherStormStage stage) {
        float development = boss.development();
        double bodyScale = boss.renderScale(partial);
        float bodyYaw = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
        // These anchors lie inside the evolved shoulders.
        boolean early = stage == WitherStormStage.A_TRANSFORMED;
        Vec3 root = early ? WitherStormMath.headOffset(0, 1.82D * bodyScale, 0.12D * bodyScale, bodyYaw, 0)
            : WitherStormMath.headOffset((part.slot() - 1) * (0.75D + development * 7D) * bodyScale,
                (2.05D + development * 3.5D) * bodyScale, (-0.3D - development * 7D) * bodyScale, bodyYaw, 0);
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
        orientAlong(dx, dy, dz);
        for(int i = 0; i < 3; i++) {
            GL11.glPushMatrix(); GL11.glTranslated(0, length * i / 3D, 0);
            float width = part.scale() * (early ? 0.42F : 0.52F + development * 2.65F) * (1F - 0.22F * i);
            GL11.glScalef(width, (float)(length / 3D + part.scale() * 0.16D), width);
            head.renderNeckSegment(); GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }
    private void teeth() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(0.73F, 0.68F, 0.75F, 1F);
        head.renderTeeth();
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
