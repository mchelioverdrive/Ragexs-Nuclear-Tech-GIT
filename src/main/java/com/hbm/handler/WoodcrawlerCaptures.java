package com.hbm.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.entity.mob.gemini.WoodcrawlerCapture;
import com.hbm.handler.restraint.Restraints;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

/** Woodcrawler escape input crosses Netty. Capture ownership stays on the server tick. */
public final class WoodcrawlerCaptures {
    private static final ConcurrentMap<EntityPlayerMP, Integer> INPUT = new ConcurrentHashMap<>();

    public static void acceptInput(EntityLivingBase victim, boolean held) {
        if(!(victim instanceof EntityPlayerMP)) return;
        if(held) INPUT.put((EntityPlayerMP) victim, 0);
        else INPUT.remove(victim);
    }

    public static void offerInput(EntityPlayerMP player, int mask) {
        INPUT.replace(player, mask & 15);
    }

    public static int input(EntityLivingBase victim) {
        return INPUT.getOrDefault(victim, 0);
    }

    public static WoodcrawlerCapture owner(EntityLivingBase victim) {
        NBTTagCompound tag = victim.getEntityData();
        if(!tag.hasKey("WoodcrawlerCaptor")) return null;
        Entity owner = victim.worldObj.getEntityByID(tag.getInteger("WoodcrawlerCaptorId"));
        if(!(owner instanceof EntityWoodcrawler) || !GeminiCaptures.heldBy(owner, victim)) return null;
        WoodcrawlerCapture capture = ((EntityWoodcrawler) owner).capture();
        return capture.owns(victim) ? capture : null;
    }

    public static boolean available(EntityLivingBase victim) {
        NBTTagCompound tag = victim.getEntityData();
        long time = victim.worldObj.getTotalWorldTime();
        return GeminiCaptures.available(victim) && !Restraints.blocked(victim) && !tag.hasKey("MockeryCaptor")
                && !tag.hasKey("GardenerCaptor") && !tag.hasKey("WretchCaptor")
                && tag.getLong("WoodcrawlerCocoonUntil") <= time && tag.getLong("WoodcrawlerCaptorUntil") <= time
                && tag.getLong("WoodcrawlerEscapeUntil") <= time;
    }

    @SubscribeEvent public void clearOrphanedGrip(LivingUpdateEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if(victim.worldObj.isRemote) return;
        NBTTagCompound tag = victim.getEntityData();
        if(tag.hasKey("WoodcrawlerCaptor") && tag.getLong("WoodcrawlerCaptorUntil") < victim.worldObj.getTotalWorldTime() && owner(victim) == null) {
            tag.removeTag("WoodcrawlerCaptor");
            tag.removeTag("WoodcrawlerCaptorId");
            tag.removeTag("WoodcrawlerCaptorUntil");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        if(event.entity.worldObj.isRemote || event.isCanceled()) return;
        WoodcrawlerCapture capture = owner(event.entityLiving);
        if(capture != null) capture.death(event);
    }
}
