package com.hbm.world;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.BiomeSyncPacket;
import com.hbm.util.Compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.chunkio.ChunkIOExecutor;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkDataEvent;

public class WorldUtil {

	private static final MethodHandle getBiomeShortHandle;

	static {
		if(Loader.isModLoaded(Compat.MOD_EIDS)) {
			try {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				MethodType methodType = MethodType.methodType(short[].class);
				getBiomeShortHandle = lookup.findVirtual(Chunk.class, "getBiomeShortArray", methodType);
			} catch(Exception e) {
				throw new AssertionError();
			}
		} else {
			getBiomeShortHandle = null;
		}
	}

	public static void setBiome(World world, int x, int z, BiomeGenBase biome) {
		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		if(Loader.isModLoaded(Compat.MOD_EIDS)) {
			short[] array = getBiomeShortArray(chunk);
			array[(z & 15) << 4 | x & 15] = (short) biome.biomeID;
		} else {
			chunk.getBiomeArray()[(z & 15) << 4 | (x & 15)] = (byte)(biome.biomeID & 255);
		}
		chunk.isModified = true;
	}

	/** A slice-local view; never retain this across chunk unload/reload or scheduled slices. */
	public static final class BiomeAccess {
		private final Chunk chunk;
		private final byte[] bytes;
		private final short[] shorts;

		public BiomeAccess(Chunk chunk) {
			this.chunk = chunk;
			shorts = getBiomeShortHandle != null ? getBiomeShortArray(chunk) : null;
			bytes = shorts == null ? chunk.getBiomeArray() : null;
		}

		public BiomeGenBase get(World world, int x, int z) {
			int index = (z & 15) << 4 | (x & 15);
			int id = shorts != null ? shorts[index] & 65535 : bytes[index] & 255;
			// Let the world's normal (and optionally patched) path initialize unset entries.
			if(id == (shorts != null ? 65535 : 255)) return world.getBiomeGenForCoords(x, z);
			BiomeGenBase biome = BiomeGenBase.getBiome(id);
			return biome == null ? BiomeGenBase.plains : biome;
		}

		public boolean setIfChanged(int x, int z, BiomeGenBase original, BiomeGenBase biome) {
			if(biome == null || biome.biomeID == original.biomeID) return false;
			int index = (z & 15) << 4 | (x & 15);
			if(shorts != null) shorts[index] = (short)biome.biomeID;
			else bytes[index] = (byte)(biome.biomeID & 255);
			chunk.setChunkModified();
			return true;
		}
	}

	public static void syncBiomeChange(World world, int x, int z) {
		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		syncBiomeChange(world, x, z, chunk);
	}

