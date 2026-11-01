package com.hbm.explosion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hbm.entity.effect.EntityFalloutRain;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

/** One server-thread sweep of retained crater sections, under the explosion slice. */
final class CraterRemnantCleanup {
	// At most ~3 MiB of section/visited bookkeeping, 512 component cells, and
	// eight million probes per crater. Exhausted coverage is deliberately retained.
	private static final int MAX_SECTIONS = 4096, MAX_COMPONENT = 512, MAX_DISTANCE = 32;
	private static final long MAX_PROBES = 8_000_000L;
	private final ExplosionNukeRayBatched owner;
	private final ArrayList<Section> sections = new ArrayList<Section>();
	private final Map<Long, Section> index = new HashMap<Long, Section>();
	private final ArrayList<Cell> component = new ArrayList<Cell>();
	private final Set<Long> members = new HashSet<Long>();
	private final Set<Long> knownAir = new HashSet<Long>();
	private int sectionCursor, bitCursor, head, side, phase, removal;
	private int minX, minY, minZ, maxX, maxY, maxZ;
	private long probes, blockedTick = Long.MIN_VALUE;
	private long validatedTick = Long.MIN_VALUE;
	private boolean complete, invalid, recheck, awaitingFallout, falloutFinished;
	private UUID fallout;
	private long changing = Long.MIN_VALUE;
	private final long[] settledKeys = new long[16];
	private final int[] settledStates = new int[16];
	private int settledCount;
	private long settledTick = Long.MIN_VALUE;
	private NBTTagList restoring;
	private int restoreCursor;

