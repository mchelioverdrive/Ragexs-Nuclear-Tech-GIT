package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import static com.hbm.inventory.OreDictManager.*;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.EmptyAStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.main.MainRegistry;
import com.hbm.util.Tuple.Pair;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class CyclotronRecipes extends SerializableRecipe {

	public static HashMap<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> recipes = new HashMap();


	//francium
	//makeRecipe(new ComparableStack(ModItems.nugget_th232), new ComparableStack(ModItems.ingot_actinium), new ItemStack(ModItems.francium_ingot), coA);



	@Override
	public void registerDefaults() {

		/// LITHIUM START ///
		int liA = 50;

		/// TODO Actual Lithium reactions (light element reactions)
		//makeRecipe(new ComparableStack(ModItems.part_lithium), new OreDictStack("dustLithium"), new ItemStack(ModItems.powder_helium), 50); // Li → He
		//helium... powder?
		makeRecipe(new ComparableStack(ModItems.part_lithium), new OreDictStack("dustBeryllium"), new ItemStack(ModItems.part_carbon), 6); // Li + Be → C (simplified)


		/// LITHIUM END ///

		/// BERYLLIUM START ///
		int beA = 25;

		/// TODO Beryllium reactions

		/// BERYLLIUM END ///

		/// CARBON START ///
		int caA = 10;



		/// CARBON END ///

		/// COPPER START ///
		int coA = 15;

		/// Copper reactions (medium-heavy element)
		makeRecipe(new ComparableStack(ModItems.part_copper), new OreDictStack("dustNickel"), new ItemStack(ModItems.powder_cobalt), 1);
		makeRecipe(new ComparableStack(ModItems.part_copper), new OreDictStack("dustZinc"), new ItemStack(ModItems.powder_gallium), 1);



		makeRecipe(new ComparableStack(ModItems.neutron_reflector), new OreDictStack("dustBismuth"), new ItemStack(ModItems.powder_polonium), coA);

		//actinium is made via uranium decay...
		//makeRecipe(new ComparableStack(ModItems.part_copper), new ComparableStack(ModItems.powder_uranium), new ItemStack(ModItems.powder_actinium), coA);

		//bobshart didn't add null support so I'm trying this...
		//makeRecipe(new ComparableStack(ModItems.powder_uranium), new EmptyAStack(), new ItemStack(ModItems.powder_actinium), 200);
		//I give up. Fuck you and your entire shitty ass cyclotron system.
		makeRecipe(
			new ComparableStack(ModItems.powder_uranium),
			new ComparableStack(Items.redstone),
			new ItemStack(ModItems.powder_actinium),
			2
		);

		/// COPPER END ///

		/// PLUTONIUM START ///
		int plA = 100;

		/// Plutonium production via decay chain
		makeRecipe(new ComparableStack(ModItems.powder_uranium), new OreDictStack("nuggetUranium238"), new ItemStack(ModItems.nugget_pu239), 5);

		/// PLUTONIUM END ///

		///TODO: fictional elements
		//not sure how thorium doesn't make sense here but ok sure gpt
		/// Francium production (realistic parent: Actinium)
		makeRecipe(new ComparableStack(ModItems.ingot_actinium), new OreDictStack("dustActinium227"), new ItemStack(ModItems.francium_ingot), 3);

		//rutherfordium
		//graphite is our stand in for carbon-12
		//makeRecipe(new ComparableStack(ModItems.nugget_cf249), new ComparableStack(ModItems.ingot_graphite), new ItemStack(ModItems.rutherfordium_nugget), 4);

		//all neutron capture reactions moved to exposure chamber bc cyclotron doesn't preserve particle containers
		// Cm-248 neutron activation -> Cf-249 (simplified breeder path)
		//makeRecipe(new ComparableStack(ModItems.ingot_cm247), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_cm248), 5);
		//makeRecipe(new ComparableStack(ModItems.ingot_cm248), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_cf249), 5);
		// Californium breeding chain
		//makeRecipe(new ComparableStack(ModItems.nugget_cf249),
		//		   new ComparableStack(ModItems.particle_neutron),
		//		   new ItemStack(ModItems.nugget_cf250), 5);
		//makeRecipe(new ComparableStack(ModItems.nugget_cf250),
		//		   new ComparableStack(ModItems.particle_neutron),
		//		   new ItemStack(ModItems.nugget_cf251), 6);
		//makeRecipe(new ComparableStack(ModItems.nugget_cf251),
		//		   new ComparableStack(ModItems.particle_neutron),
		//		   new ItemStack(ModItems.nugget_cf252), 7);
		////plutonium 239 to 240 via neutron capture
		//makeRecipe(new ComparableStack(ModItems.nugget_pu239), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_pu240), 3);
		////plutonium 240 to 241 via neutron capture
		//makeRecipe(new ComparableStack(ModItems.nugget_pu240), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_pu241), 4);
		//am 242 from am241
		//makeRecipe(new ComparableStack(ModItems.nugget_am241), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_am242), 4);
		//Cf-252 + neutron -> Es-253
		//makeRecipe(new ComparableStack(ModItems.nugget_cf252), new ComparableStack(ModItems.particle_neutron), new ItemStack(ModItems.nugget_es253), 8);

		//dubnium
		//poison powder is made from nitrogen, so it's almost accurate. I'll add solid nitrogen later.
		makeRecipe(new ComparableStack(ModItems.nugget_cf249), new ComparableStack(ModItems.powder_poison), new ItemStack(ModItems.dubnium_nugget), 6);

		//americium 241 from plutonium 241
		//should be decay but thats pain
		makeRecipe(new ComparableStack(ModItems.nugget_pu241), new ComparableStack(Items.redstone), new ItemStack(ModItems.nugget_am241), 4);

		makeRecipe(new ComparableStack(ModItems.part_carbon), new ComparableStack(ModItems.nugget_cf249),
				   new ItemStack(ModItems.rutherfordium_nugget), 4);

		//TODO gold -> more anti matter than all other reactions (the final number is antimatter fluid amount)

	}

	private static void makeRecipe(ComparableStack part, AStack in, ItemStack out, int amat) {
		recipes.put(new Pair(part, in), new Pair(out, amat));
	}

	public static Object[] getOutput(ItemStack stack, ItemStack box) {

		if(stack == null || stack.getItem() == null || box == null)
			return null;

		ComparableStack boxStack = new ComparableStack(box).makeSingular();
		ComparableStack comp = new ComparableStack(stack).makeSingular();

		//boo hoo we iterate over a hash map, cry me a river
		Pair<ItemStack, Integer> fallback = null;

		for(Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> entry : recipes.entrySet()) {

			//System.out.println("---- CHECKING RECIPE ----");
			//System.out.println("Part: " + entry.getKey().getKey().toStack());
			//System.out.println("Input type: " + entry.getKey().getValue().getClass().getSimpleName());
			//System.out.println("BoxStack: " + boxStack.toStack());
			//System.out.println("Input stack: " + (stack == null ? "null" : stack.toString()));
			//OK SHUT UP

			AStack input = entry.getKey().getValue();

			if(entry.getKey().getKey().isApplicable(boxStack)) {

				// FIRST: try specific recipes
				if(!(input instanceof EmptyAStack) && input.matchesRecipe(stack, true)) {
					return new Object[] {
						entry.getValue().getKey().copy(),
						entry.getValue().getValue()
					};
				}

				// store fallback (EmptyAStack)
				if(input instanceof EmptyAStack) {
					fallback = entry.getValue();
				}
			}
		}

		// ONLY use EmptyAStack if nothing else matched
		if(fallback != null) {
			return new Object[] {
				fallback.getKey().copy(),
				fallback.getValue()
			};
		}

		//there's literally 0 reason why this doesn't work yet it refuses, fuck this

		/*Pair<ItemStack, Integer> output = recipes.get(new Pair(boxStack, comp));

		if(output != null) {
			return new Object[] { output.getKey().copy(), output.getValue() };
		}

		for(String name : ItemStackUtil.getOreDictNames(stack)) {
			OreDictStack ods = new OreDictStack(name);
			output = recipes.get(new Pair(new ComparableStack(ModItems.part_beryllium), new OreDictStack("dustCobalt")));

			if(output != null) {
				return new Object[] { output.getKey().copy(), output.getValue() };
			}
		}*/

		return null;
	}

	public static Map<Object[], Object> getRecipes() {

		Map<Object[], Object> map = new HashMap<Object[], Object>();

		for(Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> entry : recipes.entrySet()) {
			AStack input = entry.getKey().getValue();
			if (input == null) continue;

			List<ItemStack> stack = input.extractForNEI();

			if(stack == null) continue;

			for(ItemStack ingredient : stack) {
				if(ingredient == null) continue;

				map.put(new ItemStack[] {
					entry.getKey().getKey().toStack(),
					ingredient
				}, entry.getValue().getKey());
			}
		}

		return map;
	}

	@Override
	public String getFileName() {
		return "hbmCyclotron.json";
	}

	@Override
	public Object getRecipeObject() {
		return this.recipes;
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonArray particle = ((JsonObject)recipe).get("particle").getAsJsonArray();
		JsonArray input = ((JsonObject)recipe).get("input").getAsJsonArray();
		JsonArray output = ((JsonObject)recipe).get("output").getAsJsonArray();
		int antimatter = ((JsonObject)recipe).get("antimatter").getAsInt();
		ItemStack partStack = this.readItemStack(particle);
		AStack inStack = this.readAStack(input);
		ItemStack outStack = this.readItemStack(output);

		this.recipes.put(new Pair(new ComparableStack(partStack), inStack),  new Pair(outStack, antimatter));
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		try{
			Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>> rec = (Entry<Pair<ComparableStack, AStack>, Pair<ItemStack, Integer>>) recipe;

			writer.name("particle");
			this.writeItemStack(rec.getKey().getKey().toStack(), writer);
			writer.name("input");
			this.writeAStack(rec.getKey().getValue(), writer);
			writer.name("output");
			this.writeItemStack(rec.getValue().getKey(), writer);
			writer.name("antimatter").value(rec.getValue().getValue());

		} catch(Exception ex) {
			MainRegistry.logger.error(ex);
			ex.printStackTrace();
		}
	}



	@Override
	public void deleteRecipes() {
		this.recipes.clear();
	}

	@Override
	public String getComment() {
		return "The particle item, while being an input, has to be defined as an item stack without ore dictionary support.";
	}
}
