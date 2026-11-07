package com.hbm.packet.toclient;

import java.util.UUID;
import com.hbm.entity.mob.gemini.EntityWretch;
import com.hbm.entity.mob.gemini.WretchAnatomy;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Bounded presentation snapshot. UUID/dimension prevent application to a reused entity ID. */
public final class WretchAnatomyPacket implements IMessage {
    private int entity, dimension;
    private UUID identity;
    private final WretchAnatomy anatomy = new WretchAnatomy();
    public WretchAnatomyPacket() { }
    public WretchAnatomyPacket(EntityWretch boss) {
        entity = boss.getEntityId(); dimension = boss.dimension; identity = boss.getUniqueID(); anatomy.copyVisual(boss.anatomy);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(entity); buf.writeInt(dimension); buf.writeLong(identity.getMostSignificantBits()); buf.writeLong(identity.getLeastSignificantBits()); anatomy.writeVisual(buf);
    }
    @Override public void fromBytes(ByteBuf buf) {
        entity = buf.readInt(); dimension = buf.readInt(); identity = new UUID(buf.readLong(), buf.readLong()); anatomy.readVisual(buf);
    }
    public static final class Handler implements IMessageHandler<WretchAnatomyPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final WretchAnatomyPacket packet, final MessageContext context) {
            final Minecraft mc = Minecraft.getMinecraft(); final World world = mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(world == null || mc.theWorld != world || mc.getNetHandler() != context.getClientHandler() || world.provider.dimensionId != packet.dimension) return;
                Entity entity = world.getEntityByID(packet.entity);
                if(entity instanceof EntityWretch && entity.getUniqueID().equals(packet.identity)) ((EntityWretch) entity).anatomy.copyVisual(packet.anatomy);
            }}); return null;
        }
    }
}
