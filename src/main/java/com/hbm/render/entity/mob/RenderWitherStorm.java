package com.hbm.render.entity.mob;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormPhase;
import com.hbm.render.model.ModelWitherStorm;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStorm extends Render {
    public static final ResourceLocation SKIN = new ResourceLocation("hbm", "textures/entity/wither_storm.png");
    private final ModelWitherStorm model = new ModelWitherStorm();
    private final RenderBlocks blocks = new RenderBlocks();
    private long lastParticles = Long.MIN_VALUE;
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return SKIN; }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityWitherStorm boss = (EntityWitherStorm)entity;
        Minecraft mc = Minecraft.getMinecraft();
        double distance = mc.renderViewEntity == null ? 0 : mc.renderViewEntity.getDistanceSqToEntity(boss);
        if(distance > 256D * 256D) return;
        BossStatus.setBossStatus(boss, true);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z); GL11.glRotatef(boss.prevRotationYaw + (boss.rotationYaw - boss.prevRotationYaw) * partial, 0, 1, 0);
        float scale = boss.renderScale(partial); GL11.glScalef(scale, scale, scale);
        bindTexture(SKIN);
        float age = boss.ticksExisted + partial;
        boolean evolved = boss.getPhase() != WitherStormPhase.COMMAND_BLOCK;
        float emergence = evolved ? Math.min(1F, 1F - (boss.transitionTicks() - partial) / 80F) : 0F;
        if(evolved) {
            GL11.glPushMatrix(); float grow = 0.15F + emergence * 0.85F; GL11.glScalef(grow, grow, grow);
            model.storm(age, distance < 96D * 96D); GL11.glPopMatrix();
        }
        if(!evolved || emergence < 1F) model.command(age);
        if(boss.hasCore()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, evolved ? 4.5F : 1.4F, evolved ? -4.1F : -0.2F);
            GL11.glRotatef(age * 0.8F, 0, 1, 0);
            float core = evolved ? 1.6F : 0.9F; GL11.glScalef(core, core, core);
            bindTexture(TextureMap.locationBlocksTexture);
            blocks.renderBlockAsItem(Blocks.command_block, 0, 1F);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix(); GL11.glPopAttrib();
        long tick = boss.worldObj.getTotalWorldTime();
        if(tick != lastParticles && distance < 96D * 96D && mc.gameSettings.particleSetting != 2) {
            lastParticles = tick;
            int count = mc.gameSettings.particleSetting == 0 ? 3 : 1;
            for(int i = 0; i < count; i++) boss.worldObj.spawnParticle("largesmoke", boss.posX + (boss.worldObj.rand.nextDouble() - 0.5D) * boss.width,
                boss.posY + boss.worldObj.rand.nextDouble() * boss.height, boss.posZ + (boss.worldObj.rand.nextDouble() - 0.5D) * boss.width, 0, 0.04D, 0);
        }
    }
}
