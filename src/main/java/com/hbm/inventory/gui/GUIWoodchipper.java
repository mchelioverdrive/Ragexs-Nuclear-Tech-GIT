package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerWoodchipper;
import com.hbm.tileentity.machine.TileEntityWoodchipper;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public final class GUIWoodchipper extends GuiContainer {
    private final TileEntityWoodchipper tile;

    public GUIWoodchipper(InventoryPlayer player, TileEntityWoodchipper tile) {
        super(new ContainerWoodchipper(player, tile));
        this.tile = tile;
    }

    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRendererObj.drawString(I18n.format("container.woodchipper"), 8, 7, 0xFFFFFF);
        String status = tile.active ? "woodchipper.status.entity" : tile.captive != null ? "woodchipper.status.held"
                : tile.cuttingWood() ? "woodchipper.status.material" : "woodchipper.status.idle";
        fontRendererObj.drawString(I18n.format(status), 8, 20, 0xCCD3D5);
        fontRendererObj.drawString(I18n.format("woodchipper.energy", tile.energyQuanta, tile.displayedCapacity()), 8, 54, 0xFFFFFF);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, 73, 0xFFFFFF);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        rectangle(0, 0, xSize, ySize, 0xFF22272B);
        rectangle(2, 2, xSize - 2, ySize - 2, 0xFF424C52);
        for(Object entry : inventorySlots.inventorySlots) {
            Slot slot = (Slot) entry;
            int x = slot.xDisplayPosition, y = slot.yDisplayPosition;
            rectangle(x - 1, y - 1, x + 17, y + 17, 0xFF202529);
            rectangle(x, y, x + 16, y + 16, 0xFF747F85);
        }
        int progress = Math.min(tile.displayedProgress(), tile.displayedDuration());
        bar(68, 37, 40, 8, 40 * progress / tile.displayedDuration(), 0xFFCDA051);
        long capacity = Math.max(1L, tile.displayedCapacity());
        int energy = (int) (160L * Math.min(tile.energyQuanta, capacity) / capacity);
        bar(8, 64, 160, 6, energy, 0xFF5BADCB);
    }

    private void bar(int x, int y, int width, int height, int fill, int color) {
        rectangle(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF202529);
        if(fill > 0) rectangle(x, y, x + fill, y + height, color);
    }

    private void rectangle(int x, int y, int xMax, int yMax, int color) {
        drawRect(guiLeft + x, guiTop + y, guiLeft + xMax, guiTop + yMax, color);
    }
}
