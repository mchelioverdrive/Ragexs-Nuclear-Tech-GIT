package com.hbm.entity.mob.colossus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.hbm.config.ColossusConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;

/** Loaded terrain probes and budgeted processing of past limb contacts. */
public final class ColossusTerrain {
    private final EntityPrismarineColossus boss;
    private final ArrayDeque<Contact> queue = new ArrayDeque<Contact>();
    private final ArrayDeque<Contact> current = new ArrayDeque<Contact>();
    private final ArrayDeque<Contact> priority = new ArrayDeque<Contact>();
    private final ArrayList<AxisAlignedBB> shapes = new ArrayList<AxisAlignedBB>();
    private Set<Long> examined = new HashSet<Long>();
    private final Map<Long,Long> vetoed = new LinkedHashMap<Long,Long>();
    private static World suppression;
    public ColossusTerrain(EntityPrismarineColossus boss) { this.boss = boss; }
    public static boolean suppressing(World w) { return suppression == w; }
    boolean loaded(AxisAlignedBB box) {
        return boss.worldObj.checkChunksExist(MathHelper.floor_double(box.minX), 0, MathHelper.floor_double(box.minZ),
            MathHelper.floor_double(box.maxX), 255, MathHelper.floor_double(box.maxZ));
    }
    boolean clearBlocks(AxisAlignedBB box) {
        return loaded(box) && boss.worldObj.func_147461_a(box).isEmpty();
    }
    double verticalClearance(AxisAlignedBB box, double offset) {
        AxisAlignedBB sweep = box.addCoord(0, offset, 0);
        if(!loaded(sweep)) return 0;
        for(Object collision : boss.worldObj.func_147461_a(sweep)) offset = ((AxisAlignedBB)collision).calculateYOffset(box, offset);
        return offset;
    }
    double floorLift(AxisAlignedBB box) {
        if(clearBlocks(box)) return 0;
        double probe=0.25;
        AxisAlignedBB raised=box.getOffsetBoundingBox(0,probe,0);
        if(!clearBlocks(raised)) return 0;
        return probe+verticalClearance(raised,-probe);
    }
    double ledgeHeight(AxisAlignedBB foot, double dx, double dz, double height) {
        AxisAlignedBB sweep = foot.addCoord(dx, 0, dz);
        if(!loaded(sweep)) return Double.POSITIVE_INFINITY;
        for(Object collision : boss.worldObj.func_147461_a(sweep)) height = Math.max(height, ((AxisAlignedBB)collision).maxY);
        return height;
    }

    double groundOffset(AxisAlignedBB sole, double scale) {
        double rise=1.0625, drop=1.25*scale;
        AxisAlignedBB raised=sole.getOffsetBoundingBox(0,rise,0);
        AxisAlignedBB sweep=raised.addCoord(0,-rise-drop,0);
        if(!loaded(sweep)) return 0;
        double offset=-rise-drop;
        for(Object o:boss.worldObj.func_147461_a(sweep)) offset=((AxisAlignedBB)o).calculateYOffset(raised,offset);
        return offset<=-rise-drop?0:(rise+offset)/scale;
    }

    Vec3 groundImpact(ColossusContact contact) {
        AxisAlignedBB b=contact.bounds;
        AxisAlignedBB lower=AxisAlignedBB.getBoundingBox(b.minX,b.minY,b.minZ,b.maxX,contact.center.yCoord,b.maxZ);
        if(!loaded(lower.expand(1,0,1))) return null;
        AxisAlignedBB surface=null;
        for(Object o:boss.worldObj.func_147461_a(lower)) {
            AxisAlignedBB box=(AxisAlignedBB)o;
            if(box.maxY<=contact.center.yCoord && (surface==null || box.maxY>surface.maxY) && contact.intersects(box))
                surface=box;
        }
        if(surface==null) return null;
        return Vec3.createVectorHelper(Math.max(surface.minX,Math.min(surface.maxX,contact.center.xCoord)),surface.maxY,
            Math.max(surface.minZ,Math.min(surface.maxZ,contact.center.zCoord)));
    }

    boolean enabled() {
        return !boss.worldObj.isRemote && ColossusConfig.terrain && ColossusConfig.blocks>0
            && boss.worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing");
    }

    void beginAttack() { examined=new HashSet<Long>(); }

