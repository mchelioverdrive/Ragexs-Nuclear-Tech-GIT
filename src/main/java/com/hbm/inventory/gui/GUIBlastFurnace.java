package com.hbm.inventory.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.container.ContainerBlastFurnace;
import com.hbm.lib.RefStrings;
import com.hbm.tileentity.machine.TileEntityMachineBlastFurnace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

public class GUIBlastFurnace extends GuiInfoContainer {
	
	private static ResourceLocation texture = new ResourceLocation(RefStrings.MODID + ":textures/gui/processing/gui_blast_furnace.png");
	private TileEntityMachineBlastFurnace furnace;

	public GUIBlastFurnace(InventoryPlayer invPlayer, TileEntityMachineBlastFurnace tedf) {
		super(new ContainerBlastFurnace(invPlayer, tedf));
		furnace = tedf;
		setMachineInfoOwner(tedf);
		
		this.xSize = 176;
		this.ySize = 222;
	}
	
	@Override
	public void drawScreen(int x, int y, float interp) {
		super.drawScreen(x, y, interp);

        boolean overItem = false;
        for(Object value : this.inventorySlots.inventorySlots) {
            Slot slot = (Slot) value;
            if(this.isMouseOverSlot(slot, x, y)) { overItem = true; break; }
        }
        if(!overItem && this.mc.thePlayer.inventory.getItemStack() == null) {
            java.util.List<String> details = new java.util.ArrayList<>();
            details.add(I18n.format("tooltip.blast.rate") + " " + String.format(java.util.Locale.US, "%.2f MW", furnace.speed));
            furnace.appendProgressInfo(details);
            drawCustomInfoStat(x, y, guiLeft + 62, guiTop + 18, 56, 88, x, y, details.toArray(new String[0]));
        }
        if(furnace.getStackInSlot(0) != null)
            drawCustomInfoStat(x, y, guiLeft + 8, guiTop + 91, 16, 16, x, y, I18n.format("tooltip.blast.legacyFuel"));

		furnace.tanks[0].renderTankInfo(this, x, y, guiLeft + 25, guiTop + 71, 18, 18);
		furnace.tanks[1].renderTankInfo(this, x, y, guiLeft + 25, guiTop + 17, 18, 18);
		for(int tank = 2; tank < 5; tank++) furnace.tanks[tank].renderTankInfo(this, x, y, guiLeft + 8 + (tank - 2) * 50, guiTop + 122, 45, 5);
		drawCustomInfoStat(x, y, guiLeft + 80, guiTop + 45, 16, 16, x, y, I18n.format("tooltip.blast.reductant"));
		drawCustomInfoStat(x, y, guiLeft + 134, guiTop + 27, 16, 16, x, y, I18n.format("tooltip.blast.flux"));
		furnace.tanks[5].renderTankInfo(this, x, y, guiLeft + 134, guiTop + 56, 20, 5);
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.furnace.hasCustomInventoryName() ? this.furnace.getInventoryName() : I18n.format(this.furnace.getInventoryName());
		
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
		for(int tank = 2; tank < 5; tank++) MachineGuiParts.label(fontRendererObj, I18n.format("tooltip.blast.tank" + tank), 8 + (tank - 2) * 50, 112, 45, 4210752);
		this.fontRendererObj.drawString("CO2", 134, 47, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		drawTexturedModalRect(guiLeft + 133, guiTop + 26, 79, 26, 18, 18);
        // Remove the obsolete auxiliary-fuel socket before drawing process/heat fill.
        drawTexturedModalRect(guiLeft + 79, guiTop + 80, 154, 80, 18, 18);
        if(furnace.getStackInSlot(0) != null) drawTexturedModalRect(guiLeft + 7, guiTop + 90, 79, 26, 18, 18);
		
		int fuel = (int) Math.round((double) furnace.fuel * 26D / (double) furnace.MAX_FUEL);
		int prog = (int) Math.round(furnace.progress * (88D - fuel));
		
		drawTexturedModalRect(guiLeft + 62, guiTop + 106 - prog - fuel, 176, 102 - prog - fuel, 56, prog);
		
		drawTexturedModalRect(guiLeft + 62, guiTop + 106 - fuel, 176, 128 - fuel, 56, fuel);
		
		if(furnace.isProgressing) {
			drawTexturedModalRect(guiLeft + 81, guiTop + 64, 176, 0, 14, 14);
		}

		drawRect(guiLeft + 27, guiTop + 85, guiLeft + 27 + furnace.tanks[0].getFill() * 14 / furnace.tanks[0].getMaxFill(), guiTop + 88, 0xFF800000);
		drawRect(guiLeft + 27, guiTop + 31, guiLeft + 27 + furnace.tanks[1].getFill() * 14 / furnace.tanks[1].getMaxFill(), guiTop + 34, 0xFF800000);
		MachineGuiParts.recess(guiLeft + 5, guiTop + 111, 152, 17);
		for(int tank = 2; tank < 5; tank++) {
			int left = guiLeft + 8 + (tank - 2) * 50;
			MachineGuiParts.recess(left, guiTop + 122, 45, 5);
			furnace.tanks[tank].renderTank(left, guiTop + 127, zLevel, 45, 5, 1);
		}
		MachineGuiParts.recess(guiLeft + 134, guiTop + 56, 20, 5);
		furnace.tanks[5].renderTank(guiLeft + 134, guiTop + 61, zLevel, 20, 5, 1);
		GL11.glColor4f(1, 1, 1, 1);
	}
}
