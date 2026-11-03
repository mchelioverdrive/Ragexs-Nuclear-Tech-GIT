package com.hbm.world.sandworm;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/** Empty, independently saved dimension; only encounter builders place terrain. */
public final class WormChunkProvider implements IChunkProvider {
    private final World world;
    public WormChunkProvider(World world) { this.world = world; }
    @Override public Chunk provideChunk(int x, int z) {
        Chunk chunk = new Chunk(world, new Block[65536], new byte[65536], x, z);
        java.util.Arrays.fill(chunk.getBiomeArray(), (byte)net.minecraft.world.biome.BiomeGenBase.plains.biomeID);
        chunk.generateSkylightMap(); return chunk;
    }
    @Override public Chunk loadChunk(int x, int z) { return provideChunk(x, z); }
    @Override public boolean chunkExists(int x, int z) { return true; }
    @Override public void populate(IChunkProvider provider, int x, int z) { }
    @Override public boolean saveChunks(boolean all, IProgressUpdate progress) { return true; }
    @Override public boolean unloadQueuedChunks() { return false; }
    @Override public boolean canSave() { return true; }
    @Override public String makeString() { return "RNT Sandworm interiors"; }
    @Override public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) { return Collections.emptyList(); }
    @Override public ChunkPosition func_147416_a(World w, String name, int x, int y, int z) { return null; }
    @Override public int getLoadedChunkCount() { return 0; }
    @Override public void recreateStructures(int x, int z) { }
    @Override public void saveExtraData() { }
}
