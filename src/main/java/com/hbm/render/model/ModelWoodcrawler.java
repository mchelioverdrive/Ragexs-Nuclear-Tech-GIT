package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/** Native articulated mesh: eight two-bone limbs with solved, independently lifted feet. */
public final class ModelWoodcrawler extends ModelBase {

	private final ModelRenderer thorax, abdomen, head, eye, pupil, leftFang, rightFang, cocoon;
	private final ModelRenderer[] hips = new ModelRenderer[8];
	private final ModelRenderer[] shins = new ModelRenderer[8];
	private final ModelRenderer[] toes = new ModelRenderer[8];

	public ModelWoodcrawler() {
		textureWidth = 128;
		textureHeight = 64;
		thorax = part(0, 0, -4, -6, -6, 8, 10, 11);
		thorax.setRotationPoint(0, -12, 0);
		abdomen = part(80, 0, -3.5F, -4, -2, 7, 9, 9);
		abdomen.setRotationPoint(0, -1, 5);
		thorax.addChild(abdomen);
		head = part(0, 24, -3.5F, -8, -5, 7, 8, 6);
		head.setRotationPoint(0, -3, -5);
		thorax.addChild(head);
		// A single protruding eye, with a separate iris and dark pupil.
		eye = part(0, 48, -2.5F, -2.5F, -1, 5, 5, 2);
		eye.setRotationPoint(0, -4.5F, -5);
		head.addChild(eye);
		ModelRenderer iris = part(36, 48, -1.5F, -1.5F, -0.5F, 3, 3, 1);
		iris.setRotationPoint(0, 0, -1);
		eye.addChild(iris);
		pupil = part(20, 48, -0.5F, -1.5F, -0.5F, 1, 3, 1);
		pupil.setRotationPoint(0, 0, -1.6F);
		eye.addChild(pupil);
		leftFang = part(64, 16, -0.5F, 0, -0.5F, 1, 7, 1);
		leftFang.setRotationPoint(-2.2F, -0.5F, -4);
		rightFang = part(64, 16, -0.5F, 0, -0.5F, 1, 7, 1);
		rightFang.setRotationPoint(2.2F, -0.5F, -4);
		head.addChild(leftFang);
		head.addChild(rightFang);
		cocoon = part(80, 30, -3, 0, -3, 6, 12, 6);
		cocoon.setRotationPoint(0, 3, 1);
		abdomen.addChild(cocoon);

		for(int i = 0; i < hips.length; i++) {
			hips[i] = part(40, 0, -1, 0, -1, 2, 20, 2);
			ModelRenderer joint = part(64, 0, -1.5F, -1.5F, -1.5F, 3, 3, 3);
			joint.setRotationPoint(0, 20, 0);
			hips[i].addChild(joint);
			shins[i] = part(52, 0, -0.5F, 0, -0.5F, 1, 28, 1);
			shins[i].setRotationPoint(0, 20, 0);
			hips[i].addChild(shins[i]);
			toes[i] = part(64, 16, -0.5F, 0, -3, 1, 1, 4);
			toes[i].setRotationPoint(0, 28, 0);
			shins[i].addChild(toes[i]);
		}
		// Narrow ridges and hanging sensory filaments break up the compact central body.
		for(int i = 0; i < 5; i++) {
			ModelRenderer ridge = part(64, 16, -0.5F, -4, -0.5F, 1, 5, 1);
			ridge.setRotationPoint((i % 2 == 0 ? -1 : 1) * 2, -5, -3 + i * 2);
			ridge.rotateAngleX = -0.45F;
			thorax.addChild(ridge);
		}
	}

	private ModelRenderer part(int u, int v, float x, float y, float z, int w, int h, int d) {
		ModelRenderer part = new ModelRenderer(this, u, v);
		part.addBox(x, y, z, w, h, d);
		return part;
	}

	@Override
	public void render(Entity entity, float stride, float amount, float age, float yaw, float pitch, float scale) {
		setRotationAngles(stride, amount, age, yaw, pitch, scale, entity);
		thorax.render(scale);
		for(ModelRenderer hip : hips) hip.render(scale);
	}

