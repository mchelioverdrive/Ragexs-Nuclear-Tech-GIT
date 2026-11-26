package com.hbm.inventory.container;

import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.tileentity.machine.TileEntityWoodchipper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ContainerWoodchipper extends Container {
    private final TileEntityWoodchipper tile;

    public ContainerWoodchipper(InventoryPlayer player, TileEntityWoodchipper tile) {
        this.tile = tile;
        addSlotToContainer(new Slot(tile, TileEntityWoodchipper.INPUT, 46, 33));
        addSlotToContainer(new SlotCraftingOutput(player.player, tile, TileEntityWoodchipper.OUTPUT, 114, 33));
        for(int row = 0; row < 3; row++) {
            for(int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        }
        for(int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 8 + col * 18, 142));
    }

    @Override public boolean canInteractWith(EntityPlayer player) { return tile.isUseableByPlayer(player); }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if(slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if(index < 2) {
            if(!mergeItemStack(stack, 2, inventorySlots.size(), true)) return null;
        } else {
            if(!tile.isItemValidForSlot(TileEntityWoodchipper.INPUT, stack) || !mergeItemStack(stack, 0, 1, false)) return null;
        }
        if(stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, stack);
        return original;
    }
}
