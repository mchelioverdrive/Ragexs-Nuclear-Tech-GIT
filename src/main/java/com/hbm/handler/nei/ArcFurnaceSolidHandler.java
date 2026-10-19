package com.hbm.handler.nei;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;

public class ArcFurnaceSolidHandler extends NEIUniversalHandler {

	public ArcFurnaceSolidHandler() {
		super(ModBlocks.machine_arc_furnace.getLocalizedName(), ModBlocks.machine_arc_furnace, steelRecipes());
        guiGui.add(com.hbm.inventory.gui.GUIMachineArcFurnaceLarge.class);
	}

    private static java.util.HashMap steelRecipes() {
        java.util.HashMap recipes = ArcFurnaceRecipes.getSolidRecipes();
        Object[] feeds = {new net.minecraft.item.ItemStack(com.hbm.items.ModItems.iron_dri, com.hbm.realsim.ArcSteelBatch.DRI),
                new net.minecraft.item.ItemStack(com.hbm.items.ModItems.coke, 1, com.hbm.items.ItemEnums.EnumCokeType.COAL.ordinal()),
                new net.minecraft.item.ItemStack(com.hbm.items.ModItems.quicklime)};
        recipes.put(feeds, new net.minecraft.item.ItemStack[] {
                new net.minecraft.item.ItemStack(com.hbm.items.ModItems.ingot_steel, com.hbm.realsim.ArcSteelBatch.STEEL),
                new net.minecraft.item.ItemStack(com.hbm.items.ModItems.slagingot, com.hbm.realsim.ArcSteelBatch.SLAG)});
        return recipes;
    }

	@Override
	public String getKey() {
		return "ntmArcFurnaceSolid";
	}
}
