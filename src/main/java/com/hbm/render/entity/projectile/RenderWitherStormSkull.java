package com.hbm.render.entity.projectile;

import com.hbm.render.entity.mob.RenderWitherStorm;
import net.minecraft.client.model.ModelSkeletonHead;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStormSkull extends Render {
    private final ModelSkeletonHead model = new ModelSkeletonHead(0, 0, 64, 64);
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return RenderWitherStorm.WITHER; }
    @Override public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        float flightYaw = e.prevRotationYaw + MathHelper.wrapAngleTo180_float(e.rotationYaw - e.prevRotationYaw) * partial;
        float flightPitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * partial;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        // The throwable derives its angles from motion with +Z forward. The model faces -Z.
        GL11.glRotatef(flightYaw + 180F, 0, 1, 0);
        GL11.glRotatef(flightPitch, 1, 0, 0);
        GL11.glScalef(-1F, -1F, 1F);
        GL11.glTranslatef(0, 0.25F, 0);
        bindTexture(RenderWitherStorm.WITHER);
        model.render(e, 0, 0, 0, 0, 0, 0.0625F);
        GL11.glPopMatrix();
    }
}
