package com.hbm.world.gen.structure;

import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementPlan.Plot;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Furnished, north-facing lots. The road joins each lot at local (0, 0). */
final class SettlementBuildings {
    private static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    private static final int WHITE = 0, YELLOW = 4, LIGHT_GRAY = 8, BLUE = 11, RED = 14;

    private SettlementBuildings() { }

    static void build(SettlementPlan plan, SettlementKind kind, int variant, int x, int y, int z, Rotation rotation, long seed) {
        Plot plot = new Plot(plan, kind.halfWidth(variant), kind.depth(variant), x, y, z, rotation);
        Random random = new Random(seed);
        switch(kind) {
        case RESTAURANT: restaurant(plot, variant); break;
        case STORE: store(plot, variant, random); break;
        case DATA_CENTER: dataCenter(plot, variant); break;
        case FARM: farm(plot, variant, random); break;
        case HOUSE: house(plot, variant, random); break;
        default: throw new IllegalArgumentException("Expected a building lot");
        }
    }

    private static void restaurant(Plot p, int variant) {
        int front = 11, rear = variant == 2 ? 26 : 29, side = variant == 1 ? 12 : 11;
        parking(p, 17, 9, 2);
        p.fill(-17, 0, 10, -side - 1, 0, 33, ModBlocks.asphalt);
        p.fill(side + 1, 0, 10, 17, 0, 33, ModBlocks.asphalt);
        p.fill(-17, 0, 31, 17, 0, 33, ModBlocks.asphalt);
        p.fill(-side, 0, 10, side, 0, 10, ModBlocks.concrete_smooth);
        p.fill(-1, 0, 6, 1, 0, 10, ModBlocks.concrete_smooth);
        shell(p, -side, front, side, rear, 1, 6, ModBlocks.concrete_colored, variant == 0 ? WHITE : LIGHT_GRAY,
                Blocks.quartz_block, 0, ModBlocks.concrete_smooth, 0);
        p.fill(-side, 5, front, side, 5, front, ModBlocks.concrete_colored, RED);
        p.fill(-side, 6, front, side, 6, front, ModBlocks.concrete_colored, YELLOW);
        p.fill(-side, 2, front + 1, -side, 3, rear - 6, Blocks.glass);
        p.fill(-side + 2, 2, front, -2, 3, front, Blocks.glass);
        p.fill(2, 2, front, side - 2, 3, front, Blocks.glass);
        doubleDoor(p, -1, front, false);
        p.sign(-3, 4, front - 1, NORTH, "McDonald's", "Restaurant");
        p.fill(-4, 7, front, 4, 11, front, ModBlocks.concrete_colored, RED);
        goldenArches(p, front - 1);
        if(variant == 0) {
            p.fill(-side, 7, front + 1, -side, 7, rear, ModBlocks.concrete_colored, RED);
            p.fill(side, 7, front + 1, side, 7, rear, ModBlocks.concrete_colored, RED);
        } else if(variant == 2) {
            p.fill(-side, 7, front + 1, side, 7, front + 6, ModBlocks.concrete_colored, RED);
            p.fill(side - 2, 4, 14, 17, 4, 16, ModBlocks.concrete_colored, YELLOW);
            p.fill(16, 1, 14, 16, 3, 14, ModBlocks.concrete_colored, RED);
        }
        for(int z = 14; z <= 19; z += 4) for(int x = -side + 3; x <= -3; x += 4) table(p, x, 1, z, Blocks.quartz_stairs, Blocks.quartz_block);
        for(int x = 3; x < side - 1; x += 4) table(p, x, 1, 14, Blocks.quartz_stairs, Blocks.quartz_block);
        p.fill(2, 1, 18, side - 2, 1, 18, ModBlocks.concrete_colored, RED);
        p.fill(2, 2, 18, side - 2, 2, 18, Blocks.quartz_block);
        p.cardinal(3, 3, 18, ModBlocks.deco_crt, NORTH);
        p.sign(side + 1, 3, 16, EAST, "DRIVE-THRU", "Pick up here");
        p.fill(side, 2, 19, side, 2, 20, Blocks.glass_pane);
        p.sign(side + 1, 3, 19, EAST, "Pick-up window");
        p.fill(1, 1, 23, side - 1, 1, 23, ModBlocks.block_steel);
        p.facing(3, 2, 23, Blocks.furnace, NORTH);
        p.facing(4, 2, 23, Blocks.furnace, NORTH);
        p.block(6, 2, 23, ModBlocks.deco_toaster);
        p.block(8, 2, 23, Blocks.cauldron, 3);
        p.fill(1, 1, 24, side - 1, 4, 24, ModBlocks.concrete_colored, LIGHT_GRAY);
        p.fill(2, 1, 24, 2, 2, 24, Blocks.air);
        p.door(2, 1, 24, false, NORTH, false);
        p.sign(4, 3, 23, NORTH, "Cold storage");
        p.fill(5, 1, rear - 1, side - 2, 1, rear - 1, ModBlocks.crate_steel);
        restroom(p, -side + 1, 21, -6, rear - 1, 1);
        restroom(p, -5, 21, -1, rear - 1, 1);
        p.fill(-side + 1, 1, rear + 1, -side + 5, 2, rear + 1, Blocks.stonebrick);
        p.block(-side + 2, 1, rear + 2, ModBlocks.crate_metal);
        p.block(-side + 3, 1, rear + 2, ModBlocks.barrel_iron);
        p.sign(-side + 4, 2, rear + 2, SOUTH, "Waste disposal");
        lights(p, -side + 2, front + 2, side - 2, rear - 1, 6, 5);
    }

