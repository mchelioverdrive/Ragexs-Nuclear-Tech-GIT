package api.hbm.fluidmk2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.Tuple.Pair;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.energymk2.PowerNetDiagnostics;
import api.hbm.fluid.IFluidConnector;
import api.hbm.fluid.PipeNet;

/** Persistent, dirty-scheduled fluid network. */
public class FluidNetMK2 extends NodeNet<IFluidConnector, IFluidProviderMK2, FluidNode> {

	public static final int DIRTY_TOPOLOGY = 1;
	public static final int DIRTY_SUPPLY = 2;
	public static final int DIRTY_DEMAND = 4;
	private static final int[] FULL_PRESSURE_RANGE = new int[] {0, IFluidUserMK2.HIGHEST_VALID_PRESSURE};
	private static final IdentityHashMap<IFluidProviderMK2, Set<FluidNetMK2>> providerMemberships = new IdentityHashMap<IFluidProviderMK2, Set<FluidNetMK2>>();
	private static final IdentityHashMap<IFluidConnector, Set<FluidNetMK2>> receiverMemberships = new IdentityHashMap<IFluidConnector, Set<FluidNetMK2>>();

	public long fluidTracker;
	private long trackerTick = Long.MIN_VALUE;
	protected FluidType type;
	private int dirtyCauses;

