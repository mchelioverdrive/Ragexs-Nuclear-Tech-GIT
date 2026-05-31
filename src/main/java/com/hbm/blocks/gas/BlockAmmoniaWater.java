package com.hbm.blocks.gas;

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
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAmmoniaWater extends Block {

	@SideOnly(Side.CLIENT)
	private IIcon icon;

	public BlockAmmoniaWater() {

		super(Material.water);

		this.setBlockName(
			"ammoniaWater"
		);

		this.setHardness(-1.0F);
		this.setResistance(6000000F);

		// slightly darker / denser looking
		this.setLightOpacity(3);

		// no fluid updates
		this.setTickRandomly(false);
	}

	@Override
	public int getRenderType() {
		return 4; // vanilla liquid renderer
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
	public int getRenderBlockPass() {
		return 1;
	}

	@Override
	public void onEntityCollidedWithBlock(
		World world,
		int x,
		int y,
		int z,
		Entity entity
	) {

		// Dense slushy mantle feel

		// heavy horizontal resistance
		entity.motionX *= 0.88D;
		entity.motionZ *= 0.88D;

		// vertical resistance
		entity.motionY *= 0.92D;

		// gentle sinking
		entity.motionY -= 0.02D;

		// convection / turbulence
		entity.motionY +=
			(world.rand.nextDouble() - 0.5D)
				* 0.015D;

		// pressure damage if submerged
		if(
			entity instanceof EntityLivingBase
				&& world.rand.nextInt(60) == 0
		) {

			entity.attackEntityFrom(
				DamageSource.generic,
				1.0F
			);
		}
	}

	@Override
	public boolean shouldSideBeRendered(
		IBlockAccess world,
		int x,
		int y,
		int z,
		int side
	) {

		Block adjacent =
			world.getBlock(x, y, z);

		return adjacent != this;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(
		IIconRegister reg
	) {

		// temporary texture
		this.icon =
			reg.registerIcon(
				"minecraft:water_still"
			);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(
		int side,
		int meta
	) {
		return this.icon;
	}

	@Override
	public boolean isCollidable() {
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
}
