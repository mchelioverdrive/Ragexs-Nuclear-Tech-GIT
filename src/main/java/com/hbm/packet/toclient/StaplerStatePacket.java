package com.hbm.packet.toclient;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public final class StaplerStatePacket implements IMessage {
    private NBTTagCompound state;

    public StaplerStatePacket() {}
    public StaplerStatePacket(NBTTagCompound state) { this.state = state; }
    @Override public void fromBytes(ByteBuf buffer) { state = ByteBufUtils.readTag(buffer); }
    @Override public void toBytes(ByteBuf buffer) { ByteBufUtils.writeTag(buffer, state); }

    public static final class Handler implements IMessageHandler<StaplerStatePacket, IMessage> {
        @Override @SideOnly(Side.CLIENT)
        public IMessage onMessage(StaplerStatePacket message, MessageContext context) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
            client.func_152344_a(new Runnable() {
                @Override public void run() {
                    if(client.getNetHandler() == context.getClientHandler() && message.state != null)
                        com.hbm.world.squirrelstapler.StaplerClient.accept(client.theWorld, message.state);
                }
            });
            return null;
        }
    }
}