    private static void goldenArches(Plot p, int z) {
        String[] rows = {".###.###.", "##.###.##", "##..#..##", "##.....##", "##.....##"};
        for(int row = 0; row < rows.length; row++) for(int col = 0; col < rows[row].length(); col++)
            if(rows[row].charAt(col) == '#') p.block(col - 4, 11 - row, z, ModBlocks.concrete_colored, YELLOW);
    }

    private static void store(Plot p, int variant, Random random) {
        int front = 20, rear = 44, end = 22;
        parking(p, 26, 17, 3);
        p.fill(-26, 0, 18, 26, 0, 50, ModBlocks.asphalt);
        p.fill(-22, 0, 18, 22, 0, 19, ModBlocks.concrete_smooth);
        p.fill(-2, 0, 14, 2, 0, 19, ModBlocks.concrete_smooth);
        shell(p, -end, front, end, rear, 1, 9, ModBlocks.concrete_colored, LIGHT_GRAY,
                ModBlocks.concrete_smooth, 0, ModBlocks.concrete, 0);
        p.fill(-end, 5, front, end, 7, front, ModBlocks.concrete_colored, BLUE);
        p.fill(-end, 2, front, end, 3, front, Blocks.glass);
        doubleDoor(p, -1, front, false);
        doubleDoor(p, 3, front, false);
        p.fill(-7, 7, front - 1, 7, 7, front - 1, ModBlocks.concrete_colored, BLUE);
        p.sign(-1, 6, front - 1, NORTH, "WALMART", variant == 0 ? "Neighborhood" : variant == 1 ? "Supercenter" : "Retail & Supply");
        for(int[] spark : new int[][] {{8, 6}, {8, 8}, {7, 6}, {9, 8}, {7, 8}, {9, 6}})
            p.block(spark[0], spark[1], front - 1, ModBlocks.concrete_colored, YELLOW);
        for(int x = -18; x <= 18; x += 4) {
            if(x == 2) continue;
            p.fill(x, 1, 22, x, 1, 25, ModBlocks.concrete_colored, BLUE);
            p.fill(x, 2, 23, x, 2, 25, Blocks.stone_slab, 7);
            p.cardinal(x, 2, 22, ModBlocks.deco_crt, NORTH);
        }
        int[] departments = variant == 0 ? new int[] {0, 1, 2} : variant == 1 ? new int[] {1, 2, 0} : new int[] {2, 0, 1};
        for(int section = 0; section < departments.length; section++) {
            int left = -20 + section * 14;
            int department = departments[section];
            String name = department == 0 ? "Grocery" : department == 1 ? "Electronics" : "Clothing";
            p.block(left + 1, 4, 27, ModBlocks.concrete_colored, BLUE);
            p.sign(left + 1, 4, 26, NORTH, name);
            for(int x = left + 2; x <= left + 10; x += 4) {
                int start = 28 + random.nextInt(2), finish = variant == 2 ? 36 : 35;
                if(department == 0) {
                    p.fill(x, 1, start, x, 1, finish, ModBlocks.crate);
                    for(int z = start; z <= finish; z++)
                        p.block(x, 2, z, z % 3 == 0 ? Blocks.melon_block : z % 3 == 1 ? Blocks.pumpkin : Blocks.hay_block, z % 3 == 1 ? 2 : 0);
                } else if(department == 1) {
                    p.fill(x, 1, start, x, 1, finish, ModBlocks.steel_grate);
                    for(int z = start; z <= finish; z += 2) p.cardinal(x, 2, z, ModBlocks.deco_crt, EAST);
                } else {
                    p.fill(x, 1, start, x, 1, finish, Blocks.planks);
                    for(int z = start; z <= finish; z++) p.block(x, 2, z, Blocks.wool, (z + x) & 15);
                }
            }
        }
        p.fill(-21, 1, 38, 21, 6, 38, ModBlocks.concrete_colored, LIGHT_GRAY);
        p.door(-14, 1, 38, false, NORTH, false);
        p.door(4, 1, 38, false, NORTH, false);
        p.sign(-12, 3, 37, NORTH, "Employees only");
        p.sign(6, 3, 37, NORTH, "Warehouse");
        p.fill(-8, 1, 39, -8, 5, 43, ModBlocks.concrete_colored, LIGHT_GRAY);
        desk(p, -18, 1, 42);
        desk(p, -12, 1, 42);
        p.facing(-20, 1, 41, Blocks.furnace, NORTH);
        p.block(-19, 1, 41, Blocks.cauldron, 3);
        for(int x = -5; x <= 17; x += 5) p.fill(x, 1, 40, x, 3, 42, ModBlocks.crate_steel);
        loadingDock(p, rear, 6);
        loadingDock(p, rear, 15);
        vents(p, -17, 10, 25, 5);
        lights(p, -19, 23, 19, 43, 9, 7);
    }

