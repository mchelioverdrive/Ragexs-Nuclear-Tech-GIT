package com.hbm.handler;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.entity.mob.gemini.WoodcrawlerCapture;
import com.hbm.handler.restraint.RestraintState;
import com.hbm.handler.restraint.Restraints;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.WoodcrawlerRestraintPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/** Capture-only packet filtering. Netty reads immutable anchors; world mutations stay on server ticks. */
public final class WoodcrawlerCaptures {
    private static final String GATE = "hbm_woodcrawler_grip";
    private static final UUID SPEED = UUID.fromString("f252508a-6c90-4264-b339-01b0d04890cf");
    private static final java.lang.reflect.Field FLOATING = cpw.mods.fml.relauncher.ReflectionHelper.findField(
            net.minecraft.network.NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");
    private static final Map<World, Set<EntityWoodcrawler>> ACTIVE = new WeakHashMap<World, Set<EntityWoodcrawler>>();
    private static final class Anchor {
        final double x, y, z; final float yaw, pitch;
        Anchor(double x, double y, double z, float yaw, float pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
    }
    private static final class Gate extends ChannelInboundHandlerAdapter {
        volatile Anchor anchor;
        volatile int input;
        @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            Anchor at = anchor;
            if(at != null && message instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) message;
                // Vanilla's teleport acknowledgement sees the real anchor, never a client-selected position.
                message = new C03PacketPlayer.C06PacketPlayerPosLook(at.x, at.y, at.y + 1.62, at.z,
                        packet.func_149463_k() ? packet.func_149462_g() : at.yaw,
                        packet.func_149463_k() ? packet.func_149470_h() : at.pitch, false);
            }
            super.channelRead(context, message);
        }
    }
    private static Gate gate(EntityPlayerMP player) {
        return player.playerNetServerHandler == null ? null
                : (Gate) player.playerNetServerHandler.netManager.channel().pipeline().get(GATE);
    }
    public static boolean prepare(EntityPlayerMP player) {
        if(player.playerNetServerHandler == null || !player.playerNetServerHandler.netManager.isChannelOpen()) return false;
        if(gate(player) == null) player.playerNetServerHandler.netManager.channel().pipeline().addBefore("packet_handler", GATE, new Gate());
        return gate(player) != null;
    }
    public static void offerInput(EntityPlayerMP player, int mask) {
        Gate gate = gate(player);
        if(gate != null && gate.anchor != null) gate.input = mask & 15;
    }
    public static WoodcrawlerCapture owner(EntityLivingBase victim) {
        if(!victim.getEntityData().hasKey("WoodcrawlerCaptor")) return null;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("WoodcrawlerCaptorId"));
        return owner instanceof EntityWoodcrawler && owner.isEntityAlive()
                && owner.getUniqueID().toString().equals(victim.getEntityData().getString("WoodcrawlerCaptor"))
                && ((EntityWoodcrawler) owner).capture().owns(victim) ? ((EntityWoodcrawler) owner).capture() : null;
    }
    public static boolean available(EntityLivingBase victim) {
        return !Restraints.blocked(victim) && !victim.getEntityData().hasKey("MockeryCaptor")
                && !victim.getEntityData().hasKey("GardenerCaptor") && !victim.getEntityData().hasKey("WretchCaptor")
                && victim.getEntityData().getLong("WoodcrawlerCaptorUntil") <= victim.worldObj.getTotalWorldTime()
                && victim.getEntityData().getLong("WoodcrawlerEscapeUntil") <= victim.worldObj.getTotalWorldTime();
    }
    public static void track(EntityWoodcrawler mob) {
        Set<EntityWoodcrawler> set = ACTIVE.get(mob.worldObj);
        if(set == null) { set = new LinkedHashSet<EntityWoodcrawler>(); ACTIVE.put(mob.worldObj, set); }
        set.add(mob);
    }
    public static void restrain(EntityLivingBase victim, boolean held) {
        IAttributeInstance speed = victim.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if(speed.getModifier(SPEED) != null) speed.removeModifier(speed.getModifier(SPEED));
        if(held) speed.applyModifier(new AttributeModifier(SPEED, "Woodcrawler grip", -1, 2).setSaved(false));
        RestraintState.get(victim).controlAI(victim, held || Restraints.blocked(victim));
        if(victim instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) victim;
            Gate gate = gate(player);
            if(gate != null) { gate.anchor = held ? new Anchor(victim.posX, victim.posY, victim.posZ, victim.rotationYaw, victim.rotationPitch) : null; gate.input = 0; }
            if(player.playerNetServerHandler != null) PacketDispatcher.wrapper.sendTo(new WoodcrawlerRestraintPacket(held), player);
        }
    }
    public static void anchor(EntityLivingBase victim, double x, double y, double z) {
        victim.velocityChanged = true;
        if(victim instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) victim; Gate gate = gate(player);
            if(gate == null) return;
            gate.anchor = new Anchor(x, y, z, victim.rotationYaw, victim.rotationPitch);
            WoodcrawlerCapture capture = owner(victim);
            if(capture != null) capture.input(gate.input);
            if(victim.worldObj.getTotalWorldTime() % 2 == 0)
                player.playerNetServerHandler.setPlayerLocation(x, y, z, victim.rotationYaw, victim.rotationPitch);
            // Native collision support is replaced by a validated tendril attachment.
            try { FLOATING.setInt(player.playerNetServerHandler, 0); }
            catch(IllegalAccessException failure) { throw new IllegalStateException("Cannot validate Woodcrawler grip support", failure); }
        }
    }
    @SubscribeEvent public void living(LivingUpdateEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if(victim.worldObj.isRemote) return;
        WoodcrawlerCapture capture = owner(victim);
        if(capture != null) {
            victim.motionX = victim.motionY = victim.motionZ = 0; victim.setJumping(false);
            victim.moveForward = victim.moveStrafing = 0; victim.setSprinting(false); victim.fallDistance = 0;
        } else if(victim.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getModifier(SPEED) != null
                || victim.getEntityData().hasKey("WoodcrawlerCaptor") && victim.getEntityData().getLong("WoodcrawlerCaptorUntil") < victim.worldObj.getTotalWorldTime()) {
            victim.getEntityData().removeTag("WoodcrawlerCaptor"); victim.getEntityData().removeTag("WoodcrawlerCaptorId"); victim.getEntityData().removeTag("WoodcrawlerCaptorUntil");
            restrain(victim, false);
        }
    }
    @SubscribeEvent public void end(TickEvent.WorldTickEvent event) {
        if(event.world.isRemote || event.phase != TickEvent.Phase.END) return;
        Set<EntityWoodcrawler> set = ACTIVE.get(event.world);
        if(set == null) return;
        for(Iterator<EntityWoodcrawler> it = set.iterator(); it.hasNext();) {
            EntityWoodcrawler mob = it.next();
            if(!mob.isEntityAlive() || !mob.addedToChunk || event.world.getEntityByID(mob.getEntityId()) != mob) mob.capture().release();
            if(!mob.capture().active()) { it.remove(); continue; }
            mob.capture().position();
        }
    }
    private static void release(EntityLivingBase victim) { WoodcrawlerCapture owner = owner(victim); if(owner != null) owner.release(); }
    @SubscribeEvent public void logout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) { release(event.player); }
    @SubscribeEvent public void dimension(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        World old = net.minecraftforge.common.DimensionManager.getWorld(event.fromDim);
        if(old != null) {
            Entity e = old.getEntityByID(event.player.getEntityData().getInteger("WoodcrawlerCaptorId"));
            if(e instanceof EntityWoodcrawler && ((EntityWoodcrawler) e).capture().owns(event.player)) ((EntityWoodcrawler) e).capture().release();
        }
        restrain(event.player, false);
    }
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if(event.entity.worldObj.isRemote || event.isCanceled()) return;
        WoodcrawlerCapture capture = owner(event.entityLiving);
        if(capture != null) capture.death(event);
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        Set<EntityWoodcrawler> set = ACTIVE.remove(event.world);
        if(set != null) for(EntityWoodcrawler mob : set) mob.capture().release();
    }
}
