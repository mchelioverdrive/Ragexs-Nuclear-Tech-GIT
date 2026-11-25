package com.hbm.render.tileentity;

import com.hbm.entity.mob.colossus.BlockOceanTempleCore;
import com.hbm.entity.mob.colossus.ColossusPose;
import com.hbm.render.entity.mob.RenderPrismarineColossus;
import com.hbm.render.model.ModelPrismarineColossus;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

public final class RenderOceanTempleCore extends TileEntitySpecialRenderer {
    private final ModelPrismarineColossus model = new ModelPrismarineColossus();
    private final ColossusPose pose = new ColossusPose();

    public RenderOceanTempleCore() {
        pose.evaluate(ColossusPose.IDLE, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0);
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTick) {
        BlockOceanTempleCore.Tile core = (BlockOceanTempleCore.Tile) tile;
        if(core.isActivated()) return;
        float scale = core.getDisplayScale();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 1, z + 0.5);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL11.GL_NORMALIZE);
        bindTexture(RenderPrismarineColossus.SKIN);
        model.render(pose, false, 0, 1, 0, false);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
