package com.hbm.handler.disease;

import java.lang.reflect.Field;

import com.hbm.particle.ParticleRabiesFroth;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.*;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

/** Emit only from rendered, synchronized hosts. Model pose is read after rendering, never changed. */
@SideOnly(Side.CLIENT)
public final class RabiesFrothClient {
	private static final Field MODEL = ReflectionHelper.findField(RendererLivingEntity.class, "mainModel", "field_77045_g");
	private static final Field CAT_HEAD = ReflectionHelper.findField(ModelOcelot.class, "ocelotHead", "field_78156_g");
	private static final Field BAT_HEAD = ReflectionHelper.findField(ModelBat.class, "batHead", "field_82895_a");
	private static final Field HORSE_HEAD = ReflectionHelper.findField(ModelHorse.class, "head", "field_110709_a");
	private static final Field HORSE_JAW = ReflectionHelper.findField(ModelHorse.class, "mouthBottom", "field_110708_c");
	private static final Field CHILD_Y = ReflectionHelper.findField(ModelQuadruped.class, "field_78145_g");
	private static final Field CHILD_Z = ReflectionHelper.findField(ModelQuadruped.class, "field_78151_h");
	private static final Vector3f X = new Vector3f(1, 0, 0), Y = new Vector3f(0, 1, 0), Z = new Vector3f(0, 0, 1);
	private float partialTicks;
	private int remaining;

