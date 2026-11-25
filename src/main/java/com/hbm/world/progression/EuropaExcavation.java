package com.hbm.world.progression;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.laythe.WorldProviderLaythe;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Chunk-local provenance for placed terrain and a short queue of pre-mutation Forge events. */
public final class EuropaExcavation {

	private static final String PLACED_TERRAIN = "RNTPlacedEuropaTerrain";
	private static final Map<Chunk, BitSet> placed = new WeakHashMap<>();
	private static final Map<World, Map<Chunk, Map<Integer, Change>>> pending = new WeakHashMap<>();

	static boolean isEuropa(World world) {
		return !world.isRemote && world.provider instanceof WorldProviderLaythe;
	}

	private static boolean isTerrain(Block block) {
		return block == Blocks.packed_ice || block == Blocks.snow || block == Blocks.stone
				|| block == ModBlocks.laythe_silt || block == ModBlocks.ore_oil
				|| block == Blocks.netherrack || block == Blocks.clay
				|| block == ModBlocks.ore_sulfur || block == ModBlocks.ore_asbestos;
	}

	private static int index(int x, int y, int z) {
		return (y << 8) | ((z & 15) << 4) | (x & 15);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onBreak(BlockEvent.BreakEvent event) {
		if(!isEuropa(event.world) || !isTerrain(event.block) || !(event.getPlayer() instanceof EntityPlayerMP)
				|| event.getPlayer() instanceof FakePlayer) return;
		queue(event, event.x, event.y, event.z, event.block, event.block);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlace(BlockEvent.PlaceEvent event) {
		if(!isEuropa(event.world)) return;
		if(event instanceof BlockEvent.MultiPlaceEvent) {
			for(BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
				queue(event, snapshot.x, snapshot.y, snapshot.z, snapshot.getCurrentBlock(), snapshot.getReplacedBlock());
			}
		} else {
			queue(event, event.x, event.y, event.z, event.placedBlock, event.blockSnapshot.getReplacedBlock());
		}
	}

	private static void queue(BlockEvent event, int x, int y, int z, Block block, Block before) {
		World world = event.world;
		if(!world.blockExists(x, y, z)) return;
		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		int position = index(x, y, z);
		if(event instanceof BlockEvent.PlaceEvent && !isTerrain(block) && !isTerrain(before)
				&& !isPlaced(chunk, position)) return;
		Map<Integer, Change> changes = pending.computeIfAbsent(world, key -> new HashMap<>())
				.computeIfAbsent(chunk, key -> new HashMap<>());
		// A second action in the same tick supplies the first action's post-state.
		Change previous = changes.put(position, new Change(event, block));
		if(previous != null) finish(chunk, position, previous, before);
	}

	/** Call after a supported machine has successfully removed the supplied original block. */
	public static void recordMachineRemoval(World world, int x, int y, int z, Block block) {
		if(!isEuropa(world) || !isTerrain(block) || !world.blockExists(x, y, z)) return;
		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		int position = index(x, y, z);
		Map<Chunk, Map<Integer, Change>> chunks = pending.get(world);
		Map<Integer, Change> changes = chunks == null ? null : chunks.get(chunk);
		Change previous = changes == null ? null : changes.remove(position);
		if(previous != null) finish(chunk, position, previous, block);
		recordRemoval(chunk, position);
	}

	public static void finishBlockEvents(World world) {
		Map<Chunk, Map<Integer, Change>> chunks = pending.remove(world);
		if(chunks != null) chunks.forEach(EuropaExcavation::finishChunk);
	}

	private static void finishChunk(Chunk chunk, Map<Integer, Change> changes) {
		changes.forEach((position, change) -> finish(chunk, position, change,
				chunk.getBlock(position & 15, position >> 8, (position >> 4) & 15)));
	}

	private static void finish(Chunk chunk, int position, Change change, Block after) {
		if(change.event.isCanceled()) return;
		if(change.event instanceof BlockEvent.PlaceEvent) {
			if(change.block == after) setPlaced(chunk, position, isTerrain(after));
		} else if(change.block != after) {
			recordRemoval(chunk, position);
		}
	}

	private static void recordRemoval(Chunk chunk, int position) {
		if(isPlaced(chunk, position)) {
			setPlaced(chunk, position, false);
		} else {
			EuropaLankEncounters.recordExcavation();
		}
	}

	private static boolean isPlaced(Chunk chunk, int position) {
		BitSet blocks = placed.get(chunk);
		return blocks != null && blocks.get(position);
	}

	private static void setPlaced(Chunk chunk, int position, boolean value) {
		if(value == isPlaced(chunk, position)) return;
		BitSet blocks = placed.computeIfAbsent(chunk, key -> new BitSet());
		blocks.set(position, value);
		if(blocks.isEmpty()) placed.remove(chunk);
		chunk.setChunkModified();
	}

	@SubscribeEvent
	public void onLoad(ChunkDataEvent.Load event) {
		if(!isEuropa(event.world) || !event.getData().hasKey(PLACED_TERRAIN)) return;
		BitSet blocks = BitSet.valueOf(event.getData().getByteArray(PLACED_TERRAIN));
		if(!blocks.isEmpty()) placed.put(event.getChunk(), blocks);
	}

	@SubscribeEvent
	public void onSave(ChunkDataEvent.Save event) {
		if(!isEuropa(event.world)) return;
		Chunk chunk = event.getChunk();
		Map<Chunk, Map<Integer, Change>> chunks = pending.get(event.world);
		Map<Integer, Change> changes = chunks == null ? null : chunks.remove(chunk);
		if(changes != null) finishChunk(chunk, changes);
		BitSet blocks = placed.get(chunk);
		if(blocks != null) event.getData().setByteArray(PLACED_TERRAIN, blocks.toByteArray());
	}

	@SubscribeEvent
	public void onUnload(WorldEvent.Unload event) {
		if(isEuropa(event.world)) finishBlockEvents(event.world);
	}

	private static final class Change {
		final BlockEvent event;
		final Block block;

		Change(BlockEvent event, Block block) {
			this.event = event;
			this.block = block;
		}
	}
}
