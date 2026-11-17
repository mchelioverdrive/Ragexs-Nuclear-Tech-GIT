package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;

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
    private long lastTick = Long.MIN_VALUE;
    private int calls, examined, unloaded, spacing, plans, loadedChunks;
    private boolean fallbackUsed;
    private String lastSuccess = "none", searchMode = "not searched";

    void tick(WorldServer world) { lastTick = world.getTotalWorldTime(); if(calls < Integer.MAX_VALUE) calls++; }
    void examined() { examined++; }
    void spacing() { spacing++; }
    void plan() { plans++; }
    void unloaded() { unloaded++; }

    String failure(Map<String, Integer> reasons) {
        String common = "no candidate locations";
        int most = 0;
        for(Map.Entry<String, Integer> entry : reasons.entrySet()) if(entry.getValue() > most) {
            most = entry.getValue(); common = entry.getKey();
        }
        return "most common rejection=" + common + "; rejections=" + reasons;
    }

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
        examined = unloaded = spacing = plans = loadedChunks = 0;
        fallbackUsed = false;
        searchMode = "configured distance";
        owner.markDirty();
        return true;
    }

    void success(WorldSavedData owner, WorldServer world, String type, int x, int y, int z, int interval) {
        seconds = interval;
        failures = 0;
        lastFailure = "none";
        lastSuccess = x + ", " + y + ", " + z + " (dimension " + world.provider.dimensionId + ")";
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

    String status(World world) {
        long now = world.getTotalWorldTime();
        return "scheduler executing=" + (lastTick != Long.MIN_VALUE && now >= lastTick && now - lastTick <= 40)
                + "; callbacks this session=" + calls + "; next attempt=" + seconds + " active seconds"
                + "; last search candidates examined=" + examined + "; rejected unloaded=" + unloaded
                + "; rejected spacing=" + spacing + "; placement plans attempted=" + plans
                + "; fallback chunks requested=" + loadedChunks + "; search=" + searchMode
                + "; failures since last success=" + failures + "; last failure=" + lastFailure + "; last success=" + lastSuccess;
    }

    void read(NBTTagCompound root) {
        if(!root.hasKey("AutomaticGeneration")) return;
        NBTTagCompound tag = root.getCompoundTag("AutomaticGeneration");
        seconds = MathHelper.clamp_int(tag.getInteger("Seconds"), 1, 86400);
        failures = Math.max(0, tag.getInteger("Failures"));
        lastFailure = tag.getString("LastFailure");
        if(tag.hasKey("LastSuccess")) lastSuccess = tag.getString("LastSuccess");
        examined = Math.max(0, tag.getInteger("Examined")); unloaded = Math.max(0, tag.getInteger("Unloaded"));
        spacing = Math.max(0, tag.getInteger("Spacing")); plans = Math.max(0, tag.getInteger("Plans"));
        loadedChunks = Math.max(0, tag.getInteger("LoadedChunks"));
        if(tag.hasKey("SearchMode")) searchMode = tag.getString("SearchMode");
    }

    void write(NBTTagCompound root) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Seconds", seconds);
        tag.setInteger("Failures", failures);
        tag.setString("LastFailure", lastFailure);
        tag.setString("LastSuccess", lastSuccess);
        tag.setInteger("Examined", examined); tag.setInteger("Unloaded", unloaded);
        tag.setInteger("Spacing", spacing); tag.setInteger("Plans", plans);
        tag.setInteger("LoadedChunks", loadedChunks); tag.setString("SearchMode", searchMode);
        root.setTag("AutomaticGeneration", tag);
    }

    static List<EntityPlayer> players(World world) {
        List<EntityPlayer> result = new ArrayList<EntityPlayer>();
        int examined = 0;
        for(Object entry : world.playerEntities) {
            if(++examined > 128) break;
            EntityPlayer player = (EntityPlayer) entry;
            if(player.isEntityAlive() && player.worldObj == world)
                result.add(player);
        }
        return result;
    }

    /** A bounded snapshot of chunk presence, never a terrain read or chunk request. */
    static final class Candidates {
        final List<ChunkCoordinates> preferred = new ArrayList<ChunkCoordinates>();
        final List<ChunkCoordinates> closer = new ArrayList<ChunkCoordinates>();
        final int x, z, min, max, half;
        final boolean loadedAvailable;
        Candidates(WorldServer world, int x, int z, int min, int max, int half) {
            this.x = x; this.z = z; this.min = min; this.max = max; this.half = half;
            int left = (x - max - half) >> 4, bottom = (z - max - half) >> 4;
            int width = ((x + max + half) >> 4) - left + 1;
            int depth = ((z + max + half) >> 4) - bottom + 1;
            int[][] missing = new int[width + 1][depth + 1];
            for(int cx = 0; cx < width; cx++) for(int cz = 0; cz < depth; cz++)
                missing[cx + 1][cz + 1] = missing[cx][cz + 1] + missing[cx + 1][cz] - missing[cx][cz]
                        + (world.getChunkProvider().chunkExists(left + cx, bottom + cz) ? 0 : 1);
            int preferredCount = 0, closerCount = 0;
            // Exhaustive integer centers within the configuration-capped radius. Prefix
            // sums check complete footprints in constant time; reservoirs retain only 24.
            for(int dx = -max; dx <= max; dx++) for(int dz = -max; dz <= max; dz++) {
                long distance = (long) dx * dx + (long) dz * dz;
                if(distance > (long) max * max || !safeBounds(x + dx, z + dz, half)) continue;
                int x0 = ((x + dx - half) >> 4) - left, x1 = ((x + dx + half) >> 4) - left + 1;
                int z0 = ((z + dz - half) >> 4) - bottom, z1 = ((z + dz + half) >> 4) - bottom + 1;
                if(missing[x1][z1] - missing[x0][z1] - missing[x1][z0] + missing[x0][z0] != 0) continue;
                boolean normal = distance >= (long) min * min;
                List<ChunkCoordinates> pool = normal ? preferred : closer;
                int count = normal ? ++preferredCount : ++closerCount;
                int slot = count <= ATTEMPTS ? pool.size() : world.rand.nextInt(count);
                if(slot < ATTEMPTS) {
                    ChunkCoordinates point = new ChunkCoordinates(x + dx, 0, z + dz);
                    if(slot == pool.size()) pool.add(point); else pool.set(slot, point);
                }
            }
            Collections.shuffle(preferred, world.rand); Collections.shuffle(closer, world.rand);
            loadedAvailable = !preferred.isEmpty() || !closer.isEmpty();
        }
    }

    ChunkCoordinates candidate(Candidates candidates) {
        if(!candidates.preferred.isEmpty()) return candidates.preferred.remove(candidates.preferred.size() - 1);
        if(!candidates.closer.isEmpty()) {
            searchMode = "closer loaded footprint (configured annulus unavailable/exhausted)";
            return candidates.closer.remove(candidates.closer.size() - 1);
        }
        return null;
    }

    /** One nearby minimum footprint per opportunity, with no persistent chunk tickets. */
    ChunkCoordinates fallback(WorldServer world, Candidates candidates) {
        if(fallbackUsed || candidates.loadedAvailable) return null;
        fallbackUsed = true;
        int radius = Math.min(candidates.max, Math.min(candidates.min, candidates.half + 24));
        double angle = world.rand.nextDouble() * Math.PI * 2;
        int x = MathHelper.clamp_int(candidates.x + (int) Math.round(Math.cos(angle) * radius),
                -29999984 + candidates.half, 29999983 - candidates.half);
        int z = MathHelper.clamp_int(candidates.z + (int) Math.round(Math.sin(angle) * radius),
                -29999984 + candidates.half, 29999983 - candidates.half);
        return new ChunkCoordinates(x, 0, z);
    }

    void loadFallback(WorldServer world, ChunkCoordinates point, int half) {
        searchMode = "bounded nearby footprint loading (no loaded footprint available)";
        // At most 7x7 chunks for the 80-block camp, 4x4 for a nest. Requests
        // run on the server thread after spacing checks, immediately before preflight.
        for(int cx = (point.posX - half) >> 4; cx <= (point.posX + half) >> 4; cx++)
            for(int cz = (point.posZ - half) >> 4; cz <= (point.posZ + half) >> 4; cz++)
                if(!world.getChunkProvider().chunkExists(cx, cz)) {
                    loadedChunks++;
                    world.getChunkFromChunkCoords(cx, cz);
                }
    }

    private static boolean safeBounds(int x, int z, int half) {
        return (long) x - half >= -29999984L && (long) x + half + 1 <= 29999984L
                && (long) z - half >= -29999984L && (long) z + half + 1 <= 29999984L;
    }

    static boolean footprint(World world, int x, int z, int half) {
        return safeBounds(x, z, half)
                && GardenerNests.loadedVolume(world, AxisAlignedBB.getBoundingBox(x - half, 0, z - half,
                        x + half + 1, world.getActualHeight(), z + half + 1));
    }

    static String playerStatus(World world) {
        int creative = 0, survival = 0, adventure = 0;
        for(EntityPlayer player : players(world)) {
            if(player.capabilities.isCreativeMode) creative++;
            else if(player instanceof net.minecraft.entity.player.EntityPlayerMP
                    && ((net.minecraft.entity.player.EntityPlayerMP) player).theItemInWorldManager.getGameType().getID() == 2) adventure++;
            else survival++;
        }
        return "Living eligible players: Creative=" + creative + ", Survival=" + survival + ", Adventure=" + adventure
                + "; all three game modes can anchor automatic generation";
    }
}
