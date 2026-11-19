package com.hbm.packet.toclient;

import com.hbm.handler.GeminiCaptures;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;

/** Grip transitions for observers, plus interpolated position samples for the captive player. */
public final class GeminiGripPacket implements IMessage {
    private int id, dimension, mode;
    private boolean position;
    private double x, feet, z;

    public GeminiGripPacket() { }
    public GeminiGripPacket(Entity victim, int mode, boolean position) {
        id = victim.getEntityId();
        dimension = victim.worldObj.provider.dimensionId;
        this.mode = mode;
        this.position = position;
        if(position) {
            x = victim.posX;
            feet = victim.boundingBox.minY;
            z = victim.posZ;
        }
    }

    @Override public void fromBytes(ByteBuf buf) {
        id = buf.readInt();
        dimension = buf.readInt();
        mode = buf.readUnsignedByte();
        position = buf.readBoolean();
        if(position) { x = buf.readDouble(); feet = buf.readDouble(); z = buf.readDouble(); }
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(id);
        buf.writeInt(dimension);
        buf.writeByte(mode);
        buf.writeBoolean(position);
        if(position) { buf.writeDouble(x); buf.writeDouble(feet); buf.writeDouble(z); }
    }

    public static final class Handler implements IMessageHandler<GeminiGripPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT)
        public IMessage onMessage(GeminiGripPacket packet, MessageContext context) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.func_152344_a(() -> {
                if(mc.getNetHandler() != context.getClientHandler() || mc.theWorld == null || mc.thePlayer == null
                        || mc.theWorld.provider.dimensionId != packet.dimension) return;
                Entity victim = mc.theWorld.getEntityByID(packet.id);
                if(victim == null) return;
                GeminiCaptures.clientGrip(victim, packet.mode, packet.position, packet.x, packet.feet, packet.z);
                if(victim instanceof EntityBoat && mc.thePlayer.ridingEntity == victim)
                    ((EntityBoat) victim).setIsBoatEmpty(packet.mode != GeminiCaptures.RELEASED);
            });
            return null;
        }
    }
}
