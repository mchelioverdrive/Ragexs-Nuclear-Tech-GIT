package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerMachineChemplant;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.machine.TileEntityMachineChemplant;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import org.lwjgl.opengl.GL11;

public class GUIMachineChemplant extends GuiInfoContainer {
    private final TileEntityMachineChemplant chemplant;

    public GUIMachineChemplant(InventoryPlayer player, TileEntityMachineChemplant tile) {
        super(new ContainerMachineChemplant(player, tile));
        setMachineInfoOwner(tile);
        chemplant = tile;
        xSize = 248;
        ySize = 240;
    }

    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        if(mc.thePlayer.inventory.getItemStack() != null) return;
        Slot hovered = getSlotAtPosition(x, y);
        if(hovered != null) {
            if(hovered.inventory == chemplant && hovered.getSlotIndex() == 4 && !hovered.getHasStack())
                drawCustomInfoStat(x, y, guiLeft + ContainerMachineChemplant.TEMPLATE_X, guiTop + 62, 16, 16, x, y,
                        I18n.format("gui.rtm.template"), I18n.format("gui.rtm.template.retained"));
            return;
        }
        for(int i = 0; i < 4; i++) {
            chemplant.processInputs()[i].renderTankInfo(this, x, y, guiLeft + 8 + i * 18, guiTop + 26, 16, 42);
            chemplant.processOutputs()[i].renderTankInfo(this, x, y, guiLeft + 170 + i * 18, guiTop + 26, 16, 42);
        }
        drawElectricityInfo(this, x, y, guiLeft + 82, guiTop + 26, 8, 52, chemplant.energyQuanta, chemplant.maxPower);
        drawCustomInfoStat(x, y, guiLeft + 94, guiTop + 51, 68, 30, x, y,
                I18n.format("gui.rtm.template"), I18n.format("gui.rtm.template.retained"));
        drawCustomInfoStat(x, y, guiLeft + ContainerMachineChemplant.PROCESS_X, guiTop + ContainerMachineChemplant.PROCESS_Y,
                ContainerMachineChemplant.PROCESS_WIDTH, ContainerMachineChemplant.PROCESS_HEIGHT, x, y,
                I18n.format("gui.rtm.process") + ": " + MachineGuiParts.scaled(chemplant.getDisplayedProgress(), chemplant.maxProgress, 100) + "%",
                I18n.format(chemplant.isProgressing ? "gui.rtm.running" : "gui.rtm.waiting"));
        drawCustomInfoStat(x, y, guiLeft + 8, guiTop + 70, 72, 2, x, y, I18n.format("gui.rtm.containers"));
        drawCustomInfoStat(x, y, guiLeft + 170, guiTop + 70, 72, 2, x, y, I18n.format("gui.rtm.containers"));
    }

    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        String name = chemplant.hasCustomInventoryName() ? chemplant.getInventoryName() : I18n.format(chemplant.getInventoryName());
        MachineGuiParts.centeredLabel(fontRendererObj, name, 12, 6, xSize - 24, MachineGuiParts.TEXT);
        label("gui.rtm.fluids.in", 8, 16, 72);
        label("gui.rtm.fluids.out", 170, 16, 72);
        label("gui.rtm.template", 94, 51, 68);
        label("gui.rtm.process", 94, 85, 68);
        label("gui.rtm.items.in", 8, 132, 72);
        label("gui.rtm.items.out", 170, 132, 72);
        label(chemplant.isProgressing ? "gui.rtm.running" : "gui.rtm.waiting", 94, 112, 68);
        label("container.inventory", 44, 146, 162);
    }

    private void label(String key, int x, int y, int width) {
        MachineGuiParts.centeredLabel(fontRendererObj, I18n.format(key), x, y, width, MachineGuiParts.TEXT);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        MachineGuiParts.panel(guiLeft, guiTop, xSize, ySize);
        MachineGuiParts.recess(guiLeft + 6, guiTop + 24, 72, 106);
        MachineGuiParts.recess(guiLeft + 168, guiTop + 24, 74, 106);
        MachineGuiParts.panel(guiLeft + 94, guiTop + 47, 68, 79);
        MachineGuiParts.panel(guiLeft + 40, guiTop + 142, 170, 94);
        for(Object value : inventorySlots.inventorySlots) MachineGuiParts.slot((Slot) value, guiLeft, guiTop);
        MachineGuiParts.battery(this, (Slot) inventorySlots.inventorySlots.get(0), guiLeft, guiTop);
        for(int i = 1; i <= 3; i++) MachineGuiParts.circuit(this, (Slot) inventorySlots.inventorySlots.get(i), guiLeft, guiTop);
        MachineGuiParts.template(this, (Slot) inventorySlots.inventorySlots.get(4), guiLeft, guiTop);
        FluidTank[] in = chemplant.processInputs(), out = chemplant.processOutputs();
        for(int i = 0; i < 4; i++) {
            MachineGuiParts.tank(in[i], guiLeft + 8 + i * 18, guiTop + 26, zLevel, 16, 42);
            MachineGuiParts.tank(out[i], guiLeft + 170 + i * 18, guiTop + 26, zLevel, 16, 42);
        }
        MachineGuiParts.power(this, guiLeft + 82, guiTop + 26, chemplant.energyQuanta, chemplant.maxPower, 8);
        MachineGuiParts.progress(guiLeft + ContainerMachineChemplant.PROCESS_X, guiTop + ContainerMachineChemplant.PROCESS_Y,
                ContainerMachineChemplant.PROCESS_WIDTH, ContainerMachineChemplant.PROCESS_HEIGHT,
                chemplant.getDisplayedProgress(), chemplant.maxProgress, chemplant.isProgressing);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
