package com.hbm.render.entity.effect;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.effect.EntityNukeTorex.Cloudlet;
import com.hbm.entity.effect.EntityNukeTorex.TorexType;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.main.ModEventHandlerClient;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

public class RenderTorex extends Render {

	private static final ResourceLocation cloudlet = new ResourceLocation(RefStrings.MODID + ":textures/particle/particle_base.png");
	private final Random flashRandom = new Random();
	private static final ResourceLocation flash = new ResourceLocation(RefStrings.MODID + ":textures/particle/flare.png");

	@Override
	public void doRender(Entity entity, double x, double y, double z, float f0, float interp) {

		EntityNukeTorex cloud = (EntityNukeTorex)entity;
		if(cloud.isContainedVisual()) return;
		NukeCloudClient.camera.capture();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT |
			GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_FOG_BIT | GL11.GL_TEXTURE_BIT);
		GL11.glPushMatrix();
		try {
			GL11.glTranslated(x, y, z);
			GL11.glDisable(GL11.GL_FOG);
			cloudletWrapper(cloud, interp);
			if(cloud.ticksExisted < 101) flashWrapper(cloud, interp);
			if(cloud.ticksExisted < 10 && System.currentTimeMillis() - ModEventHandlerClient.flashTimestamp > 1_000)
				ModEventHandlerClient.flashTimestamp = System.currentTimeMillis();

			if(cloud.didPlaySound && !cloud.didShake && System.currentTimeMillis() - ModEventHandlerClient.shakeTimestamp > 1_000) {
				EntityPlayer player = MainRegistry.proxy.me();
				if(player != null) {
					ModEventHandlerClient.shakeTimestamp = System.currentTimeMillis();
					cloud.didShake = true;
					player.hurtTime = 15; player.maxHurtTime = 15; player.attackedAtYaw = 0F;
				}
			}
		} finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private void cloudletWrapper(EntityNukeTorex cloud, float interp) {
		NukeCloudClient.State state = NukeCloudClient.renderState(cloud);
		float cloudAlpha = cloud.getAlpha();
		float cloudScale = (float)cloud.getScale();
		if(state == null || state.count == 0 || cloudAlpha <= 0 ||
			state.boundsCurrent(cloudScale) && !NukeCloudClient.camera.visible(state.minX, state.minY, state.minZ, state.maxX, state.maxY, state.maxZ)) return;
		state.refreshOrder(interp);
		double greying = cloud.getGreying();

		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDepthMask(false);
		RenderHelper.disableStandardItemLighting();
		bindTexture(cloudlet);

		Tessellator tess = Tessellator.instance;
		boolean drawing = false;
		for(int i = 0; i < state.count; i++) {
			Cloudlet item = state.order[i];
			float alpha = item.getAlpha(cloudAlpha);
			if(alpha <= 0) continue;
			float scale = item.getScale(cloudScale);
			double x = item.getInterpX(interp, cloudScale), y = item.getInterpY(interp, cloudScale), z = item.getInterpZ(interp, cloudScale);
			double radius = scale * 2D;
			if(!NukeCloudClient.camera.visible(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)) continue;
			if(!drawing) { tess.startDrawingQuads(); drawing = true; }
			tessellateCloudlet(tess, x - cloud.posX, y - cloud.posY, z - cloud.posZ, item, interp, greying, alpha, scale);
		}
		if(drawing) tess.draw();

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		RenderHelper.enableStandardItemLighting();
		GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
		GL11.glDisable(GL11.GL_BLEND);
	}

	private void flashWrapper(EntityNukeTorex cloud, float interp) {

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDepthMask(false);
		RenderHelper.disableStandardItemLighting();

		bindTexture(flash);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();

		double age = Math.min(cloud.ticksExisted + interp, 100);
		float alpha = (float) ((100D - age) / 100F);

		Random rand = flashRandom;
		rand.setSeed(cloud.getEntityId());

		for(int i = 0; i < 3; i++) {
			float x = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float y = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float z = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			tessellateFlash(tess, x, y + cloud.coreHeight, z, (float) (25 * cloud.rollerSize), alpha, interp);
		}

		tess.draw();

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		RenderHelper.enableStandardItemLighting();
		GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
		GL11.glDisable(GL11.GL_BLEND);
	}

	private void tessellateCloudlet(Tessellator tess, double posX, double posY, double posZ, Cloudlet cloud, float interp, double greying, float alpha, float scale) {

		float f1 = ActiveRenderInfo.rotationX;
		float f2 = ActiveRenderInfo.rotationZ;
		float f3 = ActiveRenderInfo.rotationYZ;
		float f4 = ActiveRenderInfo.rotationXY;
		float f5 = ActiveRenderInfo.rotationXZ;

		float brightness = cloud.type == TorexType.CONDENSATION ? 0.9F : 0.75F * cloud.colorMod;
		tess.setColorRGBA_F((float)cloud.getInterpRed(interp, greying) * brightness, (float)cloud.getInterpGreen(interp, greying) * brightness, (float)cloud.getInterpBlue(interp, greying) * brightness, alpha);

		tess.addVertexWithUV((double) (posX - f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ - f2 * scale - f4 * scale), 1, 1);
		tess.addVertexWithUV((double) (posX - f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ - f2 * scale + f4 * scale), 1, 0);
		tess.addVertexWithUV((double) (posX + f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ + f2 * scale + f4 * scale), 0, 0);
		tess.addVertexWithUV((double) (posX + f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ + f2 * scale - f4 * scale), 0, 1);

	}

	private void tessellateFlash(Tessellator tess, double posX, double posY, double posZ, float scale, float alpha, float interp) {

		float f1 = ActiveRenderInfo.rotationX;
		float f2 = ActiveRenderInfo.rotationZ;
		float f3 = ActiveRenderInfo.rotationYZ;
		float f4 = ActiveRenderInfo.rotationXY;
		float f5 = ActiveRenderInfo.rotationXZ;

		tess.setColorRGBA_F(1F, 1F, 1F, alpha);

		tess.addVertexWithUV((double) (posX - f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ - f2 * scale - f4 * scale), 1, 1);
		tess.addVertexWithUV((double) (posX - f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ - f2 * scale + f4 * scale), 1, 0);
		tess.addVertexWithUV((double) (posX + f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ + f2 * scale + f4 * scale), 0, 0);
		tess.addVertexWithUV((double) (posX + f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ + f2 * scale - f4 * scale), 0, 1);

	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		return null;
	}
}
