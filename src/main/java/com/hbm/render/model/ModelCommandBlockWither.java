package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.Vec3;

/** The compact skeleton uses model pixels with +Y up and -Z forward. */
public final class ModelCommandBlockWither extends ModelBase {
    private static final float PIXEL = 1F / 16F;
    public static final float CORE_Y = 17.5F * PIXEL, CORE_Z = -8.5F * PIXEL, CORE_SIZE = 15F * PIXEL;
    private final ModelRenderer torso = new ModelRenderer(this);

    public ModelCommandBlockWither() {
        torso.addBox(-2, 4, -1, 4, 25, 6);
        torso.addBox(-7, 24, 0, 14, 4, 5);
        torso.addBox(-9, 9, -2, 18, 16, 5);

        for(int slot = 0; slot < 3; slot++) {
            Vec3 pivot = EntityWitherStorm.commandHeadOffset(slot, 0F, 16F);
            if(slot == 1) {
                torso.addBox(-3, (float)pivot.yCoord - 8, (float)pivot.zCoord - 2, 6, 8, 10);
            } else {
                torso.addBox((float)pivot.xCoord - 2, (float)pivot.yCoord - 6, (float)pivot.zCoord - 2, 4, 6, 4);
            }
        }

        for(int side = -1; side <= 1; side += 2) {
            bone(side, 5, 26, 2, 13, 4, 5, -0.13F);
            bone(side, 15, 24, 2, 7, 3, 4, -0.96F);
            bone(side, 19, 18.5F, 1, 4, 3, 3, -2.05F);
            // Stagger the ribs around the cube and leave the center of its face and top open.
            chestRib(side, 6.25F, 21, -16.5F, 18);
            chestRib(side, 6.75F, 15.5F, -16.25F, 18);
            chestRib(side, 7F, 10, -16.5F, 18);
            bone(side, 1, 7, -3, 8, 3, 8, -0.33F);
            bone(side, 1, 4, -2, 6, 2, 6, -0.48F);
        }
        ModelRenderer tail = new ModelRenderer(this);
        tail.setRotationPoint(0, 6, 2);
        tail.addBox(-1.5F, -6, -1.5F, 3, 7, 3);
        tail.rotateAngleX = -0.45F;
        torso.addChild(tail);
    }

    private void chestRib(int side, float x, float y, float front, int depth) {
        ModelRenderer rib = new ModelRenderer(this);
        rib.setRotationPoint(side * x, y, front);
        rib.addBox(side < 0 ? -4 : 0, -1.5F, 0, 4, 3, depth);
        rib.rotateAngleX = -0.12F;
        torso.addChild(rib);
    }

    private void bone(int side, float x, float y, float z, int length, int height, int depth, float angle) {
        ModelRenderer bone = new ModelRenderer(this);
        bone.setRotationPoint(side * x, y, z);
        bone.addBox(side < 0 ? -length : 0, -height / 2F, -depth / 2F, length, height, depth);
        bone.rotateAngleZ = side * angle;
        torso.addChild(bone);
    }

    public void renderSkeleton() { torso.render(PIXEL); }
}
