package com.hbm.packet.toclient;

import com.hbm.sound.WitherStormSound;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/** Boss cues reach listeners outside the entity tracking and vanilla sound radii. */
public final class WitherStormSoundPacket implements IMessage {
    public int boss;
    public String sound;
    public float volume, pitch;
    public double x, y, z;
    public WitherStormSoundPacket() { }
    public WitherStormSoundPacket(int boss, String sound, float volume, float pitch, double x, double y, double z) {
        this.boss = boss; this.sound = sound; this.volume = volume; this.pitch = pitch;
        this.x = x; this.y = y; this.z = z;
    }
    @Override public void fromBytes(ByteBuf b) {
        boss = b.readInt(); sound = ByteBufUtils.readUTF8String(b);
        volume = b.readFloat(); pitch = b.readFloat(); x = b.readDouble(); y = b.readDouble(); z = b.readDouble();
        if(!sound.startsWith("hbm:entity.witherStorm.") || sound.length() > 64 || !Float.isFinite(volume)
            || !Float.isFinite(pitch) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Invalid Wither Storm sound");
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeInt(boss); ByteBufUtils.writeUTF8String(b, sound);
        b.writeFloat(volume); b.writeFloat(pitch); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
    }
    public static final class Handler implements IMessageHandler<WitherStormSoundPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final WitherStormSoundPacket message, final MessageContext ctx) {
            final Minecraft mc = Minecraft.getMinecraft(); final World world = mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(world != null && mc.theWorld == world && mc.getNetHandler() == ctx.getClientHandler())
                    WitherStormSound.play(world, message);
            }});
            return null;
        }
    }
}
