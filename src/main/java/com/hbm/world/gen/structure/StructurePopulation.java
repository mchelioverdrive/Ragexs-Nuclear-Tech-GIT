package com.hbm.world.gen.structure;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

/** A start must leave every one of its vanilla population windows available. */
final class StructurePopulation {
    private StructurePopulation() { }

    static boolean untouched(World world, int chunkX, int chunkZ, int currentX, int currentZ) {
        IChunkProvider provider = world.getChunkProvider();
        if(!(provider instanceof ChunkProviderServer)) return false;
        ChunkProviderServer server = (ChunkProviderServer)provider;
        if(!(server.currentChunkLoader instanceof AnvilChunkLoader)) return false;
        AnvilChunkLoader loader = (AnvilChunkLoader)server.currentChunkLoader;
        for(int x = chunkX - 2; x <= chunkX + 1; x++) {
            for(int z = chunkZ - 2; z <= chunkZ + 1; z++) {
                if(x == currentX && z == currentZ) continue;
                if(server.chunkExists(x, z)) {
                    if(server.provideChunk(x, z).isTerrainPopulated) return false;
                } else if(loader.chunkExists(world, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