    private static void dataCenter(Plot p, int variant) {
        int left = -p.halfWidth + 3, right = p.halfWidth - 3, front = 4, rear = p.depth - 6;
        int roof = 7 + variant, divider = front + 7, utility = rear - 7;
        p.fill(-3, 0, 0, 3, 0, front - 1, ModBlocks.asphalt);
        p.fill(3, 0, 1, p.halfWidth, 0, 3, ModBlocks.asphalt);
        p.fill(right + 1, 0, 1, p.halfWidth, 0, p.depth - 2, ModBlocks.asphalt);
        p.fill(2, 0, rear + 1, p.halfWidth, 0, p.depth - 2, ModBlocks.asphalt);
        shell(p, left, front, right, rear, 1, roof, ModBlocks.concrete_colored, LIGHT_GRAY,
                ModBlocks.concrete_smooth, 0, ModBlocks.concrete, 0);
        p.fill(left, roof - 1, front, right, roof - 1, front, ModBlocks.concrete_colored, BLUE);
        doubleDoor(p, -1, front, true);
        p.fill(-2, 1, front + 1, -2, 4, front + 2, ModBlocks.concrete);
        p.fill(1, 1, front + 1, 1, 4, front + 2, ModBlocks.concrete);
        p.fill(-2, 1, front + 2, 1, 4, front + 2, ModBlocks.concrete);
        doubleDoor(p, -1, front + 2, true);
        p.button(-2, 1, front - 1, NORTH);
        p.button(-2, 1, front + 1, SOUTH);
        p.button(1, 1, front + 1, NORTH);
        p.button(1, 1, front + 3, SOUTH);
        p.sign(2, 3, front - 1, NORTH, "DATA CENTER", new String[] {"Edge Facility", "Regional Hub", "Campus"}[variant]);
        p.fill(left + 2, 2, front, left + 5, 3, front, ModBlocks.reinforced_glass);
        p.fill(right - 5, 2, front, right - 2, 3, front, ModBlocks.reinforced_glass);
        p.fill(-2, 1, front + 2, -2, 4, rear - 1, ModBlocks.concrete);
        p.fill(2, 1, front + 2, 2, 4, rear - 1, ModBlocks.concrete);
        p.fill(left + 1, 1, divider, -3, 4, divider, ModBlocks.concrete);
        p.fill(3, 1, divider, right - 1, 4, divider, ModBlocks.concrete);
        p.fill(3, 1, utility, right - 1, 4, utility, ModBlocks.concrete);
        p.door(-2, 1, front + 4, false, EAST, false);
        p.door(2, 1, front + 4, false, WEST, false);
        p.sign(-1, 3, front + 5, EAST, "Security");
        p.sign(1, 3, front + 5, WEST, "Employee office");
        desk(p, -6, 1, front + 3);
        desk(p, 6, 1, front + 3);
        p.block(left + 1, 1, front + 5, ModBlocks.crate_steel);
        p.door(-2, 1, divider + 1, true, EAST, false);
        p.block(-1, 1, divider + 1, Blocks.stone_pressure_plate);
        p.block(-3, 1, divider + 1, Blocks.stone_pressure_plate);
        p.sign(-1, 3, divider + 2, EAST, "Server hall");
        for(int x = left + 2; x <= -5; x += 3) {
            for(int z = divider + 3; z <= rear - 3; z += 2) rack(p, x, z);
            for(int z = divider + 3; z <= rear - 3; z++) p.pipe(x, 5, z, ModBlocks.deco_pipe, SOUTH);
            p.fill(x, 4, divider + 3, x, 4, rear - 3, ModBlocks.steel_grate);
        }
        p.door(2, 1, divider + 2, false, WEST, false);
        p.sign(1, 3, divider + 3, WEST, "Distribution");
        p.fill(4, 1, divider + 2, right - 2, 1, divider + 2, ModBlocks.red_cable);
        p.block(4, 1, divider + 3, ModBlocks.machine_transformer);
        p.facing(5, 1, divider + 3, ModBlocks.machine_diesel, SOUTH);
        p.facing(6, 1, divider + 3, ModBlocks.machine_diesel, SOUTH);
        p.sign(right - 2, 3, divider - 1, NORTH, "Backup power");
        p.door(2, 1, utility + 1, false, WEST, false);
        p.sign(1, 3, utility + 2, WEST, "Battery storage");
        for(int x = 4; x < right - 1; x += 2) p.facing(x, 1, utility + 2, ModBlocks.machine_battery, SOUTH);
        p.fill(4, 1, utility + 3, right - 2, 1, utility + 3, ModBlocks.red_cable);
        loadingDock(p, rear, right - 4);
        vents(p, left + 2, roof + 1, divider, variant + 2);
        for(int z = divider + 3; z < rear - 1; z += 5) p.block(left + 1, 3, z, ModBlocks.air_vent);
        lights(p, left + 2, front + 2, right - 2, rear - 2, roof, 5);
    }

