package com.hbm.world.squirrelstapler;

import com.hbm.items.ItemAmmoEnums.Ammo762NATO;
import com.hbm.items.ModItems;
import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapData;

import java.util.Random;

final class StaplerForest implements IWorldGenerator {
    static final int CABIN_X = 40, CABIN_Z = 8;
    static final int DISPLAY_X = 42, DISPLAY_Z = 3;
    static final int TALLY_X = 44;
    static final int BED_X = 38, BED_Z = 10;
    static final int SUPPLY_X = 44, SUPPLY_Z = 8;
    static final int LAMP_X = 40, LAMP_Z = 8;

    @Override public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider chunks) {
        if(!StaplerWorldType.isForest(world)) return;
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        if(x == CABIN_X && z == CABIN_Z) {
            StaplerCampaign campaign = StaplerCampaign.get(world);
            if(campaign.cabinY == 0) buildCabin(world, campaign);
            return;
        }
        if(Math.abs(chunkX) > 28 || Math.abs(chunkZ) > 28) return;
        makeTrail(world, chunkX << 4, chunkZ << 4, random);
        if(chunkX == 0 && chunkZ == 0) {
            int y = ground(world, 11, 10);
            if(y > 0) sign(world, 11, y, 10, false, 4, "HUNTER'S CABIN", "Follow trail", "east", "");
        } else if(Math.max(Math.abs(chunkX), Math.abs(chunkZ)) >= 4 && random.nextInt(40) == 0) {
            landmark(world, x, z, random.nextInt(3));
        }
    }

    static int ground(World world, int x, int z) {
        int y = world.getTopSolidOrLiquidBlock(x, z);
        if(y < 2 || y > 230) return -1;
        for(int step = 0; step < 12 && y > 1; step++, y--) {
            Block block = world.getBlock(x, y - 1, z);
            Material material = block.getMaterial();
            if(material.isLiquid() || world.getBlock(x, y, z).getMaterial().isLiquid()) return -1;
            if(block == Blocks.grass || block == Blocks.dirt || block == Blocks.sand
                    || block == Blocks.gravel || block == Blocks.stone) return y;
            if(material.blocksMovement() && !block.isWood(world, x, y - 1, z) && material != Material.leaves) return -1;
        }
        return -1;
    }

    private static void makeTrail(World world, int startX, int startZ, Random random) {
        for(int z = startZ; z < startZ + 16; z++) for(int x = startX; x < startX + 16; x++) {
            if(Math.abs(z - 8) > 1 && Math.abs(x - 8 - (int)(10D * Math.sin((z - 8) / 70D))) > 1) continue;
            int y = ground(world, x, z);
            if(y < 0 || world.getBlock(x, y, z).getMaterial().blocksMovement()) continue;
            world.setBlock(x, y - 1, z, random.nextInt(5) == 0 ? Blocks.cobblestone : Blocks.gravel, 0, 2);
            clear(world, x, y, z);
        }
    }

    private static void landmark(World world, int x, int z, int kind) {
        int y = ground(world, x, z);
        if(y < 0) return;
        for(int dz = -3; dz <= 3; dz++) for(int dx = -3; dx <= 3; dx++) {
            int floor = ground(world, x + dx, z + dz);
            if(floor < 0 || Math.abs(floor - y) > 3) return;
        }
        for(int dz = -3; dz <= 3; dz++) for(int dx = -3; dx <= 3; dx++) {
            int bx = x + dx, bz = z + dz;
            for(int by = y; by <= y + 5; by++) clear(world, bx, by, bz);
            for(int by = y - 1; by >= y - 4 && !world.getBlock(bx, by, bz).getMaterial().isSolid(); by--)
                world.setBlock(bx, by, bz, Blocks.cobblestone, 0, 2);
        }
        if(kind == 0) {
            for(int dx = -3; dx <= 3; dx++) for(int dz = -2; dz <= 2; dz++) {
                world.setBlock(x + dx, y - 1, z + dz, Blocks.planks, 1, 2);
                if(dx != 2 || dz != 0) world.setBlock(x + dx, y + 3, z + dz, Blocks.wooden_slab, 1, 2);
                if(Math.abs(dx) == 3 && Math.abs(dz) == 2)
                    for(int dy = 0; dy < 3; dy++) world.setBlock(x + dx, y + dy, z + dz, Blocks.log, 1, 2);
            }
            sign(world, x, y, z - 1, false, 0, "BROKEN BLIND", "We heard it", "walking upright.", "");
        } else {
            for(int d = -2; d <= 2; d += 4) {
                world.setBlock(x + d, y, z, Blocks.mossy_cobblestone, 0, 2);
                world.setBlock(x, y, z + d, Blocks.mossy_cobblestone, 0, 2);
            }
            for(int dy = 0; dy < (kind == 1 ? 5 : 2); dy++)
                world.setBlock(x, y + dy, z, kind == 1 ? Blocks.obsidian : Blocks.stonebrick, 0, 2);
            sign(world, x, y, z + 2, false, 0, kind == 1 ? "THE STILL POST" : "ECHO CAIRN", "Do not answer.", "", "");
        }
    }

    private static void buildCabin(World world, StaplerCampaign data) {
        int surface = ground(world, CABIN_X, CABIN_Z);
        int y = surface < 0 ? 66 : surface + 1;
        for(int z = 2; z <= 14; z++) for(int x = 33; x <= 47; x++) {
            for(int dy = 0; dy <= 10; dy++) clear(world, x, y + dy, z);
            if(x < 35 || x > 45 || z < 3 || z > 13) continue;
            for(int dy = 2; dy <= 10; dy++) {
                if(world.getBlock(x, y - dy, z).getMaterial().isSolid()) break;
                world.setBlock(x, y - dy, z, Blocks.cobblestone, 0, 2);
            }
            world.setBlock(x, y - 1, z, Blocks.planks, 1, 2);
            if(x != 35 && x != 45 && z != 3 && z != 13) continue;
            for(int dy = 0; dy <= 3; dy++) {
                if(x == 35 && z == 8 && dy <= 1) continue;
                boolean corner = (x == 35 || x == 45) && (z == 3 || z == 13);
                boolean window = dy == 1 && ((x == 40 && z == 13) || (x == 45 && z == 8));
                world.setBlock(x, y + dy, z, corner ? Blocks.log : window ? Blocks.glass : Blocks.planks, window ? 0 : 1, 2);
            }
        }
        for(int z = 2; z <= 14; z++) for(int x = 34; x <= 46; x++) {
            int roof = 4 + (6 - Math.abs(8 - z)) / 2;
            world.setBlock(x, y + roof, z, Blocks.planks, 1, 2);
            if(x == 35 || x == 45) for(int dy = 4; dy < roof; dy++) world.setBlock(x, y + dy, z, Blocks.planks, 1, 2);
        }
        for(int x = 32; x <= 34; x++) {
            int path = ground(world, x, 8);
            if(path > 0 && path <= y - 1) world.setBlock(x, path - 1, 8, Blocks.gravel, 0, 2);
        }
        world.setBlock(34, y - 2, 8, Blocks.cobblestone, 0, 2);
        world.setBlock(34, y - 1, 8, Blocks.wooden_slab, 1, 2);
        world.setBlock(35, y, 8, Blocks.wooden_door, 0, 2);
        world.setBlock(35, y + 1, 8, Blocks.wooden_door, 8, 2);
        world.setBlock(BED_X, y, BED_Z - 1, Blocks.bed, 0, 2);
        world.setBlock(BED_X, y, BED_Z, Blocks.bed, 8, 2);
        world.setBlock(DISPLAY_X, y + 1, DISPLAY_Z, Blocks.log, 1, 2);
        world.setBlock(41, y, 4, Blocks.bookshelf, 0, 2);
        world.setBlock(42, y, 4, Blocks.bookshelf, 0, 2);
        world.setBlock(43, y, 4, Blocks.bookshelf, 0, 2);
        world.setBlock(44, y, 5, Blocks.crafting_table, 0, 2);
        world.setBlock(44, y, 6, Blocks.furnace, 5, 2);
        world.setBlock(44, y, 10, Blocks.chest, 4, 2);
        world.setBlock(43, y, 12, Blocks.chest, 2, 2);
        world.setBlock(36, y + 1, 5, Blocks.torch, 1, 2);
        world.setBlock(36, y + 1, 11, Blocks.torch, 1, 2);
        world.setBlock(LAMP_X, y, LAMP_Z, Blocks.jukebox, 0, 2);
        world.setBlock(LAMP_X, y + 1, LAMP_Z, Blocks.redstone_torch, 5, 2);
        sign(world, 34, y + 1, 7, true, 4, "In 5 days,", "God is coming.", "", "");
        sign(world, 40, y + 2, 4, true, 3, "SNEAK + USE", "Stapler on log", "Bring trophies", "and staples.");
        sign(world, SUPPLY_X, y + 1, SUPPLY_Z, true, 4, "SUPPLIES", "Use to restock", "Ammo & staples", "");
        data.cabinY = y;
        data.clientStamp = -1L;
        updateDisplay(world, data);
        TileEntity chest = world.getTileEntity(44, y, 10);
        if(chest instanceof TileEntityChest) {
            TileEntityChest supplies = (TileEntityChest) chest;
            supplies.setInventorySlotContents(0, new ItemStack(StaplerMode.rifle));
            supplies.setInventorySlotContents(1, ammunition(64));
            supplies.setInventorySlotContents(2, new ItemStack(ModItems.stapler));
            supplies.setInventorySlotContents(3, new ItemStack(ModItems.staples, 64));
            supplies.setInventorySlotContents(4, new ItemStack(Items.bread, 24));
            supplies.setInventorySlotContents(5, newMap(world));
        }
        data.markDirty();
    }

    static void updateDisplay(World world, StaplerCampaign data) {
        if(!loaded(world, data)) return;
        sign(world, TALLY_X, data.cabinY + 2, DISPLAY_Z + 1, true, 3, "TROPHY RACK",
                "Day " + data.day + " tally", data.normal + "/" + data.normalRequired + " normal",
                data.large + "/" + data.largeRequired + " large");
    }

    static void lamp(World world, StaplerCampaign data, boolean dark) {
        if(!loaded(world, data) || world.getBlock(LAMP_X, data.cabinY, LAMP_Z) != Blocks.jukebox) return;
        int y = data.cabinY + 1;
        Block old = world.getBlock(LAMP_X, y, LAMP_Z);
        if(dark && old == Blocks.redstone_torch) clear(world, LAMP_X, y, LAMP_Z);
        else if(!dark && old == Blocks.air) world.setBlock(LAMP_X, y, LAMP_Z, Blocks.redstone_torch, 5, 2);
    }

    static boolean loaded(World world, StaplerCampaign data) {
        return data.cabinY > 0 && world.blockExists(CABIN_X, data.cabinY, CABIN_Z);
    }

    static ItemStack ammunition(int count) { return ModItems.ammo_762.stackFromEnum(count, Ammo762NATO.STOCK); }

    private static ItemStack newMap(World world) {
        int id = world.getUniqueDataId("map");
        ItemStack map = new ItemStack(Items.filled_map, 1, id);
        MapData data = new MapData("map_" + id);
        data.dimension = 0;
        data.xCenter = CABIN_X;
        data.zCenter = CABIN_Z;
        data.scale = 1;
        world.setItemData("map_" + id, data);
        data.markDirty();
        map.setStackDisplayName("Hunter's Survey");
        return map;
    }

    private static void sign(World world, int x, int y, int z, boolean wall, int direction, String... lines) {
        world.setBlock(x, y, z, wall ? Blocks.wall_sign : Blocks.standing_sign, direction, 2);
        TileEntity tile = world.getTileEntity(x, y, z);
        if(tile instanceof TileEntitySign) {
            System.arraycopy(lines, 0, ((TileEntitySign) tile).signText, 0, 4);
            tile.markDirty();
            world.markBlockForUpdate(x, y, z);
        }
    }

    private static void clear(World world, int x, int y, int z) {
        if(!world.isAirBlock(x, y, z)) world.setBlock(x, y, z, Blocks.air, 0, 2);
    }
}
