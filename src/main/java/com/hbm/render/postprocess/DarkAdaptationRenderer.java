package com.hbm.render.postprocess;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import com.hbm.config.ClientConfig;
import com.hbm.items.armor.ArmorFSB;
import com.hbm.main.MainRegistry;
import com.hbm.main.ModEventHandlerClient;
import com.hbm.util.Compat;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * Copies the completed world into a private texture and composites it back at
 * RenderGameOverlayEvent.Pre(ALL). GuiIngameForge fires this boundary after the
 * world/hand and before every HUD element, unlike RenderWorldLastEvent (which is
 * still inside EntityRenderer's world pass). Thus HUDs, scopes, chat and GUIs
 * never enter the source image.
 */
public final class DarkAdaptationRenderer implements IResourceManagerReloadListener {
	private final DarkAdaptationState state = new DarkAdaptationState();
	private final DarkAdaptationShader shader = new DarkAdaptationShader();
	private int sourceTexture;
	private int textureWidth;
	private int textureHeight;
	private long lastNanos;
	private World lastWorld;
	private int exposureFrames;
	private boolean reloadPending = true;
	private boolean warned;
	private final boolean angelica = Compat.isModLoaded(Compat.MOD_ANG);
	private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4);

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void beforeHud(RenderGameOverlayEvent.Pre event) {
		if(event.type != RenderGameOverlayEvent.ElementType.ALL) return;
		Minecraft mc = Minecraft.getMinecraft();
		if(mc.theWorld == null || mc.thePlayer == null) { releaseWorld(); return; }
		if(lastWorld == null) { lastWorld = mc.theWorld; lastNanos = System.nanoTime(); }
		else if(lastWorld != mc.theWorld) { // retain physiology across dimension travel, but recreate GL capture state
			lastWorld = mc.theWorld; deleteTexture(); lastNanos = System.nanoTime();
		}
		if(mc.isGamePaused()) { lastNanos = System.nanoTime(); return; }

		long now = System.nanoTime();
		float delta = lastNanos == 0L ? 0F : (now - lastNanos) * 0.000000001F;
		lastNanos = now;
		if(++exposureFrames >= (quality() == 2 ? 3 : 5)) {
			exposureFrames = 0;
			state.update(measureWorldExposure(mc.thePlayer), delta, nuclearFlashActive());
		} else {
			state.update(state.getExposure(), delta, nuclearFlashActive());
		}

		if(!ClientConfig.DARK_ADAPTATION_ENABLED.get() || quality() == 0 || suppressed(mc.thePlayer)) return;
		float configuredStrength = clamp(ClientConfig.DARK_ADAPTATION_STRENGTH.get(), 0F, 2F);
		if(configuredStrength < 0.001F || state.getEffectiveAdaptation() < 0.002F || !OpenGlHelper.isFramebufferEnabled()) return;
		if(reloadPending) { reloadPending = false; if(!shader.load()) return; }
		if(!shader.isLoaded()) return;
		try { composite(mc, configuredStrength); }
		catch(Throwable failure) { disableOnce("Dark adaptation post-process disabled safely: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); }
	}

	private void composite(Minecraft mc, float configuredStrength) {
		int width = mc.displayWidth, height = mc.displayHeight;
		if(width <= 0 || height <= 0) return;
		int oldFramebuffer = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
		int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		viewportBuffer.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
		int viewportX = viewportBuffer.get(0), viewportY = viewportBuffer.get(1), viewportW = viewportBuffer.get(2), viewportH = viewportBuffer.get(3);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			ensureTexture(width, height);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
			// The copy is a distinct texture: the active/Angelica-owned color attachment is never sampled in place.
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
			GL11.glViewport(0, 0, width, height);
			GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_LIGHTING); GL11.glDepthMask(false); GL11.glColorMask(true, true, true, true);
			GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(0, 1, 0, 1, -1, 1);
			GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glColor4f(1F, 1F, 1F, 1F);
			shader.use(width, height, state, configuredStrength,
				clamp(ClientConfig.DARK_ADAPTATION_NOISE.get(), 0F, 0.05F),
				clamp(ClientConfig.DARK_ADAPTATION_CENTER_LOSS.get(), 0F, 0.35F), quality());
			drawQuad();
			GL20.glUseProgram(oldProgram);
			GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
		} finally {
			// Attribute state excludes FBO/program; restore both explicitly for Angelica and later HUD renderers.
			GL20.glUseProgram(oldProgram); EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, oldFramebuffer);
			GL11.glPopAttrib(); GL11.glViewport(viewportX, viewportY, viewportW, viewportH);
			GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture); GL13.glActiveTexture(oldActiveTexture);
			GL11.glMatrixMode(oldMatrixMode);
		}
	}

	private static void drawQuad() {
		Tessellator t = Tessellator.instance; t.startDrawingQuads();
		t.addVertexWithUV(0, 0, 0, 0, 0); t.addVertexWithUV(1, 0, 0, 1, 0);
		t.addVertexWithUV(1, 1, 0, 1, 1); t.addVertexWithUV(0, 1, 0, 0, 1); t.draw();
	}

	private void ensureTexture(int width, int height) {
		if(sourceTexture == 0) sourceTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		if(width != textureWidth || height != textureHeight) {
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
			textureWidth = width; textureHeight = height;
		}
	}

	/**
	 * Angelica and legacy shader pipelines cannot reliably downsample/read their
	 * private render targets here. This intentionally uses a no-readback fallback:
	 * seven eye-neighbour block/sky samples plus sun and weather. It never samples
	 * GUI pixels and never performs a full-frame CPU readback.
	 */
	private float measureWorldExposure(EntityPlayer player) {
		int x = MathHelper.floor_double(player.posX), y = MathHelper.floor_double(player.posY + player.getEyeHeight()), z = MathHelper.floor_double(player.posZ);
		int[][] offsets = {{0,0,0},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
		float sum = 0F, peak = 0F;
		for(int[] o : offsets) {
			int block = player.worldObj.getSavedLightValue(EnumSkyBlock.Block, x + o[0], y + o[1], z + o[2]);
			int sky = player.worldObj.getSavedLightValue(EnumSkyBlock.Sky, x + o[0], y + o[1], z + o[2]);
			float celestial = player.worldObj.getSunBrightness(1F) * (1F - player.worldObj.getRainStrength(1F) * 0.45F);
			float sample = Math.max(block / 15F, sky / 15F * celestial); sum += sample; peak = Math.max(peak, sample);
		}
		return clamp(Math.max(sum / offsets.length, peak * 0.62F), 0F, 1F);
	}

	private boolean suppressed(EntityPlayer player) {
		if(player.isPotionActive(Potion.nightVision) || player.isPotionActive(Potion.blindness)) return true;
		ItemStack chest = player.inventory.armorInventory[2];
		return chest != null && chest.getItem() instanceof ArmorFSB && ((ArmorFSB)chest.getItem()).thermal;
	}
	private boolean nuclearFlashActive() { return System.currentTimeMillis() < ModEventHandlerClient.flashTimestamp + ModEventHandlerClient.flashDuration; }
	private int quality() { return Math.max(0, Math.min(2, ClientConfig.DARK_ADAPTATION_QUALITY.get())); }
	private static float clamp(float v, float lo, float hi) { return DarkAdaptationState.clamp(v, lo, hi); }

	@SubscribeEvent
	public void debug(RenderGameOverlayEvent.Post event) {
		if(event.type != RenderGameOverlayEvent.ElementType.ALL || !ClientConfig.DARK_ADAPTATION_DEBUG.get()) return;
		Minecraft mc = Minecraft.getMinecraft(); if(mc.theWorld == null) return;
		String line = String.format("Eye exp %.3f  cone %.3f  rod %.3f  effective %.3f", state.getExposure(), state.getConeAdaptation(), state.getRodAdaptation(), state.getEffectiveAdaptation());
		mc.fontRenderer.drawStringWithShadow(line, 4, 4, 0xB0B0B0);
		mc.fontRenderer.drawStringWithShadow("path light-sample + framebuffer  shader/FBO " + shader.isLoaded() + "/" + OpenGlHelper.isFramebufferEnabled() + "  Angelica " + angelica, 4, 14, 0x909090);
	}

	private void disableOnce(String message) { shader.destroy(); if(!warned) { warned = true; MainRegistry.logger.warn(message); } }
	private void releaseWorld() { if(lastWorld != null) { state.reset(); lastWorld = null; } lastNanos = 0L; deleteTexture(); }
	private void deleteTexture() { if(sourceTexture != 0) GL11.glDeleteTextures(sourceTexture); sourceTexture = textureWidth = textureHeight = 0; }
	@Override public void onResourceManagerReload(IResourceManager manager) { shader.destroy(); reloadPending = true; warned = false; }
}
