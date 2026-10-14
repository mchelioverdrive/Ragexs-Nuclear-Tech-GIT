package com.hbm.render.tileentity;

import org.lwjgl.opengl.GL11;
import com.hbm.blocks.BlockDummyable;
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

public class RenderBlastFurnace extends TileEntitySpecialRenderer implements IItemRendererProvider {
    // Geometry/UVs from upstream 29a29447b7d6c952fb05f97e616145081230074a.
    // Both tile and item share RTM's prepared parse, VBO upload and reload owner.
    private static final PreparedModelHandle MODEL = PreparedModelCache.getHfr(new ResourceLocation(RefStrings.MODID, "models/machines/blast_furnace.obj"), false, true);
    private static final ResourceLocation TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/models/machines/blast_furnace.png");
    @Override public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float interpolation) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + 0.5, y, z + 0.5);
            switch(tile.getBlockMetadata() - BlockDummyable.offset) {
            case 2: GL11.glRotatef(90, 0, 1, 0); break;
            case 4: GL11.glRotatef(180, 0, 1, 0); break;
            case 3: GL11.glRotatef(270, 0, 1, 0); break;
            }
            renderModel();
        } finally { GL11.glPopMatrix(); }
    }
    private void renderModel() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glColor4f(1, 1, 1, 1);
            bindTexture(TEXTURE); MODEL.renderAll();
        } finally { GL11.glPopAttrib(); }
    }
    @Override public Item getItemForRenderer() { return Item.getItemFromBlock(ModBlocks.machine_blast_furnace); }
    @Override public IItemRenderer getRenderer() {
        return new ItemRenderBase() {
            @Override public void renderInventory() { GL11.glTranslated(0, -4.5, 0); GL11.glScaled(4, 4, 4); }
            @Override public void renderCommon() { GL11.glScaled(0.5, 0.5, 0.5); renderModel(); }
        };
    }
}
