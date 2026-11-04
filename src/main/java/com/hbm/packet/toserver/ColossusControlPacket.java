package com.hbm.packet.toserver;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Preserve ordered escape edges across network bursts. Player END ticks validate and apply them. */
public final class ColossusControlPacket implements IMessage {
    public static final ConcurrentHashMap<UUID,ArrayDeque<ColossusControlPacket>> INPUT=new ConcurrentHashMap<>();
    public int boss,vertical,token,sequence;
    public boolean brace,release;
    public EntityPlayerMP player;
    public ColossusControlPacket() { }
    public ColossusControlPacket(int boss,int vertical,boolean brace,boolean release) { this.boss=boss;this.vertical=vertical;this.brace=brace;this.release=release; }
    public ColossusControlPacket(int boss,int token,int sequence,boolean down) {
        this(boss,0,false,down); this.token=token; this.sequence=sequence;
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeInt(boss); b.writeByte(vertical); b.writeBoolean(brace); b.writeBoolean(release);
        if(sequence>0) { b.writeInt(token); b.writeInt(sequence); }
    }
    @Override public void fromBytes(ByteBuf b) {
        int length=b.readableBytes();
        if(length!=7 && length!=15) throw new IllegalArgumentException("Invalid Colossus input");
        boss=b.readInt(); vertical=b.readByte(); brace=b.readBoolean(); release=b.readBoolean();
        if(length==15) {
            token=b.readInt(); sequence=b.readInt();
            if(sequence<=0) throw new IllegalArgumentException("Invalid Colossus escape sequence");
        }
    }
    public static final class Handler implements IMessageHandler<ColossusControlPacket,IMessage> {
        @Override public IMessage onMessage(ColossusControlPacket packet,MessageContext context) {
            packet.player=context.getServerHandler().playerEntity;
            INPUT.compute(packet.player.getUniqueID(),(id,queue)->{
                if(queue==null) queue=new ArrayDeque<>();
                if(queue.size()<32) queue.addLast(packet);
                return queue;
            });
            return null;
        }
    }
}
