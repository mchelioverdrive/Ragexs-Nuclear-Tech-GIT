package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemPistons.EnumPistonType;
import com.hbm.realsim.*;
import com.hbm.tileentity.machine.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** Fuel eligibility comes from the same policies as native admission, not from LHV alone. */
public class FuelUseHandler extends ProcessInfoHandler {
    private static volatile List<Process> cache;
    private static long revision = -1;
    public static void invalidate() { cache = null; }
    @Override public String getRecipeName() { return "Fuels, engines and process heat"; }
    @Override public String getOverlayIdentifier() { return "ntmFuelUses"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.heater_firebox, ModBlocks.heater_oven, ModBlocks.heater_oilburner, ModBlocks.heater_electric, ModBlocks.machine_wood_burner, ModBlocks.machine_diesel, ModBlocks.machine_combustion_engine, ModBlocks.machine_turbinegas, ModBlocks.machine_turbofan, ModBlocks.machine_stirling, ModBlocks.machine_stirling_steel, ModBlocks.machine_industrial_boiler, ModBlocks.machine_flare, ModBlocks.furnace_iron, ModBlocks.machine_furnace_brick_off, ModBlocks.machine_press, ModBlocks.machine_nuke_furnace_off, ModBlocks.machine_rtg_furnace_off, ModBlocks.machine_difurnace_rtg_off}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision();
        if(cache != null && revision == current) return cache;
        revision = current;
        List<Process> rows = new ArrayList<>();
        legacyFurnaceFuels(rows);
        for(FluidType type : Fluids.getInNiceOrder()) {
            if(type.hasTrait(com.hbm.inventory.fluid.trait.FT_Gaseous.class) || type.hasTrait(com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous_ART.class)
                    || type.hasTrait(com.hbm.inventory.fluid.trait.FT_Flammable.class))
                rows.add(new Process(ModBlocks.machine_flare, inputs(ItemFluidIcon.make(type, 1)), new ItemStack[0],
                        "Disposal, not a generator; no recovered fluid or power", "Gas vent: up to 60,000 standard L/min base (4x with upgrades)",
                        "Flammable burn: up to 12,000 L/min base (4x with upgrades)", "Burning needs breathable air; gas can vent when burning fails", "Actual flow limited by inventory; releases pollution"));
            if(!Combustion.isBurnerFuel(type)) continue;
            if(type == Fluids.HEAVYOIL || type == Fluids.HEAVYOIL_VACUUM || type == Fluids.HEATINGOIL || type == Fluids.HEATINGOIL_VACUUM)
                fuel(rows, ModBlocks.machine_industrial_boiler, type, 12000000L, 850000, true, "Internal firing requires air, boiler feed and outlet space");
            fuel(rows, ModBlocks.heater_oilburner, type, TileEntityHeaterOilburner.ratedThermalWatts, Combustion.FIREBOX_EFFICIENCY_PPM, true, "Switch on; firing setting and heat demand govern consumption");
            fuel(rows, ModBlocks.machine_wood_burner, type, TileEntityMachineWoodBurner.ratedPowerWatts, TileEntityMachineWoodBurner.electricalEfficiencyPpm, false, "Liquid firing mode; requires breathable atmosphere");
            if(Combustion.dieselEfficiency(type) > 0) fuel(rows, ModBlocks.machine_diesel, type, TileEntityMachineDiesel.ratedPowerWatts, Combustion.dieselEfficiency(type), false, "Breathable atmosphere; idle firing and load response apply");
            if(Combustion.isReciprocatingFuel(type)) for(EnumPistonType piston : EnumPistonType.values()) {
                int efficiency = piston.getElectricalEfficiencyPpm(type.getTrait(FT_Combustible.class).getGrade());
                if(efficiency <= 0) continue;
                rows.add(new Process(ModBlocks.machine_combustion_engine,
                        inputs(ItemFluidIcon.make(type, 1), new ItemStack(ModItems.piston_set, 1, piston.ordinal())), new ItemStack[0],
                        "Fuel parcel: 1 L; piston set is retained hardware", fuelNote(type, efficiency),
                        rate(type, TileEntityMachineCombustionEngine.ratedPowerWatts, efficiency, false), "Requires breathable air; idle firing and governor response apply"));
            }
            if(TurbineFuelAccount.suitable(type.getName())) {
                rows.add(new Process(ModBlocks.machine_turbinegas, inputs(ItemFluidIcon.make(type, 1), ItemFluidIcon.make(Fluids.LUBRICANT, 0)), new ItemStack[0],
                        "Fuel parcel: 1 standard L; lubricant is a separate utility", fuelNote(type, TileEntityMachineTurbineGas.ELECTRICAL_EFFICIENCY_PPM),
                        rate(type, TileEntityMachineTurbineGas.RATED_ELECTRIC_WATTS, TileEntityMachineTurbineGas.ELECTRICAL_EFFICIENCY_PPM, false),
                        "Lubricant: 0.06 L/min while firing; paid 29 s startup", "Up to 55% LHV enters exhaust; optional HRSG consumes water"));
            }
            if(FuelSuitability.has(type.getName(), MaterialRole.AVIATION_FUEL) && type.hasTrait(FT_Combustible.class)
                    && type.getTrait(FT_Combustible.class).getGrade() == FT_Combustible.FuelGrade.AERO)
                fuel(rows, ModBlocks.machine_turbofan, type, 1000000, 250000, false, "Base mode; afterburners reduce efficiency to 25% / (1 + level)");
        }
        for(ItemStack fuel : NEISafe.solidFuelCandidates()) {
            long chemical = Combustion.solidChemicalMicrojoules(fuel);
            if(chemical <= 0) continue;
            boolean duplicate = false;
            for(Process row : rows) if(row.machine.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.heater_firebox)
                    && NEISafe.matchesRecipeStack(row.inputs[0][0], fuel)) { duplicate = true; break; }
            if(duplicate) continue;
            fuel = fuel.copy(); fuel.stackSize = 1;
            for(Block block : new Block[] {ModBlocks.heater_firebox, ModBlocks.heater_oven, ModBlocks.machine_wood_burner, ModBlocks.machine_industrial_boiler}) {
                int efficiency = block == ModBlocks.machine_wood_burner ? TileEntityMachineWoodBurner.electricalEfficiencyPpm : Combustion.FIREBOX_EFFICIENCY_PPM;
                long useful = java.math.BigInteger.valueOf(chemical).multiply(java.math.BigInteger.valueOf(efficiency)).divide(java.math.BigInteger.valueOf(500000000000L)).longValueExact();
                rows.add(new Process(block, inputs(fuel), new ItemStack[0],
                        "One whole fuel item; unused energy is retained", "Useful " + (block == ModBlocks.machine_wood_burner ? "electrical" : "heat") + " energy: " + api.hbm.energymk2.EnergyUnits.formatJoules(useful),
                        "Requires oxygen/air and output demand; ash accumulates", "Fuel heat and electrical output are separate quantities"));
            }
        }
        for(Block block : new Block[] {ModBlocks.machine_stirling, ModBlocks.machine_stirling_steel}) {
            TileEntityStirling engine = new TileEntityStirling(block);
            rows.add(new Process(block, inputs(), new ItemStack[0],
                    "External heat -> electrical work; requires installed cog", "Heat intake: " + MachineInfo.thermalWatts(engine.getRatedHeatInputWatts()),
                    "Electrical ceiling: " + MachineInfo.watts(engine.getRatedOutputWatts()),
                    String.format(Locale.US, "Efficiency <= %.1f%% and Carnot temperature limit", engine.getConfiguredEfficiencyPpm() / 10000D),
                    "Delivered output varies with source temperature and load"));
        }
        rows.add(new Process(ModBlocks.heater_electric, inputs(), new ItemStack[0], "Electricity -> process heat", "Set target temperature; heat delivery is demand limited", "Ratings and temperature: hover the machine"));
        cache = java.util.Collections.unmodifiableList(rows); return cache;
    }
    private static void fuel(List<Process> rows, Block machine, FluidType type, long watts, int efficiency, boolean heat, String conditions) {
        rows.add(new Process(machine, inputs(ItemFluidIcon.make(type, 1)), new ItemStack[0],
                "Fuel parcel: 1 L (standard L for gases)", fuelNote(type, efficiency), rate(type, watts, efficiency, heat), conditions,
                "Rated full-load values; partial load/startup changes consumption"));
    }
    private static void legacyFurnaceFuels(List<Process> rows) {
        TileEntityFurnaceIron iron = new TileEntityFurnaceIron();
        List<ItemStack> candidates = NEISafe.solidFuelCandidates();
        // Registry/declared metadata candidates, not live inventories or creative rendering callbacks.
        for(Object item : net.minecraft.item.Item.itemRegistry) candidates.add(new ItemStack((net.minecraft.item.Item) item));
        for(String name : net.minecraftforge.oredict.OreDictionary.getOreNames())
            candidates.addAll(NEISafe.copyValid(net.minecraftforge.oredict.OreDictionary.getOres(name)));
        java.util.Map<net.minecraft.item.Item, java.util.Set<Integer>> seen = new java.util.IdentityHashMap<>();
        for(ItemStack fuel : candidates) {
            if(!NEISafe.isValid(fuel)) continue;
            fuel = fuel.copy(); fuel.stackSize = 1;
            if(fuel.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) fuel.setItemDamage(0);
            if(!seen.computeIfAbsent(fuel.getItem(), ignored -> new java.util.HashSet<>()).add(fuel.getItemDamage())) continue;
            int vanilla = net.minecraft.tileentity.TileEntityFurnace.getItemBurnTime(fuel), modified = iron.burnModule.getBurnTime(fuel);
            if(modified > 0) rows.add(new Process(ModBlocks.furnace_iron, inputs(fuel), new ItemStack[0],
                    "One fuel item supplies " + modified + " native burn-credit ticks", "Native iron-furnace material/time modifiers apply",
                    "Loaded smelting recipes are shown separately; no fixed items per fuel"));
            if(vanilla > 0) for(Block block : new Block[] {ModBlocks.machine_furnace_brick_off, ModBlocks.machine_press})
                rows.add(new Process(block, inputs(fuel), new ItemStack[0],
                        "One fuel item supplies " + vanilla + " native burn-credit ticks", "Processing rate follows the machine, stock and upgrades",
                        "Fuel containers follow the native fuel-slot return behavior"));
        }
        for(java.util.Map.Entry<com.hbm.inventory.RecipesCommon.ComparableStack, Integer> entry : TileEntityNukeFurnace.getFuelDefinitions().entrySet())
            rows.add(new Process(ModBlocks.machine_nuke_furnace_off, inputs(entry.getKey().toStack()), new ItemStack[0],
                    "One nuclear fuel item buys " + entry.getValue() + " smelting operations", "25 ticks per operation; no fuel charge paid per idle tick",
                    "Fuel item's native container is returned; recipes are shown separately"));
        for(com.hbm.items.machine.ItemRTGPellet pellet : com.hbm.items.machine.ItemRTGPellet.pelletList)
            for(Block block : new Block[] {ModBlocks.machine_rtg_furnace_off, ModBlocks.machine_difurnace_rtg_off})
                rows.add(new Process(block, inputs(new ItemStack(pellet)), new ItemStack[0],
                        "Retained isotope source supplies native furnace heating", "Pellet heat, source age and loaded material recipe govern progress",
                        "Source is not consumed per smelt; decay configuration still applies"));
    }
    private static String fuelNote(FluidType type, int efficiency) {
        long useful = java.math.BigInteger.valueOf(type.getPhysicalState().chemicalMicrojoulesPerUnit()).multiply(java.math.BigInteger.valueOf(efficiency))
                .divide(java.math.BigInteger.valueOf(500000000000L)).longValueExact();
        return String.format(Locale.US, "%.1f%% LHV; %s useful / L", efficiency / 10000D, api.hbm.energymk2.EnergyUnits.formatJoules(useful));
    }
    private static String rate(FluidType type, long watts, int efficiency, boolean heat) {
        double liters = watts * 60D * 1e12D / (type.getPhysicalState().chemicalMicrojoulesPerUnit() * (double) efficiency);
        return String.format(Locale.US, "%s rated; %.3f L/min at full load", heat ? MachineInfo.thermalWatts(watts) : MachineInfo.watts(watts), liters);
    }
}
