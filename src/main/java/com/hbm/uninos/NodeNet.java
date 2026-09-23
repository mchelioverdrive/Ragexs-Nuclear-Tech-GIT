package com.hbm.uninos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import api.hbm.tile.ILoadedTile;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public abstract class NodeNet<R, P, L extends GenNode> {
	
	/** Global random for figuring things out like random leftover distribution */
	public static Random rand = new Random();
	
	public boolean valid = true;
	/** True while a bounded topology repair owns this network. Distribution waits until repair completes. */
	public boolean topologyRepairing;
	public Set<L> links = new LinkedHashSet();
	protected World world;

	public HashMap<R, Long> receiverEntries = new HashMap();
	public HashMap<P, Long> providerEntries = new HashMap();
	
	public NodeNet() {
		UniNodespace.activeNodeNets.add(this);
	}

	/// SUBSCRIBER HANDLING ///
	public boolean isSubscribed(R receiver) { return this.receiverEntries.containsKey(receiver); }
	public void addReceiver(R receiver) { this.receiverEntries.put(receiver, System.currentTimeMillis()); }
	public void removeReceiver(R receiver) { this.receiverEntries.remove(receiver); }

	/// PROVIDER HANDLING ///
	public boolean isProvider(P provider) { return this.providerEntries.containsKey(provider); }
	public void addProvider(P provider) { this.providerEntries.put(provider, System.currentTimeMillis()); }
	public void removeProvider(P provider) { this.providerEntries.remove(provider); }
	
	/** Combines two networks into one */
	public void joinNetworks(NodeNet network) {
		if(network == this) return;

		List<L> oldNodes = new ArrayList(network.links.size());
		oldNodes.addAll(network.links);
		
		for(L conductor : oldNodes) forceJoinLink(conductor);
		network.links.clear();

		this.completeMergeFrom(network);
	}

	/** Finalizes endpoint ownership after a synchronous or bounded link merge. */
	public void completeMergeFrom(NodeNet network) {
		for(Object connector : new ArrayList(network.receiverEntries.keySet())) this.addReceiver((R) connector);
		for(Object connector : new ArrayList(network.providerEntries.keySet())) this.addProvider((P) connector);
		network.destroy();
		this.onTopologyChanged();
	}

	/** Adds the node as part of this network's links */
	public NodeNet joinLink(L node) {
		if(node.net != null) node.net.leaveLink(node);
		return forceJoinLink(node);
	}

	/** Adds the node as part of this network's links, skips the part about removing it from existing networks */
	public NodeNet forceJoinLink(L node) {
		if(this.world == null && node.world != null) {
			this.world = node.world;
			UniNodespace.registerNetwork(this);
		}
		this.links.add(node);
		node.setNet(this);
		this.onTopologyChanged();
		return this;
	}

	/** Removes the specified node */
	public void leaveLink(L node) {
		node.setNet(null);
		this.links.remove(node);
		this.onTopologyChanged();
	}
	
	/// GENERAL POWER NET CONTROL ///
	public void invalidate() {
		this.valid = false;
		UniNodespace.activeNodeNets.remove(this);
		UniNodespace.unregisterNetwork(this);
	}
	public boolean isValid() { return this.valid; }
	public boolean isTopologyRepairing() { return this.topologyRepairing; }
	public World getWorld() { return this.world; }
	public void resetTrackers() { }
	public abstract void update();
	protected void onTopologyChanged() { }
	public void topologyChanged() { this.onTopologyChanged(); }

	public void reapExpiredLinks() {
		boolean changed = false;
		Iterator<L> iterator = this.links.iterator();
		while(iterator.hasNext()) {
			L link = iterator.next();
			if(!link.expired) continue;
			iterator.remove();
			changed = true;
		}
		if(changed) this.onTopologyChanged();
	}
	
	public void destroy() {
		this.invalidate();
		for(GenNode link : this.links) if(link.net == this) link.setNet(null);
		this.links.clear();
		this.receiverEntries.clear();
		this.providerEntries.clear();
	}
	
	public static boolean isBadLink(Object o) {
		if(o instanceof ILoadedTile && !((ILoadedTile) o).isLoaded()) return true;
		if(o instanceof TileEntity && ((TileEntity) o).isInvalid()) return true;
		return false;
	}
}
