package com.hbm.entity.mob.witherstorm;

import com.hbm.config.WitherStormConfig;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class WitherStormEvents {
    @SubscribeEvent public void join(EntityJoinWorldEvent event) {
        if(event.world.isRemote) return;
        if(event.entity instanceof EntityWitherStorm) {
            EntityWitherStorm boss = (EntityWitherStorm)event.entity;
            WitherStormEncounters data = WitherStormEncounters.get(event.world);
            if((boss.registered && !data.contains(boss.getUniqueID())) || !data.admit(boss)) { event.setCanceled(true); boss.setDead(); }
            else boss.registered = true;
        } else if(event.entity instanceof EntityWitherStormPart || event.entity instanceof EntityWitherStormSkull) {
            WitherStormEncounters data = WitherStormEncounters.get(event.world);
            Entity previous = data.find(event.entity.getUniqueID(), event.world);
            if(previous != null && previous != event.entity) { event.setCanceled(true); event.entity.setDead(); }
            else { data.track(event.entity); if(event.entity.isDead) event.setCanceled(true); }
        }
        // Scope only synchronous block callbacks; consumed blocks must not scatter inventories.
        if(WitherStormTerrain.suppressing(event.world) && event.entity instanceof net.minecraft.entity.item.EntityItem) event.setCanceled(true);
    }
    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload event) {
        if(event.world.isRemote) return;
        WitherStormEncounters data = WitherStormEncounters.get(event.world);
        for(java.util.List section : event.getChunk().entityLists) for(Object value : section) {
            Entity e = (Entity)value;
            if(e instanceof EntityWitherStorm || e instanceof EntityWitherStormPart || e instanceof EntityWitherStormSkull) data.untrack(e);
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(!event.world.isRemote) WitherStormEncounters.get(event.world).unload(event.world);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void summon(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if(event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !WitherStormConfig.survivalSummon || !player.isSneaking() || held == null || held.getItem() != Items.nether_star) return;
        World world = player.worldObj;
        int x = event.x, y = event.y, z = event.z;
        if(!WitherStormMath.areaLoaded(world, x, z, 2) || world.getBlock(x, y, z) != com.hbm.blocks.ModBlocks.block_euphemium) return;
        // Existing late-game RNT material supplies the core; no new progression item.
        for(int axis = 0; axis < 2; axis++) {
            boolean valid = world.getBlock(x, y - 1, z) == Blocks.soul_sand;
            for(int i = -1; i <= 1 && valid; i++) {
                int bx = x + (axis == 0 ? i : 0), bz = z + (axis == 1 ? i : 0);
                valid = world.getBlock(bx, y, bz) == (i == 0 ? com.hbm.blocks.ModBlocks.block_euphemium : Blocks.soul_sand)
                    && world.getBlock(bx, y + 1, bz) == Blocks.skull && world.getTileEntity(bx, y + 1, bz) instanceof TileEntitySkull
                    && ((TileEntitySkull)world.getTileEntity(bx, y + 1, bz)).func_145904_a() == 1
                    && player.canPlayerEdit(bx, y, bz, event.face, held) && player.canPlayerEdit(bx, y + 1, bz, event.face, held);
            }
            if(!valid || !player.canPlayerEdit(x, y - 1, z, event.face, held)) continue;
            event.setCanceled(true);
            if(world.isRemote || !WitherStormEncounters.get(world).room()) return;
            EntityWitherStorm boss = new EntityWitherStorm(world);
            boss.setLocationAndAngles(x + 0.5D, y + 3D, z + 0.5D, player.rotationYaw, 0);
            if(!world.spawnEntityInWorld(boss)) { boss.setDead(); return; }
            WitherStormTerrain.beginSuppression(world);
            try {
                for(int i = -1; i <= 1; i++) {
                    int bx = x + (axis == 0 ? i : 0), bz = z + (axis == 1 ? i : 0);
                    // Skull break callbacks otherwise drop the ritual's skulls.
                    world.setBlockMetadataWithNotify(bx, y + 1, bz, 8, 2);
                    world.setBlockToAir(bx, y + 1, bz); world.setBlockToAir(bx, y, bz);
                }
                world.setBlockToAir(x, y - 1, z);
            } finally { WitherStormTerrain.endSuppression(); }
            if(!player.capabilities.isCreativeMode && --held.stackSize == 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            boss.playSound("hbm:entity.witherStorm.transform", 2F, 1F);
            return;
        }
    }
}
