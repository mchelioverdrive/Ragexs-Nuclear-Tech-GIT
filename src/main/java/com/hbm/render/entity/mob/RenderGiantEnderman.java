package com.hbm.render.entity.mob;

import java.nio.DoubleBuffer;

import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import com.hbm.entity.mob.giantenderman.EntityGiantEnderman;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose.Action;
import com.hbm.render.model.ModelGiantEnderman;
import com.hbm.render.model.ModelPrismarineColossus;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class RenderGiantEnderman extends Render {
    private static final ResourceLocation SKIN = new ResourceLocation("hbm", "textures/entity/giant_enderman.png");
    private final ModelGiantEnderman model = new ModelGiantEnderman();
    private final ModelPrismarineColossus head = new ModelPrismarineColossus();
    private final GiantEndermanPose pose = new GiantEndermanPose();
    private final DoubleBuffer transform = BufferUtils.createDoubleBuffer(16);

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return SKIN; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGiantEnderman boss = (EntityGiantEnderman)entity;
        if (boss.isEntityAlive()) BossStatus.setBossStatus(boss, true);
        boss.evaluate(pose, partial);
        Action action = boss.action();
        double time = boss.frame(partial);
        float alpha = action == Action.VANISH ? (float)(1 - GiantEndermanPose.ease(time / 14))
            : action == Action.ARRIVE ? (float)GiantEndermanPose.ease(time / 14)
            : action == Action.DEATH ? (float)(1 - GiantEndermanPose.ease((time - 57) / 25)) : 1;
        if (alpha < 0.02F) return;

        float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-interpolatedYaw(boss, partial), 0, 1, 0);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            bindTexture(SKIN);
            model.render(pose, false, alpha, false, Math.min(1, boss.hurtTime / 7F));
            if (action == Action.FINISH) renderTornHead(boss, time);
            bindTexture(SKIN);
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            model.render(pose, true, alpha, boss.chestExposed(), 0);
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void renderTornHead(EntityGiantEnderman boss, double time) {
        Entity entity = boss.worldObj.getEntityByID(boss.finishTargetId());
        if (!(entity instanceof EntityPrismarineColossus)) return;
        EntityPrismarineColossus colossus = (EntityPrismarineColossus)entity;
        if (!colossus.endermanHeadDetached()) return;
        GL11.glPushMatrix();
        transform.clear();
        transform.put(pose.bones[GiantEndermanPose.hand(boss.activeHand())].matrix);
        transform.flip();
        GL11.glMultMatrix(transform);
        GL11.glTranslated(0, -2.1 - 12 * GiantEndermanPose.ease((time - 69) / 11), 1.1);
        GL11.glRotated(-25 + time * 2.2, 1, 0, 1);
        GL11.glScalef(colossus.scale(), colossus.scale(), colossus.scale());
        bindTexture(RenderPrismarineColossus.SKIN);
        head.renderDetachedHead();
        GL11.glPopMatrix();
    }

    static float interpolatedYaw(EntityGiantEnderman boss, float partial) {
        return boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
    }
}
