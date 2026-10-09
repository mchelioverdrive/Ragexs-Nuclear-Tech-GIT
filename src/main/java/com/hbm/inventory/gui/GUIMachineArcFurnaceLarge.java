package com.hbm.inventory.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.container.ContainerMachineArcFurnaceLarge;
import com.hbm.inventory.material.NTMMaterial.SmeltingBehavior;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.tileentity.machine.TileEntityMachineArcFurnaceLarge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class GUIMachineArcFurnaceLarge extends GuiMoltenMaterials {

	private static ResourceLocation texture = new ResourceLocation(RefStrings.MODID + ":textures/gui/processing/gui_arc_furnace.png");
	private TileEntityMachineArcFurnaceLarge arc;

	public GUIMachineArcFurnaceLarge(InventoryPlayer invPlayer, TileEntityMachineArcFurnaceLarge arc) {
		super(new ContainerMachineArcFurnaceLarge(invPlayer, arc));
		this.arc = arc;
		
		this.xSize = 176;
		this.ySize = 240;
		addMoltenTank(0, arc.liquids, () -> arc.maxLiquid, () -> arc.selectedLiquidMaterial,
				stack -> stack.material != null && stack.amount > 0 && stack.material.smeltable == SmeltingBehavior.SMELTABLE,
				152, 106, 16, 70, 208, 70, 0);
	}
	
	@Override
	public void drawScreen(int x, int y, float interp) {
		super.drawScreen(x, y, interp);

		this.drawElectricityInfo(this, x, y, guiLeft + 8, guiTop + 36, 7, 70, arc.getStoredEnergyQuanta(), arc.getEnergyCapacityQuanta());
	}
	
	@Override
	protected void mouseClickedOutsideMolten(int x, int y, int k) {
		super.mouseClickedOutsideMolten(x, y, k);
		
		if(this.checkClick(x, y, 151, 17, 18, 18)) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("liquid", true);
			PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, arc.xCoord, arc.yCoord, arc.zCoord));
		}
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.arc.hasCustomInventoryName() ? this.arc.getInventoryName() : I18n.format(this.arc.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 0xffffff);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int x, int y) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		if(arc.liquidMode) drawTexturedModalRect(guiLeft + 151, guiTop + 17, 190, 18, 18, 18);
		if(arc.isProgressing) drawTexturedModalRect(guiLeft + 7, guiTop + 17, 190, 0, 18, 18);
		
		int p = (int) (arc.getStoredEnergyQuanta() * 70 / arc.maxPower);
		drawTexturedModalRect(guiLeft + 8, guiTop + 106 - p, 176, 70 - p, 7, p);
		
		int o = (int) (arc.getProjectedProgress() * 70);
		drawTexturedModalRect(guiLeft + 17, guiTop + 106 - o, 183, 70 - o, 7, o);
		
		drawMoltenTanks();
	}
}
