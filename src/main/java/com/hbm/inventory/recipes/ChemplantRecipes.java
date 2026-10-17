package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.inventory.FluidStack;
import static com.hbm.inventory.OreDictManager.*;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ItemEnums;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemArcElectrode;
import com.hbm.items.machine.ItemCircuit;
import com.hbm.items.machine.ItemPWRFuel.EnumPWRFuel;
import com.hbm.items.machine.ItemZirnoxRod.EnumZirnoxType;
import com.hbm.main.MainRegistry;


import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ChemplantRecipes extends SerializableRecipe {

	/**
	 * Nice order: The order in which the ChemRecipe are added to the recipes list
	 * Meta order: Fixed using the id param, saved in indexMapping
	 */

	public static HashMap<Integer, ChemRecipe> indexMapping = new HashMap<>();
	public static List<ChemRecipe> recipes = new ArrayList<>();
	public static long recipeGeneration;

	@Override
	public void registerDefaults() {



		//6-30, formerly oil cracking, coal liquefaction and solidifciation
		registerOtherOil();

		recipes.add(new ChemRecipe(36, "COOLANT", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.blend("COOLANT", "FRESH_WATER", 1, "ETHANOL", 9)));
		recipes.add(new ChemRecipe(37, "CRYOGEL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(37)));

		//TOOL STEEL DOES NOT INVOLVE FUCKING MERCURY RETARD
		recipes.add(new ChemRecipe(38, "DESH", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(38))); //here have 4 since this is already cancer
		recipes.add(new ChemRecipe(39, "NITAN", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(39)));
		// Simplified anthraquinone loop: the plant abstracts the recycled organic working solution.

		recipes.add(new ChemRecipe(40, "PEROXIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(40)));

		recipes.add(new ChemRecipe(90, "SULFURIC_ACID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(90)));

		recipes.add(new ChemRecipe(92, "SOLVENT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(92)));

		recipes.add(new ChemRecipe(43, "POLYMER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(43)));

		//but synthetic polymers exist

		recipes.add(new ChemRecipe(44, "SYN_POLYMER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(44)));



		recipes.add(new ChemRecipe(81, "BAKELITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(81)));
		recipes.add(new ChemRecipe(82, "RUBBER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(82)));



		//Laminate Glass going here

        recipes.add(new ChemRecipe(97, "LAMINATE", 1)
                .inputItems(new ComparableStack(ModBlocks.reinforced_glass), new OreDictStack(STEEL.bolt(), 4), new ComparableStack(ModItems.ingot_pc))
                .outputItems(new ItemStack(ModBlocks.reinforced_laminate)).physicalProcess(200000, 4000));
		//PC refers to HARD PLASTIC SOMEHOW. Is this FUCKING GUY RETARDED?

		recipes.add(new ChemRecipe(94, "PC", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(94)));
		recipes.add(new ChemRecipe(96, "PVC", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(96)));
		recipes.add(new ChemRecipe(89, "DYNAMITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(89)));
		recipes.add(new ChemRecipe(83, "TNT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(83)));
		recipes.add(new ChemRecipe(95, "TATB", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(95)));
		recipes.add(new ChemRecipe(84, "C4", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(84)));
		//44, formerly deuterium
		//45, formerly steam

        recipes.add(new ChemRecipe(46, "YELLOWCAKE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(46)));
		// Yellowcake-to-UF6 compresses oxide conversion and fluorination; enrichment remains in the gas centrifuge.

        recipes.add(new ChemRecipe(47, "UF6", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(47)));

        recipes.add(new ChemRecipe(48, "PUF6", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(48)));
		//recipes.add(new ChemRecipe(49, "SAS3", 200)
		//		.inputItems(
		//				new OreDictStack(SA326.dust()),
		//				new OreDictStack(S.dust(), 2))
		//		.inputFluids(new FluidStack(Fluids.PEROXIDE, 2000))
		//		.outputFluids(new FluidStack(Fluids.SAS3, 1000)));
		recipes.add(new ChemRecipe(53, "CORDITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(53)));
		// Aromatics and phosgene stand in for the aromatic diamine and acid-chloride monomer families.
		recipes.add(new ChemRecipe(54, "KEVLAR", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(54)));
		recipes.add(new ChemRecipe(55, "CONCRETE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(55)));
		recipes.add(new ChemRecipe(56, "CONCRETE_ASBESTOS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(56)));
		recipes.add(new ChemRecipe(79, "DUCRETE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(79)));
		recipes.add(new ChemRecipe(57, "SOLID_FUEL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(57)));

		//if something says do not remove do not remove it

		//do not remove this I know it's redundant but the electrolysis machine sucks and is late game for some fucking reason

		recipes.add(new ChemRecipe(58, "ELECTROLYSIS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(58)));
		//this is here for early game hydrogen/compat. DO NOT REMOVE IT. I don't want to have to come back in this class and be like
		// "oh, a realistification broke progression because these fucking guys gated electrolysis behind a big ass machine or something"
		// SO DO NOT REMOVE IT! You can nerf shit like this, or fix it if it's chemically imbalanced but don't just FUCKING REMOVE IT!

		// Recipe 58 retains the early workshop water-electrolysis route at an explicit 8 kW rating.


		// Obsolete xenon shortcut removed: recover xenon from the assayed noble-gas tail.
		// Obsolete xenon shortcut removed: recover xenon from the assayed noble-gas tail.
		//recipes.add(new ChemRecipe(61, "SATURN", 60)
		//		.inputItems(
		//				new OreDictStack(DURA.dust(), 2),
		//				new OreDictStack(CU.dust(), 1),
		//				new OreDictStack(ANY_COAL_COKE.dust(), 1))
		//		.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 100))
		//		.outputItems(new ItemStack(ModItems.ingot_saturnite, 4)));
		//recipes.add(new ChemRecipe(62, "BALEFIRE", 100)
		//		.inputItems(new ComparableStack(ModItems.egg_balefire_shard))
		//		.inputFluids(new FluidStack(Fluids.KEROSENE, 6000))
		//		.outputItems(new ItemStack(ModItems.powder_balefire))
		//		.outputFluids(new FluidStack(Fluids.BALEFIRE, 8000)));
		//recipes.add(new ChemRecipe(63, "SCHRABIDIC", 100)
		//		.inputItems(new ComparableStack(ModItems.pellet_charged))
		//		.inputFluids(
		//				new FluidStack(Fluids.SAS3, 8000),
		//				new FluidStack(Fluids.PEROXIDE, 6000))
		//		.outputFluids(new FluidStack(Fluids.SCHRABIDIC, 16000)));
		//recipes.add(new ChemRecipe(64, "SCHRABIDATE", 150)
		//		.inputItems(new OreDictStack(IRON.dust()))
		//		.inputFluids(new FluidStack(Fluids.SCHRABIDIC, 250))
		//		.outputItems(new ItemStack(ModItems.powder_schrabidate)));

        recipes.add(new ChemRecipe(65, "COLTAN_CLEANING", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(65)));
		// Acid leaching and separation are compressed into a concentrate; tantalum crystallization has no dedicated machine.

        recipes.add(new ChemRecipe(67, "COLTAN_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(67)));
		recipes.add(new ChemRecipe(68, "VIT_LIQUID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(68)));
		recipes.add(new ChemRecipe(69, "VIT_GAS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(69)));
		recipes.add(new ChemRecipe(88, "LUBRICANT", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.reactorFraction()));
		// Batch base-oil recovery bootstraps lubrication before the advanced vacuum train.
		recipes.add(new ChemRecipe(1200, "LUBRICANT_BASE", 1).petroleum(com.hbm.realsim.PetroleumProcesses.lubricantBaseOil()));
		recipes.add(new ChemRecipe(70, "TEL", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.additive()));
		recipes.add(new ChemRecipe(4, "FR_REOIL", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.reclaimed()));
		recipes.add(new ChemRecipe(5, "FR_PETROIL", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.blend("PETROIL", "RECLAIMED", 4, "LUBRICANT", 1)));
		recipes.add(new ChemRecipe(86, "PETROIL_LEADED", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.leaded("PETROIL")));
		recipes.add(new ChemRecipe(71, "GASOLINE", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.finishing("REFORMATE", "GASOLINE")));

		recipes.add(new ChemRecipe(85, "GASOLINE_LEADED", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.leaded("GASOLINE")));
		recipes.add(new ChemRecipe(87, "COALGAS_LEADED", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.leaded("COALGAS")));

		recipes.add(new ChemRecipe(72, "FRACKSOL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.frackingSlurry()));
		//kill
		recipes.add(new ChemRecipe(73, "HELIUM3", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(73)));

		recipes.add(new ChemRecipe(75, "ETHANOL", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.fermentation()));
		//recipes.add(new ChemRecipe(76, "METH", 30)
		//		.inputItems(
		//				new ComparableStack(Items.wheat),
		//				new ComparableStack(Items.dye, 2, 3))
		//		.inputFluids(
		//				new FluidStack(Fluids.LUBRICANT, 400),
		//				new FluidStack(Fluids.PEROXIDE, 400))
		//		.outputItems(new ItemStack(ModItems.chocolate, 4)));

		recipes.add(new ChemRecipe(77, "CO2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(77)));
		//recipes.add(new ChemRecipe(80, "EPEARL", 100)
		//		.inputItems(new OreDictStack(DIAMOND.dust(), 1))
		//		.inputFluids(new FluidStack(Fluids.XPJUICE, 500))
		//		.outputFluids(new FluidStack(Fluids.ENDERJUICE, 100)));
		//recipes.add(new ChemRecipe(99, "NITROCRYO", 150)
				//.inputItems(new ComparableStack(ModItems.powder_ice, 4))
				//.inputFluids(new FluidStack(Fluids.BLOOD, 1000))
				//.outputItems(new ItemStack(Blocks.sand, 4))
				//.outputFluids(new FluidStack(Fluids.NITROGEN_GAS, 1000)));
		// Ammonia oxidation and aqueous absorption are represented as one plant operation.

		recipes.add(new ChemRecipe(99, "NITROACID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(99)));
		// The plant abstracts chloramine formation and ammonia coupling; nitric acid is not a hydrazine precursor.

		recipes.add(new ChemRecipe(100, "HYDRAZINE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.hydrazine()));
		//ammonia

		recipes.add(new ChemRecipe(101, "AMONGUS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(101)));

		recipes.add(new ChemRecipe(103, "HCL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(103)));

		recipes.add(new ChemRecipe(104, "AMMONIUM", 1).petroleum(com.hbm.realsim.ChemistryProcesses.ammoniumNitrate()));
		recipes.add(new ChemRecipe(105, "ARSENIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(105)));

		//recipes.add(new ChemRecipe(107, "ELBOWS", 250)
		//		.inputFluids(new FluidStack(Fluids.PEROXIDE, 100), new FluidStack(Fluids.ELBOWGREASE, 900))
		//		.inputItems(
		//				new ComparableStack(ModBlocks.sand_dirty, 20))
		//		.outputItems(new ItemStack(ModItems.ingot_iridium, 1)));

		/*
		I’m not going to do the insult part—but yeah, chemically this doesn’t make sense.
		You’ve basically got:
		peroxide (oxidizer)
		“elbow grease” (I’m assuming oil/organic sludge)
		oily sand
		→ somehow producing pure iridium metal
		That jumps over multiple real-world steps and mixes things that don’t logically lead to iridium at all.
		Iridium isn’t something you “extract” from dirty sand + oxidizer—it’s one of the rarest platinum group metals
		and shows up in very specific geological contexts.
		 */

		recipes.add(new ChemRecipe(115, "SHELL_CHLORINE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(115)));
		recipes.add(new ChemRecipe(116, "SHELL_PHOSGENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(116)));
		recipes.add(new ChemRecipe(117, "SHELL_MUSTARD", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(117)));

        recipes.add(new ChemRecipe(118, "CC_CENTRIFUGE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(118)));
		recipes.add(new ChemRecipe(119, "THORIUM_SALT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(119)));

		recipes.add(new ChemRecipe(120, "MASS_CAKE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(120)));

		recipes.add(new ChemRecipe(121, "BUTTER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(121)));
		recipes.add(new ChemRecipe(122, "STRAWICE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(122)));
		recipes.add(new ChemRecipe(123, "POISON", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(123)));

		recipes.add(new ChemRecipe(1001, "SOIL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1001)));

		recipes.add(new ChemRecipe(1002, "CHLOROMETHANE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1002)));

		//aluminum processing

        recipes.add(new ChemRecipe(1003, "BAYER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1003)));

		recipes.add(new ChemRecipe(1004, "URANIUM_BROMIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1004)));
		recipes.add(new ChemRecipe(1005, "PLUTONIUM_BROMIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1005)));

		recipes.add(new ChemRecipe(1007, "THORIUM_BROMIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1007)));

		//ACTUAL FRACKING SOLUTION

		recipes.add(new ChemRecipe(1008, "FRACKSOL2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.frackingSlurry()));


		//coal gasification
		recipes.add(new ChemRecipe(1009, "COALGAS", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.gasification()));

		// methane reforming
		recipes.add(new ChemRecipe(1010, "SMR", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.methaneReforming()));


		//molten salt actually this is going in the crucible since it's FUCKING USELESS BOB YOU RELENTLESS FUCK
		//NEVERMIND THE FUCKING CRUCIBLE ONLY OUTPUTS FUCKING SOLIDS FOR SOME FUCKING REASON DESPITE BEING USED TO POUR FLUIDS
		//NICE SHITLOW TINKERS RIPPED DOGSHIT SYSTEM. ANYWAY IT'S GOING IN THE FUCKING CHEMPLANT JUST LIKE EVERY OTHER RETARDED
		//ACTUAL CHEMICAL PROCESS

		recipes.add(new ChemRecipe(1011, "MOLTEN_SALT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.saltMelting()));

		//potash to potassium chloride

		recipes.add(new ChemRecipe(1012, "POTASSIUM_CHLORIDEPROD", 1).petroleum(com.hbm.realsim.ChemistryProcesses.potashChlorides()));
		//oh my god it's radioactive and
		// god fucking dammit I need to add more ways to obtain it and
		// god fucking dammit I have to rebalance anything relating to clocks or GPS systems.

		//Molten KCl + sodium vapor → molten NaCl + potassium vapor

		recipes.add(new ChemRecipe(1013, "POTASSIUM", 1).petroleum(com.hbm.realsim.ChemistryProcesses.potassiumReduction()));

		//potassium hydroxide + water + Iodine (2) = potassium iodide
		recipes.add(new ChemRecipe(1014, "POTASSIUM_IODIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1014)));


		//rubidium salt -> rubidium metal
		recipes.add(new ChemRecipe(1015, "RUBIDIUM", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1015)));


		//pure potassium
		//Raw potassium metal is primarily manufactured by reducing molten potassium chloride with sodium metal at high temperatures
		//recipes.add(new ChemRecipe(1016, "POTASSIUM_PURE", 110)
		//	.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 2000), new FluidStack(Fluids.SODIUM, 2000))
		//		.outputItems(new ItemStack(ModItems.potassium_powder, 1), new ItemStack(ModItems.itemsalt, 1)));
		//should be done in the solidifier
		//done

		//sodium hydroxide

		recipes.add(new ChemRecipe(1016, "SODIUM_HYDROXIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.sodiumHydroxide()));

		//recipes.add(new ChemRecipe(1017, "ARGON", 100)
		//	.inputFluids(new FluidStack(Fluids.AIR, 1000))
		//	.outputFluids(new FluidStack(Fluids.ARGON, 10))
		//);
		//in cryogenic distillation

		// Activated pollucite: HCl supplies actual chloride; rejected gangue and carrier are retained.
		recipes.add(new ChemRecipe(1017, "POLLUCITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1017)));

		//pollucite solution into heavy fraction and light fraction
		recipes.add(new ChemRecipe(1018, "POLLUCITE_FRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1018)));

		// Heavy chloride liquor: dry/recover CsCl and RbCl with steam recovery.
		recipes.add(new ChemRecipe(1019, "CESIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1019)));

		// Light chloride liquor: recover NaCl and aqueous KCl.
		recipes.add(new ChemRecipe(1020, "POTASSIUM_SODIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1020)));


		//cesium salt + calcium -> cesium metal + calcium chloride
		recipes.add(new ChemRecipe(1021, "CESIUM_EXTRACTION_2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1021)));


		//easier glass production using sodium sulfate
		recipes.add(new ChemRecipe(1022, "GLASS_SODIUM", 50)
			.inputItems(new ComparableStack(ModItems.sodium_sulfate, 1), new ComparableStack(Blocks.sand, 8))
			.outputItems(new ItemStack(Blocks.glass, 8))
		);

		// Alkali roasting of beryl (fixed + balanced) (SIMPLISTIC)
		recipes.add(new ChemRecipe(1023, "BERYL_ROASTING", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1023)));

		// Sodium carbonate synthesis (CO2 absorption)
		recipes.add(new ChemRecipe(1024, "SODIUM_CARBONATE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1024)));

		//reinforced glass (silicate use
		recipes.add(new ChemRecipe(1025, "REINFORCED_GLASS", 100)
			.inputItems(new ComparableStack(ModItems.powder_sodium_silicate, 4), new ComparableStack(Blocks.sand, 8))
			.outputItems(new ItemStack(ModBlocks.reinforced_glass, 16))
		);

		recipes.add(new ChemRecipe(1026, "CALCINED_DOLOMITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1026)));

		recipes.add(new ChemRecipe(1027, "MAGNESIUM_REDUCTION", 200)
			.inputItems(
				new ComparableStack(ModItems.calcined_dolomite, 1),
				new ComparableStack(ModItems.ingot_silicon, 1)
			)
			.outputItems(
				new ItemStack(ModItems.magnesium_ingot, 1),
				//slag whatever
				//SLAG //new MaterialStack(Mats.MAT_SLAG
				new ItemStack(ModItems.slagingot, 1)
				//new OreDictStack(SLAG.ingot(), 1)
				//new Mats.MaterialStack(MAT_SLAG, 1)
				//(ItemStack) new Mats.MaterialStack(MAT_SLAG, 1)
				//incompatible types
				//TODO FIX THIS DOGSHIT
				//would be but bob coded this shit so retarded that I don't even want to touch it.
			)
		);


		//calcium step 1 HCl + quicklime -> CACL2
		// Calcium chloride synthesis
		recipes.add(new ChemRecipe(1028, "CALCIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1028)));

		recipes.add(new ChemRecipe(1029, "MOLTEN_STRONTIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1029)));

		recipes.add(new ChemRecipe(1030, "STRONTIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1030)));

		// barium carbonate
		recipes.add(new ChemRecipe(1031, "BARIUM_CARBONATE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1031)));

		// Optional reverse synthesis route; this is not the normal sulfur-production route.

		recipes.add(new ChemRecipe(1032, "HYDROGEN_SULFIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.sulfideSynthesis()));

		//barium oxide
		//recipes.add(new ChemRecipe(1032, "BARIUM_OXIDE", 50)
		//	.inputItems(new ComparableStack(ModItems.barium_carbonate, 1)) //BaCO3 + heat -> BaO + CO2
		//	.outputItems(new ItemStack(ModItems.barium_oxide, 1))
		//	.outputFluids(new FluidStack(Fluids.CARBONDIOXIDE, 1000))
		//);
		//regular furnace/etc.

		// barium production (hydrogen reduction)
		recipes.add(new ChemRecipe(1033, "BARIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1033)));
		//please kill me

		// radium extraction (barium-assisted, simple and 'sane')
		recipes.add(new ChemRecipe(1034, "RADIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1034)));

		//sodium sulfate into sodium first step Reduce sodium sulfate with carbon:

		recipes.add(new ChemRecipe(1035, "SODIUM_SULFATE_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.sulfateReduction()));

		//sodium sulfide into hydrogen sulfide

		recipes.add(new ChemRecipe(1036, "SODIUM_SULFIDE_ACIDIFICATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.sulfideAcidification()));

		// Thermal stage: H2S + 1.5 O2 -> SO2 + H2O. Catalytic stage: 2 H2S + SO2 -> 3 S + 2 H2O.
		// SO2 remains internal; the abstracted plant uses the net reaction 2 H2S + O2 -> 2 S + 2 H2O.

		recipes.add(new ChemRecipe(1037, "CLAUS_PROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1037)));

        recipes.add(new ChemRecipe(1038, "RARE_EARTH_ELEMENTS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1038)));

		// Scandium extraction

        recipes.add(new ChemRecipe(1039, "SCANDIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1039)));

		// Yttrium extraction

        recipes.add(new ChemRecipe(1040, "YTTRIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1040)));

		//waste_water + lime → water + sludge
		recipes.add(new ChemRecipe(1041, "WASTE_WATER_TREATMENT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1041)));

		//waste_water + sodium_hydroxide → water + salt_sludge
		recipes.add(new ChemRecipe(1042, "WASTE_WATER_TREATMENT_2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1042)));

		recipes.add(new ChemRecipe(1043, "SCANDIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1043)));

		recipes.add(new ChemRecipe(1044, "SCANDIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1044)));

		//scandium oxide from rare metals residue (titanium)
		recipes.add(new ChemRecipe(1045, "SCANDIUM_FROM_TITANIUM", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1045)));

		// Yttrium from titanium
		recipes.add(new ChemRecipe(1046, "YTTRIUM_FROM_TITANIUM", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1046)));

		recipes.add(new ChemRecipe(1047, "YTTRIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1047)));

		recipes.add(new ChemRecipe(1048, "YTTRIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1048)));

		//lepidolite
		recipes.add(new ChemRecipe(1049, "LEPIDOLITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1049)));
		//I can't be bothered to separate this into another process, maybe later

		//carnallite + water -> potassium chloride + magnesium chloride + water
		recipes.add(new ChemRecipe(1050, "CARNALLITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1050)));

		//powder_fertilizer from potassium chloride
		recipes.add(new ChemRecipe(1051, "FERTILIZER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1051)));

		//potassium sulfate from potassium chloride and sulfuric acid (best fertilizer)
		//recipes.add(new ChemRecipe(1052, "POTASSIUM_SULFATE", 50)
		//	.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000), new FluidStack(Fluids.SULFURIC_ACID, 1000))
		//	.outputItems(new ItemStack(ModItems.potassium_sulfate, 1))
		//	.outputFluids(new FluidStack(Fluids.HCL, 1000))
		//);

		//fertilizer from potassium sulfate (best process)
		recipes.add(new ChemRecipe(1052, "FERTILIZER_2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1052)));


		//I FUCKING HATE HOW UNMODULAR THIS INDEX SYSTEM IS !!! MORE RETARDED HUMAN SLOP DESIGN!!!
		recipes.add(new ChemRecipe(1053, "GASOLINE2", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.syntheticGasoline()));

		// Step 2: Oxidation (Chemplant)
		recipes.add(new ChemRecipe(1054, "OSMIRIDIUM_OXIDATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1054)));

		recipes.add(new ChemRecipe(1055, "OSMIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1055)));

		//rhodium extraction using chlorine
		recipes.add(new ChemRecipe(1056, "RHODIUM_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1056)));

		recipes.add(new ChemRecipe(1057, "IRIDIUM_OXIDATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1057)));

		recipes.add(new ChemRecipe(1061, "IRIDIUM_DISSOLUTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1061)));

		//Fluorite → hydrofluoric acid

		recipes.add(new ChemRecipe(1062, "HYDROFLUORIC_ACID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.hydrofluoric()));


		//DO NOT REMOVE DO NOT REMOVE DO NOT REMOVE
		//salt + water + power → chlorine + hydrogen + lye

		recipes.add(new ChemRecipe(1063, "CHLORINE_PRODUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.chloralkali(true)));

		// Recipe 1063 preserves the early chlor-alkali route with paid work and all coproducts.
		//ok but the electrolyser is a fucking energy sponge so early game that's a nightmare so DO NOT DO THAT.
		//... And Considering you can do electrolysis in your back yard, LETS KEEP THE RECIPE YEAH?

		//chlorocalcite from calcium chloride and potassium chloride

        recipes.add(new ChemRecipe(1064, "CHLOROCALCITE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1064)));

		//RUTHENIUM_RESIDUE + HYDROGEN -> RUTHENIUM + STEAM

		recipes.add(new ChemRecipe(1065, "RUTHENIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.rutheniumReduction()));

		//bromine from bittern + chlorine
		recipes.add(new ChemRecipe(1066, "BROMINE_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1066)));

		//iodine brine + chlorine -> iodine + salt brine
		recipes.add(new ChemRecipe(1067, "IODINE_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1067)));

		//potassium iodide solution + chlorine -> iodine + potassium chloride
		recipes.add(new ChemRecipe(1068, "IODINE_EXTRACTION2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1068)));

		//Potassium chloride + ammonium nitrate = saltpeter/niter
		recipes.add(new ChemRecipe(1069, "SALTPETER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1069)));

		//ammonium chloride + heat -> ammonia + hydrogen chloride

		recipes.add(new ChemRecipe(1070, "AMMONIUM_CHLORIDE_DECOMPOSITION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.ammoniumChlorideDecomposition()));

		//Potassium chloride + sodium nitrate = saltpeter/niter
		recipes.add(new ChemRecipe(1071, "SALTPETER2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1071)));

		//helium capsule
		recipes.add(new ChemRecipe(1072, "HELIUM_CAPSULE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1072)));

		//neon used to make vaccum tubes
		recipes.add(new ChemRecipe(1073, "NEON_CAPSULE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1073)));

		//fragment_samarium
		//    ↓ (Chemical Plant + acid)
		recipes.add(new ChemRecipe(1074, "SAMARIUM_FRAGMENT_REFINEMENT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1074)));

		//fullerene from graphite electrodes + helium
		recipes.add(new ChemRecipe(1075, "FULLERENE_SYNTHESIS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1075)));

		//sulfuric acid + europium_dust_tiny = europium solution
		recipes.add(new ChemRecipe(1076, "EUROPIUM_SOL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1076)));

		//gadolinium_dust_tiny into gadolinium concentrate
		//recipes.add(new ChemRecipe(1077, "GADOLIN_SOL", 400)
		//				.inputItems(
		//					new ComparableStack(
		//						ModItems.gadolinium_dust_tiny, 9
		//					)
		//				)
		//				.inputFluids(
		//					new FluidStack(Fluids.SULFURIC_ACID, 1000)
		//				)
		//				.outputItems(
		//					new ItemStack(
		//						ModItems.gadoliniumsol
		//					)
		//				)
		//);
		//belongs in the crystalizer (leaching reactor)


		//now gadolinium powder + heat (steam) = liquid gadolinium

		recipes.add(new ChemRecipe(1077, "GADOLINIUM_LIQUID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.gadoliniumMelting()));

		//liquid gadolinium + solvent = gadolinium powder 2

		recipes.add(new ChemRecipe(1078, "GADOLINIUM_PRECIPITATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.gadoliniumRefining()));

		//terbiumsol + high perf solvent = terbium step toward ingot
		//Separation: The mixture is leached using acids, (we did this in the crystallizer (leaching reactor))
		// and specialized solvent extraction or ion-exchange methods are utilized to separate terbium from other (here!)
		// rare-earth elements.
		recipes.add(new ChemRecipe(1079, "TERBIUM_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1079)));

		//terbium oxidation
		recipes.add(new ChemRecipe(1080, "TERBIUM_OXIDATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1080)));

		//Metallothermic Reduction: To obtain metallic terbium, the oxide is converted into terbium fluoride ( //we are here
		//) or anhydrous chloride, which is then reduced using calcium or lithium metal in a high-temperature vacuum or inert atmosphere.
		recipes.add(new ChemRecipe(1081, "TERBIUM_FLUORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1081)));

						//.inputFluids(
						//	new FluidStack(Fluids.CALCIUM_SOLUTION, 1000)
						//)
						//.outputItems(
						//	new ItemStack(ModItems.terbium_ingot, 1)
						//) //no those come later

		//dysprosiumsol + solvent = dysprosium powder 2
		recipes.add(new ChemRecipe(1082, "DYSPROSIUM_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1082)));

		//powder_ytterbium_oxide + hydrofluoric acid = powder_ytterbium_fluoride
		recipes.add(new ChemRecipe(1083, "YTTERBIUM_FLUORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1083)));

		//lutetiumsol + solvent = lutetium powder 2
		recipes.add(new ChemRecipe(1084, "LUTETIUM_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1084)));

		//thuliumsol + solvent = thuliumsol2
		recipes.add(new ChemRecipe(1085, "THULIUM_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1085)));

		// Consolidate existing vanadium; recover the erbium addition without creating metal.
		recipes.add(new ChemRecipe(1086, "VANADIUM_INGOT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1086)));

		recipes.add(new ChemRecipe(1087, "ER_YAG_PRECURSOR", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1087)));

		recipes.add(new ChemRecipe(1088, "YB_YAG_PRECURSOR", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1088)));

		recipes.add(new ChemRecipe(1089, "ZNSE_SYNTHESIS", 200)
						.inputItems(
							new ComparableStack(ModItems.crystal_zinc, 1),
							new ComparableStack(ModItems.powder_selenium, 1)
						)
						.outputItems(
							new ItemStack(ModItems.znse_feedstock)
						)
		);

		//recipes.add(new ChemRecipe(1090, "IR_LASER_GASMIX", 400)
		//				.inputFluids(
		//					new FluidStack(Fluids.CARBONDIOXIDE, 1000),
		//					new FluidStack(Fluids.NITROGEN_GAS, 500),
		//					//new FluidStack(Fluids.HELIUM4, 500) //helium 3...?
		//					//EXCEPT THAT THE CHEMPLANT CANNOT TAKE MORE THAN 2 FLUIDS AT A TIME, SO I HAVE TO ADD EVEN MORE FUCKING RECIPES!!!
		//				)
		//				.outputFluids(
		//					new FluidStack(Fluids.CARBONDIOXIDE_NITROGEN_HELIUM, 400)
		//				)
		//);

		recipes.add(new ChemRecipe(1090, "IR_LASER_GASMIX_1", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1090)));

		recipes.add(new ChemRecipe(1091, "IR_LASER_GASMIX_2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1091)));

		recipes.add(new ChemRecipe(1092, "KTP_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1092)));

		recipes.add(new ChemRecipe(1093, "BBO_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1093)));

		recipes.add(new ChemRecipe(1094, "TITANIUM_SAPPHIRE_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1094)));

		recipes.add(new ChemRecipe(1095, "LITHIUM_NIOBATE_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1095)));

		recipes.add(new ChemRecipe(1096, "VISIBLE_OPTICS_ARRAY", 400)
						.inputItems(
							new ComparableStack(ModItems.KTP_crystal, 1),
							new ComparableStack(ModItems.LBO_crystal, 1),
							new ComparableStack(ModItems.BBO_crystal, 1),
							new ComparableStack(ModItems.lithium_niobate_crystal, 1)
						)
						.outputItems(
							new ItemStack(ModItems.visible_optics_array, 1)
						)
		);

		recipes.add(new ChemRecipe(1097, "VISIBLE_DOPANT_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1097)));

		recipes.add(new ChemRecipe(1098, "CERIUM_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1098)));

		//TWO FLUIDS AT A TIME BECAUSE BOB HATES GOD
		//recipes.add(new ChemRecipe(1097, "EXCIMER_GASMIX", 400)
		//				.inputFluids(
		//					new FluidStack(Fluids.KRYPTON, 250),
		//					new FluidStack(Fluids.XENON, 250),
		//					new FluidStack(Fluids.FLUORINE, 250),
		//					new FluidStack(Fluids.CHLORINE, 250)
		//				)
		//				.outputFluids(
		//					new FluidStack(Fluids.EXCIMER_GASMIX, 1000)
		//				)
		//);

		recipes.add(new ChemRecipe(1099, "EXCIMER_GASMIX1", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1099)));

		recipes.add(new ChemRecipe(1100, "EXCIMER_GASMIX2", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1100)));
		recipes.add(new ChemRecipe(1101, "EXCIMER_GASMIX3", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1101)));

		recipes.add(new ChemRecipe(1102, "CALCIUM_FLUORIDE_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1102)));

		recipes.add(new ChemRecipe(1103, "KDP_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1103)));

		recipes.add(new ChemRecipe(1104, "CLBO_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1104)));

		recipes.add(new ChemRecipe(1105, "LBO_CRYSTAL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1105)));

		//tritium into helium 3 (small amount)
		// 1106 retired: a chemical template cannot instantly age tritium into its daughter isotope. //WILL BE USED IN NUCLEAR FUSION!!!

		//zircon into hafnium + zirconium
		recipes.add(new ChemRecipe(1107, "ZIRCONIUM_HAFNIUM_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1107)));

		//HALEU1975 / HALEU15 / LEU5
		//
		//uranium/plutonium mix
		//trace transuranics
		//fission waste
		//maybe krypton/xenon byproduct fluid later
		recipes.add(new ChemRecipe(1108, "PBR_URAN_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1108)));

		//powder_spent_leu
		recipes.add(new ChemRecipe(1109, "LEU_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1109)));

		//TH232
		//
		//mostly thorium left
		//bred U-233 traces
		//some waste
		recipes.add(new ChemRecipe(1110, "THORIUM_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1110)));

		//U233
		//
		//recovered U-233
		//nasty fission products
		//high rad value
		recipes.add(new ChemRecipe(1111, "U233_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1111)));

		//MOX241
		//
		//plutonium-rich recycle stream
		//americium traces
		//very radioactive waste
		recipes.add(new ChemRecipe(1112, "MOX_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1112)));


		//non fuel pellets
		//Graphite
		//
		//irradiated graphite dust
		//maybe carbon-14 contamination
		//low-level waste or graphite recycle
		recipes.add(new ChemRecipe(1113, "GRAPHITE_REPROCESS", 400)
						.inputItems(
							new ComparableStack(ModItems.dust_graphite, 1)
						)
						//.inputFluids(
						//	new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						//)
						.outputItems(
							new ItemStack(ModItems.ingot_graphite, 1),
							new ItemStack(ModItems.nuclear_waste)
						)
						//.outputFluids(
						//	new FluidStack(Fluids.XENON, 200)
						//)
		);

		//Lead absorber
		//
		//lead dust + contaminated waste
		recipes.add(new ChemRecipe(1114, "LEAD_ABSORBER_REPROCESS", 400)
						.inputItems(
							new ComparableStack(ModItems.powder_lead_irradiated, 1)
						)
						//.inputFluids(
						//	new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						//)
						.outputItems(
							new ItemStack(ModItems.powder_lead, 1),
							new ItemStack(ModItems.nuclear_waste, 2)
						)
						//.outputFluids(
						//	new FluidStack(Fluids.XENON, 200)
						//)
		);

		//Boron absorber
		//
		//borated waste dust
		//neutron-poisoned absorber scrap

		recipes.add(new ChemRecipe(1115, "BORON_ABSORBER_REPROCESS", 400)
						.inputItems(
							new ComparableStack(ModItems.powder_boron_spent, 1)
						)
						//.inputFluids(
						//	new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						//)
						.outputItems(
							new ItemStack(ModItems.ingot_boron, 1),
							new ItemStack(ModItems.powder_lithium),
							new ItemStack(ModItems.nuclear_waste)
						)
						//.outputFluids(
						//	new FluidStack(Fluids.XENON, 200)
						//)
		);

		//DU absorber
		//
		//depleted uranium powder
		//maybe slightly “activated” DU
		recipes.add(new ChemRecipe(1116, "DU_ABSORBER_REPROCESS", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1116)));

		// Cooled PWR fuel is dissolved and separated here rather than being magically
		// isotope-sorted by the mechanical centrifuge.  These are deliberately bulk
		// recovery streams: uranium/plutonium-bearing material, recoverable zirconium
		// cladding, and radioactive raffinate for vitrification.  The small recovery
		// quantities leave a material loss in the high-level waste.
		registerPwrReprocessing(1117, "PWR_URANIUM_REPROCESS", EnumPWRFuel.MEU, 1, 1, true);
		registerPwrReprocessing(1118, "PWR_U233_REPROCESS", EnumPWRFuel.HEU233, 1, 0, true);
		registerPwrReprocessing(1119, "PWR_U235_REPROCESS", EnumPWRFuel.HEU235, 1, 1, true);
		registerPwrReprocessing(1120, "PWR_NEPTUNIUM_REPROCESS", EnumPWRFuel.MEN, 0, 1, false);
		registerPwrReprocessing(1121, "PWR_NP237_REPROCESS", EnumPWRFuel.HEN237, 0, 1, false);
		registerPwrReprocessing(1122, "PWR_MOX_REPROCESS", EnumPWRFuel.MOX, 1, 1, true);
		registerPwrReprocessing(1123, "PWR_PLUTONIUM_REPROCESS", EnumPWRFuel.MEP, 0, 1, false);
		registerPwrReprocessing(1124, "PWR_PU239_REPROCESS", EnumPWRFuel.HEP239, 0, 1, false);
		registerPwrReprocessing(1125, "PWR_PU241_REPROCESS", EnumPWRFuel.HEP241, 0, 1, false);
		registerPwrReprocessing(1126, "PWR_AMERICIUM_REPROCESS", EnumPWRFuel.MEA, 0, 0, false);
		registerPwrReprocessing(1127, "PWR_AM242_REPROCESS", EnumPWRFuel.HEA242, 0, 0, false);

		// Magnox rods are helium-backfilled and sealed fuel assemblies, not hand-crafted items.
		registerMagnoxFuelRod(1131, "MAGNOX_NATURAL_URANIUM", EnumZirnoxType.NATURAL_URANIUM_FUEL, new OreDictStack(U.billet(), 2));
		registerMagnoxFuelRod(1132, "MAGNOX_URANIUM", EnumZirnoxType.URANIUM_FUEL, new ComparableStack(ModItems.billet_uranium_fuel, 2));
		registerMagnoxFuelRod(1133, "MAGNOX_THORIUM", EnumZirnoxType.TH232, new OreDictStack(TH232.billet(), 2));
		registerMagnoxFuelRod(1134, "MAGNOX_THORIUM_FUEL", EnumZirnoxType.THORIUM_FUEL, new ComparableStack(ModItems.billet_thorium_fuel, 2));
		registerMagnoxFuelRod(1135, "MAGNOX_MOX", EnumZirnoxType.MOX_FUEL, new ComparableStack(ModItems.billet_mox_fuel, 2));
		registerMagnoxFuelRod(1136, "MAGNOX_PLUTONIUM", EnumZirnoxType.PLUTONIUM_FUEL, new ComparableStack(ModItems.billet_plutonium_fuel, 2));
		registerMagnoxFuelRod(1137, "MAGNOX_U233", EnumZirnoxType.U233_FUEL, new OreDictStack(U233.billet(), 2));
		registerMagnoxFuelRod(1138, "MAGNOX_U235", EnumZirnoxType.U235_FUEL, new OreDictStack(U235.billet(), 2));
		registerMagnoxFuelRod(1139, "MAGNOX_LES", EnumZirnoxType.LES_FUEL, new ComparableStack(ModItems.billet_les, 2));
		registerMagnoxFuelRod(1140, "MAGNOX_LITHIUM", EnumZirnoxType.LITHIUM, new OreDictStack(LI.ingot(), 2));
		registerMagnoxFuelRod(1141, "MAGNOX_ZFB_MOX", EnumZirnoxType.ZFB_MOX, new ComparableStack(ModItems.billet_mox_fuel), new OreDictStack(ZR.billet()));


		// Compact CO + 2H2 -> CH3OH synthesis: SYNGAS represents the conditioned CO/H2 feed.
		recipes.add(new ChemRecipe(1128, "METHANOL_SYNTHESIS", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.methanol()));

		// Coal hydrogenation owns its carbon/char/ash/water and paid utility bill.
		recipes.add(new ChemRecipe(1129, "COAL_GASOLINE", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.coalLiquid("COALGAS")));

		// UNSATURATEDS is the game's mixed light-olefin stream; this is an intentionally
		// compact ethylene-polymerization route rather than a claim that every component polymerizes.

		recipes.add(new ChemRecipe(1130, "POLYETHYLENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1130)));

		// Amine sweetening and regeneration are abstracted here; the amine solvent is recycled internally.
		recipes.add(new ChemRecipe(1142, "SOUR_GAS_SWEETENING", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.sweetening()));
		//I am losing my fucking sanity

		// 1143: obsolete biological nitrate shortcut; retained template IDs resolve as unavailable.


		// 1144: obsolete biological nitrate shortcut; retained template IDs resolve as unavailable.

		recipes.add(new ChemRecipe(1145, "WATER_TREATMENT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1145)));

		recipes.add(new ChemRecipe(1146, "WATER_DEMINERALIZATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1146)));

		recipes.add(new ChemRecipe(1147, "BORATED_WATER", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1147)));

		recipes.add(new ChemRecipe(1148, "BRINE_EVAPORATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1148)));

		//Methylamine (CH{3} NH{2})
		//recipes.add(new ChemRecipe(1145, "METHYLAMINE", 1200)
		//		.inputItems(
		//			new ComparableStack(ModItems.powder_coal_tiny, 1) //1 carbon
		//		)
		//		.inputFluids(
		//			new FluidStack(Fluids.HYDROGEN_GAS, 400), //3 hydrogen + 1 Hydrogen
		//			new FluidStack(Fluids.NITROGEN_GAS, 100)
		//		)
		//		.outputFluids(new FluidStack(Fluids.METHYLAMINE, 1000))
		//);







								   //todo methamphetamine, methylamine, ephedrine, pseudoephedrine, etc. for fun chemistry and maybe a drug lab or something who knows
		//thanks for the very legal autofill ai anyway to the chemical reactor *bat man noise


	        recipes.add(new ChemRecipe(1150, "SULFUR_RECOVERY", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.sulfurRecovery()));
        recipes.add(new ChemRecipe(1151, "COAL_LIQUEFACTION", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.coalLiquid("COALOIL")));
        recipes.add(new ChemRecipe(1152, "FISH_OIL_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.oilExtraction(true)));
        recipes.add(new ChemRecipe(1153, "SEED_OIL_EXTRACTION", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.oilExtraction(false)));
        recipes.add(new ChemRecipe(1154, "WOOD_PYROLYSIS", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.woodPyrolysis()));
        recipes.add(new ChemRecipe(1155, "BITUMEN_MELTING", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.tarMelting("TAR_CRUDE_ITEM", "BITUMEN")));
        recipes.add(new ChemRecipe(1156, "COAL_TAR_MELTING", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.tarMelting("TAR_COAL_ITEM", "COALCREOSOTE")));
        recipes.add(new ChemRecipe(1157, "WOOD_TAR_MELTING", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.tarMelting("TAR_WOOD_ITEM", "WOODOIL")));
        recipes.add(new ChemRecipe(1177, "BORIC_ACID", 1).petroleum(com.hbm.realsim.ChemistryProcesses.boricAcid()));
        recipes.add(new ChemRecipe(1178, "MINERAL_BRINE_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.mineralBrineSeparation()));
        recipes.add(new ChemRecipe(1179, "LITHIUM_CARBONATE", 1).petroleum(com.hbm.realsim.MineralChemicalProcesses.lithiumCarbonate()));
        recipes.add(new ChemRecipe(1180, "LITHIUM_CHLORIDE", 1).petroleum(com.hbm.realsim.MineralChemicalProcesses.lithiumChloride()));
        recipes.add(new ChemRecipe(1181, "RAW_GAS_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.rawGasRecovery()));
        recipes.add(new ChemRecipe(1173, "TANTALUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1173)));
        recipes.add(new ChemRecipe(1174, "NIOBIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1174)));
        recipes.add(new ChemRecipe(1175, "ALUMINA_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1175)));
        recipes.add(new ChemRecipe(1176, "CALCIUM_DRYING", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1176)));
        recipes.add(new ChemRecipe(1171, "XYLENE_PRODUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.xylene()));
        recipes.add(new ChemRecipe(1172, "SYNGAS_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.syngasSeparation()));
        recipes.add(new ChemRecipe(1163, "SULFUR_METERING", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1163)));
        recipes.add(new ChemRecipe(1164, "BUTADIENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1164)));
        recipes.add(new ChemRecipe(1165, "PHENOL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1165)));
        recipes.add(new ChemRecipe(1166, "FORMALDEHYDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1166)));
        recipes.add(new ChemRecipe(1167, "BISPHENOL_A", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1167)));
        recipes.add(new ChemRecipe(1168, "BENZENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1168)));
        recipes.add(new ChemRecipe(1169, "PHENYLENEDIAMINE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1169)));
        recipes.add(new ChemRecipe(1170, "TEREPHTHALOYL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1170)));
        recipes.add(new ChemRecipe(1162, "POTASSIUM_IODIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.potashLyeMelting()));
        recipes.add(new ChemRecipe(1161, "TEL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.additiveDoses()));
        recipes.add(new ChemRecipe(1160, "SODIUM_HYDROXIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.meltLye()));
        recipes.add(new ChemRecipe(1159, "PHOSGENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.phosgene()));
        recipes.add(new ChemRecipe(1158, "CRACK_TAR_MELTING", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.tarMelting("TAR_CRACK_ITEM", "CRACKOIL")));

        recipes.add(new ChemRecipe(1182, "BERYLLIUM_FLUORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1182)));
        recipes.add(new ChemRecipe(1183, "BERYLLIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1183)));
        recipes.add(new ChemRecipe(1184, "ZIRCONIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1184)));
        recipes.add(new ChemRecipe(1185, "HAFNIUM_REDUCTION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1185)));
        recipes.add(new ChemRecipe(1186, "FULLERENE_RECOVERY", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1186)));
        recipes.add(new ChemRecipe(1187, "LITHIUM_FLUORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1187)));
        recipes.add(new ChemRecipe(1188, "THORIUM_FLUORIDE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1188)));
        recipes.add(new ChemRecipe(1189, "FLUORIDE_FUEL_SALT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1189)));
        recipes.add(new ChemRecipe(1190, "TOLUENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1190)));
        recipes.add(new ChemRecipe(1191, "GLYCEROL", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1191)));
        recipes.add(new ChemRecipe(1192, "HEXAMINE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1192)));
        recipes.add(new ChemRecipe(1193, "RDX", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1193)));
        recipes.add(new ChemRecipe(1194, "NITROCELLULOSE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1194)));
        recipes.add(new ChemRecipe(1195, "NITROGLYCERIN", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1195)));
        recipes.add(new ChemRecipe(1196, "BLISTER_COMPOUND", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1196)));
        recipes.add(new ChemRecipe(1197, "PERTECHNETATE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1197)));
        recipes.add(new ChemRecipe(1198, "DICYANOACETYLENE", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1198)));
        recipes.add(new ChemRecipe(1199, "EGG_SEPARATION", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(1199)));
}

	private static void registerPwrReprocessing(int id, String name, EnumPWRFuel fuel, int uranium, int plutonium, boolean zirconiumCladding) {
		List<ItemStack> outputs = new ArrayList<ItemStack>();
		if(uranium > 0) outputs.add(new ItemStack(ModItems.powder_uranium, uranium));
		if(plutonium > 0) outputs.add(new ItemStack(ModItems.powder_plutonium, plutonium));
		if(zirconiumCladding) outputs.add(new ItemStack(ModItems.nugget_zirconium, 1));
		outputs.add(new ItemStack(ModItems.nuclear_waste, 2));

		recipes.add(new ChemRecipe(id, name, 500)
				.inputItems(new ComparableStack(ModItems.pwr_fuel_depleted, 1, fuel.ordinal()))
				.inputFluids(new FluidStack(Fluids.NITRIC_ACID, 1_000))
				.outputItems(outputs.toArray(new ItemStack[outputs.size()]))
				.outputFluids(new FluidStack(Fluids.WASTEFLUID, 1_000)));
	}

	private static void registerMagnoxFuelRod(int id, String name, EnumZirnoxType fuel, AStack... fuelInputs) {
		AStack[] inputs = new AStack[fuelInputs.length + 1];
		inputs[0] = new ComparableStack(ModItems.rod_zirnox_empty);
		System.arraycopy(fuelInputs, 0, inputs, 1, fuelInputs.length);

		recipes.add(new ChemRecipe(id, name, 50)
				.inputItems(inputs)
				.inputFluids(new FluidStack(Fluids.HELIUM4, 10))
				.outputItems(new ItemStack(ModItems.rod_zirnox, 1, fuel.ordinal())));
	}

	public static void registerOtherOil() {
		recipes.add(new ChemRecipe(31, "BP_BIOGAS", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.digestion()));
		recipes.add(new ChemRecipe(32, "BP_BIOFUEL", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.blend("BIOFUEL", "SUNFLOWEROIL", 4, "ETHANOL", 1)));
		recipes.add(new ChemRecipe(33, "LPG", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.gasRecovery()));
		recipes.add(new ChemRecipe(34, "OIL_SAND", 1).petroleum(com.hbm.realsim.ChemicalFuelProcesses.oilSand()));
		recipes.add(new ChemRecipe(35, "ASPHALT", 1).petroleum(com.hbm.realsim.ChemistryProcesses.defaults().get(35)));
	}

	public static class ChemRecipe {

		public int listing;
		private int id;
		public String name;
		public AStack[] inputs;
		public FluidStack[] inputFluids;
		public ItemStack[] outputs;
		public FluidStack[] outputFluids;
		private int duration;
		public long physicalPowerQuanta;
        public com.hbm.realsim.ProcessRecipe physicalBill;
		public int oxygenConsumption = 0; // How much oxygen the recipe consumes from the atmosphere per tick while processing

		public ChemRecipe(int index, String name, int duration) {
            this(index, name, duration, true);
        }
        private ChemRecipe(int index, String name, int duration, boolean register) {
			this.id = index;
			this.name = name;
			this.duration = duration;
			this.listing = recipes.size();

			this.inputs = new AStack[4];
			this.outputs = new ItemStack[4];
			this.inputFluids = new FluidStack[4];
			this.outputFluids = new FluidStack[4];

            if(register) register();
        }
        private void register() {
			if(!indexMapping.containsKey(id)) {
				indexMapping.put(id, this);
				recipeGeneration++;
			} else {
				throw new IllegalStateException("Chemical plant recipe " + name + " has been registered with duplicate id " + id + " used by " + indexMapping.get(id).name + "!");
			}
		}

		public ChemRecipe(int index, String name, int duration, int oxygenConsumption) {
			this(index, name, duration);
			this.oxygenConsumption = oxygenConsumption;
		}

		public ChemRecipe inputItems(AStack... in) {
			for(int i = 0; i < in.length; i++) this.inputs[i] = in[i];
			return this;
		}

		public ChemRecipe inputFluids(FluidStack... in) {
			for(int i = 0; i < in.length; i++) this.inputFluids[i] = in[i];
			return this;
		}

		public ChemRecipe outputItems(ItemStack... out) {
			for(int i = 0; i < out.length; i++) this.outputs[i] = out[i];
			return this;
		}

		public ChemRecipe outputFluids(FluidStack... out) {
			for(int i = 0; i < out.length; i++) this.outputFluids[i] = out[i];
			return this;
		}

        public ChemRecipe petroleum(com.hbm.realsim.ProcessRecipe bill) {
            com.hbm.realsim.ChemicalFuelProcesses.validateReactorRating(bill);
            bill = bill.electricallyDriven();
            int fluidProducts = 0, solidProducts = 0;
            for(String product : bill.outputs) if(!product.startsWith("VENT_")) {
                if(com.hbm.realsim.PhysicalRegistry.get(product).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) solidProducts++; else fluidProducts++;
            }
            if(bill.inputs.length > 4 || bill.solidInputs.length > 4 || fluidProducts > 4 || solidProducts > 4) throw new IllegalArgumentException("Chemical Reactor bill exceeds its four feed/coproduct/solid ports");
            physicalBill = bill; duration = bill.duration; physicalPowerQuanta = Math.max(1L, com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.workQuanta, bill.duration));
            inputs = new AStack[4]; outputs = new ItemStack[4]; inputFluids = new FluidStack[4]; outputFluids = new FluidStack[4];
            for(int i = 0; i < bill.inputs.length; i++) inputFluids[i] = new FluidStack(Fluids.fromName(bill.inputs[i]), bill.quantities[i], bill.pressures[i]);
            for(int i = 0; i < bill.solidInputs.length; i++) inputs[i] = com.hbm.realsim.ProcessMachine.feed(bill, i, "Chemical Reactor template " + id + " (" + name + ")");
            int fluid = 0, solid = 0;
            for(int i = 0; i < bill.outputs.length; i++) {
                String name = bill.outputs[i]; if(name.startsWith("VENT_") || bill.productMass[i] == 0) continue;
                int amount = Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[i], com.hbm.realsim.PhysicalRegistry.get(name).massUnits));
                if(com.hbm.realsim.PhysicalRegistry.get(name).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) outputs[solid++] = com.hbm.realsim.ProcessMachine.solid(name, amount);
                else outputFluids[fluid++] = new FluidStack(Fluids.fromName(name), amount);
            }
            return this;
        }

        public ItemStack outputItemDisplay(int slot) {
            if(physicalBill == null) return outputs[slot];
            int solid = 0;
            for(int i = 0; i < physicalBill.outputs.length; i++) if(!physicalBill.outputs[i].startsWith("VENT_") && physicalBill.productMass[i] > 0
                    && com.hbm.realsim.PhysicalRegistry.get(physicalBill.outputs[i]).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) {
                String name = physicalBill.outputs[i];
                long left = physicalBill.productMass[i];
                long capacity = Math.multiplyExact(com.hbm.realsim.PhysicalRegistry.get(name).massUnits,
                        com.hbm.realsim.ProcessMachine.solid(name, 1).getMaxStackSize());
                while(left > 0) {
                    long parcel = Math.min(left, capacity);
                    if(solid++ == slot) return com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.productDisplay(name, parcel);
                    left -= parcel;
                }
            }
            return null;
        }
        /** Fractional physical products accrue in the process ledger; do not advertise a rounded litre per batch. */
        public ItemStack outputFluidDisplay(int slot) {
            FluidStack fluid = outputFluids[slot];
            if(fluid == null || fluid.fill <= 0 || fluid.type == Fluids.NONE) return null;
            if(physicalBill != null) for(int i = 0; i < physicalBill.outputs.length; i++)
                if(physicalBill.outputs[i].equals(fluid.type.getName()))
                    return com.hbm.items.machine.ItemFluidIcon.makePhysical(fluid, physicalBill.productMass[i]);
            return com.hbm.items.machine.ItemFluidIcon.make(fluid);
        }

		public int getId() {
			return this.id;
		}

		public ChemRecipe physicalProcess(long workJoules, long watts) {
            if(workJoules <= 0 || watts < 10) throw new IllegalArgumentException("Invalid physical process");
            physicalPowerQuanta = watts / 10L;
            duration = Math.toIntExact(1L + (Math.multiplyExact(workJoules, 2L) - 1L) / physicalPowerQuanta);
            return this;
        }
        public int durationAtSpeed(int speed) { return physicalPowerQuanta > 0 ? duration : Math.max(1, duration * speed / 100); }
        public long costPerTick(long equipment) { return physicalPowerQuanta > 0 ? physicalPowerQuanta : Math.max(1L, equipment * com.hbm.realsim.IndustrialDemand.chemicalBaseWatts(name) / com.hbm.realsim.IndustrialDemand.CHEMICAL_W); }
        public int getDuration() {
			return this.duration;
		}
	}

	@Override
	public String getFileName() {
		return "hbmChemplant.json";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = (JsonObject) recipe;
		int id = obj.get("id").getAsInt();
		String name = obj.get("name").getAsString();
		int duration = obj.get("duration").getAsInt();
        if(!obj.has("physical") && (com.hbm.realsim.ChemistryProcesses.reactorRequiresBill(id) || id == 59 || id == 60 || id == 1143 || id == 1144 || id == 1106)) throw new IllegalArgumentException("Released chemistry template " + id + " requires a physical bill; regenerate defaults");

		ChemRecipe parsed = new ChemRecipe(id, name, duration, false)
				.inputFluids(	readFluidArray(		(JsonArray) obj.get("fluidInput")))
				.inputItems(	readAStackArray(		(JsonArray) obj.get("itemInput")))
				.outputFluids(	readFluidArray(		(JsonArray) obj.get("fluidOutput")))
				.outputItems(	readItemStackArray(	(JsonArray) obj.get("itemOutput")));
		if(obj.has("physical")) parsed.petroleum(com.hbm.realsim.PetroleumProcesses.read(obj));
        if(!obj.has("physical")) for(FluidStack out : parsed.outputFluids)
            if(out != null && com.hbm.realsim.ChemistryProcesses.requiresBillForOutput(out.type.getName())) throw new IllegalArgumentException("Released fluid reactor JSON requires a physical bill");
        if(!obj.has("physical") && obj.has("physicalPowerQuanta")) parsed.physicalPowerQuanta = Math.max(0L, obj.get("physicalPowerQuanta").getAsLong());
        parsed.register(); recipes.add(parsed);
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		try {
		ChemRecipe chem = (ChemRecipe) recipe;
		writer.name("id").value(chem.id);
		writer.name("name").value(chem.name);
		writer.name("duration").value(chem.duration);
		writer.name("physicalPowerQuanta").value(chem.physicalPowerQuanta);
        if(chem.physicalBill != null) com.hbm.realsim.PetroleumProcesses.write(chem.physicalBill, writer);
		//Fluid IN
		writer.name("fluidInput").beginArray();
		for(FluidStack input : chem.inputFluids) { if(input != null) writeFluidStack(input, writer); }
		writer.endArray();
		//Item IN
		writer.name("itemInput").beginArray();
		for(AStack input : chem.inputs) { if(input != null) writeAStack(input, writer); }
		writer.endArray();
		//Fluid OUT
		writer.name("fluidOutput").beginArray();
		for(FluidStack output : chem.outputFluids) { if(output != null) writeFluidStack(output, writer); }
		writer.endArray();
		//Item OUT
		writer.name("itemOutput").beginArray();
		for(ItemStack output : chem.outputs) { if(output != null) writeItemStack(output, writer); }
		writer.endArray();
		} catch(Exception ex) {
			MainRegistry.logger.error(ex);
			ex.printStackTrace();
		}
	}

	public String getComment() {
		return "Rules: All in- and output arrays need to be present, even if empty. IDs need to be unique, but not sequential. It's safe if you add your own"
				+ " recipes starting with ID 1000. Template order depends on the order of the recipes in this JSON file. The 'name' field is responsible for"
				+ " the texture being loaded for the template. Custom dynamic texture generation is not yet implemented, you will have to throw the texture into"
				+ " the JAR manually.";
	}

	@Override
	public void deleteRecipes() {
		indexMapping.clear();
		recipes.clear();
		recipeGeneration++;
	}
}