    private static void rack(Plot p, int x, int z) {
        p.fillFacing(x, 1, z, x, 3, z, ModBlocks.steel_wall, EAST);
        p.fill(x + 1, 1, z, x + 1, 3, z, ModBlocks.steel_grate);
        p.cardinal(x + 1, 2, z, ModBlocks.deco_crt, EAST);
        p.block(x, 3, z, Blocks.redstone_lamp);
    }

    private static void house(Plot p, int variant, Random random) {
        boolean attached = variant == 3, detached = variant == 4, twoStories = variant == 1;
        int left = detached ? -10 : -8, right = attached ? 3 : detached ? 1 : 8;
        int front = 7, rear = variant == 0 ? 17 : 19, roof = twoStories ? 9 : 5;
        int door = attached || detached ? -2 : 0, siding = random.nextInt(3);
        Block wall = variant == 2 ? Blocks.brick_block : siding == 0 ? Blocks.planks : ModBlocks.concrete_colored;
        int wallMeta = variant == 2 ? 0 : siding == 0 ? 2 + random.nextInt(2) : siding == 1 ? WHITE : LIGHT_GRAY;
        p.fill(-1, 0, 0, 1, 0, 5, ModBlocks.concrete_smooth);
        p.fill(Math.min(-1, door - 1), 0, 5, 1, 0, 6, ModBlocks.concrete_smooth);
        shell(p, left, front, right, rear, 1, roof, wall, wallMeta, Blocks.planks, random.nextInt(3), Blocks.planks, 1);
        p.door(door, 1, front, false, NORTH, false);
        p.fill(left + 2, 2, front, door - 2, 3, front, Blocks.glass_pane);
        p.fill(door + 2, 2, front, right - 1, 3, front, Blocks.glass_pane);
        p.fill(left, 2, front + 2, left, 3, front + 4, Blocks.glass_pane);
        p.fill(left + 2, 2, rear, left + 4, 3, rear, Blocks.glass_pane);
        gable(p, left - 1, front - 1, right + 1, rear + 1, roof, variant == 2 ? Blocks.brick_stairs : Blocks.spruce_stairs, wall, wallMeta);
        p.door(door, 1, rear, false, SOUTH, false);
        p.fill(door - 1, 0, rear + 1, door + 1, 0, rear + 2, Blocks.stonebrick);

        int kitchen = random.nextBoolean() && right >= 7 && !twoStories ? right - 1 : left + 1;
        p.facing(kitchen, 1, front + 3, Blocks.furnace, kitchen < 0 ? EAST : WEST);
        p.block(kitchen, 1, front + 4, Blocks.cauldron, 3);
        p.block(kitchen, 1, front + 5, Blocks.crafting_table);
        p.block(kitchen, 2, front + 5, ModBlocks.deco_toaster);
        p.stairs(door + 2, 1, front + 3, Blocks.oak_stairs, SOUTH);
        p.stairs(door + 3, 1, front + 3, Blocks.oak_stairs, SOUTH);
        p.block(door + 2, 1, front + 4, Blocks.carpet, 9);
        p.block(door + 3, 1, front + 4, Blocks.carpet, 9);
        p.cardinal(door + 3, 1, front + 5, ModBlocks.deco_crt, NORTH);
        p.fill(left + 1, 1, front + 6, right - 1, 3, front + 6, wall, wallMeta);
        p.door(door, 1, front + 6, false, NORTH, false);
        p.bed(left + 2, 1, rear - 2, SOUTH);
        p.facing(left + 4, 1, rear - 1, Blocks.chest, NORTH);
        restroom(p, right - 4, rear - 4, right - 1, rear - 1, 1);
        if(twoStories) {
            p.fill(left + 1, 4, front + 1, right - 1, 4, rear - 1, Blocks.planks, 1);
            p.fill(right - 1, 4, front + 1, right - 1, 4, front + 6, Blocks.air);
            p.fill(right - 1, 1, front + 6, right - 1, 3, front + 6, Blocks.air);
            for(int i = 0; i < 4; i++) p.stairs(right - 1, i + 1, front + 2 + i, Blocks.spruce_stairs, SOUTH);
            p.fill(right - 2, 5, front + 1, right - 2, 5, front + 6, Blocks.fence);
            p.bed(left + 3, 5, front + 3, NORTH);
            p.bed(left + 3, 5, rear - 2, SOUTH);
            p.facing(left + 5, 5, rear - 1, Blocks.chest, NORTH);
            p.fill(left + 1, 5, front + 6, right - 3, 7, front + 6, wall, wallMeta);
            p.door(-1, 5, front + 6, false, NORTH, false);
            p.fill(left + 2, 6, front, 1, 7, front, Blocks.glass_pane);
            p.fill(left + 2, 6, rear, 1, 7, rear, Blocks.glass_pane);
            lights(p, left + 2, front + 2, right - 2, rear - 2, roof, 5);
        } else {
            lights(p, left + 2, front + 2, right - 2, rear - 2, roof, 5);
        }
        if(attached || detached) garage(p, attached ? 3 : 5, attached ? 10 : 12, attached ? front : 11, wall, wallMeta, attached);
        if(variant == 5) basement(p, left, right, front, rear);

        p.fill(-p.halfWidth + 1, 1, rear + 2, -p.halfWidth + 1, 1, 24, Blocks.fence);
        p.fill(p.halfWidth - 1, 1, rear + 2, p.halfWidth - 1, 1, 24, Blocks.fence);
        p.fill(-p.halfWidth + 1, 1, 24, p.halfWidth - 1, 1, 24, Blocks.fence);
        p.block(0, 1, 24, Blocks.fence_gate);
        tree(p, -p.halfWidth + 3, rear + 3, 0);
        p.fill(-7, 0, 3, -4, 0, 4, Blocks.dirt, 2);
        for(int x = -7; x <= -4; x++) p.block(x, 1, 3 + random.nextInt(2), Blocks.red_flower, random.nextInt(9));
    }

