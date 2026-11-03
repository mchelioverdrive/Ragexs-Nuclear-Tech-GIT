package com.hbm.render.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/** Original RNT geometry. No ModularBosses model coordinates or textures are reused. */
public final class ModelSandworm extends ModelBase {
    private final Mesh body = new Mesh(new float[] {6.5F, 8, 8.3F, 8, 6.5F}, new float[] {-10, -7, 0, 7, 10});
    private final Mesh head = new Mesh(new float[] {6.5F, 8, 9, 9}, new float[] {-10, -4, 5, 11});
    private final Mesh tail = new Mesh(new float[] {7, 6.5F, 4, 1, 0.1F}, new float[] {10, 4, -6, -15, -24});
    private final ModelRenderer jaw;
    private final ModelRenderer tooth;
    public ModelSandworm() {
        textureWidth = 64; textureHeight = 64;
        jaw = new ModelRenderer(this, 0, 0); jaw.addBox(-20, -12, 0, 40, 24, 110);
        tooth = new ModelRenderer(this, 32, 0); tooth.addBox(-6, -24, -6, 12, 48, 12);
    }
    public void body() { body.draw(); }
    public void tail() { tail.draw(); }
    public void head(float time) {
        head.draw();
        GL11.glColor3f(0.16F, 0.035F, 0.045F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glNormal3f(0, 0, 1); GL11.glTexCoord2f(0.5F, 0.5F); GL11.glVertex3f(0, 0, 9.7F);
        for(int i = 0; i <= 24; i++) { double a = i * Math.PI / 12; GL11.glVertex3d(Math.sin(a) * 8.9, Math.cos(a) * 8.9, 9.7); }
        GL11.glEnd(); GL11.glColor3f(1, 1, 1);
        for(int i = 0; i < 8; i++) {
            GL11.glPushMatrix(); GL11.glRotatef(i * 45, 0, 0, 1); GL11.glTranslatef(0, 7.4F, 6);
            GL11.glRotatef(10 + (float)Math.sin(time * 0.09) * 5, 1, 0, 0); jaw.render(0.0625F);
            GL11.glColor3f(0.95F, 0.86F, 0.62F); GL11.glTranslatef(0, -1, 5); GL11.glRotatef(30, 1, 0, 0); tooth.render(0.0625F);
            GL11.glColor3f(1, 1, 1); GL11.glPopMatrix();
        }
    }
    /** Retains tessellation on the CPU; no per-frame trigonometry or transient segment entities. */
    public static final class Mesh {
        private final double[] vertices;
        public Mesh(float[] radius, float[] depth) {
            int sides = 24;
            vertices = new double[(radius.length - 1) * sides * 4 * 8]; int v = 0;
            for(int ring = 0; ring < radius.length - 1; ring++) for(int side = 0; side < sides; side++) {
                // Winding faces outward on the +Z oriented shell.
                int[] rr = {ring, ring + 1, ring + 1, ring};
                int[] ss = {side, side, side + 1, side + 1};
                for(int corner = 0; corner < 4; corner++) {
                    int r = rr[corner], s = ss[corner]; double a = s * Math.PI * 2 / sides;
                    double nx = Math.sin(a), ny = Math.cos(a);
                    vertices[v++] = nx * radius[r]; vertices[v++] = ny * radius[r]; vertices[v++] = depth[r];
                    vertices[v++] = nx; vertices[v++] = ny; vertices[v++] = 0;
                    vertices[v++] = s / (double)sides; vertices[v++] = r / (double)(radius.length - 1);
                }
            }
        }
        public void draw() {
            Tessellator t = Tessellator.instance; t.startDrawingQuads();
            for(int i = 0; i < vertices.length; i += 8) {
                t.setNormal((float)vertices[i + 3], (float)vertices[i + 4], (float)vertices[i + 5]);
                t.addVertexWithUV(vertices[i], vertices[i + 1], vertices[i + 2], vertices[i + 6], vertices[i + 7]);
            }
            t.draw();
        }
    }
}
