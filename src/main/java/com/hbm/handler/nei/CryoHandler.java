package com.hbm.handler.nei;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.recipes.CryoRecipes;

import codechicken.nei.PositionedStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

public class CryoHandler extends NEIUniversalHandler {

	public CryoHandler() {
		super("Cryogenic Distillation", ModBlocks.machine_cryo_distill, CryoRecipes.getCryoRecipes());
	}

	@Override
	public void drawBackground(int recipe) {
		// Draw the panel without the shared NEI atlas, which can fail resource lookup.
		Gui.drawRect(0, 0, 166, 65, 0xffc6c6c6);
		RecipeSet page = (RecipeSet) arecipes.get(recipe);
		for(PositionedStack input : page.input) drawSlot(input);
		for(PositionedStack output : page.output) drawSlot(output);
		drawSlot(page.machinePositioned);
		Gui.drawRect(147, 1, 165, 19, 0xff8b8b8b);
	}

	private void drawSlot(PositionedStack stack) {
		Gui.drawRect(stack.relx - 1, stack.rely - 1, stack.relx + 17, stack.rely + 17, 0xff8b8b8b);
	}

	@Override
	public void drawForeground(int recipe) {
		// The default foreground would bind the same unused background atlas again.
		GL11.glColor4f(1, 1, 1, 1);
		GL11.glDisable(GL11.GL_LIGHTING);
		FontRenderer font = Minecraft.getMinecraft().fontRenderer;
		font.drawString("->", 77, 17, 0x404040);
		font.drawString("?", 153, 6, 0x404040);
		drawExtras(recipe);
	}

	@Override
	public String getKey() {
		return "ntmCryodistill";
	}

}

