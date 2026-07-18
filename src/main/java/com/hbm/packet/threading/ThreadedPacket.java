package com.hbm.packet.threading;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Optional packet-dispatch hook for payloads that can be precompiled before
 * they are sent. Packet threading is intentionally kept opt-in; current send
 * paths use the synchronous fallback until multiplayer testing validates a
 * worker queue.
 */
public interface ThreadedPacket extends IMessage {

	void sendToAllAround(TargetPoint point);

	void sendTo(EntityPlayerMP player);
}
