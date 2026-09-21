package com.hbm.uninos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.util.Tuple.Pair;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.PowerNetDiagnostics;
import api.hbm.energymk2.PowerNetMK2;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Unified Nodespace, a Nodespace for all applications.
 * "Nodespace" is an invisible "dimension" where nodes exist, a node is basically the "soul" of a tile entity with networking capabilities.
 * Instead of tile entities having to find each other which is costly and assumes the tiles are loaded, tiles simply create nodes at their
 * respective position in nodespace, the nodespace itself handles stuff like connections which can also happen in unloaded chunks.
 * A node is so to say the "soul" of a tile entity which can act independent of its "body".
 * @author hbm
 */
public class UniNodespace {

	public static Map<World, UniNodeWorld> worlds = new HashMap();
	public static Set<NodeNet> activeNodeNets = new HashSet();
	private static final List<NodeNet> reapScratch = new ArrayList<NodeNet>();

	public static GenNode getNode(World world, int x, int y, int z, INetworkProvider type) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld != null) return nodeWorld.nodes.get(new Pair(new BlockPos(x, y, z), type));
		return null;
	}

	public static void createNode(World world, GenNode node) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld == null) {
			nodeWorld = new UniNodeWorld();
			worlds.put(world, nodeWorld);
		}
		node.world = world;
		nodeWorld.pushNode(node);
	}

	public static void destroyNode(World world, int x, int y, int z, INetworkProvider type) {
		GenNode node = getNode(world, x, y, z, type);
		if(node != null) {
			worlds.get(world).popNode(node);
		}
	}

	public static void destroyNode(World world, GenNode node) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(node != null && nodeWorld != null) {
			nodeWorld.popNode(node);
		}
	}

	public static void unloadWorld(World world) {
		UniNodeWorld nodeWorld = worlds.remove(world);
		if(nodeWorld == null) return;

		nodeWorld.updateScratch.clear();
		nodeWorld.updateScratch.addAll(nodeWorld.nodes.values());
		for(GenNode node : nodeWorld.updateScratch) {
			if(node.net != null && node.net.isValid()) node.net.destroy();
			node.world = null;
			node.expired = true;
		}
		nodeWorld.clear();
	}

	private static int reapTimer = 0;
	public static void updateNodespace() {
		PowerNetDiagnostics.beginTick();

		for(World world : MinecraftServer.getServer().worldServers) {
			UniNodeWorld nodeWorld = worlds.get(world);

			if(nodeWorld == null) continue;

			nodeWorld.updateScratch.clear();
			for(GenNode node : nodeWorld.nodes.values()) {
				if(!nodeWorld.updateScratch.add(node)) continue;
				if(!node.hasValidNet() || node.recentlyChanged) {
					checkNodeConnection(world, node, node.networkProvider);
					node.recentlyChanged = false;
				}
			}
			nodeWorld.updateScratch.clear();
		}

		updateNetworks();
		PowerNetDiagnostics.finishTick(countPowerNetworks());
		updateReapTimer();
	}

	private static void updateNetworks() {
		// Non-power UNINOS networks retain their established per-tick behavior.
		for(NodeNet net : activeNodeNets) {
			if(net instanceof PowerNetMK2) continue;
			net.resetTrackers();
			net.update();
		}

		for(UniNodeWorld nodeWorld : worlds.values()) nodeWorld.updatePowerNetworks();
		
		if(reapTimer <= 0) {
			reapScratch.clear();
			for(NodeNet net : activeNodeNets) {
				net.reapExpiredLinks();
				if(net.links.size() <= 0) reapScratch.add(net);
			}
			for(NodeNet net : reapScratch) net.destroy();
			reapScratch.clear();
		}
	}

	private static int countPowerNetworks() {
		int count = 0;
		for(UniNodeWorld nodeWorld : worlds.values()) count += nodeWorld.powerNetworks.size();
		return count;
	}

	static void registerNetwork(NodeNet network) {
		if(!(network instanceof PowerNetMK2) || network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld != null) nodeWorld.powerNetworks.add((PowerNetMK2) network);
	}

	static void unregisterNetwork(NodeNet network) {
		if(!(network instanceof PowerNetMK2)) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld != null) nodeWorld.removePowerNetwork((PowerNetMK2) network);
	}

	public static void markPowerNetworkDirty(PowerNetMK2 network) {
		if(network == null || !network.isValid() || network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld != null) nodeWorld.dirtyPowerNetworks.add(network);
	}

	public static void setPowerNetworkLegacy(PowerNetMK2 network, boolean legacy) {
		if(network == null || network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld == null) return;
		if(legacy) nodeWorld.legacyPowerNetworks.add(network);
		else nodeWorld.legacyPowerNetworks.remove(network);
	}
	
	private static void updateReapTimer() {
		if(reapTimer <= 0) reapTimer = 5 * 60 * 20; // 5 minutes is more than plenty 
		else reapTimer--;
	}

	/** Goes over each connection point of the given node, tries to find neighbor nodes and to join networks with them */
	private static void checkNodeConnection(World world, GenNode node, INetworkProvider provider) {

		for(DirPos con : node.connections) {
			GenNode conNode = getNode(world, con.getX(), con.getY(), con.getZ(), provider); // get whatever neighbor node intersects with that connection
			if(conNode != null) { // if there is a node at that place
				if(conNode.hasValidNet() && conNode.net == node.net) continue; // if the net is valid and both nodes have the same net, skip
				if(checkConnection(conNode, con, false)) {
					connectToNode(node, conNode);
				}
			}
		}

		if(node.net == null || !node.net.isValid()) provider.provideNetwork().joinLink(node);
	}

	/** Checks if the node can be connected to given the DirPos, skipSideCheck will ignore the DirPos' direction value */
	public static boolean checkConnection(GenNode connectsTo, DirPos connectFrom, boolean skipSideCheck) {
		for(DirPos revCon : connectsTo.connections) {
			if(revCon.getX() - revCon.getDir().offsetX == connectFrom.getX() && revCon.getY() - revCon.getDir().offsetY == connectFrom.getY() && revCon.getZ() - revCon.getDir().offsetZ == connectFrom.getZ() && (revCon.getDir() == connectFrom.getDir().getOpposite() || skipSideCheck)) {
				return true;
			}
		}
		return false;
	}

	/** Links two nodes with different or potentially no networks */
	private static void connectToNode(GenNode origin, GenNode connection) {

		if(origin.hasValidNet() && connection.hasValidNet()) { // both nodes have nets, but the nets are different (previous assumption), join networks
			if(origin.net.links.size() > connection.net.links.size()) {
				origin.net.joinNetworks(connection.net);
			} else {
				connection.net.joinNetworks(origin.net);
			}
		} else if(!origin.hasValidNet() && connection.hasValidNet()) { // origin has no net, connection does, have origin join connection's net
			connection.net.joinLink(origin);
		} else if(origin.hasValidNet() && !connection.hasValidNet()) { // ...and vice versa
			origin.net.joinLink(connection);
		}
	}

	public static class UniNodeWorld {

		public HashMap<Pair<BlockPos, INetworkProvider>, GenNode> nodes = new LinkedHashMap<>();
		private final Set<GenNode> updateScratch = Collections.newSetFromMap(new IdentityHashMap<GenNode, Boolean>());
		private final Set<PowerNetMK2> powerNetworks = Collections.newSetFromMap(new IdentityHashMap<PowerNetMK2, Boolean>());
		private final Set<PowerNetMK2> legacyPowerNetworks = Collections.newSetFromMap(new IdentityHashMap<PowerNetMK2, Boolean>());
		private final Set<PowerNetMK2> dirtyPowerNetworks = new LinkedHashSet<PowerNetMK2>();
		private final List<PowerNetMK2> powerUpdateScratch = new ArrayList<PowerNetMK2>();
		private int compatibilitySweep;

		/** Adds a node at all its positions to the nodespace */
		public void pushNode(GenNode node) {
			for(BlockPos pos : node.positions) {
				nodes.put(new Pair(pos, node.networkProvider), node);
			}
		}

		/** Removes the specified node from all positions from nodespace */
		public void popNode(GenNode node) {
			if(node.net != null) node.net.destroy();
			for(BlockPos pos : node.positions) {
				nodes.remove(new Pair(pos, node.networkProvider));
			}
			node.expired = true;
		}

		private void updatePowerNetworks() {
			for(PowerNetMK2 network : this.powerNetworks) network.resetTrackers();

			this.powerUpdateScratch.clear();
			this.powerUpdateScratch.addAll(this.legacyPowerNetworks);
			for(PowerNetMK2 network : this.powerUpdateScratch) network.markCompatibilityDirty();

			if(++this.compatibilitySweep >= 20) {
				this.compatibilitySweep = 0;
				this.powerUpdateScratch.clear();
				this.powerUpdateScratch.addAll(this.powerNetworks);
				for(PowerNetMK2 network : this.powerUpdateScratch) network.markCompatibilityDirty();
			}

			this.powerUpdateScratch.clear();
			this.powerUpdateScratch.addAll(this.dirtyPowerNetworks);
			this.dirtyPowerNetworks.clear();
			for(PowerNetMK2 network : this.powerUpdateScratch) {
				if(!network.isValid()) continue;
				int causes = network.consumeDirtyCauses();
				if(causes == 0) continue;
				network.update();
				PowerNetDiagnostics.recordNetworkVisit(network.providerEntries.size(), network.receiverEntries.size(), causes);
			}
			this.powerUpdateScratch.clear();
		}

		private void removePowerNetwork(PowerNetMK2 network) {
			this.powerNetworks.remove(network);
			this.legacyPowerNetworks.remove(network);
			this.dirtyPowerNetworks.remove(network);
		}

		private void clear() {
			this.nodes.clear();
			this.updateScratch.clear();
			this.powerNetworks.clear();
			this.legacyPowerNetworks.clear();
			this.dirtyPowerNetworks.clear();
			this.powerUpdateScratch.clear();
		}
	}
}
