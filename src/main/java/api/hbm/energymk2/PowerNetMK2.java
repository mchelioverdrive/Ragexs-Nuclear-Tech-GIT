package api.hbm.energymk2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;

import java.util.Map.Entry;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.energymk2.Nodespace.PowerNode;

/**
 * Technically MK3 since it's now UNINOS compatible, although UNINOS was build out of 95% nodespace code
 *
 * @author hbm
 */
public class PowerNetMK2 extends NodeNet<IEnergyReceiverMK2, IEnergyProviderMK2, PowerNode> {

	public static final int DIRTY_TOPOLOGY = 1;
	public static final int DIRTY_SUPPLY = 2;
	public static final int DIRTY_DEMAND = 4;
	public static final int DIRTY_COMPATIBILITY = 8;

	public long energyTracker = 0L;

	protected static int timeout = 3_000;
	private static final ConnectionPriority[] PRIORITIES = ConnectionPriority.values();

	private final EndpointScratch<IEnergyProviderMK2> providerScratch = new EndpointScratch<IEnergyProviderMK2>();
	private final EndpointScratch<IEnergyReceiverMK2>[] receiverScratch = createReceiverScratch();
	private final EndpointScratch<IEnergyReceiverMK2>[] diodeReceiverScratch = createReceiverScratch();
	private final long[] demandScratch = new long[PRIORITIES.length];
	private final long[] diodeDemandScratch = new long[PRIORITIES.length];
	private final Set<IEnergyProviderMK2> persistentProviders = Collections.newSetFromMap(new IdentityHashMap<IEnergyProviderMK2, Boolean>());
	private final Set<IEnergyReceiverMK2> persistentReceivers = Collections.newSetFromMap(new IdentityHashMap<IEnergyReceiverMK2, Boolean>());
	private int dirtyCauses;

	private static final IdentityHashMap<IEnergyProviderMK2, Set<PowerNetMK2>> providerMemberships = new IdentityHashMap<IEnergyProviderMK2, Set<PowerNetMK2>>();
	private static final IdentityHashMap<IEnergyReceiverMK2, Set<PowerNetMK2>> receiverMemberships = new IdentityHashMap<IEnergyReceiverMK2, Set<PowerNetMK2>>();

	@Override
	public void addReceiver(IEnergyReceiverMK2 receiver) {
		boolean registered = this.receiverEntries.containsKey(receiver);
		this.receiverEntries.put(receiver, System.currentTimeMillis());
		this.persistentReceivers.remove(receiver);
		trackReceiver(receiver, this);
		if(registered) PowerNetDiagnostics.recordRefresh();
		else PowerNetDiagnostics.recordRegistration();
		this.refreshLegacyStatus();
		this.markDemandDirty();
	}

	public void addReceiverPersistent(IEnergyReceiverMK2 receiver) {
		boolean registered = this.receiverEntries.containsKey(receiver);
		if(!registered) {
			this.receiverEntries.put(receiver, System.currentTimeMillis());
			trackReceiver(receiver, this);
			PowerNetDiagnostics.recordRegistration();
		}
		if(this.persistentReceivers.add(receiver) || !registered) this.markDemandDirty();
		this.refreshLegacyStatus();
	}

	@Override
	public void removeReceiver(IEnergyReceiverMK2 receiver) {
		if(this.receiverEntries.remove(receiver) == null) return;
		this.persistentReceivers.remove(receiver);
		untrackReceiver(receiver, this);
		this.refreshLegacyStatus();
		this.markDemandDirty();
	}

	@Override
	public void addProvider(IEnergyProviderMK2 provider) {
		boolean registered = this.providerEntries.containsKey(provider);
		this.providerEntries.put(provider, System.currentTimeMillis());
		this.persistentProviders.remove(provider);
		trackProvider(provider, this);
		if(registered) PowerNetDiagnostics.recordRefresh();
		else PowerNetDiagnostics.recordRegistration();
		this.refreshLegacyStatus();
		this.markSupplyDirty();
	}

	public void addProviderPersistent(IEnergyProviderMK2 provider) {
		boolean registered = this.providerEntries.containsKey(provider);
		if(!registered) {
			this.providerEntries.put(provider, System.currentTimeMillis());
			trackProvider(provider, this);
			PowerNetDiagnostics.recordRegistration();
		}
		if(this.persistentProviders.add(provider) || !registered) this.markSupplyDirty();
		this.refreshLegacyStatus();
	}

