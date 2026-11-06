package com.hbm.packet.toclient;

import com.hbm.handler.disease.DiseaseHost;
import cpw.mods.fml.common.network.ByteBufUtils;
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

/** Presentation only: no private infection record, timer or source is synchronized. */
public final class DeepRootVisualPacket implements IMessage {
	private int entityId, dimension, stage;
	private String type;
	private boolean spasm;

	public DeepRootVisualPacket() { }
	public DeepRootVisualPacket(EntityLivingBase host) {
		this(host, DiseaseHost.get(host).deepRootVisualStage, DiseaseHost.get(host).deepRootSpasm);
	}
	public DeepRootVisualPacket(EntityLivingBase host, int stage, boolean spasm) {
		entityId = host.getEntityId();
		dimension = host.worldObj.provider.dimensionId;
		type = host instanceof net.minecraft.entity.player.EntityPlayer ? "player" : host.getClass().getName();
		this.stage = stage;
		this.spasm = spasm;
	}
	@Override public void toBytes(ByteBuf buf) {
		buf.writeInt(entityId); buf.writeInt(dimension); ByteBufUtils.writeUTF8String(buf, type);
		buf.writeByte(stage); buf.writeBoolean(spasm);
	}
	@Override public void fromBytes(ByteBuf buf) {
		entityId = buf.readInt(); dimension = buf.readInt(); type = ByteBufUtils.readUTF8String(buf);
		stage = buf.readUnsignedByte(); spasm = buf.readBoolean();
		if(stage > 6) stage = 0;
	}

	public static final class Handler implements IMessageHandler<DeepRootVisualPacket, IMessage> {
		@Override @SideOnly(Side.CLIENT)
		public IMessage onMessage(final DeepRootVisualPacket message, final MessageContext ctx) {
			final Minecraft mc = Minecraft.getMinecraft();
			final World world = mc.theWorld;
			mc.func_152344_a(new Runnable() {
				@Override public void run() {
					if(world == null || mc.theWorld != world || mc.getNetHandler() != ctx.getClientHandler()
							|| world.provider.dimensionId != message.dimension) return;
					Entity entity = world.getEntityByID(message.entityId);
					// Player implementation classes differ between dedicated server and local/remote clients.
					boolean player = entity instanceof net.minecraft.entity.player.EntityPlayer
							&& "player".equals(message.type);
					if(entity instanceof EntityLivingBase && (player || entity.getClass().getName().equals(message.type))) {
						DiseaseHost data = DiseaseHost.get((EntityLivingBase) entity);
						data.deepRootVisualStage = message.stage;
						data.deepRootSpasm = message.stage >= 3 && message.spasm;
					}
				}
			});
			return null;
		}
	}
}
