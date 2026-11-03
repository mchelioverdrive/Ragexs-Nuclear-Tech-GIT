package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

/** Original angular storm anatomy. Geometry and UV layout are authored for RNT. */
public final class ModelWitherStorm extends ModelBase {
    private final ModelRenderer[] body = new ModelRenderer[18];
    private final ModelRenderer[] ribs = new ModelRenderer[7];
    private final ModelRenderer leftShoulder, rightShoulder;
    public ModelWitherStorm() {
        textureWidth = textureHeight = 64;
        for(int i = 0; i < body.length; i++) {
            body[i] = new ModelRenderer(this, 0, 0);
            body[i].addBox(-8, -8, -8, 16, 16, 16);
        }
        ribs[0] = box(-3, 0, -2, 6, 48, 4);
        for(int i = 1; i < ribs.length; i++) ribs[i] = box(i % 2 == 0 ? 1 : -18, 9 + (i / 2) * 9, -2, 17, 4, 4);
        // Overlap the crown and each other; the moving necks start inside these masses.
        leftShoulder = box(-56, 52, -54, 64, 40, 64);
        rightShoulder = box(-8, 52, -54, 64, 40, 64);
    }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d) {
        ModelRenderer part = new ModelRenderer(this, 0, 0); part.addBox(x, y, z, w, h, d); return part;
    }
    public void command(float age) {
        for(int i = 0; i < ribs.length; i++) { ribs[i].rotateAngleZ = i == 0 ? 0 : (float)Math.sin(age * 0.035F + i) * 0.08F; ribs[i].render(0.0625F); }
    }
    public void storm(float age, boolean detailed) {
        leftShoulder.render(0.0625F); rightShoulder.render(0.0625F);
        int count = detailed ? body.length : 7;
        for(int i = 0; i < count; i++) {
            // Unequal overlapping masses build a broad crown and a tapered underside.
            int level = i % 6; double angle = i * 2.39996323D;
            ModelRenderer p = body[i];
            p.rotationPointX = (float)(Math.cos(angle) * (level + 1) * 5);
            p.rotationPointZ = (float)(Math.sin(angle) * (level + 1) * 4);
            p.rotationPointY = 24 + level * 23;
            p.rotateAngleX = (float)Math.sin(angle) * 0.3F;
            p.rotateAngleZ = (float)Math.cos(angle) * 0.3F;
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glTranslatef(p.rotationPointX / 16, p.rotationPointY / 16, p.rotationPointZ / 16);
            float size = 2.3F + level * 0.55F;
            org.lwjgl.opengl.GL11.glScalef(size, 2.5F, size);
            p.rotationPointX = p.rotationPointY = p.rotationPointZ = 0;
            p.render(0.0625F);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}
