package com.hbm.world.gen.structure;

import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.BossProgressionConfig;
import com.hbm.config.StructureConfig;
import com.hbm.world.progression.BossArenaBlock;
import com.hbm.world.progression.BossProgression;
import com.hbm.world.progression.BossProgression.Arena;
import com.hbm.world.progression.BossProgression.Boss;
import com.hbm.world.squirrelstapler.StaplerWorldType;
import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

/** Each arena is built in sixteen non-overlapping vanilla population windows. */
public final class BossArenaGenerator implements IWorldGenerator {
    public static final int CORE_OFFSET = 22;
    private static final int EXTENT = 32, SHAFT_X = 7, SHAFT_Z = 27;
    private static final int FIRST_SLICE = -2, LAST_SLICE = 1;
    private static final int MAP_LIMIT = 29999960;
    private static final long SALT = 0x424F53534152454EL;

    public static boolean generatesIn(World world) {
        BossProgressionConfig.Values config = BossProgressionConfig.settings;
        return config.enabled && config.enableStructures && !StaplerWorldType.suppressStructures(world)
            && (StructureConfig.enableStructures == 1
                || StructureConfig.enableStructures == 2 && world.getWorldInfo().isMapFeaturesEnabled());
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider terrain, IChunkProvider chunks) {
        int dimension = world.provider.dimensionId;
        if(world.isRemote || dimension != 0 && dimension != -1 || StaplerWorldType.suppressStructures(world)) return;
        int x = center(chunkX), z = center(chunkZ);
        if(Math.abs(x) > MAP_LIMIT || Math.abs(z) > MAP_LIMIT
            || !world.checkChunksExist(x, 1, z, x + 15, 100, z + 15)) return;

        BossProgression data = BossProgression.get(world);
        Arena arena = recordedWindow(data, dimension, chunkX, chunkZ);
        if(arena == null && generatesIn(world)) {
            int size = cellSize();
            Arena candidate = candidate(world.getSeed(), dimension, Math.floorDiv(chunkX, size), Math.floorDiv(chunkZ, size));
            int cx = candidate.x >> 4, cz = candidate.z >> 4;
            if(inWindow(chunkX - cx) && inWindow(chunkZ - cz) && candidate.boss.isEnabled()
                && world.getActualHeight() > roof(candidate.boss) + 4 && data.hasSeparation(candidate.boss, candidate.x, candidate.z)
                && (dimension != 0 || !OceanTempleGenerator.intersectsArena(world, candidate.x, candidate.z))
                && StructurePopulation.untouched(world, cx, cz, chunkX, chunkZ)) {
                arena = data.reserve(candidate.boss, candidate.x, candidate.z);
            }
        }
        if(arena == null) return;
        int column = chunkX - (arena.x >> 4) - FIRST_SLICE;
        int row = chunkZ - (arena.z >> 4) - FIRST_SLICE;
        int bit = 1 << (row * 4 + column);
        if(arena.hasSlice(bit)) return;
        placeWindow(world, arena, x, z);
        data.recordSlice(arena, bit);
    }

    private static Arena recordedWindow(BossProgression data, int dimension, int chunkX, int chunkZ) {
        for(int dz = FIRST_SLICE; dz <= LAST_SLICE; dz++) {
            for(int dx = FIRST_SLICE; dx <= LAST_SLICE; dx++) {
                int x = center(chunkX - dx), z = center(chunkZ - dz);
                if(dimension == -1) {
                    Arena arena = data.arenaAt(Boss.GIANT_GHAST, x, z);
                    if(arena != null) return arena;
                } else {
                    Arena arena = data.arenaAt(Boss.GIANT_MAGMA_GOLEM, x, z);
                    if(arena == null) arena = data.arenaAt(Boss.GIANT_ENDERMAN, x, z);
                    if(arena != null) return arena;
                }
            }
        }
        return null;
    }