    void contact(ColossusContact[] samples, Obstruction obstruction) {
        if(!enabled()) return;
        AxisAlignedBB bounds=samples[0].bounds;
        for(int i=1;i<samples.length;i++) bounds=ColossusContact.union(bounds,samples[i].bounds);
        current.add(new Contact(bounds,samples,examined));
        if(obstruction!=null && priority.isEmpty() && !examined.contains(key(obstruction.x,obstruction.y,obstruction.z))) {
            for(ColossusContact sample:samples) if(sample.intersects(obstruction.shape)) {
                priority.addFirst(new Contact(unit(obstruction.x,obstruction.y,obstruction.z),samples,examined));
                break;
            }
        }
    }

    void contactSurface(ColossusContact sample,double surface) {
        if(!enabled() || priority.size()>=8) return;
        AxisAlignedBB b=sample.bounds;
        // Spend the first lookups at the struck surface instead of in the air along the downswing.
        double bottom=Math.max(b.minY,Math.floor(surface-1E-6));
        if(bottom<surface) {
            AxisAlignedBB layer=AxisAlignedBB.getBoundingBox(b.minX,bottom,b.minZ,b.maxX,surface,b.maxZ);
            priority.add(new Contact(layer,new ColossusContact[]{sample},examined));
        }
    }

    public void clear() { queue.clear(); current.clear(); priority.clear(); }

    private static long key(int x,int y,int z) {
        return ((long)(x & 0x3ffffff)<<38) | ((long)(z & 0x3ffffff)<<12) | y;
    }

    private static AxisAlignedBB unit(int x,int y,int z) {
        return AxisAlignedBB.getBoundingBox(x,y,z,x+1,y+1,z+1);
    }

    private boolean available(int x,int y,int z) {
        return y>=1 && y<=254 && EntityPrismarineColossus.loaded(boss.worldObj,x,z,1);
    }

    private boolean breakable(Block block,int x,int y,int z,int meta) {
        World w=boss.worldObj;
        return block!=Blocks.air && block!=Blocks.bedrock && block!=Blocks.command_block && block!=Blocks.portal
            && block!=Blocks.end_portal && block!=Blocks.end_portal_frame && !block.getMaterial().isLiquid()
            && !block.hasTileEntity(meta) && w.getTileEntity(x,y,z)==null && block.getBlockHardness(w,x,y,z)>=0
            && !ColossusConfig.protectedBlocks.contains(Block.blockRegistry.getNameForObject(block)) && block.canEntityDestroy(w,x,y,z,boss);
    }

    private void collisionShapes(Block block,int x,int y,int z,AxisAlignedBB box) {
        shapes.clear();
        block.addCollisionBoxesToList(boss.worldObj,x,y,z,box,shapes,boss);
    }

