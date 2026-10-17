package com.hbm.inventory.gui;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.RefStrings;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** HBM pixel conventions: raised casing, recessed sockets, graduated tanks and engraved templates.
 * Coordinates are screen coordinates; tank rectangles describe both the fluid interior and hover.
 * Draw in the background layer, before Minecraft draws inventory items and their tooltips.
 */
@SideOnly(Side.CLIENT)
public final class MachineGuiParts {
    private static final ResourceLocation CHEMPLANT = new ResourceLocation(RefStrings.MODID, "textures/gui/gui_chemplant.png");
    private static final ResourceLocation ASSEMBLER = new ResourceLocation(RefStrings.MODID, "textures/gui/gui_assembler.png");
    public static final int TEXT = 0x404040;

    private MachineGuiParts() { }

    public static void panel(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, 0xFF373737);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFFFFFFF);
        Gui.drawRect(x + 3, y + 3, x + width - 1, y + height - 1, 0xFF555555);
        Gui.drawRect(x + 3, y + 3, x + width - 3, y + height - 3, 0xFFC6C6C6);
    }

    public static void recess(int x, int y, int width, int height) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFFFFFFF);
        Gui.drawRect(x - 1, y - 1, x + width, y + height, 0xFF373737);
        Gui.drawRect(x, y, x + width, y + height, 0xFF8B8B8B);
    }

    public static void slot(Slot slot, int left, int top) {
        recess(left + slot.xDisplayPosition, top + slot.yDisplayPosition, 16, 16);
    }

    public static void template(GuiInfoContainer gui, Slot slot, int left, int top) {
        int x = left + slot.xDisplayPosition, y = top + slot.yDisplayPosition;
        circuit(gui, slot, left, top);
        if(!slot.getHasStack()) sprite(gui, CHEMPLANT, x, y, 80, 54, 16, 16);
    }

    public static void circuit(GuiInfoContainer gui, Slot slot, int left, int top) {
        // Native assembler upgrade socket, including its chip contacts, drawn behind the item.
        sprite(gui, ASSEMBLER, left + slot.xDisplayPosition - 1, top + slot.yDisplayPosition - 1, 151, 17, 18, 18);
    }

    public static void battery(GuiInfoContainer gui, Slot slot, int left, int top) {
        sprite(gui, CHEMPLANT, left + slot.xDisplayPosition - 1, top + slot.yDisplayPosition - 1, 79, 17, 18, 18);
    }

    public static void tank(FluidTank tank, int x, int y, double z, int width, int height) {
        recess(x, y, width, height);
        tank.renderTank(x, y + height, z, width, height);
        for(int tick = 4; tick < height; tick += 5)
            Gui.drawRect(x, y + tick, x + (width < 8 ? 1 : tick % 10 == 4 ? 3 : 2), y + tick + 1, 0xFF912F2F);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static int scaled(long value, long capacity, int pixels) {
        return capacity <= 0 ? 0 : (int) (Math.max(0D, Math.min(1D, (double) value / capacity)) * pixels);
    }

    public static void power(GuiInfoContainer gui, int x, int y, long stored, long capacity, int width) {
        recess(x, y, width, 52);
        int fill = scaled(stored, capacity, 52);
        sprite(gui, ASSEMBLER, x, y + 52 - fill, 176, 52 - fill, width, fill);
    }

    public static void progress(int x, int y, int width, int height, int value, int maximum, boolean active) {
        recess(x, y, width, height);
        int fill = scaled(value, maximum, width);
        Gui.drawRect(x, y, x + fill, y + height, active ? 0xFF588A43 : 0xFF8B8056);
        if(fill > 0) Gui.drawRect(x, y, x + fill, y + 1, active ? 0xFFB1CA9E : 0xFFD0C49A);
        for(int row = 0; row < 3; row++)
            Gui.drawRect(x + width - 4 + row, y + height / 2 - 2 + row,
                    x + width - 3 + row, y + height / 2 + 3 - row, 0xFF373737);
    }

    public static void label(FontRenderer font, String text, int x, int y, int width, int color) {
        font.drawString(font.trimStringToWidth(text, width), x, y, color);
    }

    public static void centeredLabel(FontRenderer font, String text, int x, int y, int width, int color) {
        String fitted = font.trimStringToWidth(text, width);
        font.drawString(fitted, x + (width - font.getStringWidth(fitted)) / 2, y, color);
    }

    public static void sprite(GuiInfoContainer gui, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        if(width > 0 && height > 0) gui.drawTexturedModalRect(x, y, u, v, width, height);
    }
}
