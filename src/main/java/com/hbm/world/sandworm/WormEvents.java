package com.hbm.world.sandworm;

import com.hbm.config.SandwormConfig;
import com.hbm.config.SpaceConfig;
import com.hbm.entity.mob.sandworm.EntitySandworm;
import com.hbm.entity.mob.sandworm.EntityWormMonster;
import com.hbm.entity.mob.sandworm.EntityWormProjectile;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.world.progression.BossProgression;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
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
        long now = data.time();
        if(SandwormConfig.surface.natural && now % SandwormConfig.surface.checkInterval == 0) {
            data.surface.trySpawn(DimensionManager.getWorld(SpaceConfig.siroccoDimension), data, now);
            if(!SandwormConfig.surface.extrasolarOnly) data.surface.trySpawn(overworld, data, now);
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(!(e.player instanceof EntityPlayerMP)) return;
        WormEncounters data = WormEncounters.get(e.player.worldObj);
        data.recover(e.player);
        data.surface.reset((EntityPlayerMP)e.player, data.time());
        checkInventory(e.player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.player instanceof EntityPlayerMP && DimensionManager.getWorld(0) != null)
            WormEncounters.get(e.player.worldObj).surface.forget((EntityPlayerMP)e.player);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if(!(e.player instanceof EntityPlayerMP)) return;
        WormEncounters data = WormEncounters.get(e.player.worldObj);
        data.respawn(e.player);
        data.surface.reset((EntityPlayerMP)e.player, data.time());
    }
    @SubscribeEvent public void changeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(!(e.player instanceof EntityPlayerMP)) return;
        WormEncounters data = WormEncounters.get(e.player.worldObj);
        data.recover(e.player);
        data.surface.reset((EntityPlayerMP)e.player, data.time());
    }
    @SubscribeEvent public void pickup(PlayerEvent.ItemPickupEvent e) { recordSteel(e.player, e.pickedUp.getEntityItem()); }
    @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent e) { recordSteel(e.player, e.crafting); }
    @SubscribeEvent public void smelted(PlayerEvent.ItemSmeltedEvent e) { recordSteel(e.player, e.smelting); }

    private static boolean isAlienSteel(ItemStack stack) {
        if(stack == null) return false;
        Item item = stack.getItem();
        return item == ModItems.ingot_combine_steel || item == ModItems.plate_combine_steel
                || item == Item.getItemFromBlock(ModBlocks.block_combine_steel);
    }

    private static void recordSteel(EntityPlayer player, ItemStack stack) {
        if(player instanceof EntityPlayerMP && !player.capabilities.isCreativeMode && isAlienSteel(stack))
            BossProgression.get(player.worldObj).recordAlienSteel();
    }

    private static void checkInventory(EntityPlayer player) {
        if(player.capabilities.isCreativeMode) return;
        BossProgression progress = BossProgression.get(player.worldObj);
        if(progress.hasAlienSteel()) return;
        for(ItemStack stack : player.inventory.mainInventory) {
            if(!isAlienSteel(stack)) continue;
            progress.recordAlienSteel();
            return;
        }
    }
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP)e.player;
        if(player.dimension == SandwormConfig.dimension && player.posY < 50)
            WormEncounters.get(player.worldObj).rescue(player);
        if(player.ticksExisted % 20 != 0) return;
        checkInventory(player);
        if(player.dimension == SpaceConfig.siroccoDimension || !SandwormConfig.surface.extrasolarOnly && player.dimension == 0) {
            WormEncounters data = WormEncounters.get(player.worldObj);
            data.surface.sample(player, data.time());
        }
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
