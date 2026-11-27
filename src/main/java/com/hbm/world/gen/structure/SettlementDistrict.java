package com.hbm.world.gen.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;

import com.hbm.config.SettlementConfig.Values;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementRoads.Grid;
import com.hbm.world.gen.structure.SettlementRoads.Profile;

import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/** A finite set of lots and their common road. No part requests new chunks. */
final class SettlementDistrict {
    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final int END = 9;

    final SettlementKind kind;
    final int x, z;
    final Rotation rotation;
    final Profile road;
    final List<Lot> lots = new ArrayList<>();
    final Set<Long> chunks = new LinkedHashSet<>();
    final StructureBoundingBox bounds = StructureBoundingBox.getNewBoundingBox();
    private int roadStart = -END, bulbRadius;

    private SettlementDistrict(SettlementKind kind, int x, int z, Rotation rotation, Profile road) {
        this.kind = kind;
        this.x = x;
        this.z = z;
        this.rotation = rotation;
        this.road = road;
    }

    static List<SettlementDistrict> alternatives(SettlementKind kind, int x, int z, long seed, Values config) {
        Random random = new Random(seed);
        Rotation rotation = ROTATIONS[random.nextInt(4)];
        int variant = variant(kind, random);
        long lotSeed = random.nextLong();
        List<SettlementDistrict> options = new ArrayList<>();
        Profile profile = config.roads ? profile(kind) : null;
        if(profile != null) options.add(connected(kind, x, z, rotation, variant, lotSeed, profile, random, config));
        for(int fallback = variant; fallback >= 0; fallback = kind.smallerVariant(fallback)) {
            for(int turn = 0; turn < ROTATIONS.length; turn++) {
                SettlementDistrict single = new SettlementDistrict(kind, x, z, ROTATIONS[(rotation.ordinal() + turn) & 3], null);
                single.add(kind, fallback, 0, true, lotSeed);
                options.add(single);
            }
        }
        return options;
    }

    private static SettlementDistrict connected(SettlementKind kind, int x, int z, Rotation rotation, int variant,
            long lotSeed, Profile profile, Random random, Values config) {
        SettlementDistrict district = new SettlementDistrict(kind, x, z, rotation, profile);
        district.add(kind, variant, 0, true, lotSeed);

        int count = 1;
        if(kind == SettlementKind.HOUSE && config.neighborhoods && config.neighborhoodSize > 1 && random.nextInt(4) != 0)
            count = 2 + random.nextInt(config.neighborhoodSize - 1);
        if(kind == SettlementKind.STORE && SettlementKind.RESTAURANT.frequency(config) > 0) count = 2;
        if(kind == SettlementKind.RESTAURANT && SettlementKind.HOUSE.frequency(config) > 0 && random.nextInt(3) == 0) count = 2;
        if(kind == SettlementKind.FARM && random.nextBoolean()) count = 2;
        if(kind == SettlementKind.DATA_CENTER && variant != 2 && random.nextInt(3) == 0) count = 2;
        int distance = 0;
        for(int i = 1; i < count; i++) {
            SettlementKind next = kind == SettlementKind.STORE ? SettlementKind.RESTAURANT
                    : kind == SettlementKind.RESTAURANT ? SettlementKind.HOUSE : kind;
            int nextVariant = next == SettlementKind.DATA_CENTER ? 0 : variant(next, random);
            Lot previous = district.lots.get(i - 1);
            distance += previous.kind.halfWidth(previous.variant) + next.halfWidth(nextVariant) + 4;
            if(distance + END * 2 + 1 > config.roadLength) break;
            district.add(next, nextVariant, distance, (i & 1) == 0, random.nextLong());
        }
        if(kind == SettlementKind.HOUSE) {
            int bulb = profile.radius() + 2;
            int start = -kind.halfWidth(variant) - bulb - 1;
            if(district.last().along + END - start + bulb + 1 <= config.roadLength && random.nextBoolean()) {
                district.roadStart = start;
                district.bulbRadius = bulb;
                district.include(start - bulb, -bulb, start + bulb, bulb);
            }
        }
        district.include(district.roadStart, -profile.radius(), district.last().along + END, profile.radius());
        return district;
    }

    boolean enabled(Values config) {
        if((road != null && !config.roads) || (kind == SettlementKind.HOUSE && lots.size() > 1 && !config.neighborhoods)) return false;
        for(Lot lot : lots) if(lot.kind.frequency(config) == 0) return false;
        return true;
    }

    int variant() { return lots.get(0).variant; }

    String description() {
        StringBuilder details = new StringBuilder(rotation.name());
        if(road != null) details.append(" road=").append(road.name());
        for(Lot lot : lots) details.append(' ').append(lot.kind.variantName(lot.variant));
        return details.toString();
    }

