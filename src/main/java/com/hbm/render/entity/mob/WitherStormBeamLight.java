package com.hbm.render.entity.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.EntityWitherStormPart;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** A bounded, surface-conforming additive projection. Never changes blocks or the world lightmap. */
@SideOnly(Side.CLIENT)
final class WitherStormBeamLight {
    private static final Map<EntityWitherStormPart, WitherStormBeamLight> lights = new WeakHashMap<>();
    private static final double[][][] SIDES = {
        {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
        {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},
        {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
        {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
        {{1,0,0},{1,1,0},{1,1,1},{1,0,1}}
    };
    private static final int[][] NORMALS = {{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};

    private final List<double[]> faces = new ArrayList<>();
    private double activeUntil = -100D;
    private long sampled = Long.MIN_VALUE;

    static void render(EntityWitherStormPart part, EntityWitherStorm boss, float partial) {
        if(part.slot() >= 3) return;
        WitherStormBeamLight light = lights.get(part);
        if(light == null) {
            if(!boss.beamActive(part.slot())) return;
            light = new WitherStormBeamLight(); lights.put(part, light);
        }
        double now = boss.worldObj.getTotalWorldTime() + (double)partial;
        Entity target = boss.worldObj.getEntityByID(boss.headTarget(part.slot()));
        if(boss.beamActive(part.slot()) && WitherStormMath.targetable(target, boss.worldObj)) {
            float scale = boss.renderScale(partial), skull = boss.headModelScale(part.slot(), partial, scale);
            Vec3 base = boss.headPartOffset(part.slot(), boss.bodyYaw(partial), scale);
            Vec3 eye = WitherStormMath.headOffset(0, boss.getStage().eyeY() * skull, boss.getStage().eyeZ() * skull,
                part.headYaw(partial, boss), part.headPitch(partial, boss));
            Vec3 start = Vec3.createVectorHelper(boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * partial + base.xCoord + eye.xCoord,
                boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * partial + base.yCoord + 0.6D * scale + eye.yCoord,
                boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * partial + base.zCoord + eye.zCoord);
            Vec3 end = WitherStormMath.targetPoint(target, partial);
            Vec3 delta = Vec3.createVectorHelper(end.xCoord - start.xCoord, end.yCoord - start.yCoord, end.zCoord - start.zCoord);
            double length = delta.lengthVector();
            Vec3 forward = WitherStormMath.headOffset(0, 0, -1, part.headYaw(partial, boss), part.headPitch(partial, boss));
            if(length > 0.1D && length <= 48D && delta.dotProduct(forward) / length > 0.95D) {
                light.activeUntil = now;
                if(boss.worldObj.getTotalWorldTime() >= light.sampled + 4L || light.sampled == Long.MIN_VALUE) {
                    light.sampled = boss.worldObj.getTotalWorldTime();
                    // Continue the rendered eye-to-target ray to its actual terrain intersection.
                    Vec3 rayEnd = start.addVector(delta.xCoord / length * 96D, delta.yCoord / length * 96D, delta.zCoord / length * 96D);
                    // 1.7.10's DDA mutates its starting Vec3. Keep the real eye for surface-facing and occlusion tests.
                    MovingObjectPosition hit = boss.worldObj.rayTraceBlocks(
                        Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord), rayEnd);
                    light.faces.clear();
                    if(hit != null && hit.hitVec != null) {
                        double width = Math.min(length * 0.06D, skull * (0.5D + boss.development()));
                        light.project(boss.worldObj, start, hit.hitVec, Math.min(10D, Math.max(2D, width * 2.5D)));
                    }
                }
            }
        }
        float fade = (float)Math.max(0D, 1D - (now - light.activeUntil) / 12D);
        if(fade > 0F && boss.isEntityAlive()) light.draw(fade);
    }

    private void project(World world, Vec3 eye, Vec3 hit, double radius) {
        int r = MathHelper.ceiling_double_int(radius), hx = MathHelper.floor_double(hit.xCoord), hy = MathHelper.floor_double(hit.yCoord), hz = MathHelper.floor_double(hit.zCoord);
        // Only a shallow band near the impact, with a hard geometry limit. Top faces and exposed walls receive light.
        for(int x = hx - r; x <= hx + r; x++) for(int z = hz - r; z <= hz + r; z++) {
            if(!WitherStormMath.areaLoaded(world, x, z, 1)) continue;
            for(int y = Math.max(1, hy - 4); y <= Math.min(254, hy + 4); y++) {
                Block block = world.getBlock(x, y, z);
                if(!block.getMaterial().blocksMovement() || block.getMaterial().isLiquid()) continue;
                for(int f = 0; f < SIDES.length; f++) {
                    int[] n = NORMALS[f];
                    if(world.getBlock(x+n[0], y+n[1], z+n[2]).getMaterial().blocksMovement()) continue;
                    Vec3 center = Vec3.createVectorHelper(x+0.5D+n[0]*0.502D,y+0.5D+n[1]*0.502D,z+0.5D+n[2]*0.502D);
                    if((eye.xCoord-center.xCoord)*n[0]+(eye.yCoord-center.yCoord)*n[1]+(eye.zCoord-center.zCoord)*n[2] <= 0D
                        || center.squareDistanceTo(hit) > radius*radius) continue;
                    // Occluded surfaces must not glow through a ledge or wall.
                    if(world.rayTraceBlocks(center, eye) != null) continue;
                    double[] face = new double[16];
                    for(int v = 0; v < 4; v++) {
                        double vx=x+SIDES[f][v][0]+n[0]*0.003D, vy=y+SIDES[f][v][1]+n[1]*0.003D, vz=z+SIDES[f][v][2]+n[2]*0.003D;
                        double d=((vx-hit.xCoord)*(vx-hit.xCoord)+(vy-hit.yCoord)*(vy-hit.yCoord)+(vz-hit.zCoord)*(vz-hit.zCoord))/(radius*radius);
                        face[v*4]=vx; face[v*4+1]=vy; face[v*4+2]=vz; face[v*4+3]=Math.pow(Math.max(0D,1D-d),2D)*0.38D;
                    }
                    faces.add(face);
                    if(faces.size() >= 512) return;
                }
            }
        }
    }

    private void draw(float fade) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        float lx=OpenGlHelper.lastBrightnessX, ly=OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240F,240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        Tessellator t=Tessellator.instance; t.startDrawingQuads();
        for(double[] face:faces) for(int i=0;i<16;i+=4) {
            t.setColorRGBA_F(0.55F,0.06F,0.95F,(float)face[i+3]*fade);
            t.addVertex(face[i]-RenderManager.renderPosX,face[i+1]-RenderManager.renderPosY,face[i+2]-RenderManager.renderPosZ);
        }
        t.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lx,ly);
        GL11.glPopAttrib();
    }
}
