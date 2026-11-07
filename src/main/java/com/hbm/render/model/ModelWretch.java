package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityWretch;
import com.hbm.entity.mob.gemini.WretchPose;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Hunched bear remains: split elongated skull, uneven shoulders, sinewy arms and rooted flesh pores. */
public final class ModelWretch extends ModelBase {
    private final ModelRenderer torso, hump, pelvis, skull, jaw, muzzle, nose, ears, teeth, eyes, pores;
    private final ModelRenderer[] growths = new ModelRenderer[13], roots = new ModelRenderer[13];
    private final ModelRenderer bone, hand, fingers;
    private final ModelRenderer[] hind = new ModelRenderer[2], feet = new ModelRenderer[2];
    public ModelWretch() {
        textureWidth = textureHeight = 16;
        torso = box(-17, 23, -16, 34, 25, 35);
        torso.rotateAngleX = -0.16F;
        hump = box(-19, 38, -17, 37, 17, 24);
        hump.rotateAngleZ = -0.12F;
        pelvis = box(-12, 14, -20, 25, 23, 22);
        skull = box(-7, -4, -9, 14, 12, 24); skull.setRotationPoint(0, 50, 21);
        muzzle = box(-5, -3, 10, 10, 7, 18);
        jaw = box(-5, -7, 0, 10, 4, 28); jaw.setRotationPoint(0, -1, -1);
        nose = box(-5, -2, 25, 10, 5, 4);
        ears = box(-12, 5, -7, 5, 7, 4); ears.addBox(7, 7, -8, 4, 5, 4);
        teeth = box(-4, -5, 8, 2, 5, 2);
        for(int i = 0; i < 4; i++) teeth.addBox(i % 2 == 0 ? -4 : 3, -5, 13 + i * 3, 1, 4 + i % 2, 2);
        eyes = box(-7.4F, 1, 2, 1, 2, 4); eyes.addBox(6.4F, 1, 3, 1, 2, 3);
        pores = box(-18, 40, 7, 6, 6, 2);
        pores.addBox(10, 31, 17, 5, 8, 2); pores.addBox(-8, 26, 19, 7, 5, 2);
        bone = box(-0.5F, 0, -0.5F, 1, 16, 1);
        hand = box(-6, -3, -4, 12, 6, 10);
        fingers = box(-5, -3, 3, 2, 3, 13);
        fingers.addBox(-1, -4, 4, 2, 3, 16); fingers.addBox(3, -3, 3, 2, 3, 11);
        fingers.addBox(5, -2, -1, 7, 3, 3);
        for(int i = 0; i < 2; i++) {
            hind[i] = box(-5, -14, -5, 10, 23 + i * 2, 11);
            hind[i].setRotationPoint(i == 0 ? -11 : 12, 15, -13);
            feet[i] = box(-5, 0, -6, 10, 5, 17);
        }
        for(int i = 0; i < growths.length; i++) {
            growths[i] = box(-3, -3, -3, 6 + i % 3, 6 + i % 4, 5 + i % 2);
            roots[i] = box(-0.6F, 0, -0.6F, 1, 7 + i % 5, 1);
            float x = (i % 2 == 0 ? -1 : 1) * (13 + i % 4);
            growths[i].setRotationPoint(x, 25 + i * 2, i % 3 * 9 - 10);
            roots[i].setRotationPoint(x, 28 + i * 2, i % 3 * 9 - 10);
            roots[i].rotateAngleZ = i % 2 == 0 ? 0.7F : -0.7F;
        }
    }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d) {
        ModelRenderer p = new ModelRenderer(this, 0, 0); p.addBox(x, y, z, w, h, d); return p;
    }
    private void skullLayer(ModelRenderer layer, float red, float green, float blue) {
        GL11.glColor4f(red, green, blue, 1); layer.render(0.0625F);
    }
    public void renderBody(EntityWretch boss, float partial) {
        float age = boss.isAwake() ? boss.ticksExisted + partial : 0;
        float gait = boss.limbSwing - boss.limbSwingAmount * (1 - partial);
        float stride = Math.min(1, boss.prevLimbSwingAmount + (boss.limbSwingAmount - boss.prevLimbSwingAmount) * partial);
        float time = boss.stateTime(partial);
        float wind = boss.state() == EntityWretch.SLAM ? (float) Math.sin(Math.min(1D, time / 34D) * Math.PI) : 0;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, (float) Math.sin(age * 0.07D) * 0.025F + wind * 0.12F, 0);
        GL11.glColor4f(0.45F, 0.22F, 0.18F, 1); torso.render(0.0625F); pelvis.render(0.0625F);
        GL11.glColor4f(0.25F, 0.16F, 0.11F, 1); hump.render(0.0625F);
        skull.rotateAngleX = -0.12F - wind * 0.35F;
        if(boss.state() == EntityWretch.RECOVER) skull.rotateAngleX = 0.18F;
        jaw.rotateAngleX = boss.state() == EntityWretch.IDLE ? -0.08F : -0.24F - (float) Math.sin(age * 0.12D) * 0.08F;
        // Render skull layers independently under its transform to retain distinct bone, teeth and dark sockets.
        skullLayer(skull, 0.68F, 0.51F, 0.36F);
        GL11.glPushMatrix(); GL11.glTranslatef(0, 50F / 16F, 21F / 16F);
        GL11.glRotatef(skull.rotateAngleX * 57.29578F, 1, 0, 0);
        skullLayer(muzzle, 0.56F, 0.36F, 0.27F); skullLayer(jaw, 0.5F, 0.27F, 0.22F);
        skullLayer(ears, 0.25F, 0.15F, 0.1F); skullLayer(nose, 0.08F, 0.04F, 0.035F);
        skullLayer(teeth, 0.85F, 0.8F, 0.61F); skullLayer(eyes, 0.02F, 0.009F, 0.008F);
        GL11.glPopMatrix();
        for(int i = 0; i < 2; i++) {
            hind[i].rotateAngleX = (float) Math.cos(gait * 0.45D + i * Math.PI) * stride * 0.5F;
            hind[i].rotateAngleZ = i == 0 ? 0.12F : -0.22F;
            GL11.glColor4f(0.35F, 0.2F, 0.15F, 1); hind[i].render(0.0625F);
            GL11.glPushMatrix(); GL11.glTranslatef(i == 0 ? -0.7F : 0.75F, 0, -0.75F);
            GL11.glColor4f(0.23F, 0.14F, 0.1F, 1); feet[i].render(0.0625F); GL11.glPopMatrix();
        }
        GL11.glColor4f(0.08F, 0.025F, 0.02F, 1); pores.render(0.0625F);
        for(int i = 0; i < growths.length; i++) {
            growths[i].rotateAngleZ = (float) Math.sin(age * 0.045D + i * 1.3D) * 0.12F;
            roots[i].rotateAngleX = (float) Math.sin(age * 0.06D + i) * 0.18F;
            GL11.glColor4f(0.63F, 0.25F, 0.23F, 1); growths[i].render(0.0625F);
            GL11.glColor4f(0.33F, 0.15F, 0.11F, 1); roots[i].render(0.0625F);
        }
        GL11.glPopMatrix();
        for(int arm = 0; arm < 2; arm++) {
            GL11.glColor4f(0.53F, 0.26F, 0.21F, 1);
            for(int segment = 0; segment < 2; segment++) {
                Vec3 a = WretchPose.local(boss, arm, segment, partial), b = WretchPose.local(boss, arm, segment + 1, partial);
                renderSegment(a, b, segment == 0 ? 0.72F : 0.54F);
            }
            Vec3 tip = WretchPose.local(boss, arm, 2, partial);
            GL11.glPushMatrix(); GL11.glTranslated(tip.xCoord, tip.yCoord, tip.zCoord);
            GL11.glRotatef(arm == 0 ? -12 : 18, 0, 0, 1);
            hand.render(0.0625F);
            GL11.glColor4f(0.48F, 0.3F, 0.22F, 1);
            GL11.glScalef(arm == 0 ? 1 : 1.18F, 1, arm == 0 ? 1.12F : 0.9F);
            fingers.render(0.0625F); GL11.glPopMatrix();
        }
    }
    private void renderSegment(Vec3 a, Vec3 b, float width) {
        double dx = b.xCoord - a.xCoord, dy = b.yCoord - a.yCoord, dz = b.zCoord - a.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        GL11.glPushMatrix(); GL11.glTranslated(a.xCoord, a.yCoord, a.zCoord);
        GL11.glRotated(Math.toDegrees(Math.atan2(dx, dz)), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(Math.atan2(Math.sqrt(dx * dx + dz * dz), dy)), 1, 0, 0);
        GL11.glScalef(width * 16, (float) length, width * 16); bone.render(0.0625F);
        GL11.glPopMatrix();
    }
}
