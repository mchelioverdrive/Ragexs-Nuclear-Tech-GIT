package com.hbm.render.entity.mob;

import com.hbm.entity.mob.gemini.EntityGardenerCocoon;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Fixed fractured geology over a slowly breathing core. All motion is local presentation. */
public final class RenderGardenerCocoon extends Render {
    private static final ResourceLocation STONE = new ResourceLocation("textures/blocks/stone.png");
    private static final ResourceLocation DIRT = new ResourceLocation("textures/blocks/dirt.png");
    private static final ResourceLocation FLESH = new ResourceLocation("hbm:textures/blocks/fleshv2.png");
    private static final int RINGS = 9, SIDES = 11;
    private static final double[][][] HANGING = mesh(true), SURFACE = mesh(false);

    public RenderGardenerCocoon() { shadowSize = 0; }
    @Override protected ResourceLocation getEntityTexture(Entity entity) { return FLESH; }
    private static double[][][] mesh(boolean hanging) {
        double[][][] mesh = new double[RINGS][SIDES][3];
        for(int ring = 0; ring < RINGS; ring++) {
            double t = ring / (double) (RINGS - 1);
            double radius = hanging ? 0.035D + 0.65D * Math.pow(1 - t, 0.65D)
                    : 0.015D + 0.87D * Math.pow(Math.sin(t * Math.PI * 0.5D), 0.8D);
            for(int side = 0; side < SIDES; side++) {
                double angle = side * Math.PI * 2 / SIDES + ring * 0.045D;
                double rough = 0.87D + 0.12D * Math.sin(side * 2.39D + ring * 1.73D);
                mesh[ring][side][0] = Math.cos(angle) * radius * rough + Math.sin(t * 7) * 0.045D;
                mesh[ring][side][1] = (hanging ? 3 : 1) * (1 - t);
                mesh[ring][side][2] = Math.sin(angle) * radius * rough + Math.sin(t * 11) * 0.04D;
            }
        }
        return mesh;
    }
    @Override public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityGardenerCocoon cocoon = (EntityGardenerCocoon) entity;
        if(entity.isInvisible()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            // Position-derived variety is stable across saves and does not depend on network entity IDs.
            double phase = entity.posX * 1.37D + entity.posZ * 2.19D;
            GL11.glRotatef((float) (phase * 53 % 360), 0, 1, 0);
            GL11.glDisable(GL11.GL_CULL_FACE);
            double age = entity.ticksExisted + partial;
            double pulse = Math.sin(age * 0.027D + phase) * Math.sin(age * 0.011D + phase * 0.7D) * 0.012D;
            double contraction = Math.pow(Math.max(0, Math.sin(age * 0.009D + phase)), 18) * 0.018D;
            double[][][] shape = cocoon.isHanging() ? HANGING : SURFACE;
            if(cocoon.isEmpty()) {
                float shrink = Math.max(0.1F, 1F - cocoon.collapseAmount() * 0.9F);
                GL11.glScalef(1, shrink, 1);
            }
            bindTexture(FLESH);
            GL11.glColor3f(0.46F, 0.17F, 0.15F);
            draw(shape, 0, cocoon.maturity(), cocoon.isHanging(), 0.965D + pulse - contraction);
            bindTexture(cocoon.isHanging() ? STONE : DIRT);
            GL11.glColor3f(cocoon.isHanging() ? 0.83F : 0.7F, cocoon.isHanging() ? 0.80F : 0.62F, cocoon.isHanging() ? 0.76F : 0.53F);
            draw(shape, 1, cocoon.maturity(), cocoon.isHanging(), 1);
            bindTexture(STONE); GL11.glColor3f(0.72F, 0.70F, 0.66F);
            draw(shape, 2, cocoon.maturity(), cocoon.isHanging(), 1.003D);
            bindTexture(FLESH); GL11.glColor3f(0.27F, 0.13F, 0.12F);
            veins(shape, cocoon.isHanging());
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static boolean exposed(int ring, int side, float maturity, boolean hanging) {
        int hash = (ring * 17 + side * 13) % 31;
        return hanging ? ring > 0 && ring < 7 && hash < 1 + (int) (maturity * 4)
                : hash < 18 + (int) (maturity * 7);
    }
    /** Inner tissue is continuous; interrupted exterior panels expose irregular seams as it matures. */
    private static void draw(double[][][] shape, int layer, float maturity, boolean hanging, double scale) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for(int ring = 0; ring < RINGS - 1; ring++) for(int side = 0; side < SIDES; side++) {
            int next = (side + 1) % SIDES;
            if(layer > 0 && (exposed(ring, side, maturity, hanging) || ((ring * 7 + side) % 9 == 0) != (layer == 2))) continue;
            double angle = (side + 0.5D) * Math.PI * 2 / SIDES;
            tess.setNormal((float) Math.cos(angle), hanging ? -0.18F : 0.55F, (float) Math.sin(angle));
            vertex(tess, shape[ring][side], scale, 0, 0);
            vertex(tess, shape[ring + 1][side], scale, 0, 1);
            vertex(tess, shape[ring + 1][next], scale, 1, 1);
            vertex(tess, shape[ring][next], scale, 1, 0);
        }
        tess.draw();
    }
    private static void vertex(Tessellator tess, double[] point, double scale, double u, double v) {
        tess.addVertexWithUV(point[0] * scale, point[1], point[2] * scale, u, v);
    }
    private static void veins(double[][][] shape, boolean hanging) {
        Tessellator tess = Tessellator.instance; tess.startDrawingQuads();
        for(int side = 0; side < SIDES; side += 3) {
            for(int ring = 1; ring < RINGS - 1; ring++) {
                double[] a = shape[ring][side], b = shape[ring + 1][side];
                double angle = side * Math.PI * 2 / SIDES;
                double dx = Math.cos(angle + Math.PI / 2) * 0.009D, dz = Math.sin(angle + Math.PI / 2) * 0.009D;
                tess.setNormal((float) Math.cos(angle), 0.2F, (float) Math.sin(angle));
                tess.addVertexWithUV(a[0] * 1.012D - dx, a[1], a[2] * 1.012D - dz, 0, 0);
                tess.addVertexWithUV(b[0] * 1.012D - dx, b[1], b[2] * 1.012D - dz, 0, 1);
                tess.addVertexWithUV(b[0] * 1.012D + dx, b[1], b[2] * 1.012D + dz, 1, 1);
                tess.addVertexWithUV(a[0] * 1.012D + dx, a[1], a[2] * 1.012D + dz, 1, 0);
            }
            // Low, splayed roots on the mound; attachment tendrils stay tight to the cave ceiling.
            double angle = side * Math.PI * 2 / SIDES;
            double y = hanging ? 2.98D : 0.025D;
            double inner = hanging ? 0.45D : 0.5D, outer = hanging ? 0.73D : 0.98D;
            tess.setNormal(0, hanging ? -1 : 1, 0);
            tess.addVertexWithUV(Math.cos(angle) * inner - 0.025D, y, Math.sin(angle) * inner, 0, 0);
            tess.addVertexWithUV(Math.cos(angle + 0.16D) * outer, y, Math.sin(angle + 0.16D) * outer, 0, 1);
            tess.addVertexWithUV(Math.cos(angle + 0.2D) * outer + 0.015D, y, Math.sin(angle + 0.2D) * outer, 1, 1);
            tess.addVertexWithUV(Math.cos(angle) * inner + 0.025D, y, Math.sin(angle) * inner, 1, 0);
        }
        tess.draw();
    }
}
