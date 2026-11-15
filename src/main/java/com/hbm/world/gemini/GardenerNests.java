package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityGardener;
import com.hbm.entity.mob.gemini.EntityOceanGardener;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

/** Post-unlock cave colonization, independent of and additive to the existing world generator. */
public final class GardenerNests extends WorldSavedData {
    private static final String NAME = "GeminiGardenerNests";
    private static final int WIDTH = 29, HEIGHT = 15, VOLUME = WIDTH * WIDTH * HEIGHT;
    private static final Set<String> STRUCTURES = new LinkedHashSet<String>(Arrays.asList(
            "Mineshaft", "Stronghold", "Village", "Temple", "NTMFeatures", "RTMLegacyStructures"));
    private static boolean structureLimitExceeded;
    private static int placementDepth;
    public static boolean placingNest() { return placementDepth > 0; }
    private final List<Nest> nests = new ArrayList<Nest>();
    private final List<Residence> residences = new ArrayList<Residence>();
    private int searchTicks = 1200;

    public GardenerNests(String name) { super(name); }
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
        if(world.isRemote || !(world instanceof WorldServer) || !active(world) || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
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
        if(!active(world) || !(tunnel ? canExcavate(world, boss, x, y, z) : safe(world, x, y, z, true))
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
                if(!boss.addedToChunk && ((!GeminiSpawning.manualAdmission(boss) && (!active(event.world) || !GeminiPopulation.canAdmit(event.world,
                        EntityGardener.class, boss, GeminiConfig.gardenerMaxLoaded))) || !boss.hasSpawnSpace())) event.setCanceled(true);
            }
        }
        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if(event.phase != TickEvent.Phase.END || event.world.isRemote || !(event.world instanceof WorldServer)
                    || event.world.provider.dimensionId != 0 || !GeminiConfig.enabled || event.world.playerEntities.isEmpty()) return;
            World world = event.world;
            GardenerNests data = (GardenerNests) world.mapStorage.loadData(GardenerNests.class, NAME);
            if(data == null) { data = new GardenerNests(NAME); world.mapStorage.setData(NAME, data); }
            if(world.getTotalWorldTime() % 1200 == 0) data.observeResidences(world);
            if(active(world) && world.getGameRules().getGameRuleBooleanValue("mobGriefing")) data.tick((WorldServer) world);
        }
    }
    private void tick(WorldServer world) {
        for(Iterator<Nest> it = nests.iterator(); it.hasNext();) {
            Nest nest = it.next();
            if(nest.phase >= 3) continue;
            // A population-limited ready nest must not starve construction or discovery for other survival areas.
            if(nest.phase == 2 && world.getTotalWorldTime() % 100 != 0) continue;
            if(!loaded(world, nest.bounds().expand(2, 0, 2))) continue; // Unloaded jobs must not starve other loaded nests.
            if(nest.phase == 0 && nest.cursor == 0 && (overlapsStructure(world, nest)
                    || WretchCamps.hasPlayerPlacement(world, nest.bounds()))) { it.remove(); markDirty(); return; }
            for(int work = 0; work < 32 && nest.phase < 2; work++) {
                int index = nest.cursor;
                int dx = index % WIDTH - 14, dz = index / WIDTH % WIDTH - 14, dy = index / (WIDTH * WIDTH) - 2;
                int x = nest.x + dx, y = nest.y + dy, z = nest.z + dz;
                if(!safe(world, x, y, z, true)) {
                    if(nest.phase == 0) it.remove(); else nest.phase = 4;
                    markDirty(); return;
                }
                if(nest.phase == 1) {
                    Block desired = desired(dx, dy, dz);
                    if(desired != null && world.getBlock(x, y, z) != desired
                            && !change(world, null, x, y, z, desired)) { nest.phase = 4; markDirty(); return; }
                }
                if(++nest.cursor >= VOLUME) {
                    nest.cursor = 0; nest.phase++;
                    if(nest.phase == 1) {
                        if(overlapsStructure(world, nest) || WretchCamps.hasPlayerPlacement(world, nest.bounds())
                                || !world.getEntitiesWithinAABBExcludingEntity(null, nest.bounds()).isEmpty()) {
                            it.remove(); markDirty(); return;
                        }
                    }
                }
            }
            if(nest.phase == 2 && world.getTotalWorldTime() % 100 == 0) spawn(world, nest);
            markDirty(); return; // One job and 32 coordinates per world tick, including validation.
        }
        if(nests.size() >= GeminiConfig.gardenerNestLimit) return;
        if(--searchTicks > 0) { if(searchTicks % 20 == 0) markDirty(); return; }
        searchTicks = 1200; markDirty();
        if(world.rand.nextInt(GeminiConfig.gardenerNestChance) != 0
                || world.rand.nextInt(100) >= GeminiInvasionManager.encounterPercent(world)) return;
        if(GeminiConfig.gardenerNestsPerArea == 0 || residences.isEmpty()) return;
        Residence area = residences.get(world.rand.nextInt(residences.size()));
        if(!area.established || areaNests(area) >= GeminiConfig.gardenerNestsPerArea) return;
        int sector = area.sector++ % GeminiConfig.gardenerNestsPerArea;
        // Eight loaded columns and at most 32 vertical cells per column per minute-long opportunity.
        for(int attempt = 0; attempt < 8; attempt++) {
            double angle = (sector + 0.2D + world.rand.nextDouble() * 0.6D) * Math.PI * 2D / GeminiConfig.gardenerNestsPerArea;
            int radius = GeminiConfig.gardenerNestMinDistance + world.rand.nextInt(
                    GeminiConfig.gardenerNestMaxDistance - GeminiConfig.gardenerNestMinDistance + 1);
            int x = MathHelper.floor_double(area.x + Math.cos(angle) * radius);
            int z = MathHelper.floor_double(area.z + Math.sin(angle) * radius);
            double distance = area.distance(x, z);
            if(distance < (double) GeminiConfig.gardenerNestMinDistance * GeminiConfig.gardenerNestMinDistance
                    || distance > (double) GeminiConfig.gardenerNestMaxDistance * GeminiConfig.gardenerNestMaxDistance) continue;
            if(!loaded(world, AxisAlignedBB.getBoundingBox(x - 17, 0, z - 17, x + 18, 64, z + 18)) || tooClose(x, z)) continue;
            for(int y = 42; y >= 12; y--) {
                if(world.getBlock(x, y, z) != Blocks.air || !natural(world.getBlock(x, y - 1, z))
                        || !world.getBlock(x, y - 1, z).getMaterial().blocksMovement()
                        || world.getBlock(x, y + 1, z) != Blocks.air || world.canBlockSeeTheSky(x, y + 13, z)) continue;
                Nest nest = new Nest(x, y, z);
                nest.homeX = area.x; nest.homeZ = area.z; nest.hasHome = true;
                if(overlapsStructure(world, nest) || WretchCamps.hasPlayerPlacement(world, nest.bounds())) break;
                nests.add(nest); markDirty(); return;
            }
        }
    }
    private boolean tooClose(int x, int z) {
        for(Nest nest : nests) {
            double dx = (double) x - nest.x, dz = (double) z - nest.z;
            if(dx * dx + dz * dz < 64D * 64D) return true;
        }
        return false;
    }
    private int areaNests(Residence area) {
        int count = 0;
        double radius = GeminiConfig.gardenerNestMaxDistance + 32D;
        for(Nest nest : nests) if(area.distance(nest.x, nest.z) <= radius * radius) count++;
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
        int x, z, dwell, sector;
        boolean established;
        Residence(UUID owner, int x, int z) { this.owner = owner; this.x = x; this.z = z; }
        double distance(double px, double pz) { double dx = px - x, dz = pz - z; return dx * dx + dz * dz; }
    }
    private static Block desired(int dx, int dy, int dz) {
        int radius = dx * dx + dz * dz;
        if(dy == -1 && radius <= 196) return ModBlocks.gardener_flesh;
        // Six radial runs of infectious growth lead out across the organic floor.
        double angle = Math.atan2(dz, dx);
        if(dy == 0 && radius >= 25 && radius <= 180
                && (Math.abs(Math.sin(angle * 3D)) < 0.32D || (dx * 3 + dz * 5) % 13 == 0)) return ModBlocks.mockery_growth;
        if(dy >= 0 && dy <= 11 && radius <= 196 - dy * dy) return Blocks.air;
        if(dy >= 1 && dy <= 12 && radius > 196 - dy * dy && radius <= 211 - dy * dy
                && ((dx + dz) & 1) == 0) return ModBlocks.gardener_flesh;
        return null;
    }
    /** Registered vanilla and NTM starts protect their full saved bounds, even when not presently populated. */
    private static boolean overlapsStructure(World world, Nest nest) {
        return overlapsStructure(world, AxisAlignedBB.getBoundingBox(nest.x - 16, nest.y - 3, nest.z - 16,
                nest.x + 16, nest.y + 14, nest.z + 16));
    }
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
        if(GeminiPopulation.find(world, nest.boss) != null) { nest.phase = 3; return; }
        if(!GeminiPopulation.canAdmit(world, EntityGardener.class, null, GeminiConfig.gardenerMaxLoaded)) return;
        if(!world.getEntitiesWithinAABB(EntityPlayer.class, nest.bounds()).isEmpty()) return;
        EntityGardener boss = new EntityGardener(world);
        boss.setNestIdentity(nest.boss); boss.anchor(nest.x + 0.5D, nest.y, nest.z + 0.5D);
        if(nest.hasHome) boss.setHuntingGround(nest.homeX + 0.5D, nest.homeZ + 0.5D);
        if(boss.hasSpawnSpace()
                && world.spawnEntityInWorld(boss)) nest.phase = 3;
        else if(++nest.retries >= 12) nest.phase = 4;
    }
    private static final class Nest {
        final int x, y, z;
        UUID boss = UUID.randomUUID();
        int phase, cursor, retries; // validate, build, spawn, complete, abandoned
        int homeX, homeZ;
        boolean hasHome;
        Nest(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        AxisAlignedBB bounds() { return AxisAlignedBB.getBoundingBox(x - 14, y - 2, z - 14, x + 15, y + 13, z + 15); }
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        residences.clear();
        NBTTagList homes = nbt.getTagList("Residences", 10);
        for(int i = 0; i < Math.min(128, homes.tagCount()); i++) {
            NBTTagCompound tag = homes.getCompoundTagAt(i);
            try {
                Residence area = new Residence(UUID.fromString(tag.getString("Owner")), tag.getInteger("X"), tag.getInteger("Z"));
                if(Math.abs((long) area.x) > 29999900 || Math.abs((long) area.z) > 29999900) continue;
                area.established = tag.getBoolean("Established"); area.dwell = MathHelper.clamp_int(tag.getInteger("Dwell"), 0, 24000);
                area.sector = MathHelper.clamp_int(tag.getInteger("Sector"), 0, 1000000); residences.add(area);
            } catch(IllegalArgumentException ignored) { }
        }
        nests.clear(); searchTicks = MathHelper.clamp_int(nbt.getInteger("SearchTicks"), 1, 1200);
        NBTTagList list = nbt.getTagList("Nests", 10);
        for(int i = 0; i < Math.min(64, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Nest nest = new Nest(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            if(nest.y < 12 || nest.y > 42 || Math.abs((long) nest.x) > 29999900 || Math.abs((long) nest.z) > 29999900) continue;
            nest.phase = MathHelper.clamp_int(tag.getInteger("Phase"), 0, 4);
            nest.cursor = MathHelper.clamp_int(tag.getInteger("Cursor"), 0, VOLUME - 1);
            nest.retries = MathHelper.clamp_int(tag.getInteger("Retries"), 0, 12);
            nest.hasHome = tag.getBoolean("HasHome"); nest.homeX = tag.getInteger("HomeX"); nest.homeZ = tag.getInteger("HomeZ");
            // Restart unfinished legacy jobs through full expanded validation; never respawn completed bosses.
            if(tag.getInteger("Layout") < 2 && nest.phase < 3) { nest.phase = 0; nest.cursor = 0; nest.retries = 0; }
            try { nest.boss = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { }
            nests.add(nest);
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList homes = new NBTTagList();
        for(Residence area : residences) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Owner", area.owner.toString());
            tag.setInteger("X", area.x); tag.setInteger("Z", area.z); tag.setInteger("Dwell", area.dwell);
            tag.setInteger("Sector", area.sector); tag.setBoolean("Established", area.established); homes.appendTag(tag);
        }
        nbt.setTag("Residences", homes);
        nbt.setInteger("SearchTicks", searchTicks);
        NBTTagList list = new NBTTagList();
        for(Nest nest : nests) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", nest.x); tag.setInteger("Y", nest.y); tag.setInteger("Z", nest.z);
            tag.setInteger("Phase", nest.phase); tag.setInteger("Cursor", nest.cursor); tag.setInteger("Retries", nest.retries);
            tag.setInteger("Layout", 2);
            tag.setBoolean("HasHome", nest.hasHome); tag.setInteger("HomeX", nest.homeX); tag.setInteger("HomeZ", nest.homeZ);
            tag.setString("Boss", nest.boss.toString()); list.appendTag(tag);
        }
        nbt.setTag("Nests", list);
    }
}
