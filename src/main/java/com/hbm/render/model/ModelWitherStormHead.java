package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

/** Original RNT geometry: +Y is up, the brow/jaw/front face point along -Z. */
public final class ModelWitherStormHead extends ModelBase {
    public static final float HEAD_SCALE = 1.5F;
    // Model-space blocks, before HEAD_SCALE and the entity's scale/rotation.
    public static final double EYE_Y = 0.775D, EYE_Z = -0.755D, EYE_HALF_SIZE = 0.1875D;
    public static final double NECK_Y = 12D / 16D;
    private final ModelRenderer shell, brow, jaw, teeth, neck;
    public ModelWitherStormHead() {
        textureWidth = textureHeight = 64;
        shell = box(-12, 3, -12, 24, 19, 22, 0, 0);
        brow = box(-14, 17, -15, 28, 7, 8, 0, 32);
        jaw = box(-12, -4, -14, 24, 5, 22, 0, 32);
        teeth = new ModelRenderer(this, 48, 0);
        for(int i = 0; i < 5; i++) teeth.addBox(-10 + i * 4, 0, -14, 2, 5, 3);
        neck = box(-8, 0, -8, 16, 16, 16, 0, 0);
    }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d, int u, int v) {
        ModelRenderer part = new ModelRenderer(this, u, v); part.addBox(x, y, z, w, h, d); return part;
    }
    public void renderHead(float age, boolean beam) {
        shell.render(0.0625F); brow.render(0.0625F);
        jaw.rotateAngleX = beam ? -0.22F - (float)Math.sin(age * 0.15F) * 0.06F : -0.04F;
        jaw.render(0.0625F); teeth.render(0.0625F);
    }
    /** Unit block along +Y; the renderer bridges the body and animated skull. */
    public void renderNeckSegment() { neck.render(0.0625F); }
}
