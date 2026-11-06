package com.hbm.handler.disease;

import java.lang.reflect.Field;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Bounded native model overlays on rendered hosts only. No entity scans or decorative server entities. */
@SideOnly(Side.CLIENT)
public final class DeepRootClient {
	private static final Field MODEL = ReflectionHelper.findField(RendererLivingEntity.class, "mainModel", "field_77045_g");
	private static final Field CAT_BODY = ReflectionHelper.findField(ModelOcelot.class, "ocelotBody", "field_78162_h");
	private static final Field HORSE_BODY = ReflectionHelper.findField(ModelHorse.class, "body", "field_110715_k");
	private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/blocks/fleshv2.png");
	private final ModelRenderer roots, bulbs, sprouts;
	private float partialTicks;

	public DeepRootClient() {
		ModelBase base = new ModelBase() { };
		base.textureWidth = base.textureHeight = 16;
		roots = new ModelRenderer(base, 0, 0);
		roots.addBox(-0.5F, -5, -0.5F, 1, 9, 1);
		branch(base, roots, -0.1F, -2, 0, 0.65F, 1, 5, 1);
		branch(base, roots, 0, 1, 0, -0.85F, 1, 4, 1);
		bulbs = new ModelRenderer(base, 0, 0);
		bulbs.addBox(-2, -2, -1, 4, 4, 3);
		branch(base, bulbs, 1.2F, -1, 1, -0.3F, 2, 3, 2);
		sprouts = new ModelRenderer(base, 0, 0);
		sprouts.addBox(-0.5F, -11, 1, 1, 12, 1);
		branch(base, sprouts, 0, -5, 1, 0.6F, 1, 8, 1);
		branch(base, sprouts, 0, -8, 1, -0.8F, 1, 6, 1);
	}

	private static void branch(ModelBase base, ModelRenderer parent, float x, float y, float z, float angle, int w, int h, int d) {
		ModelRenderer branch = new ModelRenderer(base, 0, 0);
		branch.setRotationPoint(x, y, z);
		branch.rotateAngleZ = angle;
		branch.addBox(-w * 0.5F, -h, 0, w, h, d);
		parent.addChild(branch);
	}

