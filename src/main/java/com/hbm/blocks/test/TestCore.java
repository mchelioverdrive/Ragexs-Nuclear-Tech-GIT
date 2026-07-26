package com.hbm.blocks.test;

import com.hbm.explosion.nuclear.NuclearDetonationFactory;
import com.hbm.explosion.nuclear.NuclearDetonationOptions;

import java.util.Random;

import com.hbm.config.BombConfig;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public class TestCore extends Block {

	public TestCore(Material mat) {
		super(mat);
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		
		if(!world.isRemote) {
			
			int meta = world.getBlockMetadata(x, y, z);
			
			if(meta >= 6) {

				world.setBlockToAir(x, y, z);
				NuclearDetonationFactory.detonate(world, x + 0.5, y + 0.5, z + 0.5, (int)(BombConfig.missileRadius), NuclearDetonationOptions.standard());
				
			} else if(meta > 0) {
				
				world.newExplosion(null, x + 0.5, y + 0.5, z + 0.5, 5.0F, false, true);
			}
		}
	}
}
