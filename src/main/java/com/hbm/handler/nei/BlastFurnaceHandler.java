package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.gui.GUIBlastFurnace;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes.Recipe;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumCokeType;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Optional flux/enrichment are explicit variants of the loaded native heating bill. */
public class BlastFurnaceHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return ModBlocks.machine_blast_furnace.getLocalizedName(); }
    @Override public String getOverlayIdentifier() { return "ntmIndustrialBlastFurnace"; }
    @Override public Class<? extends net.minecraft.client.gui.inventory.GuiContainer> getGuiClass() { return GUIBlastFurnace.class; }
    @Override public void loadTransferRects() { transferRects.add(new RecipeTransferRect(new Rectangle(79, 62, 18, 18), "ntmIndustrialBlastFurnace")); }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.machine_blast_furnace}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(Recipe recipe : IndustrialBlastFurnaceRecipes.recipes) {
            ItemStack[] feed = NEISafe.copyValid(recipe.feed.extractForNEI().toArray(new ItemStack[0]));
            if(feed == null) continue;
            String fuel = recipe.coke ? "COKE_ITEM" : "COAL_ITEM";
            for(FluidType blast : new FluidType[] {Fluids.AIR, Fluids.AIR_HEATED, Fluids.AIRBLAST}) {
                for(int enrichment = 0; enrichment <= (recipe.coke ? 1 : 0); enrichment++) {
                    for(int flux = 0; flux <= (recipe.slag != null && recipe.slag.stackSize < recipe.slag.getMaxStackSize() ? 1 : 0); flux++) {
                        List<ItemStack[]> in = new ArrayList<>(); in.add(feed);
                        in.add(new ItemStack[] {recipe.coke ? new ItemStack(ModItems.coke, 1, EnumCokeType.COAL.ordinal()) : new ItemStack(Items.coal)});
                        in.add(new ItemStack[] {ItemFluidIcon.make(blast, recipe.air)});
                        if(enrichment > 0) in.add(new ItemStack[] {ItemFluidIcon.make(Fluids.OXYGEN_GAS, (recipe.air + 9) / 10)});
                        if(flux > 0) in.add(new ItemStack[] {new ItemStack(ModItems.powder_limestone)});
                        List<ItemStack> out = new ArrayList<>(); out.add(recipe.steel.copy());
                        if(recipe.slag != null) { ItemStack slag = recipe.slag.copy(); slag.stackSize += flux; out.add(slag); }
                        out.add(ItemFluidIcon.make(Fluids.BLAST_FURNACE_GAS, 0));
                        if(flux > 0) {
                            long mass = java.math.BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG).multiply(new java.math.BigDecimal("44.0095"))
                                    .divide(new java.math.BigDecimal("100.0869"), 0, java.math.RoundingMode.DOWN).longValueExact();
                            out.add(ItemFluidIcon.makePhysical(new com.hbm.inventory.FluidStack(Fluids.CARBONDIOXIDE, 1), mass));
                        }
                        long heat = enrichment > 0 ? recipe.heatJoules * 9 / 10 : recipe.heatJoules;
                        if(flux > 0) heat = Math.max(1, heat - 1200000L);
                        double gas = PhysicalRegistry.get(fuel).chemicalMicrojoulesPerUnit() * (BlastFurnaceProcess.gasRecovery(fuel) / 1000000D)
                                / PhysicalRegistry.get("BLAST_FURNACE_GAS").chemicalMicrojoulesPerUnit();
                        long rating = BlastFurnaceProcess.ratedWatts(recipe.coke, blast.getName(), enrichment > 0);
                        rows.add(new Process(ModBlocks.machine_blast_furnace, in.toArray(new ItemStack[0][]), out.toArray(new ItemStack[0]),
                                "Heating bill: " + SteamCycleHandler.energy(heat) + "; " + com.hbm.realsim.MachineInfo.thermalWatts(rating),
                                String.format(Locale.US, "Minimum heating time %.3f s; supply can slow it", heat / (double) rating),
                                "Reductant useful heat and hot blast offset the bill", "Deficit requires 900 C+ heater below controller",
                                String.format(Locale.US, "Recovered furnace gas: %.6f standard L/job; remainder retained", gas),
                                "Flux/enrichment ingredients apply only to this variant"));
                    }
                }
            }
        }
        cache = java.util.Collections.unmodifiableList(rows); revision = current; return cache;
    }
}
