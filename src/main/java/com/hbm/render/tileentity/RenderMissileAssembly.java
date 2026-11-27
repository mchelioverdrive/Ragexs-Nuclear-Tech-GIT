package com.hbm.render.tileentity;

import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.render.util.MissileRosterModel;
import com.hbm.tileentity.machine.TileEntityMachineMissileAssembly;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class RenderMissileAssembly extends TileEntitySpecialRenderer {
	
	public RenderMissileAssembly() { }
	
	@Override
	public void renderTileEntityAt(TileEntity tileentity, double x, double y, double z, float f) {
		
		if(!(tileentity instanceof TileEntityMachineMissileAssembly))
			return;
		
		TileEntityMachineMissileAssembly te = (TileEntityMachineMissileAssembly)tileentity;
		
		GL11.glPushMatrix();
		
		GL11.glTranslatef((float) x + 0.5F, (float) y, (float) z + 0.5F);
		GL11.glDisable(GL11.GL_CULL_FACE);
		
		switch(te.getBlockMetadata()) {
		case 2:
			GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 4:
			GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 3:
			GL11.glRotatef(0, 0F, 1F, 0F); break;
		case 5:
			GL11.glRotatef(90, 0F, 1F, 0F); break;
		}

		bindTexture(ResourceManager.missile_assembly_tex);
		ResourceManager.missile_assembly.renderAll();

		MissileRosterModel model = MissileRosterModel.get(te.getWorldPreview());
		if(model != null) {
			GL11.glTranslatef(-model.height * 0.5F, 1.65F, 0F);
			GL11.glRotatef(-90F, 0F, 0F, 1F);
			model.render();
		}

		GL11.glPopMatrix();
	}
}
