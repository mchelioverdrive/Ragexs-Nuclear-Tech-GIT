package com.hbm.world.gen;

import java.util.Random;
import com.hbm.blocks.ModBlocks;
import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

public final class FakeCommandBlockGenerator implements IWorldGenerator {
    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider provider) {
        if(world.isRemote || world.provider.dimensionId != 0 || random.nextInt(32) != 0) return;
        BiomeGenBase spikes = BiomeGenBase.getBiome(BiomeGenBase.icePlains.biomeID + 128);
        int bestX = 0, bestY = 63, bestZ = 0;
        for(int i = 0; i < 24; i++) {
            // Stay off chunk borders and only replace an exposed, elevated spike tip.
            int x = (chunkX << 4) + 1 + random.nextInt(14);
            int z = (chunkZ << 4) + 1 + random.nextInt(14);
            if(world.getBiomeGenForCoords(x, z) != spikes) continue;
            int y = world.getTopSolidOrLiquidBlock(x, z) - 1;
            if(y <= bestY || world.getBlock(x, y, z) != Blocks.packed_ice || !world.isAirBlock(x, y + 1, z)
                || world.getTileEntity(x, y, z) != null) continue;
            bestX = x; bestY = y; bestZ = z;
        }
        if(bestY > 63) world.setBlock(bestX, bestY, bestZ, ModBlocks.fake_command_block, 0, 2);
    }
}
