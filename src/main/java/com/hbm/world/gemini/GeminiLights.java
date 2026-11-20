package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityGeminiLight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** The invasion save owns this transient scheduler. A running encounter and its claims are never saved. */
public final class GeminiLights {
    private static final int CHECK_SECONDS = 15;
    private static final double GARDENER_RANGE_SQ = 128D * 128D;
    private Encounter active;
    private long nextCheck = -1;

    public enum Kind {
        LIGHTS("Lights in the Sky"), HARVEST("Harvest");
        public final String name;
        Kind(String name) { this.name = name; }
    }

    public static final class Encounter {
        public final Kind kind;
        public final double x, z;
        private final long endsAt;
        private final boolean forced;
        private final List<EntityGeminiLight> lights = new ArrayList<>();
        private final Map<UUID, EntityGeminiLight> claims = new HashMap<>();
        // A Light leaves after its first successful grip, so this set cannot outgrow the starting group.
        private final Set<UUID> captured = new HashSet<>();
        private boolean closing;

        private Encounter(Kind kind, double x, double z, long endsAt, boolean forced) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.endsAt = endsAt;
            this.forced = forced;
        }

        public boolean available(Entity prey, EntityGeminiLight light) {
            UUID id = prey.getUniqueID();
            EntityGeminiLight owner = claims.get(id);
            return !closing && !captured.contains(id) && (owner == null || owner == light);
        }

        public boolean claim(Entity prey, EntityGeminiLight light) {
            if(!available(prey, light)) return false;
            claims.put(prey.getUniqueID(), light);
            return true;
        }

        public void captured(Entity prey) { captured.add(prey.getUniqueID()); }
        public void relinquish(Entity prey, EntityGeminiLight light) { claims.remove(prey.getUniqueID(), light); }

