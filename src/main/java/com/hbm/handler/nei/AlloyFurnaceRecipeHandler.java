package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.blocks.ModBlocks;
import com.hbm.handler.imc.ICompatNHNEI;
import com.hbm.inventory.gui.GUIDiFurnace;
import com.hbm.inventory.recipes.BlastFurnaceRecipes;
import com.hbm.inventory.recipes.MachineRecipes;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public class AlloyFurnaceRecipeHandler extends SafeTemplateRecipeHandler implements ICompatNHNEI {

	public static ArrayList<Fuel> fuels;

	@Override
	public ItemStack[] getMachinesForRecipe() {
		return new ItemStack[]{
				new ItemStack(ModBlocks.machine_difurnace_off),
				new ItemStack(ModBlocks.machine_difurnace_rtg_off)};
	}

	@Override
	public String getRecipeID() {
		return "alloysmelting";
	}

	public class SmeltingSet extends TemplateRecipeHandler.CachedRecipe {
		PositionedStack input1;
		PositionedStack input2;
		PositionedStack result;

		public SmeltingSet(List<ItemStack> list, List<ItemStack> list2, ItemStack result) {
			this.input1 = NEISafe.positionedStack(list, 75, 7);
			this.input2 = NEISafe.positionedStack(list2, 75, 43);
			this.result = NEISafe.positionedStack(result, 129, 25);
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return NEISafe.getCycledIngredients(this, cycleticks / 48, Arrays.asList(new PositionedStack[] { input1, input2 }));
		}

		@Override
		public PositionedStack getOtherStack() {
			ensureResources();
            return fuels.isEmpty() ? null : fuels.get((cycleticks / 48) % fuels.size()).stack;
		}

		@Override
		public PositionedStack getResult() {
			return result;
		}
	}

	public static class Fuel {
		public Fuel(ItemStack ingred) {

			this.stack = NEISafe.positionedStack(ingred, 3, 25, false);
		}

		public PositionedStack stack;
	}

    @Override public List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe gui, ItemStack stack, List<String> lines, int recipe) {
        if(!isRecipeItem(stack, recipe)) return super.handleItemTooltip(gui, stack, lines, recipe);
        int ticks = com.hbm.tileentity.machine.TileEntityDiFurnace.getItemPower(stack, true);
        if(ticks > 0) {
            lines.add("Fueled furnace: " + ticks + " fuel ticks per item");
            lines.add("Job: " + com.hbm.tileentity.machine.TileEntityDiFurnace.processingSpeed + " fuel ticks; unused credit is retained");
            lines.add(String.format(java.util.Locale.US, "Fuel per job: %.4f item equivalents", com.hbm.tileentity.machine.TileEntityDiFurnace.processingSpeed / (double) ticks));
        }
        return super.handleItemTooltip(gui, stack, lines, recipe);
    }

	@Override
	public String getRecipeName() {
		return ModBlocks.machine_difurnace_off.getLocalizedName();
	}

	@Override
	public String getGuiTexture() {
		return GUIDiFurnace.texture.toString();
	}

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if((outputId.equals("alloysmelting")) && getClass() == AlloyFurnaceRecipeHandler.class) {
			Map<List<ItemStack>[], ItemStack> recipes = BlastFurnaceRecipes.getRecipesForNEI();
			for(Entry<List<ItemStack>[], ItemStack> recipe : recipes.entrySet()) {
				this.arecipes.add(new SmeltingSet(recipe.getKey()[0], recipe.getKey()[1], recipe.getValue()));
			}
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		Map<List<ItemStack>[], ItemStack> recipes = BlastFurnaceRecipes.getRecipesForNEI();
		for(Entry<List<ItemStack>[], ItemStack> recipe : recipes.entrySet()) {
			if(NEISafe.matchesRecipeStack(recipe.getValue(), result))
				this.arecipes.add(new SmeltingSet(recipe.getKey()[0], recipe.getKey()[1], recipe.getValue()));
		}
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if((inputId.equals("alloysmelting")) && getClass() == AlloyFurnaceRecipeHandler.class) {
			loadCraftingRecipes("alloysmelting", new Object[0]);
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		for(ItemStack fuel : MachineRecipes.instance().getAlloyFuels()) {
			if(NEISafe.matchesRecipeStack(ingredient, fuel)) {
				loadCraftingRecipes("alloysmelting");
				return;
			}
		}

		Map<List<ItemStack>[], ItemStack> recipes = BlastFurnaceRecipes.getRecipesForNEI();
		for(Entry<List<ItemStack>[], ItemStack> recipe : recipes.entrySet()) {
			List<ItemStack> combined = new ArrayList<ItemStack>();
			combined.addAll(recipe.getKey()[0]);
			combined.addAll(recipe.getKey()[1]);
			for(ItemStack combinedStack : combined) {
				if(NEISafe.matchesRecipeStack(ingredient, combinedStack)) {
					this.arecipes.add(new SmeltingSet(recipe.getKey()[0], recipe.getKey()[1], recipe.getValue()));
					break;
				}
			}
		}
	}

	@Override
	public Class<? extends GuiContainer> getGuiClass() {
		return GUIDiFurnace.class;
	}

	@Override
	public void loadTransferRects() {
		transferRects.add(new RecipeTransferRect(new Rectangle(96, 25, 24, 18), "alloysmelting"));
	}

	@Override
	public void drawExtras(int recipe) {
		drawProgressBar(57, 26, 176, 0, 14, 14, 48, 7);

		drawProgressBar(96, 24, 176, 14, 24, 16, 48, 0);

		drawProgressBar(39, 7, 201, 0, 16, 52, 480, 7);
	}

    private static void ensureResources() {
        if(fuels != null && !fuels.isEmpty()) return;
        fuels = new ArrayList<Fuel>();
        for(ItemStack item : MachineRecipes.instance().getAlloyFuels()) {
            if(NEISafe.isValid(item) && com.hbm.tileentity.machine.TileEntityDiFurnace.getItemPower(item, true) > 0) fuels.add(new Fuel(item));
        }
    }
}
