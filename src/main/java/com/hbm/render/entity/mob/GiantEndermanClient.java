package com.hbm.render.entity.mob;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.entity.mob.giantenderman.EntityGiantEnderman;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose.Action;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public final class GiantEndermanClient {
    private final Map<EntityGiantEnderman, Visual> active = new LinkedHashMap<>();
    private final GiantEndermanPose pose = new GiantEndermanPose();
    private boolean grabbed;

    @SubscribeEvent public void joined(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity instanceof EntityGiantEnderman) {
            active.put((EntityGiantEnderman)event.entity, new Visual());
        }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        grabbed = false;
        if (mc.isGamePaused()) return;
        int remaining = mc.gameSettings.particleSetting == 2 ? 22 : mc.gameSettings.particleSetting == 1 ? 100 : 200;
        Iterator<Map.Entry<EntityGiantEnderman, Visual>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityGiantEnderman, Visual> entry = it.next();
            EntityGiantEnderman boss = entry.getKey();
            if (boss.isDead || boss.worldObj != mc.theWorld || mc.theWorld.getEntityByID(boss.getEntityId()) != boss) {
                it.remove();
                continue;
            }
            if (mc.thePlayer == null) continue;
            double distance = boss.getDistanceSqToEntity(mc.thePlayer);
            if (distance > 112 * 112) continue;
            Entity victim = boss.worldObj.getEntityByID(boss.grabbedId());
            if (victim instanceof EntityLivingBase && boss.action() == Action.GRAB && boss.isEntityAlive()) {
                boss.evaluate(pose, 1);
                Vec3 point = boss.victimPoint(pose, (EntityLivingBase)victim, boss.rotationYaw, boss.posX, boss.posY, boss.posZ);
                if (victim.getDistanceSq(point.xCoord, point.yCoord, point.zCoord) < 64) {
                    victim.setPosition(point.xCoord, point.yCoord + victim.yOffset, point.zCoord);
                    victim.motionX = victim.motionY = victim.motionZ = 0;
                    victim.fallDistance = 0;
                    if (victim == mc.thePlayer) grabbed = true;
                }
            }

            Visual last = entry.getValue();
            Action action = boss.action();
            double frame = boss.frame(0);
            boolean changed = action != last.action;
            boolean burst = (action == Action.VANISH && (changed && frame >= 8 || last.frame < 8 && frame >= 8))
                || changed && (action == Action.ARRIVE || action == Action.SCREAM)
                || action == Action.DEATH && last.frame < 62 && frame >= 62;
            int setting = mc.gameSettings.particleSetting;
            int amount = burst ? 82 : boss.ticksExisted % 2 == 0 ? 5 : 0;
            if (distance > 64 * 64) amount /= 2;
            amount = Math.min(remaining, amount / (setting == 2 ? 5 : setting == 1 ? 2 : 1));
            for (int i = 0; i < amount; i++) {
                double width = burst ? 8 : 4.6;
                double x = boss.posX + (boss.getRNG().nextDouble() - 0.5) * width * 2;
                double y = boss.posY + boss.getRNG().nextDouble() * 30;
                double z = boss.posZ + (boss.getRNG().nextDouble() - 0.5) * width * 2;
                particle(mc, boss, x, y, z, burst ? 4 : 1.2);
            }
            remaining -= amount;
            boolean smash = action == Action.SMASH && frame >= 33 && frame < 45;
            boolean stomp = action == Action.STOMP && frame >= 28 && frame < 33;
            if ((smash || stomp) && remaining > 0) {
                double radius = smash ? (boss.phase() == 3 ? Math.min(15, (frame - 33) * 1.6) : 5) : 5;
                double yaw = Math.toRadians(boss.rotationYaw);
                double reach = smash ? 5.5 : 1.6;
                int count = Math.min(remaining, setting == 2 ? 4 : setting == 1 ? 10 : 20);
                for (int i = 0; i < count; i++) {
                    double angle = (i + boss.getRNG().nextDouble()) * Math.PI * 2 / count;
                    particle(mc, boss, boss.posX - Math.sin(yaw) * reach + Math.cos(angle) * radius, boss.posY + 0.35,
                        boss.posZ + Math.cos(yaw) * reach + Math.sin(angle) * radius, 1.2);
                }
                remaining -= count;
            }
            last.action = action;
            last.frame = frame;
        }
    }

    private static void particle(Minecraft mc, EntityGiantEnderman boss, double x, double y, double z, double speed) {
        mc.effectRenderer.addEffect(new EntityPortalFX(mc.theWorld, x, y, z, (boss.getRNG().nextDouble() - 0.5) * speed,
            boss.getRNG().nextDouble() * 0.7, (boss.getRNG().nextDouble() - 0.5) * speed));
    }

    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event) {
        if (!grabbed || event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        String text = I18n.format("giant_enderman.grab.escape");
        mc.fontRenderer.drawStringWithShadow(text, (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(text)) / 2,
            event.resolution.getScaledHeight() * 2 / 3, 0xF4BAFF);
    }

    private static final class Visual {
        Action action;
        double frame;
    }
}
