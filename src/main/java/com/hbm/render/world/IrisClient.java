package com.hbm.render.world;

import com.hbm.dim.WorldProviderCelestial;
import com.hbm.world.gemini.GeminiIris;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;

/** Main-client-thread snapshots and bounded visual atmosphere; never writes world progression/light/weather. */
@SideOnly(Side.CLIENT)
public final class IrisClient {
    private static WorldClient owner;
    private static NBTTagCompound state = new NBTTagCompound();
    private static long received;

    public static void accept(WorldClient world, NBTTagCompound snapshot) {
        if(world.provider.dimensionId != 0) return;
        owner = world;
        state = snapshot;
        received = world.getTotalWorldTime();
        if(state.getBoolean("sound") && state.getBoolean("effects") && Minecraft.getMinecraft().thePlayer != null) {
            net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            world.playSound(player.posX, player.posY + 12, player.posZ, "hbm:gemini.iris_rumble",
                    0.22F + state.getFloat("pressure") * 0.16F, 0.85F, false);
        }
    }

    private static boolean valid(WorldClient world) { return world != null && world == owner && world.provider.dimensionId == 0; }
    static double clock(WorldClient world, float partial) {
        return state.getLong("clock") + Math.max(0, Math.min(40, world.getTotalWorldTime() - received)) + partial;
    }
    private static float remaining(WorldClient world, String key, float partial) {
        return Math.max(0F, state.getInteger(key) - (float)Math.max(0, Math.min(40, world.getTotalWorldTime() - received)) / 20F - partial / 20F);
    }
    static float envelope(WorldClient world, String key, String lengthKey, float partial) {
        float left = remaining(world, key, partial), length = state.getInteger(lengthKey);
        if(length <= 0 || left <= 0) return 0;
        float ramp = Math.min(15F, length * 0.25F);
        float t = Math.min(1F, Math.min(left, length - left) / ramp);
        return t * t * (3F - 2F * t);
    }
    static float visibility(WorldClient world, float partial) {
        if(!valid(world)) return 0;
        int override = state.getInteger("visibility");
        if(override != 1 && Minecraft.getMinecraft().gameSettings.renderDistanceChunks < state.getInteger("minRenderChunks")) return 0;
        return GeminiIris.visibility(state.getInteger("stage"), override, state.getBoolean("rendering"),
                state.getBoolean("active"), state.getBoolean("progression"), GeminiIris.night(world, partial),
                world.getRainStrength(partial), Math.min(1F, remaining(world, "reveal", partial) / 10F));
    }
    static NBTTagCompound state() { return state; }
    static float atmosphere(WorldClient world, float partial) {
        if(!valid(world) || !state.getBoolean("effects")) return 0;
        int phase = state.getInteger("phase");
        float pulse = envelope(world, "pulse", "pulseLength", partial);
        return state.getFloat("intensity") * ((Math.max(0, phase - 2) * 0.06F)
                + pulse * (0.16F + phase * 0.045F) * (1F + state.getFloat("pressure") * 0.6F));
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(event.world == owner) { owner = null; state = new NBTTagCompound(); }
    }

    /** Non-celestial providers retain their existing handler (or the native vanilla sky). */
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if(world == null) { owner = null; state = new NBTTagCompound(); return; }
        if(world.provider.dimensionId != 0 || world.provider instanceof WorldProviderCelestial) return;
        IRenderHandler parent = world.provider.getSkyRenderer();
        if(!(parent instanceof IrisSkyRenderer)) world.provider.setSkyRenderer(new IrisSkyRenderer(parent));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fog(EntityViewRenderEvent.FogColors event) {
        float strength = atmosphere(Minecraft.getMinecraft().theWorld, (float)event.renderPartialTicks);
        if(strength <= 0) return;
        event.red *= 1F - strength * 0.35F;
        event.green *= 1F - strength;
        event.blue *= 1F - strength * 0.9F;
    }

    /** A gentle scene-only ambient-light filter before HUD rendering; no block light updates. */
    @SubscribeEvent public void ambience(RenderGameOverlayEvent.Pre event) {
        if(event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        float amount = atmosphere(mc.theWorld, event.partialTicks);
        if(amount <= 0 || mc.thePlayer == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_SRC_COLOR);
            GL11.glColor4f(1F - amount * 0.35F, 1F - amount * 0.65F, 1F - amount * 0.6F, 1F);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertex(0, event.resolution.getScaledHeight(), -90);
            t.addVertex(event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), -90);
            t.addVertex(event.resolution.getScaledWidth(), 0, -90); t.addVertex(0, 0, -90);
            t.draw();
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
}
