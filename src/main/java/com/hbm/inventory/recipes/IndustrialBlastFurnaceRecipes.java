package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import net.minecraft.item.ItemStack;

/** Curated ferrous subset of upstream's industrial furnace, on RTM's recipe loader. */
public class IndustrialBlastFurnaceRecipes extends SerializableRecipe {
    public static final List<Recipe> recipes = new ArrayList<Recipe>();
    public static class Recipe {
        public final AStack feed;
        public final boolean coke;
        public final ItemStack steel, slag;
        public final long heatJoules;
        public final int air;
        public Recipe(AStack feed, boolean coke, ItemStack steel, ItemStack slag, long heatJoules, int air) {
            if(feed == null || feed.stacksize <= 0 || steel == null || steel.stackSize <= 0 || steel.stackSize > steel.getMaxStackSize()
                    || slag != null && (slag.stackSize <= 0 || slag.stackSize > slag.getMaxStackSize())
                    || heatJoules <= 0 || heatJoules > 1_000_000_000L || air <= 0 || air > 1_000_000) throw new IllegalArgumentException("Invalid blast furnace bill");
            this.feed = feed; this.coke = coke; this.steel = steel; this.slag = slag; this.heatJoules = heatJoules; this.air = air;
        }
    }
    @Override public void registerDefaults() {
        // The ore parcel represents enough recoverable iron for three adopted 1-kg ingots.
        add("oreIron", 1, true, 3, 2, 18_000_000L, 2700);
        add("oreIron", 1, false, 2, 3, 16_000_000L, 2400);
        add("ingotIron", 2, true, 2, 0, 8_000_000L, 1800);
        add("dustIron", 2, true, 2, 0, 8_000_000L, 1800);
        add("ingotIron", 2, false, 2, 0, 12_000_000L, 2400);
        add("dustIron", 2, false, 2, 0, 12_000_000L, 2400);
    }
    private static void add(String feed, int amount, boolean coke, int steel, int slag, long heat, int air) {
        recipes.add(new Recipe(new OreDictStack(feed, amount), coke, new ItemStack(ModItems.ingot_steel, steel),
                slag == 0 ? null : new ItemStack(ModItems.slagingot, slag), heat, air));
    }
    public static Recipe find(ItemStack feed, ItemStack reductant) {
        String identity = com.hbm.realsim.BlastFurnaceProcess.fuelIdentity(reductant);
        if(identity == null) return null;
        for(Recipe recipe : recipes) if(recipe.coke == identity.equals("COKE_ITEM") && recipe.feed.matchesRecipe(feed, false)) return recipe;
        return null;
    }
    @Override public String getFileName() { return "hbmIndustrialBlastFurnace.json"; }
    @Override public String getComment() { return "Ferrous process bills. One reductant item is reserved with the feed; auxiliary fuel is charged by mass/LHV. Heat is J; air is standard L. Old hbmBlastFurnace.json remains the Metallurgical Furnace override."; }
    @Override public Object getRecipeObject() { return recipes; }
    @Override public void deleteRecipes() { recipes.clear(); }
    @Override public void readRecipe(JsonElement element) {
        JsonObject obj = element.getAsJsonObject();
        recipes.add(new Recipe(readAStack(obj.getAsJsonArray("feed")), obj.get("coke").getAsBoolean(), readItemStack(obj.getAsJsonArray("steel")),
                obj.has("slag") ? readItemStack(obj.getAsJsonArray("slag")) : null, obj.get("heatJoules").getAsLong(), obj.get("air").getAsInt()));
    }
    @Override public void writeRecipe(Object object, JsonWriter writer) throws IOException {
        Recipe recipe = (Recipe) object;
        writer.name("feed"); writeAStack(recipe.feed, writer);
        writer.name("coke").value(recipe.coke);
        writer.name("steel"); writeItemStack(recipe.steel, writer);
        if(recipe.slag != null) { writer.name("slag"); writeItemStack(recipe.slag, writer); }
        writer.name("heatJoules").value(recipe.heatJoules); writer.name("air").value(recipe.air);
    }
}
