package api.hbm.fluidmk2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.Compat;

import api.hbm.energymk2.PowerNetDiagnostics;
import api.hbm.fluid.IFluidConnector;
import api.hbm.tile.ILoadedTile;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Persistent machine-facing fluid port descriptors. */
public final class FluidNetEndpointRegistry {

	private static final int RECEIVER = 0;
	private static final int PROVIDER = 1;

	private static final IdentityHashMap<Object, EndpointState> endpoints = new IdentityHashMap<Object, EndpointState>();
	private static final IdentityHashMap<World, Set<EndpointState>> endpointsByWorld = new IdentityHashMap<World, Set<EndpointState>>();
	private static final Set<World> topologyDirtyWorlds = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
	private static final List<EndpointState> scratch = new ArrayList<EndpointState>();

	private FluidNetEndpointRegistry() { }

	public static boolean attachReceiver(IFluidConnector endpoint, FluidType type, World world, int x, int y, int z, ForgeDirection direction) {
		return attach(endpoint, type, world, x, y, z, direction, RECEIVER);
	}

	public static boolean attachProvider(IFluidProviderMK2 endpoint, FluidType type, World world, int x, int y, int z, ForgeDirection direction) {
		return attach(endpoint, type, world, x, y, z, direction, PROVIDER);
	}

	private static boolean attach(Object endpoint, FluidType type, World world, int x, int y, int z, ForgeDirection direction, int role) {
		if(endpoint == null || type == null || world == null || world.isRemote) return false;
		EndpointState state = endpoints.get(endpoint);
		if(state == null || state.world != world) {
			if(state != null) detach(endpoint);
			state = new EndpointState(endpoint, world);
			endpoints.put(endpoint, state);
			Set<EndpointState> states = endpointsByWorld.get(world);
			if(states == null) {
				states = Collections.newSetFromMap(new IdentityHashMap<EndpointState, Boolean>());
				endpointsByWorld.put(world, states);
			}
			states.add(state);
		}
		Descriptor descriptor = state.find(role, type, x, y, z, direction);
		if(descriptor == null) {
			descriptor = new Descriptor(role, type, x, y, z, direction);
			state.descriptors.add(descriptor);
			reconcile(state, descriptor);
		} else if(descriptor.network == null || !descriptor.network.isValid()) {
			reconcile(state, descriptor);
		}
		return descriptor.network != null && descriptor.network.isValid();
	}

	public static void detachConnection(IFluidConnector endpoint, FluidType type, World world, int x, int y, int z) {
		EndpointState state = endpoints.get(endpoint);
		if(state == null || state.world != world) return;
		for(int i = state.descriptors.size() - 1; i >= 0; i--) {
			Descriptor descriptor = state.descriptors.get(i);
			if(descriptor.role != RECEIVER || descriptor.type != type || descriptor.x != x || descriptor.y != y || descriptor.z != z) continue;
			FluidNetMK2 old = descriptor.network;
			state.descriptors.remove(i);
			if(old != null && !state.uses(RECEIVER, old)) old.removeReceiver(endpoint);
		}
		if(state.descriptors.isEmpty()) removeState(state);
	}

	public static void detach(Object endpoint) {
		EndpointState state = endpoints.get(endpoint);
		if(state == null) return;
		List<FluidNetMK2> networks = new ArrayList<FluidNetMK2>();
		for(Descriptor descriptor : state.descriptors) if(descriptor.network != null && !networks.contains(descriptor.network)) networks.add(descriptor.network);
		for(FluidNetMK2 network : networks) {
			if(endpoint instanceof IFluidConnector) network.removeReceiver((IFluidConnector) endpoint);
			if(endpoint instanceof IFluidProviderMK2) network.removeProvider((IFluidProviderMK2) endpoint);
		}
		state.descriptors.clear();
		removeState(state);
	}

	public static void detachWorld(World world) {
		Set<EndpointState> states = endpointsByWorld.remove(world);
		if(states != null) {
			scratch.clear();
			scratch.addAll(states);
			for(EndpointState state : scratch) {
				endpoints.remove(state.endpoint);
				for(Descriptor descriptor : state.descriptors) if(descriptor.network != null) removeMembership(state.endpoint, descriptor.role, descriptor.network);
				state.descriptors.clear();
			}
			scratch.clear();
		}
		topologyDirtyWorlds.remove(world);
	}

	public static void markTopologyDirty(World world) { if(world != null) topologyDirtyWorlds.add(world); }