    private static void garage(Plot p, int x1, int x2, int front, Block wall, int meta, boolean attached) {
        p.fill(-2, 0, 1, x2 - 1, 0, 4, ModBlocks.concrete_smooth);
        p.fill(x1 + 1, 0, 4, x2 - 1, 0, front - 1, ModBlocks.concrete_smooth);
        shell(p, x1, front, x2, front + 7, 1, 5, wall, meta, ModBlocks.concrete, 0, Blocks.planks, 1);
        p.fill(x1 + 1, 1, front, x2 - 1, 3, front, Blocks.air);
        p.fillFacing(x1 + 1, 4, front, x2 - 1, 4, front, ModBlocks.steel_wall, NORTH);
        p.fill(x1 + 2, 1, front + 6, x2 - 1, 1, front + 6, ModBlocks.crate_steel);
        p.block(x2 - 1, 1, front + 5, Blocks.crafting_table);
        if(attached) p.door(x1, 1, front + 4, false, WEST, false);
    }

    private static void basement(Plot p, int left, int right, int front, int rear) {
        p.fill(left + 1, -4, front + 1, right - 1, -4, rear - 1, Blocks.stonebrick);
        p.fill(left + 1, -3, front + 1, left + 1, -1, rear - 1, Blocks.stonebrick);
        p.fill(right - 1, -3, front + 1, right - 1, -1, rear - 1, Blocks.stonebrick);
        p.fill(left + 1, -3, front + 1, right - 1, -1, front + 1, Blocks.stonebrick);
        p.fill(left + 1, -3, rear - 1, right - 1, -1, rear - 1, Blocks.stonebrick);
        p.fill(right - 1, 0, rear - 4, right - 1, 1, rear - 4, Blocks.stonebrick);
        for(int y = -3; y <= 1; y++) p.facing(right - 2, y, rear - 4, Blocks.ladder, WEST);
        p.fill(left + 3, -3, rear - 2, left + 6, -3, rear - 2, ModBlocks.crate_steel);
        p.fill(-2, 0, front + 3, 2, 0, front + 3, Blocks.glowstone);
    }

