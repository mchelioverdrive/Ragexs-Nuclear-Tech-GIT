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

		if(this.mc.thePlayer.inventory.getItemStack() == null) {
			Slot slot = (Slot) this.inventorySlots.inventorySlots.get(0);
			if(this.isMouseOverSlot(slot, x, y) && !slot.getHasStack()) {
				this.func_146283_a(java.util.Arrays.asList(I18n.format("tooltip.blast.fuel")), x, y);
			}
		}
		
		String label = I18n.format("tooltip.blast.rate") + " " + String.format(java.util.Locale.US, "%.2f MW", furnace.speed);
		drawCustomInfoStat(x, y, guiLeft + 79, guiTop + 62, 18, 18, x, y, label);

		furnace.tanks[0].renderTankInfo(this, x, y, guiLeft + 25, guiTop + 71, 18, 18);
		furnace.tanks[1].renderTankInfo(this, x, y, guiLeft + 25, guiTop + 17, 18, 18);
		for(int tank = 2; tank < 5; tank++) furnace.tanks[tank].renderTankInfo(this, x, y, guiLeft + 8 + (tank - 2) * 50, guiTop + 118, 45, 12);
		drawCustomInfoStat(x, y, guiLeft + 80, guiTop + 45, 16, 16, x, y, I18n.format("tooltip.blast.reductant"));
		drawCustomInfoStat(x, y, guiLeft + 134, guiTop + 27, 16, 16, x, y, I18n.format("tooltip.blast.flux"));
		furnace.tanks[5].renderTankInfo(this, x, y, guiLeft + 134, guiTop + 45, 20, 12);
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.furnace.hasCustomInventoryName() ? this.furnace.getInventoryName() : I18n.format(this.furnace.getInventoryName());
		
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
		for(int tank = 2; tank < 5; tank++) this.fontRendererObj.drawString(I18n.format("tooltip.blast.tank" + tank), 8 + (tank - 2) * 50, 112, 4210752);
		this.fontRendererObj.drawString("CO2", 134, 47, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		drawTexturedModalRect(guiLeft + 133, guiTop + 26, 79, 26, 18, 18);
		
		int fuel = (int) Math.round((double) furnace.fuel * 26D / (double) furnace.MAX_FUEL);
		int prog = (int) Math.round(furnace.progress * (88D - fuel));
		
		drawTexturedModalRect(guiLeft + 62, guiTop + 106 - prog - fuel, 176, 102 - prog - fuel, 56, prog);
		
		drawTexturedModalRect(guiLeft + 62, guiTop + 106 - fuel, 176, 128 - fuel, 56, fuel);
		
		if(furnace.isProgressing) {
			drawTexturedModalRect(guiLeft + 81, guiTop + 64, 176, 0, 14, 14);
		}

		drawRect(guiLeft + 27, guiTop + 85, guiLeft + 27 + furnace.tanks[0].getFill() * 14 / furnace.tanks[0].getMaxFill(), guiTop + 88, 0xFF800000);
		drawRect(guiLeft + 27, guiTop + 31, guiLeft + 27 + furnace.tanks[1].getFill() * 14 / furnace.tanks[1].getMaxFill(), guiTop + 34, 0xFF800000);
		// The three lower labels previously covered empty art: expose actual synchronized stock,
		// including the optional oxygen feed, without moving inventory slots or changing the texture.
		drawRect(guiLeft + 5, guiTop + 111, guiLeft + 155, guiTop + 126, 0xFFC6C6C6);
		for(int tank = 2; tank < 5; tank++) {
			int left = guiLeft + 8 + (tank - 2) * 50;
			drawRect(left, guiTop + 121, left + 45, guiTop + 125, 0xFF302F2C);
			int width = (int) Math.min(43L, (long) furnace.tanks[tank].getFill() * 43 / furnace.tanks[tank].getMaxFill());
			drawRect(left + 1, guiTop + 122, left + 1 + width, guiTop + 124, 0xFF000000 | furnace.tanks[tank].getTankType().getColor());
		}
	}
}
