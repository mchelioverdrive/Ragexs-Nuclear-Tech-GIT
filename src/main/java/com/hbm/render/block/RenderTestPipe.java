package com.hbm.render.block;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.network.FluidDuctStandard;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.main.ResourceManager;
import com.hbm.render.util.ObjUtil;
import com.hbm.tileentity.network.TileEntityPipeBaseNT;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;

public class RenderTestPipe implements ISimpleBlockRenderingHandler {

	private static class Geometry {
		private static final WavefrontObject MODEL = (WavefrontObject) ResourceManager.pipe_neo;
		private static final GroupObject[][] SHAPES = ConductorRenderCache.buildPipeShapes(MODEL);
		private static final GroupObject[] INVENTORY = ConductorRenderCache.groups(MODEL, "pX", "nX", "pZ", "nZ");
	}

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {

		GL11.glPushMatrix();
		Tessellator tessellator = Tessellator.instance;
		IIcon iicon = block.getIcon(0, metadata);
		IIcon overlay = block.getIcon(1, metadata);
		tessellator.setColorOpaque_F(1, 1, 1);

		if(renderer.hasOverrideBlockTexture()) {
			iicon = renderer.overrideBlockTexture;
		}
		
		GL11.glRotated(180, 0, 1, 0);
		GL11.glScaled(1.25D, 1.25D, 1.25D);
		tessellator.startDrawingQuads();
		ObjUtil.renderGroupsWithIcon(Geometry.INVENTORY, iicon, tessellator, false);
		tessellator.draw();
		tessellator.startDrawingQuads();
		ObjUtil.setColor(Fluids.NONE.getColor());
		ObjUtil.renderGroupsWithIcon(Geometry.INVENTORY, overlay, tessellator, false);
		ObjUtil.clearColor();
		tessellator.draw();

		GL11.glPopMatrix();
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {

		Tessellator tessellator = Tessellator.instance;
		int meta = world.getBlockMetadata(x, y, z);
		IIcon iicon = block.getIcon(0, meta);
		IIcon overlay = block.getIcon(1, meta);
		tessellator.setColorOpaque_F(1, 1, 1);

		if(renderer.hasOverrideBlockTexture()) {
			iicon = renderer.overrideBlockTexture;
		}
		
		TileEntity te = world.getTileEntity(x, y, z);
		
		int color = 0xff00ff;
		FluidType type = Fluids.NONE;
		
		if(te instanceof TileEntityPipeBaseNT) {
			TileEntityPipeBaseNT pipe = (TileEntityPipeBaseNT) te;
			color = pipe.getType().getColor();
			type = pipe.getType();
		}

		tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
		tessellator.setColorOpaque_F(1, 1, 1);

		int mask = ConductorRenderCache.getFluidMask(world, x, y, z, type);
		
		tessellator.addTranslation(x + 0.5F, y + 0.5F, z + 0.5F);
		
		renderDuct(iicon, overlay, color, tessellator, Geometry.SHAPES[mask]);
		
		tessellator.addTranslation(-x - 0.5F, -y - 0.5F, -z - 0.5F);

		return true;
	}
	
	private void renderDuct(IIcon iicon, IIcon overlay, int color, Tessellator tessellator, GroupObject[] groups) {
		ObjUtil.renderGroupsWithIcon(groups, iicon, tessellator, true);
		ObjUtil.setColor(color);
		ObjUtil.renderGroupsWithIcon(groups, overlay, tessellator, true);
		ObjUtil.clearColor();
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return true;
	}

	@Override
	public int getRenderId() {
		return FluidDuctStandard.renderID;
	}
}
