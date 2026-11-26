package com.hbm.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.hbm.entity.mob.squirrel.EntitySquirrel;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.GeminiGripPacket;
import com.hbm.world.gemini.GeminiInvasionManager;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.world.WorldEvent;

/** A physical grip for the existing capture states. Captors still choose every destination and outcome. */
public final class GeminiCaptures {
    public static final int RELEASED = 0, HELD = 1, STRUGGLE = 2;
    private static final Map<World, Map<Entity, Grip>> SERVER = new HashMap<>();
    // Client-thread state tied to an entity instance, so tracking loss and respawn cannot strand a flag.
    private static final Map<Entity, ClientGrip> CLIENT = new IdentityHashMap<>();

    private static final class Grip {
        final Entity captor, victim;
        final World world;
        final Consumer<String> interrupt;
        final BooleanSupplier update;
        final int mode;
        double x, y, z, captorX, captorY, captorZ, sentX, sentY, sentZ;

        Grip(Entity captor, Entity victim, boolean struggle, Consumer<String> interrupt, BooleanSupplier update) {
            this.captor = captor;
            this.victim = victim;
            this.world = captor.worldObj;
            this.interrupt = interrupt;
            this.update = update;
            mode = struggle ? STRUGGLE : HELD;
            x = victim.posX;
            y = victim.posY;
            z = victim.posZ;
            updateCaptorPosition();
        }

        void updateCaptorPosition() {
            captorX = captor.posX;
            captorY = captor.posY;
            captorZ = captor.posZ;
        }

        String invalid() {
            if(!loaded(captor, world)) return "Captor died or left the world";
            if(!loaded(victim, world) || victim.noClip || victim.ridingEntity != null
                    || victim instanceof EntityPlayerMP && !connected((EntityPlayerMP) victim)) return "Victim died, mounted or left the world";
            if(victim.getDistanceSq(x, y, z) > 4D) return "Victim was moved out of the grip";
            if(captor.getDistanceSq(captorX, captorY, captorZ) > 64D) return "Captor moved out of range";
            return null;
        }
    }

    private static final class ClientGrip {
        int mode, steps;
        double x, feet, z;
    }

    private static boolean loaded(Entity entity, World world) {
        return entity.worldObj == world && entity.isEntityAlive() && entity.addedToChunk
                && world.getEntityByID(entity.getEntityId()) == entity
                && world.getChunkProvider().chunkExists(entity.chunkCoordX, entity.chunkCoordZ);
    }

    private static Grip grip(Entity victim) {
        if(victim.worldObj.isRemote) return null;
        Map<Entity, Grip> grips = SERVER.get(victim.worldObj);
        return grips == null ? null : grips.get(victim);
    }

    public static boolean available(Entity victim) {
        return grip(victim) == null && !victim.noClip && (!(victim instanceof EntitySquirrel) || !((EntitySquirrel) victim).isAttached());
    }

    public static <T extends Entity> T captive(Entity captor, Class<T> type) {
        Map<Entity, Grip> grips = SERVER.get(captor.worldObj);
        if(grips != null) for(Grip grip : grips.values()) {
            if(grip.captor == captor && type.isInstance(grip.victim) && grip.invalid() == null) return type.cast(grip.victim);
        }
        return null;
    }

    public static boolean heldBy(Entity captor, Entity victim) {
        Grip grip = grip(victim);
        return grip != null && grip.captor == captor;
    }

    public static boolean valid(Entity captor, Entity victim) {
        Grip grip = grip(victim);
        return grip != null && grip.captor == captor && grip.invalid() == null;
    }

    public static boolean attach(Entity captor, Entity victim, boolean struggle, Consumer<String> interrupt) {
        return attach(captor, victim, struggle, interrupt, null);
    }

    public static boolean attach(Entity captor, Entity victim, boolean struggle, Consumer<String> interrupt, BooleanSupplier update) {
        if(captor.worldObj.isRemote || !available(victim) || !loaded(victim, captor.worldObj)
                || !loaded(captor, captor.worldObj) || victim.ridingEntity != null) return false;
        if(victim instanceof EntityPlayerMP && !connected((EntityPlayerMP) victim)) return false;
        Grip grip = new Grip(captor, victim, struggle, interrupt, update);
        SERVER.computeIfAbsent(grip.world, world -> new IdentityHashMap<>()).put(victim, grip);
        stop(victim);
        if(victim instanceof EntityLiving) ((EntityLiving) victim).getNavigator().clearPathEntity();
        // Stop any previously tracked knockback once. Ordinary movement uses tracker deltas.
        victim.velocityChanged = true;
        broadcast(grip, grip.mode);
        if(victim instanceof EntityPlayerMP) sendPosition(grip);
        return true;
    }

