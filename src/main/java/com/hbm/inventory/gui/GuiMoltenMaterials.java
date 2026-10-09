package com.hbm.inventory.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.material.NTMMaterial.SmeltingBehavior;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.MoltenSelectionPacket;
import com.hbm.util.I18nUtil;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/** Shared interaction for the existing layered molten gauges. The popup also exposes subpixel quantities. */
public abstract class GuiMoltenMaterials extends GuiInfoContainer {
	private final List<MoltenTank> moltenTanks = new ArrayList<MoltenTank>();
	private MoltenTank expandedTank;
	private int firstRow;
	private static final int ROW_HEIGHT = 14;
	private static final int MENU_WIDTH = 150;

	protected GuiMoltenMaterials(Container container) {
		super(container);
	}

	protected void addMoltenTank(int tank, List<MaterialStack> stacks, IntSupplier capacity, IntSupplier selected,
			Predicate<MaterialStack> selectable, int x, int bottom, int tankWidth, int tankHeight, int u, int v, int additiveOffset) {
		moltenTanks.add(new MoltenTank(tank, stacks, capacity, selected, selectable, x, bottom, tankWidth, tankHeight, u, v, additiveOffset));
	}

	protected void drawMoltenTanks() {
		for(MoltenTank tank : moltenTanks) {
			long quantity = 0;
			int lastHeight = 0;
			for(MaterialStack stack : tank.stacks) {
				quantity += Math.max(0, stack.amount);
				int targetHeight = tank.heightAt(quantity);
				if(stack.material == null || targetHeight <= lastHeight) continue;
				int offset = stack.material.smeltable == SmeltingBehavior.ADDITIVE ? tank.additiveOffset : 0;
				Color color = new Color(stack.material.moltenColor);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GL11.glColor3f(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F);
				drawTexturedModalRect(guiLeft + tank.x, guiTop + tank.bottom - targetHeight, tank.u + offset, tank.v - targetHeight, tank.width, targetHeight - lastHeight);
				GL11.glEnable(GL11.GL_BLEND);
				GL11.glColor4f(1F, 1F, 1F, 0.3F);
				drawTexturedModalRect(guiLeft + tank.x, guiTop + tank.bottom - targetHeight, tank.u + offset, tank.v - targetHeight, tank.width, targetHeight - lastHeight);
				GL11.glDisable(GL11.GL_BLEND);
				lastHeight = targetHeight;
			}
		}
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glColor4f(1F, 1F, 1F, 1F);
		// Draw borders after all textured layers so they cannot affect the next gauge's blending.
		for(MoltenTank tank : moltenTanks) {
			long quantity = 0;
			for(MaterialStack stack : tank.stacks) {
				int lower = tank.heightAt(quantity);
				quantity += Math.max(0, stack.amount);
				int upper = tank.heightAt(quantity);
				if(stack.material != null && stack.material.id == tank.selected.getAsInt() && upper > lower) {
					drawBorder(guiLeft + tank.x, guiTop + tank.bottom - upper, tank.width, upper - lower, 0xFFFFFF80);
				}
			}
		}
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		try {
			drawMoltenOverlay(mouseX, mouseY);
		} finally {
			GL11.glPopAttrib();
			GL11.glColor4f(1F, 1F, 1F, 1F);
		}
	}

