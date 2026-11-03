package com.hbm.world.sandworm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** One authoritative saved record. Door bits are north/east/south/west in world orientation. */
public final class WormEncounter {
    public UUID id;
    public int slot, sourceDimension, rows, buildCursor, cleanupCursor;
    public long roomSpawns;
    public double sourceX, sourceY, sourceZ;
    public int[] doors, rotations;
    public final java.util.Set<UUID> sparks = new java.util.HashSet<UUID>();
    public boolean brainDead, heartDead, canceled, cleaned, exteriorRetired;
    public long lastActive, completedAt;
    public transient int brainMissing, heartMissing;
    public boolean complete() { return brainDead && heartDead; }
    public boolean retired() { return complete() || canceled; }
    public boolean ready() { return !cleaned && buildCursor >= volume(); }
    public int volume() { return rows * 3 * WormSchematic.VOLUME; }
    public int x() { return (slot % 1024) * 256; }
    public int z() { return (slot / 1024) * 512; }
    public int brainRoom() { return rows * 3 - 1; }
    public int heartRoom() { return (rows / 2) * 3 + 2; }
    public int roomX(int r) { return x() + (r % 3) * WormSchematic.SIZE; }
    public int roomZ(int r) { return z() + (r / 3) * WormSchematic.SIZE; }
    public boolean contains(double px, double pz) { return px >= x() && px < x() + 63 && pz >= z() && pz < z() + rows * 21; }
    public UUID spawnId(String role) { return UUID.nameUUIDFromBytes(("rntSandworm:" + id + ":" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public void generateLayout() {
        int count = rows * 3;
        doors = new int[count]; rotations = new int[count];
        Random random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        boolean[] visited = new boolean[count];
        List<Integer> stack = new ArrayList<Integer>(); stack.add(0); visited[0] = true;
        // A finite depth-first spanning tree guarantees access to both organs, unlike retry-based layouts.
        while(!stack.isEmpty()) {
            int a = stack.get(stack.size() - 1), choices = 0;
            int[] directions = new int[4];
            for(int d = 0; d < 4; d++) { int b = neighbor(a, d); if(b >= 0 && !visited[b]) directions[choices++] = d; }
            if(choices == 0) { stack.remove(stack.size() - 1); continue; }
            int d = directions[random.nextInt(choices)], b = neighbor(a, d);
            connect(a, b, d); visited[b] = true; stack.add(b);
        }
        for(int a = 0; a < count; a++) {
            rotations[a] = random.nextInt(4);
            for(int d = 1; d <= 2; d++) { int b = neighbor(a, d); if(b >= 0 && random.nextInt(4) == 0) connect(a, b, d); }
        }
    }
    private int neighbor(int a, int d) {
        int x = a % 3, z = a / 3;
        if(d == 0) z--; else if(d == 1) x++; else if(d == 2) z++; else x--;
        return x < 0 || x >= 3 || z < 0 || z >= rows ? -1 : z * 3 + x;
    }
    private void connect(int a, int b, int direction) { doors[a] |= 1 << direction; doors[b] |= 1 << ((direction + 2) % 4); }
    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("id", id.toString()); n.setInteger("slot", slot); n.setInteger("sourceDimension", sourceDimension);
        n.setDouble("sourceX", sourceX); n.setDouble("sourceY", sourceY); n.setDouble("sourceZ", sourceZ);
        n.setInteger("rows", rows); n.setIntArray("doors", doors); n.setIntArray("rotations", rotations);
        net.minecraft.nbt.NBTTagList population = new net.minecraft.nbt.NBTTagList();
        for(UUID spark : sparks) population.appendTag(new net.minecraft.nbt.NBTTagString(spark.toString()));
        n.setTag("sparks", population);
        n.setInteger("build", buildCursor); n.setInteger("cleanup", cleanupCursor); n.setLong("spawns", roomSpawns);
        n.setBoolean("brainDead", brainDead); n.setBoolean("heartDead", heartDead); n.setBoolean("canceled", canceled);
        n.setBoolean("cleaned", cleaned); n.setBoolean("exteriorRetired", exteriorRetired);
        n.setLong("lastActive", lastActive); n.setLong("completedAt", completedAt); return n;
    }
    public static WormEncounter read(NBTTagCompound n) {
        WormEncounter e = new WormEncounter();
        e.id = UUID.fromString(n.getString("id")); e.slot = n.getInteger("slot");
        e.sourceDimension = n.getInteger("sourceDimension"); e.sourceX = n.getDouble("sourceX");
        e.sourceY = n.getDouble("sourceY"); e.sourceZ = n.getDouble("sourceZ");
        e.rows = n.getInteger("rows"); e.doors = n.getIntArray("doors"); e.rotations = n.getIntArray("rotations");
        net.minecraft.nbt.NBTTagList population = n.getTagList("sparks", 8);
        for(int i = 0; i < population.tagCount(); i++) e.sparks.add(UUID.fromString(population.getStringTagAt(i)));
        if(e.slot < 0 || e.slot >= 1000000 || e.rows < 3 || e.rows > 12 || e.doors.length != e.rows * 3 || e.rotations.length != e.doors.length)
            throw new IllegalStateException("Invalid saved Sandworm layout " + e.id);
        e.buildCursor = Math.max(0, Math.min(e.volume(), n.getInteger("build")));
        e.cleanupCursor = Math.max(0, Math.min(e.volume(), n.getInteger("cleanup")));
        e.roomSpawns = n.getLong("spawns"); e.brainDead = n.getBoolean("brainDead"); e.heartDead = n.getBoolean("heartDead");
        e.canceled = n.getBoolean("canceled"); e.cleaned = n.getBoolean("cleaned"); e.exteriorRetired = n.getBoolean("exteriorRetired");
        e.lastActive = n.getLong("lastActive"); e.completedAt = n.getLong("completedAt"); return e;
    }
}
