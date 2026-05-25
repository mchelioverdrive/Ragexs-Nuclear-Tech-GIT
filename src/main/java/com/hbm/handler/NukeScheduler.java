package com.hbm.handler;

import com.hbm.config.GeneralConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class NukeScheduler {

	private int tickTimer = 0;

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {

		if(event.phase != TickEvent.Phase.END)
			return;

		// only check every 5 seconds
		tickTimer++;

		if(tickTimer < 100)
			return;

		tickTimer = 0;

		if(GeneralConfig.scheduledNukeTime == null)
			return;

		long now = System.currentTimeMillis();

		if(now >= GeneralConfig.scheduledNukeTime) {

			GeneralConfig.enableNuking =
				GeneralConfig.scheduledNukeValue;

			System.out.println(
				"[HBM] Nukes automatically "
					+ (GeneralConfig.enableNuking ? "enabled" : "disabled")
			);

			// clear schedule
			GeneralConfig.scheduledNukeTime = null;
			GeneralConfig.scheduledNukeValue = null;
		}
	}
}
