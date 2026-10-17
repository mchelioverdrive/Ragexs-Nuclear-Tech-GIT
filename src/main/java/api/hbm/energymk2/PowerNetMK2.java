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

	public long transferredEnergyQuantaThisTick = 0L;

	private static final ConnectionPriority[] PRIORITIES = ConnectionPriority.values();

	private final EndpointScratch<IEnergyProviderMK2> providerScratch = new EndpointScratch<IEnergyProviderMK2>();
	private final EndpointScratch<IEnergyReceiverMK2>[] receiverScratch = createReceiverScratch();
	private final EndpointScratch<IEnergyReceiverMK2>[] diodeReceiverScratch = createReceiverScratch();
	private final long[] demandScratch = new long[PRIORITIES.length];
	private final long[] diodeDemandScratch = new long[PRIORITIES.length];
	private int dirtyCauses;
	private long topologyChanges, deliveredInUpdate;

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

		this.completeMergeFrom(powerNetwork);
	}

	@Override
	public void completeMergeFrom(NodeNet network) {
		if(!(network instanceof PowerNetMK2)) {
			super.completeMergeFrom(network);
			return;
		}
		PowerNetMK2 powerNetwork = (PowerNetMK2) network;
		for(IEnergyReceiverMK2 receiver : new ArrayList<IEnergyReceiverMK2>(powerNetwork.receiverEntries.keySet())) this.addReceiver(receiver);
		for(IEnergyProviderMK2 provider : new ArrayList<IEnergyProviderMK2>(powerNetwork.providerEntries.keySet())) this.addProvider(provider);
		powerNetwork.destroy();
		PowerNetDiagnostics.recordMerge();
		this.markTopologyDirty();
	}

	@Override
	protected void onTopologyChanged() {
		this.markTopologyDirty();
	}

	public void markTopologyDirty() {
		this.topologyChanges++;
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

	@Override public void resetTrackers() { this.transferredEnergyQuantaThisTick = 0; }

	@Override
	public void update() {
		ElectricalWorkGuard guard = ElectricalWorkGuard.enter(this);
		if(guard == null) return;
		long diagnosticStart = PowerNetDiagnostics.startDistribution();
		long available = 0;
		deliveredInUpdate = 0;
		providerScratch.clear();
		try {
			// Snapshot membership before callbacks, which can detach/remove endpoints.
			for(IEnergyProviderMK2 provider : new ArrayList<IEnergyProviderMK2>(providerEntries.keySet())) {
				if(!guard.visit(provider)) break;
				if(isBadLink(provider)) continue;
				provider.prepareEnergyTransfer();
				long supply = Math.max(0, Math.min(provider.getStoredEnergyQuanta(), provider.getMaxOutputQuantaPerTick()));
				supply = Math.min(supply, Long.MAX_VALUE - available);
				if(supply > 0) { providerScratch.add(provider, supply); available += supply; }
			}
			if(providerScratch.size > 0) guard.source(providerScratch.get(0));
			guard.budget(available);
			distribute(available, receiverScratch, demandScratch, guard, true);
		} finally {
			try {
				// Always settle accepted deliveries, including on a diagnostic cutoff.
				long left = deliveredInUpdate;
				for(int i = 0; i < providerScratch.size && left > 0; i++) {
					long debit = proportional(deliveredInUpdate, providerScratch.amounts[i], available);
					debit = Math.min(left, debit);
					if(debit > 0) providerScratch.get(i).extractEnergyQuanta(debit);
					providerScratch.amounts[i] -= debit;
					left -= debit;
				}
				for(int i = 0; i < providerScratch.size && left > 0; i++) {
					long debit = Math.min(left, providerScratch.amounts[i]);
					if(debit > 0) providerScratch.get(i).extractEnergyQuanta(debit);
					left -= debit;
				}
				transferredEnergyQuantaThisTick = ElectricalWorkGuard.add(transferredEnergyQuantaThisTick, deliveredInUpdate);
				guard.delivered(deliveredInUpdate);
				if(guard.halted()) markSupplyDirty();
			} finally {
				providerScratch.clear();
				guard.close();
				PowerNetDiagnostics.finishDistribution(diagnosticStart);
			}
		}
	}

	/** Legacy HE entry point for addons. */
	@Deprecated
	public long sendPowerDiode(long legacyHe) {
		return EnergyUnits.quantaToLegacyHe(sendEnergyQuantaThroughDiode(EnergyUnits.legacyHeToQuanta(legacyHe)));
	}

	public long sendEnergyQuantaThroughDiode(long energyQuanta) {
		if(energyQuanta <= 0) return energyQuanta;
		ElectricalWorkGuard guard = ElectricalWorkGuard.enter(this);
		if(guard == null) return energyQuanta;
		long diagnosticStart = PowerNetDiagnostics.startDistribution();
		try {
			guard.budget(energyQuanta);
			long delivered = distribute(energyQuanta, diodeReceiverScratch, diodeDemandScratch, guard, false);
			guard.delivered(delivered);
			transferredEnergyQuantaThisTick = ElectricalWorkGuard.add(transferredEnergyQuantaThisTick, delivered);
			if(delivered > 0) markDemandDirty();
			return energyQuanta - delivered;
		} finally {
			guard.close();
			PowerNetDiagnostics.finishDistribution(diagnosticStart);
		}
	}

	/** Linear endpoint work; each receiver receives at most one offer per pass. */
	private long distribute(long available, EndpointScratch<IEnergyReceiverMK2>[] scratch, long[] demands, ElectricalWorkGuard guard, boolean debitProviders) {
		clearReceiverScratch(scratch, demands);
		if(available <= 0) return 0;
		for(IEnergyReceiverMK2 receiver : new ArrayList<IEnergyReceiverMK2>(receiverEntries.keySet())) {
			if(!guard.visit(receiver)) break;
			if(isBadLink(receiver)) continue;
			long demand = ElectricalWorkGuard.room(receiver);
			int priority = receiver.getPriority().ordinal();
			demand = Math.min(demand, Long.MAX_VALUE - demands[priority]);
			if(demand > 0) { scratch[priority].add(receiver, demand); demands[priority] += demand; }
		}
		long delivered = 0;
		try {
			for(int priority = PRIORITIES.length - 1; priority >= 0 && delivered < available; priority--) {
				long unallocated = Math.min(available - delivered, demands[priority]);
				long demandLeft = demands[priority];
				EndpointScratch<IEnergyReceiverMK2> endpoints = scratch[priority];
				for(int i = 0; i < endpoints.size && unallocated > 0; i++) {
					if(!guard.visit(endpoints.get(i))) return delivered;
					long demand = endpoints.amounts[i];
					long offer = proportional(unallocated, demand, demandLeft);
					demandLeft -= demand;
					unallocated -= offer;
					IEnergyReceiverMK2 receiver = endpoints.get(i);
					if(!receiverEntries.containsKey(receiver) || isBadLink(receiver)) continue;
					long accepted = guard.offer(receiver, offer);
					delivered += accepted;
					if(debitProviders) deliveredInUpdate += accepted;
				}
				// Next priority sees the actual remaining supply, not cumulative double subtraction.
			}
			return delivered;
		} finally { clearReceiverScratch(scratch, demands); }
	}

	private static long proportional(long amount, long weight, long total) {
		if(amount == 0 || weight == 0) return 0;
		if(amount <= Long.MAX_VALUE / weight) return amount * weight / total;
		return java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(weight))
				.divide(java.math.BigInteger.valueOf(total)).longValueExact();
	}

	public String describeTopology() {
		return "network=" + Integer.toHexString(System.identityHashCode(this))
				+ ",dim=" + (getWorld() == null ? "none" : getWorld().provider.dimensionId)
				+ ",cables=" + links.size() + ",providers=" + providerEntries.size() + ",receivers=" + receiverEntries.size()
				+ ",topologyChanges=" + topologyChanges + ",repairing=" + isTopologyRepairing();
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
