package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.File;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityGardener;
import com.hbm.entity.mob.gemini.EntityOceanGardener;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Post-unlock underground colonization, additive to the existing world generator. */
public final class GardenerNests extends WorldSavedData {
    private static final String NAME = "GeminiGardenerNests";
    private static final int LAYOUT = 3, HALF = 55, HEIGHT = 24;
    private static final int[][] HOMES = {{-24, -16}, {24, -16}, {0, 26}};
    // Scans are chunk-major; only the current chunk and its notification neighbors need loading.
    private static final int SCANS_PER_TICK = 1024, EDITS_PER_TICK = 128;
    private int nextJob;
    private static final Set<String> STRUCTURES = new LinkedHashSet<String>(Arrays.asList(
            "Mineshaft", "Stronghold", "Village", "Temple", "NTMFeatures", "RTMLegacyStructures"));
    private static boolean structureLimitExceeded;
    private static int placementDepth;
    public static boolean placingNest() { return placementDepth > 0; }
    static void beginPlacement() { placementDepth++; }
    static void endPlacement() { placementDepth--; }
    /** MapStorage swallows IO failures. Construction must observe a successful journal
     * write before touching terrain, using the same native WorldSavedData file format. */
    static boolean checkpoint(WorldServer world, WorldSavedData data) {
        try {
            File target = world.getSaveHandler().getMapFileFromName(data.mapName);
            if(target == null) throw new IOException("World has no saved-data file");
            File temporary = new File(target.getPath() + ".gemini.tmp");
            NBTTagCompound saved = new NBTTagCompound(), root = new NBTTagCompound();
            data.writeToNBT(saved); root.setTag("data", saved);
            try(FileOutputStream output = new FileOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(root, output);
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            data.setDirty(false);
            return true;
        } catch(IOException | RuntimeException failure) {
            data.markDirty();
            com.hbm.main.MainRegistry.logger.error("Gemini construction journal could not be saved; terrain work paused", failure);
            GeminiInvasionManager.debug(world, "structure-journal", data.mapName + ": construction save failed; terrain work paused (see server log)");
            return false;
        }
    }
    private final List<Nest> nests = new ArrayList<Nest>();
    private final List<Residence> residences = new ArrayList<Residence>();
    private final GeminiStructureSchedule schedule = new GeminiStructureSchedule();
    private final Map<String, DeliveryClaim> deliveries = new LinkedHashMap<String, DeliveryClaim>();
    // Living identities survive chunk unload. Loaded membership remains in GeminiPopulation.
    private final Set<UUID> living = new LinkedHashSet<UUID>();
    private final Set<UUID> censusRemoved = new LinkedHashSet<UUID>();
    private boolean censusDone;
    private File[] censusRegions;
    private int censusRegion, censusChunk;
    private long censusLog;

    static Set<UUID> living(World world) { return get(world).living; }
    static boolean censusReady(World world) { return world.provider.dimensionId != 0 || get(world).censusDone; }
    /** One-time upgrade census: read at most one saved chunk per tick, without loading its terrain/entities. */
    static void census(WorldServer world) {
        GardenerNests data = get(world);
        if(world.provider.dimensionId != 0 || data.censusDone) return;
        try {
            File directory = new File(world.getSaveHandler().getWorldDirectory(), "region");
            if(data.censusRegions == null) {
                data.censusRegions = directory.listFiles((dir, name) -> name.matches("r\\.-?\\d+\\.-?\\d+\\.mca"));
                if(data.censusRegions == null) {
                    if(directory.exists()) throw new IOException("Cannot list Overworld region directory");
                    data.censusRegions = new File[0];
                }
                Arrays.sort(data.censusRegions);
            }
            for(int budget = 0; budget < 128; budget++) {
                if(data.censusRegion >= data.censusRegions.length) {
                    data.censusDone = true; data.censusRemoved.clear(); data.censusRegions = null; data.markDirty(); return;
                }
                File file = data.censusRegions[data.censusRegion];
                String[] coordinates = file.getName().split("\\.");
                int x = Integer.parseInt(coordinates[1]) * 32 + data.censusChunk % 32;
                int z = Integer.parseInt(coordinates[2]) * 32 + data.censusChunk / 32;
                // Use the native region cache so concurrent chunk saving shares the same header/stream lock.
                net.minecraft.world.chunk.storage.RegionFile region = net.minecraft.world.chunk.storage.RegionFileCache.createOrLoadRegionFile(
                        world.getSaveHandler().getWorldDirectory(), x, z);
                try(DataInputStream input = region.getChunkDataInputStream(x & 31, z & 31)) {
                    if(input == null && region.isChunkSaved(x & 31, z & 31))
                        throw new IOException("Unreadable saved chunk at " + x + "," + z);
                    if(input != null) {
                        NBTTagList entities = CompressedStreamTools.read(input).getCompoundTag("Level").getTagList("Entities", 10);
                        for(int i = 0; i < entities.tagCount(); i++) {
                            NBTTagCompound tag = entities.getCompoundTagAt(i);
                            if(net.minecraft.entity.EntityList.stringToClassMapping.get(tag.getString("id")) != EntityGardener.class
                                    || tag.getFloat("HealF") <= 0 && tag.getShort("Health") <= 0) continue;
                            UUID id = new UUID(tag.getLong("UUIDMost"), tag.getLong("UUIDLeast"));
                            if(!data.censusRemoved.contains(id) && data.living.add(id)) data.markDirty();
                        }
                    }
                    if(++data.censusChunk >= 1024) { data.censusChunk = 0; data.censusRegion++; }
                    if(input != null) return;
                }
            }
        } catch(IOException | RuntimeException failure) {
            if(world.getTotalWorldTime() >= data.censusLog) {
                com.hbm.main.MainRegistry.logger.error("Underground Gardener census paused; new admissions wait", failure);
                data.censusLog = world.getTotalWorldTime() + 1200;
            }
        }
    }
    public static void remember(EntityGardener mob) {
        if(!mob.worldObj.isRemote && !mob.isOcean() && mob.isEntityAlive()
                && get(mob.worldObj).living.add(mob.getUniqueID())) get(mob.worldObj).markDirty();
    }

    /** Registered pit, including while the resident's home chunk is unloaded. */
    public static final class Access {
        public final int x, y, z, top;
        public final boolean ready;
        private Access(Nest nest) { x = nest.x; y = nest.y; z = nest.z; top = nest.top; ready = nest.accessVersion > 0; }
    }
    public static Access access(EntityGardener mob) {
        if(mob.isOcean() || mob.worldObj.isRemote) return null;
        for(Nest nest : get(mob.worldObj).nests) for(Resident resident : nest.residents)
            if(resident.id.equals(mob.getUniqueID())) return new Access(nest);
        return null;
    }

    /** Registered chamber slots and the open pit; never constructs or loads a route. */
    public static final class Delivery {
        public final int nestX, y, nestZ, top, slot;
        public final double x, z;
        private Delivery(int nx, int y, int nz, int top, int slot) {
            nestX = nx; this.y = y; nestZ = nz; this.top = top; this.slot = slot;
            x = nx + (top < 0 ? 4.5D : 30.5D); z = nz + (slot - 1) * 4 + 0.5D;
        }
        private String key() { return nestX + ":" + y + ":" + nestZ + ":" + slot; }
        public boolean inChamber(net.minecraft.entity.Entity e) {
            return Math.abs(e.boundingBox.minY - y) < 2 && e.posX < nestX + 35 && e.getDistanceSq(x, y, z) < 100;
        }
        public boolean atAccess(net.minecraft.entity.Entity e) {
            return top >= 0 && e.posY >= y && e.posY <= top + 1.1
                    && Math.abs(e.posX - (nestX + 48.5)) <= 1.25 && Math.abs(e.posZ - (nestZ + 0.5)) < 0.6;
        }
        public net.minecraft.util.Vec3 waypoint(com.hbm.entity.mob.gemini.EntityWoodcrawler mob) {
            if(top >= 0 && mob.posY > y + 2) {
                boolean shaft = atAccess(mob);
                return net.minecraft.util.Vec3.createVectorHelper(nestX + (shaft ? 48.5D : 47.5D), shaft ? Math.max(y, mob.posY - 5) : top, nestZ + 0.5D);
            }
            return net.minecraft.util.Vec3.createVectorHelper(x, y, z);
        }
        public NBTTagCompound write() {
            NBTTagCompound n = new NBTTagCompound(); n.setInteger("X", nestX); n.setInteger("Y", y); n.setInteger("Z", nestZ);
            n.setInteger("Top", top); n.setInteger("Slot", slot); return n;
        }
        public static Delivery read(NBTTagCompound n) { return new Delivery(n.getInteger("X"), n.getInteger("Y"), n.getInteger("Z"), n.getInteger("Top"), MathHelper.clamp_int(n.getInteger("Slot"), 0, 2)); }
    }
    private static final class DeliveryClaim {
        final UUID owner; final long until;
        DeliveryClaim(UUID owner, long until) { this.owner = owner; this.until = until; }
    }
    private static boolean deliverySpace(com.hbm.entity.mob.gemini.EntityWoodcrawler carrier, net.minecraft.entity.EntityLivingBase victim, Delivery d) {
        World world = carrier.worldObj;
        boolean registered = false;
        for(Nest nest : get(world).nests) if(nest.built && nest.accessVersion > 0 && nest.x == d.nestX && nest.y == d.y
                && nest.z == d.nestZ && nest.top == d.top) { registered = true; break; }
        if(!registered) return false;
        // Test the actual upright captive envelope along the clear west pit wall.
        double half = Math.max(0.6, victim.width) * 0.5 + 0.001;
        AxisAlignedBB shaft = AxisAlignedBB.getBoundingBox(d.nestX + 48.5 - half, d.y, d.nestZ + 0.5 - half,
                d.nestX + 48.5 + half, d.top + 3, d.nestZ + 0.5 + half);
        AxisAlignedBB corridor = AxisAlignedBB.getBoundingBox(d.x - half, d.y + 0.01, d.nestZ + 0.5 - half,
                d.nestX + 48.5 + half, d.y + Math.max(1.8, victim.height), d.nestZ + 0.5 + half);
        AxisAlignedBB slot = AxisAlignedBB.getBoundingBox(d.x - 0.6, d.y + 0.01, d.z - 0.6, d.x + 0.6, d.y + Math.max(1.8, victim.height), d.z + 0.6);
        if(!loaded(world, slot) || !world.func_147461_a(slot).isEmpty() || world.isAnyLiquid(slot)) return false;
        if(d.top >= 0 && (!loaded(world, shaft) || !loaded(world, corridor) || !world.func_147461_a(shaft).isEmpty()
                || !world.func_147461_a(corridor).isEmpty() || world.isAnyLiquid(shaft))) return false;
        for(Object entry : world.getEntitiesWithinAABBExcludingEntity(carrier, slot.expand(1, 0, 1))) {
            net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) entry;
            if(entity != victim && entity.canBeCollidedWith()) return false;
        }
        DeliveryClaim claim = get(world).deliveries.get(d.key());
        return claim == null || claim.until < world.getTotalWorldTime() || claim.owner.equals(carrier.getUniqueID());
    }
    public static List<Delivery> deliveries(final com.hbm.entity.mob.gemini.EntityWoodcrawler carrier, net.minecraft.entity.EntityLivingBase victim) {
        List<Delivery> result = new ArrayList<Delivery>();
        for(Nest nest : get(carrier.worldObj).nests) if(nest.built && nest.accessVersion > 0
                && carrier.getDistanceSq(nest.x + 55.5, Math.max(nest.y, carrier.posY), nest.z + 0.5) <= 96 * 96) {
            for(int slot = 0; slot < 3; slot++) {
                Delivery d = new Delivery(nest.x, nest.y, nest.z, nest.top, slot);
                if(deliverySpace(carrier, victim, d)) result.add(d);
            }
        }
        java.util.Collections.sort(result, new java.util.Comparator<Delivery>() {
            @Override public int compare(Delivery a, Delivery b) {
                net.minecraft.util.Vec3 x = a.waypoint(carrier), y = b.waypoint(carrier);
                return Double.compare(carrier.getDistanceSq(x.xCoord, x.yCoord, x.zCoord), carrier.getDistanceSq(y.xCoord, y.yCoord, y.zCoord));
            }
        });
        return result.size() > 6 ? new ArrayList<Delivery>(result.subList(0, 6)) : result;
    }
    public static boolean claimDelivery(com.hbm.entity.mob.gemini.EntityWoodcrawler carrier, net.minecraft.entity.EntityLivingBase victim, Delivery d) {
        if(!deliverySpace(carrier, victim, d)) return false;
        get(carrier.worldObj).deliveries.put(d.key(), new DeliveryClaim(carrier.getUniqueID(), carrier.worldObj.getTotalWorldTime() + 120)); return true;
    }
    public static void releaseDelivery(com.hbm.entity.mob.gemini.EntityWoodcrawler carrier, Delivery d) {
        DeliveryClaim claim = get(carrier.worldObj).deliveries.get(d.key());
        if(claim != null && claim.owner.equals(carrier.getUniqueID())) get(carrier.worldObj).deliveries.remove(d.key());
    }

