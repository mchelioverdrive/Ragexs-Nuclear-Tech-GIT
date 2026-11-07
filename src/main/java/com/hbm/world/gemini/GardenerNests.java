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
    private static final int WIDTH = 19, HEIGHT = 11, VOLUME = WIDTH * WIDTH * HEIGHT;
    private static final Set<String> STRUCTURES = new LinkedHashSet<String>(Arrays.asList(
            "Mineshaft", "Stronghold", "Village", "Temple", "NTMFeatures", "RTMLegacyStructures"));
    private static boolean structureLimitExceeded;
    private final List<Nest> nests = new ArrayList<Nest>();
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
                || block == Blocks.diamond_ore || block == Blocks.emerald_ore;
    }
    private static boolean safe(World world, int x, int y, int z, boolean existingNest) {
        if(y < 6 || y > 54 || !world.blockExists(x, y, z)) return false;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        return (natural(block) || existingNest && (block == ModBlocks.gardener_flesh || block == ModBlocks.mockery_growth))
                && !block.hasTileEntity(meta) && world.getTileEntity(x, y, z) == null
                && block.getBlockHardness(world, x, y, z) >= 0;
    }
    /** One checked mutation, also used by slam terrain damage. No explosion or recursive neighbor scan. */
    public static boolean change(World world, EntityGardener boss, int x, int y, int z, Block replacement) {
        if(!active(world) || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !loaded(world, AxisAlignedBB.getBoundingBox(x - 1, y, z - 1, x + 2, y + 1, z + 2))
                || !safe(world, x, y, z, true)) return false;
        Block old = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if(old == replacement || (boss != null && !old.canEntityDestroy(world, x, y, z, boss))) return false;
        AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        if(replacement != Blocks.air && !world.getEntitiesWithinAABBExcludingEntity(null, cell).isEmpty()) return false;
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        if(!world.canMineBlock(actor, x, y, z)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, old, meta, actor))) return false;
        if(!active(world) || !safe(world, x, y, z, true) || world.getBlock(x, y, z) != old
                || world.getBlockMetadata(x, y, z) != meta) return false;
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if(!world.setBlock(x, y, z, replacement, 0, 0)) return false;
        if(replacement != Blocks.air && ForgeEventFactory.onPlayerBlockPlace(actor, snapshot, ForgeDirection.UP).isCanceled()) {
            snapshot.restore(true, false); return false;
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
            if(!event.world.isRemote && event.entity instanceof EntityGardener) {
                EntityGardener boss = (EntityGardener) event.entity;
                // Natural bosses retain progression/caps; manually initialized creatures are independent.
                if(!boss.addedToChunk && !GeminiSpawning.manualAdmission(boss) && (!active(event.world) || !GeminiPopulation.canAdmit(event.world,
                        EntityGardener.class, boss, GeminiConfig.gardenerMaxLoaded))) event.setCanceled(true);
            }
        }
        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if(event.phase != TickEvent.Phase.END || !active(event.world) || event.world.playerEntities.isEmpty()
                    || !event.world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                    || !event.world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return;
            World world = event.world;
            GardenerNests data = (GardenerNests) world.mapStorage.loadData(GardenerNests.class, NAME);
            if(data == null) { data = new GardenerNests(NAME); world.mapStorage.setData(NAME, data); }
            data.tick((WorldServer) world);
        }
    }
    private void tick(WorldServer world) {
        for(Iterator<Nest> it = nests.iterator(); it.hasNext();) {
            Nest nest = it.next();
            if(nest.phase >= 3) continue;
            if(!loaded(world, nest.bounds().expand(2, 0, 2))) continue; // Unloaded jobs must not starve other loaded nests.
            if(nest.phase == 0 && nest.cursor == 0 && overlapsStructure(world, nest)) { it.remove(); markDirty(); return; }
            for(int work = 0; work < 32 && nest.phase < 2; work++) {
                int index = nest.cursor;
                int dx = index % WIDTH - 9, dz = index / WIDTH % WIDTH - 9, dy = index / (WIDTH * WIDTH) - 2;
                int x = nest.x + dx, y = nest.y + dy, z = nest.z + dz;
                if(!safe(world, x, y, z, nest.phase == 1)) {
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
                        if(overlapsStructure(world, nest) || !world.getEntitiesWithinAABBExcludingEntity(null, nest.bounds()).isEmpty()) {
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
        EntityPlayer player = (EntityPlayer) world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
        if(!player.isEntityAlive()) return;
        // Eight loaded columns and at most 32 vertical cells per column per minute-long opportunity.
        for(int attempt = 0; attempt < 8; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2D;
            int radius = 28 + world.rand.nextInt(29);
            int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
            int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
            if(!loaded(world, AxisAlignedBB.getBoundingBox(x - 11, 0, z - 11, x + 12, 64, z + 12)) || tooClose(x, z)) continue;
            for(int y = 42; y >= 12; y--) {
                if(world.getBlock(x, y, z) != Blocks.air || !natural(world.getBlock(x, y - 1, z))
                        || !world.getBlock(x, y - 1, z).getMaterial().blocksMovement()
                        || world.getBlock(x, y + 1, z) != Blocks.air || world.canBlockSeeTheSky(x, y + 6, z)) continue;
                Nest nest = new Nest(x, y, z);
                if(overlapsStructure(world, nest)) break;
                nests.add(nest); markDirty(); return;
            }
        }
    }
    private boolean tooClose(int x, int z) {
        for(Nest nest : nests) {
            double dx = (double) x - nest.x, dz = (double) z - nest.z;
            if(dx * dx + dz * dz < 256D * 256D) return true;
        }
        return false;
    }
    private static Block desired(int dx, int dy, int dz) {
        int radius = dx * dx + dz * dz;
        if(dy == -1 && radius <= 64) return ModBlocks.gardener_flesh;
        // Six radial runs of infectious growth lead out across the organic floor.
        double angle = Math.atan2(dz, dx);
        if(dy == 0 && radius >= 16 && radius <= 60
                && (Math.abs(Math.sin(angle * 3D)) < 0.32D || (dx * 3 + dz * 5) % 13 == 0)) return ModBlocks.mockery_growth;
        if(dy >= 0 && dy <= 6 && radius <= 64 - dy * dy) return Blocks.air;
        if(dy >= 1 && dy <= 7 && radius > 64 - dy * dy && radius <= 76 - dy * dy
                && ((dx + dz) & 1) == 0) return ModBlocks.gardener_flesh;
        return null;
    }
    /** Registered vanilla and NTM starts protect their full saved bounds, even when not presently populated. */
    private static boolean overlapsStructure(World world, Nest nest) {
        if(structureLimitExceeded) return true;
        StructureBoundingBox area = new StructureBoundingBox(nest.x - 11, nest.y - 3, nest.z - 11, nest.x + 11, nest.y + 10, nest.z + 11);
        int examined = 0;
        for(String name : STRUCTURES) {
            MapGenStructureData structures = (MapGenStructureData) world.perWorldStorage.loadData(MapGenStructureData.class, name);
            if(structures == null) continue;
            NBTTagCompound features = structures.func_143041_a();
            for(Object key : features.func_150296_c()) {
                if(++examined > 4096) return true; // Fail closed in huge saves instead of an unbounded structure search.
                NBTTagCompound feature = features.getCompoundTag((String) key);
                int[] box = feature.getIntArray("BB");
                if(box.length == 6 && new StructureBoundingBox(box).intersectsWith(area)) return true;
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
        if(world.getCollidingBoundingBoxes(boss, boss.boundingBox).isEmpty() && !world.isAnyLiquid(boss.boundingBox)
                && world.spawnEntityInWorld(boss)) nest.phase = 3;
        else if(++nest.retries >= 12) nest.phase = 4;
    }
    private static final class Nest {
        final int x, y, z;
        UUID boss = UUID.randomUUID();
        int phase, cursor, retries; // validate, build, spawn, complete, abandoned
        Nest(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        AxisAlignedBB bounds() { return AxisAlignedBB.getBoundingBox(x - 9, y - 2, z - 9, x + 10, y + 9, z + 10); }
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        nests.clear(); searchTicks = MathHelper.clamp_int(nbt.getInteger("SearchTicks"), 1, 1200);
        NBTTagList list = nbt.getTagList("Nests", 10);
        for(int i = 0; i < Math.min(64, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Nest nest = new Nest(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
            if(nest.y < 12 || nest.y > 42 || Math.abs((long) nest.x) > 29999900 || Math.abs((long) nest.z) > 29999900) continue;
            nest.phase = MathHelper.clamp_int(tag.getInteger("Phase"), 0, 4);
            nest.cursor = MathHelper.clamp_int(tag.getInteger("Cursor"), 0, VOLUME - 1);
            nest.retries = MathHelper.clamp_int(tag.getInteger("Retries"), 0, 12);
            try { nest.boss = UUID.fromString(tag.getString("Boss")); } catch(IllegalArgumentException ignored) { }
            nests.add(nest);
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("SearchTicks", searchTicks);
        NBTTagList list = new NBTTagList();
        for(Nest nest : nests) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("X", nest.x); tag.setInteger("Y", nest.y); tag.setInteger("Z", nest.z);
            tag.setInteger("Phase", nest.phase); tag.setInteger("Cursor", nest.cursor); tag.setInteger("Retries", nest.retries);
            tag.setString("Boss", nest.boss.toString()); list.appendTag(tag);
        }
        nbt.setTag("Nests", list);
    }
}