	@SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
		if(event.phase == TickEvent.Phase.START) partialTicks = event.renderTickTime;
	}

	@SubscribeEvent public void rendered(RenderLivingEvent.Post event) throws IllegalAccessException {
		EntityLivingBase host = event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		DiseaseHost data = DiseaseHost.get(host);
		int stage = data.deepRootVisualStage;
		if(stage < 2 || !host.worldObj.isRemote || host.worldObj != mc.theWorld || !host.isEntityAlive()
				|| host.isInvisible() || mc.renderViewEntity == null || mc.renderViewEntity.getDistanceSqToEntity(host) > 2304D) return;
		// Early roots flicker intermittently; bulbs and later growths remain visible.
		if(stage < 4 && (host.ticksExisted + host.getEntityId() * 13) % 100 >= (data.deepRootSpasm ? 100 : 16)) return;
		if(host instanceof EntityPlayer && ((EntityPlayer) host).isPlayerSleeping()) return;
		ModelBase model = (ModelBase) MODEL.get(event.renderer);
		ModelRenderer body = body(model);
		if(body == null || body.cubeList.isEmpty() || !body.showModel || body.isHidden) return;
		ModelBox box = (ModelBox) body.cubeList.get(0);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glPushMatrix();
		try {
			GL11.glTranslated(event.x, event.y, event.z);
			GL11.glRotatef(180F - bodyYaw(host), 0, 1, 0);
			String name = EnumChatFormatting.getTextWithoutFormattingCodes(host.getCommandSenderName());
			if(("Dinnerbone".equals(name) || "Grumm".equals(name))
					&& (!(host instanceof EntityPlayer) || !((EntityPlayer) host).getHideCape())) {
				GL11.glTranslatef(0, host.height + 0.1F, 0);
				GL11.glRotatef(180, 0, 0, 1);
			}
			float scale = host instanceof EntityPlayer || host instanceof EntityVillager ? 0.9375F
					: host instanceof EntityOcelot && ((EntityOcelot) host).isTamed() ? 0.8F : 1F;
			if(host instanceof EntityVillager && host.isChild()) scale *= 0.5F;
			if(host instanceof EntityHorse) {
				int type = ((EntityHorse) host).getHorseType();
				scale = type == 1 ? 0.87F : type == 2 ? 0.92F : 1F;
			}
			GL11.glScalef(-scale, -scale, scale);
			GL11.glTranslatef(0, -1.5078125F, 0);
			if(host instanceof EntityHorse && host.isChild()) {
				float growth = ((EntityHorse) host).getHorseSize();
				GL11.glScalef(growth, growth, growth);
				GL11.glTranslatef(0, 1.35F * (1F - growth), 0);
			} else if(model.isChild && !(host instanceof EntityVillager)) {
				GL11.glScalef(0.5F, 0.5F, 0.5F);
				GL11.glTranslatef(0, 1.5F, 0);
			}
			body.postRender(0.0625F); // Reuse the actual animated torso, including sitting/rearing/sneaking poses.
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glDisable(GL11.GL_CULL_FACE);
			mc.getTextureManager().bindTexture(TEXTURE);
			GL11.glColor4f(0.65F, 0.53F, 0.38F, 1F);
			for(int i = 0; i < 3; i++) {
				GL11.glPushMatrix();
				float x = i == 0 ? box.posX1 : i == 1 ? box.posX2 : (box.posX1 + box.posX2) * 0.5F;
				float y = box.posY1 + (box.posY2 - box.posY1) * (i == 1 ? 0.65F : 0.35F);
				float z = i == 2 ? box.posZ2 : (box.posZ1 + box.posZ2) * 0.5F;
				GL11.glTranslatef(x / 16F, y / 16F, z / 16F);
				GL11.glRotatef(i == 0 ? -90 : i == 1 ? 90 : 0, 0, 1, 0);
				if(data.deepRootSpasm) GL11.glRotatef(MathHelper.sin((host.ticksExisted + partialTicks) * 2F) * 12F, 0, 0, 1);
				roots.render(0.0625F);
				if(stage >= 4) bulbs.render(0.0625F);
				if(stage >= 5) {
					float growth = stage == 6 ? 1.25F : 1F;
					GL11.glScalef(growth, growth, growth);
					sprouts.render(0.0625F);
				}
				GL11.glPopMatrix();
			}
		} finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private static ModelRenderer body(ModelBase model) throws IllegalAccessException {
		if(model instanceof ModelBiped) return ((ModelBiped) model).bipedBody;
		if(model instanceof ModelVillager) return ((ModelVillager) model).villagerBody;
		if(model instanceof ModelQuadruped) return ((ModelQuadruped) model).body;
		if(model instanceof ModelWolf) return ((ModelWolf) model).wolfBody;
		if(model instanceof ModelOcelot) return (ModelRenderer) CAT_BODY.get(model);
		if(model instanceof ModelHorse) return (ModelRenderer) HORSE_BODY.get(model);
		if(model instanceof ModelChicken) return ((ModelChicken) model).body;
		return null; // Unknown replacement models retain native rendering; no guessed attachment locations.
	}

	private float bodyYaw(EntityLivingBase host) {
		float yaw = angle(host.prevRenderYawOffset, host.renderYawOffset);
		if(host.ridingEntity instanceof EntityLivingBase) {
			EntityLivingBase mount = (EntityLivingBase) host.ridingEntity;
			float head = angle(host.prevRotationYawHead, host.rotationYawHead);
			float clamped = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(head - angle(mount.prevRenderYawOffset, mount.renderYawOffset)), -85F, 85F);
			yaw = head - clamped;
			if(clamped * clamped > 2500F) yaw += clamped * 0.2F;
		}
		return yaw;
	}
	private float angle(float previous, float current) {
		return previous + MathHelper.wrapAngleTo180_float(current - previous) * partialTicks;
	}
}
