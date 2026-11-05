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
    private static final float UNIT = 0.125F;
    private final MagmaGolemPose pose = new MagmaGolemPose();
    private final List<List<ModelRenderer>> segments = new ArrayList<List<ModelRenderer>>();

    public ModelGiantMagmaGolem() {
        textureWidth = textureHeight = 256;
        for (int i = 0; i < pose.joints.length; i++) segments.add(new ArrayList<ModelRenderer>());

        // Each cuboid has its own region in the base, cooled and emissive atlases.
        box(BODY, 0, 0, -23, 3, -12, 46, 39, 22);
        box(BODY, 145, 0, -14, -4, -9, 28, 9, 18);

        // The eye sockets sit on the face behind the brow and the nose.
        box(HEAD, 145, 32, -10, 6, -2, 20, 17, 16);
        box(HEAD, 0, 68, -11, 16, 13, 22, 4, 5);
        box(HEAD, 54, 68, -3, 7, 13, 6, 10, 10);
        box(JAW, 90, 68, -9, -2, -3, 18, 4, 13);

        for (int side : new int[] {LEFT, RIGHT}) {
            box(side + SHOULDER, 0, 90, -9.5F, -25, -9.5F, 19, 30, 19);
            box(side + FOREARM, 80, 90, -9.5F, -20, -10, 19, 21, 20);
            box(side + FOREARM, 164, 90, -10, -33, -10.5F, 20, 13, 21);

            box(side + HIP, 0, 143, -8.5F, -18, -7.5F, 17, 19, 15);
            box(side + SHIN, 66, 143, -7.5F, -16, -7.5F, 15, 17, 15);
            box(side + FOOT, 130, 143, -8, -4, -6, 16, 9, 16);
        }
    }

    private void box(int bone, int u, int v, float x, float y, float z, int width, int height, int depth) {
        ModelRenderer cube = new ModelRenderer(this, u, v);
        cube.addBox(x, y, z, width, height, depth);
        segments.get(bone).add(cube);
    }

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
        // The pose's jaw value is an opening angle. Downward is a negative X rotation here.
        GL11.glRotated(Math.toDegrees(index == JAW ? -joint.rx : joint.rx), 1, 0, 0);
        List<ModelRenderer> pieces = segments.get(index);
        if (!pieces.isEmpty() && !(emissive && stone)) {
            if (emissive) {
                float pulse = index == HEAD ? 1 : 0.87F + 0.10F * (float)Math.sin(time * 0.23 + index * 0.72)
                    + 0.03F * (float)Math.sin(time * 0.67 + index);
                if (golem.action() == Action.DYING) pulse *= Math.max(0, 1 - age / 20);
                GL11.glColor4f(1, 1, 1, pulse);
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
