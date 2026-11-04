package com.hbm.entity.mob.witherstorm;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/** Bounded, sampled player trails; only the selected route is saved with the boss. */
final class WitherStormPursuit {
    private static final int SAMPLE_INTERVAL = 20, MAX_POINTS = 96;
    private static final double WAYPOINT_RADIUS_SQ = 4D * 4D;
    private final ArrayDeque<Waypoint> route = new ArrayDeque<>();
    private UUID target;
    private Waypoint lastKnown;
    private long nextRefresh;
    private long importedThrough = Long.MIN_VALUE;

    static boolean connected(EntityPlayerMP player) {
        return !(player instanceof FakePlayer) && player.playerNetServerHandler != null
            && player.playerNetServerHandler.netManager.isChannelOpen();
    }

    static boolean eligible(EntityPlayerMP player) {
        // Spectator mode is absent in vanilla 1.7.10. Exclude nonphysical backports.
        return connected(player) && player.isEntityAlive() && !player.noClip && !player.capabilities.isCreativeMode
            && !player.capabilities.disableDamage;
    }

    Vec3 destination(EntityWitherStorm boss, Trails trails) {
        World world = boss.worldObj;
        long now = world.getTotalWorldTime();
        if(now >= nextRefresh) {
            nextRefresh = now + SAMPLE_INTERVAL;
            trails.sample(world);
            EntityPlayerMP selected = null, nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for(Object value : world.playerEntities) {
                if(!(value instanceof EntityPlayerMP)) continue;
                EntityPlayerMP player = (EntityPlayerMP)value;
                if(!eligible(player) || player.worldObj != world) continue;
                if(player.getUniqueID().equals(target)) { selected = player; break; }
                double distance = boss.getDistanceSqToEntity(player);
                if(distance < best) { nearest = player; best = distance; }
            }
            if(selected == null && nearest != null) {
                selected = nearest;
                target = selected.getUniqueID();
                route.clear();
                lastKnown = null;
                importedThrough = Long.MIN_VALUE;
            }
            if(selected != null) {
                ArrayDeque<Waypoint> history = trails.history.get(target);
                if(history != null) for(Waypoint point : history) {
                    if(point.tick > importedThrough) {
                        append(route, point);
                        importedThrough = point.tick;
                    }
                }
                // Keep a more precise fallback for when the sampled waypoints run out.
                if(lastKnown == null || selected.getDistanceSq(lastKnown.position.xCoord, lastKnown.position.yCoord, lastKnown.position.zCoord) > 0D)
                    lastKnown = new Waypoint(now, selected.posX, selected.posY, selected.posZ);
            }
        }
        while(!route.isEmpty() && horizontalDistanceSq(boss, route.peekFirst().position) <= WAYPOINT_RADIUS_SQ) {
            Waypoint reached = route.removeFirst();
            ArrayDeque<Waypoint> history = trails.history.get(target);
            if(history != null) while(history.size() > 1 && history.peekFirst().tick <= reached.tick) history.removeFirst();
        }
        return !route.isEmpty() ? route.peekFirst().position : lastKnown == null ? null : lastKnown.position;
    }

    private static double horizontalDistanceSq(EntityWitherStorm boss, Vec3 point) {
        double dx = boss.posX - point.xCoord, dz = boss.posZ - point.zCoord;
        return dx * dx + dz * dz;
    }

    private static void append(ArrayDeque<Waypoint> points, Waypoint point) {
        if(points.size() >= MAX_POINTS) points.removeFirst();
        points.addLast(point);
    }

    static final class Trails {
        private final Map<UUID, ArrayDeque<Waypoint>> history = new HashMap<>();
        private long nextSample;

        void sample(World world) {
            long now = world.getTotalWorldTime();
            if(now < nextSample) return;
            nextSample = now + SAMPLE_INTERVAL;
            Set<UUID> eligible = new HashSet<>();
            for(Object value : world.playerEntities) {
                if(!(value instanceof EntityPlayerMP)) continue;
                EntityPlayerMP player = (EntityPlayerMP)value;
                if(!eligible(player)) continue;
                UUID id = player.getUniqueID();
                eligible.add(id);
                ArrayDeque<Waypoint> points = history.computeIfAbsent(id, key -> new ArrayDeque<>());
                while(!points.isEmpty() && points.peekFirst().tick < now - 2400L) points.removeFirst();
                Waypoint last = points.peekLast();
                if(last == null || player.getDistanceSq(last.position.xCoord, last.position.yCoord, last.position.zCoord) >= 36D)
                    append(points, new Waypoint(now, player.posX, player.posY, player.posZ));
            }
            history.keySet().retainAll(eligible);
        }

        void clear() { history.clear(); nextSample = 0; }
    }

    void write(NBTTagCompound nbt) {
        WitherStormMath.uuid(nbt, "Player", target);
        nbt.setLong("ImportedThrough", importedThrough);
        if(lastKnown != null) nbt.setTag("LastKnown", lastKnown.write());
        NBTTagList points = new NBTTagList();
        for(Waypoint point : route) points.appendTag(point.write());
        nbt.setTag("Route", points);
    }

    void read(NBTTagCompound nbt) {
        target = WitherStormMath.uuid(nbt, "Player");
        importedThrough = nbt.getLong("ImportedThrough");
        if(nbt.hasKey("LastKnown", 10)) lastKnown = Waypoint.read(nbt.getCompoundTag("LastKnown"));
        NBTTagList points = nbt.getTagList("Route", 10);
        for(int i = Math.max(0, points.tagCount() - MAX_POINTS); i < points.tagCount(); i++) {
            Waypoint point = Waypoint.read(points.getCompoundTagAt(i));
            if(point != null) route.addLast(point);
        }
    }

    private static final class Waypoint {
        final long tick;
        final Vec3 position;

        Waypoint(long tick, double x, double y, double z) {
            this.tick = tick;
            position = Vec3.createVectorHelper(x, y, z);
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Tick", tick);
            tag.setDouble("X", position.xCoord);
            tag.setDouble("Y", position.yCoord);
            tag.setDouble("Z", position.zCoord);
            return tag;
        }

        static Waypoint read(NBTTagCompound tag) {
            double x = tag.getDouble("X"), y = tag.getDouble("Y"), z = tag.getDouble("Z");
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Waypoint(tag.getLong("Tick"), x, y, z) : null;
        }
    }
}
