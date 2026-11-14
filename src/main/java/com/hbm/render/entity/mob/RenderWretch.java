package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityWretch;
import com.hbm.render.model.ModelWretch;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class RenderWretch extends Render {
    private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/blocks/fleshv2.png");
    private final ModelWretch model = new ModelWretch();
    private final Frustrum frustum = new Frustrum();
    public RenderWretch() { shadowSize = 2.6F; }
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityWretch boss = (EntityWretch) entity;
        if(boss.isInvisible()) return;
        frustum.setPosition(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
        if(!frustum.isBoundingBoxInFrustum(boss.visualBounds())) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            float facing = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
            GL11.glRotatef(-facing, 0, 1, 0);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL); GL11.glDisable(GL11.GL_CULL_FACE); bindTexture(TEXTURE);
            if(boss.hurtTime > 0) GL11.glColor4f(1, 0.4F, 0.4F, 1);
            model.renderBody(boss, partial);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
