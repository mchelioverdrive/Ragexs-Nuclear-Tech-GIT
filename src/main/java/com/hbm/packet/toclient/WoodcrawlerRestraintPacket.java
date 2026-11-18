package com.hbm.packet.toclient;

import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;

public final class WoodcrawlerRestraintPacket implements IMessage {
    private boolean held;
    public WoodcrawlerRestraintPacket() { }
    public WoodcrawlerRestraintPacket(boolean held) { this.held = held; }
    @Override public void fromBytes(ByteBuf b) { held = b.readBoolean(); }
    @Override public void toBytes(ByteBuf b) { b.writeBoolean(held); }
    public static final class Handler implements IMessageHandler<WoodcrawlerRestraintPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final WoodcrawlerRestraintPacket packet, final MessageContext context) {
            final Minecraft mc = Minecraft.getMinecraft();
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(mc.getNetHandler() == context.getClientHandler() && mc.thePlayer != null)
                    mc.thePlayer.getEntityData().setBoolean("WoodcrawlerRestrained", packet.held);
            }}); return null;
        }
    }
}