	private static final class Section {
		final long key;
		final BitSet visited = new BitSet(4096);
		Section(long key) { this.key = key; }
	}
	private static final class Cell {
		final long key;
		final Block block;
		final int meta;
		Cell(long key, Block block, int meta) { this.key = key; this.block = block; this.meta = meta; }
	}
	CraterRemnantCleanup(ExplosionNukeRayBatched owner) { this.owner = owner; }
	private static long key(int x, int y, int z) { return ((long)x & 0x3ffffffL) << 38 | ((long)z & 0x3ffffffL) << 12 | y & 4095L; }
	private static int x(long key) { return (int)(key >> 38); }
	private static int y(long key) { return (int)(key & 4095); }
	private static int z(long key) { return (int)(key << 26 >> 38); }
	private static int bit(int x, int y, int z) { return (y & 15) << 8 | (z & 15) << 4 | x & 15; }
	private Section section(int x, int y, int z) { return index.get(key(x & ~15, y & ~15, z & ~15)); }
	void affect(int cx, int sy, int cz) {
		if(complete) return;
		long key = key(cx << 4, sy << 4, cz << 4);
		if(index.containsKey(key) || sections.size() == MAX_SECTIONS) return;
		Section section = new Section(key); sections.add(section); index.put(key, section);
	}
	boolean pending() { return !complete; }
	boolean ready(long tick) { return !complete && blockedTick != tick; }
	boolean valid() { return !invalid && !recheck; }
	long changingAt(int x, int y, int z) { long previous = changing; changing = key(x, y, z); return previous; }
	void finishChange(long previous) { changing = previous; }
	void removed(int x, int y, int z) { long key = key(x, y, z); if(members.contains(key)) knownAir.add(key); }
	void awaitFallout() { awaitingFallout = true; }
	void bindFallout(UUID id) { fallout = id; awaitingFallout = false; }
	void noFallout() { awaitingFallout = false; }
	void finishedFallout(UUID id) { if(id.equals(fallout)) falloutFinished = true; }
	/** 0 = unavailable/unresolved, 1 = still changing, 2 = locally settled. */
	private int settled(int cx, int cz) {
		long tick = ExplosionWorkScheduler.currentTick();
		if(settledTick != tick) { settledTick = tick; settledCount = 0; }
		long key = net.minecraft.world.ChunkCoordIntPair.chunkXZ2Int(cx, cz);
		for(int i = 0; i < settledCount; i++) if(settledKeys[i] == key) return settledStates[i];
		int state = localState(cx, cz);
		if(settledCount < settledKeys.length) { settledKeys[settledCount] = key; settledStates[settledCount++] = state; }
		return state;
	}
	private int localState(int cx, int cz) {
		if(awaitingFallout) return 1;
		if(fallout != null && !falloutFinished) {
			EntityFalloutRain rain = FalloutCollapseManager.rain(owner.world, fallout);
			if(rain == null) return 0;
			if(!rain.isChunkSettled(cx, cz)) return 1;
		}
		return FalloutCollapseManager.hasActiveNear(owner.world, cx, cz) ? 1 : 2;
	}
	void changed(int x, int y, int z, int x2, int y2, int z2) {
		settledCount = 0;
		if(component.isEmpty()) return;
		if(x == x2 && y == y2 && z == z2 && key(x, y, z) == changing) return;
		if(x <= maxX + 1 && x2 >= minX - 1 && y <= maxY + 1 && y2 >= minY - 1 && z <= maxZ + 1 && z2 >= minZ - 1) invalid = true;
	}
	void renderChanged(int x, int y, int z, int x2, int y2, int z2) {
		settledCount = 0;
		if(!component.isEmpty() && x <= maxX + 1 && x2 >= minX - 1 && y <= maxY + 1 && y2 >= minY - 1 && z <= maxZ + 1 && z2 >= minZ - 1)
			// Lighting also emits range notifications during our safe removals.
			// Revalidate actual cells instead of discarding a group for relighting.
			recheck = true;
	}
	void unloaded(int cx, int cz) {
		changed(cx << 4, 0, cz << 4, (cx << 4) + 15, 255, (cz << 4) + 15);
	}
	void advance(long deadline, long tick) {
		if(!restore(deadline)) return;
		while(!complete && ExplosionWorkScheduler.before(deadline)) {
			if(probes >= MAX_PROBES) { close(); return; }
			if(!component.isEmpty()) {
				if(invalid) { retire(); continue; }
				if(recheck) { head = side = 0; if(phase == 2) phase = 1; recheck = false; }
				if(phase < 2) search(deadline, tick);
				else if(validatedTick == tick) remove(deadline);
				else { head = side = 0; phase = 1; }
				continue;
			}
			if(sectionCursor >= sections.size()) { close(); return; }
			Section section = sections.get(sectionCursor);
			int bx = x(section.key), by = y(section.key), bz = z(section.key);
			int settled = settled(bx >> 4, bz >> 4);
			if(settled == 1) { blockedTick = tick; return; }
			Chunk chunk = ExplosionTerrainAccess.loaded(owner.world, bx >> 4, bz >> 4);
			if(settled == 0 || chunk == null) { section.visited.set(0, 4096); sectionCursor++; bitCursor = 0; continue; }
			if(bitCursor >= 4096) { sectionCursor++; bitCursor = 0; continue; }
			int bit = bitCursor;
			if(section.visited.get(bit)) { bitCursor++; continue; }
			int x = bx + (bit & 15), y = by + (bit >> 8), z = bz + (bit >> 4 & 15);
			probes++;
			Block block = chunk.getBlock(x & 15, y, z & 15);
			if(!owner.insideCrater(x, y, z) || block == Blocks.air || block.isAir(owner.world, x, y, z)
					|| !eligible(block, x, y, z)) { section.visited.set(bit); bitCursor++; continue; }
			add(x, y, z, block, chunk.getBlockMetadata(x & 15, y, z & 15));
		}
	}
	private boolean eligible(Block block, int x, int y, int z) {
		return !block.getMaterial().isLiquid() && !Float.isInfinite(NuclearBlockBehavior.cost(owner.world, block, x, y, z, owner.posX, owner.posY, owner.posZ));
	}
	private void add(int x, int y, int z, Block block, int meta) {
		if(component.isEmpty()) { minX = maxX = x; minY = maxY = y; minZ = maxZ = z; }
		else { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); }
		long key = key(x, y, z); component.add(new Cell(key, block, meta)); members.add(key);
	}
	private void search(long deadline, long tick) {
		while(head < component.size() && ExplosionWorkScheduler.before(deadline) && probes < MAX_PROBES) {
			Cell cell = component.get(head);
			int x = x(cell.key), y = y(cell.key), z = z(cell.key);
			Chunk chunk = ExplosionTerrainAccess.loaded(owner.world, x >> 4, z >> 4);
			if(phase == 1 && head < removal) { head++; side = 0; continue; }
			if(chunk != null && knownAir.contains(cell.key) && chunk.getBlock(x & 15, y, z & 15) == Blocks.air) { head++; side = 0; continue; }
			if(chunk == null || chunk.getBlock(x & 15, y, z & 15) != cell.block || chunk.getBlockMetadata(x & 15, y, z & 15) != cell.meta
					|| settled(x >> 4, z >> 4) != 2 || !eligible(cell.block, x, y, z)) { retire(); return; }
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[side];
			int xx = x + dir.offsetX, yy = y + dir.offsetY, zz = z + dir.offsetZ;
			probes++;
			if(yy >= 0 && yy <= 255) {
				Chunk neighbor = ExplosionTerrainAccess.loaded(owner.world, xx >> 4, zz >> 4);
				if(neighbor == null) { retire(); return; }
				Block block = neighbor.getBlock(xx & 15, yy, zz & 15);
				if(block != Blocks.air && !block.isAir(owner.world, xx, yy, zz) && !members.contains(key(xx, yy, zz))) {
					if(phase == 1) { retire(); return; }
					Section section = section(xx, yy, zz);
					Cell seed = component.get(0);
					// A boundary, protected/supporting neighbor, unavailable coverage or
					// exhausted search is UNRESOLVED, never evidence of disconnection.
					if(!owner.insideCrater(xx, yy, zz) || section == null || section.visited.get(bit(xx, yy, zz))
							|| component.size() == MAX_COMPONENT || Math.abs(xx - x(seed.key)) > MAX_DISTANCE
							|| Math.abs(yy - y(seed.key)) > MAX_DISTANCE || Math.abs(zz - z(seed.key)) > MAX_DISTANCE
							|| settled(xx >> 4, zz >> 4) != 2 || !eligible(block, xx, yy, zz)) { retire(); return; }
					add(xx, yy, zz, block, neighbor.getBlockMetadata(xx & 15, yy, zz & 15));
				}
			}
			if(++side == 6) { side = 0; head++; }
		}
		if(head == component.size()) {
			if(phase == 0) { phase = 1; head = side = 0; }
			else { phase = 2; validatedTick = tick; }
		}
	}
	private void remove(long deadline) {
		if(removal == component.size()) { retire(); return; }
		Cell cell = component.get(removal);
		int x = x(cell.key), y = y(cell.key), z = z(cell.key);
		// Only loaded chunks: cleanup never adds terrain loading or generation.
		for(int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) for(int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
			if(ExplosionTerrainAccess.loaded(owner.world, cx, cz) == null || settled(cx, cz) != 2) { retire(); return; }
		}
		if(!ExplosionWorkScheduler.before(deadline)) return;
		probes++;
		changing = cell.key;
		try {
			if(!owner.removeRemnant(x, y, z, cell.block, cell.meta, deadline)) {
				// A slice ending is not a rejected component. Keep this removal
				// cursor unless a callback actually invalidated the live decision.
				if(!invalid && (recheck || !ExplosionWorkScheduler.before(deadline))) return;
				retire(); return;
			}
		} finally { changing = Long.MIN_VALUE; }
		removal++;
	}
	private void retire() {
		for(Cell cell : component) {
			Section section = section(x(cell.key), y(cell.key), z(cell.key));
			if(section != null) section.visited.set(bit(x(cell.key), y(cell.key), z(cell.key)));
		}
		component.clear(); members.clear(); knownAir.clear(); head = side = phase = removal = 0; invalid = recheck = false;
	}
	void close() { retire(); sections.clear(); index.clear(); settledCount = 0; restoring = null; complete = true; }
	void read(NBTTagCompound tag) {
		close(); complete = tag.getBoolean("complete");
		sectionCursor = Math.max(0, tag.getInteger("section")); bitCursor = Math.max(0, Math.min(4096, tag.getInteger("bit")));
		probes = Math.max(0L, tag.getLong("probes")); awaitingFallout = tag.getBoolean("awaitingFallout"); falloutFinished = tag.getBoolean("falloutFinished");
		fallout = tag.hasKey("falloutMost") ? new UUID(tag.getLong("falloutMost"), tag.getLong("falloutLeast")) : null;
		if(!complete) restoring = tag.getTagList("sections", 10);
		restoreCursor = 0; blockedTick = Long.MIN_VALUE;
	}
	boolean restore(long deadline) {
		while(restoring != null && restoreCursor < Math.min(MAX_SECTIONS, restoring.tagCount()) && ExplosionWorkScheduler.before(deadline)) {
			NBTTagCompound data = restoring.getCompoundTagAt(restoreCursor++);
			int x = data.getInteger("x"), y = data.getInteger("y"), z = data.getInteger("z");
			if(x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000 || y < 0 || y > 255) { close(); return true; }
			affect(x >> 4, y >> 4, z >> 4);
			byte[] bits = data.getByteArray("visited");
			section(x, y, z).visited.or(BitSet.valueOf(bits.length <= 512 ? bits : java.util.Arrays.copyOf(bits, 512)));
		}
		if(restoring != null && restoreCursor >= Math.min(MAX_SECTIONS, restoring.tagCount())) restoring = null;
		return restoring == null;
	}
	NBTTagCompound write() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setBoolean("complete", complete); tag.setInteger("section", sectionCursor); tag.setInteger("bit", bitCursor); tag.setLong("probes", probes);
		tag.setBoolean("awaitingFallout", awaitingFallout); tag.setBoolean("falloutFinished", falloutFinished);
		if(fallout != null) { tag.setLong("falloutMost", fallout.getMostSignificantBits()); tag.setLong("falloutLeast", fallout.getLeastSignificantBits()); }
		if(restoring != null) { tag.setTag("sections", restoring.copy()); return tag; }
		NBTTagList list = new NBTTagList();
		for(Section section : sections) {
			NBTTagCompound data = new NBTTagCompound(); data.setInteger("x", x(section.key)); data.setInteger("y", y(section.key)); data.setInteger("z", z(section.key));
			data.setByteArray("visited", section.visited.toByteArray()); list.appendTag(data);
		}
		// The scan stays on its seed until a component retires. On reload only
		// that bounded, uncommitted search is redone against the current world.
		tag.setTag("sections", list); return tag;
	}
}
