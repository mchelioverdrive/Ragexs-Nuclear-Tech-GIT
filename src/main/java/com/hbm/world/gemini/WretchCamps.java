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
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;

/** New-chunk-only camps. No retrogen, chunk tickets, respawn timer or replacement terrain engine. */
public final class WretchCamps extends WorldSavedData {
    private static final String NAME = "GeminiWretchCamps";
    private static final int LIMIT = 256;
    private final List<Camp> camps = new ArrayList<Camp>();
    private final Map<Long, BitSet> placedColumns = new HashMap<Long, BitSet>();
    private final Set<Long> generatedChunks = new HashSet<Long>();
    private final List<AxisAlignedBB> villageBuildings = new ArrayList<AxisAlignedBB>();
    private boolean placementOverflow;
    private long villageRefresh = Long.MIN_VALUE;
    public WretchCamps(String name) { super(name); }

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
    private static boolean safe(World world, int x, int y, int z) {
        if(y < 2 || y > 250 || !world.blockExists(x, y, z)) return false;
        Block b = world.getBlock(x, y, z);
        return natural(b) && !b.hasTileEntity(world.getBlockMetadata(x, y, z)) && world.getTileEntity(x, y, z) == null;
    }
    private static int ground(World world, int x, int z) {
        int y = Math.min(245, world.getHeightValue(x, z));
        for(int steps = 0; steps < 48 && y > 45; steps++, y--) {
            Block b = world.getBlock(x, y, z);
            if(b == Blocks.grass || b == Blocks.dirt) return y + 1;
            if(!natural(b)) return -1;
        }
        return -1;
    }
    private boolean spaced(int x, int z) {
        for(Camp camp : camps) {
            double dx = (double) x - camp.x, dz = (double) z - camp.z;
            if(dx * dx + dz * dz < 512D * 512D) return false;
        }
        return true;
    }
    private void generate(WorldServer world, int chunkX, int chunkZ) {
        if(camps.size() >= LIMIT || GeminiConfig.wretchCampChance == 0) return;
        Random random = new Random(world.getSeed() ^ chunkX * 341873128712L ^ chunkZ * 132897987541L);
        if(random.nextInt(GeminiConfig.wretchCampChance) != 0) return;
        int x = (chunkX << 4) + 8, z = (chunkZ << 4) + 8;
        if(!spaced(x, z) || !WoodcrawlerSpawning.isForest(world, x, z)
                || !GardenerNests.loaded(world, AxisAlignedBB.getBoundingBox(x - 7, 0, z - 7, x + 8, 256, z + 8))) return;
        int y = ground(world, x, z);
        if(y < 48 || y > 180) return;
        // Reject construction, tiles and unsuitable ground across the entire clearing before reserving it.
        for(int dx = -6; dx <= 6; dx++) for(int dz = -6; dz <= 6; dz++) {
            int floor = ground(world, x + dx, z + dz);
            if(floor < y - 1 || floor > y + 1) return;
            for(int dy = -2; dy <= 8; dy++) if(!safe(world, x + dx, y + dy, z + dz)) return;
            // Preserve existing trees; require a natural clearing with room for the body and tents.
            if(Math.abs(dx) <= 5 && Math.abs(dz) <= 5)
                for(int dy = 0; dy <= 5; dy++) if(world.getBlock(x + dx, y + dy, z + dz).getMaterial().blocksMovement()) return;
        }
        AxisAlignedBB clearing = AxisAlignedBB.getBoundingBox(x - 6, y - 1, z - 6, x + 7, y + 8, z + 7);
        if(!world.getEntitiesWithinAABBExcludingEntity(null, clearing).isEmpty()) return;
        Camp camp = new Camp(x, y, z);
        // Reserve before the first mutation. Even a vetoed partial camp is never generated again.
        camps.add(camp); markDirty();
        List<Cell> plan = layout(camp);
        EntityPlayer actor = FakePlayerFactory.getMinecraft(world);
        for(Cell cell : plan) {
            if(!active(world) || !safe(world, cell.x, cell.y, cell.z)
                    || !world.canMineBlock(actor, cell.x, cell.y, cell.z)) return;
            Block old = world.getBlock(cell.x, cell.y, cell.z);
            int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
            if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(cell.x, cell.y, cell.z, world, old, meta, actor))) return;
            if(!safe(world, cell.x, cell.y, cell.z) || world.getBlock(cell.x, cell.y, cell.z) != old
                    || world.getBlockMetadata(cell.x, cell.y, cell.z) != meta) return;
            BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, cell.x, cell.y, cell.z);
            if(!world.setBlock(cell.x, cell.y, cell.z, cell.block, cell.meta, 2)) return;
            if(ForgeEventFactory.onPlayerBlockPlace(actor, snapshot, ForgeDirection.UP).isCanceled()) {
                snapshot.restore(true, false); return;
            }
        }
        camp.state = 1; markDirty();
    }
    private static List<Cell> layout(Camp c) {
        List<Cell> cells = new ArrayList<Cell>();
        // Two torn, open canvas shelters; broken benches, cold fire ring and invading undergrowth.
        for(int side : new int[] {-1, 1}) {
            for(int dz = -4; dz <= -1; dz++) {
                cells.add(new Cell(c, side * 4, 2, dz, Blocks.wool, 12));
                if(dz != -2) cells.add(new Cell(c, side * 5, 1, dz, Blocks.wool, 12));
                if(dz != -3) cells.add(new Cell(c, side * 3, 1, dz, Blocks.wool, 12));
            }
            cells.add(new Cell(c, side * 4, 0, -4, Blocks.planks, 1));
            cells.add(new Cell(c, side * 4, 0, -1, Blocks.fence, 0));
            cells.add(new Cell(c, side * 4, 0, 4, Blocks.log, 4));
            cells.add(new Cell(c, side * 3, 0, 4, Blocks.log, 4));
            cells.add(new Cell(c, side * 5, 0, -5, Blocks.leaves, 4));
            cells.add(new Cell(c, side * 6, 1, -4, Blocks.leaves, 4));
        }
        for(int dx = -1; dx <= 1; dx++) for(int dz = 2; dz <= 4; dz++)
            if(dx != 0 || dz != 3) cells.add(new Cell(c, dx, 0, dz, Blocks.cobblestone, 0));
        cells.add(new Cell(c, 0, 0, 3, Blocks.coal_block, 0));
        cells.add(new Cell(c, -6, 1, -1, Blocks.web, 0));
        // Small, irregular tracks and felled wood suggest a large resident without expanding the clearing.
        cells.add(new Cell(c, -2, -1, 5, Blocks.gravel, 0)); cells.add(new Cell(c, 1, -1, 4, Blocks.gravel, 0));
        cells.add(new Cell(c, -3, 0, 5, Blocks.log, 8)); cells.add(new Cell(c, -3, 0, 6, Blocks.log, 8));
        return cells;
    }
    private void tick(WorldServer world) {
        for(Camp c : camps) {
            if(c.state != 1 || !GardenerNests.loaded(world, AxisAlignedBB.getBoundingBox(c.x - 10, 0, c.z - 10, c.x + 11, 256, c.z + 11))
                    || world.getClosestPlayer(c.x, c.y, c.z, 64D) == null) continue;
            if(GeminiPopulation.find(world, c.boss) != null) {
                c.state = 2; markDirty(); return;
            }
            if(!naturalBudget(c.boss)) continue;
            if(!GeminiPopulation.canAdmit(world, EntityWretch.class, null, GeminiConfig.wretchMaxLoaded)) return;
            EntityWretch boss = new EntityWretch(world);
            boss.bindCamp(c.boss, c.x, c.y, c.z);
            boss.setLocationAndAngles(c.x + 0.5D, c.y, c.z + 0.5D, world.rand.nextFloat() * 360F, 0);
            if(boss.hasSpawnClearance() && !world.isAnyLiquid(boss.boundingBox)
                    && world.getEntitiesWithinAABBExcludingEntity(boss, boss.boundingBox).isEmpty()
                    && world.spawnEntityInWorld(boss)) c.state = 2;
            else if(++c.retries >= 20) c.state = 4;
            markDirty(); return;
        }
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
    private void protect(int x, int z) {
        long chunk = column(x >> 4, z >> 4);
        BitSet columns = placedColumns.get(chunk);
        if(columns == null && placedColumns.size() < 8192) { columns = new BitSet(256); placedColumns.put(chunk, columns); }
        if(columns == null) placementOverflow = true;
        else for(int dx = -1; dx <= 1; dx++) for(int dz = -1; dz <= 1; dz++)
            if(((x + dx) >> 4) == (x >> 4) && ((z + dz) >> 4) == (z >> 4)) columns.set(((x + dx) & 15) | (((z + dz) & 15) << 4));
        markDirty();
        for(Camp c : camps) if(Math.abs(x - c.x) <= 34 && Math.abs(z - c.z) <= 34) {
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
            // FML's population callback also covers flat/custom generators and runs after native decoration.
            if(active(world) && world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                    && world.getGameRules().getGameRuleBooleanValue("mobGriefing"))
                get(world).generate((WorldServer) world, chunkX, chunkZ);
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
            if(event.phase == TickEvent.Phase.END && active(event.world) && event.world.getTotalWorldTime() % 20 == 0
                    && !event.world.playerEntities.isEmpty() && event.world.getGameRules().getGameRuleBooleanValue("doMobSpawning"))
                get(event.world).tick((WorldServer) event.world);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void join(EntityJoinWorldEvent event) {
            if(event.world.isRemote || !(event.entity instanceof EntityWretch)) return;
            EntityWretch boss = (EntityWretch) event.entity;
            if(!boss.addedToChunk && !boss.wasLoaded() && !GeminiSpawning.manualAdmission(boss) && (!owns(boss) || !active(event.world) || !get(event.world).naturalBudget(boss.getUniqueID())
                    || !GeminiPopulation.canAdmit(event.world, EntityWretch.class, boss, GeminiConfig.wretchMaxLoaded)))
                event.setCanceled(true);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void place(BlockEvent.PlaceEvent event) {
            if(event.world.isRemote || event.isCanceled()) return;
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
    private static final class Cell {
        final int x, y, z, meta; final Block block;
        Cell(Camp c, int dx, int dy, int dz, Block block, int meta) { x = c.x + dx; y = c.y + dy; z = c.z + dz; this.block = block; this.meta = meta; }
    }
    private static final class Camp {
        final int x, y, z;
        UUID boss = UUID.randomUUID();
        int state, retries; // abandoned construction, waiting, spawned, defeated, blocked spawn; never respawn spawned camps
        final Set<Long> protectedColumns = new HashSet<Long>();
        Camp(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
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
        NBTTagList list = nbt.getTagList("Camps", 10);
        for(int i = 0; i < Math.min(LIMIT, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Camp c = new Camp(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            if(c.y < 48 || c.y > 180 || Math.abs((long) c.x) > 29999900 || Math.abs((long) c.z) > 29999900) continue;
            try { c.boss = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { continue; }
            c.state = MathHelper.clamp_int(tag.getInteger("State"), 0, 4); c.retries = MathHelper.clamp_int(tag.getInteger("Retries"), 0, 20);
            for(int packed : tag.getIntArray("Protected")) {
                int dx = packed & 127, dz = packed >> 7 & 127;
                if(dx <= 76 && dz <= 76) c.protectedColumns.add(column(c.x + dx - 38, c.z + dz - 38));
            }
            camps.add(c);
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for(Camp c : camps) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", c.x); tag.setInteger("Y", c.y); tag.setInteger("Z", c.z);
            tag.setString("Boss", c.boss.toString()); tag.setInteger("State", c.state); tag.setInteger("Retries", c.retries);
            int[] columns = new int[c.protectedColumns.size()]; int i = 0;
            for(long col : c.protectedColumns) columns[i++] = ((int) (col >> 32) - c.x + 38) | (((int) col - c.z + 38) << 7);
            tag.setIntArray("Protected", columns); list.appendTag(tag);
        }
        nbt.setTag("Camps", list);
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
