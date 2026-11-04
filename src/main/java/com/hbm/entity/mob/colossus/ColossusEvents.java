package com.hbm.entity.mob.colossus;

import com.hbm.config.ColossusConfig;
import com.hbm.packet.toserver.ColossusControlPacket;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class ColossusEvents {
    @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinWorldEvent event) {
        if(event.world.isRemote) return;
        if(event.entity instanceof EntityPrismarineColossus && !((EntityPrismarineColossus)event.entity).admit()) event.setCanceled(true);
        if(event.entity instanceof EntityItem && ColossusTerrain.suppressing(event.world)) event.setCanceled(true);
    }
    @SubscribeEvent public void unload(ChunkEvent.Unload event) {
        if(event.world.isRemote) return;
        for(java.util.List list:event.getChunk().entityLists) for(Object o:list) {
            if(o instanceof EntityPrismarineColossus) ((EntityPrismarineColossus)o).unload();
            if(o instanceof EntityColossusPart) ((EntityColossusPart)o).setDead();
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(event.world.isRemote) return;
        for(Object o:event.world.loadedEntityList) if(o instanceof EntityPrismarineColossus) ((EntityPrismarineColossus)o).unload();
    }
    @SubscribeEvent public void logout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        ColossusControlPacket.INPUT.remove(event.player.getUniqueID());
        release(event.player);
    }
    @SubscribeEvent public void changed(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) { ColossusControlPacket.INPUT.remove(event.player.getUniqueID()); }
    private void release(EntityPlayer p) {
        for(Object o:p.worldObj.loadedEntityList) if(o instanceof EntityPrismarineColossus) ((EntityPrismarineColossus)o).releasePlayer(p);
    }
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.player.worldObj.isRemote) return;
        EntityPlayer p=event.player;
        int grace=p.getEntityData().getInteger("RNTColossusFallGrace");
        if(grace>0) { p.fallDistance=0; p.getEntityData().setInteger("RNTColossusFallGrace",grace-1); }
        ColossusControlPacket input=ColossusControlPacket.INPUT.remove(p.getUniqueID());
        if(input==null || input.player!=p || !(p instanceof EntityPlayerMP) || !p.isEntityAlive()) return;
        Entity entity=p.worldObj.getEntityByID(input.boss);
        if(entity instanceof EntityPrismarineColossus && ((EntityPrismarineColossus)entity).holds(p)) ((EntityPrismarineColossus)entity).control((EntityPlayerMP)p,input.vertical,input.brace,input.release);
    }
    @SubscribeEvent public void fall(LivingFallEvent event) {
        if(event.entityLiving.getEntityData().getInteger("RNTColossusFallGrace")>0) event.setCanceled(true);
    }
    @SubscribeEvent public void natural(TickEvent.WorldTickEvent event) {
        World w=event.world;
        if(event.phase!=TickEvent.Phase.END || w.isRemote || !ColossusConfig.natural || w.provider.dimensionId!=0 || w.getTotalWorldTime()%24000!=0 || w.playerEntities.isEmpty() || !ColossusEncounters.get(w).room()) return;
        EntityPlayer p=(EntityPlayer)w.playerEntities.get(w.rand.nextInt(w.playerEntities.size()));
        if(p.capabilities.isCreativeMode || !p.isEntityAlive()) return;
        double angle=w.rand.nextDouble()*Math.PI*2,x=p.posX+Math.cos(angle)*64,z=p.posZ+Math.sin(angle)*64;
        if(!EntityPrismarineColossus.loaded(w,x,z,16) || w.getBiomeGenForCoords((int)Math.floor(x),(int)Math.floor(z)).biomeID!=net.minecraft.world.biome.BiomeGenBase.ocean.biomeID) return;
        EntityPrismarineColossus boss=new EntityPrismarineColossus(w);
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z),y=w.getHeightValue(bx,bz);
        for(int i=0;i<64 && y>1;i++,y--) if(w.getBlock(bx,y-1,bz).getMaterial().blocksMovement()) break;
        boss.setLocationAndAngles(x,y,z,0,0);
        if(boss.safeSpawn() && !w.spawnEntityInWorld(boss)) boss.setDead();
    }
}
