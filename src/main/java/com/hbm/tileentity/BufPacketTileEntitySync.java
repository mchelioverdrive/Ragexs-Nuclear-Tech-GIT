package com.hbm.tileentity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Bridges an IBufPacketReceiver's existing wire format into initial chunk data. */
public final class BufPacketTileEntitySync {

	private static final String PAYLOAD_KEY = "hbmBufSync";
	private static final String RECEIVER_CLASS_KEY = "hbmBufSyncClass";

	private BufPacketTileEntitySync() { }

	public static Packet createDescriptionPacket(TileEntity tile, IBufPacketReceiver receiver) {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt, receiver);
		return new S35PacketUpdateTileEntity(tile.xCoord, tile.yCoord, tile.zCoord, 0, nbt);
	}

	public static void writeToNBT(NBTTagCompound nbt, IBufPacketReceiver receiver) {
		ByteBuf payload = Unpooled.buffer();

		try {
			receiver.serialize(payload);
			byte[] serialized = new byte[payload.readableBytes()];
			payload.readBytes(serialized);
			nbt.setString(RECEIVER_CLASS_KEY, receiver.getClass().getName());
			nbt.setByteArray(PAYLOAD_KEY, serialized);
		} finally {
			payload.release();
		}
	}

	@SideOnly(Side.CLIENT)
	public static void applyOnClientThread(NBTTagCompound nbt, IBufPacketReceiver receiver) {
		if(!receiver.getClass().getName().equals(nbt.getString(RECEIVER_CLASS_KEY))) return;
		if(!nbt.hasKey(PAYLOAD_KEY)) return;

		final TileEntity expectedTile = (TileEntity) receiver;
		final String receiverClass = receiver.getClass().getName();
		final byte[] serialized = nbt.getByteArray(PAYLOAD_KEY).clone();

		Minecraft.getMinecraft().func_152344_a(new Runnable() {
			@Override
			public void run() {
				if(Minecraft.getMinecraft().theWorld == null) return;
				if(!Minecraft.getMinecraft().theWorld.blockExists(expectedTile.xCoord, expectedTile.yCoord, expectedTile.zCoord)) return;

				TileEntity currentTile = Minecraft.getMinecraft().theWorld.getTileEntity(expectedTile.xCoord, expectedTile.yCoord, expectedTile.zCoord);
				if(currentTile != expectedTile || !(currentTile instanceof IBufPacketReceiver)) return;
				if(!currentTile.getClass().getName().equals(receiverClass)) return;

				ByteBuf payload = Unpooled.wrappedBuffer(serialized);
				try {
					((IBufPacketReceiver) currentTile).deserialize(payload);
				} finally {
					payload.release();
				}
			}
		});
	}
}
