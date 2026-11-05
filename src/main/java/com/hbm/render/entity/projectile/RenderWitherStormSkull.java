package com.hbm.render.entity.projectile;

import com.hbm.render.entity.mob.RenderWitherStorm;
import com.hbm.render.model.ModelWitherStormHead;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStormSkull extends Render {
    private final ModelWitherStormHead model = new ModelWitherStormHead();
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return RenderWitherStorm.SKIN; }
    @Override public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        float flightYaw = e.prevRotationYaw + MathHelper.wrapAngleTo180_float(e.rotationYaw - e.prevRotationYaw) * partial;
        float flightPitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * partial;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        // The throwable derives its angles from motion with +Z forward. The model faces -Z.
        GL11.glRotatef(flightYaw + 180F, 0, 1, 0);
        GL11.glRotatef(flightPitch, 1, 0, 0);
        GL11.glScalef(0.4F, 0.4F, 0.4F);
        GL11.glTranslatef(0, -0.6F, 0);
        bindTexture(RenderWitherStorm.SKIN);
        model.renderHead(e.ticksExisted + partial, false);
        GL11.glPopMatrix();
    }
}
