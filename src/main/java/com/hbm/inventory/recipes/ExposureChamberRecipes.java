package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import static com.hbm.inventory.OreDictManager.*;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;

import com.hbm.items.machine.ItemBreedingRod;
import com.hbm.items.machine.ItemCircuit;
import com.hbm.items.machine.ItemZirnoxRod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ExposureChamberRecipes extends SerializableRecipe {

	public static List<ExposureChamberRecipe> recipes = new ArrayList();

	@Override
	public void registerDefaults() {
		//recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_higgs), new OreDictStack(U.ingot()), new ItemStack(ModItems.ingot_schraranium)));
		//recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_higgs), new OreDictStack(U238.ingot()), new ItemStack(ModItems.ingot_schrabidium)));
		//die; oh this machine no longer has a use. Great so what are exposure chambers used for in real life?
		//recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_dark), new OreDictStack(PU.ingot()), new ItemStack(ModItems.ingot_euphemium)));
		//recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_sparkticle), new OreDictStack(SBD.ingot()), new ItemStack(ModItems.ingot_dineutronium)));

		//item.particle_aelectron.name=Positron Capsule
		//item.particle_amat.name=Antimatter Capsule
		//item.particle_aproton.name=Antiproton Capsule
		//item.particle_aschrab.name=Anti Unbihexium Capsule
		//item.particle_copper.name=Copper Ion Capsule
		//item.particle_dark.name=Dark Matter Capsule
		//item.particle_digamma.name=§cThe Digamma Particle§r
		//item.particle_empty.name=Empty Particle Capsule
		//item.particle_higgs.name=Higgs Boson Capsule
		//item.particle_hydrogen.name=Hydrogen Ion Capsule
		//item.particle_lead.name=Lead Ion Capsule
		//item.particle_lutece.name=Lutece Quasiparticle
		//item.particle_muon.name=Muon Capsule
		//item.particle_sparkticle.name=Sparkticle Capsule
		//item.particle_strange.name=Strange Quark Capsule
		//item.particle_tachyon.name=Tachyon Capsule

		//particle_neutron + cobalt_ingot → cobalt_60_ingot
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_aelectron), new OreDictStack(CO.ingot()), new ItemStack(ModItems.ingot_co60)));

		//particle_neutron + uranium_ingot → enriched_uranium
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_aelectron), new OreDictStack(U.ingot()), new ItemStack(ModItems.ingot_uranium_fuel)));

		//Semiconductor doping
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_copper), new OreDictStack(SI.nugget()),
											  new ComparableStack(ModItems.circuit, 8, ItemCircuit.EnumCircuitType.CHIP_BISMOID).toStack()));

		//particle_neutron + lithium → tritium
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_aelectron),
											  new ComparableStack(new ItemStack(ModItems.rod_quad, 1,
																				ItemBreedingRod.BreedingRodType.LITHIUM.ordinal())),
											  new ItemStack(ModItems.rod_quad, 1,
															ItemBreedingRod.BreedingRodType.TRITIUM.ordinal())));

		////am 242 from am241
		//		makeRecipe(new ComparableStack(ModItems.nugget_am241), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_am242), 4);
		//
		//		//Cf-252 + neutron -> Es-253
		//		makeRecipe(new ComparableStack(ModItems.nugget_cf252), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_es253), 8);
		//TODO transfer particle recipes from cyclotron to exposure chamber
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_am241),
											  new ItemStack(ModItems.nugget_am242)));
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_cf252),
											  new ItemStack(ModItems.nugget_es253)));

		////plutonium 239 to 240 via neutron capture
		//		makeRecipe(new ComparableStack(ModItems.nugget_pu239), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_pu240), 3);
		//
		//		//plutonium 240 to 241 via neutron capture
		//		makeRecipe(new ComparableStack(ModItems.nugget_pu240), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_pu241), 4);
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_pu239),
											  new ItemStack(ModItems.nugget_pu240)));
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_pu240),
											  new ItemStack(ModItems.nugget_pu241)));

		//// Californium breeding chain
		//		makeRecipe(new ComparableStack(ModItems.nugget_cf249),
		//				   new ComparableStack(ModItems.particle_neutron),
		//				   new ItemStack(ModItems.nugget_cf250), 5);
		//
		//		makeRecipe(new ComparableStack(ModItems.nugget_cf250),
		//				   new ComparableStack(ModItems.particle_neutron),
		//				   new ItemStack(ModItems.nugget_cf251), 6);
		//
		//		makeRecipe(new ComparableStack(ModItems.nugget_cf251),
		//				   new ComparableStack(ModItems.particle_neutron),
		//				   new ItemStack(ModItems.nugget_cf252), 7);
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_cf249),
											  new ItemStack(ModItems.nugget_cf250)));
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_cf250),
											  new ItemStack(ModItems.nugget_cf251)));
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.nugget_cf251),
											  new ItemStack(ModItems.nugget_cf252)));

		//// Cm-248 neutron activation -> Cf-249 (simplified breeder path)
		//		makeRecipe(new ComparableStack(ModItems.ingot_cm247), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_cm248), 5);
		//		makeRecipe(new ComparableStack(ModItems.ingot_cm248), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_cf249), 5);
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.ingot_cm247),
											  new ItemStack(ModItems.nugget_cm248)));
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new ComparableStack(ModItems.ingot_cm248),
											  new ItemStack(ModItems.nugget_cf249)));

		//		makeRecipe(new ComparableStack(ModItems.neutron_reflector), new OreDictStack("dustBismuth"), new ItemStack(ModItems.powder_polonium), coA);
		recipes.add(new ExposureChamberRecipe(new ComparableStack(ModItems.particle_neutron),
											  new OreDictStack("dustBismuth"),
											  new ItemStack(ModItems.powder_polonium)));



	}

	public static ExposureChamberRecipe getRecipe(ItemStack particle, ItemStack input) {
		for(ExposureChamberRecipe recipe : recipes) if(recipe.particle.matchesRecipe(particle, true) && recipe.ingredient.matchesRecipe(input, true)) return recipe;
		return null;
	}

	public static HashMap getRecipes() {

		HashMap<Object, Object> recipes = new HashMap<Object, Object>();

		for(ExposureChamberRecipe recipe : ExposureChamberRecipes.recipes) {

			Object[] array = new Object[2];

			array[1] = recipe.particle;
			AStack stack = recipe.ingredient.copy();
			stack.stacksize = 8;
			array[0] = stack;
			ItemStack output = recipe.output.copy();
			output.stackSize = 8;

			recipes.put(array, output);
		}

		return recipes;
	}

	@Override
	public String getFileName() {
		return "hbmExposureChamber.json";
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

		AStack particle = this.readAStack(obj.get("particle").getAsJsonArray());
		AStack ingredient = this.readAStack(obj.get("ingredient").getAsJsonArray());
		ItemStack output = this.readItemStack(obj.get("output").getAsJsonArray());

		ExposureChamberRecipe rec = new ExposureChamberRecipe(particle, ingredient, output);
		recipes.add(rec);
	}

	@Override
	public void writeRecipe(Object o, JsonWriter writer) throws IOException {
		ExposureChamberRecipe recipe = (ExposureChamberRecipe) o;

		writer.name("particle");
		this.writeAStack(recipe.particle, writer);
		writer.name("ingredient");
		this.writeAStack(recipe.ingredient, writer);
		writer.name("output");
		this.writeItemStack(recipe.output, writer);
	}

	public static class ExposureChamberRecipe {

		public AStack particle;
		public AStack ingredient;
		public ItemStack output;

		public ExposureChamberRecipe(AStack particle, AStack ingredient, ItemStack output) {
			this.particle = particle;
			this.ingredient = ingredient;
			this.output = output;
		}
	}
}
