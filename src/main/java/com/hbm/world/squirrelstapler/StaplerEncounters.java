package com.hbm.world.squirrelstapler;

import com.hbm.config.SquirrelStaplerConfig;
import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.world.squirrelstapler.StaplerSquirrels.Apparition;
import com.hbm.world.squirrelstapler.StaplerSquirrels.Bear;
import com.hbm.world.squirrelstapler.StaplerSquirrels.Ghost;
import com.hbm.world.squirrelstapler.StaplerSquirrels.Large;
import com.hbm.world.squirrelstapler.StaplerSquirrels.Silhouette;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import cpw.mods.fml.common.eventhandler.Event.Result;

import java.util.ArrayList;
import java.util.List;

final class StaplerEncounters {
    enum Cue {
        SCREAM(2, 100), FOOTSTEPS(2, 100), FALSE_CALL(2, 100), SHADOW(2, 115),
        SILHOUETTE(3, 150), MESSAGE(2, 100), ANIMALS(3, 140), CABIN(3, 360),
        LIGHTS(3, 240), SILENCE(3, 180), GHOST(3, 260), BEAR(4, 460);

        final int day, length;
        Cue(int day, int length) { this.day = day; this.length = length; }
    }

    private Cue cue;
    private long endAt, nextAt, nextAmbient;
    private Entity actor;
    private EntityPlayer witness;
    private boolean featuredEncounter;

    boolean owns(Entity entity) { return actor == entity; }
    boolean silent() { return cue == Cue.SILENCE; }
    boolean dark() { return cue == Cue.LIGHTS || cue == Cue.CABIN; }
    boolean cabinDark() { return cue == Cue.CABIN; }

    void clear() {
        if(actor != null) actor.setDead();
        actor = null;
        witness = null;
        cue = null;
        endAt = nextAt = nextAmbient = 0;
        featuredEncounter = false;
    }

    void chunkUnloaded(int x, int z) {
        if(actor != null && (MathHelper.floor_double(actor.posX) >> 4) == x && (MathHelper.floor_double(actor.posZ) >> 4) == z) {
            actor.setDead();
            actor = null;
        }
    }

    void tickHunt(World world, StaplerCampaign data) {
        long time = world.getTotalWorldTime();
        if(cue != null) {
            if(time >= endAt || !permitted(cue) || witness == null || witness.worldObj != world || !witness.isEntityAlive()) {
                if(actor != null) actor.setDead();
                actor = null;
                witness = null;
                cue = null;
                nextAt = time + interval(world, data.day);
            } else if(cue == Cue.FOOTSTEPS && (endAt - time) % 14 == 0L) {
                soundBehind(witness, "step.grass", 0.85F, 0.65F + world.rand.nextFloat() * 0.25F, 9D + world.rand.nextInt(3));
            }
            return;
        }
        if(nextAmbient == 0) nextAmbient = time + 180;
        if(time >= nextAmbient) {
            EntityPlayer listener = player(world);
            if(listener != null) {
                int atmosphere = Math.min(data.day, 5);
                soundBehind(listener, atmosphere >= 4 ? "hbm:stapler.hum" : "hbm:stapler.wind",
                        0.36F + atmosphere * 0.07F, 1.22F - atmosphere * 0.09F, 7D);
            }
            nextAmbient = time + 450 + world.rand.nextInt(370);
        }
        if(!SquirrelStaplerConfig.settings.events || data.day < 2) return;
        if(nextAt == 0) nextAt = time + (data.day >= 3 ? 360 + world.rand.nextInt(300) : interval(world, data.day));
        if(time < nextAt) return;
        nextAt = time + 100;
        EntityPlayer listener = player(world);
        if(listener == null) return;
        if(!featuredEncounter && data.day >= 3) {
            Cue featured = data.day >= 4 && permitted(Cue.BEAR) ? Cue.BEAR : Cue.GHOST;
            if(begin(world, data, listener, featured, false)) {
                featuredEncounter = true;
                return;
            }
        }
        List<Cue> available = new ArrayList<>();
        for(Cue value : Cue.values()) if(value.day <= data.day && permitted(value)) available.add(value);
        if(!available.isEmpty()) begin(world, data, listener, available.get(world.rand.nextInt(available.size())), false);
    }

    private static int interval(World world, int day) {
        int base = 1350 - Math.min(5, day) * 200;
        return base + world.rand.nextInt(base);
    }

    private static boolean permitted(Cue cue) {
        SquirrelStaplerConfig.Values settings = SquirrelStaplerConfig.settings;
        return (cue != Cue.GHOST || settings.ghosts) && (cue != Cue.BEAR || settings.bears);
    }

