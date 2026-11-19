package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWretch;
import cpw.mods.fml.common.IWorldGenerator;
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
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Loaded-area invasion settlements and saved one-shot resident admissions. */
public final class WretchCamps extends WorldSavedData {
    private static final String NAME = "GeminiWretchCamps";
    private static final int LIMIT = 256;
    private final GeminiStructureSchedule schedule = new GeminiStructureSchedule();
    private final List<Camp> camps = new ArrayList<Camp>();
    private final List<Camp> pending = new ArrayList<Camp>();
    private final Map<Long, BitSet> placedColumns = new HashMap<Long, BitSet>();
    private final Set<Long> generatedChunks = new HashSet<Long>();
    private final List<AxisAlignedBB> villageBuildings = new ArrayList<AxisAlignedBB>();
    private boolean placementOverflow;
    private int nextJob;
    private long villageRefresh = Long.MIN_VALUE;
    public WretchCamps(String name) { super(name); }

    public static final String TYPE = "wretch";
    private static GeminiInvasionManager.StructureInfo info(Camp camp, int dimension) {
        String[] states = {"residents not admitted", "waiting for Wretch", "Wretch spawned", "defeated", "blocked spawn"};
        return new GeminiInvasionManager.StructureInfo(TYPE, camp.x, camp.y - 1, camp.z, dimension,
                "construction=" + (camp.failed ? "failed" : camp.job != null ? camp.recovering ? "recovering"
                : camp.cursor == 0 && camp.pass == 0 ? "queued" : "constructing" : camp.state > 0 ? "completed" : "failed")
                + "; pass=" + camp.pass + "; cursor=" + camp.cursor + "; reason=" + camp.reason
                + "; " + states[camp.state] + "; " + description(camp) + "; " + population(camp));
    }
    public static List<GeminiInvasionManager.StructureInfo> known(World world) {
        List<GeminiInvasionManager.StructureInfo> result = new ArrayList<GeminiInvasionManager.StructureInfo>();
        if(!(world instanceof WorldServer) || world.isRemote) return result;
        String name = NAME + (world.provider.dimensionId == 0 ? "" : "_" + world.provider.dimensionId);
        WretchCamps data = (WretchCamps) world.mapStorage.loadData(WretchCamps.class, name);
        if(data != null) {
            for(Camp camp : data.camps) result.add(info(camp, world.provider.dimensionId));
            for(Camp camp : data.pending) result.add(new GeminiInvasionManager.StructureInfo(TYPE, camp.x, camp.y - 1, camp.z,
                    world.provider.dimensionId, "construction=queued; reason=legacy reservation without a saved block plan; " + description(camp)));
        }
        return result;
    }
    public static int completedCount(World world) {
        if(!(world instanceof WorldServer) || world.isRemote) return 0;
        int count = 0;
        for(Camp camp : get(world).camps) if(camp.state > 0 && camp.job == null && !camp.failed) count++;
        return count;
    }

