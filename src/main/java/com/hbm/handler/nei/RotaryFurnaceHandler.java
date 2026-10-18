package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.Locale;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.GUIMachineRotaryFurnace;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes.RotaryFurnaceRecipe;
import com.hbm.items.machine.ItemScraps;
import com.hbm.util.I18nUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

public class RotaryFurnaceHandler extends NEIUniversalHandler {

	public RotaryFurnaceHandler() {
		super(ModBlocks.machine_rotary_furnace.getLocalizedName(), ModBlocks.machine_rotary_furnace, RotaryFurnaceRecipes.getRecipes());
	}

    @Override public int recipiesPerPage() { return 1; }
    @Override public int getNEIHandlerHeight() { return 80; }
    @Override public int getNEIMaxRecipesPerPage() { return 1; }

	@Override
	public String getKey() {
		return "ntmRotaryFurnace";
	}

	@Override
	public void loadTransferRects() {
		super.loadTransferRects();
		transferRectsGui.add(new RecipeTransferRect(new Rectangle(58, 19, 32, 10), "ntmRotaryFurnace"));
		guiGui.add(GUIMachineRotaryFurnace.class);
		RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
	}

    @Override public java.util.List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe gui, ItemStack stack, java.util.List<String> lines, int recipe) {
        // NEI calls this for every visible recipe, including when no item is hovered.
        if(!isRecipeItem(stack, recipe)) return super.handleItemTooltip(gui, stack, lines, recipe);
        RecipeSet page = (RecipeSet) arecipes.get(recipe);
        Object[] key = (Object[]) page.originalInputInstance;
        for(RotaryFurnaceRecipe bill : RotaryFurnaceRecipes.recipes) {
            if(key.length != bill.ingredients.length + (bill.fluid == null ? 0 : 1) + 1) continue;
            boolean match = true;
            for(int i = 0; i < bill.ingredients.length; i++) if(key[i] != bill.ingredients[i]) match = false;
            if(!match) continue;
            long heatQuanta = com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.thermalJoules * 2L, bill.duration) * bill.duration;
            lines.add("Firebox heat per job: " + api.hbm.energymk2.EnergyUnits.formatJoules(heatQuanta));
            lines.add("Solid fuel: " + api.hbm.energymk2.EnergyUnits.formatJoules(com.hbm.realsim.PetroleumProcesses.ceilDiv(heatQuanta * 1000000L, com.hbm.realsim.Combustion.FIREBOX_EFFICIENCY_PPM)) + " LHV per job (85% useful)");
            com.hbm.realsim.SolidFuel fuel = com.hbm.realsim.SolidFuel.resolve(stack);
            if(stack.hasTagCompound() && stack.getTagCompound().getBoolean("neiRotaryFuel") && fuel != null) {
                long useful = java.math.BigInteger.valueOf(fuel.chemicalMicrojoules()).multiply(java.math.BigInteger.valueOf(com.hbm.realsim.Combustion.FIREBOX_EFFICIENCY_PPM))
                        .divide(java.math.BigInteger.valueOf(500000000000L)).longValueExact();
                lines.add(String.format(Locale.US, "%s: %.5f item equivalents/job", stack.getDisplayName(), heatQuanta / (double) useful));
            }
            lines.add("Whole fuel items are consumed; unused fuel energy is retained");
            lines.add("Steam drive: " + bill.steam + " units/tick; " + ((long) bill.steam * bill.duration) + " units/job");
            break;
        }
        return super.handleItemTooltip(gui, stack, lines, recipe);
    }

	@Override
	public void drawExtras(int recipe) {

		RecipeSet rec = (RecipeSet) this.arecipes.get(recipe);
		Object[] original = (Object[]) rec.originalInputInstance;
		ItemStack output = rec.output[0].item;

		outer: for(RotaryFurnaceRecipe arc : RotaryFurnaceRecipes.recipes) {

			if(ItemStack.areItemStacksEqual(ItemScraps.create(arc.output, true), output) && arc.ingredients.length == original.length - (arc.fluid == null ? 0 : 1) - 1) {

				for(int i = 0; i < arc.ingredients.length; i++) {
					if(arc.ingredients[i] != original[i]) continue outer;
				}

				FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
				String duration = String.format(Locale.US, "%.2f s; fuel: hover", arc.duration / 20D);
				String consumption = I18nUtil.resolveKey(Fluids.STEAM.getUnlocalizedName()) + ": " + String.format(Locale.US, "%,d", arc.steam) + " L/t";
				int side = 160;
				fontRenderer.drawString(duration, side - fontRenderer.getStringWidth(duration), 55, 0x404040);
				fontRenderer.drawString(consumption, side - fontRenderer.getStringWidth(consumption), 67, 0x404040);
				return;
			}
		}
	}
}
