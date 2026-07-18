package com.hbm.packet.toclient;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.threading.PrecompiledPacket;
import com.hbm.packet.threading.ThreadedPacket;
import com.hbm.tileentity.IBufPacketReceiver;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class BufPacket extends PrecompiledPacket implements ThreadedPacket {

	int x;
	int y;
	int z;
	IBufPacketReceiver rec;
	ByteBuf buf;

	public BufPacket() { }

	public BufPacket(int x, int y, int z, IBufPacketReceiver rec) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.rec = rec;
	}

	public BufPacket(ByteBuf compiled) {
		super(compiled);
	}

	public static ByteBuf compile(int x, int y, int z, IBufPacketReceiver rec) {
		ByteBuf buf = Unpooled.buffer();
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		rec.serialize(buf);
		return buf;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.x = buf.readInt();
		this.y = buf.readInt();
		this.z = buf.readInt();
		this.buf = Unpooled.copiedBuffer(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBuf payload = getPayload();
		if(payload != null) {
			super.toBytes(buf);
			return;
		}

		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		this.rec.serialize(buf);
	}

	@Override
	public void sendToAllAround(TargetPoint point) {
		PacketDispatcher.wrapper.sendToAllAround(this, point);
	}

	@Override
	public void sendTo(EntityPlayerMP player) {
		PacketDispatcher.wrapper.sendTo(this, player);
	}

	public static class Handler implements IMessageHandler<BufPacket, IMessage> {

		@Override
		public IMessage onMessage(BufPacket m, MessageContext ctx) {

			if(Minecraft.getMinecraft().theWorld == null) {
				m.buf.release();
				return null;
			}

			TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(m.x, m.y, m.z);

			try {
				if(te instanceof IBufPacketReceiver) {
					((IBufPacketReceiver) te).deserialize(m.buf);
				}
			} finally {
				m.buf.release();
			}

			return null;
		}
	}
}
