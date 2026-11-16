package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.config.SpaceConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.duna.WorldProviderDuna;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

/** Server-thread entry point for Gemini spawns, spread, generation and events. */
public final class GeminiInvasionManager {
	private static GeminiWorldSavedData requireData(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null) throw new net.minecraft.command.CommandException("The server Overworld is not available.");
		return data;
	}

	public static long getElapsedTicks(World world) { return requireData(world).getElapsedTicks(); }
	public static long getRemainingTicks(World world) { return requireData(world).getRemainingTicks(); }
	public static int getEventSecondsRemaining(World world) { return requireData(world).getEventSecondsRemaining(); }
	public static boolean isDebug(World world) { return requireData(world).isDebug(); }
	static boolean debugging(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		return data != null && data.isDebug();
	}
	public static void setDebug(World world, boolean enabled) { requireData(world).setDebug(enabled); }
	public static boolean progressionActive(World world) {
		WorldServer overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
		return overworld != null && canActivityOccur(overworld) && !overworld.playerEntities.isEmpty();
	}
	public static void setPhase(World world, int phase) {
		if(phase < 1 || phase > 4) throw new net.minecraft.command.CommandException("Supported phases are 1, 2, 3 and 4.");
		GeminiWorldSavedData data = requireData(world);
		long previous = data.getElapsedTicks();
		data.setPhase(world, phase);
		if(previous != data.getElapsedTicks()) debug(world, "timer", "Phase control set invasion timer to " + data.getElapsedTicks() + " ticks.");
	}
	public static void reset(World world) {
		requireData(world).reset(world);
		debug(world, "timer", "Invasion progression reset; Mars milestone and existing structures retained.");
	}
	public static void setElapsedTicks(World world, long ticks) {
		if(ticks < 0) throw new net.minecraft.command.CommandException("Ticks must be nonnegative.");
		GeminiWorldSavedData data = requireData(world);
		if(!data.hasMarsVisited()) throw new net.minecraft.command.CommandException("Invasion has not started. Use /gemini phase set 1 or /visitedmars true first.");
		long previous = data.getElapsedTicks();
		data.setElapsedTicks(world, ticks);
		debug(world, "timer", "Invasion timer changed from " + previous + " to " + ticks + " ticks.");
	}
	public static void addElapsedTicks(World world, long ticks) {
		GeminiWorldSavedData data = requireData(world);
		if(ticks < 0 || ticks > Long.MAX_VALUE - data.getElapsedTicks())
			throw new net.minecraft.command.CommandException("Ticks must be nonnegative and must not overflow the invasion timer.");
		if(!data.hasMarsVisited()) throw new net.minecraft.command.CommandException("Invasion has not started. Use /gemini phase set 1 or /visitedmars true first.");
		long previous = data.getElapsedTicks();
		data.advanceTicks(world, ticks);
		debug(world, "timer", "Invasion timer advanced from " + previous + " to " + data.getElapsedTicks() + " ticks.");
	}

	/** Unlock consumers query the saved phase; transitions also shorten pending structure countdowns. */
	static void phaseChanged(World world, int from, int to) {
		WorldServer overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
		if(overworld != null) {
			WretchCamps.reschedule(overworld);
			GardenerNests.reschedule(overworld);
		}
		debug(world, "phase-" + from + "-" + to, "Invasion phase " + from + " -> " + to + ".");
	}
	static net.minecraft.command.CommandException rejected(World world, String kind, String reason) {
		debug(world, kind + "-failure", reason);
		return new net.minecraft.command.CommandException(reason);
	}

	/** Saved opt-in, transient per-save throttles, server thread only. */
	public static void debug(World world, String key, String message) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null || !data.isDebug()) return;
		WorldServer overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
		long now = overworld.getTotalWorldTime();
		Long previous = data.debugMessages.get(key);
		if(previous != null && now >= previous && now - previous < 200L) return;
		data.debugMessages.put(key, now);
		MainRegistry.logger.info("[Gemini debug] " + message);
		for(Object entry : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
			EntityPlayerMP player = (EntityPlayerMP) entry;
			if(player.canCommandSenderUseCommand(2, "gemini"))
				player.addChatMessage(new ChatComponentText("[Gemini] " + message));
		}
	}

	/** Tracking snapshots do not inspect or load structure chunks. */
	public static final class StructureInfo {
		public final String type, status;
		public final int x, y, z, dimension;
		public StructureInfo(String type, int x, int y, int z, int dimension, String status) {
			this.type = type; this.x = x; this.y = y; this.z = z; this.dimension = dimension; this.status = status;
		}
		public double distanceSq(double px, double py, double pz) {
			double dx = x + 0.5D - px, dy = y - py, dz = z + 0.5D - pz;
			return dx * dx + dy * dy + dz * dz;
		}
		@Override public String toString() { return type + " at " + x + ", " + y + ", " + z + " (dimension " + dimension + ", " + status + ")"; }
	}

	/** A milestone query, independent of the invasion enable setting and current dimension. */
	public static boolean hasMarsVisited(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		return data != null && data.hasMarsVisited();
	}

	/** Administrative server-thread toggle; retain phase progress and the event countdown. */
	public static boolean setMarsVisited(World world, boolean visited) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null) return false;
		boolean previous = data.hasMarsVisited();
		int previousPhase = data.getPhase();
		data.setMarsVisited(visited);
		if(previous != visited) debug(world, "activation", "Mars milestone=" + visited + "; invasion phase=" + data.getPhase() + ".");
		if(previousPhase != data.getPhase()) phaseChanged(world, previousPhase, data.getPhase());
		return true;
	}

	/**
	 * Every future activity producer must check this on the server thread before mutating
	 * the world, including ongoing infection/mob callbacks and structure generation.
	 * This initial implementation confines activity to the Overworld.
	 */
	public static boolean canActivityOccur(World world) {
		return canActivityOccur(world, 1);
	}

	public static boolean canActivityOccur(World world, int requiredPhase) {
		return GeminiConfig.enabled && world instanceof WorldServer && !world.isRemote
				&& world.provider.dimensionId == 0 && getPhase(world) >= requiredPhase;
	}

	/** Saved unlock state, independent of enablement. */
	public static int getPhase(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		return data == null ? 0 : data.getPhase();
	}

	public static int encounterPercent(World world) {
		int phase = getPhase(world);
		return phase < 1 ? 0 : GeminiConfig.phaseEncounterPercent[Math.min(phase, 4) - 1];
	}

	private static void recordArrival(EntityPlayer player) {
		if(!(player instanceof EntityPlayerMP) || player.worldObj.isRemote) return;
		World world = player.worldObj;
		if(player.dimension != world.provider.dimensionId || !isMars(world)) return;
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data != null) setMarsVisited(world, true);
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

	private static boolean preventsSleep(EntityPlayer player) {
		// Forge 1.7.10 has survival/adventure and creative, but no spectator mode.
		return GeminiConfig.geminiPreventSleeping && !player.capabilities.isCreativeMode
				&& canActivityOccur(player.worldObj);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onSleep(PlayerSleepInBedEvent event) {
		if(!preventsSleep(event.entityPlayer)) return;
		// OTHER_PROBLEM returns before vanilla sets sleeping/bed occupancy, and unlike
		// NOT_POSSIBLE_NOW/NOT_SAFE it does not cause BlockBed to send a second message.
		event.result = EntityPlayer.EnumStatus.OTHER_PROBLEM;
		event.entityPlayer.addChatComponentMessage(new ChatComponentText("I don't feel tired for some reason"));
	}

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		// Also cover players already asleep when the invasion unlocks or config reloads.
		if(event.phase == TickEvent.Phase.START && event.player.isPlayerSleeping() && preventsSleep(event.player))
			event.player.wakeUpPlayer(true, true, false);
	}

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		if(event.phase != TickEvent.Phase.END
				|| !(event.world instanceof WorldServer) || event.world.isRemote
				|| event.world.provider.dimensionId != 0
				|| event.world.getTotalWorldTime() % 20 != 0) return;
		// Diagnostics are opt-in; disabled invasions perform no progression or scheduling work.
		if(!GeminiConfig.enabled) { WoodcrawlerSpawning.diagnostic(event.world, "disabled", "Gemini invasion disabled"); return; }
		if(event.world.playerEntities.isEmpty()) { WoodcrawlerSpawning.diagnostic(event.world, "players", "No players in the Overworld"); return; }

		WorldServer overworld = (WorldServer) event.world;
		GeminiWorldSavedData data = GeminiWorldSavedData.get(overworld);
		if(data == null) return;
		if(!data.hasMarsVisited()) { WoodcrawlerSpawning.diagnostic(overworld, "mars", "Mars visit requirement not satisfied"); return; }
		if(data.getPhase() < 1) { WoodcrawlerSpawning.diagnostic(overworld, "phase", "Incorrect invasion phase"); return; }
		data.advancePhase(overworld);
		// Independent saved structure opportunities must run before encounter-countdown returns.
		GardenerNests.schedule(overworld);
		WretchCamps.schedule(overworld);
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
		int phase = Math.max(0, Math.min(3, data.getPhase() - 1));
		int min = GeminiConfig.woodcrawlerInvasionMinIntervalSeconds[phase];
		int max = GeminiConfig.woodcrawlerInvasionMaxIntervalSeconds[phase];
		int interval = min + world.rand.nextInt(max - min + 1);
		data.setEventSecondsRemaining(interval);
	}

	/**
	 * One gated, synchronous Overworld opportunity on MinecraftForge.EVENT_BUS.
	 * Subscribers should choose a single bounded event rather than launch
	 * every event type. WoodcrawlerSpawning supplies a bounded forest encounter.
	 * Deferred work must recheck canActivityOccur when it actually executes.
	 */
	public static final class InvasionEvent extends Event {
		public final WorldServer world;

		private InvasionEvent(WorldServer world) {
			this.world = world;
		}
	}
}