    public GardenerNests(String name) { super(name); }
    private static String saveName(World world) {
        return NAME + (world.provider.dimensionId == 0 ? "" : "_" + world.provider.dimensionId);
    }
    private static GardenerNests get(World world) {
        String name = saveName(world);
        GardenerNests data = (GardenerNests) world.mapStorage.loadData(GardenerNests.class, name);
        if(data == null) { data = new GardenerNests(name); world.mapStorage.setData(name, data); }
        return data;
    }
    public static List<GeminiInvasionManager.StructureInfo> known(World world) {
        List<GeminiInvasionManager.StructureInfo> result = new ArrayList<GeminiInvasionManager.StructureInfo>();
        if(!(world instanceof WorldServer) || world.isRemote) return result;
        GardenerNests data = (GardenerNests) world.mapStorage.loadData(GardenerNests.class, saveName(world));
        if(data != null) for(Nest nest : data.nests)
            result.add(info(world, nest, describe(world, nest)));
        return result;
    }
    public static int completedCount(World world) {
        if(!(world instanceof WorldServer) || world.isRemote) return 0;
        int count = 0;
        for(Nest nest : get(world).nests) if(nest.built && nest.accessVersion > 0) count++;
        return count;
    }
    private static GeminiInvasionManager.StructureInfo info(World world, Nest nest, String status) {
        return new GeminiInvasionManager.StructureInfo("underground_gardener", nest.x, nest.y, nest.z, world.provider.dimensionId, status);
    }
    private static String generationGate(World world) {
        String global = GeminiInvasionManager.structureGate(world, 3);
        if(global != null) return global;
        if(!GeminiConfig.gardenerEnabled) return "gardener.enabled is false";
        if(world.difficultySetting == EnumDifficulty.PEACEFUL) return "difficulty is Peaceful";
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return "mobGriefing is false";
        int jobs = 0, reserved = 0;
        for(Nest nest : get(world).nests) {
            if(!nest.failed) { reserved++; if(!nest.built || nest.accessVersion == 0) jobs++; }
            else if(nest.built) reserved++;
        }
        if(jobs >= 2) return "two staged nest construction jobs already reserved";
        if(reserved >= GeminiConfig.gardenerNestLimit) return "nest limit reached";
        if(GeminiConfig.gardenerNestsPerArea == 0) return "nests per survival area is zero";
        if(GeminiStructureSchedule.players(world).isEmpty()) return "no living players in this world";
        return null;
    }
    public static String eligibility(World world) {
        String gate = generationGate(world);
        return gate == null ? "eligible around living players of any game mode" : gate;
    }
    public static String diagnostics(World world) {
        if(!(world instanceof WorldServer) || world.isRemote) return "server only";
        return "Automatic nests enabled=" + (GeminiConfig.enabled && GeminiConfig.gardenerEnabled
                && GeminiConfig.gardenerNestLimit > 0 && GeminiConfig.gardenerNestsPerArea > 0)
                + "; gate=" + eligibility(world) + "; " + get(world).schedule.status(world)
                + "; resident target=" + GeminiConfig.gardenerResidents + "; living Gardener cap=" + GeminiConfig.gardenerMaxLoaded
                + "; living (including unloaded)=" + GeminiPopulation.gardenerCount(world, null)
                + "; jobs=" + get(world).jobStatus(world) + "; nests=" + get(world).nestStatus(world);
    }
    static void reschedule(WorldServer world) { GardenerNests data = get(world); data.schedule.reschedule(data); }
    static void schedule(WorldServer world) {
        GardenerNests data = get(world);
        data.schedule.tick(world);
        try {
            if(generationGate(world) != null) return;
            if(!data.schedule.due(data, GeminiConfig.gardenerNestIntervalSeconds)) return;
            String reason = data.generateNearPlayers(world);
            if(reason != null) data.schedule.failed(data, world, "nest", reason);
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Automatic Gemini nest generation failed", failure);
            data.schedule.failed(data, world, "nest", "engine/block callback exception (see server log)");
        }
    }

    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player) {
        return debugSpawn(player, null);
    }
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player, ChunkCoordinates requested) {
        return debugSpawn(player, requested, false);
    }
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player, ChunkCoordinates requested, boolean force) {
        if(!(player.worldObj instanceof WorldServer) || player.worldObj.isRemote)
            throw new net.minecraft.command.CommandException("Nest placement requires a server world.");
        WorldServer world = (WorldServer) player.worldObj;
        GardenerNests data = get(world);
        int x = requested == null ? MathHelper.floor_double(player.posX) : requested.posX;
        int z = requested == null ? MathHelper.floor_double(player.posZ) : requested.posZ;
        int y = requested == null ? 9 + world.rand.nextInt(4) : requested.posY;
        // Exact saved coordinates select a record for an explicit upgrade. No automatic migration.
        for(Nest saved : data.nests) if(saved.x == x && saved.z == z && (requested == null || saved.y == y)) {
            if(!force) throw new net.minecraft.command.CommandException("Existing nest: repeat with force to regenerate/upgrade this record.");
            if(!saved.built && saved.layout == LAYOUT) {
                saved.debug = saved.recovering = true; saved.failed = false; saved.idle = 0;
                saved.journalReady = checkpoint(world, data);
                if(!saved.journalReady) { saved.failed = true; saved.reason = "could not save construction journal; terrain work paused"; }
                return info(world, saved, "construction already queued; " + describe(world, saved));
            }
            y = MathHelper.clamp_int(saved.y, 9, 12); // Explicit upgrades keep the floor above bedrock and at Y=8..11.
            String reason = placementBounds(world, x, y, z);
            if(reason != null) throw new net.minecraft.command.CommandException(reason);
            saved.y = y; saved.layout = LAYOUT; saved.built = false; saved.phase = 0;
            saved.cursor = 0; saved.top = -1; saved.debug = true; saved.upgrading = true;
            saved.failed = false; saved.recovering = true; saved.idle = 0; saved.reason = "queued explicit upgrade";
            saved.accessVersion = 0;
            saved.ensureCapacity();
            data.markDirty();
            saved.journalReady = checkpoint(world, data);
            if(!saved.journalReady) { saved.failed = true; saved.reason = "could not save construction journal; terrain work paused"; }
            return info(world, saved, "explicit upgrade queued; resident identities and defeat states retained");
        }
        String reason = placementBounds(world, x, y, z);
        if(reason != null) throw new net.minecraft.command.CommandException(reason);
        for(Nest saved : data.nests) if(Math.abs(saved.x - x) < 128 && Math.abs(saved.z - z) < 112)
            throw new net.minecraft.command.CommandException("Placement overlaps a saved nest; select its exact origin and use force to upgrade it.");
        Nest nest = new Nest(x, y, z); nest.debug = true; nest.ensureCapacity();
        nest.hasHome = true; nest.homeX = MathHelper.floor_double(player.posX); nest.homeZ = MathHelper.floor_double(player.posZ);
        data.nests.add(nest); data.markDirty();
        nest.journalReady = checkpoint(world, data);
        if(!nest.journalReady) { nest.failed = true; nest.reason = "could not save construction journal; terrain work paused"; }
        return info(world, nest, "construction queued (bounded server ticks); floor Y=" + (y - 1) + "; " + describe(world, nest));
    }
    private static String placementBounds(World world, int x, int y, int z) {
        if(y < 6 || y + HEIGHT + 2 >= world.getActualHeight()) return "Nest air level must be 6.." + (world.getActualHeight() - HEIGHT - 3);
        if((long) x - HALF - 16 < -29999984L || (long) x + 64 + 16 > 29999984L
                || (long) z - HALF - 16 < -29999984L || (long) z + HALF + 16 > 29999984L)
            return "Unsafe world boundaries for chamber, shaft and neighboring block updates";
        return null;
    }
    /** Smoothly joined lobes leave broad living areas connected through a central hall. */
    private static boolean inside(int x, int z) {
        double ripple = Math.sin(x * 0.19D + z * 0.11D) * 0.8D;
        if(x * x + z * z <= (40 + ripple) * (40 + ripple)) return true;
        for(int[] home : HOMES) {
            int dx = x - home[0], dz = z - home[1];
            if(dx * dx + dz * dz <= (28 + ripple) * (28 + ripple)) return true;
        }
        return false;
    }
    /** Conservative circular model envelope for body steering; connects all three living areas. */
    public static boolean inHabitat(double x, double z, int margin) {
        for(int sample = 0; sample < 24; sample++) {
            double angle = sample * Math.PI / 12D;
            if(!inside((int) Math.round(x + Math.cos(angle) * margin), (int) Math.round(z + Math.sin(angle) * margin))) return false;
        }
        return true;
    }
    private static int ceiling(int x, int z) {
        return 22 + (int) Math.round(2D * Math.sin(x * 0.055D) * Math.cos(z * 0.065D));
    }
    private static Block cavernCell(int x, int y, int z) {
        if(!inside(x, z)) return null;
        int roof = ceiling(x, z);
        if(y == -1 || y == roof || y == roof + 1) return ModBlocks.gardener_flesh;
        if(y < 0 || y >= roof) return null;
        if(!inside(x - 1, z) || !inside(x + 1, z) || !inside(x, z - 1) || !inside(x, z + 1)) return ModBlocks.gardener_flesh;
        // Floor veins stay away from all three initial limb envelopes.
        boolean home = false;
        for(int[] point : HOMES) if((x - point[0]) * (x - point[0]) + (z - point[1]) * (z - point[1]) < 17 * 17) home = true;
        if(y == 0 && !home && ((x * 3 + z * 5) % 23 == 0 || Math.abs(Math.sin(Math.atan2(z, x) * 3)) < 0.12D))
            return ModBlocks.mockery_growth;
        return Blocks.air;
    }
    /** Fifteen clear blocks across, surrounded by a one-block organic seal. */
    private static Block accessBlock(Nest nest, int x, int y, int z) {
        int top = nest.top - nest.y;
        if(x >= 47 && x <= 63 && Math.abs(z) <= 8 && y >= -1 && y < top + 12) {
            if(y == -1) return ModBlocks.gardener_flesh;
            if(x >= 48 && x <= 62 && Math.abs(z) <= 7) return Blocks.air;
            // A body-sized opening from the chamber into the west pit wall.
            if(x == 47 && Math.abs(z) <= 6 && y >= 0 && y < 12) return Blocks.air;
            if(y < top) return ModBlocks.gardener_flesh;
            return Blocks.air;
        }
        // A supported surface dismount; never caps or obstructs the pit interior.
        if(x >= 40 && x < 47 && Math.abs(z) <= 7 && y >= top - 1 && y < top + 12)
            return y == top - 1 ? ModBlocks.gardener_flesh : Blocks.air;
        int start = nest.layout >= LAYOUT ? 30 : 0;
        if(x >= start && x < 47 && Math.abs(z) <= 7 && y >= -1 && y <= 12) {
            Block room = nest.layout >= LAYOUT ? cavernCell(x, y, z) : null;
            if(room == Blocks.air || room == ModBlocks.mockery_growth) return room;
            return y == -1 || y == 12 || Math.abs(z) == 7 ? ModBlocks.gardener_flesh : Blocks.air;
        }
        return null;
    }
    private static Block jobBlock(Nest nest, int x, int y, int z) {
        Block access = accessBlock(nest, x, y, z);
        return access != null ? access : nest.built ? null : cavernCell(x, y, z);
    }

    /** Cells of the existing layouts and their bounded terrain preparation. */
    static final class Placement {
        final int x, y, z, meta; final Block block;
        Placement(int x, int y, int z, Block block, int meta) { this.x = x; this.y = y; this.z = z; this.block = block; this.meta = meta; }
    }
    static final class PlacementPlan {
        final List<AxisAlignedBB> regions = new ArrayList<AxisAlignedBB>();
        final Map<Long, Placement> edits = new LinkedHashMap<Long, Placement>();
        AxisAlignedBB readBounds;
        String reason;
        void put(int x, int y, int z, Block block, int meta) {
            long key = ((long) (x & 0x3FFFFFF) << 34) | ((long) (z & 0x3FFFFFF) << 8) | (y & 255);
            edits.put(key, new Placement(x, y, z, block, meta));
        }
        List<Placement> cells() {
            List<Placement> result = new ArrayList<Placement>();
            // Seal liquids and support loose terrain before excavation; attachments go last.
            for(Placement cell : edits.values()) if(cell.block != Blocks.air && cell.block != Blocks.ladder && cell.block != ModBlocks.mockery_growth) result.add(cell);
            for(Placement cell : edits.values()) if(cell.block == Blocks.air) result.add(cell);
            for(Placement cell : edits.values()) if(cell.block == Blocks.ladder || cell.block == ModBlocks.mockery_growth) result.add(cell);
            return result;
        }
        String checkBounds(WorldServer world) {
            for(AxisAlignedBB box : regions) {
                if(box.minX < -29999984 || box.maxX > 29999984 || box.minZ < -29999984 || box.maxZ > 29999984
                        || box.minY < 0 || box.maxY > world.getActualHeight()) return "unsafe world boundaries for the complete placement volume";
                if(!loadedVolume(world, box.expand(1, 0, 1))) return "unloaded chunks intersect the preparation area; move closer/load them before retrying";
            }
            if(readBounds != null) {
                if(readBounds.minX < -29999984 || readBounds.maxX > 29999984 || readBounds.minZ < -29999984 || readBounds.maxZ > 29999984)
                    return "unsafe world boundaries for the resident clearance area";
                if(!loadedVolume(world, readBounds)) return "unloaded chunks intersect the resident clearance area";
            }
            return null;
        }
    }
    static boolean loadedVolume(World world, AxisAlignedBB box) {
        for(int x = MathHelper.floor_double(box.minX) >> 4; x <= (MathHelper.ceiling_double_int(box.maxX) - 1) >> 4; x++)
            for(int z = MathHelper.floor_double(box.minZ) >> 4; z <= (MathHelper.ceiling_double_int(box.maxZ) - 1) >> 4; z++)
                if(!world.getChunkProvider().chunkExists(x, z)) return false;
        return true;
    }
    /** First solid terrain above the ground, ignoring tree canopies and vegetation; independent of player Y. */
    static int surface(World world, int x, int z) {
        for(int y = Math.min(world.getActualHeight() - 1, world.getHeightValue(x, z)); y >= 1; y--) {
            Block block = world.getBlock(x, y, z);
            if(block.isWood(world, x, y, z) || block.isLeaves(world, x, y, z)) continue;
            if(block.getMaterial().blocksMovement() || block.getMaterial().isLiquid()) return y + 1;
        }
        return -1;
    }
    static void countRejection(Map<String, Integer> reasons, String reason) {
        // Include the obstruction coordinates in the single opt-in candidate summary.
        Integer count = reasons.get(reason); reasons.put(reason, count == null ? 1 : count + 1);
    }
    static net.minecraft.command.CommandException searchFailure(World world, String kind, boolean exact, Map<String, Integer> reasons) {
        GeminiInvasionManager.debug(world, kind + "-candidates", "Forced " + kind + " candidate rejections: " + reasons);
        String blocking = null;
        int count = -1;
        for(Map.Entry<String, Integer> entry : reasons.entrySet()) {
            if(blocking == null || !entry.getKey().startsWith("unloaded") && (blocking.startsWith("unloaded") || entry.getValue() > count)) {
                blocking = entry.getKey(); count = entry.getValue();
            }
        }
        return GeminiInvasionManager.rejected(world, kind, (exact ? "Requested " : "No safe nearby ") + kind + " placement: " + blocking
                + (exact ? ". Origin was not moved." : ". Use explicit x y z or /gemini debug true for candidate details."));
    }

    public static boolean active(World world) {
        return GeminiConfig.gardenerEnabled && GeminiInvasionManager.canActivityOccur(world, 3)
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }
    public static boolean loaded(World world, AxisAlignedBB box) {
        for(int x = MathHelper.floor_double(box.minX) >> 4; x <= MathHelper.floor_double(box.maxX) >> 4; x++)
            for(int z = MathHelper.floor_double(box.minZ) >> 4; z <= MathHelper.floor_double(box.maxZ) >> 4; z++)
                if(!world.getChunkProvider().chunkExists(x, z)) return false;
        return true;
    }
    /** Deliberately narrow: constructed blocks, liquids, portals and mod machines cannot be excavated. */
    private static boolean natural(Block block) {
        return block == Blocks.air || block == Blocks.stone || block == Blocks.dirt || block == Blocks.gravel
                || block == Blocks.coal_ore || block == Blocks.iron_ore || block == Blocks.gold_ore
                || block == Blocks.redstone_ore || block == Blocks.lit_redstone_ore || block == Blocks.lapis_ore
                || block == Blocks.diamond_ore || block == Blocks.emerald_ore
                // Plain NTM ores replace ordinary cave stone too. Exclude specialized/hazardous subclasses and reservoirs.
                || block.getClass() == com.hbm.blocks.generic.BlockOre.class && block != ModBlocks.ore_oil
                    && block != ModBlocks.ore_oil_empty && block != ModBlocks.ore_gas && block != ModBlocks.ore_gas_empty
                    && block != ModBlocks.ore_fire;
    }
    private static boolean safe(World world, int x, int y, int z, boolean existingNest) {
        if(y < 6 || y > 54 || !world.blockExists(x, y, z)) return false;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        return (natural(block) || existingNest && (block == ModBlocks.gardener_flesh || block == ModBlocks.mockery_growth))
                && !block.hasTileEntity(meta) && world.getTileEntity(x, y, z) == null
                && block.getBlockHardness(world, x, y, z) >= 0;
    }
    /** Surface tunnels use the same narrow geology and protections as nests, without the cave height limit. */
    public static boolean canExcavate(World world, EntityGardener boss, int x, int y, int z) {
        if(y < 6 || y >= world.getHeight() - 1 || !world.blockExists(x, y, z)
                || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return false;
        Block block = world.getBlock(x, y, z);
        return (natural(block) || block == Blocks.grass || block == Blocks.sand || block == Blocks.sandstone
                || block == Blocks.clay || block == Blocks.mycelium || block == ModBlocks.gardener_flesh
                || block == ModBlocks.mockery_growth) && !block.hasTileEntity(world.getBlockMetadata(x, y, z))
                && world.getTileEntity(x, y, z) == null && block.getBlockHardness(world, x, y, z) >= 0
                && block.canEntityDestroy(world, x, y, z, boss);
    }
    public static boolean excavate(World world, EntityGardener boss, int x, int y, int z) {
        AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        return !world.isRemote && canExcavate(world, boss, x, y, z)
                && !WretchCamps.hasPlayerPlacement(world, cell) && !overlapsStructure(world, cell)
                && mutate(world, boss, x, y, z, Blocks.air, true);
    }
    /** One checked mutation, also used by slam terrain damage. No explosion or recursive neighbor scan. */
    public static boolean change(World world, EntityGardener boss, int x, int y, int z, Block replacement) {
        return mutate(world, boss, x, y, z, replacement, false);
    }
    private static boolean mutate(World world, EntityGardener boss, int x, int y, int z, Block replacement, boolean tunnel) {
        if(world.isRemote || !(world instanceof WorldServer) || !(active(world) || GeminiSpawning.structureDebugAdmission(boss))
                || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !loaded(world, AxisAlignedBB.getBoundingBox(x - 1, y, z - 1, x + 2, y + 1, z + 2))
                || !(tunnel ? canExcavate(world, boss, x, y, z) : safe(world, x, y, z, true))) return false;
        Block old = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if(old == replacement || (boss != null && !old.canEntityDestroy(world, x, y, z, boss))) return false;
        AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        if(replacement != Blocks.air && !world.getEntitiesWithinAABBExcludingEntity(null, cell).isEmpty()) return false;
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        if(!world.canMineBlock(actor, x, y, z)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, old, meta, actor))) return false;
        if(!(active(world) || GeminiSpawning.structureDebugAdmission(boss)) || !(tunnel ? canExcavate(world, boss, x, y, z) : safe(world, x, y, z, true))
                || WretchCamps.hasPlayerPlacement(world, cell) || world.getBlock(x, y, z) != old
                || world.getBlockMetadata(x, y, z) != meta) return false;
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if(!world.setBlock(x, y, z, replacement, 0, 0)) return false;
        if(replacement != Blocks.air) {
            placementDepth++;
            try {
                if(ForgeEventFactory.onPlayerBlockPlace(actor, snapshot, ForgeDirection.UP).isCanceled()) {
                    snapshot.restore(true, false); return false;
                }
            } finally { placementDepth--; }
        }
        world.markAndNotifyBlock(x, y, z, world.getChunkFromBlockCoords(x, z), old, replacement, 3);
        return true;
    }

    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void mapgen(InitMapGenEvent event) {
            // Optional generators using Forge's native hook expose their saved structure bounds too.
            if(event.newGen instanceof MapGenStructure) {
                String name = ((MapGenStructure) event.newGen).func_143025_a();
                if(name != null && !STRUCTURES.contains(name)) {
                    if(STRUCTURES.size() >= 128) structureLimitExceeded = true;
                    else STRUCTURES.add(name);
                }
            }
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void join(EntityJoinWorldEvent event) {
            if(!event.world.isRemote && event.entity instanceof EntityGardener && !(event.entity instanceof EntityOceanGardener)) {
                EntityGardener boss = (EntityGardener) event.entity;
                // Natural bosses retain progression/caps; manually initialized creatures are independent.
                if(!boss.addedToChunk && ((!GeminiSpawning.manualAdmission(boss) && (!(active(event.world) || GeminiSpawning.structureDebugAdmission(boss)) || !GeminiPopulation.canAdmit(event.world,
                        EntityGardener.class, boss, GeminiConfig.gardenerMaxLoaded))) || !boss.hasSpawnSpace())) event.setCanceled(true);
            }
        }
        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if(event.phase != TickEvent.Phase.END || event.world.isRemote || !(event.world instanceof WorldServer)
                    || event.world.playerEntities.isEmpty()) return;
            World world = event.world;
            GardenerNests data = get(world);
            if(GeminiConfig.enabled && world.provider.dimensionId == 0 && world.getTotalWorldTime() % 1200 == 0) data.observeResidences(world);
            data.tick((WorldServer) world);
        }
    }
    private void tick(WorldServer world) {
        if(nests.isEmpty()) return;
        // Population timers run independently of the round-robin construction budget.
        if(world.getTotalWorldTime() % 20 == 0) for(Nest nest : nests) {
            if(!nest.built || !residentActivity(world, nest)) continue;
            if(nest.spawnSeconds > 0) nest.spawnSeconds--;
            if(nest.spawnSeconds == 0) { spawn(world, nest); nest.spawnSeconds = GeminiConfig.gardenerReplenishSeconds; }
            markDirty();
        }
        for(int count = 0; count < nests.size(); count++) {
            Nest nest = nests.get((nextJob++) % nests.size());
            if(nextJob < 0) nextJob = 0;
            if(!nest.debug && !(active(world) && world.getGameRules().getGameRuleBooleanValue("mobGriefing"))) continue;
            if(nest.failed && world.getTotalWorldTime() % 200 != 0) continue;
            if(!nest.built || nest.accessVersion == 0) {
                int cursor = nest.cursor, phase = nest.phase;
                build(world, nest);
                if(cursor != nest.cursor || phase != nest.phase || nest.built && nest.accessVersion > 0) {
                    nest.idle = 0; nest.failed = false;
                } else if(++nest.idle >= 1200) { nest.failed = true; nest.recovering = true; }
                if(nest.idle > 0) GeminiInvasionManager.debug(world, "nest-build-" + nest.x + ":" + nest.z,
                        "Nest construction " + (nest.failed ? "stalled; retrying with backoff" : "waiting")
                        + " at " + nest.x + "," + nest.y + "," + nest.z + "; pass=" + nest.phase
                        + "; cursor=" + nest.cursor + "; reason=" + nest.reason);
                markDirty(); return;
            }
        }
    }
    private static boolean residentActivity(World world, Nest nest) {
        return active(world) || nest.debug && GeminiConfig.enabled && GeminiConfig.gardenerEnabled
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }
    /** Load at most one missing chunk in a tick, with no tickets and no full-footprint requirement. */
    static boolean prepareChunk(WorldServer world, int x, int z) {
        for(int cx = (x >> 4) - 1; cx <= (x >> 4) + 1; cx++)
            for(int cz = (z >> 4) - 1; cz <= (z >> 4) + 1; cz++)
                if(!world.getChunkProvider().chunkExists(cx, cz)) {
                    world.getChunkFromChunkCoords(cx, cz); return false;
                }
        return true;
    }
    private static boolean constructionIntersects(World world, AxisAlignedBB box) {
        for(Nest nest : get(world).nests) if((!nest.built || nest.accessVersion == 0) && nest.bounds().intersectsWith(box)) return true;
        return WretchCamps.constructionIntersects(world, box);
    }
    /** Relocate one obstructing riding group per tick; never edit a still occupied solid cell. */
    static String clearConstructionCell(WorldServer world, int x, int y, int z, AxisAlignedBB volume) {
        AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        for(Object entry : world.getEntitiesWithinAABBExcludingEntity(null, cell)) {
            Entity entity = (Entity) entry;
            if(entity.isDead) continue;
            String identity = entity.getClass().getSimpleName() + " UUID=" + entity.getUniqueID()
                    + (entity instanceof EntityPlayer ? " player=" + entity.getCommandSenderName() : "");
            boolean moved = relocate(world, entity, volume);
            String reason = "solid placement at " + x + "," + y + "," + z + ": " + identity
                    + (moved ? " relocated outside construction volume; retrying" : " has no safe loaded relocation; retrying");
            GeminiInvasionManager.debug(world, "structure-obstruction-" + entity.getUniqueID(), reason);
            return reason;
        }
        return null;
    }
    private static boolean relocate(WorldServer world, Entity entity, AxisAlignedBB volume) {
        Entity root = entity;
        Set<Entity> seen = new LinkedHashSet<Entity>();
        while(root.ridingEntity != null && seen.add(root) && seen.size() < 16) root = root.ridingEntity;
        if(root.ridingEntity != null) return false;
        List<Entity> group = new ArrayList<Entity>();
        AxisAlignedBB box = root.boundingBox.copy();
        for(Entity rider = root; rider != null; rider = rider.riddenByEntity) {
            if(group.contains(rider) || group.size() >= 16) return false;
            group.add(rider);
            box = AxisAlignedBB.getBoundingBox(Math.min(box.minX, rider.boundingBox.minX), Math.min(box.minY, rider.boundingBox.minY),
                    Math.min(box.minZ, rider.boundingBox.minZ), Math.max(box.maxX, rider.boundingBox.maxX),
                    Math.max(box.maxY, rider.boundingBox.maxY), Math.max(box.maxZ, rider.boundingBox.maxZ));
        }
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double halfX = (box.maxX - box.minX) / 2 + 2, halfZ = (box.maxZ - box.minZ) / 2 + 2;
        // Prefer the closest side, then bounded alternatives along each side. No chunk requests.
        List<double[]> candidates = new ArrayList<double[]>();
        for(int offset : new int[] {0, -16, 16, -32, 32}) {
            candidates.add(new double[] {volume.minX - halfX, MathHelper.clamp_double(cz, volume.minZ, volume.maxZ) + offset});
            candidates.add(new double[] {volume.maxX + halfX, MathHelper.clamp_double(cz, volume.minZ, volume.maxZ) + offset});
            candidates.add(new double[] {MathHelper.clamp_double(cx, volume.minX, volume.maxX) + offset, volume.minZ - halfZ});
            candidates.add(new double[] {MathHelper.clamp_double(cx, volume.minX, volume.maxX) + offset, volume.maxZ + halfZ});
        }
        final double originX = cx, originZ = cz;
        java.util.Collections.sort(candidates, new java.util.Comparator<double[]>() {
            @Override public int compare(double[] a, double[] b) {
                return Double.compare((a[0] - originX) * (a[0] - originX) + (a[1] - originZ) * (a[1] - originZ),
                        (b[0] - originX) * (b[0] - originX) + (b[1] - originZ) * (b[1] - originZ));
            }
        });
        for(double[] target : candidates) {
            double dx = target[0] - cx, dz = target[1] - cz;
            AxisAlignedBB horizontal = box.getOffsetBoundingBox(dx, 0, dz);
            if(horizontal.minX < -29999984 || horizontal.maxX >= 29999984 || horizontal.minZ < -29999984 || horizontal.maxZ >= 29999984
                    || !loadedVolume(world, horizontal.expand(1, 0, 1))) continue;
            int tx = MathHelper.floor_double(target[0]), tz = MathHelper.floor_double(target[1]);
            int top = Math.min(world.getActualHeight() - 1, world.getHeightValue(tx, tz));
            for(int floor = top; floor >= 1; floor--) {
                if(!World.doesBlockHaveSolidTopSurface(world, tx, floor, tz)) continue;
                double dy = floor + 1D - box.minY;
                AxisAlignedBB destination = box.getOffsetBoundingBox(dx, dy, dz);
                if(destination.maxY >= world.getActualHeight() || destination.intersectsWith(volume)
                        || constructionIntersects(world, destination) || !world.getCollidingBoundingBoxes(root, destination).isEmpty()
                        || world.isAnyLiquid(destination) || !world.checkNoEntityCollision(destination, root)) continue;
                boolean supported = true;
                for(double px : new double[] {destination.minX, destination.maxX - 0.001D})
                    for(double pz : new double[] {destination.minZ, destination.maxZ - 0.001D}) {
                        int bx = MathHelper.floor_double(px), bz = MathHelper.floor_double(pz);
                        Block support = world.getBlock(bx, floor, bz);
                        if(!World.doesBlockHaveSolidTopSurface(world, bx, floor, bz) || support == Blocks.cactus
                                || support instanceof net.minecraft.block.BlockFalling) supported = false;
                    }
                if(!supported) continue;
                boolean hazardous = false;
                for(int bx = MathHelper.floor_double(destination.minX); bx < MathHelper.ceiling_double_int(destination.maxX); bx++)
                    for(int by = floor + 1; by < MathHelper.ceiling_double_int(destination.maxY); by++)
                        for(int bz = MathHelper.floor_double(destination.minZ); bz < MathHelper.ceiling_double_int(destination.maxZ); bz++) {
                            Block block = world.getBlock(bx, by, bz);
                            if(block == Blocks.fire || block == Blocks.cactus) hazardous = true;
                        }
                if(hazardous) continue;
                for(Entity member : group) {
                    if(member instanceof net.minecraft.entity.EntityLivingBase)
                        ((net.minecraft.entity.EntityLivingBase) member).setPositionAndUpdate(member.posX + dx, member.posY + dy, member.posZ + dz);
                    else member.setPosition(member.posX + dx, member.posY + dy, member.posZ + dz);
                    member.motionX = member.motionY = member.motionZ = 0; member.fallDistance = 0;
                }
                return true;
            }
        }
        return false;
    }
    private void build(WorldServer world, Nest nest) {
        if(!nest.journalReady) {
            nest.journalReady = checkpoint(world, this);
            if(!nest.journalReady) { nest.failed = true; nest.reason = "could not save construction journal; terrain work paused"; return; }
        }
        String bounds = placementBounds(world, nest.x, nest.y, nest.z);
        if(bounds != null) { nest.reason = bounds; return; }
        if(nest.top < 0) {
            // Survey the entire pit and dismount, including canopy height; one missing chunk per tick.
            for(int x = nest.x + 40; x <= nest.x + 63; x++) for(int z = nest.z - 8; z <= nest.z + 8; z++)
                if(!prepareChunk(world, x, z)) { nest.reason = "loading access pit survey chunk"; return; }
            int top = nest.y + HEIGHT + 2;
            for(int x = nest.x + 40; x <= nest.x + 63; x++) for(int z = nest.z - 8; z <= nest.z + 8; z++)
                top = Math.max(top, world.getHeightValue(x, z));
            if(top + 12 >= world.getActualHeight()) { nest.reason = "surface pit exceeds world height"; return; }
            nest.top = top;
            nest.journalReady = false; // Persist the original survey before any excavation.
            return;
        }
        int left = (nest.x - HALF) >> 4, bottom = (nest.z - HALF) >> 4;
        int width = ((nest.x + 63) >> 4) - left + 1, depth = ((nest.z + HALF) >> 4) - bottom + 1;
        int chamberCells = nest.built ? 0 : width * depth * 256 * 27;
        int accessStart = nest.layout >= LAYOUT ? 30 : 0;
        int accessWidth = 64 - accessStart;
        int total = chamberCells + accessWidth * 17 * (nest.top + 12 - (nest.y - 1));
        int edits = 0;
        placementDepth++;
        try {
            for(int scans = 0; scans < SCANS_PER_TICK && edits < EDITS_PER_TICK; scans++) {
                if(nest.cursor >= total) {
                    if(nest.phase == 4 && !nest.built && !prepareFirstResident(world, nest)) return;
                    nest.cursor = 0;
                    if(++nest.phase < 5) { nest.reason = "construction pass " + nest.phase + "/4"; return; }
                    boolean completedNest = !nest.built;
                    nest.built = true; nest.accessVersion = 1; nest.phase = 2; nest.reason = "none"; nest.recovering = nest.failed = false;
                    if(!checkpoint(world, this)) {
                        nest.built = !completedNest; nest.accessVersion = 0; nest.phase = 4; nest.cursor = total;
                        nest.failed = nest.recovering = true; nest.reason = "could not save completion record; retrying"; return;
                    }
                    if(nest.upgrading) { nest.upgrading = false; updateLoadedHomes(world, nest); }
                    if(completedNest) {
                        if(!nest.debug) schedule.success(this, world, "nest", nest.x, nest.y, nest.z, GeminiConfig.gardenerNestIntervalSeconds);
                        spawn(world, nest); // First admission happens on the construction completion tick.
                        nest.spawnSeconds = GeminiConfig.gardenerReplenishSeconds;
                    }
                    return;
                }
                int index = nest.cursor, x, y, z;
                if(index < chamberCells) {
                    int chunk = index / (256 * 27), local = index % (256 * 27);
                    x = ((left + chunk % width) << 4) + local / 27 % 16;
                    z = ((bottom + chunk / width) << 4) + local / (27 * 16);
                    y = nest.y - 1 + local % 27;
                } else {
                    int access = index - chamberCells;
                    x = nest.x + accessStart + access % accessWidth;
                    z = nest.z - 8 + access / accessWidth % 17;
                    y = nest.y - 1 + access / (accessWidth * 17);
                }
                Block block = jobBlock(nest, x - nest.x, y - nest.y, z - nest.z);
                if(block == null) { nest.cursor++; continue; }
                if(!prepareChunk(world, x, z)) { nest.reason = "loading construction chunk at " + (x >> 4) + "," + (z >> 4); return; }
                Block old = world.getBlock(x, y, z);
                if(old == Blocks.bedrock) { nest.reason = "bedrock at " + x + "," + y + "," + z + "; construction paused"; return; }
                if(nest.phase == 0) { nest.cursor++; continue; } // Complete bedrock/boundary preflight before excavation.
                boolean decoration = block == ModBlocks.mockery_growth;
                boolean selected = nest.phase == 1 ? block != Blocks.air && !decoration
                        : nest.phase == 2 ? block == Blocks.air || decoration : nest.phase == 3 ? decoration : true;
                Block wanted = nest.phase == 2 && decoration ? Blocks.air : block;
                int wantedMeta = 0;
                if(selected && (old != wanted || world.getBlockMetadata(x, y, z) != wantedMeta)) {
                    if(wanted.getMaterial().blocksMovement()) {
                        String obstruction = clearConstructionCell(world, x, y, z, nest.bounds());
                        if(obstruction != null) { nest.reason = obstruction; nest.recovering = true; return; }
                    }
                    // Verification repairs changed cells through the same bounded path.
                    if(world.getTileEntity(x, y, z) != null) world.removeTileEntity(x, y, z);
                    if(!world.setBlock(x, y, z, wanted, wantedMeta, 2)) {
                        nest.reason = "block insertion rejected at " + x + "," + y + "," + z; return;
                    }
                    edits++;
                    // Interior air needs no hundreds-of-thousands-wide neighbor publication.
                    if(wanted != Blocks.air) world.notifyBlocksOfNeighborChange(x, y, z, wanted);
                    if(world.getBlock(x, y, z) != wanted || world.getBlockMetadata(x, y, z) != wantedMeta) {
                        nest.reason = "block callback changed " + x + "," + y + "," + z; return;
                    }
                }
                nest.cursor++;
            }
        } catch(RuntimeException failure) {
            if(!nest.reason.startsWith("block/chunk callback exception"))
                com.hbm.main.MainRegistry.logger.error("Gardener nest construction paused", failure);
            nest.reason = "block/chunk callback exception; job retained (see server log)";
        } finally { placementDepth--; }
    }
    private static boolean prepareFirstResident(WorldServer world, Nest nest) {
        int[] home = HOMES[0];
        for(int cx = (nest.x + home[0] - 21) >> 4; cx <= (nest.x + home[0] + 21) >> 4; cx++)
            for(int cz = (nest.z + home[1] - 21) >> 4; cz <= (nest.z + home[1] + 21) >> 4; cz++)
                if(!world.getChunkProvider().chunkExists(cx, cz)) {
                    world.getChunkFromChunkCoords(cx, cz); nest.reason = "loading first resident clearance chunk"; return false;
                }
        return true;
    }
    /** Discover a small loaded origin; the saved job requests its actual footprint incrementally. */
    private String generateNearPlayers(WorldServer world) {
        List<EntityPlayer> players = GeminiStructureSchedule.players(world);
        Map<String, Integer> rejected = new LinkedHashMap<String, Integer>();
        int start = world.rand.nextInt(players.size());
        for(int attempt = 0; attempt < GeminiStructureSchedule.ATTEMPTS; attempt++) {
            EntityPlayer player = players.get((start + attempt) % players.size());
            int homeX = MathHelper.floor_double(player.posX), homeZ = MathHelper.floor_double(player.posZ);
            for(Residence area : residences) if(area.owner.equals(player.getUniqueID()) && area.established
                    && area.distance(player.posX, player.posZ) <= 32D * 32D) { homeX = area.x; homeZ = area.z; break; }
            if(areaNests(homeX, homeZ) >= GeminiConfig.gardenerNestsPerArea) {
                countRejection(rejected, "survival area nest limit"); continue;
            }
            double angle = world.rand.nextDouble() * Math.PI * 2;
            int radius = attempt < 16 ? GeminiConfig.gardenerNestMinDistance + world.rand.nextInt(
                    GeminiConfig.gardenerNestMaxDistance - GeminiConfig.gardenerNestMinDistance + 1)
                    : world.rand.nextInt(Math.min(64, GeminiConfig.gardenerNestMaxDistance) + 1);
            int x = homeX + (int) (Math.cos(angle) * radius), z = homeZ + (int) (Math.sin(angle) * radius);
            int y = 9 + world.rand.nextInt(4); // air level: floor is always Y=8..11, independent of player/surface height.
            schedule.examined();
            if(placementBounds(world, x, y, z) != null) { countRejection(rejected, "world boundaries"); continue; }
            if(tooClose(x, z)) { schedule.spacing(); countRejection(rejected, "nest footprints overlap"); continue; }
            if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                schedule.unloaded(); countRejection(rejected, "origin chunk unloaded"); continue;
            }
            Nest nest = new Nest(x, y, z); nest.ensureCapacity();
            nest.homeX = homeX; nest.homeZ = homeZ; nest.hasHome = true;
            nests.add(nest); schedule.plan(); markDirty(); nest.journalReady = checkpoint(world, this);
            if(!nest.journalReady) { nest.failed = true; nest.reason = "could not save construction journal; terrain work paused"; return nest.reason; }
            return null; // The queued reservation is not a registered structure until verified complete.
        }
        return schedule.failure(rejected);
    }
    private boolean tooClose(int x, int z) {
        for(Nest nest : nests) {
            double dx = (double) x - nest.x, dz = (double) z - nest.z;
            if(Math.abs(dx) < 128D && Math.abs(dz) < 112D) return true;
        }
        return false;
    }
    private int areaNests(int x, int z) {
        int count = 0;
        double radius = GeminiConfig.gardenerNestMaxDistance + 32D;
        for(Nest nest : nests) {
            if(nest.failed && !nest.built) continue;
            double dx = (double) nest.x - x, dz = (double) nest.z - z;
            if(dx * dx + dz * dz <= radius * radius) count++;
        }
        return count;
    }
    /** A bed is explicit residence evidence. Otherwise require twenty loaded minutes near recorded construction. */
    private void observeResidences(World world) {
        int examined = 0;
        for(Object entry : world.playerEntities) {
            if(++examined > 128) break;
            EntityPlayer player = (EntityPlayer) entry;
            if(!player.isEntityAlive() || player.capabilities.isCreativeMode) continue;
            Residence area = null;
            for(Residence saved : residences) if(saved.owner.equals(player.getUniqueID())) { area = saved; break; }
            if(area == null) {
                if(residences.size() >= 128) continue;
                area = new Residence(player.getUniqueID(), MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ));
                residences.add(area);
            }
            ChunkCoordinates bed = player.getBedLocation(world.provider.dimensionId);
            if(bed != null && world.blockExists(bed.posX, bed.posY, bed.posZ) && world.getBlock(bed.posX, bed.posY, bed.posZ) == Blocks.bed) {
                // The first established home stays fixed, including when an explorer sleeps in a temporary bed.
                if(!area.established) {
                    area.x = bed.posX; area.z = bed.posZ; area.established = true;
                }
            } else if(!area.established) {
                if(area.distance(player.posX, player.posZ) > 48D * 48D) {
                    area.x = MathHelper.floor_double(player.posX); area.z = MathHelper.floor_double(player.posZ); area.dwell = 0;
                }
                // World spawn is a stable fallback only after the player actually settles nearby.
                ChunkCoordinates spawn = world.getSpawnPoint();
                AxisAlignedBB work = AxisAlignedBB.getBoundingBox(area.x - 24, 1, area.z - 24, area.x + 24, world.getHeight() - 1, area.z + 24);
                if(WretchCamps.hasPlayerPlacement(world, work)) area.dwell += 1200;
                if(area.dwell >= 24000) {
                    area.established = true;
                    if(area.distance(spawn.posX, spawn.posZ) <= 32D * 32D) { area.x = spawn.posX; area.z = spawn.posZ; }
                }
            }
            if(area.established) for(Residence shared : residences) {
                if(shared != area && shared.established && area.distance(shared.x, shared.z) < 96D * 96D) {
                    area.x = shared.x; area.z = shared.z; break;
                }
            }
        }
        markDirty();
    }
    private static final class Residence {
        final UUID owner;
        int x, z, dwell;
        boolean established;
        Residence(UUID owner, int x, int z) { this.owner = owner; this.x = x; this.z = z; }
        double distance(double px, double pz) { double dx = px - x, dz = pz - z; return dx * dx + dz * dz; }
    }
    /** Registered starts still protect ordinary ongoing mob damage, independently of structure placement. */
    public static boolean overlapsStructure(World world, AxisAlignedBB box) {
        if(structureLimitExceeded) return true;
        StructureBoundingBox area = new StructureBoundingBox(MathHelper.floor_double(box.minX), MathHelper.floor_double(box.minY),
                MathHelper.floor_double(box.minZ), MathHelper.floor_double(box.maxX), MathHelper.ceiling_double_int(box.maxY), MathHelper.floor_double(box.maxZ));
        int examined = 0;
        for(String name : STRUCTURES) {
            MapGenStructureData structures = (MapGenStructureData) world.perWorldStorage.loadData(MapGenStructureData.class, name);
            if(structures == null) continue;
            NBTTagCompound features = structures.func_143041_a();
            for(Object key : features.func_150296_c()) {
                if(++examined > 4096) return true; // Fail closed in huge saves instead of an unbounded structure search.
                NBTTagCompound feature = features.getCompoundTag((String) key);
                int[] bounds = feature.getIntArray("BB");
                if(bounds.length == 6 && new StructureBoundingBox(bounds).intersectsWith(area)) return true;
            }
        }
        return false;
    }
    private void spawn(WorldServer world, Nest nest) {
        if(!nest.built || nest.accessVersion == 0) return;
        nest.ensureCapacity();
        if(!residentActivity(world, nest)) { nest.reason = "resident activity/configuration gate closed"; return; }
        String rejected = null;
        for(int slot = 0; slot < nest.residents.size(); slot++) {
            Resident resident = nest.residents.get(slot);
            net.minecraft.entity.EntityLiving found = GeminiPopulation.find(world, resident.id);
            if(found instanceof EntityGardener) {
                // Native death animation keeps the entity loaded for twenty ticks after LivingDeathEvent.
                if(found.isEntityAlive()) {
                    resident.state = 1; assignHome((EntityGardener) found, nest, slot, false);
                } else if(found.getHealth() <= 0) {
                    resident.state = 2; resident.reason = "defeated";
                }
                continue;
            }
            if(resident.state == 1) continue; // Unloaded survivors still own their slot and count globally.
            if(resident.state == 2) { resident = new Resident(); nest.residents.set(slot, resident); }
            String admission = GeminiPopulation.gardenerAdmissionReason(world);
            if(admission != null) { resident.reason = admission; rejected = admission; continue; }
            EntityGardener boss = new EntityGardener(world);
            if(nest.debug) GeminiSpawning.markStructureDebugSpawn(boss);
            boss.setNestIdentity(resident.id); assignHome(boss, nest, slot, true);
            String reason = boss.spawnRejection();
            if(reason == null && (!world.spawnEntityInWorld(boss) || !boss.addedToChunk || world.getEntityByID(boss.getEntityId()) != boss))
                reason = "Forge/world entity insertion rejected";
            if(reason == null) {
                resident.state = 1; resident.reason = "none"; remember(boss);
                nest.reason = "none";
                GeminiInvasionManager.debug(world, "gardener-spawn", "Resident " + (slot + 1) + " entered nest at " + boss.posX + "," + boss.posY + "," + boss.posZ);
                return; // At most one new resident per opportunity.
            } else {
                // Some join handlers reserve membership before a later Forge listener vetoes insertion.
                GeminiPopulation.forgetFailedSpawn(boss);
                resident.reason = reason; rejected = "resident " + (slot + 1) + ": " + reason;
            }
        }
        nest.reason = rejected == null ? "none" : rejected;
    }
    private static void assignHome(EntityGardener boss, Nest nest, int slot, boolean relocate) {
        int[] home = nest.layout >= LAYOUT ? HOMES[slot % HOMES.length] : new int[] {0, 0};
        boss.assignNest(nest.x + 0.5D, nest.y, nest.z + 0.5D,
                nest.x + home[0] + 0.5D, nest.z + home[1] + 0.5D, nest.layout >= LAYOUT, relocate);
        if(nest.hasHome) boss.setHuntingGround(nest.homeX + 0.5D, nest.homeZ + 0.5D);
    }
    private static void updateLoadedHomes(World world, Nest nest) {
        for(int slot = 0; slot < nest.residents.size(); slot++) {
            net.minecraft.entity.EntityLiving mob = GeminiPopulation.find(world, nest.residents.get(slot).id);
            // Update anchors without replacing entities, health, combat or captive states.
            if(mob instanceof EntityGardener) assignHome((EntityGardener) mob, nest, slot, false);
        }
    }
    /** Entity NBT loads independently of structure data; rebind saved survivors even when the registry tick ran first. */
    public static void bindResident(EntityGardener mob) {
        if(mob.worldObj.isRemote || mob.isOcean()) return;
        GardenerNests data = get(mob.worldObj);
        for(Nest nest : data.nests) if(nest.built) for(int slot = 0; slot < nest.residents.size(); slot++)
            if(nest.residents.get(slot).id.equals(mob.getUniqueID())) { assignHome(mob, nest, slot, false); return; }
    }
    public static void defeated(EntityGardener mob) {
        if(mob.worldObj.isRemote || mob.isOcean()) return;
        GardenerNests data = get(mob.worldObj);
        if(!data.censusDone && data.censusRemoved.add(mob.getUniqueID())) data.markDirty();
        if(data.living.remove(mob.getUniqueID())) data.markDirty();
        for(Nest nest : data.nests) for(Resident resident : nest.residents) if(resident.id.equals(mob.getUniqueID())) {
            if(resident.state != 2) {
                resident.state = 2; resident.reason = "defeated";
                nest.spawnSeconds = GeminiConfig.gardenerReplenishSeconds; data.markDirty();
            }
            return;
        }
    }
    private String nestStatus(World world) {
        StringBuilder result = new StringBuilder();
        for(Nest nest : nests) if(nest.built) result.append(" [").append(nest.x).append(',').append(nest.y).append(',').append(nest.z)
                .append(' ').append(describe(world, nest)).append(']');
        return result.length() == 0 ? "none" : result.toString();
    }
    private String jobStatus(World world) {
        StringBuilder result = new StringBuilder();
        for(Nest nest : nests) if(!nest.built || nest.accessVersion == 0) result.append(" [").append(nest.x).append(',').append(nest.y).append(',').append(nest.z)
                .append(" layout=").append(nest.layout).append(" pass=").append(nest.phase).append(" cursor=").append(nest.cursor)
                .append(" reason=").append(nest.reason).append(']');
        return result.length() == 0 ? "none" : result.toString();
    }
    private static String describe(World world, Nest nest) {
        int spawned = 0, pending = 0, defeated = 0;
        for(Resident resident : nest.residents) { if(resident.state == 0) pending++; else if(resident.state == 1) spawned++; else defeated++; }
        return "construction=" + (nest.failed ? "failed" : nest.built && nest.accessVersion > 0 ? "completed"
                : nest.recovering || nest.built ? "recovering" : nest.phase == 0 && nest.cursor == 0 ? "queued" : "constructing")
                + "; pass=" + nest.phase + "; cursor=" + nest.cursor + "; reason=" + nest.reason
                + "; floor Y=" + (nest.y - 1) + "; chamber=" + (nest.layout >= LAYOUT ? "~105x99, interior height 20-24" : "legacy 29x29x15")
                + "; access=" + (nest.accessVersion > 0 ? "15x15 open pit" : "pit conversion pending")
                + "; replenish in=" + nest.spawnSeconds + " active seconds"
                + "; layout=" + nest.layout + "; capacity=" + nest.residents.size() + " (configured=" + GeminiConfig.gardenerResidents + ")"
                + "; spawned=" + spawned + "; pending=" + pending + "; defeated slots=" + defeated
                + "; chunks available=" + loadedVolume(world, nest.bounds()) + "; last rejection=" + nest.reason;
    }
    private static final class Resident {
        UUID id = UUID.randomUUID();
        int state; // pending, living (including unloaded), defeated; only new UUIDs replenish
        String reason = "none";
    }
    private static final class Nest {
        final int x, z;
        int y, layout = LAYOUT;
        int phase, cursor, top = -1;
        int idle;
        int accessVersion, spawnSeconds = GeminiConfig.gardenerReplenishSeconds;
        int homeX, homeZ;
        boolean hasHome, built, debug, upgrading;
        boolean failed, recovering;
        boolean journalReady;
        String reason = "queued";
        final List<Resident> residents = new ArrayList<Resident>();
        Nest(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        void ensureCapacity() { while(residents.size() < GeminiConfig.gardenerResidents) residents.add(new Resident()); }
        AxisAlignedBB bounds() {
            return layout >= LAYOUT ? AxisAlignedBB.getBoundingBox(x - HALF, y - 1, z - HALF, x + 64, Math.max(y + 26, top + 12), z + HALF + 1)
                    : AxisAlignedBB.getBoundingBox(x - 14, y - 1, z - 14, x + 15, y + 13, z + 15);
        }
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        living.clear();
        censusDone = nbt.getBoolean("GardenerCensusDone");
        censusRemoved.clear();
        NBTTagList removed = nbt.getTagList("CensusRemoved", 10);
        for(int i = 0; i < removed.tagCount(); i++) {
            try { censusRemoved.add(UUID.fromString(removed.getCompoundTagAt(i).getString("Id"))); }
            catch(IllegalArgumentException ignored) { }
        }
        NBTTagList identities = nbt.getTagList("LivingGardeners", 10);
        for(int i = 0; i < identities.tagCount(); i++) {
            try { living.add(UUID.fromString(identities.getCompoundTagAt(i).getString("Id"))); }
            catch(IllegalArgumentException ignored) { }
        }
        residences.clear();
        NBTTagList homes = nbt.getTagList("Residences", 10);
        for(int i = 0; i < Math.min(128, homes.tagCount()); i++) {
            NBTTagCompound tag = homes.getCompoundTagAt(i);
            try {
                Residence area = new Residence(UUID.fromString(tag.getString("Owner")), tag.getInteger("X"), tag.getInteger("Z"));
                if(Math.abs((long) area.x) > 29999900 || Math.abs((long) area.z) > 29999900) continue;
                area.established = tag.getBoolean("Established"); area.dwell = MathHelper.clamp_int(tag.getInteger("Dwell"), 0, 24000);
                residences.add(area);
            } catch(IllegalArgumentException ignored) { }
        }
        nests.clear(); schedule.read(nbt);
        NBTTagList list = nbt.getTagList("Nests", 10);
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Nest nest = new Nest(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            if(nest.y < 1 || nest.y > 243 || Math.abs((long) nest.x) > 29999984 || Math.abs((long) nest.z) > 29999984) continue;
            nest.layout = tag.getInteger("Layout");
            nest.phase = MathHelper.clamp_int(tag.getInteger("Phase"), 0, 4);
            nest.built = tag.getBoolean("Built") || nest.layout < LAYOUT && (nest.phase == 2 || nest.phase == 3);
            nest.debug = tag.getBoolean("Debug"); nest.upgrading = tag.getBoolean("Upgrading");
            nest.cursor = MathHelper.clamp_int(tag.getInteger("Cursor"), 0, 1000000);
            nest.top = tag.hasKey("Top") ? MathHelper.clamp_int(tag.getInteger("Top"), -1, 254) : -1;
            nest.hasHome = tag.getBoolean("HasHome"); nest.homeX = tag.getInteger("HomeX"); nest.homeZ = tag.getInteger("HomeZ");
            nest.reason = tag.hasKey("Reason") ? tag.getString("Reason") : "legacy record retained";
            nest.idle = MathHelper.clamp_int(tag.getInteger("ConstructionIdle"), 0, 1200);
            nest.failed = tag.getBoolean("ConstructionFailed");
            nest.journalReady = true; // This record was read from the native saved-data file.
            NBTTagList residents = tag.getTagList("Residents", 10);
            if(tag.hasKey("Residents")) {
                for(int r = 0; r < residents.tagCount(); r++) {
                    NBTTagCompound entry = residents.getCompoundTagAt(r);
                    Resident resident = new Resident();
                    try { resident.id = UUID.fromString(entry.getString("Id")); }
                    catch(IllegalArgumentException invalid) { continue; }
                    resident.state = MathHelper.clamp_int(entry.getInteger("State"), 0, 2);
                    resident.reason = entry.getString("Reason"); nest.residents.add(resident);
                }
            } else {
                Resident resident = new Resident();
                try { resident.id = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { }
                // A completed legacy identity must never be respawned merely because its chunk is unloaded.
                resident.state = nest.phase == 3 ? 1 : 0;
                nest.residents.add(resident);
            }
            nest.accessVersion = tag.getInteger("AccessVersion");
            nest.recovering = !nest.built || nest.accessVersion == 0;
            // A reload can replay a chunk save older than its job cursor. Reverify the
            // final pass from its start; keep excavation and all resident identities.
            if(nest.phase == 4) nest.cursor = 0;
            nest.spawnSeconds = tag.hasKey("SpawnSeconds") ? MathHelper.clamp_int(tag.getInteger("SpawnSeconds"), 0, 86400)
                    : GeminiConfig.gardenerReplenishSeconds;
            if(nest.accessVersion == 0 && !tag.getBoolean("PitJob")) {
                // Old cursors enumerate a different volume. Restart only the access job for built nests.
                nest.phase = nest.cursor = 0; nest.top = -1;
                if(!nest.built) nest.layout = LAYOUT;
            }
            for(Resident resident : nest.residents) if(resident.state == 1) living.add(resident.id);
            nests.add(nest);
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("GardenerCensusDone", censusDone);
        NBTTagList removed = new NBTTagList();
        for(UUID id : censusRemoved) { NBTTagCompound tag = new NBTTagCompound(); tag.setString("Id", id.toString()); removed.appendTag(tag); }
        nbt.setTag("CensusRemoved", removed);
        NBTTagList identities = new NBTTagList();
        for(UUID id : living) { NBTTagCompound tag = new NBTTagCompound(); tag.setString("Id", id.toString()); identities.appendTag(tag); }
        nbt.setTag("LivingGardeners", identities);
        NBTTagList homes = new NBTTagList();
        for(Residence area : residences) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Owner", area.owner.toString());
            tag.setInteger("X", area.x); tag.setInteger("Z", area.z); tag.setInteger("Dwell", area.dwell);
            tag.setBoolean("Established", area.established); homes.appendTag(tag);
        }
        nbt.setTag("Residences", homes); schedule.write(nbt);
        NBTTagList list = new NBTTagList();
        for(Nest nest : nests) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", nest.x); tag.setInteger("Y", nest.y); tag.setInteger("Z", nest.z);
            tag.setInteger("AccessVersion", nest.accessVersion); tag.setBoolean("PitJob", true);
            tag.setInteger("SpawnSeconds", nest.spawnSeconds);
            tag.setInteger("Phase", nest.phase); tag.setInteger("Cursor", nest.cursor); tag.setInteger("Top", nest.top);
            tag.setBoolean("Built", nest.built); tag.setBoolean("Debug", nest.debug); tag.setBoolean("Upgrading", nest.upgrading);
            tag.setInteger("Layout", nest.layout); tag.setString("Reason", nest.reason);
            tag.setInteger("ConstructionIdle", nest.idle); tag.setBoolean("ConstructionFailed", nest.failed);
            tag.setBoolean("HasHome", nest.hasHome); tag.setInteger("HomeX", nest.homeX); tag.setInteger("HomeZ", nest.homeZ);
            NBTTagList residents = new NBTTagList();
            for(Resident resident : nest.residents) {
                NBTTagCompound entry = new NBTTagCompound(); entry.setString("Id", resident.id.toString());
                entry.setInteger("State", resident.state); entry.setString("Reason", resident.reason); residents.appendTag(entry);
            }
            tag.setTag("Residents", residents); list.appendTag(tag);
        }
        nbt.setTag("Nests", list);
    }
}
