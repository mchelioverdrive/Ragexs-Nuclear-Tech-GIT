package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.handler.imc.ICompatNHNEI;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.gui.GUIMachineAssembler;
import com.hbm.inventory.recipes.AssemblerRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemAssemblyTemplate;
import com.hbm.lib.RefStrings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public class AssemblerRecipeHandler extends SafeTemplateRecipeHandler implements ICompatNHNEI {

    public LinkedList<RecipeTransferRect> transferRectsRec = new LinkedList<RecipeTransferRect>();
    public LinkedList<RecipeTransferRect> transferRectsGui = new LinkedList<RecipeTransferRect>();
    public LinkedList<Class<? extends GuiContainer>> guiRec = new LinkedList<Class<? extends GuiContainer>>();
    public LinkedList<Class<? extends GuiContainer>> guiGui = new LinkedList<Class<? extends GuiContainer>>();

	@Override
	public ItemStack[] getMachinesForRecipe() {
		return new ItemStack[]{
				new ItemStack(ModBlocks.machine_assembler),
				new ItemStack(ModBlocks.machine_assemfac)};
	}

	@Override
	public String getRecipeID() {
		return "assembly";
	}

    public class SmeltingSet extends TemplateRecipeHandler.CachedRecipe {

		List<PositionedStack> input;
        PositionedStack result;

        public SmeltingSet(List<Object> in, ItemStack result) {

	input = new ArrayList();

	ComparableStack comp = new ComparableStack(result);
	ItemStack template = ItemAssemblyTemplate.writeType(new ItemStack(ModItems.assembly_template), comp);

	for(int i = 0; i < Math.min(in.size(), AssemblerRecipes.MAX_INGREDIENTS); i++) {
		input.add(NEISafe.positionedStack(in.get(i), 30 + (i % 4) * 18, 6 + (i / 4) * 18));
	}

            input.add(NEISafe.positionedStack(template, 66 + 45, 6));
            this.result = NEISafe.positionedStack(result, 138, 24);
        }

        @Override
		public List<PositionedStack> getIngredients() {
            return NEISafe.getCycledIngredients(this, cycleticks / 48, input);
        }

        @Override
		public PositionedStack getResult() {
            return result;
        }
    }

	@Override
	public String getRecipeName() {
		return "Fabrication Machine";
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei_assembler.png";
	}

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {

		if ((outputId.equals("assembly")) && getClass() == AssemblerRecipeHandler.class) {

			Map<ItemStack, List<Object>> recipes = AssemblerRecipes.getRecipes();

			for (Map.Entry<ItemStack, List<Object>> recipe : recipes.entrySet()) {
				this.arecipes.add(new SmeltingSet(recipe.getValue(), recipe.getKey()));
			}
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {

		Map<ItemStack, List<Object>> recipes = AssemblerRecipes.getRecipes();

		for (Map.Entry<ItemStack, List<Object>> recipe : recipes.entrySet()) {

			if (NEISafe.matchesRecipeStack(recipe.getKey(), result))
				this.arecipes.add(new SmeltingSet(recipe.getValue(), recipe.getKey()));
		}
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {

		if ((inputId.equals("assembly")) && getClass() == AssemblerRecipeHandler.class) {
			loadCraftingRecipes("assembly", new Object[0]);
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		if(!NEISafe.isValid(ingredient)) return;
		if(ingredient.getItem() == ModItems.assembly_template) {
			ComparableStack type = ItemAssemblyTemplate.readType(ingredient);
			if(type != null) loadCraftingRecipes(type.toStack());
			return;
		}

		Map<ItemStack, List<Object>> recipes = AssemblerRecipes.getRecipes();

		outer:
		for (Map.Entry<ItemStack, List<Object>> recipe : recipes.entrySet()) {
			for(Object value : recipe.getValue()) {
				List<?> candidates = value instanceof List ? (List<?>) value : java.util.Arrays.asList(value);
				for(Object candidate : candidates) if(candidate instanceof ItemStack && NEISafe.matchesRecipeStack((ItemStack) candidate, ingredient)) {
					this.arecipes.add(new SmeltingSet(recipe.getValue(), recipe.getKey()));
					continue outer;
				}
			}
		}
	}

    @Override
    public Class<? extends GuiContainer> getGuiClass() {
	return null;
    }

    @Override
    public void loadTransferRects() {
        transferRectsGui = new LinkedList<RecipeTransferRect>();
        guiGui = new LinkedList<Class<? extends GuiContainer>>();

        transferRects.add(new RecipeTransferRect(new Rectangle(138 - 1 - 36, 23, 36, 18), "assembly"));
        transferRectsGui.add(new RecipeTransferRect(new Rectangle(18 * 2 + 2, 89 - 7 - 11, 18 * 5 - 4, 18 + 16), "assembly"));
        guiGui.add(GUIMachineAssembler.class);
        RecipeTransferRectHandler.registerRectsToGuis(getRecipeTransferRectGuis(), transferRects);
        RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
    }

    @Override
    public void drawExtras(int recipe) {

        drawProgressBar(83 - (18 * 4) - 9 + 1, 6, 0, 86, 16, 18 * 3 - 2, 480, 7);

        drawProgressBar(83 - 3 + 16 + 5, 5 + 18, 16, 86, 36, 18, 48, 0);
    }

    @Override
    public TemplateRecipeHandler newInstance() {
        return super.newInstance();
    }

}
