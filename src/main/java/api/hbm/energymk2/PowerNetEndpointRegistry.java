package api.hbm.energymk2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.util.Compat;

import api.hbm.energymk2.Nodespace.PowerNode;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Persistent descriptions of the conductor positions used by MK2 endpoints.
 * Existing connection calls declare these descriptions; network rebuilds replay
 * them without relying on timestamp refresh or redistributing clean networks.
 */
final class PowerNetEndpointRegistry {

	private static final int RECEIVER = 0;
	private static final int PROVIDER = 1;

	private static final IdentityHashMap<IEnergyHandlerMK2, EndpointState> endpoints = new IdentityHashMap<IEnergyHandlerMK2, EndpointState>();
	private static final IdentityHashMap<World, Set<EndpointState>> endpointsByWorld = new IdentityHashMap<World, Set<EndpointState>>();
	private static final Set<World> topologyDirtyWorlds = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
	private static final List<EndpointState> stateScratch = new ArrayList<EndpointState>();

	private PowerNetEndpointRegistry() { }

	static boolean attachReceiver(IEnergyReceiverMK2 endpoint, World world, int x, int y, int z, ForgeDirection direction) {
		return attach(endpoint, world, x, y, z, direction, RECEIVER);
	}

	static boolean attachProvider(IEnergyProviderMK2 endpoint, World world, int x, int y, int z, ForgeDirection direction) {
		return attach(endpoint, world, x, y, z, direction, PROVIDER);
	}

	private static boolean attach(IEnergyHandlerMK2 endpoint, World world, int x, int y, int z, ForgeDirection direction, int role) {
		if(world == null || world.isRemote) return false;

		EndpointState state = endpoints.get(endpoint);
		if(state == null || state.world != world) {
			if(state != null) detach(endpoint);
			state = new EndpointState(endpoint, world);
			endpoints.put(endpoint, state);
			Set<EndpointState> worldEndpoints = endpointsByWorld.get(world);
			if(worldEndpoints == null) {
				worldEndpoints = Collections.newSetFromMap(new IdentityHashMap<EndpointState, Boolean>());
				endpointsByWorld.put(world, worldEndpoints);
			}
			worldEndpoints.add(state);
		}

		Connection connection = state.find(role, x, y, z, direction);
		if(connection == null) {
			connection = new Connection(role, x, y, z, direction);
			state.connections.add(connection);
			reconcile(state, connection);
		} else if(connection.network == null || !connection.network.isValid()) {
			reconcile(state, connection);
		}

		return connection.network != null && connection.network.isValid();
	}

	static void unsubscribeReceiver(IEnergyReceiverMK2 endpoint, World world, int x, int y, int z) {
		EndpointState state = endpoints.get(endpoint);
		if(state == null || state.world != world) return;
		for(int i = state.connections.size() - 1; i >= 0; i--) {
			Connection connection = state.connections.get(i);
			if(connection.role != RECEIVER || connection.x != x || connection.y != y || connection.z != z) continue;
			PowerNetMK2 oldNetwork = connection.network;
			state.connections.remove(i);
			if(oldNetwork != null && !state.uses(RECEIVER, oldNetwork)) oldNetwork.removeReceiver(endpoint);
		}
	}

	static void detach(IEnergyHandlerMK2 endpoint) {
		EndpointState state = endpoints.remove(endpoint);
		if(state != null) {
			Set<EndpointState> worldEndpoints = endpointsByWorld.get(state.world);
			if(worldEndpoints != null) {
				worldEndpoints.remove(state);
				if(worldEndpoints.isEmpty()) endpointsByWorld.remove(state.world);
			}
			state.connections.clear();
		}
		PowerNetMK2.detachEndpointMemberships(endpoint);
	}

	static void detachWorld(World world) {
		Set<EndpointState> worldEndpoints = endpointsByWorld.remove(world);
		if(worldEndpoints == null) return;
		stateScratch.clear();
		stateScratch.addAll(worldEndpoints);
		for(EndpointState state : stateScratch) {
			endpoints.remove(state.endpoint);
			state.connections.clear();
			PowerNetMK2.detachEndpointMemberships(state.endpoint);
		}
		stateScratch.clear();
		topologyDirtyWorlds.remove(world);
	}

	static void markTopologyDirty(World world) {
		if(world != null) topologyDirtyWorlds.add(world);
	}

