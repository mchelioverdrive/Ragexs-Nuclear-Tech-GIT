package com.hbm.world.gen.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.hbm.blocks.BlockDummyable;
import com.hbm.config.SettlementConfig;
import com.hbm.config.SettlementConfig.Values;
import com.hbm.config.StructureConfig;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.network.TileEntityPylonBase;
import com.hbm.util.fauxpointtwelve.Rotation;
import com.hbm.world.gen.structure.SettlementData.Operation;
import com.hbm.world.gen.structure.SettlementData.Phase;
import com.hbm.world.gen.structure.SettlementData.Placement;
import com.hbm.world.gen.structure.SettlementData.Site;
import com.hbm.world.gen.structure.SettlementData.Slice;
import com.hbm.world.gen.structure.SettlementPlan.Column;
import com.hbm.world.gen.structure.SettlementPlan.State;
import com.hbm.world.gen.structure.SettlementRoads.Profile;
import com.hbm.world.gen.structure.SettlementTerrain.Earthwork;
import com.hbm.world.squirrelstapler.StaplerWorldType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPressurePlate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class SettlementGenerator implements IWorldGenerator {
    private static final SettlementGenerator INSTANCE = new SettlementGenerator();
    private static final SettlementKind[] KINDS = SettlementKind.values();
    private static final int SURVEY_COST = 64;
    private static final int PLACEMENT_COST = 4;
    private static final int CHUNK_TIMEOUT = 200;
    private static final Phase[] PHASES = Phase.values();
    private static final String PROGRESS = "RNTMSettlementProgress";

    private final Map<World, Session> sessions = new WeakHashMap<>();

    private SettlementGenerator() { }

    public static void register() {
        com.hbm.lib.HbmWorld.registerWorldGen(INSTANCE, 500);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @Override
    public void generate(Random ignored, int cx, int cz, World world, IChunkProvider generator, IChunkProvider provider) {
        if(!naturalAllowed(world)) return;
        ChunkProviderServer server = provider(world);
        if(server == null) return;
        Session session = session(world);
        // Tick-driven chunk loads must not seed more settlement jobs.
        if(session.loadingChunk) return;
        Chunk chunk = loaded(server, cx, cz);
        if(chunk == null || session.progress(chunk).seeded || !session.fresh(chunk) || chunk.inhabitedTime != 0) return;
        session.progress(chunk).seeded = true;

        Values config = SettlementConfig.settings;
        Random random = new Random(world.getSeed() ^ (long) cx * 341873128712L ^ (long) cz * 132897987541L ^ 0x73B604C1326F20E7L);
        int start = random.nextInt(KINDS.length - 1);
        for(int i = 0; i < KINDS.length - 1; i++) {
            SettlementKind kind = KINDS[(start + i) % (KINDS.length - 1)];
            int frequency = kind.frequency(config);
            if(frequency == 0 || random.nextInt(frequency) != 0) continue;
            int x = (cx << 4) + 8, z = (cz << 4) + 8;
            if(Math.abs(x) >= 29999600 || Math.abs(z) >= 29999600) return;
            BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x, z);
            Candidate candidate = new Candidate(SettlementDistrict.alternatives(kind, x, z, random.nextLong(), config),
                    chunk.getHeightValue(x & 15, z & 15) - 1, world.getTotalWorldTime());
            if(!BiomeDictionary.isBiomeOfType(biome, Type.PLAINS) && !BiomeDictionary.isBiomeOfType(biome, Type.SAVANNA)
                    && !BiomeDictionary.isBiomeOfType(biome, Type.SPARSE)) {
                diagnostic(world, "rejected", candidate.description(), "biome " + biome.biomeName);
                continue;
            }
            if(session.candidates.size() >= 16) {
                diagnostic(world, "rejected", candidate.description(), "natural candidate queue is full");
                return;
            }
            session.candidates.addLast(candidate);
            diagnostic(world, "queued", candidate.description(), "awaiting naturally populated chunks");
            return;
        }
    }

    /** Authorize the complete footprint before persisting the wand request. */
    public static void request(World world, EntityPlayer player, SettlementKind kind, int variant, Rotation rotation,
            Profile profile, int x, int y, int z) {
        if(world.isRemote) return;
        if(!player.capabilities.isCreativeMode) {
            chat(player, "creative");
            return;
        }
        if(provider(world) == null) {
            chat(player, "storage");
            return;
        }
        SettlementData data = INSTANCE.session(world).data(world);
        Placement placement = new Placement(kind, variant, rotation, kind == SettlementKind.ROAD ? profile : null, x, y, z, player.getUniqueID(), 1);
        if(data.queued(placement)) {
            chat(player, "duplicate");
            return;
        }
        if(data.busy()) {
            chat(player, "busy");
            return;
        }
        SettlementPlan plan;
        try {
            plan = placement.build(world.getSeed());
        } catch(RuntimeException failure) {
            diagnostic(world, "failed", placement.description(), reason(failure));
            chat(player, "failed", 0, reason(failure));
            return;
        }
        for(Column column : plan.columns.values()) {
            if(!SettlementTerrain.inBounds(world, column)) {
                diagnostic(world, "rejected", placement.description(), "world limits at " + position(column));
                chat(player, "height");
                return;
            }
            if(!world.canMineBlock(player, column.x, column.y, column.z)) {
                diagnostic(world, "rejected", placement.description(), "permission denied at " + position(column));
                chat(player, "protected", column.x, column.y, column.z);
                return;
            }
        }
        data.enqueue(placement);
        diagnostic(world, "queued", placement.description(), "forced request added to the world queue");
        chat(player, "forced", new ChatComponentTranslation(kind.variantKey(variant)), x, y, z);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if(!(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        ChunkProviderServer provider = provider(world);
        if(provider == null) return;
        if(event.phase == TickEvent.Phase.START) {
            Session existing = sessions.get(world);
            if(existing != null) existing.keepChunks(provider);
            return;
        }
        Session session = session(world);
        Values config = SettlementConfig.settings;
        long now = world.getTotalWorldTime();
        boolean natural = naturalAllowed(world);
        session.requestedChunk = false;
        session.keepChunks(provider);
        session.candidates.removeIf(candidate -> {
            if(natural && now <= candidate.expiry) return false;
            diagnostic(world, "rejected", candidate.description(), natural ? "candidate timed out waiting for available terrain" : "natural generation disabled");
            return true;
        });

        // Serialize terrain mutation so overlapping wand jobs apply in order.
        Site site = session.data(world).nextPlacement();
        if(site != null) {
            place(world, provider, session, site, config.workPerTick);
            return;
        }
        Placement request = session.data.request();
        if(request != null && session.draft != null && session.draft.site.natural) {
            Draft deferred = session.draft;
            deferred.candidate.layouts.add(0, deferred.district);
            session.candidates.addLast(deferred.candidate);
            session.draft = null;
            session.release(world, provider);
            diagnostic(world, "queued", description(deferred.site), "survey deferred for a wand request");
        }
        if(session.draft == null) {
            if(request != null) {
                try {
                    session.draft = new Draft(request.build(world.getSeed()), request, null, null, 0, now);
                    diagnostic(world, "generating", request.description(), "preparing forced terrain");
                    tell(request.owner, "preparing");
                } catch(RuntimeException failure) {
                    session.data.finishRequest();
                    diagnostic(world, "failed", request.description(), reason(failure));
                    tell(request.owner, "failed", 0, reason(failure));
                }
            } else if(natural) {
                startNatural(world, provider, session, config);
            }
        }
        if(session.draft != null) survey(world, provider, session, config.workPerTick);
    }

    private static void startNatural(WorldServer world, ChunkProviderServer provider, Session session, Values config) {
        int count = Math.min(6, session.candidates.size());
        while(count-- > 0) {
            Candidate candidate = session.candidates.removeFirst();
            Iterator<SettlementDistrict> layouts = candidate.layouts.iterator();
            while(layouts.hasNext()) {
                SettlementDistrict district = layouts.next();
                String rejection = null;
                if(!district.enabled(config)) rejection = "structure types or connected roads disabled by configuration";
                else if(session.data.tooClose(district.bounds, config.separation)) rejection = "site separation or overlap";
                if(rejection == null) {
                    Availability state = session.availability(provider, district);
                    if(state == Availability.WAITING) {
                        // Give the preferred road or orientation time to load before using a smaller footprint.
                        if(world.getTotalWorldTime() < candidate.expiry - 2300) break;
                        continue;
                    }
                    if(state == Availability.USED) rejection = "footprint crosses previously saved or edited terrain";
                }
                layouts.remove();
                int attempt = ++candidate.attempts;
                if(rejection == null) {
                    try {
                        SettlementPlan plan = district.build((x, z) -> SettlementTerrain.surface(world, loaded(provider, x >> 4, z >> 4), x, z));
                        int y = plan.columns.values().iterator().next().y;
                        Placement placement = new Placement(district.kind, district.variant(), district.rotation, district.road,
                                district.x, y, district.z, null, attempt);
                        Draft draft = new Draft(plan, placement, candidate, district, config.terrainDepth, world.getTotalWorldTime());
                        session.holdFootprint(provider, draft.site);
                        session.draft = draft;
                        diagnostic(world, "generating", placement.description(), "surveying " + district.description());
                        return;
                    } catch(RuntimeException failure) {
                        rejection = reason(failure);
                    }
                }
                diagnostic(world, "rejected", candidate.description(district, attempt), rejection);
            }
            if(!candidate.layouts.isEmpty()) session.candidates.addLast(candidate);
        }
    }

    private static void survey(WorldServer world, ChunkProviderServer provider, Session session, int budget) {
        Draft draft = session.draft;
        boolean natural = draft.site.natural;
        if(natural && (draft.changed || !naturalAllowed(world) || !draft.district.enabled(SettlementConfig.settings))) {
            rejectSurvey(world, provider, session, draft, "site edited or natural generation disabled during survey", false);
            return;
        }
        Chunk chunk = null;
        for(int work = 0; work + SURVEY_COST <= budget && draft.next != null; work += SURVEY_COST) {
            Column column = draft.next;
            int cx = column.x >> 4, cz = column.z >> 4;
            try {
                if(chunk == null || chunk.xPosition != cx || chunk.zPosition != cz) {
                    if(!natural && !session.prepare(world, provider, cx, cz, false)) {
                        if(world.getTotalWorldTime() - draft.lastWork > CHUNK_TIMEOUT)
                            rejectSurvey(world, provider, session, draft, "could not load or populate chunks around " + cx + "," + cz, true);
                        else surveyProgress(world, draft);
                        return;
                    }
                    chunk = loaded(provider, cx, cz);
                    if(chunk == null || (natural && !session.fresh(chunk))) {
                        rejectSurvey(world, provider, session, draft, "footprint unloaded or changed at " + position(column), false);
                        return;
                    }
                    session.hold(provider, cx, cz);
                }
                Slice slice = draft.site.slices.get(key(cx, cz));
                String failure = SettlementTerrain.survey(world, chunk, slice, column, draft.depth, draft.clearanceTop, draft.earthwork);
                if(failure != null) {
                    rejectSurvey(world, provider, session, draft, failure + " at " + position(column), !natural);
                    return;
                }
            } catch(RuntimeException failure) {
                rejectSurvey(world, provider, session, draft, reason(failure) + " at " + position(column), !natural);
                return;
            }
            draft.next = draft.columns.hasNext() ? draft.columns.next() : null;
            draft.surveyed++;
            draft.lastWork = world.getTotalWorldTime();
        }
        if(draft.next != null) {
            surveyProgress(world, draft);
            return;
        }
        if(natural && (session.availability(provider, draft.district) != Availability.READY || session.data.overlaps(draft.site))) {
            rejectSurvey(world, provider, session, draft, "site unloaded, changed or reserved before construction", false);
            return;
        }

        session.draft = null;
        session.data.accept(draft.site);
        if(!natural) session.data.finishRequest();
        diagnostic(world, "queued", description(draft.site), "terrain prepared " + draft.earthwork
                + ", footprint chunks=" + draft.site.slices.size() + ", operations=" + draft.site.totalWork);
    }

    private static void rejectSurvey(WorldServer world, ChunkProviderServer provider, Session session, Draft draft, String reason, boolean failed) {
        session.draft = null;
        session.release(world, provider);
        diagnostic(world, failed ? "failed" : "rejected", description(draft.site), reason + " after " + draft.surveyed + " columns, " + draft.earthwork);
        if(draft.candidate != null) {
            if(!draft.candidate.layouts.isEmpty()) session.candidates.addLast(draft.candidate);
        } else {
            session.data.finishRequest();
            tell(draft.site.placement.owner, "failed", 0, reason);
        }
    }

    private static void surveyProgress(World world, Draft draft) {
        if(draft.site.natural || world.getTotalWorldTime() - draft.lastMessage < 100) return;
        draft.lastMessage = world.getTotalWorldTime();
        tell(draft.site.placement.owner, "surveyProgress", draft.surveyed, draft.totalColumns);
    }

    private static void place(WorldServer world, ChunkProviderServer provider, Session session, Site site, int budget) {
        long now = world.getTotalWorldTime();
        if(session.placing != site) {
            session.placing = site;
            session.phase = -1;
            session.placementSlices = site.slices.values().iterator();
            session.current = null;
            session.completed = site.savedCompleted;
            session.unchanged = site.savedUnchanged;
            session.failed = session.unavailable = 0;
            session.lastFailure = null;
            session.lastProgress = session.lastMessage = now;
            diagnostic(world, "generating", description(site), "preparing all footprint chunks, " + session.counts(site));
            tell(owner(site), "building");
        }

        Phase phase = session.phase < 0 ? null : PHASES[session.phase];
        while(budget >= PLACEMENT_COST) {
            if(session.current == null) {
                if(!session.placementSlices.hasNext()) {
                    if(++session.phase == PHASES.length) finish(world, provider, session, site);
                    else session.placementSlices = site.slices.values().iterator();
                    return;
                }
                session.current = session.placementSlices.next();
                session.lastProgress = now;
                session.chunkErrors = 0;
            }
            Slice slice = session.current;
            if(slice.unavailable != null) {
                if(phase == null) session.unavailable(slice);
            } else {
                Chunk chunk = loaded(provider, slice.x, slice.z);
                if((phase == null && (!site.natural || !slice.footprint.isEmpty())) || (chunk == null && slice.pending())) {
                    try {
                        if(!session.prepare(world, provider, slice.x, slice.z, site.natural)) {
                            if(now - session.lastProgress <= CHUNK_TIMEOUT) break;
                            throw new IllegalStateException("timed out preparing chunks");
                        }
                        chunk = loaded(provider, slice.x, slice.z);
                    } catch(RuntimeException failure) {
                        if(++session.chunkErrors < 3 && now - session.lastProgress <= CHUNK_TIMEOUT) break;
                        // A broken chunk must not prevent work on the rest of the site.
                        slice.unavailable = "chunk " + slice.x + "," + slice.z + ": " + reason(failure);
                        session.unavailable(slice);
                        session.data.markDirty();
                        diagnostic(world, "unresolved", description(site), slice.unavailable);
                        session.current = null;
                        budget -= PLACEMENT_COST;
                        continue;
                    }
                }
                if(slice.pending()) {
                    Checkpoint checkpoint = session.progress(chunk).checkpoints.computeIfAbsent(site.id, id -> new Checkpoint());
                    if(phase == null) {
                        session.completed += checkpoint.completed;
                        session.failed += checkpoint.failed;
                        session.unchanged += checkpoint.unchanged;
                        if(checkpoint.lastFailure != null) session.lastFailure = checkpoint.lastFailure;
                    } else {
                        while(budget >= PLACEMENT_COST && checkpoint.cursor < slice.limit(phase)) {
                            runOperation(world, chunk, session, slice, checkpoint, phase);
                            checkpoint.cursor++;
                            chunk.setChunkModified();
                            budget -= PLACEMENT_COST;
                        }
                        if(checkpoint.cursor < slice.limit(phase)) break;
                    }
                }
            }
            session.current = null;
            budget -= PLACEMENT_COST;
        }
        if(now - session.lastMessage >= 100) {
            session.lastMessage = now;
            tell(owner(site), "buildProgress", session.completed, site.totalWork, session.remaining(site), session.failed);
            diagnostic(world, "generating", description(site), (phase == null ? "chunks" : phase.name()) + ", " + session.counts(site));
        }
    }

    private static void runOperation(WorldServer world, Chunk chunk, Session session, Slice slice, Checkpoint checkpoint, Phase phase) {
        Operation op = slice.at(checkpoint.cursor, phase);
        int column = op.position & 255;
        int lx = column & 15, lz = column >> 4;
        int x = (slice.x << 4) + lx, z = (slice.z << 4) + lz, y = op.position >> 8;
        State expected = slice.state(op.after);
        boolean initial = phase == Phase.BASE || phase == Phase.ATTACHMENTS;
        try {
            if(y < 0 || y >= Math.min(256, world.getActualHeight())) throw new IllegalStateException("operation outside world height");
            if(expected.block == null) throw new IllegalStateException("saved target block is missing");
            if(slice.site.natural && checkpoint.protectedColumns.get(column)) throw new IllegalStateException("natural site was edited");
            if(phase == Phase.ANIMALS) {
                if(op.legacyStep < 0 || op.legacyStep >= checkpoint.legacyCursor) spawn(world, chunk, op.animal, x, y, z);
            } else if(initial || !matches(chunk, lx, y, lz, expected, true) || !hasDetails(world, expected, op, x, y, z)) {
                if(!matches(chunk, lx, y, lz, expected, !initial)) checkpoint.written.set(op.position);
                install(world, chunk, slice, op, x, y, z, initial);
            }
            if(!initial) {
                if(phase == Phase.VERIFY && !supported(world, expected, x, y, z)) throw new IllegalStateException("missing block support or matching half");
                if(phase == Phase.VERIFY && !checkpoint.written.get(op.position)) {
                    checkpoint.unchanged++;
                    session.unchanged++;
                }
                checkpoint.completed++;
                session.completed++;
            }
        } catch(RuntimeException failure) {
            String details = x + "," + y + "," + z + " expected " + blockState(expected.block, expected.meta)
                    + ", actual " + blockState(chunk.getBlock(lx, y, lz), chunk.getBlockMetadata(lx, y, lz)) + " (" + reason(failure) + ")";
            if(initial) {
                if(checkpoint.recoverable++ < 3) diagnostic(world, "recoverable", description(slice.site), details);
                return;
            }
            checkpoint.failed++;
            session.failed++;
            checkpoint.lastFailure = session.lastFailure = details;
            if(session.failed <= 12) diagnostic(world, "unresolved", description(slice.site), details);
        }
    }

    private static void spawn(World world, Chunk chunk, String name, int x, int y, int z) {
        if(chunk.getBlock(x & 15, y, z & 15) != Blocks.air) throw new IllegalStateException("livestock space is blocked");
        Entity animal = EntityList.createEntityByName(name, world);
        if(animal == null) throw new IllegalStateException("unknown livestock " + name);
        animal.setLocationAndAngles(x + .5D, y + .05D, z + .5D, 0, 0);
        if(!world.spawnEntityInWorld(animal)) throw new IllegalStateException("server rejected spawning " + name);
    }

    private static boolean matches(Chunk chunk, int x, int y, int z, State expected, boolean mature) {
        Block block = chunk.getBlock(x, y, z);
        if(block != expected.block) return false;
        int meta = chunk.getBlockMetadata(x, y, z);
        if(meta == expected.meta || block == Blocks.air) return true;
        // Finished sections may tick while later chunks are still being written.
        if(!mature) return false;
        if(block == Blocks.farmland || block instanceof BlockPressurePlate) return true;
        if(block == Blocks.wheat || block == Blocks.carrots || block == Blocks.potatoes
                || block instanceof com.hbm.blocks.BlockCrop) return meta >= expected.meta;
        if(block instanceof BlockBed) return (meta & ~4) == (expected.meta & ~4);
        if(block instanceof BlockButton) return (meta & 7) == (expected.meta & 7);
        if(block instanceof BlockDoor) {
            int mask = (expected.meta & 8) == 0 ? 11 : 9;
            return (meta & mask) == (expected.meta & mask);
        }
        return false;
    }

    private static boolean supported(World world, State state, int x, int y, int z) {
        Block block = state.block;
        int meta = state.meta;
        if(block instanceof BlockBush && block instanceof IPlantable) {
            return world.getBlock(x, y - 1, z).canSustainPlant(world, x, y - 1, z, ForgeDirection.UP, (IPlantable) block);
        }
        if(block instanceof BlockDoor) {
            boolean upper = (meta & 8) != 0;
            int otherY = y + (upper ? -1 : 1);
            return world.getBlock(x, otherY, z) == block && ((world.getBlockMetadata(x, otherY, z) & 8) != 0) != upper
                    && World.doesBlockHaveSolidTopSurface(world, x, upper ? y - 2 : y - 1, z);
        }
        if(block instanceof BlockBed) {
            boolean head = BlockBed.isBlockHeadOfBed(meta);
            int[] offset = BlockBed.field_149981_a[meta & 3];
            int otherX = x + (head ? -offset[0] : offset[0]), otherZ = z + (head ? -offset[1] : offset[1]);
            return world.getBlock(otherX, y, otherZ) == block
                    && (world.getBlockMetadata(otherX, y, otherZ) & 11) == (meta ^ 8)
                    && World.doesBlockHaveSolidTopSurface(world, x, y - 1, z);
        }
        if(block instanceof BlockButton) {
            int[] sides = {0, 5, 4, 3, 2};
            int facing = meta & 7;
            return facing > 0 && facing < sides.length && block.canPlaceBlockOnSide(world, x, y, z, sides[facing]);
        }
        if(block instanceof BlockLadder || block == Blocks.wall_sign) {
            ForgeDirection side = ForgeDirection.getOrientation(meta);
            int supportX = x - side.offsetX, supportZ = z - side.offsetZ;
            return side.offsetY == 0 && side != ForgeDirection.UNKNOWN && (block == Blocks.wall_sign
                    ? world.getBlock(supportX, y, supportZ).getMaterial().isSolid() : world.isSideSolid(supportX, y, supportZ, side));
        }
        if(block instanceof BlockPressurePlate) {
            return World.doesBlockHaveSolidTopSurface(world, x, y - 1, z) || BlockFence.func_149825_a(world.getBlock(x, y - 1, z));
        }
        return !(block instanceof BlockCarpet) || !world.isAirBlock(x, y - 1, z);
    }

    private static boolean hasDetails(World world, State state, Operation op, int x, int y, int z) {
        if(!state.block.hasTileEntity(state.meta)) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if(op.sign == null) return tile != null;
        if(!(tile instanceof TileEntitySign)) return false;
        String[] lines = ((TileEntitySign) tile).signText;
        for(int i = 0; i < lines.length; i++) if(!(i < op.sign.length ? op.sign[i] : "").equals(lines[i])) return false;
        return true;
    }

    private static void install(World world, Chunk chunk, Slice slice, Operation op, int x, int y, int z, boolean initial) {
        int lx = x & 15, lz = z & 15;
        State after = slice.state(op.after);
        Block block = chunk.getBlock(lx, y, lz);
        int meta = chunk.getBlockMetadata(lx, y, lz);
        boolean forced = !slice.site.natural;
        if(!forced && block != after.block) {
            State before = slice.state(op.before);
            if((block != before.block || meta != before.meta) && !SettlementTerrain.naturallyChanged(world, before.block, block, x, y, z))
                throw new IllegalStateException("natural block changed");
        }

        if(forced && block.hasTileEntity(meta) && (initial || block != after.block)) {
            // The old block's break callback must never see a tile of the new type.
            TileEntity tile = world.getTileEntity(x, y, z);
            if(tile instanceof TileEntityPylonBase) ((TileEntityPylonBase) tile).disconnectAll();
            chunk.removeTileEntity(lx, y, lz);
            if(block != after.block && after.block.hasTileEntity(after.meta)) {
                writeBlock(world, x, y, z, block, SettlementPlan.AIR, true);
                block = chunk.getBlock(lx, y, lz);
            }
        }
        if(block != after.block) writeBlock(world, x, y, z, block, after, forced);
        if(chunk.getBlock(lx, y, lz) == after.block && !matches(chunk, lx, y, lz, after, !initial)) {
            // onBlockAdded can pick its own facing (for example furnaces and chests).
            world.setBlockMetadataWithNotify(x, y, z, after.meta, 2);
        }
        if(!matches(chunk, lx, y, lz, after, !initial)) throw new IllegalStateException("block state did not settle");
        if(op.sign != null) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if(!(tile instanceof TileEntitySign)) throw new IllegalStateException("missing sign tile entity");
            String[] lines = ((TileEntitySign) tile).signText;
            for(int i = 0; i < lines.length; i++) lines[i] = i < op.sign.length ? op.sign[i] : "";
            tile.markDirty();
            world.markBlockForUpdate(x, y, z);
        } else if(forced && after.block.hasTileEntity(after.meta)) {
            world.markBlockForUpdate(x, y, z);
        }
        if(!hasDetails(world, after, op, x, y, z)) throw new IllegalStateException("missing tile entity or sign text");
    }

    private static String blockState(Block block, int meta) {
        return (block == null ? "<missing block>" : Block.blockRegistry.getNameForObject(block)) + " (metadata " + meta + ")";
    }

    private static void writeBlock(World world, int x, int y, int z, Block before, State after, boolean forced) {
        if(!forced || !(before instanceof BlockDummyable)) {
            world.setBlock(x, y, z, after.block, after.meta, 2);
            return;
        }
        // Replacing a dummy at the footprint boundary must not delete its core outside the plot.
        boolean previous = BlockDummyable.safeRem;
        BlockDummyable.safeRem = true;
        try {
            world.setBlock(x, y, z, after.block, after.meta, 2);
        } finally {
            BlockDummyable.safeRem = previous;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) { edited(event.world, event.x, event.z); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if(event instanceof BlockEvent.MultiPlaceEvent) {
            for(BlockSnapshot block : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) edited(event.world, block.x, block.z);
        } else edited(event.world, event.x, event.z);
    }

    private void edited(World world, int x, int z) {
        if(!(world instanceof WorldServer) || world.provider.dimensionId != 0) return;
        Session session = session(world);
        ChunkProviderServer provider = provider(world);
        if(provider == null) return;
        Chunk chunk = loaded(provider, x >> 4, z >> 4);
        if(chunk == null) return;
        ChunkProgress progress = session.progress(chunk);
        progress.touched = true;
        long key = key(chunk.xPosition, chunk.zPosition);
        int column = SettlementData.columnIndex(x, z);
        if(session.draft != null && session.draft.site.natural) {
            Slice part = session.draft.site.slices.get(key);
            if(part != null && part.footprint.get(column)) session.draft.changed = true;
        }
        if(session.data == null) return;
        for(Slice slice : session.data.at(key)) {
            if(slice.site.natural && slice.pending() && !slice.site.placed && slice.footprint.get(column)) {
                progress.checkpoints.computeIfAbsent(slice.site.id, id -> new Checkpoint()).protectedColumns.set(column);
                chunk.setChunkModified();
            }
        }
    }

    @SubscribeEvent
    public void readChunk(ChunkDataEvent.Load event) {
        if(!(event.world instanceof WorldServer)) return;
        if(event.world.provider.dimensionId != 0 && !event.getData().hasKey(PROGRESS)) return;
        ChunkProgress progress = session(event.world).progress(event.getChunk());
        progress.fromDisk = true;
        NBTTagList list = event.getData().getTagList(PROGRESS, 10);
        for(int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Checkpoint checkpoint = new Checkpoint();
            if(tag.getBoolean("Phased")) checkpoint.cursor = checkpoint.savedCursor = tag.getInteger("Cursor");
            else checkpoint.legacyCursor = tag.getInteger("Cursor");
            if(tag.hasKey("LegacyCursor")) checkpoint.legacyCursor = tag.getInteger("LegacyCursor");
            checkpoint.completed = tag.getInteger("Completed");
            checkpoint.failed = tag.getInteger("Failed");
            checkpoint.unchanged = tag.getInteger("Unchanged");
            checkpoint.written = BitSet.valueOf(tag.getByteArray("Written"));
            checkpoint.lastFailure = tag.hasKey("LastFailure") ? tag.getString("LastFailure") : null;
            progress.checkpoints.put(tag.getLong("Site"), checkpoint);
            checkpoint.protectedColumns = BitSet.valueOf(tag.getByteArray("Protected"));
        }
    }

    @SubscribeEvent
    public void saveChunk(ChunkDataEvent.Save event) {
        if(!(event.world instanceof WorldServer)) return;
        Session session = sessions.get(event.world);
        if(session == null) return;
        Chunk chunk = event.getChunk();
        ChunkProgress progress = session.chunks.get(chunk);
        if(progress == null || progress.checkpoints.isEmpty()) return;
        NBTTagList list = new NBTTagList();
        Iterator<Map.Entry<Long, Checkpoint>> entries = progress.checkpoints.entrySet().iterator();
        while(entries.hasNext()) {
            Map.Entry<Long, Checkpoint> entry = entries.next();
            Site site = session.data == null ? null : session.data.site(entry.getKey());
            if(session.data != null && site == null) {
                entries.remove();
                continue;
            }
            Checkpoint checkpoint = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Site", entry.getKey());
            tag.setBoolean("Phased", true);
            tag.setInteger("Cursor", checkpoint.cursor);
            tag.setInteger("Completed", checkpoint.completed);
            tag.setInteger("Failed", checkpoint.failed);
            tag.setInteger("Unchanged", checkpoint.unchanged);
            if(checkpoint.legacyCursor != 0) tag.setInteger("LegacyCursor", checkpoint.legacyCursor);
            if(!checkpoint.written.isEmpty()) tag.setByteArray("Written", checkpoint.written.toByteArray());
            if(checkpoint.lastFailure != null) tag.setString("LastFailure", checkpoint.lastFailure);
            if(!checkpoint.protectedColumns.isEmpty()) tag.setByteArray("Protected", checkpoint.protectedColumns.toByteArray());
            list.appendTag(tag);
            checkpoint.savedCursor = checkpoint.cursor;
            if(site != null && site.placed) {
                Slice slice = site.slices.get(key(chunk.xPosition, chunk.zPosition));
                if(slice != null && slice.pending() && checkpoint.cursor >= slice.limit(Phase.ANIMALS))
                    session.data.saved(slice, checkpoint.completed, checkpoint.unchanged);
            }
        }
        event.getData().setTag(PROGRESS, list);
    }

    @SubscribeEvent
    public void unloadChunk(ChunkEvent.Unload event) {
        if(!(event.world instanceof WorldServer) || event.world.provider.dimensionId != 0) return;
        Session session = sessions.get(event.world);
        if(session != null) session.progress(event.getChunk()).fromDisk = true;
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if(event.world instanceof WorldServer) sessions.remove(event.world);
    }

    private static boolean naturalAllowed(World world) {
        return world instanceof WorldServer && world.provider.dimensionId == 0 && !StaplerWorldType.suppressStructures(world)
                && (StructureConfig.enableStructures == 1 || StructureConfig.enableStructures == 2 && world.getWorldInfo().isMapFeaturesEnabled());
    }

    private static ChunkProviderServer provider(World world) {
        if(!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;
        ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
        return provider.currentChunkLoader instanceof AnvilChunkLoader ? provider : null;
    }

    private static long key(int x, int z) { return ChunkCoordIntPair.chunkXZ2Int(x, z); }

    private static Chunk loaded(ChunkProviderServer provider, int x, int z) {
        return (Chunk) provider.loadedChunkHashMap.getValueByKey(key(x, z));
    }

    private Session session(World world) { return sessions.computeIfAbsent(world, key -> new Session()); }

    private static void diagnostic(World world, String stage, String description, String details) {
        if(SettlementConfig.settings.diagnostics)
            MainRegistry.logger.info("[RNTM settlements] {} dim={} {} - {}", stage, world.provider.dimensionId, description, details);
    }

    private static String description(Site site) {
        return (site.placement == null ? "legacy " + (site.natural ? "natural" : "wand") + " site" : site.placement.description())
                + (site.id == 0 ? "" : " site=" + site.id)
                + " bounds=[" + site.bounds.minX + "," + site.bounds.minY + "," + site.bounds.minZ + " to "
                + site.bounds.maxX + "," + site.bounds.maxY + "," + site.bounds.maxZ + "] chunks=" + site.slices.size();
    }

    private static String position(Column column) { return column.x + "," + column.y + "," + column.z; }

    private static String reason(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static UUID owner(Site site) { return site.placement == null ? null : site.placement.owner; }

    private static void finish(WorldServer world, ChunkProviderServer provider, Session session, Site site) {
        if(session.failed != 0 || session.unavailable != 0 || session.remaining(site) != 0) {
            session.data.fail(site);
            diagnostic(world, "incomplete", description(site), session.counts(site) + ", last failure=" + session.lastFailure);
            tell(owner(site), "incomplete", session.completed, site.totalWork, session.failed, session.lastFailure);
        } else {
            site.placed = true;
            for(Slice slice : site.slices.values()) {
                Chunk chunk = loaded(provider, slice.x, slice.z);
                if(chunk == null || !slice.pending()) continue;
                Checkpoint checkpoint = session.progress(chunk).checkpoints.get(site.id);
                if(checkpoint != null && checkpoint.savedCursor >= slice.limit(Phase.ANIMALS))
                    session.data.saved(slice, checkpoint.completed, checkpoint.unchanged);
            }
            diagnostic(world, "completed", description(site), session.counts(site));
            tell(owner(site), "complete", session.completed, session.unchanged);
        }
        session.release(world, provider);
        session.placing = null;
    }

    private static void chat(EntityPlayer player, String key, Object... values) {
        player.addChatMessage(new ChatComponentTranslation("settlement.wand." + key, values));
    }

    private static void tell(UUID owner, String key, Object... values) {
        if(owner == null) return;
        for(Object online : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayer player = (EntityPlayer) online;
            if(owner.equals(player.getUniqueID())) {
                chat(player, key, values);
                return;
            }
        }
    }

    private enum Availability { WAITING, READY, USED }

    private static final class Session {
        final Map<Chunk, ChunkProgress> chunks = new WeakHashMap<>();
        final ArrayDeque<Candidate> candidates = new ArrayDeque<>();
        final Set<Long> releaseChunks = new HashSet<>();
        final Set<Long> heldChunks = new HashSet<>();
        SettlementData data;
        Draft draft;
        Site placing;
        Iterator<Slice> placementSlices;
        Slice current;
        int phase, completed, unchanged, failed, unavailable, chunkErrors;
        String lastFailure;
        long lastProgress, lastMessage;
        boolean loadingChunk, requestedChunk;

        int remaining(Site site) { return site.totalWork - completed - failed; }

        String counts(Site site) {
            return "planned=" + site.totalWork + " completed=" + completed + " remaining=" + remaining(site)
                    + " failed=" + failed + " unchanged=" + unchanged + " unavailable chunks=" + unavailable;
        }

        void unavailable(Slice slice) {
            unavailable++;
            failed += slice.size();
            lastFailure = slice.unavailable;
        }

        SettlementData data(World world) {
            if(data == null) data = SettlementData.get(world);
            return data;
        }

        ChunkProgress progress(Chunk chunk) { return chunks.computeIfAbsent(chunk, key -> new ChunkProgress()); }

        boolean fresh(Chunk chunk) {
            ChunkProgress progress = chunks.get(chunk);
            return progress == null || (!progress.fromDisk && !progress.touched);
        }

        Availability availability(ChunkProviderServer provider, SettlementDistrict district) {
            Availability result = Availability.READY;
            for(long key : district.chunks) {
                int x = (int) key, z = (int) (key >>> 32);
                Chunk chunk = loaded(provider, x, z);
                if(chunk != null && !fresh(chunk)) return Availability.USED;
                if(!populated(provider, x, z)) result = Availability.WAITING;
            }
            return result;
        }

        private static boolean populated(ChunkProviderServer provider, int x, int z) {
            // Vanilla biome decoration overlaps the eastern and southern neighboring chunks.
            for(int dx = -1; dx <= 0; dx++) for(int dz = -1; dz <= 0; dz++) {
                Chunk chunk = loaded(provider, x + dx, z + dz);
                if(chunk == null || !chunk.isTerrainPopulated) return false;
            }
            return true;
        }

        boolean prepare(WorldServer world, ChunkProviderServer provider, int x, int z, boolean natural) {
            boolean allPresent = true;
            // Keep loaded parts of this window available while acquiring the next chunk.
            for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) {
                int cx = x + dx, cz = z + dz;
                long key = key(cx, cz);
                hold(provider, cx, cz);
                if(loaded(provider, cx, cz) != null) continue;
                if(requestedChunk) {
                    allPresent = false;
                    continue;
                }
                if(natural && !((AnvilChunkLoader) provider.currentChunkLoader).chunkExists(world, cx, cz)) return false;
                requestedChunk = loadingChunk = true;
                releaseChunks.add(key);
                try {
                    Chunk chunk = provider.loadChunk(cx, cz);
                    if(chunk == null || chunk instanceof EmptyChunk) throw new IllegalStateException("chunk provider did not supply " + cx + "," + cz);
                } finally {
                    loadingChunk = false;
                }
            }
            if(!allPresent) return false;
            if(!populated(provider, x, z)) {
                // A disk-only loader can leave an unpopulated chunk in an already loaded window.
                if(!natural && !requestedChunk) populateOne(provider, x, z);
                if(!populated(provider, x, z)) return false;
            }
            return true;
        }

        void hold(ChunkProviderServer provider, int x, int z) {
            long key = key(x, z);
            heldChunks.add(key);
            if(provider.chunksToUnload.remove(key)) releaseChunks.add(key);
        }

        void holdFootprint(ChunkProviderServer provider, Site site) {
            for(Slice slice : site.slices.values()) {
                if(slice.footprint.isEmpty()) continue;
                for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++) {
                    if(loaded(provider, slice.x + dx, slice.z + dz) != null) hold(provider, slice.x + dx, slice.z + dz);
                }
            }
        }

        void keepChunks(ChunkProviderServer provider) {
            for(long key : heldChunks) if(provider.chunksToUnload.remove(key)) releaseChunks.add(key);
        }

        private void populateOne(ChunkProviderServer provider, int x, int z) {
            for(int dx = -1; dx <= 0; dx++) for(int dz = -1; dz <= 0; dz++) {
                if(loaded(provider, x + dx, z + dz).isTerrainPopulated) continue;
                requestedChunk = loadingChunk = true;
                try {
                    provider.populate(provider, x + dx, z + dz);
                } finally {
                    loadingChunk = false;
                }
                return;
            }
        }

        void release(WorldServer world, ChunkProviderServer provider) {
            for(long key : releaseChunks) {
                int cx = (int) key, cz = (int) (key >>> 32);
                if(loaded(provider, cx, cz) != null && !world.getPlayerManager().func_152621_a(cx, cz))
                    provider.unloadChunksIfNotNearSpawn(cx, cz);
            }
            releaseChunks.clear();
            heldChunks.clear();
        }
    }

    private static final class ChunkProgress {
        boolean fromDisk, seeded, touched;
        final Map<Long, Checkpoint> checkpoints = new HashMap<>();
    }

    private static final class Checkpoint {
        int cursor, savedCursor, legacyCursor, completed, unchanged, failed, recoverable;
        String lastFailure;
        BitSet protectedColumns = new BitSet(256);
        BitSet written = new BitSet();
    }

    private static final class Draft {
        final Site site;
        final Iterator<Column> columns;
        final Candidate candidate;
        final SettlementDistrict district;
        final int depth, clearanceTop, totalColumns;
        final Earthwork earthwork = new Earthwork();
        Column next;
        int surveyed;
        long lastWork, lastMessage;
        boolean changed;

        Draft(SettlementPlan plan, Placement placement, Candidate candidate, SettlementDistrict district, int depth, long now) {
            site = new Site(plan, placement);
            clearanceTop = site.bounds.maxY;
            List<Column> traversal = new ArrayList<>(plan.columns.values());
            traversal.sort(Comparator.comparingLong(column -> key(column.x >> 4, column.z >> 4)));
            columns = traversal.iterator();
            next = columns.hasNext() ? columns.next() : null;
            totalColumns = plan.columns.size();
            this.candidate = candidate;
            this.district = district;
            this.depth = depth;
            lastWork = lastMessage = now;
        }
    }

    private static final class Candidate {
        final List<SettlementDistrict> layouts;
        final SettlementDistrict preferred;
        final int surfaceY;
        final long expiry;
        int attempts;

        Candidate(List<SettlementDistrict> layouts, int surfaceY, long now) {
            this.layouts = layouts;
            preferred = layouts.get(0);
            this.surfaceY = surfaceY;
            expiry = now + 2400;
        }

        String description() { return description(preferred, attempts + 1); }

        String description(SettlementDistrict district, int attempt) {
            return "natural " + district.kind.variantName(district.variant()) + " at " + district.x + "," + surfaceY + "," + district.z
                    + " attempt=" + attempt + " " + district.description();
        }
    }
}
