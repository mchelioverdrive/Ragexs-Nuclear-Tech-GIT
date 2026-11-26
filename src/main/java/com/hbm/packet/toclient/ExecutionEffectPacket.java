package com.hbm.packet.toclient;

import com.hbm.config.ExecutionEffectsConfig.Values;
import com.hbm.handler.restraint.ExecutionDeviceEffects.Cue;
import com.hbm.particle.ExecutionEffectsClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.Vec3;

public final class ExecutionEffectPacket implements IMessage {
    private Cue cue;
    private double x, y, z;
    private int dimension, rotation, channels, maxParticles, maxDecals, decalTicks, distance;
    private float intensity;

    public ExecutionEffectPacket() { }

    public ExecutionEffectPacket(Cue cue, int dimension, Vec3 origin, int rotation, int channels, float intensity, Values config) {
        this.cue = cue;
        this.dimension = dimension;
        x = origin.xCoord;
        y = origin.yCoord;
        z = origin.zCoord;
        this.rotation = rotation;
        this.channels = channels;
        this.intensity = intensity;
        maxParticles = config.maxParticles;
        maxDecals = config.maxDecals;
        decalTicks = config.decalTicks;
        distance = config.distance;
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeByte(cue.ordinal()).writeInt(dimension).writeDouble(x).writeDouble(y).writeDouble(z);
        buffer.writeByte(rotation).writeByte(channels).writeFloat(intensity);
        buffer.writeShort(maxParticles).writeShort(maxDecals).writeShort(decalTicks).writeByte(distance);
    }

    @Override public void fromBytes(ByteBuf buffer) {
        cue = Cue.fromId(buffer.readUnsignedByte());
        dimension = buffer.readInt();
        x = buffer.readDouble();
        y = buffer.readDouble();
        z = buffer.readDouble();
        rotation = buffer.readUnsignedByte() & 3;
        channels = buffer.readUnsignedByte();
        intensity = buffer.readFloat();
        maxParticles = buffer.readUnsignedShort();
        maxDecals = buffer.readUnsignedShort();
        decalTicks = buffer.readUnsignedShort();
        distance = buffer.readUnsignedByte();
    }

    public static final class Handler implements IMessageHandler<ExecutionEffectPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT)
        public IMessage onMessage(ExecutionEffectPacket packet, MessageContext context) {
            Minecraft mc = Minecraft.getMinecraft();
            WorldClient world = mc.theWorld;
            NetHandlerPlayClient connection = context.getClientHandler();
            mc.func_152344_a(() -> {
                if(world == null || world != mc.theWorld || mc.getNetHandler() != connection
                        || world.provider.dimensionId != packet.dimension || packet.cue == null) return;
                ExecutionEffectsClient.emit(mc, world, packet.cue, packet.x, packet.y, packet.z, packet.rotation,
                        packet.channels, packet.intensity, packet.maxParticles, packet.maxDecals, packet.decalTicks, packet.distance);
            });
            return null;
        }
    }
}
