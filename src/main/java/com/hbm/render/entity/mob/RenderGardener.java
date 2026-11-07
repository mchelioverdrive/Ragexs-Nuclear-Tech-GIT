package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityGardener;
import com.hbm.entity.mob.gemini.GardenerPose;
import com.hbm.render.model.ModelGardener;
import com.hbm.render.model.ModelOceanGardener;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/** Thick articulated tubes follow the authoritative collision skeleton, including windup and collapse. */
public final class RenderGardener extends Render {
    private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/blocks/worm_flesh.png");
    private final ModelGardener model = new ModelGardener();
    private final ModelGardener oceanModel = new ModelOceanGardener();
    public RenderGardener() { shadowSize = 2F; }
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGardener boss = (EntityGardener) entity;
        if(boss.isInvisible()) return;
        if(boss.isEntityAlive() && boss.isAwake()) BossStatus.setBossStatus(boss, true);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            bindTexture(TEXTURE); GL11.glDisable(GL11.GL_CULL_FACE);
            (boss.isOcean() ? oceanModel : model).renderBody(boss, partial);
            double bx = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial;
            double by = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial;
            double bz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial;
            GL11.glTranslated(-bx, -by, -bz);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            for(int arm = 0; arm < GardenerPose.ARMS; arm++) {
                tess.setColorOpaque_F(boss.armDisabled(arm) ? 0.15F : 0.27F, 0.20F, 0.17F);
                for(int segment = 0; segment < GardenerPose.SEGMENTS; segment++)
                    tube(tess, GardenerPose.point(boss, arm, segment, partial), GardenerPose.point(boss, arm, segment + 1, partial), GardenerPose.RADIUS);
                // Fine forks add the skeletal silhouette without independent decorative entities.
                Vec3 root = GardenerPose.point(boss, arm, 2, partial), tip = GardenerPose.point(boss, arm, 4, partial);
                double dx = tip.xCoord - root.xCoord, dz = tip.zCoord - root.zCoord;
                double length = Math.max(1D, Math.sqrt(dx * dx + dz * dz));
                for(int fork = -1; fork <= 1; fork += 2) {
                    Vec3 bend = root.addVector(dx * 0.45D - dz / length * fork * 1.4D,
                            boss.isOcean() ? -4D : -1.3D, dz * 0.45D + dx / length * fork * 1.4D);
                    Vec3 end = bend.addVector(dx * 0.25D, boss.isOcean() ? -4.5D : -1.1D, dz * 0.25D);
                    tube(tess, root, bend, 0.10D); tube(tess, bend, end, 0.055D);
                }
            }
            tess.draw();
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static void tube(Tessellator tess, Vec3 a, Vec3 b, double radius) {
        Vec3 direction = b.subtract(a).normalize();
        Vec3 u = direction.crossProduct(Math.abs(direction.yCoord) < 0.9D ? Vec3.createVectorHelper(0, 1, 0) : Vec3.createVectorHelper(1, 0, 0)).normalize();
        Vec3 v = direction.crossProduct(u).normalize();
        for(int side = 0; side < 8; side++) {
            double p = side * Math.PI / 4D, q = (side + 1) * Math.PI / 4D;
            vertex(tess, a, u, v, radius, p, side / 8D, 0);
            vertex(tess, b, u, v, radius, p, side / 8D, 1);
            vertex(tess, b, u, v, radius, q, (side + 1) / 8D, 1);
            vertex(tess, a, u, v, radius, q, (side + 1) / 8D, 0);
            tess.setNormal((float) direction.xCoord, (float) direction.yCoord, (float) direction.zCoord);
            tess.addVertexWithUV(b.xCoord, b.yCoord, b.zCoord, 0.5D, 0.5D);
            vertex(tess, b, u, v, radius, p, 0, 0);
            vertex(tess, b, u, v, radius, q, 1, 0);
            tess.addVertexWithUV(b.xCoord, b.yCoord, b.zCoord, 0.5D, 0.5D);
        }
    }
    private static void vertex(Tessellator tess, Vec3 p, Vec3 u, Vec3 v, double radius, double angle, double texU, double texV) {
        double x = u.xCoord * Math.cos(angle) + v.xCoord * Math.sin(angle);
        double y = u.yCoord * Math.cos(angle) + v.yCoord * Math.sin(angle);
        double z = u.zCoord * Math.cos(angle) + v.zCoord * Math.sin(angle);
        tess.setNormal((float) x, (float) y, (float) z);
        tess.addVertexWithUV(p.xCoord + x * radius, p.yCoord + y * radius, p.zCoord + z * radius, texU, texV);
    }
}
