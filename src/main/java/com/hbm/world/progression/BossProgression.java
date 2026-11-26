package com.hbm.world.progression;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.BossProgressionConfig;
import com.hbm.config.ColossusConfig;
import com.hbm.config.SandwormConfig;
import com.hbm.config.WitherStormConfig;
import com.hbm.items.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Server-side boss milestones shared by all players and dimensions of a save. */
public final class BossProgression extends WorldSavedData {
    private static final String NAME = "rntBossProgression";
    private static final String ARENA_ORIGIN = "RTMBossArena";
    private static final String COMBAT_DEATH = "RTMBossCombatDeath";

    public enum Boss {
        ENDER_DRAGON("EnderDragon", "Ender Dragon", null),
        WITHER_STORM("WitherStorm", "Wither Storm", ENDER_DRAGON),
        PRISMARINE_COLOSSUS("PrismarineColossus", "Prismarine Colossus", WITHER_STORM),
        GIANT_GHAST("GiantGhast", "Giant Ghast", PRISMARINE_COLOSSUS, -1, 32, "Nether Bastion"),
        GIANT_MAGMA_GOLEM("GiantMagmaGolem", "Giant Magma Golem", GIANT_GHAST, 0, 12, "Basalt Crucible"),
        GIANT_ENDERMAN("GiantEnderman", "Giant Enderman", GIANT_MAGMA_GOLEM, 0, 88, "Hollow Spires"),
        SANDWORM("Sandworm", "Sandworm", null);

        private final String saveKey;
        public final String displayName, encounterName;
        public final int dimension, floor;
        private final Boss prerequisite;

        Boss(String saveKey, String displayName, Boss prerequisite) {
            this(saveKey, displayName, prerequisite, 0, 0, null);
        }

        Boss(String saveKey, String displayName, Boss prerequisite, int dimension, int floor, String encounterName) {
            this.saveKey = saveKey;
            this.displayName = displayName;
            this.prerequisite = prerequisite;
            this.dimension = dimension;
            this.floor = floor;
            this.encounterName = encounterName;
        }

        public boolean hasArena() { return encounterName != null; }

        public Boss prerequisite() {
            Boss next = prerequisite;
            while(next != null && !next.isEnabled()) next = next.prerequisite;
            return next;
        }

        public boolean isEnabled() {
            BossProgressionConfig.Values settings = BossProgressionConfig.settings;
            switch(this) {
                case ENDER_DRAGON: return settings.enderDragon;
                case WITHER_STORM: return settings.witherStorm && WitherStormConfig.survivalSummon;
                case PRISMARINE_COLOSSUS: return settings.prismarineColossus && ColossusConfig.enabled;
                case GIANT_GHAST: return settings.giantGhast;
                case GIANT_MAGMA_GOLEM: return settings.giantMagmaGolem;
                case GIANT_ENDERMAN: return settings.giantEnderman;
                default: return true;
            }
        }

        public boolean dependsOn(Boss earlier) {
            for(Boss next = prerequisite; next != null; next = next.prerequisite) {
                if(next == earlier) return true;
            }
            return false;
        }
    }

    public static final class Arena {
        public final Boss boss;
        public final int x, z;
        private int slices;
        private UUID active;

        public Arena(Boss boss, int x, int z) {
            this.boss = boss;
            this.x = x;
            this.z = z;
        }

        public boolean isComplete() { return slices == 0xFFFF; }
        public boolean isActive() { return active != null; }
        public boolean hasSlice(int bit) { return (slices & bit) != 0; }
        public boolean contains(double x, double z, int radius) {
            return Math.abs(x - (this.x + 0.5)) <= radius && Math.abs(z - (this.z + 0.5)) <= radius;
        }
    }

    private final EnumSet<Boss> defeated = EnumSet.noneOf(Boss.class);
    private final EnumSet<Boss> unlocked = EnumSet.noneOf(Boss.class);
    private final EnumSet<Boss> rewarded = EnumSet.noneOf(Boss.class);
    private final Map<Boss, Map<Long, Arena>> arenas = new EnumMap<>(Boss.class);
    private final Map<UUID, Arena> activeArenas = new HashMap<>();
    private final Map<Boss, Integer> activeCounts = new EnumMap<>(Boss.class);
    private boolean witherStormBypass;
    private boolean bypass;
    private boolean legacyEndChecked;
    private boolean alienSteelAcquired;

