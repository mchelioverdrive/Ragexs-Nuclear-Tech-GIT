package api.hbm.energymk2;

import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;

/** Electrical units used by RTM. Stored and transferred energy is exact half-joule quanta. */
public final class EnergyUnits {

	public static final int TICKS_PER_SECOND = 20;
	public static final int QUANTA_PER_JOULE = 2;
	public static final int QUANTA_PER_LEGACY_HE = 1;
	public static final int QUANTA_PER_RF = 5;
	public static final int QUANTA_PER_EU = 20;
	public static final int WATTS_PER_QUANTUM_PER_TICK = TICKS_PER_SECOND / QUANTA_PER_JOULE;
	public static final byte FORMAT_VERSION = 1;
	public static final String FORMAT_VERSION_KEY = "energyFormatVersion";
	public static final String ENERGY_KEY = "energyQuanta";
	public static final String CAPACITY_KEY = "energyCapacityQuanta";

	private EnergyUnits() { }

	public static double toJoules(long quanta) { return (double) quanta / QUANTA_PER_JOULE; }
	public static long joulesToQuanta(long joules) { return Math.multiplyExact(joules, QUANTA_PER_JOULE); }
	public static long legacyHeToQuanta(long he) { return he; }
	public static long quantaToLegacyHe(long quanta) { return quanta; }
	public static double quantaToLegacyHe(double quanta) { return quanta; }
	public static long rfToQuanta(long rf) { return Math.multiplyExact(rf, QUANTA_PER_RF); }
	public static long quantaToRf(long quanta) { return quanta / QUANTA_PER_RF; }
	public static long euToQuanta(long eu) { return Math.multiplyExact(eu, QUANTA_PER_EU); }
	public static long quantaToEu(long quanta) { return quanta / QUANTA_PER_EU; }

	/** Version zero stored the same exact quantum count under an HE-era key. */
	public static long readEnergyQuanta(NBTTagCompound data, String legacyKey) {
		if(data.getByte(FORMAT_VERSION_KEY) >= FORMAT_VERSION) return data.getLong(ENERGY_KEY);
		return legacyHeToQuanta(data.getLong(legacyKey));
	}

	public static void writeEnergyQuanta(NBTTagCompound data, long quanta) {
		data.setByte(FORMAT_VERSION_KEY, FORMAT_VERSION);
		data.setLong(ENERGY_KEY, quanta);
	}

	public static long readCapacityQuanta(NBTTagCompound data, String legacyKey) {
		if(data.getByte(FORMAT_VERSION_KEY) >= FORMAT_VERSION) return data.getLong(CAPACITY_KEY);
		return legacyHeToQuanta(data.getLong(legacyKey));
	}

	public static void writeCapacityQuanta(NBTTagCompound data, long capacityQuanta) {
		data.setByte(FORMAT_VERSION_KEY, FORMAT_VERSION);
		data.setLong(CAPACITY_KEY, capacityQuanta);
	}
	public static long quantaPerTickToWatts(long quantaPerTick) { return Math.multiplyExact(quantaPerTick, WATTS_PER_QUANTUM_PER_TICK); }
	public static long wattsToQuantaPerTick(long watts) { return watts / WATTS_PER_QUANTUM_PER_TICK; }

	/** Carries fractional tick budgets without allocation or long-term rounding loss. */
	public static final class PowerTickAccumulator {
		private long remainderWatts;

		public long nextQuanta(long watts) {
			long whole = watts / WATTS_PER_QUANTUM_PER_TICK;
			this.remainderWatts += watts % WATTS_PER_QUANTUM_PER_TICK;
			if(this.remainderWatts >= WATTS_PER_QUANTUM_PER_TICK) {
				whole++;
				this.remainderWatts -= WATTS_PER_QUANTUM_PER_TICK;
			}
			return whole;
		}

		public long getRemainderWatts() { return remainderWatts; }
		public void setRemainderWatts(long remainderWatts) {
			if(remainderWatts < 0 || remainderWatts >= WATTS_PER_QUANTUM_PER_TICK) throw new IllegalArgumentException("remainderWatts");
			this.remainderWatts = remainderWatts;
		}
	}

	public static String formatJoules(long quanta) { return format(toJoules(quanta), "J"); }
	public static String formatWatts(long watts) { return format(watts, "W"); }
	public static String formatQuantaPerTickAsWatts(long quantaPerTick) { return format((double) quantaPerTick * WATTS_PER_QUANTUM_PER_TICK, "W"); }
	public static String formatQuantaPerSecondAsWatts(long quantaPerSecond) { return format((double) quantaPerSecond / QUANTA_PER_JOULE, "W"); }
	public static double toWattHours(long quanta) { return toJoules(quanta) / 3600D; }

	private static String format(double value, String unit) {
		String[] prefixes = {"", "k", "M", "G", "T", "P", "E"};
		int index = 0;
		while(Math.abs(value) >= 1000D && index < prefixes.length - 1) {
			value /= 1000D;
			index++;
		}
		return String.format(Locale.US, "%.3g %s%s", value, prefixes[index], unit);
	}
}