    public static int[] surveyCandidate(World seedWorld, World target, Boss boss, int x, int z) {
        if(!generatesIn(seedWorld) || !boss.isEnabled()) return null;
        if(target != null && target.getActualHeight() <= roof(boss) + 4) return null;
        int size = cellSize();
        int cellX = Math.floorDiv(x >> 4, size), cellZ = Math.floorDiv(z >> 4, size);
        long nearest = Long.MAX_VALUE;
        int[] point = null;
        BossProgression data = BossProgression.get(seedWorld);
        for(int dz = -4; dz <= 4; dz++) {
            for(int dx = -4; dx <= 4; dx++) {
                Arena candidate = candidate(seedWorld.getSeed(), boss.dimension, cellX + dx, cellZ + dz);
                if(candidate.boss != boss || Math.abs(candidate.x) > MAP_LIMIT || Math.abs(candidate.z) > MAP_LIMIT) continue;
                long offsetX = (long)candidate.x - x, offsetZ = (long)candidate.z - z;
                long distance = offsetX * offsetX + offsetZ * offsetZ;
                if(distance >= nearest || !data.hasSeparation(boss, candidate.x, candidate.z)) continue;
                if(boss.dimension == 0 && OceanTempleGenerator.intersectsArena(seedWorld, candidate.x, candidate.z)) continue;
                if(target != null && !StructurePopulation.untouched(target, candidate.x >> 4, candidate.z >> 4,
                    Integer.MIN_VALUE, Integer.MIN_VALUE)) continue;
                nearest = distance;
                point = new int[] {candidate.x, candidate.z};
            }
        }
        return point;
    }

    private static Arena candidate(long seed, int dimension, int cellX, int cellZ) {
        Random random = new Random(seed ^ (cellX * 341873128712L + cellZ * 132897987541L + dimension * 78168472331L + SALT));
        int jitter = jitter();
        int size = cellSize();
        int x = center(cellX * size + 2 + random.nextInt(jitter + 1));
        int z = center(cellZ * size + 2 + random.nextInt(jitter + 1));
        Boss boss = dimension == -1 ? Boss.GIANT_GHAST : ((cellX + cellZ) & 1) == 0 ? Boss.GIANT_MAGMA_GOLEM : Boss.GIANT_ENDERMAN;
        return new Arena(boss, x, z);
    }

    private static int center(int chunk) { return (chunk << 4) + 8; }
    private static boolean inWindow(int offset) { return offset >= FIRST_SLICE && offset <= LAST_SLICE; }
    private static int gap() { return (BossProgressionConfig.settings.minimumSeparation + 15) / 16; }
    private static int jitter() { return Math.min(32, gap() / 2); }
    private static int cellSize() { return gap() + jitter() + 4; }

    public static int roof(Boss boss) {
        switch(boss) {
            case GIANT_GHAST: return 94;
            case GIANT_MAGMA_GOLEM: return 46;
            default: return 144;
        }
    }

    public static boolean isLoaded(World world, Arena arena) {
        return world.checkChunksExist(arena.x - EXTENT, 1, arena.z - EXTENT,
            arena.x + EXTENT - 1, roof(arena.boss), arena.z + EXTENT - 1);
    }

    private static void placeWindow(World world, Arena arena, int windowX, int windowZ) {
        Boss boss = arena.boss;
        int floor = boss.floor;
        int bottom = boss == Boss.GIANT_ENDERMAN ? 54 : floor - 2;
        int top = boss == Boss.GIANT_GHAST ? 107 : roof(boss);
        boolean hasShaft = windowX == arena.x && windowZ == arena.z + 16;
        int shaftBottom = boss == Boss.GIANT_ENDERMAN ? bottom + 1 : floor + 1;
        int shaftTop = top;
        if(hasShaft && boss == Boss.GIANT_MAGMA_GOLEM) {
            int surface = 52;
            for(int dx = -2; dx <= 2; dx++) {
                for(int dz = -2; dz <= 2; dz++) {
                    surface = Math.max(surface, world.getTopSolidOrLiquidBlock(arena.x + SHAFT_X + dx, arena.z + SHAFT_Z + dz));
                }
            }
            shaftTop = Math.min(surface + 2, world.getActualHeight() - 3);
        } else if(boss == Boss.GIANT_ENDERMAN) shaftTop = floor + 3;

        for(int x = windowX; x < windowX + 16; x++) {
            for(int z = windowZ; z < windowZ + 16; z++) {
                int dx = x - arena.x, dz = z - arena.z;
                boolean shaft = Math.abs(dx - SHAFT_X) <= 2 && Math.abs(dz - SHAFT_Z) <= 2;
                int columnTop = shaft ? Math.max(top, shaftTop) : top;
                for(int y = bottom; y <= columnTop; y++) {
                    Block block;
                    int metadata = 0;
                    if(dx == 0 && dz == CORE_OFFSET && y == floor) {
                        block = BossArenaBlock.core;
                    } else if(shaft && y >= shaftBottom && y <= shaftTop) {
                        block = towerShaft(boss, dx - SHAFT_X, y, dz - SHAFT_Z, shaftBottom, shaftTop);
                        if(block == Blocks.ladder) metadata = 3;
                    } else {
                        switch(boss) {
                            case GIANT_GHAST: block = fortress(dx, y, dz, floor); break;
                            case GIANT_MAGMA_GOLEM: block = crucible(dx, y, dz, floor); break;
                            default: block = ruin(dx, y, dz, floor); break;
                        }
                        if(block == Blocks.ladder) metadata = dx > 0 ? 4 : 5;
                        if(block == Blocks.stonebrick && (dx * 31 + dz * 17 + y & 3) == 0) metadata = 2;
                    }
                    if(block != null) world.setBlock(x, y, z, block, metadata, 2);
                }
            }
        }
    }

