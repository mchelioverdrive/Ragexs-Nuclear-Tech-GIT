package com.hbm.render.world;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;

/** Fixed world-space celestial bearing, drawn in the sky pass before terrain, clouds and weather. */
@SideOnly(Side.CLIENT)
public final class IrisSkyRenderer extends IRenderHandler {
    private final IRenderHandler parent;
    private boolean renderingParent;
    private static ResourceLocation tissue;
    private static int bodyList, cavityList;
    private static final int SEGMENTS = 96, RINGS = 28;
    private static final float[] RADII = {2.2F, 8F, 23F, 55F};

    public IrisSkyRenderer(IRenderHandler parent) { this.parent = parent; }

    @Override public void render(float partial, WorldClient world, Minecraft mc) {
        if(renderingParent) return;
        renderingParent = true;
        try {
            if(parent != null) parent.render(partial, world, mc);
            else {
                world.provider.setSkyRenderer(null);
                try { mc.renderGlobal.renderSky(partial); }
                finally { world.provider.setSkyRenderer(this); }
            }
        } finally { renderingParent = false; }
        renderIris(partial, world, mc);
    }

    private static void renderIris(float partial, WorldClient world, Minecraft mc) {
        if(world.provider.dimensionId != 0) return;
        float alpha = IrisClient.visibility(world, partial), haze = IrisClient.atmosphere(world, partial);
        if(alpha <= 0 && haze <= 0) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(haze > 0) skyHaze(haze);
            if(alpha <= 0) return;
            initialize(mc);
            int stage = Math.max(1, Math.min(4, IrisClient.state().getInteger("stage")));
            double time = IrisClient.clock(world, partial);
            float size = IrisClient.state().getFloat("size");
            float radius = Math.min(80F, RADII[stage - 1] * size);
            // Local -Z at elevation 45 degrees, azimuth +X/-Z. Never apply camera yaw/pitch or position.
            GL11.glRotatef(-45, 0, 1, 0); GL11.glRotatef(45, 1, 0, 0);
            GL11.glTranslatef(0, 0, -100);
            GL11.glScalef(radius, radius, radius);
            GL11.glRotatef((float)Math.sin(time / 48000D) * 0.6F, 0, 0, 1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.getTextureManager().bindTexture(tissue);
            // Geometry has baked hemispherical illumination; alpha uses constant blend color below.
            GL11.glEnable(GL11.GL_BLEND);
            org.lwjgl.opengl.GL14.glBlendColor(1, 1, 1, alpha);
            GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
            GL11.glCallList(bodyList);
            float observing = IrisClient.envelope(world, "observation", "observationLength", partial);
            GL11.glTranslatef(IrisClient.state().getFloat("gazeX") * observing,
                    IrisClient.state().getFloat("gazeY") * observing, 0.31F);
            float dilation = 0.96F + (float)Math.sin(time * IrisClient.state().getFloat("speed") / 1400D) * (stage == 1 ? 0.025F : 0.095F);
            GL11.glScalef(dilation, dilation, 1);
            GL11.glRotatef((float)Math.sin(time / 9100D) * 1.2F, 0, 0, 1);
            GL11.glCallList(cavityList);
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }

    private static void skyHaze(float strength) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.07F, 0.014F, 0.009F, strength * 0.9F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for(int face = 0; face < 6; face++) {
            for(int corner = 0; corner < 4; corner++) {
                double a = (corner == 0 || corner == 3 ? -100 : 100), b = corner < 2 ? -100 : 100;
                if(face < 2) t.addVertex(a, b, face == 0 ? -100 : 100);
                else if(face < 4) t.addVertex(a, face == 2 ? -100 : 100, b);
                else t.addVertex(face == 4 ? -100 : 100, a, b);
            }
        }
        t.draw();
    }

