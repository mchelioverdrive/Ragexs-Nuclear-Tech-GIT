package api.hbm.energymk2;

import com.hbm.config.GeneralConfig;

/** Optional aggregate counters for the MK2 power-network scheduler. */
public final class PowerNetDiagnostics {

	private static long tick;
	private static long processed;
	private static long attachments;
	private static long detachments;
	private static long topologyInvalidations;
	private static long supplyInvalidations;
	private static long demandInvalidations;
	private static long merges;
	private static long splits;
	private static long integrityRemovals;
	private static long providerEndpoints;
	private static long receiverEndpoints;
	private static long maxProviderEndpoints;
	private static long maxReceiverEndpoints;
	private static long distributionNanos;
	private static long storedTopologyNodes;
	private static long dirtyTopologyNodes;
	private static long topologyNodesProcessed;
	private static long deferredTopologyPasses;
	private static long conductorAttachments;
	private static long conductorDetachments;
	private static long topologyNanos;
	private static long fluidAttachments;
	private static long fluidDetachments;
	private static long fluidIntegrityRemovals;
	private static long chunkAttachments;
	private static long chunkDetachments;
	private static long loadedConductors;
	private static long fluidNetworks;
	private static long fluidNetworksProcessed;

	private static long lastTick;
	private static long lastActive;
	private static long lastProcessed;
	private static long lastAttachments;
	private static long lastDetachments;
	private static long lastTopologyInvalidations;
	private static long lastSupplyInvalidations;
	private static long lastDemandInvalidations;
	private static long lastMerges;
	private static long lastSplits;
	private static long lastIntegrityRemovals;
	private static long lastProviderEndpoints;
	private static long lastReceiverEndpoints;
	private static long lastMaxProviderEndpoints;
	private static long lastMaxReceiverEndpoints;
	private static long lastDistributionNanos;
	private static long lastStoredTopologyNodes;
	private static long lastDirtyTopologyNodes;
	private static long lastTopologyNodesProcessed;
	private static long lastDeferredTopologyPasses;
	private static long lastConductorAttachments;
	private static long lastConductorDetachments;
	private static long lastTopologyNanos;
	private static long lastFluidAttachments;
	private static long lastFluidDetachments;
	private static long lastFluidIntegrityRemovals;
	private static long lastChunkAttachments;
	private static long lastChunkDetachments;
	private static long lastLoadedConductors;
	private static long lastFluidNetworks;
	private static long lastFluidNetworksProcessed;

	private static long totalProcessed;
	private static long totalAttachments;
	private static long totalDetachments;
	private static long totalMerges;
	private static long totalSplits;
	private static long totalIntegrityRemovals;
	private static long totalDistributionNanos;

	private PowerNetDiagnostics() { }

	public static boolean isEnabled() { return GeneralConfig.enablePowerNetDiagnostics; }

	public static void beginTick() { if(isEnabled()) tick++; }

	public static void finishTick(long activeNetworks) {
		if(!isEnabled()) return;
		lastTick = tick;
		lastActive = activeNetworks;
		lastProcessed = processed;
		lastAttachments = attachments;
		lastDetachments = detachments;
		lastTopologyInvalidations = topologyInvalidations;
		lastSupplyInvalidations = supplyInvalidations;
		lastDemandInvalidations = demandInvalidations;
		lastMerges = merges;
		lastSplits = splits;
		lastIntegrityRemovals = integrityRemovals;
		lastProviderEndpoints = providerEndpoints;
		lastReceiverEndpoints = receiverEndpoints;
		lastMaxProviderEndpoints = maxProviderEndpoints;
		lastMaxReceiverEndpoints = maxReceiverEndpoints;
		lastDistributionNanos = distributionNanos;
		lastStoredTopologyNodes = storedTopologyNodes;
		lastDirtyTopologyNodes = dirtyTopologyNodes;
		lastTopologyNodesProcessed = topologyNodesProcessed;
		lastDeferredTopologyPasses = deferredTopologyPasses;
		lastConductorAttachments = conductorAttachments;
		lastConductorDetachments = conductorDetachments;
		lastTopologyNanos = topologyNanos;
		lastFluidAttachments = fluidAttachments;
		lastFluidDetachments = fluidDetachments;
		lastFluidIntegrityRemovals = fluidIntegrityRemovals;
		lastChunkAttachments = chunkAttachments;
		lastChunkDetachments = chunkDetachments;
		lastLoadedConductors = loadedConductors;
		lastFluidNetworks = fluidNetworks;
		lastFluidNetworksProcessed = fluidNetworksProcessed;
		clearCurrent();
	}

	private static void clearCurrent() {
		processed = 0;
		attachments = 0;
		detachments = 0;
		topologyInvalidations = 0;
		supplyInvalidations = 0;
		demandInvalidations = 0;
		merges = 0;
		splits = 0;
		integrityRemovals = 0;
		providerEndpoints = 0;
		receiverEndpoints = 0;
		maxProviderEndpoints = 0;
		maxReceiverEndpoints = 0;
		distributionNanos = 0;
		storedTopologyNodes = 0;
		dirtyTopologyNodes = 0;
		topologyNodesProcessed = 0;
		deferredTopologyPasses = 0;
		conductorAttachments = 0;
		conductorDetachments = 0;
		topologyNanos = 0;
		fluidAttachments = 0;
		fluidDetachments = 0;
		fluidIntegrityRemovals = 0;
		chunkAttachments = 0;
		chunkDetachments = 0;
		fluidNetworks = 0;
		fluidNetworksProcessed = 0;
	}

