package com.hbm.inventory.recipes.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.hbm.realsim.PetroleumProcesses;
import com.hbm.realsim.ProcessRecipe;
import com.hbm.realsim.ProcessMachine;
import com.hbm.realsim.PhysicalRegistry;
import com.hbm.realsim.PhysicalState;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemFluidIcon;
import net.minecraft.item.ItemStack;

/** Canonical petroleum JSON boundary. Display counts describe 1000 batches, with rounding only in NEI. */
public abstract class PetroleumRecipeLoader extends SerializableRecipe {
    protected abstract Map<String, ProcessRecipe> bills();
    protected abstract String machine();
    @Override public void registerDefaults() { bills().putAll(PetroleumProcesses.defaults(machine())); }
    @Override public void deleteRecipes() { bills().clear(); }
    @Override public Object getRecipeObject() { return bills(); }
    @Override public String getComment() { return "Phase 5C physical bills: exact product massUnits; 22711000000000 units/kg. duration is ticks, work/heat are J/batch. Input quantities are phase-specific inventory units. Old volume-only petroleum recipes require migration."; }
    @Override public void readRecipe(JsonElement json) {
        ProcessRecipe recipe = PetroleumProcesses.read(json.getAsJsonObject());
        if(!recipe.id.startsWith(machine() + ":") || recipe.inputs.length == 0) throw new IllegalArgumentException("Wrong petroleum machine bill");
        validateRating(machine(), recipe);
        bills().put(recipe.inputs[0], recipe);
    }
    public static void validateRating(String machine, ProcessRecipe recipe) {
        int rate = machine.equals("refinery") || machine.equals("heat") ? 24 : machine.equals("coker") ? 6 : 12;
        if(!machine.equals("alkyl") && (PhysicalRegistry.get(recipe.inputs[0]).basis != PhysicalState.Basis.LIQUID_LITER
                || (long) recipe.quantities[0] * 1200L > (long) recipe.duration * rate)) throw new IllegalArgumentException("Petroleum throughput exceeds " + machine + " nameplate");
        if(machine.equals("alkyl") && ((long) recipe.quantities[0] * 1200L > (long) recipe.duration * 3000L)) throw new IllegalArgumentException("Alkylation gas feed exceeds nameplate");
    }
    @Override public void writeRecipe(Object entry, JsonWriter writer) throws IOException {
        PetroleumProcesses.write(((Map.Entry<String, ProcessRecipe>) entry).getValue(), writer);
    }
    public static HashMap display(Map<String, ProcessRecipe> bills) {
        HashMap result = new HashMap();
        for(ProcessRecipe recipe : bills.values()) {
            ItemStack[] inputs = new ItemStack[recipe.inputs.length];
            for(int i = 0; i < inputs.length; i++) inputs[i] = ItemFluidIcon.make(Fluids.fromName(recipe.inputs[i]), Math.multiplyExact(recipe.quantities[i], 1000), recipe.pressures[i]);
            ItemStack[] outputs = new ItemStack[recipe.outputs.length];
            for(int i = 0; i < outputs.length; i++) {
                int amount = Math.toIntExact(java.math.BigInteger.valueOf(recipe.productMass[i]).multiply(java.math.BigInteger.valueOf(1000))
                        .divide(java.math.BigInteger.valueOf(PhysicalRegistry.get(recipe.outputs[i]).massUnits)).longValueExact());
                outputs[i] = PhysicalRegistry.get(recipe.outputs[i]).phase == PhysicalState.Phase.SOLID
                        ? ProcessMachine.solid(recipe.outputs[i], Math.max(1, amount)) : ItemFluidIcon.make(Fluids.fromName(recipe.outputs[i]), amount);
            }
            result.put(inputs.length == 1 ? inputs[0] : inputs, outputs);
        }
        return result;
    }
    /** Legacy tuple preview only. Execution must use the full bill, including utilities and all coproducts. */
    public static com.hbm.inventory.FluidStack preview(ProcessRecipe bill, int index, int scale) {
        if(index >= bill.outputs.length) return new com.hbm.inventory.FluidStack(Fluids.NONE, 0);
        String name = bill.outputs[index];
        int amount = java.math.BigInteger.valueOf(bill.productMass[index]).multiply(java.math.BigInteger.valueOf(scale))
                .divide(java.math.BigInteger.valueOf(PhysicalRegistry.get(name).massUnits)).intValueExact();
        return new com.hbm.inventory.FluidStack(Fluids.fromName(name), amount);
    }
}
