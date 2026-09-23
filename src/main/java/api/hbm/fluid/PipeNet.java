package api.hbm.fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;

import api.hbm.fluidmk2.FluidNetMK2;

/**
 * Compatibility view for old callers. Conductor topology and endpoint ownership
 * belong exclusively to FluidNetMK2; this class stores no TileEntities.
 */
public final class PipeNet implements IPipeNet {

	private static final IdentityHashMap<FluidNetMK2, PipeNet> adapters = new IdentityHashMap<FluidNetMK2, PipeNet>();
	private final FluidNetMK2 network;

	private PipeNet(FluidNetMK2 network) { this.network = network; }

	public static PipeNet forNetwork(FluidNetMK2 network) {
		if(network == null || !network.isValid()) return null;
		PipeNet adapter = adapters.get(network);
		if(adapter == null) {
			adapter = new PipeNet(network);
			adapters.put(network, adapter);
		}
		return adapter;
	}

	public static void release(FluidNetMK2 network) { adapters.remove(network); }

	@Override public void joinNetworks(IPipeNet network) { }
	@Override public List<IFluidConductor> getLinks() { return Collections.emptyList(); }
	@Override public HashSet<IFluidConnector> getSubscribers() { return new HashSet<IFluidConnector>(this.network.receiverEntries.keySet()); }
	@Override public IPipeNet joinLink(IFluidConductor conductor) { return this; }
	@Override public void leaveLink(IFluidConductor conductor) { }
	@Override public void subscribe(IFluidConnector connector) { this.network.addReceiver(connector); }
	@Override public void unsubscribe(IFluidConnector connector) { this.network.removeReceiver(connector); }
	@Override public boolean isSubscribed(IFluidConnector connector) { return this.network.isSubscribed(connector); }
	@Override public void destroy() { this.network.destroy(); }
	@Override public boolean isValid() { return this.network.isValid(); }
	@Override public long transferFluid(long fill, int pressure) { return this.network.transferFluidExternal(this.network.getType(), pressure, fill, null); }
	@Override public FluidType getType() { return this.network.getType(); }

	public static long fairTransfer(List<IFluidConnector> subscribers, FluidType type, int pressure, long fill) {
		if(fill <= 0 || subscribers.isEmpty()) return fill;
		long totalDemand = 0;
		long[] demand = new long[subscribers.size()];
		for(int i = 0; i < subscribers.size(); i++) {
			demand[i] = subscribers.get(i).getDemand(type, pressure);
			totalDemand += demand[i];
		}
		if(totalDemand <= 0) return fill;
		long transferred = 0;
		for(int i = 0; i < subscribers.size(); i++) {
			long amount = (long) Math.floor((double) demand[i] / (double) totalDemand * fill);
			if(amount > 0) transferred += amount - subscribers.get(i).transferFluid(type, pressure, amount);
		}
		return fill - transferred;
	}
}
