package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerMoltenSaltReactor;
import com.hbm.tileentity.machine.TileEntityMoltenSaltReactor;

import net.minecraft.client.resources.I18n;

public class GUIMoltenSaltReactor extends GuiInfoContainer {

	private TileEntityMoltenSaltReactor reactor;

	public GUIMoltenSaltReactor(TileEntityMoltenSaltReactor reactor) {
		super(new ContainerMoltenSaltReactor(reactor));
		this.reactor = reactor;
		this.xSize = 176;
		this.ySize = 116;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String name = I18n.format(this.reactor.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 0xFFFFFF);
		this.fontRendererObj.drawString(reactor.assembled ? "Assembled" : "Not assembled", 8, 22, reactor.assembled ? 0x55FF55 : 0xFF5555);
		this.fontRendererObj.drawString("Blocks: " + reactor.reactorBlocks + "  Ports: " + reactor.portCount, 8, 36, 0xE0E0E0);
		this.fontRendererObj.drawString("Channels: " + reactor.channelCount + "  Heat Ex: " + reactor.heatExchangerCount, 8, 50, 0xE0E0E0);
		this.fontRendererObj.drawString("Sources: " + reactor.sourceCount, 8, 64, 0xE0E0E0);
		this.fontRendererObj.drawString("Salt: " + reactor.tanks[0].getFill() + "/" + reactor.tanks[0].getMaxFill() + " mB", 8, 78, 0xE0E0E0);
		this.fontRendererObj.drawString("Hot Salt: " + reactor.tanks[1].getFill() + "/" + reactor.tanks[1].getMaxFill() + " mB", 8, 92, 0xE0E0E0);
		this.fontRendererObj.drawString("Rate: " + reactor.output + "/" + reactor.getMaxProcessRate() + " mB/t", 8, 106, 0xE0E0E0);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF202020);
		drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + ySize - 4, 0xFF303030);
	}
}
