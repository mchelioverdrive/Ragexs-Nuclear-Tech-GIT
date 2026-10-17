package com.hbm.inventory.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.container.ContainerMachineCryoDistill;
import com.hbm.lib.RefStrings;
import com.hbm.tileentity.machine.TileEntityMachineCryoDistill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

public class GUIMachineCryoDistill extends GuiInfoContainer {
	
	private static ResourceLocation texture = new ResourceLocation(RefStrings.MODID + ":textures/gui/processing/gui_cryodistill.png");
	private TileEntityMachineCryoDistill cryo;

	public GUIMachineCryoDistill(InventoryPlayer invPlayer, TileEntityMachineCryoDistill tedf) {
		super(new ContainerMachineCryoDistill(invPlayer, tedf));
        setMachineInfoOwner(tedf);
		cryo = tedf;
		
		this.xSize = 176;
		this.ySize = 238;
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		cryo.tanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 19, guiTop + 17, 16, 52);
		cryo.tanks[1].renderTankInfo(this, mouseX, mouseY, guiLeft + 57, guiTop + 17, 16, 52);
		cryo.tanks[2].renderTankInfo(this, mouseX, mouseY, guiLeft + 79, guiTop + 17, 16, 52);
		cryo.tanks[3].renderTankInfo(this, mouseX, mouseY, guiLeft + 101, guiTop + 17, 16, 52);
		cryo.tanks[4].renderTankInfo(this, mouseX, mouseY, guiLeft + 123, guiTop + 17, 16, 52);
		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + 145, guiTop + 17, 16, 52, cryo.getStoredEnergyQuanta(), cryo.maxPower);
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.cryo.hasCustomInventoryName() ? this.cryo.getInventoryName() : I18n.format(this.cryo.getInventoryName());
		
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 5, 0xffffff);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 100, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		
		int j = MachineGuiParts.scaled(cryo.getStoredEnergyQuanta(), cryo.maxPower, 52);
		drawTexturedModalRect(guiLeft + 145, guiTop + 69 - j, 176, 52 - j, 16, j);
		
		cryo.tanks[0].renderTank(guiLeft + 19, guiTop + 69, this.zLevel, 16, 52);
		cryo.tanks[1].renderTank(guiLeft + 57, guiTop + 69, this.zLevel, 16, 52);
		cryo.tanks[2].renderTank(guiLeft + 79, guiTop + 69, this.zLevel, 16, 52);
		cryo.tanks[3].renderTank(guiLeft + 101, guiTop + 69, this.zLevel, 16, 52);
		cryo.tanks[4].renderTank(guiLeft + 123, guiTop + 69, this.zLevel, 16, 52);

	}
}
