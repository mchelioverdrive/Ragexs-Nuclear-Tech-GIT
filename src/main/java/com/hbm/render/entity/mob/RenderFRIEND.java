package com.hbm.render.entity.mob;

import com.hbm.entity.mob.EntityFRIEND;
import com.hbm.lib.RefStrings;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class RenderFRIEND extends RenderBiped {
	public RenderFRIEND() {
		super(new ModelBiped(0.0F), 0.5F, 1.0F);
	}

	@Override
	protected ResourceLocation getEntityTexture(EntityLiving entity) {
		return this.getEntityTexture((EntityFRIEND) entity);
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return this.getEntityTexture((EntityFRIEND) entity);
	}


	protected ResourceLocation getEntityTexture(EntityFRIEND entity) {
		return new ResourceLocation(RefStrings.MODID + ":textures/entity/nicefunguy.png");
	}

	@Override
	public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
		GL11.glPushMatrix();

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1F, 1F, 1F, 0.5F);

		// translate FIRST (world space offset)
		GL11.glTranslatef(0.0F, 0.6F, 0.0F);

		// then scale
		GL11.glScalef(0.7F, 1.6F, 0.7F);

		super.doRender(entity, x, y, z, yaw, partialTicks);

		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
	}



}
