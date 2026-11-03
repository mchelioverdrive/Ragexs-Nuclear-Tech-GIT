package com.hbm.packet.toclient;

import com.hbm.handler.disease.DiseaseHost;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/** Tracking-only visual bit. No stage, source, presentation, timeline or infection NBT leaves the server. */
public final class RabiesVisualPacket implements IMessage {
	private int entityId;
	private boolean frothing;

	public RabiesVisualPacket() { }
	public RabiesVisualPacket(int entityId, boolean frothing) {
		this.entityId = entityId;
		this.frothing = frothing;
	}
	@Override public void fromBytes(ByteBuf buf) { entityId = buf.readInt(); frothing = buf.readBoolean(); }
	@Override public void toBytes(ByteBuf buf) { buf.writeInt(entityId); buf.writeBoolean(frothing); }

	public static final class Handler implements IMessageHandler<RabiesVisualPacket, IMessage> {
		@Override @SideOnly(Side.CLIENT)
		public IMessage onMessage(final RabiesVisualPacket message, final MessageContext ctx) {
			final Minecraft mc = Minecraft.getMinecraft();
			final World world = mc.theWorld;
			mc.func_152344_a(new Runnable() {
				@Override public void run() {
					if(world == null || mc.theWorld != world || mc.getNetHandler() != ctx.getClientHandler()) return;
					Entity entity = world.getEntityByID(message.entityId);
					if(entity instanceof EntityLivingBase) {
						DiseaseHost data = DiseaseHost.get((EntityLivingBase) entity);
						if(message.frothing && !data.rabiesFrothing)
							data.nextRabiesFroth = entity.ticksExisted + 1 + world.rand.nextInt(20);
						data.rabiesFrothing = message.frothing;
					}
				}
			});
			return null;
		}
	}
}
