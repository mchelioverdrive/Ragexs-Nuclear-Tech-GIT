package com.hbm.render.tileentity;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineWoodchipper;
import com.hbm.blocks.machine.MachineWoodchipper.Finish;
import com.hbm.blocks.machine.MachineWoodchipper.Part;
import com.hbm.tileentity.machine.TileEntityWoodchipper;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public final class RenderWoodchipper extends TileEntitySpecialRenderer implements IItemRenderer {
    private static final Part ROTOR = new Part(Finish.STEEL, 4, -1, -1, 12, 1, 1);
    private static final Part[] TEETH = {
        new Part(Finish.PANEL, 4, 1, -1, 5, 2, 0),
        new Part(Finish.PANEL, 6, -2, 0, 7, -1, 1),
        new Part(Finish.PANEL, 8, 1, -1, 9, 2, 0),
        new Part(Finish.PANEL, 10, -2, 0, 11, -1, 1)
    };
    private final RenderBlocks faces = new RenderBlocks();

    @Override public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntityWoodchipper chipper = (TileEntityWoodchipper) tile;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(chipper.yaw(), 0, 1, 0);
        render(chipper.rotorAngle(partial));
        GL11.glPopMatrix();
    }

    @Override public boolean handleRenderType(ItemStack item, ItemRenderType type) { return type != ItemRenderType.FIRST_PERSON_MAP; }
    @Override public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) { return true; }
    @Override public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        if(type == ItemRenderType.INVENTORY) GL11.glTranslatef(0F, -0.5F, 0F);
        else if(type != ItemRenderType.ENTITY) GL11.glTranslatef(0.5F, 0F, 0.5F);
        render(0F);
        GL11.glPopMatrix();
    }

    private void render(float angle) {
        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for(Part part : MachineWoodchipper.FRAME) draw(part, tess, -0.5, 0, -0.5);
        tess.draw();
        rotor(tess, 5.5 / 16D - 0.5D, angle);
        rotor(tess, 8.5 / 16D - 0.5D, 90F - angle);
        GL11.glPopAttrib();
    }

    private void rotor(Tessellator tess, double z, float angle) {
        GL11.glPushMatrix();
        GL11.glTranslated(0, 10.5 / 16D, z);
        GL11.glRotatef(angle, 1, 0, 0);
        tess.startDrawingQuads();
        draw(ROTOR, tess, -0.5, 0, 0);
        for(Part tooth : TEETH) draw(tooth, tess, -0.5, 0, 0);
        tess.draw();
        GL11.glPopMatrix();
    }

    private void draw(Part part, Tessellator tess, double x, double y, double z) {
        MachineWoodchipper block = (MachineWoodchipper) ModBlocks.machine_woodchipper;
        IIcon icon = block.getFinish(part.finish);
        // Keep the face bounds inside the atlas tile. Offsets place the separate rotor pivots.
        double yOffset = part.y < 0 ? 0.125 : 0, zOffset = part.z < 0 ? 0.125 : 0;
        faces.setRenderBounds(part.x / 16D, part.y / 16D + yOffset, part.z / 16D + zOffset,
                part.xMax / 16D, part.yMax / 16D + yOffset, part.zMax / 16D + zOffset);
        y -= yOffset;
        z -= zOffset;
        tess.setColorOpaque_F(0.50F, 0.50F, 0.50F);
        faces.renderFaceYNeg(block, x, y, z, icon);
        tess.setColorOpaque_F(1F, 1F, 1F);
        faces.renderFaceYPos(block, x, y, z, icon);
        tess.setColorOpaque_F(0.80F, 0.80F, 0.80F);
        faces.renderFaceZNeg(block, x, y, z, icon);
        faces.renderFaceZPos(block, x, y, z, icon);
        tess.setColorOpaque_F(0.65F, 0.65F, 0.65F);
        faces.renderFaceXNeg(block, x, y, z, icon);
        faces.renderFaceXPos(block, x, y, z, icon);
    }
}
