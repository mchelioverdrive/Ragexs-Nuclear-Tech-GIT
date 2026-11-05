package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.WitherStormMath;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.opengl.GL11;

/** +Y is up, -Z is forward. The eye is on the upper skull, above the open mouth. */
public final class ModelWitherStormHead extends ModelBase {
    public static final float HEAD_SCALE = WitherStormMath.HEAD_SCALE;
    public static final double EYE_Y = WitherStormMath.EYE_Y, EYE_Z = WitherStormMath.EYE_Z, EYE_HALF_SIZE = 0.1875D;
    public static final double NECK_Y = 12D / 16D;
    private final ModelRenderer shell, brow, jaw, upperTeeth, lowerTeeth, neck, ordinary, sideSkull;
    public ModelWitherStormHead() {
        textureWidth = textureHeight = 64;
        ordinary = box(-4, -4, -4, 8, 8, 8, 0, 0);
        sideSkull = box(-3, -3, -3, 6, 6, 6, 32, 0);
        // Map the large cuboid unwraps inside the tiled storm skin's normalized UV range.
        textureWidth = textureHeight = 128;
        shell = new ModelRenderer(this, 0, 0);
        shell.addBox(-12, 9, -15, 24, 16, 24);
        shell.addBox(-12, 1, -10, 4, 10, 19);
        shell.addBox(8, 1, -10, 4, 10, 19);
        shell.addBox(-8, 1, 4, 16, 10, 6);
        brow = box(-12, 20, -16, 24, 4, 5, 0, 32);
        jaw = box(-10, -3, -20, 20, 6, 22, 0, 32);
        jaw.setRotationPoint(0, 0, 6);
        upperTeeth = new ModelRenderer(this, 0, 0);
        lowerTeeth = new ModelRenderer(this, 0, 0);
        for(int i = 0; i < 5; i++) {
            upperTeeth.addBox(-9 + i * 4, 6 - (i % 2), -14, 2, 3 + (i % 2), 2);
            lowerTeeth.addBox(-9 + i * 4, 2, -19, 2, 2 + (i % 2), 2);
        }
        neck = box(-8, 0, -8, 16, 16, 16, 0, 0);
    }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d, int u, int v) {
        ModelRenderer part = new ModelRenderer(this, u, v); part.addBox(x, y, z, w, h, d); return part;
    }
    public void renderWitherSkull(boolean center) {
        // Vanilla uses downward Y. A half roll keeps the face on -Z and turns it upright.
        GL11.glPushMatrix();
        GL11.glRotatef(180, 0, 0, 1);
        (center ? ordinary : sideSkull).render(0.125F);
        GL11.glPopMatrix();
    }
    public void renderHead(float age, boolean beam) {
        shell.render(0.0625F); brow.render(0.0625F);
        jaw.rotateAngleX = -0.12F - (beam ? 0.18F : 0.04F) - (float)Math.sin(age * 0.12F) * 0.05F;
        jaw.render(0.0625F);
    }
    /** Called with a pale material: lower teeth inherit the exact jaw hinge transform. */
    public void renderTeeth() {
        upperTeeth.render(0.0625F);
        GL11.glPushMatrix(); jaw.postRender(0.0625F); lowerTeeth.render(0.0625F); GL11.glPopMatrix();
    }
    public void renderNeckSegment() { neck.render(0.0625F); }
}
