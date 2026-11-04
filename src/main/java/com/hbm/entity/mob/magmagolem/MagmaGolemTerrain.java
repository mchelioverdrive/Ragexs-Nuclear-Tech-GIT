package com.hbm.entity.mob.magmagolem;

import com.hbm.config.MagmaGolemConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class MagmaGolemTerrain {
    private MagmaGolemTerrain() { }

    static boolean loaded(World world, AxisAlignedBB box) {
        return world.checkChunksExist(floor(box.minX), 0, floor(box.minZ), floor(box.maxX), 255, floor(box.maxZ));
    }

    static boolean visible(World world, Vec3 from, Vec3 to) {
        return loaded(world, AxisAlignedBB.getBoundingBox(Math.min(from.xCoord, to.xCoord), 0, Math.min(from.zCoord, to.zCoord),
            Math.max(from.xCoord, to.xCoord), 1, Math.max(from.zCoord, to.zCoord))) && world.rayTraceBlocks(from, to) == null;
    }

    static boolean wet(World world, AxisAlignedBB box) {
        for (int x = floor(box.minX); x <= floor(box.maxX); x++) {
            for (int z = floor(box.minZ); z <= floor(box.maxZ); z++) {
                if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
                for (int y = Math.max(0, floor(box.minY)); y <= Math.min(255, floor(box.maxY)); y++) {
                    Block block = world.getBlock(x, y, z);
                    if (block.getMaterial() != Material.water) continue;
                    double surface = block instanceof BlockLiquid
                        ? y + 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(x, y, z)) : y + 1;
                    if (world.getBlock(x, y + 1, z).getMaterial() == Material.water) surface = y + 1;
                    if (x + 1 > box.minX && x < box.maxX && z + 1 > box.minZ && z < box.maxZ
                            && y < box.maxY && surface > box.minY) return true;
                }
            }
        }
        return false;
    }

    static boolean griefing(World world) {
        return world instanceof WorldServer && MagmaGolemConfig.griefing
            && world.getGameRules().getGameRuleBooleanValue("mobGriefing");
    }

    /** Process only a local contact volume and cap both inspections and edits. */
    static void strike(EntityGiantMagmaGolem boss, AxisAlignedBB box, int budget, boolean fire) {
        World world = boss.worldObj;
        if (!griefing(world) || !loaded(world, box)) return;
        int inspected = 0;
        for (int y = Math.max(1, floor(box.minY)); y <= Math.min(254, floor(box.maxY)); y++) {
            for (int x = floor(box.minX); x <= floor(box.maxX); x++) {
                for (int z = floor(box.minZ); z <= floor(box.maxZ); z++) {
                    if (budget == 0 || ++inspected > 128) return;
                    Block block = world.getBlock(x, y, z);
                    int meta = world.getBlockMetadata(x, y, z);
                    float hardness = block.getBlockHardness(world, x, y, z);
                    if (block.getMaterial().blocksMovement() && !block.hasTileEntity(meta) && hardness >= 0 && hardness <= 4
                            && block.canEntityDestroy(world, x, y, z, boss)
                            && !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta,
                                FakePlayerFactory.getMinecraft((WorldServer)world)))
                            && griefing(world) && world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == meta
                            && world.func_147480_a(x, y, z, false)) budget--;
                    if (fire && budget > 0 && world.rand.nextInt(5) == 0 && ignite(world, x, y + 1, z)) budget--;
                }
            }
        }
    }

    static boolean ignite(World world, int x, int y, int z) {
        if (!griefing(world) || !world.blockExists(x, y, z) || !world.isAirBlock(x, y, z)
                || !Blocks.fire.canPlaceBlockAt(world, x, y, z)) return false;
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if (!world.setBlock(x, y, z, Blocks.fire, 0, 0)) return false;
        if (ForgeEventFactory.onPlayerBlockPlace(FakePlayerFactory.getMinecraft((WorldServer)world), snapshot, ForgeDirection.UP).isCanceled()) {
            snapshot.restore(true, false);
            return false;
        }
        world.markAndNotifyBlock(x, y, z, world.getChunkFromBlockCoords(x, z), snapshot.replacedBlock, Blocks.fire, 3);
        return true;
    }

    private static int floor(double value) { return MathHelper.floor_double(value); }

    public static final class Explosions {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onTnt(ExplosionEvent.Detonate event) {
            Explosion explosion = event.explosion;
            if (event.world.isRemote || !(explosion.exploder instanceof EntityTNTPrimed)) return;
            for (Entity entity : event.getAffectedEntities()) {
                if (entity instanceof EntityGiantMagmaGolem) ((EntityGiantMagmaGolem)entity).onFootBlast(explosion);
            }
        }
    }
}
