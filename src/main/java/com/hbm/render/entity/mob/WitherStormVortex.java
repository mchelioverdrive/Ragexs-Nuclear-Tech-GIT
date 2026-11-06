package com.hbm.render.entity.mob;

import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormThunderstormPose;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;

/** Retained sector batches rotate independently; only the small inward stream needs vertex transforms. */
@SideOnly(Side.CLIENT)
public final class WitherStormVortex {
    private static final double TAU = Math.PI * 2D;
    private static final double[] RADII = {43D, 64D, 88D, 112D, 138D};
    private static final double[] HEIGHTS = {22D, 31D, 40D, 30D, 43D};
    private static final int SECTORS = 8, BATCHES = 5 * SECTORS + 2, LEVELS = 4;
    private static final int[][] FACES = {{5,1,3,7}, {0,4,6,2}, {6,7,3,2}, {0,1,5,4}, {4,5,7,6}, {1,0,2,3}};
    private static final float[] SHADE = {0.83F, 0.67F, 1F, 0.48F, 0.75F, 0.91F};
    private final double[] point = new double[3];
    private final int[] counts = new int[LEVELS];
    private Fragment[] fragments;
    private Batch[] batches;
    private int maximum, lists;

    private static double random(int i, int salt) {
        int h = i * 73428767 ^ salt * 912931;
        return (((h ^ h >>> 13) * 1274126177) & 0x7fffffff) / 2147483648D;
    }

    /** Also used by captured terrain particles. Its world-axis orbit is unchanged. */
    public static void orbit(int ring, double angle, double radialOffset, double time, double[] out) {
        double r = RADII[ring] + radialOffset + Math.sin(angle * 3D + ring + time * 0.008D) * 2.5D;
        out[0] = -3D + Math.cos(angle) * r;
        out[1] = HEIGHTS[ring] + Math.sin(angle + ring * 1.7D) * r * 0.10D
            + Math.sin(angle * 2D - time * 0.017D + ring) * 2D;
        out[2] = 10D + Math.sin(angle) * r * (0.88D + ring * 0.02D);
    }
    public static double speed(int ring) { return 0.012D / (1D + ring * 0.42D); }

