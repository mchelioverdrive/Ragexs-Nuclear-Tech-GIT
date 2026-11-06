package com.hbm.sound;

import java.util.ArrayList;
import java.util.List;
import com.hbm.packet.toclient.WitherStormSoundPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** Finite clips, custom distance falloff and a nearby virtual source retain direction at boss distances. */
@SideOnly(Side.CLIENT)
public final class WitherStormSound extends MovingSound {
    private static final List<WitherStormSound> active = new ArrayList<>();
    private final java.lang.ref.WeakReference<World> world;
    private final int boss;
    private final String cue;
    private final double x, y, z;
    private final float gain;
    private int age;

    private WitherStormSound(World world, WitherStormSoundPacket packet) {
        super(new ResourceLocation(packet.sound));
        this.world = new java.lang.ref.WeakReference<>(world); boss = packet.boss; cue = packet.sound;
        x = packet.x; y = packet.y; z = packet.z;
        gain = Math.max(0F, Math.min(1F, packet.volume));
        field_147663_c = Math.max(0.5F, Math.min(2F, packet.pitch));
        field_147666_i = ISound.AttenuationType.NONE;
        repeat = false;
        update();
    }
    public static void play(World world, WitherStormSoundPacket packet) {
        Minecraft mc = Minecraft.getMinecraft();
        active.removeIf(sound -> sound.world.get() != world || sound.donePlaying || !mc.getSoundHandler().isSoundPlaying(sound));
        for(WitherStormSound sound : active) if(sound.boss == packet.boss && sound.cue.equals(packet.sound)) return;
        int count = 0;
        for(WitherStormSound sound : active) if(sound.boss == packet.boss) count++;
        if(count >= 6) {
            WitherStormSound oldest = null;
            for(WitherStormSound sound : active) if(sound.boss == packet.boss) { oldest = sound; break; }
            if(oldest != null) { oldest.donePlaying = true; mc.getSoundHandler().stopSound(oldest); active.remove(oldest); }
        }
        WitherStormSound sound = new WitherStormSound(world, packet);
        active.add(sound);
        mc.getSoundHandler().playSound(sound);
    }
    @Override public void update() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity listener = mc.renderViewEntity;
        if(mc.theWorld != world.get() || listener == null || ++age > 1200) { donePlaying = true; repeat = false; return; }
        double dx = x - listener.posX, dy = y - listener.posY, dz = z - listener.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Smoothly fade to silence at the packet radius, keeping a useful rumble beyond 256 blocks.
        double edge = Math.max(0D, 1D - Math.pow(distance / 768D, 4D));
        volume = (float)(gain * edge / (1D + distance / 96D));
        double positionScale = distance > 8D ? 8D / distance : 1D;
        xPosF = (float)(listener.posX + dx * positionScale);
        yPosF = (float)(listener.posY + dy * positionScale);
        zPosF = (float)(listener.posZ + dz * positionScale);
    }
}
