package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.hbm.inventory.OreDictManager.*;
import static com.hbm.inventory.material.Mats.*;
import static com.hbm.inventory.material.MaterialShapes.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemScraps;

import net.minecraft.item.ItemStack;

public class RotaryFurnaceRecipes extends SerializableRecipe {

	public static List<RotaryFurnaceRecipe> recipes = new ArrayList();

	@Override
	public void registerDefaults() {

		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_STEEL, INGOT.q(1)), 100, 1, new OreDictStack(IRON.ingot()), new OreDictStack(COAL.gem())));
		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_STEEL, INGOT.q(1)), 100, 1, new OreDictStack(IRON.ingot()), new OreDictStack(ANY_COKE.gem())));

		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_STEEL, INGOT.q(1)), 100, 1, new OreDictStack(IRON.fragment(), 9), new OreDictStack(COAL.gem())));
		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_STEEL, INGOT.q(1)), 100, 1, new OreDictStack(IRON.fragment(), 9), new OreDictStack(ANY_COKE.gem())));
		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_STEEL, INGOT.q(1)), 200, 1, new OreDictStack(IRON.fragment(), 9), new OreDictStack(ANY_COKE.gem()), new ComparableStack(ModItems.powder_flux)));

		//recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_DESH, INGOT.q(1)), 100, 200, new FluidStack(Fluids.LIGHTOIL, 100), new ComparableStack(ModItems.powder_desh_ready)));

		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_GUNMETAL, INGOT.q(4)), 200, 1, new OreDictStack(CU.ingot(), 3), new OreDictStack(Sn.ingot(), 1)));
		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_WEAPONSTEEL, INGOT.q(1)), 200, 2, new FluidStack(Fluids.GAS_COKER, 4), new OreDictStack(STEEL.ingot(), 1), new ComparableStack(ModItems.powder_flux, 2)));
		recipes.add(new RotaryFurnaceRecipe(new MaterialStack(MAT_SATURN, INGOT.q(2)), 200, 2, new FluidStack(Fluids.REFORMGAS, 4), new OreDictStack(DURA.dust(), 4), new OreDictStack(CU.dust())));
	}

	public static HashMap getRecipes() {

		HashMap<Object, Object> recipes = new HashMap<Object, Object>();

		for(RotaryFurnaceRecipe recipe : RotaryFurnaceRecipes.recipes) {

			int size = recipe.ingredients.length + (recipe.fluid != null ? 1 : 0) + 1;
			Object[] array = new Object[size];

			for(int i = 0; i < recipe.ingredients.length; i++) {
				array[i] = recipe.ingredients[i];
			}

			if(recipe.fluid != null) array[recipe.ingredients.length] = ItemFluidIcon.make(recipe.fluid);
            List<ItemStack> fuelChoices = new ArrayList<>();
            for(ItemStack candidate : MachineRecipes.instance().getAlloyFuels()) if(com.hbm.realsim.SolidFuel.resolve(candidate) != null) {
                ItemStack fuel = candidate.copy(); fuel.stackSize = 1;
                if(!fuel.hasTagCompound()) fuel.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
                fuel.getTagCompound().setBoolean("neiRotaryFuel", true); fuelChoices.add(fuel);
            }
            array[size - 1] = fuelChoices.toArray(new ItemStack[0]);

			recipes.put(array, ItemScraps.create(recipe.output, true));
		}

		return recipes;
	}

	public static RotaryFurnaceRecipe getRecipe(ItemStack... inputs) {

		outer:
		for(RotaryFurnaceRecipe recipe : recipes) {

			List<AStack> recipeList = new ArrayList();
			for(AStack ingredient : recipe.ingredients) recipeList.add(ingredient);

			for(int i = 0; i < inputs.length; i++) {

				ItemStack inputStack = inputs[i];

				if(inputStack != null) {

					boolean hasMatch = false;
					Iterator<AStack> iterator = recipeList.iterator();

					while(iterator.hasNext()) {
						AStack recipeStack = iterator.next();

						if(recipeStack.matchesRecipe(inputStack, true) && inputStack.stackSize >= recipeStack.stacksize) {
							hasMatch = true;
							recipeList.remove(recipeStack);
							break;
						}
					}

					if(!hasMatch) {
						continue outer;
					}
				}
			}

			if(recipeList.isEmpty()) return recipe;
		}

		return null;
	}

	@Override
	public String getFileName() {
		return "hbmRotaryFurnace.json";
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

		AStack[] inputs = this.readAStackArray(obj.get("inputs").getAsJsonArray());
		FluidStack fluid = obj.has("fluid") ? this.readFluidStack(obj.get("fluid").getAsJsonArray()) : null;

		JsonArray array = obj.get("output").getAsJsonArray();
		MaterialStack stack = new MaterialStack(Mats.matByName.get(array.get(0).getAsString()), array.get(1).getAsInt());

		int duration = obj.get("duration").getAsInt();
		int steam = obj.get("steam").getAsInt();

		recipes.add(new RotaryFurnaceRecipe(stack, duration, steam, fluid, inputs));
	}

	@Override
	public void writeRecipe(Object obj, JsonWriter writer) throws IOException {
		RotaryFurnaceRecipe recipe = (RotaryFurnaceRecipe) obj;

		writer.name("inputs").beginArray();
		for(AStack aStack : recipe.ingredients) {
			this.writeAStack(aStack, writer);
		}
		writer.endArray();

		if(recipe.fluid != null) {
			writer.name("fluid");
			this.writeFluidStack(recipe.fluid, writer);
		}

		writer.name("output").beginArray();
		writer.setIndent("");
		writer.value(recipe.output.material.names[0]).value(recipe.output.amount);
		writer.endArray();
		writer.setIndent("  ");

		writer.name("duration").value(recipe.duration);
		writer.name("steam").value(recipe.steam);
	}

	public static class RotaryFurnaceRecipe {

		public AStack[] ingredients;
		public FluidStack fluid;
		public MaterialStack output;
		public int duration;
        public long thermalJoules;
		public int steam; // ordinary steam counts/tick, 20 kW shaft per count at adopted 40% expansion efficiency

		public RotaryFurnaceRecipe(MaterialStack output, int duration, int steam, FluidStack fluid, AStack... ingredients) {
			if(duration <= 0 || steam <= 0 || steam > 4) throw new IllegalArgumentException("Rotary drive requires 1..4 ordinary-steam counts/tick; migrate legacy steam-token recipes");
            this.ingredients = ingredients;
			this.fluid = fluid;
			this.output = output;
			if(output == null || output.amount <= 0) throw new IllegalArgumentException("Missing rotary product");
            this.thermalJoules = com.hbm.realsim.PetroleumProcesses.ceilDiv(Math.multiplyExact((long) output.amount, 1800000L), com.hbm.inventory.material.MaterialShapes.INGOT.q(1));
            // Paid melting/process heat at a 500 kW firebox, independent of the steam drive.
            this.duration = Math.max(duration, Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(thermalJoules * 20L, 500000L)));
			this.steam = steam;
		}

		public RotaryFurnaceRecipe(MaterialStack output, int duration, int steam, AStack... ingredients) {
			this(output, duration, steam, null, ingredients);
		}
	}
}
