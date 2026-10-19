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
import com.hbm.main.MainRegistry;

/** Ground-motion cue; contains physical source data and applies no damage or hurt animation. */
public class NuclearSeismicPacket implements IMessage {
 private double x,y,z,yieldKt,coupling,burialDepth; private int duration; private float baseIntensity;
 private boolean valid;
 private static final java.util.Set<Integer> REJECTED_LENGTHS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
 public NuclearSeismicPacket() { }
 public NuclearSeismicPacket(double x,double y,double z,double yieldKt,double coupling,double burialDepth,int duration,float baseIntensity){this.x=x;this.y=y;this.z=z;this.yieldKt=yieldKt;this.coupling=coupling;this.burialDepth=burialDepth;this.duration=duration;this.baseIntensity=baseIntensity;}
 public void fromBytes(ByteBuf b){
  valid = false;
  // A gauge payload routed here is not a seismic event. Reject it before reading any fields.
  int length = b.readableBytes();
  if(length != 56) {
   if(REJECTED_LENGTHS.add(length)) MainRegistry.logger.error("Rejected invalid seismic packet: expected 56 bytes, received " + length + ". Check matching HBM network registrations on both sides.");
   b.skipBytes(length);
   return;
  }
  x=b.readDouble();y=b.readDouble();z=b.readDouble();yieldKt=b.readDouble();coupling=b.readDouble();burialDepth=b.readDouble();duration=b.readInt();baseIntensity=b.readFloat();
  valid = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(yieldKt) && Double.isFinite(coupling) && Double.isFinite(burialDepth) && Float.isFinite(baseIntensity) && duration > 0;
 }
 public void toBytes(ByteBuf b){b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeDouble(yieldKt);b.writeDouble(coupling);b.writeDouble(burialDepth);b.writeInt(duration);b.writeFloat(baseIntensity);}
	public static class Handler implements IMessageHandler<NuclearSeismicPacket, IMessage> {

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(NuclearSeismicPacket message, MessageContext context) {
			if(!message.valid) return null;

			EntityPlayer player = Minecraft.getMinecraft().thePlayer;
			if(player == null) return null;

			double distance = Math.sqrt(
				player.getDistanceSq(message.x, message.y, message.z)
			);

			double audibleRange = Math.max(
				48D,
				Math.cbrt(Math.max(0.001D, message.yieldKt)) * 80D
			);

			/*
			 * Close players hear and feel more.
			 * The effect fades to zero at audibleRange.
			 */
			double distanceFactor = Math.max(
				0D,
				1D - distance / audibleRange
			);

			float localIntensity = (float)(
				message.baseIntensity
					* message.coupling
					* distanceFactor
			);

			if(localIntensity <= 0F) return null;

			long now = System.currentTimeMillis();

			ModEventHandlerClient.seismicTimestamp = now;
			ModEventHandlerClient.seismicDuration = message.duration;
			ModEventHandlerClient.seismicIntensity = Math.max(
				ModEventHandlerClient.seismicIntensity,
				localIntensity
			);

			/*
			 * Only add this separate muffled rumble for underground shots.
			 * Surface detonations already receive the normal Torex sound.
			 */
			if(message.burialDepth > 0.5D) {

				/*
				 * Deeper explosions become quieter, but retain a 20% floor
				 * at point-blank range so very deep shots are not completely silent.
				 */
				double depthAttenuation =
					0.20D
						+ 0.80D
						* Math.exp(-message.burialDepth / 48D);

				float volume = (float)Math.max(
					0D,
					Math.min(
						1D,
						localIntensity * depthAttenuation * 0.75D
					)
				);

				/*
				 * Lower pitch approximates the loss of high-frequency sound
				 * through soil and rock.
				 */
				float pitch = (float)Math.max(
					0.32D,
					Math.min(
						0.65D,
						0.65D - message.burialDepth / 512D
					)
				);

				if(volume > 0.02F) {

					/*
					 * Play at the player's position because distance attenuation
					 * has already been calculated above. Playing it at the
					 * hypocenter would cause Minecraft to attenuate it a second time.
					 */
					MainRegistry.proxy.playSoundClient(
						player.posX,
						player.posY,
						player.posZ,
						"hbm:misc.rumble",
						volume,
						pitch
					);
				}
			}

			return null;
		}
	}
}
