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

		recipes.put(Fluids.AIR, new Quartet<>(
			new FluidStack(Fluids.NITROGEN, 78),
			new FluidStack(Fluids.OXYGEN, 21),
			new FluidStack(Fluids.ARGON, 1),
			new FluidStack(Fluids.NOBLE_GAS_MIX, 1)
		));

		recipes.put(Fluids.NOBLE_GAS_MIX, new Quartet<>(
			new FluidStack(Fluids.NEON, 40),
			new FluidStack(Fluids.CARBONDIOXIDE, 1),
			new FluidStack(Fluids.KRYPTON, 10),
			new FluidStack(Fluids.XENON, 5)
		));

		//Titan
		recipes.put(Fluids.TEKTOAIR, new Quartet<>(
				new FluidStack(Fluids.CHLORINE, 45),
				new FluidStack(Fluids.AROMATICS, 25),
				new FluidStack(Fluids.GAS, 15),
				new FluidStack(Fluids.PETROLEUM, 5)
				));

		//Jupiter
		recipes.put(Fluids.JOOLGAS, new Quartet<>(
				new FluidStack(Fluids.HELIUM3, 25),
				new FluidStack(Fluids.HYDROGEN, 25),
				new FluidStack(Fluids.CHLORINE, 20),
				new FluidStack(Fluids.NEON, 12)
				));

		//Neptune
		recipes.put(Fluids.NGAS, new Quartet<>(
				new FluidStack(Fluids.GAS, 35),
				new FluidStack(Fluids.AMMONIA, 24),
				new FluidStack(Fluids.UNSATURATEDS, 15),
				new FluidStack(Fluids.XENON, 14)
				));

		//Uranus
		recipes.put(Fluids.UGAS, new Quartet<>(
				new FluidStack(Fluids.OXYGEN, 35),
				new FluidStack(Fluids.NITROGEN, 25),
				new FluidStack(Fluids.METHANOL, 15),
				new FluidStack(Fluids.AROMATICS, 10)
				));

		//Saturn
		recipes.put(Fluids.SARNUSGAS, new Quartet<>(
				new FluidStack(Fluids.HYDROGEN, 45),
				new FluidStack(Fluids.HELIUM3, 25),
				new FluidStack(Fluids.GAS, 15),
				new FluidStack(Fluids.NITROGEN, 5)
				));

		//Venus
		recipes.put(Fluids.EVEAIR, new Quartet<>(
				new FluidStack(Fluids.KMnO4, 30),
				new FluidStack(Fluids.MERCURY, 25),
				new FluidStack(Fluids.XENON, 15),
				new FluidStack(Fluids.AMMONIA, 5)
				));

		//Mars
		recipes.put(Fluids.DUNAAIR, new Quartet<>(
				new FluidStack(Fluids.CARBONDIOXIDE, 80),
				new FluidStack(Fluids.NITROGEN, 10),
				new FluidStack(Fluids.ARGON, 8),
				new FluidStack(Fluids.OXYGEN, 2)
				));

		//brine -> iodine brine + salt water (which is just brine...)
		recipes.put(Fluids.BRINE, new Quartet<>(
				new FluidStack(Fluids.WATER, 30),
				new FluidStack(Fluids.IODINE_BRINE, 10),
				new FluidStack(Fluids.WATER, 30),
				new FluidStack(Fluids.IODINE_BRINE, 10)
				));

		//helium 4 production non retarded:
		recipes.put(Fluids.GAS, new Quartet<>(
			new FluidStack(Fluids.GAS, 10),
			new FluidStack(Fluids.HELIUM4, 1),
			new FluidStack(Fluids.GAS, 70), //methane
			new FluidStack(Fluids.HYDROGEN, 27)
			//new FluidStack(Fluids.PROPANE, 100),
			//new FluidStack(Fluids.BUTANE, 50) //DAMMIT BOBBY I DONT FEEL LIKE ADDING ALL THAT RIGHT NOW
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
