package com.hbm.world.gemini;

import com.hbm.entity.mob.gemini.EntityNaturesMockery;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

public final class MockeryEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void living(LivingUpdateEvent event) {
        EntityLivingBase host = event.entityLiving;
        if(host.worldObj.isRemote) return;
        NBTTagCompound tag = host.getEntityData();
        if(!tag.hasKey("MockeryCaptor")) return;
        Entity owner = host.worldObj.getEntityByID(tag.getInteger("MockeryCaptorId"));
        if(owner instanceof EntityNaturesMockery && owner.isEntityAlive() && owner.addedToChunk
                && owner.getUniqueID().toString().equals(tag.getString("MockeryCaptor"))
                && ((EntityNaturesMockery) owner).owns(host) && MockerySpawning.active((EntityNaturesMockery) owner)) {
            ((EntityNaturesMockery) owner).hold(host);
        } else {
            tag.removeTag("MockeryCaptor"); tag.removeTag("MockeryCaptorId");
            tag.setLong("MockeryEscapeUntil", host.worldObj.getTotalWorldTime() + 400L);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void chat(ServerChatEvent event) {
        for(Object entry : event.player.worldObj.getEntitiesWithinAABB(EntityNaturesMockery.class, event.player.boundingBox.expand(12, 6, 12))) {
            EntityNaturesMockery mob = (EntityNaturesMockery) entry;
            if(mob.isEntityAlive() && MockerySpawning.active(mob) && mob.getDistanceSqToEntity(event.player) <= 144D) mob.hear(event.message);
        }
    }
}
