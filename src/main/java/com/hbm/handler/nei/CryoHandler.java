package com.hbm.handler.nei;

import java.util.*;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.CryoRecipes;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.inventory.gui.GUIMachineCryoDistill;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.ProcessRecipe;
import net.minecraft.item.ItemStack;

/** The physical separator bill defines the batch; unused native outlets are not products. */
public class CryoHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Cryogenic Distillation"; }
    @Override public String getOverlayIdentifier() { return "ntmCryodistill"; }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.machine_cryo_distill}; }
    @Override public Class<? extends net.minecraft.client.gui.inventory.GuiContainer> getGuiClass() { return GUIMachineCryoDistill.class; }
    @Override protected List<Process> processes() { return snapshot(); }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(FluidType feed : Fluids.getInNiceOrder()) {
            ProcessRecipe bill = CryoRecipes.physicalBill(feed, CryoRecipes.getOutput(feed));
            if(bill == null) continue;
            List<ItemStack> outputs = new ArrayList<>();
            for(int i = 0; i < bill.outputs.length; i++) outputs.add(PetroleumRecipeLoader.productDisplay(bill, i));
            rows.add(new Process(ModBlocks.machine_cryo_distill, inputs(ItemFluidIcon.make(feed, bill.quantities[0])), outputs.toArray(new ItemStack[0]),
                    "Native batch: " + bill.quantities[0] + " standard L; " + bill.duration / 20D + " s",
                    "Electrical work: " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.workQuanta),
                    "Rated draw: " + com.hbm.realsim.MachineInfo.watts(bill.electricalRatingWatts),
                    "Exact fractional gas products accumulate; all active outlets need room"));
        }
        cache = Collections.unmodifiableList(rows); revision = current; return cache;
    }
    // ProcessInfoHandler draws a plain panel; no dependency on the legacy shared NEI texture atlas.
}
