package com.hbm.inventory.container;

import com.hbm.inventory.SlotCraftingOutput;
import com.hbm.tileentity.machine.TileEntityNukeFurnace;
import com.hbm.util.InventoryUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerNukeFurnace extends Container {

	private TileEntityNukeFurnace diFurnace;
	private int dualCookTime;
	private int dualPower;
	private int runtimeProgressTick = -1;
	private int runtimeProgressBase = -1;
	private int runtimeProgressActive = -1;
	private int clientProgressTick;
	private int clientProgressBase;
	private boolean clientProgressActive;

	public ContainerNukeFurnace(InventoryPlayer invPlayer, TileEntityNukeFurnace tedf) {
		dualCookTime = 0;
		dualPower = 0;

		diFurnace = tedf;

		this.addSlotToContainer(new Slot(tedf, 0, 56, 53) {
			@Override
			public int getSlotStackLimit() {
				return 1;
			}
		});

		this.addSlotToContainer(new Slot(tedf, 1, 56, 17));
		this.addSlotToContainer(new SlotCraftingOutput(invPlayer.player, tedf, 2, 116, 35));

		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 9; j++) {
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}

		for(int i = 0; i < 9; i++) {
			this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 142));
		}
	}

	@Override
	public void addCraftingToCrafters(ICrafting crafting) {
		super.addCraftingToCrafters(crafting);
		crafting.sendProgressBarUpdate(this, 0, this.diFurnace.dualCookTime);
		crafting.sendProgressBarUpdate(this, 1, this.diFurnace.dualPower);
		crafting.sendProgressBarUpdate(this, 2, this.diFurnace.getRuntimeProgressTick());
		crafting.sendProgressBarUpdate(this, 3, this.diFurnace.isRuntimeProgressActive() ? 1 : 0);
		crafting.sendProgressBarUpdate(this, 4, this.diFurnace.getRuntimeProgressBase());
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer p_82846_1_, int par2) {
		ItemStack var3 = null;
		Slot var4 = (Slot) this.inventorySlots.get(par2);

		if(var4 != null && var4.getHasStack()) {
			ItemStack var5 = var4.getStack();
			var3 = var5.copy();

			if(par2 <= 2) {
				if(!this.mergeItemStack(var5, 3, this.inventorySlots.size(), true)) {
					return null;
				}
			} else {
				
				if(TileEntityNukeFurnace.getFuelValue(var5) > 0) {
					if(!InventoryUtil.mergeItemStack(this.inventorySlots, var5, 0, 1, false))
						return null;
				} else {
					if(!this.mergeItemStack(var5, 1, 2, false)) 
						return null;
				}
			}

			if(var5.stackSize == 0) {
				var4.putStack((ItemStack) null);
			} else {
				var4.onSlotChanged();
			}
		}

		return var3;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return diFurnace.isUseableByPlayer(player);
	}

	@Override
	public void detectAndSendChanges() {
		super.detectAndSendChanges();

		for(int i = 0; i < this.crafters.size(); i++) {
			ICrafting par1 = (ICrafting) this.crafters.get(i);

			if(this.dualCookTime != this.diFurnace.dualCookTime) {
				par1.sendProgressBarUpdate(this, 0, this.diFurnace.dualCookTime);
			}

			if(this.dualPower != this.diFurnace.dualPower) {
				par1.sendProgressBarUpdate(this, 1, this.diFurnace.dualPower);
			}
			int progressTick = this.diFurnace.getRuntimeProgressTick();
			int progressBase = this.diFurnace.getRuntimeProgressBase();
			int progressActive = this.diFurnace.isRuntimeProgressActive() ? 1 : 0;
			if(this.runtimeProgressTick != progressTick) par1.sendProgressBarUpdate(this, 2, progressTick);
			if(this.runtimeProgressActive != progressActive) par1.sendProgressBarUpdate(this, 3, progressActive);
			if(this.runtimeProgressBase != progressBase) par1.sendProgressBarUpdate(this, 4, progressBase);
		}

		this.dualCookTime = this.diFurnace.dualCookTime;
		this.dualPower = this.diFurnace.dualPower;
		this.runtimeProgressTick = this.diFurnace.getRuntimeProgressTick();
		this.runtimeProgressBase = this.diFurnace.getRuntimeProgressBase();
		this.runtimeProgressActive = this.diFurnace.isRuntimeProgressActive() ? 1 : 0;
	}

	@Override
	public void updateProgressBar(int i, int j) {
		if(i == 0) {
			diFurnace.dualCookTime = j;
		}
		if(i == 1) {
			diFurnace.dualPower = j;
		}
		if(i == 2) clientProgressTick = j & 0xFFFF;
		if(i == 3) clientProgressActive = j != 0;
		if(i == 4) clientProgressBase = j;
		if(i >= 2 && i <= 4) diFurnace.setClientProgressState(clientProgressTick, clientProgressBase, clientProgressActive);
	}
}
