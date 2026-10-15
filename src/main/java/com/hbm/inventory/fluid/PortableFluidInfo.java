package com.hbm.inventory.fluid;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.hbm.inventory.FluidContainer;
import com.hbm.inventory.FluidContainerRegistry;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Registry adapter, including plain Item empties and custom/vanilla registered containers. */
@SideOnly(Side.CLIENT)
public final class PortableFluidInfo {
    private PortableFluidInfo() { }
    public static void append(ItemStack stack, List<String> lines) {
        ItemStack identity = stack;
        if(stack.hasTagCompound() && stack.getTagCompound().hasKey("display")) {
            identity = stack.copy();
            identity.getTagCompound().removeTag("display"); // Cosmetic names do not change tooltip contents.
            if(identity.getTagCompound().hasNoTags()) identity.setTagCompound(null);
        }
        FluidContainer filled = FluidContainerRegistry.filledDescriptor(identity);
        if(filled != null && filled.emptyContainer != null) {
            FluidInfo.appendContainer(lines, filled.type, filled.amount(stack), filled.content, 0);
            if(stack.getItem() instanceof net.minecraft.item.ItemBucket) lines.add("Places one fluid block");
            return;
        }
        List<FluidContainer> candidates = FluidContainerRegistry.itemDescriptors(stack);
        Set<String> capacities = new HashSet<>();
        if(candidates != null) for(FluidContainer container : candidates) {
            ItemStack empty = container.emptyContainer;
            if(empty != null && empty.isItemEqual(identity) && ItemStack.areItemStackTagsEqual(empty, identity)) {
                String capacity = "Capacity: " + FluidInfo.amount(container.type, container.content);
                if(capacities.add(capacity)) lines.add(capacity);
            }
        }
        if(!capacities.isEmpty()) {
            if(stack.getItem() == net.minecraft.init.Items.bucket) lines.add("Places one fluid block");
            if(stack.getItem() == com.hbm.items.ModItems.gas_empty) lines.add("Accepted: registered gas/cryogenic fills; phase follows fluid identity");
            else if(stack.getItem() == com.hbm.items.ModItems.canister_empty) lines.add("Accepted: fluids with a canister definition");
            else if(stack.getItem() == com.hbm.items.ModItems.fluid_tank_lead_empty) lines.add("Lead-shielded fluid vessel");
            else lines.add("Accepts compatible fluids");
            return;
        }
        // Forge-only containers have no RTM assay; never synthesize one from their names.
        if(stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
            net.minecraftforge.fluids.IFluidContainerItem item = (net.minecraftforge.fluids.IFluidContainerItem) stack.getItem();
            net.minecraftforge.fluids.FluidStack fluid = item.getFluid(stack);
            if(fluid != null) lines.add(fluid.getLocalizedName());
            lines.add(String.format(java.util.Locale.US, "%,d / %,d mB", fluid == null ? 0 : fluid.amount, item.getCapacity(stack)));
        } else {
            net.minecraftforge.fluids.FluidStack fluid = net.minecraftforge.fluids.FluidContainerRegistry.getFluidForFilledItem(stack);
            if(fluid != null) {
                lines.add(fluid.getLocalizedName());
                lines.add(String.format(java.util.Locale.US, "%,d / %,d mB", fluid.amount, fluid.amount));
            }
        }
    }
}
