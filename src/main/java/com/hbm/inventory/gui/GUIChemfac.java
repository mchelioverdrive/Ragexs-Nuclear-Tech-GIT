package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerChemfac;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.machine.TileEntityMachineChemfac;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import org.lwjgl.opengl.GL11;

public class GUIChemfac extends GuiInfoContainer {
    private final TileEntityMachineChemfac chemfac;

    public GUIChemfac(InventoryPlayer player, TileEntityMachineChemfac tile) {
        super(new ContainerChemfac(player, tile));
        setMachineInfoOwner(tile);
        chemfac = tile;
        xSize = 316;
        ySize = 240;
    }

    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        Slot hovered = getSlotAtPosition(x, y);
        if(mc.thePlayer.inventory.getItemStack() != null || hovered != null && hovered.getHasStack()) return;
        for(int lane = 0; lane < 8; lane++) {
            int ox = guiLeft + ContainerChemfac.LANE_PITCH_X * (lane % 2);
            int oy = guiTop + ContainerChemfac.LANE_PITCH_Y * (lane / 2);
            FluidTank[] in = chemfac.processInputs(lane), out = chemfac.processOutputs(lane);
            for(int i = 0; i < 4; i++) {
                in[i].renderTankInfo(this, x, y, ox + 46 + i * 4, oy + 14, 3, 34);
                out[i].renderTankInfo(this, x, y, ox + 126 + i * 4, oy + 14, 3, 34);
            }
            drawCustomInfoStat(x, y, ox + 66, oy + 16, 18, 10, x, y,
                    I18n.format("gui.rtm.lane", lane + 1) + ": " + MachineGuiParts.scaled(chemfac.getDisplayedProgress(lane), chemfac.maxProgress[lane], 100) + "%",
                    I18n.format(chemfac.isRuntimeLaneActive(lane) ? "gui.rtm.running" : "gui.rtm.waiting"));
            Slot template = (Slot) inventorySlots.inventorySlots.get(chemfac.getTemplateIndex(lane));
            if(hovered == template && !template.getHasStack())
                drawCustomInfoStat(x, y, ox + 66, oy + 32, 16, 16, x, y,
                        I18n.format("gui.rtm.template"), I18n.format("gui.rtm.template.retained"));
        }
        drawElectricityInfo(this, x, y, guiLeft + 294, guiTop + 25, 16, 52,
                chemfac.energyQuanta, chemfac.getEnergyCapacityQuanta());
        chemfac.water.renderTankInfo(this, x, y, guiLeft + 294, guiTop + 108, 6, 52);
        chemfac.steam.renderTankInfo(this, x, y, guiLeft + 304, guiTop + 108, 6, 52);
    }

    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        String name = chemfac.hasCustomInventoryName() ? chemfac.getInventoryName() : I18n.format(chemfac.getInventoryName());
        MachineGuiParts.centeredLabel(fontRendererObj, name, 8, 3, 280, MachineGuiParts.TEXT);
        MachineGuiParts.label(fontRendererObj, I18n.format("gui.rtm.upgrades.short"), 276, 162, 34, MachineGuiParts.TEXT);
        MachineGuiParts.label(fontRendererObj, I18n.format("gui.rtm.cooling.short"), 290, 98, 24, MachineGuiParts.TEXT);
        MachineGuiParts.label(fontRendererObj, I18n.format("gui.rtm.flow"), 8, 162, 48, MachineGuiParts.TEXT);
        MachineGuiParts.label(fontRendererObj, I18n.format("gui.rtm.template"), 8, 176, 48, MachineGuiParts.TEXT);
        MachineGuiParts.label(fontRendererObj, I18n.format("gui.rtm.retained"), 8, 186, 48, MachineGuiParts.TEXT);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        MachineGuiParts.panel(guiLeft, guiTop, xSize, ySize);
        MachineGuiParts.panel(guiLeft + 58, guiTop + 156, 170, 80);
        for(int lane = 0; lane < 8; lane++) {
            int ox = guiLeft + ContainerChemfac.LANE_PITCH_X * (lane % 2);
            int oy = guiTop + ContainerChemfac.LANE_PITCH_Y * (lane / 2);
            MachineGuiParts.panel(ox + 3, oy + 12, 140, 36);
        }
        for(Object value : inventorySlots.inventorySlots) MachineGuiParts.slot((Slot) value, guiLeft, guiTop);
        MachineGuiParts.battery(this, (Slot) inventorySlots.inventorySlots.get(0), guiLeft, guiTop);
        for(int i = 1; i <= 4; i++) MachineGuiParts.circuit(this, (Slot) inventorySlots.inventorySlots.get(i), guiLeft, guiTop);
        for(int lane = 0; lane < 8; lane++) {
            int ox = guiLeft + ContainerChemfac.LANE_PITCH_X * (lane % 2);
            int oy = guiTop + ContainerChemfac.LANE_PITCH_Y * (lane / 2);
            FluidTank[] in = chemfac.processInputs(lane), out = chemfac.processOutputs(lane);
            for(int i = 0; i < 4; i++) {
                MachineGuiParts.tank(in[i], ox + 46 + i * 4, oy + 14, zLevel, 3, 34);
                MachineGuiParts.tank(out[i], ox + 126 + i * 4, oy + 14, zLevel, 3, 34);
            }
            MachineGuiParts.template(this, (Slot) inventorySlots.inventorySlots.get(chemfac.getTemplateIndex(lane)), guiLeft, guiTop);
            MachineGuiParts.progress(ox + 66, oy + 16, 18, 10, chemfac.getDisplayedProgress(lane),
                    chemfac.maxProgress[lane], chemfac.isRuntimeLaneActive(lane));
        }
        MachineGuiParts.power(this, guiLeft + 294, guiTop + 25, chemfac.energyQuanta, chemfac.getEnergyCapacityQuanta(), 16);
        MachineGuiParts.tank(chemfac.water, guiLeft + 294, guiTop + 108, zLevel, 6, 52);
        MachineGuiParts.tank(chemfac.steam, guiLeft + 304, guiTop + 108, zLevel, 6, 52);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
