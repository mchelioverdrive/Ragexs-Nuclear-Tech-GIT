package com.hbm.inventory.fluid;

import java.util.Locale;

/** Player-facing volume formatting. One existing Forge fluid unit is one liter. */
public final class FluidVolume {

	private FluidVolume() { }

	public static String format(long liters) {
		return String.format(Locale.US, "%,d L", liters);
	}

	public static String formatPair(long liters, long capacityLiters) {
		return String.format(Locale.US, "%,d / %,d L", liters, capacityLiters);
	}

	public static String formatPerTick(long litersPerTick) {
		return String.format(Locale.US, "%,d L/t", litersPerTick);
	}

	public static String formatPerSecond(long litersPerSecond) {
		return String.format(Locale.US, "%,d L/s", litersPerSecond);
	}
}
