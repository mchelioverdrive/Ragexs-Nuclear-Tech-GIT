package com.hbm.handler.nei;

import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.ChemistryProcesses;
import com.hbm.realsim.ProcessRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class IsotopeSeparationHandler extends NEIUniversalHandler {
    private final HashMap<Object, ProcessRecipe> bills = new HashMap<>();
    public IsotopeSeparationHandler() {
        super("Heavy-water isotope separation", new HashMap<>(), new HashMap<>());
        add(ChemistryProcesses.heavyWater(), ModBlocks.machine_deuterium_extractor);
        add(ChemistryProcesses.heavyWaterTower(), ModBlocks.machine_deuterium_tower);
    }
    private void add(ProcessRecipe bill, net.minecraft.block.Block machine) {
        ItemStack[] key = {ItemFluidIcon.make(Fluids.fromName(bill.inputs[0]), bill.quantities[0], bill.pressures[0]), new ItemStack(machine)};
        recipes.put(key, AtmosphericTowerHandler.products(bill)); machineOverrides.put(key, new ItemStack(machine)); bills.put(key, bill);
    }
    @Override public ItemStack[] getMachinesForRecipe() { return new ItemStack[] {new ItemStack(ModBlocks.machine_deuterium_extractor), new ItemStack(ModBlocks.machine_deuterium_tower)}; }
    @Override public String getKey() { return "ntmIsotopeSeparation"; }
    @Override public void drawExtras(int recipe) {
        ProcessRecipe bill = bills.get(((RecipeSet) arecipes.get(recipe)).originalInputInstance);
        Minecraft.getMinecraft().fontRenderer.drawString("Installed machine (retained)", 4, 2, 0x404040);
        Minecraft.getMinecraft().fontRenderer.drawString(String.format(java.util.Locale.US, "%.1f s; %s; %d C heat", bill.duration / 20D,
                com.hbm.realsim.MachineInfo.watts(bill.electricalRatingWatts), bill.temperatureC), 4, 55, 0x404040);
    }
}
