package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.opengl.GL11;

/** Retained geometry: a Wither skeleton, an enclosing core and overlapping growth lobes. */
public final class ModelWitherStorm extends ModelBase {
    private final ModelRenderer skeleton, cage, cube;
    private final ModelRenderer[] mantle = new ModelRenderer[28];
    private final float[][] lobes = new float[28][6];
    public ModelWitherStorm() {
        textureWidth = textureHeight = 64;
        skeleton = new ModelRenderer(this, 0, 16);
        skeleton.addBox(-10, 28, -1.5F, 20, 3, 3);
        skeleton.addBox(-1.5F, 5, -1.5F, 3, 24, 3);
        cage = new ModelRenderer(this, 0, 16);
        // Three pairs of ribs surround the command block without covering its front.
        for(int i = 0; i < 3; i++) {
            cage.addBox(-7, 14 + i * 5, -9, 4, 2, 10);
            cage.addBox(3, 14 + i * 5, -9, 4, 2, 10);
        }
        cube = new ModelRenderer(this, 0, 0);
        cube.addBox(-8, -8, -8, 16, 16, 16);
        for(int i = 0; i < mantle.length; i++) {
            mantle[i] = new ModelRenderer(this, (i & 1) * 16, (i % 3) * 8);
            mantle[i].addBox(-4, -4, -4, 8, 8, 8);
            double angle = i * 2.39996323D;
            float ring = i % 3 == 0 ? 0.55F : 0.82F;
            lobes[i][0] = (float)Math.cos(angle) * ring;
            lobes[i][1] = (i % 7 - 3) / 3F;
            lobes[i][2] = (float)Math.sin(angle) * ring;
            lobes[i][3] = 0.28F + (i % 4) * 0.045F;
            lobes[i][4] = 0.3F + (i % 3) * 0.06F;
            lobes[i][5] = 0.3F + (i % 5) * 0.035F;
            mantle[i].rotateAngleX = (float)Math.sin(angle) * 0.18F;
            mantle[i].rotateAngleZ = (float)Math.cos(angle) * 0.22F;
        }
    }
    public void command() { skeleton.render(0.0625F); }
    public void coreFrame() { cage.render(0.0625F); }
    public void storm(float development, boolean detailed) {
        if(development <= 0F) return;
        // Width/depth grow much faster than skulls. Early lobes collect behind exposed ribs.
        float w = 0.2F + development * 10F;
        float h = 0.35F + development * 6.2F;
        float d = 0.25F + development * 7.5F;
        float center = 1.35F + development * 6F;
        float back = development < 0.22F ? 0.55F : 0.55F * (1F - development);
        // Intersecting solid volumes prevent holes from below or at reduced detail.
        mass(0, center, back, w * 1.45F, h * 1.6F, d * 1.5F);
        mass(-w * 0.35F, center + h * 0.2F, back, w * 1.25F, h * 1.35F, d * 1.6F);
        mass(w * 0.35F, center - h * 0.12F, back, w * 1.25F, h * 1.5F, d * 1.55F);
        // A solid sternum joins the exposed command core to the mantle at every size.
        float core = 0.65F + development * 1.6F;
        float front = -0.18F - development * 8.5F - core * 0.35F;
        float rear = 0.65F - development * 3F;
        mass(0, 1.35F + development * 2.8F, (front + rear) * 0.5F,
            0.65F + development * 3F, 0.8F + development * 2.8F, rear - front);
        int count = detailed ? mantle.length : 18;
        for(int i = 0; i < count; i++) {
            float[] l = lobes[i];
            GL11.glPushMatrix();
            GL11.glTranslatef(l[0] * w, center + l[1] * h * 0.75F, back + l[2] * d);
            GL11.glScalef(w * l[3] * 2F, h * l[4] * 2F, d * l[5] * 2F);
            mantle[i].render(0.0625F);
            GL11.glPopMatrix();
        }
    }
    private void mass(float x, float y, float z, float w, float h, float d) {
        GL11.glPushMatrix(); GL11.glTranslatef(x, y, z); GL11.glScalef(w, h, d);
        cube.render(0.0625F); GL11.glPopMatrix();
    }
}
