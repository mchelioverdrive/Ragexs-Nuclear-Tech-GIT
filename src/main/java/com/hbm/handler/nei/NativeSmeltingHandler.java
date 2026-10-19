package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.hbm.blocks.ModBlocks;
import com.hbm.tileentity.machine.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/** Native furnace consumers of the vanilla smelting registry, with their own timing requirements. */
public class NativeSmeltingHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static int recipeCount = -1;
    @Override public String getRecipeName() { return "Furnace Smelting"; }
    @Override public String getOverlayIdentifier() { return "ntmNativeSmelting"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.machine_furnace_brick_off, ModBlocks.furnace_iron, ModBlocks.furnace_steel, ModBlocks.machine_electric_furnace_off, ModBlocks.machine_arc_furnace_off, ModBlocks.machine_nuke_furnace_off, ModBlocks.machine_rtg_furnace_off, ModBlocks.machine_microwave}; }
    private static synchronized List<Process> snapshot() {
        Map<ItemStack, ItemStack> recipes = FurnaceRecipes.smelting().getSmeltingList();
        if(cache != null && recipeCount == recipes.size()) return cache;
        List<Process> rows = new ArrayList<>();
        for(Map.Entry<ItemStack, ItemStack> entry : recipes.entrySet()) {
            if(!NEISafe.isValid(entry.getKey()) || !NEISafe.isValid(entry.getValue())) continue;
            ItemStack input = entry.getKey().copy(); input.stackSize = 1;
            add(rows, ModBlocks.machine_furnace_brick_off, input, entry.getValue(), "200 progress / fuel-dependent material burn speed ticks", "Solid fuel, breathable air; ash accumulates");
            add(rows, ModBlocks.furnace_iron, input, entry.getValue(), "160 ticks base; speed/power upgrades alter operation", "Solid fuel required; output room can pause work");
            add(rows, ModBlocks.furnace_steel, input, entry.getValue(), "40,000 heat-work units per lane; three parallel lanes", "Rate depends on imported heat; no fixed cycle time");
            add(rows, ModBlocks.machine_electric_furnace_off, input, entry.getValue(), "100 ticks base (5 s); upgrades alter time/draw", "Electrical heating; native operating-power bill applies");
            add(rows, ModBlocks.machine_arc_furnace_off, input, entry.getValue(), TileEntityMachineArcFurnace.processingSpeed + " ticks / item", "Small arc furnace: vanilla smelting, electricity and three electrodes required");
            add(rows, ModBlocks.machine_nuke_furnace_off, input, entry.getValue(), TileEntityNukeFurnace.processingSpeed + " ticks / item", "Dedicated nuclear fuel charge required");
            add(rows, ModBlocks.machine_rtg_furnace_off, input, entry.getValue(), "1,000 progress / isotope heat per tick", "RTG pellets supply heat; output depends on source decay");
            if(input.getItem() instanceof ItemFood || entry.getValue().getItem() instanceof ItemFood)
                add(rows, ModBlocks.machine_microwave, input, entry.getValue(), "300 timer units; speed setting advances 2*speed/tick", "Food input or food output only; requires electricity");
        }
        cache = java.util.Collections.unmodifiableList(rows); recipeCount = recipes.size(); return cache;
    }
    private static void add(List<Process> rows, Block machine, ItemStack input, ItemStack output, String time, String conditions) {
        boolean arc = machine == ModBlocks.machine_arc_furnace_off;
        ItemStack[][] feed = arc ? inputs(input, new ItemStack(com.hbm.items.ModItems.arc_electrode, 3)) : inputs(input);
        rows.add(new Process(machine, feed, new ItemStack[] {output}, "Loaded furnace registry; quantities shown are per item", time, conditions,
                arc ? "Three electrodes are required hardware; native processing retains them" : "Fuel/heat requirements are separate utilities"));
    }
}
