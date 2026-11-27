package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.hbm.inventory.OreDictManager.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ItemGenericPart.EnumPartType;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemMissile;
import com.hbm.items.weapon.MissileDefinition;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemArcElectrode.EnumElectrodeType;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ArcWelderRecipes extends SerializableRecipe {

	public static List<ArcWelderRecipe> recipes = new ArrayList();

	@Override
	public void registerDefaults() {

		//Parts
		// Motors are retained in the assembler: welding can make their housings, but it cannot
		// place the rotor, bearings, insulated windings, and end hardware represented by the part.
		// recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.motor, 2), 100, 400L,
		// 		new OreDictStack(STEEL.plate(), 1), new ComparableStack(ModItems.coil_copper), new ComparableStack(ModItems.coil_copper_torus)));
		recipes.add(new ArcWelderRecipe(DictFrame.fromOne(ModItems.part_generic, EnumPartType.LDE), 200, 5_000L,
				new OreDictStack(AL.plate(), 4), new OreDictStack(FIBER.ingot(), 4), new OreDictStack(ANY_HARDPLASTIC.ingot())));
		recipes.add(new ArcWelderRecipe(DictFrame.fromOne(ModItems.part_generic, EnumPartType.LDE), 200, 10_000L,
				new OreDictStack(TI.plate(), 2), new OreDictStack(FIBER.ingot(), 4), new OreDictStack(ANY_HARDPLASTIC.ingot())));
		recipes.add(new ArcWelderRecipe(DictFrame.fromOne(ModItems.part_generic, EnumPartType.HDE), 600, 25_000_000L, new FluidStack(Fluids.STELLAR_FLUX, 4_000),
				new OreDictStack(ANY_BISMOIDBRONZE.plateCast(), 2), new OreDictStack(CMB.plateWelded(), 1), new ComparableStack(ModItems.ingot_cft)));

		//Dense Wires
		// Dense wire is bundled and insulated in the assembler rather than structurally welded.
		// recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.wire_dense, 1, Mats.MAT_ALLOY.id), 100, 10_000L,
		// 		new OreDictStack(ALLOY.wireFine(), 8)));

		//earlygame welded parts
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_IRON.id), 100, 100L,
				new OreDictStack(IRON.plateCast(), 2)));
		//high-demand mid-game parts
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_STEEL.id), 100, 500L,
				new OreDictStack(STEEL.plateCast(), 2)));
		//literally just the combination oven
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_COPPER.id), 200, 1_000L,
				new OreDictStack(CU.plateCast(), 2)));
		//mid-game, single combustion engine running on LPG
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_TITANIUM.id), 600, 50_000L,
				new OreDictStack(TI.plateCast(), 2)));
		//mid-game PWR
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_ZIRCONIUM.id), 600, 10_000L,
				new OreDictStack(ZR.plateCast(), 2)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_ALUMINIUM.id), 300, 10_000L,
				new OreDictStack(AL.plateCast(), 2)));
		// Shield gas is vented: 12 standard L/min argon, not oxygen as an inert blanket.
		//late-game fusion
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_TCALLOY.id), 1_200, 4_000L, new FluidStack(Fluids.ARGON, 12),
				new OreDictStack(TCALLOY.plateCast(), 2)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_CDALLOY.id), 1_200, 4_000L, new FluidStack(Fluids.ARGON, 12),
				new OreDictStack(CDALLOY.plateCast(), 2)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_TUNGSTEN.id), 1_200, 3_000L, new FluidStack(Fluids.ARGON, 12),
				new OreDictStack(W.plateCast(), 2)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_CMB.id), 1_200, 8_000L, new FluidStack(Fluids.REFORMGAS, 20),
				new OreDictStack(CMB.plateCast(), 2)));
		//pre-DFC
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.plate_welded, 1, Mats.MAT_OSMIRIDIUM.id), 6_000, 10_000L, new FluidStack(Fluids.REFORMGAS, 100),
				new OreDictStack(OSMIRIDIUM.plateCast(), 2)));

		//Missile Parts
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.thruster_small), 160, 10_000L, new OreDictStack(STEEL.plate(), 4), new OreDictStack(AL.wireFine(), 4), new OreDictStack(CU.plate(), 4)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.thruster_medium), 200, 20_000L, new OreDictStack(STEEL.plate(), 8), new ComparableStack(ModItems.motor, 1), new OreDictStack(GRAPHITE.ingot(), 8)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.thruster_large), 300, 50_000L, new OreDictStack(DURA.ingot(), 10), new ComparableStack(ModItems.motor, 1), new OreDictStack(OreDictManager.getReflector(), 12)));

		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.fuel_tank_small), 160, 1_000L, new OreDictStack(AL.plate(), 6), new ComparableStack(ModBlocks.steel_scaffold, 4), new OreDictStack(ANY_RUBBER.ingot(), 2)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.fuel_tank_medium), 200, 2_000L, new OreDictStack(AL.plateCast(), 4), new OreDictStack(TI.plate(), 8), new ComparableStack(ModBlocks.steel_scaffold, 12), new OreDictStack(ANY_RUBBER.ingot(), 4)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.fuel_tank_large), 300, 5_000L, new OreDictStack(AL.plateWelded(), 8), new OreDictStack(BIGMT.plate(), 12), new ComparableStack(ModBlocks.steel_scaffold, 16), new OreDictStack(ANY_RUBBER.ingot(), 6)));

		//Missiles

		Item[] tanks = {ModItems.fuel_tank_small, ModItems.fuel_tank_medium, ModItems.fuel_tank_large};
		Item[] engines = {ModItems.thruster_small, ModItems.thruster_medium, ModItems.thruster_large};
		for(MissileDefinition missile : MissileDefinition.values()) {
			MissileDefinition.Generation generation = missile.generation;
			int stage = generation.ordinal();
			recipes.add(new ArcWelderRecipe(missile.airframeStack(), 600 + 300 * stage, 3_000L + 6_000L * stage,
					new ComparableStack(tanks[generation.tankSize]),
					new ComparableStack(engines[generation.engineSize], 1 + stage / 2),
					new ComparableStack(missile.hullMaterial(), generation.hullCost)));
		}
		// recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.rp_capsule_20), 600, 50_000L, new ComparableStack(ModItems.plate_stainless, 4), new OreDictStack(STEEL.shell()), new ComparableStack(ModItems.circuit, 1, EnumCircuitType.AVIONICS )));

		//recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.insert_cmb), 600, 50_000L, new FluidStack(Fluids.NEON, 2_000),
		//		new OreDictStack(CMB.plate(), 2), new OreDictStack(U238.ingot())));

		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.sat_mapper), 900, 10_000L, new ComparableStack(ModItems.sat_base), new ComparableStack(ModItems.sat_head_mapper)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.sat_scanner), 900, 10_000L, new ComparableStack(ModItems.sat_base), new ComparableStack(ModItems.sat_head_scanner)));
		recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.sat_radar), 900, 10_000L, new ComparableStack(ModItems.sat_base), new ComparableStack(ModItems.sat_head_radar)));
		//recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.sat_laser), 600, 50_000L, new ComparableStack(ModItems.sat_base), new ComparableStack(ModItems.sat_head_laser)));
		//recipes.add(new ArcWelderRecipe(new ItemStack(ModItems.sat_resonator), 600, 50_000L, new ComparableStack(ModItems.sat_base), new ComparableStack(ModItems.sat_head_resonator)));

		// Vented cold-flow/leak test; flight propellant is supplied to the installed thruster.
		recipes.add(new ArcWelderRecipe(new ItemStack(ModBlocks.machine_xenon_thruster), 2400, 8_000L, new FluidStack(Fluids.XENON, 20),
				new OreDictStack(W.plateWelded(), 8), new OreDictStack(NB.plateWelded(), 4),
				new ComparableStack(ModItems.circuit, 4, EnumCircuitType.AVIONICS),
				new ComparableStack(ModItems.circuit, 2, EnumCircuitType.QUANTUM),
				new ComparableStack(ModItems.arc_electrode, 2, EnumElectrodeType.SATURNITE)));

		//krypton + silicon wafer = printed silicon wafer
		recipes.add(new ArcWelderRecipe(DictFrame.fromOne(ModItems.circuit, EnumCircuitType.SILICON), 30, 1_000L, new FluidStack(Fluids.KRYPTON, 1), new ComparableStack(ModItems.billet_silicon)));

	}

	public static HashMap getRecipes() {

		HashMap<Object, Object> recipes = new HashMap<Object, Object>();

		for(ArcWelderRecipe recipe : ArcWelderRecipes.recipes) {

			int size = recipe.ingredients.length + (recipe.fluid != null ? 1 : 0);
			Object[] array = new Object[size];

			for(int i = 0; i < recipe.ingredients.length; i++) {
				array[i] = recipe.ingredients[i];
			}

			if(recipe.fluid != null) array[size - 1] = ItemFluidIcon.make(recipe.fluid);

			recipes.put(array, recipe.output);
		}

		return recipes;
	}

	public static ArcWelderRecipe getRecipe(ItemStack... inputs) {

		outer:
		for(ArcWelderRecipe recipe : recipes) {

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
		return "hbmArcWelder.json";
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
		ItemStack output = this.readItemStack(obj.get("output").getAsJsonArray());
		if(output != null && output.getItem() instanceof ItemMissile && !((ItemMissile) output.getItem()).launchable) return;
		int duration = obj.get("duration").getAsInt();
		long consumption = obj.get("consumption").getAsLong();

		recipes.add(new ArcWelderRecipe(output, duration, consumption, fluid, inputs));
	}

	@Override
	public void writeRecipe(Object obj, JsonWriter writer) throws IOException {
		ArcWelderRecipe recipe = (ArcWelderRecipe) obj;

		writer.name("inputs").beginArray();
		for(AStack aStack : recipe.ingredients) {
			this.writeAStack(aStack, writer);
		}
		writer.endArray();

		if(recipe.fluid != null) {
			writer.name("fluid");
			this.writeFluidStack(recipe.fluid, writer);
		}

		writer.name("output");
		this.writeItemStack(recipe.output, writer);

		writer.name("duration").value(recipe.duration);
		writer.name("consumption").value(recipe.consumption);
	}

	public static class ArcWelderRecipe {

		public AStack[] ingredients;
		public FluidStack fluid;
		public ItemStack output;
		public int duration;
		public long consumption;

		public ArcWelderRecipe(ItemStack output, int duration, long consumption, FluidStack fluid, AStack... ingredients) {
			this.ingredients = ingredients;
			this.fluid = fluid;
			this.output = output;
			this.duration = duration;
			this.consumption = consumption;
		}

		public ArcWelderRecipe(ItemStack output, int duration, long consumption, AStack... ingredients) {
			this(output, duration, consumption, null, ingredients);
		}
	}
}
