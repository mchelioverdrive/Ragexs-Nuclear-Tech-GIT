package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.MiningConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Water;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.Groundwater;
import com.hbm.tileentity.machine.TileEntityMachinePumpBase;
import com.hbm.tileentity.machine.oil.*;
import com.hbm.tileentity.machine.oil.TileEntityOilDrillBase.ExtractionProfile;
import com.hbm.util.WeightedRandomGeneric;
import com.hbm.world.feature.BedrockOre;
import com.hbm.world.feature.BedrockOre.BedrockOreDefinition;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Geological tables use loaded config/definitions, never live chunks, drops or random sampling. */
public class ExtractionHandler extends ProcessInfoHandler {
    private static volatile List<Process> cache;
    private static long revision = -1;
    private static String[] cachedDrops;
    private static boolean cachedReplacement;
    public static void invalidate() { cache = null; }
    @Override public String getRecipeName() { return "Resource Extraction"; }
    @Override public String getOverlayIdentifier() { return "ntmExtraction"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.machine_well, ModBlocks.machine_pumpjack, ModBlocks.machine_fracking_tower, ModBlocks.machine_excavator, ModBlocks.machine_mining_laser, ModBlocks.pump_electric, ModBlocks.pump_steam}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision();
        String[] drops = MiningConfig.excavatorBedrockDrops.toArray(new String[0]);
        boolean replacement = com.hbm.config.GeneralConfig.enable528 && com.hbm.config.GeneralConfig.enable528BedrockReplacement;
        if(cache != null && revision == current && java.util.Arrays.equals(cachedDrops, drops) && cachedReplacement == replacement) return cache;
        List<Process> rows = new ArrayList<>();
        oil(rows, ModBlocks.machine_well, new TileEntityMachineOilWell());
        oil(rows, ModBlocks.machine_pumpjack, new TileEntityMachinePumpjack());
        oil(rows, ModBlocks.machine_fracking_tower, new TileEntityMachineFrackingTower());
        for(String entry : drops) {
            if(entry == null) continue;
            String[] fields = entry.split(" "); if(fields.length != 4) continue;
            try {
                Item item = (Item) Item.itemRegistry.getObject(fields[0]);
                Block block = Block.getBlockFromName(fields[0]);
                ItemStack output = item != null ? new ItemStack(item, 1, Integer.parseInt(fields[1]))
                        : block != null ? new ItemStack(block, 1, Integer.parseInt(fields[1])) : null;
                int min = Integer.parseInt(fields[2]), max = Integer.parseInt(fields[3]);
                int upper = Math.max(min, max); if(!NEISafe.isValid(output) || upper <= 0) continue;
                double chance = max > min ? Math.min(1D, max / (double) ((long) max - min + 1)) : 1D;
                rows.add(new Process(ModBlocks.machine_excavator, inputs(), new ItemStack[] {output},
                        "Ordinary bedrock; installed drill required", "Output: " + min + ".." + upper + " items per completed ring work",
                        String.format(Locale.US, "Nonzero chance: %.4f%%; independent config roll", chance * 100),
                        "Every configured material rolls; not a weighted loot pick", "Bedrock cycle: ceil(1200 / effective drill speed) ticks",
                        "Effective speed = drill speed * (1 + speed level / 2)", "Renewable bedrock; drill wear/upgrades still apply"));
            } catch(NumberFormatException ignored) { /* Native parser also ignores malformed numeric config. */ }
        }
        bedrock(rows, "Earth", BedrockOre.weightedOres);
        bedrock(rows, "Nether", BedrockOre.weightedOresNether);
        for(java.util.Map.Entry<com.hbm.dim.SolarSystem.Body, List<WeightedRandomGeneric<BedrockOreDefinition>>> entry : BedrockOre.weightedPlanetOres.entrySet())
            bedrock(rows, entry.getKey().name(), entry.getValue());
        rows.add(new Process(ModBlocks.machine_excavator, inputs(), new ItemStack[0],
                "World mining: real placed blocks, not a fixed ore pool", "Installed drill, ring radius and block hardness set cycle", "Fortune/silk/crusher upgrades change native block drops", "Blocks deplete; protected/unbreakable blocks are excluded"));
        rows.add(new Process(ModBlocks.machine_mining_laser, inputs(), new ItemStack[0],
                "Mines targeted placed blocks; no random resource pool", "Cycle depends on block hardness, speed and upgrades", "Can apply crystallizer, centrifuge, shredder or smelting upgrades", "Native block drops/IMiningDrill interactions; blocks deplete"));
        for(CelestialBody body : CelestialBody.getAllBodies()) {
            if(!body.canLand) continue;
            CBT_Water water = body.getTrait(CBT_Water.class); if(water == null || water.fluid == Fluids.NONE) continue;
            pump(rows, body.name, water.fluid, false); pump(rows, body.name, water.fluid, true);
            if(water.fluid == Fluids.SALT_WATER || water.fluid == Fluids.FRESH_WATER) {
                FluidType alternative = water.fluid == Fluids.SALT_WATER ? Fluids.FRESH_WATER : Fluids.SALT_WATER;
                pump(rows, body.name, alternative, false); pump(rows, body.name, alternative, true);
            }
        }
        cache = java.util.Collections.unmodifiableList(rows); revision = current; cachedDrops = drops; cachedReplacement = replacement; return cache;
    }
    private static void oil(List<Process> rows, Block machine, TileEntityOilDrillBase owner) {
        ExtractionProfile p = owner.getExtractionProfile();
        for(int location = 0; location < 4; location++) {
            boolean bedrock = location == 3, duna = location == 1;
            FluidType oil = location == 2 ? Fluids.OIL_DS : Fluids.OIL;
            int amount = bedrock ? p.bedrockOil : duna ? p.dunaOil : p.oil;
            int lo = bedrock ? p.bedGasMin : p.gasMin, hi = bedrock ? p.bedGasMax : p.gasMax;
            ItemStack[] output = products(oil, amount, Fluids.RAW_NATURAL_GAS, hi);
            if(output.length == 0) continue;
            rows.add(new Process(machine, p.solution > 0 ? inputs(ItemFluidIcon.make(Fluids.FRACKSOL, p.solution)) : inputs(), output,
                    (bedrock ? "Renewable bedrock" : duna ? "Duna oil deposit" : location == 2 ? "Laythe oil deposit" : "Ordinary oil deposit") + "; drill must reach source",
                    "Crude: " + amount + " L; raw gas: " + lo + ".." + hi + " standard L / extraction",
                    String.format(Locale.US, "Ceilings before delays: %.3f L/min crude; %.3f standard L/min gas", amount * 1200D / p.interval, hi * 1200D / p.interval),
                    rate(owner, p), bedrock ? "No depletion" : String.format(Locale.US, "Depletion chance: %.3f%% / success", (duna ? p.dunaDepletion : p.depletion) * 100),
                    "Needs room for both products; scan delay/upgrades may slow flow", "Afterburn upgrade can consume raw gas for auxiliary power"));
        }
        if(p.gasDeposit > 0) rows.add(new Process(machine, inputs(), products(Fluids.RAW_NATURAL_GAS, p.gasDeposit, Fluids.PETROLEUM, p.petroleumMax),
                "Natural gas deposit; finite source below the drill", "Raw gas: " + p.gasDeposit + " standard L; petroleum: " + p.petroleumMin + ".." + p.petroleumMax + " standard L / extraction",
                String.format(Locale.US, "Ceilings before delays: %.3f standard L/min gas; %.3f standard L/min petroleum", p.gasDeposit * 1200D / p.interval, p.petroleumMax * 1200D / p.interval),
                rate(owner, p), String.format(Locale.US, "Depletion chance %.3f%% / success", p.depletion * 100), "Both output tanks must have room"));
    }
    private static String rate(TileEntityOilDrillBase owner, ExtractionProfile p) {
        return String.format(Locale.US, "Extraction >= %.2f s; base electrical draw %s", p.interval / 20D, com.hbm.realsim.MachineInfo.watts(owner.getPowerReq() * 10L));
    }
    private static ItemStack[] products(FluidType a, int x, FluidType b, int y) {
        List<ItemStack> products = new ArrayList<>();
        if(x > 0) products.add(ItemFluidIcon.make(a, x)); if(y > 0) products.add(ItemFluidIcon.make(b, y));
        return products.toArray(new ItemStack[0]);
    }
    private static void bedrock(List<Process> rows, String body, List<WeightedRandomGeneric<BedrockOreDefinition>> definitions) {
        long total = 0; for(WeightedRandomGeneric<BedrockOreDefinition> entry : definitions) total += Math.max(0, entry.itemWeight);
        if(total == 0) return;
        for(WeightedRandomGeneric<BedrockOreDefinition> entry : definitions) {
            BedrockOreDefinition ore = entry.get();
            if("Earth".equals(body) && com.hbm.config.GeneralConfig.enable528 && com.hbm.config.GeneralConfig.enable528BedrockReplacement) {
                BedrockOreDefinition replacement = BedrockOre.replacements.get(ore.id);
                if(replacement != null) ore = replacement;
            }
            if(entry.itemWeight <= 0 || !NEISafe.isValid(ore.stack)) continue;
            rows.add(new Process(ModBlocks.machine_excavator, ore.acid == null ? inputs() : inputs(ItemFluidIcon.make(ore.acid)), new ItemStack[] {ore.stack},
                    body + " bedrock deposit; drill tier >= " + ore.tier,
                    String.format(Locale.US, "Deposit selection weight: %d/%d (%.3f%%)", entry.itemWeight, total, entry.itemWeight * 100D / total),
                    "Weight selects deposit at world generation, not each extraction", "Configured deposit yields shown stack per completed work cycle", "No deposit depletion in collectBedrock; acid paid each cycle", "Cycle depends on ring hardness and installed drill speed"));
        }
    }
    private static void pump(List<Process> rows, String body, FluidType fluid, boolean steam) {
        long watts = steam ? Groundwater.STEAM_SHAFT_WATTS : Groundwater.ELECTRIC_WATTS;
        int nameplate = steam ? TileEntityMachinePumpBase.steamLitersPerMinute : TileEntityMachinePumpBase.electricLitersPerMinute;
        long microliters = Groundwater.microlitersPerTick(watts, nameplate / 60D);
        if(fluid != Fluids.SALT_WATER && fluid != Fluids.FRESH_WATER) microliters /= Math.max(1, TileEntityMachinePumpBase.nonWaterDebuff);
        List<ItemStack> outputs = new ArrayList<>(); outputs.add(ItemFluidIcon.make(fluid, 1));
        if(steam) outputs.add(ItemFluidIcon.make(Fluids.SPENTSTEAM, 1));
        rows.add(new Process(steam ? ModBlocks.pump_steam : ModBlocks.pump_electric,
                steam ? inputs(ItemFluidIcon.make(Fluids.STEAM, 100)) : inputs(), outputs.toArray(new ItemStack[0]),
                body + " groundwater; Y <= " + TileEntityMachinePumpBase.groundHeight + "; valid ground footing",
                fluid == Fluids.SALT_WATER ? "Ocean biome (Forge OCEAN tag) at pump center" : fluid == Fluids.FRESH_WATER ? "Non-ocean biome at pump center" : "Planetary fluid; biome salinity does not apply",
                String.format(Locale.US, "Groundwater <= %.3f L/min; output icon is identity only", microliters * 1200D / 1000000),
                steam ? "100 steam counts -> 1 spent count; 100 kJ shaft credit" : "Electrical demand: " + com.hbm.realsim.MachineInfo.watts(watts),
                steam ? "2 kW shaft draw while operating; 30 L/min nameplate" : "No aquifer depletion; full tank pauses pumping",
                "Not available in orbit/no-sky worlds; no live-world scan"));
    }
}
