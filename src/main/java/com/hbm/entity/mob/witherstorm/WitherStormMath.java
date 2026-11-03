package com.hbm.entity.mob.witherstorm;

import java.util.List;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class WitherStormMath {
    private WitherStormMath() { }
    public static boolean loaded(World world, double x, double z) {
        return world.getChunkProvider().chunkExists(MathHelper.floor_double(x) >> 4, MathHelper.floor_double(z) >> 4);
    }
    public static boolean areaLoaded(World world, double x, double z, double radius) {
        int x0 = MathHelper.floor_double(x - radius) >> 4, x1 = MathHelper.floor_double(x + radius) >> 4;
        int z0 = MathHelper.floor_double(z - radius) >> 4, z1 = MathHelper.floor_double(z + radius) >> 4;
        for(int cx = x0; cx <= x1; cx++) for(int cz = z0; cz <= z1; cz++)
            if(!world.getChunkProvider().chunkExists(cx, cz)) return false;
        return true;
    }
    public static boolean edible(Entity e, World world) {
        if(e == null || e.isDead || e.worldObj != world || e instanceof EntityWitherStorm || e instanceof EntityWitherStormPart || e instanceof EntityWitherStormSkull) return false;
        if(!loaded(world, e.posX, e.posZ)) return false;
        if(e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer)e;
            if(p.capabilities.disableDamage || p.capabilities.isCreativeMode || !p.isEntityAlive()) return false;
            if(p instanceof EntityPlayerMP && ((EntityPlayerMP)p).playerNetServerHandler.netManager.isChannelOpen() == false) return false;
        }
        return e instanceof EntityLivingBase ? ((EntityLivingBase)e).isEntityAlive() : e instanceof EntityItem || e instanceof EntityFallingBlock;
    }
    public static double finite(double n) { return Double.isNaN(n) || Double.isInfinite(n) ? 0D : n; }
    public static double limit(double n, double max) { return Math.max(-max, Math.min(max, finite(n))); }
    public static void pull(Entity e, double x, double y, double z, double acceleration) {
        double dx = x - e.posX, dy = y - (e.posY + e.height * 0.5D), dz = z - e.posZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if(length < 0.001D || !Double.isFinite(length)) return;
        e.motionX = limit(e.motionX * 0.94D + dx / length * acceleration, 0.8D);
        // Native living/item ticks apply gravity after this callback. Compensate
        // while lifting, otherwise early-phase beams cannot raise grounded mobs.
        double gravity = dy > 0.1D ? (e instanceof EntityLivingBase ? 0.08D : 0.04D) : 0D;
        e.motionY = limit(e.motionY * 0.94D + dy / length * acceleration + gravity, 0.8D);
        e.motionZ = limit(e.motionZ * 0.94D + dz / length * acceleration, 0.8D);
        e.fallDistance = 0;
        e.velocityChanged = true;
    }
    public static UUID uuid(NBTTagCompound tag, String key) {
        if(!tag.hasKey(key)) return null;
        try { return UUID.fromString(tag.getString(key)); } catch(IllegalArgumentException invalid) { return null; }
    }
    public static void uuid(NBTTagCompound tag, String key, UUID id) { if(id != null) tag.setString(key, id.toString()); }

    /** Bounded section walk, including rejected entries. Never asks World for an unbounded result list. */
    public static void nearby(Entity center, double radius, List<Entity> result, int offset) {
        result.clear();
        int cx = MathHelper.floor_double(center.posX) >> 4, cz = MathHelper.floor_double(center.posZ) >> 4;
        int reach = MathHelper.ceiling_double_int(radius / 16D), side = reach * 2 + 1;
        int inspected = 0;
        for(int i = 0; i < side * side; i++) {
            int index = Math.floorMod(i + offset, side * side);
            int x = cx + index % side - reach, z = cz + index / side - reach;
            if(!center.worldObj.getChunkProvider().chunkExists(x, z)) continue;
            Chunk chunk = center.worldObj.getChunkProvider().provideChunk(x, z);
            for(int section = 0; section < 16; section++) {
                List entries = chunk.entityLists[(section + (offset & 15)) & 15];
                for(int j = 0; j < entries.size(); j++) {
                    if(++inspected > WitherStormConfig.queryBudget) return;
                    Entity e = (Entity)entries.get((j + Math.floorMod(offset, Math.max(1, entries.size()))) % entries.size());
                    if(edible(e, center.worldObj) && center.getDistanceSqToEntity(e) <= radius * radius) {
                        result.add(e);
                        if(result.size() == 64) return;
                    }
                }
            }
        }
    }
}
