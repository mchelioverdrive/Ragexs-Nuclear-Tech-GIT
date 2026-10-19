package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.List;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.*;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.tileentity.machine.TileEntityMoltenSaltReactor;
import com.hbm.tileentity.machine.TileEntitySolarBoiler;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** Environmental and specialist fluid paths which have no conventional crafting recipe. */
public class ResourceProcessHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Resource Processes"; }
    @Override public String getOverlayIdentifier() { return "ntmResourceProcesses"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.algae_film, ModBlocks.air_vent, ModBlocks.air_scrubber, ModBlocks.gas_dock, ModBlocks.machine_molten_salt_reactor, ModBlocks.machine_solar_boiler, ModBlocks.machine_hephaestus, ModBlocks.pwr_controller, ModBlocks.watz, ModBlocks.rbmk_boiler, ModBlocks.rbmk_heater, ModBlocks.rbmk_cooler, ModBlocks.rbmk_burner, ModBlocks.reactor_zirnox, ModBlocks.plasma_heater, ModBlocks.machine_schrabidium_transmutator}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        rows.add(new Process(ModBlocks.algae_film, inputs(icon(Fluids.CARBONDIOXIDE, 1)), new ItemStack[] {icon(Fluids.OXYGEN_GAS, 1)},
                "Orbit worlds only; 50% conversion chance each tick", "1 standard L CO2 -> 1 standard L O2 on success", "Mean 600 L/min with continuous feed and output room"));
        rows.add(new Process(ModBlocks.air_vent, inputs(icon(Fluids.OXYGEN_GAS, 0)), new ItemStack[0],
                "Fills a sealed atmosphere blob; oxygen is not bottled output", "Needs >= 20 L tank reserve; fills up to 20 L/tick", "Steady upkeep: random 50% chance to consume 1 L/tick", "Lost/unsealed blob consumes 20 L/tick until recovery"));
        rows.add(new Process(ModBlocks.air_scrubber, inputs(), new ItemStack[] {icon(Fluids.CARBONDIOXIDE, 1)},
                "Captures CO2 only when linked air pump requests scrubbing", "5 J electrical work per standard L captured", "Rate depends on atmosphere upkeep; output room required"));
        rows.add(new Process(ModBlocks.gas_dock, inputs(icon(Fluids.HYDROGEN, 100), icon(Fluids.OXYGEN, 500)), new ItemStack[] {icon(Fluids.JOOLGAS, 8000)},
                "Jool gas collection; rocket must complete launch/return", "100 L hydrogen + 500 L oxygen -> 8,000 gas units", "Repeated collection while rocket is deployed; tank space required", "Operating interval follows native rocket state, not a fixed recipe"));
        rows.add(new Process(ModBlocks.machine_molten_salt_reactor, inputs(icon(Fluids.THORIUM_SALT, TileEntityMoltenSaltReactor.SALT_PER_TICK)),
                new ItemStack[] {icon(Fluids.THORIUM_SALT_HOT, TileEntityMoltenSaltReactor.SALT_PER_TICK)},
                "Continuous salt heating: up to " + TileEntityMoltenSaltReactor.SALT_PER_TICK * 1200 + " L/min", "Requires input and hot-salt outlet room", "Shielding changes corrosion/radiation; corrosion can leak salt"));
        solarBoiler(rows, ModBlocks.machine_solar_boiler, TileEntitySolarBoiler.RATED_THERMAL_WATTS, "Tracked mirrors and sunlight; 500 kJ startup heat");
        solarBoiler(rows, ModBlocks.machine_hephaestus, com.hbm.tileentity.machine.TileEntityMachineHephaestus.RATED_THERMAL_WATTS,
                "Lava/volcanic heat below machine; 2 MJ startup heat");
        for(ProcessRecipe bill : PetroleumProcesses.defaults("heat").values()) {
            List<ItemStack> outputs = new ArrayList<>();
            for(int i = 0; i < bill.outputs.length; i++) outputs.add(ItemFluidIcon.makePhysical(new FluidStack(Fluids.fromName(bill.outputs[i]), 1), bill.productMass[i]));
            rows.add(new Process(ModBlocks.machine_hephaestus, inputs(icon(Fluids.fromName(bill.inputs[0]), bill.quantities[0])), outputs.toArray(new ItemStack[0]),
                    "Geothermal crude heating; lava/volcanic sources required", "Duration: " + bill.duration / 20D + " s; heat " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.heatQuanta),
                    "Process work: " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.workQuanta),
                    "Warmup and available geothermal heat limit throughput"));
        }
        for(FluidType type : Fluids.getInNiceOrder()) {
            if(type.hasTrait(com.hbm.inventory.fluid.trait.FT_Flammable.class)) rows.add(new Process(ModBlocks.rbmk_burner,
                    inputs(icon(type, 0)), new ItemStack[0], "RBMK burner: up to 20 L / column step; requires air and > 19 C",
                    "Heat = legacy burner budget * fluid consumed / 1,000,000 HU", "Column cadence follows native RBMK simulation; no SI thermal rating"));
            if(!type.hasTrait(FT_Heatable.class)) continue;
            FT_Heatable trait = type.getTrait(FT_Heatable.class);
            FT_Heatable.HeatingStep step = trait.getFirstStep();
            if(step == null || step.typeProduced == null || step.amountReq <= 0 || step.amountProduced <= 0) continue;
            if(trait.getEfficiency(HeatingType.HEATEXCHANGER) > 0) rows.add(new Process(ModBlocks.rbmk_heater,
                    inputs(icon(type, step.amountReq)), new ItemStack[] {icon(step.typeProduced, step.amountProduced)},
                    "RBMK heater parcel; column must exceed outlet temperature", "Native threshold: " + step.heatReq + " HU; column heat and outlet space govern rate",
                    "Native exchanger efficiency and column cadence apply; no fixed L/min"));
            if(trait.getEfficiency(HeatingType.PWR) > 0) rows.add(new Process(ModBlocks.pwr_controller, inputs(icon(type, step.amountReq)), new ItemStack[] {icon(step.typeProduced, step.amountProduced)},
                    "PWR primary coolant parcel; requires active reactor core", "Flow depends on hull heat, rod/heatsink count and channels", "Native heat threshold per parcel: " + step.heatReq + " HU (not SI joules)"));
            if(type == Fluids.HELIUM4) rows.add(new Process(ModBlocks.watz, inputs(icon(type, step.amountReq)), new ItemStack[] {icon(step.typeProduced, step.amountProduced)},
                    "WATZ helium cooling; requires fueled active stack", "Flow follows reactor heat and coolant inventory", "Native heat threshold per parcel: " + step.heatReq + " HU (not SI joules)"));
            if(SteamLedger.state(type.getName()) == null && !PetroleumProcesses.defaults("heat").containsKey(type.getName()) && trait.getEfficiency(HeatingType.HEATEXCHANGER) > 0)
                rows.add(new Process(ModBlocks.machine_hephaestus, inputs(icon(type, step.amountReq)), new ItemStack[] {icon(step.typeProduced, step.amountProduced)},
                        "Geothermal trait heating; lava/volcanic heat required", "Rate follows native geothermal heat and parcel threshold", "Native heat threshold per parcel: " + step.heatReq + " HU (not SI joules)"));
        }
        rows.add(new Process(ModBlocks.rbmk_cooler, inputs(icon(Fluids.CRYOGEL, 0)), new ItemStack[0],
                "RBMK emergency cooling above 750 C; no recovered fluid", "Per column step consumes min(floor(heat - 750), cryogel inventory) L",
                "Removes one native heat unit per L; no fixed throughput"));
        // RBMK uses its own native boiler formula, not the SI SteamLedger conversion.
        for(FluidType steam : new FluidType[] {Fluids.STEAM, Fluids.HOTSTEAM, Fluids.SUPERHOTSTEAM, Fluids.ULTRAHOTSTEAM}) {
            int factor = (int) com.hbm.tileentity.machine.rbmk.TileEntityRBMKBoiler.getFactorFromSteam(steam);
            int feed = Math.max(1, factor / 100), product = Math.max(1, 100 / factor);
            rows.add(new Process(ModBlocks.rbmk_boiler, inputs(icon(Fluids.LIGHT_WATER, feed)), new ItemStack[] {icon(steam, product)},
                    "RBMK boiler native conversion ratio", "Column heat above steam temperature; world boiler dials apply", "Flow and heat cost vary with column heat and world settings", "Native whole-count rounding can alter very small operations"));
        }
        rows.add(new Process(ModBlocks.reactor_zirnox, inputs(icon(Fluids.LIGHT_WATER, 1), icon(Fluids.CARBONDIOXIDE, 0)), new ItemStack[] {icon(Fluids.SUPERHOTSTEAM, 1)},
                "ZIRNOX secondary water -> superhot steam: 1:1 native counts", "CO2 is primary circuit inventory; not consumed per steam parcel", "Steam starts at 300 C; maximum 55 counts/tick at >= 450 C", "Fuel rods, primary heat, controls and outlet space govern production", "Shutdown cooling consumes water without a recoverable output"));
        FluidType[][] plasma = {{Fluids.DEUTERIUM, Fluids.TRITIUM, Fluids.PLASMA_DT}, {Fluids.DEUTERIUM, Fluids.HELIUM3, Fluids.PLASMA_DH3},
                {Fluids.DEUTERIUM, Fluids.HYDROGEN, Fluids.PLASMA_HD}, {Fluids.HYDROGEN, Fluids.TRITIUM, Fluids.PLASMA_HT},
                {Fluids.HELIUM4, Fluids.OXYGEN, Fluids.PLASMA_XM}, {Fluids.BALEFIRE, Fluids.AMAT, Fluids.PLASMA_BF}};
        for(FluidType[] pair : plasma) rows.add(new Process(ModBlocks.plasma_heater, inputs(icon(pair[0], 1), icon(pair[1], 1)), new ItemStack[] {icon(pair[2], 1)},
                "1 native packet each feed -> 1 unstable plasma packet", "250 kJ startup; 25 kJ per packet; <= 1 packet/tick", "Containment 100 kW; unsupported plasma dissipates after 5 ticks", "Requires positioned ITER or HTRF injector target"));
        rows.add(new Process(ModBlocks.machine_schrabidium_transmutator, new ItemStack[][] {new com.hbm.inventory.RecipesCommon.OreDictStack(com.hbm.inventory.OreDictManager.U.ingot()).extractForNEI().toArray(new ItemStack[0]),
                {new ItemStack(ModItems.redcoil_capacitor), new ItemStack(ModItems.euphemium_capacitor)}},
                new ItemStack[] {new ItemStack(com.hbm.config.VersatileConfig.getTransmutatorItem())},
                "600 ticks; uranium ingot and capacitor hardware", "Requires >= 2.495 MJ buffer; completion drains stored energy", "Redcoil capacitor takes one damage; euphemium capacitor retained"));
        cache = java.util.Collections.unmodifiableList(rows); revision = current; return cache;
    }
    private static ItemStack icon(FluidType type, int amount) { return ItemFluidIcon.make(type, amount); }
    private static void solarBoiler(List<Process> rows, Block machine, long watts, String condition) {
        SteamLedger.Transition bill = SteamLedger.transition(State.COLD, State.ORDINARY, SteamLedger.Route.BOILER, 950000);
        rows.add(new Process(machine, inputs(SteamCycleHandler.parcel(State.COLD)), new ItemStack[] {SteamCycleHandler.parcel(State.ORDINARY)},
                "1 kg feed -> 1 kg steam; fractional output retained", "Heat required: " + SteamCycleHandler.energy(bill.heatJPerKg) + "/kg",
                "Rated thermal ceiling: " + MachineInfo.thermalWatts(watts), condition, "Flow depends on available environmental heat"));
    }
}