    public void tick() {
        if(!enabled()) { clear(); return; }
        World w=boss.worldObj;
        long time=w.getTotalWorldTime();
        ColossusEncounters ledger=ColossusEncounters.get(w);
        // Process current strike frames before queued work from older frames.
        while(!current.isEmpty()) {
            if(queue.size()>=64) queue.removeLast();
            queue.addFirst(current.removeLast());
        }
        // Spend the shared budget on world lookups. Also bound the work of skipping old or overlapping samples.
        for(int i=0;i<ColossusConfig.blocks*8 && (!priority.isEmpty() || !queue.isEmpty());i++) {
            ArrayDeque<Contact> pending=priority.isEmpty()?queue:priority;
            Contact c=pending.peek();
            if(c.cursor>=c.size || time-c.tick>100) {
                pending.remove();
                continue;
            }
            int x=c.x(), y=c.y(), z=c.z();
            long key=key(x,y,z);
            if(c.examined.contains(key) || !c.touches(unit(x,y,z))) { c.cursor++; continue; }
            if(!ledger.spend(time)) break;
            c.cursor++;
            if(!available(x,y,z)) continue;
            Block block=w.getBlock(x,y,z);
            int meta=w.getBlockMetadata(x,y,z);
            if(!breakable(block,x,y,z,meta)) { c.examined.add(key); continue; }
            collisionShapes(block,x,y,z,c.bounds);
            boolean touches=false;
            for(AxisAlignedBB shape:shapes) if(c.touches(shape)) { touches=true; break; }
            if(!touches) {
                if(!shapes.isEmpty() || block.getMaterial().blocksMovement() || block.getCollisionBoundingBoxFromPool(w,x,y,z)!=null) continue;
                block.setBlockBoundsBasedOnState(w,x,y,z);
                if(!c.touches(AxisAlignedBB.getBoundingBox(x+block.getBlockBoundsMinX(),y+block.getBlockBoundsMinY(),z+block.getBlockBoundsMinZ(),
                    x+block.getBlockBoundsMaxX(),y+block.getBlockBoundsMaxY(),z+block.getBlockBoundsMaxZ()))) continue;
            }
            c.examined.add(key);
            EntityPlayerMP actor = FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer)w);
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x,y,z,w,block,meta,actor);
            if(MinecraftForge.EVENT_BUS.post(event)) {
                if(vetoed.size()>=128) vetoed.remove(vetoed.keySet().iterator().next());
                vetoed.put(key,time+200);
                continue;
            }
            if(!enabled() || !boss.isEntityAlive() || w.getBlock(x,y,z)!=block || w.getBlockMetadata(x,y,z)!=meta
                || w.getTileEntity(x,y,z)!=null) continue;
            suppression = w;
            try { w.setBlock(x,y,z,Blocks.air,0,2); } finally { suppression = null; }
        }
    }

    final class ObstacleProbe {
        private final ArrayDeque<Cursor> strips=new ArrayDeque<Cursor>();
        ObstacleProbe(AxisAlignedBB[] body,double dx,double dz) {
            for(AxisAlignedBB box:body) {
                // Only new space on the leading faces can stop this horizontal step.
                if(Math.abs(dx)>1E-6) {
                    double edge=dx>0?box.maxX:box.minX;
                    strips.add(new Cursor(AxisAlignedBB.getBoundingBox(Math.min(edge,edge+dx),box.minY,
                        box.minZ+Math.min(0,dz),Math.max(edge,edge+dx),box.maxY,box.maxZ+Math.max(0,dz))));
                }
                if(Math.abs(dz)>1E-6) {
                    double edge=dz>0?box.maxZ:box.minZ;
                    strips.add(new Cursor(AxisAlignedBB.getBoundingBox(box.minX,box.minY,Math.min(edge,edge+dz),
                        box.maxX,box.maxY,Math.max(edge,edge+dz))));
                }
            }
        }
        boolean done() { return strips.isEmpty(); }
        Obstruction next() {
            World w=boss.worldObj;
            ColossusEncounters ledger=ColossusEncounters.get(w);
            long time=w.getTotalWorldTime();
            while(!strips.isEmpty()) {
                Cursor c=strips.peek();
                if(c.cursor>=c.size) { strips.remove(); continue; }
                if(!ledger.spend(time)) return null;
                int x=c.x(), y=c.y(), z=c.z();
                c.cursor++;
                if(!available(x,y,z)) continue;
                Long veto=vetoed.get(key(x,y,z));
                if(veto!=null && veto>time) continue;
                Block block=w.getBlock(x,y,z);
                if(!breakable(block,x,y,z,w.getBlockMetadata(x,y,z))) continue;
                collisionShapes(block,x,y,z,c.bounds);
                if(!shapes.isEmpty()) return new Obstruction(x,y,z,shapes.get(0).copy());
            }
            return null;
        }
    }

    static final class Obstruction {
        final int x,y,z;
        final AxisAlignedBB shape;
        Obstruction(int x,int y,int z,AxisAlignedBB shape) { this.x=x;this.y=y;this.z=z;this.shape=shape; }
    }

    private static class Cursor {
        final AxisAlignedBB bounds;
        final int x,y,z,sx,sz,size;
        int cursor;
        Cursor(AxisAlignedBB b) {
            bounds=b;
            x=MathHelper.floor_double(b.minX); y=Math.max(1,MathHelper.floor_double(b.minY)); z=MathHelper.floor_double(b.minZ);
            sx=MathHelper.ceiling_double_int(b.maxX)-x; sz=MathHelper.ceiling_double_int(b.maxZ)-z;
            size=sx*sz*Math.max(0,Math.min(255,MathHelper.ceiling_double_int(b.maxY))-y);
        }
        int x() { return x+cursor%sx; }
        int z() { return z+cursor/sx%sz; }
        int y() { return y+cursor/(sx*sz); }
    }

    private final class Contact extends Cursor {
        final ColossusContact[] samples;
        final Set<Long> examined;
        final long tick;
        Contact(AxisAlignedBB bounds,ColossusContact[] samples,Set<Long> examined) {
            super(bounds);
            this.samples=samples;
            this.examined=examined;
            tick=boss.worldObj.getTotalWorldTime();
        }
        boolean touches(AxisAlignedBB box) {
            for(ColossusContact sample:samples) if(sample.intersects(box)) return true;
            return false;
        }
    }
}
