package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.HashMap;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.gui.GUIMachineMissileAssembly;
import com.hbm.items.weapon.MissileDefinition;

import net.minecraft.item.ItemStack;

public class MissileAssemblyHandler extends NEIUniversalHandler {

	public MissileAssemblyHandler() {
		super(ModBlocks.machine_missile_assembly.getLocalizedName(), ModBlocks.machine_missile_assembly, assemblyRecipes());
	}

	private static HashMap<Object, Object> assemblyRecipes() {
		HashMap<Object, Object> recipes = new HashMap<>();
		for(MissileDefinition missile : MissileDefinition.values()) {
			recipes.put(new ItemStack[] {
					new ItemStack(missile.targetingCircuit()), new ItemStack(missile.warhead()), missile.airframeStack()
			}, missile.missileStack());
		}
		return recipes;
	}

	@Override
	public String getKey() {
		return "ntmMissileAssembly";
	}

	@Override
	public void loadTransferRects() {
		super.loadTransferRects();
		transferRectsGui.add(new RecipeTransferRect(new Rectangle(134, 35, 16, 18), getKey()));
		guiGui.add(GUIMachineMissileAssembly.class);
		RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
	}
}