    private static void farm(Plot p, int variant, Random random) {
        boolean industrial = variant == 3;
        int end = p.depth - 3, edge = p.halfWidth - 1;
        p.fill(-1, 0, 0, 1, 0, end, Blocks.dirt, 1);
        p.fill(2, 0, 5, 10, 0, 6, Blocks.dirt, 1);
        p.fill(2, 0, 17, 9, 0, 18, Blocks.dirt, 1);
        shell(p, 3, 7, 12, 15, 1, 5, Blocks.planks, 0, Blocks.planks, 0, Blocks.planks, 1);
        p.door(6, 1, 7, false, NORTH, false);
        p.fill(8, 2, 7, 10, 3, 7, Blocks.glass_pane);
        p.facing(4, 1, 10, Blocks.furnace, EAST);
        p.block(4, 1, 11, Blocks.crafting_table);
        p.bed(10, 1, 13, SOUTH);
        p.facing(8, 1, 14, Blocks.chest, NORTH);
        gable(p, 2, 6, 13, 16, 5, Blocks.spruce_stairs, Blocks.planks, 0);
        int barnRear = industrial ? 30 : 28;
        shell(p, 3, 19, 13, barnRear, 1, 5, ModBlocks.concrete_colored, RED,
                Blocks.planks, 1, Blocks.planks, 1);
        p.fill(6, 1, 19, 9, 3, 19, Blocks.air);
        p.fill(5, 1, barnRear - 2, 11, 2, barnRear - 1, variant == 2 ? Blocks.hay_block : ModBlocks.crate);
        p.sign(5, 4, 18, NORTH, "Barn");
        gable(p, 2, 18, 14, barnRear + 1, 5, Blocks.spruce_stairs, ModBlocks.concrete_colored, RED);
        shell(p, 14, 4, p.halfWidth, 10, 1, 4, Blocks.planks, 1, Blocks.cobblestone, 0, ModBlocks.concrete, 0);
        p.fill(14, 1, 5, 14, 2, 7, Blocks.air);
        p.block(15, 1, 8, ModBlocks.crate_steel);
        p.block(15, 1, 9, Blocks.crafting_table);
        silo(p, 15, 15);
        if(industrial) {
            silo(p, 19, 15);
            fields(p, 4, 33, edge, end - 1, variant, random);
            fields(p, 16, 20, edge, 29, variant, random);
        }
        int fieldFront = 4 + random.nextInt(3);
        if(variant == 2) {
            pen(p, -edge, fieldFront, -3, 16, "Cow");
            pen(p, -edge, 19, -3, end - 1, random.nextBoolean() ? "Sheep" : "Pig");
        } else if(variant == 4) {
            boundary(p, -edge, fieldFront, -3, end - 1);
            for(int x = -edge + 3; x <= -5; x += 5) for(int z = fieldFront + 3; z <= end - 3; z += 5)
                tree(p, x, z, random.nextInt(4) == 0 ? 2 : 0);
        } else {
            fields(p, -edge, fieldFront, -3, end - 1, variant, random);
        }
    }

