package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.gui.GUICompressor;
import com.hbm.inventory.recipes.CompressorRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Displays the loaded registry, rather than a second list of hardcoded compression recipes. */
public class CompressorHandler extends NEIUniversalHandler {
    public CompressorHandler() {
        super(ModBlocks.machine_compressor.getLocalizedName(), ModBlocks.machine_compressor, new HashMap<>());
        refresh();
    }
    @Override public String getKey() { return "ntmCompression"; }
    private void refresh() {
        recipes.clear();
        recipes.putAll(CompressorRecipes.getNEIRecipes());
    }
    @Override public void loadCraftingRecipes(String id, Object... results) { refresh(); super.loadCraftingRecipes(id, results); }
    @Override public void loadCraftingRecipes(ItemStack result) { refresh(); super.loadCraftingRecipes(result); }
    @Override public void loadUsageRecipes(ItemStack ingredient) {
        refresh();
        if(ingredient.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.machine_compressor)) super.loadCraftingRecipes(getKey());
        else super.loadUsageRecipes(ingredient);
    }
    @Override public void loadTransferRects() {
        super.loadTransferRects();
        transferRectsGui.add(new RecipeTransferRect(new Rectangle(40, 18, 60, 24), getKey()));
        guiGui.add(GUICompressor.class);
        RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
    }
    @Override public void drawExtras(int recipe) {
        ItemStack key = (ItemStack) ((RecipeSet) arecipes.get(recipe)).originalInputInstance;
        NBTTagCompound tag = key.getTagCompound();
        boolean steam = com.hbm.realsim.SteamLedger.state(com.hbm.inventory.fluid.Fluids.fromID(key.getItemDamage()).getName()) != null;
        Minecraft.getMinecraft().fontRenderer.drawString(steam ? "Steam recompression" : tag.getBoolean("compressorVent") ? "Decompress; no work recovery" : "Compress / liquefy", 4, 2, 0x404040);
        String cost = tag.getLong("compressorWork") > 0 ? api.hbm.energymk2.EnergyUnits.formatJoules(tag.getLong("compressorWork")) : "Base cycle";
        // Steam uses the continuous thermal account's mass-flow/rejection limits, not a discrete recipe timer.
        String detail = steam ? cost + " / shown parcel" : String.format(java.util.Locale.US, "%.2f s; %s", tag.getInteger("compressorTicks") / 20D, cost);
        Minecraft.getMinecraft().fontRenderer.drawString(detail, 4, 55, 0x404040);
    }
}
