package com.hbm.dim.sirocco;

import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.SandwormConfig;
import com.hbm.config.SpaceConfig;
import com.hbm.items.ModItems;
import com.hbm.world.generator.DungeonToolbox;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public final class WorldGeneratorSirocco implements IWorldGenerator {
    @Override public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider provider) {
        if(world.provider.dimensionId != SpaceConfig.siroccoDimension || !SandwormConfig.surface.structures
                || !world.getWorldInfo().isMapFeaturesEnabled() || !world.getChunkProvider().chunkExists(chunkX, chunkZ)) return;
        int kind = random.nextInt(512);
        if(kind > 5) return;
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        int low = 256, high = 0;
        for(int dx = -4; dx <= 4; dx++) for(int dz = -4; dz <= 4; dz++) {
            int y = world.getHeightValue(x + dx, z + dz);
            low = Math.min(low, y);
            high = Math.max(high, y);
        }
        if(low < 16 || high > 240 || high - low > 3) return;
        if(kind >= 4) {
            for(int dx = -4; dx <= 4; dx++) {
                int center = (dx * dx) / 8 - 1;
                for(int dz = center - 1; dz <= center + 1; dz++) {
                    int y = world.getHeightValue(x + dx, z + dz);
                    if(world.getBlock(x + dx, y - 1, z + dz) != ModBlocks.duna_sands) continue;
                    world.setBlock(x + dx, y - 2, z + dz, ModBlocks.ferric_clay, 0, 2);
                    world.setBlock(x + dx, y - 1, z + dz, Blocks.air, 0, 2);
                }
            }
            return;
        }

        int floor = high - 1;
        Block shell = kind == 0 ? ModBlocks.concrete_colored : kind == 1 ? ModBlocks.deco_steel
                : kind == 2 ? ModBlocks.ducrete : ModBlocks.ferric_clay;
        DungeonToolbox.generateBox(world, x - 3, low - 1, z - 3, 7, floor - low + 2, 7, shell);
        DungeonToolbox.generateBox(world, x - 3, floor + 1, z - 3, 7, 3, 1, shell);
        DungeonToolbox.generateBox(world, x - 3, floor + 1, z - 2, 1, 2, 5, shell);
        DungeonToolbox.generateBox(world, x - 3, floor + 4, z - 3, 3, 1, 3, shell);
        world.setBlock(x + 2, floor + 1, z - 2, ModBlocks.deco_pipe_rusted, 0, 2);
        world.setBlock(x - 2, floor + 1, z - 2, Blocks.chest, 3, 2);
        TileEntity chest = world.getTileEntity(x - 2, floor + 1, z - 2);
        if(chest instanceof TileEntityChest) {
            TileEntityChest cache = (TileEntityChest)chest;
            cache.setInventorySlotContents(4, new ItemStack(ModItems.ingot_saturnite, 1 + random.nextInt(3)));
            cache.setInventorySlotContents(13, new ItemStack(ModItems.biomass_compressed, 2 + random.nextInt(3)));
            if(random.nextInt(3) == 0) cache.setInventorySlotContents(22, new ItemStack(ModItems.circuit_targeting_tier6));
        }
        world.setBlock(x, floor + 1, z + 2, Blocks.standing_sign, 0, 2);
        TileEntity sign = world.getTileEntity(x, floor + 1, z + 2);
        if(sign instanceof TileEntitySign) {
            TileEntitySign warning = (TileEntitySign)sign;
            warning.signText[0] = kind == 0 ? "MINING OUTPOST" : kind == 1 ? "SURVEY WRECK"
                    : kind == 2 ? "RESEARCH RUIN" : "EXCAVATION";
            warning.signText[1] = "TREMORS BELOW";
            warning.signText[2] = "LEAVE THE SAND";
            warning.signText[3] = "IF IT ERUPTS";
            warning.markDirty();
        }
    }
}
