package com.hbm.tileentity.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.hbm.blocks.BlockDummyable;
import com.hbm.util.ColorUtil;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.Nodespace;
import api.hbm.energymk2.Nodespace.PowerNode;
import com.hbm.uninos.UniNodespace;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileEntityPylonBase extends TileEntityCableBaseNT {
	
	public List<int[]> connected = new ArrayList<int[]>();
	public int color;
	private UUID wireEndpointId = UUID.randomUUID();
	private boolean connectionsNeedSaving;
	
	public static int canConnect(TileEntityPylonBase first, TileEntityPylonBase second) {
		
		if(first == null || second == null || first.isInvalid() || second.isInvalid() || first.worldObj != second.worldObj)
			return 4;
		if(first.getWireClass() != second.getWireClass())
			return 1;
		
		if(first == second)
			return 2;
		if(first.hasConnection(second.xCoord, second.yCoord, second.zCoord)
				|| second.hasConnection(first.xCoord, first.yCoord, first.zCoord)) return 5;
		
		return withinWireRange(first, second) ? 0 : 3;
	}

	private static boolean withinWireRange(TileEntityPylonBase first, TileEntityPylonBase second) {
		double len = Math.min(first.getMaxWireLength(), second.getMaxWireLength());
		return first.getConnectionPoint().distanceTo(second.getConnectionPoint()) <= len;
	}

	/** Mount count describes geometry, not ordinary distribution service compatibility. */
	public WireClass getWireClass() { return WireClass.DISTRIBUTION; }
	public enum WireClass { DISTRIBUTION, TRANSMISSION }
	public UUID getWireEndpointId() { return wireEndpointId; }

	public static boolean validPosition(int x, int y, int z) {
		return x >= -30000000 && x < 30000000 && z >= -30000000 && z < 30000000 && y >= 0 && y < 256;
	}

	public static boolean endpointChunkLoaded(World world, int x, int y, int z) {
		return world != null && validPosition(x, y, z) && world.getChunkProvider().chunkExists(x >> 4, z >> 4)
				&& world.getChunkFromChunkCoords(x >> 4, z >> 4).isChunkLoaded;
	}

	/** Never creates a tile or loads a chunk, including during chunk attachment. */
	public static TileEntityPylonBase loadedPylon(World world, int x, int y, int z) {
		if(!endpointChunkLoaded(world, x, y, z)) return null;
		Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
		TileEntity tile = (TileEntity) chunk.chunkTileEntityMap.get(new net.minecraft.world.ChunkPosition(x & 15, y, z & 15));
		return tile instanceof TileEntityPylonBase && !tile.isInvalid() ? (TileEntityPylonBase) tile : null;
	}

	/** Follow only loaded multiblock parts; do not recursively force-load a damaged structure. */
	public static TileEntityPylonBase findPylon(World world, int x, int y, int z) {
		if(!endpointChunkLoaded(world, x, y, z)) return null;
		Block block = world.getBlock(x, y, z);
		if(block instanceof BlockDummyable) {
			for(int steps = 0; steps < 256; steps++) {
				if(!endpointChunkLoaded(world, x, y, z) || world.getBlock(x, y, z) != block) return null;
				int meta = world.getBlockMetadata(x, y, z);
				if(meta >= 12) return loadedPylon(world, x, y, z);
				ForgeDirection dir = ForgeDirection.getOrientation(meta % 6).getOpposite();
				x += dir.offsetX;
				y += dir.offsetY;
				z += dir.offsetZ;
			}
			return null;
		}
		return loadedPylon(world, x, y, z);
	}


	public boolean hasConnection(int x, int y, int z) {
		for(int[] pos : connected) if(pos != null && pos.length == 3 && pos[0] == x && pos[1] == y && pos[2] == z) return true;
		return false;
	}

	/** Both records commit before touching the derived graph; no node is required yet. */
	public static int connect(TileEntityPylonBase first, TileEntityPylonBase second) {
		if(first == null || second == null || first.worldObj == null || first.worldObj.isRemote
				|| loadedPylon(first.worldObj, first.xCoord, first.yCoord, first.zCoord) != first
				|| loadedPylon(first.worldObj, second.xCoord, second.yCoord, second.zCoord) != second) return 4;
		first.reconcileWireRecords();
		second.reconcileWireRecords();
		int result = canConnect(first, second);
		if(result != 0) return result;
		first.connected.add(new int[] {second.xCoord, second.yCoord, second.zCoord});
		second.connected.add(new int[] {first.xCoord, first.yCoord, first.zCoord});
		first.appendNodeConnection(second.xCoord, second.yCoord, second.zCoord);
		second.appendNodeConnection(first.xCoord, first.yCoord, first.zCoord);
		first.wireStateChanged();
		second.wireStateChanged();
		return 0;
	}
	
	public boolean setColor(ItemStack stack) {
		if(stack == null) return false;
		int color = ColorUtil.getColorFromDye(stack);
		if(color == 0 || color == this.color) return false;
		stack.stackSize--;
		this.color = color;
		
		this.markDirty();
		if(worldObj instanceof WorldServer) {
			WorldServer world = (WorldServer) worldObj;
			world.getPlayerManager().markBlockForUpdate(xCoord, yCoord, zCoord);
		}
		
		return true;
	}

	@Override
	public PowerNode createNode() {
		TileEntity tile = (TileEntity) this;
		PowerNode node = new PowerNode(new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord)).setConnections(new DirPos(xCoord, yCoord, zCoord, ForgeDirection.UNKNOWN));
		for(int[] pos : this.connected) node.addConnection(new DirPos(pos[0], pos[1], pos[2], ForgeDirection.UNKNOWN));
		return node;
	}
	
	public void addConnection(int x, int y, int z) {
		// Retained API entry point also performs the complete two-ended transaction.
		connect(this, loadedPylon(worldObj, x, y, z));
	}

	private void appendNodeConnection(int x, int y, int z) {
		PowerNode node = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
		if(node != null && node.hasStandardConnections()) {
			// A retained compact cable node cannot describe a replacement pylon's wire ports.
			UniNodespace.destroyNode(worldObj, node);
			this.node = null;
		} else if(node != null && !node.expired) {
			node.addConnection(new DirPos(x, y, z, ForgeDirection.UNKNOWN));
			UniNodespace.markNodeDirty(worldObj, node);
		}
		queueNodeReconciliation();
	}

	private void wireStateChanged() {
		markDirty();
		if(worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	private boolean removeRecord(int x, int y, int z) {
		boolean changed = false;
		for(Iterator<int[]> it = connected.iterator(); it.hasNext();) {
			int[] pos = it.next();
			if(pos == null || pos.length != 3 || (pos[0] == x && pos[1] == y && pos[2] == z)) {
				it.remove();
				changed = true;
			}
		}
		return changed;
	}

	/** Called only for an already removed endpoint, so its pop has already scheduled splits. */
	private static void detachNodeReference(World world, int x, int y, int z, int removedX, int removedY, int removedZ) {
		PowerNode remote = Nodespace.getNode(world, x, y, z);
		if(remote == null || remote.expired) return;
		List<DirPos> keep = new ArrayList<DirPos>();
		for(DirPos pos : remote.connections) {
			if(pos.getDir() == ForgeDirection.UNKNOWN && pos.getX() == removedX && pos.getY() == removedY && pos.getZ() == removedZ) continue;
			keep.add(pos);
		}
		remote.setConnections(keep.toArray(new DirPos[keep.size()]));
		UniNodespace.markNodeDirty(world, remote);
	}

	public void disconnectAll() {
		if(worldObj == null || worldObj.isRemote) return;
		TileEntityPylonBase current = loadedPylon(worldObj, xCoord, yCoord, zCoord);
		if(current != null && current != this) {
			// A repeated callback on an old tile must not detach a replacement's new wires.
			connected.clear();
			this.node = null;
			return;
		}
		List<int[]> old = new ArrayList<int[]>(connected);
		connected.clear();
		// Pop before altering reverse references: the old neighbors seed bounded split repair.
		Nodespace.destroyNode(worldObj, xCoord, yCoord, zCoord);
		this.node = null;
		for(int[] pos : old) {
			if(pos == null || pos.length != 3 || !validPosition(pos[0], pos[1], pos[2])
					|| (pos[0] == xCoord && pos[1] == yCoord && pos[2] == zCoord)) continue;
			TileEntityPylonBase remote = loadedPylon(worldObj, pos[0], pos[1], pos[2]);
			if(remote != null) {
				if(remote.removeRecord(xCoord, yCoord, zCoord)) remote.wireStateChanged();
			} else if(!endpointChunkLoaded(worldObj, pos[0], pos[1], pos[2])) {
				WireRemovalData.get(worldObj).removeWhenLoaded(new BlockPos(pos[0], pos[1], pos[2]), new BlockPos(xCoord, yCoord, zCoord));
			}
			detachNodeReference(worldObj, pos[0], pos[1], pos[2], xCoord, yCoord, zCoord);
		}
		if(!old.isEmpty()) wireStateChanged();
	}

	/** Journal first, then reciprocal validation. An unloaded peer is never treated as deleted. */
	private void reconcileWireRecords() {
		List<int[]> before = new ArrayList<int[]>(connected);
		Set<BlockPos> removals = WireRemovalData.get(worldObj).takeRemovals(new BlockPos(xCoord, yCoord, zCoord));
		boolean changed = connectionsNeedSaving;
		connectionsNeedSaving = false;
		if(removals != null) for(BlockPos pos : removals) changed |= removeRecord(pos.getX(), pos.getY(), pos.getZ());
		for(Iterator<int[]> it = connected.iterator(); it.hasNext();) {
			int[] pos = it.next();
			if(pos == null || pos.length != 3 || !validPosition(pos[0], pos[1], pos[2])) {
				it.remove();
				changed = true;
				continue;
			}
			if(!endpointChunkLoaded(worldObj, pos[0], pos[1], pos[2])) continue;
			TileEntityPylonBase remote = loadedPylon(worldObj, pos[0], pos[1], pos[2]);
			if(remote == null || remote == this || remote.getWireClass() != getWireClass()
					|| !remote.hasConnection(xCoord, yCoord, zCoord) || !withinWireRange(this, remote)) {
				it.remove();
				changed = true;
			}
		}
		if(changed) {
			Nodespace.destroyNode(worldObj, xCoord, yCoord, zCoord);
			this.node = null;
			for(int[] pos : before) {
				if(pos == null || pos.length != 3 || !validPosition(pos[0], pos[1], pos[2])
						|| hasConnection(pos[0], pos[1], pos[2])) continue;
				TileEntityPylonBase remote = loadedPylon(worldObj, pos[0], pos[1], pos[2]);
				if(remote != null && remote != this && remote.removeRecord(xCoord, yCoord, zCoord)) remote.wireStateChanged();
				detachNodeReference(worldObj, pos[0], pos[1], pos[2], xCoord, yCoord, zCoord);
			}
			wireStateChanged();
			queueNodeReconciliation();
		}
	}

	@Override
	public void reconcileConductorNode() {
		if(worldObj == null || worldObj.isRemote || isInvalid()) return;
		reconcileWireRecords();
		PowerNode registered = Nodespace.getNode(worldObj, xCoord, yCoord, zCoord);
		PowerNode desired = createNode();
		if(registered == null || registered.expired || !sameNodePorts(registered, desired)) {
			if(registered != null) UniNodespace.destroyNode(worldObj, registered);
			registered = desired;
			Nodespace.createNode(worldObj, registered);
		}
		this.node = registered;
	}

	private static boolean sameNodePorts(PowerNode first, PowerNode second) {
		if(first.hasStandardConnections() || !java.util.Arrays.equals(first.positions, second.positions)
				|| first.connections.length != second.connections.length) return false;
		for(int i = 0; i < first.connections.length; i++) {
			DirPos a = first.connections[i], b = second.connections[i];
			if(!a.equals(b) || a.getDir() != b.getDir()) return false;
		}
		return true;
	}
	
	public abstract ConnectionType getConnectionType();
	public abstract Vec3[] getMountPos();
	public abstract double getMaxWireLength();
	
	public Vec3 getConnectionPoint() {
		Vec3[] mounts = this.getMountPos();
		
		if(mounts == null || mounts.length == 0)
			return Vec3.createVectorHelper(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
		
		return mounts[0].addVector(xCoord, yCoord, zCoord);
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("conCount", connected.size());
		nbt.setInteger("color", color);
		nbt.setLong("wireEndpointMost", wireEndpointId.getMostSignificantBits());
		nbt.setLong("wireEndpointLeast", wireEndpointId.getLeastSignificantBits());
		
		for(int i = 0; i < connected.size(); i++) {
			nbt.setIntArray("con" + i, connected.get(i));
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		int count = nbt.getInteger("conCount");
		this.color = nbt.getInteger("color");
		if(nbt.hasKey("wireEndpointMost", 4) && nbt.hasKey("wireEndpointLeast", 4)) {
			wireEndpointId = new UUID(nbt.getLong("wireEndpointMost"), nbt.getLong("wireEndpointLeast"));
		}
		
		this.connected.clear();
		
		// Iterate actual tags rather than trusting an unbounded legacy conCount.
		TreeMap<Integer, int[]> records = new TreeMap<Integer, int[]>();
		for(Object keyObject : nbt.func_150296_c()) {
			String key = (String) keyObject;
			if(!key.startsWith("con") || key.length() <= 3 || key.length() > 13 || !nbt.hasKey(key, 11)) continue;
			long index = 0;
			for(int c = 3; c < key.length(); c++) {
				char digit = key.charAt(c);
				if(digit < '0' || digit > '9') { index = -1; break; }
				index = index * 10 + digit - '0';
			}
			if(index >= 0 && index < count) records.put((int) index, nbt.getIntArray(key));
		}
		Set<BlockPos> seen = new HashSet<BlockPos>();
		for(int[] pos : records.values()) {
			if(pos.length != 3 || !validPosition(pos[0], pos[1], pos[2])
					|| (pos[0] == xCoord && pos[1] == yCoord && pos[2] == zCoord)
					|| !seen.add(new BlockPos(pos[0], pos[1], pos[2]))) continue;
			connected.add(pos);
		}
		connectionsNeedSaving = count != connected.size() || !nbt.hasKey("wireEndpointMost", 4);
	}

	@Override
	public Packet getDescriptionPacket() {
		
		NBTTagCompound nbt = new NBTTagCompound();
		this.writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbt);
	}
	
	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		this.readFromNBT(pkt.func_148857_g());
	}

	public static enum ConnectionType {
		SINGLE,
		TRIPLE,
		QUAD
		//more to follow
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
