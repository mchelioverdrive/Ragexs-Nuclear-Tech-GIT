package api.hbm.energymk2;

import com.hbm.config.GeneralConfig;

/** Optional aggregate counters for the MK2 power-network scheduler. */
public final class PowerNetDiagnostics {

	private static long tick;
	private static long visits;
	private static long compatibilityVisits;
	private static long registrations;
	private static long refreshes;
	private static long topologyInvalidations;
	private static long supplyInvalidations;
	private static long demandInvalidations;
	private static long timeoutRemovals;
	private static long providerEndpoints;
	private static long receiverEndpoints;
	private static long maxProviderEndpoints;
	private static long maxReceiverEndpoints;
	private static long distributionNanos;

	private static long lastTick;
	private static long lastVisits;
	private static long lastCompatibilityVisits;
	private static long lastRegistrations;
	private static long lastRefreshes;
	private static long lastTopologyInvalidations;
	private static long lastSupplyInvalidations;
	private static long lastDemandInvalidations;
	private static long lastTimeoutRemovals;
	private static long lastProviderEndpoints;
	private static long lastReceiverEndpoints;
	private static long lastMaxProviderEndpoints;
	private static long lastMaxReceiverEndpoints;
	private static long lastDistributionNanos;
	private static long lastCleanSkipped;

	private static long totalVisits;
	private static long totalCompatibilityVisits;
	private static long totalRegistrations;
	private static long totalRefreshes;
	private static long totalTopologyInvalidations;
	private static long totalSupplyInvalidations;
	private static long totalDemandInvalidations;
	private static long totalTimeoutRemovals;
	private static long totalDistributionNanos;

	private PowerNetDiagnostics() { }

	public static boolean isEnabled() {
		return GeneralConfig.enablePowerNetDiagnostics;
	}

	public static void beginTick() {
		if(!isEnabled()) return;
		tick++;
	}

	public static void finishTick(long activeNetworks) {
		if(!isEnabled()) return;
		lastTick = tick;
		lastVisits = visits;
		lastCompatibilityVisits = compatibilityVisits;
		lastRegistrations = registrations;
		lastRefreshes = refreshes;
		lastTopologyInvalidations = topologyInvalidations;
		lastSupplyInvalidations = supplyInvalidations;
		lastDemandInvalidations = demandInvalidations;
		lastTimeoutRemovals = timeoutRemovals;
		lastProviderEndpoints = providerEndpoints;
		lastReceiverEndpoints = receiverEndpoints;
		lastMaxProviderEndpoints = maxProviderEndpoints;
		lastMaxReceiverEndpoints = maxReceiverEndpoints;
		lastDistributionNanos = distributionNanos;
		lastCleanSkipped = Math.max(0, activeNetworks - visits);
		clearCurrent();
	}

	private static void clearCurrent() {
		visits = 0;
		compatibilityVisits = 0;
		registrations = 0;
		refreshes = 0;
		topologyInvalidations = 0;
		supplyInvalidations = 0;
		demandInvalidations = 0;
		timeoutRemovals = 0;
		providerEndpoints = 0;
		receiverEndpoints = 0;
		maxProviderEndpoints = 0;
		maxReceiverEndpoints = 0;
		distributionNanos = 0;
	}

	public static long startDistribution() {
		return isEnabled() ? System.nanoTime() : 0L;
	}

	public static void finishDistribution(long started) {
		if(started != 0L) {
			long elapsed = System.nanoTime() - started;
			distributionNanos += elapsed;
			totalDistributionNanos += elapsed;
		}
	}

	public static void recordNetworkVisit(int providers, int receivers, int causes) {
		if(!isEnabled()) return;
		visits++;
		if((causes & PowerNetMK2.DIRTY_COMPATIBILITY) != 0) compatibilityVisits++;
		providerEndpoints += providers;
		receiverEndpoints += receivers;
		maxProviderEndpoints = Math.max(maxProviderEndpoints, providers);
		maxReceiverEndpoints = Math.max(maxReceiverEndpoints, receivers);
		totalVisits++;
		if((causes & PowerNetMK2.DIRTY_COMPATIBILITY) != 0) totalCompatibilityVisits++;
	}

	public static void recordRegistration() {
		if(!isEnabled()) return;
		registrations++;
		totalRegistrations++;
	}

	public static void recordRefresh() {
		if(!isEnabled()) return;
		refreshes++;
		totalRefreshes++;
	}

	public static void recordInvalidation(int cause) {
		if(!isEnabled()) return;
		if((cause & PowerNetMK2.DIRTY_TOPOLOGY) != 0) { topologyInvalidations++; totalTopologyInvalidations++; }
		if((cause & PowerNetMK2.DIRTY_SUPPLY) != 0) { supplyInvalidations++; totalSupplyInvalidations++; }
		if((cause & PowerNetMK2.DIRTY_DEMAND) != 0) { demandInvalidations++; totalDemandInvalidations++; }
	}

	public static void recordTimeoutRemoval() {
		if(!isEnabled()) return;
		timeoutRemovals++;
		totalTimeoutRemovals++;
	}

	public static String[] getReport() {
		if(!isEnabled()) return new String[] {
			"MK2 power diagnostics are disabled.",
			"Set 1.45_enablePowerNetDiagnostics=true in hbm.cfg and restart the server."
		};
		return new String[] {
			"MK2 power diagnostics, completed tick " + lastTick + ":",
			"networks visited/redistributed/clean-skipped/compat = " + (lastVisits + lastCleanSkipped) + "/" + lastVisits + "/" + lastCleanSkipped + "/" + lastCompatibilityVisits,
			"registrations/refreshes/timeouts = " + lastRegistrations + "/" + lastRefreshes + "/" + lastTimeoutRemovals,
			"topology/supply/demand invalidations = " + lastTopologyInvalidations + "/" + lastSupplyInvalidations + "/" + lastDemandInvalidations,
			"provider endpoints sum/max = " + lastProviderEndpoints + "/" + lastMaxProviderEndpoints + ", receiver endpoints sum/max = " + lastReceiverEndpoints + "/" + lastMaxReceiverEndpoints,
			"distribution time = " + lastDistributionNanos + " ns",
			"total distribution time = " + totalDistributionNanos + " ns",
			"totals visits/compat/registrations/refreshes/timeouts = " + totalVisits + "/" + totalCompatibilityVisits + "/" + totalRegistrations + "/" + totalRefreshes + "/" + totalTimeoutRemovals,
			"totals topology/supply/demand invalidations = " + totalTopologyInvalidations + "/" + totalSupplyInvalidations + "/" + totalDemandInvalidations
		};
	}
}
