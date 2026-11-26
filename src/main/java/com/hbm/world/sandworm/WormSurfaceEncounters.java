package com.hbm.world.sandworm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.hbm.config.SandwormConfig;
import com.hbm.config.SpaceConfig;
import com.hbm.entity.missile.EntityRideableRocket;
import com.hbm.entity.mob.sandworm.EntitySandworm;
import com.hbm.world.gemini.GeminiHostileSpawning;
import com.hbm.world.progression.BossProgression;
import com.hbm.world.squirrelstapler.StaplerWorldType;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;

/** Server-only surface activity. The encounter ledger owns saved reservations and cooldowns. */
public final class WormSurfaceEncounters {
    private static final int ACTIVITY_LIMIT = 2400;
    private final Map<UUID, Activity> players = new HashMap<UUID, Activity>();

    private static boolean eligible(World world) {
        int dimension = world.provider.dimensionId;
        return dimension == SpaceConfig.siroccoDimension || (!SandwormConfig.surface.extrasolarOnly
                && dimension == 0 && !StaplerWorldType.active(world));
    }

    void reset(EntityPlayerMP player, long now) {
        forget(player);
        if(eligible(player.worldObj)) players.put(player.getUniqueID(), new Activity(player, now));
    }

    void forget(EntityPlayerMP player) {
        players.remove(player.getUniqueID());
    }

    void sample(EntityPlayerMP player, long now) {
        if(!eligible(player.worldObj)) return;
        UUID id = player.getUniqueID();
        Activity activity = players.get(id);
        if(activity == null || activity.dimension != player.dimension) {
            players.put(id, new Activity(player, now));
            return;
        }
        double distance = Math.hypot(player.posX - activity.x, player.posZ - activity.z);
        activity.x = player.posX;
        activity.z = player.posZ;
        activity.moving = false;
        activity.onSand = false;
        if(player.ridingEntity instanceof EntityRideableRocket) {
            activity.arrived = now;
            activity.ticks = 0;
            return;
        }
        int x = MathHelper.floor_double(player.posX), z = MathHelper.floor_double(player.posZ);
        if(isTarget(player) && player.onGround && player.ridingEntity == null
                && player.worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
            int surface = sandySurface(player.worldObj, x, z);
            if(surface >= 0 && Math.abs(player.posY - surface) <= 1.5) {
                activity.onSand = true;
                activity.moving = distance >= (player.isSneaking() ? 4 : 1.5) && distance < 32;
                activity.ticks = Math.min(ACTIVITY_LIMIT, activity.ticks + (SandwormConfig.surface.movementDetection && activity.moving ? 20 : 4));
                return;
            }
        }
        activity.ticks = Math.max(0, activity.ticks - 40);
    }

    private static boolean isTarget(EntityPlayerMP player) {
        return player.isEntityAlive() && !player.capabilities.isCreativeMode && !player.capabilities.isFlying;
    }

    public boolean mayPursue(EntityPlayerMP player, long now) {
        Activity activity = players.get(player.getUniqueID());
        return activity != null && activity.dimension == player.dimension && isTarget(player) && player.ridingEntity == null
                && now - activity.arrived >= SandwormConfig.surface.arrivalGrace;
    }

    public boolean isOnSand(EntityPlayerMP player) {
        Activity activity = players.get(player.getUniqueID());
        return activity != null && activity.onSand;
    }

    public boolean vibrates(EntityPlayerMP player) {
        Activity activity = players.get(player.getUniqueID());
        return activity != null && activity.moving;
    }

    private static int sandySurface(World world, int x, int z) {
        int top = world.getHeightValue(x, z);
        if(top < 8 || world.getBlock(x, top - 1, z).getMaterial() != Material.sand) return -1;
        if(world.provider.dimensionId == 0) {
            BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
            if(biome != BiomeGenBase.desert && biome != BiomeGenBase.desertHills) return -1;
        }
        return top;
    }

    void trySpawn(WorldServer world, WormEncounters data, long now) {
        if(world == null || world.playerEntities.isEmpty() || !eligible(world)
                || world.difficultySetting == EnumDifficulty.PEACEFUL || !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                || !BossProgression.canVisitSirocco(world) || !data.hasNaturalRoom(world.provider.dimensionId, now)) return;
        EntityPlayerMP candidate = null;
        int activityTicks = 0;
        for(Object entity : world.playerEntities) {
            if(!(entity instanceof EntityPlayerMP)) continue;
            EntityPlayerMP player = (EntityPlayerMP)entity;
            if(!mayPursue(player, now) || !isOnSand(player)) continue;
            Activity activity = players.get(player.getUniqueID());
            if(activity.ticks > activityTicks) {
                candidate = player;
                activityTicks = activity.ticks;
            }
        }
        if(candidate == null || activityTicks < 320 || world.rand.nextInt(ACTIVITY_LIMIT * 8) >= activityTicks) return;
        int playerX = MathHelper.floor_double(candidate.posX), playerZ = MathHelper.floor_double(candidate.posZ);
        if(!world.getChunkProvider().chunkExists(playerX >> 4, playerZ >> 4)) return;
        int surface = sandySurface(world, playerX, playerZ);
        if(surface < 0 || Math.abs(candidate.posY - surface) > 2 || candidate.ridingEntity != null) return;
        for(int attempt = 0; attempt < 4; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            int distance = Math.min(72, Math.max(18, (int)SandwormConfig.targetRange - 8));
            int x = MathHelper.floor_double(candidate.posX + Math.sin(angle) * distance);
            int z = MathHelper.floor_double(candidate.posZ + Math.cos(angle) * distance);
            if(!world.checkChunksExist(x - 12, 0, z - 12, x + 12, 255, z + 12)) continue;
            int ground = sandySurface(world, x, z);
            if(ground < 0 || sandySurface(world, x - 8, z - 8) < 0 || sandySurface(world, x + 8, z + 8) < 0) continue;
            EntitySandworm worm = new EntitySandworm(world);
            if(!GeminiHostileSpawning.allowAutomaticHostile(worm)) return;
            float yaw = (float)Math.toDegrees(Math.atan2(candidate.posZ - z, candidate.posX - x)) - 90;
            worm.setLocationAndAngles(x + 0.5, Math.max(8, ground - 25), z + 0.5, yaw, 0);
            worm.naturalSpawn = true;
            if(world.spawnEntityInWorld(worm)) {
                data.recordNaturalSpawn(world.provider.dimensionId, now);
                players.get(candidate.getUniqueID()).ticks = 0;
                candidate.addChatMessage(new ChatComponentTranslation("sandworm.tremors"));
            } else {
                data.cancel(worm.getUniqueID());
                WormEncounter record = data.find(worm.getUniqueID());
                if(record != null) { record.exteriorRetired = true; data.markDirty(); }
            }
            return;
        }
    }

    private static final class Activity {
        final int dimension;
        long arrived;
        double x, z;
        int ticks;
        boolean moving, onSand;

        Activity(EntityPlayerMP player, long now) {
            dimension = player.dimension;
            arrived = now;
            x = player.posX;
            z = player.posZ;
        }
    }
}