	@Override
	public void setRotationAngles(float stride, float amount, float age, float yaw, float pitch, float scale, Entity entity) {
		EntityWoodcrawler mob = (EntityWoodcrawler) entity;
		float partial = MathHelper.clamp_float(age - mob.ticksExisted, 0, 1);
		float climb = mob.prevClimbBlend + (mob.climbBlend - mob.prevClimbBlend) * partial;
		float crouch = mob.prevCrouchBlend + (mob.crouchBlend - mob.prevCrouchBlend) * partial;
		float stalk = mob.prevStalkBlend + (mob.stalkBlend - mob.prevStalkBlend) * partial;
		float attack = mob.attackAnimationTicks > 0 ? MathHelper.sin((14 - mob.attackAnimationTicks + partial) / 14F * (float) Math.PI) : 0;
		float movement = MathHelper.clamp_float(amount * 2, 0, 1);
		float bodyY = -12 + crouch * 18 + stalk * 2;
		thorax.rotationPointY = bodyY + MathHelper.sin(stride * 1.8F) * movement * 0.7F;
		thorax.rotateAngleX = climb * 0.25F + attack * 0.22F;
		thorax.rotateAngleZ = MathHelper.sin(stride * 0.9F) * movement * 0.025F;
		abdomen.rotateAngleX = 0.12F + MathHelper.sin(age * 0.04F) * 0.025F;
		head.rotateAngleY = MathHelper.clamp_float(yaw * 0.017453292F, -0.7F, 0.7F);
		head.rotateAngleX = MathHelper.clamp_float(pitch * 0.017453292F, -0.45F, 0.45F) - attack * 0.5F;
		eye.rotateAngleZ = MathHelper.sin(age * 0.023F) * 0.025F;
		pupil.rotationPointX = head.rotateAngleY * 0.4F;
		leftFang.rotateAngleX = rightFang.rotateAngleX = -0.55F - attack * 0.9F;
		leftFang.rotateAngleZ = 0.18F + attack * 0.35F;
		rightFang.rotateAngleZ = -leftFang.rotateAngleZ;
		cocoon.showModel = mob.isCarrying();
		cocoon.rotateAngleX = MathHelper.sin(stride * 0.9F) * movement * 0.08F;
		for(int i = 0; i < 8; i++) {
			int row = i / 2;
			float side = (i & 1) == 0 ? -1 : 1;
			float phase = stride * 0.9F + row * (float) Math.PI + (side < 0 ? 0 : (float) Math.PI);
			float swing = MathHelper.sin(phase);
			float lift = Math.max(0, swing) * movement;
			float hipZ = -5 + row * 3.3F;
			hips[i].setRotationPoint(side * 3, thorax.rotationPointY - 1, hipZ);
			float footX = side * (24 - crouch * 9 + (row == 0 || row == 3 ? -2 : 2));
			float footZ = -20 + row * 13 + MathHelper.cos(phase) * movement * (7 - stalk * 2);
			float footY = 24 - lift * (6 - stalk * 2);
			// On a wall, feet reach forward and cycle vertically, with each knee still solved separately.
			float wallCycle = age * 0.19F + row * (float) Math.PI + (side < 0 ? 0 : (float) Math.PI);
			footX += (side * (13 + row) - footX) * climb;
			footY += (-23 + row * 13 + MathHelper.cos(wallCycle) * 7 - footY) * climb;
			footZ += (-17 - Math.max(0, MathHelper.sin(wallCycle)) * 5 - footZ) * climb;
			if(row == 0) {
				footY -= attack * 12;
				footZ -= attack * 11;
			}
			solveLeg(i, side, footX - hips[i].rotationPointX, footY - hips[i].rotationPointY,
					footZ - hipZ, climb);
		}
	}

	private void solveLeg(int index, float side, float dx, float dy, float dz, float climb) {
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double distance = MathHelper.clamp_double(Math.sqrt(horizontal * horizontal + dy * dy), 9, 47.8D);
		double direction = Math.atan2(horizontal, dy);
		double spread = Math.acos(MathHelper.clamp_double((400 + distance * distance - 784) / (40 * distance), -1, 1));
		double bend = Math.PI - Math.acos(MathHelper.clamp_double((400 + 784 - distance * distance) / 1120, -1, 1));
		hips[index].rotateAngleY = (float) (-Math.atan2(dz, Math.abs(dx)) * side);
		hips[index].rotateAngleZ = (float) (-side * (direction + spread));
		shins[index].rotateAngleZ = (float) (side * bend);
		toes[index].rotateAngleZ = -hips[index].rotateAngleZ - shins[index].rotateAngleZ;
		toes[index].rotateAngleX = -climb * 1.1F;
	}
}
