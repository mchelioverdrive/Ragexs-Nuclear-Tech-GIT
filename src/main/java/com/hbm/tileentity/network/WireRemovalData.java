package com.hbm.tileentity.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hbm.util.fauxpointtwelve.BlockPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Removal journal only; endpoint NBT still owns wires and UniNodespace owns live topology. */
public class WireRemovalData extends WorldSavedData {

	private static final String NAME = "hbm_wire_removals";
	private final Map<BlockPos, Set<BlockPos>> pending = new HashMap<BlockPos, Set<BlockPos>>();

	public WireRemovalData() { super(NAME); }
	public WireRemovalData(String name) { super(name); }

	static WireRemovalData get(World world) {
		WireRemovalData data = (WireRemovalData) world.perWorldStorage.loadData(WireRemovalData.class, NAME);
		if(data == null) {
			data = new WireRemovalData();
			world.perWorldStorage.setData(NAME, data);
		}
		return data;
	}

	void removeWhenLoaded(BlockPos target, BlockPos removed) {
		Set<BlockPos> removals = pending.get(target);
		if(removals == null) {
			removals = new HashSet<BlockPos>();
			pending.put(target, removals);
		}
		if(removals.add(removed)) markDirty();
	}

	Set<BlockPos> takeRemovals(BlockPos target) {
		Set<BlockPos> removals = pending.remove(target);
		if(removals != null) markDirty();
		return removals;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		pending.clear();
		NBTTagList entries = nbt.getTagList("removals", 10);
		for(int i = 0; i < entries.tagCount(); i++) {
			int[] edge = entries.getCompoundTagAt(i).getIntArray("edge");
			if(edge.length != 6 || !TileEntityPylonBase.validPosition(edge[0], edge[1], edge[2])
					|| !TileEntityPylonBase.validPosition(edge[3], edge[4], edge[5])) continue;
			removeWhenLoaded(new BlockPos(edge[0], edge[1], edge[2]), new BlockPos(edge[3], edge[4], edge[5]));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		NBTTagList entries = new NBTTagList();
		for(Map.Entry<BlockPos, Set<BlockPos>> entry : pending.entrySet()) {
			BlockPos target = entry.getKey();
			for(BlockPos removed : entry.getValue()) {
				NBTTagCompound tag = new NBTTagCompound();
				tag.setIntArray("edge", new int[] {target.getX(), target.getY(), target.getZ(), removed.getX(), removed.getY(), removed.getZ()});
				entries.appendTag(tag);
			}
		}
		nbt.setTag("removals", entries);
	}
}
