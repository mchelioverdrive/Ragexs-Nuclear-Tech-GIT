package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** An upright bundle of thin struts, with independently solved feet and hanging prey hooks. */
public final class ModelWoodcrawler extends ModelBase {

	private static final int[] UPPER_LENGTH = {24, 32, 29, 22}, LOWER_LENGTH = {26, 38, 35, 28};
	private final ModelRenderer thorax, abdomen, head, eye, pupil, cocoon;
	private final ModelRenderer[] hips = new ModelRenderer[8], shins = new ModelRenderer[8], toes = new ModelRenderer[8];
	private final ModelRenderer[] feelers = new ModelRenderer[4];
	private final ModelRenderer[] tendrils = new ModelRenderer[2], tips = new ModelRenderer[2], hooks = new ModelRenderer[2];

	public ModelWoodcrawler() {
		textureWidth = 128;
		textureHeight = 64;
		thorax = part(0, 0, -1, -9, -0.5F, 2, 30, 2);
		// An exposed vertebral column and separated ribs replace a solid insect thorax.
		for(int i = 0; i < 8; i++) {
			float width = i < 5 ? 3 : 2.5F;
			ModelRenderer rib = part(0, 0, -width, -0.5F, 0, (int) (width * 2), 1, 1);
			rib.setRotationPoint(0, -4 + i * 3, 0);
			thorax.addChild(rib);
			for(int side = -1; side <= 1; side += 2) {
				ModelRenderer curve = part(64, 16, -0.5F, -0.5F, -3, 1, 1, 3);
				curve.setRotationPoint(side * (width - 0.5F), 0, 0.5F);
				curve.rotateAngleY = side * 0.3F;
				rib.addChild(curve);
			}
		}
		abdomen = part(80, 0, -2, -1.5F, -1, 4, 3, 3);
		abdomen.setRotationPoint(0, 21, 0);
		thorax.addChild(abdomen);
		head = part(0, 24, -1.5F, -7, -3, 3, 7, 4);
		head.setRotationPoint(0, -9, -1);
		thorax.addChild(head);
		eye = part(0, 48, -1, -1, -1, 2, 2, 1);
		eye.setRotationPoint(0, -4, -3);
		head.addChild(eye);
		pupil = part(20, 48, -0.5F, -1, -0.5F, 1, 2, 1);
		pupil.setRotationPoint(0, 0, -0.7F);
		eye.addChild(pupil);
		cocoon = part(80, 30, -3, 0, -3, 6, 12, 6);
		cocoon.setRotationPoint(0, 7, 0);
		abdomen.addChild(cocoon);

		for(int i = 0; i < hips.length; i++) {
			int row = i / 2, upper = UPPER_LENGTH[row], lower = LOWER_LENGTH[row];
			hips[i] = part(40, 0, -0.5F, 0, -0.5F, 1, upper, 1);
			ModelRenderer joint = part(64, 0, -1, -1, -1, 2, 2, 2);
			joint.setRotationPoint(0, upper, 0);
			hips[i].addChild(joint);
			shins[i] = part(52, 0, -0.5F, 0, -0.5F, 1, lower, 1);
			// Solve both bones in model space; their shared knee stays joined while the
			// bend plane can fold sideways under a low roof instead of flipping vertically.
			toes[i] = row == 0 ? part(64, 16, -0.5F, -1, -0.5F, 1, 7, 1)
					: part(64, 16, -0.5F, 0, -2.5F, 1, 1, 3);
			toes[i].setRotationPoint(0, lower, 0);
			shins[i].addChild(toes[i]);
			if(row == 0) {
				ModelRenderer barb = part(64, 16, -0.5F, -4, -0.5F, 1, 4, 1);
				barb.setRotationPoint(0, 6, -2);
				barb.rotateAngleX = 0.55F;
				toes[i].addChild(barb);
			}
		}
		for(int i = 0; i < feelers.length; i++) {
			feelers[i] = part(64, 16, -0.5F, 0, -0.5F, 1, 13 + i * 2, 1);
			feelers[i].setRotationPoint((i % 2 == 0 ? -1 : 1) * 1.8F, -3 + i * 2, 1);
			thorax.addChild(feelers[i]);
		}
		for(int i = 0; i < tendrils.length; i++) {
			tendrils[i] = part(64, 16, -0.5F, 0, -0.5F, 1, 14, 1);
			tendrils[i].setRotationPoint(i == 0 ? -2 : 2, 7, -2);
			thorax.addChild(tendrils[i]);
			tips[i] = part(64, 16, -0.5F, 0, -0.5F, 1, 16, 1);
			tips[i].setRotationPoint(0, 14, 0);
			tendrils[i].addChild(tips[i]);
			hooks[i] = part(64, 16, -0.5F, -5, -0.5F, 1, 6, 1);
			hooks[i].setRotationPoint(0, 16, -1);
			tips[i].addChild(hooks[i]);
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
		EntityWoodcrawler mob = (EntityWoodcrawler) entity;
		float partial = MathHelper.clamp_float(age - mob.ticksExisted, 0, 1);
		float depth = mob.prevBurrowDepth + (mob.burrowDepth - mob.prevBurrowDepth) * partial;
		GL11.glPushMatrix();
		try {
			// Positive model Y points into the ground. Keep the authoritative hitbox at the surface.
			GL11.glTranslatef(0, depth * (EntityWoodcrawler.STANDING_HEIGHT + 0.3F), 0);
			GL11.glPushMatrix();
			float bodyY = thorax.rotationPointY;
			try {
				GL11.glTranslatef(0, bodyY * scale, 0);
				GL11.glScalef(1, mob.getBodyVerticalScale(partial), 1);
				thorax.rotationPointY = 0;
				thorax.render(scale);
			} finally {
				thorax.rotationPointY = bodyY;
				GL11.glPopMatrix();
			}
			for(ModelRenderer hip : hips) hip.render(scale);
			for(ModelRenderer shin : shins) shin.render(scale);
		} finally { GL11.glPopMatrix(); }
	}

	@Override
	public void setRotationAngles(float stride, float amount, float age, float yaw, float pitch, float scale, Entity entity) {
		EntityWoodcrawler mob = (EntityWoodcrawler) entity;
		float partial = MathHelper.clamp_float(age - mob.ticksExisted, 0, 1);
		float climb = mob.prevClimbBlend + (mob.climbBlend - mob.prevClimbBlend) * partial;
		float crouch = mob.prevCrouchBlend + (mob.crouchBlend - mob.prevCrouchBlend) * partial;
		float stalk = mob.prevStalkBlend + (mob.stalkBlend - mob.prevStalkBlend) * partial;
		float attack = mob.getGrabExtension(partial);
		float depth = mob.prevBurrowDepth + (mob.burrowDepth - mob.prevBurrowDepth) * partial;
		float movement = MathHelper.clamp_float(amount * 2, 0, 1) * (1 - depth);
		thorax.rotationPointY = mob.getBodyModelY(partial);
		// The central strut remains stable; small independent motions live in the neck and appendages.
		thorax.rotateAngleX = thorax.rotateAngleZ = 0;
		abdomen.rotateAngleX = MathHelper.sin(age * 0.037F) * 0.025F;
		head.rotateAngleY = MathHelper.clamp_float(yaw * 0.017453292F, -0.6F, 0.6F);
		head.rotateAngleX = MathHelper.clamp_float(pitch * 0.017453292F, -0.35F, 0.4F) + 0.13F - attack * 0.22F;
		head.rotateAngleZ = MathHelper.sin(age * 0.029F) * 0.045F;
		pupil.rotationPointX = head.rotateAngleY * 0.2F;
		cocoon.showModel = mob.isCarrying();
		cocoon.rotateAngleX = MathHelper.sin(stride * 0.65F) * movement * 0.06F;
		for(int i = 0; i < feelers.length; i++) {
			feelers[i].rotateAngleX = 0.15F + MathHelper.sin(age * 0.045F + i * 1.7F) * 0.09F;
			feelers[i].rotateAngleZ = (i % 2 == 0 ? 1 : -1) * (0.18F + MathHelper.sin(age * 0.031F + i) * 0.07F);
		}
		for(int i = 0; i < tendrils.length; i++) {
			// Active hooks are drawn by the renderer from these exact sockets to live prey.
			tendrils[i].showModel = !mob.isGrabbing() || mob.getTendrilTarget() == null;
			tendrils[i].rotateAngleX = -0.14F - crouch * 1.2F + MathHelper.sin(age * 0.04F + i * 2) * 0.09F;
			tendrils[i].rotateAngleZ = (i == 0 ? 1 : -1) * (0.12F + MathHelper.sin(age * 0.033F) * 0.05F);
			tips[i].rotateAngleX = -0.2F + MathHelper.sin(age * 0.05F + i) * 0.1F;
			hooks[i].rotateAngleX = 0.75F;
		}
		for(int i = 0; i < hips.length; i++) {
			int row = i / 2;
			float side = (i & 1) == 0 ? -1 : 1;
			float phase = stride * 0.65F + row * 2.15F + (side < 0 ? 0 : (float) Math.PI);
			float cycle = MathHelper.sin(phase);
			float lift = Math.max(0, cycle) * movement;
			float hipZ = row == 0 ? -2 : -2 + (row - 1) * 2;
			float hipY = row <= 1 ? -2 : row == 2 ? 6 : 19;
			// Shoulder struts, lower ribs and pelvis carry different limb pairs, rather than a spider fan.
			hips[i].setRotationPoint(side * 1.8F, thorax.rotationPointY + hipY * mob.getBodyVerticalScale(partial), hipZ);
			float footX = side * (16 + crouch * 8 + (row == 1 || row == 2 ? 3 : 0));
			float footZ = -18 + (row - 1) * 18 + MathHelper.cos(phase) * movement * (8 - stalk * 3);
			float footY = 24 - lift * (7 - stalk * 3);
			float wallCycle = age * 0.17F + row * 2.15F + (side < 0 ? 0 : (float) Math.PI);
			footX += (side * (14 + row) - footX) * climb;
			footY += (-31 + row * 14 + MathHelper.cos(wallCycle) * 6 - footY) * climb;
			footZ += (-24 - Math.max(0, MathHelper.sin(wallCycle)) * 5 - footZ) * climb;
			if(row == 0) {
				// Tall folded raptorial arms echo the reference's inverted-V silhouette. They
				// unfold toward prey during a grab and fold sideways for a low doorway crawl.
				footX = side * (16 + crouch * 16 - attack * 5);
				footY = thorax.rotationPointY - 9 + crouch * 19 + MathHelper.sin(age * 0.035F + i * 1.5F) * 1.2F;
				footY += (12 - footY) * attack * 0.8F;
				footZ = -14 - attack * 20 + MathHelper.sin(age * 0.027F + i) * 1.5F;
				footY += (-26 - footY) * climb;
				footZ += (-28 - footZ) * climb;
			}
			footX *= 1 - depth * 0.3F;
			footY -= depth * 12;
			solveLeg(i, side, footX - hips[i].rotationPointX, footY - hips[i].rotationPointY, footZ - hipZ, crouch, climb);
		}
	}

	private void solveLeg(int index, float side, float dx, float dy, float dz, float crouch, float climb) {
		double upper = UPPER_LENGTH[index / 2], lower = LOWER_LENGTH[index / 2];
		double actual = Math.max(0.001D, Math.sqrt(dx * dx + dy * dy + dz * dz));
		double distance = MathHelper.clamp_double(actual, Math.abs(upper - lower) + 0.1D, upper + lower - 0.2D);
		double nx = dx / actual, ny = dy / actual, nz = dz / actual;
		// Upright legs bend outward; raptorial elbows rise above the head. Crawling
		// rotates that plane horizontally so long bones remain below the doorway roof.
		double px = index < 2 ? 0 : side, py = index < 2 ? -1 : 0, pz = 0;
		double dot = px * nx + py * ny;
		px -= dot * nx; py -= dot * ny; pz -= dot * nz;
		double plen = Math.max(0.001D, Math.sqrt(px * px + py * py + pz * pz));
		double horizontal = Math.max(0.001D, Math.sqrt(nx * nx + nz * nz));
		double fold = crouch * (1 - climb);
		px = px / plen * (1 - fold) + nz / horizontal * side * fold;
		py = py / plen * (1 - fold);
		pz = pz / plen * (1 - fold) - nx / horizontal * side * fold;
		plen = Math.max(0.001D, Math.sqrt(px * px + py * py + pz * pz));
		double along = (upper * upper + distance * distance - lower * lower) / (2 * distance);
		double reach = Math.sqrt(Math.max(0, upper * upper - along * along));
		double kx = nx * along + px / plen * reach, ky = ny * along + py / plen * reach, kz = nz * along + pz / plen * reach;
		orient(hips[index], kx, ky, kz);
		shins[index].setRotationPoint(hips[index].rotationPointX + (float) kx,
				hips[index].rotationPointY + (float) ky, hips[index].rotationPointZ + (float) kz);
		orient(shins[index], nx * distance - kx, ny * distance - ky, nz * distance - kz);
		toes[index].rotateAngleZ = -shins[index].rotateAngleZ;
		toes[index].rotateAngleX = -climb * 1.1F;
		if(index < 2) {
			toes[index].rotateAngleZ = side * 0.55F;
			toes[index].rotateAngleX = -0.5F;
		}
	}

	private void orient(ModelRenderer bone, double x, double y, double z) {
		double length = Math.max(0.001D, Math.sqrt(x * x + y * y + z * z));
		// ModelRenderer.render applies Rz * Ry * Rx, not Ry * Rz. Use pitch and
		// roll to map the local +Y bone onto the solved vector in that actual order.
		bone.rotateAngleY = 0;
		bone.rotateAngleX = (float) Math.asin(MathHelper.clamp_double(z / length, -1, 1));
		bone.rotateAngleZ = (float) Math.atan2(-x, y);
	}
}
