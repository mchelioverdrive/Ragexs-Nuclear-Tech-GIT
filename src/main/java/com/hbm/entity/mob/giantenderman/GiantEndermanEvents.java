package com.hbm.entity.mob.giantenderman;

import java.util.List;

import com.hbm.config.GiantEndermanConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class GiantEndermanEvents {
    static final String FALL_GRACE = "RNTGiantEndermanFallGrace";

    public static void registerEgg() {
        Item egg = new Item() {
            @Override public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                                              int side, float hitX, float hitY, float hitZ) {
                if (!player.capabilities.isCreativeMode) return false;
                if (world.isRemote) return true;
                EntityGiantEnderman boss = new EntityGiantEnderman(world);
                if (boss.safePosition(x + 0.5, y + 1, z + 0.5)) {
                    boss.setLocationAndAngles(x + 0.5, y + 1, z + 0.5, player.rotationYaw, 0);
                    if (world.spawnEntityInWorld(boss)) return true;
                }
                player.addChatMessage(new ChatComponentTranslation("giant_enderman.spawn.unsafe"));
                return false;
            }
        }.setUnlocalizedName("giant_enderman_spawn_egg").setTextureName("hbm:giant_enderman_spawn_egg").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerItem(egg, "giant_enderman_spawn_egg");
    }

    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        for (List<?> slice : event.getChunk().entityLists) {
            for (Object entity : slice) {
                if (entity instanceof EntityGiantEnderman) ((EntityGiantEnderman)entity).unload();
                if (entity instanceof EntityGiantEndermanPart) ((EntityGiantEndermanPart)entity).setDead();
                if (entity instanceof EntityEndermanTerrain) ((EntityEndermanTerrain)entity).setDead();
            }
        }
    }

    @SubscribeEvent public void worldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        for (Object entity : event.world.loadedEntityList) {
            if (entity instanceof EntityGiantEnderman) ((EntityGiantEnderman)entity).unload();
        }
    }

    @SubscribeEvent public void fall(LivingFallEvent event) {
        if (event.entityLiving.getEntityData().getInteger(FALL_GRACE) > 0) event.setCanceled(true);
    }

    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote) return;
        int grace = event.player.getEntityData().getInteger(FALL_GRACE);
        if (grace <= 0) return;
        event.player.fallDistance = 0;
        event.player.getEntityData().setInteger(FALL_GRACE, grace - 1);
    }

    @SubscribeEvent public void naturalSpawn(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (event.phase != TickEvent.Phase.END || world.isRemote || world.provider.dimensionId != 1 || !GiantEndermanConfig.natural
            || world.getTotalWorldTime() % 12000 != 0 || world.difficultySetting == EnumDifficulty.PEACEFUL || world.playerEntities.isEmpty()) return;
        EntityPlayer player = (EntityPlayer)world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
        if (!player.isEntityAlive() || player.capabilities.disableDamage) return;
        if (!world.getEntitiesWithinAABB(EntityGiantEnderman.class, player.boundingBox.expand(112, 128, 112)).isEmpty()) return;
        EntityGiantEnderman boss = new EntityGiantEnderman(world);
        double angle = world.rand.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 4; i++, angle += Math.PI * 0.5) {
            double x = player.posX + Math.cos(angle) * 48, z = player.posZ + Math.sin(angle) * 48;
            Vec3 point = boss.findGround(x, z, (int)player.posY + 16, 42);
            if (point == null) continue;
            boss.setLocationAndAngles(point.xCoord, point.yCoord, point.zCoord, 0, 0);
            world.spawnEntityInWorld(boss);
            break;
        }
    }
}
