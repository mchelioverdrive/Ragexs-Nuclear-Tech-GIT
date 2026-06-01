package com.hbm.blocks.machine;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.hbm.blocks.ModBlocks;
import com.hbm.tileentity.machine.TileEntityMoltenSaltReactorPort;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class MachineMoltenSaltReactorPort extends BlockContainer implements ITooltipProvider {

	private final boolean input;

	public MachineMoltenSaltReactorPort(Material mat, boolean input) {
		super(mat);
		this.input = input;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityMoltenSaltReactorPort(input);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		if(this == ModBlocks.machine_msr_input) {
			list.add(EnumChatFormatting.YELLOW + "Shielding-compatible MSR inlet.");
			list.add(EnumChatFormatting.YELLOW + "Place touching the reactor and pipe liquid thorium salt into it.");
		} else {
			list.add(EnumChatFormatting.YELLOW + "Shielding-compatible MSR outlet.");
			list.add(EnumChatFormatting.YELLOW + "Place touching the reactor and pipe hot thorium salt out of it.");
		}
	}
}
