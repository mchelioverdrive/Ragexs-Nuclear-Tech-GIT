package com.hbm.blocks.machine;

import com.hbm.tileentity.machine.TileEntityMachineMiniRTG;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class MachineMiniRTG extends BlockContainer {

	public MachineMiniRTG(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
		return new TileEntityMachineMiniRTG();
	}

	@Override
	public int getRenderType() {
		return MachineRTG.renderID;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}


    @Override public void onBlockPlacedBy(World world, int x, int y, int z, net.minecraft.entity.EntityLivingBase entity, net.minecraft.item.ItemStack stack) {
        com.hbm.tileentity.IPersistentNBT.restoreData(world, x, y, z, stack);
    }
    @Override public java.util.ArrayList<net.minecraft.item.ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return com.hbm.tileentity.IPersistentNBT.getDrops(world, x, y, z, this);
    }
    @Override public void onBlockHarvested(World world, int x, int y, int z, int meta, net.minecraft.entity.player.EntityPlayer player) {
        if(!world.isRemote && !player.capabilities.isCreativeMode) {
            harvesters.set(player); dropBlockAsItem(world, x, y, z, meta, 0); harvesters.set(null);
        }
    }
    @Override public void harvestBlock(World world, net.minecraft.entity.player.EntityPlayer player, int x, int y, int z, int meta) { }
}
