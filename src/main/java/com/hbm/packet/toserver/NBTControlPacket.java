package com.hbm.packet.toserver;

import java.io.IOException;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.machine.MachineRuntimeManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.World;

public class NBTControlPacket implements IMessage {
	
	PacketBuffer buffer;
	int x;
	int y;
	int z;

	public NBTControlPacket() { }

	public NBTControlPacket(NBTTagCompound nbt, int x, int y, int z) {
		
		this.buffer = new PacketBuffer(Unpooled.buffer());
		this.x = x;
		this.y = y;
		this.z = z;
		
		try {
			buffer.writeNBTTagCompoundToBuffer(nbt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		
		if(buffer == null) buffer = new PacketBuffer(Unpooled.buffer());
		
		buffer.writeBytes(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		
		if (buffer == null) buffer = new PacketBuffer(Unpooled.buffer());
		
		buf.writeBytes(buffer);
	}

	public static class Handler implements IMessageHandler<NBTControlPacket, IMessage> {
		
		@Override
		public IMessage onMessage(final NBTControlPacket m, MessageContext ctx) {
			final NetHandlerPlayServer handler = ctx.getServerHandler();
			final EntityPlayer p = handler.playerEntity;
			final World world = p.worldObj;
			try {
				final NBTTagCompound nbt = m.buffer.readNBTTagCompoundFromBuffer();
				if(nbt != null) MachineRuntimeManager.enqueueControl(world, new Runnable() {
					@Override public void run() {
						if(p.worldObj != world || !handler.netManager.isChannelOpen() || !world.blockExists(m.x, m.y, m.z)) return;
						TileEntity te = world.getTileEntity(m.x, m.y, m.z);
						if(te instanceof IControlReceiver && !te.isInvalid()) {
							IControlReceiver tile = (IControlReceiver) te;
							if(tile.hasPermission(p)) {
								tile.receiveControl(p, nbt);
								tile.receiveControl(nbt);
							}
						}
					}
				});
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				m.buffer.release();
			}
			
			return null;
		}
	}
}
