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

		//assume there's a 0 after all these numbers because that's how this works for some reason (in mb)

		//Earth
		recipes.put(Fluids.AIR, new Quartet<>(
			new FluidStack(Fluids.NITROGEN, 78),
			new FluidStack(Fluids.OXYGEN, 21),
			new FluidStack(Fluids.ARGON, 1),
			new FluidStack(Fluids.NOBLE_GAS_MIX, 1)
		));

		//trace atmospheric noble gases
		recipes.put(Fluids.NOBLE_GAS_MIX, new Quartet<>(
			new FluidStack(Fluids.NEON, 60),
			new FluidStack(Fluids.KRYPTON, 20),
			new FluidStack(Fluids.XENON, 5),
			new FluidStack(Fluids.ARGON, 15)
		));

		//Titan
		//mostly nitrogen with methane hydrocarbons
		recipes.put(Fluids.TEKTOAIR, new Quartet<>(
			new FluidStack(Fluids.NITROGEN, 90),
			new FluidStack(Fluids.GAS, 8),
			new FluidStack(Fluids.UNSATURATEDS, 2),
			new FluidStack(Fluids.HYDROGEN, 1)
		));

		//Jupiter
		//mostly hydrogen and helium
		recipes.put(Fluids.JOOLGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN, 89),
			new FluidStack(Fluids.HELIUM4, 10),
			new FluidStack(Fluids.AMMONIA, 1),
			new FluidStack(Fluids.NEON, 1)
		));

		//Neptune
		//hydrogen helium methane ice giant
		recipes.put(Fluids.NGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN, 80),
			new FluidStack(Fluids.HELIUM4, 19),
			new FluidStack(Fluids.GAS, 5),
			new FluidStack(Fluids.AMMONIA, 1)
		));

		//Uranus
		//very similar to Neptune
		recipes.put(Fluids.UGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN, 82),
			new FluidStack(Fluids.HELIUM4, 15),
			new FluidStack(Fluids.GAS, 5),
			new FluidStack(Fluids.AMMONIA, 1)
		));

		//Saturn
		recipes.put(Fluids.SARNUSGAS, new Quartet<>(
			new FluidStack(Fluids.HYDROGEN, 96),
			new FluidStack(Fluids.HELIUM4, 3),
			new FluidStack(Fluids.AMMONIA, 1),
			new FluidStack(Fluids.NEON, 1)
		));

		//Venus
		//hot CO2 sulfur atmosphere
		recipes.put(Fluids.EVEAIR, new Quartet<>(
			new FluidStack(Fluids.CARBONDIOXIDE, 96),
			new FluidStack(Fluids.NITROGEN, 3),
			new FluidStack(Fluids.SOURGAS, 1), //I'll add SULFUR_DIOXIDE later
			new FluidStack(Fluids.ARGON, 1)
		));

		//Mars
		recipes.put(Fluids.DUNAAIR, new Quartet<>(
			new FluidStack(Fluids.CARBONDIOXIDE, 95),
			new FluidStack(Fluids.NITROGEN, 3),
			new FluidStack(Fluids.ARGON, 2),
			new FluidStack(Fluids.OXYGEN, 1)
		));

		//brine -> iodine brine + salt water (which is just brine...)
		recipes.put(Fluids.BRINE, new Quartet<>(
			new FluidStack(Fluids.WATER, 70),
			new FluidStack(Fluids.IODINE_BRINE, 10),
			new FluidStack(Fluids.BRINE, 20), //salt water = brine
			new FluidStack(Fluids.BROMINE, 1)
		));

		//helium 4 production non retarded:
		//recipes.put(Fluids.GAS, new Quartet<>(
		//	new FluidStack(Fluids.GAS, 10),
		//	new FluidStack(Fluids.HELIUM4, 1),
		//	new FluidStack(Fluids.GAS, 70), //methane
		//	new FluidStack(Fluids.HYDROGEN, 27)
		//	//new FluidStack(Fluids.PROPANE, 100),
		//	//new FluidStack(Fluids.BUTANE, 50) //DAMMIT BOBBY I DONT FEEL LIKE ADDING ALL THAT RIGHT NOW
		//));
		recipes.put(Fluids.GAS, new Quartet<>(
			new FluidStack(Fluids.GAS, 85),
			new FluidStack(Fluids.HYDROGEN, 10),
			new FluidStack(Fluids.HELIUM4, 1),
			new FluidStack(Fluids.UNSATURATEDS, 4)
		));


	} // this is such a sexy machine might use your code for atmospheric distillator

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


		recipes.put(input, new Quartet(output1, output2, output3, output4));
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
