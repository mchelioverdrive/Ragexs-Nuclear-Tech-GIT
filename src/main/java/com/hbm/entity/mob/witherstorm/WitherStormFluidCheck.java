package com.hbm.entity.mob.witherstorm;

import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Exact material presence query that skips empty 16-block sections of the enormous Stage D box. */
final class WitherStormFluidCheck {
    private WitherStormFluidCheck() {}

    static boolean contains(World world, AxisAlignedBB box, Material material, boolean requireLoaded) {
        int x0 = MathHelper.floor_double(box.minX), x1 = MathHelper.floor_double(box.maxX + 1D);
        int y0 = MathHelper.floor_double(box.minY), y1 = MathHelper.floor_double(box.maxY + 1D);
        int z0 = MathHelper.floor_double(box.minZ), z1 = MathHelper.floor_double(box.maxZ + 1D);
        // Match World.handleMaterialAcceleration's loaded-area guard, including its upper edge.
        if(requireLoaded && !world.checkChunksExist(x0, y0, z0, x1, y1, z1)) return false;
        x0 = Math.max(-30000000, x0); x1 = Math.min(30000000, x1);
        z0 = Math.max(-30000000, z0); z1 = Math.min(30000000, z1);
        y0 = Math.max(0, y0); y1 = Math.min(256, y1);
        if(x0 >= x1 || y0 >= y1 || z0 >= z1) return false;
        for(int cx = x0 >> 4; cx <= (x1 - 1) >> 4; cx++) for(int cz = z0 >> 4; cz <= (z1 - 1) >> 4; cz++) {
            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            int minX = Math.max(x0, cx << 4), maxX = Math.min(x1, (cx + 1) << 4);
            int minZ = Math.max(z0, cz << 4), maxZ = Math.min(z1, (cz + 1) << 4);
            for(int sy = y0 >> 4; sy <= (y1 - 1) >> 4; sy++) {
                ExtendedBlockStorage section = sections[sy];
                if(section == null || section.isEmpty()) continue;
                int minY = Math.max(y0, sy << 4), maxY = Math.min(y1, (sy + 1) << 4);
                for(int x = minX; x < maxX; x++) for(int y = minY; y < maxY; y++) for(int z = minZ; z < maxZ; z++)
                    if(chunk.getBlock(x & 15, y, z & 15).getMaterial() == material) return true;
            }
        }
        return false;
    }
}