    private static void fields(Plot p, int x1, int z1, int x2, int z2, int variant, Random random) {
        boundary(p, x1, z1, x2, z2);
        boolean alongX = random.nextBoolean();
        int spacing = 4 + random.nextInt(2);
        Block[] crop = {Blocks.wheat, Blocks.carrots, Blocks.potatoes, ModBlocks.crop_strawberry, ModBlocks.crop_tea, ModBlocks.crop_coffee};
        int[] rows = new int[Math.max(x2 - x1, z2 - z1) + 1];
        for(int i = 0; i < rows.length; i++) rows[i] = variant == 0 ? 0 : variant == 1 ? 1 + random.nextInt(3) : random.nextInt(crop.length);
        for(int x = x1 + 1; x < x2; x++) for(int z = z1 + 1; z < z2; z++) {
            int row = alongX ? z - z1 : x - x1;
            if(row % spacing == 2) p.block(x, 0, z, Blocks.water);
            else if((alongX ? x - x1 : z - z1) % 13 == 0) p.block(x, 0, z, Blocks.dirt, 1);
            else {
                p.block(x, 0, z, Blocks.farmland, 7);
                p.block(x, 1, z, crop[rows[row]], 4 + random.nextInt(4));
            }
        }
    }

    private static void pen(Plot p, int x1, int z1, int x2, int z2, String species) {
        boundary(p, x1, z1, x2, z2);
        p.fill(x1 + 1, 0, z1 + 2, x1 + 3, 0, z1 + 3, Blocks.water);
        p.fill(x1 + 1, 1, z2 - 3, x1 + 2, 1, z2 - 2, Blocks.hay_block);
        for(int x = x1 + 4; x <= x2 - 2; x += 3) p.livestock(x, z1 + 5, species);
    }

    private static void boundary(Plot p, int x1, int z1, int x2, int z2) {
        p.fill(x1, 1, z1, x2, 1, z1, Blocks.fence);
        p.fill(x1, 1, z2, x2, 1, z2, Blocks.fence);
        p.fill(x1, 1, z1 + 1, x1, 1, z2 - 1, Blocks.fence);
        p.fill(x2, 1, z1 + 1, x2, 1, z2 - 1, Blocks.fence);
        p.cardinal(x2, 1, z1 + 3, Blocks.fence_gate, EAST);
    }

    private static void silo(Plot p, int x, int z) {
        p.fill(x - 1, 1, z - 1, x + 1, 9, z + 1, ModBlocks.block_aluminium);
        p.fillFacing(x - 1, 10, z, x + 1, 10, z, ModBlocks.steel_wall, NORTH);
        p.facing(x, 10, z - 1, ModBlocks.steel_wall, NORTH);
        p.facing(x, 10, z + 1, ModBlocks.steel_wall, SOUTH);
        p.fill(x, 1, z, x, 8, z, Blocks.air);
        p.door(x, 1, z - 1, true, NORTH, false);
        p.block(x, 1, z - 2, Blocks.stone_pressure_plate);
        p.sign(x, 4, z - 2, NORTH, "Grain silo");
    }

    private static void shell(Plot p, int x1, int z1, int x2, int z2, int floor, int roof, Block wall, int wallMeta,
            Block flooring, int floorMeta, Block roofing, int roofMeta) {
        p.fill(x1, floor - 1, z1, x2, floor - 1, z2, flooring, floorMeta);
        // The roof height also defines the clear interior volume in each column.
        p.fill(x1, roof, z1, x2, roof, z2, roofing, roofMeta);
        p.fill(x1, floor, z1, x2, roof - 1, z1, wall, wallMeta);
        p.fill(x1, floor, z2, x2, roof - 1, z2, wall, wallMeta);
        p.fill(x1, floor, z1 + 1, x1, roof - 1, z2 - 1, wall, wallMeta);
        p.fill(x2, floor, z1 + 1, x2, roof - 1, z2 - 1, wall, wallMeta);
    }

    private static void gable(Plot p, int left, int front, int right, int rear, int start, Block stair, Block wall, int meta) {
        int rise = Math.min(4, (right - left) / 2);
        for(int i = 0; i < rise; i++) {
            for(int z = front; z <= rear; z++) {
                p.stairs(left + i, start + i, z, stair, EAST);
                p.stairs(right - i, start + i, z, stair, WEST);
            }
            p.fill(left + i + 1, start + i, front + 1, right - i - 1, start + i, front + 1, wall, meta);
            p.fill(left + i + 1, start + i, rear - 1, right - i - 1, start + i, rear - 1, wall, meta);
        }
        p.fill(left + rise, start + rise, front, right - rise, start + rise, rear, Blocks.planks, 1);
    }

