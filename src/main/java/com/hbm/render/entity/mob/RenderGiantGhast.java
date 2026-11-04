package com.hbm.render.entity.mob;

import com.hbm.entity.mob.giantghast.EntityGiantGhast;
import com.hbm.render.model.ModelGiantGhast;

import net.minecraft.entity.boss.BossStatus;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class RenderGiantGhast extends Render {
    private final ModelGiantGhast model = new ModelGiantGhast();
    private int displayedBossId;

    public RenderGiantGhast() { shadowSize = 4.2F; }

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return null; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGiantGhast boss = (EntityGiantGhast)entity;
        if (boss.isEntityAlive()) {
            BossStatus.setBossStatus(boss, true);
            displayedBossId = boss.getEntityId();
        } else if (displayedBossId == boss.getEntityId() && boss.getCommandSenderName().equals(BossStatus.bossName)) {
            BossStatus.statusBarTime = 0;
        }
        float death = boss.deathProgress() == 0 ? 0 : Math.min(1, (boss.deathProgress() + partial) / EntityGiantGhast.DEATH_TICKS);
        float bodyYaw = boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-bodyYaw, 0, 1, 0);
            if (death > 0) {
                GL11.glTranslated(0, -3.2F * death * death, 0);
                GL11.glRotatef(24 * death * death, 0, 0, 1);
                GL11.glScalef(1 - 0.2F * death, 1 - 0.42F * death, 1 - 0.2F * death);
            }
            model.render(boss, boss.ticksExisted + partial, death);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
