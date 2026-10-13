package com.hbm.realsim;

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
    private ProcessMachine() { }
    public static IProcessHeatSource heater(TileEntity machine) {
        if(machine.getWorldObj() == null) return null;
        int y = machine.yCoord;
        // A stacked fractionator conveys feed/vapor; its base supplies the top's paid utilities.
        if(machine instanceof com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower) {
            while(machine.getWorldObj().getTileEntity(machine.xCoord, y - 3, machine.zCoord) instanceof com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower) y -= 3;
        }
        TileEntity below = machine.getWorldObj().getTileEntity(machine.xCoord, y - 1, machine.zCoord);
        return below instanceof IProcessHeatSource ? (IProcessHeatSource) below : null;
    }
    public static boolean configure(ProcessBatch batch, ProcessRecipe selected, FluidTank[] outputs) {
        ProcessRecipe recipe = batch.active() ? batch.recipe() : selected;
        if(recipe == null) return false;
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
        int available = 0;
        if(recipe.solidCount > 0 && slots != null) for(int i = first; i <= last; i++) if(matches(slots[i], recipe.solidInput)) available += slots[i].stackSize;
        return recipe.solidCount == 0 || available >= recipe.solidCount;
    }
    private static boolean matches(ItemStack stack, String identity) {
        if(stack == null) return false;
        if(identity.equals("BIOMASS_ITEM")) return stack.getItem() == ModItems.biomass;
        if(identity.equals("COAL_BRIQUETTE_ITEM") || identity.equals("LIGNITE_BRIQUETTE_ITEM"))
            return stack.getItem() == ModItems.briquette && stack.getItemDamage() == (identity.equals("COAL_BRIQUETTE_ITEM")
                    ? com.hbm.items.ItemEnums.EnumBriquetteType.COAL.ordinal() : com.hbm.items.ItemEnums.EnumBriquetteType.LIGNITE.ordinal());
        if(identity.equals("LIGNITE_ITEM")) for(String key : com.hbm.util.ItemStackUtil.getOreDictNames(stack))
            if(key.equals("gemLignite") || key.equals("dustLignite")) return true;
        if(identity.equals("SAPLING_ITEM")) for(String key : com.hbm.util.ItemStackUtil.getOreDictNames(stack)) if(key.equals("treeSapling")) return true;
        if(identity.equals("WOOD_BRIQUETTE_ITEM")) return stack.getItem() == ModItems.briquette
                && stack.getItemDamage() == com.hbm.items.ItemEnums.EnumBriquetteType.WOOD.ordinal();
        if(identity.equals("FUEL_ADDITIVE_ITEM")) return stack.getItem() == ModItems.fuel_additive;
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
        return identity.equals(Combustion.solidIdentity(stack));
    }
    public static ItemStack solid(String name, int count) {
        ItemStack stack;
        if(name.equals("PETCOKE_ITEM")) stack = DictFrame.fromOne(ModItems.coke, EnumCokeType.PETROLEUM);
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
        else throw new IllegalArgumentException("No physical solid output adapter: " + name);
        stack.stackSize = count; return stack;
    }
    private static boolean add(ItemStack[] slots, int first, int last, ItemStack product) {
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
                && batch.products.preview(recipe.outputs[i], recipe.productMass[i]) > 0) solidOutput = true;
        int fluid = 0; ItemStack[] preview = slots == null || !solidOutput ? null : slots.clone();
        if(preview != null) for(int i = first; i <= last; i++) if(preview[i] != null) preview[i] = preview[i].copy();
        for(int i = 0; i < recipe.outputs.length; i++) {
            String name = recipe.outputs[i]; long amount = batch.products.preview(name, recipe.productMass[i]);
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
        int left = frozen.solidCount;
        if(left > 0) for(int i = inputFirst; i <= inputLast && left > 0; i++) if(matches(slots[i], frozen.solidInput)) {
            int amount = Math.min(left, slots[i].stackSize); slots[i].stackSize -= amount; left -= amount; if(slots[i].stackSize == 0) slots[i] = null;
        }
        batch.reserve(frozen); return true;
    }
    public static boolean step(TileEntity machine, ProcessBatch batch, ProcessRecipe selected, FluidTank[] inputs,
            FluidTank[] outputs, ItemStack[] slots, int inputFirst, int inputLast, int outputFirst, int outputLast) {
        if(machine.getWorldObj() == null || machine.getWorldObj().isRemote) return false;
        boolean changed = false;
        if(!batch.active()) {
            if(!reserveInputs(batch, selected, inputs, outputs, slots, inputFirst, inputLast, outputFirst, outputLast)) return false;
            changed = true;
        }
        ProcessRecipe recipe = batch.recipe();
        if(!outputReady(batch, recipe, outputs, slots, outputFirst, outputLast)) return changed;
        IEnergyReceiverMK2 power = machine instanceof IEnergyReceiverMK2 ? (IEnergyReceiverMK2) machine : null;
        IProcessHeatSource heater = heater(machine);
        long availableHeat = heater != null && heater.getProcessTemperatureC() >= recipe.temperatureC ? heater.getHeatStored() : 0;
        long now = machine.getWorldObj().getTotalWorldTime();
        if(batch.canAdvance(now, power == null ? 0 : power.getStoredEnergyQuanta(), availableHeat)) {
            long work = batch.nextWork(), heat = batch.nextHeat();
            if(work > 0) power.setStoredEnergyQuanta(power.getStoredEnergyQuanta() - work);
            if(heat > 0) heater.useUpHeat(Math.toIntExact(heat));
            batch.advance(now); changed = true;
        }
        if(batch.complete()) {
            int[] quantities = batch.finish(); int fluid = 0;
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
