package com.hbm.blocks.generic;

import com.hbm.tileentity.deco.TileEntityLargeShredderDummy;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A single anchored decorative prop; no machine or multiblock behavior. */
public class BlockLargeShredderDummy extends BlockContainer {

	public BlockLargeShredderDummy() {
		super(Material.iron);
		setBlockBounds(-1.265F, 0F, -2F, 2.265F, 4.225F, 3F);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int metadata) {
		return new TileEntityLargeShredderDummy();
	}

	@Override public int getRenderType() { return -1; }
	@Override public boolean isOpaqueCube() { return false; }
	@Override public boolean renderAsNormalBlock() { return false; }
	@Override public int damageDropped(int metadata) { return 0; }

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int facing = MathHelper.floor_double(player.rotationYaw * 4F / 360F + 0.5D) & 3;
		int metadata = facing == 0 ? 3 : facing == 1 ? 4 : facing == 2 ? 2 : 5;
		world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		int facing = world.getBlockMetadata(x, y, z);
		// Conservative envelope of the X3030 body, centered on the anchor.
		if(facing == 4 || facing == 5) {
			setBlockBounds(-2F, 0F, -1.265F, 3F, 4.225F, 2.265F);
		} else {
			setBlockBounds(-1.265F, 0F, -2F, 2.265F, 4.225F, 3F);
		}
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		setBlockBoundsBasedOnState(world, x, y, z);
		return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
	}
}
