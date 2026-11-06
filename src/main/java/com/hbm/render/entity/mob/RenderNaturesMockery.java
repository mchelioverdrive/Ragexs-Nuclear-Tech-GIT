package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityNaturesMockery;
import com.hbm.render.model.ModelNaturesMockery;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class RenderNaturesMockery extends RenderLiving {
    private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/blocks/fleshv2.png");
    public RenderNaturesMockery() { super(new ModelNaturesMockery(), 0.7F); }
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return TEXTURE; }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        super.doRender(entity, x, y, z, yaw, partial);
        EntityNaturesMockery mob = (EntityNaturesMockery) entity;
        EntityLivingBase target = mob.tendrilTarget();
        if(target == null || mob.isInvisible() || !mob.isEntityAlive()) return;
        double mx = mob.lastTickPosX + (mob.posX - mob.lastTickPosX) * partial;
        double my = mob.lastTickPosY + (mob.posY - mob.lastTickPosY) * partial;
        double mz = mob.lastTickPosZ + (mob.posZ - mob.lastTickPosZ) * partial;
        double dx = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial - mx;
        double dy = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial + target.height * 0.5D - my - 1.15D;
        double dz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial - mz;
        if(dx * dx + dy * dy + dz * dz > 49D) return;
        float extension = mob.prevExtension + (mob.extension - mob.prevExtension) * partial;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y + 1.15D, z);
            GL11.glDisable(GL11.GL_CULL_FACE);
            bindTexture(TEXTURE);
            for(int strand = 0; strand < 3; strand++) {
                Tessellator tess = Tessellator.instance;
                tess.startDrawingQuads();
                tess.setColorOpaque_F(0.55F + strand * 0.07F, 0.15F, 0.13F);
                for(int segment = 0; segment < 16; segment++) {
                    double t0 = segment / 16D, t1 = (segment + 1) / 16D;
                    double[] a = point(dx, dy, dz, t0, extension, strand, mob.ticksExisted + partial, mob.isHolding(), target.width);
                    double[] b = point(dx, dy, dz, t1, extension, strand, mob.ticksExisted + partial, mob.isHolding(), target.width);
                    double vx = b[0] - a[0], vy = b[1] - a[1], vz = b[2] - a[2];
                    double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    if(len < 0.00001D) continue;
                    vx /= len; vy /= len; vz /= len;
                    // Stable perpendicular frame, including near-vertical tendrils.
                    double ux = Math.abs(vy) < 0.9D ? -vz : 0;
                    double uy = Math.abs(vy) < 0.9D ? 0 : vz;
                    double uz = Math.abs(vy) < 0.9D ? vx : -vy;
                    double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                    ux /= ul; uy /= ul; uz /= ul;
                    double wx = vy * uz - vz * uy, wy = vz * ux - vx * uz, wz = vx * uy - vy * ux;
                    double r0 = 0.065D * (1 - t0 * 0.6D), r1 = 0.065D * (1 - t1 * 0.6D);
                    for(int side = 0; side < 6; side++) {
                        double angle0 = side * Math.PI / 3D, angle1 = (side + 1) * Math.PI / 3D;
                        vertex(tess, a, ux, uy, uz, wx, wy, wz, angle0, r0, side / 6D, t0 * 4);
                        vertex(tess, b, ux, uy, uz, wx, wy, wz, angle0, r1, side / 6D, t1 * 4);
                        vertex(tess, b, ux, uy, uz, wx, wy, wz, angle1, r1, (side + 1) / 6D, t1 * 4);
                        vertex(tess, a, ux, uy, uz, wx, wy, wz, angle1, r0, (side + 1) / 6D, t0 * 4);
                    }
                }
                tess.draw();
            }
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static double[] point(double x, double y, double z, double t, float extend, int strand, float age, boolean hold, float width) {
        double bend = Math.sin(Math.PI * t);
        double phase = age * 0.09D + strand * 2.094D + t * 9;
        double coil = hold ? Math.max(0, (t - 0.65D) / 0.35D) * width * 0.6D : 0;
        return new double[] {x * t * extend + Math.cos(phase) * (bend * 0.16D + coil),
                y * t * extend - bend * (hold ? 0.12D : 0.4D) + Math.sin(phase) * coil,
                z * t * extend + Math.sin(phase) * (bend * 0.16D + coil)};
    }
    private static void vertex(Tessellator t, double[] p, double ux, double uy, double uz, double wx, double wy, double wz,
            double angle, double radius, double u, double v) {
        double c = Math.cos(angle), s = Math.sin(angle);
        t.setNormal((float) (ux * c + wx * s), (float) (uy * c + wy * s), (float) (uz * c + wz * s));
        t.addVertexWithUV(p[0] + (ux * c + wx * s) * radius, p[1] + (uy * c + wy * s) * radius,
                p[2] + (uz * c + wz * s) * radius, u, v);
    }
}
