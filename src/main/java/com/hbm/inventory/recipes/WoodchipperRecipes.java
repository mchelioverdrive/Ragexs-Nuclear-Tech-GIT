package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import static com.hbm.inventory.OreDictManager.*;

public final class WoodchipperRecipes extends SerializableRecipe {
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();
    private static final LinkedHashMap<OreDictStack, ItemStack> RECIPES = new LinkedHashMap<>();

    static {
        DEFAULTS.put(KEY_LOG, 8);
        DEFAULTS.put(KEY_PLANKS, 2);
        DEFAULTS.put(KEY_STICK, 1);
        DEFAULTS.put(KEY_SAPLING, 1);
        DEFAULTS.put(KEY_LEAVES, 1);
        DEFAULTS.put(KEY_SLAB, 1);
    }

    public static LinkedHashMap<OreDictStack, ItemStack> getRecipes() {
        return RECIPES;
    }

    /** The returned stack belongs to the recipe. Inventory callers copy it before inserting it. */
    public static ItemStack getResult(ItemStack input) {
        if(input == null || input.stackSize <= 0 || input.getItem() == ModItems.biomass || input.getItem() == ModItems.biomass_compressed)
            return null;
        int[] ids = OreDictionary.getOreIDs(input);
        if(ids.length == 0) return null;
        for(Map.Entry<OreDictStack, ItemStack> recipe : RECIPES.entrySet()) {
            for(int id : ids) {
                if(recipe.getKey().name.equals(OreDictionary.getOreName(id))) return recipe.getValue();
            }
        }
        return null;
    }

    @Override public void registerDefaults() { DEFAULTS.forEach(WoodchipperRecipes::register); }
    @Override public void deleteRecipes() { RECIPES.clear(); }
    @Override public String getFileName() { return "hbmWoodchipper.json"; }
    @Override public Object getRecipeObject() { return RECIPES; }

    private static void register(String ore, int count) {
        RECIPES.put(new OreDictStack(ore), new ItemStack(ModItems.biomass, count));
    }

    @Override public void readRecipe(JsonElement recipe) {
        JsonObject json = recipe.getAsJsonObject();
        AStack input = readAStack(json.getAsJsonArray("input"));
        int biomass = json.get("biomass").getAsInt();
        if(!(input instanceof OreDictStack) || input.stacksize != 1
                || !DEFAULTS.containsKey(((OreDictStack) input).name) || biomass < 1 || biomass > 64)
            throw new IllegalArgumentException("Woodchipper recipes take one item from a supported wood tag and yield 1 to 64 biomass.");
        register(((OreDictStack) input).name, biomass);
    }

    @Override public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) recipe;
        writer.name("input");
        writeAStack((OreDictStack) entry.getKey(), writer);
        writer.name("biomass").value(((ItemStack) entry.getValue()).stackSize);
    }
}
