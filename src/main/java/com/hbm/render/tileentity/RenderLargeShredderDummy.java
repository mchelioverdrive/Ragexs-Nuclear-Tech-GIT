package com.hbm.render.tileentity;

import org.lwjgl.opengl.GL11;

import com.hbm.blocks.ModBlocks;
import com.hbm.lib.RefStrings;
import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.loader.prepared.PreparedModelCache;
import com.hbm.render.loader.prepared.PreparedModelHandle;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

public class RenderLargeShredderDummy extends TileEntitySpecialRenderer implements IItemRendererProvider {

	// Final X3030: c7a659107f3a0fd8fb1101c2816a5482c140b1a2 (January 26, 2019).
	// Original triangle geometry enters the shared prepared-model upload/reload path directly.
	private static final PreparedModelHandle BODY = PreparedModelCache.get(
			new ResourceLocation(RefStrings.MODID, "models/decor/shredder_body.obj"), false, true);
	private static final PreparedModelHandle BLADE = PreparedModelCache.get(
			new ResourceLocation(RefStrings.MODID, "models/decor/shredder_blade.obj"), false, true);
	private static final ResourceLocation BODY_TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/models/decor/TheGadget3_.png");
	private static final ResourceLocation BLADE_TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/models/decor/turbofan_blades.png");

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interpolation) {
		GL11.glPushMatrix();
		try {
			GL11.glTranslated(x + 0.5D, y, z + 0.5D);
			GL11.glRotatef(180F, 0F, 1F, 0F);
			switch(tile.getBlockMetadata()) {
			case 4: GL11.glRotatef(90F, 0F, 1F, 0F); break;
			case 3: GL11.glRotatef(180F, 0F, 1F, 0F); break;
			case 5: GL11.glRotatef(270F, 0F, 1F, 0F); break;
			}
			renderModel();
		} finally {
			GL11.glPopMatrix();
		}
	}

	private void renderModel() {
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
		try {
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glColor4f(1F, 1F, 1F, 1F);
			GL11.glShadeModel(GL11.GL_FLAT);
			bindTexture(BODY_TEXTURE);
			BODY.renderAll();
			bindTexture(BLADE_TEXTURE);
			// Resting phase zero from the X3030 renderer; retain both local pivots.
			renderBlade(-0.25D, 0.21875D, false);
			renderBlade(0.25D, 0D, true);
		} finally {
			GL11.glPopAttrib();
		}
	}

	private void renderBlade(double x, double z, boolean mirrored) {
		GL11.glPushMatrix();
		try {
			GL11.glTranslated(x, 2.85D, z);
			if(mirrored) GL11.glScaled(-1D, 1D, 1D);
			BLADE.renderAll();
		} finally {
			GL11.glPopMatrix();
		}
	}

	@Override
	public Item getItemForRenderer() {
		return Item.getItemFromBlock(ModBlocks.machine_shredder_large_dummy);
	}

	@Override
	public IItemRenderer getRenderer() {
		return new ItemRenderBase() {
			@Override public void renderInventory() {
				GL11.glScaled(3D, 3D, 3D);
				GL11.glTranslated(0D, -2D, 0D);
			}
			@Override public void renderNonInv() {
				GL11.glScaled(0.7D, 0.7D, 0.7D);
			}
			@Override public void renderCommon() {
				renderModel();
			}
		};
	}
}
