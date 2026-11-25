package com.hbm.sound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

/** A stoppable sound with Minecraft's standard positional attenuation. */
@SideOnly(Side.CLIENT)
public final class PositionedAudio extends AudioWrapper {
    private final PositionedSound sound;

    public PositionedAudio(String name, float x, float y, float z, float volume, float pitch, boolean repeat) {
        sound = new Sound(name, x, y, z, volume, pitch, repeat);
    }

    @Override public void startSound() { Minecraft.getMinecraft().getSoundHandler().playSound(sound); }
    @Override public void stopSound() { Minecraft.getMinecraft().getSoundHandler().stopSound(sound); }
    @Override public boolean isPlaying() { return Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(sound); }

    private static final class Sound extends PositionedSound {
        Sound(String name, float x, float y, float z, float volume, float pitch, boolean repeat) {
            super(new ResourceLocation(name));
            this.xPosF = x;
            this.yPosF = y;
            this.zPosF = z;
            this.volume = volume;
            this.field_147663_c = pitch;
            this.repeat = repeat;
        }
    }
}
