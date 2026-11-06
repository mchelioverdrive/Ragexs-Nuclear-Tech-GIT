package com.hbm.render.model;

import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormThunderstormPose;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import org.lwjgl.opengl.GL11;

/** Retained, premerged mantle and a single batch of continuously posed arms. */
public final class ModelWitherStormThunderstorm {
    private final double[] points = new double[25 * 3], point = new double[3];
    private final WitherStormArmMesh[] arms = {new WitherStormArmMesh(24), new WitherStormArmMesh(14),
        new WitherStormArmMesh(8), new WitherStormArmMesh(5)};
    private int[] surfaces;

    public void render(EntityWitherStorm boss, double time, float partial, double distance, Frustrum camera,
                       double wx, double wy, double wz) {
        float size = WitherStormThunderstormPose.size(boss.development());
        double scale = boss.renderScale(partial) * size, angle = Math.toRadians(boss.bodyYaw(partial));
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        boolean mantle = WitherStormMesh.visible(camera,wx,wy,wz,scale,cosine,sine,-28.1,3.9,-11.1,21.6,66.6,32.1);
        int lod = distance < 96D * 96D ? 0 : distance < 192D * 192D ? 1 : distance < 320D * 320D ? 2 : 3;
        int surface = distance < 192D * 192D ? 0 : 2;
        if(mantle && surfaces == null) surfaces = WitherStormMesh.loadThunderstorm();
        GL11.glPushMatrix();
        GL11.glScalef(size, size, size);
        if(mantle) GL11.glCallList(surfaces[surface]);
        Tessellator t = Tessellator.instance;
        WitherStormArmMesh mesh = arms[lod];
        boolean drawing = false;
        for(int arm = 0; arm < 7; arm++) {
            int count = mesh.sections.length, slot = arm + 3;
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for(int i = 0; i <= count; i++) {
                boss.posedArmPoint(slot, (double)i / count, time, partial, point);
                points[i*3] = point[0]; points[i*3+1] = point[1]; points[i*3+2] = point[2];
                minX = Math.min(minX,point[0]); maxX = Math.max(maxX,point[0]);
                minY = Math.min(minY,point[1]); maxY = Math.max(maxY,point[1]);
                minZ = Math.min(minZ,point[2]); maxZ = Math.max(maxZ,point[2]);
            }
            float width = WitherStormThunderstormPose.armWidth(slot);
            if(!WitherStormMesh.visible(camera,wx,wy,wz,scale,cosine,sine,minX-width,minY-width,minZ-width,
                maxX+width,maxY+width,maxZ+width)) continue;
            if(!drawing) { t.startDrawingQuads(); drawing = true; }
            for(int i = 0; i < count; i++) mesh.emit(t,i,width * mesh.taper[i],points,i*3);
        }
        if(drawing) t.draw();
        if(mantle) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
            float pulse = 224F + 16F * (float)Math.sin(time * 0.045D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, pulse, pulse);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glCallList(surfaces[surface + 1]);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GL11.glPopAttrib();
        }
        GL11.glPopMatrix();
    }
}
