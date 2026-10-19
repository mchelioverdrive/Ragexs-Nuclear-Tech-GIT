package com.hbm.handler.nei;

import java.util.*;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.items.special.ItemBedrockOreNew;
import com.hbm.items.special.ItemBedrockOreNew.BedrockOreGrade;
import com.hbm.items.special.ItemBedrockOreNew.BedrockOreType;
import com.hbm.realsim.MachineInfoItems;
import com.hbm.tileentity.machine.TileEntityCore;
import com.hbm.tileentity.machine.TileEntityMachineDischarger;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** Environmental sources and variable world/NBT consumers have descriptive, non-transferable views. */
public class UtilityProcessHandler extends ProcessInfoHandler {
    private static List<Process> cache;
    private static long revision = -1;
    @Override public String getRecipeName() { return "Utility Processes"; }
    @Override public String getOverlayIdentifier() { return "ntmUtilities"; }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.machine_solar, ModBlocks.machine_spp_bottom, ModBlocks.machine_geo, ModBlocks.machine_radgen, ModBlocks.machine_minirtg, ModBlocks.machine_powerrtg, ModBlocks.machine_rtg_grey, ModBlocks.machine_drain, ModBlocks.machine_autosaw, ModBlocks.machine_ore_slopper, ModBlocks.machine_lpw2, ModBlocks.machine_htr3, ModBlocks.machine_htrf4, ModBlocks.machine_xenon_thruster, ModBlocks.machine_discharger, ModBlocks.dfc_core, ModBlocks.machine_drive_processor, ModBlocks.machine_keyforge, ModBlocks.machine_missile_assembly, ModBlocks.machine_rocket_assembly, ModBlocks.transporter_rocket, ModBlocks.soyuz_launcher, ModBlocks.machine_teleporter}; }
    @Override protected List<Process> processes() { return snapshot(); }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision(); if(cache != null && revision == current) return cache;
        List<Process> rows = new ArrayList<>();
        rows.add(new Process(ModBlocks.machine_teleporter, inputs(ItemFluidIcon.make(Fluids.NMASS, com.hbm.tileentity.machine.TileEntityMachineTeleporter.flucu)), new ItemStack[0],
                "Cross-dimension entity teleport; no material output", "100 L N-mass per entity across dimensions; same dimension uses none",
                "500 kJ electrical work per entity; target coordinates required"));
        rows.add(new Process(ModBlocks.soyuz_launcher, inputs(ItemFluidIcon.make(Fluids.KEROSENE, 0), ItemFluidIcon.make(Fluids.OXYGEN, 0)), new ItemStack[0],
                "Launches installed rocket; no fixed material product", "Default launch: 128,000 L kerosene AND 128,000 L oxygen",
                "Mode 1: each feed min(5,000 + horizontal target distance, 128,000) L", "Launch also requires 75% native electrical buffer and valid payload"));
        rows.add(new Process(ModBlocks.transporter_rocket, inputs(ItemFluidIcon.make(Fluids.HYDROGEN, 0), ItemFluidIcon.make(Fluids.OXYGEN, 0)), new ItemStack[0],
                "Transfers existing cargo to a linked rocket transporter; creates no resources", "Both tanks pay the same maneuver cost, capped at 64,000 L each",
                "Cost follows transferred mass, source/destination bodies, orbit and fuel ISP", "Receiver capacity and available rocket required; no fixed batch output"));
        for(Block block : new Block[] {ModBlocks.machine_solar, ModBlocks.machine_spp_bottom, ModBlocks.machine_geo, ModBlocks.machine_radgen, ModBlocks.machine_minirtg, ModBlocks.machine_powerrtg}) {
            List<String> notes = new ArrayList<>();
            notes.add("Continuous electrical source; no item/fluid product");
            MachineInfoItems.append(new ItemStack(block), notes);
            if(block == ModBlocks.machine_solar || block == ModBlocks.machine_spp_bottom) notes.add("Needs sunlight, clear sky and valid panel structure");
            if(block == ModBlocks.machine_geo) notes.add("Hot/cold world blocks provide environmental heat; layout governs output");
            if(block == ModBlocks.machine_radgen) notes.add("Requires environmental radiation; output follows native radiation sample");
            rows.add(new Process(block, inputs(), new ItemStack[0], notes.toArray(new String[0])));
        }
        for(ItemRTGPellet pellet : ItemRTGPellet.pelletList) {
            ItemStack source = new ItemStack(pellet);
            rows.add(new Process(ModBlocks.machine_rtg_grey, inputs(source), new ItemStack[0],
                    "Isotope source supplies heat continuously; not consumed each tick",
                    "Fresh thermal power: " + com.hbm.realsim.IsotopeLedger.freshThermalWatts(pellet) + " Wth",
                    "5% thermoelectric conversion; native RTG nameplate also limits output",
                    "Decay and retained source age determine actual output", "When decay is enabled, exhausted products have their own recipe view"));
        }
        for(FluidType type : Fluids.getInNiceOrder()) {
            if(type == Fluids.NONE) continue;
            rows.add(new Process(ModBlocks.machine_drain, inputs(ItemFluidIcon.make(type, 0)), new ItemStack[0],
                    "Disposes configured tank fluid; no recovered product", "Each tick spills max(1, floor(current tank fill / 2)) L",
                    "Pollution and gas release depend on fluid traits; amount varies with fill"));
        }
        rows.add(new Process(ModBlocks.machine_autosaw, inputs(), new ItemStack[0], "Harvests actual placed trees; no random resource pool",
                "World blocks, tool state and inventory space govern drops", "No world is sampled by this NEI view; use Sawmill for log processing"));
        for(BedrockOreType type : BedrockOreType.values()) rows.add(new Process(ModBlocks.machine_ore_slopper,
                inputs(new ItemStack(ModItems.bedrock_ore_base), ItemFluidIcon.make(Fluids.SALT_WATER, 5)),
                new ItemStack[] {ItemBedrockOreNew.make(BedrockOreGrade.BASE, type), ItemFluidIcon.make(Fluids.SLOP, 5)},
                "Legacy NBT ore slurry; solid output icon identifies one possible material",
                "Amount = ore content in input NBT * (1 + efficiency level * 0.1)", "Fractions accumulate; no guaranteed one-item yield per input",
                "600 - 150 * speed level ticks; efficiency affects solid recovery", "Water -> slop: 5 L / consumed ore item"));
        rows.add(new Process(ModBlocks.machine_lpw2, inputs(ItemFluidIcon.make(Fluids.KEROSENE_REFORM, 0), ItemFluidIcon.make(Fluids.OXYGEN, 0)), new ItemStack[0],
                "Orbital propulsion; consumes equal quantities from both tanks", "Quantity per burn: SolarSystem fuel-cost formula (ship mass, delta-v, ISP)",
                "No fixed L/min or item output; must face prograde in orbit"));
        rows.add(new Process(ModBlocks.machine_xenon_thruster, inputs(ItemFluidIcon.make(Fluids.XENON, 0)), new ItemStack[0],
                "Orbital propulsion; xenon amount follows ship mass, delta-v and ISP", "Electrical work: 2.5 kJ per L burned; 200 tick prewarm",
                "No fixed L/min; requires prograde orientation in orbit"));
        rows.add(new Process(ModBlocks.machine_drive_processor, inputs(new ItemStack(ModItems.full_drive), new ItemStack(ModItems.hard_drive)), new ItemStack[0],
                "Processes data NBT on the installed full drive; not a fixed item recipe", "100 ticks at 2 kW; circuit tier must meet drive processing tier",
                "Requires >= 75% energy buffer; power failure resets progress", "Copy action replaces one empty drive with a copy of the full drive"));
        rows.add(new Process(ModBlocks.machine_keyforge, inputs(), new ItemStack[0], "Copies key pins between compatible keys or randomizes transferable pins",
                "Key identity is player-selected NBT; no fixed output key", "Native forge also converts inserted 4-gauge ammunition to QUACK variant"));
        rows.add(new Process(ModBlocks.machine_missile_assembly, inputs(), new ItemStack[0], "Assembles a custom missile from compatible native component items",
                "Chip, warhead, fuselage, optional fins and thruster determine output NBT", "Consumes supplied components; output slot must be empty", "Use assembly GUI validation; no single fixed missile recipe"));
        rows.add(new Process(ModBlocks.machine_rocket_assembly, inputs(), new ItemStack[0], "Assembles/disassembles a custom staged rocket from native parts",
                "Capsule and stage component counts determine output NBT", "Consumes supplied components; disassembly returns installed parts", "Native size/part compatibility checks apply; no fixed rocket recipe"));
        for(Block block : new Block[] {ModBlocks.machine_htr3, ModBlocks.machine_htrf4}) rows.add(new Process(block, inputs(), new ItemStack[0],
                "Orbital propulsion; consumes configured tank contents per maneuver", "Quantity follows ship mass, delta-v and configured fluid ISP (fallback 300)",
                "No fixed batch or L/min; native tank configuration must admit the feed",
                block == ModBlocks.machine_htrf4 ? "Additional electrical work: 500 kJ per L burned" : "Requires prograde orientation in orbit"));
        List<ItemStack> discharges = new ArrayList<>(new com.hbm.inventory.RecipesCommon.OreDictStack(com.hbm.inventory.OreDictManager.U233.ingot()).extractForNEI());
        discharges.add(new ItemStack(ModItems.ingot_electronium));
        for(ItemStack input : discharges) {
            long energy = TileEntityMachineDischarger.conversionQuanta(input); if(energy <= 0) continue;
            input = input.copy(); input.stackSize = 1;
            rows.add(new Process(ModBlocks.machine_discharger, inputs(input), new ItemStack[] {new ItemStack(input.getItem() == ModItems.ingot_electronium ? ModItems.ingot_dineutronium : ModItems.ingot_titanium)},
                    "100 ticks conversion; must cool to <= 20 C before starting", "Electrical stock added: " + api.hbm.energymk2.EnergyUnits.formatJoules(energy),
                    "Completion heats machine to 2,000 C; cooldown limits sustained rate"));
        }
        TileEntityCore core = new TileEntityCore();
        for(FluidType type : Fluids.getInNiceOrder()) if(core.getFuelEfficiency(type) > 0)
            rows.add(new Process(ModBlocks.dfc_core, inputs(ItemFluidIcon.make(type, 0)), new ItemStack[0],
                    "DFC fuel in either core tank; both tanks require eligible fuel", "Per emitter call each tank pays ceil(native drive quantity / 1000) L",
                    "Catalysts and installed core required; drive and core set output", "Native drive units are not a calibrated SI energy recipe",
                    "No fixed L/min or direct item product; electrical recovery needs receiver"));
        cache = Collections.unmodifiableList(rows); revision = current; return cache;
    }
}
