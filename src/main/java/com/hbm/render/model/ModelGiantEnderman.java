package com.hbm.render.model;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

import com.hbm.entity.mob.giantenderman.GiantEndermanPose;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import static com.hbm.entity.mob.giantenderman.GiantEndermanPose.*;

public final class ModelGiantEnderman extends ModelBase {
    private final List<Piece> pieces = new ArrayList<>();
    private final DoubleBuffer transform = BufferUtils.createDoubleBuffer(16);

    public ModelGiantEnderman() {
        textureWidth = 256;
        textureHeight = 512;

        // A hollow rib cage, with a recessed backbone, broken ribs, and a dark upper mantle.
        box(HIPS, 0, -2.45, -0.7, -1.3, 4.9, 1.7, 2.6);
        box(CHEST, 0, -3.15, 4.9, -1.7, 6.3, 2.2, 3.4);
        box(CHEST, 1, -2.6, 5.5, -1.85, 5.2, 1.45, 3.7);
        box(CHEST, 0, -1.05, 6.7, -1.0, 2.1, 1.7, 2.0);
        box(CHEST, 1, -1.15, 0.25, -1.65, 2.3, 5.0, 1.3);
        box(CHEST, 2, -0.45, 0.5, -0.48, 0.9, 5.0, 0.55);
        box(CHEST, 3, -0.24, 1.35, 0.12, 0.48, 2.85, 0.15);
        for (int side = -1; side <= 1; side += 2) {
            box(CHEST, 0, side < 0 ? -3.25 : 2.45, 0.6, -1.7, 0.8, 5.5, 1.0);
            for (int i = 0; i < 5; i++) {
                double y = 0.95 + i * 0.87;
                double reach = 1.9 + Math.min(i, 3) * 0.24;
                box(CHEST, 2, side < 0 ? -reach : reach - 0.56, y, -1.32, 0.56, 0.42, 2.67);
                box(CHEST, 2, side < 0 ? -reach : 0.83, y - 0.16, 0.88, reach - 0.83, 0.43, 0.55);
                box(CHEST, 1, side < 0 ? -reach - 0.16 : reach - 0.58, y + 0.15, 0.16, 0.74, 0.37, 0.81);
            }
        }

        // Oversized skull, recessed eyes, independent lower jaw and emissive inner mouth.
        box(HEAD, 0, -3.65, 1.72, -3.3, 7.3, 4.38, 6.6);
        box(HEAD, 1, -3.52, -0.82, -3.3, 7.04, 2.55, 3.6);
        box(HEAD, 0, -3.65, 0.24, 0.1, 0.91, 2.1, 3.25);
        box(HEAD, 0, 2.74, 0.24, 0.1, 0.91, 2.1, 3.25);
        box(HEAD, 0, -2.75, 1.76, 2.95, 5.5, 0.72, 0.57);
        box(HEAD, 1, -3.71, 4.7, 2.99, 7.42, 0.32, 0.54);
        box(HEAD, 3, -2.72, 0.32, 0.2, 5.44, 1.4, 0.18);
        box(HEAD, 4, -1.94, 0.54, 0.4, 3.88, 0.66, 0.13);
        for (int side = -1; side <= 1; side += 2) {
            double eye = side < 0 ? -3.13 : 0.82;
            box(HEAD, 1, eye - 0.14, 3.63, 3.27, 2.58, 1.16, 0.14);
            box(HEAD, 3, eye, 3.84, 3.41, 2.31, 0.73, 0.13);
            box(HEAD, 4, eye + 0.78, 3.91, 3.54, 0.73, 0.58, 0.13);
        }
        box(JAW, 0, -3.46, -0.8, -0.56, 6.92, 1.2, 6.36);
        box(JAW, 1, -2.88, -0.96, 3.55, 5.76, 0.85, 2.28);
        box(JAW, 3, -2.71, 0.37, 1.22, 5.42, 0.16, 4.24);
        box(JAW, 4, -1.7, 0.51, 3.6, 3.4, 0.13, 1.65);

        for (int side = -1; side <= 1; side += 2) {
            int arm = arm(side), leg = side < 0 ? LEFT_LEG : RIGHT_LEG;
            box(arm, 0, -0.92, -8.32, -0.91, 1.84, 8.48, 1.82);
            box(arm, 1, -1.06, -2.05, -1.01, 2.12, 2.21, 2.02);
            box(arm, 2, -0.55, -6.5, -0.94, 1.1, 0.7, 0.15);
            box(arm + 1, 0, -0.78, -8.32, -0.8, 1.56, 8.4, 1.6);
            box(arm + 1, 1, -0.87, -7.8, -0.88, 1.74, 1.12, 1.76);
            box(arm + 2, 0, -1.0, -1.0, -0.87, 2.0, 1.35, 1.74);
            for (int finger = 0; finger < 3; finger++) {
                box(arm + 2, 1, -0.99 + finger * 0.7, -1.65 + Math.abs(finger - 1) * 0.2, 0.18, 0.55, 0.85, 0.72);
            }
            box(arm + 2, 0, side < 0 ? 0.8 : -1.38, -0.93, 0.16, 0.58, 1.03, 0.81);
            box(leg, 0, -1.02, -7.3, -1.01, 2.04, 7.5, 2.02);
            box(leg, 1, -1.09, -1.4, -1.09, 2.18, 1.48, 2.18);
            box(leg + 1, 0, -0.86, -7.55, -0.87, 1.72, 7.62, 1.74);
            box(leg + 1, 1, -0.97, -1.2, -0.96, 1.94, 1.5, 1.92);
            box(leg + 2, 0, -0.96, -0.75, -1.0, 1.92, 1.03, 2.46);
        }
    }

    private void box(int bone, int material, double x, double y, double z, double w, double h, double d) {
        ModelRenderer mesh = new ModelRenderer(this, 0, material * 96);
        mesh.addBox((float)x * 8, (float)y * 8, (float)z * 8, (int)Math.round(w * 8), (int)Math.round(h * 8), (int)Math.round(d * 8));
        pieces.add(new Piece(bone, material, mesh));
    }

    public void render(GiantEndermanPose pose, boolean emissive, float alpha, boolean exposed, float hurt) {
        for (int i = 0; i < pose.bones.length; i++) {
            GL11.glPushMatrix();
            transform.clear();
            transform.put(pose.bones[i].matrix);
            transform.flip();
            GL11.glMultMatrix(transform);
            for (Piece piece : pieces) {
                if (piece.bone != i || (piece.material >= 3) != emissive) continue;
                if (emissive && i == CHEST && !exposed) continue;
                if (emissive && i == JAW && pose.mouth < 0.04) continue;
                float glow = i == CHEST ? 0.82F : 1;
                GL11.glColor4f(glow, glow * (1 - hurt * 0.52F), glow * (1 - hurt * 0.42F), alpha);
                piece.mesh.render(0.125F);
            }
            GL11.glPopMatrix();
        }
    }

    private static final class Piece {
        final int bone, material;
        final ModelRenderer mesh;
        Piece(int bone, int material, ModelRenderer mesh) {
            this.bone = bone;
            this.material = material;
            this.mesh = mesh;
        }
    }
}
