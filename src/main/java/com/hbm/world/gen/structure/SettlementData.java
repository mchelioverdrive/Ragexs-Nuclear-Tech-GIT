package com.hbm.world.gen.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementPlan.Column;
import com.hbm.world.gen.structure.SettlementPlan.State;
import com.hbm.world.gen.structure.SettlementRoads.Profile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPressurePlate;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockCarpet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/** Per-dimension footprint index and unfinished work. Cursors are saved with the affected chunks. */
public final class SettlementData extends WorldSavedData {
    private static final String NAME = "RNTMSettlements";
    private final Map<Long, Site> sites = new LinkedHashMap<>();
    private final Map<Long, Site> active = new LinkedHashMap<>();
    private final ArrayDeque<Placement> requests = new ArrayDeque<>();
    private final Map<Long, List<Slice>> byChunk = new HashMap<>();
    private final Map<Long, List<Site>> byRegion = new HashMap<>();
    private long nextId = 1;

    public SettlementData(String name) { super(name); }

    static SettlementData get(World world) {
        SettlementData data = (SettlementData) world.perWorldStorage.loadData(SettlementData.class, NAME);
        if(data == null) {
            data = new SettlementData(NAME);
            world.perWorldStorage.setData(NAME, data);
        }
        return data;
    }

    boolean busy() {
        int pending = requests.size();
        for(Site site : active.values()) if(!site.placed) pending++;
        return pending >= 8;
    }

    boolean queued(Placement request) {
        for(Placement pending : requests) if(request.samePlacement(pending)) return true;
        for(Site site : active.values()) if(!site.placed && request.samePlacement(site.placement)) return true;
        return false;
    }

    Placement request() { return requests.peekFirst(); }

    void enqueue(Placement request) {
        requests.addLast(request);
        markDirty();
    }

    void finishRequest() {
        requests.removeFirst();
        markDirty();
    }

    Site nextPlacement() {
        for(Site site : active.values()) if(!site.placed) return site;
        return null;
    }

    Site site(long id) { return sites.get(id); }

    List<Slice> at(long key) { return byChunk.getOrDefault(key, Collections.emptyList()); }

    boolean overlaps(Site proposal) {
        for(Map.Entry<Long, Slice> entry : proposal.slices.entrySet()) {
            for(Slice existing : at(entry.getKey())) {
                if(existing.footprint.intersects(entry.getValue().footprint)) return true;
            }
        }
        return false;
    }

    boolean tooClose(StructureBoundingBox bounds, int gap) {
        for(int rx = (bounds.minX - gap) >> 7; rx <= (bounds.maxX + gap) >> 7; rx++) {
            for(int rz = (bounds.minZ - gap) >> 7; rz <= (bounds.maxZ + gap) >> 7; rz++) {
                List<Site> region = byRegion.get(ChunkCoordIntPair.chunkXZ2Int(rx, rz));
                if(region == null) continue;
                for(Site site : region) {
                    StructureBoundingBox box = site.bounds;
                    if(box.maxX + gap >= bounds.minX && box.minX - gap <= bounds.maxX
                            && box.maxZ + gap >= bounds.minZ && box.minZ - gap <= bounds.maxZ) return true;
                }
            }
        }
        return false;
    }

    void accept(Site site) {
        site.id = nextId++;
        for(Slice slice : site.slices.values()) {
            slice.prepare();
            if(slice.pending()) site.remaining++;
            site.totalWork += slice.size();
        }
        index(site);
        markDirty();
    }

    void saved(Slice slice, int completed, int unchanged) {
        if(!slice.pending()) return;
        slice.site.savedWork += slice.size();
        slice.site.savedCompleted += completed;
        slice.site.savedUnchanged += unchanged;
        slice.clearWork();
        if(--slice.site.remaining == 0) active.remove(slice.site.id);
        markDirty();
    }

    void fail(Site site) {
        sites.remove(site.id);
        active.remove(site.id);
        for(Slice slice : site.slices.values()) removeFrom(byChunk, slice.key(), slice);
        for(int x = site.bounds.minX >> 7; x <= site.bounds.maxX >> 7; x++) {
            for(int z = site.bounds.minZ >> 7; z <= site.bounds.maxZ >> 7; z++)
                removeFrom(byRegion, ChunkCoordIntPair.chunkXZ2Int(x, z), site);
        }
        markDirty();
    }

    private static <T> void removeFrom(Map<Long, List<T>> index, long key, T value) {
        List<T> entries = index.get(key);
        entries.remove(value);
        if(entries.isEmpty()) index.remove(key);
    }

