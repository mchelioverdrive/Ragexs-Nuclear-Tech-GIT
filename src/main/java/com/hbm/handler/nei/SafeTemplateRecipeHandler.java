package com.hbm.handler.nei;

import java.util.ArrayList;

import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * TemplateRecipeHandler base which rejects malformed cached recipes before GTNH
 * NEI can build recipe ids/favorites from them.
 */
public abstract class SafeTemplateRecipeHandler extends TemplateRecipeHandler {

	public SafeTemplateRecipeHandler() {
		this.arecipes = new SafeRecipeList(this);
	}

    @Override public java.util.List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe gui,
            net.minecraft.item.ItemStack stack, java.util.List<String> lines, int recipe) {
        com.hbm.realsim.ProcessRecipe bill = com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.describedBill(stack);
        if(bill != null) {
            com.hbm.realsim.MachineInfo.process(lines, bill);
            if(bill.heatQuanta > 0) {
                lines.add("Heat per batch: " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.heatQuanta));
                lines.add("Thermal duty is separate from electrical work; use the machine's heat supply");
            }
            for(int i = 0; i < bill.outputs.length; i++) if(bill.outputs[i].startsWith("VENT_"))
                lines.add("Vented: " + bill.outputs[i].substring(5) + " (not collected)");
        }
        if(stack != null && stack.hasTagCompound()) {
            if(stack.getTagCompound().getBoolean("neiRetainedCatalyst")) lines.add("Required catalyst hardware; not consumed");
            if(stack.getTagCompound().hasKey("neiProductMass")) {
                String identity = stack.getTagCompound().getString("neiProductIdentity");
                java.math.BigDecimal amount = java.math.BigDecimal.valueOf(stack.getTagCompound().getLong("neiProductMass"))
                        .divide(java.math.BigDecimal.valueOf(com.hbm.realsim.PhysicalRegistry.get(identity).massUnits), 8, java.math.RoundingMode.DOWN).stripTrailingZeros();
                lines.add("Exact yield: " + amount.toPlainString() + " items/batch; fractional material accumulates");
            }
        }
        return super.handleItemTooltip(gui, stack, lines, recipe);
    }

	private static class SafeRecipeList extends ArrayList<TemplateRecipeHandler.CachedRecipe> {
		private static final long serialVersionUID = 1L;
		private final SafeTemplateRecipeHandler handler;

		private SafeRecipeList(SafeTemplateRecipeHandler handler) {
			this.handler = handler;
		}

		@Override
		public boolean add(TemplateRecipeHandler.CachedRecipe recipe) {
			if(NEISafe.isSafeRecipe(handler, recipe, "add")) {
				return super.add(recipe);
			}
			return false;
		}

		@Override
		public void add(int index, TemplateRecipeHandler.CachedRecipe element) {
			if(NEISafe.isSafeRecipe(handler, element, "indexed add")) {
				super.add(index, element);
			}
		}
	}
}
