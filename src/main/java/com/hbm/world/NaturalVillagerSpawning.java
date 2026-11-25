package com.hbm.world;

import java.util.ArrayList;
import java.util.List;

import com.hbm.config.MobConfig;
import com.hbm.config.MobConfig.NaturalVillagerValues;
import com.hbm.world.gemini.GeminiInvasionManager;

import cpw.mods.fml.common.eventhandler.Event.Result;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraftforge.event.ForgeEventFactory;

public final class NaturalVillagerSpawning {

	private static final String NATURAL = "RNTNaturalVillager";
	private static final ThreadLocal<VillagerPack> SELECTION = new ThreadLocal<VillagerPack>();

	// Called from SpawnerAnimals so only the live passive spawn roll offers an RTM pack.
	public static Object selectPack(Object serverWorld, Object creatureType, int x, int y, int z) {
		WorldServer world = (WorldServer) serverWorld;
		EnumCreatureType type = (EnumCreatureType) creatureType;
		NaturalVillagerValues settings = MobConfig.naturalVillagers;
		if(type != EnumCreatureType.creature || world.provider.dimensionId != 0 || settings == null
				|| !settings.enabled || settings.weight == 0 || settings.localLimit == 0
				|| !world.blockExists(x, y, z) || !settings.allowsBiome(world.getBiomeGenForCoords(x, z).biomeID)
				|| GeminiInvasionManager.canActivityOccur(world) || world.getTopSolidOrLiquidBlock(x, z) != y) {
			return world.spawnRandomCreature(type, x, y, z);
		}

		VillagerPack previous = SELECTION.get();
		VillagerPack pack = new VillagerPack(world, x, y, z, settings);
		SELECTION.set(pack);
		try {
			SpawnListEntry entry = world.spawnRandomCreature(type, x, y, z);
			if(entry == pack) pack.remaining = settings.minGroup + world.rand.nextInt(settings.maxGroup - settings.minGroup + 1);
			return entry;
		} finally {
			if(previous == null) SELECTION.remove();
			else SELECTION.set(previous);
		}
	}

	public static List<SpawnListEntry> potentialSpawns(Object serverWorld, Object creatureType, int x, int y, int z,
			List<SpawnListEntry> entries) {
		WorldServer world = (WorldServer) serverWorld;
		EnumCreatureType type = (EnumCreatureType) creatureType;
		VillagerPack pack = type == EnumCreatureType.creature ? SELECTION.get() : null;
		if(pack != null && !pack.offered && world == pack.world && x == pack.x && y == pack.y && z == pack.z) {
			pack.offered = true;
			// Forge 1.7.10 exposes the original list to PotentialSpawns listeners.
			entries = entries == null ? new ArrayList<SpawnListEntry>() : new ArrayList<SpawnListEntry>(entries);
			entries.add(pack);
		}
		return ForgeEventFactory.getPotentialSpawns(world, type, x, y, z, entries);
	}

	public static Result checkSpawn(Object entity, Object sourceWorld, float x, float y, float z, Object entry) {
		EntityLiving mob = (EntityLiving) entity;
		World world = (World) sourceWorld;
		if(!(entry instanceof VillagerPack) || mob.getClass() != EntityVillager.class)
			return ForgeEventFactory.canEntitySpawn(mob, world, x, y, z);

		VillagerPack pack = (VillagerPack) entry;
		EntityVillager villager = (EntityVillager) mob;
		if(pack.remaining <= 0 || !canPlace(villager, pack.settings)
				|| ForgeEventFactory.canEntitySpawn(mob, world, x, y, z) == Result.DENY
				|| !belowLocalLimit(villager, pack.settings)) return Result.DENY;
		mob.getEntityData().setBoolean(NATURAL, true);
		return Result.ALLOW;
	}

	public static boolean insert(Object sourceWorld, Object entity, Object entry) {
		boolean inserted = ((WorldServer) sourceWorld).spawnEntityInWorld((Entity) entity);
		if(inserted && entry instanceof VillagerPack) ((VillagerPack) entry).remaining--;
		return inserted;
	}

	// Extend the existing passive spawn count without changing any villager's creature type elsewhere.
	public static boolean countsForSpawning(Object entity, Object creatureType, boolean forSpawnCount) {
		Entity mob = (Entity) entity;
		EnumCreatureType type = (EnumCreatureType) creatureType;
		return mob.isCreatureType(type, forSpawnCount) || forSpawnCount && type == EnumCreatureType.creature
				&& mob.addedToChunk && isNaturalVillager(mob) && mob.worldObj.provider.dimensionId == 0;
	}

	private static boolean canPlace(EntityVillager villager, NaturalVillagerValues settings) {
		World world = villager.worldObj;
		int x = MathHelper.floor_double(villager.posX);
		int y = MathHelper.floor_double(villager.posY);
		int z = MathHelper.floor_double(villager.posZ);
		return y > 0 && villager.boundingBox.maxY < world.getActualHeight()
				&& world.checkChunksExist(x - 2, y - 1, z - 2, x + 2, y + 2, z + 2)
				&& settings.allowsBiome(world.getBiomeGenForCoords(x, z).biomeID)
				&& world.getTopSolidOrLiquidBlock(x, z) == y
				&& SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.creature, world, x, y, z)
				&& world.getClosestPlayer(villager.posX, villager.posY, villager.posZ, settings.playerDistance) == null
				&& villager.getCanSpawnHere();
	}

	private static boolean belowLocalLimit(EntityVillager candidate, NaturalVillagerValues settings) {
		int radius = settings.radius;
		int radiusSquared = radius * radius;
		AxisAlignedBB area = AxisAlignedBB.getBoundingBox(candidate.posX - radius, 0, candidate.posZ - radius,
				candidate.posX + radius, candidate.worldObj.getHeight(), candidate.posZ + radius);
		int count = 0;
		// Forge's chunk-indexed lookup skips unloaded chunks and includes villagers already inserted by this pack.
		for(Object entity : candidate.worldObj.getEntitiesWithinAABB(EntityVillager.class, area)) {
			EntityVillager villager = (EntityVillager) entity;
			double dx = villager.posX - candidate.posX;
			double dz = villager.posZ - candidate.posZ;
			if(dx * dx + dz * dz <= radiusSquared && isNaturalVillager(villager) && ++count >= settings.localLimit) return false;
		}
		return true;
	}

	private static boolean isNaturalVillager(Entity entity) {
		return entity.getClass() == EntityVillager.class && entity.isEntityAlive() && entity.getEntityData().getBoolean(NATURAL);
	}

	private static final class VillagerPack extends SpawnListEntry {
		final WorldServer world;
		final int x, y, z;
		final NaturalVillagerValues settings;
		boolean offered;
		int remaining;

		VillagerPack(WorldServer world, int x, int y, int z, NaturalVillagerValues settings) {
			super(EntityVillager.class, settings.weight, settings.minGroup, settings.maxGroup);
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
			this.settings = settings;
		}
	}
}
