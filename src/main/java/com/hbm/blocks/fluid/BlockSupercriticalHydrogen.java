package com.hbm.blocks.fluid;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockSupercriticalHydrogen extends Block {

	@SideOnly(Side.CLIENT)
	protected IIcon icon;

	public BlockSupercriticalHydrogen() {

		super(Material.water);

		this.setBlockName(
			"supercritical_hydrogen"
		);

		this.setHardness(100F);
		this.setResistance(999999F);
		this.setLightOpacity(1);
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(
		World world,
		int x,
		int y,
		int z
	) {
		return null;
	}

	@Override
	public void onEntityCollidedWithBlock(
		World world,
		int x,
		int y,
		int z,
		Entity entity
	) {

		// movement nearly impossible
		entity.motionX *= 0.15D;
		entity.motionY *= 0.15D;
		entity.motionZ *= 0.15D;

		entity.fallDistance = 0F;

		entity.setFire(1);

		if(entity instanceof EntityLivingBase) {

			((EntityLivingBase) entity)
				.attackEntityFrom(
					DamageSource.drown,
					4.0F
				);
		}
	}

	@Override
	public boolean canCollideCheck(
		int meta,
		boolean hitIfLiquid
	) {
		return false;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerBlockIcons(
		IIconRegister reg
	) {
		icon = reg.registerIcon(
			"lava_still"
		);
	}

	@Override
	public IIcon getIcon(
		int side,
		int meta
	) {
		return icon;
	}
}