	@SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
		if(event.phase == TickEvent.Phase.START) remaining = 4; // At most 80 short-lived flecks/second across all hosts.
	}

	@SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
		if(event.phase == TickEvent.Phase.START) partialTicks = event.renderTickTime;
	}

	@SubscribeEvent public void rendered(RenderLivingEvent.Post event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityLivingBase host = event.entity;
		if(host.worldObj != mc.theWorld || !host.worldObj.isRemote || !host.isEntityAlive() || host.isInvisible()
				|| !(host instanceof EntityAnimal || host instanceof EntityBat)) return;
		DiseaseHost data = DiseaseHost.get(host);
		if(!data.rabiesFrothing || host.ticksExisted < data.nextRabiesFroth) return;
		data.nextRabiesFroth = host.ticksExisted + 10 + host.getRNG().nextInt(11);
		if(remaining == 0 || mc.renderViewEntity == null || mc.gameSettings.particleSetting == 2
				|| mc.gameSettings.particleSetting == 1 && host.getRNG().nextInt(3) == 0
				|| mc.renderViewEntity.getDistanceSqToEntity(host) > 256D) return;
		try {
			ModelBase model = (ModelBase) MODEL.get(event.renderer);
			Matrix4f mouth = mouthTransform(host, model, partialTicks);
			if(mouth == null) return; // Unknown modded anatomy must not fall back to the torso/feet.
			Vector4f point = Matrix4f.transform(mouth, new Vector4f((host.getRNG().nextFloat() - 0.5F) * 0.8F, 0, 0, 1), null);
			Vector4f outward = Matrix4f.transform(mouth, new Vector4f(0, 0, -1, 0), null);
			float size = (float) Math.sqrt(outward.x * outward.x + outward.y * outward.y + outward.z * outward.z) * 16F;
			double x = host.lastTickPosX + (host.posX - host.lastTickPosX) * partialTicks + point.x;
			double y = host.lastTickPosY + (host.posY - host.lastTickPosY) * partialTicks + point.y;
			double z = host.lastTickPosZ + (host.posZ - host.lastTickPosZ) * partialTicks + point.z;
			if(mc.renderViewEntity.getDistanceSq(x, y, z) > 256D) return;
			mc.effectRenderer.addEffect(new ParticleRabiesFroth(host.worldObj, x, y, z,
					outward.x * 0.16D, outward.y * 0.16D - 0.002D, outward.z * 0.16D, size));
			remaining--;
		} catch(IllegalAccessException ex) {
			throw new IllegalStateException("Cannot read animal mouth pose", ex);
		}
	}

	private static Matrix4f mouthTransform(EntityLivingBase host, ModelBase model, float t) throws IllegalAccessException {
		ModelRenderer head;
		float mouthY, mouthZ;
		boolean wolf = model instanceof ModelWolf;
		if(wolf) { head = ((ModelWolf) model).wolfHeadMain; mouthY = 2.7F; mouthZ = -5.4F; }
		else if(model instanceof ModelOcelot) { head = (ModelRenderer) CAT_HEAD.get(model); mouthY = 1.8F; mouthZ = -4.4F; }
		else if(model instanceof ModelBat) { head = (ModelRenderer) BAT_HEAD.get(model); mouthY = 2F; mouthZ = -3.5F; }
		else if(model instanceof ModelHorse) { head = (ModelRenderer) HORSE_HEAD.get(model); mouthY = -5.2F; mouthZ = -7F; }
		else if(model instanceof ModelQuadruped) {
			head = ((ModelQuadruped) model).head;
			if(host instanceof EntityPig) { mouthY = 2.8F; mouthZ = -9.4F; }
			else if(host instanceof EntityCow) { mouthY = 3.6F; mouthZ = -6.4F; }
			else if(host instanceof EntitySheep) { mouthY = 1.8F; mouthZ = -6.4F; }
			else return null;
		} else return null;

		// Match RendererLivingEntity's body frame; head.rotateAngleY already contains the relative head yaw.
		float bodyYaw = angle(host.prevRenderYawOffset, host.renderYawOffset, t);
		if(host.ridingEntity instanceof EntityLivingBase) {
			EntityLivingBase mount = (EntityLivingBase) host.ridingEntity;
			bodyYaw = angle(mount.prevRenderYawOffset, mount.renderYawOffset, t);
			float difference = MathHelper.wrapAngleTo180_float(angle(host.prevRotationYawHead, host.rotationYawHead, t) - bodyYaw);
			float clamped = MathHelper.clamp_float(difference, -85F, 85F);
			bodyYaw = angle(host.prevRotationYawHead, host.rotationYawHead, t) - clamped;
			if(clamped * clamped > 2500F) bodyYaw += clamped * 0.2F;
		}
		Matrix4f matrix = new Matrix4f();
		matrix.rotate((float) Math.toRadians(180F - bodyYaw), Y);
		String name = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(host.getCommandSenderName());
		if("Dinnerbone".equals(name) || "Grumm".equals(name)) {
			translate(matrix, 0, host.height + 0.1F, 0);
			matrix.rotate((float) Math.PI, Z);
		}
		if(host instanceof EntityBat) {
			translate(matrix, 0, ((EntityBat) host).getIsBatHanging() ? -0.1F : MathHelper.cos((host.ticksExisted + t) * 0.3F) * 0.1F, 0);
		}
		float scale = host instanceof EntityBat ? 0.35F : host instanceof EntityOcelot && ((EntityOcelot) host).isTamed() ? 0.8F : 1F;
		if(host instanceof EntityHorse) {
			int type = ((EntityHorse) host).getHorseType();
			scale = type == 1 ? 0.87F : type == 2 ? 0.92F : 1F;
		}
		matrix.scale(new Vector3f(-scale, -scale, scale));
		translate(matrix, 0, -1.5078125F, 0);
		if(model.isChild) {
			if(wolf) translate(matrix, 0, 5F / 16F, 2F / 16F);
			else if(model instanceof ModelOcelot) {
				matrix.scale(new Vector3f(0.75F, 0.75F, 0.75F));
				translate(matrix, 0, 10F / 16F, 4F / 16F);
			} else if(model instanceof ModelQuadruped) {
				translate(matrix, 0, CHILD_Y.getFloat(model) / 16F, CHILD_Z.getFloat(model) / 16F);
			}
		}
		if(host instanceof EntityHorse && host.isChild()) {
			EntityHorse horse = (EntityHorse) host;
			float growth = horse.getHorseSize(), headScale = 0.5F + growth * growth * 0.5F;
			matrix.scale(new Vector3f(headScale, headScale, headScale));
			float grazing = horse.getGrassEatingAmount(0F); // ModelHorse.render uses the previous tick for this translation.
			translate(matrix, 0, (0.9F * grazing + 1.35F * (1F - grazing)) * (1F - growth), 0.15F * (1F - growth) * grazing);
		}
		// Convert model pixels to blocks, then reproduce ModelRenderer's rotation order.
		matrix.scale(new Vector3f(1F / 16F, 1F / 16F, 1F / 16F));
		pose(matrix, head, wolf);
		if(model instanceof ModelHorse) pose(matrix, (ModelRenderer) HORSE_JAW.get(model), false);
		translate(matrix, 0, mouthY, mouthZ);
		return matrix;
	}

	private static void pose(Matrix4f matrix, ModelRenderer part, boolean renderWithRotation) {
		translate(matrix, part.rotationPointX, part.rotationPointY, part.rotationPointZ);
		if(!renderWithRotation) matrix.rotate(part.rotateAngleZ, Z);
		matrix.rotate(part.rotateAngleY, Y);
		matrix.rotate(part.rotateAngleX, X);
		if(renderWithRotation) matrix.rotate(part.rotateAngleZ, Z);
	}

	private static void translate(Matrix4f matrix, float x, float y, float z) { matrix.translate(new Vector3f(x, y, z)); }
	private static float angle(float previous, float current, float t) { return previous + MathHelper.wrapAngleTo180_float(current - previous) * t; }
}
