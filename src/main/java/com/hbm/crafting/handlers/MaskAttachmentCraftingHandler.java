package com.hbm.crafting.handlers;

import static com.hbm.inventory.OreDictManager.STEEL;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;

/** Mounts a finished mask without discarding its installed filter or other saved data. */
public class MaskAttachmentCraftingHandler extends ShapedOreRecipe {

	private final Item mask;

	public MaskAttachmentCraftingHandler(Item attachment, Item mask) {
		super(new ItemStack(attachment), "LML", " B ", 'L', Items.leather, 'M', mask, 'B', STEEL.bolt());
		this.mask = mask;
	}

	@Override
	public ItemStack getCraftingResult(InventoryCrafting inventory) {
		ItemStack result = super.getCraftingResult(inventory);
		for(int i = 0; i < inventory.getSizeInventory(); i++) {
			ItemStack source = inventory.getStackInSlot(i);
			if(source != null && source.getItem() == mask && source.hasTagCompound()) {
				result.setTagCompound((NBTTagCompound) source.getTagCompound().copy());
				break;
			}
		}
		return result;
	}
}
