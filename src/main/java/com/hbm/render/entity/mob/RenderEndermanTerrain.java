package com.hbm.render.entity.mob;

import java.nio.DoubleBuffer;

import com.hbm.entity.mob.giantenderman.EntityEndermanTerrain;
import com.hbm.entity.mob.giantenderman.EntityGiantEnderman;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose;
import com.hbm.entity.mob.giantenderman.GiantEndermanTerrain.Cell;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class RenderEndermanTerrain extends Render {
    private final RenderBlocks blocks = new RenderBlocks();
    private final GiantEndermanPose pose = new GiantEndermanPose();
    private final DoubleBuffer transform = BufferUtils.createDoubleBuffer(16);

    @Override protected ResourceLocation getEntityTexture(Entity entity) { return TextureMap.locationBlocksTexture; }

    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityEndermanTerrain formation = (EntityEndermanTerrain)entity;
        EntityGiantEnderman carrier = formation.carrier();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            if (carrier == null) {
                GL11.glTranslated(x, y, z);
                GL11.glRotatef((formation.ticksExisted + partial) * 9, 0.6F, 1, 0.35F);
            } else {
                carrier.evaluate(pose, partial);
                GL11.glTranslated(interpolate(carrier.lastTickPosX, carrier.posX, partial) - RenderManager.renderPosX,
                    interpolate(carrier.lastTickPosY, carrier.posY, partial) - RenderManager.renderPosY,
                    interpolate(carrier.lastTickPosZ, carrier.posZ, partial) - RenderManager.renderPosZ);
                GL11.glRotatef(-RenderGiantEnderman.interpolatedYaw(carrier, partial), 0, 1, 0);
                transform.clear();
                transform.put(pose.bones[GiantEndermanPose.hand(carrier.activeHand())].matrix);
                transform.flip();
                GL11.glMultMatrix(transform);
                GL11.glTranslated(0, -1.4, 1.1);
                GL11.glRotatef(18, 0, 0, 1);
            }
            bindEntityTexture(entity);
            GL11.glColor4f(1, 1, 1, 1);
            for (Cell cell : formation.cells) {
                Block block = Block.getBlockById(cell.id);
                if (block == null || block == Blocks.air) continue;
                GL11.glPushMatrix();
                GL11.glTranslatef(cell.x, cell.y, cell.z);
                blocks.renderBlockAsItem(block, cell.meta, 1);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static double interpolate(double from, double to, float partial) { return from + (to - from) * partial; }
}
