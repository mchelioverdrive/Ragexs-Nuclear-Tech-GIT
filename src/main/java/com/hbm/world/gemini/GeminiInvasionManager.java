package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.config.SpaceConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.duna.WorldProviderDuna;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

/** Server-thread entry point for all future Gemini spawns, spread, generation and events. */
public final class GeminiInvasionManager {

	/** A milestone query, independent of the invasion enable setting and current dimension. */
	public static boolean hasMarsVisited(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		return data != null && data.hasMarsVisited();
	}

	/**
	 * Every future activity producer must check this on the server thread before mutating
	 * the world, including ongoing infection/mob callbacks and structure generation.
	 * This initial implementation confines activity to the Overworld.
	 */
	public static boolean canActivityOccur(World world) {
		return GeminiConfig.enabled && world instanceof WorldServer && !world.isRemote
				&& world.provider.dimensionId == 0 && hasMarsVisited(world);
	}

	private static void recordArrival(EntityPlayer player) {
		if(!(player instanceof EntityPlayerMP) || player.worldObj.isRemote) return;
		World world = player.worldObj;
		if(player.dimension != world.provider.dimensionId || !isMars(world)) return;
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data != null) data.recordMarsVisit();
	}

	private static boolean isMars(World world) {
		// SolarSystem registers real Mars under the legacy name "duna". Do not use
		// getPlanet/getTarget: a station in orbit can resolve to Mars without a visit.
		if(!(world.provider instanceof WorldProviderDuna)
				|| world.provider.dimensionId != SpaceConfig.dunaDimension) return false;
		CelestialBody body = CelestialBody.getBodyOrNull(world.provider.dimensionId);
		return body != null && "duna".equals(body.name);
	}

	@SubscribeEvent
	public void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
		recordArrival(event.player);
	}

	@SubscribeEvent
	public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		recordArrival(event.player);
	}

	@SubscribeEvent
	public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		recordArrival(event.player);
	}

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		// Disabled invasions perform no save lookup or scheduling. Arrival recording
		// above still remembers genuine Mars visits while the feature is disabled.
		if(event.phase != TickEvent.Phase.END || !GeminiConfig.enabled
				|| !(event.world instanceof WorldServer) || event.world.isRemote
				|| event.world.provider.dimensionId != 0
				|| event.world.getTotalWorldTime() % 20 != 0
				|| event.world.playerEntities.isEmpty()) return;
		if(!canActivityOccur(event.world)) return;

		WorldServer overworld = (WorldServer) event.world;
		GeminiWorldSavedData data = GeminiWorldSavedData.get(overworld);
		int remaining = data.getEventSecondsRemaining();
		if(remaining == 0) {
			scheduleNext(overworld, data);
			return;
		}
		if(remaining > 1) {
			data.setEventSecondsRemaining(remaining - 1);
			return;
		}

		// Reserve the next interval before dispatch; never replay missed events or
		// dispatch a collection of invasions on unlock, login or server restart.
		scheduleNext(overworld, data);
		MinecraftForge.EVENT_BUS.post(new InvasionEvent(overworld));
	}

	private static void scheduleNext(WorldServer world, GeminiWorldSavedData data) {
		int min = GeminiConfig.minEventIntervalSeconds;
		int max = GeminiConfig.maxEventIntervalSeconds;
		data.setEventSecondsRemaining(min + world.rand.nextInt(max - min + 1));
	}

	/**
	 * One gated, synchronous Overworld opportunity on MinecraftForge.EVENT_BUS.
	 * Future subscribers should choose a single bounded event rather than launch
	 * every event type. No creatures, infections or structures are added here.
	 * Deferred work must recheck canActivityOccur when it actually executes.
	 */
	public static final class InvasionEvent extends Event {
		public final WorldServer world;

		private InvasionEvent(WorldServer world) {
			this.world = world;
		}
	}
}