    /** One small original tissue texture and two retained meshes for the lifetime of the GL context. */
    private static void initialize(Minecraft mc) {
        if(tissue != null) return;
        int width = 512;
        DynamicTexture texture = new DynamicTexture(width, width);
        int[] pixels = texture.getTextureData();
        for(int y = 0; y < width; y++) for(int x = 0; x < width; x++) {
            double u = (x - width / 2D) / (width / 2D), v = (y - width / 2D) / (width / 2D);
            double angle = Math.atan2(v, u), r = Math.sqrt(u * u + v * v);
            double n = noise(u * 5, v * 5) * 0.55 + noise(u * 19, v * 19) * 0.28 + noise(u * 73, v * 73) * 0.17;
            double folded = Math.abs(Math.sin(angle * 23 + n * 9 + r * 32));
            double veins = Math.pow(Math.max(0, 1 - folded * 6), 2) * 0.45;
            double ridges = Math.sin(r * 58 + n * 15 + Math.sin(angle * 7) * 2) * 0.10;
            double color = Math.max(0.08, Math.min(1, 0.25 + n * 0.75 + ridges - veins));
            int red = (int)(52 + color * 143), green = (int)(21 + color * 71), blue = (int)(15 + color * 39);
            pixels[y * width + x] = 0xFF000000 | red << 16 | green << 8 | blue;
        }
        texture.updateDynamicTexture();
        tissue = mc.getTextureManager().getDynamicTextureLocation("gemini_iris_tissue", texture);
        bodyList = GLAllocation.generateDisplayLists(2);
        cavityList = bodyList + 1;
        GL11.glNewList(bodyList, GL11.GL_COMPILE);
        mesh(false);
        GL11.glEndList();
        GL11.glNewList(cavityList, GL11.GL_COMPILE);
        mesh(true);
        GL11.glEndList();
    }

    private static void mesh(boolean cavity) {
        Tessellator t = Tessellator.instance;
        int rings = cavity ? 14 : RINGS;
        t.startDrawingQuads();
        for(int ring = 0; ring < rings; ring++) for(int segment = 0; segment < SEGMENTS; segment++) {
            vertex(t, cavity, (ring + 0D) / rings, segment);
            vertex(t, cavity, (ring + 1D) / rings, segment);
            vertex(t, cavity, (ring + 1D) / rings, segment + 1);
            vertex(t, cavity, (ring + 0D) / rings, segment + 1);
        }
        t.draw();
    }

    private static void vertex(Tessellator t, boolean cavity, double fraction, int segment) {
        double a = segment * Math.PI * 2 / SEGMENTS;
        double irregular = 1 + Math.sin(a * 7) * 0.035 + Math.cos(a * 13) * 0.018;
        double r = cavity ? fraction * 0.37 * irregular : fraction;
        double x = Math.cos(a) * r, y = Math.sin(a) * r;
        double z = cavity ? fraction * fraction * 0.045 : Math.sqrt(Math.max(0, 1 - r * r)) * 0.25;
        if(cavity && fraction < 0.72) {
            // Recessed dark red interior. No sclera, human iris or catchlight.
            t.setColorOpaque_F((float)(0.018 + fraction * 0.045), (float)(0.003 + fraction * 0.006), (float)(0.004 + fraction * 0.005));
        } else {
            float light = (float)(0.16 + Math.sqrt(Math.max(0, 1 - r * r)) * 0.64 + (-x + y) * 0.12);
            if(cavity) light *= 0.55 + 0.45 * Math.cos(a * 19 + fraction * 13);
            t.setColorOpaque_F(light, light * 0.94F, light * 0.88F);
        }
        // The interior samples a darkened part of the same original tissue asset.
        t.addVertexWithUV(x, y, z, x * 0.5 + 0.5, y * 0.5 + 0.5);
    }

    private static double noise(double x, double y) {
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double top = hash(ix, iy) * (1 - fx) + hash(ix + 1, iy) * fx;
        double bottom = hash(ix, iy + 1) * (1 - fx) + hash(ix + 1, iy + 1) * fx;
        return top * (1 - fy) + bottom * fy;
    }
    private static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263 + 1979;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 65535) / 65535D;
    }
}
