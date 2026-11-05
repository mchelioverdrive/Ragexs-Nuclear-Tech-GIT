package com.hbm.render.entity.mob;

import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import com.hbm.entity.mob.magmagolem.MagmaGolemPose.Action;
import com.hbm.render.model.ModelGiantMagmaGolem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class RenderGiantMagmaGolem extends Render {
    private final ModelGiantMagmaGolem model = new ModelGiantMagmaGolem();
    private int displayedBossId;

    public RenderGiantMagmaGolem() { shadowSize = 3.3F; }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return ModelGiantMagmaGolem.MOLTEN; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGiantMagmaGolem golem = (EntityGiantMagmaGolem)entity;
        if (golem.isEntityAlive()) {
            BossStatus.setBossStatus(golem, true);
            displayedBossId = golem.getEntityId();
        } else if (golem.getEntityId() == displayedBossId && golem.getCommandSenderName().equals(BossStatus.bossName)) {
            BossStatus.statusBarTime = 0;
        }
        float facing = golem.prevRotationYaw + MathHelper.wrapAngleTo180_float(golem.rotationYaw - golem.prevRotationYaw) * partial;
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-facing, 0, 1, 0);
            GL11.glColor4f(1, 1, 1, 1);
            model.render(golem, textures, partial, false);
            if (golem.action() != Action.DYING || golem.animationAge() < 20) {
                textures.bindTexture(ModelGiantMagmaGolem.GLOW);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                GL11.glDisable(GL11.GL_LIGHTING);
                // Preserve the mask's low-alpha embers during additive blending.
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(false);
                model.render(golem, textures, partial, true);
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }
}
