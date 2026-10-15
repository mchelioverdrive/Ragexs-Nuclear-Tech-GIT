package com.hbm.inventory.recipes;

import static com.hbm.inventory.fluid.Fluids.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ItemEnums.EnumTarType;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.ModItems;
import com.hbm.util.Tuple.Pair;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class SolidificationRecipes extends SerializableRecipe {
    private static com.hbm.realsim.ProcessRecipe bitumenBill;
    private static final HashMap<FluidType, com.hbm.realsim.ProcessRecipe> overrides = new HashMap<>();
    private static final com.hbm.realsim.ProcessRecipe waterBill = com.hbm.realsim.ChemistryProcesses.freezing(false), co2Bill = com.hbm.realsim.ChemistryProcesses.freezing(true),
            sodiumBill = com.hbm.realsim.ChemistryProcesses.metalCooling(false), potassiumBill = com.hbm.realsim.ChemistryProcesses.metalCooling(true);
    public static com.hbm.realsim.ProcessRecipe getPhysical(FluidType type) {
        if(!recipes.containsKey(type)) return null;
        if(overrides.containsKey(type)) return overrides.get(type);
        return type == BITUMEN ? bitumenBill : type == WATER ? waterBill : type == CARBONDIOXIDE ? co2Bill : type == SODIUM ? sodiumBill : type == POTASSIUM ? potassiumBill : null;
    }

	public static final int SF_OIL =		200;
	public static final int SF_CRACK =		200;
	public static final int SF_HEAVY =		150;
	public static final int SF_BITUMEN =	100;
	public static final int SF_SMEAR =		100;
	public static final int SF_HEATING =	50;
	public static final int SF_RECLAIMED =	100;
	public static final int SF_PETROIL =	125;
	public static final int SF_LUBE =		100;
	public static final int SF_NAPH =		150;
	public static final int SF_DIESEL =		200;
	public static final int SF_LIGHT =		225;
	public static final int SF_KEROSENE =	275;
	public static final int SF_GAS =		375;
	public static final int SF_PETROLEUM =	300;
	public static final int SF_LPG =		150;
	public static final int SF_BIOGAS =		1750;
	public static final int SF_BIOFUEL =	750;
	public static final int SF_COALOIL =	200;
	public static final int SF_CREOSOTE =	200;
	public static final int SF_WOOD =		1000;
	//mostly for alternate chemistry, dump into SF if not desired
	public static final int SF_AROMA =		1000;
	public static final int SF_UNSAT =		1000;

	public static final int SF_POTASSIUM = 100;
	//in the event that these compounds are STILL too useless, add unsat + gas -> kerosene recipe for all those missile junkies
	//aromatics can be idfk wax or soap or sth, perhaps artificial lubricant?
	//on that note, add more leaded variants

	private static HashMap<FluidType, Pair<Integer, ItemStack>> recipes = new HashMap();

	@Override
	public void registerDefaults() {
        bitumenBill = com.hbm.realsim.ChemicalFuelProcesses.bitumenFreezing();
        registerRecipe(BITUMEN, 2, DictFrame.fromOne(ModItems.oil_tar, EnumTarType.CRUDE));

		registerRecipe(WATER,		1,			Blocks.ice);
		registerRecipe(LAVA,		1000,			Blocks.obsidian);
		registerRecipe(MERCURY,		125,			ModItems.ingot_mercury);
		// Digestion is not reversible by cooling: fuel gas cannot solidify into wet biomass.
		registerRecipe(SALIENT,		1280,			new ItemStack(ModItems.bio_wafer, 8)); //4 (food val) * 2 (sat mod) * 2 (constant) * 10 (quanta) * 8 (batch size)
		registerRecipe(BLOOD,		1290,			new ItemStack(ModItems.flesh_wafer, 5));
		//registerRecipe(ENDERJUICE,	100,			Items.ender_pearl);
		//registerRecipe(WATZ,		1000,			ModItems.ingot_mud);
        // Red mud requires chemical recovery/reduction; cooling cannot create iron.
		registerRecipe(SODIUM,		100,			ModItems.powder_sodium);
		registerRecipe(LEAD, 100, ModItems.ingot_lead);
        // Dirty carrier water cannot become bitumen-bearing ore.
		registerRecipe(CARBONDIOXIDE, 1000,		ModBlocks.dry_ice);


		registerRecipe(POTASSIUM, 100,	ModItems.potassium_powder);
		//idk anymore im losing my mind

		// Petroleum residues are converted by paid coker bills, not legacy burn-time solidification.

		registerRecipe(BALEFIRE,		250,			ModItems.solid_fuel_bf);


		//todo: potassium powder


	}

	private static void registerSFAuto(FluidType fluid) {
		registerSFAuto(fluid, 1_440_000L, ModItems.solid_fuel); //3200 burntime * 1.5 burntime bonus * 300 TU/t
	}
	private static void registerSFAuto(FluidType fluid, long tuPerSF, Item fuel) {
		long tuPerBucket = fluid.getTrait(FT_Flammable.class).getLegacyBurnerBudget();
		double penalty = 1.25D;

		int mB = (int) (tuPerSF * 1000L * penalty / tuPerBucket);

		if(mB > 10_000) mB -= (mB % 1000);
		else if(mB > 1_000) mB -= (mB % 100);
		else if(mB > 100) mB -= (mB % 10);

		mB = Math.max(mB, 1);

		registerRecipe(fluid, mB, fuel);
	}

	private static void registerRecipe(FluidType type, int quantity, Item output) { registerRecipe(type, quantity, new ItemStack(output)); }
	private static void registerRecipe(FluidType type, int quantity, Block output) { registerRecipe(type, quantity, new ItemStack(output)); }
	private static void registerRecipe(FluidType type, int quantity, ItemStack output) {
		recipes.put(type, new Pair<Integer, ItemStack>(quantity, output));
	}

	public static Pair<Integer, ItemStack> getOutput(FluidType type) {
		return recipes.get(type);
	}

	public static HashMap<ItemStack, ItemStack> getRecipes() {

		HashMap<ItemStack, ItemStack> recipes = new HashMap<ItemStack, ItemStack>();

		for(Entry<FluidType, Pair<Integer, ItemStack>> entry : SolidificationRecipes.recipes.entrySet()) {

			FluidType type = entry.getKey();
			int amount = entry.getValue().getKey();
			ItemStack out = entry.getValue().getValue().copy();
            com.hbm.realsim.ProcessRecipe bill = getPhysical(type);
            if(bill != null) {
                amount = bill.quantities[0];
                out = com.hbm.realsim.ProcessMachine.solid(bill.outputs[0], Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[0], com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).massUnits)));
            }

			recipes.put(ItemFluidIcon.make(type, amount), out);
		}

		return recipes;
	}

	@Override
	public String getFileName() {
		return "hbmSolidifier.json";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void deleteRecipes() {
        bitumenBill = null; overrides.clear();
		recipes.clear();
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = (JsonObject) recipe;
		FluidStack in = this.readFluidStack(obj.get("input").getAsJsonArray());

		ItemStack out = this.readItemStack(obj.get("output").getAsJsonArray());
        com.hbm.realsim.ProcessRecipe canonical = in.type == BITUMEN ? com.hbm.realsim.ChemicalFuelProcesses.bitumenFreezing() : in.type == WATER ? waterBill : in.type == CARBONDIOXIDE ? co2Bill : in.type == SODIUM ? sodiumBill : in.type == POTASSIUM ? potassiumBill : null;
        com.hbm.realsim.ProcessRecipe bill = obj.has("physical") ? com.hbm.realsim.PetroleumProcesses.read(obj) : canonical;
        if(bill != null) {
            if(bill.inputs.length != 1 || !bill.inputs[0].equals(in.type.getName()) || bill.solidInputs.length != 0 || bill.heatQuanta != 0
                    || bill.electricalRatingWatts > 5000 || com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).phase != com.hbm.realsim.PhysicalState.Phase.SOLID) throw new IllegalArgumentException("Solidifier requires one fluid feed, one leading solid product and at most 5 kWe");
            for(int i = 1; i < bill.outputs.length; i++) if(!bill.outputs[i].startsWith("VENT_")) throw new IllegalArgumentException("Solidifier has one product port");
            ItemStack product = com.hbm.realsim.ProcessMachine.solid(bill.outputs[0], Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[0], com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).massUnits)));
            if(!obj.has("physical") && (in.fill != bill.quantities[0] || !out.isItemEqual(product) || out.stackSize != product.stackSize)) throw new IllegalArgumentException("Legacy solidification disagrees with its physical bill; regenerate defaults");
            overrides.put(in.type, bill); in.fill = bill.quantities[0]; out = product;
        } else if(in.type == SLOP || in.type == REDMUD || com.hbm.realsim.SolidFuel.resolve(out) != null) throw new IllegalArgumentException("Material/fuel recovery requires a physical reaction, not nominal cooling");
		recipes.put(in.type, new Pair(in.fill, out));
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		Entry<FluidType, Pair<Integer, ItemStack>> rec = (Entry<FluidType, Pair<Integer, ItemStack>>) recipe;
		FluidStack in = new FluidStack(rec.getKey(), rec.getValue().getKey());
		com.hbm.realsim.ProcessRecipe bill = getPhysical(rec.getKey());
        if(bill != null) com.hbm.realsim.PetroleumProcesses.write(bill, writer);
		writer.name("input");
		this.writeFluidStack(in, writer);
		writer.name("output");
        ItemStack product = bill == null ? rec.getValue().getValue() : com.hbm.realsim.ProcessMachine.solid(bill.outputs[0], Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[0], com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).massUnits)));
		this.writeItemStack(product, writer);
	}
}
