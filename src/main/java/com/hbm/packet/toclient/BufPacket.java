package com.hbm.packet.toclient;

import com.hbm.tileentity.IBufPacketReceiver;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BufPacket implements IMessage {

	int x;
	int y;
	int z;
	String receiverClass;
	IBufPacketReceiver rec;
	ByteBuf buf;
	
	public BufPacket() { }

	public BufPacket(int x, int y, int z, IBufPacketReceiver rec) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.rec = rec;
		this.receiverClass = rec.getClass().getName();
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.x = buf.readInt();
		this.y = buf.readInt();
		this.z = buf.readInt();
		this.receiverClass = ByteBufUtils.readUTF8String(buf);
		this.buf = Unpooled.buffer(buf.readableBytes());
		this.buf.writeBytes(buf, buf.readerIndex(), buf.readableBytes());
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		ByteBufUtils.writeUTF8String(buf, receiverClass);
		this.rec.serialize(buf);
	}

	public static class Handler implements IMessageHandler<BufPacket, IMessage> {
		
		@Override
		public IMessage onMessage(final BufPacket message, MessageContext ctx) {
			Minecraft.getMinecraft().func_152344_a(new Runnable() {
				@Override
				public void run() {
					try {
						World world = Minecraft.getMinecraft().theWorld;
						if(world == null || !world.blockExists(message.x, message.y, message.z)) return;

						TileEntity tile = world.getTileEntity(message.x, message.y, message.z);
						if(!(tile instanceof IBufPacketReceiver)) return;
						if(!tile.getClass().getName().equals(message.receiverClass)) return;

						((IBufPacketReceiver) tile).deserialize(message.buf);
					} finally {
						message.buf.release();
					}
				}
			});

			return null;
		}
	}
}
