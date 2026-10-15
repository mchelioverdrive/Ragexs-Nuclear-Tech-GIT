package com.hbm.handler.nei;

import java.util.HashMap;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidIcon;

import net.minecraft.item.ItemStack;

public class DairyHandler extends NEIUniversalHandler {

	public DairyHandler() {
		super("Dairy", ModBlocks.machine_milk_reformer, getDairyRecipesForNEI());
	}

	@Override
	public String getKey() {
		return "ntmDairy";
	}

    public static HashMap<Object, Object> getDairyRecipesForNEI() {

        return com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.display(java.util.Collections.singletonMap("MILK",com.hbm.realsim.AncillaryProcesses.dairy()));
	}
    
}
