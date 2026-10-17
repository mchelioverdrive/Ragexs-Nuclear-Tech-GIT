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

/** Canonical petroleum JSON boundary. NEI previews retain the complete utility bill and exact one-batch yields. */
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
        recipe = PetroleumProcesses.forEquipment(machine(), recipe);
        validateRating(machine(), recipe);
        bills().put(recipe.inputs[0], recipe);
    }
    public static void validateRating(String machine, ProcessRecipe recipe) {
        validatePorts(machine, recipe);
        int rate = machine.equals("refinery") || machine.equals("heat") ? 24 : machine.equals("coker") ? 6 : 12;
        if(!machine.equals("alkyl") && (PhysicalRegistry.get(recipe.inputs[0]).basis != PhysicalState.Basis.LIQUID_LITER
                || (long) recipe.quantities[0] * 1200L > (long) recipe.duration * rate)) throw new IllegalArgumentException("Petroleum throughput exceeds " + machine + " nameplate");
        if(machine.equals("alkyl") && ((long) recipe.quantities[0] * 1200L > (long) recipe.duration * 3000L)) throw new IllegalArgumentException("Alkylation gas feed exceeds nameplate");
    }
    /** Physical native inventory/tank limits, shared by loaded bills and accounting checks. */
    public static void validatePorts(String machine, ProcessRecipe recipe) {
        int fluidInputs = machine.equals("hydro") || machine.equals("alkyl") ? 2 : 1;
        int fluidOutputs = machine.equals("heat") ? 1 : machine.equals("refinery") || machine.equals("vacuum") ? 4
                : machine.equals("reform") || machine.equals("coker") ? 3 : 2;
        int solidOutputs = machine.equals("refinery") || machine.equals("crack") || machine.equals("coker") ? 1 : 0;
        int fluid = 0, solid = 0;
        for(String name : recipe.outputs) if(!name.startsWith("VENT_")) {
            if(PhysicalRegistry.get(name).phase == PhysicalState.Phase.SOLID) solid++; else fluid++;
        }
        if(recipe.inputs.length < 1 || recipe.inputs.length > fluidInputs || recipe.solidInputs.length != 0
                || fluid > fluidOutputs || solid > solidOutputs)
            throw new IllegalArgumentException(machine + " / " + recipe.id + " exceeds native material ports");
    }
    @Override public void writeRecipe(Object entry, JsonWriter writer) throws IOException {
        PetroleumProcesses.write(((Map.Entry<String, ProcessRecipe>) entry).getValue(), writer);
    }
    public static HashMap display(Map<String, ProcessRecipe> bills) {
        HashMap result = new HashMap();
        for(ProcessRecipe recipe : bills.values()) {
            java.util.List<Object> inputs = new java.util.ArrayList<>();
            for(int i = 0; i < recipe.inputs.length; i++) inputs.add(ItemFluidIcon.make(Fluids.fromName(recipe.inputs[i]), recipe.quantities[i], recipe.pressures[i]));
            for(int i = 0; i < recipe.solidInputs.length; i++) inputs.add(ProcessMachine.feed(recipe, i, "NEI " + recipe.id));
            if(recipe.id.startsWith("hydro:") || recipe.id.startsWith("reform:")) {
                ItemStack catalyst = new ItemStack(com.hbm.items.ModItems.catalytic_converter);
                catalyst.setTagCompound(new net.minecraft.nbt.NBTTagCompound()); catalyst.getTagCompound().setBoolean("neiRetainedCatalyst", true);
                inputs.add(catalyst);
            }
            java.util.List<ItemStack> outputs = new java.util.ArrayList<>();
            for(int i = 0; i < recipe.outputs.length; i++) {
                String name = recipe.outputs[i];
                if(name.startsWith("VENT_") || recipe.productMass[i] == 0) continue;
                outputs.add(productDisplay(recipe, i));
            }
            // Metadata is presentation-only NBT on the preview. It never enters machine inventories.
            if(!inputs.isEmpty() && inputs.get(0) instanceof ItemStack) describe((ItemStack) inputs.get(0), recipe);
            result.put(inputs.size() == 1 ? inputs.get(0) : inputs.toArray(), outputs.toArray(new ItemStack[0]));
        }
        return result;
    }
    public static ItemStack productDisplay(ProcessRecipe bill, int product) {
        return productDisplay(bill.outputs[product], bill.productMass[product]);
    }
    /** One display parcel, including its own exact mass when a large solid lot spans several slots. */
    public static ItemStack productDisplay(String identity, long mass) {
        if(identity.startsWith("VENT_") || mass == 0) return null;
        int quantity = Math.toIntExact(PetroleumProcesses.ceilDiv(mass, PhysicalRegistry.get(identity).massUnits));
        if(PhysicalRegistry.get(identity).phase != PhysicalState.Phase.SOLID)
            return ItemFluidIcon.makePhysical(new com.hbm.inventory.FluidStack(Fluids.fromName(identity), quantity), mass);
        ItemStack stack = ProcessMachine.solid(identity, quantity);
        if(!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound().setString("neiProductIdentity", identity); stack.getTagCompound().setLong("neiProductMass", mass); return stack;
    }
    public static ItemStack describe(ItemStack preview, ProcessRecipe bill) {
        if(preview == null || bill == null) return preview;
        if(!preview.hasTagCompound()) preview.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound(); bill.write(tag);
        preview.getTagCompound().setTag("neiProcessBill", tag); return preview;
    }
    public static ProcessRecipe describedBill(ItemStack preview) {
        return preview != null && preview.hasTagCompound() && preview.getTagCompound().hasKey("neiProcessBill", 10)
                ? ProcessRecipe.read(preview.getTagCompound().getCompoundTag("neiProcessBill")) : null;
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
