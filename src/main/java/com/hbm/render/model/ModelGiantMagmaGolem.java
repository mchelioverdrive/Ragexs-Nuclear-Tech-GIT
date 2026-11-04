package com.hbm.render.model;

import java.util.ArrayList;
import java.util.List;

import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import com.hbm.entity.mob.magmagolem.MagmaGolemPose;
import com.hbm.entity.mob.magmagolem.MagmaGolemPose.Action;
import com.hbm.entity.mob.magmagolem.MagmaGolemPose.Joint;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import static com.hbm.entity.mob.magmagolem.MagmaGolemPose.*;

public final class ModelGiantMagmaGolem extends ModelBase {
    public static final ResourceLocation MOLTEN = texture("giant_magma_golem");
    public static final ResourceLocation STONE = texture("giant_magma_golem_stone");
    public static final ResourceLocation GLOW = texture("giant_magma_golem_glow");
    private static final int CRUST = 0, MAGMA = 1, BASALT = 2, BLACK = 3, EMBER = 4;
    private static final float UNIT = 0.125F;
    private final MagmaGolemPose pose = new MagmaGolemPose();
    private final List<List<ModelRenderer>> segments = new ArrayList<List<ModelRenderer>>();

    public ModelGiantMagmaGolem() {
        textureWidth = textureHeight = 512;
        for (int i = 0; i < pose.joints.length; i++) segments.add(new ArrayList<ModelRenderer>());

        // A narrow waist supports a stepped barrel chest, raised back plates, and a deep sternum.
        box(BODY, BASALT, -1.9, -0.37, -1.07, 3.8, 1.48, 2.19);
        box(BODY, CRUST, -2.36, 0.65, -1.24, 4.72, 2.62, 2.48);
        box(BODY, CRUST, -2.7, 2.58, -1.39, 5.4, 1.85, 2.88);
        box(BODY, BASALT, -2.42, 4.31, -1.29, 4.84, 0.6, 2.63);
        box(BODY, MAGMA, -1.25, 1.22, 1.16, 2.5, 2.57, 0.49);
        box(BODY, CRUST, -0.56, 0.85, 1.38, 1.12, 0.78, 0.43);
        box(BODY, BASALT, -1.36, 3.75, 1.22, 2.72, 0.67, 0.48);
        for (int sign : new int[] {-1, 1}) {
            box(BODY, CRUST, sign < 0 ? -2.43 : 1.06, 2.23, 1.15, 1.37, 1.42, 0.56);
            box(BODY, BASALT, sign < 0 ? -2.39 : 1.12, 3.41, 1.39, 1.27, 0.51, 0.37);
            box(BODY, CRUST, sign < 0 ? -1.85 : 0.73, 2.82, -1.64, 1.12, 1.44, 0.48);
            box(BODY, MAGMA, sign < 0 ? -1.83 : 1.33, 1.02, -1.38, 0.5, 1.73, 0.24);
        }

        // The eyes and mouth sit behind projecting brow, cheek and nose geometry.
        box(HEAD, CRUST, -1.16, 0.54, -0.86, 2.32, 1.59, 1.66);
        box(HEAD, BASALT, -1.21, 1.79, -0.75, 2.42, 0.5, 1.72);
        box(HEAD, CRUST, -1.17, 1.59, 0.77, 2.34, 0.39, 0.38);
        box(HEAD, BLACK, -0.79, 0.37, 0.64, 1.58, 0.58, 0.31);
        box(HEAD, CRUST, -0.26, 0.84, 0.83, 0.52, 0.67, 0.58);
        box(HEAD, BASALT, -0.29, 0.79, 1.12, 0.58, 0.28, 0.35);
        for (int sign : new int[] {-1, 1}) {
            box(HEAD, BLACK, sign < 0 ? -0.97 : 0.26, 1.17, 0.76, 0.71, 0.4, 0.21);
            box(HEAD, EMBER, sign < 0 ? -0.87 : 0.33, 1.25, 0.96, 0.54, 0.23, 0.09);
            box(HEAD, CRUST, sign < 0 ? -1.16 : 0.79, 0.39, 0.66, 0.37, 0.96, 0.48);
        }
        box(JAW, BASALT, -0.95, -0.33, -1.08, 1.9, 0.41, 1.75);
        box(JAW, MAGMA, -0.58, 0.04, 0.02, 1.16, 0.13, 0.59);
        box(JAW, CRUST, -0.74, -0.27, 0.55, 1.48, 0.22, 0.29);

        arm(LEFT);
        arm(RIGHT);
        leg(LEFT);
        leg(RIGHT);
    }

    private void arm(int side) {
        int upper = side + SHOULDER, lower = side + FOREARM;
        double outer = side == LEFT ? 0.57 : -1.14;
        box(upper, CRUST, -0.86, -3.03, -0.82, 1.72, 3.43, 1.64);
        box(upper, BASALT, -1.03, -1.43, -0.94, 2.06, 1.94, 1.94);
        box(upper, CRUST, -0.96, 0.26, -0.88, 1.91, 0.38, 1.85);
        box(upper, CRUST, outer, -1.65, -0.74, 0.57, 1.68, 1.54);
        box(upper, MAGMA, -0.61, -2.59, 0.68, 1.22, 1.32, 0.29);
        box(lower, MAGMA, -0.92, -2.75, -0.94, 1.84, 2.91, 1.88);
        box(lower, CRUST, outer, -1.94, -0.71, 0.49, 1.52, 1.32);
        box(lower, BASALT, -0.82, -1.62, -1.06, 1.63, 0.89, 0.36);
        box(lower, MAGMA, -1.15, -4.02, -1.15, 2.3, 1.78, 2.28);
        box(lower, CRUST, -1.07, -3.13, -1.22, 2.14, 0.67, 0.62);
        for (int i = 0; i < 3; i++) {
            double x = -1.07 + i * 0.74;
            box(lower, MAGMA, x, -4.17 + (i == 1 ? -0.05 : 0), 0.83, 0.65, 1.21, 0.53);
            box(lower, BASALT, x + 0.06, -3.62, 1.24, 0.53, 0.61, 0.18);
        }
        box(lower, MAGMA, side == LEFT ? -1.27 : 0.84, -3.57, 0.16, 0.45, 1.08, 0.89);
    }

