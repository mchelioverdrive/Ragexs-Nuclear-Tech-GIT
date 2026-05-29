package com.hbm.blocks.fluid;

import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockSolarPlasma extends Block {

	public BlockSolarPlasma() {
		super(Material.air);

		this.setBlockName("solar_plasma");
		this.setLightLevel(1.0F);
		this.setHardness(-1.0F);
		this.setResistance(6000000.0F);
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
		// do nothing (prevents cascade updates)
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		// do nothing (no flow, no logic)
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerBlockIcons(IIconRegister reg) {
		this.blockIcon = Blocks.lava.getIcon(0, 0);
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}
	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
	}
	@Override
	public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {

		// lava-like drag
		entity.motionX *= 0.5;
		entity.motionZ *= 0.5;

		// buoyancy (hot plasma rises)
		entity.motionY += 0.03;

		// burn
		entity.setFire(10);

		if(entity instanceof EntityLivingBase) {
			((EntityLivingBase) entity).attackEntityFrom(
				DamageSource.inFire,
				6.0F
			);
		}
	}


	//@Override
		//public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
		//	return false;
		//}


	//@Override
	//public boolean isBlockNormalCube() {
	//	return false;
	//}
	//@Override
	//public boolean getBlocksMovement(IBlockAccess world, int x, int y, int z) {
	//	return false;
	//}
}
