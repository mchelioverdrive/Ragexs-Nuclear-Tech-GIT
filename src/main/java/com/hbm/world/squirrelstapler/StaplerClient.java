package com.hbm.world.squirrelstapler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import org.lwjgl.opengl.GL11;

public final class StaplerClient {
    private static World world;
    private static NBTTagCompound state;
    private static float gloom;
    private static boolean endingPending;

    public static void accept(World current, NBTTagCompound update) {
        if(current == null || !StaplerWorldType.isForest(current)) return;
        boolean wasPlaying = world == current && state != null && state.getInteger("Stage") != StaplerCampaign.COMPLETE;
        world = current;
        state = update.hasKey("Day") ? update : null;
        if(state == null) endingPending = false;
        else if(wasPlaying && stage() == StaplerCampaign.COMPLETE) endingPending = true;
    }

    private static int stage() { return state.getInteger("Stage"); }
    private static boolean active(World current) { return state != null && world == current; }
    private static boolean atmospheric(World current) { return active(current) && stage() != StaplerCampaign.COMPLETE; }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        if(world != client.theWorld) {
            world = null;
            state = null;
            gloom = 0F;
            endingPending = false;
        }
        if(state == null) return;
        int effects = state.getInteger("Effects");
        float target = stage() == StaplerCampaign.COMPLETE ? 0F : stage() == StaplerCampaign.ARRIVAL ? 1F
                : stage() == StaplerCampaign.COUNTDOWN ? 0.82F : Math.min(0.9F, (state.getInteger("Day") - 1) * 0.12F + ((effects & 1) != 0 ? 0.4F : 0F));
        gloom += (target - gloom) * 0.075F;
        if(endingPending && client.thePlayer != null && client.thePlayer.isEntityAlive()) {
            endingPending = false;
            client.displayGuiScreen(new EndingScreen());
        }
    }

    @SubscribeEvent public void hud(RenderGameOverlayEvent.Post event) {
        Minecraft client = Minecraft.getMinecraft();
        if(event.type != RenderGameOverlayEvent.ElementType.ALL || !active(client.theWorld)) return;
        client.fontRenderer.drawStringWithShadow("Day " + state.getInteger("Day") + " / " + state.getInteger("Days"), 9, 9, 0xF1E7CD);
        String status;
        if(stage() == StaplerCampaign.COMPLETE) status = "Campaign complete";
        else if(stage() == StaplerCampaign.COUNTDOWN) {
            int seconds = state.getInteger("Seconds");
            status = String.format(java.util.Locale.ROOT, "GOD IS COMING  %d:%02d", seconds / 60, seconds % 60);
        } else if(stage() == StaplerCampaign.ARRIVAL) status = "THE ARRIVAL";
        else {
            int normal = state.getInteger("Normal"), large = state.getInteger("Large");
            status = normal == 0 && large == 0 ? "Rack complete. Sleep at the cabin." : "Rack needs " + normal + " normal  /  " + large + " large";
        }
        client.fontRenderer.drawStringWithShadow(status, 9, 22, 0xDED5C5);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void fog(EntityViewRenderEvent.RenderFogEvent event) {
        if(!atmospheric(event.entity.worldObj) || event.fogMode < 0 || event.block == null || event.block.getMaterial().isLiquid()) return;
        int blocks = state.getInteger("Fog");
        if(blocks == 0) return;
        float distance = Math.min(event.farPlaneDistance, Math.max(14F, blocks * (1F - gloom * 0.73F)));
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, distance * (0.62F - gloom * 0.35F));
        GL11.glFogf(GL11.GL_FOG_END, distance);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void fogColor(EntityViewRenderEvent.FogColors event) {
        if(!atmospheric(event.entity.worldObj) || event.block == null || event.block.getMaterial().isLiquid()) return;
        // A low red component survives as the rest of the sky loses its color.
        event.red = event.red * (1F - gloom * 0.83F) + gloom * 0.075F;
        event.green *= 1F - gloom * 0.9F;
        event.blue *= 1F - gloom * 0.84F;
    }

    @SubscribeEvent public void sound(PlaySoundEvent17 event) {
        if(!atmospheric(Minecraft.getMinecraft().theWorld)) return;
        if(event.category == SoundCategory.MUSIC) event.result = null;
        boolean silent = (state.getInteger("Effects") & 2) != 0;
        if((silent || stage() != StaplerCampaign.HUNT) && (event.category == SoundCategory.ANIMALS
                || silent && event.category == SoundCategory.AMBIENT)) event.result = null;
    }

    @SubscribeEvent public void rack(RenderWorldLastEvent event) {
        Minecraft client = Minecraft.getMinecraft();
        if(!active(client.theWorld) || client.renderViewEntity == null) return;
        int y = state.getInteger("CabinY");
        int normal = Math.min(8, state.getInteger("NormalMounted")), large = Math.min(4, state.getInteger("LargeMounted"));
        EntityLivingBase viewer = client.renderViewEntity;
        if(y <= 0 || normal + large == 0 || viewer.getDistanceSq(StaplerForest.DISPLAY_X, y, StaplerForest.DISPLAY_Z) > 256D) return;
        IIcon icon = StaplerMode.trophy.getIconFromDamage(0);
        if(icon == null) return;
        double x = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * event.partialTicks;
        double ey = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * event.partialTicks;
        double z = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * event.partialTicks;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glTranslated(StaplerForest.DISPLAY_X + 0.5D - x, y + 1.65D - ey, StaplerForest.DISPLAY_Z + 1.012D - z);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            client.getTextureManager().bindTexture(TextureMap.locationItemsTexture);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for(int i = 0; i < normal; i++) tile(tessellator, icon, (i % 4 - 1.5D) * 0.55D, -0.18D - (i / 4) * 0.45D, 0.42D);
            for(int i = 0; i < large; i++) tile(tessellator, icon, (i - 1.5D) * 0.65D, 0.45D, 0.61D);
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void tile(Tessellator tessellator, IIcon icon, double x, double y, double size) {
        double half = size / 2D;
        tessellator.addVertexWithUV(x - half, y - half, 0D, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(x + half, y - half, 0D, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(x + half, y + half, 0D, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(x - half, y + half, 0D, icon.getMinU(), icon.getMinV());
    }

    private static final class EndingScreen extends GuiScreen {
        @Override public void initGui() {
            buttonList.add(new GuiButton(0, width / 2 - 80, height / 2 + 36, 160, 20, "Return to the forest"));
        }

        @Override protected void actionPerformed(GuiButton button) {
            if(button.id == 0) mc.displayGuiScreen(null);
        }

        @Override public boolean doesGuiPauseGame() { return false; }

        @Override public void drawScreen(int mouseX, int mouseY, float partialTick) {
            drawGradientRect(0, 0, width, height, 0xFA151012, 0xFF090809);
            drawCenteredString(fontRendererObj, "THE FOREST HAS CLOSED ITS LEDGER", width / 2, height / 2 - 40, 0xEEE2C9);
            drawCenteredString(fontRendererObj, "Your hunt is complete.", width / 2, height / 2 - 12, 0xC8B7A6);
            super.drawScreen(mouseX, mouseY, partialTick);
        }
    }
}
