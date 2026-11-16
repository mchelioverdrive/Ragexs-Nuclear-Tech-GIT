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
    private long villageRefresh = Long.MIN_VALUE;
    public WretchCamps(String name) { super(name); }

    public static final String TYPE = "wretch";
    private static GeminiInvasionManager.StructureInfo info(Camp camp, int dimension) {
        String[] states = {"abandoned construction", "waiting for Wretch", "Wretch spawned", "defeated", "blocked spawn"};
        return new GeminiInvasionManager.StructureInfo(TYPE, camp.x, camp.y - 1, camp.z, dimension,
                states[camp.state] + "; " + description(camp) + "; " + population(camp));
    }
    public static List<GeminiInvasionManager.StructureInfo> known(World world) {
        List<GeminiInvasionManager.StructureInfo> result = new ArrayList<GeminiInvasionManager.StructureInfo>();
        if(!(world instanceof WorldServer) || world.isRemote) return result;
        String name = NAME + (world.provider.dimensionId == 0 ? "" : "_" + world.provider.dimensionId);
        WretchCamps data = (WretchCamps) world.mapStorage.loadData(WretchCamps.class, name);
        if(data != null) for(Camp camp : data.camps) result.add(info(camp, world.provider.dimensionId));
        return result;
    }
    private static String generationGate(World world) {
        if(!(world instanceof WorldServer) || world.isRemote || world.provider.dimensionId != 0) return "Overworld server only";
        if(!active(world)) return "requires enabled invasion/Wretch, phase 4 and non-Peaceful difficulty";
        if(!world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return "doMobSpawning is false";
        if(!world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return "mobGriefing is false";
        if(GeminiConfig.wretchCampChance == 0) return "camp chance disabled";
        if(get(world).camps.size() >= GeminiConfig.wretchCampLimit) return "camp limit reached";
        if(GeminiStructureSchedule.players(world).isEmpty()) return "no living survival/adventure players";
        return null;
    }
    public static String eligibility(World world) {
        String gate = generationGate(world);
        return gate == null ? "eligible in loaded terrain around survival players" : gate;
    }
    public static String diagnostics(World world) {
        if(!(world instanceof WorldServer) || world.isRemote) return "server only";
        return "Automatic camps enabled=" + (GeminiConfig.enabled && GeminiConfig.wretchEnabled
                && GeminiConfig.wretchCampChance > 0 && GeminiConfig.wretchCampLimit > 0)
                + "; " + get(world).schedule.status() + "; " + GeminiStructureSchedule.availability(world,
                        GeminiConfig.wretchCampMinDistance, GeminiConfig.wretchCampMaxDistance, 42);
    }
    static void reschedule(WorldServer world) { WretchCamps data = get(world); data.schedule.reschedule(data); }
    static void schedule(WorldServer world) {
        if(generationGate(world) != null) return;
        WretchCamps data = get(world);
        if(!data.schedule.due(data, GeminiConfig.wretchCampIntervalSeconds)) return;
        try {
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
        Camp selected = null, previous = null;
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
            Camp camp = saved != null && saved.layout == WretchCampSettlement.VERSION ? saved : WretchCampSettlement.create(world, x, y, z);
            if(saved != null && saved != camp) { camp.boss = saved.boss; camp.state = saved.state; camp.retries = saved.retries; camp.protectedColumns.addAll(saved.protectedColumns); }
            GardenerNests.PlacementPlan plan = WretchCampSettlement.plan(world, camp);
            if(plan.reason != null) GardenerNests.countRejection(rejected, plan.reason);
            else { best = plan; selected = camp; previous = saved; break; }
        }
        if(best == null) throw GardenerNests.searchFailure(world, "camp", requested != null, rejected);
        String reason = data.place(world, selected, StructurePlacementMode.FORCED_DEBUG, best);
        if(reason != null) throw GeminiInvasionManager.rejected(world, "camp", "Camp placement failed: " + reason);
        if(previous != null && previous != selected) data.camps.remove(previous);
        final Camp completed = selected;
        for(java.util.Iterator<Camp> it = data.pending.iterator(); it.hasNext();) {
            Camp queued = it.next();
            if(queued.x == completed.x && queued.z == completed.z) it.remove();
        }
        selected.debug = true; data.markDirty();
        try {
            if(selected.state == 1 && world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) data.spawn(world, selected);
            if(world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) data.populate(world, selected, 4);
        } catch(RuntimeException failure) {
            com.hbm.main.MainRegistry.logger.error("Debug camp resident initialization failed; saved slots retained", failure);
        }
        return new GeminiInvasionManager.StructureInfo(TYPE, selected.x, selected.y - 1, selected.z, world.provider.dimensionId,
                "mode=forced debug; " + description(selected) + "; replaced blocks=" + best.replaced
                + "; registered=" + (previous == null ? "new" : "existing") + "; " + population(selected));
    }

    private Camp at(int x, int y, int z) {
        for(Camp camp : camps) if(camp.x == x && camp.y == y && camp.z == z) return camp;
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
        int start = world.rand.nextInt(players.size()), plans = 0;
        for(int attempt = 0; attempt < GeminiStructureSchedule.ATTEMPTS && plans < 2; attempt++) {
            EntityPlayer player = players.get((start + attempt) % players.size());
            net.minecraft.util.ChunkCoordinates point = GeminiStructureSchedule.candidate(world,
                    MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ),
                    GeminiConfig.wretchCampMinDistance, GeminiConfig.wretchCampMaxDistance);
            // Upgrade old population-selected jobs through this same bounded scheduler. An
            // unavailable selection is released so it cannot reserve an unusable area forever.
            if(!pending.isEmpty()) {
                Camp legacy = pending.remove(0); markDirty();
                double dx = legacy.x - player.posX, dz = legacy.z - player.posZ;
                double distance = dx * dx + dz * dz;
                if(distance >= (double) GeminiConfig.wretchCampMinDistance * GeminiConfig.wretchCampMinDistance
                        && distance <= (double) GeminiConfig.wretchCampMaxDistance * GeminiConfig.wretchCampMaxDistance)
                    point = new net.minecraft.util.ChunkCoordinates(legacy.x, 0, legacy.z);
            }
            int x = point.posX, z = point.posZ;
            if(!spaced(x, z)) { GardenerNests.countRejection(rejected, "camp spacing"); continue; }
            if(!GeminiStructureSchedule.footprint(world, x, z, 42)) {
                GardenerNests.countRejection(rejected, "complete minimum settlement footprint not loaded or outside world bounds"); continue;
            }
            int surface = ground(world, x, z);
            if(surface < 1) { GardenerNests.countRejection(rejected, "no surface terrain"); continue; }
            Camp camp = WretchCampSettlement.create(world, x, MathHelper.clamp_int(surface, 1, world.getActualHeight() - 20), z);
            if(!GardenerNests.loadedVolume(world, WretchCampSettlement.bounds(camp, 2, world.getActualHeight())))
                camp.size = 80; // Still village-sized; fit the loaded area without requesting chunks.
            plans++;
            String reason = place(world, camp, StructurePlacementMode.NATURAL, WretchCampSettlement.plan(world, camp));
            if(reason == null) {
                schedule.success(this, world, "camp", camp.x, camp.y - 1, camp.z, GeminiConfig.wretchCampIntervalSeconds);
                return null;
            }
            GardenerNests.countRejection(rejected, reason);
        }
        return "bounded loaded-area search: " + rejected;
    }
    private String place(WorldServer world, Camp camp, StructurePlacementMode mode, GardenerNests.PlacementPlan preparation) {
        String reason = GardenerNests.place(world, preparation, false, mode);
        if(reason != null) return reason;
        if(!camps.contains(camp)) camps.add(camp);
        // Record generated columns once, using the existing compact provenance masks. Do not
        // issue a player-placement callback or repeatedly walk every camp for every edited block.
        Set<Long> columns = new HashSet<Long>();
        for(GardenerNests.Placement cell : preparation.edits.values()) {
            columns.add(column(cell.x, cell.z));
            camp.bottom = Math.min(camp.bottom, cell.y); camp.top = Math.max(camp.top, cell.y + 1);
        }
        for(long key : columns) recordColumn((int) (key >> 32), (int) key);
        if(camp.state == 0 || camp.state == 4) { camp.state = 1; camp.retries = 0; }
        markDirty();
        GeminiInvasionManager.debug(world, "camp-register", "Completed " + description(camp) + " at " + camp.x + ", " + (camp.y - 1) + ", " + camp.z
                + "; replaced blocks=" + preparation.replaced + ".");
        return null;
    }
    private void tick(WorldServer world) {
        int work = 0;
        for(Camp camp : camps) {
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
    public static boolean breakContact(EntityWretch boss, int x, int y, int z) {
        World world = boss.worldObj;
        if(!(world instanceof WorldServer) || !boss.isOperational() || !GeminiConfig.wretchTerrainDestruction
                || !world.getGameRules().getGameRuleBooleanValue("mobGriefing") || !boss.inTerritory(x, z, boss.territoryRadius())
                || y < 1 || y > 254 || !world.blockExists(x, y, z) || !boss.mayBreakContact(x, y, z)) return false;
        WretchCamps data = get(world);
        Block block = world.getBlock(x, y, z); int meta = world.getBlockMetadata(x, y, z);
        float hardness = block.getBlockHardness(world, x, y, z);
        if(block == Blocks.air || block == Blocks.tnt || block.getMaterial().isLiquid() || hardness < 0 || hardness > 5
                || block.hasTileEntity(meta) || world.getTileEntity(x, y, z) != null || !block.canEntityDestroy(world, x, y, z, boss)) return false;
        if(!GeminiConfig.wretchPlayerStructureDamage) {
            if(data.placementOverflow || data.placed(x, z)
                    || !data.generatedChunks.contains(column(x >> 4, z >> 4))) return false;
            Camp camp = data.find(boss.getUniqueID());
            if(camp != null && camp.protectedColumns.contains(column(x, z))) return false;
            boolean generated = GeminiConfig.wretchGeneratedStructureDamage && data.generatedBuilding(world, x, y, z);
            if(!natural(block) && !generated) return false;
            if(!generated) {
                // Conservative legacy construction guard, including natural-material supports beside walls.
                for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++) {
                    if(!world.blockExists(x + dx, y, z + dz)) return false;
                    Block nearby = world.getBlock(x + dx, y, z + dz);
                    if(!natural(nearby) && nearby.getMaterial().blocksMovement()) return false;
                }
            }
        }
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        if(!world.canMineBlock(actor, x, y, z) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta, actor))) return false;
        if(!boss.isOperational() || !GeminiConfig.wretchTerrainDestruction || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !boss.mayBreakContact(x, y, z) || world.getBlock(x, y, z) != block || world.getBlockMetadata(x, y, z) != meta
                || world.getTileEntity(x, y, z) != null) return false;
        if(world.setBlock(x, y, z, Blocks.air, 0, 3)) { boss.contactBroken(); return true; }
        return false;
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
                    && GeminiConfig.enabled && GeminiConfig.wretchEnabled
                    && event.world.difficultySetting != EnumDifficulty.PEACEFUL && event.world.getTotalWorldTime() % 20 == 0
                    && !event.world.playerEntities.isEmpty() && event.world.getGameRules().getGameRuleBooleanValue("doMobSpawning"))
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
            if(camp.layout == WretchCampSettlement.VERSION && pending.size() < LIMIT) pending.add(camp);
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
            result.add(camp);
        }
        return result;
    }
    private static void readRoads(Camp camp, int[] packed, List<WretchCampSettlement.Road> output) {
        for(int i = 0; i < Math.min(packed.length, camp.size * camp.size); i++) {
            int value = packed[i], x = (value & 255) - 128, z = (value >> 8 & 255) - 128;
            int y = value >> 16 & 255, width = value >>> 24;
            if(x >= -camp.size / 2 && x < camp.size / 2 && z >= -camp.size / 2 && z < camp.size / 2
                    && y >= 1 && y <= 236 && width >= 2 && width <= 4) output.add(new WretchCampSettlement.Road(x, y, z, width));
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
