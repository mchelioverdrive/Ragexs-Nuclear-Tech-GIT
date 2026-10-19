package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.*;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.gui.GUIITER;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.items.machine.ItemFluidIcon;
import net.minecraft.item.ItemStack;

/** ITER coolant operations and timed byproducts are distinct native production paths. */
public class FusionRecipeHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Fusion Reactor"; }
    @Override public String getOverlayIdentifier() { return "fusion"; }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.iter}; }
    @Override public Class<? extends net.minecraft.client.gui.inventory.GuiContainer> getGuiClass() { return GUIITER.class; }
    @Override public void loadTransferRects() { transferRects.add(new RecipeTransferRect(new Rectangle(69, 16, 44, 37), "fusion")); }
    @Override protected List<Process> processes() { return snapshot(); }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(FluidType plasma : FusionRecipes.byproducts.keySet()) {
            rows.add(new Process(ModBlocks.iter, inputs(ItemFluidIcon.make(plasma, 0)), new ItemStack[] {FusionRecipes.getByproduct(plasma)},
                    "Timed byproduct: one item / " + FusionRecipes.getByproductDelay(plasma) / 20D + " s of powered plasma operation",
                    "Plasma icon identifies continuous feed; no fixed batch quantity", "Requires intact fusion shield, magnet power and output space",
                    "1 MW base auxiliary draw; shield changes power and breeding"));
            int amount = Math.max(1, FusionRecipes.getCoolant(plasma));
            for(FluidType cold : Fluids.getInNiceOrder()) {
                if(!cold.hasTrait(FT_Heatable.class)) continue;
                FT_Heatable.HeatingStep step = cold.getTrait(FT_Heatable.class).getFirstStep();
                if(step == null || step.typeProduced == null) continue;
                rows.add(new Process(ModBlocks.iter, inputs(ItemFluidIcon.make(plasma, 1), ItemFluidIcon.make(cold, amount)),
                        new ItemStack[] {ItemFluidIcon.make(step.typeProduced, amount)},
                        "One native plasma packet consumed per coolant operation", amount + " L cold coolant -> " + amount + " L hot coolant",
                        "Up to 20 operations/tick (24,000/min); not fixed output", "Requires shield, magnet power and matching hot-fluid outlet",
                        "Blocked cooling causes heat stress and shield damage", "Timed byproducts shown separately; no direct electrical output"));
            }
        }
        cache = Collections.unmodifiableList(rows); revision = current; return cache;
    }
}
