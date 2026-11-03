package com.hbm.world.sandworm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.SandwormConfig;
import com.hbm.entity.mob.sandworm.EntityBrain;
import com.hbm.entity.mob.sandworm.EntityHeart;
import com.hbm.entity.mob.sandworm.EntitySandworm;
import com.hbm.entity.mob.sandworm.EntitySpark;
import com.hbm.entity.mob.sandworm.EntityTick;
import com.hbm.entity.mob.sandworm.EntityWormMonster;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** Overworld-owned ledger, including offline return dependencies and unloaded exterior ownership. */
public final class WormEncounters extends WorldSavedData {
    private static final String NAME = "rntSandwormEncounters";
    private final Map<UUID, WormEncounter> records = new LinkedHashMap<UUID, WormEncounter>();
    private final Map<Integer, WormEncounter> slots = new HashMap<Integer, WormEncounter>();
    private final Map<UUID, Return> players = new HashMap<UUID, Return>();
    private final Map<UUID, Long> reentryAfter = new HashMap<UUID, Long>();
    private final Map<UUID, Entity> loaded = new HashMap<UUID, Entity>();
    private final Map<UUID, Boolean> transfers = new LinkedHashMap<UUID, Boolean>();
    private int nextSlot, turn;
    private int savedDimension = SandwormConfig.dimension;
    public WormEncounters() { super(NAME); }
    public WormEncounters(String name) { super(name); }
    public static WormEncounters get(World world) {
        World root = DimensionManager.getWorld(0);
        if(root == null || world.isRemote) throw new IllegalStateException("Sandworm ledger is server overworld-owned");
        WormEncounters d = (WormEncounters)root.mapStorage.loadData(WormEncounters.class, NAME);
        if(d == null) { d = new WormEncounters(); root.mapStorage.setData(NAME, d); }
        return d;
    }
    public Collection<WormEncounter> entries() { return java.util.Collections.unmodifiableCollection(records.values()); }
    public WormEncounter find(UUID id) { return records.get(id); }
    public boolean retiringAt(int x, int z) {
        if(x < 0 || z < 0 || x >= 1024 * 256) return false;
        WormEncounter e = slots.get(x / 256 + z / 512 * 1024);
        return e != null && e.contains(x, z) && e.retired();
    }
    public Entity loaded(UUID id) {
        Entity e = loaded.get(id);
        return e != null && !e.isDead && e.addedToChunk ? e : null;
    }
    public void untrack(Entity entity) { if(loaded.get(entity.getUniqueID()) == entity) loaded.remove(entity.getUniqueID()); }
    public boolean track(Entity entity) {
        Entity existing = loaded(entity.getUniqueID());
        if(existing != null && existing != entity) return false;
        if(entity instanceof EntitySpark) {
            WormEncounter e = find(((EntitySpark)entity).encounter);
            if(e != null && !e.sparks.contains(entity.getUniqueID())) {
                // Count saved unloaded Sparks too, so wandering waves cannot accumulate without bound.
                if(e.sparks.size() >= SandwormConfig.sparkLimit + e.rows * 3) return false;
                e.sparks.add(entity.getUniqueID()); markDirty();
            }
        }
        loaded.put(entity.getUniqueID(), entity); return true;
    }
    public boolean room() {
        int active = 0;
        for(WormEncounter e : records.values()) if(!e.retired()) active++;
        return active < SandwormConfig.maxEncounters && nextSlot < 1000000;
    }
    public WormEncounter admit(EntitySandworm worm) {
        WormEncounter e = find(worm.getUniqueID());
        if(e == null) {
            if(worm.registered) return null;
            if(!room() || worm.dimension == SandwormConfig.dimension) return null;
            e = new WormEncounter(); e.id = worm.getUniqueID(); e.slot = nextSlot++;
            e.sourceDimension = worm.dimension; e.sourceX = worm.posX; e.sourceY = worm.posY; e.sourceZ = worm.posZ;
            e.rows = SandwormConfig.rows; e.lastActive = time(); e.generateLayout(); records.put(e.id, e); slots.put(e.slot, e); markDirty();
        }
        if(!track(worm)) return null;
        worm.registered = true;
        return e;
    }
    public long time() { return DimensionManager.getWorld(0).getTotalWorldTime(); }
    private boolean occupied(WormEncounter e) {
        for(Return r : players.values()) if(r.inside && e.id.equals(r.encounter)) return true;
        WorldServer interior = DimensionManager.getWorld(SandwormConfig.dimension);
        if(interior != null) for(Object o : interior.playerEntities) {
            EntityPlayer p = (EntityPlayer)o;
            if(e.contains(p.posX, p.posZ)) return true;
        }
        return false;
    }
    public void organDied(EntityWormMonster organ) {
        WormEncounter e = find(organ.encounter);
        if(e != null && organ instanceof EntitySpark && e.sparks.remove(organ.getUniqueID())) markDirty();
        if(e == null || e.retired()) return;
        if(organ instanceof EntityBrain && e.spawnId("brain").equals(organ.getUniqueID())) e.brainDead = true;
        if(organ instanceof EntityHeart && e.spawnId("heart").equals(organ.getUniqueID())) e.heartDead = true;
        if(e.complete()) {
            e.completedAt = time();
            for(Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
                EntityPlayerMP p = (EntityPlayerMP)o;
                Return r = players.get(p.getUniqueID());
                if(r != null && e.id.equals(r.encounter)) p.addChatMessage(new ChatComponentTranslation("sandworm.complete"));
            }
        }
        markDirty();
    }
    public boolean swallow(EntityPlayerMP p, WormEncounter e) {
        Long grace = reentryAfter.get(p.getUniqueID());
        if(grace != null && time() < grace) return false;
        if(!e.ready() || e.retired() || p.dimension == SandwormConfig.dimension || !p.isEntityAlive() || p.ridingEntity != null
                || (!SandwormConfig.swallowCreative && p.capabilities.isCreativeMode)) return false;
        Return r = players.get(p.getUniqueID());
        if(r == null || !e.id.equals(r.encounter) || !r.inside) {
            r = new Return(); r.encounter = e.id; r.dimension = p.dimension;
            r.x = p.posX; r.y = p.posY; r.z = p.posZ; players.put(p.getUniqueID(), r);
        }
        e.lastActive = time(); transfers.put(p.getUniqueID(), true); markDirty(); return true;
    }
    public void requestExit(EntityPlayer p, double x, double z) {
        Return r = players.get(p.getUniqueID()); WormEncounter e = r == null ? null : find(r.encounter);
        if(e == null || p.dimension != SandwormConfig.dimension || !e.contains(x, z)) return;
        if(e.complete() || e.canceled) transfers.put(p.getUniqueID(), false);
        else if(p.ticksExisted % 40 == 0) p.addChatMessage(new ChatComponentTranslation("sandworm.exitLocked"));
    }
    public void recover(EntityPlayer p) {
        Return r = players.get(p.getUniqueID());
        if(r != null && ((r.inside && p.dimension != SandwormConfig.dimension) || find(r.encounter) == null)) transfers.put(p.getUniqueID(), false);
        else if(p.dimension == SandwormConfig.dimension && r == null) transfers.put(p.getUniqueID(), false);
    }
    public int returnDimension(EntityPlayer p) {
        Return r = players.get(p.getUniqueID());
        return r != null && DimensionManager.isDimensionRegistered(r.dimension) && r.dimension != SandwormConfig.dimension ? r.dimension : 0;
    }
    public void respawn(EntityPlayer p) { Return r = players.get(p.getUniqueID()); if(r != null && r.inside) transfers.put(p.getUniqueID(), false); }
    public void rescue(EntityPlayerMP p) {
        Return r = players.get(p.getUniqueID()); WormEncounter e = r == null ? null : find(r.encounter);
        if(e != null && e.ready() && !e.cleaned) {
            p.playerNetServerHandler.setPlayerLocation(e.x() + 10.5, 65.1, e.z() + 10.5, p.rotationYaw, 0); p.fallDistance = 0;
        } else transfers.put(p.getUniqueID(), false);
    }
    public void cancel(UUID id) {
        WormEncounter e = find(id);
        if(e != null && !e.retired()) { e.canceled = true; e.completedAt = time(); markDirty(); }
    }
    private WorldServer interior() {
        if(DimensionManager.getWorld(SandwormConfig.dimension) == null) DimensionManager.initDimension(SandwormConfig.dimension);
        return DimensionManager.getWorld(SandwormConfig.dimension);
    }
    public void tick() {
        // ServerTick END owns transfers: never mutate world's entity lists inside an entity/block callback.
        processTransfers();
        long now = time();
        if(now % 20 == 0) {
            java.util.Iterator<Long> grace = reentryAfter.values().iterator();
            while(grace.hasNext()) if(grace.next() <= now) { grace.remove(); markDirty(); }
        }
        java.util.Iterator<Entity> it = loaded.values().iterator();
        while(it.hasNext()) { Entity e = it.next(); if(e.isDead || !e.addedToChunk || DimensionManager.getWorld(e.dimension) != e.worldObj) it.remove(); }
        if(records.isEmpty()) return;
        List<WormEncounter> work = new ArrayList<WormEncounter>();
        for(WormEncounter e : records.values()) {
            boolean occupied = occupied(e);
            if(occupied && now % 100 == 0) { e.lastActive = now; markDirty(); }
            if(!e.retired() && !occupied && now - e.lastActive >= SandwormConfig.abandonTicks) cancel(e.id);
            if(!e.cleaned && ((!e.ready() && !e.retired()) || (e.retired() && !occupied && now - e.completedAt >= SandwormConfig.cleanupTicks))) work.add(e);
        }
        int budget = SandwormConfig.blocksPerTick, chunks = SandwormConfig.chunksPerTick;
        if(!work.isEmpty()) {
            WorldServer w = interior();
            int start = Math.floorMod(turn++, work.size());
            for(int n = 0; n < work.size() && budget > 0; n++) {
                WormEncounter e = work.get((start + n) % work.size());
                int allowance = Math.min(budget, Math.max(32, SandwormConfig.blocksPerTick / work.size()));
                while(allowance-- > 0 && budget > 0) {
                    boolean cleanup = e.retired();
                    int cursor = cleanup ? e.cleanupCursor : e.buildCursor;
                    if(cursor >= e.volume()) { if(cleanup) { e.cleaned = true; e.sparks.clear(); } break; }
                    int room = cursor / WormSchematic.VOLUME, i = cursor % WormSchematic.VOLUME;
                    int lx = i % 21, lz = (i / 21) % 21, ly = i / 441, rotation = e.rotations[room] & 3;
                    int rx = WormSchematic.rotatedX(lx, lz, rotation), rz = WormSchematic.rotatedZ(lx, lz, rotation);
                    int x = e.roomX(room) + rx, z = e.roomZ(room) + rz, y = 64 + ly;
                    if(!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                        if(chunks <= 0) break;
                        w.getChunkFromChunkCoords(x >> 4, z >> 4); chunks--;
                    }
                    if(cleanup) { if(w.getBlock(x, y, z) != Blocks.air) w.setBlock(x, y, z, Blocks.air, 0, 2); e.cleanupCursor++; }
                    else {
                        String kind = room == e.brainRoom() || room == e.heartRoom() ? "organ" : room % 3 == 1 ? "corridor" : "chamber";
                        WormSchematic template = WormSchematic.get(kind);
                        Block block = template.block(i); int meta = template.meta(i, rotation);
                        int doors = e.doors[room];
                        boolean doorway = (rx >= 8 && rx <= 12 && ((rz <= 2 && (doors & 1) != 0) || (rz >= 18 && (doors & 4) != 0)))
                                || (rz >= 8 && rz <= 12 && ((rx <= 2 && (doors & 8) != 0) || (rx >= 18 && (doors & 2) != 0)));
                        if(doorway && ly >= 1 && ly <= 5) { block = Blocks.air; meta = 0; }
                        if(room == 0 && ly == 0 && rx >= 9 && rx <= 11 && rz >= 9 && rz <= 11) { block = WormContent.exit; meta = 0; }
                        w.setBlock(x, y, z, block, meta, 2);
                        // Fluids need a scheduled tick after quiet construction placement.
                        if(block instanceof net.minecraftforge.fluids.BlockFluidClassic) w.scheduleBlockUpdate(x, y, z, block, 20);
                        e.buildCursor++;
                    }
                    budget--;
                }
                markDirty();
            }
        }
        if(now % 20 == 0) activateRooms();
        java.util.Iterator<Return> returns = players.values().iterator();
        while(returns.hasNext()) { Return r = returns.next(); WormEncounter e = find(r.encounter); if(!r.inside && e != null && e.retired()) { returns.remove(); markDirty(); } }
        java.util.Iterator<WormEncounter> finished = records.values().iterator();
        while(finished.hasNext()) {
            WormEncounter e = finished.next();
            // Unknown registered exteriors retire instead of admitting a new encounter, so acknowledged records can be removed.
            if(e.cleaned && e.exteriorRetired && !occupied(e)) { slots.remove(e.slot); finished.remove(); markDirty(); }
        }
    }
    private void activateRooms() {
        WorldServer w = DimensionManager.getWorld(SandwormConfig.dimension);
        if(w == null || w.playerEntities.isEmpty()) return;
        for(Object o : new ArrayList<Object>(w.playerEntities)) {
            EntityPlayer p = (EntityPlayer)o;
            Return r = players.get(p.getUniqueID()); WormEncounter e = r == null ? null : find(r.encounter);
            if(e == null || !e.ready() || e.retired() || !e.contains(p.posX, p.posZ)) continue;
            for(int room = 0; room < e.rows * 3; room++) {
                int x = e.roomX(room) + 10, z = e.roomZ(room) + 10;
                if(p.getDistanceSq(x, 65, z) > 28 * 28 || !w.checkChunksExist(x - 6, 64, z - 6, x + 6, 73, z + 6)) continue;
                if(room == e.brainRoom() || room == e.heartRoom()) {
                    boolean brain = room == e.brainRoom();
                    if(brain ? e.brainDead : e.heartDead) continue;
                    UUID id = e.spawnId(brain ? "brain" : "heart");
                    if(loaded(id) != null) { if(brain) e.brainMissing = 0; else e.heartMissing = 0; continue; }
                    // Allow entity loading to settle before reconciling an absent organ after a restart.
                    int missing = brain ? ++e.brainMissing : ++e.heartMissing;
                    if(missing < 5) continue;
                    EntityWormMonster boss = brain ? new EntityBrain(w) : new EntityHeart(w);
                    spawn(w, e, boss, id, x + 0.5, z + 0.5);
                    if(brain) e.brainMissing = 0; else e.heartMissing = 0;
                } else if(room != 0 && (e.roomSpawns & (1L << room)) == 0) {
                    e.roomSpawns |= 1L << room; markDirty();
                    spawn(w, e, new EntityTick(w), e.spawnId("tick" + room), x - 4, z - 4);
                    EntitySpark spark = new EntitySpark(w); spark.setVariant(1 + room % 4);
                    spawn(w, e, spark, e.spawnId("spark" + room), x + 4, z + 4);
                }
            }
        }
    }
    private void spawn(WorldServer w, WormEncounter e, EntityWormMonster mob, UUID id, double x, double z) {
        mob.bind(e.id, id); mob.setLocationAndAngles(x, 65, z, 0, 0);
        w.spawnEntityInWorld(mob);
    }
    private void processTransfers() {
        Map<UUID, Boolean> queue = new LinkedHashMap<UUID, Boolean>(transfers); transfers.clear();
        for(Map.Entry<UUID, Boolean> request : queue.entrySet()) {
            EntityPlayerMP p = findPlayer(request.getKey());
            if(p == null || !p.isEntityAlive()) continue;
            Return r = players.get(request.getKey()); WormEncounter e = r == null ? null : find(r.encounter);
            if(request.getValue()) {
                if(e == null || !e.ready() || e.retired() || p.dimension == SandwormConfig.dimension) continue;
                r.inside = true; markDirty();
                WormTeleporter.transfer(p, interior(), e.x() + 10.5, 65.1, e.z() + 10.5);
                p.addChatMessage(new ChatComponentTranslation("sandworm.enter"));
            } else {
                WorldServer destination = MinecraftServer.getServer().worldServerForDimension(returnDimension(p));
                if(destination == null) destination = DimensionManager.getWorld(0);
                double[] safe = safeReturn(destination, r);
                WormTeleporter.transfer(p, destination, safe[0], safe[1], safe[2]);
                transfers.remove(p.getUniqueID());
                reentryAfter.put(p.getUniqueID(), time() + SandwormConfig.returnGrace);
                if(r != null) {
                    r.inside = false;
                    if(e == null || e.retired()) players.remove(p.getUniqueID());
                }
                markDirty();
            }
        }
    }
    private static EntityPlayerMP findPlayer(UUID id) {
        for(Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP)o; if(id.equals(p.getUniqueID())) return p;
        }
        return null;
    }
    private double[] safeReturn(WorldServer w, Return r) {
        int cx = r == null ? w.getSpawnPoint().posX : MathHelper.floor_double(r.x);
        int cz = r == null ? w.getSpawnPoint().posZ : MathHelper.floor_double(r.z);
        int cy = r == null ? w.getSpawnPoint().posY : MathHelper.clamp_int(MathHelper.floor_double(r.y), 2, 250);
        // Bounded local search, preferring the original elevation, then a surface landing.
        for(int i = 0; i < 81; i++) {
            int x = cx + i % 9 - 4, z = cz + i / 9 - 4;
            for(int j = 0; j < 2; j++) {
                int y = j == 0 ? cy : MathHelper.clamp_int(w.getTopSolidOrLiquidBlock(x, z), 2, 250);
                if(w.getBlock(x, y - 1, z).getMaterial().isSolid() && !w.getBlock(x, y - 1, z).getMaterial().isLiquid()
                        && w.isAirBlock(x, y, z) && w.isAirBlock(x, y + 1, z)) return new double[] {x + 0.5, y + 0.1, z + 0.5};
            }
        }
        // If terrain changed or the original dimension was removed, provide a small safe emergency landing.
        int y = Math.min(250, Math.max(cy + 4, w.getTopSolidOrLiquidBlock(cx, cz) + 2));
        for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) {
            w.setBlock(cx + dx, y - 1, cz + dz, Blocks.cobblestone);
            w.setBlockToAir(cx + dx, y, cz + dz); w.setBlockToAir(cx + dx, y + 1, cz + dz);
        }
        return new double[] {cx + 0.5, y + 0.1, cz + 0.5};
    }
    @Override public void writeToNBT(NBTTagCompound n) {
        n.setInteger("version", 1); n.setInteger("dimension", savedDimension); n.setInteger("nextSlot", nextSlot);
        NBTTagList encounters = new NBTTagList(); for(WormEncounter e : records.values()) encounters.appendTag(e.write()); n.setTag("encounters", encounters);
        NBTTagList returns = new NBTTagList();
        for(Map.Entry<UUID, Return> entry : players.entrySet()) {
            Return r = entry.getValue(); NBTTagCompound p = new NBTTagCompound();
            p.setString("player", entry.getKey().toString()); p.setString("encounter", r.encounter.toString());
            p.setInteger("dimension", r.dimension); p.setDouble("x", r.x); p.setDouble("y", r.y); p.setDouble("z", r.z); p.setBoolean("inside", r.inside); returns.appendTag(p);
        }
        n.setTag("returns", returns);
        NBTTagList grace = new NBTTagList();
        for(Map.Entry<UUID, Long> entry : reentryAfter.entrySet()) {
            NBTTagCompound g = new NBTTagCompound(); g.setString("player", entry.getKey().toString()); g.setLong("until", entry.getValue()); grace.appendTag(g);
        }
        n.setTag("returnGrace", grace);
    }
    @Override public void readFromNBT(NBTTagCompound n) {
        savedDimension = n.getInteger("dimension");
        if(savedDimension != SandwormConfig.dimension) throw new IllegalStateException("Restore saved Sandworm dimensionID " + savedDimension + " before opening this world");
        records.clear(); slots.clear(); players.clear(); reentryAfter.clear(); loaded.clear(); transfers.clear(); nextSlot = n.getInteger("nextSlot");
        NBTTagList encounters = n.getTagList("encounters", 10);
        for(int i = 0; i < encounters.tagCount(); i++) { WormEncounter e = WormEncounter.read(encounters.getCompoundTagAt(i)); records.put(e.id, e); slots.put(e.slot, e); nextSlot = Math.max(nextSlot, e.slot + 1); }
        NBTTagList returns = n.getTagList("returns", 10);
        for(int i = 0; i < returns.tagCount(); i++) {
            NBTTagCompound p = returns.getCompoundTagAt(i); Return r = new Return();
            r.encounter = UUID.fromString(p.getString("encounter")); r.dimension = p.getInteger("dimension");
            r.x = p.getDouble("x"); r.y = p.getDouble("y"); r.z = p.getDouble("z"); r.inside = p.getBoolean("inside");
            players.put(UUID.fromString(p.getString("player")), r);
        }
        NBTTagList grace = n.getTagList("returnGrace", 10);
        for(int i = 0; i < grace.tagCount(); i++) {
            NBTTagCompound g = grace.getCompoundTagAt(i); reentryAfter.put(UUID.fromString(g.getString("player")), g.getLong("until"));
        }
    }
    private static final class Return { UUID encounter; int dimension; double x, y, z; boolean inside; }
}
