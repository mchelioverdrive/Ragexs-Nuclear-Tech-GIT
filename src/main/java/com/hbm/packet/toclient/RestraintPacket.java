package com.hbm.packet.toclient;

import com.hbm.handler.restraint.RestraintState;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Vanilla 1.7.10 mob spawn packets do not synchronize UUIDs. Guard runtime IDs by world and entity type instead. */
public final class RestraintPacket implements IMessage {
    private int id, dimension;
    private String type;
    private NBTTagCompound state;
    public RestraintPacket() { }
    public RestraintPacket(EntityLivingBase e) { id = e.getEntityId(); dimension = e.worldObj.provider.dimensionId; type = e.getClass().getName(); state = RestraintState.get(e).write(); }
    @Override public void toBytes(ByteBuf b) { b.writeInt(id); b.writeInt(dimension); ByteBufUtils.writeUTF8String(b, type); ByteBufUtils.writeTag(b, state); }
    @Override public void fromBytes(ByteBuf b) { id = b.readInt(); dimension = b.readInt(); type = ByteBufUtils.readUTF8String(b); state = ByteBufUtils.readTag(b); }
    public static final class Handler implements IMessageHandler<RestraintPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final RestraintPacket p, final MessageContext context) {
            final Minecraft mc = Minecraft.getMinecraft(); final World world = mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(world == null || mc.theWorld != world || mc.getNetHandler() != context.getClientHandler() || p.state == null) return;
                Entity e = world.getEntityByID(p.id);
                if(world.provider.dimensionId == p.dimension && e instanceof EntityLivingBase && e.getClass().getName().equals(p.type))
                    RestraintState.get((EntityLivingBase) e).read(p.state);
            }});
            return null;
        }
    }
}
