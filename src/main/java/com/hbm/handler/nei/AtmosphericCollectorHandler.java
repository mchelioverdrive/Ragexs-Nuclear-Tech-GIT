package com.hbm.handler.nei;

import java.util.HashMap;
import com.hbm.blocks.ModBlocks;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.dim.trait.CBT_Atmosphere.FluidEntry;
import com.hbm.inventory.fluid.trait.FT_Gaseous;
import com.hbm.items.machine.ItemFluidIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Environmental sources: the displayed machine is required infrastructure, never consumed. */
public class AtmosphericCollectorHandler extends NEIUniversalHandler {
    private final HashMap<Object, String[]> conditions = new HashMap<>();
    public AtmosphericCollectorHandler() {
        super(ModBlocks.machine_atmo_vent.getLocalizedName(), ModBlocks.machine_atmo_vent, new HashMap<>());
        for(CelestialBody body : CelestialBody.getAllBodies()) {
            if(!body.canLand) continue;
            CBT_Atmosphere atmosphere = body.getTrait(CBT_Atmosphere.class);
            if(atmosphere == null || atmosphere.fluids.isEmpty()) continue;
            FluidEntry mainEntry = atmosphere.fluids.get(0);
            for(FluidEntry entry : atmosphere.fluids) if(entry.pressure > mainEntry.pressure) mainEntry = entry;
            for(FluidEntry entry : atmosphere.fluids) {
                if(!atmosphere.hasFluid(entry.fluid) || !entry.fluid.hasTrait(FT_Gaseous.class)) continue;
                boolean bulk = entry.fluid == mainEntry.fluid;
                ItemStack machine = new ItemStack(ModBlocks.machine_atmo_vent);
                int rate = com.hbm.tileentity.machine.TileEntityAtmoExtractor.collectionPerTick(entry.fluid, !bulk);
                long watts = com.hbm.tileentity.machine.TileEntityAtmoExtractor.collectionPowerWatts(entry.fluid, !bulk);
                recipes.put(machine, ItemFluidIcon.make(entry.fluid, rate * 20, 0));
                conditions.put(machine, new String[] {body.name + " natural atmosphere", "1 s; " + watts / 1000 + " kWe (" + (bulk ? "bulk" : "selective") + ")"});
            }
        }
    }
    @Override public String getKey() { return "ntmAtmosphericCollection"; }
    @Override public void drawExtras(int recipe) {
        String[] text = conditions.get(((RecipeSet) arecipes.get(recipe)).originalInputInstance);
        if(text == null) return;
        Minecraft.getMinecraft().fontRenderer.drawString(text[0], 4, 2, 0x404040);
        Minecraft.getMinecraft().fontRenderer.drawString(text[1], 4, 55, 0x404040);
    }
}
