package com.hbm.entity.mob.colossus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.ColossusConfig;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.ColossusAttachmentPacket;
import api.hbm.entity.IRadiationImmune;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class EntityPrismarineColossus extends EntityLiving implements IBossDisplayData, IMob, IRadiationImmune {
    public static final int ACTIVE=0, DORMANT=1, ADMIN=2, GLITCHED=3;
    public final ColossusPose skeleton=new ColossusPose();
    private final EntityColossusPart[] parts=new EntityColossusPart[8];
    private final ColossusTerrain terrain=new ColossusTerrain(this);
    private final List<EntityLivingBase> candidates=new ArrayList<EntityLivingBase>(64);
    private final Set<UUID> struck=new HashSet<UUID>();
    private final Map<UUID,Grip> grips=new LinkedHashMap<UUID,Grip>();
    private EntityLivingBase target, grabbed;
    private int cooldown=60, searchOffset, blocked, steeringTicks, grabEscape, stagger, shakeTimer=280;
    private float walk, previousWalk, moving, previousMoving, previousHeadYaw, previousHeadPitch;
    private double steeringX,steeringZ;
    private boolean clockUsed, registered, deathDrops, dropShards=true;
    private long clockOriginal,clockStarted,clockExpected;
    private boolean clockChanged;
    private AxisAlignedBB lastHand;
    private int clientState=-1;
    private float reaction;
    private int interpolation;
    private double nextX,nextY,nextZ;
    private float nextYaw;

    public EntityPrismarineColossus(World w) {
        super(w); setSize(3,6); isImmuneToFire=true; ignoreFrustumCheck=true; experienceValue=150;
        dataWatcher.updateObject(23,ColossusConfig.scale);
        setHealth(getMaxHealth());
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20,(byte)ACTIVE); dataWatcher.addObject(21,(byte)ColossusPose.IDLE);
        dataWatcher.addObject(22,0); dataWatcher.addObject(23,1F);
        dataWatcher.addObject(24,0F); dataWatcher.addObject(25,0F); dataWatcher.addObject(26,0);
        dataWatcher.addObject(27,0F); dataWatcher.addObject(28,(byte)0);
        dataWatcher.addObject(29,0F);
        dataWatcher.addObject(30,0);
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(ColossusConfig.health);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(ColossusConfig.range);
    }
    @Override protected void updateEntityActionState() { }
    @Override protected void despawnEntity() { }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override protected void fall(float distance) { }
    @Override public boolean isInRangeToRenderDist(double d) { return d<256D*256D; }
    @Override public void addPotionEffect(PotionEffect effect) { }
    public int variant() { return dataWatcher.getWatchableObjectByte(20); }
    public int state() { return dataWatcher.getWatchableObjectByte(21); }
    public float scale() { return dataWatcher.getWatchableObjectFloat(23); }
    public float headYaw() { return dataWatcher.getWatchableObjectFloat(24); }
    public float headPitch() { return dataWatcher.getWatchableObjectFloat(25); }
    public float weakFlare(float partial) { return Math.max(0,Math.min(1,(dataWatcher.getWatchableObjectInt(30)-(int)worldObj.getTotalWorldTime()-partial)/20F)); }
    public boolean dormant() { return variant()==DORMANT; }
    public float stateTime(float partial) { return Math.max(0,Math.min(ColossusPose.duration(state()), (int)worldObj.getTotalWorldTime()-dataWatcher.getWatchableObjectInt(22)+partial)); }
    public float walk(float partial) { return previousWalk+(walk-previousWalk)*partial; }
    public float moving(float partial) { return previousMoving+(moving-previousMoving)*partial; }
    public void setVariant(int variant) { dataWatcher.updateObject(20,(byte)MathHelper.clamp_int(variant,0,3)); }
    public void activate() { if(!worldObj.isRemote && dormant()) { setVariant(ACTIVE); sound("mob.irongolem.throw",0.7F); cooldown=35; } }
    public void setDropShards(boolean value) { dropShards=value; }
    public boolean admit() { registered=ColossusEncounters.get(worldObj).admit(getUniqueID()); return registered; }
    public boolean safeSpawn() { return clearBody(posX,posY,posZ) && supported(posX,posY,posZ); }
    public void releasePlayer(EntityPlayer p) { if(grabbed==p) releaseGrab(); if(grips.containsKey(p.getUniqueID())) release(p,false); }
    @Override public void setPositionAndRotation2(double x,double y,double z,float yaw,float pitch,int ticks) {
        nextX=x;nextY=y;nextZ=z;nextYaw=yaw;interpolation=Math.max(1,ticks);
    }
    private void interpolate() {
        if(interpolation>0) {
            double steps=interpolation--;
            setPosition(posX+(nextX-posX)/steps,posY+(nextY-posY)/steps,posZ+(nextZ-posZ)/steps);
            rotationYaw+=MathHelper.wrapAngleTo180_float(nextYaw-rotationYaw)/steps;
        }
    }
    @Override public String getCommandSenderName() {
        if(hasCustomNameTag()) return getCustomNameTag();
        return net.minecraft.util.StatCollector.translateToLocal(variant()==ADMIN ? "entity.hbm.colossus.admin.name" : variant()==GLITCHED ? "entity.hbm.colossus.glitched.name" : dormant() ? "entity.hbm.colossus.dormant.name" : "entity.hbm.prismarine_colossus.name");
    }
    public void evaluate(ColossusPose pose,float partial) {
        float hy=worldObj.isRemote ? previousHeadYaw+(headYaw()-previousHeadYaw)*partial : headYaw();
        float hp=worldObj.isRemote ? previousHeadPitch+(headPitch()-previousHeadPitch)*partial : headPitch();
        pose.evaluate(state(),stateTime(partial),walk(partial),moving(partial),hy,hp,ticksExisted+partial,reaction,dataWatcher.getWatchableObjectFloat(29));
    }
    @Override public void onLivingUpdate() {
        if(getHealth()<=0) return;
        previousWalk=walk; previousMoving=moving; previousHeadYaw=headYaw(); previousHeadPitch=headPitch();
        reaction*=0.8F;
        if(worldObj.isRemote) {
            interpolate();
            walk=dataWatcher.getWatchableObjectFloat(27); moving+=(dataWatcher.getWatchableObjectByte(28)-moving)*0.3F;
            if(clientState!=state()) { clientState=state(); reaction=0; }
            evaluate(skeleton,1); clientEffects(); return;
        }
        if(!registered) { if(!ColossusEncounters.get(worldObj).admit(getUniqueID())) { setDead(); return; } registered=true; }
        if(ticksExisted%20==1) { findTargets(); maintainParts(); }
        if(!targetable(target)) { target=null; dataWatcher.updateObject(26,0); }
        if(target!=null) aim();
        if(dormant()) {
            moving=0;
            if(ColossusConfig.proximityActivation && target instanceof EntityPlayer && getDistanceSqToEntity(target)<64) activate();
        } else {
            if(state()==ColossusPose.IDLE) {
                if(cooldown>0) cooldown--;
                pursue();
                if(!grips.isEmpty() && --shakeTimer<=0) { begin(ColossusPose.SHAKE); shakeTimer=280; }
                else if(target!=null && cooldown<=0 && ColossusConfig.attacks) chooseAttack();
            } else {
                moving=0; steeringTicks=0;
                int t=(int)stateTime(0);
                evaluate(skeleton,0);
                attackTick(t);
                if(variant()==GLITCHED && t==14 && state()!=ColossusPose.STUN && rand.nextInt(8)==0) begin(ColossusPose.STUN);
                if(t>=ColossusPose.duration(state())) { releaseGrab(); begin(ColossusPose.IDLE); cooldown=ColossusConfig.cooldown*(variant()==ADMIN?3:4)/4; if(variant()==GLITCHED) cooldown=20+rand.nextInt(Math.max(1,cooldown)); }
            }
            if(variant()==GLITCHED && state()==ColossusPose.IDLE && ticksExisted%173==0) begin(ColossusPose.STUN);
        }
        gravity();
        walk+=(float)Math.hypot(posX-prevPosX,posZ-prevPosZ);
        if(ticksExisted%3==0) dataWatcher.updateObject(27,walk);
        dataWatcher.updateObject(28,(byte)(moving>0.1F?1:0));
        evaluate(skeleton,0);
        for(EntityColossusPart p:parts) if(p!=null && !p.isDead) p.follow();
        updateAttachments(); terrain.tick();
        if(clockChanged && worldObj.getTotalWorldTime()-clockStarted>=200) restoreClock();
        if(ticksExisted%240==0 && !dormant()) sound("mob.irongolem.death",0.35F);
        if(moving>0.1F && Math.floor(walk*0.65/Math.PI)!=Math.floor(previousWalk*0.65/Math.PI)) {
            Vec3 foot=point(Math.sin(walk*0.65)>0?ColossusPose.RIGHT_FOOT:ColossusPose.LEFT_FOOT,0,-4,-0.5);
            impactEffect(foot,2); sound("step.stone",0.65F);
        }
        renderYawOffset=rotationYaw; rotationYawHead=rotationYaw;
        fallDistance=0;
    }
    private boolean targetable(Entity e) {
        if(!(e instanceof EntityLivingBase) || e==this || !e.isEntityAlive() || e.worldObj!=worldObj || e instanceof EntityPrismarineColossus) return false;
        if(e instanceof EntityPlayer && !ColossusConfig.creative && ((EntityPlayer)e).capabilities.isCreativeMode && variant()!=GLITCHED) return false;
        return getDistanceSqToEntity(e)<=ColossusConfig.range*ColossusConfig.range;
    }
    private void findTargets() {
        candidates.clear(); int inspected=0, reach=MathHelper.ceiling_float_int(ColossusConfig.range/16), side=reach*2+1;
        int cx=MathHelper.floor_double(posX)>>4, cz=MathHelper.floor_double(posZ)>>4;
        outer: for(int k=0;k<side*side;k++) {
            int index=Math.floorMod(k+searchOffset,side*side), x=cx+index%side-reach,z=cz+index/side-reach;
            if(!worldObj.getChunkProvider().chunkExists(x,z)) continue;
            Chunk chunk=worldObj.getChunkProvider().provideChunk(x,z);
            for(int s=0;s<16;s++) for(Object o:chunk.entityLists[(s+searchOffset)&15]) {
                if(++inspected>512 || candidates.size()>=64) break outer;
                Entity e=(Entity)o; if(targetable(e)) candidates.add((EntityLivingBase)e);
            }
        }
        searchOffset++;
        EntityLivingBase best=targetable(target)?target:null;
        double score=best==null?Double.MAX_VALUE:score(best);
        for(EntityLivingBase e:candidates) if(score(e)<score) { best=e; score=score(e); }
        target=best; dataWatcher.updateObject(26,best==null?0:best.getEntityId());
    }
    private double score(EntityLivingBase e) { return getDistanceSqToEntity(e)*(e instanceof EntityPlayer?0.25:1)*(e==getAITarget()?0.1:1); }
    private void aim() {
        double dx=target.posX-posX,dz=target.posZ-posZ;
        float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
        // Root yaw follows Minecraft's +Z heading; the skeleton itself faces -Z.
        if(!dormant() && state()==ColossusPose.IDLE && grips.isEmpty() && grabbed==null) rotationYaw+=MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired-rotationYaw),-2,2);
        float relative=MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired-rotationYaw),-55,55);
        float pitch=(float)Math.toDegrees(Math.atan2(target.posY+target.getEyeHeight()-(posY+17*scale()),Math.max(1,Math.hypot(dx,dz))));
        dataWatcher.updateObject(24,headYaw()+MathHelper.clamp_float(relative-headYaw(),-3,3));
        dataWatcher.updateObject(25,headPitch()+MathHelper.clamp_float(MathHelper.clamp_float(pitch,-48,20)-headPitch(),-2,2));
    }
    private void pursue() {
        moving=0;
        if(target==null || !grips.isEmpty() || getDistanceSqToEntity(target)<Math.pow(6*scale(),2)) return;
        double dx=target.posX-posX,dz=target.posZ-posZ;
        float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
        if(Math.abs(MathHelper.wrapAngleTo180_float(desired-rotationYaw))>35) return;
        if(--steeringTicks<=0) {
            steeringTicks=5; steeringX=steeringZ=0;
            double base=Math.toRadians(rotationYaw);
            double[] angles={0,0.6,-0.6,1.1,-1.1};
            for(double angle:angles) {
                double vx=-Math.sin(base+angle)*ColossusConfig.speed, vz=Math.cos(base+angle)*ColossusConfig.speed;
                if(clearBody(posX+vx*5,posY,posZ+vz*5)) { steeringX=vx; steeringZ=vz; blocked=0; break; }
                if(clearBody(posX+vx*5,posY+1,posZ+vz*5) && supported(posX+vx*5,posY+1,posZ+vz*5)) { setPosition(posX,posY+1,posZ); steeringX=vx; steeringZ=vz; blocked=0; break; }
            }
            if(steeringX==0 && steeringZ==0) blocked++;
        }
        if(steeringX!=0 || steeringZ!=0) { setPosition(posX+steeringX,posY,posZ+steeringZ); moving=1; }
        else if(blocked>=4 && cooldown==0 && ColossusConfig.terrain && ColossusConfig.attacks) { begin(target.posY>posY+7*scale()?ColossusPose.PUNCH:ColossusPose.SLAM); blocked=0; }
    }
    public static boolean loaded(World w,double x,double z,int radius) {
        return w.checkChunksExist(MathHelper.floor_double(x)-radius,0,MathHelper.floor_double(z)-radius,MathHelper.floor_double(x)+radius,255,MathHelper.floor_double(z)+radius);
    }
    public boolean clearBody(double x,double y,double z) {
        double s=scale();
        if(y<1 || y+20*s>255 || !loaded(worldObj,x,z,MathHelper.ceiling_double_int(7*s))) return false;
        // Three coarse anatomical volumes, evaluated only on steering refresh/spawn, never a 20-block vanilla mover.
        return clear(AxisAlignedBB.getBoundingBox(x-3*s,y+0.1,z-2*s,x+3*s,y+8*s,z+2*s))
            && clear(AxisAlignedBB.getBoundingBox(x-6*s,y+8*s,z-2*s,x+6*s,y+15*s,z+2*s))
            && clear(AxisAlignedBB.getBoundingBox(x-3*s,y+15*s,z-3*s,x+3*s,y+20*s,z+3*s));
    }
    private boolean ownedBox(AxisAlignedBB box) { for(EntityColossusPart p:parts) if(p!=null && p.boundingBox==box) return true; return false; }
    private boolean clear(AxisAlignedBB box) {
        for(Object o:worldObj.getCollidingBoundingBoxes(this,box)) if(!ownedBox((AxisAlignedBB)o)) return false;
        return true;
    }
    private boolean supported(double x,double y,double z) {
        double s=scale(); if(!loaded(worldObj,x,z,MathHelper.ceiling_double_int(3*s+1))) return false;
        int support=0;
        for(int a=-1;a<=1;a+=2) for(int b=-1;b<=1;b+=2) {
            int bx=MathHelper.floor_double(x+a*2*s), by=MathHelper.floor_double(y-0.05), bz=MathHelper.floor_double(z+b*s);
            AxisAlignedBB box=worldObj.getBlock(bx,by,bz).getCollisionBoundingBoxFromPool(worldObj,bx,by,bz);
            if(box!=null && box.maxY>=y-0.15) support++;
        }
        return support>=2;
    }
    private void gravity() {
        if(!loaded(worldObj,posX,posZ,4)) return;
        double s=scale();
        AxisAlignedBB feet=AxisAlignedBB.getBoundingBox(posX-2.7*s,posY,posZ-1.5*s,posX+2.7*s,posY+0.5,posZ+1.5*s);
        motionY=Math.max(-0.8,motionY-0.08);
        double fall=motionY;
        for(Object o:worldObj.getCollidingBoundingBoxes(this,feet.addCoord(0,fall,0))) if(!ownedBox((AxisAlignedBB)o)) fall=((AxisAlignedBB)o).calculateYOffset(feet,fall);
        setPosition(posX,posY+fall,posZ); onGround=fall!=motionY;
        if(onGround) motionY=0;
    }
    private void chooseAttack() {
        double d=Math.hypot(target.posX-posX,target.posZ-posZ)/scale();
        if(Math.abs(headYaw())>20) return;
        if(variant()==ADMIN && ColossusConfig.clock && !clockUsed) { clockUsed=true; begin(ColossusPose.CLOCK); return; }
        if(d>11) return;
        int choice=rand.nextInt(5);
        if(d<4) begin(choice==0?ColossusPose.GRAB:ColossusPose.STOMP);
        else if(target.posY>posY+8*scale()) begin(ColossusPose.PUNCH);
        else begin(choice==0?ColossusPose.GRAB:choice==1?ColossusPose.SWEEP:choice==2?ColossusPose.PUNCH:ColossusPose.SLAM);
    }
    public void begin(int next) {
        if(worldObj.isRemote) return;
        if(next!=ColossusPose.GRAB) releaseGrab();
        dataWatcher.updateObject(21,(byte)next); dataWatcher.updateObject(22,(int)worldObj.getTotalWorldTime());
        dataWatcher.updateObject(29,target==null?0F:(float)Math.max(0,(target.posY-posY)/scale()));
        struck.clear(); lastHand=null; moving=0;
        if(next!=ColossusPose.IDLE) sound("mob.irongolem.throw",0.6F);
    }
    private void attackTick(int t) {
        int state=state();
        if(state==ColossusPose.CLOCK && t==45) {
            impactEffect(point(ColossusPose.LEFT_HAND,0,-2,0),5); sound("ambient.weather.thunder",0.65F);
            if(ColossusConfig.clockTime && !worldObj.provider.hasNoSky) {
                clockOriginal=worldObj.getWorldTime(); clockStarted=worldObj.getTotalWorldTime();
                clockExpected=clockOriginal-Math.floorMod(clockOriginal,24000)+18000;
                worldObj.setWorldTime(clockExpected); clockChanged=!ColossusConfig.permanentTime;
            }
        }
        if(state==ColossusPose.SHAKE && t==35) {
            for(Grip g:new ArrayList<Grip>(grips.values())) if(!g.brace) release(g.player,true);
            return;
        }
        if(!ColossusConfig.attacks) { releaseGrab(); return; }
        if(state==ColossusPose.PUNCH && t>=24 && t<=32) movingHit(ColossusPose.RIGHT_HAND,1,1.6F,t%2==0);
        if(state==ColossusPose.SWEEP && t>=26 && t<=42) movingHit(ColossusPose.RIGHT_HAND,0.75F,1.3F,t%3==0);
        if(state==ColossusPose.SLAM && t==44) {
            for(int hand:new int[]{ColossusPose.LEFT_HAND,ColossusPose.RIGHT_HAND}) {
                Vec3 p=point(hand,0,-3,0); hitVolume(limbBox(hand),1.4F,2.2F); terrain.contact(limbBox(hand));
                impactEffect(p,4); shockwave(p,7*scale(),1.0F,2F);
            }
            sound("random.explode",0.6F);
        }
        if(state==ColossusPose.STOMP && t==31) {
            Vec3 p=point(ColossusPose.LEFT_FOOT,0,-4,-0.5);
            AxisAlignedBB foot=limbBox(ColossusPose.LEFT_FOOT); hitVolume(foot,1.3F,2.4F);
            terrain.contact(foot.expand(0,0.8,0)); shockwave(p,6*scale(),0.8F,1.8F); impactEffect(p,4); sound("random.explode",0.7F);
        }
        if(state==ColossusPose.GRAB) {
            if(t>=24 && t<=35 && grabbed==null) {
                AxisAlignedBB hand=limbBox(ColossusPose.RIGHT_HAND);
                for(EntityLivingBase e:candidates) if(targetable(e) && !grips.containsKey(e.getUniqueID()) && e.ridingEntity==null && hand.intersectsWith(e.boundingBox)) {
                    grabbed=e; grabEscape=0; if(e instanceof EntityPlayer) ((EntityPlayer)e).addChatMessage(new ChatComponentTranslation("colossus.grab")); break;
                }
            }
            if(grabbed!=null) {
                if(!targetable(grabbed)) { releaseGrab(); return; }
                if(grabbed instanceof EntityPlayer && grabbed.isSneaking()) grabEscape++; else grabEscape=Math.max(0,grabEscape-1);
                if(grabEscape>=8) { releaseGrab(); begin(ColossusPose.STUN); return; }
                if(t==86) { grabbed.attackEntityFrom(DamageSource.causeMobDamage(this),ColossusConfig.damage*2.5F); releaseGrab(); }
            }
        }
    }
    private void movingHit(int limb,float damage,float force,boolean destroy) {
        AxisAlignedBB now=limbBox(limb), swept=now;
        if(lastHand!=null) swept=AxisAlignedBB.getBoundingBox(Math.min(now.minX,lastHand.minX),Math.min(now.minY,lastHand.minY),Math.min(now.minZ,lastHand.minZ),Math.max(now.maxX,lastHand.maxX),Math.max(now.maxY,lastHand.maxY),Math.max(now.maxZ,lastHand.maxZ));
        hitVolume(swept,damage,force); if(destroy) terrain.contact(swept);
        lastHand=now;
    }
    private void hitVolume(AxisAlignedBB box,float multiplier,float force) {
        for(EntityLivingBase e:candidates) if(targetable(e) && !grips.containsKey(e.getUniqueID()) && e!=grabbed && box.intersectsWith(e.boundingBox) && struck.add(e.getUniqueID())) {
            e.attackEntityFrom(DamageSource.causeMobDamage(this),ColossusConfig.damage*multiplier);
            double dx=e.posX-posX,dz=e.posZ-posZ, length=Math.max(0.1,Math.hypot(dx,dz));
            e.addVelocity(dx/length*force,0.65,dz/length*force); e.velocityChanged=true; impactEffect(Vec3.createVectorHelper(e.posX,e.posY,e.posZ),2);
        }
    }
    private void shockwave(Vec3 p,double radius,float damage,float force) {
        for(EntityLivingBase e:candidates) {
            double dx=e.posX-p.xCoord,dz=e.posZ-p.zCoord;
            if(dx*dx+dz*dz<=radius*radius && Math.abs(e.posY-p.yCoord)<2.5*scale() && targetable(e) && e!=grabbed && !grips.containsKey(e.getUniqueID()) && struck.add(e.getUniqueID())) {
                e.attackEntityFrom(DamageSource.causeMobDamage(this),ColossusConfig.damage*damage);
                double length=Math.max(0.1,Math.hypot(dx,dz));e.addVelocity(dx/length*force,0.65,dz/length*force);e.velocityChanged=true;
            }
        }
    }
    public Vec3 point(int bone,double x,double y,double z) { return toWorld(skeleton.point(bone,x,y,z)); }
    public Vec3 toWorld(Vec3 local) {
        // Rotate local -Z front into vanilla +Z at yaw zero.
        double yaw=Math.toRadians(180-rotationYaw), c=Math.cos(yaw),s=Math.sin(yaw),scale=scale();
        return Vec3.createVectorHelper(posX+(local.xCoord*c+local.zCoord*s)*scale,posY+local.yCoord*scale,posZ+(-local.xCoord*s+local.zCoord*c)*scale);
    }
    public Vec3 fromWorld(Vec3 world,int bone) {
        double yaw=Math.toRadians(180-rotationYaw),c=Math.cos(yaw),s=Math.sin(yaw),scale=scale();
        double dx=(world.xCoord-posX)/scale,dz=(world.zCoord-posZ)/scale;
        double x=dx*c-dz*s,y=(world.yCoord-posY)/scale,z=dx*s+dz*c;
        double[] m=skeleton.bones[bone].matrix; x-=m[12];y-=m[13];z-=m[14];
        return Vec3.createVectorHelper(m[0]*x+m[1]*y+m[2]*z,m[4]*x+m[5]*y+m[6]*z,m[8]*x+m[9]*y+m[10]*z);
    }
    public AxisAlignedBB boneBox(int bone,double x,double y,double z,double hx,double hy,double hz) {
        double minX=Double.MAX_VALUE,minY=minX,minZ=minX,maxX=-minX,maxY=maxX,maxZ=maxX;
        for(int i=0;i<8;i++) {
            Vec3 v=point(bone,x+((i&1)==0?-hx:hx),y+((i&2)==0?-hy:hy),z+((i&4)==0?-hz:hz));
            minX=Math.min(minX,v.xCoord);minY=Math.min(minY,v.yCoord);minZ=Math.min(minZ,v.zCoord);
            maxX=Math.max(maxX,v.xCoord);maxY=Math.max(maxY,v.yCoord);maxZ=Math.max(maxZ,v.zCoord);
        }
        return AxisAlignedBB.getBoundingBox(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public AxisAlignedBB limbBox(int bone) { return boneBox(bone,0,bone==ColossusPose.LEFT_FOOT?-3:-3.0,bone==ColossusPose.LEFT_FOOT?-0.5:0,1.65,1.1,1.8); }
    public AxisAlignedBB partBox(int slot) {
        switch(slot) {
            case 0: return boneBox(ColossusPose.TORSO,0,3.5,0,4,3.5,2);
            case 1: return boneBox(ColossusPose.HEAD,0,2.5,0,3,2.5,2.5);
            case 2: return boneBox(ColossusPose.HEAD,0,2.7,2.65,0.95,0.85,0.12);
            case 3: return limbBox(ColossusPose.LEFT_HAND);
            case 4: return limbBox(ColossusPose.RIGHT_HAND);
            case 5: return boneBox(ColossusPose.LEFT_LEG,0,-2,0,1.6,2,1.6);
            case 6: return boneBox(ColossusPose.RIGHT_LEG,0,-2,0,1.6,2,1.6);
            default: return boneBox(ColossusPose.ROOT,0,1.8,0,4,1.8,2.7);
        }
    }
    private void maintainParts() {
        for(int i=0;i<parts.length;i++) if(parts[i]==null || parts[i].isDead || !parts[i].addedToChunk) {
            if(parts[i]!=null) parts[i].setDead();
            EntityColossusPart part=new EntityColossusPart(worldObj); part.attach(this,i);
            if(loaded(worldObj,part.posX,part.posZ,1) && worldObj.spawnEntityInWorld(part)) parts[i]=part;
        }
    }
    @Override public boolean attackEntityFrom(DamageSource source,float damage) { return hit(-1,source,damage); }
    public boolean hit(int slot,DamageSource source,float damage) {
        if(worldObj.isRemote || !isEntityAlive() || !Float.isFinite(damage) || damage<=0) return false;
        activate();
        Entity actor=source.getEntity(); if(targetable(actor)) { target=(EntityLivingBase)actor; dataWatcher.updateObject(26,actor.getEntityId()); }
        evaluate(skeleton,0);
        if(weakHit(source)) {
            boolean gauntlet=actor instanceof EntityPlayer && ((EntityPlayer)actor).getHeldItem()!=null && ((EntityPlayer)actor).getHeldItem().getItem()==ColossusContent.gauntlet && source.getSourceOfDamage()==actor;
            if(ColossusConfig.gauntletRequired && !gauntlet) { sound("random.orb",0.5F); return false; }
            reaction=1; dataWatcher.updateObject(30,(int)worldObj.getTotalWorldTime()+20); impactEffect(point(ColossusPose.HEAD,0,2.7,2.7),6); sound("dig.glass",0.5F);
            return super.attackEntityFrom(source,gauntlet?2048:damage*ColossusConfig.weakMultiplier);
        }
        if(source==DamageSource.outOfWorld) return super.attackEntityFrom(source,damage);
        if(hurtResistantTime>0) return false;
        reaction=1; stagger+=Math.min(50,(int)damage);
        if(stagger>=60 && state()!=ColossusPose.STUN) { stagger=0; begin(ColossusPose.STUN); cooldown=80; }
        sound("dig.stone",0.55F);
        float reduced=Math.min(damage*0.01F,2F);
        if(!ColossusConfig.bypassArmor) reduced=Math.min(reduced,Math.max(0,getHealth()-1));
        if(reduced<=0) return false;
        boolean hit=super.attackEntityFrom(source,reduced); hurtResistantTime=8; return hit;
    }
    private boolean weakHit(DamageSource source) {
        if(source.isExplosion() || source.isMagicDamage() || source.isFireDamage()) return false;
        Entity direct=source.getSourceOfDamage(),actor=source.getEntity(); if(direct==null) return false;
        Vec3 from,to;
        if(direct==actor && actor instanceof EntityLivingBase) {
            from=Vec3.createVectorHelper(actor.posX,actor.posY+actor.getEyeHeight(),actor.posZ);
            Vec3 look=actor.getLookVec(); if(look==null) return false;
            double reach=actor instanceof EntityPlayer && ((EntityPlayer)actor).capabilities.isCreativeMode?6:4.5;
            to=from.addVector(look.xCoord*reach,look.yCoord*reach,look.zCoord*reach);
        } else {
            from=Vec3.createVectorHelper(direct.posX-direct.motionX,direct.posY-direct.motionY,direct.posZ-direct.motionZ);
            to=Vec3.createVectorHelper(direct.posX+direct.motionX,direct.posY+direct.motionY,direct.posZ+direct.motionZ);
        }
        Vec3 a=fromWorld(from,ColossusPose.HEAD),b=fromWorld(to,ColossusPose.HEAD);
        if(a.zCoord<=2.64 || b.zCoord>=a.zCoord) return false;
        double t=(2.65-a.zCoord)/(b.zCoord-a.zCoord); if(t<0 || t>1) return false;
        double x=a.xCoord+(b.xCoord-a.xCoord)*t,y=a.yCoord+(b.yCoord-a.yCoord)*t;
        if(Math.abs(x)>0.95 || Math.abs(y-2.7)>0.85) return false;
        Vec3 contact=point(ColossusPose.HEAD,x,y,2.65);
        MovingObjectPosition obstruction=worldObj.rayTraceBlocks(from,contact);
        return obstruction==null;
    }
    @Override protected boolean interact(EntityPlayer p) {
        if(worldObj.isRemote) return true;
        if(p.isSneaking() && dormant()) { activate(); return true; }
        if(!ColossusConfig.climbing || !isEntityAlive() || !p.isEntityAlive() || p.ridingEntity!=null || grips.size()>=4 || p==grabbed || p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        if(grips.containsKey(p.getUniqueID())) return true;
        Vec3 local=fromWorld(Vec3.createVectorHelper(p.posX,p.posY,p.posZ),ColossusPose.ROOT);
        if(Math.abs(local.xCoord)>5.8 || Math.abs(local.xCoord)<2 || local.zCoord<1 || local.zCoord>4 || local.yCoord>4.5 || local.yCoord< -1) return false;
        Grip g=new Grip(p,local.xCoord<0?-1:1); grips.put(p.getUniqueID(),g); p.addChatMessage(new ChatComponentTranslation("colossus.climb")); return true;
    }
    public void control(EntityPlayerMP p,int vertical,boolean brace,boolean release) {
        if(grabbed==p) { if(release) grabEscape+=2; return; }
        Grip g=grips.get(p.getUniqueID()); if(g==null) return;
        g.input=MathHelper.clamp_int(vertical,-1,1); g.brace=brace; g.inputTick=worldObj.getTotalWorldTime();
        if(release) release(p,false);
    }
    public Vec3 climbPoint(float progress,int side) {
        // Continuous travel between leg, hip, torso, shoulder and rear-skull holds.
        float q=MathHelper.clamp_float(progress,0,4); int node=Math.min(3,(int)q); float f=q-node;
        int[] bones={side<0?ColossusPose.RIGHT_LEG:ColossusPose.LEFT_LEG,ColossusPose.TORSO,ColossusPose.TORSO,ColossusPose.HEAD,ColossusPose.HEAD};
        double[][] offsets={{2.2,-6.8,2.1},{3,-1,2.6},{3,4.5,2.6},{2,0,3.3},{0,1.5,3.8}};
        Vec3 a=point(bones[node],offsets[node][0]*side,offsets[node][1],offsets[node][2]);
        Vec3 b=point(bones[node+1],offsets[node+1][0]*side,offsets[node+1][1],offsets[node+1][2]);
        return Vec3.createVectorHelper(a.xCoord+(b.xCoord-a.xCoord)*f,a.yCoord+(b.yCoord-a.yCoord)*f,a.zCoord+(b.zCoord-a.zCoord)*f);
    }
    private void updateAttachments() {
        Iterator<Grip> it=grips.values().iterator();
        while(it.hasNext()) {
            Grip g=it.next(); EntityPlayer p=g.player;
            if(p.isDead || !p.isEntityAlive() || p.worldObj!=worldObj || p.getDistanceSqToEntity(this)>Math.pow(30*scale(),2)) { detach(p); it.remove(); continue; }
            if(worldObj.getTotalWorldTime()-g.inputTick>10) { g.input=0; g.brace=false; }
            g.progress=MathHelper.clamp_float(g.progress+g.input*0.014F,0,4);
            Vec3 anchor=climbPoint(g.progress,g.side);
            if(!placeAttached(p,anchor)) { detach(p); it.remove(); continue; }
            if(ticksExisted%4==0) syncGrip(p,g.progress,false,g.side);
        }
        if(grabbed!=null) {
            Vec3 anchor=point(ColossusPose.RIGHT_HAND,0,-3,-0.8);
            if(!placeAttached(grabbed,anchor)) releaseGrab();
            else if(ticksExisted%4==0) syncGrip(grabbed,0,true,1);
        }
    }
    private boolean placeAttached(Entity e,Vec3 p) {
        if(!loaded(worldObj,p.xCoord,p.zCoord,1)) return false;
        AxisAlignedBB box=e.boundingBox.copy().offset(p.xCoord-e.posX,p.yCoord-e.posY,p.zCoord-e.posZ);
        if(!clear(box)) return false;
        e.motionX=e.motionY=e.motionZ=0; e.fallDistance=0;
        if(e instanceof EntityPlayerMP && ((EntityPlayerMP)e).playerNetServerHandler!=null && ticksExisted%4==0) ((EntityPlayerMP)e).playerNetServerHandler.setPlayerLocation(p.xCoord,p.yCoord,p.zCoord,e.rotationYaw,e.rotationPitch);
        else e.setPosition(p.xCoord,p.yCoord,p.zCoord);
        return true;
    }
    private void syncGrip(Entity e,float progress,boolean grabbed,int side) {
        PacketDispatcher.wrapper.sendToAllAround(new ColossusAttachmentPacket(getEntityId(),e.getEntityId(),progress,grabbed,side),new TargetPoint(dimension,posX,posY,posZ,128));
    }
    private void detach(Entity e) {
        e.fallDistance=0;
        if(e instanceof EntityLivingBase && e.isEntityAlive()) ((EntityLivingBase)e).addPotionEffect(new PotionEffect(Potion.resistance.id,80,4));
        if(e instanceof EntityPlayer) e.getEntityData().setInteger("RNTColossusFallGrace",100);
        PacketDispatcher.wrapper.sendToAllAround(new ColossusAttachmentPacket(0,e.getEntityId(),0,false,1),new TargetPoint(dimension,posX,posY,posZ,128));
    }
    private void release(EntityPlayer p,boolean shaken) {
        grips.remove(p.getUniqueID()); detach(p);
        if(shaken) { double yaw=Math.toRadians(rotationYaw); p.addVelocity(Math.sin(yaw)*0.6,0.4,-Math.cos(yaw)*0.6); p.velocityChanged=true; }
    }
    private void releaseGrab() { if(grabbed!=null) { detach(grabbed); grabbed=null; } }
    public void releaseAll() { releaseGrab(); for(Grip g:grips.values()) detach(g.player); grips.clear(); }
    public boolean holds(EntityPlayer p) { return grabbed==p || grips.containsKey(p.getUniqueID()); }
    public void unload() { releaseAll(); restoreClock(); terrain.clear(); for(EntityColossusPart p:parts) if(p!=null) p.setDead(); }
    public void restoreClock() {
        if(!clockChanged || worldObj.isRemote) return;
        long elapsed=worldObj.getTotalWorldTime()-clockStarted;
        long advance=worldObj.getGameRules().getGameRuleBooleanValue("doDaylightCycle")?elapsed:0;
        if(Math.abs(worldObj.getWorldTime()-(clockExpected+advance))<=5) worldObj.setWorldTime(clockOriginal+advance);
        clockChanged=false;
    }
    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if(!dead) return;
        releaseAll(); terrain.clear(); begin(ColossusPose.DEATH); restoreClock(); sound("mob.irongolem.death",0.5F);
    }
    @Override protected void dropFewItems(boolean player,int looting) { }
    @Override protected void onDeathUpdate() {
        if(state()!=ColossusPose.DEATH && !worldObj.isRemote) begin(ColossusPose.DEATH);
        evaluate(skeleton,0);
        if(worldObj.isRemote) { interpolate();clientEffects(); return; }
        for(EntityColossusPart p:parts) if(p!=null) p.setDead();
        int t=(int)stateTime(0);
        if(t==50 || t==110) { impactEffect(point(ColossusPose.TORSO,0,3,0),6); sound("random.explode",0.65F); }
        if(t>=140) {
            if(!deathDrops && dropShards && variant()!=GLITCHED && worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) { deathDrops=true; entityDropItem(new ItemStack(ColossusContent.shard,16+rand.nextInt(17)),0); }
            setDead();
        }
    }
    @Override public void setDead() {
        if(!worldObj.isRemote) { unload(); if(registered) ColossusEncounters.get(worldObj).remove(getUniqueID()); }
        super.setDead();
    }
    private void sound(String name,float pitch) { if(ColossusConfig.volume>0) playSound(name,ColossusConfig.volume,pitch); }
    private void impactEffect(Vec3 p,int strength) {
        if(worldObj.isRemote) return;
        // Vanilla entity status packets carry a small effect code, not per-particle network traffic.
        worldObj.setEntityState(this,(byte)(strength>=5?43:strength>=4?42:41));
    }
    @Override public void handleHealthUpdate(byte code) {
        if(code==2) reaction=1;
        if(code>=41 && code<=43) { burst(code==43?ColossusPose.HEAD:ColossusPose.ROOT,code==43?17:0.4); return; }
        super.handleHealthUpdate(code);
    }
    private void clientEffects() {
        if(variant()==GLITCHED && ticksExisted%4==0) burst(ColossusPose.TORSO,3);
        if(state()==ColossusPose.DEATH && ticksExisted%3==0) burst(ColossusPose.TORSO,3);
    }
    private void burst(int bone,double y) {
        for(int i=0;i<ColossusConfig.particles;i++) {
            Vec3 p=bone==ColossusPose.HEAD?point(bone,0,2.7,2.7):point(bone,(rand.nextDouble()-0.5)*7,y,(rand.nextDouble()-0.5)*4);
            String particle=variant()==GLITCHED && i%3==0?"portal":i%2==0?"blockcrack_"+Block.getIdFromBlock(Blocks.stone)+"_0":"iconcrack_"+net.minecraft.item.Item.getIdFromItem(ColossusContent.shard);
            worldObj.spawnParticle(particle,p.xCoord,p.yCoord,p.zCoord,(rand.nextDouble()-0.5)*0.5,rand.nextDouble()*0.5,(rand.nextDouble()-0.5)*0.5);
        }
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n); n.setInteger("Variant",variant()); n.setFloat("Scale",scale()); n.setBoolean("Registered",registered);
        n.setInteger("State",state()); n.setInteger("StateTime",(int)stateTime(0)); n.setInteger("Cooldown",cooldown); n.setFloat("Walk",walk);
        n.setBoolean("ClockUsed",clockUsed); n.setBoolean("ClockChanged",clockChanged); n.setLong("ClockOriginal",clockOriginal); n.setLong("ClockStarted",clockStarted); n.setLong("ClockExpected",clockExpected);
        n.setBoolean("DropShards",dropShards); n.setBoolean("DeathDrops",deathDrops);
    }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n); setVariant(n.getInteger("Variant"));
        float saved=n.getFloat("Scale"); dataWatcher.updateObject(23,Float.isFinite(saved)?MathHelper.clamp_float(saved,0.5F,2):1F);
        registered=false; cooldown=MathHelper.clamp_int(n.getInteger("Cooldown"),20,1200);
        int savedState=MathHelper.clamp_int(n.getInteger("State"),0,9);
        // Transient grips never persist; interrupted attacks reload kneeling rather than duplicating their impact.
        begin(getHealth()<=0?ColossusPose.DEATH:dormant() || savedState==ColossusPose.IDLE?ColossusPose.IDLE:ColossusPose.STUN);
        if(getHealth()<=0) dataWatcher.updateObject(22,(int)worldObj.getTotalWorldTime()-MathHelper.clamp_int(n.getInteger("StateTime"),0,139));
        clockUsed=n.getBoolean("ClockUsed"); clockChanged=n.getBoolean("ClockChanged"); clockOriginal=n.getLong("ClockOriginal"); clockStarted=n.getLong("ClockStarted"); clockExpected=n.getLong("ClockExpected");
        dropShards=!n.hasKey("DropShards") || n.getBoolean("DropShards"); deathDrops=n.getBoolean("DeathDrops");
    }
    private static final class Grip {
        final EntityPlayer player;
        final int side;
        float progress;
        int input;
        boolean brace;
        long inputTick;
        Grip(EntityPlayer p,int side) { player=p;this.side=side; }
    }
}
