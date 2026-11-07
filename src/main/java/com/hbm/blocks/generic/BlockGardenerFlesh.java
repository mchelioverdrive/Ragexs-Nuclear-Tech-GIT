package com.hbm.blocks.generic;

import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/** Stable nest tissue; unlike melted flesh it has no melting or autonomous spread. */
public final class BlockGardenerFlesh extends Block {
    public BlockGardenerFlesh() { super(Material.clay); setHardness(1.2F); setResistance(3F); setStepSound(soundTypeGravel); }
    @Override public void onEntityWalking(World world, int x, int y, int z, Entity entity) {
        if(!world.isRemote && entity instanceof EntityLivingBase && entity.ticksExisted % 20 == 0 && GardenerNests.active(world))
            GeminiInfections.exposeContact((EntityLivingBase) entity, "gardener_nest", "gardener");
    }
}