    private static void restroom(Plot p, int x1, int z1, int x2, int z2, int y) {
        p.fill(x1, y, z1, x2, y + 2, z1, Blocks.quartz_block);
        p.fill(x1, y, z1 + 1, x1, y + 2, z2, Blocks.quartz_block);
        p.fill(x2, y, z1 + 1, x2, y + 2, z2, Blocks.quartz_block);
        p.fill(x1 + 1, y, z2, x2 - 1, y + 2, z2, Blocks.quartz_block);
        p.door(x1 + 1, y, z1, false, NORTH, false);
        p.block(x2 - 1, y, z2 - 1, Blocks.cauldron, 3);
        p.stairs(x1 + 1, y, z2 - 1, Blocks.quartz_stairs, SOUTH);
        p.sign(x1 + 2, y + 2, z1 - 1, NORTH, "Restroom");
    }

    private static void table(Plot p, int x, int y, int z, Block stair, Block top) {
        p.stairs(x - 1, y, z, stair, WEST);
        p.block(x, y, z, top);
        p.stairs(x + 1, y, z, stair, EAST);
    }

    private static void desk(Plot p, int x, int y, int z) {
        p.fill(x, y, z, x + 1, y, z, Blocks.planks, 1);
        p.cardinal(x, y + 1, z, ModBlocks.deco_crt, NORTH);
        p.stairs(x, y, z - 1, Blocks.oak_stairs, NORTH);
    }

    private static void doubleDoor(Plot p, int x, int z, boolean metal) {
        p.door(x, 1, z, metal, NORTH, false);
        p.door(x + 1, 1, z, metal, NORTH, true);
    }

    private static void loadingDock(Plot p, int z, int x) {
        p.fill(x - 2, 1, z, x + 2, 3, z, Blocks.air);
        p.fillFacing(x - 2, 4, z, x + 2, 4, z, ModBlocks.steel_wall, SOUTH);
        p.fill(x - 2, 0, z + 1, x + 2, 0, z + 2, ModBlocks.concrete_smooth);
        p.fill(x - 2, 0, z + 3, x - 2, 0, z + 4, ModBlocks.concrete_colored, YELLOW);
        p.fill(x + 2, 0, z + 3, x + 2, 0, z + 4, ModBlocks.concrete_colored, YELLOW);
        p.sign(x, 5, z + 1, SOUTH, "Loading dock");
    }

    private static void vents(Plot p, int x, int y, int z, int count) {
        for(int i = 0; i < count; i++) {
            int dx = x + i * 5;
            p.fillFacing(dx, y, z, dx + 2, y, z + 1, ModBlocks.steel_wall, NORTH);
            p.fill(dx, y + 1, z, dx + 2, y + 1, z, ModBlocks.steel_grate);
            p.block(dx + 1, y + 1, z + 1, ModBlocks.air_vent);
        }
    }

    private static void parking(Plot p, int width, int end, int rows) {
        p.fill(-width, 0, 0, width, 0, end, ModBlocks.asphalt);
        p.fill(-width, 0, 0, -4, 0, 0, ModBlocks.concrete_smooth);
        p.fill(4, 0, 0, width, 0, 0, ModBlocks.concrete_smooth);
        int spacing = (end + 1) / rows;
        for(int row = 0; row < rows; row++) {
            int z = row * spacing + 1;
            for(int x = 5; x <= width - 2; x += 3) {
                p.fill(x, 0, z, x, 0, z + spacing - 3, ModBlocks.concrete_colored, WHITE);
                p.fill(-x, 0, z, -x, 0, z + spacing - 3, ModBlocks.concrete_colored, WHITE);
            }
        }
        for(int x = -width + 1; x <= width - 1; x += (width - 1) * 2) {
            p.fill(x, 1, end - 2, x, 4, end - 2, Blocks.nether_brick_fence);
            p.block(x, 5, end - 2, Blocks.glowstone);
        }
    }

    private static void lights(Plot p, int x1, int z1, int x2, int z2, int roof, int spacing) {
        for(int x = x1; x <= x2; x += spacing) for(int z = z1; z <= z2; z += spacing) p.block(x, roof, z, Blocks.glowstone);
    }

    private static void tree(Plot p, int x, int z, int species) {
        p.fill(x - 2, 3, z - 2, x + 2, 4, z + 2, Blocks.leaves, species | 4);
        p.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, Blocks.leaves, species | 4);
        p.fill(x, 1, z, x, 5, z, Blocks.log, species);
        p.block(x, 6, z, Blocks.leaves, species | 4);
    }
}
