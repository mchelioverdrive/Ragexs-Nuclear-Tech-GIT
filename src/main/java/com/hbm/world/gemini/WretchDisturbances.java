package com.hbm.world.gemini;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.HashMap;
import com.hbm.entity.mob.gemini.EntityWretch;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Server-thread, bounded sensory history. Values contain neither worlds nor entity references. */
public final class WretchDisturbances {
    public static final int MOVEMENT = 0, WORK = 1, COMBAT = 2, EXPLOSION = 3;
    private static final Map<World, Buffer> WORLDS = new WeakHashMap<World, Buffer>();
    public static final class Cue {
        public long serial, time;
        public double x, y, z;
        public float strength;
        public int kind;
        public UUID player;
    }
    private static final class Buffer {
        final Cue[] cues = new Cue[48];
        long serial, tick = Long.MIN_VALUE;
        int count;
        final Map<UUID, Sample> movement = new HashMap<UUID, Sample>();
    }
    private static final class Sample { double x, y, z; long time; }
    private WretchDisturbances() { }
    public static boolean eligible(EntityPlayer p) {
        return p != null && p.isEntityAlive() && !p.capabilities.isCreativeMode && !p.capabilities.disableDamage
                && !p.isEntityInvulnerable() && !p.noClip && p.addedToChunk;
    }
    public static void record(World world, double x, double y, double z, float strength, int kind, Entity source) {
        if(world.isRemote || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if(source instanceof EntityWretch) return; // Never wake or provoke oneself with one's own damage.
        if(source instanceof EntityPlayer && !eligible((EntityPlayer) source)) return;
        Buffer b = WORLDS.get(world);
        if(b == null) { b = new Buffer(); WORLDS.put(world, b); }
        long now = world.getTotalWorldTime();
        if(b.tick != now) { b.tick = now; b.count = 0; }
        if(b.count++ >= (kind >= COMBAT ? 64 : 32)) return;
        int slot = (int) (++b.serial % b.cues.length);
        Cue c = b.cues[slot]; if(c == null) c = b.cues[slot] = new Cue();
        c.serial = b.serial; c.time = now; c.x = x; c.y = y; c.z = z;
        c.strength = Math.max(0.1F, Math.min(40F, strength)); c.kind = kind;
        c.player = source instanceof EntityPlayer ? source.getUniqueID() : null;
    }
    public static Cue[] recent(World world) { Buffer b = WORLDS.get(world); return b == null ? null : b.cues; }
    public static void movement(EntityPlayer player) {
        if(player.worldObj.isRemote || !eligible(player)) return;
        Buffer b = WORLDS.get(player.worldObj);
        if(b == null) { b = new Buffer(); WORLDS.put(player.worldObj, b); }
        long now = player.worldObj.getTotalWorldTime();
        Sample old = b.movement.get(player.getUniqueID());
        if(old == null) {
            if(b.movement.size() >= 512) b.movement.clear();
            old = new Sample(); b.movement.put(player.getUniqueID(), old); old.x = player.posX; old.y = player.posY; old.z = player.posZ; old.time = now; return;
        }
        double dx = player.posX - old.x, dy = player.posY - old.y, dz = player.posZ - old.z;
        if(now >= old.time && now - old.time <= 40 && dx * dx + dy * dy + dz * dz > 0.01 && dx * dx + dz * dz < 100)
            record(player.worldObj, player.posX, player.posY, player.posZ, player.isSneaking() ? 0.35F : player.isSprinting() ? 2.5F : 1.2F, MOVEMENT, player);
        old.x = player.posX; old.y = player.posY; old.z = player.posZ; old.time = now;
    }
    public static void unload(World world) { WORLDS.remove(world); }
}
