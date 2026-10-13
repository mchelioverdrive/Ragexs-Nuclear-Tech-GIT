package com.hbm.realsim;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import org.junit.Test;

/** Tests the production bills and ownership account, including binary save/load and JSON boundaries. */
public class PetroleumAccountingTest {
    private List<ProcessRecipe> bills() {
        List<ProcessRecipe> result = new ArrayList<ProcessRecipe>();
        for(String machine : new String[] {"heat", "refinery", "fraction", "vacuum", "crack", "hydro", "reform", "coker", "alkyl"}) result.addAll(PetroleumProcesses.defaults(machine).values());
        result.add(ChemicalFuelProcesses.methanol()); result.add(ChemicalFuelProcesses.syntheticGasoline());
        result.add(ChemicalFuelProcesses.coalLiquid("COALGAS")); result.add(ChemicalFuelProcesses.coalLiquid("COALOIL"));
        result.add(ChemicalFuelProcesses.gasification()); result.add(ChemicalFuelProcesses.methaneReforming());
        result.add(ChemicalFuelProcesses.gasRecovery()); result.add(ChemicalFuelProcesses.sweetening()); result.add(ChemicalFuelProcesses.sulfurRecovery());
        result.add(ChemicalFuelProcesses.digestion()); result.add(ChemicalFuelProcesses.fermentation());
        result.add(ChemicalFuelProcesses.oilExtraction(true)); result.add(ChemicalFuelProcesses.oilExtraction(false)); result.add(ChemicalFuelProcesses.woodPyrolysis());
        result.add(ChemicalFuelProcesses.oilSand()); result.add(ChemicalFuelProcesses.additive());
        for(String feed : new String[] {"COAL_ITEM", "COAL_BRIQUETTE_ITEM", "LIGNITE_ITEM", "LIGNITE_BRIQUETTE_ITEM"})
            result.add(ChemicalFuelProcesses.cokeOven(feed));
        for(String feed : new String[] {"WOOD_LOG", "SAPLING_ITEM", "WOOD_BRIQUETTE_ITEM"}) result.add(ChemicalFuelProcesses.woodCokeOven(feed));
        for(String feed : new String[] {"TAR_CRUDE_ITEM", "TAR_CRACK_ITEM", "TAR_COAL_ITEM", "TAR_WOOD_ITEM"})
            result.add(ChemicalFuelProcesses.tarCoking(feed));
        result.add(ChemicalFuelProcesses.reactorFraction()); result.add(ChemicalFuelProcesses.bitumenFreezing());
        result.add(ChemicalFuelProcesses.reclaimed());
        for(String[] pair : new String[][] {{"TAR_CRUDE_ITEM", "BITUMEN"}, {"TAR_COAL_ITEM", "COALCREOSOTE"},
                {"TAR_WOOD_ITEM", "WOODOIL"}, {"TAR_CRACK_ITEM", "CRACKOIL"}})
            result.add(ChemicalFuelProcesses.tarMelting(pair[0], pair[1]));
        result.add(ChemicalFuelProcesses.blend("PETROIL", "RECLAIMED", 4, "LUBRICANT", 1));
        result.add(ChemicalFuelProcesses.blend("BIOFUEL", "SUNFLOWEROIL", 4, "ETHANOL", 1));
        result.add(ChemicalFuelProcesses.blend("BIOFUEL", "FISHOIL", 4, "ETHANOL", 1));
        result.add(ChemicalFuelProcesses.blend("DIESEL_REFORM", "DIESEL", 9, "REFORMATE", 1));
        result.add(ChemicalFuelProcesses.blend("DIESEL_CRACK_REFORM", "DIESEL_CRACK", 9, "REFORMATE", 1));
        result.add(ChemicalFuelProcesses.blend("KEROSENE_REFORM", "KEROSENE", 9, "REFORMATE", 1));
        for(String fuel : new String[] {"PETROIL", "GASOLINE", "COALGAS"}) result.add(ChemicalFuelProcesses.leaded(fuel));
        return result;
    }
    private NBTTagCompound roundTrip(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag, output);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(output.toByteArray()));
    }
    private void paidTick(ProcessBatch account, long tick) {
        assertTrue(account.canAdvance(tick, account.nextWork(), account.nextHeat())); account.advance(tick);
    }
    @Test public void everyProductionBillClosesMassAndChemicalEnergy() {
        for(ProcessRecipe recipe : bills()) {
            long out = 0; BigDecimal input = BigDecimal.ZERO, output = BigDecimal.ZERO;
            for(int i = 0; i < recipe.inputs.length; i++) input = input.add(ProcessRecipe.energy(recipe.inputs[i], recipe.quantities[i] * PhysicalRegistry.get(recipe.inputs[i]).massUnits));
            if(recipe.solidCount > 0) input = input.add(ProcessRecipe.energy(recipe.solidInput, recipe.solidCount * PhysicalRegistry.get(recipe.solidInput).massUnits));
            for(int i = 0; i < recipe.outputs.length; i++) { out = Math.addExact(out, recipe.productMass[i]); output = output.add(ProcessRecipe.energy(recipe.outputs[i], recipe.productMass[i])); }
            assertEquals(recipe.id, recipe.inputMass(), out);
            if(recipe.separation) assertTrue(recipe.id, output.subtract(input).abs().compareTo(new BigDecimal("0.000001")) <= 0);
            else assertTrue(recipe.id, output.compareTo(input.add(BigDecimal.valueOf((recipe.workQuanta + recipe.heatQuanta) / 2)).add(new BigDecimal("0.000001"))) <= 0);
            assertTrue(recipe.id, recipe.rejectedJ.compareTo(new BigDecimal("-0.000001")) >= 0);
            assertTrue(recipe.id, recipe.chemicalInputJ.add(recipe.sensibleInputJ)
                    .add(BigDecimal.valueOf((recipe.workQuanta + recipe.heatQuanta) / 2))
                    .subtract(recipe.chemicalOutputJ).subtract(recipe.sensibleOutputJ)
                    .subtract(recipe.rejectedJ).abs().compareTo(new BigDecimal("0.000001")) <= 0);
        }
    }
    @Test public void thousandBatchesRetainEveryFractionAcrossNbtAndSpeciesSwitches() throws Exception {
        MassRemainder account = new MassRemainder();
        java.util.Map<String, Long> expected = new java.util.HashMap<String, Long>(), exported = new java.util.HashMap<String, Long>();
        for(int cycle = 0; cycle < 31; cycle++) for(ProcessRecipe recipe : bills()) {
            for(int i = 0; i < recipe.outputs.length; i++) {
                String name = recipe.outputs[i]; long mass = recipe.productMass[i];
                expected.put(name, (expected.containsKey(name) ? expected.get(name) : 0L) + mass);
                long removed = (long) account.convert(name, mass) * PhysicalRegistry.get(name).massUnits;
                exported.put(name, (exported.containsKey(name) ? exported.get(name) : 0L) + removed);
                assertEquals(name, expected.get(name).longValue(), exported.get(name) + account.remaining(name));
            }
            NBTTagCompound tag = new NBTTagCompound(); account.write(tag); account = new MassRemainder(); account.read(roundTrip(tag));
        }
    }
    @Test public void unpaidAndUnownedBatchesCannotOutput() {
        ProcessBatch account = new ProcessBatch();
        try { account.finish(); fail(); } catch(IllegalStateException expected) { }
        account.reserve(ChemicalFuelProcesses.methanol());
        assertFalse(account.canAdvance(0, account.nextWork() - 1, account.nextHeat()));
        assertFalse(account.canAdvance(0, account.nextWork(), account.nextHeat() - 1));
        try { account.finish(); fail(); } catch(IllegalStateException expected) { }
        assertEquals(0, account.products.remaining("METHANOL"));
    }
    @Test public void partialPaidBatchSurvivesUnloadAndRecipeChangeWithoutRefund() throws Exception {
        ProcessRecipe recipe = PetroleumProcesses.defaults("reform").get("NAPHTHA"); ProcessBatch account = new ProcessBatch(); account.reserve(recipe);
        for(int t = 1; t <= 43; t++) paidTick(account, t);
        NBTTagCompound tag = new NBTTagCompound(); account.write(tag); account = new ProcessBatch(); account.read(roundTrip(tag));
        assertEquals(43, account.ticks()); assertEquals(recipe.id, account.recipe().id);
        assertFalse(account.canAdvance(43, Long.MAX_VALUE, Long.MAX_VALUE));
        try { account.reserve(ChemicalFuelProcesses.methanol()); fail("recipe overwritten"); } catch(IllegalStateException expected) { }
        // No unloaded time is simulated: only the next explicitly paid world tick progresses.
        paidTick(account, 10000); assertEquals(44, account.ticks());
        for(int t = 44; t < recipe.duration; t++) paidTick(account, 10000 + t);
        assertTrue(account.complete()); int[] output = account.finish(); long exported = 0;
        for(int i = 0; i < output.length; i++) exported += output[i] * PhysicalRegistry.get(recipe.outputs[i]).massUnits + account.products.remaining(recipe.outputs[i]);
        assertEquals(recipe.inputMass(), exported);
        try { account.finish(); fail("duplicate output"); } catch(IllegalStateException expected) { }
    }
    @Test public void physicalJsonRoundTripsExactBillsAndRejectsLegacyOrUnbalancedCustoms() throws Exception {
        for(ProcessRecipe recipe : bills()) {
            StringWriter buffer = new StringWriter(); JsonWriter writer = new JsonWriter(buffer); writer.beginObject(); PetroleumProcesses.write(recipe, writer); writer.endObject(); writer.close();
            ProcessRecipe loaded = PetroleumProcesses.read(new JsonParser().parse(buffer.toString()).getAsJsonObject());
            assertArrayEquals(recipe.productMass, loaded.productMass); assertArrayEquals(recipe.quantities, loaded.quantities); assertEquals(recipe.heatQuanta, loaded.heatQuanta); assertEquals(recipe.workQuanta, loaded.workQuanta);
        }
        try { PetroleumProcesses.read(new JsonParser().parse("{\"input\":\"OIL\"}").getAsJsonObject()); fail("volume-only JSON accepted"); } catch(IllegalArgumentException expected) { }
        ProcessRecipe source = PetroleumProcesses.defaults("refinery").get("HOTOIL"); long[] invalid = source.productMass.clone(); invalid[0]++;
        try { new ProcessRecipe(source.id, source.inputs, source.quantities, source.pressures, null, 0, source.outputs, invalid, source.duration, 18000, 81500, 350, true); fail("unbalanced custom bill accepted"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void corruptedNbtAndUnresolvedSubstancesRefuseConversion() {
        try { new MassRemainder().convert("UNDEFINED_PETROLEUM", 1); fail(); } catch(IllegalArgumentException expected) { }
        try { new ProcessRecipe("bad", new String[] {"NITAN"}, new int[] {1}, new int[] {0}, null, 0, new String[] {"DIESEL"}, new long[] {1}, 1, 1, 1, 20, false); fail(); } catch(IllegalArgumentException expected) { }
        ProcessBatch account = new ProcessBatch(); account.reserve(ChemicalFuelProcesses.methanol()); NBTTagCompound tag = new NBTTagCompound(); account.write(tag);
        tag.getCompoundTag("realSimProcess").setLong("heatPaid", 1);
        try { new ProcessBatch().read(tag); fail("unpaid stored credit accepted"); } catch(IllegalStateException expected) { }
    }
    @Test public void hydrogenConsumptionAndSourGasRecoveryUseActualMass() {
        ProcessRecipe recipe = PetroleumProcesses.defaults("hydro").get("OIL");
        assertEquals("HYDROGEN_GAS", recipe.inputs[1]); assertEquals(1, recipe.pressures[1]);
        assertTrue(recipe.quantities[1] >= 47);
        assertTrue(PhysicalRegistry.get("PETROLEUM").mixture.containsKey("LPG_VAPOR_ASSAY"));
        assertTrue(PhysicalRegistry.get("SOURGAS").mixture.containsKey("HYDROGEN_SULFIDE"));
        assertNotEquals(PhysicalRegistry.get("GAS").chemicalMicrojoulesPerUnit(), PhysicalRegistry.get("GAS_COKER").chemicalMicrojoulesPerUnit());
    }
    @Test public void loadedTickAndMaterialNameplatesCannotBeBypassed() {
        for(String machine : new String[] {"refinery", "fraction", "vacuum", "crack", "hydro", "reform", "coker"}) {
            int ceiling = machine.equals("refinery") ? 24 : machine.equals("coker") ? 6 : 12;
            for(ProcessRecipe recipe : PetroleumProcesses.defaults(machine).values()) assertTrue(recipe.id, recipe.quantities[0] * 1200D / recipe.duration <= ceiling);
        }
        ProcessBatch batch = new ProcessBatch(); batch.reserve(PetroleumProcesses.defaults("refinery").get("HOTOIL")); paidTick(batch, 1);
        assertFalse(batch.canAdvance(1, Long.MAX_VALUE, Long.MAX_VALUE)); assertEquals(1, batch.ticks());
    }
    @Test public void filledOutputAndPressurizedHydrogenSurviveSelectionChanges() {
        com.hbm.inventory.fluid.FluidType diesel = new com.hbm.inventory.fluid.FluidType("DIESEL", 0, 0, 0, 0, com.hbm.render.util.EnumSymbol.NONE);
        com.hbm.inventory.fluid.FluidType reformate = new com.hbm.inventory.fluid.FluidType("REFORMATE", 0, 0, 0, 0, com.hbm.render.util.EnumSymbol.NONE);
        com.hbm.inventory.fluid.FluidType hydrogenType = new com.hbm.inventory.fluid.FluidType("HYDROGEN_GAS", 0, 0, 0, 0, com.hbm.render.util.EnumSymbol.NONE);
        com.hbm.inventory.fluid.tank.FluidTank blocked = new com.hbm.inventory.fluid.tank.FluidTank(diesel, 4000);
        blocked.setFill(300);
        assertFalse(blocked.configureForRecipe(reformate, 0));
        assertEquals(300, blocked.getFill()); assertEquals(diesel, blocked.getTankType());
        ProcessRecipe hydro = PetroleumProcesses.defaults("hydro").get("OIL");
        com.hbm.inventory.fluid.tank.FluidTank hydrogen = new com.hbm.inventory.fluid.tank.FluidTank(hydrogenType, 4000);
        hydrogen.setFill(hydro.quantities[1]);
        assertFalse(hydrogen.configureForRecipe(hydrogenType, 1));
        assertEquals(hydro.quantities[1], hydrogen.getFill()); assertEquals(0, hydrogen.getPressure());
        hydrogen.withPressure(1); hydrogen.setFill(hydro.quantities[1]);
        assertTrue(hydrogen.configureForRecipe(hydrogenType, 1));
    }
    @Test public void customBillsMustFitTheirMachineRating() {
        for(ProcessRecipe recipe : bills()) if(recipe.id.startsWith("chem:")) try { ChemicalFuelProcesses.validateRating(recipe); }
        catch(IllegalArgumentException failure) { throw new AssertionError(recipe.id, failure); }
        ProcessRecipe stock = ChemicalFuelProcesses.methanol();
        ProcessRecipe tooFast = new ProcessRecipe("custom:too_fast", stock.inputs, stock.quantities, stock.pressures,
                stock.solidInput, stock.solidCount, stock.outputs, stock.productMass, 1,
                stock.workQuanta / 2, stock.heatQuanta / 2, stock.temperatureC, stock.separation);
        try { ChemicalFuelProcesses.validateRating(tooFast); fail("rating bypass"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void reportFrozenProductionBills() {
        for(ProcessRecipe recipe : bills()) {
            StringBuilder line = new StringBuilder("BILL|").append(recipe.id).append('|');
            for(int i = 0; i < recipe.inputs.length; i++) line.append(recipe.inputs[i]).append('=').append(recipe.quantities[i]).append('@').append(recipe.pressures[i]).append(';');
            if(recipe.solidCount > 0) line.append(recipe.solidInput).append('=').append(recipe.solidCount).append(';');
            line.append('|');
            for(int i = 0; i < recipe.outputs.length; i++) line.append(recipe.outputs[i]).append('=').append(BigDecimal.valueOf(recipe.productMass[i]).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 9, java.math.RoundingMode.HALF_UP)).append(';');
            System.out.println(line.append('|').append(recipe.duration).append('|').append(recipe.workQuanta / 2).append('|').append(recipe.heatQuanta / 2).append('|').append(recipe.temperatureC).append('|').append(recipe.rejectedJ.setScale(3, java.math.RoundingMode.HALF_UP)));
        }
        for(String name : new String[] {"OIL", "OIL_DS", "HOTOIL", "CRACKOIL", "HEAVYOIL", "HEAVYOIL_VACUUM", "BITUMEN", "SMEAR", "LIGHTOIL", "LIGHTOIL_CRACK", "LIGHTOIL_VACUUM", "NAPHTHA", "NAPHTHA_CRACK", "NAPHTHA_COKER", "OIL_COKER", "KEROSENE", "DIESEL", "GASOLINE", "HEATINGOIL", "REFORMATE", "AROMATICS", "UNSATURATEDS", "PETROLEUM", "SOURGAS", "GAS_COKER", "REFORMGAS", "LPG", "HYDROGEN_GAS", "HYDROGEN_SULFIDE", "RECLAIMED", "LUBRICANT", "PETROIL", "PETCOKE_ITEM", "COKE_ITEM", "LIGNITE_COKE_ITEM", "COAL_ITEM", "LIGNITE_ITEM", "COALOIL", "COALGAS", "SYNGAS", "METHANOL", "BIOFUEL", "FISHOIL", "SUNFLOWEROIL", "WOODOIL", "COALCREOSOTE"}) {
            PhysicalState state = PhysicalRegistry.get(name);
            System.out.println("ASSAY|" + name + "|" + state.phase + "|" + state.kilogramsPerUnit() + "|" + state.lhvJoulesPerKg + "|" + state.mixture);
        }
    }
}