	static void reconcileWorldIfDirty(World world) {
		if(!topologyDirtyWorlds.remove(world)) return;
		reconcileWorld(world);
	}

	static void auditWorld(World world) {
		Set<EndpointState> worldEndpoints = endpointsByWorld.get(world);
		if(worldEndpoints == null) return;

		stateScratch.clear();
		stateScratch.addAll(worldEndpoints);
		for(EndpointState state : stateScratch) {
			if(PowerNetMK2.isBadLink(state.endpoint)) {
				PowerNetDiagnostics.recordIntegrityRemoval();
				detach(state.endpoint);
				continue;
			}
			for(Connection connection : state.connections) {
				PowerNetMK2 oldNetwork = connection.network;
				if(oldNetwork == null || resolve(state, connection) == oldNetwork) continue;
				connection.network = null;
				if(!state.uses(connection.role, oldNetwork)) {
					if(connection.role == RECEIVER) oldNetwork.removeReceiver((IEnergyReceiverMK2) state.endpoint);
					else oldNetwork.removeProvider((IEnergyProviderMK2) state.endpoint);
				}
				PowerNetDiagnostics.recordIntegrityRemoval();
			}
		}
		stateScratch.clear();
	}

	private static void reconcileWorld(World world) {
		Set<EndpointState> worldEndpoints = endpointsByWorld.get(world);
		if(worldEndpoints == null) return;

		stateScratch.clear();
		stateScratch.addAll(worldEndpoints);
		for(EndpointState state : stateScratch) {
			if(PowerNetMK2.isBadLink(state.endpoint)) {
				detach(state.endpoint);
				continue;
			}
			for(Connection connection : state.connections) reconcile(state, connection);
		}
		stateScratch.clear();
	}

	private static void reconcile(EndpointState state, Connection connection) {
		PowerNetMK2 oldNetwork = connection.network;
		PowerNetMK2 newNetwork = resolve(state, connection);
		if(oldNetwork == newNetwork) return;

		connection.network = newNetwork;
		if(oldNetwork != null && !state.uses(connection.role, oldNetwork)) {
			if(connection.role == RECEIVER) oldNetwork.removeReceiver((IEnergyReceiverMK2) state.endpoint);
			else oldNetwork.removeProvider((IEnergyProviderMK2) state.endpoint);
		}
		if(newNetwork != null) {
			if(connection.role == RECEIVER) newNetwork.addReceiver((IEnergyReceiverMK2) state.endpoint);
			else newNetwork.addProvider((IEnergyProviderMK2) state.endpoint);
		}
	}

	private static PowerNetMK2 resolve(EndpointState state, Connection connection) {
		TileEntity tile = Compat.getTileStandard(state.world, connection.x, connection.y, connection.z);
		if(!(tile instanceof IEnergyConductorMK2)) return null;
		IEnergyConductorMK2 conductor = (IEnergyConductorMK2) tile;
		if(!conductor.canConnect(connection.direction.getOpposite())) return null;
		PowerNode node = Nodespace.getNode(state.world, connection.x, connection.y, connection.z);
		return node != null && node.hasValidNet() ? node.net : null;
	}

	private static class EndpointState {
		private final IEnergyHandlerMK2 endpoint;
		private final World world;
		private final List<Connection> connections = new ArrayList<Connection>(2);

		private EndpointState(IEnergyHandlerMK2 endpoint, World world) {
			this.endpoint = endpoint;
			this.world = world;
		}

		private Connection find(int role, int x, int y, int z, ForgeDirection direction) {
			for(Connection connection : this.connections) {
				if(connection.role == role && connection.x == x && connection.y == y && connection.z == z && connection.direction == direction) return connection;
			}
			return null;
		}

		private boolean uses(int role, PowerNetMK2 network) {
			for(Connection connection : this.connections) {
				if(connection.role == role && connection.network == network) return true;
			}
			return false;
		}
	}

	private static class Connection {
		private final int role;
		private final int x;
		private final int y;
		private final int z;
		private final ForgeDirection direction;
		private PowerNetMK2 network;

		private Connection(int role, int x, int y, int z, ForgeDirection direction) {
			this.role = role;
			this.x = x;
			this.y = y;
			this.z = z;
			this.direction = direction;
		}
	}
}
