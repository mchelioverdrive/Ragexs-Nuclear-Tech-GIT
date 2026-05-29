package com.hbm.blocks.fluid;

import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class BlockSolarPlasma extends Block {

	public BlockSolarPlasma() {
		super(Material.rock); // important: NOT liquid material
		this.setLightLevel(1.0F);
		this.setHardness(100.0F);
		this.setResistance(100.0F);
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
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}
}
