package com.hbm.handler.nei;

import api.hbm.energymk2.EnergyUnits;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.gui.GUIMachineSolderingStation;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.util.InventoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

public class SolderingStationHandler extends NEIUniversalHandler {

	public SolderingStationHandler() {
		// Handlers are also constructed for IMC before recipes are initialized.
		super(ModBlocks.machine_soldering_station.getLocalizedName(), ModBlocks.machine_soldering_station, new HashMap());
	}

	@Override
	public String getKey() {
		return "ntmSoldering";
	}

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if(outputId.equals(getKey())) {
			for(SolderingRecipe recipe : SolderingRecipes.recipes) addRecipe(recipe);
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		for(SolderingRecipe recipe : SolderingRecipes.recipes) {
			if(NEISafe.matchesRecipeStack(recipe.output, result)) addRecipe(recipe);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		for(SolderingRecipe recipe : SolderingRecipes.recipes) {
			for(Object input : getInputs(recipe)) {
				if(input instanceof AStack ? ((AStack) input).matchesRecipe(ingredient, true)
						: NEISafe.matchesRecipeStack((ItemStack) input, ingredient)) {
					addRecipe(recipe);
					break;
				}
			}
		}
	}

	private List<Object> getInputs(SolderingRecipe recipe) {
		List<Object> inputs = new ArrayList<Object>();
		for(AStack stack : recipe.toppings) inputs.add(stack);
		for(AStack stack : recipe.pcb) inputs.add(stack);
		for(AStack stack : recipe.solder) inputs.add(stack);
		if(recipe.fluid != null) inputs.add(ItemFluidIcon.make(recipe.fluid));
		return inputs;
	}

	private void addRecipe(SolderingRecipe recipe) {
		List<Object> inputs = getInputs(recipe);
		ItemStack[][] in = NEISafe.copyValid2D(InventoryUtil.extractObject(inputs.toArray()));
		ItemStack[][] out = NEISafe.copyValid2D(InventoryUtil.extractObject(recipe.output));
		// Do not show an incomplete bill when a required ore-dictionary ingredient is absent.
		if(in == null || in.length != inputs.size() || out == null) return;
		for(ItemStack[] choices : in) for(ItemStack stack : choices) if(stack.getItem() == ModItems.item_secret) return;
		if(recipe.output.getItem() == ModItems.item_secret) return;
		this.arecipes.add(new RecipeSet(in, out, recipe));
	}

	@Override
	public void loadTransferRects() {
		super.loadTransferRects();
		transferRectsGui.add(new RecipeTransferRect(new Rectangle(67, 17, 32, 14), "ntmSoldering"));
		guiGui.add(GUIMachineSolderingStation.class);
		RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
	}

	@Override
	public void drawExtras(int recipe) {

		RecipeSet rec = (RecipeSet) this.arecipes.get(recipe);
		SolderingRecipe sol = (SolderingRecipe) rec.originalInputInstance;

		FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
		String duration = String.format(Locale.US, "%.1f s / ", sol.duration / 20D) + EnergyUnits.formatJoules(Math.multiplyExact(sol.consumption, (long) sol.duration));
		String consumption = com.hbm.realsim.MachineInfo.watts(sol.consumption * 10L);
		int side = 160;
		fontRenderer.drawString(duration, side - fontRenderer.getStringWidth(duration), 43, 0x404040);
		fontRenderer.drawString(consumption, side - fontRenderer.getStringWidth(consumption), 55, 0x404040);
	}
}
