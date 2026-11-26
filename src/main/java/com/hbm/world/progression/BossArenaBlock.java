package com.hbm.world.progression;

import com.hbm.config.BossProgressionConfig;
import com.hbm.entity.mob.giantenderman.EntityGiantEnderman;
import com.hbm.entity.mob.giantghast.EntityGiantGhast;
import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import com.hbm.world.gen.structure.BossArenaGenerator;
import com.hbm.world.progression.BossProgression.Arena;
import com.hbm.world.progression.BossProgression.Boss;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/** The saved arena owns activation; the core itself is an ordinary, unticked block. */
public final class BossArenaBlock extends Block {
    public static Block core;

    private BossArenaBlock() {
        super(Material.rock);
        setBlockName("boss_arena_core").setBlockTextureName("hbm:brick_jungle_circle")
            .setBlockUnbreakable().setResistance(6000000F).setLightLevel(0.8F);
    }

    public static void register() {
        core = new BossArenaBlock();
        GameRegistry.registerBlock(core, "boss_arena_core");
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if(world.isRemote || player instanceof FakePlayer || !player.isEntityAlive()) return true;
        BossProgression data = BossProgression.get(world);
        Arena arena = findArena(data, world.provider.dimensionId, x, y, z - BossArenaGenerator.CORE_OFFSET);
        if(arena == null) return true;

        Boss boss = arena.boss;
        BossProgressionConfig.Values settings = BossProgressionConfig.settings;
        Boss missing = data.missingPrerequisite(boss);
        if(!settings.enabled || !boss.isEnabled()) return tell(player, "This encounter is disabled.");
        if(missing != null) return tell(player, "Defeat " + missing.displayName + " before challenging " + boss.displayName + ".");
        if(arena.isActive()) return tell(player, boss.displayName + " is already active at this arena.");
        if(!settings.repeatEncounters && data.isDefeated(boss)) return tell(player, boss.displayName + " has already been defeated in this world.");
        if(data.activeCount(boss) >= settings.maximumActive) return tell(player, "The active encounter limit for " + boss.displayName + " has been reached.");
        if(world.difficultySetting == EnumDifficulty.PEACEFUL) return tell(player, "This encounter requires a hostile difficulty.");
        if(!arena.isComplete() || !BossArenaGenerator.isLoaded(world, arena)) {
            return tell(player, "Explore the whole arena and keep its surrounding chunks loaded before activating the core.");
        }
        if(!spawn(world, data, arena)) return tell(player, "The boss could not spawn. Clear the center of the arena and try again.");
        return tell(player, boss.displayName + " has awakened.");
    }

    private static Arena findArena(BossProgression data, int dimension, int x, int y, int z) {
        for(Boss boss : Boss.values()) {
            if(boss.hasArena() && boss.dimension == dimension && boss.floor == y) {
                Arena arena = data.arenaAt(boss, x, z);
                if(arena != null) return arena;
            }
        }
        return null;
    }

    private static boolean spawn(World world, BossProgression data, Arena arena) {
        double x = arena.x + 0.5, y = arena.boss.floor + 1, z = arena.z + 0.5;
        EntityLiving entity;
        switch(arena.boss) {
            case GIANT_GHAST:
                EntityGiantGhast ghast = new EntityGiantGhast(world);
                ghast.setLocationAndAngles(x, y + 8, z, 0, 0);
                if(!ghast.hasSpawnClearance()) return false;
                entity = ghast;
                break;
            case GIANT_MAGMA_GOLEM:
                EntityGiantMagmaGolem golem = new EntityGiantMagmaGolem(world);
                golem.setLocationAndAngles(x, y, z, 0, 0);
                if(!golem.hasSpawnClearance() || !world.checkNoEntityCollision(golem.boundingBox, golem)) return false;
                entity = golem;
                break;
            case GIANT_ENDERMAN:
                EntityGiantEnderman enderman = new EntityGiantEnderman(world);
                if(!enderman.safePosition(x, y, z)) return false;
                enderman.setLocationAndAngles(x, y, z, 0, 0);
                entity = enderman;
                break;
            default: return false;
        }
        data.start(arena, entity);
        boolean spawned = false;
        try {
            spawned = world.spawnEntityInWorld(entity);
        } finally {
            if(!spawned) data.release(entity.getUniqueID());
        }
        if(spawned && entity instanceof EntityGiantEnderman) ((EntityGiantEnderman)entity).activateEncounter();
        return spawned;
    }

    private static boolean tell(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText(message));
        return true;
    }
}
