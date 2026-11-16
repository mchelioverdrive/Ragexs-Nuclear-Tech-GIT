package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.List;

import com.hbm.config.GeminiConfig;
import com.hbm.main.MainRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/** Saved active-time countdowns. Called once per second, exclusively on the server thread. */
final class GeminiStructureSchedule {
    static final int ATTEMPTS = 24, RETRY_SECONDS = 60;
    private int seconds = 20, failures;
    private String lastFailure = "none";
    private int revision = GeminiConfig.revision;
    private long lastLog = Long.MIN_VALUE;

    void reschedule(WorldSavedData owner) {
        seconds = Math.min(seconds, 20);
        owner.markDirty();
    }

    boolean due(WorldSavedData owner, int interval) {
        if(revision != GeminiConfig.revision) {
            revision = GeminiConfig.revision;
            seconds = Math.min(seconds, Math.min(interval, RETRY_SECONDS));
        }
        if(--seconds > 0) { owner.markDirty(); return false; }
        // Reserve a retry before any terrain or block callback can fail.
        seconds = RETRY_SECONDS;
        owner.markDirty();
        return true;
    }

    void success(WorldSavedData owner, WorldServer world, String type, int x, int y, int z, int interval) {
        seconds = interval;
        failures = 0;
        lastFailure = "none";
        owner.markDirty();
        MainRegistry.logger.info("[Gemini] Generated " + type + " at " + x + ", " + y + ", " + z
                + " in dimension " + world.provider.dimensionId + ".");
    }

    void failed(WorldSavedData owner, WorldServer world, String type, String reason) {
        seconds = RETRY_SECONDS;
        if(failures < Integer.MAX_VALUE) failures++;
        lastFailure = reason;
        owner.markDirty();
        long now = world.getTotalWorldTime();
        if(lastLog == Long.MIN_VALUE || now < lastLog || now - lastLog >= 6000L) {
            MainRegistry.logger.info("[Gemini] Automatic " + type + " attempt failed in dimension "
                    + world.provider.dimensionId + ": " + reason + "; retry in " + seconds + " active seconds.");
            lastLog = now;
        }
        GeminiInvasionManager.debug(world, type + "-search", reason);
    }

    String status() {
        return "next attempt=" + seconds + " active seconds; failures since last success=" + failures + "; last failure=" + lastFailure;
    }

    void read(NBTTagCompound root) {
        if(!root.hasKey("AutomaticGeneration")) return;
        NBTTagCompound tag = root.getCompoundTag("AutomaticGeneration");
        seconds = MathHelper.clamp_int(tag.getInteger("Seconds"), 1, 86400);
        failures = Math.max(0, tag.getInteger("Failures"));
        lastFailure = tag.getString("LastFailure");
    }

    void write(NBTTagCompound root) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Seconds", seconds);
        tag.setInteger("Failures", failures);
        tag.setString("LastFailure", lastFailure);
        root.setTag("AutomaticGeneration", tag);
    }

    static List<EntityPlayer> players(World world) {
        List<EntityPlayer> result = new ArrayList<EntityPlayer>();
        int examined = 0;
        for(Object entry : world.playerEntities) {
            if(++examined > 128) break;
            EntityPlayer player = (EntityPlayer) entry;
            if(player.isEntityAlive() && !player.capabilities.isCreativeMode && player.worldObj == world)
                result.add(player);
        }
        return result;
    }

    static ChunkCoordinates candidate(WorldServer world, int x, int z, int min, int max) {
        double angle = world.rand.nextDouble() * Math.PI * 2D;
        // Sample the annulus, including its near edge on small view distances.
        double radius = min + world.rand.nextDouble() * (max - min);
        return new ChunkCoordinates(MathHelper.floor_double(x + Math.cos(angle) * radius), 0,
                MathHelper.floor_double(z + Math.sin(angle) * radius));
    }

    static boolean footprint(World world, int x, int z, int half) {
        return (long) x - half >= -29999984L && (long) x + half + 1 <= 29999984L
                && (long) z - half >= -29999984L && (long) z + half + 1 <= 29999984L
                && GardenerNests.loadedVolume(world, AxisAlignedBB.getBoundingBox(x - half, 0, z - half,
                        x + half + 1, world.getActualHeight(), z + half + 1));
    }

    /** Bounded, read-only diagnostic samples; never requests a chunk or consumes world RNG. */
    static String availability(World world, int min, int max, int half) {
        List<EntityPlayer> players = players(world);
        boolean loaded = false;
        for(int i = 0; i < Math.min(4, players.size()) && !loaded; i++) {
            EntityPlayer player = players.get(i);
            for(int j = 0; j < 8 && !loaded; j++) {
                double angle = j * Math.PI / 4D;
                int radius = j % 2 == 0 ? min : (min + max) / 2;
                loaded = footprint(world, MathHelper.floor_double(player.posX + Math.cos(angle) * radius),
                        MathHelper.floor_double(player.posZ + Math.sin(angle) * radius), half);
            }
        }
        return "eligible survival players=" + players.size() + "; loaded footprint in diagnostic samples=" + loaded;
    }
}
