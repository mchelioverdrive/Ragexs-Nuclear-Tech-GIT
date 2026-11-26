package com.hbm.world.progression;

import com.hbm.world.gen.structure.BossArenaGenerator;
import com.hbm.world.gen.structure.OceanTempleGenerator;
import com.hbm.world.progression.BossProgression.Arena;
import com.hbm.world.progression.BossProgression.Boss;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public final class BossDiscovery {
    private BossDiscovery() { }

    public static Boss next(BossProgression data) {
        for(Boss boss : Boss.values()) {
            if(boss == Boss.SANDWORM) break;
            if(data.canSummon(boss) && !data.isDefeated(boss)) return boss;
        }
        return null;
    }

    static void dropJournal(BossProgression data, Boss completed, Entity entity) {
        Boss boss = null;
        for(Boss candidate : Boss.values()) {
            if(candidate.dependsOn(completed) && data.canSummon(candidate) && !data.isDefeated(candidate)) {
                boss = candidate;
                break;
            }
        }
        if(boss == null || boss == Boss.WITHER_STORM) return;
        String guidance = guidance(entity.worldObj, boss, MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posZ));
        ItemStack journal = new ItemStack(Items.written_book);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "RTM Encounter Survey");
        tag.setString("author", "RTM Survey Office");
        NBTTagList pages = new NBTTagList();
        pages.appendTag(new NBTTagString(guidance));
        pages.appendTag(new NBTTagString("Use /bossprogression next for a fresh survey."));
        tag.setTag("pages", pages);
        journal.setTagCompound(tag);
        entity.entityDropItem(journal, 0);
    }

    public static String guidance(World source, Boss boss, int x, int z) {
        if(boss == Boss.ENDER_DRAGON) return "Ender Dragon. Use Eyes of Ender to find a stronghold and open its End portal.";
        if(boss == Boss.WITHER_STORM) return "Wither Storm. Build the Fake Command Block or euphemium summoning ritual.";
        String recorded = locateRecorded(source, boss, x, z);
        if(recorded != null) return recorded;
        World overworld = DimensionManager.getWorld(0);
        int[] origin = reference(source, boss.dimension, x, z);
        if(boss == Boss.PRISMARINE_COLOSSUS) {
            int[] candidate = overworld == null ? null : OceanTempleGenerator.surveyCandidate(overworld, origin[0], origin[1]);
            return candidate == null ? "Prismarine Colossus. Explore new deep oceans for an Ocean Temple."
                : "Ocean Temple survey site in the Overworld near X " + candidate[0] + ", Z " + candidate[2]
                    + ". Explore the seafloor and right-click the temple core.";
        }
        if(!boss.hasArena()) return boss.displayName;
        World seedWorld = overworld == null ? source : overworld;
        World target = DimensionManager.getWorld(boss.dimension);
        int[] candidate = BossArenaGenerator.surveyCandidate(seedWorld, target, boss, origin[0], origin[1]);
        if(candidate == null) return boss.displayName + ". No " + boss.encounterName + " survey site was found nearby.";
        return boss.encounterName + " survey site in " + dimensionName(boss.dimension) + " near X " + candidate[0]
            + ", Z " + candidate[1] + ". Explore new terrain around this site and look for the core at Y " + boss.floor + ".";
    }

    public static String locateRecorded(World source, Boss boss, int x, int z) {
        int[] origin = reference(source, boss.dimension, x, z);
        if(boss == Boss.PRISMARINE_COLOSSUS) {
            World overworld = DimensionManager.getWorld(0);
            int[] temple = overworld == null ? null : OceanTempleGenerator.nearestRecorded(overworld, origin[0], origin[1]);
            return temple == null ? null : location("Ocean Temple", 0, temple[0], temple[1], temple[2], temple[3] == 1, false);
        }
        if(!boss.hasArena()) return null;
        Arena arena = BossProgression.get(source).nearestArena(boss, origin[0], origin[1]);
        return arena == null ? null : location(boss.encounterName, boss.dimension, arena.x, boss.floor,
            arena.z + BossArenaGenerator.CORE_OFFSET, arena.isComplete(), arena.isActive());
    }

    private static String location(String name, int dimension, int x, int y, int z, boolean complete, boolean active) {
        return name + " core in " + dimensionName(dimension) + " at X " + x + ", Y " + y + ", Z " + z
            + (active ? " (boss active)." : complete ? "." : " (explore the area to finish generation).");
    }

    private static String dimensionName(int dimension) { return dimension == -1 ? "the Nether" : "the Overworld"; }

    private static int[] reference(World source, int dimension, int x, int z) {
        int from = source.provider.dimensionId;
        if(from == 0 && dimension == -1) return new int[] {Math.floorDiv(x, 8), Math.floorDiv(z, 8)};
        if(from == -1 && dimension == 0) return new int[] {x * 8, z * 8};
        return new int[] {x, z};
    }
}
