package com.hbm.world.sandworm;

import com.hbm.config.SandwormConfig;
import com.hbm.entity.mob.sandworm.EntitySandworm;
import com.hbm.entity.mob.sandworm.EntityWormMonster;
import com.hbm.entity.mob.sandworm.EntityWormProjectile;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;

public final class WormEvents {
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase != TickEvent.Phase.END || DimensionManager.getWorld(0) == null) return;
        WorldServer overworld = DimensionManager.getWorld(0);
        WormEncounters data = WormEncounters.get(overworld); data.tick();
        if(SandwormConfig.natural && overworld.getTotalWorldTime() % SandwormConfig.naturalInterval == 0 && data.room() && !overworld.playerEntities.isEmpty()) {
            EntityPlayer p = (EntityPlayer)overworld.playerEntities.get(overworld.rand.nextInt(overworld.playerEntities.size()));
            if(p.capabilities.isCreativeMode || !p.isEntityAlive()) return;
            double angle = overworld.rand.nextDouble() * Math.PI * 2;
            int x = MathHelper.floor_double(p.posX + Math.sin(angle) * 80), z = MathHelper.floor_double(p.posZ + Math.cos(angle) * 80);
            if(!overworld.checkChunksExist(x - 12, 0, z - 12, x + 12, 255, z + 12)) return;
            BiomeGenBase b = overworld.getBiomeGenForCoords(x, z);
            if(b != BiomeGenBase.desert && b != BiomeGenBase.desertHills) return;
            EntitySandworm worm = new EntitySandworm(overworld);
            worm.setLocationAndAngles(x + 0.5, Math.max(8, overworld.getHeightValue(x, z) - 35), z + 0.5, overworld.rand.nextFloat() * 360, -35);
            if(!overworld.spawnEntityInWorld(worm)) {
                data.cancel(worm.getUniqueID()); WormEncounter e = data.find(worm.getUniqueID()); if(e != null) { e.exteriorRetired = true; data.markDirty(); }
            }
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) { if(!e.player.worldObj.isRemote) WormEncounters.get(e.player.worldObj).recover(e.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) { if(!e.player.worldObj.isRemote) WormEncounters.get(e.player.worldObj).respawn(e.player); }
    @SubscribeEvent public void changeDimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(!e.player.worldObj.isRemote) WormEncounters.get(e.player.worldObj).recover(e.player); }
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase == TickEvent.Phase.END && e.player instanceof EntityPlayerMP && e.player.dimension == SandwormConfig.dimension && e.player.posY < 50)
            WormEncounters.get(e.player.worldObj).rescue((EntityPlayerMP)e.player);
    }
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST) public void join(EntityJoinWorldEvent e) {
        if(e.world.isRemote) return;
        if(e.entity instanceof EntitySandworm) {
            WormEncounters data = WormEncounters.get(e.world);
            WormEncounter record = data.admit((EntitySandworm)e.entity);
            if(record == null || record.retired()) {
                if(record != null) { record.exteriorRetired = true; data.markDirty(); }
                e.entity.setDead(); e.setCanceled(true);
            }
            return;
        }
        if(e.entity instanceof EntityWormProjectile) {
            java.util.UUID owner = ((EntityWormProjectile)e.entity).encounter();
            WormEncounter record = owner == null ? null : WormEncounters.get(e.world).find(owner);
            if(owner != null && (record == null || record.retired())) { e.entity.setDead(); e.setCanceled(true); }
            return;
        }
        if(!(e.entity instanceof EntityWormMonster)) return;
        EntityWormMonster mob = (EntityWormMonster)e.entity;
        WormEncounters data = WormEncounters.get(e.world);
        WormEncounter record = mob.encounter == null ? null : data.find(mob.encounter);
        if((mob.encounter != null && (record == null || record.retired())) || !data.track(mob)) { mob.setDead(); e.setCanceled(true); }
    }
    @SubscribeEvent public void unload(ChunkEvent.Unload event) {
        if(event.world.isRemote || DimensionManager.getWorld(0) == null) return;
        WormEncounters data = WormEncounters.get(event.world);
        for(java.util.List list : event.getChunk().entityLists) for(Object o : list) {
            Entity e = (Entity)o;
            if(e instanceof EntitySandworm || e instanceof EntityWormMonster) data.untrack(e);
        }
    }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent e) {
        if(e.world.provider.dimensionId == SandwormConfig.dimension && !e.getPlayer().capabilities.isCreativeMode) e.setCanceled(true);
    }
    @SubscribeEvent public void placeBlock(BlockEvent.PlaceEvent e) {
        if(e.world.provider.dimensionId == SandwormConfig.dimension && !e.player.capabilities.isCreativeMode) e.setCanceled(true);
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate e) {
        if(e.world.provider.dimensionId == SandwormConfig.dimension) e.getAffectedBlocks().clear();
    }
}
