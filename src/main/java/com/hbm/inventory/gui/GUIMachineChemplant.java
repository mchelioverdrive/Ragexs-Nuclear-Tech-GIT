package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerMachineChemplant;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.machine.TileEntityMachineChemplant;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public class GUIMachineChemplant extends GuiInfoContainer {
    private final TileEntityMachineChemplant chemplant;
    public GUIMachineChemplant(InventoryPlayer player, TileEntityMachineChemplant tile) {
        super(new ContainerMachineChemplant(player, tile)); setMachineInfoOwner(tile); chemplant = tile;
        xSize = 248; ySize = 244;
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        for(int i = 0; i < 4; i++) {
            chemplant.processInputs()[i].renderTankInfo(this, x, y, guiLeft + 8 + i * 18, guiTop + 26, 16, 42);
            chemplant.processOutputs()[i].renderTankInfo(this, x, y, guiLeft + 170 + i * 18, guiTop + 26, 16, 42);
        }
        drawElectricityInfo(this, x, y, guiLeft + 82, guiTop + 26, 8, 52, chemplant.energyQuanta, chemplant.maxPower);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        String name = chemplant.hasCustomInventoryName() ? chemplant.getInventoryName() : I18n.format(chemplant.getInventoryName());
        fontRendererObj.drawString(name, (xSize - fontRendererObj.getStringWidth(name)) / 2, 6, 4210752);
        fontRendererObj.drawString("Feed", 8, 16, 4210752);
        fontRendererObj.drawString("Products", 170, 16, 4210752);
        fontRendererObj.drawString("Template", 101, 52, 4210752);
        fontRendererObj.drawString("Items in", 8, 134, 4210752);
        fontRendererObj.drawString("Items out", 170, 134, 4210752);
        fontRendererObj.drawString(I18n.format("container.inventory"), 44, 150, 4210752);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        for(Object value : inventorySlots.inventorySlots) {
            Slot slot = (Slot) value;
            drawRect(guiLeft + slot.xDisplayPosition - 1, guiTop + slot.yDisplayPosition - 1,
                    guiLeft + slot.xDisplayPosition + 17, guiTop + slot.yDisplayPosition + 17, 0xff373737);
            drawRect(guiLeft + slot.xDisplayPosition, guiTop + slot.yDisplayPosition,
                    guiLeft + slot.xDisplayPosition + 16, guiTop + slot.yDisplayPosition + 16, 0xff8b8b8b);
        }
        FluidTank[] in = chemplant.processInputs(), out = chemplant.processOutputs();
        for(int i = 0; i < 4; i++) {
            for(int tankX : new int[] {8 + i * 18, 170 + i * 18})
                drawRect(guiLeft + tankX, guiTop + 26, guiLeft + tankX + 16, guiTop + 68, 0xff373737);
            in[i].renderTank(guiLeft + 8 + i * 18, guiTop + 68, zLevel, 16, 42);
            out[i].renderTank(guiLeft + 170 + i * 18, guiTop + 68, zLevel, 16, 42);
        }
        drawRect(guiLeft + 82, guiTop + 26, guiLeft + 90, guiTop + 78, 0xff373737);
        int charge = (int) (Math.min(chemplant.energyQuanta, chemplant.maxPower) * 52 / chemplant.maxPower);
        drawRect(guiLeft + 82, guiTop + 78 - charge, guiLeft + 90, guiTop + 78, 0xffffb000);
        drawRect(guiLeft + 88, guiTop + 96, guiLeft + 160, guiTop + 106, 0xff373737);
        int progress = chemplant.getDisplayedProgress() * 72 / Math.max(chemplant.maxProgress, 1);
        drawRect(guiLeft + 88, guiTop + 96, guiLeft + 88 + progress, guiTop + 106, 0xff62a84a);
    }
}
