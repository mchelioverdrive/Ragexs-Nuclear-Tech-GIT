package com.hbm.world;

import java.util.ArrayList;
import java.util.List;

import com.hbm.config.MobConfig;
import com.hbm.config.MobConfig.NaturalVillagerValues;
import com.hbm.config.SquirrelConfig;
import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.entity.mob.squirrel.SquirrelHabitat;
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
	private static final ThreadLocal<SpawnSelection> SELECTION = new ThreadLocal<>();

	// Called from SpawnerAnimals so only the live passive spawn roll offers these packs.
	public static Object selectPack(Object serverWorld, Object creatureType, int x, int y, int z) {
		WorldServer world = (WorldServer) serverWorld;
		EnumCreatureType type = (EnumCreatureType) creatureType;
		NaturalVillagerValues settings = MobConfig.naturalVillagers;
		if(type != EnumCreatureType.creature || world.provider.dimensionId != 0 || !world.blockExists(x, y, z)) {
			return world.spawnRandomCreature(type, x, y, z);
		}

		VillagerPack villagers = null;
		if(!com.hbm.world.squirrelstapler.StaplerWorldType.active(world) && settings != null && settings.enabled && settings.weight > 0 && settings.localLimit > 0
				&& settings.allowsBiome(world.getBiomeGenForCoords(x, z).biomeID)
				&& !GeminiInvasionManager.canActivityOccur(world) && world.getTopSolidOrLiquidBlock(x, z) == y) {
			villagers = new VillagerPack(settings);
		}
		SquirrelConfig.Values squirrelSettings = SquirrelConfig.settings;
		SquirrelPack squirrels = SquirrelHabitat.naturalSite(world, x, y, z, squirrelSettings)
				? new SquirrelPack(squirrelSettings) : null;
		if(villagers == null && squirrels == null) return world.spawnRandomCreature(type, x, y, z);

		SpawnSelection previous = SELECTION.get();
		SELECTION.set(new SpawnSelection(world, x, y, z, villagers, squirrels));
		try {
			SpawnListEntry entry = world.spawnRandomCreature(type, x, y, z);
			if(entry instanceof SpawnPack)
				((SpawnPack) entry).remaining = entry.minGroupCount + world.rand.nextInt(entry.maxGroupCount - entry.minGroupCount + 1);
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
		SpawnSelection selection = type == EnumCreatureType.creature ? SELECTION.get() : null;
		if(selection != null && !selection.offered && world == selection.world
				&& x == selection.x && y == selection.y && z == selection.z) {
			selection.offered = true;
			// Forge 1.7.10 exposes the original list to PotentialSpawns listeners.
			entries = entries == null ? new ArrayList<SpawnListEntry>() : new ArrayList<SpawnListEntry>(entries);
			if(selection.villagers != null) entries.add(selection.villagers);
			if(selection.squirrels != null) entries.add(selection.squirrels);
		}
		return ForgeEventFactory.getPotentialSpawns(world, type, x, y, z, entries);
	}

	public static Result checkSpawn(Object entity, Object sourceWorld, float x, float y, float z, Object entry) {
		EntityLiving mob = (EntityLiving) entity;
		World world = (World) sourceWorld;
		if(entry instanceof SquirrelPack && mob instanceof EntitySquirrel) {
			SquirrelPack pack = (SquirrelPack) entry;
			EntitySquirrel squirrel = (EntitySquirrel) mob;
			if(pack.remaining <= 0 || !squirrel.getCanSpawnHere()
					|| !SquirrelHabitat.naturalSite(world, MathHelper.floor_double(x), MathHelper.floor_double(y),
							MathHelper.floor_double(z), pack.settings)
					|| ForgeEventFactory.canEntitySpawn(mob, world, x, y, z) == Result.DENY
					|| !SquirrelHabitat.belowLocalLimit(squirrel, pack.settings.localLimit)) return Result.DENY;
			return Result.ALLOW;
		}
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
		if(inserted && entry instanceof SpawnPack) ((SpawnPack) entry).remaining--;
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

	private static final class SpawnSelection {
		final WorldServer world;
		final int x, y, z;
		final VillagerPack villagers;
		final SquirrelPack squirrels;
		boolean offered;

		SpawnSelection(WorldServer world, int x, int y, int z, VillagerPack villagers, SquirrelPack squirrels) {
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
			this.villagers = villagers;
			this.squirrels = squirrels;
		}
	}

	private abstract static class SpawnPack extends SpawnListEntry {
		int remaining;

		SpawnPack(Class<? extends EntityLiving> type, int weight, int min, int max) {
			super(type, weight, min, max);
		}
	}

	private static final class VillagerPack extends SpawnPack {
		final NaturalVillagerValues settings;

		VillagerPack(NaturalVillagerValues settings) {
			super(EntityVillager.class, settings.weight, settings.minGroup, settings.maxGroup);
			this.settings = settings;
		}
	}

	private static final class SquirrelPack extends SpawnPack {
		final SquirrelConfig.Values settings;

		SquirrelPack(SquirrelConfig.Values settings) {
			super(EntitySquirrel.class, settings.spawnWeight, settings.minGroupSize, settings.maxGroupSize);
			this.settings = settings;
		}
	}
}
