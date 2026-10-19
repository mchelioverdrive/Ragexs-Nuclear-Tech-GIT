package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.GUIMachineRotaryFurnace;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes.RotaryFurnaceRecipe;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemScraps;
import com.hbm.realsim.*;
import net.minecraft.item.ItemStack;

/** Native bills with independent process fuel, steam drive and fractional exhaust. */
public class RotaryFurnaceHandler extends ProcessInfoHandler {
    private static volatile List<Process> cache;
    private static long revision = -1;
    public static void invalidate() { cache = null; }
    @Override public String getRecipeName() { return ModBlocks.machine_rotary_furnace.getLocalizedName(); }
    @Override public String getOverlayIdentifier() { return "ntmRotaryFurnace"; }
    @Override public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(58, 19, 32, 10), "ntmRotaryFurnace"));
    }
    @Override public Class<? extends net.minecraft.client.gui.inventory.GuiContainer> getGuiClass() { return GUIMachineRotaryFurnace.class; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.machine_rotary_furnace}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision();
        if(cache != null && revision == current) return cache;
        revision = current;
        List<ItemStack> fuels = new ArrayList<>();
        for(ItemStack stack : NEISafe.solidFuelCandidates()) if(SolidFuel.resolve(stack) != null) {
            ItemStack fuel = stack.copy(); fuel.stackSize = 1;
            boolean duplicate = false;
            for(ItemStack present : fuels) if(NEISafe.matchesRecipeStack(fuel, present)) { duplicate = true; break; }
            if(!duplicate) fuels.add(fuel);
        }
        List<Process> rows = new ArrayList<>();
        for(RotaryFurnaceRecipe bill : RotaryFurnaceRecipes.recipes) {
            if(bill == null || bill.ingredients == null || bill.output == null || bill.duration <= 0 || bill.steam <= 0 || fuels.isEmpty()) continue;
            List<ItemStack[]> in = new ArrayList<>();
            boolean valid = true;
            for(com.hbm.inventory.RecipesCommon.AStack ingredient : bill.ingredients) {
                ItemStack[] choices = ingredient == null ? null : NEISafe.copyValid(ingredient.extractForNEI().toArray(new ItemStack[0]));
                if(choices == null) { valid = false; break; }
                in.add(choices);
            }
            if(!valid) continue;
            if(bill.fluid != null) in.add(new ItemStack[] {ItemFluidIcon.make(bill.fluid)});
            in.add(fuels.toArray(new ItemStack[0]));
            int steam = Math.multiplyExact(bill.steam, bill.duration);
            in.add(new ItemStack[] {ItemFluidIcon.make(Fluids.STEAM, steam)});
            long heat = Math.multiplyExact(PetroleumProcesses.ceilDiv(bill.thermalJoules * 2L, bill.duration), bill.duration);
            ItemStack exhaust = ItemFluidIcon.makePhysical(new com.hbm.inventory.FluidStack(Fluids.SPENTSTEAM, 1), SteamLedger.State.ORDINARY.mass(steam));
            rows.add(new Process(ModBlocks.machine_rotary_furnace, in.toArray(new ItemStack[0][]),
                    new ItemStack[] {ItemScraps.create(bill.output, true), exhaust},
                    String.format(Locale.US, "%.2f s; %.2f L water-equivalent steam/job", bill.duration / 20D, steam / 100D),
                    "Firebox heat: " + api.hbm.energymk2.EnergyUnits.formatJoules(heat) + " per job",
                    "Fuel LHV: " + api.hbm.energymk2.EnergyUnits.formatJoules(PetroleumProcesses.ceilDiv(heat * 1000000L, Combustion.FIREBOX_EFFICIENCY_PPM)) + " per job (85% useful)",
                    "Fuel alternatives are eligibility, not one item/job; credit retained",
                    "Steam drive: " + bill.steam * 20 + " kW shaft; drain fractional exhaust", "Requires air; ingredients and process fluid consumed per job"));
        }
        cache = java.util.Collections.unmodifiableList(rows); return cache;
    }
}
