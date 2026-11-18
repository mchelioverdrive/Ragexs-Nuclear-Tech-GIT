package com.hbm.render.entity;

import com.hbm.items.tool.ItemFlashlight;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.WoodcrawlerStrugglePacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public final class WoodcrawlerToolsClient {
    private int inputClock;
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if(event.phase != TickEvent.Phase.END || mc.thePlayer == null) return;
        if(!mc.thePlayer.getEntityData().getBoolean("WoodcrawlerRestrained")) { inputClock = 0; return; }
        if(++inputClock % 5 == 0) {
            int input = mc.currentScreen != null ? 0 : (mc.gameSettings.keyBindForward.getIsKeyPressed() ? 1 : 0)
                    | (mc.gameSettings.keyBindBack.getIsKeyPressed() ? 2 : 0) | (mc.gameSettings.keyBindLeft.getIsKeyPressed() ? 4 : 0)
                    | (mc.gameSettings.keyBindRight.getIsKeyPressed() ? 8 : 0);
            PacketDispatcher.wrapper.sendToServer(new WoodcrawlerStrugglePacket(input));
        }
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        mc.thePlayer.setSprinting(false); mc.thePlayer.fallDistance = 0;
    }
    @SubscribeEvent public void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if(event.entity.worldObj.isRemote && event.entity.getEntityData().getBoolean("WoodcrawlerRestrained")) event.entityLiving.motionY = 0;
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); if(mc.theWorld == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glDepthMask(false);
            int count = 0;
            for(Object entry : mc.theWorld.playerEntities) {
                EntityPlayer player = (EntityPlayer) entry; ItemStack held = player.getHeldItem();
                if(!ItemFlashlight.on(held) || player.getDistanceSqToEntity(mc.thePlayer) > 64 * 64 || ++count > 16) continue;
                beam(player, held, event.partialTicks);
            }
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static void beam(EntityPlayer player, ItemStack stack, float partial) {
        Vec3 origin = Vec3.createVectorHelper(player.lastTickPosX + (player.posX - player.lastTickPosX) * partial,
                player.lastTickPosY + (player.posY - player.lastTickPosY) * partial - player.yOffset + player.getEyeHeight(),
                player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial);
        Vec3 look = player.getLook(partial), right = look.crossProduct(Vec3.createVectorHelper(0, 1, 0)).normalize();
        if(right.lengthVector() < 0.1) right = Vec3.createVectorHelper(1, 0, 0);
        Vec3 up = right.crossProduct(look).normalize(); double distance = ItemFlashlight.range(stack), radius = 0.25 + distance * 0.16;
        boolean uv = ItemFlashlight.uv(stack); Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLE_FAN);
        tess.setColorRGBA_F(uv ? 0.55F : 1F, uv ? 0.2F : 0.94F, uv ? 1F : 0.65F, 0.06F);
        tess.addVertex(origin.xCoord, origin.yCoord, origin.zCoord);
        for(int side = 0; side <= 16; side++) {
            double angle = side * Math.PI / 8, x = Math.cos(angle) * radius, y = Math.sin(angle) * radius;
            Vec3 end = origin.addVector(look.xCoord * distance + right.xCoord * x + up.xCoord * y,
                    look.yCoord * distance + right.yCoord * x + up.yCoord * y, look.zCoord * distance + right.zCoord * x + up.zCoord * y);
            MovingObjectPosition hit = player.worldObj.func_147447_a(Vec3.createVectorHelper(origin.xCoord, origin.yCoord, origin.zCoord), end, false, true, false);
            if(hit != null) end = hit.hitVec;
            tess.addVertex(end.xCoord, end.yCoord, end.zCoord);
        }
        tess.draw();
        Vec3 end = origin.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        MovingObjectPosition hit = player.worldObj.func_147447_a(Vec3.createVectorHelper(origin.xCoord, origin.yCoord, origin.zCoord), end, false, true, false);
        if(hit == null) return;
        // An additive spot is drawn on the actual first solid surface, with native depth testing.
        Vec3 normal = hit.sideHit < 2 ? Vec3.createVectorHelper(0, hit.sideHit == 0 ? -1 : 1, 0)
                : hit.sideHit < 4 ? Vec3.createVectorHelper(0, 0, hit.sideHit == 2 ? -1 : 1) : Vec3.createVectorHelper(hit.sideHit == 4 ? -1 : 1, 0, 0);
        Vec3 center = hit.hitVec.addVector(normal.xCoord * 0.003, normal.yCoord * 0.003, normal.zCoord * 0.003);
        Vec3 a = Math.abs(normal.yCoord) > 0.5 ? Vec3.createVectorHelper(1, 0, 0) : Vec3.createVectorHelper(0, 1, 0), b = normal.crossProduct(a);
        tess.startDrawing(GL11.GL_TRIANGLE_FAN); tess.setColorRGBA_F(uv ? 0.55F : 1F, uv ? 0.2F : 0.94F, uv ? 1F : 0.65F, 0.45F);
        tess.addVertex(center.xCoord, center.yCoord, center.zCoord);
        for(int i = 0; i <= 16; i++) {
            double angle = i * Math.PI / 8, r = Math.min(0.48, 0.2 + origin.distanceTo(center) * 0.04), x = Math.cos(angle) * r, y = Math.sin(angle) * r;
            tess.setColorRGBA_F(uv ? 0.55F : 1F, uv ? 0.2F : 0.94F, uv ? 1F : 0.65F, 0);
            tess.addVertex(center.xCoord + a.xCoord * x + b.xCoord * y, center.yCoord + a.yCoord * x + b.yCoord * y, center.zCoord + a.zCoord * x + b.zCoord * y);
        }
        tess.draw();
    }
}
