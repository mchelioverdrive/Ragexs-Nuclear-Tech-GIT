package com.hbm.packet.toclient;

import com.hbm.main.ModEventHandlerClient;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** Ground-motion cue; contains physical source data and applies no damage or hurt animation. */
public class NuclearSeismicPacket implements IMessage {
 private double x,y,z,yieldKt,coupling,burialDepth; private int duration; private float baseIntensity;
 public NuclearSeismicPacket() { }
 public NuclearSeismicPacket(double x,double y,double z,double yieldKt,double coupling,double burialDepth,int duration,float baseIntensity){this.x=x;this.y=y;this.z=z;this.yieldKt=yieldKt;this.coupling=coupling;this.burialDepth=burialDepth;this.duration=duration;this.baseIntensity=baseIntensity;}
 public void fromBytes(ByteBuf b){x=b.readDouble();y=b.readDouble();z=b.readDouble();yieldKt=b.readDouble();coupling=b.readDouble();burialDepth=b.readDouble();duration=b.readInt();baseIntensity=b.readFloat();}
 public void toBytes(ByteBuf b){b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeDouble(yieldKt);b.writeDouble(coupling);b.writeDouble(burialDepth);b.writeInt(duration);b.writeFloat(baseIntensity);}
 public static class Handler implements IMessageHandler<NuclearSeismicPacket,IMessage>{ @Override @SideOnly(Side.CLIENT) public IMessage onMessage(NuclearSeismicPacket m,MessageContext c){EntityPlayer p=Minecraft.getMinecraft().thePlayer;if(p==null)return null;double distance=Math.sqrt(p.getDistanceSq(m.x,m.y,m.z));double range=Math.max(48D,Math.cbrt(Math.max(.001D,m.yieldKt))*80D);float local=(float)(m.baseIntensity*m.coupling*Math.max(0D,1D-distance/range));if(local>0F){long now=System.currentTimeMillis();ModEventHandlerClient.seismicTimestamp=now;ModEventHandlerClient.seismicDuration=m.duration;ModEventHandlerClient.seismicIntensity=Math.max(ModEventHandlerClient.seismicIntensity,local);}return null;} }
}
