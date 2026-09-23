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
import net.minecraft.world.World;

/**
 * Technically MK3 since it's now UNINOS compatible, although UNINOS was build out of 95% nodespace code
 *
 * @author hbm
 */
public class PowerNetMK2 extends NodeNet<IEnergyReceiverMK2, IEnergyProviderMK2, PowerNode> {

	public static final int DIRTY_TOPOLOGY = 1;
	public static final int DIRTY_SUPPLY = 2;
	public static final int DIRTY_DEMAND = 4;

	public long energyTracker = 0L;

	private static final ConnectionPriority[] PRIORITIES = ConnectionPriority.values();

	private final EndpointScratch<IEnergyProviderMK2> providerScratch = new EndpointScratch<IEnergyProviderMK2>();
	private final EndpointScratch<IEnergyReceiverMK2>[] receiverScratch = createReceiverScratch();
	private final EndpointScratch<IEnergyReceiverMK2>[] diodeReceiverScratch = createReceiverScratch();
	private final long[] demandScratch = new long[PRIORITIES.length];
	private final long[] diodeDemandScratch = new long[PRIORITIES.length];
	private int dirtyCauses;

	private static final IdentityHashMap<IEnergyProviderMK2, Set<PowerNetMK2>> providerMemberships = new IdentityHashMap<IEnergyProviderMK2, Set<PowerNetMK2>>();
	private static final IdentityHashMap<IEnergyReceiverMK2, Set<PowerNetMK2>> receiverMemberships = new IdentityHashMap<IEnergyReceiverMK2, Set<PowerNetMK2>>();

	@Override
	public void addReceiver(IEnergyReceiverMK2 receiver) {
		if(this.receiverEntries.containsKey(receiver)) return;
		this.receiverEntries.put(receiver, 0L);
		trackReceiver(receiver, this);
		PowerNetDiagnostics.recordAttachment();
		this.markDemandDirty();
	}

	public void addReceiverPersistent(IEnergyReceiverMK2 receiver) {
		this.addReceiver(receiver);
	}

	@Override
	public void removeReceiver(IEnergyReceiverMK2 receiver) {
		if(this.receiverEntries.remove(receiver) == null) return;
		untrackReceiver(receiver, this);
		PowerNetDiagnostics.recordDetachment();
		this.markDemandDirty();
	}

	@Override
	public void addProvider(IEnergyProviderMK2 provider) {
		if(this.providerEntries.containsKey(provider)) return;
		this.providerEntries.put(provider, 0L);
		trackProvider(provider, this);
		PowerNetDiagnostics.recordAttachment();
		this.markSupplyDirty();
	}

	public void addProviderPersistent(IEnergyProviderMK2 provider) {
		this.addProvider(provider);
	}

	@Override
	public void removeProvider(IEnergyProviderMK2 provider) {
		if(this.providerEntries.remove(provider) == null) return;
		untrackProvider(provider, this);
		PowerNetDiagnostics.recordDetachment();
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

		for(IEnergyReceiverMK2 receiver : powerNetwork.receiverEntries.keySet()) this.addReceiver(receiver);
		for(IEnergyProviderMK2 provider : powerNetwork.providerEntries.keySet()) this.addProvider(provider);
		powerNetwork.destroy();
		PowerNetDiagnostics.recordMerge();
		this.markTopologyDirty();
	}

	@Override
	protected void onTopologyChanged() {
		this.markTopologyDirty();
	}

	public void markTopologyDirty() {
		PowerNetEndpointRegistry.markTopologyDirty(this.getWorld());
		this.markDirty(DIRTY_TOPOLOGY);
	}
	public void markSupplyDirty() { this.markDirty(DIRTY_SUPPLY); }
	public void markDemandDirty() { this.markDirty(DIRTY_DEMAND); }

	private void markDirty(int cause) {
		if(!this.isValid()) return;
		boolean newlyDirty = (this.dirtyCauses & cause) != cause;
		this.dirtyCauses |= cause;
		if(newlyDirty) PowerNetDiagnostics.recordInvalidation(cause);
		UniNodespace.markPowerNetworkDirty(this);
	}