    public void render(EntityWitherStorm boss, double x, double y, double z, float partial, double distance, Frustrum camera) {
        int limit = Math.max(0, Math.min(8192, WitherStormConfig.maxVortexFragments));
        if(limit == 0) { clear(); return; }
        if(!boss.isEntityAlive()) return;
        double scale = boss.renderScale(partial) * WitherStormThunderstormPose.size(boss.development());
        double wx = x + RenderManager.renderPosX - 3D * scale, wy = y + RenderManager.renderPosY;
        double wz = z + RenderManager.renderPosZ + 10D * scale;
        if(!camera.isBoxInFrustum(wx-160D*scale,wy, wz-160D*scale,wx+160D*scale,wy+72D*scale,wz+160D*scale)) return;
        if(limit != maximum) prepare(limit);
        Minecraft mc = Minecraft.getMinecraft();
        double quality = mc.gameSettings.particleSetting == 0 ? 1D : mc.gameSettings.particleSetting == 1 ? 0.6D : 0.28D;
        if(!mc.gameSettings.fancyGraphics) quality *= 0.75D;
        quality *= Math.max(0.16D, Math.min(1D, 180D / Math.max(180D, Math.sqrt(distance))));
        int lod = 0;
        while(lod < LEVELS - 1 && counts[lod] > maximum * quality) lod++;
        double time = boss.worldObj.getTotalWorldTime() + (double)partial;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        GL11.glTranslated(x - 3D * scale,y,z + 10D * scale); GL11.glScaled(scale,scale,scale);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240F,240F);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(true);
        for(int g = 0; g < BATCHES; g++) {
            Batch b = batches[lod * BATCHES + g];
            if(b.count == 0) continue;
            int ring = g / SECTORS;
            double spin = time * (ring < 5 ? speed(ring) * (0.98D + (g % SECTORS) * 0.005D) : 0.008D);
            double cosine = Math.cos(spin), sine = Math.sin(spin);
            double bob = Math.sin(time * 0.023D + g * 1.7D) * 1.1D;
            double radius = (b.radius + 8D) * scale;
            double cx = wx + (b.x * cosine - b.z * sine) * scale, cy = wy + (b.y + bob) * scale;
            double cz = wz + (b.x * sine + b.z * cosine) * scale;
            if(!camera.isBoxInFrustum(cx-radius,cy-radius,cz-radius,cx+radius,cy+radius,cz+radius)) continue;
            GL11.glPushMatrix();
            GL11.glRotatef((float)-Math.toDegrees(spin),0,1,0);
            GL11.glTranslated(0,bob,0);
            GL11.glRotatef((float)(Math.sin(time*0.009D+g)*1.2D),0,0,1);
            float breath = 1F + (float)Math.sin(time*0.013D+g)*0.012F;
            GL11.glScalef(breath,1F,breath);
            GL11.glCallList(lists + lod * BATCHES + g);
            GL11.glPopMatrix();
        }
        // About one fragment in nineteen peels off its ring. Cached corners, size, color and phases are reused.
        if(camera.isBoxInFrustum(wx-155D*scale,wy,wz-155D*scale,wx+155D*scale,wy+65D*scale,wz+155D*scale)) {
            Tessellator t = Tessellator.instance; t.startDrawingQuads();
            for(int i = 0; i < counts[lod]; i++) {
                Fragment f = fragments[i];
                if(!f.stream) continue;
                double angle = f.angle + time * f.orbitSpeed;
                orbit(f.ring,angle,f.radial,time,point);
                double cycle = time * 0.0014D + f.cycle, progress = cycle - Math.floor(cycle), inward = progress * progress;
                double px = (point[0] + 3D) * (1D-inward), py = point[1] + (33D-point[1])*inward;
                double pz = (point[2] - 10D) * (1D-inward);
                double shrink = Math.min(1D,Math.min(progress*12D,(1D-progress)*12D));
                if(!camera.isBoxInFrustum(wx+(px-2D)*scale,wy+(py-2D)*scale,wz+(pz-2D)*scale,
                    wx+(px+2D)*scale,wy+(py+2D)*scale,wz+(pz+2D)*scale)) continue;
                f.emit(t,px,py,pz,shrink,time*f.spin);
            }
            t.draw();
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lx,ly);
        GL11.glPopMatrix(); GL11.glPopAttrib();
    }

    private void clear() {
        if(lists != 0) GLAllocation.deleteDisplayLists(lists);
        lists = maximum = 0; fragments = null; batches = null;
    }

    private void prepare(int limit) {
        clear(); maximum = limit;
        fragments = new Fragment[limit];
        for(int i = 0; i < limit; i++) fragments[i] = new Fragment(i,point);
        counts[0] = limit;
        counts[1] = Math.min(limit,Math.max(Math.min(limit,5),(int)(limit*0.625D)));
        counts[2] = Math.min(limit,Math.max(Math.min(limit,5),(int)(limit*0.35D)));
        counts[3] = Math.min(limit,Math.max(Math.min(limit,5),(int)(limit*0.175D)));
        lists = GLAllocation.generateDisplayLists(LEVELS*BATCHES);
        batches = new Batch[LEVELS*BATCHES];
        for(int lod = 0; lod < LEVELS; lod++) for(int g = 0; g < BATCHES; g++) {
            int index = lod*BATCHES+g;
            Batch batch = batches[index] = new Batch();
            GL11.glNewList(lists+index,GL11.GL_COMPILE);
            Tessellator t = Tessellator.instance; t.startDrawingQuads();
            for(int i = 0; i < counts[lod]; i++) {
                Fragment f = fragments[i];
                if(f.stream || f.group != g) continue;
                f.emit(t,f.x,f.y,f.z,1D,0D); batch.include(f);
            }
            t.draw(); GL11.glEndList(); batch.finish();
        }
    }

    private static final class Batch {
        double x,y,z,radius,minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        int count;
        void include(Fragment f) {
            count++;
            minX=Math.min(minX,f.x-f.size); minY=Math.min(minY,f.y-f.size); minZ=Math.min(minZ,f.z-f.size);
            maxX=Math.max(maxX,f.x+f.size); maxY=Math.max(maxY,f.y+f.size); maxZ=Math.max(maxZ,f.z+f.size);
        }
        void finish() {
            if(count == 0) return;
            x=(minX+maxX)*0.5D; y=(minY+maxY)*0.5D; z=(minZ+maxZ)*0.5D;
            double dx=maxX-x,dy=maxY-y,dz=maxZ-z; radius=Math.sqrt(dx*dx+dy*dy+dz*dz);
        }
    }

    private static final class Fragment {
        final int ring,group;
        final boolean mote,stream;
        final double angle,radial,size,cycle,spin,orbitSpeed,x,y,z;
        final float color;
        final double[] corners = new double[24];
        Fragment(int i,double[] out) {
            ring=i%5; double phase=random(i,1)*TAU;
            angle=phase+Math.sin(phase*2D)*0.22D;
            radial=(random(i,3)-0.5D)*(7D+ring*2D);
            mote=i>=5&&i%61==0; stream=i>=5&&i%19==0&&!mote;
            size=mote?0.09D:0.17D+random(i,4)*random(i,4)*0.9D;
            cycle=random(i,7); spin=0.013D+random(i,11)*0.024D;
            orbitSpeed=speed(ring)*(0.90D+random(i,2)*0.20D);
            color=mote?1F:i%29==0?0.48F:(float)(0.12D+random(i,10)*0.12D);
            if(mote) {
                x=Math.cos(angle)*(18D+random(i,8)*6D); y=13D+random(i,9)*37D+Math.sin(phase); z=Math.sin(angle)*21D-2D;
                group=5*SECTORS+i%2;
            } else {
                orbit(ring,angle,radial,0D,out);
                x=out[0]+3D; y=out[1]+(random(i,5)-0.5D)*5D+Math.sin(phase)*random(i,6)*3D; z=out[2]-10D;
                group=ring*SECTORS+Math.floorMod((int)Math.floor(angle/TAU*SECTORS),SECTORS);
            }
            double ca=Math.cos(phase),sa=Math.sin(phase),cb=Math.cos(phase*0.7D),sb=Math.sin(phase*0.7D);
            for(int v=0;v<8;v++) {
                double a=((v&1)-0.5D)*size,b=(((v>>1)&1)-0.5D)*size*0.8D,c=(((v>>2)&1)-0.5D)*size;
                double yy=b*cb-c*sb,zz=b*sb+c*cb;
                corners[v*3]=a*ca+zz*sa; corners[v*3+1]=yy; corners[v*3+2]=-a*sa+zz*ca;
            }
        }
        void emit(Tessellator t,double px,double py,double pz,double shrink,double spin) {
            double ca=spin==0D?1D:Math.cos(spin),sa=spin==0D?0D:Math.sin(spin);
            for(int f=0;f<6;f++) {
                float c=color*SHADE[f]; t.setColorOpaque_F(c*(mote?0.65F:0.62F),c*(mote?0.10F:0.44F),c);
                for(int v:FACES[f]) {
                    int p=v*3; double a=corners[p],b=corners[p+2];
                    t.addVertex(px+(a*ca-b*sa)*shrink,py+corners[p+1]*shrink,pz+(a*sa+b*ca)*shrink);
                }
            }
        }
    }
}
