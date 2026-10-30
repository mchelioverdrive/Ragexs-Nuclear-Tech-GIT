package com.hbm.explosion;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;

/** Server-thread scopes plus persistent, exact block attribution for delayed updates. */
public final class NuclearDropSuppression extends WorldSavedData {

	private static final String NAME = "rntNuclearLeafDrops";
	private static final int MAX_LEAVES = 65536;
	private final LinkedHashMap<Long, Integer> leaves = new LinkedHashMap<Long, Integer>();
	private static World scopedWorld;
	private static Thread scopedThread;
	private static int depth;
	private static long suppressed;
	public NuclearDropSuppression() { super(NAME); }
	public NuclearDropSuppression(String name) { super(name); }
	private static NuclearDropSuppression data(World world) {
		NuclearDropSuppression data = (NuclearDropSuppression)world.perWorldStorage.loadData(NuclearDropSuppression.class, NAME);
		if(data == null) { data = new NuclearDropSuppression(); world.perWorldStorage.setData(NAME, data); }
		return data;
	}
	public static long suppressed() { return suppressed; }
	public static Scope enter(World world) { return new Scope(world); }
	public static final class Scope implements AutoCloseable {
		private final World previous;
		private Scope(World world) { previous = scopedWorld; scopedWorld = world; scopedThread = Thread.currentThread(); depth++; }
		public void close() { if(--depth == 0) { scopedWorld = null; scopedThread = null; } else scopedWorld = previous; }
	}
	public static void clearInventory(World world, int x, int y, int z) {
		Object tile = world.getTileEntity(x, y, z);
		if(tile instanceof IInventory) {
			IInventory inventory = (IInventory)tile;
			for(int i = 0; i < inventory.getSizeInventory(); i++) {
				if(inventory.getStackInSlot(i) != null) { inventory.setInventorySlotContents(i, null); suppressed++; }
			}
		}
	}
	public static void harvest(HarvestDropsEvent event) {
		if(event.world.isRemote || event.harvester != null) return;
		boolean nuclear = depth != 0 && scopedWorld == event.world && scopedThread == Thread.currentThread();
		if(!nuclear && (event.block instanceof BlockLeaves || NuclearBlockBehavior.fragilePlant(event.block))) {
			NuclearDropSuppression data = data(event.world);
			long key = key(event.x, event.y, event.z);
			Integer id = data.leaves.get(key);
			nuclear = id != null && id == Block.getIdFromBlock(event.block)
					&& (event.block instanceof BlockLeaves ? (event.world.getBlockMetadata(event.x, event.y, event.z) & 8) != 0
							: !event.block.canBlockStay(event.world, event.x, event.y, event.z));
			if(nuclear) { data.leaves.remove(key); data.markDirty(); }
		}
		if(nuclear) { suppressed += event.drops.size(); event.drops.clear(); event.dropChance = 0F; }
	}
	public static void spawning(EntityJoinWorldEvent event) {
		if(!event.world.isRemote && event.entity instanceof net.minecraft.entity.item.EntityFallingBlock) {
			net.minecraft.entity.item.EntityFallingBlock falling = (net.minecraft.entity.item.EntityFallingBlock)event.entity;
			NuclearDropSuppression data = (NuclearDropSuppression)event.world.perWorldStorage.loadData(NuclearDropSuppression.class, NAME);
			long key = key(net.minecraft.util.MathHelper.floor_double(falling.posX), net.minecraft.util.MathHelper.floor_double(falling.posY), net.minecraft.util.MathHelper.floor_double(falling.posZ));
			Integer id = data == null ? null : data.leaves.get(key);
			if(id != null && id == Block.getIdFromBlock(falling.func_145805_f())) { falling.field_145813_c = false; data.leaves.remove(key); data.markDirty(); }
		}
		if(depth == 0 || scopedWorld != event.world || scopedThread != Thread.currentThread() || !(event.entity instanceof EntityItem)) return;
		// Only newly spawned items during nuclear callbacks. Player tosses and
		// death inventories remain valid even if a block callback triggers them.
		for(StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			String name = frame.getClassName(), method = frame.getMethodName();
			// A callback may load a chunk. Those saved items are observers of the
			// load, not freshly scattered drops from the destroyed block.
			if(name.equals("net.minecraft.world.World") && (method.equals("addLoadedEntities") || method.equals("func_72868_a")
					|| method.equals("joinEntityInSurroundings") || method.equals("func_72897_h"))) return;
			if(name.startsWith("net.minecraft.entity.") && (method.equals("onDeath") || method.equals("func_70645_a")
					|| name.startsWith("net.minecraft.entity.player."))) return;
		}
		suppressed++; event.setCanceled(true);
	}

