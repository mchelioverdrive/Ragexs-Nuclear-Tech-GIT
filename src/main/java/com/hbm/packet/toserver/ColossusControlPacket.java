package com.hbm.packet.toserver;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Network thread stores only the latest input. Player END ticks validate and apply it on the server. */
public final class ColossusControlPacket implements IMessage {
    public static final ConcurrentHashMap<UUID,ColossusControlPacket> INPUT=new ConcurrentHashMap<UUID,ColossusControlPacket>();
    public int boss,vertical;
    public boolean brace,release;
    public EntityPlayerMP player;
    public ColossusControlPacket() { }
    public ColossusControlPacket(int boss,int vertical,boolean brace,boolean release) { this.boss=boss;this.vertical=vertical;this.brace=brace;this.release=release; }
    @Override public void toBytes(ByteBuf b) { b.writeInt(boss);b.writeByte(vertical);b.writeBoolean(brace);b.writeBoolean(release); }
    @Override public void fromBytes(ByteBuf b) { if(b.readableBytes()!=7) throw new IllegalArgumentException("Invalid Colossus input"); boss=b.readInt();vertical=b.readByte();brace=b.readBoolean();release=b.readBoolean(); }
    public static final class Handler implements IMessageHandler<ColossusControlPacket,IMessage> {
        @Override public IMessage onMessage(ColossusControlPacket packet,MessageContext context) {
            packet.player=context.getServerHandler().playerEntity;
            INPUT.put(packet.player.getUniqueID(),packet); return null;
        }
    }
}
