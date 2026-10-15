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
        for(String name : new String[] {"coke", "powder_coke", "lignite", "powder_lignite", "powder_coal", "briquette", "biomass", "biomass_compressed", "powder_sawdust", "powder_ash", "scrap", "dust", "sulfur", "solid_fuel", "rocket_fuel", "ingot_polymer", "ingot_pvc"}) {
            Field field = ModItems.class.getField(name); previous.put(field, field.get(null)); field.set(null, new Item());
        }

    }
    @After public void restoreNativeItems() throws Exception {
        for(Map.Entry<Field, Object> entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
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
        assertEquals("MAT_BRONZE", identities.get(51));
        NBTTagCompound saved = new NBTTagCompound(); saved.setIntArray("recipeStack", new int[] {1500, 137, 1531, 29, 24002, 81});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(saved, bytes);
        int[] decoded = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray())).getIntArray("recipeStack");
        assertEquals("MAT_MAGNESIUM", identities.get(decoded[0])); assertEquals(137, decoded[1]);
        assertEquals("MAT_PHOSPHORUS", identities.get(decoded[2])); assertEquals(29, decoded[3]);
        assertEquals("MAT_SOLDER", identities.get(decoded[4])); assertEquals(81, decoded[5]);
    }
}
