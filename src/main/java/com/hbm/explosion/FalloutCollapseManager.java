package com.hbm.explosion;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.hbm.entity.item.EntityFallingBlockNT;

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
		if(entity instanceof EntityFallingBlockNT && !entity.worldObj.isRemote && !entity.isDead && ((EntityFallingBlockNT)entity).isFalloutCollapse()) {
			active.add((EntityFallingBlockNT)entity);
		}
	}

	public static void remove(EntityFallingBlockNT entity) { active.remove(entity); }

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
		if(event.entity instanceof EntityFallingBlockNT && ((EntityFallingBlockNT)event.entity).isFalloutCollapse()) observe(event.world);
	}

	@SubscribeEvent
	public void unload(WorldEvent.Unload event) {
		Observer observer = observers.remove(event.world);
		if(observer != null) event.world.removeWorldAccess(observer);
		active.removeIf(entity -> entity.worldObj == event.world);
	}

	public static void clear() {
		for(Map.Entry<World, Observer> entry : observers.entrySet()) entry.getKey().removeWorldAccess(entry.getValue());
		observers.clear(); active.clear(); spawnTick = Long.MIN_VALUE; spawns = 0;
	}

	private static final class Observer implements IWorldAccess {
		public void onEntityCreate(Entity entity) { register(entity); }
		public void onEntityDestroy(Entity entity) { if(entity instanceof EntityFallingBlockNT) remove((EntityFallingBlockNT)entity); }
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
