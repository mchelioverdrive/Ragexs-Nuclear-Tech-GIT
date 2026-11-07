package com.hbm.blocks.generic;

import java.util.Random;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.MockeryGrowth;
import com.hbm.world.gemini.MockerySpawning;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Inert offshoot: no random spreading, tile entity, scheduled producer or entity spawning. */
public final class BlockMockeryGrowth extends BlockBush {
    public BlockMockeryGrowth() {
        setTickRandomly(false);
        setBlockBounds(0.15F, 0, 0.15F, 0.85F, 0.65F, 0.85F);
        setHardness(0.3F);
        setStepSound(soundTypeGravel);
    }
    @Override protected boolean canPlaceBlockOn(Block block) { return MockeryGrowth.suitableGround(block); }
    @Override public boolean canBlockStay(World world, int x, int y, int z) { return canPlaceBlockOn(world.getBlock(x, y - 1, z)); }
    @Override public int quantityDropped(Random random) { return 0; }
    @Override public int getBlockColor() { return 0xAD4945; }
    @Override public int colorMultiplier(IBlockAccess world, int x, int y, int z) { return getBlockColor(); }
    @Override public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        if(!world.isRemote && entity instanceof EntityLivingBase && entity.ticksExisted % 20 == 0 && MockerySpawning.active(world))
            GeminiInfections.exposeContact((EntityLivingBase) entity, "mockery_growth", "natures_mockery");
    }
}
