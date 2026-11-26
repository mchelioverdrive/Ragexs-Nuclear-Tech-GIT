package com.hbm.render.model;

import com.hbm.entity.mob.squirrel.EntitySquirrel;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public final class ModelSquirrel extends ModelBase {
    private final ModelRenderer root, head, lowerTail, middleTail, tailTip;
    private final ModelRenderer[] legs = new ModelRenderer[4];

    public ModelSquirrel() {
        textureWidth = textureHeight = 64;
        root = new ModelRenderer(this);
        box(root, 0, 0, 0F, 0F, 0F, -2.5F, -3.4F, -3.5F, 5, 4, 7);
        box(root, 0, 11, 0F, 0F, 0F, -1.5F, -0.1F, -3.9F, 3, 1, 6);

        head = box(root, 24, 0, 0F, -2.3F, -3.3F, -2.5F, -2.5F, -3.2F, 5, 4, 4);
        box(head, 24, 9, 0F, 0F, 0F, -1.5F, -0.5F, -4.2F, 3, 2, 2);
        box(head, 44, 0, 0F, 0F, 0F, -0.5F, -0.6F, -4.55F, 1, 1, 1);
        for(int side : new int[] {-1, 1}) {
            ModelRenderer ear = box(head, 0, 20, side * 1.7F, -2.4F, -0.8F, -0.5F, -1.9F, -0.55F, 1, 2, 1);
            ear.rotateAngleZ = side * 0.22F;
            box(head, 48, 0, side * 2.15F, -1.2F, -2.15F, -0.5F, -0.5F, -0.5F, 1, 1, 1);
        }
        legs[0] = box(root, 0, 28, -1.55F, 0F, -2.3F, -0.5F, 0F, -1.5F, 1, 3, 2);
        legs[1] = box(root, 0, 28, 1.55F, 0F, -2.3F, -0.5F, 0F, -1.5F, 1, 3, 2);
        legs[2] = box(root, 10, 28, -1.55F, 0F, 2.2F, -1F, 0F, -1.6F, 2, 3, 3);
        legs[3] = box(root, 10, 28, 1.55F, 0F, 2.2F, -1F, 0F, -1.6F, 2, 3, 3);

        lowerTail = box(root, 0, 36, 0F, -1.15F, 2.8F, -1.5F, -2.7F, -0.5F, 3, 4, 4);
        middleTail = box(lowerTail, 16, 36, 0F, -0.5F, 2.4F, -2.5F, -2.7F, -0.6F, 5, 5, 5);
        tailTip = box(middleTail, 36, 36, 0F, -0.3F, 3.35F, -2F, -2.6F, -0.4F, 4, 4, 3);
    }

    private ModelRenderer box(ModelRenderer parent, int u, int v, float x, float y, float z,
            float left, float top, float front, int width, int height, int depth) {
        ModelRenderer part = new ModelRenderer(this, u, v);
        part.addBox(left, top, front, width, height, depth);
        part.setRotationPoint(x, y, z);
        parent.addChild(part);
        return part;
    }

    @Override public void setLivingAnimations(EntityLivingBase entity, float stride, float speed, float partialTick) {
        EntitySquirrel squirrel = (EntitySquirrel) entity;
        ForgeDirection face = squirrel.getAttachmentFace();
        boolean onWall = face.offsetX != 0 || face.offsetZ != 0;
        boolean climbing = squirrel.getAction() == EntitySquirrel.CLIMBING;
        boolean leaping = squirrel.getAction() == EntitySquirrel.LEAPING;
        boolean carried = squirrel.getCarrierId() >= 0;
        float time = squirrel.ticksExisted + partialTick;
        root.setRotationPoint(0F, onWall || climbing ? 18F : face == ForgeDirection.DOWN ? 15F : 21F, onWall ? -2.2F : 0F);
        root.rotateAngleX = onWall || climbing ? -(float) Math.PI / 2F
                : face == ForgeDirection.DOWN ? (float) Math.PI : carried ? -0.25F : 0F;
        lowerTail.rotateAngleX = (carried ? 1.55F : leaping ? 0.28F : 0.95F)
                + MathHelper.sin(time * 0.17F) * (squirrel.isAttached() ? 0.04F : 0.14F);
        lowerTail.rotateAngleY = MathHelper.sin(time * 0.15F) * (squirrel.isAttached() ? 0.03F : 0.21F);
        middleTail.rotateAngleX = 0.38F + MathHelper.sin(time * 0.19F - 0.4F) * 0.08F;
        tailTip.rotateAngleX = 0.28F + MathHelper.sin(time * 0.19F - 0.8F) * 0.13F;

        float step = climbing ? time * 0.62F : stride * 1.6F;
        float swing = MathHelper.cos(step) * (climbing ? 0.65F : Math.min(1F, speed) * 0.95F);
        for(int i = 0; i < legs.length; i++) {
            float walking = i == 0 || i == 3 ? swing : -swing;
            legs[i].rotateAngleX = carried ? (i < 2 ? -0.65F : 0.8F) : squirrel.isAttached() ? (i < 2 ? 0.25F : -0.2F)
                    : leaping ? (i < 2 ? -1.1F : 0.65F) : walking;
        }
    }

    @Override public void render(Entity entity, float stride, float speed, float age, float yaw, float pitch, float scale) {
        EntitySquirrel squirrel = (EntitySquirrel) entity;
        boolean fixedHead = squirrel.isAttached() || squirrel.getAction() == EntitySquirrel.CLIMBING || squirrel.getCarrierId() >= 0;
        head.rotateAngleY = fixedHead ? 0F : MathHelper.clamp_float(yaw, -65F, 65F) * 0.0174533F;
        head.rotateAngleX = squirrel.getAction() == EntitySquirrel.EATING ? 0.28F + MathHelper.sin(age * 1.2F) * 0.17F
                : fixedHead ? 0F : MathHelper.clamp_float(pitch, -45F, 45F) * 0.0174533F;
        root.render(scale);
    }
}
