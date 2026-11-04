package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.WitherStormConfig;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class WitherStormEvents {
    private final Map<World, List<BlockEvent.PlaceEvent>> placedSkulls = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST) public void join(EntityJoinWorldEvent event) {
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
        placedSkulls.remove(event.world);
        if(!event.world.isRemote) WitherStormEncounters.get(event.world).unload(event.world);
    }
    @SubscribeEvent public void skullPlaced(BlockEvent.PlaceEvent event) {
        if(!event.world.isRemote && WitherStormConfig.survivalSummon && event.placedBlock == Blocks.skull
            && event.itemInHand != null && event.itemInHand.getItem() == Items.skull && event.itemInHand.getItemDamage() == 1) {
            placedSkulls.computeIfAbsent(event.world, world -> new ArrayList<>()).add(event);
        }
    }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        List<BlockEvent.PlaceEvent> placements = placedSkulls.remove(event.world);
        if(placements != null) for(BlockEvent.PlaceEvent placement : placements) {
            // Forge has committed the placement and protection listeners have had a chance to veto it.
            if(!placement.isCanceled()) summonStructure(placement);
        }
        if(event.world.provider.dimensionId == 0) WitherStormEncounters.get(event.world).tickChallenge(event.world);
    }
    @SubscribeEvent public void fearAttack(LivingAttackEvent event) {
        Entity attacker = event.source.getEntity();
        if(!event.entity.worldObj.isRemote && attacker instanceof EntityLiving && event.source.getSourceOfDamage() == attacker
            && WitherStormFear.isFleeing((EntityLiving)attacker)) event.setCanceled(true);
    }
    private void summonStructure(BlockEvent.PlaceEvent placement) {
        World world = placement.world;
        int y = placement.y - 1;
        if(y < 1 || !isWitherSkull(world, placement.x, y + 1, placement.z)
            || !WitherStormEncounters.get(world).room()) return;
        for(int axis = 0; axis < 2; axis++) {
            int dx = 1 - axis, dz = axis;
            for(int offset = -1; offset <= 1; offset++) {
                int x = placement.x + offset * dx, z = placement.z + offset * dz;
                if(!WitherStormMath.loaded(world, x - dx, z - dz) || !WitherStormMath.loaded(world, x + dx, z + dz)
                    || world.getBlock(x, y, z) != ModBlocks.fake_command_block || world.getBlock(x, y - 1, z) != Blocks.soul_sand
                    || !world.isAirBlock(x - dx, y - 1, z - dz) || !world.isAirBlock(x + dx, y - 1, z + dz)) continue;
                boolean matches = true;
                for(int i = -1; i <= 1 && matches; i++) {
                    int bx = x + i * dx, bz = z + i * dz;
                    matches = (i == 0 || world.getBlock(bx, y, bz) == Blocks.soul_sand) && isWitherSkull(world, bx, y + 1, bz)
                        && world.canMineBlock(placement.player, bx, y, bz) && world.canMineBlock(placement.player, bx, y + 1, bz)
                        && placement.player.canPlayerEdit(bx, y, bz, 1, placement.itemInHand)
                        && placement.player.canPlayerEdit(bx, y + 1, bz, 1, placement.itemInHand);
                }
                if(!matches || !world.canMineBlock(placement.player, x, y - 1, z)
                    || !placement.player.canPlayerEdit(x, y - 1, z, 1, placement.itemInHand)) continue;
                if(spawnInitial(world, x + 0.5D, y + 2D, z + 0.5D, axis * 90F, false)) consume(world, x, y, z, dx, dz);
                return;
            }
        }
    }
    private static boolean isWitherSkull(World world, int x, int y, int z) {
        if(world.getBlock(x, y, z) != Blocks.skull) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntitySkull && ((TileEntitySkull)tile).func_145904_a() == 1;
    }
    static boolean spawnInitial(World world, double x, double y, double z, float yaw, boolean challenge) {
        EntityWitherStorm boss = new EntityWitherStorm(world);
        boss.setLocationAndAngles(x, y, z, yaw, 0);
        if(challenge) boss.startPursuit();
        if(!world.spawnEntityInWorld(boss)) { boss.setDead(); return false; }
        if(challenge) boss.startFlightTicket();
        boss.playSound("hbm:entity.witherStorm.transform", 2F, 1F);
        return true;
    }
    private static void consume(World world, int x, int y, int z, int dx, int dz) {
        WitherStormTerrain.beginSuppression(world);
        try {
            for(int i = -1; i <= 1; i++) {
                int bx = x + i * dx, bz = z + i * dz;
                world.setBlockMetadataWithNotify(bx, y + 1, bz, 8, 2);
                world.setBlock(bx, y + 1, bz, Blocks.air, 0, 2);
                world.setBlock(bx, y, bz, Blocks.air, 0, 2);
            }
            world.setBlock(x, y - 1, z, Blocks.air, 0, 2);
            for(int i = -1; i <= 1; i++) {
                world.notifyBlockChange(x + i * dx, y + 1, z + i * dz, Blocks.air);
                world.notifyBlockChange(x + i * dx, y, z + i * dz, Blocks.air);
            }
            world.notifyBlockChange(x, y - 1, z, Blocks.air);
        } finally { WitherStormTerrain.endSuppression(); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void summon(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack held = player.getHeldItem();
        if(event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !WitherStormConfig.survivalSummon || !player.isSneaking() || held == null || held.getItem() != Items.nether_star) return;
        World world = player.worldObj;
        int x = event.x, y = event.y, z = event.z;
        if(!WitherStormMath.areaLoaded(world, x, z, 2) || world.getBlock(x, y, z) != ModBlocks.block_euphemium) return;
        for(int axis = 0; axis < 2; axis++) {
            boolean valid = world.getBlock(x, y - 1, z) == Blocks.soul_sand;
            for(int i = -1; i <= 1 && valid; i++) {
                int bx = x + (axis == 0 ? i : 0), bz = z + (axis == 1 ? i : 0);
                valid = world.getBlock(bx, y, bz) == (i == 0 ? ModBlocks.block_euphemium : Blocks.soul_sand)
                    && isWitherSkull(world, bx, y + 1, bz)
                    && player.canPlayerEdit(bx, y, bz, event.face, held) && player.canPlayerEdit(bx, y + 1, bz, event.face, held);
            }
            if(!valid || !player.canPlayerEdit(x, y - 1, z, event.face, held)) continue;
            event.setCanceled(true);
            if(world.isRemote || !WitherStormEncounters.get(world).room()) return;
            if(!spawnInitial(world, x + 0.5D, y + 3D, z + 0.5D, player.rotationYaw, false)) return;
            consume(world, x, y, z, 1 - axis, axis);
            if(!player.capabilities.isCreativeMode && --held.stackSize == 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            return;
        }
    }
}
