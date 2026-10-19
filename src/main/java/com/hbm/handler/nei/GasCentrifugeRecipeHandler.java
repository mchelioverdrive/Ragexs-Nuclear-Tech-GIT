package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.PseudoFluidType;
import com.hbm.inventory.gui.GUIMachineGasCent;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.*;
import net.minecraft.item.ItemStack;

/** Every internal cascade stage uses the paid native bill, not a fictional single-module total yield. */
public class GasCentrifugeRecipeHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Gas Centrifuge"; }
    @Override public String getOverlayIdentifier() { return "gascentprocessing"; }
    @Override public Class<? extends net.minecraft.client.gui.inventory.GuiContainer> getGuiClass() { return GUIMachineGasCent.class; }
    @Override public void loadTransferRects() { transferRects.add(new RecipeTransferRect(new Rectangle(69, 16, 44, 37), "gascentprocessing")); }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected net.minecraft.block.Block[] machineBlocks() { return new net.minecraft.block.Block[] {ModBlocks.machine_gascent}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(PseudoFluidType stage : PseudoFluidType.types.values()) {
            if(stage == PseudoFluidType.NONE || stage.getFluidConsumed() <= 0) continue;
            ProcessRecipe bill = NuclearChemicalProcesses.cascade(stage.name, false);
            if(bill != null) {
                for(boolean fast : new boolean[] {false, true}) {
                    if(stage.getIfHighSpeed() && !fast) continue;
                    bill = NuclearChemicalProcesses.cascade(stage.name, fast);
                    List<ItemStack> in = new ArrayList<>();
                    in.add(stream(stage, bill.quantities[0]));
                    if(fast) in.add(new ItemStack(ModItems.upgrade_gc_speed));
                    List<ItemStack> out = new ArrayList<>();
                    for(int i = 0; i < bill.outputs.length; i++) {
                        String identity = bill.outputs[i];
                        if(identity.startsWith("CASCADE_")) {
                            PseudoFluidType next = PseudoFluidType.types.get(identity.substring(8));
                            int quantity = Math.toIntExact(bill.productMass[i] / PhysicalRegistry.get(identity).massUnits);
                            out.add(stream(next, quantity));
                        } else out.add(PetroleumRecipeLoader.productDisplay(bill, i));
                    }
                    rows.add(new Process(ModBlocks.machine_gascent, inputs(in.toArray(new ItemStack[0])), out.toArray(new ItemStack[0]),
                            "Cascade stage: " + stage.name + (fast ? "; speed upgrade retained" : "; base speed"),
                            "Duration: " + bill.duration / 20D + " s; " + MachineInfo.watts(bill.electricalRatingWatts),
                            "Electrical work: " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.workQuanta),
                            "Connect enriched internal stream to next module; drain tails/F", "Exact fractional solid/fluid products accumulate; no extra U-235"));
                }
            } else {
                List<ItemStack> outputs = new ArrayList<>();
                if(stage.getOutputType() != null && stage.getOutputType() != PseudoFluidType.NONE && stage.getFluidProduced() > 0)
                    outputs.add(stream(stage.getOutputType(), stage.getFluidProduced()));
                if(stage.getOutput() != null) for(ItemStack stack : stage.getOutput()) if(NEISafe.isValid(stack)) outputs.add(stack);
                if(outputs.isEmpty()) continue;
                rows.add(new Process(ModBlocks.machine_gascent, inputs(stream(stage, stage.getFluidConsumed())), outputs.toArray(new ItemStack[0]),
                        "Internal stage: " + stage.name + "; 150 ticks base", "2 kW base; speed upgrade: 80 ticks at 3 kW", "Direct module transfer; every output must have room"));
            }
        }
        cache = java.util.Collections.unmodifiableList(rows); revision = current; return cache;
    }
    private static ItemStack stream(PseudoFluidType stage, int amount) {
        for(Map.Entry<FluidType, PseudoFluidType> entry : GasCentrifugeRecipes.fluidConversions.entrySet())
            if(entry.getValue() == stage) return ItemFluidIcon.make(entry.getKey(), amount);
        ItemStack icon = ItemFluidIcon.make(Fluids.UF6, amount);
        icon.getTagCompound().setString("neiPseudoFluid", stage.name);
        icon.setStackDisplayName(stage.getName() + " (internal cascade stream)");
        return icon;
    }
}
