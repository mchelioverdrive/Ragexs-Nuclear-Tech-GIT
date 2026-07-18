package api.hbm.energymk2;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.hbm.util.Compat;
import com.hbm.util.fauxpointtwelve.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Short-lived cache for hot neighbor tile lookups performed by MK2 energy providers and receivers.
 *
 * Entries are scoped by World instance instead of dimension id so an unloaded/recreated dimension cannot
 * inherit stale tile references. The outer WeakHashMap allows worlds to be collected even if an unload
 * event is missed, and the unload handler explicitly drops entries as soon as Forge announces it.
 */
public class TileAccessCache {

	private static final int NULL_TTL = 20;
	private static final int TILE_TTL = 60;
	private static final Map<World, Map<BlockPos, TileAccessEntry>> CACHE = new WeakHashMap();

	static {
		MinecraftForge.EVENT_BUS.register(new TileAccessCache());
	}

	public static TileEntity getTile(World world, int x, int y, int z) {
		if(world == null || !world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
			clear(world, x, y, z);
			return null;
		}

		BlockPos pos = new BlockPos(x, y, z);
		long now = world.getTotalWorldTime();

		synchronized(CACHE) {
			Map<BlockPos, TileAccessEntry> worldCache = CACHE.get(world);
			TileAccessEntry entry = worldCache != null ? worldCache.get(pos) : null;
			if(entry != null) {
				if(entry.isValid(world, x, y, z, now)) {
					return entry.tile;
				}
				worldCache.remove(pos);
			}
		}

		TileEntity tile = Compat.getTileStandard(world, x, y, z);
		if(tile != null && tile.isInvalid()) {
			return null;
		}

		synchronized(CACHE) {
			Map<BlockPos, TileAccessEntry> worldCache = CACHE.get(world);
			if(worldCache == null) {
				worldCache = new HashMap();
				CACHE.put(world, worldCache);
			}
			worldCache.put(pos, new TileAccessEntry(tile, now + (tile == null ? NULL_TTL : TILE_TTL)));
		}

		return tile;
	}

	private static void clear(World world, int x, int y, int z) {
		if(world == null) return;

		synchronized(CACHE) {
			Map<BlockPos, TileAccessEntry> worldCache = CACHE.get(world);
			if(worldCache != null) {
				worldCache.remove(new BlockPos(x, y, z));
				if(worldCache.isEmpty()) {
					CACHE.remove(world);
				}
			}
		}
	}

	public static void clear(World world) {
		if(world == null) return;

		synchronized(CACHE) {
			CACHE.remove(world);
		}
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event) {
		clear(event.world);
	}

	private static class TileAccessEntry {
		private final TileEntity tile;
		private final long expiresAt;

		private TileAccessEntry(TileEntity tile, long expiresAt) {
			this.tile = tile;
			this.expiresAt = expiresAt;
		}

		private boolean isValid(World world, int x, int y, int z, long now) {
			if(now >= expiresAt) return false;
			if(tile == null) return true;
			return !tile.isInvalid() && tile.worldObj == world && tile.xCoord == x && tile.yCoord == y && tile.zCoord == z;
		}
	}
}
