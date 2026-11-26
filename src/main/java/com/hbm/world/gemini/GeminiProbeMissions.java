package com.hbm.world.gemini;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import com.hbm.config.GeminiConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.SolarSystem;
import com.hbm.world.gemini.DeepSpaceProbe.State;

import net.minecraft.command.CommandException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Server-side mission registry owned by the shared Gemini save. */
public final class GeminiProbeMissions {
	private final NavigableMap<Integer, DeepSpaceProbe> missions = new TreeMap<>();
	private final Set<DeepSpaceProbe> flying = new LinkedHashSet<>();
	private int discoveryProbe;
	private long activationAt;

	/** Called by the Satellite ID-Linker when a payload is paired with a Control Interface. */
	public static int register(World world, int assignedId) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null) return 0;
		GeminiProbeMissions probes = data.probes;
		if(probes.missions.containsKey(assignedId)) return assignedId;
		if(!GeminiConfig.probeSettings.enabled) return 0;
		DeepSpaceProbe probe = probes.create(data);
		return probe == null ? 0 : probe.id;
	}

	private DeepSpaceProbe create(GeminiWorldSavedData data) {
		int last = missions.isEmpty() ? 0 : missions.lastKey();
		if(last == Integer.MAX_VALUE) return null;
		DeepSpaceProbe probe = new DeepSpaceProbe(last + 1);
		missions.put(probe.id, probe);
		data.markDirty();
		return probe;
	}

	public static String launchBlocker(World world, int id) {
		if(!GeminiConfig.probeSettings.enabled) return "Deep space probe launches are disabled.";
		if(world.provider.dimensionId != 0) return "Launch the deep space probe from the Overworld.";
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null) return "The server Overworld is not available.";
		DeepSpaceProbe probe = data.probes.missions.get(id);
		if(probe == null) return "Link the probe to a Satellite Control Interface before launch.";
		if(probe.state != State.AWAITING_LAUNCH) return "This probe has already launched.";
		return departureBody() == null ? "The solar navigation catalog has no route beyond Neptune." : null;
	}

	public static boolean launch(World world, int id) {
		if(launchBlocker(world, id) != null) return false;
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		return data.probes.depart(data, data.probes.missions.get(id), world.getTotalWorldTime());
	}

	private static CelestialBody departureBody() {
		CelestialBody earth = SolarSystem.Body.KERBIN.getBody();
		CelestialBody neptune = SolarSystem.Body.NEPTUNE.getBody();
		return earth != null && neptune != null && neptune.type == SolarSystem.Body.NEPTUNE
				&& earth.parent != null && earth.parent == neptune.parent && earth.semiMajorAxisKm < neptune.semiMajorAxisKm
				? earth : null;
	}

	private boolean depart(GeminiWorldSavedData data, DeepSpaceProbe probe, long now) {
		CelestialBody earth = departureBody();
		if(earth == null) return false;
		// The navigation system only times transfers to registered bodies. Use its orbital radii
		// to place the survey point without registering a new body, destination or dimension.
		double surveyRadius = SolarSystem.Body.NEPTUNE.getBody().semiMajorAxisKm * 1.05D;
		probe.launch(now, earth.semiMajorAxisKm, surveyRadius, GeminiConfig.probeSettings);
		flying.add(probe);
		data.markDirty();
		return true;
	}

	void tick(GeminiWorldSavedData data, long now) {
		for(Iterator<DeepSpaceProbe> it = flying.iterator(); it.hasNext();) {
			DeepSpaceProbe probe = it.next();
			State previous = probe.state;
			probe.update(now);
			if(probe.state == previous) continue;
			if(probe.state == State.SIGNAL_LOST) {
				it.remove();
				recordDiscovery(probe, now);
			}
			data.markDirty();
		}
	}

	private void recordDiscovery(DeepSpaceProbe probe, long now) {
		if(discoveryProbe != 0) return;
		discoveryProbe = probe.id;
		activationAt = now + probe.activationDelayTicks;
	}

	String activationBlocker() {
		if(discoveryProbe == 0) return "waiting for a probe to discover the Iris beyond Neptune and lose signal";
		long remaining = activationAt - DimensionManager.getWorld(0).getTotalWorldTime();
		return remaining > 0 ? "waiting " + remaining + " ticks after probe signal loss" : null;
	}

	public static String summary(World world) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null || data.probes.missions.isEmpty()) return "No deep space probes registered.";
		GeminiProbeMissions probes = data.probes;
		DeepSpaceProbe latest = probes.missions.lastEntry().getValue();
		String discovery = probes.discoveryProbe == 0 ? "Iris discovery pending" : "Iris discovery recorded by probe #" + probes.discoveryProbe;
		return probes.missions.size() + " probe(s) registered. Latest #" + latest.id + " | " + latest.state + ". " + discovery + ".";
	}

	public static List<String> telemetry(World world, int id, boolean history) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		DeepSpaceProbe probe = data == null ? null : data.probes.find(id);
		if(probe == null) return Collections.singletonList(id == 0 ? "No deep space probes registered." : "No telemetry for probe #" + id + " in this world.");
		return probe.telemetry(DimensionManager.getWorld(0).getTotalWorldTime(), history);
	}

	private DeepSpaceProbe find(int id) {
		return id == 0 ? missions.isEmpty() ? null : missions.lastEntry().getValue() : missions.get(id);
	}

	/** Invoked only by the permission-level-2 Gemini command. Zero selects the latest mission. */
	public static int debugControl(World world, String action, int id) {
		GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
		if(data == null) throw new CommandException("The server Overworld is not available.");
		GeminiProbeMissions probes = data.probes;
		DeepSpaceProbe probe = probes.find(id);
		long now = DimensionManager.getWorld(0).getTotalWorldTime();
		if("launch".equals(action) && id == 0 && (probe == null || probe.state == State.SIGNAL_LOST)) {
			if(departureBody() == null) throw new CommandException("The solar navigation catalog has no route beyond Neptune.");
			probe = probes.create(data);
		}
		if(probe == null) throw new CommandException(id == 0 ? "No available probe. Use /gemini probe launch first." : "Unknown deep space probe #" + id + ".");

		switch(action) {
			case "advance":
				switch(probe.state) {
					case AWAITING_LAUNCH: probes.debugLaunch(data, probe, now); break;
					case IN_TRANSIT: probe.enterOuterSystem(now); break;
					case OUTER_SOLAR_SYSTEM: probe.detectEarly(now); break;
					case ANOMALY_DETECTED: probes.loseSignal(probe, now); break;
					default: throw new CommandException("This probe has already lost signal.");
				}
				break;
			case "launch":
				probes.debugLaunch(data, probe, now);
				break;
			case "discover":
				if(probe.state != State.IN_TRANSIT && probe.state != State.OUTER_SOLAR_SYSTEM)
					throw new CommandException("The probe must be outbound with no anomaly detected yet.");
				probe.detectEarly(now);
				break;
			case "signallost":
				if(probe.state != State.ANOMALY_DETECTED)
					throw new CommandException("The probe must detect the anomaly before it can lose signal.");
				probes.loseSignal(probe, now);
				break;
			default: throw new IllegalArgumentException(action);
		}
		data.markDirty();
		return probe.id;
	}

	private void debugLaunch(GeminiWorldSavedData data, DeepSpaceProbe probe, long now) {
		if(probe.state != State.AWAITING_LAUNCH) throw new CommandException("This probe has already launched.");
		if(!depart(data, probe, now)) throw new CommandException("The solar navigation catalog has no route beyond Neptune.");
	}

	private void loseSignal(DeepSpaceProbe probe, long now) {
		probe.state = State.SIGNAL_LOST;
		flying.remove(probe);
		recordDiscovery(probe, now);
	}

	void read(NBTTagCompound nbt) {
		missions.clear();
		flying.clear();
		NBTTagList list = nbt.getTagList("missions", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			DeepSpaceProbe probe = DeepSpaceProbe.read(list.getCompoundTagAt(i));
			if(probe == null || missions.putIfAbsent(probe.id, probe) != null) continue;
			if(probe.state != State.AWAITING_LAUNCH && probe.state != State.SIGNAL_LOST) flying.add(probe);
		}
		discoveryProbe = nbt.getInteger("discoveryProbe");
		activationAt = nbt.getLong("activationAt");
	}

	NBTTagCompound write() {
		NBTTagCompound nbt = new NBTTagCompound();
		NBTTagList list = new NBTTagList();
		for(DeepSpaceProbe probe : missions.values()) list.appendTag(probe.write());
		nbt.setTag("missions", list);
		nbt.setInteger("discoveryProbe", discoveryProbe);
		nbt.setLong("activationAt", activationAt);
		return nbt;
	}
}
