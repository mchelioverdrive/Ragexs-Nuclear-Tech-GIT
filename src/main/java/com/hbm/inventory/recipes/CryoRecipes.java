package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.util.Tuple.Quartet;

import net.minecraft.item.ItemStack;

public class CryoRecipes extends SerializableRecipe {

	private static HashMap<FluidType, Quartet<FluidStack, FluidStack, FluidStack, FluidStack>> recipes = new HashMap();

	@Override
	public void registerDefaults() {

		// Each operation consumes 100 standard gas L; the native separator caps admission at 6000 standard L/min.

        recipes.put(Fluids.RAW_NATURAL_GAS, new Quartet<>(new FluidStack(Fluids.GAS, 98), new FluidStack(Fluids.HELIUM4, 2), new FluidStack(Fluids.NONE, 0), new FluidStack(Fluids.NONE, 0)));

		//Earth
		recipes.put(Fluids.AIR, new Quartet<>(
			new FluidStack(Fluids.NITROGEN_GAS, 77),
			new FluidStack(Fluids.OXYGEN_GAS, 21),
			new FluidStack(Fluids.ARGON, 1),
			new FluidStack(Fluids.NOBLE_GAS_MIX, 1)
		));

		//Enriched noble-gas tail from air separation; argon remains dominant.
		recipes.put(Fluids.NOBLE_GAS_MIX, new Quartet<>(
			new FluidStack(Fluids.ARGON, 96),
			new FluidStack(Fluids.NEON, 2),
			new FluidStack(Fluids.KRYPTON, 1),
			new FluidStack(Fluids.XENON, 1)
		));

		//Titan
		//mostly nitrogen with methane hydrocarbons
		recipes.put(Fluids.TEKTOAIR, new Quartet<>(
			new FluidStack(Fluids.NITROGEN_GAS, 89),
			new FluidStack(Fluids.GAS, 8),
			new FluidStack(Fluids.UNSATURATEDS, 2),
			new FluidStack(Fluids.HYDROGEN_GAS, 1)
		));

		//Jupiter
		//H2/He with trace methane; ammonia is a cloud condensate, not bulk gas.
		recipes.put(Fluids.JOOLGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN_GAS, 89),
			new FluidStack(Fluids.HELIUM4, 10),
			new FluidStack(Fluids.GAS, 1),
			new FluidStack(Fluids.NONE, 0)
		));

		//Neptune
		//H2/He with methane enrichment; deeper ammonia/water ices are not upper atmosphere.
		recipes.put(Fluids.NGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN_GAS, 80),
			new FluidStack(Fluids.HELIUM4, 19),
			new FluidStack(Fluids.GAS, 1),
			new FluidStack(Fluids.NONE, 0)
		));

		//Uranus
		//H2/He with methane enrichment.
		recipes.put(Fluids.UGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN_GAS, 82),
			new FluidStack(Fluids.HELIUM4, 15),
			new FluidStack(Fluids.GAS, 3),
			new FluidStack(Fluids.NONE, 0)
		));

		//Saturn
		//H2/He with trace methane; depleted He compared to Jupiter.
		recipes.put(Fluids.SARNUSGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN_GAS, 96),
			new FluidStack(Fluids.HELIUM4, 3),
			new FluidStack(Fluids.GAS, 1),
			new FluidStack(Fluids.NONE, 0)
		));

		//Venus
		//hot CO2 sulfur atmosphere
		recipes.put(Fluids.EVEAIR, new Quartet<>(
			new FluidStack(Fluids.CARBONDIOXIDE, 95),
			new FluidStack(Fluids.NITROGEN_GAS, 3),
			new FluidStack(Fluids.SOURGAS, 1), // sulfur-bearing trace-gas abstraction
			new FluidStack(Fluids.ARGON, 1) //trace
		));

		//Mars
		recipes.put(Fluids.DUNAAIR, new Quartet<>(
			new FluidStack(Fluids.CARBONDIOXIDE, 95),
			new FluidStack(Fluids.NITROGEN_GAS, 3),
			new FluidStack(Fluids.ARGON, 1),
			new FluidStack(Fluids.OXYGEN_GAS, 1) //trace amounts do not remove
		));

		// Geological trace salts are recovered by the conserved mineral-brine chemical bill.
		// GAS is released pure methane; it cannot separate into helium, hydrogen or ethylene.



	} // this is such a sexy machine might use your code for atmospheric distillator

    public static com.hbm.realsim.ProcessRecipe physicalBill(FluidType input, Quartet<FluidStack, FluidStack, FluidStack, FluidStack> recipe) {
        if(recipe == null) return null;
        FluidStack[] streams = {recipe.getW(), recipe.getX(), recipe.getY(), recipe.getZ()};
        java.util.List<String> names = new java.util.ArrayList<>(); java.util.List<Long> masses = new java.util.ArrayList<>();
        com.hbm.realsim.PhysicalState source = com.hbm.realsim.ProcessRecipe.resolved(input.getName());
        if(source.basis != com.hbm.realsim.PhysicalState.Basis.STANDARD_GAS_LITER) throw new IllegalArgumentException("Cryogenic separator accepts reference gas feeds; phase conversion belongs in a liquefactor");
        long total = 0;
        for(FluidStack stream : streams) if(stream.fill > 0) {
            com.hbm.realsim.PhysicalState state = com.hbm.realsim.ProcessRecipe.resolved(stream.type.getName());
            if(state.basis != com.hbm.realsim.PhysicalState.Basis.STANDARD_GAS_LITER) throw new IllegalArgumentException("Gas separator cannot copy counts into a liquid phase");
            names.add(stream.type.getName()); long mass = Math.multiplyExact(state.massUnits, stream.fill); masses.add(mass); total += mass;
        }
        long rounding = source.massUnits * 100 - total;
        if(masses.isEmpty() || Math.abs(rounding) > com.hbm.realsim.PhysicalState.MASS_UNITS_PER_KG / 1000000000L) throw new IllegalArgumentException("Unbalanced gas separation");
        int last = masses.size() - 1; masses.set(last, masses.get(last) + rounding);
        long[] products = new long[masses.size()]; for(int i = 0; i < products.length; i++) products[i] = masses.get(i);
        return new com.hbm.realsim.ProcessRecipe("separation:" + input.getName(), new String[] {input.getName()}, new int[] {100}, new int[] {0}, null, 0,
                names.toArray(new String[0]), products, 20, 50000, 0, 20, true);
    }
    @Override public void registerPost() {
        for(Entry<FluidType, Quartet<FluidStack, FluidStack, FluidStack, FluidStack>> entry : recipes.entrySet()) physicalBill(entry.getKey(), entry.getValue());
    }

	public static Quartet<FluidStack, FluidStack, FluidStack, FluidStack> getOutput(FluidType type) {
		return recipes.get(type);
	}

	public static HashMap<Object, Object> getCryoRecipes() {

		HashMap<Object, Object> map = new HashMap<Object, Object>();

		for(Entry<FluidType, Quartet<FluidStack, FluidStack, FluidStack, FluidStack>> recipe : recipes.entrySet()) {
			map.put(ItemFluidIcon.make(recipe.getKey(), 1000),
					new ItemStack[] {
							ItemFluidIcon.make(recipe.getValue().getW().type,	recipe.getValue().getW().fill * 10),
							ItemFluidIcon.make(recipe.getValue().getX().type,	recipe.getValue().getX().fill * 10),
							ItemFluidIcon.make(recipe.getValue().getY().type,	recipe.getValue().getY().fill * 10),
							ItemFluidIcon.make(recipe.getValue().getZ().type,	recipe.getValue().getZ().fill * 10)});

		}

		return map;
	}

	@Override
	public String getFileName() {
		return "hbmCryodistillator.json";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = (JsonObject) recipe;

		FluidType input = Fluids.fromName(obj.get("input").getAsString());
		FluidStack output1 = this.readFluidStack(obj.get("output1").getAsJsonArray());
		FluidStack output2 = this.readFluidStack(obj.get("output2").getAsJsonArray());
		FluidStack output3 = this.readFluidStack(obj.get("output3").getAsJsonArray());
		FluidStack output4 = this.readFluidStack(obj.get("output4").getAsJsonArray());


		Quartet<FluidStack, FluidStack, FluidStack, FluidStack> separation = new Quartet<>(output1, output2, output3, output4);
        physicalBill(input, separation);
        recipes.put(input, separation);
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		Entry<FluidType, Quartet<FluidStack, FluidStack, FluidStack, FluidStack>> rec = (Entry<FluidType, Quartet<FluidStack, FluidStack, FluidStack, FluidStack>>) recipe;

		writer.name("input").value(rec.getKey().getName());
		writer.name("output1"); this.writeFluidStack(rec.getValue().getW(), writer);
		writer.name("output2"); this.writeFluidStack(rec.getValue().getZ(), writer);
		writer.name("output3"); this.writeFluidStack(rec.getValue().getY(), writer);
		writer.name("output4"); this.writeFluidStack(rec.getValue().getX(), writer);

	}

	@Override
	public void deleteRecipes() {
		recipes.clear();
	}
}