    static float irisPressure(EntityPlayer player) {
        String name = NAME + (player.dimension == 0 ? "" : "_" + player.dimension);
        WretchCamps data = (WretchCamps)player.worldObj.mapStorage.loadData(WretchCamps.class, name);
        if(data != null) for(Camp camp : data.camps) {
            double dx = player.posX - camp.x, dz = player.posZ - camp.z;
            if(camp.state > 0 && camp.job == null && !camp.failed && dx * dx + dz * dz < 256D * 256D) return 0.5F;
        }
        return 0;
    }
    private static String generationGate(World world) {
        String global = GeminiInvasionManager.structureGate(world, 4);
        if(global != null) return global;
        if(!GeminiConfig.wretchEnabled) return "wretch.enabled is false";
        if(world.difficultySetting == EnumDifficulty.PEACEFUL) return "difficulty is Peaceful";
        if(!world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return "doMobSpawning is false";
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return "mobGriefing is false";
        if(GeminiConfig.wretchCampChance == 0) return "camp chance disabled";
        int reserved = 0;
        for(Camp camp : get(world).camps) {
            if(camp.job != null && !camp.failed) return "camp construction already reserved";
            if(camp.state > 0 || camp.job != null && !camp.failed) reserved++;
        }
        if(reserved >= GeminiConfig.wretchCampLimit) return "camp limit reached";
        if(GeminiStructureSchedule.players(world).isEmpty()) return "no living players in this world";
        return null;
    }
    public static String eligibility(World world) {
        String gate = generationGate(world);
        return gate == null ? "eligible around living players of any game mode" : gate;
    }
    public static String diagnostics(World world) {
        if(!(world instanceof WorldServer) || world.isRemote) return "server only";
        return "Automatic camps enabled=" + (GeminiConfig.enabled && GeminiConfig.wretchEnabled
                && GeminiConfig.wretchCampChance > 0 && GeminiConfig.wretchCampLimit > 0)
                + "; gate=" + eligibility(world) + "; " + get(world).schedule.status(world)
                + "; registered=" + completedCount(world) + "; pending/failed=" + (known(world).size() - completedCount(world));
    }
    static void reschedule(WorldServer world) { WretchCamps data = get(world); data.schedule.reschedule(data); }
    static void schedule(WorldServer world) {
        WretchCamps data = get(world);
        data.schedule.tick(world);
        try {
            if(generationGate(world) != null) return;
            if(!data.schedule.due(data, GeminiConfig.wretchCampIntervalSeconds)) return;
            String reason = data.generateNearPlayers(world);
            if(reason != null) data.schedule.failed(data, world, "camp", reason);
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Automatic Gemini camp generation failed", failure);
            data.schedule.failed(data, world, "camp", "engine/block callback exception (see server log)");
        }
    }
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player) {
        return debugSpawn(player, null);
    }
    public static GeminiInvasionManager.StructureInfo debugSpawn(EntityPlayer player, net.minecraft.util.ChunkCoordinates requested) {
        if(!(player.worldObj instanceof WorldServer) || player.worldObj.isRemote)
            throw new net.minecraft.command.CommandException("Camp placement requires a server world.");
        WorldServer world = (WorldServer) player.worldObj;
        WretchCamps data = get(world);
        GardenerNests.PlacementPlan best = null;
        Camp selected = null;
        Map<String, Integer> rejected = new java.util.LinkedHashMap<String, Integer>();
        for(int attempt = 0; attempt < (requested == null ? 8 : 1); attempt++) {
            double angle = attempt * 2.399963229728653D;
            int radius = attempt == 0 ? 0 : 12 + attempt * 7 % 53;
            int x = requested == null ? MathHelper.floor_double(player.posX + Math.cos(angle) * radius) : requested.posX;
            int z = requested == null ? MathHelper.floor_double(player.posZ + Math.sin(angle) * radius) : requested.posZ;
            if(x < -29999984 || x >= 29999984 || z < -29999984 || z >= 29999984) {
                GardenerNests.countRejection(rejected, "unsafe world boundaries"); continue;
            }
            if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                GardenerNests.countRejection(rejected, "unloaded origin chunk"); continue;
            }
            int y = requested == null ? MathHelper.clamp_int(ground(world, x, z), 1, world.getActualHeight() - 20) : requested.posY + 1;
            Camp saved = data.at(x, y, z);
            if(saved != null && saved.job != null) {
                saved.debug = saved.recovering = true; saved.failed = false; saved.idle = 0;
                data.markDirty(); saved.journalReady = GardenerNests.checkpoint(world, data);
                if(!saved.journalReady) { saved.failed = true; saved.reason = "could not save construction journal; terrain work paused"; }
                return info(saved, world.provider.dimensionId);
            }
            Camp camp = saved != null && saved.layout == WretchCampSettlement.VERSION ? saved : WretchCampSettlement.create(world, x, y, z);
            if(saved != null && saved != camp) { camp.boss = saved.boss; camp.state = saved.state; camp.retries = saved.retries; camp.protectedColumns.addAll(saved.protectedColumns); }
            GardenerNests.PlacementPlan plan = WretchCampSettlement.plan(world, camp);
            if(plan.reason != null) GardenerNests.countRejection(rejected, plan.reason);
            else { best = plan; selected = camp; break; }
        }
        if(best == null) throw GardenerNests.searchFailure(world, "camp", requested != null, rejected);
        selected.debug = true;
        String reason = data.place(world, selected, StructurePlacementMode.FORCED_DEBUG, best);
        if(reason != null) throw GeminiInvasionManager.rejected(world, "camp", "Camp placement failed: " + reason);
        return info(selected, world.provider.dimensionId);
    }

    private Camp at(int x, int y, int z) {
        for(Camp camp : camps) if(camp.x == x && camp.y == y && camp.z == z) return camp;
        for(Camp camp : pending) if(camp.x == x && camp.y == y && camp.z == z) return camp;
        return null;
    }
    private static String description(Camp camp) {
        return "dimensions=" + camp.size + "x" + (camp.top - camp.bottom) + "x" + camp.size + "; structures=" + (camp.major + camp.minor)
                + " (main=" + camp.major + ", smaller=" + camp.minor + ")";
    }
    private static String population(Camp camp) {
        int admitted = 0, waiting = 0, blocked = 0;
        for(WretchCampSettlement.Piece piece : camp.pieces) if(piece.role != 0) {
            if(piece.resident == 1) admitted++; else if(piece.resident == 2) blocked++; else waiting++;
        }
        return "initial residents=" + admitted + "; pending=" + waiting + "; blocked=" + blocked + "; Wretch state=" + camp.state;
    }
    public static boolean active(World world) {
        return GeminiConfig.wretchEnabled && GeminiInvasionManager.canActivityOccur(world, 4)
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }
    private static WretchCamps get(World world) {
        String name = NAME + (world.provider.dimensionId == 0 ? "" : "_" + world.provider.dimensionId);
        WretchCamps data = (WretchCamps) world.mapStorage.loadData(WretchCamps.class, name);
        if(data == null) { data = new WretchCamps(name); world.mapStorage.setData(name, data); }
        return data;
    }
    private Camp find(UUID boss) {
        for(Camp camp : camps) if(camp.boss.equals(boss)) return camp;
        return null;
    }
    public static boolean owns(EntityWretch boss) {
        if(!(boss.worldObj instanceof WorldServer) || !boss.hasCamp()) return false;
        Camp camp = get(boss.worldObj).find(boss.getUniqueID());
        return camp != null && camp.state < 3 && camp.state > 0
                && boss.campX() == camp.x && boss.campY() == camp.y && boss.campZ() == camp.z;
    }
    public static void defeated(EntityWretch boss) {
        if(boss.worldObj.isRemote) return;
        WretchCamps data = get(boss.worldObj);
        Camp camp = data.find(boss.getUniqueID());
        if(camp != null) { camp.state = 3; data.markDirty(); }
    }
    private static boolean natural(Block b) {
        return b == Blocks.air || b == Blocks.grass || b == Blocks.dirt || b == Blocks.stone || b == Blocks.gravel
                || b == Blocks.log || b == Blocks.log2 || b == Blocks.leaves || b == Blocks.leaves2
                || b == Blocks.tallgrass || b == Blocks.red_flower || b == Blocks.yellow_flower
                || b == Blocks.brown_mushroom || b == Blocks.red_mushroom || b == Blocks.vine;
    }
    private static int ground(World world, int x, int z) {
        return GardenerNests.surface(world, x, z);
    }
    private boolean spaced(int x, int z) {
        for(Camp camp : camps) {
            double dx = (double) x - camp.x, dz = (double) z - camp.z;
            if(dx * dx + dz * dz < (double) GeminiConfig.wretchCampSpacing * GeminiConfig.wretchCampSpacing) return false;
        }
        for(Camp camp : pending) {
            double dx = (double) x - camp.x, dz = (double) z - camp.z;
            if(dx * dx + dz * dz < (double) GeminiConfig.wretchCampSpacing * GeminiConfig.wretchCampSpacing) return false;
        }
        return true;
    }
    private String generateNearPlayers(WorldServer world) {
        List<EntityPlayer> players = GeminiStructureSchedule.players(world);
        Map<String, Integer> rejected = new java.util.LinkedHashMap<String, Integer>();
        Map<Long, GeminiStructureSchedule.Candidates> searches = new java.util.HashMap<Long, GeminiStructureSchedule.Candidates>();
        Set<Long> exhausted = new java.util.HashSet<Long>();
        int start = world.rand.nextInt(players.size()), plans = 0;
        for(int attempt = 0; attempt < GeminiStructureSchedule.ATTEMPTS && plans < 2; attempt++) {
            EntityPlayer player = players.get((start + attempt) % players.size());
            int homeX = MathHelper.floor_double(player.posX), homeZ = MathHelper.floor_double(player.posZ);
            long key = ((long) homeX << 32) | (homeZ & 0xFFFFFFFFL);
            if(exhausted.contains(key)) continue;
            GeminiStructureSchedule.Candidates search = searches.get(key);
            if(search == null) {
                search = new GeminiStructureSchedule.Candidates(world, homeX, homeZ,
                        GeminiConfig.wretchCampMinDistance, GeminiConfig.wretchCampMaxDistance, 42);
                searches.put(key, search);
            }
            net.minecraft.util.ChunkCoordinates point = schedule.candidate(search);
            boolean fallback = point == null;
            if(point == null) {
                point = schedule.fallback(world, search);
                if(point == null) {
                    exhausted.add(key);
                    GardenerNests.countRejection(rejected, "no unused loaded camp footprint (bounded fallback unavailable)"); continue;
                }
            }
            schedule.examined();
            int x = point.posX, z = point.posZ;
            if(!spaced(x, z)) { if(fallback) schedule.discardFallback(); schedule.spacing(); GardenerNests.countRejection(rejected, "camp spacing"); continue; }
            if(fallback) {
                if(!schedule.loadFallback(world, point, search.half)) { schedule.loading(this); return null; }
                searches.put(key, new GeminiStructureSchedule.Candidates(world, homeX, homeZ,
                        search.min, search.max, search.half));
            }
            if(!GeminiStructureSchedule.footprint(world, x, z, 42)) {
                schedule.unloaded(); GardenerNests.countRejection(rejected, "complete minimum settlement footprint not loaded or outside world bounds"); continue;
            }
            int surface = ground(world, x, z);
            if(surface < 1) { GardenerNests.countRejection(rejected, "no surface terrain"); continue; }
            Camp camp = WretchCampSettlement.create(world, x, MathHelper.clamp_int(surface, 1, world.getActualHeight() - 20), z);
            if(!GeminiStructureSchedule.footprint(world, x, z, camp.size / 2 + 2))
                camp.size = 80; // Still village-sized; fit the loaded area without requesting chunks.
            plans++; schedule.plan();
            String reason = place(world, camp, StructurePlacementMode.NATURAL, WretchCampSettlement.plan(world, camp));
            if(reason == null) {
                return null;
            }
            if(reason.startsWith("unloaded")) schedule.unloaded();
            GardenerNests.countRejection(rejected, reason);
        }
        return schedule.failure(rejected);
    }
    private String place(WorldServer world, Camp camp, StructurePlacementMode mode, GardenerNests.PlacementPlan preparation) {
        if(preparation.reason != null) return preparation.reason;
        String bounds = preparation.checkBounds(world);
        if(bounds != null) return bounds;
        if(mode == StructurePlacementMode.NATURAL && !active(world)) return "natural generation gate changed";
        // The exact graded edits, identity and cursor exist before the first world write.
        // Recovery replays this plan, never resamples already modified terrain.
        camp.job = preparation.cells(); camp.cursor = camp.pass = camp.idle = 0;
        camp.unreadJob = null;
        camp.failed = camp.recovering = false; camp.reason = "queued";
        for(java.util.Iterator<Camp> it = camps.iterator(); it.hasNext();) {
            Camp old = it.next();
            if(old != camp && old.x == camp.x && old.y == camp.y && old.z == camp.z) it.remove();
        }
        for(java.util.Iterator<Camp> it = pending.iterator(); it.hasNext();) {
            Camp old = it.next();
            if(old.x == camp.x && old.y == camp.y && old.z == camp.z) it.remove();
        }
        if(!camps.contains(camp)) camps.add(camp);
        // Record generated columns once, using the existing compact provenance masks. Do not
        // issue a player-placement callback or repeatedly walk every camp for every edited block.
        Set<Long> columns = new HashSet<Long>();
        for(GardenerNests.Placement cell : preparation.edits.values()) {
            columns.add(column(cell.x, cell.z));
            camp.bottom = Math.min(camp.bottom, cell.y); camp.top = Math.max(camp.top, cell.y + 1);
        }
        for(long key : columns) recordColumn((int) (key >> 32), (int) key);
        markDirty();
        camp.journalReady = GardenerNests.checkpoint(world, this);
        if(!camp.journalReady) {
            camp.failed = true; camp.reason = "could not save construction journal; terrain work paused";
            return camp.reason;
        }
        return null;
    }
    static boolean constructionIntersects(World world, AxisAlignedBB box) {
        for(Camp camp : get(world).camps) if(camp.job != null
                && WretchCampSettlement.bounds(camp, 1, world.getActualHeight()).intersectsWith(box)) return true;
        return false;
    }
    private void build(WorldServer world, Camp camp) {
        if(!camp.journalReady) {
            camp.journalReady = GardenerNests.checkpoint(world, this);
            if(!camp.journalReady) { camp.failed = true; camp.reason = "could not save construction journal; terrain work paused"; return; }
        }
        int cursor = camp.cursor, pass = camp.pass;
        GardenerNests.beginPlacement();
        try {
            int edits = 0;
            for(int scans = 0; scans < 1024 && edits < 128; scans++) {
                if(camp.cursor >= camp.job.size()) {
                    camp.cursor = 0;
                    if(++camp.pass < 3) { camp.reason = "construction pass " + camp.pass + "/2"; return; }
                    List<GardenerNests.Placement> finished = camp.job;
                    int previousState = camp.state, previousRetries = camp.retries;
                    camp.job = null; camp.failed = camp.recovering = false; camp.reason = "none";
                    if(camp.state == 0 || camp.state == 4) { camp.state = 1; camp.retries = 0; }
                    if(!GardenerNests.checkpoint(world, this)) {
                        camp.job = finished; camp.pass = 2; camp.cursor = finished.size();
                        camp.state = previousState; camp.retries = previousRetries;
                        camp.failed = camp.recovering = true; camp.reason = "could not save completion record; retrying"; return;
                    }
                    markDirty();
                    GeminiIris.invasionPulse(world);
                    if(!camp.debug) schedule.success(this, world, "camp", camp.x, camp.y - 1, camp.z, GeminiConfig.wretchCampIntervalSeconds);
                    GeminiInvasionManager.debug(world, "camp-register", "Verified and registered " + description(camp)
                            + " at " + camp.x + ", " + (camp.y - 1) + ", " + camp.z + ".");
                    return;
                }
                GardenerNests.Placement cell = camp.job.get(camp.cursor);
                if(!GardenerNests.prepareChunk(world, cell.x, cell.z)) { camp.reason = "loading construction chunk"; return; }
                if(camp.pass == 1) {
                    world.notifyBlocksOfNeighborChange(cell.x, cell.y, cell.z, cell.block);
                    edits++;
                } else if(world.getBlock(cell.x, cell.y, cell.z) != cell.block || world.getBlockMetadata(cell.x, cell.y, cell.z) != cell.meta) {
                    if(cell.block.getMaterial().blocksMovement()) {
                        String obstruction = GardenerNests.clearConstructionCell(world, cell.x, cell.y, cell.z,
                                WretchCampSettlement.bounds(camp, 0, world.getActualHeight()));
                        if(obstruction != null) { camp.reason = obstruction; return; }
                    }
                    if(world.getTileEntity(cell.x, cell.y, cell.z) != null) world.removeTileEntity(cell.x, cell.y, cell.z);
                    if(!world.setBlock(cell.x, cell.y, cell.z, cell.block, cell.meta, 2)) {
                        camp.reason = "block insertion rejected at " + cell.x + "," + cell.y + "," + cell.z; return;
                    }
                    edits++;
                    if(camp.pass == 2) {
                        world.notifyBlocksOfNeighborChange(cell.x, cell.y, cell.z, cell.block);
                        if(world.getBlock(cell.x, cell.y, cell.z) != cell.block || world.getBlockMetadata(cell.x, cell.y, cell.z) != cell.meta) {
                            camp.reason = "block callback changed " + cell.x + "," + cell.y + "," + cell.z; return;
                        }
                    }
                }
                camp.cursor++;
            }
        } catch(RuntimeException failure) {
            if(!camp.reason.startsWith("block/chunk callback exception"))
                com.hbm.main.MainRegistry.logger.error("Wretch camp construction paused; saved plan retained", failure);
            camp.reason = "block/chunk callback exception (see server log)";
        } finally {
            GardenerNests.endPlacement();
            if(camp.job != null) {
                if(cursor != camp.cursor || pass != camp.pass) { camp.idle = 0; camp.failed = false; }
                else if(++camp.idle >= 1200) { camp.failed = true; camp.recovering = true; }
                if(camp.idle > 0) GeminiInvasionManager.debug(world, "camp-build-" + camp.x + ":" + camp.z,
                        "Camp construction " + (camp.failed ? "stalled; retrying with backoff" : "waiting")
                        + " at " + camp.x + "," + camp.y + "," + camp.z + "; pass=" + camp.pass
                        + "; cursor=" + camp.cursor + "; reason=" + camp.reason);
            }
            markDirty();
        }
    }
    private void tick(WorldServer world) {
        for(int count = 0; count < camps.size(); count++) {
            if(nextJob >= camps.size()) nextJob = 0;
            Camp camp = camps.get(nextJob++);
            if(camp.job == null || !(camp.debug || active(world)
                && world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                && world.getGameRules().getGameRuleBooleanValue("doMobSpawning"))) continue;
            if(camp.failed && world.getTotalWorldTime() % 200 != 0) continue;
            build(world, camp); break; // One shared, bounded camp job per server tick.
        }
        if(world.getTotalWorldTime() % 20 != 0) return;
        int work = 0;
        for(Camp camp : camps) {
            if(camp.job != null || camp.state == 0 || !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) continue;
            if(!active(world) && !camp.debug || world.getClosestPlayer(camp.x, camp.y, camp.z, camp.size / 2D + 64D) == null) continue;
            if(camp.state == 1 && GardenerNests.loadedVolume(world, AxisAlignedBB.getBoundingBox(camp.x - 10, 0, camp.z - 10,
                    camp.x + 11, world.getActualHeight(), camp.z + 11))) {
                if(spawn(world, camp)) work++;
            }
            if(work < 4) work += populate(world, camp, 4 - work);
            if(work >= 4) break;
        }
    }

    /** Saved one-shot population slots; caps pause them, obstruction/veto retries are finite. */
    private int populate(WorldServer world, Camp camp, int budget) {
        if(!active(world) || !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return 0;
        int work = 0;
        for(WretchCampSettlement.Piece piece : camp.pieces) {
            if(work >= budget) break;
            if(piece.role == 0 || piece.resident != 0) continue;
            int x = camp.x + piece.tx(0, piece.halfZ() - 1), z = camp.z + piece.tz(0, piece.halfZ() - 1);
            int y = piece.y;
            if(piece.kind == WretchCampSettlement.BURROW) {
                x = camp.x + piece.tx(0, -5); z = camp.z + piece.tz(0, -5);
                y -= 12 * Math.min(7, piece.y - 1) / 14;
            }
            if(!GardenerNests.loadedVolume(world, AxisAlignedBB.getBoundingBox(x - 3, 0, z - 3, x + 4, world.getActualHeight(), z + 4))) continue;
            UUID id = UUID.nameUUIDFromBytes((camp.boss.toString() + ":" + piece.x + ":" + piece.z).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(GeminiPopulation.find(world, id) != null) { piece.resident = 1; markDirty(); continue; }
            if(piece.role == WretchCampSettlement.CRAWLER ? !WoodcrawlerSpawning.canSpawn(world, null) : !FakePersonSpawning.canSpawn(world, null)) continue;
            boolean placed = false;
            try {
                net.minecraft.entity.EntityLiving mob;
                if(piece.role == WretchCampSettlement.CRAWLER) mob = new com.hbm.entity.mob.gemini.EntityWoodcrawler(world);
                else mob = new com.hbm.entity.mob.gemini.EntityFakePerson(world);
                mob.setLocationAndAngles(x + 0.5D, y, z + 0.5D, (float) (piece.rotation * 90), 0);
                NBTTagCompound identity = new NBTTagCompound();
                mob.writeToNBT(identity); identity.setLong("UUIDMost", id.getMostSignificantBits()); identity.setLong("UUIDLeast", id.getLeastSignificantBits());
                mob.readFromNBT(identity);
                if(mob instanceof com.hbm.entity.mob.gemini.EntityWoodcrawler)
                    placed = WoodcrawlerSpawning.spawnCampResident(world, (com.hbm.entity.mob.gemini.EntityWoodcrawler) mob);
                else placed = spawnPerson(world, (com.hbm.entity.mob.gemini.EntityFakePerson) mob);
            } catch(RuntimeException failure) {
                com.hbm.main.MainRegistry.logger.error("Camp resident admission failed at " + x + ", " + y + ", " + z, failure);
            }
            if(placed) piece.resident = 1;
            else if(++piece.retries >= 20) piece.resident = 2;
            work++; markDirty();
        }
        return work;
    }
    private static boolean spawnPerson(WorldServer world, com.hbm.entity.mob.gemini.EntityFakePerson mob) {
        if(!active(world) || !FakePersonSpawning.canSpawn(world, mob) || !clearResident(world, mob)) return false;
        if(net.minecraftforge.event.ForgeEventFactory.canEntitySpawn(mob, world, (float) mob.posX, (float) mob.posY, (float) mob.posZ)
                == cpw.mods.fml.common.eventhandler.Event.Result.DENY) return false;
        GeminiSpawning.markNatural(mob);
        if(!net.minecraftforge.event.ForgeEventFactory.doSpecialSpawn(mob, world, (float) mob.posX, (float) mob.posY, (float) mob.posZ)) mob.onSpawnWithEgg(null);
        return active(world) && world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                && FakePersonSpawning.canSpawn(world, mob) && clearResident(world, mob) && world.spawnEntityInWorld(mob);
    }
    private static boolean clearResident(WorldServer world, net.minecraft.entity.EntityLiving mob) {
        int x = MathHelper.floor_double(mob.posX), y = MathHelper.floor_double(mob.posY), z = MathHelper.floor_double(mob.posZ);
        return y >= 1 && mob.boundingBox.maxY < world.getActualHeight() && GardenerNests.loadedVolume(world, mob.boundingBox.expand(2, 0, 2))
                && World.doesBlockHaveSolidTopSurface(world, x, y - 1, z) && world.checkNoEntityCollision(mob.boundingBox)
                && world.getCollidingBoundingBoxes(mob, mob.boundingBox).isEmpty() && !world.isAnyLiquid(mob.boundingBox);
    }
    private boolean spawn(WorldServer world, Camp c) {
        if(c.state != 1) return false;
        if(GeminiPopulation.find(world, c.boss) != null) { c.state = 2; markDirty(); return false; }
        if(!active(world) && !(c.debug && GeminiConfig.enabled && GeminiConfig.wretchEnabled
                && world.difficultySetting != EnumDifficulty.PEACEFUL)) return false;
        if(!naturalBudget(c.boss)) {
            GeminiInvasionManager.debug(world, "camp-resident", "Wretch waiting: natural resident reservation limit."); return false;
        }
        if(!GeminiPopulation.canAdmit(world, EntityWretch.class, null, GeminiConfig.wretchMaxLoaded)) {
            GeminiInvasionManager.debug(world, "camp-resident", "Wretch waiting: loaded population limit."); return false;
        }
        EntityWretch boss = new EntityWretch(world);
        if(c.debug) GeminiSpawning.markStructureDebugSpawn(boss);
        boss.bindCamp(c.boss, c.x, c.y, c.z);
        boss.setLocationAndAngles(c.x + 0.5D, c.y, c.z + 0.5D, world.rand.nextFloat() * 360F, 0);
        // The fire ring clips the old center's foot-clearance box in either placement mode.
        boss.setPosition(c.x + 0.5D, c.y, c.z - 0.5D);
        if(boss.hasSpawnClearance() && !world.isAnyLiquid(boss.boundingBox)
                && world.getEntitiesWithinAABBExcludingEntity(boss, boss.boundingBox).isEmpty()
                && world.spawnEntityInWorld(boss)) {
            c.state = 2;
            GeminiInvasionManager.debug(world, "camp-resident", "Wretch spawned for camp at " + c.x + ", " + c.y + ", " + c.z + ".");
        } else {
            if(++c.retries >= 20) c.state = 4;
            GeminiInvasionManager.debug(world, "camp-resident", "Wretch spawn rejected: clearance, liquid, entity obstruction or Forge veto; retry=" + c.retries + ".");
        }
        markDirty();
        return true;
    }

    /** Counts persistent reservations, including defeat, rather than only currently loaded mobs. */
    private boolean naturalBudget(UUID candidate) {
        int count = 0, limit = GeminiConfig.wretchMultipleNatural ? GeminiConfig.wretchNaturalLimit : 1;
        for(Camp c : camps) if((c.state == 2 || c.state == 3) && !c.boss.equals(candidate) && ++count >= limit) return false;
        return true;
    }
    /** Legacy adapter; all real removal is validated against the current animated hand contact. */
    public static boolean breakNatural(EntityWretch boss, int x, int y, int z) {
        if(!boss.worldObj.blockExists(x, y, z) || !natural(boss.worldObj.getBlock(x, y, z))) return false;
        return breakContact(boss, x, y, z);
    }
    /** Side-effect-free policy check shared by obstruction selection and actual contact. */
    public static String contactRejection(EntityWretch boss, int x, int y, int z) {
        World world = boss.worldObj;
        if(!(world instanceof WorldServer) || !boss.isOperational() || !GeminiConfig.wretchTerrainDestruction
                || GeminiConfig.wretchBlocksPerStrike == 0) return "terrain destruction disabled/inactive";
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return "mobGriefing=false";
        if(!boss.inTerritory(x, z, boss.territoryRadius())) return "outside territory";
        if(y < 1 || y > 254 || !world.blockExists(x, y, z)) return "unloaded/world boundary";
        WretchCamps data = get(world);
        Block block = world.getBlock(x, y, z); int meta = world.getBlockMetadata(x, y, z);
        float hardness = block.getBlockHardness(world, x, y, z);
        if(block == Blocks.air || block == Blocks.tnt || block.getMaterial().isLiquid() || hardness < 0 || hardness > 5
                || block.hasTileEntity(meta) || world.getTileEntity(x, y, z) != null || !block.canEntityDestroy(world, x, y, z, boss)) return "unsafe/unbreakable block or tile";
        if(!GeminiConfig.wretchPlayerStructureDamage) {
            if(data.placementOverflow || data.placed(x, z)
                    || !data.generatedChunks.contains(column(x >> 4, z >> 4))) return "player placement/unknown chunk provenance";
            Camp camp = data.find(boss.getUniqueID());
            if(camp != null && camp.protectedColumns.contains(column(x, z))) return "protected camp column";
            boolean generated = GeminiConfig.wretchGeneratedStructureDamage && data.generatedBuilding(world, x, y, z);
            if(!natural(block) && !generated) return "protected construction";
            if(!generated) {
                // Conservative legacy construction guard, including natural-material supports beside walls.
                for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++) {
                    if(!world.blockExists(x + dx, y, z + dz)) return "unloaded protection boundary";
                    Block nearby = world.getBlock(x + dx, y, z + dz);
                    if(!natural(nearby) && nearby.getMaterial().blocksMovement()) return "adjacent protected construction";
                }
            }
        }
        return null;
    }
    private static boolean rejectContact(EntityWretch boss, int x, int y, int z, String reason) {
        boss.contactRejected(x, y, z, reason); return false;
    }
    public static boolean breakContact(EntityWretch boss, int x, int y, int z) {
        World world = boss.worldObj;
        String rejection = contactRejection(boss, x, y, z);
        if(rejection != null) return rejectContact(boss, x, y, z, rejection);
        if(!boss.mayBreakContact(x, y, z)) return rejectContact(boss, x, y, z, "no animated contact/destructive intent");
        Block block = world.getBlock(x, y, z); int meta = world.getBlockMetadata(x, y, z);
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        if(!world.canMineBlock(actor, x, y, z)) return rejectContact(boss, x, y, z, "mining permission");
        if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta, actor))) return rejectContact(boss, x, y, z, "Forge break veto");
        if(!boss.isOperational() || !GeminiConfig.wretchTerrainDestruction || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !boss.mayBreakContact(x, y, z) || world.getBlock(x, y, z) != block || world.getBlockMetadata(x, y, z) != meta
                || world.getTileEntity(x, y, z) != null || contactRejection(boss, x, y, z) != null)
            return rejectContact(boss, x, y, z, "state changed during break event");
        if(world.setBlock(x, y, z, Blocks.air, 0, 3)) {
            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) | meta << 12);
            boss.contactBroken(); return true;
        }
        return rejectContact(boss, x, y, z, "world rejected removal");
    }
    private boolean placed(int x, int z) {
        BitSet set = placedColumns.get(column(x >> 4, z >> 4));
        return set != null && set.get((x & 15) | ((z & 15) << 4));
    }
    /** Reuse recorded construction protection for non-destructive Gemini growth placement. */
    public static boolean hasPlayerPlacement(World world, AxisAlignedBB box) {
        WretchCamps data = get(world);
        if(data.placementOverflow) return true;
        for(int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
            for(int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++)
                if(data.placed(x, z)) return true;
        return false;
    }
    private boolean generatedBuilding(World world, int x, int y, int z) {
        if(world.provider.dimensionId != 0 || !generatedChunks.contains(column(x >> 4, z >> 4))) return false;
        long now = world.getTotalWorldTime();
        if(villageRefresh == Long.MIN_VALUE || now < villageRefresh || now - villageRefresh >= 600) {
            villageRefresh = now; villageBuildings.clear();
            MapGenStructureData villages = (MapGenStructureData) world.mapStorage.loadData(MapGenStructureData.class, "Village");
            if(villages != null) {
                NBTTagCompound entries = villages.func_143041_a(); int examined = 0;
                for(Object key : entries.func_150296_c()) {
                    if(++examined > 512 || villageBuildings.size() >= 1024) break;
                    NBTTagList children = entries.getCompoundTag((String) key).getTagList("Children", 10);
                    for(int i = 0; i < Math.min(128, children.tagCount()) && villageBuildings.size() < 1024; i++) {
                        NBTTagCompound part = children.getCompoundTagAt(i); String id = part.getString("id"); int[] bb = part.getIntArray("BB");
                        if(!id.startsWith("Vi") || id.equals("ViF") || id.equals("ViDF") || id.equals("ViSR") || id.equals("ViW")
                                || bb.length != 6 || bb[3] - bb[0] > 32 || bb[5] - bb[2] > 32 || bb[4] - bb[1] > 24) continue;
                        villageBuildings.add(AxisAlignedBB.getBoundingBox(bb[0], bb[1], bb[2], bb[3] + 1, bb[4] + 1, bb[5] + 1));
                    }
                }
            }
        }
        for(AxisAlignedBB box : villageBuildings) if(x >= box.minX && x < box.maxX && y >= box.minY && y < box.maxY && z >= box.minZ && z < box.maxZ) return true;
        return false;
    }
    private static long column(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    private void recordColumn(int x, int z) {
        long chunk = column(x >> 4, z >> 4);
        BitSet columns = placedColumns.get(chunk);
        if(columns == null && placedColumns.size() < 8192) { columns = new BitSet(256); placedColumns.put(chunk, columns); }
        if(columns == null) placementOverflow = true;
        else for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++)
            if(((x + dx) >> 4) == (x >> 4) && ((z + dz) >> 4) == (z >> 4)) columns.set(((x + dx) & 15) | (((z + dz) & 15) << 4));
    }
    private void protect(int x, int z) {
        recordColumn(x, z);
        markDirty();
        for(Camp c : camps) if(Math.abs(x - c.x) <= Math.max(34, c.size / 2 + 2) && Math.abs(z - c.z) <= Math.max(34, c.size / 2 + 2)) {
            for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++)
                c.protectedColumns.add(column(x + dx, z + dz));
            markDirty();
        }
    }
    public static final class Generator implements IWorldGenerator {
        @Override public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider provider) {
            // Provenance only: no terrain changes and no retroactive trust of old construction.
            if(!world.isRemote && world.provider.dimensionId == 0) {
                WretchCamps data = get(world);
                if(data.generatedChunks.size() < 32768 && data.generatedChunks.add(column(chunkX, chunkZ))) data.markDirty();
            }
        }
    }
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST) public void victimDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
            if(!event.isCanceled()) EntityWretch.victimDied(event);
        }
        @SubscribeEvent public void logout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) { EntityWretch.releaseVictim(event.player, false); }
        @SubscribeEvent public void login(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) { EntityWretch.releaseVictim(event.player, false); }
        @SubscribeEvent public void dimension(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) { EntityWretch.releaseVictim(event.player, false); }
        @SubscribeEvent public void safeFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
            if(event.entityLiving.worldObj.isRemote) return;
            net.minecraft.nbt.NBTTagCompound tag = event.entityLiving.getEntityData();
            if(tag.getLong("WretchSafeFallUntil") >= event.entityLiving.worldObj.getTotalWorldTime() && tag.hasKey("WretchSafeFallUntil")) event.setCanceled(true);
            tag.removeTag("WretchSafeFallUntil");
        }
        @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
            if(event.phase == TickEvent.Phase.END && event.world instanceof WorldServer && !event.world.isRemote
                    && !event.world.playerEntities.isEmpty())
                get(event.world).tick((WorldServer) event.world);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void join(EntityJoinWorldEvent event) {
            if(event.world.isRemote || !(event.entity instanceof EntityWretch)) return;
            EntityWretch boss = (EntityWretch) event.entity;
            if(!boss.addedToChunk && !boss.wasLoaded() && !GeminiSpawning.manualAdmission(boss) && (!owns(boss)
                    || !(active(event.world) || GeminiSpawning.structureDebugAdmission(boss)) || !get(event.world).naturalBudget(boss.getUniqueID())
                    || !GeminiPopulation.canAdmit(event.world, EntityWretch.class, boss, GeminiConfig.wretchMaxLoaded)))
                event.setCanceled(true);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void place(BlockEvent.PlaceEvent event) {
            if(event.world.isRemote || event.isCanceled()) return;
            // Ongoing Gardener mutations may use Forge placement events; they are not player construction.
            if(GardenerNests.placingNest() && event.player instanceof net.minecraftforge.common.util.FakePlayer) return;
            WretchCamps data = get(event.world);
            if(event instanceof BlockEvent.MultiPlaceEvent) {
                for(BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) data.protect(snapshot.x, snapshot.z);
            } else data.protect(event.x, event.z);
            WretchDisturbances.record(event.world, event.x + 0.5, event.y, event.z + 0.5, 3F, WretchDisturbances.WORK, event.player);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void broken(BlockEvent.BreakEvent event) {
            if(!event.isCanceled()) WretchDisturbances.record(event.world, event.x + 0.5, event.y, event.z + 0.5, 5F, WretchDisturbances.WORK, event.getPlayer());
        }
        @SubscribeEvent public void movement(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
            if(!(event.entityLiving instanceof EntityPlayer) || event.entityLiving.worldObj.isRemote || event.entityLiving.ticksExisted % 10 != 0) return;
            WretchDisturbances.movement((EntityPlayer) event.entityLiving);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void combat(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if(event.isCanceled() || event.ammount <= 0 || event.entityLiving instanceof EntityWretch) return;
            net.minecraft.entity.Entity source = event.source.getEntity();
            if(source instanceof EntityWretch || !(source instanceof EntityPlayer) && !(event.entityLiving instanceof EntityPlayer)) return;
            WretchDisturbances.record(event.entityLiving.worldObj, event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ,
                    9F, WretchDisturbances.COMBAT, source instanceof EntityPlayer ? source : event.entityLiving);
        }
        @SubscribeEvent public void explosion(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
            WretchDisturbances.record(event.world, event.explosion.explosionX, event.explosion.explosionY, event.explosion.explosionZ,
                    Math.min(35F, 12F + event.explosion.explosionSize * 2), WretchDisturbances.EXPLOSION, event.explosion.exploder);
        }
        @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
            WretchDisturbances.unload(event.world);
            if(!event.world.isRemote) for(Object entry : event.world.loadedEntityList)
                if(entry instanceof EntityWretch) ((EntityWretch) entry).suspendWretch();
        }
        @SubscribeEvent public void unloadChunk(net.minecraftforge.event.world.ChunkEvent.Unload event) {
            if(event.world.isRemote) return;
            for(java.util.List list : event.getChunk().entityLists) for(Object entry : list) {
                if(entry instanceof EntityWretch) ((EntityWretch) entry).suspendWretch();
                else if(entry instanceof net.minecraft.entity.EntityLivingBase) EntityWretch.releaseVictim((net.minecraft.entity.EntityLivingBase) entry, false);
            }
        }
    }
    static final class Camp {
        final int x, y, z;
        UUID boss = UUID.randomUUID();
        int state, retries; // abandoned construction, waiting, spawned, defeated, blocked spawn; never respawn spawned camps
        boolean debug;
        List<GardenerNests.Placement> job;
        NBTTagCompound unreadJob;
        int cursor, pass, idle;
        boolean failed, recovering;
        boolean journalReady;
        String reason = "legacy construction without a saved block plan";
        int layout, size = 13, major = 1, minor;
        int bottom, top;
        long seed;
        boolean graded;
        final List<WretchCampSettlement.Piece> pieces = new ArrayList<WretchCampSettlement.Piece>();
        final List<WretchCampSettlement.Road> roads = new ArrayList<WretchCampSettlement.Road>();
        final List<WretchCampSettlement.Road> boundary = new ArrayList<WretchCampSettlement.Road>();
        final Set<Long> protectedColumns = new HashSet<Long>();
        Camp(int x, int y, int z) { this.x = x; this.y = y; this.z = z; bottom = y - 1; top = y + 20; }
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        schedule.read(nbt);
        camps.clear(); placedColumns.clear(); generatedChunks.clear(); villageBuildings.clear(); villageRefresh = Long.MIN_VALUE;
        placementOverflow = nbt.getBoolean("PlacementOverflow");
        NBTTagList columns = nbt.getTagList("PlayerColumns", 10);
        for(int i = 0; i < Math.min(8192, columns.tagCount()); i++) {
            NBTTagCompound entry = columns.getCompoundTagAt(i); byte[] mask = entry.getByteArray("Mask");
            if(mask.length <= 32) placedColumns.put(column(entry.getInteger("X"), entry.getInteger("Z")), BitSet.valueOf(mask));
        }
        if(columns.tagCount() > 8192) placementOverflow = true;
        int[] trusted = nbt.getIntArray("GeneratedChunks");
        for(int i = 0; i + 1 < Math.min(trusted.length, 65536); i += 2) generatedChunks.add(column(trusted[i], trusted[i + 1]));
        for(Camp camp : readCamps(nbt.getTagList("Camps", 10))) camps.add(camp);
        pending.clear();
        for(Camp camp : readCamps(nbt.getTagList("PendingCamps", 10)))
            if(camp.layout == WretchCampSettlement.VERSION && pending.size() < LIMIT && at(camp.x, camp.y, camp.z) == null) pending.add(camp);
    }
    private static List<Camp> readCamps(NBTTagList list) {
        List<Camp> result = new ArrayList<Camp>();
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Camp camp = new Camp(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            camp.debug = tag.getBoolean("Debug");
            if(camp.y < 1 || camp.y > 236
                    || Math.abs((long) camp.x) > 29999984
                    || Math.abs((long) camp.z) > 29999984) continue;
            try { camp.boss = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { continue; }
            camp.state = MathHelper.clamp_int(tag.getInteger("State"), 0, 4);
            if(camp.state > 0) camp.reason = "none";
            camp.retries = MathHelper.clamp_int(tag.getInteger("Retries"), 0, 20);
            int protection = tag.getInteger("ProtectionVersion");
            for(int packed : tag.getIntArray("Protected")) {
                int dx = protection >= 2 ? (packed & 255) - 128 : (packed & 127) - 38;
                int dz = protection >= 2 ? (packed >> 8 & 255) - 128 : (packed >> 7 & 127) - 38;
                if(Math.abs(dx) <= 78 && Math.abs(dz) <= 78) camp.protectedColumns.add(column(camp.x + dx, camp.z + dz));
            }
            camp.layout = tag.getInteger("LayoutVersion");
            if(tag.hasKey("Bottom")) {
                camp.bottom = MathHelper.clamp_int(tag.getInteger("Bottom"), 0, 255);
                camp.top = MathHelper.clamp_int(tag.getInteger("Top"), camp.bottom + 1, 256);
            }
            if(camp.layout == WretchCampSettlement.VERSION) {
                camp.size = tag.getInteger("Size");
                if(camp.size != 80 && camp.size != 112 && camp.size != 144) continue;
                camp.seed = tag.getLong("LayoutSeed"); camp.graded = tag.getBoolean("Graded");
                camp.major = MathHelper.clamp_int(tag.getInteger("Major"), 6, 12);
                camp.minor = MathHelper.clamp_int(tag.getInteger("Minor"), 8, 16);
                NBTTagList pieces = tag.getTagList("Pieces", 10);
                boolean valid = pieces.tagCount() <= 28;
                for(int j = 0; j < pieces.tagCount() && valid; j++) {
                    NBTTagCompound part = pieces.getCompoundTagAt(j);
                    WretchCampSettlement.Piece piece = new WretchCampSettlement.Piece(part.getInteger("Kind"), part.getInteger("X"),
                            part.getInteger("Y"), part.getInteger("Z"), part.getInteger("Rotation"));
                    if(piece.kind < 0 || piece.kind > WretchCampSettlement.STORE || piece.rotation < 0 || piece.rotation > 3
                            || piece.y < 1 || piece.y > 236 || piece.x - piece.radiusX() < -camp.size / 2
                            || piece.x + piece.radiusX() >= camp.size / 2 || piece.z - piece.radiusZ() < -camp.size / 2
                            || piece.z + piece.radiusZ() >= camp.size / 2) { valid = false; break; }
                    piece.role = MathHelper.clamp_int(part.getInteger("Role"), 0, 2);
                    piece.resident = MathHelper.clamp_int(part.getInteger("Resident"), 0, 2);
                    piece.retries = MathHelper.clamp_int(part.getInteger("Retries"), 0, 20);
                    camp.pieces.add(piece);
                }
                readRoads(camp, tag.getIntArray("Roads"), camp.roads);
                readRoads(camp, tag.getIntArray("Boundary"), camp.boundary);
                if(!valid || camp.graded && (camp.pieces.size() != camp.major + camp.minor || camp.roads.isEmpty())) continue;
            } else if(camp.layout != 0) continue;
            camp.failed = tag.getBoolean("ConstructionFailed");
            camp.idle = MathHelper.clamp_int(tag.getInteger("ConstructionIdle"), 0, 1200);
            if(tag.hasKey("ConstructionReason")) camp.reason = tag.getString("ConstructionReason");
            if(tag.hasKey("Construction")) readJob(camp, tag.getCompoundTag("Construction"));
            result.add(camp);
        }
        return result;
    }
    private static void readRoads(Camp camp, int[] packed, List<WretchCampSettlement.Road> output) {
        for(int i = 0; i < Math.min(packed.length, camp.size * camp.size); i++) {
            int value = packed[i], x = (value & 255) - 128, z = (value >> 8 & 255) - 128;
            int y = value >> 16 & 255, width = value >>> 24;
            if(x >= -camp.size / 2 && x < camp.size / 2 && z >= -camp.size / 2 && z < camp.size / 2
                    && y >= 1 && y <= 236 && width >= 1 && width <= 4) output.add(new WretchCampSettlement.Road(x, y, z, width));
        }
    }
    private static int[] writeRoads(List<WretchCampSettlement.Road> roads) {
        int[] result = new int[roads.size()];
        for(int i = 0; i < result.length; i++) {
            WretchCampSettlement.Road road = roads.get(i);
            result[i] = (road.x + 128) | ((road.z + 128) << 8) | (road.y << 16) | (road.width << 24);
        }
        return result;
    }
    private static NBTTagCompound writeCamp(Camp camp) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("X", camp.x); tag.setInteger("Y", camp.y); tag.setInteger("Z", camp.z);
        tag.setString("Boss", camp.boss.toString()); tag.setInteger("State", camp.state); tag.setInteger("Retries", camp.retries);
        tag.setBoolean("Debug", camp.debug);
        tag.setBoolean("ConstructionFailed", camp.failed); tag.setInteger("ConstructionIdle", camp.idle);
        tag.setString("ConstructionReason", camp.reason);
        if(camp.job != null) tag.setTag("Construction", writeJob(camp));
        else if(camp.unreadJob != null) tag.setTag("Construction", camp.unreadJob);
        tag.setInteger("Bottom", camp.bottom); tag.setInteger("Top", camp.top);
        tag.setInteger("ProtectionVersion", 2);
        int[] columns = new int[camp.protectedColumns.size()]; int index = 0;
        for(long key : camp.protectedColumns) columns[index++] = ((int) (key >> 32) - camp.x + 128) | (((int) key - camp.z + 128) << 8);
        tag.setIntArray("Protected", columns);
        if(camp.layout == WretchCampSettlement.VERSION) {
            tag.setInteger("LayoutVersion", camp.layout); tag.setInteger("Size", camp.size); tag.setLong("LayoutSeed", camp.seed);
            tag.setInteger("Major", camp.major); tag.setInteger("Minor", camp.minor); tag.setBoolean("Graded", camp.graded);
            NBTTagList pieces = new NBTTagList();
            for(WretchCampSettlement.Piece piece : camp.pieces) {
                NBTTagCompound part = new NBTTagCompound();
                part.setInteger("Kind", piece.kind); part.setInteger("X", piece.x); part.setInteger("Y", piece.y); part.setInteger("Z", piece.z);
                part.setInteger("Rotation", piece.rotation); part.setInteger("Role", piece.role); part.setInteger("Resident", piece.resident); part.setInteger("Retries", piece.retries);
                pieces.appendTag(part);
            }
            tag.setTag("Pieces", pieces); tag.setIntArray("Roads", writeRoads(camp.roads)); tag.setIntArray("Boundary", writeRoads(camp.boundary));
        }
        return tag;
    }
    private static NBTTagCompound writeJob(Camp camp) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList palette = new NBTTagList();
        Map<String, Integer> indices = new HashMap<String, Integer>();
        int[] positions = new int[camp.job.size()], states = new int[positions.length];
        for(int i = 0; i < positions.length; i++) {
            GardenerNests.Placement cell = camp.job.get(i);
            String name = Block.blockRegistry.getNameForObject(cell.block), key = name + ":" + cell.meta;
            Integer index = indices.get(key);
            if(index == null) {
                index = indices.size(); indices.put(key, index);
                NBTTagCompound entry = new NBTTagCompound(); entry.setString("Block", name); entry.setInteger("Meta", cell.meta);
                palette.appendTag(entry);
            }
            positions[i] = ((cell.x - camp.x + 128) << 16) | ((cell.z - camp.z + 128) << 8) | cell.y;
            states[i] = index;
        }
        tag.setTag("Palette", palette); tag.setIntArray("Positions", positions); tag.setIntArray("States", states);
        tag.setInteger("Cursor", camp.cursor); tag.setInteger("Pass", camp.pass);
        return tag;
    }
    private static void readJob(Camp camp, NBTTagCompound tag) {
        NBTTagList palette = tag.getTagList("Palette", 10);
        int[] positions = tag.getIntArray("Positions"), states = tag.getIntArray("States");
        List<GardenerNests.Placement> cells = new ArrayList<GardenerNests.Placement>();
        boolean valid = positions.length > 0 && positions.length == states.length && positions.length <= camp.size * camp.size * 256;
        for(int i = 0; i < positions.length && valid; i++) {
            int x = (positions[i] >>> 16 & 255) - 128, z = (positions[i] >>> 8 & 255) - 128, y = positions[i] & 255;
            if(states[i] < 0 || states[i] >= palette.tagCount() || x < -camp.size / 2 || x >= camp.size / 2 || z < -camp.size / 2 || z >= camp.size / 2) { valid = false; break; }
            NBTTagCompound entry = palette.getCompoundTagAt(states[i]);
            Block block = Block.getBlockFromName(entry.getString("Block"));
            if(block == null) { valid = false; break; }
            cells.add(new GardenerNests.Placement(camp.x + x, y, camp.z + z, block, entry.getInteger("Meta") & 15));
        }
        if(!valid) {
            camp.unreadJob = (NBTTagCompound) tag.copy(); camp.failed = true;
            camp.reason = "saved block plan invalid or contains an unavailable block; retained without terrain changes"; return;
        }
        camp.job = cells; camp.pass = MathHelper.clamp_int(tag.getInteger("Pass"), 0, 2);
        camp.journalReady = true;
        camp.cursor = camp.pass == 2 ? 0 : MathHelper.clamp_int(tag.getInteger("Cursor"), 0, cells.size());
        camp.recovering = true;
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        schedule.write(nbt);
        NBTTagList list = new NBTTagList();
        for(Camp camp : camps) list.appendTag(writeCamp(camp));
        nbt.setTag("Camps", list);
        NBTTagList queued = new NBTTagList();
        for(Camp camp : pending) queued.appendTag(writeCamp(camp));
        nbt.setTag("PendingCamps", queued);
        nbt.setBoolean("PlacementOverflow", placementOverflow);
        NBTTagList columns = new NBTTagList();
        for(Map.Entry<Long, BitSet> entry : placedColumns.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound(); long key = entry.getKey();
            tag.setInteger("X", (int) (key >> 32)); tag.setInteger("Z", (int) key); tag.setByteArray("Mask", entry.getValue().toByteArray()); columns.appendTag(tag);
        }
        nbt.setTag("PlayerColumns", columns);
        int[] trusted = new int[generatedChunks.size() * 2]; int index = 0;
        for(long key : generatedChunks) { trusted[index++] = (int) (key >> 32); trusted[index++] = (int) key; }
        nbt.setIntArray("GeneratedChunks", trusted);
    }
}
