package com.hbm.realsim;

import static com.hbm.inventory.OreDictManager.*;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.items.ItemEnums;
import com.hbm.blocks.ModBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.tile.IProcessHeatSource;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumCokeType;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.inventory.OreDictManager.DictFrame;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Owning-thread adapter for real tanks, inventories, power and calibrated process heaters. */
public final class ProcessMachine {
    /** One shared dry-cooler budget for a whole parallel plant, not one nameplate per lane. */
    public interface Cooling {
        long availableCoolingQuanta(long tick);
        void rejectHeatQuanta(long tick, long quanta);
    }
    private ProcessMachine() { }
    public static void sizeProcessBuffers(ProcessRecipe recipe, FluidTank[] inputs, FluidTank[] outputs) {
        if(recipe == null || inputs.length < recipe.inputs.length) return;
        int requiredOutputs = 0;
        for(String name : recipe.outputs) if(!name.startsWith("VENT_") && PhysicalRegistry.get(name).phase != PhysicalState.Phase.SOLID) requiredOutputs++;
        if(requiredOutputs > outputs.length) return;
        for(int i = 0; i < recipe.inputs.length; i++) inputs[i].resizePreservingContents(recipe.inputCapacities[i]);
        int fluid = 0;
        for(int i = 0; i < recipe.outputs.length; i++) if(recipe.outputCapacities[i] > 0) outputs[fluid++].resizePreservingContents(recipe.outputCapacities[i]);
    }
    public static IProcessHeatSource heater(TileEntity machine) {
        return machine instanceof api.hbm.tile.IProcessHeatReceiver
                ? ((api.hbm.tile.IProcessHeatReceiver) machine).getProcessHeatSource() : null;
    }
    /** For explicitly designed underside contacts only. No scanning, offsets to nearby heaters, or stack forwarding. */
    public static IProcessHeatSource heaterBelow(TileEntity machine, int receiverX, int receiverY, int receiverZ) {
        if(machine.getWorldObj() == null) return null;
        TileEntity below = machine.getWorldObj().getTileEntity(receiverX, receiverY - 1, receiverZ);
        return below instanceof IProcessHeatSource && ((IProcessHeatSource) below).canSupplyProcessHeat(net.minecraftforge.common.util.ForgeDirection.UP)
                ? (IProcessHeatSource) below : null;
    }
    public static boolean configure(ProcessBatch batch, ProcessRecipe selected, FluidTank[] outputs) {
        ProcessRecipe recipe = batch.active() ? batch.recipe() : selected;
        if(recipe == null) return false;
        int ports = 0;
        for(String name : recipe.outputs) if(!name.startsWith("VENT_") && PhysicalRegistry.get(name).phase != PhysicalState.Phase.SOLID) ports++;
        if(ports > outputs.length) return false; // A saved paid bill survives a smaller custom machine configuration.
        int fluid = 0;
        for(String name : recipe.outputs) if(!name.startsWith("VENT_") && PhysicalRegistry.get(name).phase != PhysicalState.Phase.SOLID) {
            com.hbm.inventory.fluid.FluidType type = Fluids.fromName(name);
            if(type == null || !type.getName().equals(name) || fluid >= outputs.length || !outputs[fluid++].configureForRecipe(type, 0)) return false;
        }
        return true;
    }
    public static boolean inputsReady(ProcessRecipe recipe, FluidTank[] inputs, ItemStack[] slots, int first, int last) {
        if(recipe == null || inputs.length < recipe.inputs.length) return false;
        for(int i = 0; i < recipe.inputs.length; i++) {
            if(!inputs[i].configureForRecipe(Fluids.fromName(recipe.inputs[i]), recipe.pressures[i])
                    || !inputs[i].getTankType().getName().equals(recipe.inputs[i]) || inputs[i].getPressure() != recipe.pressures[i]
                    || inputs[i].getFill() < recipe.quantities[i]) return false;
        }
        for(int feed = 0; feed < recipe.solidInputs.length; feed++) {
            int available = 0;
            if(slots != null) for(int i = first; i <= last; i++) if(matches(slots[i], recipe.solidInputs[feed])) available += slots[i].stackSize;
            if(available < recipe.solidCounts[feed]) return false;
        }
        return true;
    }

