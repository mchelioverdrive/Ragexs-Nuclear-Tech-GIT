package com.hbm.explosion.nuclear;

import java.util.ArrayDeque;
import java.util.HashSet;
import com.hbm.config.BombConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.explosion.ExplosionTerrainAccess;
import com.hbm.explosion.ExplosionWorkScheduler;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Deterministic classification shared by independent physical and visual factories. */
public final class NuclearBurstResolver {
 public static final double FIREBALL_RADIUS_FACTOR = 0.35D;
 private static final int MAX_RELEASE_SEARCH_NODES = 16384;
 private NuclearBurstResolver() { }
 public static NuclearBurstContext resolve(World world,double x,double y,double z,int legacyRadius) {
  int radius=Math.max(1,legacyRadius), bx=MathHelper.floor_double(x), by=MathHelper.floor_double(y), bz=MathHelper.floor_double(z);
  double yield=BombConfig.ktFromRadius(radius), base=BombConfig.radiusFromKt((float)yield), surface=world.getHeightValue(bx,bz), height=y-surface, fireball=base*FIREBALL_RADIUS_FACTOR;
  double coupling=y-fireball>surface?0D:clamp((surface-(y-fireball))/Math.max(1D,fireball));
  CBT_Atmosphere atmosphere=CelestialBody.getTrait(world,CBT_Atmosphere.class); BurstType type;
  if(CelestialBody.inOrbit(world)||atmosphere==null||atmosphere.getPressure()<0.01D) type=BurstType.VACUUM;
  else if(world.getBlock(bx,by,bz).getMaterial().isLiquid()) type=BurstType.UNDERWATER;
  else if(y<surface) type=BurstType.SUBSURFACE; else if(coupling==0D) type=BurstType.AIR; else type=BurstType.SURFACE;
  double burial=type==BurstType.SUBSURFACE?Math.max(0D,surface-y):0D;
  double predicted=type==BurstType.SUBSURFACE?calculateBreakthrough(world,bx,by,bz,(int)surface,base):(type==BurstType.SURFACE?1D:0D);
  int[] breach=type==BurstType.SUBSURFACE?findOpenRelease(world,bx,by,bz,Math.min(64,Math.max(8,(int)Math.ceil(base*.60D)))):new int[]{bx,(int)surface,bz};
  boolean actual=type!=BurstType.SUBSURFACE||breach!=null, vented=type==BurstType.SUBSURFACE&&actual, contained=type==BurstType.SUBSURFACE&&!actual;
  double release=type==BurstType.SUBSURFACE?(actual?Math.max(.05D,predicted):0D):1D, deform=type==BurstType.SUBSURFACE?predicted:coupling;
  if(breach==null) breach=new int[]{bx,(int)surface,bz};
  NuclearDetonationSpec spec=NuclearDetonationSpec.fromLegacyRadius(radius); spec.burstType=type; spec.burstHeight=height; spec.groundCoupling=coupling; spec.burialDepth=burial; spec.predictedBreakthroughFactor=spec.surfaceBreakthroughFactor=predicted; spec.actualSurfaceBreach=actual; spec.atmosphericReleaseFactor=release; spec.surfaceDeformationFactor=deform; spec.breachConfirmationComplete=false; spec.contained=contained; spec.vented=vented; spec.breachX=breach[0]; spec.breachY=breach[1]; spec.breachZ=breach[2];
  return new NuclearBurstContext(radius,yield,type,surface,height,fireball,coupling,burial,predicted,actual,release,deform,contained,vented,breach[0],breach[1],breach[2],NuclearEffectsSolver.solve(spec));
 }
 /** Bounded air/replaceable-space search. Solids, including a one-block roof, terminate a route. */
 public static int[] findOpenRelease(World world,int x,int y,int z,int radius) {
  ArrayDeque<BlockPos> q=new ArrayDeque<BlockPos>(); HashSet<BlockPos> seen=new HashSet<BlockPos>();
  for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) { BlockPos p=new BlockPos(x+dx,y+dy,z+dz); if(isOpen(world,p.getX(),p.getY(),p.getZ())) { q.add(p); seen.add(p); } }
  while(!q.isEmpty()&&seen.size()<MAX_RELEASE_SEARCH_NODES) { BlockPos p=q.removeFirst(); if(world.canBlockSeeTheSky(p.getX(),p.getY(),p.getZ())) return new int[]{p.getX(),p.getY(),p.getZ()};
   int[][] d={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}; for(int[] a:d) { int xx=p.getX()+a[0],yy=p.getY()+a[1],zz=p.getZ()+a[2]; if(yy<0||yy>255||Math.abs(xx-x)>radius||Math.abs(yy-y)>radius||Math.abs(zz-z)>radius) continue; BlockPos n=new BlockPos(xx,yy,zz); if(!seen.contains(n)&&isOpen(world,xx,yy,zz)){seen.add(n);q.addLast(n);} }
  } return null;
 }
 private static boolean isOpen(World w,int x,int y,int z){ Block b=w.getBlock(x,y,z); return b==Blocks.air||b.isReplaceable(w,x,y,z); }

 /** Incremental version of the same release search for scheduled post-excavation
  * confirmation. No world changes occur here; reload may restart the search. */
 public static final class ReleaseSearch {
  private static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
  private final int x,y,z,radius;
  private final ArrayDeque<BlockPos> queue=new ArrayDeque<BlockPos>();
  private final HashSet<BlockPos> seen=new HashSet<BlockPos>();
  private int initial, side;
  private BlockPos active;
  public boolean complete;
  public int[] breach;
  public ReleaseSearch(int x,int y,int z,int radius){this.x=x;this.y=y;this.z=z;this.radius=radius;}
  public void advance(ExplosionTerrainAccess.Access access,long deadline){
   if(complete)return;
   World world=access.world;
   while(initial<27&&ExplosionWorkScheduler.before(deadline)){
    BlockPos p=new BlockPos(x+initial/9-1,y+(initial/3)%3-1,z+initial%3-1);
    if(ExplosionTerrainAccess.chunk(access,p.getX()>>4,p.getZ()>>4,deadline)==null)return;
    if(isOpen(world,p.getX(),p.getY(),p.getZ())){queue.add(p);seen.add(p);} initial++;
   }
   if(initial<27)return;
   while(ExplosionWorkScheduler.before(deadline)){
    if(active==null){
     if(queue.isEmpty()||seen.size()>=MAX_RELEASE_SEARCH_NODES){complete=true;return;}
     active=queue.peekFirst();side=-1;
    }
    if(ExplosionTerrainAccess.chunk(access,active.getX()>>4,active.getZ()>>4,deadline)==null)return;
    if(side<0){
     if(world.canBlockSeeTheSky(active.getX(),active.getY(),active.getZ())){breach=new int[]{active.getX(),active.getY(),active.getZ()};complete=true;return;}
     queue.removeFirst();side=0;
    }
    while(side<6&&ExplosionWorkScheduler.before(deadline)){
     int[] d=DIRECTIONS[side];int xx=active.getX()+d[0],yy=active.getY()+d[1],zz=active.getZ()+d[2];
     if(yy>=0&&yy<=255&&Math.abs(xx-x)<=radius&&Math.abs(yy-y)<=radius&&Math.abs(zz-z)<=radius){
      BlockPos n=new BlockPos(xx,yy,zz);
      if(!seen.contains(n)){
       if(ExplosionTerrainAccess.chunk(access,xx>>4,zz>>4,deadline)==null)return;
       if(isOpen(world,xx,yy,zz)){seen.add(n);queue.addLast(n);}
      }
     }
     side++;
    }
    if(side<6)return;active=null;
   }
  }
 }
 private static double calculateBreakthrough(World w,int x,int y,int z,int sy,double base){double reach=Math.max(1D,base*.55D),cost=0D;for(int yy=y+1;yy<sy;yy++){Block b=w.getBlock(x,yy,z);if(b==Blocks.air)continue;if(b.getMaterial().isLiquid())cost+=.25D;else cost+=1D+Math.min(8D,Math.max(0D,b.getExplosionResistance(null))/12D);}return clamp((reach-cost)/Math.max(1D,reach*.65D));}
 private static double clamp(double v){return Math.max(0D,Math.min(1D,v));}
}
