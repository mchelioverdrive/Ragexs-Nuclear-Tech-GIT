package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

public final class ModelWitherStormHead extends ModelBase {
    private final ModelRenderer shell, brow, jaw, teeth;
    public ModelWitherStormHead() {
        textureWidth = textureHeight = 64;
        shell = box(-12, 3, -12, 24, 19, 22, 0, 0);
        brow = box(-14, 17, -15, 28, 7, 8, 0, 32);
        jaw = box(-12, -4, -14, 24, 5, 22, 0, 32);
        teeth = new ModelRenderer(this, 48, 0);
        for(int i = 0; i < 5; i++) teeth.addBox(-10 + i * 4, 0, -14, 2, 5, 3);
    }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d, int u, int v) {
        ModelRenderer part = new ModelRenderer(this, u, v); part.addBox(x, y, z, w, h, d); return part;
    }
    public void renderHead(float age, boolean beam) {
        shell.render(0.0625F); brow.render(0.0625F);
        jaw.rotateAngleX = beam ? -0.22F - (float)Math.sin(age * 0.15F) * 0.06F : -0.04F;
        jaw.render(0.0625F); teeth.render(0.0625F);
    }
}