    private void leg(int side) {
        int thigh = side + HIP, shin = side + SHIN, foot = side + FOOT;
        box(thigh, CRUST, -0.92, -2.27, -0.82, 1.84, 2.38, 1.64);
        box(thigh, BASALT, -0.98, -0.79, -0.91, 1.96, 0.85, 1.82);
        box(thigh, CRUST, -0.67, -1.67, 0.74, 1.34, 1.02, 0.29);
        box(shin, BASALT, -0.76, -2.02, -0.71, 1.52, 2.16, 1.45);
        box(shin, CRUST, -0.88, -0.7, -0.8, 1.76, 0.91, 1.7);
        box(shin, MAGMA, -0.56, -1.74, 0.57, 1.12, 1.46, 0.37);
        box(foot, MAGMA, -0.9, -0.48, -0.75, 1.8, 0.95, 1.99);
        box(foot, CRUST, -0.8, 0.31, -0.57, 1.6, 0.45, 1.41);
        box(foot, MAGMA, -0.82, -0.48, 0.9, 1.64, 0.63, 0.51);
        box(foot, BASALT, -0.87, -0.51, -0.68, 1.74, 0.24, 1.92);
    }

    private void box(int bone, int material, double x, double y, double z, double width, double height, double depth) {
        int variant = segments.get(bone).size() % 4;
        ModelRenderer cube = new ModelRenderer(this, (material & 1) * 256 + variant * 18, (material >> 1) * 128 + variant * 11);
        cube.addBox((float)(x * 8), (float)(y * 8), (float)(z * 8), pixels(width), pixels(height), pixels(depth));
        segments.get(bone).add(cube);
    }

    private static int pixels(double blocks) { return Math.max(1, (int)Math.round(blocks * 8)); }

    public void render(EntityGiantMagmaGolem golem, TextureManager textures, float partial, boolean emissive) {
        golem.samplePose(pose, partial);
        float age = golem.animationAge() + partial;
        draw(ROOT, golem, textures, emissive, golem.action() == Action.DYING && age >= 20, age, golem.ticksExisted + partial);
    }

    private void draw(int index, EntityGiantMagmaGolem golem, TextureManager textures, boolean emissive,
            boolean cooledBody, float age, float time) {
        boolean leftArm = index == LEFT + SHOULDER || index == LEFT + FOREARM;
        boolean rightArm = index == RIGHT + SHOULDER || index == RIGHT + FOREARM;
        if (leftArm && !golem.hasArm(LEFT) || rightArm && !golem.hasArm(RIGHT)) return;
        boolean stone = cooledBody || leftArm && golem.cooled(LEFT) || rightArm && golem.cooled(RIGHT);
        Joint joint = pose.joints[index];
        GL11.glPushMatrix();
        GL11.glTranslated(joint.x, joint.y, joint.z);
        GL11.glRotated(Math.toDegrees(joint.rz), 0, 0, 1);
        GL11.glRotated(Math.toDegrees(joint.ry), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(joint.rx), 1, 0, 0);
        List<ModelRenderer> pieces = segments.get(index);
        if (!pieces.isEmpty() && !(emissive && stone)) {
            if (emissive) {
                float pulse = 0.79F + 0.18F * (float)Math.sin(time * 0.23 + index * 0.72)
                    + 0.03F * (float)Math.sin(time * 0.67 + index);
                if (golem.action() == Action.DYING) pulse *= Math.max(0, 1 - age / 20);
                GL11.glColor4f(1, 0.83F + 0.17F * pulse, 0.69F + 0.31F * pulse, pulse);
            } else {
                textures.bindTexture(stone ? STONE : MOLTEN);
            }
            float crumble = golem.action() == Action.DYING ? Math.max(0, (age - 38) / 38) : 0;
            for (int piece = 0; piece < pieces.size(); piece++) {
                float collapse = Math.max(0, crumble - ((index * 7 + piece * 3) % 8) * 0.055F);
                if (collapse >= 0.54F) continue;
                if (collapse > 0) {
                    GL11.glPushMatrix();
                    float sign = (piece + index) % 2 == 0 ? -1 : 1;
                    GL11.glTranslatef(sign * collapse * 1.6F, -collapse * 2.2F, collapse * 0.53F);
                    GL11.glRotatef(sign * collapse * 80, 1, 0, 1);
                }
                pieces.get(piece).render(UNIT);
                if (collapse > 0) GL11.glPopMatrix();
            }
        }
        for (int child = index + 1; child < pose.joints.length; child++) {
            if (pose.joints[child].parent == index) draw(child, golem, textures, emissive, cooledBody, age, time);
        }
        GL11.glPopMatrix();
    }

    private static ResourceLocation texture(String name) { return new ResourceLocation("hbm", "textures/entity/" + name + ".png"); }
}
