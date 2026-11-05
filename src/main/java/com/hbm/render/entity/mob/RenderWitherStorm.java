package com.hbm.render.entity.mob;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormStage;
import com.hbm.render.model.ModelCommandBlockWither;
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
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class RenderWitherStorm extends Render {
    public static final ResourceLocation SKIN = new ResourceLocation("hbm", "textures/entity/wither_storm.png");
    public static final ResourceLocation WITHER = new ResourceLocation("textures/entity/wither/wither.png");
    private final ModelWitherStorm model = new ModelWitherStorm();
    private final ModelCommandBlockWither commandModel = new ModelCommandBlockWither();
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
        // Match the root's local() attachment coordinates and the heads' -Z front.
        GL11.glTranslated(x, y, z); GL11.glRotatef(-(boss.prevRotationYaw + MathHelper.wrapAngleTo180_float(boss.rotationYaw - boss.prevRotationYaw) * partial), 0, 1, 0);
        float scale = boss.renderScale(partial); GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL11.GL_NORMALIZE);
        WitherStormStage stage = boss.getStage();
        if(!stage.hasTentacles()) {
            if(stage == WitherStormStage.A_TRANSFORMED) {
                bindTexture(SKIN);
                model.renderEarlyGrowth(boss.development());
            }
            renderCommandWither(boss.hasCore());
        } else renderStorm(boss, stage);
        GL11.glPopMatrix(); GL11.glPopAttrib();
        long tick = boss.worldObj.getTotalWorldTime();
        if(tick != lastParticles && distance < 96D * 96D && mc.gameSettings.particleSetting != 2) {
            lastParticles = tick;
            int count = mc.gameSettings.particleSetting == 0 ? 1 + (int)(boss.development() * 8F) : 1 + (int)(boss.development() * 2F);
            for(int i = 0; i < count; i++) boss.worldObj.spawnParticle("largesmoke", boss.posX + (boss.worldObj.rand.nextDouble() - 0.5D) * boss.width,
                boss.posY + boss.worldObj.rand.nextDouble() * boss.height, boss.posZ + (boss.worldObj.rand.nextDouble() - 0.5D) * boss.width, 0, 0.04D, 0);
        }
    }

    private void renderCommandWither(boolean hasCore) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.17F, 0.17F, 0.18F, 1F);
        commandModel.renderSkeleton();
        GL11.glPopAttrib();
        if(hasCore) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, ModelCommandBlockWither.CORE_Y, ModelCommandBlockWither.CORE_Z);
            float size = ModelCommandBlockWither.CORE_SIZE;
            GL11.glScalef(size, size, size);
            // Cancel RenderBlocks' fixed inventory turn so the cube uses the torso axes.
            GL11.glRotatef(-90F, 0, 1, 0);
            bindTexture(TextureMap.locationBlocksTexture);
            blocks.renderBlockAsItem(Blocks.command_block, 0, 1F);
            GL11.glPopMatrix();
        }
    }

    private void renderStorm(EntityWitherStorm boss, WitherStormStage stage) {
        bindTexture(SKIN);
        float development = boss.development();
        model.renderMantle(development, stage);
        if(stage == WitherStormStage.C_DAMAGED && boss.hasCore()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, ModelWitherStorm.coreY(development), ModelWitherStorm.coreZ(development));
            float core = ModelWitherStorm.coreSize(development);
            GL11.glScalef(core, core, core);
            model.renderCoreFrame();
            GL11.glRotatef(-90F, 0, 1, 0);
            bindTexture(TextureMap.locationBlocksTexture);
            blocks.renderBlockAsItem(Blocks.command_block, 0, 1F);
            GL11.glPopMatrix();
        }
    }
}
