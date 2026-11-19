package com.hbm.packet.toclient;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/** A server-authored sky snapshot; no client-to-server progression path. */
public final class IrisStatePacket implements IMessage {
    private NBTTagCompound state;
    public IrisStatePacket() { }
    public IrisStatePacket(NBTTagCompound state) { this.state = state; }
    @Override public void fromBytes(ByteBuf buf) { state = ByteBufUtils.readTag(buf); }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, state); }
    public static final class Handler implements IMessageHandler<IrisStatePacket, IMessage> {
        @Override @SideOnly(Side.CLIENT)
        public IMessage onMessage(final IrisStatePacket message, final MessageContext ctx) {
            final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            final net.minecraft.client.multiplayer.WorldClient world = mc.theWorld;
            mc.func_152344_a(new Runnable() {
                @Override public void run() {
                    if(world == null || mc.theWorld != world || mc.getNetHandler() != ctx.getClientHandler() || message.state == null) return;
                    com.hbm.render.world.IrisClient.accept(world, message.state);
                }
            });
            return null;
        }
    }
}
