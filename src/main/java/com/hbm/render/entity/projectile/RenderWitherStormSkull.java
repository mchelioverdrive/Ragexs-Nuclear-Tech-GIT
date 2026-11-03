package com.hbm.render.entity.projectile;

import com.hbm.render.entity.mob.RenderWitherStorm;
import com.hbm.render.model.ModelWitherStormHead;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStormSkull extends Render {
    private final ModelWitherStormHead model = new ModelWitherStormHead();
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return RenderWitherStorm.SKIN; }
    @Override public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        GL11.glPushMatrix(); GL11.glTranslated(x, y, z); GL11.glRotatef(e.ticksExisted * 12F + partial, 0, 1, 0); GL11.glScalef(0.4F, 0.4F, 0.4F);
        bindTexture(RenderWitherStorm.SKIN); model.renderHead(e.ticksExisted, false); GL11.glPopMatrix();
    }
}
