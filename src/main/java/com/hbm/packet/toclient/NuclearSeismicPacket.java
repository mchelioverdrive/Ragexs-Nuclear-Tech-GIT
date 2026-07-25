package com.hbm.packet.toclient;

import com.hbm.main.MainRegistry;
import com.hbm.main.ModEventHandlerClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** LOS-independent, per-player seismic impulse; never uses hurt-camera state. */
public class NuclearSeismicPacket implements IMessage {
	private double x, y, z, yieldKt, groundCoupling, burialDepth;
	private float intensity;
	private int duration;
	public NuclearSeismicPacket() { }
	public NuclearSeismicPacket(double x, double y, double z, double yieldKt, double groundCoupling, double burialDepth, float intensity, int duration) { this.x=x; this.y=y; this.z=z; this.yieldKt=yieldKt; this.groundCoupling=groundCoupling; this.burialDepth=burialDepth; this.intensity=intensity; this.duration=duration; }
	@Override public void fromBytes(ByteBuf b) { x=b.readDouble(); y=b.readDouble(); z=b.readDouble(); yieldKt=b.readDouble(); groundCoupling=b.readDouble(); burialDepth=b.readDouble(); intensity=b.readFloat(); duration=b.readInt(); }
	@Override public void toBytes(ByteBuf b) { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeDouble(yieldKt); b.writeDouble(groundCoupling); b.writeDouble(burialDepth); b.writeFloat(intensity); b.writeInt(duration); }
	public static class Handler implements IMessageHandler<NuclearSeismicPacket,IMessage> {
		@Override @SideOnly(Side.CLIENT) public IMessage onMessage(NuclearSeismicPacket m, MessageContext ctx) {
			EntityPlayer player=Minecraft.getMinecraft().thePlayer; if(player==null) return null;
			double distance=Math.max(1D, player.getDistance(m.x,m.y,m.z));
			float local=(float)Math.min(1D, m.intensity * m.groundCoupling * Math.sqrt(Math.max(0.001D,m.yieldKt)) / (1D + distance * 0.12D));
			if(local > 0.02F) { ModEventHandlerClient.seismicTimestamp=System.currentTimeMillis(); ModEventHandlerClient.seismicDuration=m.duration; ModEventHandlerClient.seismicIntensity=local; MainRegistry.proxy.playSoundClient(m.x,m.y,m.z,"hbm:weapon.nuclearExplosion",Math.max(0.2F,local*3F),0.55F); }
			return null;
		}
	}
}
