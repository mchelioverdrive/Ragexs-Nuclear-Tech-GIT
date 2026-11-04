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
        textureWidth=textureHeight=256;
        int torso=ColossusPose.TORSO,head=ColossusPose.HEAD;
        box(torso,0,-3.5,0,-1.75,7,6.7,3.5);
        box(torso,1,-4,4.5,-2,8,2.4,4);
        box(torso,1,-3.2,-0.35,-2,6.4,1.5,4);
        box(torso,2,-2.6,1.1,-1.88,5.2,3.4,0.25);
        // Chest carving: a raised stepped square spiral, with a dark central recess.
        spiral(torso,0,2.7,-2.12,2.7,false);
        for(int side:new int[]{-1,1}) {
            box(torso,1,side<0?-4.7:3.7,4.9,-2.1,1,2.4,4.2);
            box(torso,2,side<0?-4.5:3.5,6.4,-2.2,1,0.55,4.4);
            box(torso,1,side<0?-3.4:2.4,0.4,-2.14,1,3.7,0.35);
            for(int i=0;i<3;i++) box(torso,2,side<0?-3.3:2.5,1.1+i*1.05,-2.36,0.8,0.3,0.25);
        }
        // Solid cheeks, brow and rear skull frame a deep rectangular mouth cavity.
        box(head,0,-3,2.2,-2.5,6,2.8,5);
        box(head,1,-3,0,-2.5,1.2,2.2,5); box(head,1,1.8,0,-2.5,1.2,2.2,5);
        box(head,1,-1.8,0,1.0,3.6,2.2,1.5);
        box(head,1,-2.5,-0.1,-2.5,5,0.75,5);
        box(head,3,-1.8,0.7,0.85,3.6,1.5,0.125);
        box(head,2,-3.15,4.2,-2.7,6.3,0.8,1.5);
        box(head,1,-3.1,2.95,-2.65,6.2,0.35,0.3);
        box(head,1,-0.45,2.3,-2.8,0.9,1.5,0.5);
        box(head,2,-1.35,-0.45,-2.15,2.7,0.4,1);
        box(head,1,-0.65,-0.75,-1.85,1.3,0.3,0.7);
        // Eyes and rear rune have their own material, rendered only in the emissive pass.
        box(head,4,-2.55,3.35,-2.67,1.75,0.65,0.18); box(head,4,0.8,3.35,-2.67,1.75,0.65,0.18);
        box(head,1,-1.25,1.6,2.5,2.5,2.2,0.12);
        spiral(head,0,2.7,2.65,1.8,true);
        for(int i=0;i<3;i++) {
            box(head,2,-2.65+i*2.1,4.7,-1.8,1.1,0.45,3.8);
            box(head,1,-3.12,0.8+i*1.05,-1.7,0.25,0.4,3.4);
            box(head,1,2.87,0.8+i*1.05,-1.7,0.25,0.4,3.4);
        }
        arm(ColossusPose.LEFT_ARM,ColossusPose.LEFT_HAND);
        arm(ColossusPose.RIGHT_ARM,ColossusPose.RIGHT_HAND);
        leg(ColossusPose.LEFT_LEG,ColossusPose.LEFT_FOOT);
        leg(ColossusPose.RIGHT_LEG,ColossusPose.RIGHT_FOOT);
    }
    private void arm(int upper,int lower) {
        box(upper,0,-1.45,-4,-1.45,2.9,4,2.9);
        box(upper,1,-1.9,-1.2,-1.9,3.8,1.5,3.8);
        box(upper,2,-1.7,0.3,-1.7,3.4,0.6,3.4);
        box(upper,1,-1.65,-3.7,-1.55,3.3,0.65,3.1);
        box(lower,0,-1.35,-2.5,-1.35,2.7,2.7,2.7);
        box(lower,2,-1.6,-2.6,-1.6,3.2,0.65,3.2);
        box(lower,1,-1.7,-4.2,-1.8,3.4,1.6,3.6);
        box(lower,0,-1.85,-4.15,-1.95,3.7,1.0,3.9);
        spiral(upper,0,-2,-1.6,2.0,false);
        spiral(lower,0,-1.3,-1.53,1.8,false);
        for(int j=0;j<3;j++) box(lower,2,-1.35+j*0.95,-4.25,-2.05,0.65,0.8,0.25);
    }
    private void leg(int upper,int lower) {
        box(upper,0,-1.6,-4,-1.5,3.2,4,3);
        box(upper,1,-1.8,-0.8,-1.7,3.6,1,3.4);
        box(lower,0,-1.5,-3,-1.4,3,3.1,2.8);
        box(lower,1,-1.7,-0.5,-1.8,3.4,1.2,3.4);
        box(lower,2,-1.25,-0.25,-1.95,2.5,0.7,0.3);
        box(lower,1,-1.85,-4,-2.65,3.7,1.25,4.4);
        box(lower,2,-1.9,-3.1,-2.7,3.8,0.45,4.5);
        spiral(upper,0,-2.3,-1.65,2.3,false);
        for(int i=0;i<3;i++) box(lower,2,-1.25+i*0.9,-3.9,-2.8,0.6,0.65,0.2);
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
        ModelRenderer model=new ModelRenderer(this,0,0);
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
                if(glow) GL11.glColor4f((0.35F+corruption*0.5F)*fade,(1-corruption*0.4F)*fade,0.8F*fade,fade);
                else if(p.material==0) GL11.glColor3f(0.37F,0.65F,0.60F);
                else if(p.material==1) GL11.glColor3f(0.13F,0.29F,0.26F);
                else if(p.material==2) GL11.glColor3f(0.65F,0.76F,0.61F);
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
