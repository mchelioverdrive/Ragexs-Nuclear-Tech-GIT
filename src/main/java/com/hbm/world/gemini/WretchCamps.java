package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
    public WretchCamps(String name) { super(name); }

    public static boolean active(World world) {
        return GeminiConfig.wretchEnabled && GeminiInvasionManager.canActivityOccur(world, 4)
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }
    private static WretchCamps get(World world) {
        WretchCamps data = (WretchCamps) world.mapStorage.loadData(WretchCamps.class, NAME);
        if(data == null) { data = new WretchCamps(NAME); world.mapStorage.setData(NAME, data); }
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
        return cells;
    }
    private void tick(WorldServer world) {
        for(Camp c : camps) {
            if(c.state != 1 || !GardenerNests.loaded(world, AxisAlignedBB.getBoundingBox(c.x - 10, 0, c.z - 10, c.x + 11, 256, c.z + 11))
                    || world.getClosestPlayer(c.x, c.y, c.z, 64D) == null) continue;
            if(GeminiPopulation.find(world, c.boss) != null) {
                c.state = 2; markDirty(); return;
            }
            if(!GeminiPopulation.canAdmit(world, EntityWretch.class, null, GeminiConfig.wretchMaxLoaded)) return;
            EntityWretch boss = new EntityWretch(world);
            boss.bindCamp(c.boss, c.x, c.y, c.z);
            boss.setLocationAndAngles(c.x + 0.5D, c.y, c.z + 0.5D, world.rand.nextFloat() * 360F, 0);
            if(world.getCollidingBoundingBoxes(boss, boss.boundingBox).isEmpty() && !world.isAnyLiquid(boss.boundingBox)
                    && world.getEntitiesWithinAABBExcludingEntity(boss, boss.boundingBox).isEmpty()
                    && world.spawnEntityInWorld(boss)) c.state = 2;
            else if(++c.retries >= 20) c.state = 4;
            markDirty(); return;
        }
    }

    /** Conservative local removal through native block/protection callbacks; never touches constructed blocks. */
    public static boolean breakNatural(EntityWretch boss, int x, int y, int z) {
        World world = boss.worldObj;
        if(!active(world) || !GeminiConfig.wretchTerrainDestruction || !owns(boss) || !boss.inTerritory(x, z, 30)
                || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !GardenerNests.loaded(world, AxisAlignedBB.getBoundingBox(x - 2, y, z - 2, x + 3, y + 1, z + 3))
                || y < boss.campY() - 3 || y > boss.campY() + 8 || !safe(world, x, y, z)) return false;
        Block old = world.getBlock(x, y, z);
        if(old == Blocks.air || !old.canEntityDestroy(world, x, y, z, boss)) return false;
        Camp camp = get(world).find(boss.getUniqueID());
        if(camp == null || camp.protectedColumns.contains(column(x, z))) return false;
        // A nearby wall, machine, camp prop or other construction protects its surrounding natural blocks too.
        for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++)
            for(int dy = 0; dy <= 2; dy++) if(!safe(world, x + dx, y + dy, z + dz)) return false;
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        int meta = world.getBlockMetadata(x, y, z);
        if(!world.canMineBlock(actor, x, y, z)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, old, meta, actor))) return false;
        return active(world) && GeminiConfig.wretchTerrainDestruction && safe(world, x, y, z)
                && world.getBlock(x, y, z) == old && world.getBlockMetadata(x, y, z) == meta
                && world.setBlock(x, y, z, Blocks.air, 0, 3);
    }
    private static long column(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    private void protect(int x, int z) {
        for(Camp c : camps) if(Math.abs(x - c.x) <= 34 && Math.abs(z - c.z) <= 34) {
            for(int dx = -2; dx <= 2; dx++) for(int dz = -2; dz <= 2; dz++)
                c.protectedColumns.add(column(x + dx, z + dz));
            markDirty();
        }
    }
    public static final class Generator implements IWorldGenerator {
        @Override public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator, IChunkProvider provider) {
            // FML's population callback also covers flat/custom generators and runs after native decoration.
            if(active(world) && world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                    && world.getGameRules().getGameRuleBooleanValue("mobGriefing"))
                get(world).generate((WorldServer) world, chunkX, chunkZ);
        }
    }
    public static final class Events {
        @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
            if(event.phase == TickEvent.Phase.END && active(event.world) && event.world.getTotalWorldTime() % 20 == 0
                    && !event.world.playerEntities.isEmpty() && event.world.getGameRules().getGameRuleBooleanValue("doMobSpawning"))
                get(event.world).tick((WorldServer) event.world);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void join(EntityJoinWorldEvent event) {
            if(event.world.isRemote || !(event.entity instanceof EntityWretch)) return;
            EntityWretch boss = (EntityWretch) event.entity;
            if(!boss.addedToChunk && ((!boss.isEggSpawned() && !owns(boss)) || !active(event.world)
                    || !GeminiPopulation.canAdmit(event.world, EntityWretch.class, boss, GeminiConfig.wretchMaxLoaded)))
                event.setCanceled(true);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void place(BlockEvent.PlaceEvent event) {
            if(event.world.isRemote || event.world.provider.dimensionId != 0 || event.isCanceled()) return;
            WretchCamps data = get(event.world);
            if(event instanceof BlockEvent.MultiPlaceEvent) {
                for(BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) data.protect(snapshot.x, snapshot.z);
            } else data.protect(event.x, event.z);
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
        camps.clear();
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
    }
}
