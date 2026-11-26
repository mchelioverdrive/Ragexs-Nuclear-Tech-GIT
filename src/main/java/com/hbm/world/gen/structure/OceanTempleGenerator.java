package com.hbm.world.gen.structure;

import java.lang.ref.WeakReference;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.ColossusConfig;
import com.hbm.config.StructureConfig;
import com.hbm.entity.mob.colossus.ColossusContent;
import com.hbm.world.progression.BossProgression;
import com.hbm.world.progression.BossProgression.Boss;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.BiomeDictionary;

/** Overworld temples placed in vanilla's overlapping 16-by-16 population windows. */
public final class OceanTempleGenerator implements IWorldGenerator {
    private static final String ID = "RTMOceanTemples";
    private static final int EXTENT = 31, ARENA = 22, FLOOR = 8, ROOF = 59, WATER_SURFACE = 62;
    private static final int FIRST_SLICE = -2, LAST_SLICE = 1, ALL_SLICES = 0xFFFF;
    private static final long SALT = 0x68e0b9c8be129d12L;
    private WeakReference<World> world = new WeakReference<World>(null);
    private TempleMap map;

    public static void register() {
        MapGenStructureIO.registerStructure(Start.class, "RTMOceanTemple");
        MapGenStructureIO.func_143031_a(Piece.class, "RTMOceanTemplePiece");
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World current, IChunkProvider terrain, IChunkProvider chunks) {
        if(current.isRemote || current.provider.dimensionId != 0 || com.hbm.world.squirrelstapler.StaplerWorldType.suppressStructures(current)) return;
        int x = center(chunkX), z = center(chunkZ);
        if(!current.checkChunksExist(x, FLOOR, z, x + 15, WATER_SURFACE, z + 15)) return;
        boolean newStarts = ColossusConfig.generateOceanTemples && (StructureConfig.enableStructures == 1
            || StructureConfig.enableStructures == 2 && current.getWorldInfo().isMapFeaturesEnabled());
        if(world.get() != current) {
            world = new WeakReference<World>(current);
            map = new TempleMap();
        }
        map.populate(current, chunks, random, chunkX, chunkZ, newStarts);
    }

    public static boolean isCompleteAt(World world, int x, int y, int z) {
        if(world.provider.dimensionId != 0 || y != FLOOR || (x & 15) != 8 || (z & 15) != 8) return false;
        MapGenStructureData data = (MapGenStructureData) world.perWorldStorage.loadData(MapGenStructureData.class, ID);
        if(data == null) return false;
        NBTTagCompound start = data.func_143041_a().getCompoundTag(MapGenStructureData.func_143042_b(x >> 4, z >> 4));
        return start.getTagList("Children", 10).getCompoundTagAt(0).getInteger("Slices") == ALL_SLICES
            && world.checkChunksExist(x - ARENA, FLOOR, z - ARENA, x + ARENA, ROOF, z + ARENA);
    }

    /** Recorded sites only. Inspecting the structure data never asks a chunk provider for terrain. */
    public static int[] nearestRecorded(World overworld, double x, double z) {
        NBTTagCompound records = records(overworld);
        int[] nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for(Object key : records.func_150296_c()) {
            NBTTagCompound start = records.getCompoundTag((String)key);
            int cx = center(start.getInteger("ChunkX")), cz = center(start.getInteger("ChunkZ"));
            boolean complete = start.getTagList("Children", 10).getCompoundTagAt(0).getInteger("Slices") == ALL_SLICES;
            double distance = (cx - x) * (cx - x) + (cz - z) * (cz - z);
            if(nearest == null || complete && nearest[3] == 0 || complete == (nearest[3] == 1) && distance < best) {
                nearest = new int[] {cx, FLOOR, cz, complete ? 1 : 0};
                best = distance;
            }
        }
        return nearest;
    }

