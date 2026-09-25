package com.hbm.render.block;

import com.hbm.blocks.machine.SolarMirror;
import com.hbm.main.ResourceManager;
import com.hbm.render.util.ObjUtil;
import com.hbm.render.loader.prepared.PreparedModelHandle;
import com.hbm.tileentity.machine.TileEntitySolarMirror;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class RenderMirror implements ISimpleBlockRenderingHandler {

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) { }

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {

		Tessellator tessellator = Tessellator.instance;
		IIcon iicon = block.getIcon(0, world.getBlockMetadata(x, y, z));

		tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
		tessellator.setColorOpaque_F(1, 1, 1);

		if(renderer.hasOverrideBlockTexture()) {
			iicon = renderer.overrideBlockTexture;
		}

		TileEntity te = world.getTileEntity(x, y, z);
		
		if(!(te instanceof TileEntitySolarMirror))
			return false;
		
		TileEntitySolarMirror mirror = (TileEntitySolarMirror) te;

		int dx = mirror.tX - mirror.xCoord;
		int dy = mirror.tY - mirror.yCoord;
		int dz = mirror.tZ - mirror.zCoord;

		tessellator.addTranslation(x + 0.5F, y, z + 0.5F);
		ObjUtil.renderPartWithIcon(ResourceManager.solar_mirror, "Base", iicon, tessellator, 0, true);
		
		if(mirror.tY <= mirror.yCoord)
			ObjUtil.renderPartWithIcon(ResourceManager.solar_mirror, "Mirror", iicon, tessellator, 0, true);
		else
			printMirror(iicon, dx, dy, dz);
		
		tessellator.addTranslation(-x - 0.5F, -y, -z - 0.5F);

		return true;
	}
	
	private void printMirror(IIcon icon, int dx, int dy, int dz) {

		Tessellator tes = Tessellator.instance;

		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double pitch = -Math.asin((dy + 0.5) / dist) + Math.PI / 2D;
		double yaw = -Math.atan2(dz, dx) - Math.PI / 2D;

		((PreparedModelHandle) ResourceManager.solar_mirror).tessellatePivotedPartWithIcon("Mirror", icon, tes, (float) pitch, (float) yaw, 1F);
	}

	@Override
	public boolean shouldRender3DInInventory(int modelId) {
		return false;
	}

	@Override
	public int getRenderId() {
		return SolarMirror.renderID;
	}
}
