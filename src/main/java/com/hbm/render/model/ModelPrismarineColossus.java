package com.hbm.render.model;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import com.hbm.entity.mob.colossus.ColossusPose;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Original articulated stone geometry. Each cube is expressed in the server skeleton's block coordinates. */
public final class ModelPrismarineColossus extends ModelBase {
    private final List<Piece> pieces=new ArrayList<Piece>();
    private final DoubleBuffer transform=BufferUtils.createDoubleBuffer(16);
    public ModelPrismarineColossus() {
        textureWidth=256;
        textureHeight=512;
        int torso=ColossusPose.TORSO, head=ColossusPose.HEAD;
        box(torso,0,-3.125,0,-1.75,6.25,6.375,3.5);
        box(torso,1,-2.875,-0.375,-1.875,5.75,1.375,3.75);
        box(torso,0,-3.875,4.25,-1.875,7.75,2,3.75);
        box(torso,1,-2.375,1,-1.875,4.75,4.75,0.25);
        box(torso,5,-1,1.25,-1.9375,2,3.625,0.125);
        box(torso,2,-1.375,5.125,-2,2.75,0.375,0.25);
        box(torso,0,-0.875,3.625,-2,1.75,0.375,0.125);
        for(int side:new int[]{-1,1}) {
            box(torso,0,side<0?-3.375:2.125,0.75,-1.9375,1.25,3.75,0.25);
            box(torso,2,side<0?-3.625:1.625,4.5,-2,2,0.375,0.25);
            box(torso,2,side<0?-2.25:1.625,3.5,-2,0.625,1,0.25);
            box(torso,0,side<0?-1.625:0.75,3.125,-2,0.875,0.375,0.25);
            box(torso,5,side<0?-3.5:2.5,5.625,1.875,1,0.375,0.125);
        }

        // The brow, stepped cheekbones and hanging face stones frame recessed eyes and an open jaw.
        box(head,0,-2.625,1.625,-1.875,5.25,2.875,4.25);
        box(head,1,-2.625,0.125,0.75,5.25,1.5,1.625);
        box(head,3,-1.625,0.125,0.625,3.25,1.625,0.125);
        box(head,0,-2,-0.375,-2,4,0.5,4.25);
        box(head,5,-1.375,-0.625,-2.25,2.75,0.375,1.25);
        box(head,0,-2.75,3.75,-2.625,5.5,0.625,2);
        box(head,2,-2.25,4.125,-2.375,4.5,0.5,1.75);
        box(head,1,-0.625,3.625,-2.75,1.25,0.875,0.5);
        box(head,2,-0.375,3.875,-2.875,0.75,0.375,0.25);
        box(head,0,-0.5,1.625,-2.5,1,1.75,0.75);
        box(head,5,-0.375,1.5,-2.75,0.75,0.5,0.375);
        for(int side:new int[]{-1,1}) {
            double eye=side<0?-2.375:0.625;
            box(head,3,eye,2.375,-2.25,1.75,1.25,0.5);
            box(head,4,eye+0.125,2.625,-2.3125,1.5,0.75,0.125);
            box(head,0,side<0?-2.75:2.25,0.5,-2.375,0.5,3.25,4);
            box(head,5,side<0?-2.5:1.625,1.5,-2.5,0.875,0.875,1.75);
            box(head,2,side<0?-2.25:1.5,-0.875,-2.875,0.75,2.875,1.125);
            box(head,0,side<0?-2:1.5,-1.25,-2.625,0.5,0.375,0.75);
        }
        box(head,1,-1.25,1.5,2.375,2.5,2.25,0.125);
        spiral(head,0,ColossusPose.RUNE_Y,ColossusPose.RUNE_Z,1.875,true);

        arm(ColossusPose.LEFT_ARM,ColossusPose.LEFT_HAND);
        arm(ColossusPose.RIGHT_ARM,ColossusPose.RIGHT_HAND);
        leg(ColossusPose.LEFT_LEG,ColossusPose.LEFT_SHIN,ColossusPose.LEFT_FOOT);
        leg(ColossusPose.RIGHT_LEG,ColossusPose.RIGHT_SHIN,ColossusPose.RIGHT_FOOT);
    }
    private void arm(int upper,int lower) {
        box(upper,0,-1.375,-ColossusPose.UPPER_ARM-0.125,-1.375,2.75,ColossusPose.UPPER_ARM+0.25,2.75);
        box(upper,1,-1.875,-1.75,-1.875,3.75,2.375,3.75);
        box(upper,0,-1.75,0.25,-1.75,3.5,0.75,3.5);
        box(upper,2,-1.625,-0.25,-2,3.25,0.375,0.25);
        box(upper,5,-1.375,-3.875,-1.5,2.75,0.75,0.25);
        box(lower,1,-1.25,-2.875,-1.25,2.5,3.25,2.5);
        box(lower,0,-1.5,-2.75,-1.5,3,2.75,3);
        box(lower,5,-1.625,-2.875,-1.625,3.25,0.5,3.25);
        box(lower,2,-1.5625,-1.125,-1.5625,3.125,0.375,3.125);
        box(lower,1,-1.125,-2.25,-1.5625,2.25,0.875,0.125);
        box(lower,2,-0.875,-1.875,-1.625,0.625,0.375,0.125);
        box(lower,2,0.25,-1.875,-1.625,0.625,0.375,0.125);
        box(lower,0,-ColossusPose.FIST_X_RADIUS,ColossusPose.FIST_Y-ColossusPose.FIST_Y_RADIUS,-ColossusPose.FIST_Z_RADIUS,
            2*ColossusPose.FIST_X_RADIUS,2*ColossusPose.FIST_Y_RADIUS,2*ColossusPose.FIST_Z_RADIUS);
        for(int i=0;i<3;i++) box(lower,5,-1.25+i*0.875,-5,-1.6875,0.75,1,0.125);
    }
    private void leg(int upper,int shin,int foot) {
        box(upper,0,-1.5,-ColossusPose.THIGH-0.125,-1.375,3,ColossusPose.THIGH+0.25,2.75);
        box(upper,5,-1.625,-0.75,-1.5,3.25,1,3);
        box(upper,1,-1.625,-3.375,-1.4375,3.25,0.625,2.875);
        box(shin,0,-1.375,-ColossusPose.SHIN-0.125,-1.25,2.75,ColossusPose.SHIN+0.5,2.5);
        box(shin,1,-1.625,-0.75,-1.5,3.25,1.25,3);
        box(shin,0,-1.375,-1,-1.625,2.75,1.625,0.25);
        box(shin,2,-1,-0.625,-1.75,2,0.375,0.25);
        box(shin,2,-1,-0.25,-1.75,0.375,0.625,0.25);
        box(shin,2,0.625,-0.25,-1.75,0.375,0.625,0.25);
        box(shin,5,-0.375,-0.25,-1.75,0.75,0.625,0.25);
        box(foot,1,-1.875,-ColossusPose.SOLE,-2.75,3.75,0.5,4.25);
        box(foot,0,-1.75,-0.875,-2.625,3.5,0.75,4);
        box(foot,5,-1.5,-0.25,-1.5,3,0.5,2.875);
        box(foot,2,-1.5,-0.75,-2.75,3,0.25,0.25);
    }
    private void spiral(int bone,double x,double y,double z,double size,boolean glow) {
        // Integer texel spans meet at edges; overlapping coplanar glow faces flicker in 1.7's depth buffer.
        int span=Math.max(8,(int)Math.round(size*8)),w=2,lo= -span/2,hi=lo+span,inner= -span/10;
        int material=glow?4:1;
        box(bone,material,x+lo/8D,y+lo/8D,z,span/8D,w/8D,0.125);
        box(bone,material,x+lo/8D,y+(lo+w)/8D,z,w/8D,(span-2*w)/8D,0.125);
        box(bone,material,x+lo/8D,y+(hi-w)/8D,z,span/8D,w/8D,0.125);
        box(bone,material,x+(hi-w)/8D,y+(inner+w)/8D,z,w/8D,Math.max(1,hi-inner-2*w)/8D,0.125);
        box(bone,material,x+inner/8D,y+inner/8D,z,(hi-inner)/8D,w/8D,0.125);
        box(bone,material,x+inner/8D,y+(inner+w)/8D,z,w/8D,Math.max(1,span/3-w)/8D,0.125);
    }
    private void box(int bone,int material,double x,double y,double z,double w,double h,double d) {
        ModelRenderer model=new ModelRenderer(this,0,material*96);
        model.addBox((float)x*8,(float)y*8,(float)z*8,Math.max(1,(int)Math.round(w*8)),Math.max(1,(int)Math.round(h*8)),Math.max(1,(int)Math.round(d*8)));
        pieces.add(new Piece(bone,material,model,pieces.size()*31+bone*17));
    }
    private void matrix(ColossusPose pose,int bone) { transform.clear();transform.put(pose.bones[bone].matrix);transform.flip();GL11.glMultMatrix(transform); }
    public void render(ColossusPose pose,boolean glow,float corruption,float fade,float breakup) {
        for(int bone=0;bone<pose.bones.length;bone++) {
            GL11.glPushMatrix();matrix(pose,bone);
            for(Piece p:pieces) if(p.bone==bone && (p.material==4)==glow) {
                if(breakup>0 && p.seed%17 < breakup*17) continue;
                GL11.glPushMatrix();
                if(breakup>0) {
                    GL11.glTranslatef(((p.seed%7)-3)*breakup*2,((p.seed%11)-3)*breakup,((p.seed%13)-6)*breakup);
                    GL11.glRotatef(p.seed*breakup,1,1,0);
                }
                if(glow) GL11.glColor4f((0.25F+corruption*0.5F)*fade,(1-corruption*0.4F)*fade,0.87F*fade,fade);
                else if(p.material==0) GL11.glColor3f(0.34F,0.50F,0.46F);
                else if(p.material==1) GL11.glColor3f(0.16F,0.25F,0.22F);
                else if(p.material==2) GL11.glColor3f(0.54F,0.65F,0.54F);
                else if(p.material==5) GL11.glColor3f(0.24F,0.37F,0.33F);
                else GL11.glColor3f(0.025F,0.065F,0.055F);
                p.model.render(0.125F);
                GL11.glPopMatrix();
            }
            GL11.glPopMatrix();
        }
    }
    private static final class Piece {
        final int bone,material,seed; final ModelRenderer model;
        Piece(int b,int m,ModelRenderer p,int n) { bone=b;material=m;model=p;seed=n; }
    }
}
