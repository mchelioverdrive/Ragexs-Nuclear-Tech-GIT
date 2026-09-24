package com.hbm.render.tileentity;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.BlockDummyable;
import com.hbm.main.ResourceManager;
import com.hbm.tileentity.machine.oil.TileEntityMachinePumpjack;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;

public class RenderPumpjack extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float f) {
		
		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5, y, z + 0.5);
		GL11.glEnable(GL11.GL_LIGHTING);
		
		switch(tileEntity.getBlockMetadata() - BlockDummyable.offset) {
		case 2: GL11.glRotatef(90, 0F, 1F, 0F); break;
		case 4: GL11.glRotatef(180, 0F, 1F, 0F); break;
		case 3: GL11.glRotatef(270, 0F, 1F, 0F); break;
		case 5: GL11.glRotatef(0, 0F, 1F, 0F); break;
		}
		
		TileEntityMachinePumpjack pj = (TileEntityMachinePumpjack) tileEntity;
		
		float rotation = pj.prevRot + (pj.rot - pj.prevRot) * f;

		GL11.glShadeModel(GL11.GL_SMOOTH);
		
		bindTexture(ResourceManager.pumpjack_tex);
		ResourceManager.pumpjack.renderPart("Base");

		GL11.glPushMatrix();
		GL11.glTranslated(0, 1.5, -5.5);
		GL11.glRotatef(rotation - 90, 1, 0, 0);
		GL11.glTranslated(0, -1.5, 5.5);
		ResourceManager.pumpjack.renderPart("Rotor");
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslated(0, 3.5, -3.5);
		GL11.glRotated(Math.toDegrees(Math.sin(Math.toRadians(rotation))) * 0.25, 1, 0, 0);
		GL11.glTranslated(0, -3.5, 3.5);
		ResourceManager.pumpjack.renderPart("Head");
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslated(0, -Math.sin(Math.toRadians(rotation)), 0);
		ResourceManager.pumpjack.renderPart("Carriage");
		GL11.glPopMatrix();
		
		double pumpAngle = Math.toRadians(rotation);
		double pumpSin = Math.sin(pumpAngle);
		float headAngle = -(float) pumpSin * 0.25F;
		float headSin = MathHelper.sin(headAngle);
		float headCos = MathHelper.cos(headAngle);
		double backY = -2D * headSin;
		double backZ = -2D * headCos;

		float rotorAngle = -(float) Math.toRadians(rotation - 90);
		double rotorY = 0.5D * MathHelper.cos(rotorAngle);
		double rotorZ = -0.5D * MathHelper.sin(rotorAngle);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setColorRGBA_F(0.5F, 0.5F, 0.5F, 1F);
		
		for(int i = -1; i <= 1; i += 2) {

			tess.addVertex(0.53125 * i, 1.5 + rotorY, -5.5 + rotorZ - 0.0625D);
			tess.addVertex(0.53125 * i, 1.5 + rotorY, -5.5 + rotorZ + 0.0625D);

			tess.addVertex(0.53125 * i, 3.5 + backY, -3.5 + backZ + 0.0625D);
			tess.addVertex(0.53125 * i, 3.5 + backY, -3.5 + backZ - 0.0625D);
		}
		
		tess.setColorRGBA_F(0.2F, 0.2F, 0.2F, 1F);
		
		double pd = 0.03125D;
		double width = 0.25D;

		double height = -pumpSin;
		double frontY = headSin;
		double frontZ = headCos;
		double dist = 0.03125D;
		double cutlet = 360D / 32D;
		float initialRopeAngle = (float) Math.toRadians(cutlet * 3D);
		float ropeStepAngle = -(float) Math.toRadians(cutlet);
		float initialRopeSin = MathHelper.sin(initialRopeAngle);
		float initialRopeCos = MathHelper.cos(initialRopeAngle);
		float ropeStepSin = MathHelper.sin(ropeStepAngle);
		float ropeStepCos = MathHelper.cos(ropeStepAngle);
		
		for(int i = -1; i <= 1; i += 2) {
			double frontRadY = (2.5D + dist) * headSin;
			double frontRadZ = (2.5D + dist) * headCos;
			double rotatedY = frontRadY * initialRopeCos + frontRadZ * initialRopeSin;
			frontRadZ = frontRadZ * initialRopeCos - frontRadY * initialRopeSin;
			frontRadY = rotatedY;
			
			for(int j = 0; j < 4; j++) {

				double sumY = frontY + frontRadY;
				double sumZ = frontZ + frontRadZ;
				if(frontRadY < 0) sumZ = 3.5 + dist * 0.5;
	
				tess.addVertex((width - pd) * i, 3.5 + sumY, -3.5 + sumZ);
				tess.addVertex((width + pd) * i, 3.5 + sumY, -3.5 + sumZ);

				rotatedY = frontRadY * ropeStepCos + frontRadZ * ropeStepSin;
				frontRadZ = frontRadZ * ropeStepCos - frontRadY * ropeStepSin;
				frontRadY = rotatedY;

				sumY = frontY + frontRadY;
				sumZ = frontZ + frontRadZ;
				if(frontRadY < 0) sumZ = 3.5 + dist * 0.5;
	
				tess.addVertex((width + pd) * i, 3.5 + sumY, -3.5 + sumZ);
				tess.addVertex((width - pd) * i, 3.5 + sumY, -3.5 + sumZ);
			}

			double sumY = frontY + frontRadY;
			double sumZ = frontZ + frontRadZ;
			if(frontRadY < 0) sumZ = 3.5 + dist * 0.5;
			
			tess.addVertex((width + pd) * i, 3.5 + sumY, -3.5 + sumZ);
			tess.addVertex((width - pd) * i, 3.5 + sumY, -3.5 + sumZ);
			
			tess.addVertex((width - pd) * i, 2 + height, 0);
			tess.addVertex((width + pd) * i, 2 + height, 0);
		}
		
		double p = 0.03125D;
		tess.addVertex(p, height + 1.5, p);
		tess.addVertex(-p, height + 1.5, -p);
		tess.addVertex(-p, 0.75, -p);
		tess.addVertex(p, 0.75,  p);
		tess.addVertex(-p, height + 1.5, p);
		tess.addVertex(p, height + 1.5, -p);
		tess.addVertex(p, 0.75, -p);
		tess.addVertex(-p, 0.75, p);
		
		tess.draw();
		
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_CULL_FACE);
		
		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glPopMatrix();
	}
}
