package com.hbm.tileentity.deco;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/** Persistent render host only; orientation belongs to block metadata. */
public class TileEntityLargeShredderDummy extends TileEntity {

	@Override
	public boolean canUpdate() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return getBlockType().getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
	}
}
