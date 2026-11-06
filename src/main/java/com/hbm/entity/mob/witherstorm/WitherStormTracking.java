package com.hbm.entity.mob.witherstorm;

import java.lang.reflect.Field;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.WorldServer;

/** Adjust only this encounter's native trackers, retaining vanilla chunk watching and packets. */
final class WitherStormTracking {
    private static final Field ENTRIES = ReflectionHelper.findField(EntityTracker.class, "trackedEntityIDs", "field_72794_c");

    private WitherStormTracking() {}

    static void refresh(Entity entity, double distance) {
        WorldServer world = (WorldServer)entity.worldObj;
        EntityTracker tracker = world.getEntityTracker();
        try {
            EntityTrackerEntry entry = (EntityTrackerEntry)((IntHashMap)ENTRIES.get(tracker)).lookup(entity.getEntityId());
            // The join callback precedes vanilla tracker creation; retry at the next root refresh.
            if(entry == null) return;
            int range = (int)Math.ceil(distance / 16D) * 16;
            // Registration alone is clamped by EntityTracker.entityViewDistance in 1.7.10.
            // Never set forceSpawn: each observer must still watch the entity's actual chunk.
            entry.blocksDistanceThreshold = range;
            // Vanilla only checks chunk watching when ADDING an observer: its shorter range
            // normally removes them before chunk unload. Extended ranges must also prune
            // existing observers so chunk re-entry sends a fresh spawn instead of stale IDs.
            for(Object value : entry.trackingPlayers.toArray()) {
                EntityPlayerMP player = (EntityPlayerMP)value;
                if(!world.getPlayerManager().isPlayerWatchingChunk(player, entity.chunkCoordX, entity.chunkCoordZ))
                    entry.removeFromWatchingList(player);
            }
            entry.sendEventsToPlayers(entity.worldObj.playerEntities);
        } catch(IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Wither Storm entity tracking", failure);
        }
    }
}
