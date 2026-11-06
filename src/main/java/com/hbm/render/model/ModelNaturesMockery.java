package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityNaturesMockery;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Asymmetric swollen trunk, forked fleshy branches and independently moving feelers. */
public final class ModelNaturesMockery extends ModelBase {
    private final ModelRenderer body;
    private final ModelRenderer[] branches = new ModelRenderer[7], tips = new ModelRenderer[7];
    public ModelNaturesMockery() {
        textureWidth = textureHeight = 16;
        body = part(-6, -19, -5, 12, 19, 10);
        body.setRotationPoint(0, 24, 0);
        ModelRenderer bulb = part(-4, -10, -4, 8, 12, 8);
        bulb.setRotationPoint(1, -18, 0);
        bulb.rotateAngleZ = -0.19F;
        body.addChild(bulb);
        for(int i = 0; i < branches.length; i++) {
            branches[i] = part(-1.5F, -10 - i % 3, -1.5F, 3, 11 + i % 3, 3);
            branches[i].setRotationPoint((i % 2 == 0 ? -1 : 1) * (3 + i % 3), -8 - i * 2.5F, (i % 3 - 1) * 3);
            body.addChild(branches[i]);
            tips[i] = part(-0.5F, -9, -0.5F, 1, 10, 1);
            tips[i].setRotationPoint(0, -10 - i % 3, 0);
            branches[i].addChild(tips[i]);
            ModelRenderer fork = part(-0.5F, -6, -0.5F, 1, 7, 1);
            fork.setRotationPoint(0, -5, 0);
            fork.rotateAngleZ = i % 2 == 0 ? -0.7F : 0.8F;
            tips[i].addChild(fork);
        }
        for(int i = 0; i < 5; i++) {
            ModelRenderer root = part(-1, -1, -1, 2, 2, 10);
            root.setRotationPoint(0, -1, 0);
            root.rotateAngleY = i * 1.2566F;
            body.addChild(root);
        }
    }
    private ModelRenderer part(float x, float y, float z, int w, int h, int d) {
        ModelRenderer part = new ModelRenderer(this, 0, 0);
        part.addBox(x, y, z, w, h, d);
        return part;
    }
    @Override public void render(Entity entity, float stride, float amount, float age, float yaw, float pitch, float scale) {
        EntityNaturesMockery mob = (EntityNaturesMockery) entity;
        float pulse = MathHelper.sin(age * 0.065F) * 0.025F;
        body.rotateAngleZ = MathHelper.sin(age * 0.023F) * 0.018F;
        for(int i = 0; i < branches.length; i++) {
            float side = i % 2 == 0 ? -1 : 1;
            branches[i].rotateAngleZ = side * (0.65F + i * 0.055F) + MathHelper.sin(age * 0.045F + i * 2F) * 0.07F;
            branches[i].rotateAngleX = (i % 3 - 1) * 0.36F + MathHelper.cos(age * 0.037F + i) * 0.055F;
            tips[i].rotateAngleZ = -side * (0.35F + MathHelper.sin(age * 0.08F + i) * 0.13F + (mob.isHolding() ? 0.5F : 0));
            tips[i].rotateAngleX = MathHelper.sin(age * 0.05F + i * 1.7F) * 0.23F;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 1.5F, 0);
        GL11.glScalef(1 + mob.getGrowth() * 0.002F + pulse, 1 + pulse * 0.3F, 1 + mob.getGrowth() * 0.002F - pulse);
        GL11.glTranslatef(0, -1.5F, 0);
        GL11.glColor4f(0.72F, 0.28F, 0.25F, 1);
        body.render(scale);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }
}
