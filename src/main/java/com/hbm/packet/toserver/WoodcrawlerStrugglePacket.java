package com.hbm.packet.toserver;

import com.hbm.handler.WoodcrawlerCaptures;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;

/** Input is merely a request. The server tick bounds both frequency and progress. */
public final class WoodcrawlerStrugglePacket implements IMessage {
    private int mask;
    public WoodcrawlerStrugglePacket() { }
    public WoodcrawlerStrugglePacket(int mask) { this.mask = mask; }
    @Override public void fromBytes(ByteBuf b) { mask = b.readUnsignedByte() & 15; }
    @Override public void toBytes(ByteBuf b) { b.writeByte(mask); }
    public static final class Handler implements IMessageHandler<WoodcrawlerStrugglePacket, IMessage> {
        @Override public IMessage onMessage(WoodcrawlerStrugglePacket packet, MessageContext context) {
            WoodcrawlerCaptures.offerInput(context.getServerHandler().playerEntity, packet.mask); return null;
        }
    }
}
