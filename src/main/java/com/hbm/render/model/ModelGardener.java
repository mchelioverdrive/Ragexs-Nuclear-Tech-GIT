package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityGardener;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

/** Upright world-space mesh: layered root bulb, lobes and a single swiveling eye. */
public final class ModelGardener extends ModelBase {
    private final ModelRenderer bulb, crown, eye, iris, pupil;
    private final ModelRenderer[] lobes = new ModelRenderer[9];
    public ModelGardener() {
        textureWidth = textureHeight = 16;
        bulb = part(-24, 0, -22, 48, 20, 44);
        crown = part(-19, 12, -18, 38, 31, 36);
        eye = part(-12, -10, -12, 24, 20, 18);
        iris = part(-6, -7, -13, 12, 14, 2);
        pupil = part(-2, -6, -14, 4, 12, 2);
        for(int i = 0; i < lobes.length; i++) {
            lobes[i] = part(-4, -4, -4, 8, 8 + i % 3 * 3, 8);
            double angle = i * Math.PI * 2 / lobes.length;
            lobes[i].setRotationPoint((float) Math.cos(angle) * 20, 15 + i % 3 * 5, (float) Math.sin(angle) * 18);
            lobes[i].rotateAngleZ = (float) Math.cos(angle) * 0.45F;
        }
    }
    private ModelRenderer part(float x, float y, float z, int w, int h, int d) {
        ModelRenderer part = new ModelRenderer(this, 0, 0); part.addBox(x, y, z, w, h, d); return part;
    }
    public void renderBody(EntityGardener boss, float partial) {
        float age = boss.isAwake() ? boss.ticksExisted + partial : 0;
        GL11.glColor4f(0.58F, 0.26F, 0.24F, 1);
        bulb.render(0.0625F); crown.render(0.0625F);
        for(int i = 0; i < lobes.length; i++) {
            lobes[i].rotateAngleX = (float) Math.sin(age * 0.045D + i) * 0.12F;
            lobes[i].render(0.0625F);
        }
        EntityLivingBase target = boss.eyeTarget();
        double yaw = Math.sin(age * 0.012D) * 0.3D, pitch = 0D;
        if(target != null) {
            double dx = target.posX - boss.posX, dz = target.posZ - boss.posZ;
            double dy = target.posY + target.getEyeHeight() - boss.posY - 2.75D;
            yaw = Math.atan2(-dx, -dz);
            pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        }
        GL11.glPushMatrix();
        GL11.glTranslated(0, 2.75D, 0);
        GL11.glRotated(Math.toDegrees(yaw), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(pitch), 1, 0, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.88F, 0.84F, 0.63F, 1); eye.render(0.0625F);
        GL11.glColor4f(0.57F, 0.38F, 0.12F, 1); iris.render(0.0625F);
        GL11.glColor4f(0.025F, 0.015F, 0.015F, 1); pupil.render(0.0625F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}