    SettlementPlan build(IntBinaryOperator surface) {
        SettlementPlan plan = new SettlementPlan();
        TreeMap<Integer, Integer> grade = new TreeMap<>();
        for(Lot lot : lots) {
            int offset = road == null ? 0 : (road.radius() + 1) * (lot.south ? 1 : -1);
            BlockPos pos = position(lot.along, offset);
            int y = padLevel(lot, offset, surface);
            if(y < 1) throw new IllegalStateException("no supporting surface for " + lot.kind.variantName(lot.variant));
            if(road != null) {
                grade.put(lot.along - lot.entranceHalfWidth(), y);
                grade.put(lot.along + lot.entranceHalfWidth(), y);
            }
            SettlementBuildings.build(plan, lot.kind, lot.variant, pos.getX(), y, pos.getZ(),
                    lot.south ? rotation : rotation.add(Rotation.CLOCKWISE_180), lot.seed);
        }
        if(road == null) return plan;

        extendGrade(grade, roadStart, bulbRadius, grade.firstEntry(), surface);
        if(bulbRadius > 0) grade.put(roadStart + bulbRadius, grade.get(roadStart));
        extendGrade(grade, last().along + END, 0, grade.lastEntry(), surface);
        Map.Entry<Integer, Integer> previous = null;
        for(Map.Entry<Integer, Integer> entry : grade.entrySet()) {
            if(previous != null && Math.abs(entry.getValue() - previous.getValue()) * 4 > entry.getKey() - previous.getKey())
                throw new IllegalStateException("road grade exceeds one block per four horizontal blocks");
            previous = entry;
        }
        Grid grid = new Grid(road);
        grid.line(true, 0, roadStart, last().along + END, step -> {
            Map.Entry<Integer, Integer> low = grade.floorEntry(step), high = grade.ceilingEntry(step);
            if(low.getKey().equals(high.getKey())) return low.getValue();
            return low.getValue() + (high.getValue() - low.getValue()) * (step - low.getKey()) / (high.getKey() - low.getKey());
        });
        if(bulbRadius > 0) grid.bulb(roadStart, 0, grade.get(roadStart), bulbRadius);
        grid.unmarked(roadStart, -road.radius(), roadStart + 2, road.radius());
        grid.unmarked(last().along + END - 2, -road.radius(), last().along + END, road.radius());
        for(Lot lot : lots) grid.access(lot.along, lot.south, lot.entranceHalfWidth());
        grid.addTo(plan, x, z, rotation);
        return plan;
    }

    private int padLevel(Lot lot, int offset, IntBinaryOperator surface) {
        int[] heights = new int[9];
        int count = 0;
        for(int across = -1; across <= 1; across++) for(int inward = 0; inward < 3; inward++) {
            int x = lot.along + across * lot.kind.halfWidth(lot.variant) * 3 / 4;
            int z = offset + (lot.south ? 1 : -1) * inward * (lot.kind.depth(lot.variant) - 1) / 2;
            BlockPos sample = position(x, z);
            heights[count++] = surface.applyAsInt(sample.getX(), sample.getZ());
        }
        Arrays.sort(heights);
        return (heights[1] + heights[7]) / 2;
    }

    private void extendGrade(TreeMap<Integer, Integer> grade, int end, int flat, Map.Entry<Integer, Integer> anchor, IntBinaryOperator surface) {
        BlockPos pos = position(end, 0);
        int relief = (Math.abs(end - anchor.getKey()) - flat) / 4;
        int y = MathHelper.clamp_int(surface.applyAsInt(pos.getX(), pos.getZ()), anchor.getValue() - relief, anchor.getValue() + relief);
        grade.put(end, y);
    }

    private void add(SettlementKind type, int variant, int along, boolean south, long seed) {
        Lot lot = new Lot(type, variant, along, south, seed);
        lots.add(lot);
        int half = type.halfWidth(variant), depth = type.depth(variant);
        int cross = road == null ? 0 : road.radius() + 1;
        include(along - half, south ? cross : -cross - depth + 1, along + half, south ? cross + depth - 1 : -cross);
    }

    private void include(int x1, int z1, int x2, int z2) {
        BlockPos a = position(x1, z1), b = position(x2, z2);
        int minX = Math.min(a.getX(), b.getX()), minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()), maxZ = Math.max(a.getZ(), b.getZ());
        bounds.expandTo(new StructureBoundingBox(minX, 0, minZ, maxX, 255, maxZ));
        for(int cx = minX >> 4; cx <= maxX >> 4; cx++) for(int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
            chunks.add(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
    }

    private BlockPos position(int dx, int dz) { return new BlockPos(dx, 0, dz).rotate(rotation).add(x, 0, z); }

    private Lot last() { return lots.get(lots.size() - 1); }

    private static int variant(SettlementKind kind, Random random) {
        if(kind != SettlementKind.DATA_CENTER) return random.nextInt(kind.variantCount());
        int roll = random.nextInt(100);
        return roll < 70 ? 0 : roll < 95 ? 1 : 2;
    }

    private static Profile profile(SettlementKind kind) {
        switch(kind) {
        case RESTAURANT: return Profile.TWO_LANE;
        case STORE: return Profile.FOUR_LANE;
        case HOUSE: return Profile.RESIDENTIAL;
        case FARM: return Profile.DIRT;
        default: return Profile.ACCESS;
        }
    }

    private static final class Lot {
        final SettlementKind kind;
        final int variant, along;
        final boolean south;
        final long seed;

        Lot(SettlementKind kind, int variant, int along, boolean south, long seed) {
            this.kind = kind;
            this.variant = variant;
            this.along = along;
            this.south = south;
            this.seed = seed;
        }

        int entranceHalfWidth() {
            return kind == SettlementKind.HOUSE || kind == SettlementKind.FARM ? 1 : 3;
        }
    }
}