    private void index(Site site) {
        sites.put(site.id, site);
        if(site.remaining > 0) active.put(site.id, site);
        for(Slice slice : site.slices.values()) byChunk.computeIfAbsent(slice.key(), k -> new ArrayList<>()).add(slice);
        for(int x = site.bounds.minX >> 7; x <= site.bounds.maxX >> 7; x++) {
            for(int z = site.bounds.minZ >> 7; z <= site.bounds.maxZ >> 7; z++)
                byRegion.computeIfAbsent(ChunkCoordIntPair.chunkXZ2Int(x, z), k -> new ArrayList<>()).add(site);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        sites.clear();
        byChunk.clear();
        byRegion.clear();
        active.clear();
        requests.clear();
        nextId = Math.max(1, root.getLong("NextId"));
        NBTTagList list = root.getTagList("Sites", 10);
        for(int i = 0; i < list.tagCount(); i++) {
            Site site = new Site(list.getCompoundTagAt(i));
            if(site.slices.isEmpty()) continue;
            index(site);
            nextId = Math.max(nextId, site.id + 1);
        }
        NBTTagList queued = root.getTagList("Requests", 10);
        for(int i = 0; i < queued.tagCount(); i++) requests.addLast(new Placement(queued.getCompoundTagAt(i)));
    }

    @Override
    public void writeToNBT(NBTTagCompound root) {
        root.setLong("NextId", nextId);
        NBTTagList list = new NBTTagList();
        for(Site site : sites.values()) list.appendTag(site.write());
        root.setTag("Sites", list);
        NBTTagList queued = new NBTTagList();
        for(Placement placement : requests) queued.appendTag(placement.write());
        root.setTag("Requests", queued);
    }

    static final class Placement {
        final SettlementKind kind;
        final int variant, x, y, z, attempt;
        final Rotation rotation;
        final Profile profile;
        final UUID owner;

        Placement(SettlementKind kind, int variant, Rotation rotation, Profile profile, int x, int y, int z, UUID owner, int attempt) {
            this.kind = kind;
            this.variant = variant;
            this.rotation = rotation;
            this.profile = profile;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.attempt = attempt;
        }

        Placement(NBTTagCompound tag) {
            this(SettlementKind.valueOf(tag.getString("Kind")), tag.getInteger("Variant"), Rotation.valueOf(tag.getString("Rotation")),
                    tag.hasKey("Profile") ? Profile.valueOf(tag.getString("Profile")) : null,
                    tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"),
                    tag.hasKey("Owner") ? UUID.fromString(tag.getString("Owner")) : null, tag.getInteger("Attempt"));
        }

        boolean samePlacement(Placement other) {
            return other != null && kind == other.kind && variant == other.variant && rotation == other.rotation
                    && profile == other.profile && x == other.x && y == other.y && z == other.z;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Kind", kind.name());
            tag.setInteger("Variant", variant);
            tag.setString("Rotation", rotation.name());
            if(profile != null) tag.setString("Profile", profile.name());
            if(owner != null) tag.setString("Owner", owner.toString());
            tag.setInteger("X", x);
            tag.setInteger("Y", y);
            tag.setInteger("Z", z);
            tag.setInteger("Attempt", attempt);
            return tag;
        }

        SettlementPlan build(long worldSeed) {
            if(kind == SettlementKind.ROAD) return SettlementRoads.component(x, y, z, rotation, profile, variant);
            SettlementPlan plan = new SettlementPlan();
            long seed = worldSeed ^ (long) x * 341873128712L ^ (long) z * 132897987541L ^ y;
            SettlementBuildings.build(plan, kind, variant, x, y, z, rotation, seed);
            return plan;
        }

        String description() {
            return (owner == null ? "natural " : "forced ") + kind.variantName(variant) + " at " + x + "," + y + "," + z
                    + " rotation=" + rotation + (profile == null ? "" : " road=" + profile) + " attempt=" + attempt;
        }
    }

    static final class Site {
        final Map<Long, Slice> slices = new LinkedHashMap<>();
        final StructureBoundingBox bounds;
        final boolean natural;
        final Placement placement;
        long id;
        int remaining, savedWork, savedCompleted, savedUnchanged, totalWork;
        boolean placed;

        Site(SettlementPlan plan, Placement placement) {
            this.placement = placement;
            natural = placement.owner == null;
            bounds = StructureBoundingBox.getNewBoundingBox();
            for(Column column : plan.columns.values()) {
                int x = column.x >> 4, z = column.z >> 4;
                long key = ChunkCoordIntPair.chunkXZ2Int(x, z);
                slices.computeIfAbsent(key, k -> new Slice(this, x, z)).footprint.set(columnIndex(column.x, column.z));
                bounds.minX = Math.min(bounds.minX, column.x);
                bounds.maxX = Math.max(bounds.maxX, column.x);
                bounds.minZ = Math.min(bounds.minZ, column.z);
                bounds.maxZ = Math.max(bounds.maxZ, column.z);
                bounds.minY = Math.min(bounds.minY, column.y + column.bottom);
                bounds.maxY = Math.max(bounds.maxY, column.y + column.top);
            }
            includeBoundsChunks();
        }

        private void includeBoundsChunks() {
            for(int x = bounds.minX >> 4; x <= bounds.maxX >> 4; x++) {
                for(int z = bounds.minZ >> 4; z <= bounds.maxZ >> 4; z++) {
                    int cx = x, cz = z;
                    slices.computeIfAbsent(ChunkCoordIntPair.chunkXZ2Int(x, z), k -> new Slice(this, cx, cz));
                }
            }
        }

        Site(NBTTagCompound tag) {
            id = tag.getLong("Id");
            natural = tag.getBoolean("Natural");
            placement = tag.hasKey("Request") ? new Placement(tag.getCompoundTag("Request")) : null;
            savedWork = tag.getInteger("SavedWork");
            savedCompleted = tag.getInteger("SavedCompleted");
            savedUnchanged = tag.getInteger("SavedUnchanged");
            totalWork = savedWork;
            bounds = new StructureBoundingBox(tag.getIntArray("Bounds"));
            NBTTagList list = tag.getTagList("Chunks", 10);
            for(int i = 0; i < list.tagCount(); i++) {
                Slice slice = new Slice(this, list.getCompoundTagAt(i));
                slices.put(slice.key(), slice);
                if(slice.pending()) remaining++;
                totalWork += slice.size();
            }
            if(!slices.isEmpty()) includeBoundsChunks();
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Id", id);
            tag.setBoolean("Natural", natural);
            if(placement != null) tag.setTag("Request", placement.write());
            tag.setInteger("SavedWork", savedWork);
            tag.setInteger("SavedCompleted", savedCompleted);
            tag.setInteger("SavedUnchanged", savedUnchanged);
            tag.setIntArray("Bounds", new int[] { bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ });
            NBTTagList list = new NBTTagList();
            for(Slice slice : slices.values()) list.appendTag(slice.write());
            tag.setTag("Chunks", list);
            return tag;
        }
    }

    static int columnIndex(int x, int z) { return (z & 15) << 4 | x & 15; }

    enum Phase { BASE, ATTACHMENTS, VERIFY, ANIMALS }

    static final class Slice {
        final Site site;
        final int x, z;
        final BitSet footprint;
        private List<State> palette = new ArrayList<>();
        private Map<Integer, Integer> paletteIndex = new HashMap<>();
        private List<Operation> operations = new ArrayList<>();
        private int base, blocks;
        String unavailable;

        Slice(Site site, int x, int z) {
            this.site = site;
            this.x = x;
            this.z = z;
            footprint = new BitSet(256);
        }

        Slice(Site site, NBTTagCompound tag) {
            this.site = site;
            x = tag.getInteger("X");
            z = tag.getInteger("Z");
            footprint = BitSet.valueOf(tag.getByteArray("Footprint"));
            unavailable = tag.hasKey("Unavailable") ? tag.getString("Unavailable") : null;
            NBTTagList states = tag.getTagList("Palette", 10);
            for(int i = 0; i < states.tagCount(); i++) {
                NBTTagCompound entry = states.getCompoundTagAt(i);
                String name = entry.getString("Block");
                Block block = Block.blockRegistry.containsKey(name) ? (Block) Block.blockRegistry.getObject(name) : null;
                palette.add(new State(block, entry.getByte("Meta")));
            }
            int[] steps = tag.getIntArray("Placement");
            for(int i = 0; i + 2 < steps.length; i += 3) operations.add(new Operation(steps[i], steps[i + 1], steps[i + 2], null, null));
            NBTTagList extras = tag.getTagList("Details", 10);
            for(int i = 0; i < extras.tagCount(); i++) {
                NBTTagCompound entry = extras.getCompoundTagAt(i);
                int step = entry.getInteger("Step");
                if(step < 0 || step >= operations.size()) continue;
                Operation op = operations.get(step);
                if(entry.hasKey("LegacyStep")) op.legacyStep = entry.getInteger("LegacyStep");
                if(entry.hasKey("Animal")) op.animal = entry.getString("Animal");
                if(entry.hasKey("Line0")) {
                    op.sign = new String[4];
                    for(int line = 0; line < 4; line++) op.sign[line] = entry.getString("Line" + line);
                }
            }
            if(!tag.getBoolean("Phased")) {
                for(int i = 0; i < operations.size(); i++) {
                    if(operations.get(i).animal != null) operations.get(i).legacyStep = i;
                }
            }
            if(tag.hasKey("BaseEnd")) {
                base = tag.getInteger("BaseEnd");
                blocks = tag.getInteger("BlockEnd");
                paletteIndex = null;
            } else prepare();
        }

        long key() { return ChunkCoordIntPair.chunkXZ2Int(x, z); }
        boolean pending() { return operations != null && !operations.isEmpty(); }
        int size() { return operations == null ? 0 : operations.size(); }
        Operation at(int cursor, Phase phase) { return operations.get(phase.ordinal() >= Phase.VERIFY.ordinal() ? cursor - blocks : cursor); }
        State state(int index) { return palette.get(index); }

        int limit(Phase phase) {
            if(!pending()) return 0;
            switch(phase) {
            case BASE: return base;
            case ATTACHMENTS: return blocks;
            case VERIFY: return blocks * 2;
            default: return blocks + size();
            }
        }

        void add(int column, int y, Block previous, int meta, State target, String animal) {
            operations.add(new Operation(y << 8 | column, palette(previous, meta), palette(target.block, target.meta), target.sign, animal));
        }

        private int palette(Block block, int meta) {
            int key = Block.getIdFromBlock(block) << 4 | meta;
            Integer found = paletteIndex.get(key);
            if(found != null) return found;
            int index = palette.size();
            paletteIndex.put(key, index);
            palette.add(new State(block, meta));
            return index;
        }

        void prepare() {
            if(operations.isEmpty()) clearWork();
            else {
                operations.sort(Comparator.comparingInt(this::order)
                        .thenComparingInt(op -> op.position));
                for(Operation op : operations) {
                    int order = order(op);
                    if(order == 0) base++;
                    if(order < 2) blocks++;
                }
                paletteIndex = null;
            }
        }

        private int order(Operation op) {
            if(op.animal != null) return 2;
            Block block = state(op.after).block;
            return block instanceof BlockBush || block instanceof BlockBed || block instanceof BlockDoor
                    || block instanceof BlockLadder || block instanceof BlockSign || block instanceof BlockButton
                    || block instanceof BlockPressurePlate || block instanceof BlockCarpet ? 1 : 0;
        }

        void clearWork() {
            operations = null;
            palette = null;
            paletteIndex = null;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", x);
            tag.setInteger("Z", z);
            tag.setByteArray("Footprint", footprint.toByteArray());
            if(unavailable != null) tag.setString("Unavailable", unavailable);
            if(!pending()) return tag;
            tag.setBoolean("Phased", true);
            tag.setInteger("BaseEnd", base);
            tag.setInteger("BlockEnd", blocks);
            NBTTagList states = new NBTTagList(), extras = new NBTTagList();
            for(State state : palette) {
                NBTTagCompound entry = new NBTTagCompound();
                if(state.block != null) entry.setString("Block", Block.blockRegistry.getNameForObject(state.block));
                entry.setByte("Meta", (byte) state.meta);
                states.appendTag(entry);
            }
            int[] steps = new int[operations.size() * 3];
            for(int i = 0; i < operations.size(); i++) {
                Operation op = operations.get(i);
                steps[i * 3] = op.position;
                steps[i * 3 + 1] = op.before;
                steps[i * 3 + 2] = op.after;
                if(op.sign == null && op.animal == null) continue;
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger("Step", i);
                if(op.animal != null) entry.setString("Animal", op.animal);
                if(op.legacyStep >= 0) entry.setInteger("LegacyStep", op.legacyStep);
                if(op.sign != null) for(int line = 0; line < op.sign.length; line++) entry.setString("Line" + line, op.sign[line]);
                extras.appendTag(entry);
            }
            tag.setTag("Palette", states);
            tag.setIntArray("Placement", steps);
            tag.setTag("Details", extras);
            return tag;
        }
    }

    static final class Operation {
        final int position, before, after;
        String[] sign;
        String animal;
        int legacyStep = -1;

        Operation(int position, int before, int after, String[] sign, String animal) {
            this.position = position;
            this.before = before;
            this.after = after;
            this.sign = sign;
            this.animal = animal;
        }
    }
}