	@Override
	public void removeProvider(IEnergyProviderMK2 provider) {
		if(this.providerEntries.remove(provider) == null) return;
		this.persistentProviders.remove(provider);
		untrackProvider(provider, this);
		this.refreshLegacyStatus();
		this.markSupplyDirty();
	}

	@Override
	public void joinNetworks(NodeNet network) {
		if(network == this || !(network instanceof PowerNetMK2)) {
			super.joinNetworks(network);
			return;
		}

		PowerNetMK2 powerNetwork = (PowerNetMK2) network;
		List<PowerNode> oldNodes = new ArrayList<PowerNode>(powerNetwork.links);
		for(PowerNode conductor : oldNodes) this.forceJoinLink(conductor);
		powerNetwork.links.clear();

		for(IEnergyReceiverMK2 receiver : powerNetwork.receiverEntries.keySet()) {
			if(powerNetwork.persistentReceivers.contains(receiver)) this.addReceiverPersistent(receiver);
			else this.addReceiver(receiver);
		}
		for(IEnergyProviderMK2 provider : powerNetwork.providerEntries.keySet()) {
			if(powerNetwork.persistentProviders.contains(provider)) this.addProviderPersistent(provider);
			else this.addProvider(provider);
		}
		powerNetwork.destroy();
		this.markTopologyDirty();
	}

	@Override
	protected void onTopologyChanged() {
		this.markTopologyDirty();
	}

	public void markTopologyDirty() { this.markDirty(DIRTY_TOPOLOGY); }
	public void markSupplyDirty() { this.markDirty(DIRTY_SUPPLY); }
	public void markDemandDirty() { this.markDirty(DIRTY_DEMAND); }
	public void markCompatibilityDirty() { this.markDirty(DIRTY_COMPATIBILITY); }

	private void markDirty(int cause) {
		if(!this.isValid()) return;
		this.dirtyCauses |= cause;
		if(cause != DIRTY_COMPATIBILITY) PowerNetDiagnostics.recordInvalidation(cause);
		UniNodespace.markPowerNetworkDirty(this);
	}

	public int consumeDirtyCauses() {
		int causes = this.dirtyCauses;
		this.dirtyCauses = 0;
		return causes;
	}

	private void refreshLegacyStatus() {
		boolean legacy = this.receiverEntries.size() > this.persistentReceivers.size() || this.providerEntries.size() > this.persistentProviders.size();
		UniNodespace.setPowerNetworkLegacy(this, legacy);
	}

	public static void markReceiverDemandDirty(IEnergyReceiverMK2 receiver) {
		Set<PowerNetMK2> networks = receiverMemberships.get(receiver);
		if(networks == null) return;
		for(PowerNetMK2 network : networks) network.markDemandDirty();
	}

	public static void markProviderSupplyDirty(IEnergyProviderMK2 provider) {
		Set<PowerNetMK2> networks = providerMemberships.get(provider);
		if(networks == null) return;
		for(PowerNetMK2 network : networks) network.markSupplyDirty();
	}

	@Override public void resetTrackers() { this.energyTracker = 0; }

