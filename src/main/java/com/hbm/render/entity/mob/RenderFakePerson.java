package com.hbm.render.entity.mob;

import org.lwjgl.opengl.GL11;

import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.render.model.ModelFakePerson;

import cpw.mods.fml.common.registry.VillagerRegistry;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;

public final class RenderFakePerson extends RenderLiving {
	private static final ResourceLocation PLAYER_DEFAULT = new ResourceLocation("textures/entity/steve.png");
	private static final class Appearance implements SkinManager.SkinAvailableCallback {
		volatile ResourceLocation skin = PLAYER_DEFAULT;
		@Override public void func_152121_a(Type type, ResourceLocation texture) { if(type == Type.SKIN) skin = texture; }
	}
	private final java.util.Map<String, Appearance> appearances = new java.util.LinkedHashMap<String, Appearance>(128, 0.75F, true) {
		@Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Appearance> entry) { return size() > 128; }
	};

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
		EntityFakePerson mob = (EntityFakePerson) entity;
		if(mob.isPlayerMimic()) {
			GameProfile profile = mob.mimicProfile();
			String key = mob.mimicAppearanceKey();
			Appearance appearance = appearances.get(key);
			if(appearance == null) {
				appearance = new Appearance(); appearances.put(key, appearance);
				Minecraft.getMinecraft().func_152342_ad().func_152790_a(profile, appearance, true);
			}
			return appearance.skin;
		}
		int profession = mob.getProfession();
		return profession >= 0 && profession < PROFESSIONS.length ? PROFESSIONS[profession]
				: VillagerRegistry.getVillagerSkin(profession, DEFAULT);
	}

	@Override
	protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
		float scale = ((EntityFakePerson) entity).isPlayerMimic() ? 1F : entity.isChild() ? 0.46875F : 0.9375F;
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
