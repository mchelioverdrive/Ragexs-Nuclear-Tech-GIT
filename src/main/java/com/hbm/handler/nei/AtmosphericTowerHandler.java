package com.hbm.handler.nei;

import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.ChemistryProcesses;
import com.hbm.realsim.ProcessRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class AtmosphericTowerHandler extends NEIUniversalHandler {
    public AtmosphericTowerHandler() {
        super(ModBlocks.machine_atmo_tower.getLocalizedName(), ModBlocks.machine_atmo_tower, generateRecipes());
    }
    @Override public String getKey() { return "ntmAtmosphericSeparation"; }
    public static HashMap<Object, Object> generateRecipes() {
        HashMap<Object, Object> map = new HashMap<>();
        ProcessRecipe bill = ChemistryProcesses.airSeparation();
        map.put(ItemFluidIcon.make(Fluids.fromName(bill.inputs[0]), bill.quantities[0], bill.pressures[0]), products(bill));
        return map;
    }
    public static ItemStack[] products(ProcessRecipe bill) {
        ItemStack[] products = new ItemStack[bill.outputs.length];
        for(int i = 0; i < products.length; i++) products[i] = ItemFluidIcon.makePhysical(
                new FluidStack(Fluids.fromName(bill.outputs[i]), 1), bill.productMass[i]);
        return products;
    }
    @Override public void loadUsageRecipes(ItemStack ingredient) {
        if(ingredient.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.machine_atmo_tower)) super.loadCraftingRecipes(getKey());
        else super.loadUsageRecipes(ingredient);
    }
    @Override public void drawExtras(int recipe) {
        ProcessRecipe bill = ChemistryProcesses.airSeparation();
        Minecraft.getMinecraft().fontRenderer.drawString("Gases; drain all four outlets", 4, 2, 0x404040);
        Minecraft.getMinecraft().fontRenderer.drawString(String.format(java.util.Locale.US, "%.1f s; %s", bill.duration / 20D,
                com.hbm.realsim.MachineInfo.watts(bill.electricalRatingWatts)), 4, 55, 0x404040);
    }
}
