package com.hbm.render.block;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.machine.MachineMoltenSaltReactorPort;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class RenderMSRPort implements ISimpleBlockRenderingHandler {

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
		GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		renderInventoryBox(block, renderer, tessellator, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
		renderInventoryBox(block, renderer, tessellator, 0.25D, 0.25D, -0.125D, 0.75D, 0.75D, 1.125D);
		renderInventoryBox(block, renderer, tessellator, 0.1875D, 0.1875D, 0.0D, 0.8125D, 0.8125D, 0.1875D);
		renderInventoryBox(block, renderer, tessellator, 0.1875D, 0.1875D, 0.8125D, 0.8125D, 0.8125D, 1.0D);
		tessellator.draw();
	}

	private void renderInventoryBox(Block block, RenderBlocks renderer, Tessellator tessellator, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
		IIcon icon = block.getIcon(0, 0);
		tessellator.setNormal(0F, -1F, 0F);
		renderer.renderFaceYNeg(block, 0, 0, 0, icon);
		tessellator.setNormal(0F, 1F, 0F);
		renderer.renderFaceYPos(block, 0, 0, 0, icon);
		tessellator.setNormal(0F, 0F, -1F);
		renderer.renderFaceZNeg(block, 0, 0, 0, icon);
		tessellator.setNormal(0F, 0F, 1F);
		renderer.renderFaceZPos(block, 0, 0, 0, icon);
		tessellator.setNormal(-1F, 0F, 0F);
		renderer.renderFaceXNeg(block, 0, 0, 0, icon);
		tessellator.setNormal(1F, 0F, 0F);
		renderer.renderFaceXPos(block, 0, 0, 0, icon);
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
		renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
		renderer.renderStandardBlock(block, x, y, z);
		renderer.setRenderBounds(0.25D, 0.25D, -0.125D, 0.75D, 0.75D, 1.125D);
		renderer.renderStandardBlock(block, x, y, z);
		renderer.setRenderBounds(0.1875D, 0.1875D, 0.0D, 0.8125D, 0.8125D, 0.1875D);
		renderer.renderStandardBlock(block, x, y, z);
		renderer.setRenderBounds(0.1875D, 0.1875D, 0.8125D, 0.8125D, 0.8125D, 1.0D);
		renderer.renderStandardBlock(block, x, y, z);
		renderer.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
		return true;
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return true;
	}

	@Override
	public int getRenderId() {
		return MachineMoltenSaltReactorPort.renderID;
	}
}
