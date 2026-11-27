package com.hbm.world.gen.structure;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.Rotation;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.ForgeDirection;

/** The shared block plan. Each column specifies a finished ground level and any custom layers. */
final class SettlementPlan {
    static final State AIR = new State(Blocks.air, 0);
    static final int MIN_LAYER = -6;
    private static final ForgeDirection[] COMPASS = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST };
    private static final int[] STAIR_FACING = { 3, 0, 2, 1 };
    private static final int[] BUTTON_FACING = { 4, 1, 3, 2 };

    final Map<Long, Column> columns = new LinkedHashMap<>();

    Column addColumn(int x, int y, int z, State surface, int clearance) {
        Column column = new Column(x, y, z, surface, clearance);
        if(columns.put(ChunkCoordIntPair.chunkXZ2Int(x, z), column) != null)
            throw new IllegalStateException("Overlapping settlement columns at " + x + ", " + z);
        return column;
    }

    static final class State {
        final Block block;
        final int meta;
        final String[] sign;

        State(Block block, int meta) { this(block, meta, null); }

        State(Block block, int meta, String[] sign) {
            this.block = block;
            this.meta = meta;
            this.sign = sign;
        }
    }

    static final class Column {
        final int x, y, z;
        final State surface;
        int bottom, top;
        private State[] layers;
        String livestock;

        Column(int x, int y, int z, State surface, int clearance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.surface = surface;
            top = clearance;
        }

        void set(int layer, State state) {
            int index = layer - MIN_LAYER;
            if(layers == null) layers = new State[Math.max(12, index + 1)];
            if(index >= layers.length) layers = java.util.Arrays.copyOf(layers, index + 1);
            layers[index] = state;
            bottom = Math.min(bottom, layer);
            top = Math.max(top, layer);
        }

        State at(int layer) {
            int index = layer - MIN_LAYER;
            if(layers != null && index >= 0 && index < layers.length && layers[index] != null) return layers[index];
            if(layer == 0) return surface;
            return layer >= bottom ? AIR : null;
        }
    }

    static final class Plot {
        final int halfWidth, depth;
        private final Column[][] columns;
        private final Rotation rotation;

        Plot(SettlementPlan plan, int halfWidth, int depth, int x, int y, int z, Rotation rotation) {
            this.halfWidth = halfWidth;
            this.depth = depth;
            this.rotation = rotation;
            columns = new Column[halfWidth * 2 + 1][depth];
            State grass = new State(Blocks.grass, 0);
            for(int dx = -halfWidth; dx <= halfWidth; dx++) for(int dz = 0; dz < depth; dz++) {
                BlockPos offset = new BlockPos(dx, 0, dz).rotate(rotation);
                columns[dx + halfWidth][dz] = plan.addColumn(x + offset.getX(), y, z + offset.getZ(), grass, 2);
            }
        }

        void block(int x, int y, int z, Block block) { block(x, y, z, block, 0); }

        void block(int x, int y, int z, Block block, int meta) {
            columns[x + halfWidth][z].set(y, new State(block, meta));
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            fill(x1, y1, z1, x2, y2, z2, block, 0);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block, int meta) {
            State state = block == Blocks.air ? AIR : new State(block, meta);
            for(int x = x1; x <= x2; x++) for(int z = z1; z <= z2; z++) for(int y = y1; y <= y2; y++)
                columns[x + halfWidth][z].set(y, state);
        }

        void facing(int x, int y, int z, Block block, int localDirection) {
            block(x, y, z, block, COMPASS[rotated(localDirection)].ordinal());
        }

        void door(int x, int y, int z, boolean metal, int localDirection, boolean rightHinge) {
            Block door = metal ? Blocks.iron_door : Blocks.wooden_door;
            block(x, y, z, door, (rotated(localDirection) + 3) & 3);
            block(x, y + 1, z, door, rightHinge ? 9 : 8);
        }

        void stairs(int x, int y, int z, Block stairs, int uphillDirection) {
            block(x, y, z, stairs, STAIR_FACING[rotated(uphillDirection)]);
        }

        void bed(int x, int y, int z, int towardHead) {
            ForgeDirection head = COMPASS[towardHead];
            int meta = (rotated(towardHead) + 2) & 3;
            block(x, y, z, Blocks.bed, meta);
            block(x + head.offsetX, y, z + head.offsetZ, Blocks.bed, meta | 8);
        }

        void cardinal(int x, int y, int z, Block block, int localDirection) {
            block(x, y, z, block, (rotated(localDirection) + 2) & 3);
        }

        void pipe(int x, int y, int z, Block block, int localDirection) {
            block(x, y, z, block, (rotated(localDirection) & 1) == 0 ? 8 : 4);
        }

        void button(int x, int y, int z, int localDirection) {
            block(x, y, z, Blocks.stone_button, BUTTON_FACING[rotated(localDirection)]);
        }

        void sign(int x, int y, int z, int localDirection, String... lines) {
            columns[x + halfWidth][z].set(y, new State(Blocks.wall_sign, COMPASS[rotated(localDirection)].ordinal(), lines));
        }

        void livestock(int x, int z, String type) { columns[x + halfWidth][z].livestock = type; }

        private int rotated(int direction) { return (direction + rotation.ordinal()) & 3; }
    }
}
