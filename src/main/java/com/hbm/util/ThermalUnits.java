package com.hbm.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Player-facing SI presentation of legacy thermal values: 1 TU = 0.5 J, 1 TU/t = 10 W at 20 TPS. */
public final class ThermalUnits {

	private static final String[] PREFIXES = {"", "k", "M", "G", "T", "P", "E"};

	private ThermalUnits() { }

	public static String formatThermalEnergy(double tu) {
		return formatSi(tu * 0.5D, "J");
	}

	public static String formatThermalPower(double tuPerTick) {
		return formatSi(tuPerTick * 10D, "W") + " (J/S)";
	}

	public static String formatThermalEnergyPair(double storedTu, double capacityTu) {
		return formatThermalEnergy(storedTu) + " / " + formatThermalEnergy(capacityTu);
	}

	public static String formatThermalEnergyPerLiter(double tu, double liters) {
		if(liters == 0D) return formatThermalEnergy(tu) + " per 0 L";
		return formatSi(tu * 0.5D / liters, "J/L");
	}

	private static String formatSi(double value, String unit) {
		int prefix = 0;
		while(Math.abs(value) >= 1000D && prefix < PREFIXES.length - 1) {
			value /= 1000D;
			prefix++;
		}
		DecimalFormat formatter = new DecimalFormat(Math.abs(value) < 1D ? "0.######" : "0.###", DecimalFormatSymbols.getInstance(Locale.US));
		return formatter.format(value) + " " + PREFIXES[prefix] + unit;
	}
}
