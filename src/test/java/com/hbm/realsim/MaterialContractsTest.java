package com.hbm.realsim;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import com.hbm.items.ModItems;
import com.hbm.inventory.material.MaterialShapes;

import com.hbm.inventory.fluid.tank.FluidTank;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Production resolution, machine input boundaries and binary saved identities, without a fake world. */
public class MaterialContractsTest {
    private final Map<Field, Object> previous = new HashMap<Field, Object>();

    @Test public void assemblerDisplayPreservesConcreteNbtAndWildcardRequirements() {
        com.hbm.main.ServerProxy previousProxy = com.hbm.main.MainRegistry.proxy;
        com.hbm.main.MainRegistry.proxy = new com.hbm.main.ServerProxy();
        try {
            com.hbm.inventory.RecipesCommon.ComparableStack output = new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.ingot_polymer);
            NBTTagCompound data = new NBTTagCompound(); data.setString("grade", "retained");
            com.hbm.inventory.RecipesCommon.AStack[] ingredients = {
                new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.ingot_polymer, 3),
                new com.hbm.inventory.RecipesCommon.ComparableNBTStack(ModItems.ingot_polymer, 2).addNBT(data),
                new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.ingot_polymer, 4, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
            };
            for(int i = 0; i < ingredients.length; i++) {
                java.util.List<ItemStack> display = com.hbm.inventory.recipes.AssemblerRecipes.compileIngredientForNEI(output, i, ingredients[i]);
                assertFalse(display.isEmpty());
                for(ItemStack stack : display) {
                    assertEquals(ingredients[i].stacksize, stack.stackSize);
                    assertTrue(ingredients[i].matchesRecipe(stack, true));
                }
            }
            assertEquals("retained", ingredients[1].extractForNEI().get(0).getTagCompound().getString("grade"));
        } finally { com.hbm.main.MainRegistry.proxy = previousProxy; }
    }

    @Test public void everyDefaultAssemblerArrayFitsNativeInputPorts() throws Exception {
        String materials = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/hbm/inventory/material/Mats.java")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(java.util.regex.Pattern.compile("MAT_COPPER[^;]+setAutogen\\([^)]*DENSEWIRE").matcher(materials).find());
        org.objectweb.asm.tree.ClassNode code = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(com.hbm.inventory.recipes.AssemblerRecipes.class.getName()).accept(code, 0);
        int arrays = 0;
        for(Object value : code.methods) {
            org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) value;
            if(!method.name.equals("registerDefaults")) continue;
            for(org.objectweb.asm.tree.AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext())
                if(node.getOpcode() == org.objectweb.asm.Opcodes.ANEWARRAY && ((org.objectweb.asm.tree.TypeInsnNode) node).desc.equals("com/hbm/inventory/RecipesCommon$AStack")) {
                    org.objectweb.asm.tree.AbstractInsnNode size = node.getPrevious();
                    int count = size instanceof org.objectweb.asm.tree.IntInsnNode ? ((org.objectweb.asm.tree.IntInsnNode) size).operand
                            : size.getOpcode() - org.objectweb.asm.Opcodes.ICONST_0;
                    assertTrue("Invalid/default recipe input count " + count, count > 0 && count <= com.hbm.inventory.recipes.AssemblerRecipes.MAX_INGREDIENTS);
                    arrays++;
                }
        }
        assertTrue("Default recipe census was empty", arrays > 100);
    }

    @Before public void nativeItems() throws Exception {
        for(String name : new String[] {"coke", "powder_coke", "lignite", "powder_lignite", "powder_coal", "briquette", "biomass", "biomass_compressed", "powder_sawdust", "powder_ash", "scrap", "dust", "sulfur", "solid_fuel", "rocket_fuel", "ingot_polymer", "ingot_pvc", "iron_dri", "quicklime", "ingot_steel", "slagingot"}) {
            Field field = ModItems.class.getField(name); previous.put(field, field.get(null)); field.set(null, new Item());
        }

    }
    @After public void restoreNativeItems() throws Exception {
        for(Map.Entry<Field, Object> entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
    }
    @Test public void arcSteelReservesAllFeedsAndPaysExactlyBeforeAtomicProducts() {
        com.hbm.tileentity.machine.TileEntityMachineArcFurnaceLarge furnace = new com.hbm.tileentity.machine.TileEntityMachineArcFurnaceLarge();
        furnace.lid = 1;
        for(Item item : new Item[] {ModItems.iron_dri, ModItems.coke, ModItems.quicklime}) {
            assertTrue(furnace.canInsertItem(5, new ItemStack(item), 0));
            assertFalse(furnace.canExtractItem(5, new ItemStack(item), 0));
        }
        ItemStack[] slots = new ItemStack[25];
        for(int i = 5; i < 13; i++) slots[i] = new ItemStack(ModItems.iron_dri);
        slots[13] = new ItemStack(ModItems.coke); slots[14] = new ItemStack(ModItems.quicklime);
        slots[13].setTagCompound(new NBTTagCompound()); slots[13].getTagCompound().setString("retained", "coke");
        ArcSteelBatch batch = new ArcSteelBatch();
        slots[14] = null; assertFalse(batch.reserve(slots)); assertNotNull(slots[5]);
        slots[14] = new ItemStack(ModItems.quicklime);
        assertTrue(batch.reserve(slots)); assertNull(slots[5]); assertFalse(batch.reserve(slots));
        assertEquals(1, batch.pay(1)); assertEquals(0, batch.pay(0));
        assertFalse(batch.finish(slots)); assertEquals(ArcSteelBatch.WORK_QUANTA - 1, batch.pay(Long.MAX_VALUE));
        for(int i = 5; i < 25; i++) slots[i] = new ItemStack(ModItems.scrap, 64);
        assertFalse(batch.finish(slots)); assertTrue(batch.complete());
        slots[5] = null; slots[6] = null; assertTrue(batch.finish(slots));
        assertSame(ModItems.ingot_steel, slots[5].getItem()); assertEquals(32, slots[5].stackSize);
        assertSame(ModItems.slagingot, slots[6].getItem()); assertEquals(2, slots[6].stackSize);
        assertFalse(batch.finish(slots)); assertEquals(0, batch.takeDrops().length);
        slots = new ItemStack[25]; slots[5] = new ItemStack(ModItems.iron_dri, 8);
        slots[6] = new ItemStack(ModItems.coke, 1, 2); slots[7] = new ItemStack(ModItems.quicklime);
        assertFalse(batch.reserve(slots)); // Petroleum coke is not the specified steel charge.
        slots[6] = new ItemStack(ModItems.coke); assertTrue(batch.reserve(slots)); batch.pay(1);
        assertEquals(3, batch.takeDrops().length); assertEquals(0, batch.takeDrops().length);
    }
    @Test public void polymerAndSootFeedsUseTheirExactExistingAssaysAndForms() {
        assertEquals("polyethylene", PhysicalRegistry.get("POLYMER_ITEM").substance);
        assertEquals(PhysicalState.MASS_UNITS_PER_KG, PhysicalRegistry.get("POLYMER_ITEM").massUnits);
        assertEquals(47_200_000_000_000L, PhysicalRegistry.get("POLYMER_ITEM").chemicalMicrojoulesPerUnit());
        com.hbm.inventory.RecipesCommon.ComparableStack polymer = (com.hbm.inventory.RecipesCommon.ComparableStack) ProcessMachine.feed("POLYMER_ITEM", 2);
        assertSame(ModItems.ingot_polymer, polymer.item); assertEquals(2, polymer.stacksize);
        assertTrue(ProcessMachine.matches(new ItemStack(ModItems.ingot_polymer), "POLYMER_ITEM"));
        assertFalse(ProcessMachine.matches(new ItemStack(ModItems.ingot_pvc), "POLYMER_ITEM"));
        assertSame(ModItems.ingot_polymer, ProcessMachine.solid("POLYMER_ITEM", 1).getItem());
        com.hbm.inventory.RecipesCommon.ComparableStack soot = (com.hbm.inventory.RecipesCommon.ComparableStack) ProcessMachine.feed("CHAR_ITEM", 3);
        assertSame(ModItems.powder_ash, soot.item); assertEquals(3, soot.stacksize);
        assertEquals(com.hbm.items.ItemEnums.EnumAshType.SOOT.ordinal(), soot.meta);
        assertEquals(PhysicalState.MASS_UNITS_PER_KG, PhysicalRegistry.get("CHAR_ITEM").massUnits);
        assertTrue(ProcessMachine.matches(soot.toStack(), "CHAR_ITEM"));
        assertFalse(ProcessMachine.matches(new ItemStack(ModItems.powder_ash), "CHAR_ITEM"));
        try { ProcessMachine.feed("UNKNOWN_PHYSICAL_ITEM", 1); fail("Unknown feed accepted"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("UNKNOWN_PHYSICAL_ITEM")); }
        ProcessRecipe bill = ChemistryProcesses.defaults().get(84).snapshot();
        bill.solidInputs[1] = "WOOD_ASH_ITEM"; // Known assay, deliberately missing ingredient adapter.
        try { ProcessMachine.feed(bill, 1, "Chemical Reactor template 84"); fail("Unsupported feed accepted"); }
        catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Chemical Reactor template 84"));
            assertTrue(expected.getMessage().contains(bill.id));
            assertTrue(expected.getMessage().contains("WOOD_ASH_ITEM"));
        }
    }
    private void fuel(ItemStack stack, String identity, int units) {
        SolidFuel actual = SolidFuel.resolve(stack);
        assertNotNull(actual); assertEquals(identity, actual.identity); assertEquals(units, actual.units);
        assertEquals(PhysicalRegistry.get(identity).massUnits * units, actual.massUnits());
        assertEquals(PhysicalRegistry.get(identity).chemicalMicrojoulesPerUnit() * units, Combustion.solidChemicalMicrojoules(stack));
    }
    @Test public void nativeCoalCharcoalAndLigniteRetainAssays() {
        assertEquals("COAL_ITEM", SolidFuel.fromAliases(Arrays.asList("gemCoal")).identity);
        assertEquals("CHARCOAL_ITEM", SolidFuel.fromAliases(Arrays.asList("dustCharcoal")).identity);
        fuel(new ItemStack(ModItems.powder_coal), "COAL_ITEM", 1);
        fuel(new ItemStack(ModItems.lignite), "LIGNITE_ITEM", 1);
        fuel(new ItemStack(ModItems.powder_lignite), "LIGNITE_ITEM", 1);
    }
    @Test public void nativeCokeGradesPowdersAndBlocksKeepTheirIdentity() {
        String[] ids = {"COKE_ITEM", "LIGNITE_COKE_ITEM", "PETCOKE_ITEM"};
        for(int meta = 0; meta < ids.length; meta++) {
            fuel(new ItemStack(ModItems.coke, 1, meta), ids[meta], 1);
            fuel(new ItemStack(ModItems.powder_coke, 64, meta), ids[meta], 1);
            assertEquals(9 * PhysicalRegistry.get(ids[meta]).chemicalMicrojoulesPerUnit(), SolidFuel.of(ids[meta], 9).chemicalMicrojoules());
        }
        assertNotEquals(PhysicalRegistry.get(ids[0]).lhvJoulesPerKg, PhysicalRegistry.get(ids[1]).lhvJoulesPerKg);
        assertNotEquals(PhysicalRegistry.get(ids[1]).lhvJoulesPerKg, PhysicalRegistry.get(ids[2]).lhvJoulesPerKg);
    }
    @Test public void briquettesAndCompressedBiomassRespectPressQuantities() {
        String[] ids = {"COAL_BRIQUETTE_ITEM", "LIGNITE_BRIQUETTE_ITEM", "WOOD_BRIQUETTE_ITEM"};
        for(int meta = 0; meta < ids.length; meta++) fuel(new ItemStack(ModItems.briquette, 1, meta), ids[meta], 1);
        fuel(new ItemStack(ModItems.biomass), "BIOMASS_ITEM", 1);
        fuel(new ItemStack(ModItems.biomass_compressed), "BIOMASS_ITEM", 1);
        fuel(new ItemStack(ModItems.powder_sawdust), "SAWDUST_ITEM", 1);
        assertEquals(4 * PhysicalRegistry.get("SAWDUST_ITEM").massUnits, PhysicalRegistry.get("WOOD_BRIQUETTE_ITEM").massUnits);
        assertEquals(4 * PhysicalRegistry.get("SAWDUST_ITEM").chemicalMicrojoulesPerUnit(), PhysicalRegistry.get("WOOD_BRIQUETTE_ITEM").chemicalMicrojoulesPerUnit());
    }
    @Test public void woodAndCoalStorageHaveOneQuantity() {
        assertEquals(9 * PhysicalRegistry.get("COAL_ITEM").chemicalMicrojoulesPerUnit(), SolidFuel.fromAliases(Arrays.asList("blockCoal")).chemicalMicrojoules());
        assertEquals("WOOD_LOG", SolidFuel.fromAliases(Arrays.asList("logWood")).identity);
        assertEquals("WOOD_PLANK", SolidFuel.fromAliases(Arrays.asList("plankWood")).identity);
        assertEquals("WOOD_STICK", SolidFuel.fromAliases(Arrays.asList("stickWood")).identity);
        assertEquals(4 * PhysicalRegistry.get("WOOD_PLANK").massUnits, PhysicalRegistry.get("WOOD_LOG").massUnits);
        assertEquals(2 * PhysicalRegistry.get("WOOD_STICK").massUnits, PhysicalRegistry.get("WOOD_PLANK").massUnits);
    }    @Test public void genericAliasesCannotEraseKnownGradesOrAdmitResidues() {
        ItemStack petcoke = new ItemStack(ModItems.coke, 1, 2);

        fuel(petcoke, "PETCOKE_ITEM", 1);
        for(Item item : new Item[] {ModItems.powder_ash, ModItems.scrap, ModItems.dust, ModItems.sulfur, ModItems.solid_fuel, ModItems.rocket_fuel}) {
            ItemStack stack = new ItemStack(item);
            assertNull(Combustion.solidIdentity(stack)); assertEquals(0, Combustion.solidChemicalMicrojoules(stack));
        }
        assertNull(SolidFuel.resolve(new ItemStack(ModItems.coke, 1, 99)));
        assertNull(SolidFuel.fromAliases(Arrays.asList("gemCoal", "gemLignite")));
        assertNull(SolidFuel.fromAliases(Arrays.asList("dustSulfur", "dustAsh", "bookshelf", "itemScrap")));
    }
    @Test public void aliasesAreOrderIndependentAndQuantitySpecific() {
        for(String grade : new String[] {"CoalCoke", "LigniteCoke", "PetCoke", "PetroleumCoke"}) {
            SolidFuel a = SolidFuel.fromAliases(Arrays.asList("fuelCoke", "gemCoke", "gem" + grade));
            SolidFuel b = SolidFuel.fromAliases(Arrays.asList("gem" + grade, "gemCoke", "fuelCoke"));
            assertEquals(a.identity, b.identity); assertEquals(a.chemicalMicrojoules(), b.chemicalMicrojoules());
            SolidFuel block = SolidFuel.fromAliases(Arrays.asList("blockCoke", "block" + grade));
            assertEquals(9 * a.chemicalMicrojoules(), block.chemicalMicrojoules());
        }
        assertNull(SolidFuel.fromAliases(Arrays.asList("gemCoal", "blockCoal")));
    }
    @Test public void positiveEnergyDoesNotGrantEquipmentRoles() {
        for(String id : new String[] {"SULFUR_ITEM", "SUGAR_ITEM", "CHAR_ITEM", "HYDRAZINE", "HYDROGEN_SULFIDE", "SOLVENT", "XYLENE", "OXYHYDROGEN"}) {
            assertTrue(PhysicalRegistry.get(id).chemicalMicrojoulesPerUnit() > 0);
            assertFalse(FuelSuitability.has(id, MaterialRole.HEATING_FUEL));
        }
        assertTrue(FuelSuitability.has("COKE_ITEM", MaterialRole.REDUCTANT));
        assertFalse(FuelSuitability.has("COKE_ITEM", MaterialRole.INTERMEDIATE));
        assertTrue(FuelSuitability.has("COAL_ITEM", MaterialRole.INTERMEDIATE));
        assertFalse(FuelSuitability.has("LIGNITE_COKE_ITEM", MaterialRole.REDUCTANT));
        assertTrue(FuelSuitability.has("KEROSENE", MaterialRole.AVIATION_FUEL));
        assertFalse(FuelSuitability.has("KEROSENE", MaterialRole.DIESEL_FUEL));
        assertTrue(FuelSuitability.has("OXYGEN_GAS", MaterialRole.OXIDIZER));
        assertFalse(FuelSuitability.has("OXYGEN_GAS", MaterialRole.HEATING_FUEL));
    }
    @Test public void blastTemperatureAndCokeEnrichmentAreTheOnlyThermalCeilingBonuses() {
        assertEquals(2000000, BlastFurnaceProcess.ratedWatts(true, "AIR", false));
        assertEquals(4000000, BlastFurnaceProcess.ratedWatts(true, "AIR_HEATED", false));
        assertEquals(8000000, BlastFurnaceProcess.ratedWatts(true, "AIRBLAST", false));
        for(String air : new String[] {"AIR", "AIR_HEATED", "AIRBLAST"}) {
            assertEquals(BlastFurnaceProcess.ratedWatts(true, air, false) * 5 / 4, BlastFurnaceProcess.ratedWatts(true, air, true));
            assertEquals(BlastFurnaceProcess.ratedWatts(false, air, false), BlastFurnaceProcess.ratedWatts(false, air, true));
        }
        assertEquals(1500000, BlastFurnaceProcess.ratedWatts(false, "AIR", false));
        assertEquals(2000000, BlastFurnaceProcess.ratedWatts(false, "AIRBLAST", false));
    }
    @Test public void blastAndOxygenAreMeteredToAvailableWorkInsteadOfDrainingWholeTanks() {
        long heat = 18000000, paid = 0;
        int totalAir = 2700, airPaid = 0, oxygenPaid = 0;
        long first = BlastFurnaceProcess.availableHeatStep(heat, 0, totalAir, 0, 0, 16000, 4000, true, 625000);
        assertEquals(625000, first);
        int firstAir = (int) SteamLedger.ceilDiv(first * totalAir, heat);
        assertTrue(firstAir < totalAir); assertTrue((firstAir + 9) / 10 < 270);
        assertEquals(0, BlastFurnaceProcess.availableHeatStep(heat, 0, totalAir, 0, 0, 100, 0, true, 625000));
        assertEquals(500000, BlastFurnaceProcess.availableHeatStep(2000000, 500000, 1, 1, 1, 0, 0, true, 500000));
        // One-litre parcels must drain completely, allowing a different inlet to be chosen next.
        while(paid < heat) {
            long step = BlastFurnaceProcess.availableHeatStep(heat, paid, totalAir, airPaid, oxygenPaid, 1, 1, true, 625000);
            assertTrue(step > 0 && step <= 625000);
            int nextAir = (int) SteamLedger.ceilDiv((paid + step) * totalAir, heat);
            int nextOxygen = (nextAir + 9) / 10;
            assertEquals(1, nextAir - airPaid); assertTrue(nextOxygen - oxygenPaid <= 1);
            paid += step; airPaid = nextAir; oxygenPaid = nextOxygen;
        }
        assertEquals(heat, paid); assertEquals(totalAir, airPaid); assertEquals(270, oxygenPaid);
    }
    @Test public void blastWorkUsesOnlyPaidHeatAndNeverRequiresAWholeStepOfExternalSupply() {
        assertEquals(0, BlastFurnaceProcess.heatLimitedStep(18000000, 0, 2700, 0, 100000, 0, 0));
        assertEquals(250, BlastFurnaceProcess.heatLimitedStep(18000000, 0, 2700, 0, 100000, 250, 0));
        long paid = 0, supplied = 15510000;
        int airPaid = 0;
        while(paid < 18000000) {
            long proposed = Math.min(400000, 18000000 - paid);
            long step = BlastFurnaceProcess.heatLimitedStep(18000000, paid, 2700, airPaid, proposed,
                    supplied, BlastFurnaceProcess.blastJoulesPerLiter("AIRBLAST"));
            assertTrue(step > 0);
            int air = (int) SteamLedger.ceilDiv((paid + step) * 2700, 18000000) - airPaid;
            long hot = Math.min(step, air * BlastFurnaceProcess.blastJoulesPerLiter("AIRBLAST"));
            supplied -= step - hot; assertTrue(supplied >= 0);
            paid += step; airPaid += air;
        }
        assertEquals(2700, airPaid); assertTrue(supplied > 0);
    }
    @Test public void shaftFuelAndProcessFeedDoNotInheritHeaterAcceptance() {
        assertEquals("COKE_ITEM", BlastFurnaceProcess.fuelIdentity(new ItemStack(ModItems.coke)));
        assertNull(BlastFurnaceProcess.fuelIdentity(new ItemStack(ModItems.coke, 1, 2)));
        assertFalse(FuelSuitability.has("WOOD_LOG", MaterialRole.REDUCTANT));
        assertNull(BlastFurnaceProcess.fuelIdentity(new ItemStack(ModItems.biomass)));
        ProcessRecipe bill = ChemicalFuelProcesses.cokeOven("COAL_ITEM");
        assertTrue(ProcessMachine.inputsReady(bill, new FluidTank[0], new ItemStack[] {new ItemStack(ModItems.powder_coal)}, 0, 0));
        assertFalse(ProcessMachine.inputsReady(bill, new FluidTank[0], new ItemStack[] {new ItemStack(ModItems.briquette)}, 0, 0));
        assertFalse(ProcessMachine.inputsReady(bill, new FluidTank[0], new ItemStack[] {new ItemStack(ModItems.coke)}, 0, 0));
    }
    @Test public void savedFuelCreditRetainsGradeQuantityAndFractionalEnergy() throws Exception {
        for(String identity : new String[] {"COAL_ITEM", "LIGNITE_ITEM", "CHARCOAL_ITEM", "COKE_ITEM", "LIGNITE_COKE_ITEM", "PETCOKE_ITEM", "WOOD_BRIQUETTE_ITEM", "BIOMASS_ITEM"}) {
            FuelCredit account = new FuelCredit();
            account.reserve(identity, SolidFuel.of(identity, 9).chemicalMicrojoules(), 1, 850000);
            account.release(237);
            long remaining = account.usefulMicrojoules();
            NBTTagCompound tag = new NBTTagCompound(); account.write(tag);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag, bytes);
            tag = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
            FuelCredit loaded = new FuelCredit(); loaded.read(tag);
            assertEquals(identity, loaded.fuel()); assertEquals(remaining, loaded.usefulMicrojoules());
            assertEquals(1, loaded.release(1)); assertEquals(remaining - 500000, loaded.usefulMicrojoules());
        }
    }
    @Test public void shapesInitializeWithoutLoaderOrPartiallyInitializedMaterials() {
        assertEquals(72, MaterialShapes.INGOT.q(1));
        assertEquals(MaterialShapes.INGOT.q(1), MaterialShapes.NUGGET.q(9));
        assertEquals(MaterialShapes.INGOT.q(9), MaterialShapes.BLOCK.q(1));
        assertSame(MaterialShapes.DUST, MaterialShapes.prefixByName.get("dust"));
        assertTrue(MaterialShapes.WIRE.hasNativeGenerator());
        assertFalse(MaterialShapes.DUST.hasNativeGenerator());
        assertEquals(0, MaterialShapes.ORE.q(1));
    }
    @Test public void chromiteReductionPaysItsBillAndRetainsEveryProductAcrossReload() throws Exception {
        ProcessRecipe bill = ChemistryProcesses.defaults().get(1201);
        assertNotNull(bill);
        assertEquals("specialist:chromite_aluminothermic_reduction", bill.id);
        assertEquals(400, bill.duration); assertEquals(8_400_000L, bill.workQuanta); assertEquals(0, bill.heatQuanta);
        assertArrayEquals(new int[] {2, 1}, bill.solidCounts);
        assertArrayEquals(new String[] {"ITEM:chromite", "ALUMINIUM_ITEM"}, bill.solidInputs);
        assertArrayEquals(new String[] {"ITEM:ingot_raw@24004", "ALUMINA_ITEM", "ALUMINIUM_ITEM"}, bill.outputs);
        long total = 0; for(long mass : bill.productMass) total += mass;
        assertEquals(3 * PhysicalState.MASS_UNITS_PER_KG, total);
        assertTrue(bill.productMass[0] > PhysicalState.MASS_UNITS_PER_KG);
        assertTrue(bill.productMass[2] > 0); // Unreacted aluminium is returned, never destroyed or converted into Cr.
        ProcessBatch batch = new ProcessBatch(); batch.reserve(bill);
        for(int tick = 0; tick < 200; tick++) {
            assertFalse(batch.canAdvance(tick, 0, 0));
            assertTrue(batch.canAdvance(tick, 21000, 0)); batch.advance(tick);
        }
        NBTTagCompound tag = new NBTTagCompound(); batch.write(tag);
        ProcessBatch loaded = new ProcessBatch(); loaded.read(tag);
        assertFalse(loaded.complete());
        for(int tick = 200; tick < 400; tick++) { assertTrue(loaded.canAdvance(tick, 21000, 0)); loaded.advance(tick); }
        assertTrue(loaded.complete());
        assertArrayEquals(bill.productMass, loaded.recipe().productMass);
        int[] products = loaded.finish(); assertEquals(1, products[0]);
        batch = new ProcessBatch(); batch.read(tag);
        assertEquals(200, batch.ticks());
    }

    @Test public void materialRegistryChecksUseTheExistingDedicatedServerLoader() throws Throwable {
        java.util.Set<java.net.URL> urls = new java.util.LinkedHashSet<java.net.URL>();
        for(ClassLoader current = getClass().getClassLoader(); current != null; current = current.getParent())
            if(current instanceof java.net.URLClassLoader) java.util.Collections.addAll(urls, ((java.net.URLClassLoader) current).getURLs());
        try(com.hbm.world.gen.structure.RecordingStructureWorldTest.ServerLoader loader =
                new com.hbm.world.gen.structure.RecordingStructureWorldTest.ServerLoader(urls.toArray(new java.net.URL[0]))) {
            Field side = loader.loadClass("cpw.mods.fml.relauncher.FMLRelaunchLog").getDeclaredField("side"); side.setAccessible(true);
            side.set(null, loader.loadClass("cpw.mods.fml.relauncher.Side").getField("SERVER").get(null));
            loader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData", Object[].class).invoke(null,
                    (Object) new Object[] {"7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), java.util.Collections.emptyList()});
            loader.loadClass("net.minecraft.init.Bootstrap").getMethod("func_151354_b").invoke(null);
            Class<?> fml = loader.loadClass("cpw.mods.fml.common.Loader");
            Field mods = fml.getDeclaredField("namedMods"); mods.setAccessible(true);
            mods.set(fml.getMethod("instance").invoke(null), java.util.Collections.emptyMap());
            Class<?> type = loader.loadClass(getClass().getName()); Object checks = type.newInstance();
            for(String method : new String[] {"contactAlloyHasSeparateIdentityAndConservesFoundryQuantity",
                    "ferrochromeStainlessUsesBothNativeInputCountsWithoutPureChromium", "contactSwitchCraftingRequiresEightConductorsAndTheCorrectContactState",
                    "chromiteTemplateBindsNativeFeedsAndMetadataOutputs"}) {
                try { type.getMethod(method).invoke(checks); }
                catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
            }
        }
    }

    public void chromiteTemplateBindsNativeFeedsAndMetadataOutputs() {
        Item chromite = ModItems.chromite, aluminium = ModItems.powder_aluminium, alumina = ModItems.powder_alumina, raw = ModItems.ingot_raw;
        com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe old = com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.remove(1201);
        try {
            ModItems.chromite = net.minecraft.init.Items.quartz; ModItems.powder_aluminium = net.minecraft.init.Items.iron_ingot;
            ModItems.powder_alumina = net.minecraft.init.Items.clay_ball; ModItems.ingot_raw = net.minecraft.init.Items.gold_ingot;
            com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe recipe = new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe(1201, "CHROMITE_REDUCTION", 1)
                    .petroleum(ChemistryProcesses.defaults().get(1201));
            assertTrue(recipe.inputs[0].matchesRecipe(new ItemStack(ModItems.chromite, 2), false));
            assertFalse(recipe.inputs[0].matchesRecipe(new ItemStack(ModItems.chromite, 1), false));
            assertTrue(recipe.inputs[1].matchesRecipe(new ItemStack(ModItems.powder_aluminium), false));
            assertEquals(24004, recipe.outputs[0].getItemDamage()); assertSame(ModItems.ingot_raw, recipe.outputs[0].getItem());
            assertSame(ModItems.powder_alumina, recipe.outputs[1].getItem());
            assertNull(recipe.outputs[3]); assertEquals(21000, recipe.physicalPowerQuanta);
        } finally {
            com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.remove(1201);
            if(old != null) com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.put(1201, old);
            ModItems.chromite = chromite; ModItems.powder_aluminium = aluminium; ModItems.powder_alumina = alumina; ModItems.ingot_raw = raw;
        }
    }

    public void ferrochromeStainlessUsesBothNativeInputCountsWithoutPureChromium() throws Exception {
        Item raw = ModItems.ingot_raw, steel = ModItems.ingot_steel, stainless = ModItems.ingot_stainless;
        com.hbm.inventory.recipes.BlastFurnaceRecipes handler = new com.hbm.inventory.recipes.BlastFurnaceRecipes();
        java.util.List registry = (java.util.List) handler.getRecipeObject(); int start = registry.size();
        try {
            ModItems.ingot_raw = ModItems.ingot_steel = net.minecraft.init.Items.iron_ingot;
            ModItems.ingot_stainless = net.minecraft.init.Items.gold_ingot;
            net.minecraftforge.oredict.OreDictionary.registerOre("ingotSteel", new ItemStack(ModItems.ingot_steel));
            com.hbm.inventory.recipes.BlastFurnaceRecipes.registerFerrochromeStainless();
            java.io.StringWriter json = new java.io.StringWriter();
            com.google.gson.stream.JsonWriter writer = new com.google.gson.stream.JsonWriter(json);
            writer.beginObject(); handler.writeRecipe(registry.get(registry.size() - 1), writer); writer.endObject(); writer.close();
            com.google.gson.JsonObject exported = new com.google.gson.JsonParser().parse(json.toString()).getAsJsonObject();
            assertEquals("dict", exported.getAsJsonArray("input1").get(0).getAsString());
            assertEquals(6, exported.getAsJsonArray("input1").get(2).getAsInt());
            assertEquals(2, exported.getAsJsonArray("input2").get(2).getAsInt());
            assertEquals(24004, exported.getAsJsonArray("input2").get(3).getAsInt());
            registry.remove(registry.size() - 1);
            handler.readRecipe(exported); // Template reload must retain the counted OreDictionary feed.
            ItemStack iron = new ItemStack(ModItems.ingot_steel, 6), alloy = new ItemStack(ModItems.ingot_raw, 2, 24004);
            assertEquals(8, com.hbm.inventory.recipes.BlastFurnaceRecipes.getOutput(iron, alloy).stackSize);
            assertArrayEquals(new int[] {6, 2}, com.hbm.inventory.recipes.BlastFurnaceRecipes.getInputAmounts(iron, alloy));
            assertArrayEquals(new int[] {2, 6}, com.hbm.inventory.recipes.BlastFurnaceRecipes.getInputAmounts(alloy, iron));
            com.google.gson.JsonElement first = exported.get("input1"), second = exported.get("input2");
            exported.add("input1", second); exported.add("input2", first);
            registry.remove(registry.size() - 1); handler.readRecipe(exported);
            json = new java.io.StringWriter(); writer = new com.google.gson.stream.JsonWriter(json);
            writer.beginObject(); handler.writeRecipe(registry.get(registry.size() - 1), writer); writer.endObject(); writer.close();
            assertEquals(6, new com.google.gson.JsonParser().parse(json.toString()).getAsJsonObject().getAsJsonArray("input2").get(2).getAsInt());
            assertArrayEquals(new int[] {6, 2}, com.hbm.inventory.recipes.BlastFurnaceRecipes.getInputAmounts(iron, alloy));
            iron.stackSize = 5; assertNull(com.hbm.inventory.recipes.BlastFurnaceRecipes.getOutput(iron, alloy)); iron.stackSize = 6;
            alloy.stackSize = 1; assertNull(com.hbm.inventory.recipes.BlastFurnaceRecipes.getOutput(iron, alloy));
            alloy.stackSize = 2; alloy.setItemDamage(24003); assertNull(com.hbm.inventory.recipes.BlastFurnaceRecipes.getOutput(iron, alloy));
            assertFalse(com.hbm.inventory.recipes.BlastFurnaceRecipes.getRecipesForNEI().isEmpty());
        } finally {
            while(registry.size() > start) registry.remove(registry.size() - 1);
            ModItems.ingot_raw = raw; ModItems.ingot_steel = steel; ModItems.ingot_stainless = stainless;
        }
    }

    public void contactAlloyHasSeparateIdentityAndConservesFoundryQuantity() {
        com.hbm.inventory.recipes.CrucibleRecipes.CrucibleRecipe old = com.hbm.inventory.recipes.CrucibleRecipes.indexMapping.remove(25);
        Item raw = ModItems.ingot_raw; ModItems.ingot_raw = net.minecraft.init.Items.iron_ingot;
        try {
            com.hbm.inventory.recipes.CrucibleRecipes.registerContactAlloy();
            com.hbm.inventory.recipes.CrucibleRecipes.CrucibleRecipe recipe = com.hbm.inventory.recipes.CrucibleRecipes.indexMapping.get(25);
            assertNotNull(recipe); assertEquals(144, recipe.getInputAmount());
            assertEquals(144, recipe.output[0].amount);
            assertSame(com.hbm.inventory.material.Mats.MAT_OSMIRIDIUM, recipe.input[0].material);
            assertSame(com.hbm.inventory.material.Mats.MAT_IRIDIUM, recipe.input[1].material);
            assertSame(com.hbm.inventory.material.Mats.MAT_OSMIUM_IRIDIUM, recipe.output[0].material);
            assertEquals(24003, recipe.icon.getItemDamage());
            assertNotEquals(recipe.input[0].material.id, recipe.output[0].material.id);
            assertTrue(recipe.output[0].material.autogen.contains(MaterialShapes.INGOT));
        } finally {
            com.hbm.inventory.recipes.CrucibleRecipes.recipes.remove(com.hbm.inventory.recipes.CrucibleRecipes.indexMapping.remove(25));
            if(old != null) com.hbm.inventory.recipes.CrucibleRecipes.indexMapping.put(25, old);
            ModItems.ingot_raw = raw;
        }
    }

    public void contactSwitchCraftingRequiresEightConductorsAndTheCorrectContactState() {
        net.minecraft.block.Block wire = com.hbm.blocks.ModBlocks.red_wire_coated, output = com.hbm.blocks.ModBlocks.cable_switch;
        Item raw = ModItems.ingot_raw;
        java.util.List registry = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        int start = registry.size();
        try {
            com.hbm.blocks.ModBlocks.red_wire_coated = net.minecraft.init.Blocks.wool;
            com.hbm.blocks.ModBlocks.cable_switch = net.minecraft.init.Blocks.redstone_block;
            ModItems.ingot_raw = net.minecraft.init.Items.iron_ingot;
            net.minecraftforge.oredict.OreDictionary.registerOre("ingotRhodium", new ItemStack(net.minecraft.init.Items.gold_ingot));
            com.hbm.main.CraftingManager.registerContactSwitches();
            assertEquals(start + 2, registry.size());
            net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
            }, 3, 3);
            for(int i = 0; i < 9; i++) grid.setInventorySlotContents(i, new ItemStack(net.minecraft.init.Blocks.wool));
            net.minecraft.item.crafting.IRecipe alloy = (net.minecraft.item.crafting.IRecipe) registry.get(start);
            net.minecraft.item.crafting.IRecipe rh = (net.minecraft.item.crafting.IRecipe) registry.get(start + 1);
            grid.setInventorySlotContents(4, new ItemStack(ModItems.ingot_raw, 1, 24003));
            assertTrue(alloy.matches(grid, null)); assertEquals(8, alloy.getCraftingResult(grid).stackSize);
            grid.setInventorySlotContents(4, new ItemStack(ModItems.ingot_raw, 1, 7699)); assertFalse(alloy.matches(grid, null));
            grid.setInventorySlotContents(4, new ItemStack(net.minecraft.init.Items.gold_ingot)); assertTrue(rh.matches(grid, null));
            grid.setInventorySlotContents(0, null); assertFalse(rh.matches(grid, null));
        } finally {
            while(registry.size() > start) registry.remove(registry.size() - 1);
            ModItems.ingot_raw = raw; com.hbm.blocks.ModBlocks.red_wire_coated = wire; com.hbm.blocks.ModBlocks.cable_switch = output;
        }
    }
    @Test public void persistentIdsHaveUniqueSourceDefinitionsAndStableBinaryDecode() throws Exception {
        // This is a source-registry check: Forge's live registry requires its launch class loader.
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/hbm/inventory/material/Mats.java")), java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Integer> spaces = new HashMap<String, Integer>();
        java.util.regex.Matcher constants = java.util.regex.Pattern.compile("public static final int (_[A-Z]+) = ([0-9_]+)").matcher(source);
        while(constants.find()) spaces.put(constants.group(1), Integer.parseInt(constants.group(2).replace("_", "")));
        Map<Integer, String> identities = new HashMap<Integer, String>();
        java.util.regex.Matcher definitions = java.util.regex.Pattern.compile("(?m)^\\s*public static final NTMMaterial (MAT_\\w+)\\s*=\\s*make\\w*\\(\\s*([^,]+),").matcher(source);
        while(definitions.find()) {
            String[] expression = definitions.group(2).trim().split("\\s*\\+\\s*");
            int id = spaces.containsKey(expression[0]) ? spaces.get(expression[0]) : Integer.parseInt(expression[0].replace("_", ""));
            if(expression.length == 2) id += Integer.parseInt(expression[1].replace("_", ""));
            assertTrue(id >= 0 && id <= Short.MAX_VALUE); assertNull("Duplicate persistent ID " + id, identities.put(id, definitions.group(1)));
        }
        assertTrue(identities.size() >= 125);
        assertEquals("MAT_MAGNESIUM", identities.get(1500)); assertEquals("MAT_PHOSPHORUS", identities.get(1531));
        assertEquals("MAT_SOLDER", identities.get(24002));
        assertEquals("MAT_OSMIUM_IRIDIUM", identities.get(24003));
        assertEquals("MAT_FERROCHROME", identities.get(24004));
        assertEquals("MAT_BRONZE", identities.get(51));
        NBTTagCompound saved = new NBTTagCompound(); saved.setIntArray("recipeStack", new int[] {1500, 137, 1531, 29, 24002, 81});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(saved, bytes);
        int[] decoded = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray())).getIntArray("recipeStack");
        assertEquals("MAT_MAGNESIUM", identities.get(decoded[0])); assertEquals(137, decoded[1]);
        assertEquals("MAT_PHOSPHORUS", identities.get(decoded[2])); assertEquals(29, decoded[3]);
        assertEquals("MAT_SOLDER", identities.get(decoded[4])); assertEquals(81, decoded[5]);
    }
}
