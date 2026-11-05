package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.WitherStormStage;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.opengl.GL11;

/** The evolved mantle grows as a connected mass around the shoulders and core. */
public final class ModelWitherStorm extends ModelBase {
    private final ModelRenderer cube;

    public ModelWitherStorm() {
        textureWidth = textureHeight = 64;
        cube = new ModelRenderer(this, 0, 0);
        cube.addBox(-8, -8, -8, 16, 16, 16);
    }

    public static float coreY(float growth) { return 1.35F + growth * 2.8F; }
    public static float coreZ(float growth) { return -0.18F - growth * 8.5F; }
    public static float coreSize(float growth) { return 0.65F + growth * 1.6F; }

    public void renderEarlyGrowth(float growth) {
        float rise = Math.max(0F, growth - 0.22F) * 3F;
        mass(0, 1.72F, 0.64F, 2.24F, 2.03F, 1.48F);
        mass(-0.47F, 2.53F + rise, 0.72F, 1.68F, 1.45F, 1.40F);
        mass(0.53F, 1.47F, 0.79F, 1.55F, 1.30F, 1.35F);
        mass(-0.41F, 3.18F + rise, 0.84F, 1.09F, 0.79F, 0.96F);
        mass(-0.26F, 3.60F + rise, 0.92F, 0.57F, 0.58F, 0.54F);
    }

    public void renderMantle(float growth, WitherStormStage stage) {
        float width = 2F + growth * 17F;
        float height = 1.9F + growth * 9F;
        float depth = 1.9F + growth * 10.4F;
        float y = 1.6F + growth * 5.2F;
        float z = 0.45F - growth * 2.3F;

        if(stage == WitherStormStage.B) {
            renderDestroyer(width, height, depth, y, z);
            return;
        }
        mass(0, y, z, width * 0.80F, height * 0.84F, depth * 0.85F);
        // Overlapping shoulders extend forward beneath the heads, into the neck roots.
        mass(-width * 0.26F, y + height * 0.06F, z - depth * 0.22F, width * 0.58F, height * 0.66F, depth * 0.71F);
        mass(width * 0.26F, y + height * 0.02F, z - depth * 0.20F, width * 0.58F, height * 0.64F, depth * 0.72F);
        mass(-width * 0.24F, y + height * 0.27F, z + depth * 0.12F, width * 0.46F, height * 0.58F, depth * 0.75F);
        mass(width * 0.21F, y + height * 0.32F, z + depth * 0.16F, width * 0.49F, height * 0.62F, depth * 0.69F);
        mass(0, y - height * 0.14F, z + depth * 0.31F, width * 0.68F, height * 0.50F, depth * 0.57F);
        mass(-width * 0.18F, y - height * 0.26F, z + depth * 0.12F, width * 0.56F, height * 0.61F, depth * 0.72F);
        mass(width * 0.22F, y - height * 0.29F, z + depth * 0.19F, width * 0.47F, height * 0.50F, depth * 0.70F);

        if(stage == WitherStormStage.C_DAMAGED) {
            // The shattered crown has two uneven banks with a lowered center.
            mass(-width * 0.26F, y + height * 0.49F, z + depth * 0.18F, width * 0.30F, height * 0.43F, depth * 0.74F);
            mass(width * 0.31F, y + height * 0.46F, z + depth * 0.32F, width * 0.28F, height * 0.47F, depth * 0.53F);
            float front = coreZ(growth) - coreSize(growth) * 0.35F;
            mass(0, coreY(growth), (front + z) * 0.5F,
                coreSize(growth) * 1.5F, coreSize(growth) * 1.35F, z - front);
            fissures(width, height, depth, y, z, true);
        } else {
            float crown = height * stage.crownScale();
            mass(0, y + crown * 0.56F, z + depth * 0.13F, width * 0.74F, crown * 0.86F, depth * 0.90F);
            mass(-width * 0.08F, y + crown * 1.03F, z + depth * 0.17F, width * 0.76F, crown * 0.68F, depth * 0.78F);
            mass(-width * 0.16F, y + crown * 1.40F, z + depth * 0.13F, width * 0.62F, crown * 0.56F, depth * 0.63F);
            mass(-width * 0.25F, y + crown * 1.70F, z + depth * 0.10F, width * 0.45F, crown * 0.39F, depth * 0.49F);
            if(stage == WitherStormStage.D) fissures(width, height, depth, y, z, false);
        }
    }

    private void renderDestroyer(float width, float height, float depth, float y, float z) {
        // Stage B has a low, stepped mantle and a narrow underside between the tentacles.
        mass(0, y, z, width * 0.79F, height * 0.76F, depth * 0.87F);
        mass(-width * 0.26F, y - height * 0.07F, z - depth * 0.19F, width * 0.47F, height * 0.55F, depth * 0.64F);
        mass(width * 0.27F, y - height * 0.10F, z - depth * 0.15F, width * 0.45F, height * 0.52F, depth * 0.64F);
        mass(0, y - height * 0.33F, z + depth * 0.09F, width * 0.60F, height * 0.45F, depth * 0.76F);
        mass(-width * 0.17F, y + height * 0.33F, z + depth * 0.07F, width * 0.54F, height * 0.49F, depth * 0.75F);
        mass(width * 0.18F, y + height * 0.24F, z + depth * 0.14F, width * 0.51F, height * 0.48F, depth * 0.69F);
        mass(-width * 0.05F, y + height * 0.54F, z + depth * 0.14F, width * 0.47F, height * 0.34F, depth * 0.59F);
        mass(-width * 0.14F, y + height * 0.67F, z + depth * 0.09F, width * 0.27F, height * 0.20F, depth * 0.36F);
        mass(width * 0.37F, y + height * 0.14F, z + depth * 0.12F, width * 0.24F, height * 0.34F, depth * 0.53F);
        mass(-width * 0.33F, y + height * 0.10F, z - depth * 0.25F, width * 0.22F, height * 0.25F, depth * 0.42F);
    }

    private void fissures(float width, float height, float depth, float y, float z, boolean damaged) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.19F, 0.055F, 0.29F, 1F);
        mass(-width * 0.17F, y + height * 0.15F, z - depth * 0.56F, width * 0.09F, height * 0.17F, depth * 0.09F);
        mass(-width * 0.12F, y + height * 0.25F, z - depth * 0.56F, width * 0.07F, height * 0.13F, depth * 0.07F);
        mass(width * 0.21F, y + height * 0.21F, z - depth * 0.55F, width * 0.07F, height * 0.16F, depth * 0.09F);
        if(damaged) mass(0, y + height * 0.48F, z + depth * 0.12F, width * 0.21F, height * 0.10F, depth * 0.44F);
        else mass(-width * 0.13F, y + height * 0.56F, z - depth * 0.32F, width * 0.08F, height * 0.23F, depth * 0.07F);
        GL11.glPopAttrib();
    }

    public void renderCoreFrame() {
        for(int side = -1; side <= 1; side += 2) {
            mass(side * 0.57F, 0, -0.10F, 0.28F, 1.10F, 0.70F);
            mass(side * 0.42F, -0.48F, -0.16F, 0.45F, 0.20F, 0.62F);
        }
    }

    private void mass(float x, float y, float z, float width, float height, float depth) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glScalef(width, height, depth);
        cube.render(0.0625F);
        GL11.glPopMatrix();
    }
}
