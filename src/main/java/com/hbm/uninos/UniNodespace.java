package com.hbm.uninos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.PowerNetDiagnostics;
import api.hbm.energymk2.PowerNetMK2;
import api.hbm.fluidmk2.FluidNetEndpointRegistry;
import api.hbm.fluidmk2.FluidNetMK2;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

/** Unified Nodespace with bounded, event-driven topology processing. */
public class UniNodespace {

	private static final int TOPOLOGY_WORK_BUDGET = 4096;
	private static final int PENDING_CONDUCTOR_BUDGET = 1024;
	private static final int NETWORK_WORK_BUDGET = 4096;
	private static final ForgeDirection[] STANDARD_DIRECTIONS = ForgeDirection.VALID_DIRECTIONS;

	public static Map<World, UniNodeWorld> worlds = new HashMap<World, UniNodeWorld>();
	public static Set<NodeNet> activeNodeNets = new HashSet<NodeNet>();
	private static final List<NodeNet> reapScratch = new ArrayList<NodeNet>();

	public static GenNode getNode(World world, int x, int y, int z, INetworkProvider type) {
		UniNodeWorld nodeWorld = worlds.get(world);
		return nodeWorld != null ? nodeWorld.getNode(x, y, z, type) : null;
	}

	public static void createNode(World world, GenNode node) {
		if(world == null || world.isRemote || node == null) return;
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld == null) {
			nodeWorld = new UniNodeWorld();
			worlds.put(world, nodeWorld);
		}
		node.world = world;
		node.expired = false;
		nodeWorld.pushNode(node);
	}

	/** Queues a coordinate, rather than a TileEntity reference, for loaded-only reconciliation. */
	public static void queueConductor(World world, int x, int y, int z) {
		if(world == null || world.isRemote) return;
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld == null) {
			nodeWorld = new UniNodeWorld();
			worlds.put(world, nodeWorld);
		}
		nodeWorld.pendingConductors.add(pack(x, y, z));
	}

	public static void destroyNode(World world, int x, int y, int z, INetworkProvider type) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld == null) return;
		GenNode node = nodeWorld.getNode(x, y, z, type);
		if(node != null) nodeWorld.popNode(world, node);
	}

	public static void destroyNode(World world, GenNode node) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(node != null && nodeWorld != null) nodeWorld.popNode(world, node);
	}

	/** Marks an existing node and its directly relevant neighbors for topology repair. */
	public static void markNodeDirty(World world, GenNode node) {
		UniNodeWorld nodeWorld = worlds.get(world);
		if(nodeWorld == null || node == null || node.expired) return;
		nodeWorld.markDirty(node);
		for(GenNode neighbor : getConnectedNodes(world, node, false)) nodeWorld.markDirty(neighbor);
	}

	public static void unloadWorld(World world) {
		PowerNetMK2.detachWorldEndpoints(world);
		FluidNetEndpointRegistry.detachWorld(world);
		UniNodeWorld nodeWorld = worlds.remove(world);
		if(nodeWorld == null) return;

		for(GenNode node : new ArrayList<GenNode>(nodeWorld.allNodes)) {
			if(node.net != null && node.net.isValid()) node.net.destroy();
			node.world = null;
			node.expired = true;
		}
		nodeWorld.clear();
	}

	private static int reapTimer;

	public static void updateNodespace() {
		PowerNetDiagnostics.beginTick();
		for(World world : MinecraftServer.getServer().worldServers) {
			UniNodeWorld nodeWorld = worlds.get(world);
			if(nodeWorld != null) nodeWorld.processTopology(world, TOPOLOGY_WORK_BUDGET);
			PowerNetMK2.reconcileWorldEndpoints(world);
			FluidNetEndpointRegistry.reconcileWorldIfDirty(world);
		}
		updateNetworks();
		PowerNetDiagnostics.finishTick(countPowerNetworks());
		updateReapTimer();
	}

	private static void updateNetworks() {
		for(NodeNet net : activeNodeNets) {
			if(net instanceof PowerNetMK2 || net instanceof FluidNetMK2 || net.isTopologyRepairing()) continue;
			net.resetTrackers();
			net.update();
		}
		for(Map.Entry<World, UniNodeWorld> entry : worlds.entrySet()) entry.getValue().updatePowerNetworks(entry.getKey());

		if(reapTimer <= 0) {
			reapScratch.clear();
			for(NodeNet net : activeNodeNets) {
				net.reapExpiredLinks();
				if(net.links.isEmpty()) reapScratch.add(net);
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
		if(network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld == null) return;
		if(network instanceof PowerNetMK2) nodeWorld.powerNetworks.add((PowerNetMK2) network);
		if(network instanceof FluidNetMK2) nodeWorld.fluidNetworks.add((FluidNetMK2) network);
	}

	static void unregisterNetwork(NodeNet network) {
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld == null) return;
		if(network instanceof PowerNetMK2) nodeWorld.removePowerNetwork((PowerNetMK2) network);
		if(network instanceof FluidNetMK2) nodeWorld.removeFluidNetwork((FluidNetMK2) network);
	}

	public static void markPowerNetworkDirty(PowerNetMK2 network) {
		if(network == null || !network.isValid() || network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld != null) nodeWorld.dirtyPowerNetworks.add(network);
	}

	public static void markFluidNetworkDirty(FluidNetMK2 network) {
		if(network == null || !network.isValid() || network.getWorld() == null) return;
		UniNodeWorld nodeWorld = worlds.get(network.getWorld());
		if(nodeWorld != null) nodeWorld.dirtyFluidNetworks.add(network);
	}

	private static void updateReapTimer() {
		if(reapTimer <= 0) reapTimer = 5 * 60 * 20;
		else reapTimer--;
	}

	private static void checkNodeConnection(World world, GenNode node, INetworkProvider provider) {
		if(node.expired) return;
		int count = connectionCount(node);
		for(int i = 0; i < count; i++) {
			int x = connectionX(node, i);
			int y = connectionY(node, i);
			int z = connectionZ(node, i);
			ForgeDirection direction = connectionDirection(node, i);
			GenNode connected = getNode(world, x, y, z, provider);
			if(connected == null || connected.expired) continue;
			if(connected.hasValidNet() && connected.net == node.net) continue;
			if(checkConnection(connected, x, y, z, direction, false) && connectToNode(node, connected)) return;
		}
		if(!node.hasValidNet()) provider.provideNetwork().joinLink(node);
	}

	public static boolean checkConnection(GenNode connectsTo, DirPos connectFrom, boolean skipSideCheck) {
		return checkConnection(connectsTo, connectFrom.getX(), connectFrom.getY(), connectFrom.getZ(), connectFrom.getDir(), skipSideCheck);
	}

	private static boolean checkConnection(GenNode connectsTo, int fromX, int fromY, int fromZ, ForgeDirection fromDirection, boolean skipSideCheck) {
		if(connectsTo.hasStandardConnections()) {
			if(connectsTo.getStandardX() != fromX || connectsTo.getStandardY() != fromY || connectsTo.getStandardZ() != fromZ) return false;
			return skipSideCheck || fromDirection != ForgeDirection.UNKNOWN;
		}
		for(DirPos reverse : connectsTo.connections) {
			if(reverse.getX() - reverse.getDir().offsetX == fromX
					&& reverse.getY() - reverse.getDir().offsetY == fromY
					&& reverse.getZ() - reverse.getDir().offsetZ == fromZ
					&& (reverse.getDir() == fromDirection.getOpposite() || skipSideCheck)) return true;
		}
		return false;
	}

	/** Returns true when a bounded merge was scheduled and this node must pause. */
	private static boolean connectToNode(GenNode origin, GenNode connection) {
		if(origin.hasValidNet() && connection.hasValidNet()) {
			NodeNet target = origin.net.links.size() > connection.net.links.size() ? origin.net : connection.net;
			NodeNet source = target == origin.net ? connection.net : origin.net;
			if(source.links.size() > 256) {
				UniNodeWorld nodeWorld = worlds.get(origin.world);
				if(nodeWorld != null) nodeWorld.queueMerge(target, source, origin, connection);
				return true;
			}
			target.joinNetworks(source);
		} else if(!origin.hasValidNet() && connection.hasValidNet()) {
			connection.net.joinLink(origin);
		} else if(origin.hasValidNet() && !connection.hasValidNet()) {
			origin.net.joinLink(connection);
		}
		return false;
	}

	private static int connectionCount(GenNode node) { return node.hasStandardConnections() ? STANDARD_DIRECTIONS.length : node.connections.length; }
	private static ForgeDirection connectionDirection(GenNode node, int index) { return node.hasStandardConnections() ? STANDARD_DIRECTIONS[index] : node.connections[index].getDir(); }
	private static int connectionX(GenNode node, int index) { return node.hasStandardConnections() ? node.getStandardX() + STANDARD_DIRECTIONS[index].offsetX : node.connections[index].getX(); }
	private static int connectionY(GenNode node, int index) { return node.hasStandardConnections() ? node.getStandardY() + STANDARD_DIRECTIONS[index].offsetY : node.connections[index].getY(); }
	private static int connectionZ(GenNode node, int index) { return node.hasStandardConnections() ? node.getStandardZ() + STANDARD_DIRECTIONS[index].offsetZ : node.connections[index].getZ(); }

	private static List<GenNode> getConnectedNodes(World world, GenNode node, boolean sameNetworkOnly) {
		List<GenNode> result = new ArrayList<GenNode>(6);
		int count = connectionCount(node);
		for(int i = 0; i < count; i++) {
			int x = connectionX(node, i);
			int y = connectionY(node, i);
			int z = connectionZ(node, i);
			ForgeDirection direction = connectionDirection(node, i);
			GenNode connected = getNode(world, x, y, z, node.networkProvider);
			if(connected == null || connected.expired || connected == node) continue;
			if(sameNetworkOnly && connected.net != node.net) continue;
			if(checkConnection(connected, x, y, z, direction, false) && !result.contains(connected)) result.add(connected);
		}
		return result;
	}

	/** Collision-free for Minecraft's supported +/-30M X/Z and the full 12-bit Y domain. */
	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
	}

	public static class UniNodeWorld {

		private final IdentityHashMap<INetworkProvider, HashMap<Long, GenNode>> nodesByProvider = new IdentityHashMap<INetworkProvider, HashMap<Long, GenNode>>();
		private final Set<GenNode> allNodes = Collections.newSetFromMap(new IdentityHashMap<GenNode, Boolean>());
		private final LinkedHashSet<GenNode> dirtyTopology = new LinkedHashSet<GenNode>();
		private final LinkedHashSet<Long> pendingConductors = new LinkedHashSet<Long>();
		private final Deque<SplitTask> splitTasks = new ArrayDeque<SplitTask>();
		private final IdentityHashMap<NodeNet, SplitTask> splitByNetwork = new IdentityHashMap<NodeNet, SplitTask>();
		private final Deque<MergeTask> mergeTasks = new ArrayDeque<MergeTask>();
		private final Set<PowerNetMK2> powerNetworks = Collections.newSetFromMap(new IdentityHashMap<PowerNetMK2, Boolean>());
		private final Set<PowerNetMK2> dirtyPowerNetworks = new LinkedHashSet<PowerNetMK2>();
		private final List<PowerNetMK2> powerUpdateScratch = new ArrayList<PowerNetMK2>();
		private final Set<FluidNetMK2> fluidNetworks = Collections.newSetFromMap(new IdentityHashMap<FluidNetMK2, Boolean>());
		private final Set<FluidNetMK2> dirtyFluidNetworks = new LinkedHashSet<FluidNetMK2>();
		private final List<FluidNetMK2> fluidUpdateScratch = new ArrayList<FluidNetMK2>();
		private int integrityAudit;

		private GenNode getNode(int x, int y, int z, INetworkProvider provider) {
			HashMap<Long, GenNode> nodes = this.nodesByProvider.get(provider);
			return nodes != null ? nodes.get(pack(x, y, z)) : null;
		}

		public void pushNode(GenNode node) {
			List<GenNode> replacedNodes = new ArrayList<GenNode>();
			for(BlockPos pos : node.positions) {
				GenNode replaced = this.getNode(pos.getX(), pos.getY(), pos.getZ(), node.networkProvider);
				if(replaced != null && replaced != node && !replacedNodes.contains(replaced)) replacedNodes.add(replaced);
			}
			for(GenNode replaced : replacedNodes) this.popNode(node.world, replaced);
			HashMap<Long, GenNode> nodes = this.nodesByProvider.get(node.networkProvider);
			if(nodes == null) {
				nodes = new HashMap<Long, GenNode>();
				this.nodesByProvider.put(node.networkProvider, nodes);
			}
			for(BlockPos pos : node.positions) nodes.put(pack(pos.getX(), pos.getY(), pos.getZ()), node);
			this.allNodes.add(node);
			this.markDirty(node);
			for(GenNode neighbor : getConnectedNodes(node.world, node, false)) this.markDirty(neighbor);
			PowerNetDiagnostics.recordConductorAttached();
		}

		public void popNode(World world, GenNode node) {
			if(node.expired) return;
			List<GenNode> oldNeighbors = getConnectedNodes(world, node, true);
			HashMap<Long, GenNode> nodes = this.nodesByProvider.get(node.networkProvider);
			if(nodes != null) {
				for(BlockPos pos : node.positions) {
					long key = pack(pos.getX(), pos.getY(), pos.getZ());
					if(nodes.get(key) == node) nodes.remove(key);
				}
				if(nodes.isEmpty()) this.nodesByProvider.remove(node.networkProvider);
			}
			this.allNodes.remove(node);
			this.dirtyTopology.remove(node);
			NodeNet oldNetwork = node.net;
			if(oldNetwork != null) oldNetwork.leaveLink(node);
			node.expired = true;
			node.world = null;
			for(GenNode neighbor : oldNeighbors) this.markDirty(neighbor);
			if(oldNetwork != null && oldNetwork.isValid()) {
				if(oldNetwork.links.isEmpty()) oldNetwork.destroy();
				else if(oldNeighbors.size() > 1) this.queueSplit(oldNetwork, oldNeighbors);
			}
			PowerNetDiagnostics.recordConductorDetached();
		}

		private void markDirty(GenNode node) { if(node != null && !node.expired) this.dirtyTopology.add(node); }

		private void queueSplit(NodeNet network, List<GenNode> seeds) {
			SplitTask task = this.splitByNetwork.get(network);
			if(task == null) {
				task = new SplitTask(network, seeds);
				this.splitByNetwork.put(network, task);
				this.splitTasks.add(task);
			} else task.restart(seeds);
			network.topologyRepairing = true;
		}

		private void queueMerge(NodeNet target, NodeNet source, GenNode origin, GenNode connection) {
			if(target == source || !target.isValid() || !source.isValid()) return;
			if(target.isTopologyRepairing() || source.isTopologyRepairing()) {
				if(!target.links.isEmpty()) this.markDirty((GenNode) target.links.iterator().next());
				if(!source.links.isEmpty()) this.markDirty((GenNode) source.links.iterator().next());
				return;
			}
			target.topologyRepairing = true;
			source.topologyRepairing = true;
			this.mergeTasks.add(new MergeTask(target, source, origin, connection));
		}

		private void processTopology(World world, int budget) {
			long started = PowerNetDiagnostics.startTopology();
			int processed = this.processPendingConductors(world, Math.min(budget, PENDING_CONDUCTOR_BUDGET));
			while(processed < budget && !this.splitTasks.isEmpty()) {
				SplitTask task = this.splitTasks.peek();
				int used = task.process(world, budget - processed);
				processed += Math.max(used, 1);
				if(task.complete) {
					this.splitTasks.remove();
					this.splitByNetwork.remove(task.source);
				}
			}
			while(processed < budget && this.splitTasks.isEmpty() && !this.mergeTasks.isEmpty()) {
				MergeTask task = this.mergeTasks.peek();
				int used = task.process(budget - processed);
				processed += Math.max(used, 1);
				if(task.complete) {
					this.mergeTasks.remove();
					this.markDirty(task.origin);
					this.markDirty(task.connection);
				}
			}
			if(this.splitTasks.isEmpty() && this.mergeTasks.isEmpty()) {
				while(processed < budget && !this.dirtyTopology.isEmpty()) {
					GenNode node = this.dirtyTopology.iterator().next();
					this.dirtyTopology.remove(node);
					if(!node.expired) checkNodeConnection(world, node, node.networkProvider);
					processed++;
					if(!this.mergeTasks.isEmpty()) break;
				}
			}
			PowerNetDiagnostics.recordTopologyInventory(this.allNodes.size(), this.dirtyTopology.size(), processed, !this.splitTasks.isEmpty() || !this.mergeTasks.isEmpty() || !this.dirtyTopology.isEmpty());
			PowerNetDiagnostics.finishTopology(started);
		}

		private int processPendingConductors(World world, int budget) {
			if(!(world instanceof WorldServer) || budget <= 0 || this.pendingConductors.isEmpty()) return 0;
			int availableAtStart = this.pendingConductors.size();
			int processed = 0;
			while(processed < budget && processed < availableAtStart && !this.pendingConductors.isEmpty()) {
				long packed = this.pendingConductors.iterator().next();
				this.pendingConductors.remove(packed);
				int x = unpackX(packed);
				int y = unpackY(packed);
				int z = unpackZ(packed);
				if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
					this.pendingConductors.add(packed);
					processed++;
					continue;
				}
				Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
				TileEntity tile = chunk.func_150806_e(x & 15, y, z & 15);
				if(tile instanceof IDeferredConductor && !tile.isInvalid()) {
					((IDeferredConductor) tile).reconcileConductorNode();
				}
				processed++;
			}
			return processed;
		}

		private void updatePowerNetworks(World world) {
			for(PowerNetMK2 network : this.powerNetworks) {
				network.resetTrackers();
				PowerNetDiagnostics.recordNetworkInventory(network.providerEntries.size(), network.receiverEntries.size());
			}
			if(++this.integrityAudit >= 100) {
				this.integrityAudit = 0;
				PowerNetMK2.auditWorldEndpoints(world);
				FluidNetEndpointRegistry.auditWorld(world);
				this.powerUpdateScratch.clear();
				this.powerUpdateScratch.addAll(this.powerNetworks);
				for(PowerNetMK2 network : this.powerUpdateScratch) network.auditInvalidEndpoints();
			}
			this.powerUpdateScratch.clear();
			this.powerUpdateScratch.addAll(this.dirtyPowerNetworks);
			this.dirtyPowerNetworks.clear();
			int visitedNetworks = 0;
			for(PowerNetMK2 network : this.powerUpdateScratch) {
				if(visitedNetworks++ >= NETWORK_WORK_BUDGET) {
					this.dirtyPowerNetworks.add(network);
					continue;
				}
				if(!network.isValid()) continue;
				if(network.isTopologyRepairing()) {
					this.dirtyPowerNetworks.add(network);
					continue;
				}
				int causes = network.consumeDirtyCauses();
				if(causes == 0) continue;
				network.update();
				PowerNetDiagnostics.recordNetworkVisit(network.providerEntries.size(), network.receiverEntries.size(), causes);
			}
			this.powerUpdateScratch.clear();
			this.updateFluidNetworks();
		}

		private void updateFluidNetworks() {
			int processed = 0;
			int visited = 0;
			this.fluidUpdateScratch.clear();
			this.fluidUpdateScratch.addAll(this.dirtyFluidNetworks);
			this.dirtyFluidNetworks.clear();
			for(FluidNetMK2 network : this.fluidUpdateScratch) {
				if(visited++ >= NETWORK_WORK_BUDGET) {
					this.dirtyFluidNetworks.add(network);
					continue;
				}
				if(!network.isValid()) continue;
				if(network.isTopologyRepairing()) {
					this.dirtyFluidNetworks.add(network);
					continue;
				}
				if(network.consumeDirtyCauses() == 0) continue;
				network.resetTrackers();
				network.update();
				processed++;
			}
			this.fluidUpdateScratch.clear();
			PowerNetDiagnostics.recordFluidNetworkInventory(this.fluidNetworks.size(), processed);
		}

		private void removePowerNetwork(PowerNetMK2 network) {
			this.powerNetworks.remove(network);
			this.dirtyPowerNetworks.remove(network);
		}

		private void removeFluidNetwork(FluidNetMK2 network) {
			this.fluidNetworks.remove(network);
			this.dirtyFluidNetworks.remove(network);
		}

		private void clear() {
			for(SplitTask task : this.splitTasks) task.source.topologyRepairing = false;
			this.nodesByProvider.clear();
			this.allNodes.clear();
			this.dirtyTopology.clear();
			this.pendingConductors.clear();
			this.splitTasks.clear();
			this.splitByNetwork.clear();
			for(MergeTask task : this.mergeTasks) {
				task.target.topologyRepairing = false;
				task.source.topologyRepairing = false;
			}
			this.mergeTasks.clear();
			this.powerNetworks.clear();
			this.dirtyPowerNetworks.clear();
			this.powerUpdateScratch.clear();
			this.fluidNetworks.clear();
			this.dirtyFluidNetworks.clear();
			this.fluidUpdateScratch.clear();
		}
	}

	private static int unpackX(long packed) { return (int) (packed << 0 >> 38); }
	private static int unpackY(long packed) { return (int) (packed & 0xFFFL); }
	private static int unpackZ(long packed) { return (int) (packed << 26 >> 38); }

	private static class MergeTask {

		private final NodeNet target;
		private final NodeNet source;
		private final GenNode origin;
		private final GenNode connection;
		private boolean complete;

		private MergeTask(NodeNet target, NodeNet source, GenNode origin, GenNode connection) {
			this.target = target;
			this.source = source;
			this.origin = origin;
			this.connection = connection;
		}

		private int process(int budget) {
			if(!this.target.isValid() || !this.source.isValid()) {
				this.target.topologyRepairing = false;
				this.source.topologyRepairing = false;
				this.complete = true;
				return 0;
			}
			int used = 0;
			while(used < budget && !this.source.links.isEmpty()) {
				GenNode node = (GenNode) this.source.links.iterator().next();
				this.source.links.remove(node);
				this.target.forceJoinLink(node);
				used++;
			}
			if(this.source.links.isEmpty()) {
				this.target.completeMergeFrom(this.source);
				this.target.topologyRepairing = false;
				this.source.topologyRepairing = false;
				this.complete = true;
			}
			return used;
		}
	}

	private static class SplitTask {

		private final NodeNet source;
		private final List<GenNode> seeds = new ArrayList<GenNode>(6);
		private final Set<GenNode> visited = Collections.newSetFromMap(new IdentityHashMap<GenNode, Boolean>());
		private final Deque<GenNode> frontier = new ArrayDeque<GenNode>();
		private final List<List<GenNode>> components = new ArrayList<List<GenNode>>(6);
		private final List<NodeNet> targets = new ArrayList<NodeNet>(6);
		private List<GenNode> current;
		private int seedIndex;
		private boolean assigning;
		private int keepIndex = -1;
		private int assignComponent;
		private int assignNode;
		private boolean complete;

		private SplitTask(NodeNet source, List<GenNode> seeds) {
			this.source = source;
			this.restart(seeds);
		}

		private void restart(List<GenNode> newSeeds) {
			this.seeds.clear();
			for(GenNode seed : newSeeds) if(seed != null && !this.seeds.contains(seed)) this.seeds.add(seed);
			this.visited.clear();
			this.frontier.clear();
			this.components.clear();
			this.targets.clear();
			this.current = null;
			this.seedIndex = 0;
			this.assigning = false;
			this.keepIndex = -1;
			this.assignComponent = 0;
			this.assignNode = 0;
			this.complete = false;
		}

		private int process(World world, int budget) {
			if(!this.source.isValid()) {
				this.source.topologyRepairing = false;
				this.complete = true;
				return 0;
			}
			int used = 0;
			while(used < budget && !this.assigning) {
				if(this.frontier.isEmpty()) {
					this.current = null;
					while(this.seedIndex < this.seeds.size()) {
						GenNode seed = this.seeds.get(this.seedIndex++);
						if(seed.expired || seed.net != this.source || this.visited.contains(seed)) continue;
						this.current = new ArrayList<GenNode>();
						this.components.add(this.current);
						this.frontier.add(seed);
						break;
					}
					if(this.frontier.isEmpty()) {
						this.prepareAssignment();
						break;
					}
				}
				GenNode node = this.frontier.remove();
				if(node.expired || node.net != this.source || !this.visited.add(node)) continue;
				this.current.add(node);
				for(GenNode neighbor : getConnectedNodes(world, node, true)) if(!this.visited.contains(neighbor)) this.frontier.add(neighbor);
				used++;
			}
			while(used < budget && this.assigning && !this.complete) {
				if(this.assignComponent >= this.components.size()) {
					this.finish();
					break;
				}
				if(this.assignComponent == this.keepIndex) {
					this.assignComponent++;
					this.assignNode = 0;
					continue;
				}
				List<GenNode> component = this.components.get(this.assignComponent);
				if(this.assignNode >= component.size()) {
					this.assignComponent++;
					this.assignNode = 0;
					continue;
				}
				GenNode node = component.get(this.assignNode++);
				if(node.net == this.source) {
					this.source.links.remove(node);
					this.targets.get(this.assignComponent).forceJoinLink(node);
				}
				used++;
			}
			return used;
		}

		private void prepareAssignment() {
			this.assigning = true;
			int largest = -1;
			for(int i = 0; i < this.components.size(); i++) {
				List<GenNode> component = this.components.get(i);
				if(component.size() > largest) {
					largest = component.size();
					this.keepIndex = i;
				}
			}
			for(int i = 0; i < this.components.size(); i++) this.targets.add(i == this.keepIndex ? this.source : this.components.get(i).get(0).networkProvider.provideNetwork());
			if(this.components.size() <= 1) this.finish();
		}

		private void finish() {
			this.source.topologyRepairing = false;
			this.source.topologyChanged();
			if(this.source.links.isEmpty()) this.source.destroy();
			for(int i = 1; i < this.components.size(); i++) PowerNetDiagnostics.recordSplit();
			this.complete = true;
		}
	}
}
