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

	@Override
	public void registerDefaults() {



		//6-30, formerly oil cracking, coal liquefaction and solidifciation
		registerOtherOil();

		recipes.add(new ChemRecipe(36, "COOLANT", 50)
				//SUGAR, NOT NITER
				//.inputItems(new OreDictStack(KNO.dust()))
				//more bobcat crack rock chemistry
				.inputFluids(new FluidStack(Fluids.WATER, 200), new FluidStack(Fluids.ETHANOL, 1800))
				.outputFluids(new FluidStack(Fluids.COOLANT, 2000)));
		recipes.add(new ChemRecipe(37, "CRYOGEL", 50)
				.inputItems(new ComparableStack(ModItems.powder_ice))
				.inputFluids(new FluidStack(Fluids.COOLANT, 1800))
				.outputFluids(new FluidStack(Fluids.CRYOGEL, 2000)));

		//TOOL STEEL DOES NOT INVOLVE FUCKING MERCURY RETARD
		recipes.add(new ChemRecipe(38, "DESH", 300)
				.inputItems(
					//new ComparableStack(ModItems.ingot_steel, 2),
					//new ComparableStack(ModItems.powder_coal, 1),
					new ComparableStack(ModItems.powder_desh_mix, 1),
					new ComparableStack(ModItems.ingot_chromium, 1),
					new ComparableStack(ModItems.ingot_vanadium, 1)
					//TODO
				)
				.inputFluids(
						//(GeneralConfig.enableLBSM && GeneralConfig.enableLBSMSimpleChemsitry) ?
								//new FluidStack[] {new FluidStack(Fluids.LIGHTOIL, 200)} :
								new FluidStack[] {new FluidStack(Fluids.OXYGEN, 200),
								new FluidStack(Fluids.ARGON, 20)})
				.outputItems(new ItemStack(ModItems.ingot_desh, 4))); //here have 4 since this is already cancer
		recipes.add(new ChemRecipe(39, "NITAN", 50)
				.inputItems(new ComparableStack(ModItems.powder_nitan_mix))
				.inputFluids(
						new FluidStack(Fluids.KEROSENE, 600),
						new FluidStack(Fluids.MERCURY, 200))
				.outputFluids(new FluidStack(Fluids.NITAN, 1000)));
		recipes.add(new ChemRecipe(40, "PEROXIDE", 50)
				.inputFluids(
					new FluidStack(Fluids.WATER, 1000)
					//new FluidStack(Fluids.OXYGEN, 500)
					//well I mean it's like everywhere but at the same time the like chemical process or something
				)
				.outputFluids(new FluidStack(Fluids.PEROXIDE, 1000)));
		recipes.add(new ChemRecipe(90, "SULFURIC_ACID", 50)
				.inputItems(new OreDictStack(S.dust()))
				.inputFluids(
						new FluidStack(Fluids.PEROXIDE, 800),
						new FluidStack(Fluids.WATER, 1_000))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 2_000)));
		recipes.add(new ChemRecipe(92, "SOLVENT", 50)
				.inputFluids(new FluidStack(Fluids.NAPHTHA, 500), new FluidStack(Fluids.AROMATICS, 500))
				.outputFluids(new FluidStack(Fluids.SOLVENT, 1000)));
		recipes.add(new ChemRecipe(43, "POLYMER", 100)
				.inputItems(
						new OreDictStack(COKE.dust(), 2),
						new OreDictStack(F.dust()))
				.inputFluids(new FluidStack(Fluids.PETROLEUM, 500, GeneralConfig.enable528 ? 1 : 0))
				.outputItems(new ItemStack(ModItems.ingot_polymer, 16)));

		//but synthetic polymers exist
		recipes.add(new ChemRecipe(44, "SYN_POLYMER", 180)
				.inputItems(
						new OreDictStack(COKE.dust(), 2),
						new OreDictStack(F.dust()))
				.inputFluids(new FluidStack(Fluids.BIOFUEL, 1500))
				.outputItems(new ItemStack(ModItems.ingot_polymer)));



		recipes.add(new ChemRecipe(81, "BAKELITE", 100)
				.inputFluids(
						new FluidStack(Fluids.AROMATICS, 500, GeneralConfig.enable528 ? 1 : 0),
						new FluidStack(Fluids.PETROLEUM, 500, GeneralConfig.enable528 ? 1 : 0))
				.outputItems(new ItemStack(ModItems.ingot_bakelite)));
		recipes.add(new ChemRecipe(82, "RUBBER", 100)
						.inputItems(
						new OreDictStack(S.dust()),
						new OreDictStack(ZI.dust()))
				.inputFluids(new FluidStack(Fluids.UNSATURATEDS, 500, GeneralConfig.enable528 ? 2 : 0))
				.outputItems(new ItemStack(ModItems.ingot_rubber)));
		/*recipes.add(new ChemRecipe(94, "PET", 100)
				.inputItems(new OreDictStack(AL.dust()))
				.inputFluids(
						new FluidStack(Fluids.XYLENE, 500),
						new FluidStack(Fluids.OXYGEN, 100))
				.outputItems(new ItemStack(ModItems.ingot_pet)));*/



		//Laminate Glass going here
		recipes.add(new ChemRecipe(97, "LAMINATE", 100)
				.inputFluids(
						new FluidStack(Fluids.XYLENE, 250),
						new FluidStack(Fluids.PHOSGENE, 250))
				.inputItems(
						new ComparableStack(ModBlocks.reinforced_glass),
						new OreDictStack(STEEL.bolt(), 4))
				.outputItems(new ItemStack(com.hbm.blocks.ModBlocks.reinforced_laminate)));
		recipes.add(new ChemRecipe(94, "PC", 100)
				.inputFluids(
						new FluidStack(Fluids.XYLENE, 500, GeneralConfig.enable528 ? 2 : 0),
						new FluidStack(Fluids.PHOSGENE, 500, GeneralConfig.enable528 ? 2 : 0))
				.outputItems(new ItemStack(ModItems.ingot_pc)));
		recipes.add(new ChemRecipe(96, "PVC", 100)
				.inputItems(new OreDictStack(CD.dust()))
				.inputFluids(
						new FluidStack(Fluids.UNSATURATEDS, 250, GeneralConfig.enable528 ? 2 : 0),
						new FluidStack(Fluids.CHLORINE, 250, GeneralConfig.enable528 ? 2 : 0))
				.outputItems(new ItemStack(ModItems.ingot_pvc, 2)));
		recipes.add(new ChemRecipe(89, "DYNAMITE", 50)
				.inputItems(
						new ComparableStack(Items.sugar),
						new OreDictStack(KNO.dust()),
						new OreDictStack("sand"))
				.outputItems(new ItemStack(ModItems.ball_dynamite, 2)));
		recipes.add(new ChemRecipe(83, "TNT", 150)
				.inputItems(new OreDictStack(KNO.dust()))
				.inputFluids(new FluidStack(Fluids.AROMATICS, 500, GeneralConfig.enable528 ? 1 : 0))
				.outputItems(new ItemStack(ModItems.ball_tnt, 4)));
		recipes.add(new ChemRecipe(95, "TATB", 50)
				.inputItems(new ComparableStack(ModItems.ball_tnt))
				.inputFluids(new FluidStack(Fluids.SOURGAS, 200, 1), new FluidStack(Fluids.NITRIC_ACID, 10))
				.outputItems(new ItemStack(ModItems.ball_tatb)));
		recipes.add(new ChemRecipe(84, "C4", 150)
				.inputItems(new OreDictStack(KNO.dust()))
				.inputFluids(new FluidStack(Fluids.UNSATURATEDS, 500, GeneralConfig.enable528 ? 1 : 0))
				.outputItems(new ItemStack(ModItems.ingot_c4, 4)));
		//44, formerly deuterium
		//45, formerly steam
		recipes.add(new ChemRecipe(46, "YELLOWCAKE", 250)
				.inputItems(
						new OreDictStack(U.billet(), 2), //12 nuggets: the numbers do match up :)
						new OreDictStack(S.dust(), 2))
				.inputFluids(new FluidStack(Fluids.PEROXIDE, 500))
				.outputFluids(new FluidStack(Fluids.RAFFINATE, 1000))
				.outputItems(new ItemStack(ModItems.powder_yellowcake)));
		//what is this mickey mouse shit
		recipes.add(new ChemRecipe(47, "UF6", 800)
				.inputItems(
						new ComparableStack(ModItems.powder_yellowcake, 4))
				//not technically right? But also not wrong? There's no water in this process though.
						//new OreDictStack(F.dust(), 4))
				.inputFluids(new FluidStack(Fluids.FLUORINE, 600))
				//.outputItems(new ItemStack(ModItems.sulfur, 2)) //?
				.outputFluids(new FluidStack(Fluids.UF6, 1200)));
		recipes.add(new ChemRecipe(48, "PUF6", 950)
				.inputItems(
						new OreDictStack(PU.dust()))
						//new OreDictStack(F.dust(), 3))
				.inputFluids(new FluidStack(Fluids.FLUORINE, 600))
				.outputFluids(new FluidStack(Fluids.PUF6, 900)));
		recipes.add(new ChemRecipe(49, "SAS3", 200)
				.inputItems(
						new OreDictStack(SA326.dust()),
						new OreDictStack(S.dust(), 2))
				.inputFluids(new FluidStack(Fluids.PEROXIDE, 2000))
				.outputFluids(new FluidStack(Fluids.SAS3, 1000)));
		recipes.add(new ChemRecipe(53, "CORDITE", 40)
				.inputItems(
						new OreDictStack(KNO.dust(), 2),
						new OreDictStack(KEY_PLANKS),
						new ComparableStack(Items.sugar))
				.inputFluids(
						(GeneralConfig.enableLBSM && GeneralConfig.enableLBSMSimpleChemsitry) ?
								new FluidStack(Fluids.HEATINGOIL, 200) :
								new FluidStack(Fluids.GAS, 200))
				.outputItems(new ItemStack(ModItems.cordite, 4)));
		//and then the chemists arrived
		recipes.add(new ChemRecipe(54, "KEVLAR", 40)
				.inputItems(
						new OreDictStack(KNO.dust(), 2),
						new ComparableStack(Items.brick),
						new OreDictStack(COAL.dust()))
				.inputFluids(new FluidStack(Fluids.OXYHYDROGEN, 100), new FluidStack(Fluids.AMMONIA, 100))
				.outputItems(new ItemStack(ModItems.plate_kevlar, 4)));
		recipes.add(new ChemRecipe(55, "CONCRETE", 100)
				.inputItems(
						new ComparableStack(ModItems.powder_cement, 1),
						new ComparableStack(Blocks.gravel, 8),
						new OreDictStack(KEY_SAND, 8))
				.inputFluids(new FluidStack(Fluids.WATER, 2000))
				.outputItems(new ItemStack(ModBlocks.concrete_smooth, 16)));
		recipes.add(new ChemRecipe(56, "CONCRETE_ASBESTOS", 100)
				.inputItems(
						new ComparableStack(ModItems.powder_cement, 1),
						new ComparableStack(Blocks.gravel, 2),
						new OreDictStack(KEY_SAND, 2),
						(GeneralConfig.enableLBSM && GeneralConfig.enableLBSMSimpleChemsitry) ?
								new OreDictStack(ASBESTOS.ingot(), 1) :
								new OreDictStack(ASBESTOS.ingot(), 4))
				.inputFluids(new FluidStack(Fluids.WATER, 2000))
				.outputItems(new ItemStack(ModBlocks.concrete_asbestos, 16)));
		recipes.add(new ChemRecipe(79, "DUCRETE", 150)
				.inputItems(
						new ComparableStack(ModItems.powder_cement, 4),
						new ComparableStack(Blocks.gravel, 2),
						new OreDictStack(KEY_SAND, 8),
						new OreDictStack(U238.billet(), 2))
				.inputFluids(new FluidStack(Fluids.WATER, 2000))
				.outputItems(new ItemStack(ModBlocks.ducrete_smooth, 8)));
		recipes.add(new ChemRecipe(57, "SOLID_FUEL", 200)
				.inputItems(new ComparableStack(ModItems.solid_fuel, 2))
				.inputFluids(
						new FluidStack(Fluids.PETROLEUM, 200, GeneralConfig.enable528 ? 1 : 0),
						new FluidStack(Fluids.NITRIC_ACID, 100))
				.outputItems(new ItemStack(ModItems.rocket_fuel, 4)));
		recipes.add(new ChemRecipe(58, "ELECTROLYSIS", 150)
				.inputFluids(new FluidStack(Fluids.WATER, 4000))
				.outputFluids(
						new FluidStack(Fluids.HYDROGEN, 400),
						new FluidStack(Fluids.OXYGEN, 400)));
		recipes.add(new ChemRecipe(59, "XENON", 250, 1)
				.inputFluids(new FluidStack(Fluids.NONE, 0))
				.outputFluids(new FluidStack(Fluids.XENON, 50)));
		recipes.add(new ChemRecipe(60, "XENON_OXY", 20)
				.inputFluids(new FluidStack(Fluids.OXYGEN, 250))
				.outputFluids(new FluidStack(Fluids.XENON, 50)));
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
		recipes.add(new ChemRecipe(63, "SCHRABIDIC", 100)
				.inputItems(new ComparableStack(ModItems.pellet_charged))
				.inputFluids(
						new FluidStack(Fluids.SAS3, 8000),
						new FluidStack(Fluids.PEROXIDE, 6000))
				.outputFluids(new FluidStack(Fluids.SCHRABIDIC, 16000)));
		recipes.add(new ChemRecipe(64, "SCHRABIDATE", 150)
				.inputItems(new OreDictStack(IRON.dust()))
				.inputFluids(new FluidStack(Fluids.SCHRABIDIC, 250))
				.outputItems(new ItemStack(ModItems.powder_schrabidate)));
		recipes.add(new ChemRecipe(65, "COLTAN_CLEANING", 60)
				.inputItems(
						new OreDictStack(COLTAN.dust(), 2))
							//,
						//new OreDictStack(ANY_COAL_COKE.dust()))
				.inputFluids(
						new FluidStack(Fluids.PEROXIDE, 250),
						new FluidStack(Fluids.HYDROGEN, 500))
				.outputItems(
						new ItemStack(ModItems.powder_coltan),
						new ItemStack(ModItems.powder_niobium),
						new ItemStack(ModItems.powder_tantalium))
						//BOB YOU'RE A FUCKING RETARD LEARN CHEMISTRY
				.outputFluids(new FluidStack(Fluids.WATER, 500)));
		//pls die in a fire kthx
		//recipes.add(new ChemRecipe(66, "COLTAN_PAIN", 120)
		//		.inputItems(
		//				new ComparableStack(ModItems.powder_coltan),
		//				new OreDictStack(F.dust()))
		//		.inputFluids(
		//				new FluidStack(Fluids.GAS, 1000),
		//				new FluidStack(Fluids.OXYGEN, 500))
		//		.outputFluids(new FluidStack(Fluids.PAIN, 1000)));

		//HAHA 67 HAHAHA 67 67 67
		recipes.add(new ChemRecipe(67, "COLTAN_CRYSTAL", 80)
				.inputFluids(
						new FluidStack(Fluids.PAIN, 1000),
						new FluidStack(Fluids.PEROXIDE, 500))
				.outputItems(
						new ItemStack(ModItems.gem_tantalium),
						new ItemStack(ModItems.dust, 3))
				.outputFluids(new FluidStack(Fluids.WATER, 250)));
		recipes.add(new ChemRecipe(68, "VIT_LIQUID", 100)
				.inputItems(new ComparableStack(ModBlocks.sand_lead))
				.inputFluids(new FluidStack(Fluids.WASTEFLUID, 1000))
				.outputItems(new ItemStack(ModItems.nuclear_waste_vitrified)));
		recipes.add(new ChemRecipe(69, "VIT_GAS", 100)
				.inputItems(new ComparableStack(ModBlocks.sand_lead))
				.inputFluids(new FluidStack(Fluids.WASTEGAS, 1000))
				.outputItems(new ItemStack(ModItems.nuclear_waste_vitrified)));
		recipes.add(new ChemRecipe(88, "LUBRICANT", 20)
				.inputFluids(
						new FluidStack(Fluids.HEATINGOIL, 500),
						new FluidStack(Fluids.UNSATURATEDS, 500))
				.outputFluids(new FluidStack(Fluids.LUBRICANT, 1000)));
		recipes.add(new ChemRecipe(70, "TEL", 40)
				.inputItems(
						new OreDictStack(KEY_ANY_TAR), //FIXED
						new OreDictStack(PB.dust()))
				.inputFluids(
						new FluidStack(Fluids.PETROLEUM, 100),
						new FluidStack(Fluids.STEAM, 1000))
				.outputItems(new ItemStack(ModItems.fuel_additive)));
		recipes.add(new ChemRecipe(4, "FR_REOIL", 30)
				.inputFluids(new FluidStack(1000, Fluids.SMEAR))
				.outputFluids(new FluidStack(800, Fluids.RECLAIMED)));
		recipes.add(new ChemRecipe(5, "FR_PETROIL", 30)
				.inputFluids(
						new FluidStack(800, Fluids.RECLAIMED),
						new FluidStack(200, Fluids.LUBRICANT))
				.outputFluids(new FluidStack(1000, Fluids.PETROIL)));
		recipes.add(new ChemRecipe(86, "PETROIL_LEADED", 40)
				.inputItems(new ComparableStack(ModItems.fuel_additive))
				.inputFluids(new FluidStack(Fluids.PETROIL, 10_000))
				.outputFluids(new FluidStack(Fluids.PETROIL_LEADED, 12_000)));
		recipes.add(new ChemRecipe(71, "GASOLINE", 40)
				.inputFluids(new FluidStack(Fluids.NAPHTHA, 1000))
				.outputFluids(new FluidStack(Fluids.GASOLINE, 800)));

		//WHY THE FUCK DOES LEADED FUCKING GASOLINE EXIST IF WE AREN'T GOING TO GIVE IT A USE BOB?
		// HUH? MAYBE WE COULD USE IT TO RUN OUR FUCKING ENGINES HOTTER IN RETURN FOR MORE POLLUTION?
		// NO LET'S JUST MAKE IT BE USED IN LITERALLY ONLY THE TOSSABLE GAS CAN FOR SOME REASON!!!
		recipes.add(new ChemRecipe(85, "GASOLINE_LEADED", 40)
				.inputItems(new ComparableStack(ModItems.fuel_additive))
				.inputFluids(new FluidStack(Fluids.GASOLINE, 10_000))
				.outputFluids(new FluidStack(Fluids.GASOLINE_LEADED, 12_000)));
		recipes.add(new ChemRecipe(87, "COALGAS_LEADED", 40)
				.inputItems(new ComparableStack(ModItems.fuel_additive))
				.inputFluids(new FluidStack(Fluids.COALGAS, 10_000))
				.outputFluids(new FluidStack(Fluids.COALGAS_LEADED, 12_000)));
		recipes.add(new ChemRecipe(72, "FRACKSOL", 20)
				.inputItems(new OreDictStack(S.dust()))
				.inputFluids(
						new FluidStack(Fluids.PETROLEUM, 100),
						new FluidStack(Fluids.WATER, 1000))
				.outputFluids(new FluidStack(Fluids.FRACKSOL, 1000)));
		//kill
		recipes.add(new ChemRecipe(73, "HELIUM3", 200)
				.inputItems(new ComparableStack(ModBlocks.moon_turf, 8))
				.outputFluids(new FluidStack(Fluids.HELIUM3, 1000)));

		//one bucket of ethanol equals 275_000 TU using the diesel baseline0
		//the coal baseline is 400_000 per piece
		//if we assume a burntime of 1.5 ops (300 ticks) for sugar at 100 TU/t that would equal a total of 30_000 TU
		recipes.add(new ChemRecipe(75, "ETHANOL", 50)
				.inputItems(new ComparableStack(Items.sugar, 10))
				.outputFluids(new FluidStack(Fluids.ETHANOL, 1000)));
		//recipes.add(new ChemRecipe(76, "METH", 30)
		//		.inputItems(
		//				new ComparableStack(Items.wheat),
		//				new ComparableStack(Items.dye, 2, 3))
		//		.inputFluids(
		//				new FluidStack(Fluids.LUBRICANT, 400),
		//				new FluidStack(Fluids.PEROXIDE, 400))
		//		.outputItems(new ItemStack(ModItems.chocolate, 4)));
		recipes.add(new ChemRecipe(77, "CO2", 60)
				.inputFluids(new FluidStack(Fluids.GAS, 1000))
				.outputFluids(new FluidStack(Fluids.CARBONDIOXIDE, 1000)));
		recipes.add(new ChemRecipe(78, "HEAVY_ELECTROLYSIS", 150)
				.inputFluids(new FluidStack(Fluids.HEAVYWATER, 2000))
				.outputFluids(
						new FluidStack(Fluids.DEUTERIUM, 200),
						new FluidStack(Fluids.OXYGEN, 200)));
		//recipes.add(new ChemRecipe(80, "EPEARL", 100)
		//		.inputItems(new OreDictStack(DIAMOND.dust(), 1))
		//		.inputFluids(new FluidStack(Fluids.XPJUICE, 500))
		//		.outputFluids(new FluidStack(Fluids.ENDERJUICE, 100)));
		//recipes.add(new ChemRecipe(99, "NITROCRYO", 150)
				//.inputItems(new ComparableStack(ModItems.powder_ice, 4))
				//.inputFluids(new FluidStack(Fluids.BLOOD, 1000))
				//.outputItems(new ItemStack(Blocks.sand, 4))
				//.outputFluids(new FluidStack(Fluids.NITROGEN, 1000)));
		recipes.add(new ChemRecipe(99, "NITROACID", 180)
			.inputFluids(
				new FluidStack(Fluids.AMMONIA, 1000),
				//new FluidStack(Fluids.OXYGEN, 1000),
				new FluidStack(Fluids.WATER, 500))
			.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 1000)));
		recipes.add(new ChemRecipe(100, "HYDRAZINE", 250)
				.inputFluids(new FluidStack(Fluids.NITRIC_ACID, 2000), new FluidStack(Fluids.AMMONIA, 1000))
				.outputFluids(new FluidStack(Fluids.HYDRAZINE, 800)));
		//ammonia
		recipes.add(new ChemRecipe(101, "AMONGUS", 250)
				.inputFluids(new FluidStack(Fluids.NITROGEN, 600), new FluidStack(Fluids.HYDROGEN, 1000))
				.outputFluids(new FluidStack(Fluids.AMMONIA, 800)));
		recipes.add(new ChemRecipe(103, "HCL", 250)
				.inputFluids(new FluidStack(Fluids.HYDROGEN, 300), new FluidStack(Fluids.CHLORINE, 1000))
				.outputFluids(new FluidStack(Fluids.HCL, 400)));
		recipes.add(new ChemRecipe(104, "AMMONIUM", 250)
				.inputFluids(new FluidStack(Fluids.AMMONIA, 500), new FluidStack(Fluids.NITRIC_ACID, 1000))
				.outputItems(new ItemStack(ModItems.ammonium_nitrate, 4)));
		recipes.add(new ChemRecipe(105, "ARSENIDE", 250)
				.inputFluids(new FluidStack(Fluids.WATER, 500), new FluidStack(Fluids.LIGHTOIL, 900))
				.inputItems(
						new ComparableStack(ModItems.nugget_gallium),
						new ComparableStack(ModItems.nugget_arsenic))
				.outputItems(new ItemStack(ModItems.nugget_gaas, 1)));

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

		recipes.add(new ChemRecipe(115, "SHELL_CHLORINE", 100)
				.inputItems(
						new ComparableStack(ModItems.ammo_arty, 1, 0),
						new OreDictStack(ANY_PLASTIC.ingot(), 1))
				.inputFluids(new FluidStack(Fluids.CHLORINE, 4000))
				.outputItems(new ItemStack(ModItems.ammo_arty, 1, 9)));
		recipes.add(new ChemRecipe(116, "SHELL_PHOSGENE", 100)
				.inputItems(
						new ComparableStack(ModItems.ammo_arty, 1, 0),
						new OreDictStack(ANY_PLASTIC.ingot(), 1))
				.inputFluids(new FluidStack(Fluids.PHOSGENE, 4000))
				.outputItems(new ItemStack(ModItems.ammo_arty, 1, 10)));
		recipes.add(new ChemRecipe(117, "SHELL_MUSTARD", 100)
				.inputItems(
						new ComparableStack(ModItems.ammo_arty, 1, 0),
						new OreDictStack(ANY_PLASTIC.ingot(), 1))
				.inputFluids(new FluidStack(Fluids.MUSTARDGAS, 4000))
				.outputItems(new ItemStack(ModItems.ammo_arty, 1, 11)));
		recipes.add(new ChemRecipe(118, "CC_CENTRIFUGE", 200)
				.inputFluids(new FluidStack(Fluids.CHLOROCALCITE_CLEANED, 500), new FluidStack(Fluids.SULFURIC_ACID, 8_000))
				.outputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 250), new FluidStack(Fluids.CALCIUM_CHLORIDE, 250)));
		recipes.add(new ChemRecipe(119, "THORIUM_SALT", 60)
				.inputFluids(new FluidStack(Fluids.THORIUM_SALT_DEPLETED, 16_000))
				.inputItems(new OreDictStack(TH232.nugget(), 2))
				.outputFluids(new FluidStack(Fluids.THORIUM_SALT, 16_000))
				.outputItems(
						new ItemStack(ModItems.nugget_u233, 1),
						new ItemStack(ModItems.nuclear_waste_tiny, 1)));

		recipes.add(new ChemRecipe(120, "MASS_CAKE", 30)
				.inputFluids(new FluidStack(Fluids.CMILK, 4000), new FluidStack(Fluids.CREAM, 1000)) // why not regular milk? well its because the refined products allow for higher mass cakes while still needing less milk
				.inputItems(
						new ComparableStack(Items.sugar, 8),				// if there is a hole in my logic i will shoot myself
						new ComparableStack(Items.egg, 4))				//ex: since a cake needs 3 buckets of milk, c-milk is more dense, leading to it being only 4 buckets of condensed milk, thats 1 bucket per cake.
				.outputItems(
						new ItemStack(Items.cake, 4)));

		recipes.add(new ChemRecipe(121, "BUTTER", 50)
				.inputFluids(new FluidStack(Fluids.EMILK, 1000))
				.outputItems(
						new ItemStack(ModItems.butter)));
		recipes.add(new ChemRecipe(122, "STRAWICE", 50)
				.inputFluids(new FluidStack(Fluids.CREAM, 1000))
				.inputItems(
						new ComparableStack(ModItems.butter, 2),
						new ComparableStack(Blocks.packed_ice, 1),
						new ComparableStack(ModItems.strawberry, 4))
				.outputItems(
						new ItemStack(ModItems.s_cream, 4)));
		recipes.add(new ChemRecipe(123, "POISON", 250)
			.inputFluids(new FluidStack(Fluids.NITROGEN, 50)) //, new FluidStack(Fluids.CHLORINE, 50)
			.inputItems(
				new ComparableStack(ModItems.powder_sodium, 1),
				new ComparableStack(ModItems.powder_coal, 1))

			.outputItems(new ItemStack(ModItems.powder_poison, 4)));

		recipes.add(new ChemRecipe(1001, "SOIL", 100)
				.inputFluids(new FluidStack(Fluids.WATER, 4000))
				.inputItems(
						new ComparableStack(ModItems.ammonium_nitrate, 1),
						new ComparableStack(Blocks.gravel, 8))
				.outputItems(new ItemStack(Blocks.dirt, 8)));

		recipes.add(new ChemRecipe(1002, "CHLOROMETHANE", 50)
				.inputFluids(new FluidStack(Fluids.GAS, 750), new FluidStack(Fluids.CHLORINE, 250))
				.outputFluids(new FluidStack(Fluids.CHLOROMETHANE, 1000)));

		//aluminum processing

		recipes.add(new ChemRecipe(1003, "Bayer Process", 50)
			.inputItems(new ComparableStack(ModBlocks.ore_aluminium))
			.inputFluids(
				new FluidStack(Fluids.WATER, 600),
				new FluidStack(Fluids.SODIUM_HYDROXIDE, 200))
			.outputFluids(new FluidStack(Fluids.REDMUD, 1000))
			.outputItems(
				new ItemStack(ModItems.powder_aluminium, 8),
				//new ItemStack(ModItems.REE_sludge, 1) //REE sludge is a yttrium/scandium processing step; not sure that's right
				new ItemStack(ModItems.powder_gallium_tiny, 3),
				//bauxite sands
				new ItemStack(Blocks.sand, 1),
				//we have one item slot left here, I could be autistic about this or simplistic here
				//Trace Element Concentration: Naturally occurring radioactive materials like uranium, thorium,
				//and radium are concentrated in the residue, known as TENORM
				//Ok for my sanity here's a thorium nugget
				new ItemStack(ModItems.nugget_th232, 1)

			));

		recipes.add(new ChemRecipe(1004, "URANIUM_BROMIDE", 50)
			.inputItems(
				new OreDictStack(U235.billet(), 1),
				new ComparableStack(ModItems.powder_bromine),
				new OreDictStack(ASBESTOS.ingot(), 1))
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 4000))
			.outputFluids(new FluidStack(Fluids.URANIUM_BROMIDE, 4000)));
		recipes.add(new ChemRecipe(1005, "PLUTONIUM_BROMIDE", 50)
			.inputItems(
				new OreDictStack(PU239.billet(), 1),
				new ComparableStack(ModItems.powder_bromine),
				new OreDictStack(ASBESTOS.ingot(), 1))
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 4000))
			.outputFluids(new FluidStack(Fluids.PLUTONIUM_BROMIDE, 4000)));

		recipes.add(new ChemRecipe(1007, "THORIUM_BROMIDE", 50)
			.inputItems(
				new OreDictStack(TH232.billet(), 1),
				new ComparableStack(ModItems.powder_bromine),
				new OreDictStack(ASBESTOS.ingot(), 1))
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 4000))
			.outputFluids(new FluidStack(Fluids.THORIUM_BROMIDE, 4000)));

		//ACTUAL FRACKING SOLUTION
		recipes.add(new ChemRecipe(1008, "FRACKSOL2", 20)
			.inputItems(
				new OreDictStack(S.dust()),
				new ComparableStack(Blocks.gravel, 8)
			)
			.inputFluids(
				//new FluidStack(Fluids.PETROLEUM, 100),
				new FluidStack(Fluids.WATER, 1000))
			.outputFluids(new FluidStack(Fluids.FRACKSOL, 1000)));


		//coal gasification
		recipes.add(new ChemRecipe(1009, "COALGAS", 60)
			.inputItems(
				new OreDictStack(COAL.dust(), 8))
			.inputFluids(
				new FluidStack(Fluids.ULTRAHOTSTEAM, 3000))
			.outputFluids(
				new FluidStack(Fluids.HYDROGEN, 1000),
				new FluidStack(Fluids.CARBONDIOXIDE, 500)
				//new FluidStack(Fluids.GAS, 5)
				//this fucking idiot bob only supports 2 outputs.
			));

		// methane reforming
		recipes.add(new ChemRecipe(1010, "SMR", 60)
			.inputFluids(
				new FluidStack(Fluids.GAS, 1000),
				new FluidStack(Fluids.ULTRAHOTSTEAM, 1000))
			.outputFluids(
				new FluidStack(Fluids.HYDROGEN, 3000),
				new FluidStack(Fluids.CARBONDIOXIDE, 1000)));


		//molten salt actually this is going in the crucible since it's FUCKING USELESS BOB YOU RELENTLESS FUCK
		//NEVERMIND THE FUCKING CRUCIBLE ONLY OUTPUTS FUCKING SOLIDS FOR SOME FUCKING REASON DESPITE BEING USED TO POUR FLUIDS
		//NICE SHITLOW TINKERS RIPPED DOGSHIT SYSTEM. ANYWAY IT'S GOING IN THE FUCKING CHEMPLANT JUST LIKE EVERY OTHER RETARDED
		//ACTUAL CHEMICAL PROCESS
		recipes.add(new ChemRecipe(1011, "MOLTEN_SALT", 500)
			.inputItems(
				new OreDictStack(KEY_SALT, 4))
			//.inputFluids(
			//	new FluidStack(Fluids.ULTRAHOTSTEAM, 2000))
					//I don't give a fuck suffer
			//nvm just use fucking energy because why not
			.outputFluids(new FluidStack(Fluids.MOLTEN_SALT, 576)));

		//potash to potassium chloride
		recipes.add(new ChemRecipe(1012, "POTASSIUM_CHLORIDEPROD", 50)
				.inputItems(
					new ComparableStack(ModItems.powder_potash)
				)
				.outputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 2000))
			.outputItems(
				new ItemStack(ModItems.itemsalt, 2),
				new ItemStack(ModItems.rubidiumsalt, 1)));
		//oh my god it's radioactive and
		// god fucking dammit I need to add more ways to obtain it and
		// god fucking dammit I have to rebalance anything relating to clocks or GPS systems.

		//Molten KCl + sodium vapor → molten NaCl + potassium vapor
		recipes.add(new ChemRecipe(1013, "POTASSIUM", 90)
			.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 2000), new FluidStack(Fluids.SODIUM, 2000))

			.outputFluids(
				new FluidStack(Fluids.MOLTEN_SALT, 2000),
				new FluidStack(Fluids.POTASSIUM, 2000))
			);

		//potassium hydroxide + water + Iodine (2) = potassium iodide
		recipes.add(new ChemRecipe(1014, "POTASSIUM_IODIDE", 100)
			.inputFluids(new FluidStack(Fluids.POTASSIUM_HYDROXIDE, 1000), new FluidStack(Fluids.WATER, 500))
			.inputItems(new ComparableStack(ModItems.powder_iodine, 2))


			.outputItems(new ItemStack(ModItems.potassium_iodide_powder, 2))

		);


		//rubidium salt -> rubidium metal
		recipes.add(new ChemRecipe(1015, "RUBIDIUM", 90)
			.inputItems(
				new ComparableStack(ModItems.rubidiumsalt, 1),
				new ComparableStack(ModItems.ingot_calcium, 1))

				//.inputFluids(new FluidStack(Fluids.CALCIUM_SOLUTION, 2000)) //

			//.outputFluids(
			//	new FluidStack(Fluids.MOLTEN_SALT, 2000),
			//	new FluidStack(Fluids.RUBIDIUM, 2000))

				.outputItems(new ItemStack(ModItems.rubidium_ingot, 1))
				.outputFluids(new FluidStack(Fluids.CALCIUM_CHLORIDE, 800)));


		//pure potassium
		//Raw potassium metal is primarily manufactured by reducing molten potassium chloride with sodium metal at high temperatures
		//recipes.add(new ChemRecipe(1016, "POTASSIUM_PURE", 110)
		//	.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 2000), new FluidStack(Fluids.SODIUM, 2000))
		//		.outputItems(new ItemStack(ModItems.potassium_powder, 1), new ItemStack(ModItems.itemsalt, 1)));
		//should be done in the solidifier
		//done

		//sodium hydroxide
		recipes.add(new ChemRecipe(1016, "SODIUM_HYDROXIDE", 50)
			.inputFluids(new FluidStack(Fluids.SODIUM, 1000), new FluidStack(Fluids.WATER, 500))
			.outputFluids(new FluidStack(Fluids.SODIUM_HYDROXIDE, 1000))
		);

		//recipes.add(new ChemRecipe(1017, "ARGON", 100)
		//	.inputFluids(new FluidStack(Fluids.AIR, 1000))
		//	.outputFluids(new FluidStack(Fluids.ARGON, 10))
		//);
		//in cryogenic distillation

		//pollucite dust + sulfuric acid = Mixed sulfate solution + silica
		recipes.add(new ChemRecipe(1017, "POLLUCITE", 100)
			.inputItems(new ComparableStack(ModItems.powder_pollucite))
			.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 2000))
			.outputFluids(new FluidStack(Fluids.POLLUCITE_SOLUTION, 2000))
			.outputItems(new ItemStack(Blocks.sand, 8), new ItemStack(ModItems.powder_quartz, 2))
		);

		//pollucite solution into heavy fraction and light fraction
		recipes.add(new ChemRecipe(1018, "POLLUCITE_FRACTION", 100)
			.inputFluids(new FluidStack(Fluids.POLLUCITE_SOLUTION, 2000))
			.outputFluids(new FluidStack(Fluids.POLLUCITE_SOLUTION_HEAVY, 500), new FluidStack(Fluids.POLLUCITE_SOLUTION_LIGHT, 1500))
		);

		//heavy fraction → cesium alum (or cesium compound)
		recipes.add(new ChemRecipe(1019, "CESIUM_EXTRACTION", 100)
			.inputFluids(new FluidStack(Fluids.POLLUCITE_SOLUTION_HEAVY, 1000))
			.outputItems(new ItemStack(ModItems.cesium_salt, 1), new ItemStack(ModItems.rubidiumsalt))

		);

		//light fraction → Na + K
		recipes.add(new ChemRecipe(1020, "POTASSIUM_SODIUM_EXTRACTION", 100)
			.inputFluids(new FluidStack(Fluids.POLLUCITE_SOLUTION_LIGHT, 1000))
			//.outputFluids(new FluidStack(Fluids.SODIUM, 500), new FluidStack(Fluids.POTASSIUM, 500))
			.outputItems(
			    new ItemStack(ModItems.sodium_sulfate, 1),
			    new ItemStack(ModItems.potassium_sulfate, 1)
			)
		);


		//cesium salt + calcium -> cesium metal + calcium chloride
		recipes.add(new ChemRecipe(1021, "CESIUM_EXTRACTION_2", 100)
			.inputFluids(new FluidStack(Fluids.ARGON, 10)) // for storing cesium (it's highly reactive)
			.inputItems(new ComparableStack(ModItems.cesium_salt, 1), new ComparableStack(ModItems.ingot_calcium, 1))
			//we are changing the cesium powder item to be a contained version of cesium since it cannot exist in air
			.outputItems(new ItemStack(ModItems.powder_caesium, 1))
			.outputFluids(new FluidStack(Fluids.CALCIUM_CHLORIDE, 800))
		);


		//easier glass production using sodium sulfate
		recipes.add(new ChemRecipe(1022, "GLASS_SODIUM", 50)
			.inputItems(new ComparableStack(ModItems.sodium_sulfate, 1), new ComparableStack(Blocks.sand, 8))
			.outputItems(new ItemStack(Blocks.glass, 8))
		);

		// Alkali roasting of beryl (fixed + balanced) (SIMPLISTIC)
		recipes.add(new ChemRecipe(1023, "BERYL_ROASTING", 120)
			.inputItems(new ComparableStack(ModBlocks.ore_beryllium, 2))
			.inputFluids(new FluidStack(Fluids.SODIUM_CARBONATE, 1000))
			.outputItems(
				new ItemStack(ModItems.powder_beryllium, 2),
				new ItemStack(ModItems.powder_aluminium, 3),
				new ItemStack(ModItems.powder_sodium_silicate, 4)
				//not actually a powder, looks more like crystal meth but whatever
				//new ItemStack(ModItems.sodium_silicate, 3)
			)
		);

		// Sodium carbonate synthesis (CO2 absorption)
		recipes.add(new ChemRecipe(1024, "SODIUM_CARBONATE", 60)
			.inputFluids(
				new FluidStack(Fluids.SODIUM_HYDROXIDE, 1000),
				new FluidStack(Fluids.CARBONDIOXIDE, 1000)
			)
			.outputFluids(
				new FluidStack(Fluids.SODIUM_CARBONATE, 1000),
				new FluidStack(Fluids.WATER, 500)
			)
		);

		//reinforced glass (silicate use
		recipes.add(new ChemRecipe(1025, "REINFORCED_GLASS", 100)
			.inputItems(new ComparableStack(ModItems.powder_sodium_silicate, 4), new ComparableStack(Blocks.sand, 8))
			.outputItems(new ItemStack(ModBlocks.reinforced_glass, 8))
		);

		recipes.add(new ChemRecipe(1026, "CALCINED_DOLOMITE", 100)
			//dolomite
			.inputItems(new ComparableStack(ModBlocks.ore_magnesite, 1))
			.outputItems(new ItemStack(ModItems.calcined_dolomite, 1))
			.outputFluids(new FluidStack(Fluids.CARBONDIOXIDE, 500))
		);

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
		recipes.add(new ChemRecipe(1028, "CALCIUM_CHLORIDE", 200)
			.inputItems(
				new ComparableStack(ModItems.quicklime, 1)
			)
			.inputFluids(
				new FluidStack(Fluids.HCL, 200) // 2x ratio
			)
			.outputFluids(
				new FluidStack(Fluids.CACL2, 1000),
				new FluidStack(Fluids.WATER, 200)
			)
		);

		recipes.add(new ChemRecipe(1029, "MOLTEN_STRONTIUM_CHLORIDE", 50)
			.inputItems(new ComparableStack(ModItems.strontium_chloride, 1))
			.outputFluids(new FluidStack(Fluids.MOLTEN_STRONTIUM_CHLORIDE, 1000))
		);

		recipes.add(new ChemRecipe(1030, "STRONTIUM_CHLORIDE", 50)
			.inputItems(new ComparableStack(ModItems.powder_strontium_oxide, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 1000))
			.outputItems(new ItemStack(ModItems.strontium_chloride, 1))
			.outputFluids(new FluidStack(Fluids.WATER, 500))
		);

		// barium carbonate
		recipes.add(new ChemRecipe(1031, "BARIUM_CARBONATE", 50)
			.inputItems(new ComparableStack(ModItems.barium_sulfide, 1))
			.inputFluids(
				new FluidStack(Fluids.CARBONDIOXIDE, 1000),
				new FluidStack(Fluids.WATER, 500)
			)
			.outputItems(new ItemStack(ModItems.barium_carbonate, 1)) //, new ItemStack(ModItems.sulfur)
			//I literally cannot be bothered to add Hydrogen sulfide. cry about it...
			//ok maybe I will just give me the damn template already.
			.outputFluids(new FluidStack(Fluids.HYDROGEN_SULFIDE, 1000))
		);

		//hydrogen sulfide synthesis
		recipes.add(new ChemRecipe(1032, "HYDROGEN_SULFIDE", 50)
			.inputItems(new ComparableStack(ModItems.sulfur, 1))
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 1000))
			.outputFluids(new FluidStack(Fluids.HYDROGEN_SULFIDE, 1000))
		);

		//barium oxide
		//recipes.add(new ChemRecipe(1032, "BARIUM_OXIDE", 50)
		//	.inputItems(new ComparableStack(ModItems.barium_carbonate, 1)) //BaCO3 + heat -> BaO + CO2
		//	.outputItems(new ItemStack(ModItems.barium_oxide, 1))
		//	.outputFluids(new FluidStack(Fluids.CARBONDIOXIDE, 1000))
		//);
		//regular furnace/etc.

		// barium production (hydrogen reduction)
		recipes.add(new ChemRecipe(1033, "BARIUM_REDUCTION", 50)
			.inputItems(new ComparableStack(ModItems.barium_oxide, 1))
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 1000))
			.outputItems(new ItemStack(ModItems.ingot_barium, 1))
				//reasoning: I cannot be bothered to change how radium is currently obtained (centrifuging uranium ore)
				//however, I also want to add a use for barium
				//and since barium is used as a binding agent to produce radium this is at least somewhat based on real chemistry.
				//new ItemStack(ModItems.nugget_ra226))
			//moved to own process
			.outputFluids(new FluidStack(Fluids.WATER, 500))
		);
		//please kill me

		// radium extraction (barium-assisted, simple and 'sane')
		recipes.add(new ChemRecipe(1034, "RADIUM_EXTRACTION", 80)
			.inputItems(
				new ComparableStack(ModItems.powder_uranium, 1),
				new ComparableStack(ModItems.ingot_barium, 1)
			)
			.inputFluids(
				new FluidStack(Fluids.SULFURIC_ACID, 1000) // sulfuric, nitric, whatever you already use
			)
			.outputItems(
				new ItemStack(ModItems.nugget_ra226, 2),   // the actual goal
				new ItemStack(ModItems.nugget_uranium, 8), // most uranium remains
				new ItemStack(ModItems.nuclear_waste, 1),          // junk
				new ItemStack(ModItems.barium_nugget, 8)            // carrier
			)
			.outputFluids(
				new FluidStack(Fluids.WASTEFLUID, 500)
			)
		);

		//sodium sulfate into sodium first step Reduce sodium sulfate with carbon:
		recipes.add(new ChemRecipe(1035, "SODIUM_SULFATE_REDUCTION", 100)
			.inputItems(
				new ComparableStack(ModItems.sodium_sulfate, 1),
				new ComparableStack(ModItems.powder_coal, 2)
			)
			.inputFluids(new FluidStack(Fluids.ULTRAHOTSTEAM, 2000))
			.outputItems(new ItemStack(ModItems.sodium_sulfide, 1))
			.outputFluids(new FluidStack(Fluids.CARBONDIOXIDE, 2000))
		);

		//sodium sulfide into hydrogen sulfide
		recipes.add(new ChemRecipe(1036, "SODIUM_SULFIDE_ACIDIFICATION", 100)
			.inputItems(new ComparableStack(ModItems.sodium_sulfide, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 1000))
			.outputItems(new ItemStack(ModItems.itemsalt, 2)) // NaCl
			.outputFluids(new FluidStack(Fluids.HYDROGEN_SULFIDE, 1000))
		);

		recipes.add(new ChemRecipe(1037, "HYDROGEN_SULFIDE_OXIDATION", 100)
			.inputFluids(
				new FluidStack(Fluids.HYDROGEN_SULFIDE, 2000),
				new FluidStack(Fluids.OXYGEN, 1000)
			)
			.outputItems(new ItemStack(ModItems.sulfur, 2))
			.outputFluids(new FluidStack(Fluids.STEAM, 2000))
		);

		recipes.add(new ChemRecipe(1038, "RARE_EARTH_ELEMENTS", 100)
			.inputFluids(
				new FluidStack(Fluids.RAFFINATE, 1000),
				new FluidStack(Fluids.AMMONIA, 500)
			)


			.outputItems(new ItemStack(ModItems.REE_sludge))
		);

		// Scandium extraction
		recipes.add(new ChemRecipe(1039, "SCANDIUM_EXTRACTION", 100)
			.inputItems(new ComparableStack(ModItems.REE_sludge, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(new ItemStack(ModItems.scandium_oxide, 1))
			.outputFluids(new FluidStack(Fluids.ACIDWASTE, 500))
		);

		// Yttrium extraction
		recipes.add(new ChemRecipe(1040, "YTTRIUM_EXTRACTION", 100)
			.inputItems(new ComparableStack(ModItems.REE_sludge, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(new ItemStack(ModItems.yttrium_oxide, 1))
			.outputFluids(new FluidStack(Fluids.ACIDWASTE, 500))
		);

		//waste_water + lime → water + sludge
		recipes.add(new ChemRecipe(1041, "WASTE_WATER_TREATMENT", 100)
			.inputFluids(new FluidStack(Fluids.ACIDWASTE, 1000), new FluidStack(Fluids.WATER, 1000))
			.inputItems(new ComparableStack(ModItems.quicklime, 1))
			.outputFluids(new FluidStack(Fluids.WATER, 800), new FluidStack(Fluids.MINSOL, 250))
			//.outputItems(new ItemStack(ModItems.slagingot, 1))
		);

		//waste_water + sodium_hydroxide → water + salt_sludge
		recipes.add(new ChemRecipe(1042, "WASTE_WATER_TREATMENT_2", 100)
			.inputFluids(new FluidStack(Fluids.ACIDWASTE, 1000))
			.inputFluids(new FluidStack(Fluids.SODIUM_HYDROXIDE, 500))
			//mineral slurry and water
			.outputFluids(new FluidStack(Fluids.WATER, 800), new FluidStack(Fluids.MINSOL, 250))

		);

		recipes.add(new ChemRecipe(1043, "SCANDIUM_CHLORIDE", 100)
			.inputItems(new ComparableStack(ModItems.scandium_oxide, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(new ItemStack(ModItems.scandium_chloride, 1))
			.outputFluids(new FluidStack(Fluids.WATER, 250))
		);

		recipes.add(new ChemRecipe(1044, "SCANDIUM_REDUCTION", 150)
			.inputItems(
				new ComparableStack(ModItems.scandium_chloride, 1),
				new ComparableStack(ModItems.ingot_calcium, 1)
			)
			.outputItems(new ItemStack(ModItems.scandium_nugget, 1))
			//TODOne nugget
			.outputFluids(new FluidStack(Fluids.CALCIUM_CHLORIDE, 800))
		);

		//scandium oxide from rare metals residue (titanium)
		recipes.add(new ChemRecipe(1045, "SCANDIUM_FROM_TITANIUM", 150)
			.inputItems(new ComparableStack(ModItems.titanium_trace_metals_slurry, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(
				new ItemStack(ModItems.scandium_oxide, 1)
			)
			.outputFluids(new FluidStack(Fluids.ACIDWASTE, 250))
		);

		// Yttrium from titanium
		recipes.add(new ChemRecipe(1046, "YTTRIUM_FROM_TITANIUM", 150)
			.inputItems(new ComparableStack(ModItems.titanium_trace_metals_slurry, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(new ItemStack(ModItems.yttrium_oxide, 1))
			.outputFluids(new FluidStack(Fluids.ACIDWASTE, 250))
		);

		recipes.add(new ChemRecipe(1047, "YTTRIUM_CHLORIDE", 100)
			.inputItems(new ComparableStack(ModItems.yttrium_oxide, 1))
			.inputFluids(new FluidStack(Fluids.HCL, 500))
			.outputItems(new ItemStack(ModItems.yttrium_chloride, 1))
			.outputFluids(new FluidStack(Fluids.WATER, 250))
		);

		recipes.add(new ChemRecipe(1048, "YTTRIUM_REDUCTION", 150)
			.inputItems(
				new ComparableStack(ModItems.yttrium_chloride, 1),
				new ComparableStack(ModItems.ingot_calcium, 1)
			)
			.outputItems(new ItemStack(ModItems.powder_yttrium_tiny, 1)) //suffer
			.outputFluids(new FluidStack(Fluids.CALCIUM_CHLORIDE, 800))
		);

		//lepidolite
		recipes.add(new ChemRecipe(1049, "LEPIDOLITE", 100)
			.inputItems(new ComparableStack(ModItems.lepidolite))
			.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 2000))
			.outputItems(new ItemStack(ModItems.lithium, 1), new ItemStack(ModItems.potassium_sulfate, 1))
			.outputFluids(new FluidStack(Fluids.ACIDWASTE, 2000))
		);
		//I can't be bothered to separate this into another process, maybe later

		//carnallite + water -> potassium chloride + magnesium chloride + water
		recipes.add(new ChemRecipe(1050, "CARNALLITE", 100)
			.inputItems(new ComparableStack(ModItems.carnallite))
			.inputFluids(new FluidStack(Fluids.WATER, 2000))
			.outputItems(
				new ItemStack(ModItems.itemsalt, 1),
				new ItemStack(ModItems.magnesium_chloride, 1)
			)
			.outputFluids(new FluidStack(Fluids.WATER, 1500))
		);

		//powder_fertilizer from potassium chloride
		recipes.add(new ChemRecipe(1051, "FERTILIZER", 50)
			.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000))
			.outputItems(new ItemStack(ModItems.powder_fertilizer, 4))
		);

		//potassium sulfate from potassium chloride and sulfuric acid (best fertilizer)
		//recipes.add(new ChemRecipe(1052, "POTASSIUM_SULFATE", 50)
		//	.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000), new FluidStack(Fluids.SULFURIC_ACID, 1000))
		//	.outputItems(new ItemStack(ModItems.potassium_sulfate, 1))
		//	.outputFluids(new FluidStack(Fluids.HCL, 1000))
		//);

		//fertilizer from potassium sulfate (best process)
		recipes.add(new ChemRecipe(1052, "FERTILIZER_2", 50)
			.inputItems(new ComparableStack(ModItems.potassium_sulfate, 1))
			.outputItems(new ItemStack(ModItems.powder_fertilizer, 8))
		);


		//I FUCKING HATE HOW UNMODULAR THIS INDEX SYSTEM IS !!! MORE RETARDED HUMAN SLOP DESIGN!!!
		recipes.add(new ChemRecipe(1053, "GASOLINE2", 45)
						.inputFluids(new FluidStack(Fluids.NAPHTHA, 1200))
						.inputItems(new ComparableStack(ModItems.powder_rhenium)) //rhenium is used to make lead free gasoline
						.outputFluids(new FluidStack(Fluids.GASOLINE, 1100)));

		// Step 2: Oxidation (Chemplant)
		recipes.add(new ChemRecipe(1054, "OSMIRIDIUM_OXIDATION", 300)
						.inputItems(new ComparableStack(ModItems.powder_impure_osmiridium, 1))
						.inputFluids(new FluidStack(Fluids.OXYGEN, 1000))
						.outputItems(new ItemStack(ModItems.iridium_rich_residue, 1))
						.outputFluids(new FluidStack(Fluids.OSMIRIDIUM_SOLUTION, 200)) //osmium tetroxide, I am not refactoring it again; it's called it in LANG.
		);

		recipes.add(new ChemRecipe(1055, "OSMIUM_REDUCTION", 200)
						.inputFluids(new FluidStack(Fluids.OSMIRIDIUM_SOLUTION, 1000), new FluidStack(Fluids.HYDROGEN, 1000))
						.outputItems(new ItemStack(ModItems.powder_osmium_tiny, 1))
						.outputFluids(new FluidStack(Fluids.WATER, 1000))
		);

		//rhodium extraction using chlorine
		recipes.add(new ChemRecipe(1056, "RHODIUM_EXTRACTION", 300)
						.inputItems(new ComparableStack(ModItems.powder_rhodium_solution, 1))
						.inputFluids(new FluidStack(Fluids.CHLORINE, 1000))
						.outputItems(new ItemStack(ModItems.iridium_rich_residue, 1)) // leftovers after Rh extraction
						.outputFluids(new FluidStack(Fluids.RHODIUM_SOLUTION, 200))
		);

		recipes.add(new ChemRecipe(1057, "IRIDIUM_OXIDATION", 400)
						.inputItems(new ComparableStack(ModItems.iridium_rich_residue, 1))
						.inputFluids(new FluidStack(Fluids.OXYGEN, 1000))
						.outputItems(new ItemStack(ModItems.iridium_oxide, 1))
		);

		recipes.add(new ChemRecipe(1061, "IRIDIUM_DISSOLUTION", 300)
						.inputItems(new ComparableStack(ModItems.iridium_oxide, 1))
						.inputFluids(new FluidStack(Fluids.CHLORINE, 1000))
						.outputFluids(new FluidStack(Fluids.IRIDIUM_SOLUTION, 200))
						.outputItems(new ItemStack(ModItems.ruthenium_residue, 1))
		);

		//Fluorite → hydrofluoric acid
		recipes.add(new ChemRecipe(1062, "HYDROFLUORIC_ACID", 100)
						.inputItems(new ComparableStack(ModItems.crystal_fluorite, 1))
						.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1000))
						.outputFluids(new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000))
						.outputItems(new ItemStack(ModItems.gypsum, 4)) //should be calcium sulfate/gypsum? idk I'm just autofilling for now.
		);

		//salt + water + power → chlorine + hydrogen + lye
		recipes.add(new ChemRecipe(1063, "CHLORINE_PRODUCTION", 130)
						.inputItems(new ComparableStack(ModItems.itemsalt, 1))
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000),
							new FluidStack(Fluids.OXYGEN, 500)
						)
						//.oxygenConsumption = 500 // consumes oxygen from the atmosphere to prevent infinite loops with water electrolysis
						//that's great gpt but I'm not doing allat I'm just adding oxygen fluid in input
						.outputFluids(
							new FluidStack(Fluids.CHLORINE, 1000),
							new FluidStack(Fluids.HYDROGEN, 1000)
							//new FluidStack(Fluids.SODIUM_HYDROXIDE, 1000)
						)
						//.outputItems(new ItemStack(ModItems.sodium_hydroxide, 1))
		);

		//chlorocalcite from calcium chloride and potassium chloride
		recipes.add(new ChemRecipe(1064, "CHLOROCALCITE", 100)
						.inputFluids(
							new FluidStack(Fluids.CALCIUM_CHLORIDE, 1200),
							new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000)
						)
						.outputItems(new ItemStack(ModItems.powder_chlorocalcite, 1))
		);

		//RUTHENIUM_RESIDUE + HYDROGEN -> RUTHENIUM + STEAM
		recipes.add(new ChemRecipe(1065, "RUTHENIUM_REDUCTION", 300)
						.inputItems(new ComparableStack(ModItems.ruthenium_residue, 1))
						.inputFluids(new FluidStack(Fluids.HYDROGEN, 1000))
						.outputItems(new ItemStack(ModItems.nugget_ruthenium, 1))
						.outputFluids(new FluidStack(Fluids.STEAM, 850))
		);

		//bromine from bittern + chlorine
		recipes.add(new ChemRecipe(1066, "BROMINE_EXTRACTION", 200)
						.inputFluids(
							new FluidStack(Fluids.BITTERN, 2000),
							new FluidStack(Fluids.CHLORINE, 1000)
						)
						.outputItems(new ItemStack(ModItems.powder_bromine, 1))
						.outputFluids(
							//new FluidStack(Fluids.BROMINE, 1000),
							new FluidStack(Fluids.BRINE, 400)
						)
		);

		//iodine brine + chlorine -> iodine + salt brine
		recipes.add(new ChemRecipe(1067, "IODINE_EXTRACTION", 200)
						.inputFluids(
							new FluidStack(Fluids.IODINE_BRINE, 2000),
							new FluidStack(Fluids.CHLORINE, 1000)
						)
						.outputItems(new ItemStack(ModBlocks.ore_iodine, 1))
						.outputFluids(
							new FluidStack(Fluids.BRINE, 400)
							//Brine is a high-concentration solution of salt (usually sodium chloride) dissolved in water
							//so why the fuck would we specify that it is salty, obviously it's fucking salty,
							// it's water with high salt
						)
		);

		//potassium iodide solution + chlorine -> iodine + potassium chloride
		recipes.add(new ChemRecipe(1068, "IODINE_EXTRACTION2", 180)
						.inputItems(
							new ComparableStack(ModItems.potassium_iodide_powder, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.CHLORINE, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_iodine, 4)
						)
						.outputFluids(
							new FluidStack(Fluids.POTASSIUM_CHLORIDE, 800)
						)
		);

		//Potassium chloride + ammonium nitrate = saltpeter/niter
		recipes.add(new ChemRecipe(1069, "SALTPETER", 200)
						.inputItems(
							new ComparableStack(
								ModItems.ammonium_nitrate
							)
						)
						.inputFluids(
							new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.niter, 1),
							new ItemStack(ModItems.ammonium_chloride, 1)
						)

		);

		//ammonium chloride + heat -> ammonia + hydrogen chloride
		recipes.add(new ChemRecipe(1070, "AMMONIUM_CHLORIDE_DECOMPOSITION", 150)
						.inputItems(
							new ComparableStack(ModItems.ammonium_chloride, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.ULTRAHOTSTEAM, 1000)
						)
						.outputFluids(
							new FluidStack(Fluids.AMMONIA, 500),
							new FluidStack(Fluids.HCL, 500)
						)
		);

		//Potassium chloride + sodium nitrate = saltpeter/niter
		recipes.add(new ChemRecipe(1071, "SALTPETER2", 200)
						.inputItems(
							new ComparableStack(ModItems.sodium_nitrate)
						)
						.inputFluids(
							new FluidStack(Fluids.POTASSIUM_CHLORIDE, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.niter, 1),
							new ItemStack(ModItems.itemsalt, 1)
						)
		);

		//helium capsule
		recipes.add(new ChemRecipe(1072, "HELIUM_CAPSULE", 50)
						.inputItems(
							new ComparableStack(ModItems.particle_empty, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.HELIUM4, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.particle_helium, 1)
						)
		);

		//neon used to make vaccum tubes
		recipes.add(new ChemRecipe(1073, "NEON_CAPSULE", 50)
						.inputItems(
							new OreDictStack(KEY_ANYPANE),
							new ComparableStack(ModItems.plate_polymer),
							new OreDictStack(CARBON.wireFine())
						)
						.inputFluids(
							new FluidStack(Fluids.NEON, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.circuit, 8, ItemCircuit.EnumCircuitType.VACUUM_TUBE.ordinal())
						)
		);

		//fragment_samarium
		//    ↓ (Chemical Plant + acid)
		recipes.add(new ChemRecipe(1074, "SAMARIUM_FRAGMENT_REFINEMENT", 200)
						.inputItems(
							new ComparableStack(ModItems.fragment_samarium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.HCL, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_samarium, 1)
						)
		);

		//fullerene from graphite electrodes + helium
		recipes.add(new ChemRecipe(1075, "FULLERENE_SYNTHESIS", 600)
						.inputItems(
							new ComparableStack(ModItems.arc_electrode, 1, ItemArcElectrode.EnumElectrodeType.GRAPHITE)
						)
						.inputFluids(
							new FluidStack(Fluids.HELIUM4, 2000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_ash, 1 ,
										  ItemEnums.EnumAshType.SOOT.ordinal() ), //holy shit I hate enums
							new ItemStack(ModItems.arc_electrode_burnt, 1, ItemArcElectrode.EnumElectrodeType.GRAPHITE.ordinal())
						)
						.outputFluids(
							new FluidStack(Fluids.FULLERENE, 1000)
						)
		);

		//sulfuric acid + europium_dust_tiny = europium solution
		recipes.add(new ChemRecipe(1076, "EUROPIUM_SOL", 400)
						.inputItems(
							new ComparableStack(
								ModItems.europium_dust_tiny, 9
							)
						)
						.inputFluids(
							new FluidStack(Fluids.SULFURIC_ACID, 1000)
						)
						.outputItems(
							new ItemStack(
								ModItems.europiumsol
							)
						)
		);

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
		recipes.add(new ChemRecipe(1077, "GADOLINIUM_LIQUID", 400)
						.inputItems(
							new ComparableStack(
								ModItems.powder_gadolinium, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.ULTRAHOTSTEAM, 1000)
						)
						.outputFluids(
							new FluidStack(Fluids.GADOLINIUM, 1000)
						)
		);

		//liquid gadolinium + solvent = gadolinium powder 2
		recipes.add(new ChemRecipe(1078, "GADOLINIUM_PRECIPITATION", 400)
						.inputFluids(
							new FluidStack(Fluids.GADOLINIUM, 1000),
							new FluidStack(Fluids.SOLVENT, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_gadolinium2, 1)
						)
		);

		//terbiumsol + high perf solvent = terbium step toward ingot
		//Separation: The mixture is leached using acids, (we did this in the crystallizer (leaching reactor))
		// and specialized solvent extraction or ion-exchange methods are utilized to separate terbium from other (here!)
		// rare-earth elements.
		recipes.add(new ChemRecipe(1079, "TERBIUM_SEPARATION", 400)
						.inputItems(
							new ComparableStack(
								ModItems.terbiumsol, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.RADIOSOLVENT, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_terbium2, 1),
							new ItemStack(ModItems.powder_yttrium_tiny, 1),
							new ItemStack(ModItems.nugget_th232, 1),
							new ItemStack(ModItems.nugget_uranium, 1)
						)
		);

		//terbium oxidation
		recipes.add(new ChemRecipe(1080, "TERBIUM_OXIDATION", 400)
						.inputItems(
							new ComparableStack(
								ModItems.powder_terbium2, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.OXYGEN, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_terbium_oxide, 1)
						)
		);

		//Metallothermic Reduction: To obtain metallic terbium, the oxide is converted into terbium fluoride ( //we are here
		//) or anhydrous chloride, which is then reduced using calcium or lithium metal in a high-temperature vacuum or inert atmosphere.
		recipes.add(new ChemRecipe(1081, "TERBIUM_FLUORIDE", 400)
						.inputItems(
							new ComparableStack(
								ModItems.powder_terbium_oxide, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_terbium_fluoride, 1)
						)
		);

						//.inputFluids(
						//	new FluidStack(Fluids.CALCIUM_SOLUTION, 1000)
						//)
						//.outputItems(
						//	new ItemStack(ModItems.terbium_ingot, 1)
						//) //no those come later

		//dysprosiumsol + solvent = dysprosium powder 2
		recipes.add(new ChemRecipe(1082, "DYSPROSIUM_SEPARATION", 400)
						.inputItems(
							new ComparableStack(
								ModItems.dysprosiumsol, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.RADIOSOLVENT, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_dysprosium2, 1),
							new ItemStack(ModItems.powder_yttrium_tiny, 1),
							new ItemStack(ModItems.powder_neodymium_tiny, 1),
							new ItemStack(ModItems.powder_gadolinium2, 1)
						)
		);

		//powder_ytterbium_oxide + hydrofluoric acid = powder_ytterbium_fluoride
		recipes.add(new ChemRecipe(1083, "YTTERBIUM_FLUORIDE", 400)
						.inputItems(
							new ComparableStack(
								ModItems.powder_ytterbium_oxide, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_ytterbium_fluoride, 1)
						)
		);

		//lutetiumsol + solvent = lutetium powder 2
		recipes.add(new ChemRecipe(1084, "LUTETIUM_SEPARATION", 400)
						.inputItems(
							new ComparableStack(
								ModItems.lutetiumsol, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.RADIOSOLVENT, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_lutetium2, 1),
							new ItemStack(ModItems.powder_ytterbium_tiny, 1),
							new ItemStack(ModItems.powder_erbium_tiny, 1)
						)
		);

		//thuliumsol + solvent = thuliumsol2
		recipes.add(new ChemRecipe(1085, "THULIUM_SEPARATION", 400)
						.inputItems(
							new ComparableStack(
								ModItems.thuliumsol, 1
							)
						)
						.inputFluids(
							new FluidStack(Fluids.RADIOSOLVENT, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.powder_thulium2, 1),
							new ItemStack(ModItems.powder_erbium_tiny, 1),
							new ItemStack(ModItems.powder_ytterbium_tiny, 1)
						)
		);

		//powder_vanadium + erbium powder tiny = 3 ingot vanadium since erbium can be used to make vanadium
		recipes.add(new ChemRecipe(1086, "VANADIUM_INGOT", 400)
						.inputItems(
							new ComparableStack(
								ModItems.powder_vanadium, 1
							),
							new ComparableStack(
								ModItems.powder_erbium_tiny, 1
							)
						)
						.outputItems(
							new ItemStack(ModItems.ingot_vanadium, 3)
						)
		);

		recipes.add(new ChemRecipe(1087, "ER_YAG_PRECURSOR", 300)
						.inputItems(
							new ComparableStack(ModItems.powder_yttrium, 3),
							new ComparableStack(ModItems.powder_aluminium, 5),
							new ComparableStack(ModItems.erbium_powder, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.OXYGEN, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.er_yag_feedstock)
						)
		);

		recipes.add(new ChemRecipe(1088, "YB_YAG_PRECURSOR", 300)
						.inputItems(
							new ComparableStack(ModItems.powder_yttrium, 3),
							new ComparableStack(ModItems.powder_aluminium, 5),
							new ComparableStack(ModItems.powder_ytterbium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.OXYGEN, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.yby_feedstock)
						)
		);

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
		//					new FluidStack(Fluids.NITROGEN, 500),
		//					//new FluidStack(Fluids.HELIUM4, 500) //helium 3...?
		//					//EXCEPT THAT THE CHEMPLANT CANNOT TAKE MORE THAN 2 FLUIDS AT A TIME, SO I HAVE TO ADD EVEN MORE FUCKING RECIPES!!!
		//				)
		//				.outputFluids(
		//					new FluidStack(Fluids.CARBONDIOXIDE_NITROGEN_HELIUM, 400)
		//				)
		//);

		recipes.add(new ChemRecipe(1090, "IR_LASER_GASMIX_1", 200)
						.inputFluids(
							new FluidStack(Fluids.NITROGEN, 500),
							new FluidStack(Fluids.HELIUM4, 500)
						)
						.outputFluids(
							new FluidStack(Fluids.NITROGEN_HELIUM, 1000)
						)
		);

		recipes.add(new ChemRecipe(1091, "IR_LASER_GASMIX_2", 400)
						.inputFluids(
							new FluidStack(Fluids.CARBONDIOXIDE, 1000),
							new FluidStack(Fluids.NITROGEN_HELIUM, 1000)
						)
						.outputFluids(
							new FluidStack(Fluids.CARBONDIOXIDE_NITROGEN_HELIUM, 400)
						)
		);

		recipes.add(new ChemRecipe(1092, "KTP_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.potassium_powder, 1),
							new ComparableStack(ModItems.ingot_titanium, 1),
							new ComparableStack(ModItems.ingot_phosphorus, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.KTP_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1093, "BBO_CRYSTAL", 400)
						.inputItems(
							new OreDictStack(BA.ingot(), 1),
							new OreDictStack(B.ingot(), 2)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.BBO_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1094, "TITANIUM_SAPPHIRE_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.gem_sapphire, 1),
							new ComparableStack(ModItems.ingot_titanium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.titanium_sapphire_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1095, "LITHIUM_NIOBATE_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.lithium, 1),
							new ComparableStack(ModItems.ingot_niobium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.lithium_niobate_crystal, 1)
						)
		);

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

		recipes.add(new ChemRecipe(1097, "VISIBLE_DOPANT_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.ingot_praseodymium, 1),
							new ComparableStack(ModItems.ingot_dysprosium, 1),
							new ComparableStack(ModItems.ingot_europium, 1),
							new ComparableStack(ModItems.ingot_terbium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.visible_dopant_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1098, "CERIUM_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.powder_cerium, 1),
							new ComparableStack(ModBlocks.glass_quartz, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.cerium_crystal, 1)
						)
		);

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

		recipes.add(new ChemRecipe(1099, "EXCIMER_GASMIX1", 400)
						.inputFluids(
							new FluidStack(Fluids.KRYPTON, 500),
							new FluidStack(Fluids.FLUORINE, 500)
						)
						.outputFluids(
							new FluidStack(Fluids.KRYPTON_FLUORINE, 200)
						)
		);

		recipes.add(new ChemRecipe(1100, "EXCIMER_GASMIX2", 400)
						.inputFluids(
							new FluidStack(Fluids.XENON, 500),
							new FluidStack(Fluids.CHLORINE, 500)
						)
						.outputFluids(
							new FluidStack(Fluids.XENON_CHLORINE, 200)
						)
		);
		recipes.add(new ChemRecipe(1101, "EXCIMER_GASMIX3", 400)
						.inputFluids(
							new FluidStack(Fluids.XENON_CHLORINE, 500),
							new FluidStack(Fluids.KRYPTON_FLUORINE, 500)
						)
						.outputFluids(
							new FluidStack(Fluids.EXCIMER_GASMIX, 1000)
						)
		);

		recipes.add(new ChemRecipe(1102, "CALCIUM_FLUORIDE_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.powder_calcium, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.HYDROFLUORIC_ACID, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.calcium_fluoride_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1103, "KDP_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.potassium_powder, 1),
							new ComparableStack(ModItems.ingot_phosphorus, 1)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.KDP_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1104, "CLBO_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.powder_caesium, 1),
							new ComparableStack(ModItems.lithium, 1),
							new ComparableStack(ModItems.powder_boron, 2)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.CLBO_crystal, 1)
						)
		);

		recipes.add(new ChemRecipe(1105, "LBO_CRYSTAL", 400)
						.inputItems(
							new ComparableStack(ModItems.lithium, 1),
							new ComparableStack(ModItems.powder_boron, 3)
						)
						.inputFluids(
							new FluidStack(Fluids.WATER, 1000)
						)
						.outputItems(
							new ItemStack(ModItems.LBO_crystal, 1)
						)
		);





								   //todo methamphetamine, methylamine, ephedrine, pseudoephedrine, etc. for fun chemistry and maybe a drug lab or something who knows
		//thanks for the very legal autofill ai anyway to the chemical reactor *bat man noise


	}

	public static void registerOtherOil() {
		recipes.add(new ChemRecipe(31, "BP_BIOGAS", 60)
				.inputItems(new ComparableStack(ModItems.biomass, 16)) //if we assume 1B BF = 500k and translate that to 2B BG = 500k, then each biomass is worth ~31k or roughly 1.5 furnace operations
				.outputFluids(new FluidStack(2000, Fluids.BIOGAS)));
		recipes.add(new ChemRecipe(32, "BP_BIOFUEL", 60)
				.inputFluids(new FluidStack(1500, Fluids.BIOGAS), new FluidStack(250, Fluids.ETHANOL))
				.outputFluids(new FluidStack(1000, Fluids.BIOFUEL)));
		recipes.add(new ChemRecipe(33, "LPG", 100)
				.inputFluids(new FluidStack(2000, Fluids.PETROLEUM))
				.outputFluids(new FluidStack(1000, Fluids.LPG)));
		recipes.add(new ChemRecipe(34, "OIL_SAND", 200)
				.inputItems(new ComparableStack(ModBlocks.ore_oil_sand, 16))
					//, new OreDictStack(ANY_TAR.any(), 1))
				.outputItems(new ItemStack(Blocks.sand, 16))
				.outputFluids(new FluidStack(1000, Fluids.BITUMEN)));
		recipes.add(new ChemRecipe(35, "ASPHALT", 100)
				.inputItems(new ComparableStack(Blocks.gravel, 2), new OreDictStack(KEY_SAND, 6))
				.inputFluids(new FluidStack(1000, Fluids.BITUMEN))
				.outputItems(new ItemStack(ModBlocks.asphalt, 16)));
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
		public int oxygenConsumption = 0; // How much oxygen the recipe consumes from the atmosphere per tick while processing

		public ChemRecipe(int index, String name, int duration) {
			this.id = index;
			this.name = name;
			this.duration = duration;
			this.listing = recipes.size();

			this.inputs = new AStack[4];
			this.outputs = new ItemStack[4];
			this.inputFluids = new FluidStack[2];
			this.outputFluids = new FluidStack[2];

			if(!indexMapping.containsKey(id)) {
				indexMapping.put(id, this);
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

		public int getId() {
			return this.id;
		}

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

		recipes.add(new ChemRecipe(id, name, duration)
				.inputFluids(	readFluidArray(		(JsonArray) obj.get("fluidInput")))
				.inputItems(	readAStackArray(		(JsonArray) obj.get("itemInput")))
				.outputFluids(	readFluidArray(		(JsonArray) obj.get("fluidOutput")))
				.outputItems(	readItemStackArray(	(JsonArray) obj.get("itemOutput"))));
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		try {
		ChemRecipe chem = (ChemRecipe) recipe;
		writer.name("id").value(chem.id);
		writer.name("name").value(chem.name);
		writer.name("duration").value(chem.duration);
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
	}
}
