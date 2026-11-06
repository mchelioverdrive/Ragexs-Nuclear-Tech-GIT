package com.hbm.world.gemini;

import com.hbm.blocks.ModBlocks;
import com.hbm.entity.mob.gemini.EntityNaturesMockery;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

public final class MockeryGrowth {
    private MockeryGrowth() { }
    public static boolean suitableGround(Block block) {
        return block == Blocks.dirt || block == Blocks.grass || block == Blocks.sand || block == Blocks.stone
                || block == ModBlocks.gardener_flesh;
    }

    /** Exactly one candidate and at most one placement per call; never replace even replaceable plants. */
    public static boolean place(EntityNaturesMockery mob, int x, int y, int z) {
        World world = mob.worldObj;
        if(!MockerySpawning.active(world) || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || y < 1 || y > 254 || !mob.withinColony(x + 0.5D, y, z + 0.5D)
                || !world.blockExists(x, y, z) || world.getBlock(x, y, z) != Blocks.air
                || world.getTileEntity(x, y, z) != null || world.getTileEntity(x, y - 1, z) != null) return false;
        Block ground = world.getBlock(x, y - 1, z);
        int meta = world.getBlockMetadata(x, y - 1, z);
        if(!suitableGround(ground) || ground.hasTileEntity(meta) || ground.getBlockHardness(world, x, y - 1, z) < 0
                || !ground.canEntityDestroy(world, x, y - 1, z, mob)) return false;
        net.minecraft.entity.player.EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        if(!world.canMineBlock(actor, x, y, z) || !world.canMineBlock(actor, x, y - 1, z)
                || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y - 1, z, world, ground, meta, actor))) return false;
        // Recheck after protection callbacks, which may themselves modify the world.
        if(!MockerySpawning.active(world) || world.getBlock(x, y, z) != Blocks.air
                || world.getBlock(x, y - 1, z) != ground || world.getBlockMetadata(x, y - 1, z) != meta
                || world.getTileEntity(x, y, z) != null || world.getTileEntity(x, y - 1, z) != null) return false;
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if(!world.setBlock(x, y, z, ModBlocks.mockery_growth, 0, 0)) return false;
        if(ForgeEventFactory.onPlayerBlockPlace(actor, snapshot, ForgeDirection.UP).isCanceled()) {
            snapshot.restore(true, false);
            return false;
        }
        world.markAndNotifyBlock(x, y, z, world.getChunkFromBlockCoords(x, z), Blocks.air, ModBlocks.mockery_growth, 3);
        return true;
    }
}
