package com.hbm.render.entity.mob;

import org.lwjgl.opengl.GL11;

import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.render.model.ModelFakePerson;

import cpw.mods.fml.common.registry.VillagerRegistry;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

public final class RenderFakePerson extends RenderLiving {

	private static final ResourceLocation DEFAULT = new ResourceLocation("textures/entity/villager/villager.png");
	private static final ResourceLocation[] PROFESSIONS = {
		new ResourceLocation("textures/entity/villager/farmer.png"),
		new ResourceLocation("textures/entity/villager/librarian.png"),
		new ResourceLocation("textures/entity/villager/priest.png"),
		new ResourceLocation("textures/entity/villager/smith.png"),
		new ResourceLocation("textures/entity/villager/butcher.png")
	};

	public RenderFakePerson() { super(new ModelFakePerson(), 0.5F); }

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		int profession = ((EntityFakePerson) entity).getProfession();
		return profession >= 0 && profession < PROFESSIONS.length ? PROFESSIONS[profession]
				: VillagerRegistry.getVillagerSkin(profession, DEFAULT);
	}

	@Override
	protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
		float scale = entity.isChild() ? 0.46875F : 0.9375F;
		shadowSize = entity.isChild() ? 0.25F : 0.5F;
		GL11.glScalef(scale, scale, scale);
	}

	@Override
	protected void rotateCorpse(EntityLivingBase entity, float age, float yaw, float partialTicks) {
		if(((EntityFakePerson) entity).isRecovering()) {
			GL11.glRotatef(180F - yaw, 0F, 1F, 0F);
			GL11.glRotatef(90F, 0F, 0F, 1F);
		} else super.rotateCorpse(entity, age, yaw, partialTicks);
	}
}
