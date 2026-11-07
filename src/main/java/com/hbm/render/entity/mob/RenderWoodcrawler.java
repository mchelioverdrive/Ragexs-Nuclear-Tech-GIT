package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.lib.RefStrings;
import com.hbm.render.model.ModelWoodcrawler;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class RenderWoodcrawler extends RenderLiving {

	private static final ResourceLocation TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/entity/woodcrawler.png");

	public RenderWoodcrawler() { super(new ModelWoodcrawler(), 0.4F); }

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }

	@Override
	protected void preRenderCallback(EntityLivingBase entity, float partial) {
		EntityWoodcrawler mob = (EntityWoodcrawler) entity;
		shadowSize = 0.4F * (1 - mob.prevBurrowDepth - (mob.burrowDepth - mob.prevBurrowDepth) * partial);
	}

	@Override
	public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
		super.doRender(entity, x, y, z, yaw, partial);
		EntityWoodcrawler mob = (EntityWoodcrawler) entity;
		EntityLivingBase target = mob.getTendrilTarget();
		if(!mob.isGrabbing() || target == null || mob.isInvisible() || !mob.isEntityAlive()
				|| mob.isBurrowing() || mob.getDistanceSqToEntity(target) > 36 || !mob.hasClearTendrils(target, partial)) return;
		double mx = mob.lastTickPosX + (mob.posX - mob.lastTickPosX) * partial;
		double my = mob.lastTickPosY + (mob.posY - mob.lastTickPosY) * partial;
		double mz = mob.lastTickPosZ + (mob.posZ - mob.lastTickPosZ) * partial;
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glPushMatrix();
		try {
			GL11.glTranslated(x - mx, y - my, z - mz);
			GL11.glDisable(GL11.GL_CULL_FACE);
			bindTexture(TEXTURE);
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.setColorOpaque_F(0.65F, 0.59F, 0.53F);
			for(int side = -1; side <= 1; side += 2) {
				Vec3 previous = mob.getTendrilPoint(target, side, 0, partial);
				for(int joint = 1; joint <= 5; joint++) {
					Vec3 next = mob.getTendrilPoint(target, side, joint, partial);
					segment(tess, previous, next, joint <= 3 ? 0.032D : 0.022D);
					previous = next;
				}
			}
			tess.draw();
		} finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
	}

	/** A textured square strut, with a stable frame even when pointing straight down. */
	private static void segment(Tessellator tess, Vec3 a, Vec3 b, double radius) {
		double dx = b.xCoord - a.xCoord, dy = b.yCoord - a.yCoord, dz = b.zCoord - a.zCoord;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if(length < 0.0001D) return;
		dx /= length; dy /= length; dz /= length;
		double ux = Math.abs(dy) < 0.9D ? -dz : 1;
		double uy = 0, uz = Math.abs(dy) < 0.9D ? dx : 0;
		// Project the reference off the direction, avoiding a degenerate vertical frame.
		double dot = ux * dx + uz * dz;
		ux -= dot * dx; uy -= dot * dy; uz -= dot * dz;
		double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux /= ul; uy /= ul; uz /= ul;
		double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
		for(int face = 0; face < 4; face++) {
			double angle = face * Math.PI / 2 + Math.PI / 4;
			double c0 = Math.cos(angle), s0 = Math.sin(angle);
			double c1 = Math.cos(angle + Math.PI / 2), s1 = Math.sin(angle + Math.PI / 2);
			double ax = (ux * c0 + vx * s0) * radius, ay = (uy * c0 + vy * s0) * radius, az = (uz * c0 + vz * s0) * radius;
			double bx = (ux * c1 + vx * s1) * radius, by = (uy * c1 + vy * s1) * radius, bz = (uz * c1 + vz * s1) * radius;
			tess.setNormal((float) ((ax + bx) / radius), (float) ((ay + by) / radius), (float) ((az + bz) / radius));
			tess.addVertexWithUV(a.xCoord + ax, a.yCoord + ay, a.zCoord + az, 64D / 128, 16D / 64);
			tess.addVertexWithUV(b.xCoord + ax, b.yCoord + ay, b.zCoord + az, 64D / 128, 23D / 64);
			tess.addVertexWithUV(b.xCoord + bx, b.yCoord + by, b.zCoord + bz, 65D / 128, 23D / 64);
			tess.addVertexWithUV(a.xCoord + bx, a.yCoord + by, a.zCoord + bz, 65D / 128, 16D / 64);
		}
	}
}
