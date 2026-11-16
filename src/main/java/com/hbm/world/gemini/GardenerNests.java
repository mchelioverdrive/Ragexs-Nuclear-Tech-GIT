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
    public static String eligibility(World world) {
        if(!(world instanceof WorldServer) || world.isRemote || world.provider.dimensionId != 0) return "Overworld server only";
        if(!active(world)) return "requires enabled invasion/Gardener, phase 3 and non-Peaceful difficulty";
        if(world.playerEntities.isEmpty()) return "no players in the Overworld";
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return "mobGriefing is false";
        GardenerNests data = get(world);
        if(data.nests.size() >= GeminiConfig.gardenerNestLimit) return "nest limit reached";
        if(GeminiConfig.gardenerNestsPerArea == 0) return "nests per survival area is zero";
        for(Residence area : data.residences) if(area.established && data.areaNests(area) < GeminiConfig.gardenerNestsPerArea)
            return "eligible for loaded-terrain search; next opportunity in " + data.searchTicks + " live discovery ticks; chance, spacing and placement still apply";
        return "no established survival area with nest capacity";
    }

    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player) {
        return debugSpawn(player, null);
    }

    /** Build the canonical room, with explicit excavation and a bounded access shaft. */
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player, ChunkCoordinates requested) {
        if(!(player.worldObj instanceof WorldServer) || player.worldObj.isRemote)
            throw new net.minecraft.command.CommandException("Nest placement requires a server world.");
        WorldServer world = (WorldServer) player.worldObj;
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing"))
            throw GeminiInvasionManager.rejected(world, "nest", "Nest placement requires mobGriefing=true.");
        GardenerNests data = get(world);
        if(data.nests.size() >= 64) throw GeminiInvasionManager.rejected(world, "nest", "Nest save capacity reached (64 records).");
        ForcedPlan best = null;
        Map<String, Integer> rejected = new LinkedHashMap<String, Integer>();
        int attempts = requested == null ? 32 : 1;
        for(int attempt = 0; attempt < attempts; attempt++) {
            double angle = attempt * 2.399963229728653D;
            int radius = attempt == 0 ? 0 : 16 + attempt * 7 % 33;
            int x = requested == null ? MathHelper.floor_double(player.posX + Math.cos(angle) * radius) : requested.posX;
            int z = requested == null ? MathHelper.floor_double(player.posZ + Math.sin(angle) * radius) : requested.posZ;
            int y;
            if(requested != null) y = requested.posY;
            else {
                if(Math.abs((long) x) > 29999960 || Math.abs((long) z) > 29999960) {
                    countRejection(rejected, "unsafe world boundaries"); continue;
                }
                if(!loadedVolume(world, AxisAlignedBB.getBoundingBox(x - 22, 0, z - 22, x + 23, world.getActualHeight(), z + 23))) {
                    countRejection(rejected, "unloaded chunks intersect the resident clearance area"); continue;
                }
                // Stay near an underground player at any safe elevation; descend below the surface for outdoor/flying players.
                y = MathHelper.clamp_int(Math.min(MathHelper.floor_double(player.boundingBox.minY) - attempt % 3 * 4,
                        surface(world, x + 15, z) - 15), 12, world.getActualHeight() - 15);
            }
            Nest nest = new Nest(x, y, z);
            ForcedPlan plan = data.plan(world, nest, StructurePlacementMode.FORCED_DEBUG, player);
            String reason = plan.reason;
            if(reason == null) {
                plan.cost += radius * 8;
                if(best == null || plan.cost < best.cost) best = plan;
            } else countRejection(rejected, reason);
        }
        if(best == null) throw searchFailure(world, "nest", requested != null, rejected);
        Nest nest = new Nest(best.x, best.y, best.z);
        String reason = place(world, best.cells(), true, StructurePlacementMode.FORCED_DEBUG);
        if(reason != null) throw GeminiInvasionManager.rejected(world, "nest", "Nest placement failed; changed blocks restored: " + reason);
        nest.phase = 2; nest.built = true; nest.debug = true;
        nest.homeX = MathHelper.floor_double(player.posX); nest.homeZ = MathHelper.floor_double(player.posZ); nest.hasHome = true;
        data.nests.add(nest); data.markDirty();
        String resident;
        try {
            data.spawn(world, nest);
            resident = nest.phase == 3 ? "Gardener spawned" : "Gardener pending normal admission (settings, player, population or clearance/Forge veto)";
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Debug nest resident initialization failed", failure);
            resident = "resident initialization failed; registered nest retained for retry (see server log)";
        }
        data.markDirty();
        GeminiInvasionManager.debug(world, "nest-register", "Completed and registered debug nest at " + nest.x + ", " + nest.y + ", " + nest.z + ".");
        return info(world, nest, "registered=true; terrain preparation=" + (best.prepared ? "yes" : "no") + "; " + resident);
    }

    private ForcedPlan plan(WorldServer world, Nest nest, StructurePlacementMode mode, EntityPlayer player) {
        if(mode != StructurePlacementMode.FORCED_DEBUG) throw new IllegalArgumentException("Immediate nest preparation requires FORCED_DEBUG");
        ForcedPlan plan = new ForcedPlan(nest.x, nest.y, nest.z);
        if(nest.y < 8 || nest.y > world.getActualHeight() - 15) {
            plan.reason = "unsafe world height for complete nest; origin Y must be 8.." + (world.getActualHeight() - 15); return plan;
        }
        plan.regions.add(nest.bounds());
        // Native spawn/AI reads the limb envelope beyond the excavated room.
        plan.readBounds = nest.bounds().expand(8, 0, 8);
        plan.reason = plan.checkBounds(world);
        if(plan.reason != null) return plan;
        int[] exitX = {1, -1, 0, 0}, exitZ = {0, 0, 1, -1};
        int entrance = -1;
        for(int side = 0; side < 4; side++) {
            int ex = nest.x + exitX[side] * 18, ez = nest.z + exitZ[side] * 18;
            if(world.isAirBlock(ex, nest.y, ez) && world.isAirBlock(ex, nest.y + 1, ez)
                    && world.isAirBlock(ex - exitX[side], nest.y, ez - exitZ[side])
                    && connectedAccess(world, nest, ex, ez, player)) { entrance = side; break; }
        }
        int top = surface(world, nest.x + 15, nest.z);
        if(entrance < 0 && top >= world.getActualHeight() - 1) {
            plan.reason = world.getBlock(nest.x + 15, world.getActualHeight() - 1, nest.z) == Blocks.bedrock
                    ? "bedrock obstruction above required access shaft" : "unsafe world boundary above required access shaft";
            return plan;
        }
        if(top < nest.y + 13) {
            plan.reason = "unsafe underground cover/access boundary; choose a deeper origin"; return plan;
        }
        if(entrance >= 0) {
            int ex = exitX[entrance], ez = exitZ[entrance];
            plan.regions.add(AxisAlignedBB.getBoundingBox(nest.x + Math.min(ex * 11, ex * 18) - 1, nest.y - 1,
                    nest.z + Math.min(ez * 11, ez * 18) - 1, nest.x + Math.max(ex * 11, ex * 18) + 2,
                    nest.y + 3, nest.z + Math.max(ez * 11, ez * 18) + 2));
        } else {
            plan.regions.add(AxisAlignedBB.getBoundingBox(nest.x + 12, nest.y - 1, nest.z - 1, nest.x + 17, nest.y + 3, nest.z + 2));
            plan.regions.add(AxisAlignedBB.getBoundingBox(nest.x + 14, nest.y - 1, nest.z - 1, nest.x + 17, top + 2, nest.z + 2));
        }
        plan.reason = plan.inspect(world);
        if(plan.reason != null) return plan;
        for(int i = 0; i < VOLUME; i++) {
            int dx = i % WIDTH - 14, dz = i / WIDTH % WIDTH - 14, dy = i / (WIDTH * WIDTH) - 2;
            Block block = desired(dx, dy, dz);
            if(block == null && world.getBlock(nest.x + dx, nest.y + dy, nest.z + dz) instanceof net.minecraft.block.BlockFalling)
                block = Blocks.stone; // Keep loose roof/backing terrain from collapsing into the prepared room.
            if(block != null) plan.put(nest.x + dx, nest.y + dy, nest.z + dz, block, 0);
            if(world.isAirBlock(nest.x + dx, nest.y + dy, nest.z + dz)) plan.cost++;
        }
        if(entrance >= 0) {
            int ex = exitX[entrance], ez = exitZ[entrance];
            for(int step = 11; step <= 18; step++) for(int width = -1; width <= 1; width++) {
                int x = nest.x + ex * step + ez * width, z = nest.z + ez * step + ex * width;
                plan.put(x, nest.y - 1, z, Blocks.stone, 0);
                for(int dy = 0; dy < 3; dy++) plan.put(x, nest.y + dy, z, Blocks.air, 0);
            }
            plan.finish(world);
            return plan;
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
        for(int x = nest.x + 12; x <= nest.x + 14; x++) for(int z = nest.z - 1; z <= nest.z + 1; z++) {
            plan.put(x, nest.y - 1, z, Blocks.stone, 0);
            for(int dy = 0; dy < 3; dy++) plan.put(x, nest.y + dy, z, Blocks.air, 0);
        }
        plan.finish(world);
        return plan;
    }

    /** Do not mistake an isolated air pocket for access. Reuse a known clear route to the outside sender. */
    private static boolean connectedAccess(World world, Nest nest, int x, int z, EntityPlayer player) {
        if(nest.bounds().intersectsWith(player.boundingBox) || Math.abs(player.boundingBox.minY - nest.y) > 1) return false;
        double dx = player.posX - x - 0.5D, dz = player.posZ - z - 0.5D;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if(distance > 48) return false;
        int steps = Math.max(1, (int) Math.ceil(distance * 2));
        for(int step = 0; step <= steps; step++) {
            int px = MathHelper.floor_double(x + 0.5D + dx * step / steps);
            int pz = MathHelper.floor_double(z + 0.5D + dz * step / steps);
            if(!world.getChunkProvider().chunkExists(px >> 4, pz >> 4)
                    || !world.isAirBlock(px, nest.y, pz) || !world.isAirBlock(px, nest.y + 1, pz)) return false;
        }
        return true;
    }

    /** Shared checked placement of the real layouts; roll back every changed cell on rejection. */
    static final class Placement {
        final int x, y, z, meta; final Block block;
        Placement(int x, int y, int z, Block block, int meta) { this.x = x; this.y = y; this.z = z; this.block = block; this.meta = meta; }
    }
    /** A command-local plan for the two real layouts, including their bounded preparation areas. */
    static final class ForcedPlan {
        final int x, y, z;
        final List<AxisAlignedBB> regions = new ArrayList<AxisAlignedBB>();
        final Map<Long, Placement> edits = new LinkedHashMap<Long, Placement>();
        AxisAlignedBB readBounds;
        String reason;
        int cost;
        boolean prepared;
        ForcedPlan(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        void put(int x, int y, int z, Block block, int meta) {
            long key = ((long) (x & 0x3FFFFFF) << 34) | ((long) (z & 0x3FFFFFF) << 8) | (y & 255);
            edits.put(key, new Placement(x, y, z, block, meta));
        }
        List<Placement> cells() {
            List<Placement> result = new ArrayList<Placement>();
            // Attachment supports must exist before ladder onBlockAdded/Forge callbacks.
            for(Placement cell : edits.values()) if(cell.block != Blocks.ladder) result.add(cell);
            for(Placement cell : edits.values()) if(cell.block == Blocks.ladder) result.add(cell);
            return result;
        }
        String checkBounds(WorldServer world) {
            for(AxisAlignedBB box : regions) {
                if(box.minX < -29999984 || box.maxX > 29999984 || box.minZ < -29999984 || box.maxZ > 29999984
                        || box.minY < 1 || box.maxY >= world.getActualHeight()) return "unsafe world boundaries for the complete placement volume";
                if(!loadedVolume(world, box.expand(1, 0, 1))) return "unloaded chunks intersect the preparation area; move closer/load them before retrying";
            }
            if(readBounds != null && !loadedVolume(world, readBounds)) return "unloaded chunks intersect the resident clearance area";
            return null;
        }
        String inspect(WorldServer world) {
            String failure = checkBounds(world);
            if(failure != null) return failure;
            GardenerNests data = get(world);
            for(AxisAlignedBB box : regions) {
                if(WretchCamps.hasPlayerPlacement(world, box) || overlapsStructure(world, box)) return "protected player construction or registered world structure";
                for(Nest nest : data.nests) if(nest.bounds().intersectsWith(box)) return "protected existing nest reservation";
                for(GeminiInvasionManager.StructureInfo camp : WretchCamps.known(world))
                    if(AxisAlignedBB.getBoundingBox(camp.x - 7, camp.y - 9, camp.z - 7, camp.x + 8, camp.y + 21, camp.z + 8).intersectsWith(box))
                        return "protected existing camp";
                for(int x = (int) box.minX; x < box.maxX; x++) for(int z = (int) box.minZ; z < box.maxZ; z++)
                    for(int y = (int) box.minY; y < box.maxY; y++) {
                        String unsafe = forcedRejection(world, x, y, z);
                        if(unsafe != null) return unsafe + " at " + x + ", " + y + ", " + z;
                    }
                // Reject fluid beside the excavation as well as inside it. No drain/flood side effects.
                AxisAlignedBB edge = box.expand(1, 1, 1);
                for(int x = (int) edge.minX; x < edge.maxX; x++) for(int z = (int) edge.minZ; z < edge.maxZ; z++)
                    for(int y = Math.max(1, (int) edge.minY); y < Math.min(world.getActualHeight(), edge.maxY); y++) {
                        if(x >= box.minX && x < box.maxX && z >= box.minZ && z < box.maxZ && y >= box.minY && y < box.maxY) continue;
                        Block block = world.getBlock(x, y, z);
                        if(block.getMaterial().isLiquid()) return "liquid beside preparation area; drain or choose a dry site";
                        if(y >= box.maxY && block instanceof net.minecraft.block.BlockFalling)
                            return "unsafe falling terrain above preparation area";
                    }
            }
            return null;
        }
        void finish(WorldServer world) {
            EntityPlayer actor = FakePlayerFactory.getMinecraft(world);
            AxisAlignedBB entitiesBox = readBounds;
            if(entitiesBox == null) throw new IllegalStateException("Missing structure read bounds");
            for(AxisAlignedBB region : regions) entitiesBox = AxisAlignedBB.getBoundingBox(
                    Math.min(entitiesBox.minX, region.minX), Math.min(entitiesBox.minY, region.minY), Math.min(entitiesBox.minZ, region.minZ),
                    Math.max(entitiesBox.maxX, region.maxX), Math.max(entitiesBox.maxY, region.maxY), Math.max(entitiesBox.maxZ, region.maxZ));
            List<net.minecraft.entity.Entity> occupants = world.getEntitiesWithinAABBExcludingEntity(null, entitiesBox);
            for(Placement cell : edits.values()) {
                Block old = world.getBlock(cell.x, cell.y, cell.z);
                if(old == cell.block && world.getBlockMetadata(cell.x, cell.y, cell.z) == cell.meta) continue;
                cost++;
                if(old != Blocks.air || cell.y < y) prepared = true;
                if(!world.canMineBlock(actor, cell.x, cell.y, cell.z)) { reason = "protected block (server mining/spawn protection)"; return; }
                if(cell.block != Blocks.air) for(net.minecraft.entity.Entity entity : occupants)
                    if(entity.boundingBox != null && entity.boundingBox.intersectsWith(AxisAlignedBB.getBoundingBox(cell.x, cell.y, cell.z, cell.x + 1, cell.y + 1, cell.z + 1))) {
                        reason = "entity obstructs a solid structure/foundation block"; return;
                    }
            }
        }
    }
    static boolean loadedVolume(World world, AxisAlignedBB box) {
        for(int x = MathHelper.floor_double(box.minX) >> 4; x <= (MathHelper.ceiling_double_int(box.maxX) - 1) >> 4; x++)
            for(int z = MathHelper.floor_double(box.minZ) >> 4; z <= (MathHelper.ceiling_double_int(box.maxZ) - 1) >> 4; z++)
                if(!world.getChunkProvider().chunkExists(x, z)) return false;
        return true;
    }
    static String forcedRejection(World world, int x, int y, int z) {
        if(y < 1 || y >= world.getActualHeight()) return "unsafe world height";
        if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return "unloaded chunk";
        Block block = world.getBlock(x, y, z);
        if(block == Blocks.bedrock) return "bedrock obstruction";
        if(block.hasTileEntity(world.getBlockMetadata(x, y, z)) || world.getTileEntity(x, y, z) != null) return "protected tile entity";
        if(block.getMaterial().isLiquid()) return "liquid obstruction; drain before retrying";
        if(block.getBlockHardness(world, x, y, z) < 0) return "protected unbreakable block";
        boolean ordinary = natural(block) || block == Blocks.grass || block == Blocks.sand || block == Blocks.sandstone
                || block == Blocks.clay || block == Blocks.hardened_clay || block == Blocks.stained_hardened_clay
                || block == Blocks.mycelium || block == Blocks.netherrack || block == Blocks.end_stone || block == Blocks.soul_sand
                || block == Blocks.snow || block == Blocks.snow_layer || block.isWood(world, x, y, z) || block.isLeaves(world, x, y, z)
                || block.getMaterial() == net.minecraft.block.material.Material.plants
                || block.getMaterial() == net.minecraft.block.material.Material.vine;
        return ordinary ? null : "protected construction or non-terrain block " + Block.blockRegistry.getNameForObject(block);
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

    static String place(WorldServer world, List<Placement> plan, boolean nest, StructurePlacementMode mode) {
        boolean bypassPhase = mode == StructurePlacementMode.FORCED_DEBUG;
        EntityPlayer actor = FakePlayerFactory.getMinecraft(world);
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        boolean complete = false;
        placementDepth++;
        try {
            if(bypassPhase) {
                // Resolve all ordinary safety/mining/break vetoes before the first mutation.
                for(Placement cell : plan) {
                    String unsafe = forcedRejection(world, cell.x, cell.y, cell.z);
                    if(unsafe != null) return unsafe;
                    Block old = world.getBlock(cell.x, cell.y, cell.z);
                    int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
                    if(old == cell.block && meta == cell.meta) continue;
                    AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cell.x, cell.y, cell.z, cell.x + 1, cell.y + 1, cell.z + 1);
                    if(WretchCamps.hasPlayerPlacement(world, box) || !world.canMineBlock(actor, cell.x, cell.y, cell.z)
                            || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(cell.x, cell.y, cell.z, world, old, meta, actor)))
                        return "protected block/Forge break veto at " + cell.x + ", " + cell.y + ", " + cell.z;
                    if(world.getBlock(cell.x, cell.y, cell.z) != old || world.getBlockMetadata(cell.x, cell.y, cell.z) != meta)
                        return "terrain changed during protection preflight";
                }
            }
            for(Placement cell : plan) {
                if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                        || !bypassPhase && !(nest ? active(world) : WretchCamps.active(world))) return "generation gate changed";
                if(bypassPhase) {
                    String unsafe = forcedRejection(world, cell.x, cell.y, cell.z);
                    if(unsafe != null) return unsafe + " at " + cell.x + ", " + cell.y + ", " + cell.z;
                } else if(!(nest ? safe(world, cell.x, cell.y, cell.z, true) : WretchCamps.safe(world, cell.x, cell.y, cell.z))) return "terrain changed";
                Block old = world.getBlock(cell.x, cell.y, cell.z);
                int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
                if(old == cell.block && meta == cell.meta) continue;
                AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cell.x, cell.y, cell.z, cell.x + 1, cell.y + 1, cell.z + 1);
                if(cell.block != Blocks.air && !world.getEntitiesWithinAABBExcludingEntity(null, box).isEmpty()) return "occupied block placement";
                if(((nest || bypassPhase) && WretchCamps.hasPlayerPlacement(world, box)) || !world.canMineBlock(actor, cell.x, cell.y, cell.z)
                        || !bypassPhase && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(cell.x, cell.y, cell.z, world, old, meta, actor))) return "block protection/Forge break veto";
                if(world.getBlock(cell.x, cell.y, cell.z) != old || world.getBlockMetadata(cell.x, cell.y, cell.z) != meta
                        || world.getTileEntity(cell.x, cell.y, cell.z) != null
                        || (nest || bypassPhase) && WretchCamps.hasPlayerPlacement(world, box)) return "block changed during protection callback";
                if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                        || !bypassPhase && !(nest ? active(world) : WretchCamps.active(world))) return "generation gate changed during callback";
                if(cell.block != Blocks.air && !world.getEntitiesWithinAABBExcludingEntity(null, box).isEmpty()) return "placement occupied during callback";
                BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, cell.x, cell.y, cell.z);
                snapshots.add(snapshot);
                if(!world.setBlock(cell.x, cell.y, cell.z, cell.block, cell.meta, 0)) return "world rejected block placement";
                if(cell.block != Blocks.air && ForgeEventFactory.onPlayerBlockPlace(actor, snapshot, ForgeDirection.UP).isCanceled()) return "Forge placement veto";
                if(world.getBlock(cell.x, cell.y, cell.z) != cell.block || world.getBlockMetadata(cell.x, cell.y, cell.z) != cell.meta)
                    return "block changed during placement callback";
            }
            if(nest) for(Placement cell : plan) world.notifyBlocksOfNeighborChange(cell.x, cell.y, cell.z, cell.block);
            // Neighbor/placement callbacks must not silently remove part of the completed layout.
            if(bypassPhase) for(Placement cell : plan)
                if(world.getBlock(cell.x, cell.y, cell.z) != cell.block || world.getBlockMetadata(cell.x, cell.y, cell.z) != cell.meta)
                    return "structure initialization changed a planned block";
            complete = true;
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Gemini structure placement failed", failure);
            return "internal generation error (see server log)";
        } finally {
            try {
                if(!complete) for(int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false);
            } finally {
                placementDepth--;
                for(BlockSnapshot snapshot : snapshots) world.markBlockForUpdate(snapshot.x, snapshot.y, snapshot.z);
            }
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
            if(world.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
                if(active(world)) data.tick((WorldServer) world, StructurePlacementMode.NATURAL);
                else if(world.getTotalWorldTime() % 100 == 0)
                    for(Nest nest : data.nests) if(nest.debug && nest.phase == 2 && loaded(world, nest.bounds().expand(5, 0, 5))) {
                        data.spawn((WorldServer) world, nest); data.markDirty(); break;
                    }
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
            if(nest.phase == 0 && nest.cursor == 0 && (overlapsStructure(world, nest)
                    || WretchCamps.hasPlayerPlacement(world, nest.bounds()))) {
                GeminiInvasionManager.debug(world, "nest-failure", "Nest validation rejected: protected structure or player construction.");
                it.remove(); markDirty(); return;
            }
            for(int work = 0; work < 32 && nest.phase < 2; work++) {
                int index = nest.cursor;
                int dx = index % WIDTH - 14, dz = index / WIDTH % WIDTH - 14, dy = index / (WIDTH * WIDTH) - 2;
                int x = nest.x + dx, y = nest.y + dy, z = nest.z + dz;
                if(!safe(world, x, y, z, true)) {
                    GeminiInvasionManager.debug(world, "nest-failure", "Nest rejected: unsafe geology/liquid/tile or unloaded cell at " + x + ", " + y + ", " + z + ".");
                    if(nest.phase == 0) it.remove(); else nest.phase = 4;
                    markDirty(); return;
                }
                if(nest.phase == 1) {
                    Block desired = desired(dx, dy, dz);
                    if(desired != null && world.getBlock(x, y, z) != desired
                            && !change(world, null, x, y, z, desired)) {
                        GeminiInvasionManager.debug(world, "nest-failure", "Nest build blocked: placement/protection veto, occupied or changed terrain at " + x + ", " + y + ", " + z + ".");
                        nest.phase = 4; markDirty(); return;
                    }
                }
                if(++nest.cursor >= VOLUME) {
                    nest.cursor = 0; nest.phase++;
                    if(nest.phase == 1) {
                        if(overlapsStructure(world, nest) || WretchCamps.hasPlayerPlacement(world, nest.bounds())
                                || !world.getEntitiesWithinAABBExcludingEntity(null, nest.bounds()).isEmpty()) {
                            GeminiInvasionManager.debug(world, "nest-failure", "Nest rejected after validation: protected or occupied volume.");
                            it.remove(); markDirty(); return;
                        }
                    }
                    if(nest.phase == 2) {
                        nest.built = true;
                        GeminiInvasionManager.debug(world, "nest-built", "Completed natural nest at " + nest.x + ", " + nest.y + ", " + nest.z + ".");
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
        GeminiInvasionManager.debug(world, "nest-attempt", "Natural nest search opportunity (loaded cave terrain near established residences).");
        if(GeminiConfig.gardenerNestsPerArea == 0 || residences.isEmpty()) {
            GeminiInvasionManager.debug(world, "nest-failure", "Nest search unavailable: no residence records or nests per area disabled."); return;
        }
        Residence area = residences.get(world.rand.nextInt(residences.size()));
        if(!area.established || areaNests(area) >= GeminiConfig.gardenerNestsPerArea) {
            GeminiInvasionManager.debug(world, "nest-failure", "Nest search unavailable: selected residence not established or area nest limit reached."); return;
        }
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
                nests.add(nest); markDirty();
                GeminiInvasionManager.debug(world, "nest-register", "Reserved natural nest at " + x + ", " + y + ", " + z + "; incremental validation/build pending.");
                return;
            }
        }
        GeminiInvasionManager.debug(world, "nest-failure", "Nest search failed: no loaded, spaced, covered cave floor free of construction and structures in the sampled area.");
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
            boolean debug = tag.getBoolean("Debug");
            if(nest.y < (debug ? 8 : 12) || nest.y > (debug ? 241 : 42)
                    || Math.abs((long) nest.x) > (debug ? 29999960 : 29999900) || Math.abs((long) nest.z) > (debug ? 29999960 : 29999900)) continue;
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
            tag.setInteger("Sector", area.sector); tag.setBoolean("Established", area.established); homes.appendTag(tag);
        }
        nbt.setTag("Residences", homes);
        nbt.setInteger("SearchTicks", searchTicks);
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