	public int consumeDirtyCauses() {
		int causes = this.dirtyCauses;
		this.dirtyCauses = 0;
		return causes;
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

	public static void markEndpointStateDirty(IEnergyHandlerMK2 endpoint) {
		if(endpoint instanceof IEnergyProviderMK2) markProviderSupplyDirty((IEnergyProviderMK2) endpoint);
		if(endpoint instanceof IEnergyReceiverMK2) markReceiverDemandDirty((IEnergyReceiverMK2) endpoint);
	}

	public static void detachEndpoint(IEnergyHandlerMK2 endpoint) {
		PowerNetEndpointRegistry.detach(endpoint);
	}

	static void detachEndpointMemberships(IEnergyHandlerMK2 endpoint) {
		if(endpoint instanceof IEnergyReceiverMK2) {
			Set<PowerNetMK2> networks = receiverMemberships.get((IEnergyReceiverMK2) endpoint);
			if(networks != null) {
				List<PowerNetMK2> copy = new ArrayList<PowerNetMK2>(networks);
				for(PowerNetMK2 network : copy) network.removeReceiver((IEnergyReceiverMK2) endpoint);
			}
		}
		if(endpoint instanceof IEnergyProviderMK2) {
			Set<PowerNetMK2> networks = providerMemberships.get((IEnergyProviderMK2) endpoint);
			if(networks != null) {
				List<PowerNetMK2> copy = new ArrayList<PowerNetMK2>(networks);
				for(PowerNetMK2 network : copy) network.removeProvider((IEnergyProviderMK2) endpoint);
			}
		}
	}

	public static void reconcileWorldEndpoints(World world) {
		PowerNetEndpointRegistry.reconcileWorldIfDirty(world);
	}

	public static void auditWorldEndpoints(World world) {
		PowerNetEndpointRegistry.auditWorld(world);
	}

	public static void detachWorldEndpoints(World world) {
		PowerNetEndpointRegistry.detachWorld(world);
	}

	public void auditInvalidEndpoints() {
		boolean supplyRemoved = false;
		boolean demandRemoved = false;
		Iterator<Entry<IEnergyProviderMK2, Long>> providers = this.providerEntries.entrySet().iterator();
		while(providers.hasNext()) {
			IEnergyProviderMK2 provider = providers.next().getKey();
			if(!isBadLink(provider)) continue;
			providers.remove();
			untrackProvider(provider, this);
			PowerNetDiagnostics.recordDetachment();
			PowerNetDiagnostics.recordIntegrityRemoval();
			supplyRemoved = true;
		}
		Iterator<Entry<IEnergyReceiverMK2, Long>> receivers = this.receiverEntries.entrySet().iterator();
		while(receivers.hasNext()) {
			IEnergyReceiverMK2 receiver = receivers.next().getKey();
			if(!isBadLink(receiver)) continue;
			receivers.remove();
			untrackReceiver(receiver, this);
			PowerNetDiagnostics.recordDetachment();
			PowerNetDiagnostics.recordIntegrityRemoval();
			demandRemoved = true;
		}
		if(supplyRemoved) this.markSupplyDirty();
		if(demandRemoved) this.markDemandDirty();
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

		long powerAvailable = 0;

		// sum up available power
		Iterator<Entry<IEnergyProviderMK2, Long>> provIt = providerEntries.entrySet().iterator();
		while(provIt.hasNext()) {
			Entry<IEnergyProviderMK2, Long> entry = provIt.next();
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
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			if(rec > 0) {
				int p = receiver.getPriority().ordinal();
				this.receiverScratch[p].add(receiver, rec);
				this.demandScratch[p] += rec;
				totalDemand += rec;
			}
		}
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

		long totalDemand = 0;

		Iterator<Entry<IEnergyReceiverMK2, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Entry<IEnergyReceiverMK2, Long> entry = recIt.next();
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			int p = receiver.getPriority().ordinal();
			this.diodeReceiverScratch[p].add(receiver, rec);
			this.diodeDemandScratch[p] += rec;
			totalDemand += rec;
		}
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
		PowerNetEndpointRegistry.markTopologyDirty(this.getWorld());
		for(IEnergyReceiverMK2 receiver : this.receiverEntries.keySet()) {
			untrackReceiver(receiver, this);
			PowerNetDiagnostics.recordDetachment();
		}
		for(IEnergyProviderMK2 provider : this.providerEntries.keySet()) {
			untrackProvider(provider, this);
			PowerNetDiagnostics.recordDetachment();
		}
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