    boolean begin(World world, StaplerCampaign data, EntityPlayer listener, Cue requested, boolean debug) {
        if(cue != null || data.stage != StaplerCampaign.HUNT || !permitted(requested) || !debug && requested.day > data.day) return false;
        switch(requested) {
        case SCREAM:
            soundBehind(listener, "hbm:stapler.scream", 2.3F, 0.83F, 24D);
            break;
        case FALSE_CALL:
            soundBehind(listener, "hbm:stapler.chatter", 1.5F, 0.65F, 17D);
            break;
        case MESSAGE:
            String[] lines = {"The forest has reviewed your paperwork.", "If the trees start counting, let them finish.",
                    "Keep your voice down. The woods have excellent hearing."};
            StaplerCampaign.tell(listener, lines[world.rand.nextInt(lines.length)]);
            break;
        case ANIMALS:
            for(Object value : world.getEntitiesWithinAABB(EntitySquirrel.class, listener.boundingBox.expand(22D, 10D, 22D)))
                if(!(value instanceof Apparition)) ((EntitySquirrel)value).frighten(listener);
            soundBehind(listener, "hbm:stapler.chatter", 1.2F, 1.4F, 12D);
            break;
        case CABIN:
            if(!StaplerForest.loaded(world, data)) return false;
            StaplerForest.lamp(world, data, true);
            world.playSoundEffect(StaplerForest.LAMP_X, data.cabinY + 1, StaplerForest.LAMP_Z, "hbm:stapler.knock", 2F, 0.8F);
            if(listener.getDistanceSq(StaplerForest.CABIN_X, data.cabinY, StaplerForest.CABIN_Z) < 1600D)
                StaplerCampaign.tell(listener, "Something turned off the lamp.");
            break;
        case LIGHTS:
            soundBehind(listener, "hbm:stapler.wind", 0.9F, 0.61F, 9D);
            break;
        case FOOTSTEPS:
        case SILENCE:
            break;
        default:
            Apparition creature = requested == Cue.GHOST ? new Ghost(world) : requested == Cue.BEAR ? new Bear(world) : new Silhouette(world);
            if(!place(world, listener, creature, requested == Cue.SHADOW ? 9 : 16, requested == Cue.SHADOW ? 16 : 30)) return false;
            int pursuit = requested == Cue.BEAR ? 90 + 10 * world.rand.nextInt(7)
                    : data.day >= 4 ? Math.max(40, 170 - data.day * 20) : Integer.MAX_VALUE;
            creature.begin(listener, requested.length, pursuit);
            if(requested == Cue.SHADOW) ((Silhouette)creature).setShadow();
            actor = creature;
            if(!world.spawnEntityInWorld(creature)) {
                actor = null;
                return false;
            }
            if(requested == Cue.BEAR) creature.playSound("hbm:stapler.growl", 2.2F, 0.7F);
            break;
        }
        witness = listener;
        cue = requested;
        endAt = world.getTotalWorldTime() + requested.length;
        return true;
    }

    void tickFinale(World world, StaplerCampaign data) {
        if(data.stage == StaplerCampaign.COUNTDOWN) {
            if(data.remainingTicks > 0) {
                int elapsed = data.countdownDuration - data.remainingTicks;
                int interval = Math.max(28, 200 - 172 * elapsed / data.countdownDuration);
                long now = world.getTotalWorldTime();
                if(now >= nextAmbient) {
                    for(Object entry : world.playerEntities) {
                        EntityPlayer player = (EntityPlayer)entry;
                        soundBehind(player, "hbm:stapler.hum", 0.75F + 0.9F * elapsed / data.countdownDuration,
                                0.65F + 0.32F * elapsed / data.countdownDuration, 5D);
                    }
                    nextAmbient = now + interval;
                }
                data.remainingTicks--;
            }
            if(data.remainingTicks == 0) {
                data.stage = StaplerCampaign.ARRIVAL;
                data.remainingTicks = 160;
            }
            data.markDirty();
            return;
        }
        if(actor == null || actor.isDead || witness == null || witness.worldObj != world
                || !witness.isEntityAlive() || actor.getDistanceSqToEntity(witness) > 1600D) {
            if(actor != null) actor.setDead();
            actor = null;
            witness = player(world);
            if(witness == null) return;
            EntityStaplerArrival arrival = new EntityStaplerArrival(world);
            placeArrival(world, witness, arrival);
            actor = arrival;
            if(!world.spawnEntityInWorld(arrival)) {
                actor = null;
                return;
            }
            arrival.playSound("hbm:stapler.arrival", 3F, 0.8F);
        }
        if(--data.remainingTicks <= 0) {
            data.stage = StaplerCampaign.COMPLETE;
            clear();
            for(Object entry : world.playerEntities) StaplerCampaign.tell((EntityPlayer)entry, "THE FOREST HAS CLOSED ITS LEDGER.");
        }
        data.markDirty();
    }