	public static long startDistribution() { return isEnabled() ? System.nanoTime() : 0L; }

	public static void finishDistribution(long started) {
		if(started == 0L) return;
		long elapsed = System.nanoTime() - started;
		distributionNanos += elapsed;
		totalDistributionNanos += elapsed;
	}

	public static void recordNetworkVisit(int providers, int receivers, int causes) {
		if(!isEnabled()) return;
		processed++;
		totalProcessed++;
	}

	public static void recordNetworkInventory(int providers, int receivers) {
		if(!isEnabled()) return;
		providerEndpoints += providers;
		receiverEndpoints += receivers;
		maxProviderEndpoints = Math.max(maxProviderEndpoints, providers);
		maxReceiverEndpoints = Math.max(maxReceiverEndpoints, receivers);
	}

	public static void recordAttachment() { if(isEnabled()) { attachments++; totalAttachments++; } }
	public static void recordDetachment() { if(isEnabled()) { detachments++; totalDetachments++; } }
	public static void recordMerge() { if(isEnabled()) { merges++; totalMerges++; } }
	public static void recordSplit() { if(isEnabled()) { splits++; totalSplits++; } }
	public static void recordIntegrityRemoval() { if(isEnabled()) { integrityRemovals++; totalIntegrityRemovals++; } }
	public static void recordConductorAttached() { if(isEnabled()) conductorAttachments++; }
	public static void recordConductorDetached() { if(isEnabled()) conductorDetachments++; }
	public static void recordFluidAttachment() { if(isEnabled()) fluidAttachments++; }
	public static void recordFluidDetachment() { if(isEnabled()) fluidDetachments++; }
	public static void recordFluidIntegrityRemoval() { if(isEnabled()) fluidIntegrityRemovals++; }
	public static void recordChunkAttachment() { loadedConductors++; if(isEnabled()) chunkAttachments++; }
	public static void recordChunkDetachment() { if(loadedConductors > 0) loadedConductors--; if(isEnabled()) chunkDetachments++; }
	public static void recordFluidNetworkInventory(int active, int processed) { if(isEnabled()) { fluidNetworks += active; fluidNetworksProcessed += processed; } }

	public static long startTopology() { return isEnabled() ? System.nanoTime() : 0L; }

	public static void finishTopology(long started) {
		if(started != 0L) topologyNanos += System.nanoTime() - started;
	}

	public static void recordTopologyInventory(int stored, int dirty, int processed, boolean deferred) {
		if(!isEnabled()) return;
		storedTopologyNodes += stored;
		dirtyTopologyNodes += dirty;
		topologyNodesProcessed += processed;
		if(deferred) deferredTopologyPasses++;
	}

	public static void recordInvalidation(int cause) {
		if(!isEnabled()) return;
		if((cause & PowerNetMK2.DIRTY_TOPOLOGY) != 0) topologyInvalidations++;
		if((cause & PowerNetMK2.DIRTY_SUPPLY) != 0) supplyInvalidations++;
		if((cause & PowerNetMK2.DIRTY_DEMAND) != 0) demandInvalidations++;
	}

	public static String[] getReport() {
		if(!isEnabled()) return new String[] {
			"MK2 power diagnostics are disabled.",
			"Set 1.45_enablePowerNetDiagnostics=true in hbm.cfg and restart the server."
		};
		return new String[] {
			"MK2 power diagnostics, completed tick " + lastTick + ":",
			"active/dirty-processed/clean-skipped = " + lastActive + "/" + lastProcessed + "/" + Math.max(0, lastActive - lastProcessed),
			"attachments/detachments/integrity removals = " + lastAttachments + "/" + lastDetachments + "/" + lastIntegrityRemovals,
			"topology/supply/demand invalidations = " + lastTopologyInvalidations + "/" + lastSupplyInvalidations + "/" + lastDemandInvalidations,
			"merges/splits = " + lastMerges + "/" + lastSplits,
			"topology stored/queued/processed/deferred = " + lastStoredTopologyNodes + "/" + lastDirtyTopologyNodes + "/" + lastTopologyNodesProcessed + "/" + lastDeferredTopologyPasses,
			"topology stable-not-visited; loaded/dormant conductors = " + Math.max(0, lastStoredTopologyNodes - lastTopologyNodesProcessed) + "; " + lastLoadedConductors + "/" + Math.max(0, lastStoredTopologyNodes - lastLoadedConductors),
			"conductor attach/detach; fluid endpoint attach/detach/integrity = " + lastConductorAttachments + "/" + lastConductorDetachments + "; " + lastFluidAttachments + "/" + lastFluidDetachments + "/" + lastFluidIntegrityRemovals,
			"chunk attach/detach; fluid active/processed/clean-skipped = " + lastChunkAttachments + "/" + lastChunkDetachments + "; " + lastFluidNetworks + "/" + lastFluidNetworksProcessed + "/" + Math.max(0, lastFluidNetworks - lastFluidNetworksProcessed),
			"active provider endpoints total/max = " + lastProviderEndpoints + "/" + lastMaxProviderEndpoints + ", receiver endpoints total/max = " + lastReceiverEndpoints + "/" + lastMaxReceiverEndpoints,
			"topology/distribution time = " + lastTopologyNanos + "/" + lastDistributionNanos + " ns; distribution total = " + totalDistributionNanos + " ns",
			"totals processed/attachments/detachments/merges/splits/integrity = " + totalProcessed + "/" + totalAttachments + "/" + totalDetachments + "/" + totalMerges + "/" + totalSplits + "/" + totalIntegrityRemovals
		};
	}
}
