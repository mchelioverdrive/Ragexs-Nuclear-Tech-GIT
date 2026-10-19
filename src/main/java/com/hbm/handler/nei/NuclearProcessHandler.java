package com.hbm.handler.nei;

import java.util.*;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemICFPellet;
import com.hbm.items.machine.ItemICFPellet.EnumICFFuel;
import com.hbm.items.machine.ItemPWRFuel.EnumPWRFuel;
import com.hbm.items.machine.ItemWatzPellet.EnumWatzType;
import com.hbm.tileentity.machine.TileEntityReactorResearch;
import net.minecraft.item.ItemStack;

/** Native fuel exhaustion and configurable ICF feed definitions; never simulates a reactor. */
public class NuclearProcessHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Nuclear Processes"; }
    @Override public String getOverlayIdentifier() { return "ntmNuclearProcesses"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected net.minecraft.block.Block[] machineBlocks() {
        return new net.minecraft.block.Block[] {ModBlocks.pwr_controller, ModBlocks.watz, ModBlocks.reactor_research, ModBlocks.machine_icf_press, ModBlocks.icf, ModBlocks.rbmk_rod, ModBlocks.rbmk_rod_mod, ModBlocks.rbmk_rod_reasim, ModBlocks.rbmk_rod_reasim_mod, ModBlocks.block_graphite_fuel, ModBlocks.block_graphite_lithium};
    }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        for(Object registered : net.minecraft.item.Item.itemRegistry) if(registered instanceof com.hbm.items.machine.ItemRBMKRod) {
            com.hbm.items.machine.ItemRBMKRod rod = (com.hbm.items.machine.ItemRBMKRod) registered;
            ItemStack spent = new ItemStack(rod); com.hbm.items.machine.ItemRBMKRod.setYield(spent, 0);
            for(net.minecraft.block.Block column : new net.minecraft.block.Block[] {ModBlocks.rbmk_rod, ModBlocks.rbmk_rod_mod, ModBlocks.rbmk_rod_reasim, ModBlocks.rbmk_rod_reasim_mod})
                rows.add(new Process(column, inputs(new ItemStack(rod)), new ItemStack[] {spent},
                        "Fuel burnup changes the SAME rod's NBT; no extra rod is created", "Native integrated flux capacity: " + rod.yield,
                        "Heat, enrichment, xenon and neutron spectrum determine operation", "Hull melting limit: " + rod.meltingPoint + " C; no fixed cycle time"));
        }
        rows.add(new Process(ModBlocks.block_graphite_fuel, inputs(new ItemStack(ModBlocks.block_graphite_fuel)), new ItemStack[] {new ItemStack(ModBlocks.block_graphite_plutonium)},
                "Graphite pile fuel transmutation; placed block changes in world", "Progress threshold: " + com.hbm.tileentity.machine.pile.TileEntityPileFuel.maxProgress,
                "Neutron supply, heat and metadata affect progress; no fixed duration", "Block orientation metadata retained; overheating can destroy the fuel"));
        rows.add(new Process(ModBlocks.block_graphite_lithium, inputs(new ItemStack(ModBlocks.block_graphite_lithium)), new ItemStack[] {new ItemStack(ModBlocks.block_graphite_tritium)},
                "Graphite pile lithium breeding; placed block changes in world", "Progress threshold: " + com.hbm.tileentity.machine.pile.TileEntityPileBreedingFuel.maxProgress,
                "Absorbed neutrons and temperature set progress; no fixed duration", "Block metadata retained; no item or fluid paid each tick"));
        for(EnumPWRFuel fuel : EnumPWRFuel.values()) rows.add(new Process(ModBlocks.pwr_controller,
                inputs(new ItemStack(ModItems.pwr_fuel, 1, fuel.ordinal())), new ItemStack[] {new ItemStack(ModItems.pwr_fuel_hot, 1, fuel.ordinal())},
                "Fuel exhaustion, not a fixed crafting cycle", "Burnup threshold: " + fuel.yield + " native units",
                "Rate depends on rod layout, control rods and neutron flux", "Heat and primary coolant are separate reactor paths"));
        for(EnumWatzType fuel : EnumWatzType.values()) rows.add(new Process(ModBlocks.watz,
                inputs(new ItemStack(ModItems.watz_pellet, 1, fuel.ordinal())), new ItemStack[] {new ItemStack(ModItems.watz_pellet_depleted, 1, fuel.ordinal())},
                "One fuel pellet -> one depleted pellet at zero enrichment", "Burnup and heat depend on enrichment and stack arrangement", "Helium cooling and containment required; no fixed cycle time"));
        for(Map.Entry<ComparableStack, ItemStack> entry : TileEntityReactorResearch.getFuelRecipes().entrySet())
            rows.add(new Process(ModBlocks.reactor_research, inputs(entry.getKey().toStack()), new ItemStack[] {entry.getValue()},
                    "Research reactor fuel exhaustion; one plate -> one waste plate", "Rate depends on neutron flux and control setting", "Requires surrounding water; not a piped coolant recipe"));
        // This is the same idempotent feed-definition initialization used by the native press.
        ItemICFPellet.init();
        Map<EnumICFFuel, List<ItemStack>> feeds = new EnumMap<>(EnumICFFuel.class);
        for(Map.Entry<FluidType, EnumICFFuel> entry : ItemICFPellet.fluidMap.entrySet())
            feeds.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(ItemFluidIcon.make(entry.getKey(), 1000));
        for(Map.Entry<ComparableStack, List<Mats.MaterialStack>> entry : Mats.materialEntries.entrySet()) {
            List<Mats.MaterialStack> materials = entry.getValue();
            if(materials.size() != 1 || materials.get(0).amount != MaterialShapes.INGOT.q(1)) continue;
            EnumICFFuel fuel = ItemICFPellet.materialMap.get(materials.get(0).material);
            if(fuel != null) feeds.computeIfAbsent(fuel, ignored -> new ArrayList<>()).add(entry.getKey().toStack());
        }
        // Include ore-dictionary forms admitted by Mats, not merely its exact item entries.
        for(Map.Entry<NTMMaterial, EnumICFFuel> entry : ItemICFPellet.materialMap.entrySet()) {
            for(String name : entry.getKey().names) for(ItemStack stack : net.minecraftforge.oredict.OreDictionary.getOres("ingot" + name)) {
                List<Mats.MaterialStack> materials = Mats.getMaterialsFromItem(stack);
                if(materials != null && materials.size() == 1 && materials.get(0).material == entry.getKey() && materials.get(0).amount == MaterialShapes.INGOT.q(1))
                    feeds.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(stack.copy());
            }
        }
        EnumICFFuel[] fuels = EnumICFFuel.values();
        for(int i = 0; i < fuels.length; i++) for(int j = i + 1; j < fuels.length; j++) {
            if(!feeds.containsKey(fuels[i]) || !feeds.containsKey(fuels[j])) continue;
            for(boolean muon : new boolean[] {false, true}) {
                ItemStack pellet = ItemICFPellet.setup(fuels[i], fuels[j], muon);
                List<ItemStack[]> in = new ArrayList<>();
                in.add(new ItemStack[] {new ItemStack(ModItems.icf_pellet_empty)});
                in.add(feeds.get(fuels[i]).toArray(new ItemStack[0])); in.add(feeds.get(fuels[j]).toArray(new ItemStack[0]));
                if(muon) in.add(new ItemStack[] {new ItemStack(ModItems.particle_muon)});
                rows.add(new Process(ModBlocks.machine_icf_press, in.toArray(new ItemStack[0][]), new ItemStack[] {pellet},
                        "Two distinct fuels; feed positions may be exchanged", "Each feed: 1,000 L fluid OR one ingot-equivalent solid item",
                        "Press checks each tick; output slot must be empty",
                        muon ? "Muon is a utility charge: ONE item supplies SIXTEEN pellets" : "No muon charge; normal ignition difficulty",
                        muon ? "Muon container returned when charged; 1/16 charge per pellet" : "Optional muon mode lowers ignition threshold by four"));
                rows.add(new Process(ModBlocks.icf, inputs(pellet), new ItemStack[] {new ItemStack(ModItems.icf_pellet_depleted), ItemFluidIcon.make(Fluids.STELLAR_FLUX, 0)},
                        "Fuel exhaustion, not a one-tick recipe; real pellet NBT", "Minimum laser driver: " + ItemICFPellet.getFusingDifficulty(pellet) + " native units",
                        "Burnup threshold: " + ItemICFPellet.getMaxDepletion(pellet), "Heat depends on delivered laser power and both fuels",
                        "Stellar flux: ceil(core heat * 2.5 / max heat) per active tick", "Flux icon has no fixed quantity; coolant path shown separately"));
            }
        }
        for(FluidType type : Fluids.getInNiceOrder()) {
            if(!type.hasTrait(FT_Heatable.class)) continue;
            FT_Heatable trait = type.getTrait(FT_Heatable.class); FT_Heatable.HeatingStep step = trait.getFirstStep();
            if(step == null || step.typeProduced == null || trait.getEfficiency(FT_Heatable.HeatingType.ICF) <= 0) continue;
            rows.add(new Process(ModBlocks.icf, inputs(ItemFluidIcon.make(type, step.amountReq)), new ItemStack[] {ItemFluidIcon.make(step.typeProduced, step.amountProduced)},
                    "ICF coolant parcel; native heat threshold " + step.heatReq + " HU", "Flow limited by core heat, fluid inventory and outlet space",
                    "Native cooling uses at most 25% heat per tick before efficiency", "Core stored heat loses 0.1% per tick"));
        }
        cache = Collections.unmodifiableList(rows); revision = current; return cache;
    }
}