	@Override
	public void update() {
		long diagnosticStart = PowerNetDiagnostics.startDistribution();
		this.providerScratch.clear();
		clearReceiverScratch(this.receiverScratch, this.demandScratch);

		if(providerEntries.isEmpty() && receiverEntries.isEmpty()) {
			PowerNetDiagnostics.finishDistribution(diagnosticStart);
			return;
		}

		long timestamp = System.currentTimeMillis();

		long powerAvailable = 0;

		// sum up available power
		Iterator<Entry<IEnergyProviderMK2, Long>> provIt = providerEntries.entrySet().iterator();
		while(provIt.hasNext()) {
			Entry<IEnergyProviderMK2, Long> entry = provIt.next();
			boolean timedOut = !this.persistentProviders.contains(entry.getKey()) && timestamp - entry.getValue() > timeout;
			if(timedOut || isBadLink(entry.getKey())) {
				provIt.remove();
				this.persistentProviders.remove(entry.getKey());
				untrackProvider(entry.getKey(), this);
				if(timedOut) PowerNetDiagnostics.recordTimeoutRemoval();
				continue;
			}
			IEnergyProviderMK2 provider = entry.getKey();
			long src = Math.min(provider.getPower(), provider.getProviderSpeed());
			if(src > 0) {
				this.providerScratch.add(provider, src);
				powerAvailable += src;
			}
		}

		// sum up total demand, categorized by priority
		long totalDemand = 0;

		Iterator<Entry<IEnergyReceiverMK2, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Entry<IEnergyReceiverMK2, Long> entry = recIt.next();
			boolean timedOut = !this.persistentReceivers.contains(entry.getKey()) && timestamp - entry.getValue() > timeout;
			if(timedOut || isBadLink(entry.getKey())) {
				recIt.remove();
				this.persistentReceivers.remove(entry.getKey());
				untrackReceiver(entry.getKey(), this);
				if(timedOut) PowerNetDiagnostics.recordTimeoutRemoval();
				continue;
			}
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			if(rec > 0) {
				int p = receiver.getPriority().ordinal();
				this.receiverScratch[p].add(receiver, rec);
				this.demandScratch[p] += rec;
				totalDemand += rec;
			}
		}
		this.refreshLegacyStatus();

		long toTransfer = Math.min(powerAvailable, totalDemand);
		long energyUsed = 0;

