package com.hbm.render.entity.mob;

import org.lwjgl.opengl.GL11;

import com.hbm.entity.mob.gemini.EntityWoodcrawlerCocoon;
import com.hbm.lib.RefStrings;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** Rooted, ribbed tissue enclosing a humanoid-sized bulge. Uses the existing organic texture. */
public final class RenderWoodcrawlerCocoon extends Render {

	private static final ResourceLocation TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/blocks/fleshv2.png");
	private final ModelBase model = new ModelBase() { };
	private final ModelRenderer body, ribs, roots;

	public RenderWoodcrawlerCocoon() {
		model.textureWidth = model.textureHeight = 16;
		body = new ModelRenderer(model, 0, 0);
		body.addBox(-5, -23, -5, 10, 23, 10);
		body.addBox(-4, -28, -4, 8, 7, 8);
		ribs = new ModelRenderer(model, 0, 0);
		for(int i = 0; i < 5; i++) ribs.addBox(-7, -24 + i * 5, -6, 14, 2, 12);
		roots = new ModelRenderer(model, 0, 0);
		roots.addBox(-9, -3, -2, 18, 3, 4);
		roots.addBox(-2, -2, -9, 4, 2, 18);
		shadowSize = 0.6F;
	}

	@Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
		EntityWoodcrawlerCocoon cocoon = (EntityWoodcrawlerCocoon) entity;
		GL11.glPushMatrix();
		GL11.glTranslated(x, y, z);
		GL11.glRotatef(-yaw, 0, 1, 0);
		GL11.glScalef(1, -1, 1);
		bindEntityTexture(entity);
		GL11.glColor3f(0.55F, 0.65F, 0.35F);
		roots.render(0.0625F);
		float pulse = cocoon.isReleasing() ? 0.8F : 1F + (float) Math.sin((entity.ticksExisted + partial) * 0.12F) * 0.025F;
		GL11.glScalef(pulse, cocoon.isReleasing() ? 0.55F : 1, pulse);
		GL11.glColor3f(0.75F, 0.85F, 0.5F);
		body.render(0.0625F);
		GL11.glColor3f(0.4F, 0.45F, 0.25F);
		ribs.render(0.0625F);
		GL11.glColor3f(1, 1, 1);
		GL11.glPopMatrix();
	}

	@Override protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }
}
