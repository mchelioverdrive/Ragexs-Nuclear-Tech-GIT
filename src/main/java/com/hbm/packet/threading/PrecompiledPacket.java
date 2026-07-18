package com.hbm.packet.threading;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Packet wrapper for payloads that have already been serialized into a ByteBuf.
 *
 * The wrapped buffer is copied on construction so callers can retain their own
 * comparison buffer, then released once FML has copied the bytes into the
 * outbound packet buffer.
 */
public class PrecompiledPacket implements IMessage {

	private ByteBuf payload;

	public PrecompiledPacket() { }

	public PrecompiledPacket(ByteBuf payload) {
		this.payload = Unpooled.copiedBuffer(payload);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.payload = Unpooled.copiedBuffer(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		if(this.payload == null) {
			return;
		}

		try {
			buf.writeBytes(this.payload, this.payload.readerIndex(), this.payload.readableBytes());
		} finally {
			this.payload.release();
			this.payload = null;
		}
	}

	public ByteBuf getPayload() {
		return payload;
	}
}
