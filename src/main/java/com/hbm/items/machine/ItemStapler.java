package com.hbm.items.machine;

import com.hbm.inventory.container.ContainerStapler;
import com.hbm.inventory.gui.GUIStapler;
import com.hbm.items.ModItems;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.IGUIProvider;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemStapler extends Item implements IGUIProvider {

	public static final int GUI_ID = 421;

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if(!world.isRemote) player.openGui(MainRegistry.instance, GUI_ID, world, 0, 0, 0);
		return stack;
	}

	@Override
	public Container provideContainer(int id, EntityPlayer player, World world, int x, int y, int z) {
		return id == GUI_ID ? new ContainerStapler(player, this == ModItems.stapler) : null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int id, EntityPlayer player, World world, int x, int y, int z) {
		return id == GUI_ID ? new GUIStapler(new ContainerStapler(player, this == ModItems.stapler)) : null;
	}
}
