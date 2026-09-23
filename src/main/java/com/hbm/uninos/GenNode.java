package com.hbm.uninos;

import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import net.minecraft.world.World;

public class GenNode<N extends NodeNet> {
	
	public BlockPos[] positions;
	public DirPos[] connections = new DirPos[0];
	private boolean standardConnections;
	private int standardX;
	private int standardY;
	private int standardZ;
	/** Quick reminder that this CAN and WILL be null for the first tick between the node being created
	 * and the nodepsace update loop establishing a network. always check hasValidNet beforehand! */
	public N net;
	public boolean expired = false;
	public boolean recentlyChanged = true;
	/** Owning world, assigned by UniNodespace when the node is registered. */
	public World world;
	/** Used for distinguishing the node type when saving it to UNINOS' node map */
	public INetworkProvider networkProvider;
	
	public GenNode(INetworkProvider<N> provider, BlockPos... positions) {
		this.networkProvider = provider;
		this.positions = positions;
	}
	
	public GenNode<N> setConnections(DirPos... connections) {
		this.standardConnections = false;
		this.connections = connections != null ? connections : new DirPos[0];
		return this;
	}
	
	public GenNode<N> setStandardConnections(int xCoord, int yCoord, int zCoord) {
		this.standardConnections = true;
		this.standardX = xCoord;
		this.standardY = yCoord;
		this.standardZ = zCoord;
		this.connections = new DirPos[0];
		return this;
	}

	public boolean hasStandardConnections() { return this.standardConnections; }
	public int getStandardX() { return this.standardX; }
	public int getStandardY() { return this.standardY; }
	public int getStandardZ() { return this.standardZ; }
	
	public GenNode<N> addConnection(DirPos connection) {
		if(this.standardConnections) throw new IllegalStateException("Cannot append an exceptional connection to a compact standard node");
		DirPos[] newCons = new DirPos[this.connections.length + 1];
		for(int i = 0; i < this.connections.length; i++) newCons[i] = this.connections[i];
		newCons[newCons.length - 1] = connection;
		this.connections = newCons;
		return this;
	}
	
	public boolean hasValidNet() {
		return this.net != null && this.net.isValid();
	}
	
	public void setNet(N net) {
		this.net = net;
		this.recentlyChanged = true;
	}
}
