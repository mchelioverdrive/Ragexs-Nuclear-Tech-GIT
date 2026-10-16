package com.hbm.inventory.container;

import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.inventory.SlotUpgrade;
import com.hbm.tileentity.machine.TileEntityMachineChemfac;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerChemfac extends Container {
	
	private TileEntityMachineChemfac chemfac;

	public ContainerChemfac(InventoryPlayer playerInv, TileEntityMachineChemfac tile) {
		chemfac = tile;

		this.addSlotToContainer(new Slot(tile, 0, 266, 79));
		
		for(int i = 0; i < 2; i++) {
			for(int j = 0; j < 2; j++) {
				this.addSlotToContainer(new SlotUpgrade(tile, 1 + i * 2 + j, 251 + j * 18, 172 + i * 18));
			}
		}
		
		for(int i = 0; i < 4; i++) {
			for(int j = 0; j < 2; j++) {

				for(int k = 0; k < 2; k++) {
					for(int l = 0; l < 2; l++) {
						this.addSlotToContainer(new Slot(tile, this.inventorySlots.size(), 7 + j * 132 + l * 16, 14  + i * 38 + k * 16));
					}
				}

				for(int k = 0; k < 2; k++) {
					for(int l = 0; l < 2; l++) {
						this.addSlotToContainer(new SlotCraftingOutput(playerInv.player, tile, this.inventorySlots.size(), 75 + j * 132 + l * 16, 14  + i * 38 + k * 16));
					}
				}
				
				this.addSlotToContainer(new Slot(tile, this.inventorySlots.size(), 55 + j * 132, 30  + i * 38));
			}
		}
		
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(playerInv, j + i * 9 + 9, 62 + j * 18, 174 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(playerInv, i, 62 + i * 18, 232));
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return chemfac.isUseableByPlayer(player);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot source = (Slot) inventorySlots.get(index);
        if(source == null || !source.getHasStack()) return null;
        ItemStack stack = source.getStack(), original = stack.copy();
        if(index < 77) {
            if(!mergeItemStack(stack, 77, inventorySlots.size(), true)) return null;
        } else if(stack.getItem() instanceof api.hbm.energymk2.IBatteryItem || stack.getItem() == com.hbm.items.ModItems.battery_creative) {
            if(!mergeItemStack(stack, 0, 1, false)) return null;
        } else if(stack.getItem() instanceof com.hbm.items.machine.ItemMachineUpgrade) {
            if(!mergeItemStack(stack, 1, 5, false)) return null;
        } else {
            for(int lane = 0; lane < 8 && stack.stackSize > 0; lane++) {
                if(stack.getItem() == com.hbm.items.ModItems.chemistry_template) {
                    int slot = chemfac.getTemplateIndex(lane); mergeItemStack(stack, slot, slot + 1, false);
                } else {
                    int[] slots = chemfac.getSlotIndicesFromIndex(lane); mergeItemStack(stack, slots[0], slots[1] + 1, false);
                }
            }
        }
        if(original.stackSize == stack.stackSize) return null;
        if(stack.stackSize == 0) source.putStack(null); else source.onSlotChanged();
        source.onPickupFromSlot(player, original);
        return original;
    }
}