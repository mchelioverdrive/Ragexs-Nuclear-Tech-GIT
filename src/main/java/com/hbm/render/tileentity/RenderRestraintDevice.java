package com.hbm.render.tileentity;

import com.hbm.blocks.machine.BlockRestraintDevice;
import com.hbm.blocks.machine.BlockRestraintDevice.Kind;
import com.hbm.render.model.ModelRestraintDevice;
import com.hbm.tileentity.machine.TileEntityRestraintDevice;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public final class RenderRestraintDevice extends TileEntitySpecialRenderer implements IItemRenderer {
    private final ModelRestraintDevice[] models = new ModelRestraintDevice[Kind.values().length];
    private final ResourceLocation[] textures = new ResourceLocation[Kind.values().length];
    public RenderRestraintDevice() {
        for(Kind kind : Kind.values()) {
            models[kind.ordinal()] = new ModelRestraintDevice(kind);
            textures[kind.ordinal()] = kind == Kind.HANGING ? new ResourceLocation("minecraft:textures/blocks/planks_oak.png")
                    : new ResourceLocation("hbm:textures/models/restraint/" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".png");
        }
    }
    @Override public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntityRestraintDevice t = (TileEntityRestraintDevice) tile;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5); GL11.glRotatef(t.yaw(), 0, 1, 0);
        bindTexture(textures[t.kind().ordinal()]); GL11.glColor4f(1, 1, 1, 1);
        models[t.kind().ordinal()].render(t.animationCycle(partial), t.previousHatch + (t.hatch - t.previousHatch) * partial,
                t.previousStraps + (t.straps - t.previousStraps) * partial, t.active);
        if(t.kind() == Kind.CHAIR && t.active) arcs(t.animationCycle(partial));
        if(t.kind() == Kind.HANGING && t.hasNoose()) rope(t.animationCycle(partial), t.captiveHeight, t.captive != null);
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }
    private void arcs(float time) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(0.4F, 0.75F, 1F, 1F); GL11.glLineWidth(2F);
        Tessellator tess = Tessellator.instance; tess.startDrawing(GL11.GL_LINES);
        for(int side = -1; side <= 1; side += 2) for(int segment = 0; segment < 6; ++segment) {
            double y = 1 + segment * 0.17;
            double a = Math.sin(time * 2 + segment * 7) * 0.09;
            double b = Math.sin(time * 2 + (segment + 1) * 7) * 0.09;
            tess.addVertex(side * (0.28 + a), y, 0.05);
            tess.addVertex(side * (0.28 + b), y + 0.17, 0.05);
        }
        tess.draw();
    }
    private void rope(float time, float height, boolean occupied) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(0.62F, 0.45F, 0.25F, 1F); GL11.glLineWidth(3F);
        Tessellator tess = Tessellator.instance;
        double x = occupied ? TileEntityRestraintDevice.hangingSway(time) : 0;
        double neck = occupied ? 1 - TileEntityRestraintDevice.hangingDrop(time) + height * 0.83 : 2.5;
        tess.startDrawing(GL11.GL_LINE_STRIP);
        tess.addVertex(0, 4.56, 0); tess.addVertex(x, neck + (occupied ? 0 : 0.24), occupied ? 0.18 : 0);
        tess.draw();
        tess.startDrawing(GL11.GL_LINE_STRIP);
        for(int i = 0; i <= 24; ++i) {
            double angle = i * Math.PI / 12;
            tess.addVertex(x + Math.sin(angle) * 0.18, neck + (occupied ? 0 : Math.cos(angle) * 0.24),
                    occupied ? Math.cos(angle) * 0.18 : 0);
        }
        tess.draw(); GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    @Override public boolean handleRenderType(ItemStack item, ItemRenderType type) { return type != ItemRenderType.FIRST_PERSON_MAP; }
    @Override public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) { return true; }
    @Override public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if(!(net.minecraft.block.Block.getBlockFromItem(stack.getItem()) instanceof BlockRestraintDevice)) return;
        Kind kind = ((BlockRestraintDevice) net.minecraft.block.Block.getBlockFromItem(stack.getItem())).kind;
        GL11.glPushMatrix();
        float scale = kind == Kind.CHAIR ? 0.48F : 0.27F;
        GL11.glScalef(scale, scale, scale); GL11.glTranslated(0, -0.5, 0);
        bindTexture(textures[kind.ordinal()]); GL11.glColor4f(1, 1, 1, 1);
        models[kind.ordinal()].render(0, 0, 1, false);
        GL11.glPopMatrix();
    }
}