    public BossProgression(String name) {
        super(name);
    }

    public static BossProgression get(World world) {
        World overworld = DimensionManager.getWorld(0);
        if(overworld == null) overworld = world;
        BossProgression data = (BossProgression)overworld.mapStorage.loadData(BossProgression.class, NAME);
        if(data == null) {
            data = new BossProgression(NAME);
            overworld.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public Boss missingPrerequisite(Boss boss) {
        BossProgressionConfig.Values config = BossProgressionConfig.settings;
        if(!config.enabled || !config.requirePrecedingBoss || bypass || unlocked.contains(boss)
            || boss == Boss.WITHER_STORM && witherStormBypass
            || boss == Boss.PRISMARINE_COLOSSUS && !ColossusConfig.requireWitherStormDefeat) return null;
        Boss prerequisite = boss.prerequisite();
        return prerequisite != null && !isDefeated(prerequisite) ? prerequisite : null;
    }

    public boolean canSummon(Boss boss) {
        return boss.isEnabled() && missingPrerequisite(boss) == null;
    }

    public boolean isDefeated(Boss boss) {
        return defeated.contains(boss);
    }

    public void recordDefeat(Boss boss) {
        if(defeated.add(boss)) markDirty();
    }

    public void recordCombatDefeat(Boss boss, Entity entity) {
        if(entity.worldObj.isRemote || entity.isDead) return;
        if(boss.hasArena()) {
            if(hasArenaOrigin(entity)) {
                Arena arena = activeArenas.get(entity.getUniqueID());
                if(arena == null || arena.boss != boss) return;
                release(entity.getUniqueID());
            } else if(!canSummon(boss)) return;
        }
        if(!defeated.add(boss)) return;
        markDirty();
        if(!BossProgressionConfig.settings.enabled || !entity.worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")
            || !rewarded.add(boss)) return;
        switch(boss) {
            case GIANT_GHAST: entity.entityDropItem(new ItemStack(Items.blaze_rod, 4), 0); break;
            case GIANT_MAGMA_GOLEM: entity.entityDropItem(new ItemStack(ModItems.crystal_basaltic, 8), 0); break;
            case GIANT_ENDERMAN:
                entity.entityDropItem(new ItemStack(ModItems.ingot_desh, 4), 0);
                entity.entityDropItem(new ItemStack(Items.ender_pearl, 8), 0);
                break;
            default: break;
        }
        if(BossProgressionConfig.settings.locationRewards) BossDiscovery.dropJournal(this, boss, entity);
    }

    public static void acceptCombatDeath(Entity entity) {
        entity.getEntityData().setBoolean(COMBAT_DEATH, true);
    }

    public static void finishCombatDeath(Boss boss, Entity entity) {
        if(!entity.worldObj.isRemote && entity.getEntityData().getBoolean(COMBAT_DEATH)) {
            get(entity.worldObj).recordCombatDefeat(boss, entity);
        }
    }

    public boolean isBypassEnabled() { return bypass; }

    public void setBypass(boolean enabled) {
        if(bypass == enabled) return;
        bypass = enabled;
        markDirty();
    }

    public boolean isUnlocked(Boss boss) { return unlocked.contains(boss); }

    public void unlock(Boss boss) {
        if(unlocked.add(boss)) markDirty();
    }

    public void reset(Boss boss) {
        for(Boss candidate : Boss.values()) {
            if(candidate != boss && !candidate.dependsOn(boss)) continue;
            defeated.remove(candidate);
            unlocked.remove(candidate);
            Map<Long, Arena> sites = arenas.get(candidate);
            if(sites != null) for(Arena arena : sites.values()) release(arena.active);
        }
        if(boss == Boss.ENDER_DRAGON) legacyEndChecked = true;
        markDirty();
    }

    public static boolean allowsAmbientSpawns(Boss boss) {
        return !BossProgressionConfig.settings.enabled && boss.isEnabled();
    }

    public Arena arenaAt(Boss boss, int x, int z) {
        Map<Long, Arena> sites = arenas.get(boss);
        return sites == null ? null : sites.get(siteKey(x, z));
    }

    public boolean hasSeparation(Boss boss, int x, int z) {
        long minimum = BossProgressionConfig.settings.minimumSeparation;
        for(Map.Entry<Boss, Map<Long, Arena>> entry : arenas.entrySet()) {
            if(entry.getKey().dimension != boss.dimension) continue;
            for(Arena arena : entry.getValue().values()) {
                long dx = (long)x - arena.x, dz = (long)z - arena.z;
                if(dx * dx + dz * dz < minimum * minimum) return false;
            }
        }
        return true;
    }

    public Arena reserve(Boss boss, int x, int z) {
        Arena arena = new Arena(boss, x, z);
        arenas.computeIfAbsent(boss, key -> new HashMap<>()).put(siteKey(x, z), arena);
        markDirty();
        return arena;
    }

    public void recordSlice(Arena arena, int bit) {
        arena.slices |= bit;
        markDirty();
    }

    public Arena nearestArena(Boss boss, double x, double z) {
        Map<Long, Arena> sites = arenas.get(boss);
        if(sites == null) return null;
        Arena nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for(Arena arena : sites.values()) {
            double dx = arena.x - x, dz = arena.z - z;
            double distance = dx * dx + dz * dz;
            if(nearest == null || arena.isComplete() && !nearest.isComplete()
                || arena.isComplete() == nearest.isComplete() && distance < best) {
                nearest = arena;
                best = distance;
            }
        }
        return nearest;
    }

    private static long siteKey(int x, int z) {
        return (x & 0xffffffffL) | ((long)z << 32);
    }

    public int activeCount(Boss boss) { return activeCounts.getOrDefault(boss, 0); }

    public void start(Arena arena, Entity entity) {
        UUID id = entity.getUniqueID();
        arena.active = id;
        activeArenas.put(id, arena);
        activeCounts.put(arena.boss, activeCount(arena.boss) + 1);
        entity.getEntityData().setBoolean(ARENA_ORIGIN, true);
        markDirty();
    }

    public void release(UUID id) {
        Arena arena = activeArenas.remove(id);
        if(arena == null) return;
        arena.active = null;
        activeCounts.put(arena.boss, activeCount(arena.boss) - 1);
        markDirty();
    }

    public static boolean hasArenaOrigin(Entity entity) {
        return entity.getEntityData().getBoolean(ARENA_ORIGIN);
    }

    public static Arena activeArena(Entity entity) {
        if(!hasArenaOrigin(entity)) return null;
        Arena arena = get(entity.worldObj).activeArenas.get(entity.getUniqueID());
        return arena != null && arena.boss.dimension == entity.worldObj.provider.dimensionId ? arena : null;
    }

    public static void releaseArena(Entity entity) {
        if(!entity.worldObj.isRemote && hasArenaOrigin(entity)) get(entity.worldObj).release(entity.getUniqueID());
    }

    public boolean hasAlienSteel() {
        return alienSteelAcquired;
    }

    public static boolean canVisitSirocco(World world) {
        return !SandwormConfig.surface.requireAlienSteel || get(world).hasAlienSteel();
    }

    public void recordAlienSteel() {
        if(alienSteelAcquired) return;
        alienSteelAcquired = true;
        markDirty();
    }

    public boolean isWitherStormBypassEnabled() {
        return witherStormBypass;
    }

    public void setWitherStormBypass(boolean enabled) {
        if(witherStormBypass == enabled) return;
        witherStormBypass = enabled;
        markDirty();
    }

    public static void importLegacyEnd(World overworld) {
        BossProgression data = get(overworld);
        if(data.legacyEndChecked || data.defeated.contains(Boss.ENDER_DRAGON)) return;
        File end = new File(overworld.getSaveHandler().getWorldDirectory(), "DIM1");
        EnderDragonHistory.Result result = EnderDragonHistory.findExitPortal(end);
        if(result == EnderDragonHistory.Result.FOUND) data.recordDefeat(Boss.ENDER_DRAGON);
        if(result != EnderDragonHistory.Result.INCOMPLETE) {
            data.legacyEndChecked = true;
            data.markDirty();
        }
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        readFlags(tag.getCompoundTag("Defeated"), defeated);
        readFlags(tag.getCompoundTag("Unlocked"), unlocked);
        readFlags(tag.getCompoundTag("Rewarded"), rewarded);
        witherStormBypass = tag.getBoolean("WitherStormBypass");
        bypass = tag.getBoolean("Bypass");
        legacyEndChecked = tag.getBoolean("LegacyEndDimensionChecked");
        alienSteelAcquired = tag.getBoolean("AlienSteelAcquired");
        arenas.clear();
        activeArenas.clear();
        activeCounts.clear();
        NBTTagCompound saved = tag.getCompoundTag("Arenas");
        for(Boss boss : Boss.values()) {
            if(!boss.hasArena()) continue;
            NBTTagList sites = saved.getTagList(boss.saveKey, 10);
            for(int i = 0; i < sites.tagCount(); i++) {
                NBTTagCompound record = sites.getCompoundTagAt(i);
                Arena arena = new Arena(boss, record.getInteger("X"), record.getInteger("Z"));
                arenas.computeIfAbsent(boss, key -> new HashMap<>()).put(siteKey(arena.x, arena.z), arena);
                arena.slices = record.getInteger("Slices") & 0xFFFF;
                if(arena.isComplete() && record.hasKey("ActiveMost", 4) && record.hasKey("ActiveLeast", 4)) {
                    UUID id = new UUID(record.getLong("ActiveMost"), record.getLong("ActiveLeast"));
                    if(!activeArenas.containsKey(id)) {
                        arena.active = id;
                        activeArenas.put(id, arena);
                        activeCounts.put(boss, activeCount(boss) + 1);
                    }
                }
            }
        }
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("Defeated", writeFlags(defeated));
        tag.setTag("Unlocked", writeFlags(unlocked));
        tag.setTag("Rewarded", writeFlags(rewarded));
        tag.setBoolean("WitherStormBypass", witherStormBypass);
        tag.setBoolean("Bypass", bypass);
        tag.setBoolean("LegacyEndDimensionChecked", legacyEndChecked);
        tag.setBoolean("AlienSteelAcquired", alienSteelAcquired);
        NBTTagCompound saved = new NBTTagCompound();
        for(Map.Entry<Boss, Map<Long, Arena>> entry : arenas.entrySet()) {
            NBTTagList sites = new NBTTagList();
            for(Arena arena : entry.getValue().values()) {
                NBTTagCompound record = new NBTTagCompound();
                record.setInteger("X", arena.x);
                record.setInteger("Z", arena.z);
                record.setInteger("Slices", arena.slices);
                if(arena.active != null) {
                    record.setLong("ActiveMost", arena.active.getMostSignificantBits());
                    record.setLong("ActiveLeast", arena.active.getLeastSignificantBits());
                }
                sites.appendTag(record);
            }
            saved.setTag(entry.getKey().saveKey, sites);
        }
        tag.setTag("Arenas", saved);
    }

    private static void readFlags(NBTTagCompound tag, EnumSet<Boss> flags) {
        flags.clear();
        for(Boss boss : Boss.values()) if(tag.getBoolean(boss.saveKey)) flags.add(boss);
    }

    private static NBTTagCompound writeFlags(EnumSet<Boss> flags) {
        NBTTagCompound tag = new NBTTagCompound();
        for(Boss boss : flags) tag.setBoolean(boss.saveKey, true);
        return tag;
    }

    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void dragonDied(LivingDeathEvent event) {
            World world = event.entityLiving.worldObj;
            if(!world.isRemote && world.provider.dimensionId == 1 && event.entityLiving instanceof EntityDragon
                && event.entityLiving.getHealth() <= 0F && event.source != net.minecraft.util.DamageSource.outOfWorld) {
                get(world).recordCombatDefeat(Boss.ENDER_DRAGON, event.entityLiving);
            }
        }
    }
}
