package com.hbm.world.squirrelstapler;

import com.hbm.config.SquirrelStaplerConfig;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.NoiseGeneratorPerlin;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

import java.util.Random;

public final class StaplerWorldType extends WorldType {
    public static final String NAME = "squirrelstapler";

    public StaplerWorldType() { super(NAME); }

    public static boolean isCampaign(World world) {
        return world.getWorldInfo().getTerrainType() instanceof StaplerWorldType;
    }

    public static boolean isForest(World world) { return world.provider.dimensionId == 0 && isCampaign(world); }
    public static boolean active(World world) { return isForest(world) && SquirrelStaplerConfig.settings.enabled; }
    public static boolean suppressStructures(World world) { return active(world) && SquirrelStaplerConfig.settings.suppressStructures; }
    public static boolean suppressGemini(World world) {
        return world != null && isCampaign(world) && SquirrelStaplerConfig.settings.enabled && SquirrelStaplerConfig.settings.suppressGemini;
    }

    @Override public WorldChunkManager getChunkManager(World world) {
        return new WorldChunkManager(world) {
            @Override public GenLayer[] getModdedBiomeGenerators(WorldType type, long seed, GenLayer[] original) {
                GenLayer terrain = new ForestLayer(seed, 4);
                GenLayer surface = new ForestLayer(seed, 1);
                return super.getModdedBiomeGenerators(type, seed, new GenLayer[] {terrain, surface, surface});
            }
        };
    }

    @Override public IChunkProvider getChunkGenerator(World world, String options) {
        return new ChunkProviderGenerate(world, world.getSeed(), false);
    }

    @Override public int getSpawnFuzz() { return 1; }

    private static final class ForestLayer extends GenLayer {
        private final NoiseGeneratorPerlin hills, glades, water;
        private final int scale;

        ForestLayer(long seed, int scale) {
            super(seed);
            Random random = new Random(seed);
            hills = new NoiseGeneratorPerlin(random, 2);
            glades = new NoiseGeneratorPerlin(random, 1);
            water = new NoiseGeneratorPerlin(random, 1);
            this.scale = scale;
        }

        @Override public int[] getInts(int x, int z, int width, int depth) {
            int[] biomes = IntCache.getIntCache(width * depth);
            for(int dz = 0; dz < depth; dz++) for(int dx = 0; dx < width; dx++) {
                biomes[dx + dz * width] = biome((x + dx) * scale, (z + dz) * scale).biomeID;
            }
            return biomes;
        }

        private BiomeGenBase biome(int x, int z) {
            if(Math.abs(x - 24) <= 35 && Math.abs(z - 8) <= 23) return BiomeGenBase.plains;
            double elevation = hills.func_151601_a(x / 138D, z / 138D);
            double basin = water.func_151601_a(x / 112D, z / 112D);
            if(basin < -0.53D) return BiomeGenBase.river;
            if(Math.abs(water.func_151601_a(x / 200D + 13D, z / 200D - 9D)) < 0.058D) return BiomeGenBase.river;
            if(elevation > 0.42D) return BiomeGenBase.forestHills;
            if(glades.func_151601_a(x / 94D, z / 94D) > 0.36D) return BiomeGenBase.plains;
            return elevation < -0.22D ? BiomeGenBase.roofedForest : BiomeGenBase.forest;
        }
    }
}
