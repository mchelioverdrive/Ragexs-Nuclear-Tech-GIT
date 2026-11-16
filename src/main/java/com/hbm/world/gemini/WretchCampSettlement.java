package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.world.gemini.WretchCamps.Camp;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Modular layout and terrain planning for the existing Wretch camp, not a world generator. */
final class WretchCampSettlement {
    static final int VERSION = 1;
    static final int CENTER = 0, SHELTER = 1, HOUSE = 2, HALL = 3, ORGANIC = 4,
            BURROW = 5, LOOKOUT = 6, STORE = 7;
    static final int CRAWLER = 1, PERSON = 2;

    static final class Piece {
        final int kind, x, z, rotation;
        int y, role, resident, retries; // resident: 0 pending, 1 admitted once, 2 exhausted
        Piece(int kind, int x, int y, int z, int rotation) {
            this.kind = kind; this.x = x; this.y = y; this.z = z; this.rotation = rotation;
        }
        int halfX() { return kind == CENTER ? 10 : kind == HALL ? 8 : kind == BURROW ? 5 : kind == LOOKOUT ? 4 : kind == STORE ? 3 : 7; }
        int halfZ() { return kind == CENTER ? 10 : kind == BURROW ? 9 : kind == LOOKOUT ? 4 : kind == STORE ? 3 : 7; }
        int tx(int dx, int dz) { return x + (rotation == 0 ? dx : rotation == 1 ? -dz : rotation == 2 ? -dx : dz); }
        int tz(int dx, int dz) { return z + (rotation == 0 ? dz : rotation == 1 ? dx : rotation == 2 ? -dz : -dx); }
        int radiusX() { return (rotation & 1) == 0 ? halfX() : halfZ(); }
        int radiusZ() { return (rotation & 1) == 0 ? halfZ() : halfX(); }
        int doorX() { return tx(0, halfZ() + 3); }
        int doorZ() { return tz(0, halfZ() + 3); }
        boolean overlaps(Piece other) {
            return Math.abs(x - other.x) <= radiusX() + other.radiusX() + 1
                    && Math.abs(z - other.z) <= radiusZ() + other.radiusZ() + 1;
        }
    }

    static final class Road {
        final int x, y, z, width;
        Road(int x, int y, int z, int width) { this.x = x; this.y = y; this.z = z; this.width = width; }
    }

    static Camp create(World world, int x, int y, int z) {
        Camp camp = new Camp(x, y, z);
        camp.layout = VERSION;
        camp.seed = world.getSeed() ^ x * 341873128712L ^ z * 132897987541L;
        Random random = new Random(camp.seed);
        int sizeRoll = random.nextInt(5);
        camp.size = sizeRoll == 0 ? 80 : sizeRoll == 4 ? 144 : 112;
        return camp;
    }

    static AxisAlignedBB bounds(Camp camp, int margin, int height) {
        int half = camp.size / 2;
        return AxisAlignedBB.getBoundingBox(camp.x - half - margin, 0, camp.z - half - margin,
                camp.x + half + margin, height, camp.z + half + margin);
    }

    private static int facing(int x, int z) {
        return Math.abs(x) > Math.abs(z) ? (x > 0 ? 1 : 3) : z > 0 ? 2 : 0;
    }

    private static boolean fits(Camp camp, Piece piece) {
        int half = camp.size / 2;
        if(piece.x - piece.radiusX() < -half || piece.x + piece.radiusX() >= half
                || piece.z - piece.radiusZ() < -half || piece.z + piece.radiusZ() >= half) return false;
        for(Piece other : camp.pieces) {
            if(piece.overlaps(other) || coreContains(other, piece.doorX(), piece.doorZ(), piece.kind <= ORGANIC ? 2 : 1)
                    || coreContains(piece, other.doorX(), other.doorZ(), other.kind <= ORGANIC ? 2 : 1)) return false;
            if(other.kind != CENTER) for(int z = piece.halfZ() - 1; z <= piece.halfZ() + 3; z++) for(int x = -1; x <= 1; x++)
                if(inApron(other, piece.tx(x, z), piece.tz(x, z))) return false;
        }
        return true;
    }

    private static void add(Camp camp, int kind, int x, int z) {
        camp.pieces.add(new Piece(kind, x, camp.y, z, facing(x, z)));
    }

