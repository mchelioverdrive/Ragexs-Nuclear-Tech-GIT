package com.hbm.explosion;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.UUID;

import com.hbm.entity.item.EntityFallingBlockNT;
import com.hbm.entity.effect.EntityFalloutRain;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Server-thread lifecycle accounting, independent of the fallout jobs that spawned entities. */
public final class FalloutCollapseManager {

	public static final int MAX_SPAWNS_PER_TICK = 32;
	public static final int MAX_ACTIVE_ENTITIES = 256;
	private static final Set<EntityFallingBlockNT> active = new HashSet<EntityFallingBlockNT>();
	private static final Map<World, Observer> observers = new IdentityHashMap<World, Observer>();
	private static final Map<World, Map<UUID, EntityFalloutRain>> rains = new IdentityHashMap<World, Map<UUID, EntityFalloutRain>>();
	private static final int MAX_TRACKED_RAIN = 4096;
	private static long spawnTick = Long.MIN_VALUE;
	private static int spawns;

	public static boolean canSpawn(long tick) {
		if(spawnTick != tick) { spawnTick = tick; spawns = 0; }
		return spawns < MAX_SPAWNS_PER_TICK && active.size() < MAX_ACTIVE_ENTITIES;
	}

	public static void spawned(EntityFallingBlockNT entity) {
		spawns++;
		register(entity);
	}

	private static void register(Entity entity) {
		if(entity instanceof EntityFalloutRain && !entity.worldObj.isRemote && !entity.isDead) {
			Map<UUID, EntityFalloutRain> worldRains = rains.get(entity.worldObj);
			if(worldRains == null) { worldRains = new HashMap<UUID, EntityFalloutRain>(); rains.put(entity.worldObj, worldRains); }
			if(worldRains.size() < MAX_TRACKED_RAIN) worldRains.put(entity.getUniqueID(), (EntityFalloutRain)entity);
		}
		if(entity instanceof EntityFallingBlockNT && !entity.worldObj.isRemote && !entity.isDead && ((EntityFallingBlockNT)entity).isFalloutCollapse()) {
			active.add((EntityFallingBlockNT)entity);
		}
	}

	public static void remove(EntityFallingBlockNT entity) { active.remove(entity); }
	public static EntityFalloutRain rain(World world, UUID id) {
		Map<UUID, EntityFalloutRain> worldRains = rains.get(world);
		return worldRains == null ? null : worldRains.get(id);
	}
	public static boolean hasActiveNear(World world, int cx, int cz) {
		int inspected = 0;
		for(EntityFallingBlockNT entity : active) {
			// Legacy saves can load more entities than new spawns permit. An
			// incomplete settling check must never authorize cleanup.
			if(inspected++ == MAX_ACTIVE_ENTITIES) return true;
			if(entity.worldObj == world && !entity.isDead && Math.abs((int)Math.floor(entity.posX / 16D) - cx) <= 1
					&& Math.abs((int)Math.floor(entity.posZ / 16D) - cz) <= 1) return true;
		}
		return false;
	}

	private static void observe(World world) {
		if(!world.isRemote && !observers.containsKey(world)) {
			Observer observer = new Observer();
			observers.put(world, observer);
			world.addWorldAccess(observer);
		}
	}

	@SubscribeEvent
	public void load(WorldEvent.Load event) { observe(event.world); }

	@SubscribeEvent(receiveCanceled = true)
	public void join(EntityJoinWorldEvent event) {
		// Some initial chunk entities load before WorldEvent.Load. Install the observer
		// here, but count only onEntityCreate, after the join actually succeeds.
		if(event.entity instanceof EntityFalloutRain || event.entity instanceof EntityFallingBlockNT && ((EntityFallingBlockNT)event.entity).isFalloutCollapse()) observe(event.world);
	}

	@SubscribeEvent
	public void unload(WorldEvent.Unload event) {
		Observer observer = observers.remove(event.world);
		if(observer != null) event.world.removeWorldAccess(observer);
		active.removeIf(entity -> entity.worldObj == event.world);
		rains.remove(event.world);
	}

	public static void clear() {
		for(Map.Entry<World, Observer> entry : observers.entrySet()) entry.getKey().removeWorldAccess(entry.getValue());
		observers.clear(); active.clear(); rains.clear(); spawnTick = Long.MIN_VALUE; spawns = 0;
	}

	private static final class Observer implements IWorldAccess {
		public void onEntityCreate(Entity entity) { register(entity); }
		public void onEntityDestroy(Entity entity) {
			if(entity instanceof EntityFallingBlockNT) remove((EntityFallingBlockNT)entity);
			if(entity instanceof EntityFalloutRain) {
				Map<UUID, EntityFalloutRain> worldRains = rains.get(entity.worldObj);
				if(worldRains != null) { worldRains.remove(entity.getUniqueID()); if(worldRains.isEmpty()) rains.remove(entity.worldObj); }
			}
		}
		public void markBlockForUpdate(int x, int y, int z) { }
		public void markBlockForRenderUpdate(int x, int y, int z) { }
		public void markBlockRangeForRenderUpdate(int x, int y, int z, int x2, int y2, int z2) { }
		public void playSound(String sound, double x, double y, double z, float volume, float pitch) { }
		public void playSoundToNearExcept(EntityPlayer player, String sound, double x, double y, double z, float volume, float pitch) { }
		public void spawnParticle(String particle, double x, double y, double z, double vx, double vy, double vz) { }
		public void playRecord(String record, int x, int y, int z) { }
		public void broadcastSound(int sound, int x, int y, int z, int data) { }
		public void playAuxSFX(EntityPlayer player, int effect, int x, int y, int z, int data) { }
		public void destroyBlockPartially(int entity, int x, int y, int z, int progress) { }
		public void onStaticEntitiesChanged() { }
	}
}
