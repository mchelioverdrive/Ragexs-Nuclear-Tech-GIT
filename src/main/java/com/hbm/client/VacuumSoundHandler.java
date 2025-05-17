package com.hbm.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.player.EntityPlayer;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;

import java.lang.reflect.Field;
import java.util.Map;

public class VacuumSoundHandler {

	private static Field playingSoundsField = null;

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		System.out.println("TickEvent: " + event.phase + " " + event.player.getDisplayName() + " " + event.player.worldObj.isRemote + " " + event.player.posX + " " + event.player.posY + " " + event.player.posZ);
		if (event.phase != TickEvent.Phase.END) return;
		EntityPlayer player = event.player;
		if (player.worldObj.isRemote) {
			boolean hasAtmosphere = ChunkAtmosphereManager.proxy.hasAtmosphere(
				player.worldObj,
				(int)player.posX,
				(int)player.posY,
				(int)player.posZ
			);
			if (!hasAtmosphere) {
				muteNonSuitSounds();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void muteNonSuitSounds() {
		Minecraft mc = Minecraft.getMinecraft();
		SoundHandler soundHandler = mc.getSoundHandler();

		try {
			if (playingSoundsField == null) {
				playingSoundsField = SoundHandler.class.getDeclaredField("playingSounds");
				playingSoundsField.setAccessible(true);
			}
			Map<String, ISound> playingSounds = (Map<String, ISound>) playingSoundsField.get(soundHandler);

			for (ISound sound : playingSounds.values()) {
				String name = sound.getPositionedSoundLocation().toString();
				if (name.contains("plss_breathing") || name.contains("helmet")) continue;
				soundHandler.stopSound(sound);
			}
		} catch (Exception e) {
			// Fallback: stop all sounds (rarely needed)
			soundHandler.stopSounds();
		}
	}
}
