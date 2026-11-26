package com.hbm.render.entity.mob;

import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.lib.RefStrings;
import com.hbm.render.model.ModelSquirrel;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

public final class RenderSquirrel extends RenderLiving {
    private static final ResourceLocation FUR = new ResourceLocation(RefStrings.MODID, "textures/entity/squirrel.png");

    public RenderSquirrel() {
        super(new ModelSquirrel(), 0.26F);
    }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return FUR; }

    @Override protected void rotateCorpse(EntityLivingBase entity, float age, float bodyYaw, float partialTick) {
        EntitySquirrel squirrel = (EntitySquirrel) entity;
        if(squirrel.isAttached()) bodyYaw = EntitySquirrel.faceYaw(squirrel.getAttachmentFace());
        else if(squirrel.getAction() == EntitySquirrel.CLIMBING || squirrel.getAction() == EntitySquirrel.LEAPING || squirrel.getCarrierId() >= 0)
            bodyYaw = squirrel.prevRotationYaw + MathHelper.wrapAngleTo180_float(squirrel.rotationYaw - squirrel.prevRotationYaw) * partialTick;
        super.rotateCorpse(entity, age, bodyYaw, partialTick);
    }
}
