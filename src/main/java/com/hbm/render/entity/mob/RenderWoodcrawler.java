package com.hbm.render.entity.mob;

import com.hbm.lib.RefStrings;
import com.hbm.render.model.ModelWoodcrawler;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

public final class RenderWoodcrawler extends RenderLiving {

	private static final ResourceLocation TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/entity/woodcrawler.png");

	public RenderWoodcrawler() { super(new ModelWoodcrawler(), 0.6F); }

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }
}
