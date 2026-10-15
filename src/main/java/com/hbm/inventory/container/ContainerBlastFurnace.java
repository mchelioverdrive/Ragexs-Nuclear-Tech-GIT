package com.hbm.inventory.container;

import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.inventory.SlotNonRetarded;
import com.hbm.tileentity.machine.TileEntityMachineBlastFurnace;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerBlastFurnace extends ContainerBase {
	
	protected TileEntityMachineBlastFurnace tile;

	public ContainerBlastFurnace(InventoryPlayer invPlayer, TileEntityMachineBlastFurnace tedf) {
		super(invPlayer, tedf);
		this.tile = tedf;

		// Saved auxiliary fuel is retrievable, never accepted or burned. Keep six container slots/indices.
		this.addSlotToContainer(new net.minecraft.inventory.Slot(tedf, 0, 8, 91) {
			@Override public boolean isItemValid(net.minecraft.item.ItemStack stack) { return false; }
		});
		// Input
		this.addSlotToContainer(new SlotNonRetarded(tedf, 1, 80, 27));
		this.addSlotToContainer(new SlotNonRetarded(tedf, 2, 80, 45));
		// Output
		this.addSlotToContainer(new SlotCraftingOutput(invPlayer.player, tedf, 3, 134, 72));
		this.addSlotToContainer(new SlotCraftingOutput(invPlayer.player, tedf, 4, 134, 90));
		this.addSlotToContainer(new SlotNonRetarded(tedf, 5, 134, 27));
		
		this.playerInv(invPlayer, 8, 140, 198);
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return tile.isUseableByPlayer(player);
	}
}
