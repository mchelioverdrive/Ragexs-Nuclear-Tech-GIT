package com.hbm.packet.toclient;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.extprop.HbmPlayerProps;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class ExtPropPacket implements IMessage {

	private ByteBuf buffer;

	public ExtPropPacket() { }

	public ExtPropPacket(HbmLivingProps props, HbmPlayerProps pprps) {
		this.buffer = Unpooled.buffer();
		props.serialize(this.buffer);
		pprps.serialize(this.buffer);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.buffer = Unpooled.copiedBuffer(buf);
	}
	@Override
	public void toBytes(ByteBuf buf) {
		if(this.buffer != null) {
			try {
				buf.writeBytes(this.buffer, this.buffer.readerIndex(), this.buffer.readableBytes());
			} finally {
				this.buffer.release();
				this.buffer = null;
			}
		}
	}

	public static class Handler implements IMessageHandler<ExtPropPacket, IMessage> {

		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(ExtPropPacket m, MessageContext ctx) {

			if(Minecraft.getMinecraft().theWorld == null) {
				m.buffer.release();
				return null;
			}

			EntityPlayer player = Minecraft.getMinecraft().thePlayer;
			HbmLivingProps props = HbmLivingProps.getData(player);
			HbmPlayerProps pprps = HbmPlayerProps.getData(player);
			try {
				props.deserialize(m.buffer);
				pprps.deserialize(m.buffer);
			} finally {
				m.buffer.release();
			}

			return null;
		}
	}
}