	public static void syncBiomeChange(World world, int x, int z, Chunk chunk) {
		if(Loader.isModLoaded(Compat.MOD_EIDS)) {
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(x >> 4, z >> 4, getBiomeShortArray(chunk)), new TargetPoint(world.provider.dimensionId, x, 128, z, 1024D));
		} else {
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(x >> 4, z >> 4, chunk.getBiomeArray()), new TargetPoint(world.provider.dimensionId, x, 128, z, 1024D));
		}
	}

	public static void syncBiomeChangeBlock(World world, int x, int z) {
		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		if(Loader.isModLoaded(Compat.MOD_EIDS)) {
			short biome = getBiomeShortArray(chunk)[(z & 15) << 4 | (x & 15)];
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(x, z, biome), new TargetPoint(world.provider.dimensionId, x, 128, z, 1024D));
		} else {
			byte biome = chunk.getBiomeArray()[(z & 15) << 4 | (x & 15)];
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(x, z, biome), new TargetPoint(world.provider.dimensionId, x, 128, z, 1024D));
		}
	}

	public static void syncBiomeChange(World world, Chunk chunk) {
		/* "let's not make all this valuable information accessible, at all, hehe hoho huehue" -mojank, probably */
		/*if(!(world instanceof WorldServer)) return;
		WorldServer server = (WorldServer) world;
		PlayerManager man = server.getPlayerManager();
		Method getOrCreateChunkWatcher = ReflectionHelper.findMethod(PlayerManager.class, man, new String[] {"getOrCreateChunkWatcher"}, int.class, int.class, boolean.class);
		int x = chunk.getChunkCoordIntPair().chunkXPos;
		int z = chunk.getChunkCoordIntPair().chunkZPos;
		PlayerManager.PlayerInstance playerinstance = (PlayerInstance) getOrCreateChunkWatcher.invoke(man, x, z, false);*/

		/* this sucks ass */
		ChunkCoordIntPair coord = chunk.getChunkCoordIntPair();
		if(Loader.isModLoaded(Compat.MOD_EIDS)) {
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(coord.chunkXPos, coord.chunkZPos, getBiomeShortArray(chunk)), new TargetPoint(world.provider.dimensionId, coord.getCenterXPos(), 128, coord.getCenterZPosition() /* who named you? */, 1024D));
		} else {
			PacketDispatcher.wrapper.sendToAllAround(new BiomeSyncPacket(coord.chunkXPos, coord.chunkZPos, chunk.getBiomeArray()), new TargetPoint(world.provider.dimensionId, coord.getCenterXPos(), 128, coord.getCenterZPosition() /* who named you? */, 1024D));
		}
	}

	public static short[] getBiomeShortArray(Chunk chunk) {
		try {
			return (short[]) getBiomeShortHandle.invokeExact(chunk);
		} catch(Throwable ex) {
			throw new AssertionError();
		}
	}

	/**Chunkloads the chunk the entity is going to spawn in and then spawns it
	 * @param entity The entity to be spawned**/

	/*fun fact: this is based off of joinEntityInSurroundings in World
	  however, since mojang is staffed by field mice, that function is client side only and half-baked
	 */
	public static void loadAndSpawnEntityInWorld(Entity entity) {

		World world = entity.worldObj;
		int chunkX = MathHelper.floor_double(entity.posX / 16.0D);
		int chunkZ = MathHelper.floor_double(entity.posZ / 16.0D);
		byte loadRadius = 2;

		for (int k = chunkX - loadRadius; k <= chunkX + loadRadius; ++k)
		{
			for (int l = chunkZ - loadRadius; l <= chunkZ + loadRadius; ++l)
			{
				world.getChunkFromChunkCoords(k, l);
			}
		}

		if (!world.loadedEntityList.contains(entity))
		{
			if (!MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, world)))
			{
				world.getChunkFromChunkCoords(chunkX, chunkZ).addEntity(entity);
				world.loadedEntityList.add(entity);
				world.onEntityAdded(entity);
			}
		}
	}

	public static Chunk provideChunk(WorldServer world, int chunkX, int chunkZ) {
		try {
			ChunkProviderServer provider = world.theChunkProviderServer;
			Chunk chunk = (Chunk) provider.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
			if(chunk != null) return chunk;
			return loadChunk(world, provider, chunkX, chunkZ);
		} catch(Throwable x) {
			return null;
		}
	}

	/** Server-thread existing-terrain path with no generation fallback. Forge's
	 * syncChunkLoad falls back to originalLoadChunk when disk decoding fails, so
	 * it cannot implement a strict no-generation policy. Use the 1.7.10 loader
	 * stages and normal chunk/entity/load-event registration here. Population is
	 * left to external loading when generation is forbidden by the caller. */
	public static Chunk loadExistingChunk(WorldServer world, int chunkX, int chunkZ) throws java.io.IOException {
		ChunkProviderServer provider = world.theChunkProviderServer;
		long key = ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ);
		Chunk chunk = (Chunk)provider.loadedChunkHashMap.getValueByKey(key);
		if(chunk != null || provider.currentChunkLoader == null) return chunk;
		if(provider.currentChunkLoader instanceof AnvilChunkLoader) {
			AnvilChunkLoader loader = (AnvilChunkLoader)provider.currentChunkLoader;
			// Despite the API name, this call and all subsequent stages run here on
			// the owning server thread. NBT never enters explosion worker inputs.
			Object[] data = loader.loadChunk__Async(world, chunkX, chunkZ);
			if(data == null) return null;
			chunk = (Chunk)data[0];
			net.minecraft.nbt.NBTTagCompound compound = (net.minecraft.nbt.NBTTagCompound)data[1];
			loader.loadEntities(world, compound.getCompoundTag("Level"), chunk);
			MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, compound));
		} else {
			// IChunkLoader has no existence probe. Its load method is the disk-only
			// boundary; never fall through to ChunkProviderServer's generator.
			chunk = provider.currentChunkLoader.loadChunk(world, chunkX, chunkZ);
			if(chunk == null) return null;
		}
		chunk.lastSaveTime = world.getTotalWorldTime();
		provider.chunksToUnload.remove(Long.valueOf(key));
		provider.loadedChunkHashMap.add(key, chunk); provider.loadedChunks.add(chunk);
		chunk.onChunkLoad();
		if(provider.currentChunkProvider != null) provider.currentChunkProvider.recreateStructures(chunkX, chunkZ);
		return chunk;
	}

	private static Chunk loadChunk(WorldServer world, ChunkProviderServer provider, int chunkX, int chunkZ) {
		long chunkCoord = ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ);
		provider.chunksToUnload.remove(Long.valueOf(chunkCoord));
		Chunk chunk = (Chunk) provider.loadedChunkHashMap.getValueByKey(chunkCoord);
		AnvilChunkLoader loader = null;

		if(provider.currentChunkLoader instanceof AnvilChunkLoader) {
			loader = (AnvilChunkLoader) provider.currentChunkLoader;
		}

		if(chunk == null && loader != null && loader.chunkExists(world, chunkX, chunkZ)) {
			chunk = ChunkIOExecutor.syncChunkLoad(world, loader, provider, chunkX, chunkZ);
		}

		return chunk;
	}
}
