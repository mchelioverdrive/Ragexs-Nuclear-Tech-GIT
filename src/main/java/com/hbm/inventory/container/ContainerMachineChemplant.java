package com.hbm.inventory.container;

import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.inventory.SlotTakeOnly;
import com.hbm.tileentity.machine.TileEntityMachineChemplant;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerMachineChemplant extends Container {
    public static final int PROCESS_X = 100, PROCESS_Y = 96, PROCESS_WIDTH = 56, PROCESS_HEIGHT = 10;
    public static final int TEMPLATE_X = 120;
    private final TileEntityMachineChemplant chemplant;
    public ContainerMachineChemplant(InventoryPlayer player, final TileEntityMachineChemplant tile) {
        chemplant = tile;
        int[][] positions = new int[29][2];
        positions[0] = new int[] {98, 26};
        for(int i = 1; i <= 3; i++) positions[i] = new int[] {98 + i * 18, 26};
        positions[4] = new int[] {TEMPLATE_X, 62};
        for(int i = 0; i < 4; i++) {
            positions[5 + i] = new int[] {170 + i * 18, 112};
            positions[13 + i] = new int[] {8 + i * 18, 112};
            positions[TileEntityMachineChemplant.inputContainerSlot(i)] = new int[] {8 + i * 18, 72};
            positions[TileEntityMachineChemplant.inputReturnSlot(i)] = new int[] {8 + i * 18, 90};
            positions[TileEntityMachineChemplant.outputContainerSlot(i)] = new int[] {170 + i * 18, 72};
            positions[TileEntityMachineChemplant.outputReturnSlot(i)] = new int[] {170 + i * 18, 90};
        }
        // Container indices stay identical to persisted inventory indices, including appended ports.
        for(int i = 0; i < positions.length; i++) {
            final int index = i;
            Slot slot;
            if(i >= 5 && i <= 8) slot = new SlotCraftingOutput(player.player, tile, i, positions[i][0], positions[i][1]);
            else if(i == 11 || i == 12 || i == 19 || i == 20 || i == 23 || i == 24 || i == 27 || i == 28)
                slot = new SlotTakeOnly(tile, i, positions[i][0], positions[i][1]);
            else slot = new Slot(tile, i, positions[i][0], positions[i][1]) {
                @Override public boolean isItemValid(ItemStack stack) { return tile.isItemValidForSlot(index, stack); }
            };
            addSlotToContainer(slot);
        }
        for(int row = 0; row < 3; row++) for(int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player, col + row * 9 + 9, 44 + col * 18, 158 + row * 18));
        for(int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 44 + col * 18, 216));
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot source = (Slot) inventorySlots.get(index);
        if(source == null || !source.getHasStack()) return null;
        ItemStack stack = source.getStack(), original = stack.copy();
        if(index < 29) {
            if(!mergeItemStack(stack, 29, inventorySlots.size(), true)) return null;
        } else {
            // Route service containers before ordinary reactants; all four channels are considered.
            boolean moved = false;
            for(int channel = 0; channel < 4 && stack.stackSize > 0; channel++) {
                int slot = TileEntityMachineChemplant.inputContainerSlot(channel);
                if(chemplant.isItemValidForSlot(slot, stack)) moved |= mergeItemStack(stack, slot, slot + 1, false);
                slot = TileEntityMachineChemplant.outputContainerSlot(channel);
                if(stack.stackSize > 0 && chemplant.isItemValidForSlot(slot, stack)) moved |= mergeItemStack(stack, slot, slot + 1, false);
            }
            if(!moved) {
                int first = chemplant.isItemValidForSlot(0, stack) ? 0 : chemplant.isItemValidForSlot(1, stack) ? 1
                        : chemplant.isItemValidForSlot(4, stack) ? 4 : 13;
                int end = first == 1 ? 4 : first == 13 ? 17 : first + 1;
                if(!mergeItemStack(stack, first, end, false)) return null;
            }
        }
        if(stack.stackSize == original.stackSize) return null;
        if(stack.stackSize == 0) source.putStack(null); else source.onSlotChanged();
        source.onPickupFromSlot(player, original);
        return original;
    }
    @Override public boolean canInteractWith(EntityPlayer player) { return chemplant.isUseableByPlayer(player); }
}
