package com.hbm.inventory;

import com.hbm.inventory.fluid.FluidType;

import net.minecraft.item.ItemStack;

public class FluidContainer {
	
	//The full container (e.g. deuterium cell)
	public ItemStack fullContainer;
	//Them empty container (e.g. empty cell)
	public ItemStack emptyContainer;
	//The type of the contained liquid (e.g. deuterium)
	public FluidType type;
	// Current fill nameplate, in the fluid's declared quantity basis.
	public int content;
	public final int legacyContent;
	
	public FluidContainer(ItemStack full, ItemStack empty, FluidType type, int amount) {
		fullContainer = full;
		emptyContainer = empty;
		this.type = type;
		legacyContent = amount;
		content = com.hbm.inventory.FluidContainerRegistry.normalizedCapacity(full.getItem(), type, amount);
		if(content != legacyContent) {
			if(!full.hasTagCompound()) full.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
			full.getTagCompound().setInteger(FluidContainerRegistry.AMOUNT_KEY, content);
		}
	}

	public int amount(ItemStack stack) {
		return stack.hasTagCompound() && stack.getTagCompound().hasKey(FluidContainerRegistry.AMOUNT_KEY)
				? Math.max(0, stack.getTagCompound().getInteger(FluidContainerRegistry.AMOUNT_KEY)) : legacyContent;
	}

}
