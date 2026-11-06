package com.hbm.render.model;

import org.lwjgl.opengl.GL11;

import com.hbm.entity.mob.gemini.EntityFakePerson;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/** Native villager geometry in disguise, separated arms and a torn, elongated face on reveal. */
public final class ModelFakePerson extends ModelBase {

	private final ModelVillager disguise = new ModelVillager(0F) {
		@Override
		public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity entity) {
			super.setRotationAngles(swing, amount, age, yaw, pitch, scale, entity);
			float twitch = twitch(age, entity.getEntityId());
			villagerHead.rotateAngleZ = twitch * 0.07F;
			villagerHead.rotateAngleY += twitch * 0.12F;
			villagerBody.rotateAngleZ = -twitch * 0.025F;
		}
	};
	private final ModelRenderer head, jaw, neck, body, leftArm, rightArm, leftLeg, rightLeg;

	public ModelFakePerson() {
		textureWidth = textureHeight = 64;
		head = part(0, 0, 0, -1, -1, -4, -10, -4, 8, 10, 8);
		jaw = part(24, 0, 0, -2, -4, -2, 0, -2, 4, 4, 3);
		neck = part(24, 0, 0, -1, 0, -1, -3, -1, 2, 5, 2);
		body = part(16, 20, 0, 0, 0, -3, 0, -2, 6, 12, 4);
		leftArm = part(44, 22, 4, 1, 0, -1, 0, -1, 2, 17, 2);
		rightArm = part(44, 22, -4, 1, 0, -1, 0, -1, 2, 17, 2);
		leftLeg = part(0, 22, 2, 12, 0, -1.5F, 0, -1.5F, 3, 12, 3);
		rightLeg = part(0, 22, -2, 12, 0, -1.5F, 0, -1.5F, 3, 12, 3);
	}

	private ModelRenderer part(int u, int v, float x, float y, float z, float bx, float by, float bz, int w, int h, int d) {
		ModelRenderer part = new ModelRenderer(this, u, v);
		part.setRotationPoint(x, y, z);
		part.addBox(bx, by, bz, w, h, d);
		return part;
	}

	private static float twitch(float age, int id) {
		float phase = (age + id * 17) % 137F;
		return phase < 7F ? MathHelper.sin(phase * 2.4F) : 0F;
	}

	@Override
	public void render(Entity entity, float swing, float amount, float age, float yaw, float pitch, float scale) {
		EntityFakePerson mob = (EntityFakePerson) entity;
		if(mob.isDisguised()) {
			disguise.render(entity, swing, amount, age, yaw, pitch, scale);
			return;
		}
		float twitch = twitch(age, entity.getEntityId());
		head.rotateAngleY = yaw / 57.29578F;
		head.rotateAngleX = pitch / 57.29578F - 0.14F;
		head.rotateAngleZ = 0.18F + twitch * 0.18F;
		jaw.rotateAngleX = 0.35F + MathHelper.sin(age * 0.7F) * 0.08F;
		jaw.rotateAngleY = head.rotateAngleY;
		body.rotateAngleX = 0.15F;
		leftArm.rotateAngleX = -1.15F + MathHelper.cos(swing * 0.8F) * amount * 0.5F;
		rightArm.rotateAngleX = -1.4F - MathHelper.cos(swing * 0.8F) * amount * 0.5F;
		leftArm.rotateAngleZ = -0.18F;
		rightArm.rotateAngleZ = 0.24F + twitch * 0.12F;
		leftLeg.rotateAngleX = MathHelper.cos(swing * 0.6662F) * amount;
		rightLeg.rotateAngleX = MathHelper.cos(swing * 0.6662F + (float) Math.PI) * amount;
		body.render(scale);
		neck.render(scale);
		leftArm.render(scale);
		rightArm.render(scale);
		leftLeg.render(scale);
		rightLeg.render(scale);
		GL11.glPushMatrix();
		GL11.glScalef(0.8F, 1.15F, 0.8F);
		head.render(scale);
		GL11.glPopMatrix();
		jaw.render(scale);
	}
}
