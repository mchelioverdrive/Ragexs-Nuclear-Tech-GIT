package com.hbm.inventory.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.container.ContainerReactorZirnox;
import com.hbm.lib.RefStrings;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.tileentity.machine.TileEntityReactorZirnox;
import com.hbm.util.I18nUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class GUIReactorZirnox extends GuiInfoContainer {

	// fuck you
	private static final ResourceLocation texture = new ResourceLocation(RefStrings.MODID, "textures/gui/reactors/gui_zirnox.png");
	private TileEntityReactorZirnox zirnox;

	public GUIReactorZirnox(InventoryPlayer invPlayer, TileEntityReactorZirnox tile) {
		super(new ContainerReactorZirnox(invPlayer, tile));
		zirnox = tile;

		this.xSize = 310;
		this.ySize = 256;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		zirnox.steam.renderTankInfo(this, mouseX, mouseY, guiLeft + 160, guiTop + 108, 18, 12);
		zirnox.carbonDioxide.renderTankInfo(this, mouseX, mouseY, guiLeft + 142, guiTop + 108, 18, 12);
		zirnox.water.renderTankInfo(this, mouseX, mouseY, guiLeft + 178, guiTop + 108, 18, 12);
		this.drawCustomInfo(this, mouseX, mouseY, guiLeft + 160, guiTop + 33, 18, 17, new String[] {
			I18n.format("desc.gui.zirnox.core_temperature", Math.round(zirnox.graphiteHeat * 0.00001D * 780.0D + 20.0D)),
			I18n.format("desc.gui.zirnox.primary_temperature", Math.round(zirnox.heat * 0.00001D * 780.0D + 20.0D)),
			I18n.format("desc.gui.zirnox.peak_cladding_temperature", Math.round(zirnox.peakCladdingTemperature)),
			I18n.format("desc.gui.zirnox.rods", zirnox.controlRodInsertion, zirnox.targetControlRodInsertion),
			I18n.format("desc.gui.zirnox.scram_progress", zirnox.controlRodInsertion),
			I18n.format("desc.gui.zirnox.decay_heat", Math.round(zirnox.decayHeat)),
			I18n.format("desc.gui.zirnox.primary_contamination", Math.round(zirnox.primaryContamination)),
			I18n.format("desc.gui.zirnox.local_dose", zirnox.getLocalDoseRate()),
			I18n.format("desc.gui.zirnox.burst_detector", I18n.format("desc.gui.zirnox.burst." + getBurstDetectorState())),
			I18n.format("desc.gui.zirnox.active_trip", formatStates(zirnox.activeTripInput)),
			I18n.format("desc.gui.zirnox.cladding_damage", zirnox.claddingDamage, zirnox.claddingDamage / 1000.0D),
			I18n.format("desc.gui.zirnox.cladding_condition", I18n.format("desc.gui.zirnox.cladding." + getCladdingCondition())),
			I18n.format("desc.gui.zirnox.graphite_damage", zirnox.graphiteDamage, zirnox.graphiteDamage / 1000.0D)
		});
		this.drawCustomInfo(this, mouseX, mouseY, guiLeft + 144, guiTop + 35, 14, 14, new String[] {
			I18n.format("desc.gui.zirnox.state." + zirnox.shutdownReason),
			I18n.format("desc.gui.zirnox.restart"),
			I18n.format("desc.gui.zirnox.reserve", zirnox.getWaterReserve()),
			I18n.format("desc.gui.zirnox.relief", zirnox.shutdownWaterUsed)
		});
		this.drawCustomInfo(this, mouseX, mouseY, guiLeft + 178, guiTop + 33, 18, 17, new String[] { I18n.format("desc.gui.zirnox.primary_pressure", Math.round((zirnox.pressure) * 0.00001 * 30)) });
		
		String[] coolantText = I18nUtil.resolveKeyArray("desc.gui.zirnox.coolant");
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 36, 16, 16, guiLeft - 8, guiTop + 36 + 16, coolantText);
		
		String[] pressureText = I18nUtil.resolveKeyArray("desc.gui.zirnox.pressure");
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 36 + 16, 16, 16, guiLeft - 8, guiTop + 36 + 16 + 16, pressureText);

		if(zirnox.water.getFill() <= TileEntityReactorZirnox.FEEDWATER_TRIP_MB) {
			String[] warning1 = I18nUtil.resolveKeyArray("desc.gui.zirnox.warning1");
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 36 + 32, 16, 16, guiLeft - 8, guiTop + 36 + 32 + 16, warning1);
		}

		if(zirnox.carbonDioxide.getFill() < 11200) {
			String[] warning2 = I18nUtil.resolveKeyArray("desc.gui.zirnox.warning2");
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 36 + 32 + 16, 16, 16, guiLeft - 8, guiTop + 36 + 32 + 16 + 16, warning2);
		}

		if(zirnox.steam.getFill() >= zirnox.steam.getMaxFill()) {
			String[] warning3 = I18nUtil.resolveKeyArray("desc.gui.zirnox.warning3");
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 116, 16, 16, guiLeft - 8, guiTop + 132, warning3);
		}

		if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_LEAK_DAMAGE) {
			String warningKey;
			if(zirnox.getLocalDoseRate() <= 0.0D) {
				warningKey = "desc.gui.zirnox.warning_contained";
			} else if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_SEVERE_DAMAGE) {
				warningKey = "desc.gui.zirnox.warning_radiation_severe";
			} else {
				warningKey = "desc.gui.zirnox.warning_radiation";
			}
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 132, 16, 16,
				guiLeft - 8, guiTop + 148, I18nUtil.resolveKeyArray(warningKey));
		}
		if(zirnox.radioactiveReleaseTicks > 0) {
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft - 16, guiTop + 148, 16, 16,
				guiLeft - 8, guiTop + 164, I18nUtil.resolveKeyArray("desc.gui.zirnox.warning_release"));
		}
		if(mouseX >= guiLeft + 207 && mouseX < guiLeft + 303 && mouseY >= guiTop + 170 && mouseY < guiTop + 187) {
			java.util.List<String> faults = new java.util.ArrayList<String>();
			if(zirnox.testAutoTripsInhibited) faults.add(I18n.format("desc.gui.zirnox.fault.auto_trips"));
			if(zirnox.testRodDriveJammed) faults.add(I18n.format("desc.gui.zirnox.fault.rod_drive"));
			if(zirnox.testReliefValveJammed) faults.add(I18n.format("desc.gui.zirnox.fault.relief_valve"));
			if(!faults.isEmpty()) drawHoveringText(faults, mouseX, mouseY, fontRendererObj);
		}

		drawControlTooltips(mouseX, mouseY);

	}

	protected void mouseClicked(int x, int y, int i) {
		super.mouseClicked(x, y, i);

		String action = getControlAction(x - guiLeft, y - guiTop);
		if(action != null) sendAction(action, isShiftKeyDown());
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		String name = this.zirnox.hasCustomInventoryName() ? this.zirnox.getInventoryName() : I18n.format(this.zirnox.getInventoryName());

		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 4210752);
		String shutdownText = zirnox.shutdownLatched
			? I18n.format("desc.gui.zirnox.trip", I18n.format("desc.gui.zirnox.state." + zirnox.shutdownReason))
			: I18n.format("desc.gui.zirnox.state.none");
		this.fontRendererObj.drawString(shutdownText, 8, 151, zirnox.shutdownLatched ? 0xA02020 : 4210752);
		if(!"none".equals(zirnox.restartBlocker)) {
			this.fontRendererObj.drawString(I18n.format("desc.gui.zirnox.restart_blocked", formatBlockers()), 8, 162, 0xB06000);
		}
		this.fontRendererObj.drawString(I18n.format("desc.gui.zirnox.rods", zirnox.controlRodInsertion,
			zirnox.targetControlRodInsertion), 207, 12, 0x404040);
		this.fontRendererObj.drawString(I18n.format("desc.gui.zirnox.scram_progress", zirnox.controlRodInsertion), 207, 23, 0x404040);
		if(zirnox.shutdownLatched && zirnox.testRodDriveJammed && zirnox.controlRodInsertion < 100) {
			this.fontRendererObj.drawString(I18n.format("desc.gui.zirnox.scram_failed"), 207, 34, 0xA02020);
		}
		if(zirnox.testAutoTripsInhibited || zirnox.testRodDriveJammed || zirnox.testReliefValveJammed) {
			this.fontRendererObj.drawString(I18n.format("desc.gui.zirnox.test_override"), 207, 178, 0xA02020);
		}
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, 203, ySize);
		drawRect(guiLeft + 203, guiTop, guiLeft + xSize, guiTop + 205, 0xFFC6C6C6);
		drawRect(guiLeft + 204, guiTop + 1, guiLeft + xSize - 1, guiTop + 204, 0xFF303030);
		drawButton(207, 48, 96, 18, "SCRAM", 0xFF8C2020);
		drawButton(207, 70, 96, 18, "CONTROLLED SHUTDOWN", 0xFF9A6418);
		drawButton(207, 92, 96, 18, "RESET TRIP", "none".equals(zirnox.restartBlocker) ? 0xFF287A38 : 0xFF9A6418);
		drawButton(207, 114, 46, 18, "INSERT", 0xFF555555);
		drawButton(257, 114, 46, 18, "WITHDRAW", 0xFF555555);
		drawButton(207, 136, 96, 18, "VENT CO2", 0xFF555555);
		if(mc.thePlayer.capabilities.isCreativeMode) {
			drawButton(207, 188, 30, 14, "TRIP", zirnox.testAutoTripsInhibited ? 0xFF9C2020 : 0xFF555555);
			drawButton(240, 188, 30, 14, "ROD", zirnox.testRodDriveJammed ? 0xFF9C2020 : 0xFF555555);
			drawButton(273, 188, 30, 14, "REL", zirnox.testReliefValveJammed ? 0xFF9C2020 : 0xFF555555);
		}

		int s = zirnox.getGaugeScaled(6, 0);
		drawTexturedModalRect(guiLeft + 160, guiTop + 108, 238, 0 + 12 * s, 18, 12);

		int c = zirnox.getGaugeScaled(6, 1);
		drawTexturedModalRect(guiLeft + 142, guiTop + 108, 238, 0 + 12 * c, 18, 12);

		int w = zirnox.getGaugeScaled(6, 2);
		drawTexturedModalRect(guiLeft + 178, guiTop + 108, 238, 0 + 12 * w, 18, 12);

		int h = zirnox.getGaugeScaled(12, 3);
		drawTexturedModalRect(guiLeft + 160, guiTop + 33, 220, 0 + 18 * h, 18, 17);

		int p = zirnox.getGaugeScaled(12, 4);
		drawTexturedModalRect(guiLeft + 178, guiTop + 33, 220, 0 + 18 * p, 18, 17);

		if(zirnox.isOn) {
			for(int x = 0; x < 4; x++)
				for(int y = 0; y < 4; y++)
					drawTexturedModalRect(guiLeft + 7 + 36 * x, guiTop + 15 + 36 * y, 238, 238, 18, 18);
			for(int x = 0; x < 3; x++)
				for(int y = 0; y < 3; y++)
					drawTexturedModalRect(guiLeft + 25 + 36 * x, guiTop + 33 + 36 * y, 238, 238, 18, 18);
			drawTexturedModalRect(guiLeft + 142, guiTop + 15, 220, 238, 18, 18);
		}

		this.drawInfoPanel(guiLeft - 16, guiTop + 36, 16, 16, 2);
		this.drawInfoPanel(guiLeft - 16, guiTop + 36 + 16, 16, 16, 3);

		if(zirnox.water.getFill() <= TileEntityReactorZirnox.FEEDWATER_TRIP_MB)
			this.drawInfoPanel(guiLeft - 16, guiTop + 36 + 32, 16, 16, 6);

		if(zirnox.carbonDioxide.getFill() < 11200)
			this.drawInfoPanel(guiLeft - 16, guiTop + 36 + 32 + 16, 16, 16, 6);

		if(zirnox.steam.getFill() >= zirnox.steam.getMaxFill())
			this.drawInfoPanel(guiLeft - 16, guiTop + 116, 16, 16, 6);

		if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_LEAK_DAMAGE)
			this.drawInfoPanel(guiLeft - 16, guiTop + 132, 16, 16, 6);

		if(zirnox.radioactiveReleaseTicks > 0)
			this.drawInfoPanel(guiLeft - 16, guiTop + 148, 16, 16, 6);
	}

	private String getCladdingCondition() {
		if(zirnox.claddingDamage >= TileEntityReactorZirnox.MAX_CLADDING_DAMAGE) return "failed";
		if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_SEVERE_DAMAGE) return "severe";
		if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_LEAK_DAMAGE) return "leaking";
		return "intact";
	}

	private String getBurstDetectorState() {
		if(zirnox.claddingDamage >= TileEntityReactorZirnox.CLADDING_LEAK_DAMAGE) return "leak";
		if(zirnox.claddingDamage > 0) return "suspect";
		return "clear";
	}

	private void drawButton(int x, int y, int width, int height, String label, int color) {
		drawRect(guiLeft + x, guiTop + y, guiLeft + x + width, guiTop + y + height, color);
		int textX = guiLeft + x + (width - fontRendererObj.getStringWidth(label)) / 2;
		fontRendererObj.drawString(label, textX, guiTop + y + (height - 8) / 2, 0xFFFFFF);
	}

	private String getControlAction(int x, int y) {
		if(inBounds(x, y, 207, 48, 96, 18)) return "scram";
		if(inBounds(x, y, 207, 70, 96, 18)) return "controlledShutdown";
		if(inBounds(x, y, 207, 92, 96, 18)) return "resetTrip";
		if(inBounds(x, y, 207, 114, 46, 18)) return "insert";
		if(inBounds(x, y, 257, 114, 46, 18)) return "withdraw";
		if(inBounds(x, y, 207, 136, 96, 18)) return "vent";
		if(mc.thePlayer.capabilities.isCreativeMode && inBounds(x, y, 207, 188, 30, 14)) return "faultAutoTrips";
		if(mc.thePlayer.capabilities.isCreativeMode && inBounds(x, y, 240, 188, 30, 14)) return "faultRodDrive";
		if(mc.thePlayer.capabilities.isCreativeMode && inBounds(x, y, 273, 188, 30, 14)) return "faultReliefValve";
		return null;
	}

	private boolean inBounds(int x, int y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private void sendAction(String action, boolean fine) {
		NBTTagCompound control = new NBTTagCompound();
		control.setString("zirnoxAction", action);
		control.setBoolean("fine", fine);
		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(control, zirnox.xCoord, zirnox.yCoord, zirnox.zCoord));
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("hbm:block.rbmk_az5_cover"), 0.5F));
	}

	private String formatBlockers() {
		return formatStates(zirnox.restartBlocker);
	}

	private String formatStates(String states) {
		String[] blockers = states.split(",");
		StringBuilder text = new StringBuilder();
		for(String blocker : blockers) {
			if(text.length() > 0) text.append(", ");
			text.append(I18n.format("desc.gui.zirnox.state." + blocker));
		}
		return text.toString();
	}

	private void drawControlTooltips(int mouseX, int mouseY) {
		String action = getControlAction(mouseX - guiLeft, mouseY - guiTop);
		if(action == null) return;
		String key = "desc.gui.zirnox.control." + action;
		this.drawCustomInfo(this, mouseX, mouseY, mouseX, mouseY, 1, 1, I18nUtil.resolveKeyArray(key));
	}

}