    public static int[] surveyCandidate(World world, int x, int z) {
        if(!ColossusConfig.generateOceanTemples || com.hbm.world.squirrelstapler.StaplerWorldType.suppressStructures(world)
            || StructureConfig.enableStructures == 0
            || StructureConfig.enableStructures == 2 && !world.getWorldInfo().isMapFeaturesEnabled()) return null;
        int size = cellSize();
        int cellX = Math.floorDiv(x >> 4, size), cellZ = Math.floorDiv(z >> 4, size);
        int[] nearest = null;
        long best = Long.MAX_VALUE;
        NBTTagCompound records = records(world);
        for(int dz = -5; dz <= 5; dz++) {
            for(int dx = -5; dx <= 5; dx++) {
                int[] chunk = candidate(world, cellX + dx, cellZ + dz);
                if(chunk == null) continue;
                int cx = center(chunk[0]), cz = center(chunk[1]);
                long offX = (long)cx - x, offZ = (long)cz - z, distance = offX * offX + offZ * offZ;
                if(distance >= best || Math.abs(cx) >= 29999968 || Math.abs(cz) >= 29999968 || !deepOcean(world, cx, cz)
                    || intersectsGiantArena(world, cx, cz)
                    || !StructurePopulation.untouched(world, chunk[0], chunk[1], Integer.MIN_VALUE, Integer.MIN_VALUE)) continue;
                boolean separated = true;
                long minimum = ColossusConfig.oceanTempleMinSeparation;
                for(Object key : records.func_150296_c()) {
                    NBTTagCompound start = records.getCompoundTag((String)key);
                    long sx = (long)cx - center(start.getInteger("ChunkX")), sz = (long)cz - center(start.getInteger("ChunkZ"));
                    if(sx * sx + sz * sz < minimum * minimum) {
                        separated = false;
                        break;
                    }
                }
                if(!separated) continue;
                nearest = new int[] {cx, FLOOR, cz};
                best = distance;
            }
        }
        return nearest;
    }

    private static NBTTagCompound records(World world) {
        MapGenStructureData data = (MapGenStructureData)world.perWorldStorage.loadData(MapGenStructureData.class, ID);
        return data == null ? new NBTTagCompound() : data.func_143041_a();
    }

    static boolean intersectsArena(World world, int centerX, int centerZ) {
        NBTTagCompound starts = records(world);
        for(int x = (centerX >> 4) - 3; x <= (centerX >> 4) + 3; x++) {
            for(int z = (centerZ >> 4) - 3; z <= (centerZ >> 4) + 3; z++) {
                if(starts.hasKey(MapGenStructureData.func_143042_b(x, z), 10)) return true;
            }
        }
        return false;
    }

    private static boolean intersectsGiantArena(World world, int x, int z) {
        BossProgression progress = BossProgression.get(world);
        for(int dx = -48; dx <= 48; dx += 16) {
            for(int dz = -48; dz <= 48; dz += 16) {
                if(progress.arenaAt(Boss.GIANT_MAGMA_GOLEM, x + dx, z + dz) != null
                    || progress.arenaAt(Boss.GIANT_ENDERMAN, x + dx, z + dz) != null) return true;
            }
        }
        return false;
    }

    private static int gap() { return (ColossusConfig.oceanTempleMinSeparation + 15) / 16; }
    private static int jitter() { return Math.min(32, gap() / 2); }
    private static int cellSize() { return gap() + jitter(); }

    private static int[] candidate(World world, int cellX, int cellZ) {
        Random choice = new Random(world.getSeed() ^ (cellX * 341873128712L + cellZ * 132897987541L + SALT));
        int x = cellX * cellSize() + choice.nextInt(jitter() + 1);
        int z = cellZ * cellSize() + choice.nextInt(jitter() + 1);
        return choice.nextInt(ColossusConfig.oceanTempleRarity) == 0 ? new int[] {x, z} : null;
    }

