package com.hbm.entity.mob.squirrel;

import com.hbm.config.SquirrelConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

public final class SquirrelHabitat {
    private static final int POPULATION_RADIUS = 48;

    private SquirrelHabitat() {}

    public static boolean naturalSite(World world, int x, int y, int z, SquirrelConfig.Values settings) {
        if(!settings.enabled || !settings.naturalSpawning || settings.spawnWeight == 0 || settings.localLimit == 0
                || world.provider.dimensionId != 0 || y < 1 || y >= world.getActualHeight() - 1
                || !world.blockExists(x, y, z)) return false;
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if(!BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.FOREST)) return false;
        Block floor = world.getBlock(x, y - 1, z);
        if(floor != Blocks.grass && floor != Blocks.dirt && floor != Blocks.mycelium) return false;
        if(!SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.creature, world, x, y, z)) return false;

        // Open sky or a canopy overhead. A solid roof, even a shallow one, rules out this column.
        int skyline = world.getHeightValue(x, z);
        for(int above = y + 1; above < skyline; above++) {
            Block block = world.getBlock(x, above, z);
            if(!block.isAir(world, x, above, z) && !block.isLeaves(world, x, above, z)) return false;
        }
        for(int dx = -3; dx <= 3; dx++) for(int dz = -3; dz <= 3; dz++) {
            if(!world.blockExists(x + dx, y, z + dz)) continue;
            for(int above = y - 1; above <= Math.min(y + 5, world.getActualHeight() - 1); above++) {
                Block block = world.getBlock(x + dx, above, z + dz);
                if(block.isLeaves(world, x + dx, above, z + dz) || block.isWood(world, x + dx, above, z + dz)) return true;
            }
        }
        return false;
    }

    public static boolean belowLocalLimit(EntitySquirrel candidate, int limit) {
        World world = candidate.worldObj;
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(candidate.posX - POPULATION_RADIUS, 0, candidate.posZ - POPULATION_RADIUS,
                candidate.posX + POPULATION_RADIUS, world.getActualHeight(), candidate.posZ + POPULATION_RADIUS);
        int count = 0;
        for(Object entry : world.getEntitiesWithinAABB(EntitySquirrel.class, area)) {
            EntitySquirrel other = (EntitySquirrel) entry;
            double dx = other.posX - candidate.posX, dz = other.posZ - candidate.posZ;
            if(other.isEntityAlive() && dx * dx + dz * dz <= POPULATION_RADIUS * POPULATION_RADIUS && ++count >= limit)
                return false;
        }
        return true;
    }

    static boolean isLog(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z).isWood(world, x, y, z);
    }

    static boolean loaded(World world, AxisAlignedBB box) {
        return box.minY >= 0D && box.maxY <= world.getActualHeight()
                && world.checkChunksExist(MathHelper.floor_double(box.minX), 0, MathHelper.floor_double(box.minZ),
                        MathHelper.floor_double(box.maxX), world.getActualHeight() - 1, MathHelper.floor_double(box.maxZ));
    }

    static boolean clear(EntitySquirrel squirrel, AxisAlignedBB box) {
        World world = squirrel.worldObj;
        if(!loaded(world, box) || !world.getCollidingBoundingBoxes(squirrel, box).isEmpty()) return false;
        // isAnyLiquid looks one column farther on negative coordinates.
        int minX = MathHelper.floor_double(box.minX) - (box.minX < 0D ? 1 : 0);
        int minZ = MathHelper.floor_double(box.minZ) - (box.minZ < 0D ? 1 : 0);
        return world.checkChunksExist(minX, 0, minZ, MathHelper.floor_double(box.maxX), 0, MathHelper.floor_double(box.maxZ))
                && !world.isAnyLiquid(box);
    }
}
