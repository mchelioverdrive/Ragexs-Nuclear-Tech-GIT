package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.config.GeminiConfig.ProbeSettings;
import com.hbm.util.AstronomyUtil;

import net.minecraft.nbt.NBTTagCompound;

/** A single one-way mission. Only its milestones and the launch settings need to be saved. */
final class DeepSpaceProbe {
	enum State { AWAITING_LAUNCH, IN_TRANSIT, OUTER_SOLAR_SYSTEM, ANOMALY_DETECTED, SIGNAL_LOST }

	private static final String LAUNCH = "Launch confirmed. Outbound course loaded.";
	private static final String OUTER = "Beyond Neptune's orbit. Beginning the survey.";
	private static final String[] TRANSMISSIONS = {
		"Survey contact. Unidentified body ahead. No match in the navigation catalog.",
		"Gravity solution rejected. The pull is increasing faster than the range is closing.",
		"Telemetry out of sequence. A frame marked NEXT has already been received.",
		"The spectrometer reports complex organics. The readings repeat at regular intervals.",
		"REC--- GROUND / DO NOT ACKN--- / WE CAN HEAR THE ROOM.",
		"Motion confirmed. The body has changed course. The new bearing matches ours.",
		"Carrier fading. Automatic gain at maximum. Something is transmitting between our pulses."
	};

	final int id;
	State state = State.AWAITING_LAUNCH;
	private long stageStarted;
	private int travelTicks, anomalyTicks;
	int activationDelayTicks;
	private double departureKm, destinationKm;
	private boolean transmissions;

	DeepSpaceProbe(int id) {
		this.id = id;
	}

	void launch(long now, double departureKm, double destinationKm, ProbeSettings settings) {
		this.departureKm = departureKm;
		this.destinationKm = destinationKm;
		travelTicks = settings.travelTicks;
		anomalyTicks = settings.anomalyTicks;
		activationDelayTicks = settings.activationDelayTicks;
		transmissions = settings.anomalousTransmissions;
		stageStarted = now;
		state = State.IN_TRANSIT;
	}

	void enterOuterSystem(long now) {
		stageStarted = now;
		state = State.OUTER_SOLAR_SYSTEM;
	}

	void detectEarly(long now) {
		enterOuterSystem(now - (anomalyTicks + (long) TRANSMISSIONS.length) / (TRANSMISSIONS.length + 1));
		state = State.ANOMALY_DETECTED;
	}

	void update(long now) {
		if(state == State.IN_TRANSIT && now - stageStarted >= travelTicks) {
			enterOuterSystem(stageStarted + travelTicks);
		}
		if(state == State.OUTER_SOLAR_SYSTEM && receivedPackets(now) > 0) {
			state = State.ANOMALY_DETECTED;
		}
		if(state == State.ANOMALY_DETECTED && now - stageStarted >= anomalyTicks) {
			state = State.SIGNAL_LOST;
		}
	}

	private double progress(long now, int duration) {
		return Math.max(0D, Math.min(1D, (now - stageStarted) / (double) duration));
	}

	private int receivedPackets(long now) {
		if(state == State.SIGNAL_LOST) return TRANSMISSIONS.length;
		if(state != State.OUTER_SOLAR_SYSTEM && state != State.ANOMALY_DETECTED) return 0;
		long elapsed = Math.max(0L, Math.min(anomalyTicks, now - stageStarted));
		return (int) Math.min(TRANSMISSIONS.length, elapsed * (TRANSMISSIONS.length + 1) / anomalyTicks);
	}

	private int signal(long now, double travelProgress) {
		switch(state) {
			case AWAITING_LAUNCH:
			case SIGNAL_LOST: return 0;
			case IN_TRANSIT: return (int) Math.round(100 - 45 * travelProgress);
			default:
				// The signal begins to fall when the sixth packet reports a change of course.
				double fade = Math.max(0D, (progress(now, anomalyTicks) - 0.75D) * 4D);
				return Math.max(1, (int) Math.round(55 * (1D - fade)));
		}
	}

	List<String> telemetry(long now, boolean history) {
		List<String> lines = new ArrayList<>();
		lines.add("Deep Space Probe #" + id + " | " + state);
		int received = transmissions && (state == State.ANOMALY_DETECTED || state == State.SIGNAL_LOST) ? receivedPackets(now) : 0;
		if(history) {
			if(state != State.AWAITING_LAUNCH) lines.add(LAUNCH);
			if(state == State.OUTER_SOLAR_SYSTEM || state == State.ANOMALY_DETECTED || state == State.SIGNAL_LOST) lines.add(OUTER);
			for(int i = 0; i < received; i++) lines.add(TRANSMISSIONS[i]);
			if(state == State.SIGNAL_LOST) lines.add("[Ground station] Carrier lost. No further transmissions received.");
			if(state == State.AWAITING_LAUNCH) lines.add("No transmissions received.");
			return lines;
		}

		double fraction = state == State.AWAITING_LAUNCH ? 0D : state == State.IN_TRANSIT ? progress(now, travelTicks) : 1D;
		double distance = (destinationKm - departureKm) * fraction;
		lines.add(String.format(Locale.ROOT, "Distance traveled %,.0f km (%.2f AU)", distance, distance / AstronomyUtil.KM_IN_AU));
		long remaining = state == State.IN_TRANSIT ? Math.max(0L, travelTicks - (now - stageStarted)) : 0L;
		lines.add("Travel ETA " + (state == State.AWAITING_LAUNCH ? "pending launch" : (remaining + 19L) / 20L + " s")
				+ " | Signal " + signal(now, fraction) + "%");
		boolean detected = state == State.ANOMALY_DETECTED || state == State.SIGNAL_LOST;
		lines.add("Anomaly " + (detected ? "detected" : "none detected"));
		String last;
		if(received > 0) last = TRANSMISSIONS[received - 1];
		else if(state == State.AWAITING_LAUNCH) last = "none";
		else if(state == State.IN_TRANSIT) last = LAUNCH;
		else last = OUTER;
		lines.add("Last received transmission: " + last);
		return lines;
	}

	NBTTagCompound write() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("id", id);
		nbt.setString("state", state.name());
		nbt.setLong("stageStarted", stageStarted);
		nbt.setInteger("travelTicks", travelTicks);
		nbt.setInteger("anomalyTicks", anomalyTicks);
		nbt.setInteger("activationDelayTicks", activationDelayTicks);
		nbt.setDouble("departureKm", departureKm);
		nbt.setDouble("destinationKm", destinationKm);
		nbt.setBoolean("transmissions", transmissions);
		return nbt;
	}

	static DeepSpaceProbe read(NBTTagCompound nbt) {
		int id = nbt.getInteger("id");
		if(id <= 0) return null;
		DeepSpaceProbe probe = new DeepSpaceProbe(id);
		try {
			probe.state = State.valueOf(nbt.getString("state"));
		} catch(IllegalArgumentException ex) {
			return null;
		}
		probe.stageStarted = nbt.getLong("stageStarted");
		probe.travelTicks = Math.max(20, nbt.getInteger("travelTicks"));
		probe.anomalyTicks = Math.max(ProbeSettings.MIN_ANOMALY_TICKS, nbt.getInteger("anomalyTicks"));
		probe.activationDelayTicks = Math.max(0, nbt.getInteger("activationDelayTicks"));
		probe.departureKm = nbt.getDouble("departureKm");
		probe.destinationKm = nbt.getDouble("destinationKm");
		probe.transmissions = nbt.getBoolean("transmissions");
		return probe;
	}
}