	private void drawMoltenOverlay(int mouseX, int mouseY) {
		if(expandedTank != null && expandedTank.stacks.isEmpty()) expandedTank = null;
		if(expandedTank != null) {
			MoltenTank tank = expandedTank;
			firstRow = Math.min(firstRow, Math.max(0, tank.stacks.size() - menuRows(tank)));
			int x = menuX(tank), y = menuY(tank), rows = menuRows(tank);
			drawRect(x, y, x + MENU_WIDTH, y + rows * ROW_HEIGHT + 12, 0xFF202020);
			drawBorder(x, y, MENU_WIDTH, rows * ROW_HEIGHT + 12, 0xFF808080);
			for(int i = 0; i < rows; i++) {
				MaterialStack stack = tank.stacks.get(firstRow + i);
				boolean selected = stack.material != null && stack.material.id == tank.selected.getAsInt();
				if(selected) drawRect(x + 1, y + i * ROW_HEIGHT + 1, x + MENU_WIDTH - 1, y + (i + 1) * ROW_HEIGHT, 0xFF505020);
				if(stack.material == null) continue;
				drawRect(x + 4, y + i * ROW_HEIGHT + 4, x + 10, y + i * ROW_HEIGHT + 10, 0xFF000000 | stack.material.moltenColor);
				String name = I18nUtil.resolveKey(stack.material.getUnlocalizedName());
				fontRendererObj.drawString(fontRendererObj.trimStringToWidth(name, MENU_WIDTH - 18), x + 14, y + i * ROW_HEIGHT + 3,
						tank.selectable.test(stack) ? 0xFFFF80 : 0x909090);
			}
			fontRendererObj.drawString((firstRow + 1) + "-" + (firstRow + rows) + "/" + tank.stacks.size(), x + 4, y + rows * ROW_HEIGHT + 2, 0xB0B0B0);
			int row = menuRow(tank, mouseX, mouseY);
			if(row >= 0) drawMaterialInfo(tank, tank.stacks.get(row), mouseX, mouseY);
			return;
		}
		for(MoltenTank tank : moltenTanks) {
			if(!tank.contains(mouseX - guiLeft, mouseY - guiTop)) continue;
			List<String> lines = new ArrayList<String>();
			for(MaterialStack stack : tank.stacks) {
				if(stack.material == null) continue;
				boolean selected = stack.material.id == tank.selected.getAsInt();
				lines.add((selected ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW) + materialText(stack));
				if(selected) lines.add(EnumChatFormatting.GREEN + I18n.format("foundry.selectedOutput"));
			}
			if(lines.isEmpty()) lines.add(EnumChatFormatting.RED + I18n.format("foundry.empty"));
			MaterialStack hovered = tank.stackAt(mouseY - guiTop);
			if(hovered != null) lines.add(I18n.format(tank.selectable.test(hovered) ? "foundry.selectOutput" : "foundry.reservedMaterial"));
			lines.add(I18n.format("foundry.openMaterials"));
			func_146283_a(lines, mouseX, mouseY);
			break;
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if(expandedTank != null) {
			int row = menuRow(expandedTank, mouseX, mouseY);
			if(row >= 0) {
				if(button == 0 && select(expandedTank, expandedTank.stacks.get(row))) expandedTank = null;
				return;
			}
			if(inMenu(expandedTank, mouseX, mouseY)) return;
			expandedTank = null;
		}
		for(MoltenTank tank : moltenTanks) {
			if(!tank.contains(mouseX - guiLeft, mouseY - guiTop)) continue;
			if(button == 0) {
				MaterialStack stack = tank.stackAt(mouseY - guiTop);
				if(stack != null) { select(tank, stack); return; }
			}
			if((button == 0 || button == 1) && !tank.stacks.isEmpty()) { expandedTank = tank; firstRow = 0; }
			return;
		}
		mouseClickedOutsideMolten(mouseX, mouseY, button);
	}

	protected void mouseClickedOutsideMolten(int mouseX, int mouseY, int button) {
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if(expandedTank != null && wheel != 0) {
			firstRow = Math.max(0, Math.min(expandedTank.stacks.size() - menuRows(expandedTank), firstRow + (wheel > 0 ? -1 : 1)));
		}
	}

	@Override
	protected void keyTyped(char character, int key) {
		if(key == Keyboard.KEY_ESCAPE && expandedTank != null) expandedTank = null;
		else super.keyTyped(character, key);
	}

	private boolean select(MoltenTank tank, MaterialStack stack) {
		if(!tank.selectable.test(stack)) return false;
		PacketDispatcher.wrapper.sendToServer(new MoltenSelectionPacket(inventorySlots.windowId, tank.id, stack.material.id));
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
		return true;
	}

	private String materialText(MaterialStack stack) {
		return I18nUtil.resolveKey(stack.material.getUnlocalizedName()) + ": " + Mats.formatAmount(stack.amount, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
	}

	private void drawMaterialInfo(MoltenTank tank, MaterialStack stack, int x, int y) {
		if(stack.material == null) return;
		List<String> lines = new ArrayList<String>();
		lines.add(EnumChatFormatting.YELLOW + materialText(stack));
		if(stack.material.id == tank.selected.getAsInt()) lines.add(EnumChatFormatting.GREEN + I18n.format("foundry.selectedOutput"));
		lines.add(I18n.format(tank.selectable.test(stack) ? "foundry.selectOutput" : "foundry.reservedMaterial"));
		lines.add(I18n.format("foundry.scrollMaterials"));
		func_146283_a(lines, x, y);
	}

	private int menuRows(MoltenTank tank) { return Math.min(tank.stacks.size(), Math.max(1, Math.min(8, (height - 16) / ROW_HEIGHT))); }
	private int menuX(MoltenTank tank) { return Math.max(0, Math.min(width - MENU_WIDTH, guiLeft + tank.x + tank.width + 4)); }
	private int menuY(MoltenTank tank) { return Math.max(0, Math.min(height - menuRows(tank) * ROW_HEIGHT - 12, guiTop + tank.bottom - tank.height)); }
	private boolean inMenu(MoltenTank tank, int x, int y) { return x >= menuX(tank) && x < menuX(tank) + MENU_WIDTH && y >= menuY(tank) && y < menuY(tank) + menuRows(tank) * ROW_HEIGHT + 12; }
	private int menuRow(MoltenTank tank, int x, int y) {
		firstRow = Math.min(firstRow, Math.max(0, tank.stacks.size() - menuRows(tank)));
		if(!inMenu(tank, x, y)) return -1;
		int row = (y - menuY(tank)) / ROW_HEIGHT;
		return row < menuRows(tank) ? firstRow + row : -1;
	}

	private void drawBorder(int x, int y, int w, int h, int color) {
		drawRect(x, y, x + w, y + 1, color);
		drawRect(x, y + h - 1, x + w, y + h, color);
		drawRect(x, y, x + 1, y + h, color);
		drawRect(x + w - 1, y, x + w, y + h, color);
	}

	private static class MoltenTank {
		final int id, x, bottom, width, height, u, v, additiveOffset;
		final List<MaterialStack> stacks;
		final IntSupplier capacity, selected;
		final Predicate<MaterialStack> selectable;

		MoltenTank(int id, List<MaterialStack> stacks, IntSupplier capacity, IntSupplier selected, Predicate<MaterialStack> selectable,
				int x, int bottom, int width, int height, int u, int v, int additiveOffset) {
			this.id = id; this.stacks = stacks; this.capacity = capacity; this.selected = selected; this.selectable = selectable;
			this.x = x; this.bottom = bottom; this.width = width; this.height = height; this.u = u; this.v = v; this.additiveOffset = additiveOffset;
		}

		int heightAt(long amount) { return (int) Math.min(height, amount * height / Math.max(1, capacity.getAsInt())); }
		boolean contains(int mouseX, int mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= bottom - height && mouseY < bottom; }
		MaterialStack stackAt(int mouseY) {
			long quantity = 0;
			for(MaterialStack stack : stacks) {
				int lower = heightAt(quantity);
				quantity += Math.max(0, stack.amount);
				int upper = heightAt(quantity);
				if(mouseY >= bottom - upper && mouseY < bottom - lower) return stack;
			}
			return null;
		}
	}
}
