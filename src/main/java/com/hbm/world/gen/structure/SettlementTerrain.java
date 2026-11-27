package com.hbm.world.gen.structure;

import com.hbm.world.gen.structure.SettlementData.Slice;
import com.hbm.world.gen.structure.SettlementPlan.Column;
import com.hbm.world.gen.structure.SettlementPlan.State;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Computes the earthwork and authored layers before any placement begins. */
final class SettlementTerrain {
    private static final State FILL = new State(Blocks.dirt, 0);

    private SettlementTerrain() { }

    static int surface(World world, Chunk chunk, int x, int z) {
        if(chunk == null) return -1;
        for(int y = top(world, chunk); y >= 0; y--) {
            Block block = chunk.getBlock(x & 15, y, z & 15);
            if(block != Blocks.air && !vegetation(world, block, x, y, z)) return y;
        }
        return -1;
    }

    static String survey(World world, Chunk chunk, Slice slice, Column column, int depth, Earthwork result) {
        boolean forced = !slice.site.natural;
        int x = column.x & 15, z = column.z & 15;
        int floor = column.y + column.bottom;
        int top = column.y + column.top;
        if(!inBounds(world, column)) return "world limits";

        int ground = -1;
        if(forced) {
            top = forcedCeiling(world, chunk, column, top);
            ground = top;
        } else {
            for(int y = top(world, chunk); y >= 0; y--) {
                Block block = chunk.getBlock(x, y, z);
                if(block == Blocks.air) continue;
                top = Math.max(y, top);
                if(!vegetation(world, block, column.x, y, column.z)) {
                    ground = y;
                    break;
                }
            }
        }

        int support = floor - 1;
        while(support > 0) {
            Block block = chunk.getBlock(x, support, z);
            if(block.getMaterial().blocksMovement() && !vegetation(world, block, column.x, support, column.z)) break;
            support--;
        }
        int bottom = support + 1;
        if(!forced && (ground < 1 || Math.abs(ground - column.y) > depth || floor - bottom > depth))
            return "natural cut or fill exceeds " + depth + " blocks (surface=" + ground + ", support=" + support + ")";

        int address = SettlementData.columnIndex(x, z);
        for(int y = bottom; y <= top; y++) {
            Block existing = chunk.getBlock(x, y, z);
            int meta = chunk.getBlockMetadata(x, y, z);
            if(!forced && !replaceable(world, existing, column.x, y, column.z))
                return "protected natural obstruction " + Block.blockRegistry.getNameForObject(existing) + " at Y=" + y;
            State desired = y < floor ? FILL : column.at(y - column.y);
            boolean different = existing != desired.block || meta != desired.meta;
            if(forced) slice.add(address, y, Blocks.air, 0, desired, null);
            else if(different || desired.sign != null) slice.add(address, y, existing, meta, desired, null);
            if(different) {
                if(y < floor) result.fill++;
                else if(y > column.y && y <= ground && earth(existing)) result.cut++;
                else if(desired.block == Blocks.air) result.cleared++;
            }
        }
        if(column.livestock != null) slice.add(address, column.y + 1, Blocks.air, 0, SettlementPlan.AIR, column.livestock);
        return null;
    }

    static boolean inBounds(World world, Column column) {
        return column.x >= -30000000 && column.x < 30000000 && column.z >= -30000000 && column.z < 30000000
                && column.y + column.bottom >= 1 && column.y + column.top < world.getActualHeight();
    }

    private static int top(World world, Chunk chunk) {
        return Math.min(world.getActualHeight() - 1, chunk.getTopFilledSegment() + 15);
    }

    private static int forcedCeiling(World world, Chunk chunk, Column column, int ceiling) {
        int x = column.x & 15, z = column.z & 15;
        int highest = top(world, chunk);
        boolean aboveCut = false;
        for(int y = ceiling + 1; y <= highest; y++) {
            Block block = chunk.getBlock(x, y, z);
            if(block == Blocks.air) {
                aboveCut = true;
                continue;
            }
            if(!vegetation(world, block, column.x, y, column.z) && (aboveCut || !earth(block))) break;
            ceiling = y;
        }
        return ceiling;
    }

    private static boolean earth(Block block) {
        Material material = block.getMaterial();
        return material == Material.rock || material == Material.ground || material == Material.grass
                || material == Material.sand || material == Material.ice || material == Material.packedIce
                || material == Material.craftedSnow || material.isLiquid();
    }

    private static boolean soil(Block block) {
        return block == Blocks.grass || block == Blocks.dirt || block == Blocks.stone || block == Blocks.sand
                || block == Blocks.gravel || block == Blocks.clay || block == Blocks.sandstone || block == Blocks.mycelium
                || block == Blocks.hardened_clay || block == Blocks.stained_hardened_clay || block == Blocks.snow
                || block == Blocks.coal_ore || block == Blocks.iron_ore || block == Blocks.gold_ore || block == Blocks.redstone_ore
                || block == Blocks.lit_redstone_ore || block == Blocks.diamond_ore || block == Blocks.lapis_ore || block == Blocks.emerald_ore;
    }

    private static boolean vegetation(World world, Block block, int x, int y, int z) {
        Material material = block.getMaterial();
        return material == Material.plants || material == Material.vine || block == Blocks.snow_layer
                || block == Blocks.cactus || block == Blocks.reeds || block.isWood(world, x, y, z) || block.isLeaves(world, x, y, z);
    }

    private static boolean replaceable(World world, Block block, int x, int y, int z) {
        return block == Blocks.air || soil(block) || block.getMaterial() == Material.water || vegetation(world, block, x, y, z);
    }

    static boolean naturallyChanged(World world, Block before, Block current, int x, int y, int z) {
        if(before == null) return false;
        if((before == Blocks.grass || before == Blocks.dirt) && (current == Blocks.grass || current == Blocks.dirt)) return true;
        if(before == Blocks.air) return vegetation(world, current, x, y, z);
        if(current == Blocks.air) return vegetation(world, before, x, y, z);
        return vegetation(world, before, x, y, z) && vegetation(world, current, x, y, z);
    }

    static final class Earthwork {
        int cut, fill, cleared;

        @Override
        public String toString() { return "cut=" + cut + " fill=" + fill + " cleared=" + cleared; }
    }
}
