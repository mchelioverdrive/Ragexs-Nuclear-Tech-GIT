package com.hbm.inventory.gui;

import java.util.Collections;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.container.ContainerMachineMissileAssembly;
import com.hbm.items.weapon.MissileDefinition;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.AuxButtonPacket;
import com.hbm.render.util.MissileRosterModel;
import com.hbm.tileentity.machine.TileEntityMachineMissileAssembly;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class GUIMachineMissileAssembly extends GuiInfoContainer {
	private static final ResourceLocation TEXTURE = new ResourceLocation(RefStrings.MODID + ":textures/gui/gui_missile_assembly.png");
	private final TileEntityMachineMissileAssembly assembler;

	public GUIMachineMissileAssembly(InventoryPlayer inventory, TileEntityMachineMissileAssembly assembler) {
		super(new ContainerMachineMissileAssembly(inventory, assembler));
		this.assembler = assembler;
		this.xSize = 176;
		this.ySize = 222;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTick) {
		super.drawScreen(mouseX, mouseY, partialTick);
		if(mc.thePlayer.inventory.getItemStack() != null) return;

		MissileDefinition missile = assembler.getAirframe();
		if(over(mouseX, mouseY, 8, 36, 16, 16) && assembler.getStackInSlot(0) == null) {
			showHint(missile == null ? I18n.format("missile.assembly.circuit")
					: new ItemStack(missile.targetingCircuit()).getDisplayName(), mouseX, mouseY);
		} else if(over(mouseX, mouseY, 26, 36, 16, 16) && assembler.getStackInSlot(1) == null) {
			showHint(missile == null ? I18n.format("missile.assembly.warhead")
					: new ItemStack(missile.warhead()).getDisplayName(), mouseX, mouseY);
		} else if(over(mouseX, mouseY, 44, 36, 16, 16) && missile == null && assembler.getStackInSlot(2) == null) {
			showHint(I18n.format("missile.airframe.assembly"), mouseX, mouseY);
		} else if(over(mouseX, mouseY, 115, 35, 18, 18)) {
			showHint(I18n.format("missile.assembly.build"), mouseX, mouseY);
		}
	}

	private void showHint(String text, int mouseX, int mouseY) {
		func_146283_a(Collections.singletonList(text), mouseX, mouseY);
	}

	private boolean over(int x, int y, int left, int top, int width, int height) {
		return x >= guiLeft + left && x < guiLeft + left + width && y >= guiTop + top && y < guiTop + top + height;
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);
		if(button == 0 && over(x, y, 115, 35, 18, 18) && assembler.canBuild()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
			PacketDispatcher.wrapper.sendToServer(new AuxButtonPacket(assembler.xCoord, assembler.yCoord, assembler.zCoord, 0, 0));
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String name = assembler.hasCustomInventoryName() ? assembler.getInventoryName() : I18n.format(assembler.getInventoryName());
		center(name, 6);
		fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 94, 4210752);
		MissileDefinition missile = assembler.getPreviewMissile();
		if(missile != null) {
			center(I18n.format(missile.translationKey() + ".name"), 62);
			center(I18n.format(missile.classification.translationKey()), 74);
		}
	}

	private void center(String text, int y) {
		text = fontRendererObj.trimStringToWidth(text, xSize - 12);
		fontRendererObj.drawString(text, (xSize - fontRendererObj.getStringWidth(text)) / 2, y, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		mc.getTextureManager().bindTexture(TEXTURE);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		MissileDefinition airframe = assembler.getAirframe();
		if(airframe != null) drawTexturedModalRect(guiLeft + 49, guiTop + 23, 194, 0, 6, 8);
		if(assembler.hasMatchingWarhead(airframe)) drawTexturedModalRect(guiLeft + 31, guiTop + 23, 194, 0, 6, 8);
		if(assembler.hasMatchingCircuit(airframe)) drawTexturedModalRect(guiLeft + 13, guiTop + 23, 194, 0, 6, 8);
		if(assembler.canBuild()) drawTexturedModalRect(guiLeft + 115, guiTop + 35, 176, 0, 18, 18);

		MissileRosterModel model = MissileRosterModel.get(assembler.getPreviewMissile());
		if(model != null) {
			GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT);
			GL11.glPushMatrix();
			GL11.glTranslatef(guiLeft + 32, guiTop + 103, 100F);
			GL11.glRotatef(-90F, 0F, 0F, 1F);
			GL11.glRotatef(System.currentTimeMillis() / 25 % 360, 0F, 1F, 0F);
			float scale = 112F / model.height;
			GL11.glScalef(scale, scale, scale);
			RenderHelper.enableGUIStandardItemLighting();
			model.render();
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}
}
