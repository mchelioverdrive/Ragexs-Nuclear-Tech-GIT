package com.hbm.render.entity.projectile;

import com.hbm.entity.mob.giantghast.EntityGiantGhastFireball;

import net.minecraft.client.renderer.entity.RenderFireball;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public final class RenderGiantGhastFireball extends RenderFireball {
    public RenderGiantGhastFireball() { super(1); }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            float scale = ((EntityGiantGhastFireball)entity).visualSize();
            GL11.glScalef(scale, scale, scale);
            super.doRender(entity, 0, 0, 0, yaw, partial);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
