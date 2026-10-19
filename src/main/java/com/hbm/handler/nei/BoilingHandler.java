package com.hbm.handler.nei;

import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.ProcessRecipe;
import com.hbm.realsim.SteamLedger;
import com.hbm.tileentity.machine.TileEntityHeatBoilerBase;
import net.minecraft.item.ItemStack;

/** The common boiler processes, with higher-pressure steam restricted to industrial hardware. */
public class BoilingHandler extends NEIUniversalHandler {
    public BoilingHandler() {
        super(ModBlocks.machine_boiler.getLocalizedName(), new ItemStack[] {
                new ItemStack(ModBlocks.machine_boiler), new ItemStack(ModBlocks.machine_industrial_boiler)}, generateRecipes());
        machineOverrides = new HashMap<>();
        for(Object key : recipes.keySet()) if(industrialOnly((ItemStack) key))
            machineOverrides.put(key, new ItemStack(ModBlocks.machine_industrial_boiler));
    }
    @Override public String getKey() { return "ntmBoiling"; }
    public static HashMap<Object, Object> cache;
    public static boolean isReload = false;
    private static long fluidRevision = -1;
    private static boolean industrialOnly(ItemStack key) {
        return key.hasTagCompound() && key.getTagCompound().getString("boilerSteamOutlet").equals("HOTSTEAM");
    }
    @Override public void loadUsageRecipes(ItemStack ingredient) {
        if(!NEISafe.isValid(ingredient)) return;
        boolean small = ingredient.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.machine_boiler);
        boolean industrial = ingredient.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.machine_industrial_boiler);
        if(small || industrial) {
            super.loadCraftingRecipes(getKey());
            if(small) arecipes.removeIf(page -> industrialOnly((ItemStack) ((RecipeSet) page).originalInputInstance));
        } else super.loadUsageRecipes(ingredient);
    }
    @Override public void drawExtras(int recipe) {
        ItemStack input = (ItemStack) ((RecipeSet) arecipes.get(recipe)).originalInputInstance;
        if(input.hasTagCompound() && input.getTagCompound().hasKey("boilerTemperature")) {
            int temperature = input.getTagCompound().getInteger("boilerTemperature");
            net.minecraft.client.Minecraft.getMinecraft().fontRenderer.drawString("Heat source: " + temperature + " C+", 4, 55, 0x404040);
        }
    }
    @Override public java.util.List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe gui, ItemStack stack, java.util.List<String> lines, int recipe) {
        if(isRecipeItem(stack, recipe)) {
            ItemStack input = (ItemStack) ((RecipeSet) arecipes.get(recipe)).originalInputInstance;
            SteamLedger.State in = SteamLedger.state(Fluids.fromID(input.getItemDamage()).getName());
            if(in != null && input.hasTagCompound() && input.getTagCompound().hasKey("boilerSteamOutlet")) {
                SteamLedger.State out = SteamLedger.state(input.getTagCompound().getString("boilerSteamOutlet"));
                SteamLedger.Transition bill = SteamLedger.transition(in, out, SteamLedger.Route.BOILER, 980000);
                lines.add("Mass conserving: one feed unit; fractional steam accumulates");
                lines.add("Heat: " + SteamCycleHandler.energy(bill.heatJPerKg) + "/kg; pump work: " + SteamCycleHandler.energy(bill.workJPerKg) + "/kg");
                lines.add(out == SteamLedger.State.HOT ? "Industrial boiler only; up to 12 MWth; paid feed-pump work" : "Small boiler: 200 kWth; industrial boiler: 12 MWth");
                lines.add("Heat source >= " + (out.temperatureC + 30) + " C; startup heat required; feed and output room limit flow");
            }
        }
        return super.handleItemTooltip(gui, stack, lines, recipe);
    }
    public static HashMap<Object, Object> generateRecipes() {
        if(cache != null && !isReload && fluidRevision == Fluids.getRegistryRevision()) return cache;
        fluidRevision = Fluids.getRegistryRevision();
        cache = new HashMap<>();
        for(int pressure = 0; pressure <= 1; pressure++) {
            addBlast(Fluids.AIR, Fluids.AIR_HEATED, pressure);
            addBlast(Fluids.AIR_HEATED, Fluids.AIRBLAST, pressure);
        }
        for(FluidType type : Fluids.getInNiceOrder()) {
            if(!TileEntityHeatBoilerBase.supportsFeed(type) || type == Fluids.AIR || type == Fluids.AIR_HEATED) continue;
            SteamLedger.State steam = SteamLedger.state(type.getName());
            if(steam != null) {
                for(SteamLedger.State outlet : new SteamLedger.State[] {SteamLedger.State.ORDINARY, SteamLedger.State.HOT}) {
                    ItemStack input = ItemFluidIcon.make(type, 1);
                    input.getTagCompound().setString("boilerSteamOutlet", outlet.identity);
                    input.getTagCompound().setInteger("boilerTemperature", outlet.temperatureC + 30);
                    cache.put(input, ItemFluidIcon.makePhysical(new FluidStack(Fluids.fromName(outlet.identity), 1), steam.massPerUnit));
                }
                continue;
            }
            ProcessRecipe bill = TileEntityHeatBoilerBase.heatingRecipe(type);
            if(bill != null) {
                ItemStack input = ItemFluidIcon.make(type, bill.quantities[0], bill.pressures[0]);
                input.getTagCompound().setInteger("boilerTemperature", bill.temperatureC);
                com.hbm.inventory.recipes.loader.PetroleumRecipeLoader.describe(input, bill);
                cache.put(input, AtmosphericTowerHandler.products(bill));
            } else if(type.hasTrait(FT_Heatable.class)) {
                FT_Heatable trait = type.getTrait(FT_Heatable.class);
                if(trait.getEfficiency(HeatingType.BOILER) > 0) {
                    HeatingStep step = trait.getFirstStep();
                    cache.put(ItemFluidIcon.make(type, step.amountReq), ItemFluidIcon.make(step.typeProduced, step.amountProduced));
                }
            }
        }
        isReload = false;
        return cache;
    }
    private static void addBlast(FluidType input, FluidType output, int pressure) {
        ItemStack key = ItemFluidIcon.make(input, 1, pressure);
        key.getTagCompound().setInteger("boilerTemperature", output.temperature + 30);
        cache.put(key, ItemFluidIcon.make(output, 1, pressure));
    }
}
