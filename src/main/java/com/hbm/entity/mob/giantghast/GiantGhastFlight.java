package com.hbm.entity.mob.giantghast;

import java.lang.reflect.Field;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

final class GiantGhastFlight {
    private GiantGhastFlight() { }

    static boolean loaded(World world, AxisAlignedBB box) {
        return world.checkChunksExist(MathHelper.floor_double(box.minX), MathHelper.floor_double(box.minY),
            MathHelper.floor_double(box.minZ), MathHelper.floor_double(box.maxX), MathHelper.floor_double(box.maxY),
            MathHelper.floor_double(box.maxZ));
    }

    static boolean clear(World world, AxisAlignedBB box) {
        return box.minY >= 1 && box.maxY < world.getActualHeight() && loaded(world, box)
            && world.func_147461_a(box).isEmpty();
    }

    static double availableDrop(World world, AxisAlignedBB body, double distance) {
        double drop = Math.min(distance, Math.max(0, body.minY - 1));
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(body.minX, body.minY - drop, body.minZ,
            body.maxX, body.minY + 0.01, body.maxZ);
        if (!loaded(world, probe)) return -1;
        double offset = -drop;
        for (Object object : world.func_147461_a(probe)) {
            offset = ((AxisAlignedBB)object).calculateYOffset(body, offset);
        }
        return -offset;
    }

    static Vec3 center(Entity entity) {
        return Vec3.createVectorHelper(entity.posX, entity.boundingBox.minY + entity.height * 0.5, entity.posZ);
    }

    static boolean visible(World world, Vec3 from, Vec3 to) {
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(Math.min(from.xCoord, to.xCoord), Math.min(from.yCoord, to.yCoord),
            Math.min(from.zCoord, to.zCoord), Math.max(from.xCoord, to.xCoord), Math.max(from.yCoord, to.yCoord),
            Math.max(from.zCoord, to.zCoord));
        return loaded(world, area) && traceBlocks(world, from, to) == null;
    }

    static MovingObjectPosition traceBlocks(World world, Vec3 from, Vec3 to) {
        // The 1.7.10 block ray march mutates its start vector.
        return world.rayTraceBlocks(Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord), to);
    }

    static boolean combatant(EntityLivingBase entity, World world) {
        return entity != null && entity.worldObj == world && entity.isEntityAlive()
            && (!(entity instanceof EntityPlayer) || !((EntityPlayer)entity).capabilities.disableDamage);
    }

    static double approach(double current, double wanted, double rate) {
        return current + MathHelper.clamp_double(wanted - current, -rate, rate);
    }

    static void carryPlayers(Entity platform, AxisAlignedBB oldBox, double dx, double dy, double dz) {
        if (Math.abs(dy) > 0.55) return;
        boolean moved = dx * dx + dy * dy + dz * dz >= 0.000001;
        AxisAlignedBB surface = AxisAlignedBB.getBoundingBox(oldBox.minX, oldBox.maxY - 0.09, oldBox.minZ,
            oldBox.maxX, oldBox.maxY + 0.20, oldBox.maxZ);
        for (Object object : platform.worldObj.getEntitiesWithinAABB(EntityPlayer.class, surface)) {
            EntityPlayer player = (EntityPlayer)object;
            if (player.ridingEntity != null || player.capabilities.isFlying || player.motionY > 0.05
                    || Math.abs(player.boundingBox.minY - oldBox.maxY) > 0.09) continue;
            if (player instanceof EntityPlayerMP) SupportedPlayer.resetFloating((EntityPlayerMP)player);
            if (moved) player.moveEntity(dx, dy, dz);
        }
    }

    static void supportPlayers(Entity platform, Entity[] surfaces, double lowestRoof) {
        AxisAlignedBB box = platform.boundingBox;
        AxisAlignedBB above = AxisAlignedBB.getBoundingBox(box.minX, lowestRoof - 0.1, box.minZ,
            box.maxX, box.maxY + 0.25, box.maxZ);
        for (Object object : platform.worldObj.getEntitiesWithinAABB(EntityPlayerMP.class, above)) {
            EntityPlayerMP player = (EntityPlayerMP)object;
            if (player.ridingEntity != null || player.capabilities.isFlying || player.motionY > 0.05) continue;
            AxisAlignedBB feet = player.boundingBox;
            for (Entity surface : surfaces) {
                AxisAlignedBB roof = surface.boundingBox;
                if (Math.abs(feet.minY - roof.maxY) < 0.10 && feet.maxX > roof.minX && feet.minX < roof.maxX
                        && feet.maxZ > roof.minZ && feet.minZ < roof.maxZ) {
                    SupportedPlayer.resetFloating(player);
                    break;
                }
            }
        }
    }

    private static final class SupportedPlayer {
        // Vanilla's flight check recognizes blocks only. Forgive the counter while on this entity surface.
        private static final Field FLOATING = ReflectionHelper.findField(NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");

        static void resetFloating(EntityPlayerMP player) {
            if (player.playerNetServerHandler == null) return;
            try {
                FLOATING.setInt(player.playerNetServerHandler, 0);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Cannot reset the flight counter for a Ghast rider", ex);
            }
        }
    }
}