	public static void reconcileWorldIfDirty(World world) {
		if(!topologyDirtyWorlds.remove(world)) return;
		Set<EndpointState> states = endpointsByWorld.get(world);
		if(states == null) return;
		scratch.clear();
		scratch.addAll(states);
		for(EndpointState state : scratch) {
			if(isBad(state.endpoint)) {
				detach(state.endpoint);
				continue;
			}
			for(Descriptor descriptor : state.descriptors) reconcile(state, descriptor);
		}
		scratch.clear();
	}

	/** Removal-only safety audit. */
	public static void auditWorld(World world) {
		Set<EndpointState> states = endpointsByWorld.get(world);
		if(states == null) return;
		scratch.clear();
		scratch.addAll(states);
		for(EndpointState state : scratch) {
			if(isBad(state.endpoint)) {
				PowerNetDiagnostics.recordFluidIntegrityRemoval();
				detach(state.endpoint);
				continue;
			}
			for(Descriptor descriptor : state.descriptors) {
				FluidNetMK2 old = descriptor.network;
				if(old == null || resolve(state, descriptor) == old) continue;
				descriptor.network = null;
				if(!state.uses(descriptor.role, old)) removeMembership(state.endpoint, descriptor.role, old);
				PowerNetDiagnostics.recordFluidIntegrityRemoval();
			}
		}
		scratch.clear();
	}

	private static void reconcile(EndpointState state, Descriptor descriptor) {
		FluidNetMK2 old = descriptor.network;
		FluidNetMK2 next = resolve(state, descriptor);
		if(old == next) return;
		descriptor.network = next;
		if(old != null && !state.uses(descriptor.role, old)) removeMembership(state.endpoint, descriptor.role, old);
		if(next != null) addMembership(state.endpoint, descriptor.role, next);
	}

	private static FluidNetMK2 resolve(EndpointState state, Descriptor descriptor) {
		TileEntity tile = Compat.getTileStandard(state.world, descriptor.x, descriptor.y, descriptor.z);
		if(!(tile instanceof IFluidConnectorMK2)) return null;
		if(!((IFluidConnectorMK2) tile).canConnect(descriptor.type, descriptor.direction.getOpposite())) return null;
		GenNode node = UniNodespace.getNode(state.world, descriptor.x, descriptor.y, descriptor.z, descriptor.type.getNetworkProvider());
		return node != null && node.hasValidNet() ? (FluidNetMK2) node.net : null;
	}

	private static boolean isBad(Object endpoint) {
		if(endpoint instanceof ILoadedTile && !((ILoadedTile) endpoint).isLoaded()) return true;
		return endpoint instanceof TileEntity && ((TileEntity) endpoint).isInvalid();
	}

	private static void addMembership(Object endpoint, int role, FluidNetMK2 network) {
		if(role == RECEIVER) network.addReceiver((IFluidConnector) endpoint);
		else network.addProvider((IFluidProviderMK2) endpoint);
	}

	private static void removeMembership(Object endpoint, int role, FluidNetMK2 network) {
		if(role == RECEIVER) network.removeReceiver((IFluidConnector) endpoint);
		else network.removeProvider((IFluidProviderMK2) endpoint);
	}

	private static void removeState(EndpointState state) {
		endpoints.remove(state.endpoint);
		Set<EndpointState> states = endpointsByWorld.get(state.world);
		if(states != null) {
			states.remove(state);
			if(states.isEmpty()) endpointsByWorld.remove(state.world);
		}
	}

	private static class EndpointState {
		private final Object endpoint;
		private final World world;
		private final List<Descriptor> descriptors = new ArrayList<Descriptor>(2);

		private EndpointState(Object endpoint, World world) {
			this.endpoint = endpoint;
			this.world = world;
		}

		private Descriptor find(int role, FluidType type, int x, int y, int z, ForgeDirection direction) {
			for(Descriptor descriptor : this.descriptors) if(descriptor.role == role && descriptor.type == type && descriptor.x == x && descriptor.y == y && descriptor.z == z && descriptor.direction == direction) return descriptor;
			return null;
		}

		private boolean uses(int role, FluidNetMK2 network) {
			for(Descriptor descriptor : this.descriptors) if(descriptor.role == role && descriptor.network == network) return true;
			return false;
		}
	}

	private static class Descriptor {
		private final int role;
		private final FluidType type;
		private final int x;
		private final int y;
		private final int z;
		private final ForgeDirection direction;
		private FluidNetMK2 network;

		private Descriptor(int role, FluidType type, int x, int y, int z, ForgeDirection direction) {
			this.role = role;
			this.type = type;
			this.x = x;
			this.y = y;
			this.z = z;
			this.direction = direction;
		}
	}
}
