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
    private EntityLivingBase target, grabTarget, grabbed;
    private Vec3 grabAnchor;
    private int cooldown=60, searchOffset, blocked, steeringTicks, grabEscape, stagger, shakeTimer=280;
    private int grabSequence, grabPressTick;
    private boolean grabKeyDown, grabSpent;
    private float aimX, aimY, aimZ, previousAimX, previousAimY, previousAimZ;
    private float walk, previousWalk, moving, previousMoving, previousHeadYaw, previousHeadPitch;
    private double steeringX,steeringZ;
    private double stepY=Double.NaN;
    private static final double[] STEERING_ANGLES={0,0.6,-0.6,1.1,-1.1};
    // The first two volumes are the soles and shins. Their lower faces also determine ground contact.
    private static final double[][] WALK_BODY={
        {-4,0,-3.65,-0.25,4.75,2.125}, {0.25,0,-3.65,4,4.75,2.125},
        {-4.1,4.75,-2.4,4.1,9.5,2.4}, {-6.75,4.25,-2.75,6.75,15.75,2.75},
        {-4.45,13.25,-4.45,4.45,21.125,4.45}
    };
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
        dataWatcher.addObject(16,0F); dataWatcher.addObject(17,0F); dataWatcher.addObject(18,0F);
        dataWatcher.addObject(19,(byte)-1);
        dataWatcher.addObject(20,(byte)ACTIVE); dataWatcher.addObject(21,(byte)ColossusPose.IDLE);
        dataWatcher.addObject(22,0); dataWatcher.addObject(23,1F);
        dataWatcher.addObject(24,0F); dataWatcher.addObject(25,0F); dataWatcher.addObject(26,0);
        dataWatcher.addObject(27,0F); dataWatcher.addObject(28,(byte)0);
        dataWatcher.addObject(29,0F);
        dataWatcher.addObject(30,0); dataWatcher.addObject(31,0);
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
    private int grabSide() { return dataWatcher.getWatchableObjectByte(19); }
    private int grabHand() { return grabSide()<0?ColossusPose.RIGHT_HAND:ColossusPose.LEFT_HAND; }
    private int captureTime() { return dataWatcher.getWatchableObjectInt(31); }
    private int escapeTime() { return dataWatcher.getWatchableObjectInt(26); }
    public int grabToken() { return dataWatcher.getWatchableObjectInt(22); }
    public int grabTicksLeft() {
        return state()==ColossusPose.GRAB && captureTime()>0 && escapeTime()==0
            ? Math.max(0,captureTime()+ColossusPose.ESCAPE_TICKS-(int)stateTime(0)) : 0;
    }
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
        evaluateAt(pose,stateTime(partial),partial);
    }
    private void evaluateAt(ColossusPose pose,double time,float partial) {
        float hy=worldObj.isRemote ? previousHeadYaw+(headYaw()-previousHeadYaw)*partial : headYaw();
        float hp=worldObj.isRemote ? previousHeadPitch+(headPitch()-previousHeadPitch)*partial : headPitch();
        pose.evaluate(state(),time,walk(partial),moving(partial),hy,hp,ticksExisted+partial,reaction,dataWatcher.getWatchableObjectFloat(29),
            grabSide(),worldObj.isRemote?previousAimX+(aimX-previousAimX)*partial:aimX,
            worldObj.isRemote?previousAimY+(aimY-previousAimY)*partial:aimY,
            worldObj.isRemote?previousAimZ+(aimZ-previousAimZ)*partial:aimZ,captureTime(),escapeTime());
    }
    @Override public void onLivingUpdate() {
        if(getHealth()<=0) return;
        previousWalk=walk; previousMoving=moving; previousHeadYaw=headYaw(); previousHeadPitch=headPitch();
        reaction*=0.8F;
        if(worldObj.isRemote) {
            interpolate();
            walk=dataWatcher.getWatchableObjectFloat(27); moving+=(dataWatcher.getWatchableObjectByte(28)-moving)*0.3F;
            boolean changed=clientState!=state();
            if(changed) { clientState=state(); reaction=0; }
            previousAimX=aimX; previousAimY=aimY; previousAimZ=aimZ;
            float blend=changed || stateTime(0)>=ColossusPose.GRAB_START?1:0.55F;
            aimX+=(dataWatcher.getWatchableObjectFloat(16)-aimX)*blend;
            aimY+=(dataWatcher.getWatchableObjectFloat(17)-aimY)*blend;
            aimZ+=(dataWatcher.getWatchableObjectFloat(18)-aimZ)*blend;
            evaluate(skeleton,1); clientEffects(); return;
        }
        if(!registered) { if(!ColossusEncounters.get(worldObj).admit(getUniqueID())) { setDead(); return; } registered=true; }
        if(ticksExisted%20==1) { findTargets(); maintainParts(); }
        if(!targetable(target)) target=null;
        if(target!=null && (state()!=ColossusPose.GRAB || stateTime(0)<=ColossusPose.GRAB_LOCK)) aim();
        if(dormant()) {
            moving=0; stepY=Double.NaN;
            if(ColossusConfig.proximityActivation && target instanceof EntityPlayer && getDistanceSqToEntity(target)<64) activate();
        } else {
            if(state()==ColossusPose.IDLE) {
                if(cooldown>0) cooldown--;
                pursue();
                if(!grips.isEmpty() && --shakeTimer<=0) { begin(ColossusPose.SHAKE); shakeTimer=280; }
                else if(target!=null && cooldown<=0 && ColossusConfig.attacks && Double.isNaN(stepY)) chooseAttack();
            } else {
                moving=0; steeringTicks=0;
                int t=(int)stateTime(0);
                if(state()==ColossusPose.GRAB && t<=ColossusPose.GRAB_LOCK && targetable(grabTarget)) updateGrabAim(true);
                evaluate(skeleton,0);
                attackTick(t);
                if(variant()==GLITCHED && t==14 && state()!=ColossusPose.STUN && rand.nextInt(8)==0) begin(ColossusPose.STUN);
                if(t>=attackDuration()) { begin(ColossusPose.IDLE); cooldown=ColossusConfig.cooldown*(variant()==ADMIN?3:4)/4; if(variant()==GLITCHED) cooldown=20+rand.nextInt(Math.max(1,cooldown)); }
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
            Vec3 foot=point(Math.sin(walk*0.65)>0?ColossusPose.RIGHT_FOOT:ColossusPose.LEFT_FOOT,0,-ColossusPose.SOLE,-0.5);
            impactEffect(foot,2); sound("step.stone",0.65F);
        }
        renderYawOffset=rotationYaw; rotationYawHead=rotationYaw;
        fallDistance=0;
    }
    private boolean targetable(Entity e) {
        return eligible(e) && getDistanceSqToEntity(e)<=ColossusConfig.range*ColossusConfig.range;
    }
    private boolean eligible(Entity e) {
        if(!(e instanceof EntityLivingBase) || e==this || !e.isEntityAlive() || e.worldObj!=worldObj || e instanceof EntityPrismarineColossus) return false;
        return !(e instanceof EntityPlayer) || ColossusConfig.creative || !((EntityPlayer)e).capabilities.isCreativeMode || variant()==GLITCHED;
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
        target=best;
    }
    private double score(EntityLivingBase e) { return getDistanceSqToEntity(e)*(e instanceof EntityPlayer?0.25:1)*(e==getAITarget()?0.1:1); }
    private void aim() {
        double dx=target.posX-posX,dz=target.posZ-posZ;
        float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
        // Root yaw follows Minecraft's +Z heading; the skeleton itself faces -Z.
        if(!dormant() && state()==ColossusPose.IDLE && grips.isEmpty() && Double.isNaN(stepY)) turnToward(desired,2);
        float relative=MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired-rotationYaw),-42,42);
        float pitch=(float)Math.toDegrees(Math.atan2(target.posY+target.getEyeHeight()-(posY+18*scale()),Math.max(1,Math.hypot(dx,dz))));
        dataWatcher.updateObject(24,headYaw()+MathHelper.clamp_float(relative-headYaw(),-3,3));
        dataWatcher.updateObject(25,headPitch()+MathHelper.clamp_float(MathHelper.clamp_float(pitch,-25,16)-headPitch(),-2,2));
    }
    private void turnToward(float yaw,float speed) {
        float next=rotationYaw+MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw-rotationYaw),-speed,speed);
        if(next!=rotationYaw && clearMove(walkBody(posX,posY,posZ,next),0,0,0)) rotationYaw=next;
    }
    private void pursue() {
        moving=0;
        if(!Double.isNaN(stepY)) {
            if(advanceStep()) return;
            stepY=Double.NaN;
            steeringTicks=0;
        }
        if(target==null || !grips.isEmpty() || getDistanceSqToEntity(target)<Math.pow(6*scale(),2)) return;
        double dx=target.posX-posX,dz=target.posZ-posZ;
        float desired=(float)Math.toDegrees(Math.atan2(-dx,dz));
        if(Math.abs(MathHelper.wrapAngleTo180_float(desired-rotationYaw))>35) return;
        AxisAlignedBB[] body=walkBody(posX,posY,posZ,rotationYaw);
        if(--steeringTicks>0 && (steeringX!=0 || steeringZ!=0) && tryMove(body,steeringX,steeringZ)) return;
        double base=Math.toRadians(rotationYaw);
        for(double angle:STEERING_ANGLES) {
            double vx=-Math.sin(base+angle)*ColossusConfig.speed, vz=Math.cos(base+angle)*ColossusConfig.speed;
            if(tryMove(body,vx,vz)) { steeringTicks=5; blocked=0; return; }
        }
        if(++blocked>=20 && cooldown==0 && ColossusConfig.terrain && ColossusConfig.attacks) {
            begin(target.posY>posY+7*scale()?ColossusPose.PUNCH:ColossusPose.SLAM);
            blocked=0;
        }
    }
    private boolean tryMove(AxisAlignedBB[] body,double dx,double dz) {
        if(clearMove(body,dx,0,dz)) {
            setPosition(posX+dx,posY,posZ+dz);
            steeringX=dx; steeringZ=dz; moving=1;
            return true;
        }
        if(!supported(body)) return false;
        double top=terrain.ledgeHeight(body[0],dx,dz,posY);
        top=terrain.ledgeHeight(body[1],dx,dz,top);
        double rise=top-posY;
        if(rise<=1E-6 || rise>1.000001 || !clearMove(body,0,rise,0)) return false;
        AxisAlignedBB[] elevated=walkBody(posX,top,posZ,rotationYaw);
        if(!clearMove(elevated,dx,0,dz) || !supported(walkBody(posX+dx,top,posZ+dz,rotationYaw))) return false;
        steeringX=dx; steeringZ=dz; stepY=top;
        return advanceStep();
    }
    private boolean advanceStep() {
        double rise=stepY-posY;
        if(!clearMove(walkBody(posX,posY,posZ,rotationYaw),0,rise,0)
            || !clearMove(walkBody(posX,stepY,posZ,rotationYaw),steeringX,0,steeringZ)
            || !supported(walkBody(posX+steeringX,stepY,posZ+steeringZ,rotationYaw))) return false;
        motionY=0;
        moving=1;
        if(rise>0.24) setPosition(posX,posY+0.24,posZ);
        else {
            setPosition(posX+steeringX,stepY,posZ+steeringZ);
            stepY=Double.NaN;
        }
        return true;
    }
    public static boolean loaded(World w,double x,double z,int radius) {
        return w.checkChunksExist(MathHelper.floor_double(x)-radius,0,MathHelper.floor_double(z)-radius,MathHelper.floor_double(x)+radius,255,MathHelper.floor_double(z)+radius);
    }
    public boolean clearBody(double x,double y,double z) {
        return clearMove(walkBody(x,y,z,rotationYaw),0,0,0);
    }
    private AxisAlignedBB[] walkBody(double x,double y,double z,float heading) {
        double angle=Math.toRadians(180-heading), s=scale(), c=Math.cos(angle), sin=Math.sin(angle);
        AxisAlignedBB[] body=new AxisAlignedBB[WALK_BODY.length];
        for(int i=0;i<body.length;i++) {
            double[] b=WALK_BODY[i];
            double cx=x+((b[0]+b[3])*c+(b[2]+b[5])*sin)*s*0.5;
            double cz=z+(-(b[0]+b[3])*sin+(b[2]+b[5])*c)*s*0.5;
            double hx=((b[3]-b[0])*Math.abs(c)+(b[5]-b[2])*Math.abs(sin))*s*0.5;
            double hz=((b[3]-b[0])*Math.abs(sin)+(b[5]-b[2])*Math.abs(c))*s*0.5;
            body[i]=AxisAlignedBB.getBoundingBox(cx-hx,y+b[1]*s,cz-hz,cx+hx,y+b[4]*s,cz+hz);
        }
        return body;
    }
    private boolean clearMove(AxisAlignedBB[] body,double dx,double dy,double dz) {
        for(AxisAlignedBB box:body) {
            AxisAlignedBB sweep=box.addCoord(dx,dy,dz);
            if(sweep.minY<1 || sweep.maxY>256 || !terrain.clearBlocks(sweep)) return false;
        }
        return true;
    }
    private boolean ownedBox(AxisAlignedBB box) { for(EntityColossusPart p:parts) if(p!=null && p.boundingBox==box) return true; return false; }
    private boolean clear(AxisAlignedBB box) {
        for(Object o:worldObj.getCollidingBoundingBoxes(this,box)) if(!ownedBox((AxisAlignedBB)o)) return false;
        return true;
    }
    private boolean supported(double x,double y,double z) {
        return supported(walkBody(x,y,z,rotationYaw));
    }
    private boolean supported(AxisAlignedBB[] body) {
        for(int i=0;i<2;i++) {
            AxisAlignedBB foot=body[i];
            if(terrain.loaded(foot) && terrain.verticalClearance(foot,-0.05)>-0.05) return true;
        }
        return false;
    }
    private void gravity() {
        if(!Double.isNaN(stepY)) return;
        motionY=Math.max(-0.8,motionY-0.08);
        double fall=motionY;
        for(AxisAlignedBB box:walkBody(posX,posY,posZ,rotationYaw)) {
            fall=terrain.verticalClearance(box,fall);
            if(fall==0) break;
        }
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
        else begin(choice==0 && d<=9?ColossusPose.GRAB:choice==1?ColossusPose.SWEEP:choice==2?ColossusPose.PUNCH:ColossusPose.SLAM);
    }
    public void begin(int next) {
        if(worldObj.isRemote) return;
        releaseGrab();
        grabTarget=null;
        stepY=Double.NaN;
        dataWatcher.updateObject(21,(byte)next); dataWatcher.updateObject(22,(int)worldObj.getTotalWorldTime());
        dataWatcher.updateObject(29,target==null?0F:(float)Math.max(0,(target.posY-posY)/scale()));
        dataWatcher.updateObject(31,0); dataWatcher.updateObject(26,0);
        grabSpent=next!=ColossusPose.GRAB || !targetable(target);
        if(next==ColossusPose.GRAB && targetable(target)) {
            grabTarget=target;
            Vec3 local=rootLocal(target.posX,target.posY,target.posZ);
            dataWatcher.updateObject(19,(byte)(local.xCoord>0?1:-1));
            updateGrabAim(false);
        }
        struck.clear(); lastHand=null; moving=0;
        if(next!=ColossusPose.IDLE) sound("mob.irongolem.throw",0.6F);
    }
    private int attackDuration() {
        if(state()!=ColossusPose.GRAB) return ColossusPose.duration(state());
        if(escapeTime()>0) return escapeTime()+24;
        if(captureTime()>0) return captureTime()+ColossusPose.ESCAPE_TICKS+19;
        return ColossusPose.GRAB_END+19;
    }
    private void updateGrabAim(boolean turn) {
        double dx=grabTarget.posX-posX,dz=grabTarget.posZ-posZ;
        if(turn) {
            double angle=Math.toDegrees(Math.asin(Math.min(0.76,3.6*scale()/Math.max(1,Math.hypot(dx,dz)))));
            turnToward((float)(Math.toDegrees(Math.atan2(-dx,dz))-grabSide()*angle),2.4F);
        }
        double centerY=(grabTarget.boundingBox.minY+grabTarget.boundingBox.maxY)*0.5;
        Vec3 goal=rootLocal(grabTarget.posX,Math.max(centerY,grabTarget.boundingBox.minY+1.65*scale()),grabTarget.posZ);
        aimX=(float)goal.xCoord; aimY=(float)goal.yCoord; aimZ=(float)goal.zCoord;
        dataWatcher.updateObject(16,aimX); dataWatcher.updateObject(17,aimY); dataWatcher.updateObject(18,aimZ);
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
            Vec3 p=point(ColossusPose.LEFT_FOOT,0,-ColossusPose.SOLE,-0.5);
            AxisAlignedBB foot=limbBox(ColossusPose.LEFT_FOOT); hitVolume(foot,1.3F,2.4F);
            terrain.contact(foot.expand(0,0.8,0)); shockwave(p,6*scale(),0.8F,1.8F); impactEffect(p,4); sound("random.explode",0.7F);
        }
        if(state==ColossusPose.GRAB) {
            if(t>=ColossusPose.GRAB_START && t<=ColossusPose.GRAB_END && !grabSpent) tryGrab(t);
            if(grabbed!=null) {
                if(!eligible(grabbed)) { releaseGrab(); return; }
                if(t>=captureTime()+ColossusPose.ESCAPE_TICKS) {
                    grabbed.attackEntityFrom(DamageSource.causeMobDamage(this),ColossusConfig.damage*2.5F);
                    releaseGrab();
                }
            }
        }
    }
    private void tryGrab(int time) {
        int hand=grabHand();
        // Sample between server ticks in trajectory order, so an interceptor can take the grab.
        for(int i=1;i<=5;i++) {
            evaluateAt(skeleton,time-1+i/5D,0);
            Vec3 elbow=point(hand,0,0,0), fist=point(hand,0,ColossusPose.FIST_Y,0);
            Vec3 shoulder=point(grabSide()<0?ColossusPose.RIGHT_ARM:ColossusPose.LEFT_ARM,0,0,0);
            AxisAlignedBB box=limbBox(hand);
            if(!terrain.loaded(box.addCoord(shoulder.xCoord-fist.xCoord,0,shoulder.zCoord-fist.zCoord))
                || !unobstructed(shoulder,elbow) || !unobstructed(elbow,fist)) {
                grabSpent=true;
                break;
            }
            EntityLivingBase contact=null;
            double nearest=Double.MAX_VALUE;
            for(Object o:worldObj.getEntitiesWithinAABB(EntityLivingBase.class,box)) {
                EntityLivingBase e=(EntityLivingBase)o;
                if(!eligible(e) || grips.containsKey(e.getUniqueID()) || e.ridingEntity!=null || e.riddenByEntity!=null
                    || !intersectsFist(e.boundingBox,hand,fist)) continue;
                Vec3 center=Vec3.createVectorHelper(e.posX,(e.boundingBox.minY+e.boundingBox.maxY)*0.5,e.posZ);
                double distance=fist.squareDistanceTo(center);
                if(distance<nearest && unobstructed(fist,center)) { contact=e; nearest=distance; }
            }
            if(contact!=null) {
                grabSpent=true; grabbed=contact;
                // The swept test can contact between ticks. Pin the passenger at its current location on the capture tick.
                evaluateAt(skeleton,time,0);
                grabAnchor=fromWorld(Vec3.createVectorHelper(contact.posX,contact.posY,contact.posZ),hand);
                grabEscape=0; grabSequence=0; grabKeyDown=true; grabPressTick=time-2;
                dataWatcher.updateObject(31,time);
                syncGrab();
                break;
            }
        }
        evaluate(skeleton,0);
    }
    private boolean unobstructed(Vec3 from,Vec3 to) {
        return worldObj.func_147447_a(Vec3.createVectorHelper(from.xCoord,from.yCoord,from.zCoord),to,false,true,false)==null;
    }
    private boolean intersectsFist(AxisAlignedBB box,int hand,Vec3 fist) {
        // SAT for the rendered, oriented fist against the target's world-aligned box.
        Vec3 center=Vec3.createVectorHelper((box.minX+box.maxX)*0.5,(box.minY+box.maxY)*0.5,(box.minZ+box.maxZ)*0.5);
        Vec3 delta=Vec3.createVectorHelper(center.xCoord-fist.xCoord,center.yCoord-fist.yCoord,center.zCoord-fist.zCoord);
        double s=scale();
        Vec3[] handAxes={
            direction(fist,point(hand,1,ColossusPose.FIST_Y,0),s),
            direction(fist,point(hand,0,ColossusPose.FIST_Y+1,0),s),
            direction(fist,point(hand,0,ColossusPose.FIST_Y,1),s)
        };
        Vec3[] worldAxes={Vec3.createVectorHelper(1,0,0),Vec3.createVectorHelper(0,1,0),Vec3.createVectorHelper(0,0,1)};
        for(Vec3 axis:handAxes) if(separated(axis,delta,box,handAxes,s)) return false;
        // World axes were already checked by the enclosing AABB query.
        for(Vec3 axis:handAxes) for(Vec3 worldAxis:worldAxes)
            if(separated(axis.crossProduct(worldAxis),delta,box,handAxes,s)) return false;
        return true;
    }
    private static Vec3 direction(Vec3 from,Vec3 to,double scale) {
        return Vec3.createVectorHelper((to.xCoord-from.xCoord)/scale,(to.yCoord-from.yCoord)/scale,(to.zCoord-from.zCoord)/scale);
    }
    private static boolean separated(Vec3 axis,Vec3 delta,AxisAlignedBB box,Vec3[] hand,double s) {
        double target=(Math.abs(axis.xCoord)*(box.maxX-box.minX)+Math.abs(axis.yCoord)*(box.maxY-box.minY)+Math.abs(axis.zCoord)*(box.maxZ-box.minZ))*0.5;
        double fist=s*(ColossusPose.FIST_X_RADIUS*Math.abs(axis.dotProduct(hand[0]))
            +ColossusPose.FIST_Y_RADIUS*Math.abs(axis.dotProduct(hand[1]))+ColossusPose.FIST_Z_RADIUS*Math.abs(axis.dotProduct(hand[2])));
        return Math.abs(axis.dotProduct(delta))>target+fist+1E-7;
    }
    private void movingHit(int limb,float damage,float force,boolean destroy) {
        AxisAlignedBB now=limbBox(limb), swept=lastHand==null?now:union(now,lastHand);
        hitVolume(swept,damage,force); if(destroy) terrain.contact(swept);
        lastHand=now;
    }
    private static AxisAlignedBB union(AxisAlignedBB a,AxisAlignedBB b) {
        return AxisAlignedBB.getBoundingBox(Math.min(a.minX,b.minX),Math.min(a.minY,b.minY),Math.min(a.minZ,b.minZ),
            Math.max(a.maxX,b.maxX),Math.max(a.maxY,b.maxY),Math.max(a.maxZ,b.maxZ));
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
    private Vec3 rootLocal(double wx,double wy,double wz) {
        double yaw=Math.toRadians(180-rotationYaw),c=Math.cos(yaw),s=Math.sin(yaw),scale=scale();
        double dx=(wx-posX)/scale,dz=(wz-posZ)/scale;
        return Vec3.createVectorHelper(dx*c-dz*s,(wy-posY)/scale,dx*s+dz*c);
    }
    public Vec3 fromWorld(Vec3 world,int bone) {
        Vec3 local=rootLocal(world.xCoord,world.yCoord,world.zCoord);
        return skeleton.bones[bone].inversePoint(local.xCoord,local.yCoord,local.zCoord);
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
    public AxisAlignedBB limbBox(int bone) {
        if(bone==ColossusPose.LEFT_FOOT || bone==ColossusPose.RIGHT_FOOT)
            return boneBox(bone,0,-0.5,-0.625,1.875,0.75,2.125);
        return boneBox(bone,0,ColossusPose.FIST_Y,0,ColossusPose.FIST_X_RADIUS,ColossusPose.FIST_Y_RADIUS,ColossusPose.FIST_Z_RADIUS);
    }
    public AxisAlignedBB partBox(int slot) {
        switch(slot) {
            case 0: return boneBox(ColossusPose.TORSO,0,3.125,0,4,3.5,2.125);
            case 1: return boneBox(ColossusPose.HEAD,0,1.75,-0.25,2.75,3,2.75);
            case 2: return boneBox(ColossusPose.HEAD,0,ColossusPose.RUNE_Y,ColossusPose.RUNE_Z,0.95,0.85,0.125);
            case 3: return limbBox(ColossusPose.LEFT_HAND);
            case 4: return limbBox(ColossusPose.RIGHT_HAND);
            case 5: return boneBox(ColossusPose.LEFT_LEG,0,-2.125,0,1.625,2.125,1.5);
            case 6: return boneBox(ColossusPose.RIGHT_LEG,0,-2.125,0,1.625,2.125,1.5);
            default: return union(limbBox(ColossusPose.LEFT_FOOT),limbBox(ColossusPose.RIGHT_FOOT));
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
        Entity actor=source.getEntity(); if(targetable(actor)) target=(EntityLivingBase)actor;
        evaluate(skeleton,0);
        if(weakHit(source)) {
            boolean gauntlet=actor instanceof EntityPlayer && ((EntityPlayer)actor).getHeldItem()!=null && ((EntityPlayer)actor).getHeldItem().getItem()==ColossusContent.gauntlet && source.getSourceOfDamage()==actor;
            if(ColossusConfig.gauntletRequired && !gauntlet) { sound("random.orb",0.5F); return false; }
            reaction=1; dataWatcher.updateObject(30,(int)worldObj.getTotalWorldTime()+20); impactEffect(point(ColossusPose.HEAD,0,ColossusPose.RUNE_Y,ColossusPose.RUNE_Z),6); sound("dig.glass",0.5F);
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
        if(a.zCoord<=ColossusPose.RUNE_Z || b.zCoord>=a.zCoord) return false;
        double t=(ColossusPose.RUNE_Z-a.zCoord)/(b.zCoord-a.zCoord); if(t<0 || t>1) return false;
        double x=a.xCoord+(b.xCoord-a.xCoord)*t,y=a.yCoord+(b.yCoord-a.yCoord)*t;
        if(Math.abs(x)>0.95 || Math.abs(y-ColossusPose.RUNE_Y)>0.85) return false;
        Vec3 contact=point(ColossusPose.HEAD,x,y,ColossusPose.RUNE_Z);
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
        Grip g=grips.get(p.getUniqueID()); if(g==null) return;
        g.input=MathHelper.clamp_int(vertical,-1,1); g.brace=brace; g.inputTick=worldObj.getTotalWorldTime();
        if(release) release(p,false);
    }
    public void escapeInput(EntityPlayerMP p,int token,int sequence,boolean down) {
        if(grabbed!=p || token!=grabToken() || sequence<=grabSequence || grabTicksLeft()==0) return;
        grabSequence=sequence;
        boolean pressed=down && !grabKeyDown;
        grabKeyDown=down;
        int time=(int)stateTime(0);
        if(!pressed || time-grabPressTick<2) return;
        grabPressTick=time;
        if(++grabEscape<ColossusConfig.escapePresses) { syncGrab(); return; }
        dataWatcher.updateObject(26,time);
        releaseGrab();
        // Move away from the hand without displacing the player through terrain.
        double dx=p.posX-posX,dz=p.posZ-posZ, length=Math.max(0.01,Math.hypot(dx,dz));
        dx=dx/length*0.65; dz=dz/length*0.65;
        if(terrain.clearBlocks(p.boundingBox.addCoord(dx,0,dz))) {
            if(p.playerNetServerHandler!=null) p.playerNetServerHandler.setPlayerLocation(p.posX+dx,p.posY,p.posZ+dz,p.rotationYaw,p.rotationPitch);
            else p.setPosition(p.posX+dx,p.posY,p.posZ+dz);
            p.addVelocity(dx,0.16,dz); p.velocityChanged=true;
        }
        sound("dig.stone",0.7F);
    }
    public Vec3 climbPoint(float progress,int side) {
        // Continuous travel between leg, hip, torso, shoulder and rear-skull holds.
        float q=MathHelper.clamp_float(progress,0,4); int node=Math.min(3,(int)q); float f=q-node;
        int[] bones={side<0?ColossusPose.RIGHT_FOOT:ColossusPose.LEFT_FOOT,ColossusPose.TORSO,ColossusPose.TORSO,ColossusPose.HEAD,ColossusPose.HEAD};
        double[][] offsets={{0.9,0,2.1},{2.85,-1,2.65},{3.1,4.25,2.75},{1.85,-0.25,3.0},{0,1.4,3.55}};
        Vec3 a=point(bones[node],offsets[node][0]*side,offsets[node][1],offsets[node][2]);
        Vec3 b=point(bones[node+1],offsets[node+1][0]*side,offsets[node+1][1],offsets[node+1][2]);
        return Vec3.createVectorHelper(a.xCoord+(b.xCoord-a.xCoord)*f,a.yCoord+(b.yCoord-a.yCoord)*f,a.zCoord+(b.zCoord-a.zCoord)*f);
    }
    public Vec3 grabPoint(int side,double x,double y,double z,Entity passenger) {
        int hand=side<0?ColossusPose.RIGHT_HAND:ColossusPose.LEFT_HAND;
        Vec3 contact=point(hand,x,y,z);
        if(captureTime()<=0) return contact;
        double close=ColossusPose.smooth((stateTime(0)-captureTime()-10)/55D);
        Vec3 palm=point(hand,0,ColossusPose.FIST_Y,0);
        double px=contact.xCoord+(palm.xCoord-contact.xCoord)*close;
        double py=contact.yCoord+(palm.yCoord-passenger.height*0.5-contact.yCoord)*close;
        double pz=contact.zCoord+(palm.zCoord-contact.zCoord)*close;
        // The hand can roll slightly downward before lifting. Keep grounded passengers above the floor.
        double half=passenger.width*0.5;
        AxisAlignedBB footprint=AxisAlignedBB.getBoundingBox(px-half,py,pz-half,px+half,py+passenger.height,pz+half);
        return Vec3.createVectorHelper(px,py+terrain.floorLift(footprint),pz);
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
            if(ticksExisted%4==0) syncGrip(p,g.progress,g.side);
        }
        if(grabbed!=null) {
            Vec3 anchor=grabPoint(grabSide(),grabAnchor.xCoord,grabAnchor.yCoord,grabAnchor.zCoord,grabbed);
            if(!placeAttached(grabbed,anchor)) releaseGrab();
            else if(ticksExisted%4==0) syncGrab();
        }
    }
    private boolean placeAttached(Entity e,Vec3 p) {
        if(!loaded(worldObj,p.xCoord,p.zCoord,1)) return false;
        double dx=p.xCoord-e.posX,dy=p.yCoord-e.posY,dz=p.zCoord-e.posZ;
        if(e==grabbed) {
            AxisAlignedBB start=e.boundingBox.contract(1E-6,1E-6,1E-6);
            double lift=terrain.floorLift(start);
            if(!terrain.clearBlocks(start.offset(0,lift,0).addCoord(dx,dy-lift,dz))) return false;
        }
        AxisAlignedBB box=e.boundingBox.copy().offset(dx,dy,dz);
        if(!clear(box.contract(1E-6,1E-6,1E-6))) return false;
        e.motionX=e.motionY=e.motionZ=0; e.fallDistance=0;
        if(e instanceof EntityPlayerMP && ((EntityPlayerMP)e).playerNetServerHandler!=null && ticksExisted%4==0) ((EntityPlayerMP)e).playerNetServerHandler.setPlayerLocation(p.xCoord,p.yCoord,p.zCoord,e.rotationYaw,e.rotationPitch);
        else e.setPosition(p.xCoord,p.yCoord,p.zCoord);
        return true;
    }
    private void syncGrip(Entity e,float progress,int side) {
        PacketDispatcher.wrapper.sendToAllAround(new ColossusAttachmentPacket(getEntityId(),e.getEntityId(),progress,false,side),new TargetPoint(dimension,posX,posY,posZ,128));
    }
    private void syncGrab() {
        PacketDispatcher.wrapper.sendToAllAround(new ColossusAttachmentPacket(getEntityId(),grabbed.getEntityId(),grabEscape,grabSide(),
            grabToken(),ColossusConfig.escapePresses,grabAnchor),new TargetPoint(dimension,posX,posY,posZ,128));
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
    private void releaseGrab() { if(grabbed!=null) { detach(grabbed); grabbed=null; grabAnchor=null; } }
    public void releaseAll() { releaseGrab(); for(Grip g:grips.values()) detach(g.player); grips.clear(); }
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
            Vec3 p=bone==ColossusPose.HEAD?point(bone,0,ColossusPose.RUNE_Y,ColossusPose.RUNE_Z):point(bone,(rand.nextDouble()-0.5)*7,y,(rand.nextDouble()-0.5)*4);
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
