package com.hbm.handler.disease;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.Tessellator;
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
	private static final Field CAT_HEAD = ReflectionHelper.findField(ModelOcelot.class, "ocelotHead", "field_78156_g");
	private static final Field HORSE_HEAD = ReflectionHelper.findField(ModelHorse.class, "head", "field_110709_a");
	private static final Field CHILD_HEAD_Y = ReflectionHelper.findField(ModelQuadruped.class, "field_78145_g");
	private static final Field CHILD_HEAD_Z = ReflectionHelper.findField(ModelQuadruped.class, "field_78151_h");
	private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/blocks/fleshv2.png");
	private final ModelRenderer roots, bulbs, sprouts, lesion, crown;
	private final Deque<EntityLivingBase> postures = new ArrayDeque<EntityLivingBase>();
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
		lesion = new ModelRenderer(base, 0, 0);
		lesion.addBox(-2, -2, -0.2F, 4, 4, 1);
		crown = new ModelRenderer(base, 0, 0);
		crown.addBox(-3.5F, -3, -0.5F, 7, 6, 3);
		branch(base, crown, -2.5F, -1.5F, 1, -0.85F, 3, 7, 3);
		branch(base, crown, 2, -2, 1.5F, 0.45F, 2, 10, 2);
		branch(base, crown, 3, 1, 2, 1.25F, 1, 8, 1);
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

	private boolean visible(EntityLivingBase host) {
		Minecraft mc = Minecraft.getMinecraft();
		return host.worldObj.isRemote && host.worldObj == mc.theWorld && host.isEntityAlive() && !host.isInvisible()
				&& mc.renderViewEntity != null && mc.renderViewEntity.getDistanceSqToEntity(host) <= 2304D
				&& !(host instanceof EntityPlayer && ((EntityPlayer) host).isPlayerSleeping());
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void posture(RenderLivingEvent.Pre event) throws IllegalAccessException {
		DiseaseHost data = DiseaseHost.get(event.entity);
		if(data.deepRootVisualStage == 0 || !visible(event.entity) || body((ModelBase) MODEL.get(event.renderer)) == null) return;
		// A render-only posture around the host's feet; never touch authoritative pose, aim or hitboxes.
		GL11.glPushMatrix();
		postures.push(event.entity);
		float age = event.entity.ticksExisted + partialTicks;
		float severity = data.deepRootVisualStage >= 4 ? 1F : data.deepRootVisualStage >= 2 ? 0.35F : 0F;
		float twitch = data.deepRootSpasm ? MathHelper.sin(age * 2F) * 2F : 0F;
		GL11.glTranslated(event.x, event.y, event.z);
		GL11.glRotatef(-bodyYaw(event.entity), 0, 1, 0);
		GL11.glRotatef(severity * 16F + twitch, 1, 0, 0);
		GL11.glRotatef(severity * (-6F + MathHelper.sin(age * 0.035F) * 2F) + twitch, 0, 0, 1);
		GL11.glScalef(1F + severity * 0.07F, 1F - severity * 0.08F, 1F + severity * 0.04F);
		GL11.glRotatef(bodyYaw(event.entity), 0, 1, 0);
		GL11.glTranslated(-event.x, -event.y, -event.z);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void restorePosture(RenderLivingEvent.Post event) {
		if(!postures.isEmpty() && postures.peek() == event.entity) { postures.pop(); GL11.glPopMatrix(); }
	}

	@SubscribeEvent public void rendered(RenderLivingEvent.Post event) throws IllegalAccessException {
		EntityLivingBase host = event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		DiseaseHost data = DiseaseHost.get(host);
		int stage = data.deepRootVisualStage;
		if(stage == 0 || !visible(host)) return;
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
			GL11.glPushMatrix();
			childTransform(host, model, false);
			body.postRender(0.0625F); // Reuse the actual animated torso, including sitting/rearing/sneaking poses.
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glDisable(GL11.GL_CULL_FACE);
			mc.getTextureManager().bindTexture(TEXTURE);
			GL11.glColor4f(0.78F, 0.22F, 0.19F, 1F);
			for(int i = 0; i < 3; i++) {
				GL11.glPushMatrix();
				float x = i == 0 ? box.posX1 : i == 1 ? box.posX2 : (box.posX1 + box.posX2) * 0.5F;
				float y = box.posY1 + (box.posY2 - box.posY1) * (i == 1 ? 0.65F : 0.35F);
				float z = i == 2 ? box.posZ2 : (box.posZ1 + box.posZ2) * 0.5F;
				GL11.glTranslatef(x / 16F, y / 16F, z / 16F);
				GL11.glRotatef(i == 0 ? -90 : i == 1 ? 90 : 0, 0, 1, 0);
				if(data.deepRootSpasm) GL11.glRotatef(MathHelper.sin((host.ticksExisted + partialTicks) * 2F) * 12F, 0, 0, 1);
				if(stage == 1) {
					// Flat, muted reddish lesions leave the early host's silhouette intact.
					GL11.glColor4f(0.72F, 0.42F, 0.38F, 1F);
					GL11.glScalef(0.7F, 0.7F, 0.04F);
					lesion.render(0.0625F);
				} else {
					float pulse = 1F + MathHelper.sin((host.ticksExisted + partialTicks) * 0.075F + i * 2F) * 0.035F;
					float size = stage < 4 ? 0.55F : stage == 4 ? 1.15F : 1.6F;
					GL11.glScalef(size * pulse, size, size / pulse);
					GL11.glRotatef(MathHelper.sin((host.ticksExisted + partialTicks) * 0.045F + i) * 5F, 0, 0, 1);
					roots.render(0.0625F);
					bulbs.render(0.0625F);
				}
				if(stage >= 5) {
					float growth = stage == 6 ? 1.25F : 1F;
					GL11.glScalef(growth, growth, growth);
					sprouts.render(0.0625F);
				}
				GL11.glPopMatrix();
			}
			GL11.glPopMatrix();
			ModelRenderer head = head(model);
			if(head != null && !head.cubeList.isEmpty() && head.showModel && !head.isHidden) {
				GL11.glPushMatrix();
				childTransform(host, model, true);
				head.postRender(0.0625F);
				ModelBox headBox = (ModelBox) head.cubeList.get(0);
				GL11.glTranslatef((headBox.posX1 + (headBox.posX2 - headBox.posX1) * 0.3F) / 16F,
						(headBox.posY1 + (headBox.posY2 - headBox.posY1) * 0.45F) / 16F, (headBox.posZ1 - 0.2F) / 16F);
				float headSize = MathHelper.clamp_float((headBox.posX2 - headBox.posX1) / 8F, 0.5F, 1.2F);
				GL11.glScalef(headSize, headSize, headSize);
				GL11.glColor4f(0.78F, 0.22F, 0.19F, 1F);
				if(stage < 4) {
					float size = stage == 1 ? 0.6F : 0.9F;
					GL11.glScalef(size, size, stage == 1 ? 0.04F : 0.5F);
					if(stage == 1) lesion.render(0.0625F); else bulbs.render(0.0625F);
				} else {
					crown.render(0.0625F);
					GL11.glTranslatef(0, 0, -0.11F);
					renderEye(host, head, data);
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

	private static ModelRenderer head(ModelBase model) throws IllegalAccessException {
		if(model instanceof ModelBiped) return ((ModelBiped) model).bipedHead;
		if(model instanceof ModelVillager) return ((ModelVillager) model).villagerHead;
		if(model instanceof ModelQuadruped) return ((ModelQuadruped) model).head;
		if(model instanceof ModelWolf) return ((ModelWolf) model).wolfHeadMain;
		if(model instanceof ModelOcelot) return (ModelRenderer) CAT_HEAD.get(model);
		if(model instanceof ModelHorse) return (ModelRenderer) HORSE_HEAD.get(model);
		if(model instanceof ModelChicken) return ((ModelChicken) model).head;
		return null;
	}

	private static void childTransform(EntityLivingBase host, ModelBase model, boolean head) throws IllegalAccessException {
		if(host instanceof EntityHorse && host.isChild()) {
			EntityHorse horse = (EntityHorse) host;
			float growth = horse.getHorseSize();
			float scale = head ? 0.5F + growth * growth * 0.5F : growth;
			GL11.glScalef(scale, scale, scale);
			float eating = head ? horse.getGrassEatingAmount(0F) : 0F;
			GL11.glTranslatef(0, (1.35F - 0.45F * eating) * (1F - growth), 0.15F * (1F - growth) * eating);
		} else if(model.isChild && !(model instanceof ModelVillager)) {
			if(!head) {
				GL11.glScalef(0.5F, 0.5F, 0.5F);
				GL11.glTranslatef(0, 1.5F, 0);
			} else if(model instanceof ModelQuadruped) {
				GL11.glTranslatef(0, CHILD_HEAD_Y.getFloat(model) / 16F, CHILD_HEAD_Z.getFloat(model) / 16F);
			} else if(model instanceof ModelBiped || model instanceof ModelOcelot) {
				GL11.glScalef(0.75F, 0.75F, 0.75F);
				GL11.glTranslatef(0, model instanceof ModelBiped ? 1F : 0.625F, model instanceof ModelBiped ? 0 : 0.25F);
			} else GL11.glTranslatef(0, 0.3125F, 0.125F); // Native wolf/chicken head translation.
		}
	}

	private void renderEye(EntityLivingBase host, ModelRenderer head, DiseaseHost data) {
		if(data.deepRootEyeTick != host.ticksExisted) {
			boolean first = data.deepRootEyeTick == Integer.MIN_VALUE;
			data.deepRootEyeTick = host.ticksExisted;
			data.deepRootPrevEyeYaw = data.deepRootEyeYaw;
			data.deepRootPrevEyePitch = data.deepRootEyePitch;
			// Only rendered eyes search the small player list, once every two seconds. No world entity scans.
			int focus = (host.ticksExisted + host.getEntityId() * 17) % 240;
			if(first || focus % 40 == 0) {
				data.deepRootEyeTarget = -1;
				double nearest = 144D;
				if(focus < 190) for(Object entry : host.worldObj.playerEntities) {
					EntityPlayer player = (EntityPlayer) entry;
					double distance = host.getDistanceSqToEntity(player);
					if(player != host && player.isEntityAlive() && !player.isInvisible() && distance < nearest) {
						nearest = distance; data.deepRootEyeTarget = player.getEntityId();
					}
				}
			}
			float yaw = MathHelper.sin(host.ticksExisted * 0.017F + host.getEntityId()) * 12F;
			float pitch = MathHelper.sin(host.ticksExisted * 0.023F) * 7F;
			net.minecraft.entity.Entity target = host.worldObj.getEntityByID(data.deepRootEyeTarget);
			if(focus < 190 && target instanceof EntityPlayer && target.isEntityAlive() && host.getDistanceSqToEntity(target) <= 196D) {
				double dx = target.posX - host.posX, dz = target.posZ - host.posZ;
				double dy = target.posY + target.getEyeHeight() - host.posY - host.height * 0.85D;
				double bodyAngle = bodyYaw(host) * Math.PI / 180D;
				// World direction -> host model -> animated head local space, independent of host head aim.
				double x = Math.cos(bodyAngle) * dx + Math.sin(bodyAngle) * dz;
				double y = -dy, z = Math.sin(bodyAngle) * dx - Math.cos(bodyAngle) * dz;
				double a = x * Math.cos(head.rotateAngleZ) + y * Math.sin(head.rotateAngleZ);
				y = -x * Math.sin(head.rotateAngleZ) + y * Math.cos(head.rotateAngleZ); x = a;
				a = x * Math.cos(head.rotateAngleY) - z * Math.sin(head.rotateAngleY);
				z = x * Math.sin(head.rotateAngleY) + z * Math.cos(head.rotateAngleY); x = a;
				a = y * Math.cos(head.rotateAngleX) + z * Math.sin(head.rotateAngleX);
				z = -y * Math.sin(head.rotateAngleX) + z * Math.cos(head.rotateAngleX); y = a;
				yaw = MathHelper.clamp_float((float) (Math.atan2(-x, -z) * 180D / Math.PI), -48F, 48F);
				pitch = MathHelper.clamp_float((float) (Math.atan2(y, Math.sqrt(x * x + z * z)) * 180D / Math.PI), -30F, 30F);
			}
			data.deepRootEyeYaw += MathHelper.clamp_float((yaw - data.deepRootEyeYaw) * 0.14F, -2.5F, 2.5F);
			data.deepRootEyePitch += MathHelper.clamp_float((pitch - data.deepRootEyePitch) * 0.14F, -1.8F, 1.8F);
		}
		GL11.glPushMatrix();
		GL11.glRotatef(data.deepRootPrevEyeYaw + (data.deepRootEyeYaw - data.deepRootPrevEyeYaw) * partialTicks, 0, 1, 0);
		GL11.glRotatef(data.deepRootPrevEyePitch + (data.deepRootEyePitch - data.deepRootPrevEyePitch) * partialTicks, 1, 0, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		orb(0.145F, 0.14F, 0.12F, 0.9F, 0.7F, 0.65F);
		GL11.glTranslatef(0, 0, -0.105F);
		orb(0.078F, 0.08F, 0.025F, 0.7F, 0.29F, 0.08F);
		GL11.glTranslatef(0, 0, -0.024F);
		orb(0.025F, 0.058F, 0.006F, 0.06F, 0.015F, 0.01F);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();
	}

	/** Small fixed-detail organic eye geometry; no texture changes or extra entities. */
	private static void orb(float rx, float ry, float rz, float red, float green, float blue) {
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setColorOpaque_F(red, green, blue);
		for(int row = 0; row < 6; row++) for(int column = 0; column < 12; column++) {
			for(int corner = 0; corner < 4; corner++) {
				double lat = ((row + (corner == 1 || corner == 2 ? 1 : 0)) / 6D - 0.5D) * Math.PI;
				double lon = (column + (corner >= 2 ? 1 : 0)) * Math.PI / 6D;
				float x = (float) (Math.cos(lat) * Math.sin(lon)), y = (float) Math.sin(lat), z = (float) (Math.cos(lat) * Math.cos(lon));
				tess.setNormal(x, y, z);
				tess.addVertex(x * rx, y * ry, z * rz);
			}
		}
		tess.draw();
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
