package com.hbm.packet.toserver;

import com.hbm.inventory.container.ContainerMoltenMaterials;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Targets an open container and a stable material ID, rather than trusting tile coordinates or list indices. */
public class MoltenSelectionPacket implements IMessage {
	private int window;
	private int tank;
	private int material;

	public MoltenSelectionPacket() { }

	public MoltenSelectionPacket(int window, int tank, int material) {
		this.window = window;
		this.tank = tank;
		this.material = material;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		window = buf.readInt();
		tank = buf.readInt();
		material = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(window);
		buf.writeInt(tank);
		buf.writeInt(material);
	}

	public static class Handler implements IMessageHandler<MoltenSelectionPacket, IMessage> {
		@Override
		public IMessage onMessage(MoltenSelectionPacket message, MessageContext ctx) {
			EntityPlayer player = ctx.getServerHandler().playerEntity;
			Container container = player.openContainer;
			if(container instanceof ContainerMoltenMaterials) {
				((ContainerMoltenMaterials) container).requestSelection(player, message.window, message.tank, message.material);
			}
			return null;
		}
	}
}
