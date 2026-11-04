package com.hbm.entity.mob.colossus;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

/** One oriented sample of a rendered limb. The broad box is only used to find candidates. */
final class ColossusContact {
    private static final Vec3[] WORLD_AXES = {
        Vec3.createVectorHelper(1,0,0), Vec3.createVectorHelper(0,1,0), Vec3.createVectorHelper(0,0,1)
    };
    final Vec3 center;
    final AxisAlignedBB bounds;
    private final Vec3[] axes = new Vec3[3];
    private final double[] half;

    ColossusContact(ColossusPose pose,int bone,double y,double z,double hx,double hy,double hz,
                    double x,double baseY,double baseZ,double yaw,double scale) {
        double angle=Math.toRadians(180-yaw), c=Math.cos(angle), s=Math.sin(angle);
        double[] m=pose.bones[bone].matrix;
        for(int i=0;i<3;i++) {
            int j=i*4;
            axes[i]=Vec3.createVectorHelper(c*m[j]+s*m[j+2],m[j+1],-s*m[j]+c*m[j+2]);
        }
        Vec3 p=pose.point(bone,0,y,z);
        center=Vec3.createVectorHelper(x+scale*(c*p.xCoord+s*p.zCoord),baseY+scale*p.yCoord,
            baseZ+scale*(-s*p.xCoord+c*p.zCoord));
        half=new double[]{hx*scale,hy*scale,hz*scale};
        double ex=radius(WORLD_AXES[0]), ey=radius(WORLD_AXES[1]), ez=radius(WORLD_AXES[2]);
        bounds=AxisAlignedBB.getBoundingBox(center.xCoord-ex,center.yCoord-ey,center.zCoord-ez,
            center.xCoord+ex,center.yCoord+ey,center.zCoord+ez);
    }

    boolean intersects(AxisAlignedBB box) {
        if(!bounds.intersectsWith(box)) return false;
        double dx=(box.minX+box.maxX)*0.5-center.xCoord;
        double dy=(box.minY+box.maxY)*0.5-center.yCoord;
        double dz=(box.minZ+box.maxZ)*0.5-center.zCoord;
        double hx=(box.maxX-box.minX)*0.5, hy=(box.maxY-box.minY)*0.5, hz=(box.maxZ-box.minZ)*0.5;
        for(Vec3 axis:axes) {
            if(separates(axis.xCoord,axis.yCoord,axis.zCoord,dx,dy,dz,hx,hy,hz)) return false;
            if(separates(0,axis.zCoord,-axis.yCoord,dx,dy,dz,hx,hy,hz)
                || separates(-axis.zCoord,0,axis.xCoord,dx,dy,dz,hx,hy,hz)
                || separates(axis.yCoord,-axis.xCoord,0,dx,dy,dz,hx,hy,hz)) return false;
        }
        return true;
    }

    private double radius(Vec3 axis) {
        return half[0]*Math.abs(axis.dotProduct(axes[0]))+half[1]*Math.abs(axis.dotProduct(axes[1]))
            +half[2]*Math.abs(axis.dotProduct(axes[2]));
    }

    private boolean separates(double x,double y,double z,double dx,double dy,double dz,double hx,double hy,double hz) {
        double limb=0;
        for(int i=0;i<3;i++) limb+=half[i]*Math.abs(x*axes[i].xCoord+y*axes[i].yCoord+z*axes[i].zCoord);
        return Math.abs(x*dx+y*dy+z*dz)>limb+Math.abs(x)*hx+Math.abs(y)*hy+Math.abs(z)*hz+1E-7;
    }

    static AxisAlignedBB union(AxisAlignedBB a,AxisAlignedBB b) {
        return AxisAlignedBB.getBoundingBox(Math.min(a.minX,b.minX),Math.min(a.minY,b.minY),Math.min(a.minZ,b.minZ),
            Math.max(a.maxX,b.maxX),Math.max(a.maxY,b.maxY),Math.max(a.maxZ,b.maxZ));
    }
}