    public static void release(Entity captor, Entity victim) {
        Map<Entity, Grip> grips = SERVER.get(captor.worldObj);
        Grip grip = grips == null ? null : grips.get(victim);
        if(grip != null && grip.captor == captor) release(grip);
    }

    private static void release(Grip grip) {
        Entity victim = grip.victim;
        Map<Entity, Grip> grips = SERVER.get(grip.world);
        if(grips == null || !grips.remove(victim, grip)) return;
        if(grips.isEmpty()) SERVER.remove(grip.world);
        stop(victim);
        victim.velocityChanged = true;
        broadcast(grip, RELEASED);
        if(victim instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) victim;
            if(connected(player)) {
                PacketDispatcher.wrapper.sendTo(new GeminiGripPacket(player, RELEASED, false), player);
                if(player.worldObj == grip.world && player.isEntityAlive())
                    player.playerNetServerHandler.setPlayerLocation(player.posX, player.posY, player.posZ,
                            player.rotationYaw, player.rotationPitch);
            }
        }
    }

    private static boolean connected(EntityPlayerMP player) {
        return player.playerNetServerHandler != null && player.playerNetServerHandler.netManager.isChannelOpen();
    }

    private static void stop(Entity victim) {
        victim.motionX = victim.motionY = victim.motionZ = 0;
        victim.fallDistance = 0;
        victim.onGround = victim.isCollided = victim.isCollidedHorizontally = victim.isCollidedVertically = false;
        if(victim instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) victim;
            living.moveForward = living.moveStrafing = 0;
            living.setJumping(false);
            if(!victim.worldObj.isRemote) victim.setSprinting(false);
        }
    }

    public static void position(Entity victim, double x, double y, double z) {
        Grip grip = grip(victim);
        if(grip == null || victim.getDistanceSq(grip.x, grip.y, grip.z) > 4D) return;
        victim.setPosition(x, y, z);
        grip.x = x;
        grip.y = y;
        grip.z = z;
        grip.updateCaptorPosition();
        stop(victim);
        if(victim.riddenByEntity != null) victim.updateRiderPosition();
    }

    public static void center(Entity victim, double x, double y, double z) {
        position(victim, x, y - victim.height * 0.5D + victim.posY - victim.boundingBox.minY, z);
    }

    public static double liftProgress(int tick, int duration) {
        double t = Math.min(1D, Math.max(0D, (double) tick / duration));
        return t * t * (3D - 2D * t);
    }

    private static void broadcast(Grip grip, int mode) {
        ((WorldServer) grip.world).getEntityTracker().func_151247_a(grip.victim,
                PacketDispatcher.wrapper.getPacketFrom(new GeminiGripPacket(grip.victim, mode, false)));
    }

    private static void sendPosition(Grip grip) {
        EntityPlayerMP player = (EntityPlayerMP) grip.victim;
        if(!connected(player)) return;
        grip.sentX = grip.x;
        grip.sentY = grip.y;
        grip.sentZ = grip.z;
        PacketDispatcher.wrapper.sendTo(new GeminiGripPacket(player, grip.mode, true), player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void living(LivingUpdateEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if(victim.worldObj.isRemote || grip(victim) == null) return;
        stop(victim);
        // Keep the native base tick for damage immunity, suffocation/drowning, potions and death.
        victim.onEntityUpdate();
        if(grip(victim) != null) stop(victim);
        event.setCanceled(true);
    }

    @SubscribeEvent public void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if(event.entityPlayer instanceof EntityPlayerMP) {
            Grip grip = grip(event.target);
            if(grip != null) PacketDispatcher.wrapper.sendTo(new GeminiGripPacket(event.target, grip.mode, false),
                    (EntityPlayerMP) event.entityPlayer);
        }
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        Map<Entity, Grip> grips = SERVER.get(event.world);
        if(grips == null || grips.isEmpty()) return;
        Map<Grip, String> invalid = null;
        for(Grip grip : grips.values()) {
            String reason = grip.invalid();
            if(reason == null && grip.update != null && !grip.update.getAsBoolean()) reason = "Captor stopped carrying prey";
            if(reason != null) {
                if(invalid == null) invalid = new HashMap<>();
                invalid.put(grip, reason);
                continue;
            }
            stop(grip.victim);
            if(grip.victim instanceof EntityPlayerMP && event.world.getTotalWorldTime() % 3 == 0
                    && grip.victim.getDistanceSq(grip.sentX, grip.sentY, grip.sentZ) > 0.000001D) sendPosition(grip);
        }
        if(invalid != null) invalid.forEach(GeminiCaptures::interrupt);
    }

    private static void interrupt(Grip grip, String reason) {
        Map<Entity, Grip> grips = SERVER.get(grip.world);
        if(reason == null || grips == null || grips.get(grip.victim) != grip) return;
        diagnostic(grip.captor, grip.victim, "invalid", reason, 0, null);
        grip.interrupt.accept(reason);
        release(grip);
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Grip grip = grip(event.player);
        if(grip != null) interrupt(grip, "Victim disconnected");
        interruptCaptor(event.player.worldObj, event.player, "Captor disconnected");
    }

    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        World oldWorld = net.minecraftforge.common.DimensionManager.getWorld(event.fromDim);
        Map<Entity, Grip> grips = SERVER.get(oldWorld);
        Grip grip = grips == null ? null : grips.get(event.player);
        if(grip != null) interrupt(grip, "Victim changed dimension");
        interruptCaptor(oldWorld, event.player, "Captor changed dimension");
    }

    private static void interruptCaptor(World world, Entity captor, String reason) {
        Map<Entity, Grip> grips = SERVER.get(world);
        if(grips == null) return;
        for(Grip grip : new ArrayList<>(grips.values())) if(grip.captor == captor) interrupt(grip, reason);
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(event.world.isRemote) {
            CLIENT.keySet().removeIf(entity -> entity.worldObj == event.world);
            return;
        }
        Map<Entity, Grip> grips = SERVER.get(event.world);
        if(grips != null) for(Grip grip : new ArrayList<>(grips.values())) interrupt(grip, "World unloaded");
        SERVER.remove(event.world);
    }

    /** Called before native collision, including boats, mod vehicles and interpolating clients. */
    public static boolean blockTravel(Object entity) {
        Entity victim = (Entity) entity;
        if(victim.worldObj == null || (victim.worldObj.isRemote ? !CLIENT.containsKey(victim) : grip(victim) == null)) return false;
        stop(victim);
        return true;
    }

    /** C03 packets run on the server tick in 1.7.10. Keep look and the player's native tick. */
    public static boolean handlePlayerPosition(Object handler, Object message) {
        EntityPlayerMP player = ((NetHandlerPlayServer) handler).playerEntity;
        if(grip(player) == null || player.playerConqueredTheEnd) return false;
        C03PacketPlayer packet = (C03PacketPlayer) message;
        if(packet.func_149463_k() && Float.isFinite(packet.func_149462_g()) && Float.isFinite(packet.func_149470_h())) {
            player.rotationYaw = packet.func_149462_g();
            player.rotationPitch = packet.func_149470_h();
        }
        player.onUpdateEntity();
        MinecraftServer.getServer().getConfigurationManager().updatePlayerPertinentChunks(player);
        return true;
    }

    public static void clientGrip(Entity victim, int mode, boolean position, double x, double feet, double z) {
        if(mode == RELEASED) {
            CLIENT.remove(victim);
            stop(victim);
            return;
        }
        ClientGrip grip = CLIENT.computeIfAbsent(victim, entity -> new ClientGrip());
        stop(victim);
        grip.mode = mode;
        if(position) {
            grip.steps = 3;
            grip.x = x;
            grip.feet = feet;
            grip.z = z;
        }
    }

    public static int clientTick(EntityLivingBase player) {
        if(!CLIENT.isEmpty()) CLIENT.keySet().removeIf(entity -> entity.worldObj != player.worldObj || entity.isDead
                || player.worldObj.getEntityByID(entity.getEntityId()) != entity);
        ClientGrip grip = CLIENT.get(player);
        if(grip == null) return RELEASED;
        if(grip.steps > 0) {
            player.setPosition(player.posX + (grip.x - player.posX) / grip.steps,
                    player.posY + (grip.feet - player.boundingBox.minY) / grip.steps,
                    player.posZ + (grip.z - player.posZ) / grip.steps);
            grip.steps--;
        }
        stop(player);
        player.setSprinting(false);
        return grip.mode;
    }

    public static boolean clientHeld(Entity victim) {
        return CLIENT.containsKey(victim);
    }

    public static void diagnostic(Entity captor, Entity victim, String state, String event, int progress, Vec3 goal) {
        if(victim == null || !GeminiInvasionManager.debugging(captor.worldObj)) return;
        boolean held = heldBy(captor, victim);
        double distance = goal == null ? 0D
                : Math.sqrt(victim.getDistanceSq(goal.xCoord, goal.yCoord + victim.posY - victim.boundingBox.minY - victim.height * 0.5D, goal.zCoord));
        GeminiInvasionManager.debug(captor.worldObj, "grip-" + captor.getUniqueID() + "-" + victim.getEntityId() + "-" + state + "-" + event,
                captor.getClass().getSimpleName() + "#" + captor.getEntityId() + " holding " + victim.getClass().getSimpleName() + "#"
                + victim.getEntityId() + " state=" + state + " center=(" + victim.posX + ","
                + (victim.boundingBox.minY + victim.height * 0.5D) + "," + victim.posZ + ") target=" + goal
                + " distance=" + distance + " gravitySuspended=" + held + " movementRestricted=" + held
                + " progress=" + progress + " " + event);
    }
}
