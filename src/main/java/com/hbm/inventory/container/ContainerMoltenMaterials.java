package com.hbm.inventory.container;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Applies GUI requests during the server's normal container tick, never on the network thread. */
public abstract class ContainerMoltenMaterials extends Container {
	private final AtomicReference<Selection> pendingSelection = new AtomicReference<Selection>();

	public void requestSelection(EntityPlayer player, int window, int tank, int material) {
		if(window == windowId && tank >= 0 && tank <= 1 && material >= 0) {
			pendingSelection.set(new Selection(player, tank, material));
		}
	}

	@Override
	public void detectAndSendChanges() {
		Selection selection = pendingSelection.getAndSet(null);
		if(selection != null && selection.player.openContainer == this && !selection.player.isDead
				&& !selection.player.worldObj.isRemote && canInteractWith(selection.player)) {
			selectMoltenMaterial(selection.tank, selection.material);
		}
		super.detectAndSendChanges();
	}

	protected abstract void selectMoltenMaterial(int tank, int material);

	private static class Selection {
		final EntityPlayer player;
		final int tank;
		final int material;

		Selection(EntityPlayer player, int tank, int material) {
			this.player = player;
			this.tank = tank;
			this.material = material;
		}
	}
}
