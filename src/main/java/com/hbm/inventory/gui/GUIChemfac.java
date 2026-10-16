package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerChemfac;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.machine.TileEntityMachineChemfac;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public class GUIChemfac extends GuiInfoContainer {
    private final TileEntityMachineChemfac chemfac;
    public GUIChemfac(InventoryPlayer player, TileEntityMachineChemfac tile) {
        super(new ContainerChemfac(player, tile)); setMachineInfoOwner(tile); chemfac = tile; xSize = 288; ySize = 256;
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        drawElectricityInfo(this, x, y, guiLeft + 266, guiTop + 25, 16, 52, chemfac.energyQuanta, chemfac.getEnergyCapacityQuanta());
        for(int lane = 0; lane < 8; lane++) {
            int ox = guiLeft + 132 * (lane % 2), oy = guiTop + 38 * (lane / 2);
            FluidTank[] in = chemfac.processInputs(lane), out = chemfac.processOutputs(lane);
            for(int i = 0; i < 4; i++) {
                in[i].renderTankInfo(this, x, y, ox + 40 + i * 3, oy + 14, 3, 32);
                out[i].renderTankInfo(this, x, y, ox + 110 + i * 3, oy + 14, 3, 32);
            }
        }
        chemfac.water.renderTankInfo(this, x, y, guiLeft + 266, guiTop + 108, 7, 52);
        chemfac.steam.renderTankInfo(this, x, y, guiLeft + 276, guiTop + 108, 7, 52);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRendererObj.drawString("Feed", 40, 3, 4210752); fontRendererObj.drawString("Out", 110, 3, 4210752);
        fontRendererObj.drawString("Feed", 172, 3, 4210752); fontRendererObj.drawString("Out", 242, 3, 4210752);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        for(Object value : inventorySlots.inventorySlots) {
            Slot slot = (Slot) value;
            drawRect(guiLeft + slot.xDisplayPosition - 1, guiTop + slot.yDisplayPosition - 1,
                    guiLeft + slot.xDisplayPosition + 16, guiTop + slot.yDisplayPosition + 16, 0xff373737);
            drawRect(guiLeft + slot.xDisplayPosition, guiTop + slot.yDisplayPosition,
                    guiLeft + slot.xDisplayPosition + 15, guiTop + slot.yDisplayPosition + 15, 0xff8b8b8b);
        }
        for(int lane = 0; lane < 8; lane++) {
            int ox = guiLeft + 132 * (lane % 2), oy = guiTop + 38 * (lane / 2);
            FluidTank[] in = chemfac.processInputs(lane), out = chemfac.processOutputs(lane);
            for(int i = 0; i < 4; i++) {
                drawRect(ox + 40 + i * 3, oy + 14, ox + 43 + i * 3, oy + 46, 0xff373737);
                drawRect(ox + 110 + i * 3, oy + 14, ox + 113 + i * 3, oy + 46, 0xff373737);
                in[i].renderTank(ox + 40 + i * 3, oy + 46, zLevel, 2, 32);
                out[i].renderTank(ox + 110 + i * 3, oy + 46, zLevel, 2, 32);
            }
            int progress = chemfac.getDisplayedProgress(lane) * 17 / Math.max(chemfac.maxProgress[lane], 1);
            drawRect(ox + 55, oy + 16, ox + 72, oy + 26, 0xff373737);
            drawRect(ox + 55, oy + 16, ox + 55 + progress, oy + 26, 0xff62a84a);
        }
        int charge = (int) (Math.min(chemfac.energyQuanta, chemfac.getEnergyCapacityQuanta()) * 52 / chemfac.getEnergyCapacityQuanta());
        drawRect(guiLeft + 266, guiTop + 25, guiLeft + 282, guiTop + 77, 0xff373737);
        drawRect(guiLeft + 266, guiTop + 77 - charge, guiLeft + 282, guiTop + 77, 0xffffb000);
        chemfac.water.renderTank(guiLeft + 266, guiTop + 160, zLevel, 7, 52);
        chemfac.steam.renderTank(guiLeft + 276, guiTop + 160, zLevel, 7, 52);
    }
}