    private static boolean arrange(Camp camp) {
        if(!camp.pieces.isEmpty()) return true;
        Random random = new Random(camp.seed);
        int half = camp.size / 2;
        camp.pieces.add(new Piece(CENTER, 0, camp.y, 0, 0));
        // These four small lookouts define the envelope, without clearing its intervening land.
        add(camp, LOOKOUT, -half + 4, 0); add(camp, LOOKOUT, half - 5, 0);
        add(camp, LOOKOUT, 0, -half + 4); add(camp, LOOKOUT, 0, half - 5);
        add(camp, BURROW, -half + 12, -half + 12);
        add(camp, BURROW, half - 13, half - 13);
        camp.major = (camp.size == 80 ? 6 : camp.size == 112 ? 8 : 10) + random.nextInt(camp.size == 144 ? 3 : 2);
        camp.minor = Math.min(16, 8 + (camp.size - 80) / 8 + random.nextInt(3));
        if(camp.size == 80) {
            // Reserve real access corridors in the densest size instead of hoping random
            // packing leaves enough room between the central camp and perimeter lookouts.
            List<int[]> plots = new ArrayList<int[]>();
            for(int[] plot : new int[][] {{-22, 0}, {21, 0}, {0, -22}, {0, 21}, {23, -23}, {-23, 23}}) plots.add(plot);
            Collections.shuffle(plots, random);
            for(int i = 1; i < camp.major; i++) {
                int[] plot = plots.get(i - 1);
                Piece piece = new Piece(majorKind(i), plot[0], camp.y, plot[1], facing(plot[0], plot[1]));
                if(!fits(camp, piece)) { camp.pieces.clear(); return false; }
                camp.pieces.add(piece);
            }
            plots.clear();
            for(int[] plot : new int[][] {{-13, -34}, {13, 33}, {-34, 11}, {33, -11}}) plots.add(plot);
            Collections.shuffle(plots, random);
            for(int i = 6; i < camp.minor; i++) {
                int[] plot = plots.get(i - 6);
                Piece piece = new Piece(STORE, plot[0], camp.y, plot[1], facing(plot[0], plot[1]));
                if(!fits(camp, piece)) { camp.pieces.clear(); return false; }
                camp.pieces.add(piece);
            }
        } else {
            double offset = random.nextDouble() * Math.PI * 2;
            for(int i = 1; i < camp.major; i++) {
                int kind = majorKind(i);
                boolean placed = false;
                for(int attempt = 0; attempt < 32 && !placed; attempt++) {
                    double angle = offset + i * Math.PI * 2 / (camp.major - 1) + (random.nextDouble() - 0.5D) * 0.4D;
                    double scale = Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle)));
                    int radius = half - 16 - attempt % 4 * 2;
                    int x = (int) Math.round(Math.cos(angle) / scale * radius), z = (int) Math.round(Math.sin(angle) / scale * radius);
                    Piece piece = new Piece(kind, x, camp.y, z, facing(x, z));
                    if(fits(camp, piece)) { camp.pieces.add(piece); placed = true; }
                }
                // A bounded packing fallback completes dense small camps without relocating the origin.
                for(int z = -half + 10; z < half - 9 && !placed; z += 3) for(int x = -half + 10; x < half - 9 && !placed; x += 3) {
                    Piece piece = new Piece(kind, x, camp.y, z, facing(x, z));
                    if(fits(camp, piece)) { camp.pieces.add(piece); placed = true; }
                }
                if(!placed) { camp.pieces.clear(); return false; }
            }
            for(int i = 6; i < camp.minor; i++) {
                boolean placed = false;
                for(int attempt = 0; attempt < 160 && !placed; attempt++) {
                    int x = random.nextInt(camp.size - 10) - half + 5, z = random.nextInt(camp.size - 10) - half + 5;
                    Piece piece = new Piece(STORE, x, camp.y, z, facing(x, z));
                    if(fits(camp, piece)) { camp.pieces.add(piece); placed = true; }
                }
                for(int z = -half + 5; z < half - 5 && !placed; z += 2) for(int x = -half + 5; x < half - 5 && !placed; x += 2) {
                    Piece piece = new Piece(STORE, x, camp.y, z, facing(x, z));
                    if(fits(camp, piece)) { camp.pieces.add(piece); placed = true; }
                }
                if(!placed) { camp.pieces.clear(); return false; }
            }
        }
        int crawlers = 2 + camp.size / 24, people = camp.size / 24;
        // Assign finite, saved initial slots across the settlement, never at its central boss.
        for(Piece piece : camp.pieces) {
            if(piece.kind == CENTER || piece.kind == LOOKOUT) continue;
            if(piece.kind == HOUSE && people > 0) { piece.role = PERSON; people--; }
            else if(crawlers > 0) { piece.role = CRAWLER; crawlers--; }
            else if(people > 0) { piece.role = PERSON; people--; }
        }
        return true;
    }

    private static int majorKind(int index) {
        return index % 4 == 0 ? ORGANIC : index % 3 == 0 ? HALL : index % 2 == 0 ? HOUSE : SHELTER;
    }

    private static final class Column {
        final int surface, top;
        Column(World world, int x, int z) {
            surface = Math.max(1, GardenerNests.surface(world, x, z)); top = world.getHeightValue(x, z);
        }
    }
    private static final class Terrain {
        final WorldServer world;
        final Camp camp;
        final Map<Long, Column> columns = new HashMap<Long, Column>();
        final int shift;
        Terrain(WorldServer world, Camp camp) {
            this.world = world; this.camp = camp;
            shift = camp.y - column(0, 0).surface;
        }
        Column column(int x, int z) {
            long key = key(x, z); Column column = columns.get(key);
            if(column == null) { column = new Column(world, camp.x + x, camp.z + z); columns.put(key, column); }
            return column;
        }
        int height(int x, int z) {
            return MathHelper.clamp_int(column(x, z).surface + shift, 1, world.getActualHeight() - 20);
        }
    }

    static GardenerNests.PlacementPlan plan(WorldServer world, Camp camp) {
        GardenerNests.PlacementPlan plan = new GardenerNests.PlacementPlan(camp.x, camp.y, camp.z);
        plan.regions.add(bounds(camp, 0, world.getActualHeight()));
        plan.readBounds = bounds(camp, 2, world.getActualHeight());
        if(camp.y < 1 || camp.y + 20 > world.getActualHeight()) plan.reason = "unsafe camp foundation/resident world height";
        else plan.reason = plan.checkBounds(world);
        if(plan.reason != null) return plan;
        if(!arrange(camp)) { plan.reason = "could not pack the complete camp component layout"; return plan; }
        Terrain terrain = new Terrain(world, camp);
        if(!camp.graded) {
            for(Piece piece : camp.pieces) if(piece.kind != CENTER) {
                List<Integer> heights = new ArrayList<Integer>();
                heights.add(terrain.height(piece.x, piece.z));
                for(int dx : new int[] {-3, 3}) for(int dz : new int[] {-3, 3}) heights.add(terrain.height(piece.x + dx, piece.z + dz));
                Collections.sort(heights);
                int difference = Math.min(16, Math.max(1, Math.max(Math.abs(piece.x), Math.abs(piece.z)) - 12));
                piece.y = MathHelper.clamp_int(heights.get(2), Math.max(1, camp.y - difference), Math.min(world.getActualHeight() - 20, camp.y + difference));
            }
            if(!roads(camp, terrain)) { plan.reason = "could not connect all camp entrances within the settlement bounds"; return plan; }
            camp.graded = true;
        }
        // Prepare independent pads. The unoccupied land between them is deliberately untouched.
        for(Piece piece : camp.pieces) {
            for(int dz = -piece.halfZ(); dz <= piece.halfZ(); dz++) for(int dx = -piece.halfX(); dx <= piece.halfX(); dx++)
                prepare(plan, terrain, piece.tx(dx, dz), piece.tz(dx, dz), piece.y, 20, Blocks.grass);
            for(int dz = piece.halfZ() - 1; dz <= piece.halfZ() + 3; dz++) for(int dx = -1; dx <= 1; dx++) {
                int x = piece.tx(dx, dz), z = piece.tz(dx, dz);
                if(inside(camp, x, z)) prepare(plan, terrain, x, z, piece.y, 8, Blocks.gravel);
            }
        }
        Map<Long, Road> widened = new HashMap<Long, Road>();
        Map<Long, Integer> distances = new HashMap<Long, Integer>();
        for(Road road : camp.roads) for(int dx = -road.width; dx <= road.width; dx++) for(int dz = -road.width; dz <= road.width; dz++) {
            int x = road.x + dx, z = road.z + dz;
            if(!inside(camp, x, z) || blocked(camp, x, z, 0)) continue;
            long key = key(x, z); int distance = dx * dx + dz * dz;
            if(!distances.containsKey(key) || distance < distances.get(key)) { widened.put(key, new Road(x, road.y, z, 0)); distances.put(key, distance); }
        }
        for(Road road : widened.values()) prepare(plan, terrain, road.x, road.z, road.y, 20, Blocks.gravel);
        // Preserve every graded centerline at junctions, including one-block steps.
        for(Road road : camp.roads) prepare(plan, terrain, road.x, road.z, road.y, 20, Blocks.gravel);
        for(Piece piece : camp.pieces) build(plan, camp, piece);
        perimeter(plan, camp, terrain, widened);
        plan.reason = plan.checkBounds(world);
        return plan;
    }

    private static void prepare(GardenerNests.PlacementPlan plan, Terrain terrain, int x, int z, int y, int clearance, Block floor) {
        int wx = terrain.camp.x + x, wz = terrain.camp.z + z, support = y - 1;
        while(support >= 0 && (!World.doesBlockHaveSolidTopSurface(terrain.world, wx, support, wz)
                || terrain.world.getBlock(wx, support, wz) instanceof net.minecraft.block.BlockFalling)) support--;
        for(int fill = support + 1; fill < y - 1; fill++) plan.put(wx, fill, wz, Blocks.dirt, 0);
        plan.put(wx, y - 1, wz, floor, 0);
        int top = Math.min(terrain.world.getActualHeight(), Math.max(y + clearance, terrain.column(x, z).top));
        for(int clear = y; clear < top; clear++) plan.put(wx, clear, wz, Blocks.air, 0);
    }

    private static boolean inside(Camp camp, int x, int z) {
        int half = camp.size / 2;
        return x >= -half && x < half && z >= -half && z < half;
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    private static boolean blocked(Camp camp, int x, int z, int margin) {
        for(Piece piece : camp.pieces) if(coreContains(piece, x, z, margin)) return true;
        return false;
    }
    private static boolean coreContains(Piece piece, int x, int z, int margin) {
        int rx = piece.kind == CENTER ? 7 : Math.max(1, piece.radiusX() - 2);
        int rz = piece.kind == CENTER ? 7 : Math.max(1, piece.radiusZ() - 2);
        if(piece.kind == STORE) { rx = 2; rz = 2; }
        if(piece.kind == LOOKOUT) { rx = 3; rz = 3; }
        if(piece.kind == CENTER && x >= piece.x - 8 - margin && x <= piece.x - 5 + margin
                && Math.abs(z - piece.z + 8) <= margin) return true;
        return Math.abs(x - piece.x) <= rx + margin && Math.abs(z - piece.z) <= rz + margin;
    }
    private static boolean inApron(Piece piece, int x, int z) {
        int dx = x - piece.x, dz = z - piece.z;
        int localX = piece.rotation == 0 ? dx : piece.rotation == 1 ? dz : piece.rotation == 2 ? -dx : -dz;
        int localZ = piece.rotation == 0 ? dz : piece.rotation == 1 ? -dx : piece.rotation == 2 ? -dz : dx;
        return Math.abs(localX) <= 1 && localZ >= piece.halfZ() - 1 && localZ <= piece.halfZ() + 3;
    }
    private static boolean reservedApron(Camp camp, int x, int z) {
        for(Piece piece : camp.pieces) if(inApron(piece, x, z)) return true;
        return false;
    }
    private static void apron(Map<Long, Road> network, Piece piece) {
        for(int z = piece.halfZ() - 1; z <= piece.halfZ() + 3; z++) {
            int x = piece.tx(0, z), rz = piece.tz(0, z);
            network.put(key(x, rz), new Road(x, piece.y, rz, piece.kind <= ORGANIC ? 3 : 2));
        }
    }
    private static final class Node {
        final int x, z, steps, cost; final Node parent;
        Node(int x, int z, int steps, int cost, Node parent) { this.x = x; this.z = z; this.steps = steps; this.cost = cost; this.parent = parent; }
    }
    private static boolean roads(Camp camp, Terrain terrain) {
        camp.roads.clear();
        Map<Long, Road> network = new HashMap<Long, Road>();
        apron(network, camp.pieces.get(0));
        List<Piece> destinations = new ArrayList<Piece>(camp.pieces);
        Collections.sort(destinations, new Comparator<Piece>() {
            @Override public int compare(Piece a, Piece b) { return Integer.compare(a.x * a.x + a.z * a.z, b.x * b.x + b.z * b.z); }
        });
        for(Piece piece : destinations) {
            if(piece.kind == CENTER) continue;
            Road shared = null;
            for(int z = piece.halfZ() - 1; z <= piece.halfZ() + 3 && shared == null; z++)
                shared = network.get(key(piece.tx(0, z), piece.tz(0, z)));
            if(shared != null) { piece.y = shared.y; apron(network, piece); continue; }
            PriorityQueue<Node> open = new PriorityQueue<Node>(64, new Comparator<Node>() {
                @Override public int compare(Node a, Node b) {
                    int order = Integer.compare(a.cost, b.cost);
                    if(order == 0) order = Integer.compare(a.x, b.x);
                    return order == 0 ? Integer.compare(a.z, b.z) : order;
                }
            });
            Map<Long, Node> best = new HashMap<Long, Node>();
            Node start = new Node(piece.doorX(), piece.doorZ(), 0, 0, null), goal = null;
            open.add(start); best.put(key(start.x, start.z), start);
            int examined = 0;
            while(!open.isEmpty() && examined++ < camp.size * camp.size * 4) {
                Node node = open.poll();
                if(best.get(key(node.x, node.z)) != node) continue;
                Road join = network.get(key(node.x, node.z));
                if(join != null) {
                    goal = node; break;
                }
                for(int side = 0; side < 4; side++) {
                    int x = node.x + (side == 0 ? 1 : side == 1 ? -1 : 0), z = node.z + (side == 2 ? 1 : side == 3 ? -1 : 0);
                    if(!inside(camp, x, z) || !network.containsKey(key(x, z))
                            && (blocked(camp, x, z, piece.kind <= ORGANIC ? 2 : 1) || reservedApron(camp, x, z))) continue;
                    int roughness = Math.min(12, Math.abs(terrain.height(x, z) - terrain.height(node.x, node.z)) * 2);
                    int cost = node.cost + 10 + roughness + (int) ((x * 73428767L ^ z * 912931L ^ camp.seed) & 3);
                    Node previous = best.get(key(x, z));
                    if(previous != null && previous.cost <= cost) continue;
                    Node next = new Node(x, z, node.steps + 1, cost, node);
                    best.put(key(x, z), next); open.add(next);
                }
            }
            if(goal == null) { camp.roads.clear(); return false; }
            int y = network.get(key(goal.x, goal.z)).y, remaining = goal.steps;
            // Close buildings may need their pad raised/lowered to meet an existing road.
            // Reshape the pad rather than rejecting hills or inventing an unwalkable ramp.
            piece.y = MathHelper.clamp_int(piece.y, y - remaining, y + remaining);
            for(Node node = goal.parent; node != null; node = node.parent) {
                remaining--;
                int low = Math.max(y - 1, piece.y - remaining), high = Math.min(y + 1, piece.y + remaining);
                y = MathHelper.clamp_int(terrain.height(node.x, node.z), low, high);
                network.put(key(node.x, node.z), new Road(node.x, y, node.z, piece.kind <= ORGANIC ? 3 : 2));
            }
            apron(network, piece);
        }
        camp.roads.addAll(network.values());
        Collections.sort(camp.roads, new Comparator<Road>() {
            @Override public int compare(Road a, Road b) { int order = Integer.compare(a.x, b.x); return order == 0 ? Integer.compare(a.z, b.z) : order; }
        });
        return true;
    }

    private static void put(GardenerNests.PlacementPlan plan, Camp camp, Piece piece, int x, int y, int z, Block block, int meta) {
        if(block == Blocks.air) meta = 0;
        if(block == Blocks.log && (piece.rotation & 1) != 0 && ((meta & 12) == 4 || (meta & 12) == 8)) meta ^= 12;
        plan.put(camp.x + piece.tx(x, z), piece.y + y, camp.z + piece.tz(x, z), block, meta);
    }
    private static void build(GardenerNests.PlacementPlan plan, Camp camp, Piece piece) {
        if(piece.kind == CENTER) {
            primary(plan, camp, piece);
            // A tall assimilated marker is offset from the Wretch's body and open gathering aisle.
            for(int y = 0; y < 14; y++) put(plan, camp, piece, -8, y, -8, y % 3 == 0 ? ModBlocks.gardener_flesh : Blocks.log, 0);
            for(int x = -8; x <= -5; x++) for(int y = 11; y <= 13; y++) put(plan, camp, piece, x, y, -8, Blocks.wool, 12);
        } else if(piece.kind == SHELTER || piece.kind == HALL) {
            int length = piece.kind == HALL ? 5 : 4, width = piece.kind == HALL ? 5 : 4;
            for(int z = -length; z <= length; z++) for(int x = -width; x <= width; x++) {
                put(plan, camp, piece, x, -1, z, (x + z) % 5 == 0 ? Blocks.gravel : Blocks.planks, 1);
                int roof = 3 + (width - Math.abs(x)) / 2;
                put(plan, camp, piece, x, roof, z, Blocks.wool, 12);
                if(Math.abs(x) == width && (z + length) % 3 == 0) for(int y = 0; y < roof; y++) put(plan, camp, piece, x, y, z, Blocks.fence, 0);
            }
            for(int x : new int[] {-3, 3}) for(int z = -2; z <= 1; z++) put(plan, camp, piece, x, 0, z, Blocks.log, 4);
            put(plan, camp, piece, -width + 1, 0, -length + 1, Blocks.web, 0);
        } else if(piece.kind == HOUSE) {
            for(int x = -5; x <= 5; x++) for(int z = -5; z <= 5; z++) {
                put(plan, camp, piece, x, -1, z, (x * 3 + z) % 7 == 0 ? ModBlocks.gardener_flesh : Blocks.planks, 1);
                for(int y = 0; y < 4; y++) if(Math.abs(x) == 5 || Math.abs(z) == 5) {
                    boolean doorway = z == 5 && Math.abs(x) <= 1 && y < 3;
                    boolean broken = z == -5 && x >= 1 && x <= 3 && y >= 1;
                    put(plan, camp, piece, x, y, z, doorway || broken ? Blocks.air : y == 2 && (x + z) % 4 == 0 ? Blocks.web : Blocks.planks, 1);
                }
                if(x < 3 || z < 1) put(plan, camp, piece, x, 4 + (5 - Math.abs(x)) / 3, z, Blocks.wool, 12);
            }
            for(int z = -3; z <= 0; z++) {
                put(plan, camp, piece, -3, 0, z, ModBlocks.gardener_flesh, 0);
                put(plan, camp, piece, -3, 1, z, ModBlocks.mockery_growth, 0);
            }
        } else if(piece.kind == ORGANIC) {
            for(int x = -5; x <= 5; x++) for(int z = -5; z <= 5; z++) if(x * x + z * z <= 29) {
                put(plan, camp, piece, x, -1, z, ModBlocks.gardener_flesh, 0);
                if((x * 3 + z * 5) % 7 == 0 && Math.abs(x) > 1) put(plan, camp, piece, x, 0, z, ModBlocks.mockery_growth, 0);
            }
            for(int side : new int[] {-1, 1}) for(int z = -4; z <= 2; z += 3)
                for(int y = 0; y <= 5; y++) put(plan, camp, piece, side * (5 - y / 3), y, z, ModBlocks.gardener_flesh, 0);
        } else if(piece.kind == BURROW) {
            int depth = Math.min(7, piece.y - 1);
            for(int z = 7; z >= -7; z--) {
                int floor = -(7 - z) * depth / 14 - 1;
                for(int x = -3; x <= 3; x++) for(int y = floor; y <= floor + 7; y++) {
                    boolean lining = y == floor || y == floor + 7 || Math.abs(x) == 3 || z == -7;
                    put(plan, camp, piece, x, y, z, lining ? ((x + z + y) % 5 == 0 ? ModBlocks.gardener_flesh : Blocks.stone) : Blocks.air, 0);
                }
            }
            // The last bay is a sealed underground alcove, not a Gardener nest or spawn producer.
        } else if(piece.kind == LOOKOUT) {
            for(int x : new int[] {-2, 2}) for(int z : new int[] {-2, 2}) for(int y = 0; y <= 8; y++) put(plan, camp, piece, x, y, z, Blocks.log, 0);
            for(int x = -3; x <= 3; x++) for(int z = -3; z <= 3; z++) {
                put(plan, camp, piece, x, 8, z, Blocks.planks, 1);
                if(Math.abs(x) == 3 || Math.abs(z) == 3) put(plan, camp, piece, x, 9, z, Blocks.fence, 0);
            }
            for(int y = 0; y <= 8; y++) put(plan, camp, piece, -2, y, 1, Blocks.ladder, ladderMeta(piece.rotation));
            for(int y = 9; y <= 13; y++) put(plan, camp, piece, 2, y, -2, Blocks.fence, 0);
            for(int x = 0; x <= 2; x++) put(plan, camp, piece, x, 12, -2, Blocks.wool, 12);
        } else {
            for(int x = -2; x <= 2; x++) for(int z = -2; z <= 1; z++) {
                if(Math.abs(x) == 2 && z == -2) for(int y = 0; y < 3; y++) put(plan, camp, piece, x, y, z, Blocks.fence, 0);
                put(plan, camp, piece, x, 3, z, Blocks.wool, 12);
            }
            put(plan, camp, piece, -1, 0, -1, Blocks.hay_block, 0);
            put(plan, camp, piece, 1, 0, -1, Blocks.log, 4);
            put(plan, camp, piece, 2, 0, 1, ModBlocks.gardener_flesh, 0);
        }
    }

    private static int ladderMeta(int rotation) { return new int[] {2, 5, 3, 4}[rotation]; }

    /** Original central camp design, also used when explicitly regenerating legacy small camps. */
    static void primary(GardenerNests.PlacementPlan plan, Camp camp, Piece piece) {
        for(int side : new int[] {-1, 1}) {
            for(int dz = -4; dz <= -1; dz++) {
                put(plan, camp, piece, side * 4, 2, dz, Blocks.wool, 12);
                if(dz != -2) put(plan, camp, piece, side * 5, 1, dz, Blocks.wool, 12);
                if(dz != -3) put(plan, camp, piece, side * 3, 1, dz, Blocks.wool, 12);
            }
            put(plan, camp, piece, side * 4, 0, -4, Blocks.planks, 1);
            put(plan, camp, piece, side * 4, 0, -1, Blocks.fence, 0);
            put(plan, camp, piece, side * 4, 0, 4, Blocks.log, 4);
            put(plan, camp, piece, side * 3, 0, 4, Blocks.log, 4);
            put(plan, camp, piece, side * 5, 0, -5, Blocks.leaves, 4);
            put(plan, camp, piece, side * 6, 1, -4, Blocks.leaves, 4);
        }
        for(int dx = -1; dx <= 1; dx++) for(int dz = 2; dz <= 4; dz++)
            put(plan, camp, piece, dx, 0, dz, dx == 0 && dz == 3 ? Blocks.coal_block : Blocks.cobblestone, 0);
        put(plan, camp, piece, -6, 1, -1, Blocks.web, 0);
        put(plan, camp, piece, -2, -1, 5, Blocks.gravel, 0); put(plan, camp, piece, 1, -1, 4, Blocks.gravel, 0);
        put(plan, camp, piece, -3, 0, 5, Blocks.log, 8); put(plan, camp, piece, -3, 0, 6, Blocks.log, 8);
    }

    private static void perimeter(GardenerNests.PlacementPlan plan, Camp camp, Terrain terrain, Map<Long, Road> roads) {
        int half = camp.size / 2, steps = camp.size * 4;
        if(camp.boundary.isEmpty()) for(int i = 0; i < steps; i++) {
            double angle = i * Math.PI * 2 / steps;
            // Wide, permanent openings at the cardinal approaches and irregular breaks elsewhere.
            if(Math.abs(Math.sin(angle * 2)) < 0.20D || Math.floorMod(i + (int) camp.seed, 23) < 4) continue;
            double radius = half - 4 + Math.sin(angle * 5 + (camp.seed & 15)) * 2;
            int x = (int) Math.round(Math.cos(angle) * radius), z = (int) Math.round(Math.sin(angle) * radius);
            if(!inside(camp, x, z) || blocked(camp, x, z, 3) || roads.containsKey(key(x, z))) continue;
            int y = terrain.height(x, z), height = i % 7 == 0 ? 4 : 2;
            camp.boundary.add(new Road(x, y, z, height));
        }
        for(Road post : camp.boundary) {
            prepare(plan, terrain, post.x, post.z, post.y, post.width + 1, Blocks.gravel);
            for(int dy = 0; dy < post.width; dy++) plan.put(camp.x + post.x, post.y + dy, camp.z + post.z, post.width == 4 ? Blocks.log : Blocks.fence, 0);
        }
    }
}