    private static void placeArrival(World world, EntityPlayer player, EntityStaplerArrival actor) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        int ox = MathHelper.floor_double(player.posX), oz = MathHelper.floor_double(player.posZ);
        for(int distance : new int[] {11, 6, 3}) for(int offset : new int[] {0, 42, -42, 85, -85, 132, -132, 180}) {
            float yaw = player.rotationYaw + offset;
            double angle = Math.toRadians(yaw);
            double x = player.posX - Math.sin(angle) * distance, z = player.posZ + Math.cos(angle) * distance;
            int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
            if(!world.checkChunksExist(Math.min(ox, bx), 0, Math.min(oz, bz), Math.max(ox, bx), 0, Math.max(oz, bz))) continue;
            double y = Math.max(player.posY - 0.55D, StaplerForest.ground(world, bx, bz) + 0.2D);
            if(world.rayTraceBlocks(eye, Vec3.createVectorHelper(x, y + 2.6D, z)) != null) continue;
            actor.setLocationAndAngles(x, y, z, yaw + 180F, 0F);
            return;
        }
        actor.setLocationAndAngles(player.posX, player.posY + 3D, player.posZ, player.rotationYaw + 180F, 0F);
    }

    static void replenishPrey(World world, StaplerCampaign data) {
        long time = world.getTotalWorldTime();
        if(time < data.nextPreyCheck) return;
        data.nextPreyCheck = time + 70 + world.rand.nextInt(31);
        EntityPlayer hunter = player(world);
        if(hunter == null) return;
        AxisAlignedBB search = AxisAlignedBB.getBoundingBox(hunter.posX - 44D, 0, hunter.posZ - 44D,
                hunter.posX + 44D, world.getActualHeight(), hunter.posZ + 44D);
        int normal = 0, large = 0;
        for(Object value : world.getEntitiesWithinAABB(EntitySquirrel.class, search)) {
            EntitySquirrel squirrel = (EntitySquirrel)value;
            if(!squirrel.isEntityAlive() || squirrel.isAttached() || squirrel instanceof Apparition) continue;
            if(squirrel.isLargeHuntingSquirrel()) large++;
            else normal++;
        }
        boolean big = data.largeLeft() > 0 && large == 0 && world.rand.nextInt(5) == 0;
        if(big || data.normalLeft() > 0 && normal < 6) spawnPrey(world, hunter, big, false);
    }

    static boolean spawnPrey(World world, EntityPlayer player, boolean large, boolean debug) {
        EntitySquirrel squirrel = large ? new Large(world) : new EntitySquirrel(world);
        return place(world, player, squirrel, debug ? 4 : 16, debug ? 10 : 36)
                && ForgeEventFactory.canEntitySpawn(squirrel, world, (float)squirrel.posX, (float)squirrel.posY, (float)squirrel.posZ) != Result.DENY
                && world.spawnEntityInWorld(squirrel);
    }

    private static boolean place(World world, EntityPlayer player, EntitySquirrel squirrel, int near, int far) {
        for(int attempt = 0; attempt < 14; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2D;
            int dist = near + world.rand.nextInt(far - near + 1);
            int x = MathHelper.floor_double(player.posX + Math.cos(angle) * dist);
            int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * dist);
            if(Math.abs(x - StaplerForest.CABIN_X) < 8 && Math.abs(z - StaplerForest.CABIN_Z) < 8) continue;
            if(!world.checkChunksExist(x - 2, 0, z - 2, x + 2, 0, z + 2)) continue;
            int y = StaplerForest.ground(world, x, z);
            if(y < 0 || !world.getBlock(x, y - 1, z).getMaterial().blocksMovement()) continue;
            if(world.getClosestPlayer(x + 0.5D, y, z + 0.5D, Math.max(2D, near / 2D)) != null) continue;
            squirrel.setLocationAndAngles(x + 0.5D, y, z + 0.5D, world.rand.nextFloat() * 360F, 0);
            if(squirrel.getCanSpawnHere()) return true;
        }
        return false;
    }

    static EntityPlayer player(World world) {
        if(world.playerEntities.isEmpty()) return null;
        int offset = world.rand.nextInt(world.playerEntities.size());
        for(int i = 0; i < world.playerEntities.size(); i++) {
            EntityPlayer player = (EntityPlayer)world.playerEntities.get((offset + i) % world.playerEntities.size());
            if(player.isEntityAlive() && !player.isPlayerSleeping()) return player;
        }
        return null;
    }

    private static void soundBehind(EntityPlayer listener, String sound, float volume, float pitch, double distance) {
        double angle = Math.toRadians(listener.rotationYaw + 180D) + (listener.worldObj.rand.nextDouble() - 0.5D) * 1.7D;
        listener.worldObj.playSoundEffect(listener.posX - Math.sin(angle) * distance, listener.posY,
                listener.posZ + Math.cos(angle) * distance, sound, volume, pitch);
    }
}