    private static boolean deepOcean(World world, int x, int z) {
        for(int dx = -EXTENT; dx <= EXTENT; dx += EXTENT) {
            for(int dz = -EXTENT; dz <= EXTENT; dz += EXTENT) {
                BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x + dx, z + dz);
                if(!BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.OCEAN) || biome.rootHeight > -0.5F) return false;
            }
        }
        return true;
    }

    private static int center(int chunk) {
        return (chunk << 4) + 8;
    }

    private static final class TempleMap extends MapGenStructure {
        private int populationX, populationZ;

        TempleMap() {
            range = 2;
        }

        void populate(World world, IChunkProvider chunks, Random random, int chunkX, int chunkZ, boolean newStarts) {
            populationX = chunkX;
            populationZ = chunkZ;
            try {
                if(newStarts) func_151539_a(chunks, world, chunkX, chunkZ, null);
                // Finish placing saved starts even when generation of new temples is disabled.
                generateStructuresInChunk(world, random, chunkX, chunkZ);
            } finally {
                worldObj = null;
            }
        }

        @Override public String func_143025_a() { return ID; }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            if(populationX < chunkX + FIRST_SLICE || populationX > chunkX + LAST_SLICE
                || populationZ < chunkZ + FIRST_SLICE || populationZ > chunkZ + LAST_SLICE) return false;
            int[] candidate = candidate(worldObj, Math.floorDiv(chunkX, cellSize()), Math.floorDiv(chunkZ, cellSize()));
            if(candidate == null || chunkX != candidate[0] || chunkZ != candidate[1]) return false;

            int x = center(chunkX), z = center(chunkZ);
            if(Math.abs(x) >= 29999968 || Math.abs(z) >= 29999968
                || !deepOcean(worldObj, x, z) || intersectsGiantArena(worldObj, x, z)) return false;
            // A grid change must respect already reserved sites as well as the new grid.
            long separation = ColossusConfig.oceanTempleMinSeparation;
            for(Object value : structureMap.values()) {
                StructureStart start = (StructureStart) value;
                long dx = x - (long)center(start.func_143019_e()), dz = z - (long)center(start.func_143018_f());
                if(dx * dx + dz * dz < separation * separation) return false;
            }
            return StructurePopulation.untouched(worldObj, chunkX, chunkZ, populationX, populationZ);
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            return new Start(chunkX, chunkZ);
        }
    }

    public static final class Start extends StructureStart {
        public Start() {}

        @SuppressWarnings("unchecked")
        Start(int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            components.add(new Piece(center(chunkX), center(chunkZ)));
            updateBoundingBox();
        }
    }

    public static final class Piece extends StructureComponent {
        private int slices;

        public Piece() {}

        Piece(int x, int z) {
            boundingBox = new StructureBoundingBox(x - EXTENT, FLOOR, z - EXTENT, x + EXTENT, WATER_SURFACE, z + EXTENT);
        }

        @Override protected void func_143012_a(NBTTagCompound nbt) { nbt.setInteger("Slices", slices); }
        @Override protected void func_143011_b(NBTTagCompound nbt) { slices = nbt.getInteger("Slices"); }

        @Override
        public boolean addComponentParts(World world, Random random, StructureBoundingBox window) {
            int centerX = boundingBox.minX + EXTENT, centerZ = boundingBox.minZ + EXTENT;
            int column = ((window.minX - centerX) >> 4) - FIRST_SLICE;
            int row = ((window.minZ - centerZ) >> 4) - FIRST_SLICE;
            int bit = 1 << (row * 4 + column);
            if((slices & bit) != 0) return true;

            for(int x = Math.max(window.minX, boundingBox.minX); x <= Math.min(window.maxX, boundingBox.maxX); x++) {
                for(int z = Math.max(window.minZ, boundingBox.minZ); z <= Math.min(window.maxZ, boundingBox.maxZ); z++) {
                    for(int y = FLOOR; y <= WATER_SURFACE; y++) {
                        Block block = layout(x - centerX, y, z - centerZ);
                        if(block != null) world.setBlock(x, y, z, block, 0, 2);
                    }
                }
            }
            if(window.isVecInside(centerX - 27, FLOOR + 1, centerZ)) placeTreasure(world, centerX - 27, centerZ, true);
            if(window.isVecInside(centerX + 27, FLOOR + 1, centerZ)) placeTreasure(world, centerX + 27, centerZ, false);
            slices |= bit;
            return true;
        }

        private static Block layout(int x, int y, int z) {
            int u = Math.abs(x), v = Math.abs(z);
            if(u <= ARENA && v <= ARENA) return arena(u, y, v);
            if(v > ARENA && u <= 5) return entrance(u, y, v);
            if(u > ARENA && v <= 8 && y <= FLOOR + 9) {
                if(y == FLOOR || y == FLOOR + 9 || u == EXTENT || v == 8)
                    return (u == 27 && v == 4 && y == FLOOR + 9) ? ColossusContent.seaLantern : ColossusContent.prismarine;
                if(u == 27 && v == 0 && y == FLOOR + 1) return null;
                return Blocks.water;
            }
            return null;
        }

        private static Block arena(int u, int y, int v) {
            if(y == FLOOR) {
                if(u == 0 && v == 0) return ColossusContent.templeCore;
                if(u % 7 == 0 && v % 7 == 0) return ColossusContent.seaLantern;
                if(Math.abs(u * u + v * v - 144) < 16 || u == v) return ModBlocks.gneiss_tile;
                return ColossusContent.prismarine;
            }
            if(y == ROOF) return u % 7 == 0 && v % 7 == 0 ? ColossusContent.seaLantern : ColossusContent.prismarine;
            if(y > ROOF) return y <= ROOF + 2 && Math.max(u, v) == ARENA + ROOF - y + 1 ? ModBlocks.gneiss_chiseled : null;

            if(u == ARENA || v == ARENA) {
                if(y <= FLOOR + 5 && (u == ARENA && v <= 2 || v == ARENA && u <= 3)) return Blocks.water;
                if(y % 8 == 0 && Math.min(u, v) % 5 == 0) return ColossusContent.seaLantern;
                return ColossusContent.prismarine;
            }
            if(u >= 18 && u <= 20 && v >= 18 && v <= 20) {
                if(y % 8 == 0) return ColossusContent.seaLantern;
                if(y % 8 == 1 || y % 8 == 7) return ModBlocks.gneiss_chiseled;
                return ColossusContent.prismarine;
            }
            return Blocks.water;
        }

        private static Block entrance(int u, int y, int v) {
            boolean shaft = u <= 3 && v >= 25 && v <= 31;
            boolean rim = u == 3 || v == 25 || v == 31;
            if(y > FLOOR + 8) {
                if(!shaft) return null;
                if(!rim) return Blocks.water;
                return y % 8 == 0 ? ColossusContent.seaLantern : ColossusContent.prismarine;
            }
            if(y == FLOOR || u == 5 || v == EXTENT || (y == FLOOR + 8 && (!shaft || rim)))
                return y == FLOOR + 8 && u == 0 && v == 24 ? ColossusContent.seaLantern : ColossusContent.prismarine;
            return Blocks.water;
        }

        private static void placeTreasure(World world, int x, int z, boolean gauntlet) {
            int y = FLOOR + 1;
            if(!world.setBlock(x, y, z, Blocks.chest, gauntlet ? 5 : 4, 2)) return;
            TileEntity tile = world.getTileEntity(x, y, z);
            if(!(tile instanceof TileEntityChest)) return;
            TileEntityChest chest = (TileEntityChest) tile;
            Random loot = new Random(world.getSeed() ^ ((long)x * 341873128712L + (long)z * 132897987541L));
            chest.setInventorySlotContents(4, gauntlet ? new ItemStack(ColossusContent.gauntlet) : new ItemStack(Items.diamond, 2 + loot.nextInt(3)));
            chest.setInventorySlotContents(13, new ItemStack(Items.gold_ingot, 4 + loot.nextInt(5)));
            chest.setInventorySlotContents(22, new ItemStack(Items.emerald, 2 + loot.nextInt(4)));
        }
    }
}
