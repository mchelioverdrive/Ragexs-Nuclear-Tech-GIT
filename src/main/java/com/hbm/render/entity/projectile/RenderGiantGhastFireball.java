package com.hbm.render.entity.projectile;

import com.hbm.entity.mob.giantghast.EntityGiantGhastFireball;

import net.minecraft.client.renderer.entity.RenderFireball;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public final class RenderGiantGhastFireball extends RenderFireball {
    public RenderGiantGhastFireball() { super(1); }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGiantGhastFireball fireball = (EntityGiantGhastFireball)entity;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try {
            GL11.glTranslated(x, y, z);
            float scale = fireball.visualSize();
            GL11.glScalef(scale, scale, scale);
            if (fireball.isReflectable()) GL11.glColor4f(0.43F, 0.88F, 1, 1);
            super.doRender(entity, 0, 0, 0, yaw, partial);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }
}
