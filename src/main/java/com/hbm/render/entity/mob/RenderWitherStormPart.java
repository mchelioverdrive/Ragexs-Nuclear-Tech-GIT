package com.hbm.render.entity.mob;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.EntityWitherStormPart;
import com.hbm.render.model.ModelWitherStormHead;
import com.hbm.render.model.ModelWitherStormTentacle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
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
            GL11.glRotatef(180 - (part.prevRotationYaw + (part.rotationYaw - part.prevRotationYaw) * partial), 0, 1, 0);
            GL11.glRotatef(part.prevRotationPitch + (part.rotationPitch - part.prevRotationPitch) * partial, 1, 0, 0);
            GL11.glScalef(s * 1.5F, s * 1.5F, s * 1.5F);
            boolean beam = boss.headTarget(part.slot()) != 0;
            head.renderHead(part.ticksExisted + partial, beam);
            // Original untextured purple eye and mouth glow.
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            Tessellator t = Tessellator.instance; t.startDrawingQuads(); t.setColorRGBA_F(0.75F, 0.12F, 1F, 1F);
            t.addVertex(-0.7D, 0.7D, -0.951D); t.addVertex(0.7D, 0.7D, -0.951D); t.addVertex(0.7D, 0.9D, -0.951D); t.addVertex(-0.7D, 0.9D, -0.951D); t.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_LIGHTING); GL11.glPopMatrix();
            Entity target = boss.worldObj.getEntityByID(boss.headTarget(part.slot()));
            if(target != null && !target.isDead && mc.renderViewEntity.getDistanceSqToEntity(part) < 128D * 128D) beam(target.posX - part.posX, target.posY + target.height * 0.5D - part.posY, target.posZ - part.posZ, s);
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
    private void beam(double dx, double dy, double dz, float scale) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz); if(length < 0.1D || length > 80D) return;
        GL11.glPushMatrix(); GL11.glTranslatef(0, scale, 0);
        GL11.glRotatef((float)(Math.atan2(dx, dz) * 180D / Math.PI), 0, 1, 0);
        GL11.glRotatef((float)(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180D / Math.PI), 1, 0, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance; t.startDrawing(GL11.GL_TRIANGLE_STRIP);
        for(int i = 0; i <= 8; i++) { double a = i * Math.PI / 4, c = Math.cos(a), s = Math.sin(a);
            t.setColorRGBA_F(0.6F, 0.1F, 0.95F, 0.3F); t.addVertex(c * scale * 0.4, s * scale * 0.4, 0);
            t.setColorRGBA_F(0.35F, 0.05F, 0.7F, 0.08F); t.addVertex(c * scale * 2, s * scale * 2, length);
        }
        t.draw(); GL11.glDepthMask(true); GL11.glDisable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_LIGHTING); GL11.glPopMatrix();
    }
}
