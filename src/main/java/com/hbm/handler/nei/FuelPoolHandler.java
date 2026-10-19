package com.hbm.handler.nei;

import java.util.*;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.FuelPoolRecipes;
import com.hbm.items.machine.ItemRBMKRod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Random spent-fuel cooling and continuous rod-temperature cooling are different native paths. */
public class FuelPoolHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Spent Fuel Pool Drum"; }
    @Override public String getOverlayIdentifier() { return "ntmSpentDrum"; }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.machine_waste_drum}; }
    @Override protected List<Process> processes() { return snapshot(); }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(Map.Entry<ComparableStack, ItemStack> entry : FuelPoolRecipes.recipes.entrySet())
            rows.add(new Process(ModBlocks.machine_waste_drum, inputs(entry.getKey().toStack()), new ItemStack[] {entry.getValue()},
                    "Requires at least one adjacent water block; water is not consumed",
                    "Each item rolls every tick: chance 1 / floor(72,000 / adjacent water faces)",
                    "Mean cooling time: 3,600 / adjacent water faces seconds", "1..6 water faces; random duration, not a guaranteed timer"));
        for(Object registered : Item.itemRegistry) if(registered instanceof ItemRBMKRod)
            rows.add(new Process(ModBlocks.machine_waste_drum, inputs(new ItemStack((Item) registered)), new ItemStack[0],
                    "RBMK rod is retained; core/hull temperatures change in its NBT", "Requires adjacent water; native heat exchange coefficient 0.025",
                    "Water sink: 20 C; actual cooling depends on rod heat and decay heat",
                    "Automation extracts only when both core and hull are below 50 C", "No extra cooled rod item or fixed cooling time is promised"));
        cache = Collections.unmodifiableList(rows); revision = current; return cache;
    }
}
