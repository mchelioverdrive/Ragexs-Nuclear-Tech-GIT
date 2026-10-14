package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.gui.GUIBlastFurnace;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes.Recipe;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumCokeType;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** RTM native NEI integration; the shared generic upstream recipe framework is not imported. */
public class BlastFurnaceHandler extends NEIUniversalHandler {
    public BlastFurnaceHandler() {
        super(ModBlocks.machine_blast_furnace.getLocalizedName(), ModBlocks.machine_blast_furnace, generateRecipes());
        guiGui.add(GUIBlastFurnace.class);
        transferRectsGui.add(new RecipeTransferRect(new Rectangle(79, 62, 18, 18), getKey()));
    }
    @Override public String getKey() { return "ntmIndustrialBlastFurnace"; }
    @Override public void drawExtras(int recipe) {
        net.minecraft.client.Minecraft.getMinecraft().fontRenderer.drawString("Recover CO-bearing furnace gas", 3, 65, 0x555555);
        net.minecraft.client.Minecraft.getMinecraft().fontRenderer.drawString("Limestone flux / hot blast / O2", 3, 76, 0x555555);
    }
    private static HashMap<Object, Object> generateRecipes() {
        HashMap<Object, Object> result = new HashMap<Object, Object>();
        for(Recipe recipe : IndustrialBlastFurnaceRecipes.recipes) {
            ItemStack reductant = recipe.coke ? new ItemStack(ModItems.coke, 1, EnumCokeType.COAL.ordinal()) : new ItemStack(Items.coal);
            Object[] inputs = {recipe.feed, reductant};
            ItemStack[] outputs = recipe.slag == null ? new ItemStack[] {recipe.steel} : new ItemStack[] {recipe.steel, recipe.slag};
            result.put(inputs, outputs);
        }
        return result;
    }
}
