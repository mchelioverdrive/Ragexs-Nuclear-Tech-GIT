package com.hbm.render.entity.mob;

import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.lib.RefStrings;
import com.hbm.render.model.ModelSquirrel;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import com.hbm.world.squirrelstapler.StaplerSquirrels;
import org.lwjgl.opengl.GL11;

public final class RenderSquirrel extends RenderLiving {
    private static final ResourceLocation FUR = new ResourceLocation(RefStrings.MODID, "textures/entity/squirrel.png");

    public RenderSquirrel() {
        super(new ModelSquirrel(), 0.26F);
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return FUR; }

    @Override protected void preRenderCallback(EntityLivingBase entity, float partialTick) {
        float scale = entity instanceof StaplerSquirrels.Large ? 1.6F : entity instanceof StaplerSquirrels.Bear ? 2.8F
                : entity instanceof StaplerSquirrels.Silhouette ? 1.8F : 1F;
        float height = entity instanceof StaplerSquirrels.Silhouette && ((StaplerSquirrels.Silhouette)entity).isShadow() ? 0.22F : scale;
        GL11.glScalef(scale, height, scale);
    }

    @Override protected void renderModel(EntityLivingBase entity, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        if(!(entity instanceof StaplerSquirrels.Apparition)) {
            super.renderModel(entity, limbSwing, limbAmount, age, yaw, pitch, scale);
            return;
        }
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            if(entity instanceof StaplerSquirrels.Ghost) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                GL11.glColor4f(0.58F, 0.86F, 1F, 0.45F);
            } else if(entity instanceof StaplerSquirrels.Bear) {
                GL11.glColor4f(0.66F, 0.34F, 0.28F, 1F);
            } else {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                GL11.glColor4f(0.07F, 0.05F, 0.1F, 0.69F);
            }
            super.renderModel(entity, limbSwing, limbAmount, age, yaw, pitch, scale);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override protected void rotateCorpse(EntityLivingBase entity, float age, float bodyYaw, float partialTick) {
        EntitySquirrel squirrel = (EntitySquirrel) entity;
        if(squirrel.isAttached()) bodyYaw = EntitySquirrel.faceYaw(squirrel.getAttachmentFace());
        else if(squirrel.getAction() == EntitySquirrel.CLIMBING || squirrel.getAction() == EntitySquirrel.LEAPING || squirrel.getCarrierId() >= 0)
            bodyYaw = squirrel.prevRotationYaw + MathHelper.wrapAngleTo180_float(squirrel.rotationYaw - squirrel.prevRotationYaw) * partialTick;
        super.rotateCorpse(entity, age, bodyYaw, partialTick);
    }
}
