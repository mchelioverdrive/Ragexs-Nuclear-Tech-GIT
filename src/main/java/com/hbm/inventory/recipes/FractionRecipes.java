package com.hbm.inventory.recipes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.ProcessRecipe;

public class FractionRecipes extends PetroleumRecipeLoader {
    private static final Map<String, ProcessRecipe> recipes = new LinkedHashMap<String, ProcessRecipe>();
    private static final Map<String, com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>> nominal = new LinkedHashMap<String, com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>>();
    @Override protected Map<String, ProcessRecipe> bills() { return recipes; }
    @Override protected String machine() { return "fraction"; }
    @Override public String getFileName() { return "hbmFractions.json"; }
    public static ProcessRecipe getPhysical(FluidType input) { return recipes.get(input.getName()); }
    @Override public void registerDefaults() {
        super.registerDefaults();
        nominal.put("EGG", new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(
                new com.hbm.inventory.FluidStack(com.hbm.inventory.fluid.Fluids.CHOLESTEROL, 50), new com.hbm.inventory.FluidStack(com.hbm.inventory.fluid.Fluids.RADIOSOLVENT, 50)));
        nominal.put("CHLOROCALCITE_MIX", new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(
                new com.hbm.inventory.FluidStack(com.hbm.inventory.fluid.Fluids.CHLOROCALCITE_CLEANED, 50), new com.hbm.inventory.FluidStack(com.hbm.inventory.fluid.Fluids.COLLOID, 50)));
    }
    @Override public void deleteRecipes() { super.deleteRecipes(); nominal.clear(); }
    public static com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> getNominal(FluidType input) { return nominal.get(input.getName()); }
    public static HashMap getFractionRecipesForNEI() {
        HashMap result = display(recipes);
        for(String input : nominal.keySet()) result.put(com.hbm.items.machine.ItemFluidIcon.make(com.hbm.inventory.fluid.Fluids.fromName(input), 100),
                new net.minecraft.item.ItemStack[] {com.hbm.items.machine.ItemFluidIcon.make(nominal.get(input).getKey()), com.hbm.items.machine.ItemFluidIcon.make(nominal.get(input).getValue())});
        return result;
    }
    @Override public Object getRecipeObject() {
        Map<String, Object> all = new LinkedHashMap<String, Object>(); all.putAll(recipes); all.putAll(nominal); return all;
    }
    @Override public void readRecipe(com.google.gson.JsonElement json) {
        com.google.gson.JsonObject obj = json.getAsJsonObject();
        if(obj.has("physical")) { super.readRecipe(json); return; }
        String input = obj.get("input").getAsString();
        if(!input.equals("EGG") && !input.equals("CHLOROCALCITE_MIX")) throw new IllegalArgumentException("Petroleum fractionation requires a physical bill");
        nominal.put(input, new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(readFluidStack(obj.getAsJsonArray("output1")), readFluidStack(obj.getAsJsonArray("output2"))));
    }
    @Override public void writeRecipe(Object entry, com.google.gson.stream.JsonWriter writer) throws java.io.IOException {
        Map.Entry<String, Object> row = (Map.Entry<String, Object>) entry;
        if(row.getValue() instanceof ProcessRecipe) { com.hbm.realsim.PetroleumProcesses.write((ProcessRecipe) row.getValue(), writer); return; }
        com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> value = (com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>) row.getValue();
        writer.name("input").value(row.getKey()); writer.name("output1"); writeFluidStack(value.getKey(), writer); writer.name("output2"); writeFluidStack(value.getValue(), writer);
    }
    @Deprecated public static com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> getFractions(FluidType input) {
        ProcessRecipe bill = getPhysical(input); if(bill == null) return getNominal(input);
        return new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(preview(bill, 0, 100), preview(bill, 1, 100));
    }
}