        private void depart() {
            if(closing) return;
            closing = true;
            for(EntityGeminiLight light : lights) light.depart();
        }
    }

    void tick(WorldServer world) {
        long now = world.getTotalWorldTime();
        if(active != null) {
            if(now >= active.endsAt || !active.forced && (world.playerEntities.isEmpty() || !eligible(world, active.kind))) {
                finish(world);
                return;
            }
            active.lights.removeIf(light -> {
                if(light.isEntityAlive() && light.addedToChunk && world.getEntityByID(light.getEntityId()) == light
                        && world.doChunksNearChunkExist(MathHelper.floor_double(light.posX), MathHelper.floor_double(light.posY),
                                MathHelper.floor_double(light.posZ), 32)) return false;
                light.setDead();
                return true;
            });
            while(active.lights.size() > GeminiConfig.lightsMaxActive)
                active.lights.remove(active.lights.size() - 1).setDead();
            if(active.lights.isEmpty()) finish(world);
            else if(now + 100 >= active.endsAt || !active.forced && nightEnding(world)) active.depart();
            return;
        }
        if(!eligible(world, Kind.LIGHTS) || !night(world) || nightEnding(world)
                || GeminiConfig.lightsMaxActive == 0 || world.playerEntities.isEmpty()) return;
        if(nextCheck < 0) nextCheck = now + world.rand.nextInt(CHECK_SECONDS * 20);
        if(now < nextCheck) return;
        nextCheck = now + CHECK_SECONDS * 20;

        EntityPlayer anchor = null;
        int seen = 0;
        for(Object entry : world.playerEntities) {
            EntityPlayer player = (EntityPlayer)entry;
            if(player.isEntityAlive() && !player.capabilities.disableDamage && !(player instanceof FakePlayer)
                    && exposed(player) && world.rand.nextInt(++seen) == 0) anchor = player;
        }
        if(anchor == null) return;
        int phase = GeminiInvasionManager.getPhase(world);
        double pressure = GeminiPopulation.gardenPressure(anchor);
        if(GardenerNests.active(world)) pressure += GardenerNests.irisPressure(anchor);
        if(WretchCamps.active(world)) pressure += WretchCamps.irisPressure(anchor);
        double weight = (1D + 0.5D * (phase - GeminiConfig.lightsMinPhase)) * (1D + pressure);
        if(eligible(world, Kind.HARVEST) && roll(world, GeminiConfig.harvestIntervalSeconds, weight)) {
            if(start(world, Kind.HARVEST, anchor.posX, anchor.posZ, false) > 0) return;
        }
        if(roll(world, GeminiConfig.lightsIntervalSeconds, weight)) start(world, Kind.LIGHTS, anchor.posX, anchor.posZ, false);
    }

    private static boolean roll(World world, int seconds, double weight) {
        return world.rand.nextDouble() < -Math.expm1(-CHECK_SECONDS * weight / seconds);
    }

    private static boolean eligible(World world, Kind kind) {
        int required = kind == Kind.HARVEST ? Math.max(3, GeminiConfig.lightsMinPhase) : GeminiConfig.lightsMinPhase;
        return GeminiConfig.lightsEnabled && !world.provider.hasNoSky && world.difficultySetting != EnumDifficulty.PEACEFUL
                && GeminiInvasionManager.canActivityOccur(world, required);
    }

    private static long clock(World world) { return Math.floorMod(world.getWorldTime(), 24000L); }
    private static boolean night(World world) { return clock(world) >= 13000 && clock(world) < 23000; }
    private static boolean nightEnding(World world) { return !night(world) || clock(world) >= 22500; }

    public static boolean exposed(Entity prey) {
        World world = prey.worldObj;
        int x = MathHelper.floor_double(prey.posX), z = MathHelper.floor_double(prey.posZ);
        int y = MathHelper.floor_double(prey.boundingBox.maxY);
        return world.getChunkProvider().chunkExists(x >> 4, z >> 4)
                && y >= world.getHeightValue(x, z);
    }

    private int start(WorldServer world, Kind kind, double x, double z, boolean forced) {
        int desired = Math.min(GeminiConfig.lightsMaxActive,
                kind == Kind.HARVEST ? 8 + world.rand.nextInt(3) : 3 + world.rand.nextInt(2));
        int minimum = Math.min(desired, kind == Kind.HARVEST ? 4 : 2);
        List<Vec3> sites = new ArrayList<>();
        double heading = world.rand.nextDouble() * Math.PI * 2D;
        for(int attempt = 0; attempt < 48 && sites.size() < desired; attempt++) {
            double angle = heading + (world.rand.nextDouble() - 0.5D) * (kind == Kind.HARVEST ? 5.2D : 2.2D);
            double distance = forced && attempt >= 32 ? world.rand.nextDouble() * 16D : 34D + world.rand.nextDouble() * 35D;
            int bx = MathHelper.floor_double(x + Math.cos(angle) * distance);
            int bz = MathHelper.floor_double(z + Math.sin(angle) * distance);
            if(Math.abs(bx) >= 29999984 || Math.abs(bz) >= 29999984
                    || !world.getChunkProvider().chunkExists(bx >> 4, bz >> 4)) continue;
            int surface = world.getHeightValue(bx, bz);
            if(!forced && (surface < 2 || world.getBlock(bx, surface - 1, bz).getMaterial().isLiquid())) continue;
            double y = Math.min(world.getActualHeight() - 3D, surface + 32D + world.rand.nextInt(23));
            if(y <= surface + 2D || !world.doChunksNearChunkExist(bx, MathHelper.floor_double(y), bz, 32)) continue;
            if(!world.isAirBlock(bx, MathHelper.floor_double(y), bz)) continue;
            Vec3 site = Vec3.createVectorHelper(bx + 0.5D, y, bz + 0.5D);
            if(sites.stream().noneMatch(other -> other.squareDistanceTo(site) < 9D * 9D)) sites.add(site);
        }
        if(sites.size() < minimum || sites.isEmpty()) return 0;

        if(active != null) finish(world);
        Encounter encounter = new Encounter(kind, x, z, world.getTotalWorldTime() + 20L * GeminiConfig.lightsDurationSeconds, forced);
        active = encounter;
        for(Vec3 site : sites) {
            EntityGeminiLight light = new EntityGeminiLight(world, encounter, site);
            encounter.lights.add(light);
            if(!world.spawnEntityInWorld(light)) {
                encounter.lights.remove(light);
                light.setDead();
            }
        }
        if(encounter.lights.size() < minimum) {
            finish(world);
            return 0;
        }
        if(kind == Kind.HARVEST) GeminiIris.invasionPulse(world);
        double y = world.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z)) + 8D;
        world.playSoundEffect(x, y, z, "hbm:gemini.iris_rumble",
                kind == Kind.HARVEST ? 3.6F : 1.6F, kind == Kind.HARVEST ? 0.48F : 1.35F);
        GeminiInvasionManager.debug(world, "sky-encounter", kind.name + " began with " + encounter.lights.size() + " Lights.");
        return encounter.lights.size();
    }

    private void finish(World world) {
        if(active == null) return;
        Encounter encounter = active;
        active = null;
        nextCheck = world.getTotalWorldTime() + 60 * 20;
        for(EntityGeminiLight light : encounter.lights) light.setDead();
        GeminiInvasionManager.debug(world, "sky-encounter-end", encounter.kind.name + " ended.");
    }

    boolean owns(EntityGeminiLight light) { return active != null && active.lights.contains(light); }

    void unloadChunk(int x, int z) {
        if(active == null) return;
        for(EntityGeminiLight light : active.lights)
            if(light.chunkCoordX == x && light.chunkCoordZ == z) light.setDead();
    }

    void unload(World world) { finish(world); }

    public static boolean harvestNear(World world, double x, double z) {
        if(world.provider.dimensionId != 0) return false;
        GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
        Encounter active = data == null ? null : data.lights.active;
        if(active == null || active.kind != Kind.HARVEST || active.closing) return false;
        double dx = x - active.x, dz = z - active.z;
        return dx * dx + dz * dz <= GARDENER_RANGE_SQ;
    }

    public static void forceStart(WorldServer world, Kind kind, double x, double z) {
        GeminiLights lights = commandState(world);
        if(GeminiConfig.lightsMaxActive == 0) throw new CommandException("The Gemini Light cap is zero.");
        if(lights.start(world, kind, x, z, true) == 0)
            throw new CommandException("Insufficient clear, loaded airspace or Light admission was vetoed.");
    }

    public static void forceStop(WorldServer world, Kind kind) {
        GeminiLights lights = commandState(world);
        if(lights.active != null && lights.active.kind == kind) lights.finish(world);
    }

    public static String status(WorldServer world, Kind kind) {
        Encounter active = commandState(world).active;
        if(active == null || active.kind != kind) return kind.name + " is inactive.";
        long seconds = Math.max(0, (active.endsAt - world.getTotalWorldTime() + 19) / 20);
        return kind.name + " is " + (active.closing ? "ending" : "active") + " with "
                + active.lights.size() + " Lights and " + seconds + " seconds remaining" + (active.forced ? " (forced)." : ".");
    }

    private static GeminiLights commandState(WorldServer world) {
        GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
        if(world.provider.dimensionId != 0 || data == null) throw new CommandException("Light encounters run only in the Overworld.");
        return data.lights;
    }
}
