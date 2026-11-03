package com.hbm.packet.toclient;

import com.hbm.particle.WitherStormDebris;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/** At most 80 compact records every five ticks, only to clients near the encounter. */
public final class WitherStormDebrisPacket implements IMessage {
    private int boss;
    private int[] records;
    public WitherStormDebrisPacket() { }
    public WitherStormDebrisPacket(int boss, int[] source, int count) { this.boss = boss; records = java.util.Arrays.copyOf(source, Math.min(80, count) * 5); }
    @Override public void fromBytes(ByteBuf buf) {
        boss = buf.readInt(); int count = buf.readUnsignedByte();
        if(count > 80 || buf.readableBytes() != count * 16) throw new IllegalArgumentException("Invalid Wither Storm debris batch");
        records = new int[count * 5];
        for(int i = 0; i < records.length; i += 5) { records[i] = buf.readInt(); records[i+1] = buf.readUnsignedByte(); records[i+2] = buf.readInt(); records[i+3] = buf.readInt(); records[i+4] = buf.readUnsignedByte(); buf.readUnsignedShort(); }
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(boss); buf.writeByte(records.length / 5);
        for(int i = 0; i < records.length; i += 5) { buf.writeInt(records[i]); buf.writeByte(records[i+1]); buf.writeInt(records[i+2]); buf.writeInt(records[i+3]); buf.writeByte(records[i+4]); buf.writeShort(0); }
    }
    public static final class Handler implements IMessageHandler<WitherStormDebrisPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final WitherStormDebrisPacket message, final MessageContext ctx) {
            final Minecraft mc = Minecraft.getMinecraft(); final World world = mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(world == null || mc.theWorld != world || mc.getNetHandler() != ctx.getClientHandler()) return;
                WitherStormDebris.accept(world, message.boss, message.records);
            }});
            return null;
        }
    }
}