		// add power to receivers, ordered by priority
		for(int i = PRIORITIES.length - 1; i >= 0; i--) {
			EndpointScratch<IEnergyReceiverMK2> list = this.receiverScratch[i];
			long priorityDemand = this.demandScratch[i];

			for(int j = 0; j < list.size; j++) {
				long requested = list.amounts[j];
				double weight = (double) requested / (double) priorityDemand;
				long toSend = (long) Math.min(Math.max(toTransfer * weight, 0D), requested);
				energyUsed += (toSend - list.get(j).transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		this.energyTracker += energyUsed;
		long leftover = energyUsed;

		// remove power from providers
		for(int i = 0; i < this.providerScratch.size; i++) {
			double weight = (double) this.providerScratch.amounts[i] / (double) powerAvailable;
			long toUse = (long) Math.max(energyUsed * weight, 0D);
			this.providerScratch.get(i).usePower(toUse);
			leftover -= toUse;
		}

		// rounding error compensation, detects surplus that hasn't been used and removes it from random providers
		int iterationsLeft = 100; // whiles without emergency brakes are a bad idea
		while(iterationsLeft > 0 && leftover > 0 && this.providerScratch.size > 0) {
			iterationsLeft--;

			IEnergyProviderMK2 scapegoat = this.providerScratch.get(rand.nextInt(this.providerScratch.size));

			long toUse = Math.min(leftover, scapegoat.getPower());
			scapegoat.usePower(toUse);
			leftover -= toUse;
		}
		PowerNetDiagnostics.finishDistribution(diagnosticStart);
	}

	public long sendPowerDiode(long power) {
		long diagnosticStart = PowerNetDiagnostics.startDistribution();
		clearReceiverScratch(this.diodeReceiverScratch, this.diodeDemandScratch);

		if(receiverEntries.isEmpty()) {
			PowerNetDiagnostics.finishDistribution(diagnosticStart);
			return power;
		}

		long timestamp = System.currentTimeMillis();

		long totalDemand = 0;

		Iterator<Entry<IEnergyReceiverMK2, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Entry<IEnergyReceiverMK2, Long> entry = recIt.next();
			boolean timedOut = !this.persistentReceivers.contains(entry.getKey()) && timestamp - entry.getValue() > timeout;
			if(timedOut || isBadLink(entry.getKey())) {
				recIt.remove();
				this.persistentReceivers.remove(entry.getKey());
				untrackReceiver(entry.getKey(), this);
				if(timedOut) PowerNetDiagnostics.recordTimeoutRemoval();
				continue;
			}
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			int p = receiver.getPriority().ordinal();
			this.diodeReceiverScratch[p].add(receiver, rec);
			this.diodeDemandScratch[p] += rec;
			totalDemand += rec;
		}
		this.refreshLegacyStatus();

		long toTransfer = Math.min(power, totalDemand);
		long energyUsed = 0;

		for(int i = PRIORITIES.length - 1; i >= 0; i--) {
			EndpointScratch<IEnergyReceiverMK2> list = this.diodeReceiverScratch[i];
			long priorityDemand = this.diodeDemandScratch[i];

			for(int j = 0; j < list.size; j++) {
				long requested = list.amounts[j];
				double weight = (double) requested / (double) priorityDemand;
				long toSend = (long) Math.max(toTransfer * weight, 0D);
				energyUsed += (toSend - list.get(j).transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		this.energyTracker += energyUsed;
		if(energyUsed > 0) this.markDemandDirty();
		PowerNetDiagnostics.finishDistribution(diagnosticStart);

		return power - energyUsed;
	}

	@Override
	public void destroy() {
		for(IEnergyReceiverMK2 receiver : this.receiverEntries.keySet()) untrackReceiver(receiver, this);
		for(IEnergyProviderMK2 provider : this.providerEntries.keySet()) untrackProvider(provider, this);
		this.persistentReceivers.clear();
		this.persistentProviders.clear();
		this.dirtyCauses = 0;
		super.destroy();
	}

	private static void trackReceiver(IEnergyReceiverMK2 receiver, PowerNetMK2 network) {
		Set<PowerNetMK2> networks = receiverMemberships.get(receiver);
		if(networks == null) {
			networks = Collections.newSetFromMap(new IdentityHashMap<PowerNetMK2, Boolean>());
			receiverMemberships.put(receiver, networks);
		}
		networks.add(network);
	}

	private static void untrackReceiver(IEnergyReceiverMK2 receiver, PowerNetMK2 network) {
		Set<PowerNetMK2> networks = receiverMemberships.get(receiver);
		if(networks == null) return;
		networks.remove(network);
		if(networks.isEmpty()) receiverMemberships.remove(receiver);
	}

	private static void trackProvider(IEnergyProviderMK2 provider, PowerNetMK2 network) {
		Set<PowerNetMK2> networks = providerMemberships.get(provider);
		if(networks == null) {
			networks = Collections.newSetFromMap(new IdentityHashMap<PowerNetMK2, Boolean>());
			providerMemberships.put(provider, networks);
		}
		networks.add(network);
	}

	private static void untrackProvider(IEnergyProviderMK2 provider, PowerNetMK2 network) {
		Set<PowerNetMK2> networks = providerMemberships.get(provider);
		if(networks == null) return;
		networks.remove(network);
		if(networks.isEmpty()) providerMemberships.remove(provider);
	}

	private static void clearReceiverScratch(EndpointScratch<IEnergyReceiverMK2>[] receivers, long[] demand) {
		for(int i = 0; i < receivers.length; i++) {
			receivers[i].clear();
			demand[i] = 0;
		}
	}

	@SuppressWarnings("unchecked")
	private static EndpointScratch<IEnergyReceiverMK2>[] createReceiverScratch() {
		EndpointScratch<IEnergyReceiverMK2>[] scratch = new EndpointScratch[PRIORITIES.length];
		for(int i = 0; i < scratch.length; i++) scratch[i] = new EndpointScratch<IEnergyReceiverMK2>();
		return scratch;
	}

	private static class EndpointScratch<T> {
		private Object[] endpoints = new Object[4];
		private long[] amounts = new long[4];
		private int size;

		private void clear() {
			for(int i = 0; i < this.size; i++) this.endpoints[i] = null;
			this.size = 0;
		}

		private void add(T endpoint, long amount) {
			if(this.size == this.endpoints.length) {
				int capacity = this.size << 1;
				Object[] grownEndpoints = new Object[capacity];
				long[] grownAmounts = new long[capacity];
				System.arraycopy(this.endpoints, 0, grownEndpoints, 0, this.size);
				System.arraycopy(this.amounts, 0, grownAmounts, 0, this.size);
				this.endpoints = grownEndpoints;
				this.amounts = grownAmounts;
			}
			this.endpoints[this.size] = endpoint;
			this.amounts[this.size] = amount;
			this.size++;
		}

		@SuppressWarnings("unchecked")
		private T get(int index) {
			return (T) this.endpoints[index];
		}
	}
}
