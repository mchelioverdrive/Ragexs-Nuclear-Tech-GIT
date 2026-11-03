package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

public final class ModelWitherStormTentacle extends ModelBase {
    private final ModelRenderer segment;
    public ModelWitherStormTentacle() {
        textureWidth = textureHeight = 64;
        segment = new ModelRenderer(this, 0, 0); segment.addBox(-4, 0, -4, 8, 16, 8);
    }
    public void renderSegment() { segment.render(0.0625F); }
}