	/** Reserve before the native log/leaf callback can schedule any decay.
	 * A full journal delays the callback rather than forgetting attribution. */
	static boolean prepareLeaves(World world, int x, int y, int z, int radius) {
		NuclearDropSuppression data = data(world);
		for(int dx = -radius; dx <= radius; dx++) for(int dz = -radius; dz <= radius; dz++) {
			if(!world.getChunkProvider().chunkExists((x + dx) >> 4, (z + dz) >> 4)) continue;
			for(int yy = Math.max(0, y - radius); yy <= Math.min(255, y + radius); yy++) {
				Block b = world.getBlock(x + dx, yy, z + dz);
				if(!(b instanceof BlockLeaves)) continue;
				long key = key(x + dx, yy, z + dz);
				if(!data.leaves.containsKey(key) && data.leaves.size() == MAX_LEAVES) return false;
				data.leaves.put(key, Block.getIdFromBlock(b)); data.markDirty();
			}
		}
		return true;
	}
	static boolean prepareGravity(World world, int x, int y, int z) {
		Block above = world.getBlock(x, y + 1, z);
		if(!(above instanceof net.minecraft.block.BlockFalling)) return true;
		NuclearDropSuppression data = data(world); long key = key(x, y + 1, z);
		if(!data.leaves.containsKey(key) && data.leaves.size() == MAX_LEAVES) return false;
		data.leaves.put(key, Block.getIdFromBlock(above)); data.markDirty(); return true;
	}
	/** Fallout and its saved collapse entities run after the crater selectors.
	 * Retain exact delayed support checks as well as leaf decay, never a radius. */
	public static boolean prepareMutation(World world, int x, int y, int z) {
		Block old = world.getBlock(x, y, z);
		if(old instanceof net.minecraft.block.BlockLog || old instanceof BlockLeaves) {
			if(!prepareLeaves(world, x, y, z, old instanceof net.minecraft.block.BlockLog ? 4 : 1)) return false;
		}
		Block above = world.getBlock(x, y + 1, z);
		if(NuclearBlockBehavior.fragilePlant(above)) {
			NuclearDropSuppression data = data(world); long key = key(x, y + 1, z);
			if(!data.leaves.containsKey(key) && data.leaves.size() == MAX_LEAVES) return false;
			data.leaves.put(key, Block.getIdFromBlock(above)); data.markDirty();
		}
		return prepareGravity(world, x, y, z);
	}
	static void removed(World world, int x, int y, int z) {
		NuclearDropSuppression data = (NuclearDropSuppression)world.perWorldStorage.loadData(NuclearDropSuppression.class, NAME);
		if(data != null && data.leaves.remove(key(x, y, z)) != null) data.markDirty();
	}
	public static void tick(World world) {
		if(world.isRemote) return;
		NuclearDropSuppression data = (NuclearDropSuppression)world.perWorldStorage.loadData(NuclearDropSuppression.class, NAME);
		if(data == null) return;
		// Bounded FIFO pruning, with no terrain loads or area-wide item searches.
		int count = Math.min(64, data.leaves.size());
		while(count-- > 0) {
			Iterator<Map.Entry<Long, Integer>> iterator = data.leaves.entrySet().iterator();
			Map.Entry<Long, Integer> entry = iterator.next(); long key = entry.getKey(); int id = entry.getValue(); iterator.remove();
			int x = (int)(key >> 38), y = (int)(key & 255), z = (int)(key << 26 >> 38);
			boolean keep = !world.getChunkProvider().chunkExists(x >> 4, z >> 4);
			if(!keep) {
				Block b = world.getBlock(x, y, z);
				keep = Block.getIdFromBlock(b) == id && (b instanceof BlockLeaves ? (world.getBlockMetadata(x, y, z) & 8) != 0
						: NuclearBlockBehavior.fragilePlant(b) ? !b.canBlockStay(world, x, y, z)
						: b instanceof net.minecraft.block.BlockFalling && net.minecraft.block.BlockFalling.func_149831_e(world, x, y - 1, z));
			}
			if(keep) data.leaves.put(key, id);
			else data.markDirty();
		}
	}
	private static long key(int x, int y, int z) { return ((long)x & 0x3ffffffL) << 38 | ((long)z & 0x3ffffffL) << 12 | y & 255L; }
	@Override public void readFromNBT(NBTTagCompound tag) {
		leaves.clear(); int[] values = tag.getIntArray("leaves");
		for(int i = 0; i + 2 < values.length && leaves.size() < MAX_LEAVES; i += 3)
			leaves.put((long)values[i] << 32 | values[i + 1] & 0xffffffffL, values[i + 2]);
	}
	@Override public void writeToNBT(NBTTagCompound tag) {
		int[] values = new int[leaves.size() * 3]; int i = 0;
		for(Map.Entry<Long, Integer> entry : leaves.entrySet()) { values[i++] = (int)(entry.getKey() >> 32); values[i++] = (int)(long)entry.getKey(); values[i++] = entry.getValue(); }
		tag.setIntArray("leaves", values);
	}
}
