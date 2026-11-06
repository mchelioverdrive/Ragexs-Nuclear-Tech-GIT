package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.EntityWitherStormPart;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelWither;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import org.lwjgl.opengl.GL11;

/** The actual vanilla Wither, with an open command-block cage attached to its animated spine. */
@SideOnly(Side.CLIENT)
public final class ModelStoryModeWither extends ModelWither {
    // Vanilla keeps the bones private. Resolve their SRG names once through Forge's runtime remapper.
    private final ModelRenderer[] bones = ObfuscationReflectionHelper.getPrivateValue(ModelWither.class, this, "field_82905_a");
    private final ModelRenderer[] heads = ObfuscationReflectionHelper.getPrivateValue(ModelWither.class, this, "field_82904_b");
    private final RenderBlocks blocks = new RenderBlocks();

    public ModelStoryModeWither() {
        // Center each native side skull on its articulation pivot; retain its vanilla UVs and geometry.
        for(int i = 1; i < 3; i++) {
            ModelRenderer original = heads[i];
            ModelRenderer pivot = new ModelRenderer(this);
            pivot.setRotationPoint(original.rotationPointX - 1F, original.rotationPointY - 1F, -1F);
            original.setRotationPoint(1F, 1F, 1F);
            pivot.addChild(original); heads[i] = pivot;
        }
        ModelRenderer cage = new ModelRenderer(this, 24, 22);
        // The vanilla backbone and shoulder bar remain visible behind the core.
        for(int y = 1; y <= 9; y += 4) {
            cage.addBox(-6, y, -11, 2, 2, 13);
            cage.addBox(7, y, -11, 2, 2, 13);
            cage.addBox(-6, y, -12, 5, 2, 2);
            cage.addBox(4, y, -12, 5, 2, 2);
        }
        bones[1].addChild(cage);
    }

    // ModelWither.setLivingAnimations casts to EntityWither; this entity owns its head aiming through parts.
    @Override public void setLivingAnimations(EntityLivingBase entity, float limb, float amount, float partial) { }

    public void renderWithCore(EntityWitherStorm boss, float partial) {
        float age = boss.ticksExisted + partial;
        setRotationAngles(0, 0, age, 0, 0, 0.0625F, boss);
        for(int slot = 0; slot < 3; slot++) {
            int vanilla = slot == 1 ? 0 : slot == 0 ? 2 : 1;
            EntityWitherStormPart part = boss.headPart(slot);
            heads[vanilla].rotateAngleY = part == null ? 0F : (part.headYaw(partial, boss) - boss.bodyYaw(partial)) * (float)Math.PI / 180F;
            heads[vanilla].rotateAngleX = part == null ? 0F : -part.headPitch(partial, boss) * (float)Math.PI / 180F;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 1.5F, 0);
        GL11.glRotatef(180, 0, 0, 1);
        for(ModelRenderer bone : bones) bone.render(0.0625F);
        for(ModelRenderer head : heads) head.render(0.0625F);
        if(boss.hasCore()) {
            GL11.glPushMatrix();
            bones[1].postRender(0.0625F);
            GL11.glTranslatef(1.5F / 16F, 5.5F / 16F, -5.5F / 16F);
            GL11.glScalef(10F / 16F, 10F / 16F, 10F / 16F);
            GL11.glRotatef(-90, 0, 1, 0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            blocks.renderBlockAsItem(Blocks.command_block, 0, 1F);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }
}
