package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;

import net.minecraft.item.ItemStack;

public class ElectrolyserFluidRecipes extends SerializableRecipe {

	public static HashMap<FluidType, ElectrolysisRecipe> recipes = new HashMap();

	@Override
	public void registerDefaults() {
		// Gas fluid units are volume-like batches: electrolysis therefore keeps the 2:1 hydrogen/deuterium to oxygen ratio.
		recipes.put(Fluids.FRESH_WATER, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.electrolysis(20_000)));
		recipes.put(Fluids.HEAVYWATER, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.heavyElectrolysis()));
		recipes.put(Fluids.VITRIOL, new ElectrolysisRecipe(1_000, new FluidStack(Fluids.SULFURIC_ACID, 500), new FluidStack(Fluids.CHLORINE, 500), new ItemStack(ModItems.powder_iron), new ItemStack(ModItems.ingot_mercury)));
		recipes.put(Fluids.SLOP, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.slopTreatment()));

		// Aqueous chloride electrolysis releases chlorine, hydrogen and solid caustic; returned water has its own output.
		recipes.put(Fluids.BRINE, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.chloralkali(false)));
		recipes.put(Fluids.POTASSIUM_CHLORIDE, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.potassiumChlorideElectrolysis()));

		//molten salt -> 2sodium and 2chlorine
		recipes.put(Fluids.MOLTEN_CALCIUM_CHLORIDE, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.calciumElectrolysis()));
		recipes.put(Fluids.MOLTEN_SALT, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.moltenSaltElectrolysis()));

		// CACL2 is an aqueous processing liquor, not a molten calcium-metal feed; calcium remains supplied by its existing thermal route.

		recipes.put(Fluids.MOLTEN_STRONTIUM_CHLORIDE, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.strontiumElectrolysis()));

        recipes.put(Fluids.RHODIUM_SOLUTION, new ElectrolysisRecipe(com.hbm.realsim.MineralChemicalProcesses.preciousMetalElectrolysis(true)));
        recipes.put(Fluids.IRIDIUM_SOLUTION, new ElectrolysisRecipe(com.hbm.realsim.MineralChemicalProcesses.preciousMetalElectrolysis(false)));
        recipes.put(Fluids.MOLTEN_LITHIUM_CHLORIDE, new ElectrolysisRecipe(com.hbm.realsim.MineralChemicalProcesses.lithiumElectrolysis()));

		// This is the mod's coarse anhydrous fluoride-electrolyte abstraction. It deliberately is not a water electrolysis recipe.
		recipes.put(Fluids.HYDROFLUORIC_ACID, new ElectrolysisRecipe(com.hbm.realsim.ChemistryProcesses.fluorideElectrolysis()));





		//recipes.put(Fluids.MOLTEN_BARIUM_CHLORIDE,
		//	new ElectrolysisRecipe(
		//		1000,
		//		new FluidStack(Fluids.CHLORINE, 500),
		//		new FluidStack(Fluids.NONE, 0),
		//		new ItemStack(ModItems.ingot_barium, 1)
		//	)
		//);

	}

	public static HashMap getRecipes() {

		HashMap<Object, Object[]> recipes = new HashMap<Object, Object[]>();

		for(Entry<FluidType, ElectrolysisRecipe> entry : ElectrolyserFluidRecipes.recipes.entrySet()) {

			ElectrolysisRecipe recipe = entry.getValue();
			FluidStack input = new FluidStack(entry.getKey(), recipe.amount);
			List outputs = new ArrayList();
            if(recipe.physicalBill != null) {
                for(int i = 0; i < recipe.physicalBill.outputs.length; i++) {
                    String name = recipe.physicalBill.outputs[i]; if(name.startsWith("VENT_")) continue;
                    int count = Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(recipe.physicalBill.productMass[i], com.hbm.realsim.PhysicalRegistry.get(name).massUnits));
                    if(com.hbm.realsim.PhysicalRegistry.get(name).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) outputs.add(com.hbm.realsim.ProcessMachine.solid(name, count));
                    else outputs.add(ItemFluidIcon.make(Fluids.fromName(name), count));
                }
            } else {
                if(recipe.output1.type != Fluids.NONE) outputs.add(ItemFluidIcon.make(recipe.output1));
                if(recipe.output2.type != Fluids.NONE) outputs.add(ItemFluidIcon.make(recipe.output2));
                for(ItemStack byproduct : recipe.byproduct) outputs.add(byproduct);
            }

			recipes.put(ItemFluidIcon.make(input), outputs.toArray());
		}

		return recipes;
	}
	public static ElectrolysisRecipe getRecipe(FluidType type) {
		if(type == null)
			return null;
		return recipes.get(type);
	}

	@Override
	public String getFileName() {
		return "hbmElectrolyzerFluid.json";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void deleteRecipes() {
		recipes.clear();
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = (JsonObject) recipe;

		FluidStack input = this.readFluidStack(obj.get("input").getAsJsonArray());
		FluidStack output1 = this.readFluidStack(obj.get("output1").getAsJsonArray());
		FluidStack output2 = this.readFluidStack(obj.get("output2").getAsJsonArray());

		int duration = 20;
		if(obj.has("duration")) duration = obj.get("duration").getAsInt();

		ItemStack[] byproducts = new ItemStack[0];
		if(obj.has("byproducts")) byproducts = this.readItemStackArray(obj.get("byproducts").getAsJsonArray());

		if(!obj.has("physical") && com.hbm.realsim.ChemistryProcesses.electrolyserRequiresBill(input.type.getName())) throw new IllegalArgumentException("Released electrolysis requires a physical mass/work bill; regenerate defaults");
        if(obj.has("physical")) {
            com.hbm.realsim.ProcessRecipe bill = com.hbm.realsim.PetroleumProcesses.read(obj);
            if(bill.inputs.length != 1 || !bill.inputs[0].equals(input.type.getName())) throw new IllegalArgumentException("Electrolyser bill feed differs from its lookup key");
            recipes.put(input.type, new ElectrolysisRecipe(bill)); return;
        }
        recipes.put(input.type, new ElectrolysisRecipe(input.fill, output1, output2, duration, byproducts).physicalPower(obj.has("physicalPowerQuanta") ? Math.max(0, obj.get("physicalPowerQuanta").getAsInt()) : 0));
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		Entry<FluidType, ElectrolysisRecipe> rec = (Entry) recipe;

		if(rec.getValue().physicalBill != null) com.hbm.realsim.PetroleumProcesses.write(rec.getValue().physicalBill, writer);
        writer.name("input"); this.writeFluidStack(new FluidStack(rec.getKey(), rec.getValue().amount), writer);
		writer.name("output1"); this.writeFluidStack(rec.getValue().output1, writer);
		writer.name("output2"); this.writeFluidStack(rec.getValue().output2, writer);

		if(rec.getValue().byproduct != null && rec.getValue().byproduct.length > 0) {
			writer.name("byproducts").beginArray();
			for(ItemStack stack : rec.getValue().byproduct) this.writeItemStack(stack, writer);
			writer.endArray();
		}

		writer.name("duration").value(rec.getValue().duration);
		writer.name("physicalPowerQuanta").value(rec.getValue().physicalPowerQuanta);
	}

	public static class ElectrolysisRecipe {
		public FluidStack output1;
		public FluidStack output2;
		public int amount;
		public ItemStack[] byproduct;
		public int duration;
		public int physicalPowerQuanta;
        public com.hbm.realsim.ProcessRecipe physicalBill;
        public ElectrolysisRecipe(com.hbm.realsim.ProcessRecipe bill) {
            if(bill.inputs.length != 1 || bill.solidInputs.length != 0 || bill.workQuanta > (long) bill.duration * 2000)
                throw new IllegalArgumentException("Electrolyser requires one fluid feed and at most 20 kWe");
            physicalBill = bill; amount = bill.quantities[0]; duration = bill.duration;
            physicalPowerQuanta = (int) com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.workQuanta, bill.duration);
            output1 = new FluidStack(Fluids.NONE, 0); output2 = new FluidStack(Fluids.NONE, 0);
            int fluid = 0;
            for(int i = 0; i < bill.outputs.length; i++) {
                String name = bill.outputs[i];
                if(name.startsWith("VENT_") || com.hbm.realsim.PhysicalRegistry.get(name).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) continue;
                FluidStack display = new FluidStack(Fluids.fromName(name), Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[i], com.hbm.realsim.PhysicalRegistry.get(name).massUnits)));
                if(fluid == 0) output1 = display; else if(fluid == 1) output2 = display;
                if(++fluid > 4) throw new IllegalArgumentException("Electrolyser has four coproduct ports");
            }
            byproduct = new ItemStack[0];
        }
		public ElectrolysisRecipe physicalPower(int quanta) { physicalPowerQuanta = quanta; return this; }

		public ElectrolysisRecipe(int amount, FluidStack output1, FluidStack output2, ItemStack... byproduct) {
			this.output1 = output1;
			this.output2 = output2;
			this.amount = amount;
			this.byproduct = byproduct;
			this.duration = 20;
		}
		public ElectrolysisRecipe(int amount, FluidStack output1, FluidStack output2, int duration, ItemStack... byproduct) {
			this.output1 = output1;
			this.output2 = output2;
			this.amount = amount;
			this.byproduct = byproduct;
			this.duration = duration;
		}
	}


}