    private static net.minecraft.item.Item mineralItem(String name) {
        if(name.equals("ICE_BLOCK")) return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.ice);
        if(name.equals("PACKED_ICE_BLOCK")) return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.packed_ice);
        if(name.equals("SNOW_BLOCK")) return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.snow);
        if(name.equals("SNOWBALL_ITEM")) return net.minecraft.init.Items.snowball;
        if(name.equals("DRY_ICE_BLOCK")) return net.minecraft.item.Item.getItemFromBlock(com.hbm.blocks.ModBlocks.dry_ice);
        if(name.equals("SODIUM_ITEM")) return ModItems.powder_sodium;
        if(name.equals("POTASSIUM_ITEM")) return ModItems.potassium_powder;
        if(name.equals("IODINE_ITEM")) return ModItems.powder_iodine;
        if(name.equals("POTASSIUM_IODIDE_ITEM")) return ModItems.potassium_iodide_powder;
        if(name.equals("POTASSIUM_IODATE_ITEM")) return ModItems.potassium_iodate;
        if(name.equals("CARNALLITE_ITEM")) return ModItems.carnallite;
        if(name.equals("MAGNESIUM_CHLORIDE_ITEM")) return ModItems.magnesium_chloride;
        if(name.equals("STRONTIUM_ITEM")) return ModItems.powder_strontium;
        if(name.equals("GALLIUM_NUGGET_ITEM")) return ModItems.nugget_gallium;
        if(name.equals("ARSENIC_NUGGET_ITEM")) return ModItems.nugget_arsenic;
        if(name.equals("GAAS_NUGGET_ITEM")) return ModItems.nugget_gaas;
        if(name.equals("POTASH_LYE_ITEM")) return ModItems.caustic_potash;
        if(name.equals("SULFUR_DOSE_ITEM")) return ModItems.sulfur_dose;
        if(name.equals("BISPHENOL_A_ITEM")) return ModItems.bisphenol_a;
        if(name.equals("TPC_ITEM")) return ModItems.terephthaloyl_chloride;
        if(name.equals("PPD_ITEM")) return ModItems.phenylenediamine;
        if(name.equals("POLYMER_ITEM")) return ModItems.ingot_polymer;
        if(name.equals("SODIUM_SULFATE_ITEM")) return ModItems.sodium_sulfate;
        if(name.equals("RUBBER_ITEM")) return ModItems.ingot_rubber;
        if(name.equals("PVC_ITEM")) return ModItems.ingot_pvc;
        if(name.equals("BAKELITE_ITEM")) return ModItems.ingot_bakelite;
        if(name.equals("PC_ITEM")) return ModItems.ingot_pc;
        if(name.equals("ARAMID_ITEM")) return ModItems.plate_kevlar;
        if(name.equals("COLTAN_ORE_ITEM")) return ModItems.powder_coltan_ore;
        if(name.equals("COLTAN_CONCENTRATE_ITEM")) return ModItems.powder_coltan;
        if(name.equals("GANGUE_ITEM")) return ModItems.dust;
        if(name.equals("TANTALUM_OXIDE_ITEM")) return ModItems.tantalum_oxide;
        if(name.equals("NIOBIUM_OXIDE_ITEM")) return ModItems.niobium_oxide;
        if(name.equals("TANTALUM_ITEM")) return ModItems.gem_tantalium;
        if(name.equals("NIOBIUM_ITEM")) return ModItems.powder_niobium;
        if(name.equals("CALCIUM_ITEM")) return ModItems.ingot_calcium;
        if(name.equals("GALLIUM_TINY_ITEM")) return ModItems.powder_gallium_tiny;
        if(name.equals("ALUMINA_ITEM")) return ModItems.powder_alumina;
        if(name.equals("LYE_ITEM")) return ModItems.caustic_soda;
        if(name.equals("BAUXITE_ORE_ITEM")) return net.minecraft.item.Item.getItemFromBlock(com.hbm.blocks.ModBlocks.ore_aluminium);
        if(name.equals("SAWDUST_ITEM")) return ModItems.powder_sawdust;
        if(name.equals("PAPER_ITEM")) return net.minecraft.init.Items.paper;
        if(name.equals("COFFEE_POWDER_ITEM")) return ModItems.powder_coffee;
        if(name.equals("TEA_LEAF_ITEM")) return ModItems.tea_leaf;
        if(name.equals("FLUX_ITEM")) return ModItems.powder_flux;
        if(name.equals("CHLOROCALCITE_ITEM")) return ModItems.powder_chlorocalcite;
        if(name.equals("YELLOWCAKE_ITEM")) return ModItems.powder_yellowcake;
        if(name.equals("PLUTONIUM_ITEM")) return ModItems.powder_plutonium;
        if(name.equals("URANIUM_ORE_ITEM")) return net.minecraft.item.Item.getItemFromBlock(ModBlocks.ore_uranium);
        if(name.equals("REE_SLUDGE_ITEM")) return ModItems.REE_sludge;
        if(name.equals("REE_RESIDUE_ITEM")) return ModItems.rare_earth_residue;
        if(name.equals("U238_NUGGET_ITEM")) return ModItems.nugget_u238;
        if(name.equals("U235_NUGGET_ITEM")) return ModItems.nugget_u235;
        if(name.equals("PU_MIX_NUGGET_ITEM")) return ModItems.nugget_pu_mix;
        if(name.equals("SCANDIUM_OXIDE_ITEM")) return ModItems.scandium_oxide;
        if(name.equals("YTTRIUM_OXIDE_ITEM")) return ModItems.yttrium_oxide;
        if(name.equals("BROMINE_REAGENT_ITEM")) return ModItems.powder_bromine;
        if(name.equals("SCANDIUM_CHLORIDE_ITEM")) return ModItems.scandium_chloride;
        if(name.equals("YTTRIUM_CHLORIDE_ITEM")) return ModItems.yttrium_chloride;
        if(name.equals("SCANDIUM_NUGGET_ITEM")) return ModItems.scandium_nugget;
        if(name.equals("YTTRIUM_TINY_ITEM")) return ModItems.powder_yttrium_tiny;
        if(name.equals("TITANIUM_TRACE_ITEM")) return ModItems.titanium_trace_metals_slurry;
        if(name.equals("OSIR_CONCENTRATE_ITEM")) return ModItems.powder_impure_osmiridium;
        if(name.equals("RH_IR_CONCENTRATE_ITEM")) return ModItems.powder_rhodium_solution;
        if(name.equals("IRIDIUM_RESIDUE_ITEM")) return ModItems.iridium_rich_residue;
        if(name.equals("IRIDIUM_OXIDE_ITEM")) return ModItems.iridium_oxide;
        if(name.equals("OSMIUM_TINY_ITEM")) return ModItems.powder_osmium_tiny;
        if(name.equals("RHODIUM_ITEM")) return ModItems.powder_rhodium;
        if(name.equals("IRIDIUM_ITEM")) return ModItems.powder_iridium;
        if(name.equals("LEPIDOLITE_ITEM")) return ModItems.lepidolite;
        if(name.equals("LITHIUM_SULFATE_ITEM")) return ModItems.lithium_sulfate;
        if(name.equals("LITHIUM_CARBONATE_ITEM")) return ModItems.lithium_carbonate;
        if(name.equals("POTASSIUM_SULFATE_ITEM")) return ModItems.potassium_sulfate;
        if(name.equals("SODIUM_NITRATE_ITEM")) return ModItems.sodium_nitrate;
        if(name.equals("POTASSIUM_NITRATE_ITEM")) return ModItems.niter;
        if(name.equals("AMMONIUM_NITRATE_ITEM")) return ModItems.ammonium_nitrate;
        if(name.equals("AMMONIUM_CHLORIDE_ITEM")) return ModItems.ammonium_chloride;
        if(name.equals("FERTILIZER_ITEM")) return ModItems.powder_fertilizer;
        if(name.equals("LITHIUM_ITEM")) return ModItems.lithium;
        if(name.equals("CHARGED_MATRIX_ITEM")) return ModItems.pellet_charged;
        if(name.equals("POLLUCITE_ITEM")) return ModItems.powder_pollucite;
        if(name.equals("CESIUM_SALT_ITEM")) return ModItems.cesium_salt;
        if(name.equals("CESIUM_ITEM")) return ModItems.powder_caesium;
        if(name.equals("RUBIDIUM_ITEM")) return ModItems.rubidium_ingot;
        if(name.equals("RUBIDIUM_SALT_ITEM")) return ModItems.rubidiumsalt;
        if(name.equals("BORIC_ACID_ITEM")) return ModItems.boric_acid;
        if(name.equals("CEMENT_ITEM")) return ModItems.powder_cement;
        if(name.equals("AGGREGATE_COARSE_ITEM")) return ModItems.aggregate_coarse;
        if(name.equals("AGGREGATE_FINE_ITEM")) return ModItems.aggregate_fine;
        if(name.equals("CONCRETE_ITEM")) return net.minecraft.item.Item.getItemFromBlock(ModBlocks.concrete_smooth);
        if(name.equals("FIBER_CONCRETE_ITEM")) return net.minecraft.item.Item.getItemFromBlock(ModBlocks.concrete_asbestos);
        if(name.equals("DUCRETE_ITEM")) return net.minecraft.item.Item.getItemFromBlock(ModBlocks.ducrete_smooth);
        if(name.equals("ASPHALT_ITEM")) return net.minecraft.item.Item.getItemFromBlock(ModBlocks.asphalt);
        if(name.equals("SOIL_ITEM")) return net.minecraft.item.Item.getItemFromBlock(Blocks.dirt);
        if(name.equals("QUICKLIME_ITEM")) return ModItems.quicklime;
        if(name.equals("STRONTIUM_OXIDE_ITEM")) return ModItems.powder_strontium_oxide;
        if(name.equals("STRONTIUM_CHLORIDE_ITEM")) return ModItems.strontium_chloride;
        if(name.equals("BARIUM_SULFIDE_ITEM")) return ModItems.barium_sulfide;
        if(name.equals("BARIUM_CARBONATE_ITEM")) return ModItems.barium_carbonate;
        if(name.equals("BARIUM_OXIDE_ITEM")) return ModItems.barium_oxide;
        if(name.equals("BARIUM_ALUMINATE_ITEM")) return ModItems.barium_aluminate;
        if(name.equals("BARIUM_ITEM")) return ModItems.ingot_barium;
        if(name.equals("ALUMINIUM_ITEM")) return ModItems.powder_aluminium;
        if(name.equals("ALUMINA_ITEM")) return ModItems.powder_alumina;
        return null;
    }
    public static com.hbm.inventory.RecipesCommon.AStack mineralFeed(String name, int count) {
        net.minecraft.item.Item item = mineralItem(name);
        return item == null ? null : new com.hbm.inventory.RecipesCommon.ComparableStack(item, count);
    }
    public static AStack feed(String name, int count) {
        if(name.startsWith("ITEM:")) return new ComparableStack(SpecialistProcesses.stack(name, count));
        if(name.equals("CHAR_ITEM")) return new ComparableStack(ModItems.powder_ash, count, EnumAshType.SOOT.ordinal());
        if(name.equals("GLASS_PANE_ITEM")) return new OreDictStack(KEY_ANYPANE, count);
        if(name.equals("CARBON_WIRE_ITEM")) return new OreDictStack(CARBON.wireFine(), count);
        if(name.equals("ASBESTOS_ITEM")) return new OreDictStack(ASBESTOS.ingot(), count);
        if(name.equals("DU_BILLET_ITEM")) return new OreDictStack(U238.billet(), count); return com.hbm.realsim.ProcessMachine.mineralFeed(name, count) != null ? com.hbm.realsim.ProcessMachine.mineralFeed(name, count) : name.equals("POTASH_LYE_ITEM") ? new ComparableStack(ModItems.caustic_potash, count) : name.equals("POTASH_ITEM") ? new ComparableStack(ModItems.powder_potash, count) : name.equals("RUTHENIUM_RESIDUE_ITEM") ? new ComparableStack(ModItems.ruthenium_residue, count) : name.equals("SODIUM_SULFATE_ITEM") ? new ComparableStack(ModItems.sodium_sulfate, count) : name.equals("SODIUM_SULFIDE_ITEM") ? new ComparableStack(ModItems.sodium_sulfide, count) : name.equals("GADOLINIUM_ITEM") ? new ComparableStack(ModItems.powder_gadolinium, count) : name.equals("FUEL_ADDITIVE_DOSE_ITEM") ? new ComparableStack(ModItems.fuel_additive_dose, count) : name.equals("PROPPANT_ITEM") ? new OreDictStack("dustQuartz", count) : name.equals("LYE_ITEM") ? new ComparableStack(ModItems.caustic_soda, count) : name.equals("FLUORITE_ITEM") ? new ComparableStack(ModItems.crystal_fluorite, count) : name.equals("AMMONIUM_CHLORIDE_ITEM") ? new ComparableStack(ModItems.ammonium_chloride, count) : name.equals("SALT_ITEM") ? new ComparableStack(ModItems.itemsalt, count) : name.equals("BORON_ITEM") ? new ComparableStack(ModItems.powder_boron, count) : name.equals("SULFUR_ITEM") ? new OreDictStack(S.dust(), count) : name.equals("BIOMASS_ITEM") ? new ComparableStack(ModItems.biomass, count) : name.equals("FUEL_ADDITIVE_ITEM") ? new ComparableStack(ModItems.fuel_additive, count)
                    : name.equals("LEAD_ITEM") ? new OreDictStack(PB.dust(), count)
                    : name.equals("OIL_SAND_BLOCK") ? new ComparableStack(ModBlocks.ore_oil_sand, count)
                    : name.equals("SUGAR_ITEM") ? new ComparableStack(Items.sugar, count)
                    : name.equals("FISH_FEED_ITEM") ? new ComparableStack(Items.fish, count, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
                    : name.equals("SEED_FEED_ITEM") ? new ComparableStack(Blocks.double_plant, count, 0)
                    : name.startsWith("TAR_") ? new ComparableStack(ModItems.oil_tar, count, (name.equals("TAR_CRUDE_ITEM") ? ItemEnums.EnumTarType.CRUDE : name.equals("TAR_COAL_ITEM") ? ItemEnums.EnumTarType.COAL : name.equals("TAR_WOOD_ITEM") ? ItemEnums.EnumTarType.WOOD : ItemEnums.EnumTarType.CRACK).ordinal())
                    : name.equals("WOOD_LOG") ? new OreDictStack(KEY_LOG, count)
                    : name.equals("COAL_ITEM") ? new OreDictStack(COAL.dust(), count) : unsupportedFeed(name); }
    private static AStack unsupportedFeed(String name) { throw new IllegalArgumentException("No physical ingredient adapter: " + name); }
    /** Registration boundary shared by stock and configured physical recipes. */
    public static AStack feed(ProcessRecipe bill, int index, String family) {
        String identity = bill.solidInputs[index];
        try {
            ProcessRecipe.resolved(identity);
            AStack result = feed(identity, bill.solidCounts[index]);
            if(result instanceof ComparableStack && (((ComparableStack) result).item == null || ((ComparableStack) result).item == ModItems.nothing))
                throw new IllegalArgumentException("Uninitialized native item");
            return result;
        } catch(RuntimeException failure) {
            throw new IllegalArgumentException(family + " / " + bill.id + " / feed " + identity + ": " + failure.getMessage(), failure);
        }
    }
    public static boolean matches(ItemStack stack, String identity) {
        if(stack == null) return false;
        if(identity.equals("GLASS_PANE_ITEM")) return hasOre(stack, KEY_ANYPANE);
        if(identity.equals("CARBON_WIRE_ITEM")) return hasOre(stack, CARBON.wireFine());
        if(identity.equals("ASBESTOS_ITEM")) return hasOre(stack, ASBESTOS.ingot());
        if(identity.equals("DU_BILLET_ITEM")) return hasOre(stack, U238.billet());
        if(identity.startsWith("ITEM:")) return stack.isItemEqual(SpecialistProcesses.stack(identity, 1));
        if(identity.equals("SODIUM_ITEM") && hasOre(stack, NA.dust())) return true;
        if(mineralItem(identity) != null) return stack.getItem() == mineralItem(identity);
        if(identity.equals("SULFUR_ITEM")) return stack.getItem() == ModItems.sulfur || hasOre(stack, "dustSulfur");
        if(identity.equals("SALT_ITEM")) return stack.getItem() == ModItems.itemsalt;
        if(identity.equals("BORON_ITEM")) return stack.getItem() == ModItems.powder_boron;
        if(identity.equals("FLUORITE_ITEM")) return stack.getItem() == ModItems.crystal_fluorite;
        if(identity.equals("AMMONIUM_CHLORIDE_ITEM")) return stack.getItem() == ModItems.ammonium_chloride;
        if(identity.equals("SODIUM_SULFATE_ITEM")) return stack.getItem() == ModItems.sodium_sulfate;
        if(identity.equals("SODIUM_SULFIDE_ITEM")) return stack.getItem() == ModItems.sodium_sulfide;
        if(identity.equals("CHAR_ITEM")) return stack.getItem() == ModItems.powder_ash && stack.getItemDamage() == EnumAshType.SOOT.ordinal();
        if(identity.equals("GADOLINIUM_ITEM")) return stack.getItem() == ModItems.powder_gadolinium;
        if(identity.equals("RUTHENIUM_RESIDUE_ITEM")) return stack.getItem() == ModItems.ruthenium_residue;
        if(identity.equals("LYE_ITEM")) return stack.getItem() == ModItems.caustic_soda;
        if(identity.equals("PROPPANT_ITEM")) return stack.getItem() == ModItems.powder_quartz || hasOre(stack, "dustQuartz");
        if(identity.equals("POTASH_LYE_ITEM")) return stack.getItem() == ModItems.caustic_potash;
        if(identity.equals("POTASH_ITEM")) return stack.getItem() == ModItems.powder_potash;
        if(FuelSuitability.has(identity, MaterialRole.INTERMEDIATE) && !identity.startsWith("TAR_") && !identity.equals("SAPLING_ITEM")) {
            SolidFuel feed = SolidFuel.resolve(stack);
            return feed != null && feed.units == 1 && feed.identity.equals(identity);
        }
        if(identity.equals("SAPLING_ITEM")) for(String key : com.hbm.util.ItemStackUtil.getOreDictNames(stack)) if(key.equals("treeSapling")) return true;
        if(identity.equals("FUEL_ADDITIVE_ITEM")) return stack.getItem() == ModItems.fuel_additive;
        if(identity.equals("FUEL_ADDITIVE_DOSE_ITEM")) return stack.getItem() == ModItems.fuel_additive_dose;
        if(identity.equals("SUGAR_ITEM")) return stack.getItem() == net.minecraft.init.Items.sugar;
        if(identity.equals("OIL_SAND_BLOCK")) return stack.getItem() == net.minecraft.item.Item.getItemFromBlock(com.hbm.blocks.ModBlocks.ore_oil_sand);
        if(identity.equals("LEAD_ITEM")) {
            for(String key : com.hbm.util.ItemStackUtil.getOreDictNames(stack)) if(key.equals("dustLead") || key.equals("ingotLead")) return true;
            return false;
        }
        if(identity.equals("FISH_FEED_ITEM")) return stack.getItem() == net.minecraft.init.Items.fish;
        if(identity.equals("SEED_FEED_ITEM")) return stack.getItem() == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.double_plant) && stack.getItemDamage() == 0;
        if(identity.startsWith("TAR_")) {
            com.hbm.items.ItemEnums.EnumTarType type = identity.equals("TAR_CRUDE_ITEM") ? com.hbm.items.ItemEnums.EnumTarType.CRUDE
                    : identity.equals("TAR_COAL_ITEM") ? com.hbm.items.ItemEnums.EnumTarType.COAL
                    : identity.equals("TAR_WOOD_ITEM") ? com.hbm.items.ItemEnums.EnumTarType.WOOD : com.hbm.items.ItemEnums.EnumTarType.CRACK;
            return stack.getItem() == ModItems.oil_tar && stack.getItemDamage() == type.ordinal();
        }
        return false;
    }
    private static boolean hasOre(ItemStack stack, String ore) {
        for(String key : com.hbm.util.ItemStackUtil.getOreDictNames(stack)) if(key.equals(ore)) return true;
        return false;
    }
    public static ItemStack solid(String name, int count) {
        if(name.startsWith("ITEM:")) return SpecialistProcesses.stack(name, count);
        ItemStack stack;
        if(mineralItem(name) != null) stack = new ItemStack(mineralItem(name));
        else if(name.equals("PETCOKE_ITEM")) stack = DictFrame.fromOne(ModItems.coke, EnumCokeType.PETROLEUM);
        else if(name.equals("COKE_ITEM")) stack = DictFrame.fromOne(ModItems.coke, EnumCokeType.COAL);
        else if(name.equals("LIGNITE_COKE_ITEM")) stack = DictFrame.fromOne(ModItems.coke, EnumCokeType.LIGNITE);
        else if(name.equals("CHAR_ITEM")) stack = DictFrame.fromOne(ModItems.powder_ash, EnumAshType.SOOT);
        else if(name.equals("ASH_ITEM")) stack = new ItemStack(ModItems.powder_ash);
        else if(name.equals("WOOD_ASH_ITEM")) stack = DictFrame.fromOne(ModItems.powder_ash, EnumAshType.WOOD);
        else if(name.equals("SULFUR_ITEM")) stack = new ItemStack(ModItems.sulfur);
        else if(name.equals("CHARCOAL_ITEM")) stack = new ItemStack(net.minecraft.init.Items.coal, 1, 1);
        else if(name.equals("BIOMASS_ITEM")) stack = new ItemStack(ModItems.biomass);
        else if(name.equals("TAR_CRUDE_ITEM")) stack = DictFrame.fromOne(ModItems.oil_tar, com.hbm.items.ItemEnums.EnumTarType.CRUDE);
        else if(name.equals("SAND_BLOCK")) stack = new ItemStack(net.minecraft.init.Blocks.sand);
        else if(name.equals("FUEL_ADDITIVE_ITEM")) stack = new ItemStack(ModItems.fuel_additive);
        else if(name.equals("FUEL_ADDITIVE_DOSE_ITEM")) stack = new ItemStack(ModItems.fuel_additive_dose);
        else if(name.equals("SALT_ITEM")) stack = new ItemStack(ModItems.itemsalt);
        else if(name.equals("BORON_ITEM")) stack = new ItemStack(ModItems.powder_boron);
        else if(name.equals("AMMONIUM_NITRATE_ITEM")) stack = new ItemStack(ModItems.ammonium_nitrate);
        else if(name.equals("SODIUM_SULFIDE_ITEM")) stack = new ItemStack(ModItems.sodium_sulfide);
        else if(name.equals("GADOLINIUM_ITEM")) stack = new ItemStack(ModItems.powder_gadolinium);
        else if(name.equals("REFINED_GADOLINIUM_ITEM")) stack = new ItemStack(ModItems.powder_gadolinium2);
        else if(name.equals("RUTHENIUM_NUGGET_ITEM")) stack = new ItemStack(ModItems.nugget_ruthenium);
        else if(name.equals("GYPSUM_ITEM")) stack = new ItemStack(ModItems.gypsum);
        else if(name.equals("LYE_ITEM")) stack = new ItemStack(ModItems.caustic_soda);
        else if(name.equals("POTASH_LYE_ITEM")) stack = new ItemStack(ModItems.caustic_potash);
        else if(name.equals("RUBIDIUM_SALT_ITEM")) stack = new ItemStack(ModItems.rubidiumsalt);
        else throw new IllegalArgumentException("No physical solid output adapter: " + name);
        stack.stackSize = count; return stack;
    }
    public static boolean add(ItemStack[] slots, int first, int last, ItemStack product) {
        for(int i = first; i <= last && product.stackSize > 0; i++) {
            if(slots[i] != null && (!slots[i].isItemEqual(product) || !ItemStack.areItemStackTagsEqual(slots[i], product))) continue;
            int room = product.getMaxStackSize() - (slots[i] == null ? 0 : slots[i].stackSize);
            int amount = Math.min(room, product.stackSize);
            if(amount <= 0) continue;
            if(slots[i] == null) { slots[i] = product.copy(); slots[i].stackSize = amount; } else slots[i].stackSize += amount;
            product.stackSize -= amount;
        }
        return product.stackSize == 0;
    }
    public static boolean outputReady(ProcessBatch batch, ProcessRecipe recipe, FluidTank[] outputs, ItemStack[] slots, int first, int last) {
        if(recipe == null || !configure(batch, recipe, outputs)) return false;
        boolean solidOutput = false;
        for(int i = 0; i < recipe.outputs.length; i++) if(PhysicalRegistry.get(recipe.outputs[i]).phase == PhysicalState.Phase.SOLID
                && batch.products.preview(recipe.outputs[i], batch.pendingMass(recipe, i)) > 0) solidOutput = true;
        int fluid = 0; ItemStack[] preview = slots == null || !solidOutput ? null : slots.clone();
        if(preview != null) for(int i = first; i <= last; i++) if(preview[i] != null) preview[i] = preview[i].copy();
        for(int i = 0; i < recipe.outputs.length; i++) {
            String name = recipe.outputs[i]; long amount = batch.products.preview(name, batch.pendingMass(recipe, i));
            if(name.startsWith("VENT_")) continue; // Explicit environmental loss; never an export or heat credit.
            if(PhysicalRegistry.get(name).phase == PhysicalState.Phase.SOLID) {
                if(amount > 0 && (preview == null || !add(preview, first, last, solid(name, Math.toIntExact(amount))))) return false;
            } else {
                FluidTank tank = outputs[fluid++];
                if(amount > (long) tank.getMaxFill() - tank.getFill()) return false;
            }
        }
        return true;
    }
    public static boolean reserveInputs(ProcessBatch batch, ProcessRecipe selected, FluidTank[] inputs,
            FluidTank[] outputs, ItemStack[] slots, int inputFirst, int inputLast, int outputFirst, int outputLast) {
        if(batch.active() || !inputsReady(selected, inputs, slots, inputFirst, inputLast)
                || !outputReady(batch, selected, outputs, slots, outputFirst, outputLast)) return false;
        // Validate and freeze before debiting; a bad custom bill cannot partially consume feed.
        ProcessRecipe frozen = selected.snapshot();
        for(int i = 0; i < frozen.inputs.length; i++) inputs[i].setFill(inputs[i].getFill() - frozen.quantities[i]);
        for(int feed = 0; feed < frozen.solidInputs.length; feed++) {
            int left = frozen.solidCounts[feed];
            for(int i = inputFirst; i <= inputLast && left > 0; i++) if(matches(slots[i], frozen.solidInputs[feed])) {
                int amount = Math.min(left, slots[i].stackSize); slots[i].stackSize -= amount; left -= amount; if(slots[i].stackSize == 0) slots[i] = null;
            }
        }
        batch.reserve(frozen); return true;
    }
    public static boolean step(TileEntity machine, ProcessBatch batch, ProcessRecipe selected, FluidTank[] inputs,
            FluidTank[] outputs, ItemStack[] slots, int inputFirst, int inputLast, int outputFirst, int outputLast) {
        return stepWithHeater(machine, batch, selected, inputs, outputs, slots, inputFirst, inputLast, outputFirst, outputLast, null);
    }
    public static boolean stepWithHeater(TileEntity machine, ProcessBatch batch, ProcessRecipe selected, FluidTank[] inputs,
            FluidTank[] outputs, ItemStack[] slots, int inputFirst, int inputLast, int outputFirst, int outputLast, IProcessHeatSource integratedHeater) {
        if(machine.getWorldObj() == null || machine.getWorldObj().isRemote) return false;
        sizeProcessBuffers(batch.active() ? batch.recipe() : selected, inputs, outputs);
        boolean changed = false;
        if(!batch.active()) {
            if(!reserveInputs(batch, selected, inputs, outputs, slots, inputFirst, inputLast, outputFirst, outputLast)) return false;
            changed = true;
        }
        ProcessRecipe recipe = batch.recipe();
        if(!outputReady(batch, recipe, outputs, slots, outputFirst, outputLast)) return changed;
        IEnergyReceiverMK2 power = machine instanceof IEnergyReceiverMK2 ? (IEnergyReceiverMK2) machine : null;
        IProcessHeatSource heater = integratedHeater == null ? heater(machine) : integratedHeater;
        long availableHeat = heater != null && heater.getProcessTemperatureC() >= recipe.temperatureC ? heater.getAvailableProcessHeatQuanta(machine.getWorldObj().getTotalWorldTime()) : 0;
        long now = machine.getWorldObj().getTotalWorldTime();
        long auxiliary = machine instanceof IMachineInfo ? ((IMachineInfo) machine).getProcessAuxiliaryWatts(recipe) / 10L : 0L;
        Cooling cooling = machine instanceof Cooling ? (Cooling) machine : null;
        long rejection = PetroleumProcesses.ceilDiv(recipe.rejectedWatts, 10L);
        if((auxiliary == 0L || power != null && power.getStoredEnergyQuanta() >= auxiliary)
                && (cooling == null || cooling.availableCoolingQuanta(now) >= rejection)
                && batch.canAdvance(now, power == null ? 0 : power.getStoredEnergyQuanta() - auxiliary, availableHeat)) {
            long work = Math.addExact(batch.nextWork(), auxiliary), heat = batch.nextHeat();
            if(work > 0) power.setStoredEnergyQuanta(power.getStoredEnergyQuanta() - work);
            if(heat > 0 && heater.drawProcessHeatQuanta(now, heat) != heat) throw new IllegalStateException("Process heat debit changed during commit");
            if(cooling != null) cooling.rejectHeatQuanta(now, rejection);
            batch.advance(now); batch.recordDraw(now, work); changed = true;
        }
        if(batch.complete() || batch.hasContinuousProducts()) {
            int[] quantities = batch.complete() ? batch.finish() : batch.releaseProgress(); int fluid = 0;
            for(int i = 0; i < quantities.length; i++) {
                if(recipe.outputs[i].startsWith("VENT_")) continue;
                if(PhysicalRegistry.get(recipe.outputs[i]).phase == PhysicalState.Phase.SOLID) {
                    if(quantities[i] > 0 && !add(slots, outputFirst, outputLast, solid(recipe.outputs[i], quantities[i]))) throw new IllegalStateException("Output capacity changed during process commit");
                } else { FluidTank output = outputs[fluid++]; output.setFill(output.getFill() + quantities[i]); }
            }
            changed = true;
        }
        if(changed) machine.markDirty();
        return changed;
    }
}
