package com.hbm.inventory.gui;

import com.hbm.inventory.container.ContainerStapler;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemStapledTemplateBundle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class GUIStapler extends GuiContainer {

	private static final int LIST_X = 12, LIST_WIDTH = 205, ROW_HEIGHT = 19, ROWS = 4;
	private final ContainerStapler container;
	private final boolean stapler;
	private final int listY;
	private int scroll;
	private GuiButton add, remove, up, down;

	public GUIStapler(ContainerStapler container) {
		super(container);
		this.container = container;
		stapler = container.isStapler();
		xSize = 248;
		ySize = stapler ? 238 : 200;
		listY = stapler ? 65 : 27;
	}

	@Override
	public void initGui() {
		super.initGui();
		buttonList.clear();
		if(stapler) {
			buttonList.add(add = new GuiButton(0, guiLeft + 12, guiTop + 46, 74, 16, ""));
			buttonList.add(remove = new GuiButton(1, guiLeft + 90, guiTop + 46, 104, 16, I18n.format("gui.stapler.remove")));
		}
		buttonList.add(up = new GuiButton(2, guiLeft + 221, guiTop + listY, 15, 16, "^"));
		buttonList.add(down = new GuiButton(3, guiLeft + 221, guiTop + listY + ROWS * ROW_HEIGHT - 16, 15, 16, "v"));
		if(!stapler) {
			int selected = ItemStapledTemplateBundle.getSelectedIndex(container.getBundle());
			scroll = Math.max(0, selected - ROWS + 1);
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		ItemStack bundle = container.getBundle();
		int count = ItemStapledTemplateBundle.count(bundle);
		scrollBy(0);
		up.enabled = scroll > 0;
		down.enabled = scroll + ROWS < count;
		if(stapler) {
			add.displayString = I18n.format("gui.stapler.add", container.getStapleCost());
			add.enabled = container.canAddFromGui();
			remove.enabled = count > 0 && ItemStapledTemplateBundle.durability(bundle) > 0;
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		MachineGuiParts.panel(guiLeft, guiTop, xSize, ySize);
		MachineGuiParts.recess(guiLeft + LIST_X, guiTop + listY, LIST_WIDTH, ROWS * ROW_HEIGHT);
		for(Object entry : container.inventorySlots) MachineGuiParts.slot((Slot) entry, guiLeft, guiTop);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		ItemStack bundle = container.getBundle();
		int count = ItemStapledTemplateBundle.count(bundle);
		int selected = ItemStapledTemplateBundle.getSelectedIndex(bundle);
		int text = MachineGuiParts.TEXT;
		fontRendererObj.drawString(I18n.format(stapler ? "item.stapler.name" : "gui.stapler.select"), 12, 7, text);
		MachineGuiParts.label(fontRendererObj, I18n.format("gui.stapler.count", count), stapler ? 73 : 12,
				stapler ? 7 : listY - 11, stapler ? 75 : 93, text);
		if(bundle != null) {
			String durability = I18n.format("gui.stapler.durability", ItemStapledTemplateBundle.durability(bundle),
					ItemStapledTemplateBundle.maxDurability(bundle));
			MachineGuiParts.label(fontRendererObj, durability, stapler ? 151 : 105, stapler ? 7 : listY - 11,
					stapler ? 85 : 131, text);
		}
		fontRendererObj.drawString(I18n.format("container.inventory"), 44, listY + 81, text);
		if(stapler) {
			MachineGuiParts.centeredLabel(fontRendererObj, I18n.format("item.staples.name"), 12, 18, 60, text);
			MachineGuiParts.centeredLabel(fontRendererObj, I18n.format("gui.stapler.template"), 68, 18, 56, text);
			MachineGuiParts.centeredLabel(fontRendererObj, I18n.format("gui.stapler.bundle"), 124, 18, 52, text);
			MachineGuiParts.centeredLabel(fontRendererObj, I18n.format("gui.stapler.output"), 184, 18, 52, text);
		}
		if(count == 0) fontRendererObj.drawString(I18n.format("gui.stapler.empty"), LIST_X + 6, listY + 6, text);
		ItemStack hovered = null;
		for(int row = 0; row < ROWS && scroll + row < count; row++) {
			int index = scroll + row;
			int y = listY + row * ROW_HEIGHT;
			ItemStack template = ItemStapledTemplateBundle.getTemplate(bundle, index);
			boolean over = rowAt(mouseX, mouseY) == row;
			boolean active = selected == index;
			drawRect(LIST_X, y, LIST_X + LIST_WIDTH, y + ROW_HEIGHT - 1,
					active ? 0xFF466438 : over ? 0xFFDDDDDD : 0xFFBEBEBE);
			if(template != null) {
				RenderHelper.enableGUIStandardItemLighting();
				itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), template, LIST_X + 2, y + 1);
				RenderHelper.disableStandardItemLighting();
				String category = I18n.format(template.getItem() == ModItems.assembly_template ? "gui.stapler.assembly" : "gui.stapler.chemistry");
				MachineGuiParts.label(fontRendererObj, template.getDisplayName(), LIST_X + 21, y + 5, 119, active ? 0xFFFFFF : text);
				MachineGuiParts.label(fontRendererObj, category, LIST_X + 144, y + 5, 60, active ? 0xFFFFFF : text);
				if(over) hovered = template;
			}
		}
		if(hovered != null && mc.thePlayer.inventory.getItemStack() == null)
			renderToolTip(hovered, mouseX - guiLeft, mouseY - guiTop);
		GL11.glColor4f(1, 1, 1, 1);
	}

	private int rowAt(int mouseX, int mouseY) {
		int x = mouseX - guiLeft, y = mouseY - guiTop - listY;
		return x >= LIST_X && x < LIST_X + LIST_WIDTH && y >= 0 && y < ROWS * ROW_HEIGHT ? y / ROW_HEIGHT : -1;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		int row = rowAt(mouseX, mouseY);
		if(row >= 0 && button == 0 && mc.thePlayer.inventory.getItemStack() == null) {
			int index = scroll + row;
			if(index < ContainerStapler.ADD && index < ItemStapledTemplateBundle.count(container.getBundle()))
				sendAction(index);
			return;
		}
		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int delta = Mouse.getEventDWheel();
		if(delta != 0) scrollBy(delta > 0 ? -1 : 1);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		switch(button.id) {
		case 0: sendAction(ContainerStapler.ADD); break;
		case 1: sendAction(ContainerStapler.REMOVE); break;
		case 2: scrollBy(-ROWS); break;
		case 3: scrollBy(ROWS); break;
		default: break;
		}
	}

	private void scrollBy(int rows) {
		int max = Math.max(0, ItemStapledTemplateBundle.count(container.getBundle()) - ROWS);
		scroll = Math.max(0, Math.min(max, scroll + rows));
	}

	private void sendAction(int action) {
		mc.playerController.sendEnchantPacket(container.windowId, action);
	}
}
