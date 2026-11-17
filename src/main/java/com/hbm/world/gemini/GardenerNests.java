package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
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

/** Post-unlock underground colonization, additive to the existing world generator. */
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
    private final GeminiStructureSchedule schedule = new GeminiStructureSchedule();

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
        if(data != null) for(Nest nest : data.nests) if(nest.built)
            result.add(info(world, nest, nest.phase == 2 ? "waiting for Gardener" : nest.phase == 3 ? "Gardener spawned" : "abandoned"));
        return result;
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
        if(get(world).nests.size() >= GeminiConfig.gardenerNestLimit) return "nest limit reached";
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
                + "; gate=" + eligibility(world) + "; " + get(world).schedule.status(world);
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

    /** Build the canonical room, with explicit excavation and a bounded access shaft. */
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player, ChunkCoordinates requested) {
        if(!(player.worldObj instanceof WorldServer) || player.worldObj.isRemote)
            throw new net.minecraft.command.CommandException("Nest placement requires a server world.");
        WorldServer world = (WorldServer) player.worldObj;
        GardenerNests data = get(world);
        PlacementPlan best = null;
        Map<String, Integer> rejected = new LinkedHashMap<String, Integer>();
        int attempts = requested == null ? 8 : 1;
        for(int attempt = 0; attempt < attempts; attempt++) {
            double angle = attempt * 2.399963229728653D;
            int radius = attempt == 0 ? 0 : 16 + attempt * 7 % 33;
            int x = requested == null ? MathHelper.floor_double(player.posX + Math.cos(angle) * radius) : requested.posX;
            int z = requested == null ? MathHelper.floor_double(player.posZ + Math.sin(angle) * radius) : requested.posZ;
            int y;
            if(requested != null) y = requested.posY;
            else {
                if(x - 22 < -29999984 || x + 23 > 29999984 || z - 22 < -29999984 || z + 23 > 29999984) {
                    countRejection(rejected, "unsafe world boundaries"); continue;
                }
                if(!loadedVolume(world, AxisAlignedBB.getBoundingBox(x - 22, 0, z - 22, x + 23, world.getActualHeight(), z + 23))) {
                    countRejection(rejected, "unloaded chunks intersect the resident clearance area"); continue;
                }
                // Stay near an underground player at any safe elevation; descend below the surface for outdoor/flying players.
                y = MathHelper.clamp_int(Math.min(MathHelper.floor_double(player.boundingBox.minY) - attempt % 3 * 4,
                        surface(world, x + 15, z) - 15), 1, world.getActualHeight() - 13);
            }
            Nest nest = new Nest(x, y, z);
            PlacementPlan plan = plan(world, nest);
            String reason = plan.reason;
            if(reason == null) {
                best = plan; break;
            } else countRejection(rejected, reason);
        }
        if(best == null) throw searchFailure(world, "nest", requested != null, rejected);
        Nest nest = new Nest(best.x, best.y, best.z);
        boolean existing = false;
        for(Nest saved : data.nests) if(saved.x == nest.x && saved.y == nest.y && saved.z == nest.z) {
            nest = saved; existing = true; break;
        }
        String reason = place(world, best, true, StructurePlacementMode.FORCED_DEBUG);
        if(reason != null) throw GeminiInvasionManager.rejected(world, "nest", "Nest placement failed: " + reason);
        if(!nest.built || nest.phase == 4) { nest.phase = 2; nest.retries = 0; }
        nest.built = true; nest.debug = true;
        nest.homeX = MathHelper.floor_double(player.posX); nest.homeZ = MathHelper.floor_double(player.posZ); nest.hasHome = true;
        if(!existing) data.nests.add(nest);
        data.markDirty();
        String resident;
        try {
            if(nest.phase == 2) data.spawn(world, nest);
            resident = nest.phase == 3 ? (existing ? "Gardener identity/state retained; no duplicate initial spawn" : "Gardener spawned")
                    : "Gardener pending normal admission (settings, player, population or clearance/Forge veto)";
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Debug nest resident initialization failed", failure);
            resident = "resident initialization failed; registered nest retained for retry (see server log)";
        }
        data.markDirty();
        GeminiInvasionManager.debug(world, "nest-register", "Completed and registered debug nest at " + nest.x + ", " + nest.y + ", " + nest.z + ".");
        return info(world, nest, best.summary() + "; registered=" + (existing ? "existing" : "new") + "; " + resident);
    }

    private static PlacementPlan plan(WorldServer world, Nest nest) {
        PlacementPlan plan = new PlacementPlan(nest.x, nest.y, nest.z);
        if(nest.y < 1 || nest.y > world.getActualHeight() - 13) {
            plan.reason = "unsafe world height for complete nest; origin Y must be 1.." + (world.getActualHeight() - 13); return plan;
        }
        plan.regions.add(nest.bounds().expand(1, 0, 1));
        // Native spawn/AI reads the limb envelope beyond the excavated room.
        plan.readBounds = nest.bounds().expand(8, 0, 8);
        plan.reason = plan.checkBounds(world);
        if(plan.reason != null) return plan;
        // Also clear the chamber's own roof when the requested nest is above natural ground.
        int top = nest.y + 13;
        for(int x = nest.x + 13; x <= nest.x + 17; x++) for(int z = nest.z - 2; z <= nest.z + 2; z++)
            top = Math.max(top, surface(world, x, z));
        top = Math.min(top, world.getActualHeight() - 2);
        plan.regions.add(AxisAlignedBB.getBoundingBox(nest.x + 11, nest.y - 1, nest.z - 2, nest.x + 17, nest.y + 4, nest.z + 3));
        plan.regions.add(AxisAlignedBB.getBoundingBox(nest.x + 14, nest.y - 1, nest.z - 1, nest.x + 17, top + 2, nest.z + 2));
        plan.reason = plan.checkBounds(world);
        if(plan.reason != null) return plan;
        for(int dy = -1; dy <= 12; dy++) for(int dz = -15; dz <= 15; dz++) for(int dx = -15; dx <= 15; dx++) {
            Block block = desired(dx, dy, dz);
            // Close every face of the original chamber, retaining its organic ribs and floor.
            // Native terrain is not a seal: it may be liquid, gravel, a tile or empty space.
            if(block == null && besideInterior(dx, dy, dz)) block = Blocks.stone;
            if(block != null) plan.put(nest.x + dx, nest.y + dy, nest.z + dz, block, 0);
        }
        // Preparation only: a short doorway and a lined ladder shaft for player access.
        // The existing Gardener burrow controller retains its body-sized travel tunnels.
        for(int x = nest.x + 14; x <= nest.x + 16; x++) for(int z = nest.z - 1; z <= nest.z + 1; z++) {
            plan.put(x, nest.y - 1, z, Blocks.stone, 0);
            for(int y = nest.y; y <= top; y++)
                plan.put(x, y, z, x == nest.x + 15 && z == nest.z ? Blocks.ladder : Blocks.stone,
                        x == nest.x + 15 && z == nest.z ? 4 : 0);
        }
        // Metadata 4 attaches to the east wall; the top opens onto the surface.
        plan.put(nest.x + 16, top + 1, nest.z, Blocks.stone, 0);
        plan.put(nest.x + 15, top + 1, nest.z, Blocks.air, 0);
        plan.put(nest.x + 14, top, nest.z, Blocks.air, 0);
        plan.put(nest.x + 14, top + 1, nest.z, Blocks.air, 0);
        for(int x = nest.x + 11; x <= nest.x + 14; x++) for(int z = nest.z - 2; z <= nest.z + 2; z++) {
            for(int dy = -1; dy <= 3; dy++) {
                boolean lining = dy == -1 || dy == 3 || Math.abs(z - nest.z) == 2;
                Block room = desired(x - nest.x, dy, z - nest.z);
                // Line the passage only outside the room. Walls through its original open
                // volume would obstruct the Gardener's radial limbs at the entrance.
                if(lining && room != null) continue;
                plan.put(x, nest.y + dy, z, lining ? Blocks.stone : Blocks.air, 0);
            }
        }
        return plan;
    }

    private static boolean besideInterior(int x, int y, int z) {
        for(ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            Block neighbor = desired(x + side.offsetX, y + side.offsetY, z + side.offsetZ);
            if(neighbor == Blocks.air || neighbor == ModBlocks.mockery_growth) return true;
        }
        return false;
    }

    /** Cells of the existing layouts and their bounded terrain preparation. */
    static final class Placement {
        final int x, y, z, meta; final Block block;
        Placement(int x, int y, int z, Block block, int meta) { this.x = x; this.y = y; this.z = z; this.block = block; this.meta = meta; }
    }
    static final class PlacementPlan {
        final int x, y, z;
        final List<AxisAlignedBB> regions = new ArrayList<AxisAlignedBB>();
        final Map<Long, Placement> edits = new LinkedHashMap<Long, Placement>();
        AxisAlignedBB readBounds;
        String reason;
        int replaced;
        PlacementPlan(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        void put(int x, int y, int z, Block block, int meta) {
            long key = ((long) (x & 0x3FFFFFF) << 34) | ((long) (z & 0x3FFFFFF) << 8) | (y & 255);
            edits.put(key, new Placement(x, y, z, block, meta));
        }
        boolean contains(int x, int y, int z) {
            return y >= 0 && y < 256 && edits.containsKey(((long) (x & 0x3FFFFFF) << 34) | ((long) (z & 0x3FFFFFF) << 8) | y);
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
        String summary() {
            AxisAlignedBB box = regions.get(0);
            double minX = box.minX, minY = box.minY, minZ = box.minZ, maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
            for(AxisAlignedBB region : regions) {
                minX = Math.min(minX, region.minX); minY = Math.min(minY, region.minY); minZ = Math.min(minZ, region.minZ);
                maxX = Math.max(maxX, region.maxX); maxY = Math.max(maxY, region.maxY); maxZ = Math.max(maxZ, region.maxZ);
            }
            return "mode=forced debug; dimensions=" + (int) (maxX - minX) + "x" + (int) (maxY - minY) + "x" + (int) (maxZ - minZ)
                    + " (including preparation/access); replaced blocks=" + replaced;
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

    static String place(WorldServer world, PlacementPlan preparation, boolean nest, StructurePlacementMode mode) {
        if(preparation.reason != null) return preparation.reason;
        String bounds = preparation.checkBounds(world);
        if(bounds != null) return bounds;
        if(mode == StructurePlacementMode.NATURAL && (!world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !(nest ? active(world) : WretchCamps.active(world)))) return "natural generation gate changed";
        List<Placement> cells = preparation.cells();
        List<Placement> changed = new ArrayList<Placement>();
        // Validate every write before editing. Occupancy, ownership, hardness and mining permissions
        // are intentionally irrelevant to invasive structure generation in either mode.
        for(Placement cell : cells) {
            if(cell.y < 0 || cell.y >= world.getActualHeight() || cell.x < -29999984 || cell.x >= 29999984
                    || cell.z < -29999984 || cell.z >= 29999984) return "invalid structure block coordinates";
            if(!world.getChunkProvider().chunkExists(cell.x >> 4, cell.z >> 4)) return "unloaded structure chunk";
        }
        placementDepth++;
        try {
            for(Placement cell : cells) {
                Block old = world.getBlock(cell.x, cell.y, cell.z);
                if(old == cell.block && world.getBlockMetadata(cell.x, cell.y, cell.z) == cell.meta) continue;
                // Detach inventories before breakBlock runs: no stale tiles or inventory spill/duplication.
                // World/Chunk own invalidation and removal from the ticking tile lists.
                if(world.getTileEntity(cell.x, cell.y, cell.z) != null) world.removeTileEntity(cell.x, cell.y, cell.z);
                if(!world.setBlock(cell.x, cell.y, cell.z, cell.block, cell.meta, 2))
                    return "world rejected block placement at " + cell.x + ", " + cell.y + ", " + cell.z;
                if(old != Blocks.air) preparation.replaced++;
                if(!nest) changed.add(cell);
            }
            // Publish neighbor updates only once the seals, floors and attachments are complete.
            for(Placement cell : nest ? cells : changed) {
                boolean exposed = nest;
                if(!exposed) for(ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
                    if(!preparation.contains(cell.x + side.offsetX, cell.y + side.offsetY, cell.z + side.offsetZ)) { exposed = true; break; }
                if(exposed) world.notifyBlocksOfNeighborChange(cell.x, cell.y, cell.z, cell.block);
            }
            for(Placement cell : cells)
                if(world.getBlock(cell.x, cell.y, cell.z) != cell.block || world.getBlockMetadata(cell.x, cell.y, cell.z) != cell.meta)
                    return "block callback changed the completed structure at " + cell.x + ", " + cell.y + ", " + cell.z;
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Gemini structure placement failed", failure);
            return "engine/block callback failed (see server log); placement may be incomplete";
        } finally {
            placementDepth--;
        }
        return null;
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
                    || !GeminiConfig.enabled || event.world.playerEntities.isEmpty()) return;
            World world = event.world;
            GardenerNests data = get(world);
            if(world.provider.dimensionId == 0 && world.getTotalWorldTime() % 1200 == 0) data.observeResidences(world);
            if(active(world) && world.getGameRules().getGameRuleBooleanValue("mobGriefing"))
                data.tick((WorldServer) world, StructurePlacementMode.NATURAL);
            else if(world.getTotalWorldTime() % 100 == 0)
                for(Nest nest : data.nests) if(nest.debug && nest.phase == 2 && loaded(world, nest.bounds().expand(8, 0, 8))) {
                    data.spawn((WorldServer) world, nest); data.markDirty(); break;
                }
        }
    }
    private void tick(WorldServer world, StructurePlacementMode mode) {
        if(mode != StructurePlacementMode.NATURAL) throw new IllegalArgumentException("Forced nests are completed by command placement");
        for(Iterator<Nest> it = nests.iterator(); it.hasNext();) {
            Nest nest = it.next();
            if(nest.phase >= 3) continue;
            // A population-limited ready nest must not starve construction or discovery for other survival areas.
            if(nest.phase == 2 && world.getTotalWorldTime() % 100 != 0) continue;
            if(!loaded(world, nest.bounds().expand(2, 0, 2))) continue; // Unloaded jobs must not starve other loaded nests.
            if(nest.phase < 2) {
                // Keep the existing two-pass, 32-cells-per-tick construction delay and save cursor.
                // Commit the complete preflighted footprint together so a late occupancy/protection
                // rejection cannot strand a partly excavated natural nest.
                for(int work = 0; work < 32 && nest.phase < 2; work++) {
                    if(++nest.cursor < VOLUME) continue;
                    nest.cursor = 0;
                    if(nest.phase == 0) { nest.phase = 1; continue; }
                    PlacementPlan preparation = plan(world, nest);
                    String reason = place(world, preparation, true, StructurePlacementMode.NATURAL);
                    if(reason != null) {
                        schedule.failed(this, world, "nest", "legacy construction: " + reason);
                        it.remove(); markDirty(); return;
                    }
                    nest.phase = 2; nest.built = true;
                    schedule.success(this, world, "nest", nest.x, nest.y, nest.z, GeminiConfig.gardenerNestIntervalSeconds);
                }
            }
            if(nest.phase == 2 && world.getTotalWorldTime() % 100 == 0) spawn(world, nest);
            markDirty(); return; // One job's paced delay per tick; commit only a complete preflighted plan.
        }
    }

    /** Discovery runs independently of legacy construction jobs and resident population waits. */
    private String generateNearPlayers(WorldServer world) {
        List<EntityPlayer> players = GeminiStructureSchedule.players(world);
        Map<String, Integer> rejected = new LinkedHashMap<String, Integer>();
        Map<Long, GeminiStructureSchedule.Candidates> searches = new java.util.HashMap<Long, GeminiStructureSchedule.Candidates>();
        Set<Long> exhausted = new java.util.HashSet<Long>();
        int start = world.rand.nextInt(players.size()), plans = 0;
        for(int attempt = 0; attempt < GeminiStructureSchedule.ATTEMPTS && plans < 2; attempt++) {
            EntityPlayer player = players.get((start + attempt) % players.size());
            int homeX = MathHelper.floor_double(player.posX), homeZ = MathHelper.floor_double(player.posZ);
            for(Residence area : residences) if(area.owner.equals(player.getUniqueID()) && area.established
                    && area.distance(player.posX, player.posZ) <= 32D * 32D) {
                homeX = area.x; homeZ = area.z; break;
            }
            if(areaNests(homeX, homeZ) >= GeminiConfig.gardenerNestsPerArea) {
                countRejection(rejected, "survival area nest limit"); continue;
            }
            long key = ((long) homeX << 32) | (homeZ & 0xFFFFFFFFL);
            if(exhausted.contains(key)) continue;
            GeminiStructureSchedule.Candidates search = searches.get(key);
            if(search == null) {
                search = new GeminiStructureSchedule.Candidates(world, homeX, homeZ,
                        GeminiConfig.gardenerNestMinDistance, GeminiConfig.gardenerNestMaxDistance, 23);
                searches.put(key, search);
            }
            ChunkCoordinates point = schedule.candidate(search);
            boolean fallback = point == null;
            if(point == null) {
                point = schedule.fallback(world, search);
                if(point == null) {
                    exhausted.add(key);
                    countRejection(rejected, "no unused loaded nest footprint (bounded fallback unavailable)"); continue;
                }
            }
            schedule.examined();
            int x = point.posX, z = point.posZ;
            if(tooClose(x, z)) { schedule.spacing(); countRejection(rejected, "nest spacing (64 blocks)"); continue; }
            if(fallback) {
                schedule.loadFallback(world, point, search.half);
                searches.put(key, new GeminiStructureSchedule.Candidates(world, homeX, homeZ,
                        search.min, search.max, search.half));
            }
            if(!GeminiStructureSchedule.footprint(world, x, z, 23)) {
                schedule.unloaded(); countRejection(rejected, "complete chamber/access footprint not loaded or outside world bounds"); continue;
            }
            int surface = surface(world, x, z);
            if(surface < 17) { countRejection(rejected, "insufficient terrain depth for an underground chamber"); continue; }
            int y = Math.max(1, Math.min(42, surface - 17 - world.rand.nextInt(12)));
            Nest nest = new Nest(x, y, z);
            nest.homeX = homeX; nest.homeZ = homeZ; nest.hasHome = true;
            plans++; schedule.plan();
            String reason = place(world, plan(world, nest), true, StructurePlacementMode.NATURAL);
            if(reason != null) {
                if(reason.startsWith("unloaded")) schedule.unloaded();
                countRejection(rejected, reason); continue;
            }
            // Only complete verified structures enter the authoritative registry.
            nest.built = true; nest.phase = 2;
            nests.add(nest);
            schedule.success(this, world, "nest", x, y, z, GeminiConfig.gardenerNestIntervalSeconds);
            return null;
        }
        return schedule.failure(rejected);
    }
    private boolean tooClose(int x, int z) {
        for(Nest nest : nests) {
            double dx = (double) x - nest.x, dz = (double) z - nest.z;
            if(dx * dx + dz * dz < 64D * 64D) return true;
        }
        return false;
    }
    private int areaNests(int x, int z) {
        int count = 0;
        double radius = GeminiConfig.gardenerNestMaxDistance + 32D;
        for(Nest nest : nests) {
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
        if(!active(world) && !(nest.debug && GeminiConfig.enabled && GeminiConfig.gardenerEnabled
                && world.difficultySetting != EnumDifficulty.PEACEFUL)) return;
        if(GeminiPopulation.find(world, nest.boss) != null) { nest.phase = 3; return; }
        if(!GeminiPopulation.canAdmit(world, EntityGardener.class, null, GeminiConfig.gardenerMaxLoaded)) {
            GeminiInvasionManager.debug(world, "gardener-spawn", "Underground Gardener waiting: population limit."); return;
        }
        if(!world.getEntitiesWithinAABB(EntityPlayer.class, nest.bounds()).isEmpty()) {
            GeminiInvasionManager.debug(world, "gardener-spawn", "Underground Gardener waiting: player inside nest."); return;
        }
        EntityGardener boss = new EntityGardener(world);
        if(nest.debug) GeminiSpawning.markStructureDebugSpawn(boss);
        boss.setNestIdentity(nest.boss); boss.anchor(nest.x + 0.5D, nest.y, nest.z + 0.5D);
        if(nest.hasHome) boss.setHuntingGround(nest.homeX + 0.5D, nest.homeZ + 0.5D);
        if(boss.hasSpawnSpace()
                && world.spawnEntityInWorld(boss)) {
            nest.phase = 3;
            GeminiInvasionManager.debug(world, "gardener-spawn", "Underground Gardener spawned at " + nest.x + ", " + nest.y + ", " + nest.z + ".");
        } else {
            if(++nest.retries >= 12) nest.phase = 4;
            GeminiInvasionManager.debug(world, "gardener-spawn", "Underground Gardener rejected: body/limb clearance or Forge insertion veto; retry=" + nest.retries + ".");
        }
    }
    private static final class Nest {
        final int x, y, z;
        UUID boss = UUID.randomUUID();
        int phase, cursor, retries; // validate, build, spawn, complete, abandoned
        int homeX, homeZ;
        boolean hasHome;
        boolean built;
        boolean debug;
        Nest(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        AxisAlignedBB bounds() { return AxisAlignedBB.getBoundingBox(x - 14, y - 1, z - 14, x + 15, y + 13, z + 15); }
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
                residences.add(area);
            } catch(IllegalArgumentException ignored) { }
        }
        nests.clear(); schedule.read(nbt);
        NBTTagList list = nbt.getTagList("Nests", 10);
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Nest nest = new Nest(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            if(nest.y < 1 || nest.y > 243
                    || Math.abs((long) nest.x) > 29999984 || Math.abs((long) nest.z) > 29999984) continue;
            nest.phase = MathHelper.clamp_int(tag.getInteger("Phase"), 0, 4);
            nest.built = tag.getBoolean("Built") || nest.phase == 2 || nest.phase == 3;
            nest.debug = tag.getBoolean("Debug");
            nest.cursor = MathHelper.clamp_int(tag.getInteger("Cursor"), 0, VOLUME - 1);
            nest.retries = MathHelper.clamp_int(tag.getInteger("Retries"), 0, 12);
            nest.hasHome = tag.getBoolean("HasHome"); nest.homeX = tag.getInteger("HomeX"); nest.homeZ = tag.getInteger("HomeZ");
            // Restart unfinished legacy jobs through full expanded validation; never respawn completed bosses.
            if(tag.getInteger("Layout") < 2 && nest.phase < 3) { nest.phase = 0; nest.cursor = 0; nest.retries = 0; nest.built = false; }
            try { nest.boss = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { }
            nests.add(nest);
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList homes = new NBTTagList();
        for(Residence area : residences) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Owner", area.owner.toString());
            tag.setInteger("X", area.x); tag.setInteger("Z", area.z); tag.setInteger("Dwell", area.dwell);
            tag.setBoolean("Established", area.established); homes.appendTag(tag);
        }
        nbt.setTag("Residences", homes);
        schedule.write(nbt);
        NBTTagList list = new NBTTagList();
        for(Nest nest : nests) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", nest.x); tag.setInteger("Y", nest.y); tag.setInteger("Z", nest.z);
            tag.setInteger("Phase", nest.phase); tag.setInteger("Cursor", nest.cursor); tag.setInteger("Retries", nest.retries);
            tag.setBoolean("Built", nest.built);
            tag.setBoolean("Debug", nest.debug);
            tag.setInteger("Layout", 2);
            tag.setBoolean("HasHome", nest.hasHome); tag.setInteger("HomeX", nest.homeX); tag.setInteger("HomeZ", nest.homeZ);
            tag.setString("Boss", nest.boss.toString()); list.appendTag(tag);
        }
        nbt.setTag("Nests", list);
    }
}
