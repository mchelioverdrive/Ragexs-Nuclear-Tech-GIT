package com.hbm.inventory.recipes;

import static com.hbm.inventory.OreDictManager.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class LiquefactionRecipes extends SerializableRecipe {

	private static HashMap<Object, FluidStack> recipes = new HashMap();

    private static final HashMap<String, com.hbm.realsim.ProcessRecipe> physicalBills = new HashMap<>();
    private static final HashMap<Object, com.hbm.realsim.ProcessRecipe> overrides = new HashMap<>();
    private static Object recipeKey(ItemStack input) {
        if(input == null || input.getItem() == null) return null;
        ComparableStack exact = new ComparableStack(input).makeSingular();
        if(recipes.containsKey(exact)) return exact;
        ComparableStack wildcard = new ComparableStack(input.getItem(), 1, OreDictionary.WILDCARD_VALUE);
        if(recipes.containsKey(wildcard)) return wildcard;
        for(String name : exact.getDictKeys()) if(recipes.containsKey(name)) return name;
        return null;
    }
    private static com.hbm.realsim.ProcessRecipe stockPhysical(Object key) {
        String solid = null;
        if(key instanceof String) {
            if(key.equals(NA.dust())) solid = "SODIUM_ITEM";
        } else if(key instanceof ComparableStack) {
            net.minecraft.item.Item item = ((ComparableStack) key).toStack().getItem();
            solid = item == Items.snowball ? "SNOWBALL_ITEM"
                    : item == net.minecraft.item.Item.getItemFromBlock(Blocks.snow) ? "SNOW_BLOCK"
                    : item == net.minecraft.item.Item.getItemFromBlock(Blocks.ice) ? "ICE_BLOCK"
                    : item == net.minecraft.item.Item.getItemFromBlock(Blocks.packed_ice) ? "PACKED_ICE_BLOCK"
                    : item == net.minecraft.item.Item.getItemFromBlock(ModBlocks.dry_ice) ? "DRY_ICE_BLOCK"
                    : item == ModItems.powder_sodium ? "SODIUM_ITEM" : item == ModItems.potassium_powder ? "POTASSIUM_ITEM" : null;
        }
        if(solid == null) return null;
        com.hbm.realsim.ProcessRecipe bill = physicalBills.get(solid);
        if(bill == null) physicalBills.put(solid, bill = com.hbm.realsim.ChemistryProcesses.melting(solid));
        return bill;
    }
    public static com.hbm.realsim.ProcessRecipe getPhysical(ItemStack input) {
        Object key = recipeKey(input); if(key == null) return null;
        return overrides.containsKey(key) ? overrides.get(key) : stockPhysical(key);
    }
    private static FluidStack display(com.hbm.realsim.ProcessRecipe bill) {
        return new FluidStack(Fluids.fromName(bill.outputs[0]), Math.toIntExact(com.hbm.realsim.PetroleumProcesses.ceilDiv(bill.productMass[0], com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).massUnits)));
    }

	@Override
	public void registerDefaults() {

		//oil processing
		// Coal is not a meltable crude-oil feed. Direct coal liquefaction is represented
		// by the hydrogen-fed pyro-oven recipes, while this machine remains a physical liquefactor.
		recipes.put(KEY_LOG,										new FluidStack(100, Fluids.MUG));
		recipes.put(NA.dust(),										new FluidStack(100, Fluids.SODIUM));
		recipes.put(PB.ingot(),										new FluidStack(100, Fluids.LEAD));
		recipes.put(PB.dust(),										new FluidStack(100, Fluids.LEAD));
		//general utility recipes because why not
		recipes.put(new ComparableStack(Blocks.netherrack),			new FluidStack(250, Fluids.LAVA));
		//recipes.put(new ComparableStack(Blocks.cobblestone),		new FluidStack(250, Fluids.LAVA));
		//recipes.put(new ComparableStack(Blocks.stone),				new FluidStack(250, Fluids.LAVA));
		recipes.put(new ComparableStack(Blocks.obsidian),			new FluidStack(500, Fluids.LAVA));
		recipes.put(new ComparableStack(Items.snowball),			new FluidStack(1, Fluids.WATER));
		recipes.put(new ComparableStack(Blocks.snow),				new FluidStack(1, Fluids.WATER));
		recipes.put(new ComparableStack(Blocks.ice),				new FluidStack(1, Fluids.WATER));
		recipes.put(new ComparableStack(Blocks.packed_ice),			new FluidStack(2, Fluids.WATER));
		recipes.put(new ComparableStack(ModBlocks.dry_ice),			new FluidStack(1000, Fluids.CARBONDIOXIDE));
		//recipes.put(new ComparableStack(Items.ender_pearl),			new FluidStack(100, Fluids.ENDERJUICE));
		//ionized particles -> helium 4
        // Helium comes from assayed geological gas; an exotic charged pellet is not an unbounded bulk gas source.


		// Alcohol production uses the reactor fermentation process.
		//hemp
		// Alcohol production uses the reactor fermentation process.
		//mustard willow... isn't a real thing bye
		//recipes.put(new ComparableStack(ModBlocks.plant_flower, 1, 4), new FluidStack(50, Fluids.ETHANOL));
		//recipes.put(new ComparableStack(ModItems.glyphid_gland_empty),			new FluidStack(2000, Fluids.BIOGAS));

		recipes.put(new ComparableStack(Items.wheat_seeds),			new FluidStack(50, Fluids.SEEDSLURRY));
		recipes.put(new ComparableStack(Blocks.tallgrass, 1, 1),	new FluidStack(100, Fluids.SEEDSLURRY));
		recipes.put(new ComparableStack(Blocks.tallgrass, 1, 2),	new FluidStack(100, Fluids.SEEDSLURRY));
		recipes.put(new ComparableStack(Blocks.vine),				new FluidStack(100, Fluids.SEEDSLURRY));
		//recipes.put(new ComparableStack(ModItems.flesh),			new FluidStack(20, Fluids.BLOOD));
		//that's not how that works either-
		//recipes.put(new ComparableStack(ModItems.ingot_osmiridium),	new FluidStack(24000, Fluids.ETHANOL));
		//I don't think that's how that works-
		//recipes.put(new ComparableStack(ModItems.solid_fuel_bf),	new FluidStack(250, Fluids.BALEFIRE));

		//potassium -> liquid potassium
		recipes.put(new ComparableStack(ModItems.potassium_powder),				new FluidStack(100, Fluids.POTASSIUM));

		//potato -> small amount of potassium
		// Potassium extraction is not a phase change; retain the food-processing route instead.

		//TODO: more recipes as the crack oil derivatives are added
	}

	public static FluidStack getOutput(ItemStack stack) {

		if(stack == null || stack.getItem() == null)
			return null;

		ComparableStack comp = new ComparableStack(stack.getItem(), 1, stack.getItemDamage());

		if(recipes.containsKey(comp))
			return recipes.get(comp);

		String[] dictKeys = comp.getDictKeys();
		comp = new ComparableStack(stack.getItem(), 1, OreDictionary.WILDCARD_VALUE);

		if(recipes.containsKey(comp))
			return recipes.get(comp);

		for(String key : dictKeys) {

			if(recipes.containsKey(key))
				return recipes.get(key);
		}

		if(stack.getItem() instanceof ItemFood) {
			ItemFood food = (ItemFood) stack.getItem();
			float saturation = food.func_150905_g(stack) * food.func_150906_h(stack) * 20; //food val * saturation mod * 2 (constant) * 10 (quanta)
			return new FluidStack(Fluids.SALIENT, (int) saturation);
		}

		return null;
	}

	public static HashMap<Object, ItemStack> getRecipes() {

		HashMap<Object, ItemStack> recipes = new HashMap<Object, ItemStack>();

		for(Entry<Object, FluidStack> entry : LiquefactionRecipes.recipes.entrySet()) {

			com.hbm.realsim.ProcessRecipe bill = overrides.containsKey(entry.getKey()) ? overrides.get(entry.getKey()) : stockPhysical(entry.getKey());
            FluidStack out = bill == null ? entry.getValue() : display(bill);

			if(entry.getKey() instanceof String) {
				recipes.put(new OreDictStack((String)entry.getKey()), com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.describe(bill == null ? ItemFluidIcon.make(out.type, out.fill) : ItemFluidIcon.makePhysical(out, bill.productMass[0]), bill));
			} else {
				recipes.put(((ComparableStack)entry.getKey()).toStack(), com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.describe(bill == null ? ItemFluidIcon.make(out.type, out.fill) : ItemFluidIcon.makePhysical(out, bill.productMass[0]), bill));
			}
		}

		return recipes;
	}

	@Override
	public String getFileName() {
		return "hbmLiquefactor.json";
	}

	@Override
	public String getComment() {
		return "As with most handlers, stacksizes for the inputs are ignored and default to 1.";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void deleteRecipes() {
        overrides.clear();
		recipes.clear();
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = (JsonObject) recipe;
		AStack in = this.readAStack(obj.get("input").getAsJsonArray());
		FluidStack out = this.readFluidStack(obj.get("output").getAsJsonArray());

        Object key = in instanceof ComparableStack ? ((ComparableStack) in).makeSingular() : in instanceof OreDictStack ? ((OreDictStack) in).name : null;
        if(key == null) throw new IllegalArgumentException("Unsupported liquefactor recipe key");
        com.hbm.realsim.ProcessRecipe bill = obj.has("physical") ? com.hbm.realsim.PetroleumProcesses.read(obj) : stockPhysical(key);
        if(bill != null) {
            if(bill.inputs.length != 0 || bill.solidInputs.length != 1 || bill.solidCounts[0] != 1 || bill.electricalRatingWatts > 5000 || bill.heatQuanta != 0
                    || bill.outputs[0].startsWith("VENT_") || com.hbm.realsim.PhysicalRegistry.get(bill.outputs[0]).phase == com.hbm.realsim.PhysicalState.Phase.SOLID) throw new IllegalArgumentException("Liquefactor requires one solid parcel, one leading fluid product and at most 5 kWe");
            for(int i = 1; i < bill.outputs.length; i++) if(!bill.outputs[i].startsWith("VENT_")) throw new IllegalArgumentException("Liquefactor has one product port");
            if(key instanceof ComparableStack && !com.hbm.realsim.ProcessMachine.matches(((ComparableStack) key).toStack(), bill.solidInputs[0])) throw new IllegalArgumentException("Liquefactor lookup key differs from physical feed");
            FluidStack expected = display(bill);
            if(!obj.has("physical") && (out.type != expected.type || out.fill != expected.fill)) throw new IllegalArgumentException("Legacy phase recipe disagrees with its mass bill; regenerate defaults");
            overrides.put(key, bill); out = expected;
        } else if(com.hbm.realsim.ChemistryProcesses.requiresBillForOutput(out.type.getName())) throw new IllegalArgumentException("Released fluid production requires an explicit physical bill");
        recipes.put(key, out);
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		Entry<Object, FluidStack> rec = (Entry<Object, FluidStack>) recipe;
		Object key = rec.getKey();

		writer.name("input");
		if(key instanceof String) {
			this.writeAStack(new OreDictStack((String) key), writer);
		} else if(key instanceof ComparableStack) {
			this.writeAStack((ComparableStack) key, writer);
		}

        com.hbm.realsim.ProcessRecipe bill = overrides.containsKey(key) ? overrides.get(key) : stockPhysical(key);
        if(bill != null) com.hbm.realsim.PetroleumProcesses.write(bill, writer);
		writer.name("output");
		this.writeFluidStack(bill == null ? rec.getValue() : display(bill), writer);
	}
}
