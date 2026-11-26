package com.hbm.handler.nei;

import java.awt.Rectangle;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.gui.GUIWoodchipper;
import com.hbm.inventory.recipes.WoodchipperRecipes;

public final class WoodchipperHandler extends NEIUniversalHandler {
    public WoodchipperHandler() {
        super(ModBlocks.machine_woodchipper.getLocalizedName(), ModBlocks.machine_woodchipper, WoodchipperRecipes.getRecipes());
    }

    @Override public String getKey() { return "ntmWoodchipper"; }

    @Override public void loadTransferRects() {
        super.loadTransferRects();
        transferRectsGui.add(new RecipeTransferRect(new Rectangle(68, 33, 40, 16), getKey()));
        guiGui.add(GUIWoodchipper.class);
        RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
    }
}