	public long[] fluidAvailable = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
	public List<Pair<IFluidProviderMK2, Long>>[] providers = new ArrayList[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
	public long[][] fluidDemand = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1][ConnectionPriority.values().length];
	public List<Pair<IFluidConnector, Long>>[][] receivers = new ArrayList[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1][ConnectionPriority.values().length];
	public long[] transfered = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];

	public FluidNetMK2(FluidType type) {
		this.type = type;
		for(int pressure = 0; pressure <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; pressure++) {
			providers[pressure] = new ArrayList<Pair<IFluidProviderMK2, Long>>();
			for(int priority = 0; priority < ConnectionPriority.values().length; priority++) receivers[pressure][priority] = new ArrayList<Pair<IFluidConnector, Long>>();
		}
	}

	public FluidType getType() { return this.type; }

	@Override
	public void destroy() {
		FluidNetEndpointRegistry.markTopologyDirty(this.getWorld());
		for(IFluidConnector receiver : this.receiverEntries.keySet()) {
			untrackReceiver(receiver, this);
			PowerNetDiagnostics.recordFluidDetachment();
		}
		for(IFluidProviderMK2 provider : this.providerEntries.keySet()) {
			untrackProvider(provider, this);
			PowerNetDiagnostics.recordFluidDetachment();
		}
		this.dirtyCauses = 0;
		PipeNet.release(this);
		super.destroy();
	}

	@Override
	public void completeMergeFrom(NodeNet network) {
		if(!(network instanceof FluidNetMK2)) {
			super.completeMergeFrom(network);
			return;
		}
		FluidNetMK2 fluidNetwork = (FluidNetMK2) network;
		for(IFluidConnector receiver : new ArrayList<IFluidConnector>(fluidNetwork.receiverEntries.keySet())) this.addReceiver(receiver);
		for(IFluidProviderMK2 provider : new ArrayList<IFluidProviderMK2>(fluidNetwork.providerEntries.keySet())) this.addProvider(provider);
		fluidNetwork.destroy();
		PowerNetDiagnostics.recordMerge();
		this.markDirty(DIRTY_TOPOLOGY);
	}

	@Override
	public void addReceiver(IFluidConnector receiver) {
		if(this.receiverEntries.containsKey(receiver)) return;
		this.receiverEntries.put(receiver, 0L);
		trackReceiver(receiver, this);
		PowerNetDiagnostics.recordFluidAttachment();
		this.markDemandDirty();
	}

	@Override
	public void removeReceiver(IFluidConnector receiver) {
		if(this.receiverEntries.remove(receiver) == null) return;
		untrackReceiver(receiver, this);
		PowerNetDiagnostics.recordFluidDetachment();
		this.markDemandDirty();
	}

	@Override
	public void addProvider(IFluidProviderMK2 provider) {
		if(this.providerEntries.containsKey(provider)) return;
		this.providerEntries.put(provider, 0L);
		trackProvider(provider, this);
		PowerNetDiagnostics.recordFluidAttachment();
		this.markSupplyDirty();
	}

	@Override
	public void removeProvider(IFluidProviderMK2 provider) {
		if(this.providerEntries.remove(provider) == null) return;
		untrackProvider(provider, this);
		PowerNetDiagnostics.recordFluidDetachment();
		this.markSupplyDirty();
	}

	@Override
	protected void onTopologyChanged() {
		FluidNetEndpointRegistry.markTopologyDirty(this.getWorld());
		this.markDirty(DIRTY_TOPOLOGY);
	}

	public void markSupplyDirty() { this.markDirty(DIRTY_SUPPLY); }
	public void markDemandDirty() { this.markDirty(DIRTY_DEMAND); }

	public static void markReceiverDemandDirty(IFluidConnector receiver) {
		Set<FluidNetMK2> networks = receiverMemberships.get(receiver);
		if(networks != null) for(FluidNetMK2 network : networks) network.markDemandDirty();
	}

	public static void markProviderSupplyDirty(IFluidProviderMK2 provider) {
		Set<FluidNetMK2> networks = providerMemberships.get(provider);
		if(networks != null) for(FluidNetMK2 network : networks) network.markSupplyDirty();
	}

	private void markDirty(int cause) {
		if(!this.isValid()) return;
		this.dirtyCauses |= cause;
		UniNodespace.markFluidNetworkDirty(this);
	}

	public int consumeDirtyCauses() {
		int causes = this.dirtyCauses;
		this.dirtyCauses = 0;
		return causes;
	}

	@Override
	public void resetTrackers() {
		this.fluidTracker = 0;
		this.trackerTick = this.world != null ? this.world.getTotalWorldTime() : Long.MIN_VALUE;
	}

	@Override
	public void update() {
		if(providerEntries.isEmpty() || receiverEntries.isEmpty()) return;
		setupFluidProviders();
		setupFluidReceivers();
		transferFluid();
		cleanUp();
	}

	public void setupFluidProviders() {
		Iterator<Entry<IFluidProviderMK2, Long>> iterator = providerEntries.entrySet().iterator();
		while(iterator.hasNext()) {
			IFluidProviderMK2 provider = iterator.next().getKey();
			if(isBadLink(provider)) { iterator.remove(); continue; }
			int[] range = provider.getProvidingPressureRange(type);
			for(int pressure = Math.max(0, range[0]); pressure <= Math.min(IFluidUserMK2.HIGHEST_VALID_PRESSURE, range[1]); pressure++) {
				long available = Math.min(provider.getFluidAvailable(type, pressure), provider.getProviderSpeed(type, pressure));
				providers[pressure].add(new Pair<IFluidProviderMK2, Long>(provider, available));
				fluidAvailable[pressure] += available;
			}
		}
	}

	public void setupFluidReceivers() {
		Iterator<Entry<IFluidConnector, Long>> iterator = receiverEntries.entrySet().iterator();
		while(iterator.hasNext()) {
			IFluidConnector receiver = iterator.next().getKey();
			if(isBadLink(receiver)) { iterator.remove(); continue; }
			int[] range = receivingRange(receiver);
			for(int pressure = Math.max(0, range[0]); pressure <= Math.min(IFluidUserMK2.HIGHEST_VALID_PRESSURE, range[1]); pressure++) {
				long required = Math.min(receiver.getDemand(type, pressure), receiverSpeed(receiver, pressure));
				int priority = receiverPriority(receiver).ordinal();
				receivers[pressure][priority].add(new Pair<IFluidConnector, Long>(receiver, required));
				fluidDemand[pressure][priority] += required;
			}
		}
	}

	public void transferFluid() {
		long[] received = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
		long[] notAccountedFor = new long[IFluidUserMK2.HIGHEST_VALID_PRESSURE + 1];
		for(int pressure = 0; pressure <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; pressure++) {
			long totalAvailable = fluidAvailable[pressure];
			for(int priority = ConnectionPriority.values().length - 1; priority >= 0; priority--) {
				long toTransfer = Math.min(fluidDemand[pressure][priority], totalAvailable);
				if(toTransfer <= 0) continue;
				long priorityDemand = fluidDemand[pressure][priority];
				for(Pair<IFluidConnector, Long> entry : receivers[pressure][priority]) {
					long toSend = (long) Math.max(toTransfer * ((double) entry.getValue() / (double) priorityDemand), 0D);
					toSend -= entry.getKey().transferFluid(type, pressure, toSend);
					received[pressure] += toSend;
					fluidTracker += toSend;
				}
				totalAvailable -= received[pressure];
			}
			notAccountedFor[pressure] = received[pressure];
		}
		for(int pressure = 0; pressure <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; pressure++) {
			for(Pair<IFluidProviderMK2, Long> entry : providers[pressure]) {
				long toUse = (long) Math.max(received[pressure] * ((double) entry.getValue() / (double) fluidAvailable[pressure]), 0D);
				entry.getKey().useUpFluid(type, pressure, toUse);
				notAccountedFor[pressure] -= toUse;
			}
			int iterationsLeft = 100;
			while(iterationsLeft-- > 0 && notAccountedFor[pressure] > 0 && !providers[pressure].isEmpty()) {
				IFluidProviderMK2 provider = providers[pressure].get(rand.nextInt(providers[pressure].size())).getKey();
				long toUse = Math.min(notAccountedFor[pressure], provider.getFluidAvailable(type, pressure));
				provider.useUpFluid(type, pressure, toUse);
				notAccountedFor[pressure] -= toUse;
			}
		}
	}

	/** Push adapter for existing machine send calls; topology and receivers remain MK2-owned. */
	public long transferFluidExternal(FluidType fluid, int pressure, long amount, IFluidConnector excluded) {
		if(fluid != this.type || amount <= 0 || !this.isValid() || this.isTopologyRepairing()) return amount;
		this.beginTrackerTick();
		long remaining = amount;
		for(int priority = ConnectionPriority.values().length - 1; priority >= 0 && remaining > 0; priority--) {
			long totalDemand = 0;
			for(IFluidConnector receiver : this.receiverEntries.keySet()) {
				if(receiver == excluded || isBadLink(receiver) || receiverPriority(receiver).ordinal() != priority || !acceptsPressure(receiver, pressure)) continue;
				totalDemand += Math.min(receiver.getDemand(type, pressure), receiverSpeed(receiver, pressure));
			}
			if(totalDemand <= 0) continue;
			long availableForPriority = Math.min(remaining, totalDemand);
			long transferred = 0;
			for(IFluidConnector receiver : this.receiverEntries.keySet()) {
				if(receiver == excluded || isBadLink(receiver) || receiverPriority(receiver).ordinal() != priority || !acceptsPressure(receiver, pressure)) continue;
				long demand = Math.min(receiver.getDemand(type, pressure), receiverSpeed(receiver, pressure));
				long toSend = (long) Math.max(availableForPriority * ((double) demand / (double) totalDemand), 0D);
				transferred += toSend - receiver.transferFluid(type, pressure, toSend);
			}
			remaining -= transferred;
			this.fluidTracker += transferred;
		}
		return remaining;
	}

	private int[] receivingRange(IFluidConnector receiver) {
		return receiver instanceof IFluidReceiverMK2 ? ((IFluidReceiverMK2) receiver).getReceivingPressureRange(this.type) : FULL_PRESSURE_RANGE;
	}

	private boolean acceptsPressure(IFluidConnector receiver, int pressure) {
		int[] range = receiver instanceof IFluidReceiverMK2 ? ((IFluidReceiverMK2) receiver).getReceivingPressureRange(this.type) : null;
		return range == null || pressure >= range[0] && pressure <= range[1];
	}

	private long receiverSpeed(IFluidConnector receiver, int pressure) {
		return receiver instanceof IFluidReceiverMK2 ? ((IFluidReceiverMK2) receiver).getReceiverSpeed(this.type, pressure) : 1_000_000_000L;
	}

	private static ConnectionPriority receiverPriority(IFluidConnector receiver) {
		return receiver instanceof IFluidReceiverMK2 ? ((IFluidReceiverMK2) receiver).getFluidPriority() : ConnectionPriority.NORMAL;
	}

	private void beginTrackerTick() {
		long tick = this.world != null ? this.world.getTotalWorldTime() : Long.MIN_VALUE;
		if(this.trackerTick == tick) return;
		this.fluidTracker = 0;
		this.trackerTick = tick;
	}

	private static void trackReceiver(IFluidConnector receiver, FluidNetMK2 network) {
		Set<FluidNetMK2> networks = receiverMemberships.get(receiver);
		if(networks == null) {
			networks = Collections.newSetFromMap(new IdentityHashMap<FluidNetMK2, Boolean>());
			receiverMemberships.put(receiver, networks);
		}
		networks.add(network);
	}

	private static void untrackReceiver(IFluidConnector receiver, FluidNetMK2 network) {
		Set<FluidNetMK2> networks = receiverMemberships.get(receiver);
		if(networks == null) return;
		networks.remove(network);
		if(networks.isEmpty()) receiverMemberships.remove(receiver);
	}

	private static void trackProvider(IFluidProviderMK2 provider, FluidNetMK2 network) {
		Set<FluidNetMK2> networks = providerMemberships.get(provider);
		if(networks == null) {
			networks = Collections.newSetFromMap(new IdentityHashMap<FluidNetMK2, Boolean>());
			providerMemberships.put(provider, networks);
		}
		networks.add(network);
	}

	private static void untrackProvider(IFluidProviderMK2 provider, FluidNetMK2 network) {
		Set<FluidNetMK2> networks = providerMemberships.get(provider);
		if(networks == null) return;
		networks.remove(network);
		if(networks.isEmpty()) providerMemberships.remove(provider);
	}

	public void cleanUp() {
		for(int pressure = 0; pressure <= IFluidUserMK2.HIGHEST_VALID_PRESSURE; pressure++) {
			fluidAvailable[pressure] = 0;
			providers[pressure].clear();
			transfered[pressure] = 0;
			for(int priority = 0; priority < ConnectionPriority.values().length; priority++) {
				fluidDemand[pressure][priority] = 0;
				receivers[pressure][priority].clear();
			}
		}
	}
}
