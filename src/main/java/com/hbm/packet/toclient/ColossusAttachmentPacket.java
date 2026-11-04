package com.hbm.packet.toclient;

import com.hbm.render.entity.mob.ColossusClient;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class ColossusAttachmentPacket implements IMessage {
    public int boss,passenger;
    public float progress;
    public boolean grabbed;
    public int side,token,required;
    public float anchorX,anchorY,anchorZ;
    public ColossusAttachmentPacket() { }
    public ColossusAttachmentPacket(int boss,int passenger,float progress,boolean grabbed,int side) { this.boss=boss;this.passenger=passenger;this.progress=progress;this.grabbed=grabbed;this.side=side; }
    public ColossusAttachmentPacket(int boss,int passenger,int progress,int side,int token,int required,Vec3 anchor) {
        this(boss,passenger,progress,true,side);
        this.token=token; this.required=required;
        anchorX=(float)anchor.xCoord; anchorY=(float)anchor.yCoord; anchorZ=(float)anchor.zCoord;
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeInt(boss); b.writeInt(passenger); b.writeFloat(progress); b.writeBoolean(grabbed); b.writeByte(side);
        if(grabbed) { b.writeInt(token); b.writeByte(required); b.writeFloat(anchorX); b.writeFloat(anchorY); b.writeFloat(anchorZ); }
    }
    @Override public void fromBytes(ByteBuf b) {
        if(b.readableBytes()<14) throw new IllegalArgumentException("Invalid Colossus attachment");
        boss=b.readInt(); passenger=b.readInt(); progress=b.readFloat(); grabbed=b.readBoolean(); side=b.readByte();
        if(b.readableBytes()!=(grabbed?17:0)) throw new IllegalArgumentException("Invalid Colossus attachment payload");
        if(grabbed) { token=b.readInt(); required=b.readUnsignedByte(); anchorX=b.readFloat(); anchorY=b.readFloat(); anchorZ=b.readFloat(); }
    }
    public static final class Handler implements IMessageHandler<ColossusAttachmentPacket,IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final ColossusAttachmentPacket packet,final MessageContext context) {
            final Minecraft mc=Minecraft.getMinecraft(); final World w=mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(w!=null && mc.theWorld==w && mc.getNetHandler()==context.getClientHandler()) ColossusClient.accept(packet,w);
            }}); return null;
        }
    }
}
