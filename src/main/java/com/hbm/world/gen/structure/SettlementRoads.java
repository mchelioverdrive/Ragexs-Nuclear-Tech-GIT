package com.hbm.world.gen.structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import com.hbm.blocks.ModBlocks;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementPlan.State;

import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;

public final class SettlementRoads {
    public enum Profile {
        TWO_LANE("two_lane", 3, true), FOUR_LANE("four_lane", 6, true), RESIDENTIAL("residential", 3, true),
        DIRT("dirt", 2, false), ACCESS("access", 2, true);

        final int halfWidth;
        final boolean sidewalk;
        private final String id;

        Profile(String id, int halfWidth, boolean sidewalk) {
            this.id = id;
            this.halfWidth = halfWidth;
            this.sidewalk = sidewalk;
        }

        int radius() { return halfWidth + (sidewalk ? 1 : 0); }
        public String nameKey() { return "settlement.road." + id; }
    }

    private SettlementRoads() { }

    static SettlementPlan component(int x, int y, int z, Rotation rotation, Profile profile, int variant) {
        Grid road = new Grid(profile);
        IntUnaryOperator level = ignored -> y;
        int radius = profile.radius();
        switch(variant) {
        case 0: road.line(false, 0, 0, 19, level); break;
        case 1:
            int bend = radius + 5;
            road.line(false, 0, 0, 9, level);
            road.rightCurve(10, bend, y);
            road.line(true, 10 + bend, bend + 1, bend + 9, level);
            break;
        case 2:
        case 3:
            int center = 10 + radius;
            road.line(false, 0, 0, variant == 2 ? center : center * 2, level);
            road.line(true, center, -center, center, level);
            road.unmarked(-radius, center - radius, radius, center + radius);
            break;
        case 4: road.line(false, 0, 0, 19, step -> y + step / 6); break;
        case 5:
            road.line(false, 0, 0, 15, level);
            road.unmarked(-radius, 13, radius, 15);
            break;
        case 6:
            road.line(false, 0, 0, 16, level);
            road.bulb(0, 16, y, radius + 2);
            road.unmarked(-radius, 14, radius, 16);
            break;
        default: throw new IllegalArgumentException("Unknown road component " + variant);
        }
        SettlementPlan plan = new SettlementPlan();
        road.addTo(plan, x, z, rotation);
        return plan;
    }

    static final class Grid {
        private final Profile profile;
        private final Map<Long, Cell> cells = new LinkedHashMap<>();

        Grid(Profile profile) { this.profile = profile; }

        void line(boolean alongX, int cross, int start, int end, IntUnaryOperator grade) {
            for(int step = start; step <= end; step++) {
                int y = grade.applyAsInt(step);
                for(int width = -profile.radius(); width <= profile.radius(); width++) {
                    int x = alongX ? step : cross + width;
                    int z = alongX ? cross + width : step;
                    put(new Cell(x, y, z, Math.abs(width), step, false));
                }
            }
        }

        void bulb(int x, int z, int y, int radius) {
            int inside = profile.sidewalk ? radius - 1 : radius;
            for(int dx = -radius; dx <= radius; dx++) for(int dz = -radius; dz <= radius; dz++) {
                int distance = dx * dx + dz * dz;
                if(distance > radius * radius) continue;
                int lane = distance <= inside * inside ? profile.halfWidth - 1 : profile.radius();
                put(new Cell(x + dx, y, z + dz, lane, 0, true));
            }
        }

        void rightCurve(int lead, int radius, int y) {
            for(int x = -profile.radius(); x <= radius; x++) for(int z = lead; z <= lead + radius + profile.radius(); z++) {
                double dx = radius - x, dz = z - lead;
                int offset = (int) Math.round(Math.abs(Math.sqrt(dx * dx + dz * dz) - radius));
                if(offset > profile.radius()) continue;
                int step = lead + (int) Math.round(Math.atan2(dz, dx) * radius);
                put(new Cell(x, y, z, offset, step, false));
            }
        }

        private void put(Cell cell) {
            long key = ChunkCoordIntPair.chunkXZ2Int(cell.x, cell.z);
            Cell previous = cells.get(key);
            if(previous == null || cell.distance < previous.distance) cells.put(key, cell);
        }

        void unmarked(int x1, int z1, int x2, int z2) {
            for(int x = x1; x <= x2; x++) for(int z = z1; z <= z2; z++) {
                Cell cell = cells.get(ChunkCoordIntPair.chunkXZ2Int(x, z));
                if(cell != null) cell.unmarked = true;
            }
        }

        void access(int x, boolean south, int halfWidth) {
            int edge = south ? profile.radius() : -profile.radius();
            for(int dx = -halfWidth; dx <= halfWidth; dx++) {
                Cell cell = cells.get(ChunkCoordIntPair.chunkXZ2Int(x + dx, edge));
                if(cell != null) cell.access = true;
            }
            unmarked(x - halfWidth, south ? 1 : -profile.radius(), x + halfWidth, south ? profile.radius() : -1);
        }

        void addTo(SettlementPlan plan, int x, int z, Rotation rotation) {
            State asphalt = new State(ModBlocks.asphalt, 0);
            State concrete = new State(ModBlocks.concrete_smooth, 0);
            State yellow = new State(ModBlocks.concrete_colored, 4);
            State white = new State(ModBlocks.concrete_colored, 0);
            State dirt = new State(Blocks.dirt, 1);
            for(Cell cell : cells.values()) {
                State pavement = asphalt;
                if(profile == Profile.DIRT) pavement = dirt;
                else if(cell.access) pavement = profile == Profile.RESIDENTIAL ? concrete : asphalt;
                else if(cell.distance > profile.halfWidth) pavement = concrete;
                else if(!cell.unmarked) {
                    int dash = Math.floorMod(cell.step, 6);
                    if(profile == Profile.FOUR_LANE && cell.distance == 0) pavement = yellow;
                    else if(profile == Profile.FOUR_LANE && cell.distance == 3 && dash < 3) pavement = white;
                    else if(profile == Profile.TWO_LANE && cell.distance == 0 && dash < 3) pavement = yellow;
                    else if(profile == Profile.RESIDENTIAL && cell.distance == 0 && dash < 2) pavement = white;
                    else if(profile == Profile.ACCESS && cell.distance == profile.halfWidth) pavement = white;
                }
                BlockPos offset = new BlockPos(cell.x, 0, cell.z).rotate(rotation);
                plan.addColumn(x + offset.getX(), cell.y, z + offset.getZ(), pavement, 3);
            }
        }
    }

    private static final class Cell {
        final int x, y, z, distance, step;
        boolean unmarked, access;

        Cell(int x, int y, int z, int distance, int step, boolean unmarked) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
            this.step = step;
            this.unmarked = unmarked;
        }
    }
}