    private static Block towerShaft(Boss boss, int dx, int y, int dz, int bottom, int top) {
        if(Math.abs(dx) == 2 || Math.abs(dz) == 2) {
            if(dx == -2 && dz == 0 && (y <= bottom + 2 || y >= top - 1
                || boss == Boss.GIANT_ENDERMAN && y > boss.floor)) return Blocks.air;
            if(y % 8 == 0 && dx == 0 && dz == 2) return Blocks.glowstone;
            if(boss == Boss.GIANT_GHAST) return Blocks.nether_brick;
            return boss == Boss.GIANT_MAGMA_GOLEM ? ModBlocks.basalt_brick : Blocks.stonebrick;
        }
        return dx == 0 && dz == -1 ? Blocks.ladder : Blocks.air;
    }

    private static Block fortress(int x, int y, int z, int floor) {
        int u = Math.abs(x), v = Math.abs(z), edge = Math.max(u, v);
        if(edge > 30 || y > roof(Boss.GIANT_GHAST)) return null;
        if(y <= floor) return y == floor && u % 8 == 0 && v % 8 == 0 ? Blocks.glowstone : Blocks.nether_brick;
        if(y == roof(Boss.GIANT_GHAST)) return Blocks.nether_brick;
        if(edge >= 29) {
            if(v >= 29 && u <= 2 && y <= floor + 4) return Blocks.air;
            return y % 8 == 0 && Math.min(u, v) % 5 == 0 ? Blocks.glowstone : Blocks.nether_brick;
        }
        if(u == 28 && v == 16 && y <= floor + 22) return Blocks.ladder;
        if(u >= 25 && y == floor + 18) return v % 8 == 0 ? Blocks.glowstone : Blocks.nether_brick;
        if(u >= 27 && u != 28 && y == floor + 22) return Blocks.nether_brick;
        if(u == 25 && (v == 24 || v == 8) && y < floor + 18) return Blocks.nether_brick;
        return Blocks.air;
    }

    private static Block crucible(int x, int y, int z, int floor) {
        int u = Math.abs(x), v = Math.abs(z), edge = Math.max(u, v);
        if(edge > 29 || y > roof(Boss.GIANT_MAGMA_GOLEM)) return null;
        boolean bridge = u <= 3 || v <= 3 || edge >= 24 || u <= 19 && v <= 19;
        if(y == floor - 2) return ModBlocks.brick_obsidian;
        if(y == floor - 1) return bridge ? ModBlocks.brick_obsidian : Blocks.lava;
        if(y == floor) {
            if((u == 26 && v == 7) || (u == 26 && v == 8)) return Blocks.water;
            return !bridge ? Blocks.air : u % 7 == 0 && v % 7 == 0 ? Blocks.glowstone : ModBlocks.basalt_tiles;
        }
        if(edge == 29 || y == roof(Boss.GIANT_MAGMA_GOLEM)) {
            return y % 7 == 0 && Math.min(u, v) % 6 == 0 ? Blocks.glowstone : ModBlocks.basalt_brick;
        }
        return Blocks.air;
    }

    private static Block ruin(int x, int y, int z, int floor) {
        int u = Math.abs(x), v = Math.abs(z), edge = Math.max(u, v);
        if(edge > 30) return null;
        boolean tower = u >= 25 && v >= 25;
        if(y < floor - 2) return tower || edge >= 28 && Math.min(u, v) % 7 <= 1 ? Blocks.stonebrick : null;
        if(y <= floor) {
            if(y == floor && Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z)) < 2) return Blocks.air;
            return y == floor && u % 8 == 0 && v % 8 == 0 ? Blocks.glowstone : Blocks.stonebrick;
        }
        int spireHeight = x * z > 0 ? 48 : 35;
        if(tower && y <= floor + spireHeight - (u + v & 3)) {
            if(y % 9 == 0 && (u == 25 || v == 25)) return Blocks.glowstone;
            return Blocks.stonebrick;
        }
        if(edge == 30 && y <= floor + 2 && Math.min(u, v) % 6 < 3) return Blocks.stonebrick;
        return Blocks.air;
    }
}
