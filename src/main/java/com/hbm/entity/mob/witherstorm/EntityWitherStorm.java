package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import com.hbm.entity.logic.IChunkLoader;
import com.hbm.main.MainRegistry;
import api.hbm.entity.IRadiationImmune;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

/** Hostile encounter root. Command Block Wither and all evolved forms share this identity. */
public class EntityWitherStorm extends EntityFlying implements IBossDisplayData, IMob, IRadiationImmune, IChunkLoader {
    private static final String HURT_SOUND = "hbm:entity.witherStorm.hurt";
    private static final int SKULL_WINDUP_TICKS = 10;
    private final UUID[] partIds = new UUID[10];
    private final EntityWitherStormPart[] parts = new EntityWitherStormPart[10];
    private final int[] partChunksX = new int[10], partChunksZ = new int[10];
    private final double[] armSample = new double[3];
    private final EntityLivingBase[] targets = new EntityLivingBase[3];
    private final UUID[] targetIds = new UUID[3];
    private int skullCooldown = skullCooldownForPhase();
    private int skullHead = -1, skullWindup;
    private EntityLivingBase skullTarget;
    private long nextHurtSoundTick;
    private final List<Entity> nearby = new ArrayList<Entity>(64);
    public final WitherStormTerrain terrain = new WitherStormTerrain(this);
    public boolean registered;
    private int searchOffset, roarTimer = 200;
    private int lastGrowthSoundMass;
    private int stageBTicks;
    private float visualScale = 0.9F, previousVisualScale = 0.9F;
    private float tentacleActivity, previousTentacleActivity;
    private long visualCaptureUntil;
    private float visualCapture, previousVisualCapture;
    private boolean restoring;
    private double roamX, roamZ;
    private int roamTicks;
    private WitherStormPursuit pursuit;
    private Ticket flightTicket;
    private ChunkCoordIntPair flightChunk;

    public EntityWitherStorm(World world) {
        super(world);
        setSize(2F, 4F);
        noClip = true; isImmuneToFire = true; ignoreFrustumCheck = true;
        experienceValue = 500;
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, Integer.valueOf(0)); // consumed mass
        dataWatcher.addObject(21, Byte.valueOf((byte)0)); // phase
        dataWatcher.addObject(22, Byte.valueOf((byte)1)); // command core intact
        dataWatcher.addObject(23, Integer.valueOf(0)); // transition ticks remaining
        for(int i = 0; i < 3; i++) dataWatcher.addObject(24 + i, Integer.valueOf(0));
        dataWatcher.addObject(27, Float.valueOf(0.9F)); // server-configured growth scale
        dataWatcher.addObject(28, Byte.valueOf((byte)0)); // active tractor beams, one bit per head
        dataWatcher.addObject(29, Byte.valueOf((byte)0)); // transformed heads; central head is slot 1
        dataWatcher.addObject(30, Float.valueOf(0F)); // server-configured anatomical development
        dataWatcher.addObject(31, Byte.valueOf((byte)0)); // heads undergoing the current transition
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(300D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(64D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }
    @Override protected void updateEntityActionState() { } // Flight/attacks below own all AI.
    @Override protected void despawnEntity() { }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public EnumCreatureAttribute getCreatureAttribute() { return EnumCreatureAttribute.UNDEAD; }
    @Override public void addPotionEffect(PotionEffect effect) { if(effect.getPotionID() != Potion.wither.id) super.addPotionEffect(effect); }
    public int getGrowth() { return dataWatcher.getWatchableObjectInt(20); }
    public WitherStormPhase getPhase() { return WitherStormPhase.values()[Math.max(0, Math.min(3, dataWatcher.getWatchableObjectByte(21)))]; }
    public WitherStormStage getStage() {
        switch(getPhase()) {
            case COMMAND_BLOCK: return WitherStormStage.A;
            case DESTROYER: return headTransformed(0) && headTransformed(2) ? WitherStormStage.B : WitherStormStage.A_TRANSFORMED;
            case DEVOURER:
                if(development() < 0.775F) return WitherStormStage.C_SMALL;
                return getHealth() <= getMaxHealth() * 0.25F ? WitherStormStage.C_DAMAGED : WitherStormStage.C_BIG;
            default: return WitherStormStage.D;
        }
    }
    public boolean hasCore() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public int transitionTicks() { return dataWatcher.getWatchableObjectInt(23); }
    public int headTarget(int head) { return dataWatcher.getWatchableObjectInt(24 + head); }
    public boolean headTransformed(int head) { return (dataWatcher.getWatchableObjectByte(29) & (1 << head)) != 0; }
    public float development() { return dataWatcher.getWatchableObjectFloat(30); }
    public float headDevelopment(int head, float partial) {
        if(!headTransformed(head)) return 0F;
        return (dataWatcher.getWatchableObjectByte(31) & (1 << head)) == 0 ? 1F
            : Math.max(0F, Math.min(1F, 1F - (transitionTicks() - partial) / 80F));
    }
    public float headSize(int head) {
        if(!headTransformed(head)) return commandHeadSize(head);
        switch(getStage()) {
            case A_TRANSFORMED: return 0.58F;
            case B: return WitherStormDestroyerPose.headSize(head, development());
            case C_SMALL: return WitherStormDevourerPose.headSize(head, development());
            case C_BIG: return WitherStormBigDevourerPose.headSize(head, development());
            default: return 1.15F + development() * 0.65F;
        }
    }
    public float headModelScale(int head, float partial, float bodyScale) {
        if(getStage() == WitherStormStage.A) return bodyScale;
        float size = bodyScale * headSize(head);
        return headTransformed(head) ? size * WitherStormMath.HEAD_SCALE * (0.45F + 0.55F * headDevelopment(head, partial)) : size;
    }
    public boolean beamActive(int head) { return isEntityAlive() && headTransformed(head) && (dataWatcher.getWatchableObjectByte(28) & (1 << head)) != 0; }
    private int headsForGrowth(int mass) {
        int stageB = WitherStormConfig.transform + Math.max(1, (WitherStormConfig.devourer - WitherStormConfig.transform) / 2);
        return mass >= stageB ? 7 : mass >= WitherStormConfig.transform ? 2 : 0;
    }
    public float getStormScale() {
        return dataWatcher.getWatchableObjectFloat(27);
    }
    public float renderScale(float partial) { return previousVisualScale + (visualScale - previousVisualScale) * partial; }
    public float bodyYaw(float partial) {
        return prevRotationYaw + MathHelper.wrapAngleTo180_float(rotationYaw - prevRotationYaw) * partial;
    }
    public float attackStrength() { return 8F + getPhase().ordinal() * 5F + Math.min(16F, getGrowth() / 25000F); }
    public void addMass(int amount) {
        if(amount <= 0 || worldObj.isRemote) return;
        int mass = (int)Math.min(1000000000L, (long)getGrowth() + amount);
        boolean holdStageB = getPhase().ordinal() <= WitherStormPhase.DESTROYER.ordinal()
            && stageBTicks < WitherStormConfig.minimumStageBTicks;
        updateGrowth(mass, holdStageB);
    }

    /** Explicit growth commands select the phase immediately. */
    public void setGrowth(int mass) {
        updateGrowth(mass, false);
    }

    private void updateGrowth(int mass, boolean holdStageB) {
        if(worldObj.isRemote) return;
        WitherStormStage oldStage = getStage();
        WitherStormPhase before = getPhase(), after = WitherStormPhase.forGrowth(Math.max(0, mass));
        if(holdStageB && after.ordinal() > WitherStormPhase.DESTROYER.ordinal()) after = WitherStormPhase.DESTROYER;
        int oldHeads = dataWatcher.getWatchableObjectByte(29), heads = headsForGrowth(mass);
        // Retain saved transformations if configuration thresholds changed after saving.
        if(mass >= getGrowth()) heads = pairSideHeads(heads | oldHeads);
        float oldMax = getMaxHealth(), health = getHealth();
        dataWatcher.updateObject(20, Math.max(0, Math.min(1000000000, mass)));
        dataWatcher.updateObject(21, (byte)after.ordinal());
        dataWatcher.updateObject(29, (byte)heads);
        float development = mass < WitherStormConfig.transform ? 0.22F * Math.max(0, mass) / WitherStormConfig.transform
            : mass <= WitherStormConfig.devourer ? 0.22F + 0.33F * (mass - WitherStormConfig.transform) / (WitherStormConfig.devourer - WitherStormConfig.transform)
            : 0.55F + 0.45F * Math.min(1F, (float)(mass - WitherStormConfig.devourer) / (WitherStormConfig.thunder - WitherStormConfig.devourer));
        // Extra mass still contributes to scale and health during the hold. Keep Stage B anatomy in its usual range.
        dataWatcher.updateObject(30, after == WitherStormPhase.DESTROYER ? Math.min(0.55F, development) : development);
        dataWatcher.updateObject(27, after == WitherStormPhase.COMMAND_BLOCK ? 0.9F + 0.1F * Math.min(1F, (float)getGrowth() / WitherStormConfig.transform)
            : (float)Math.min(2.8D, 1D + Math.log1p((double)getGrowth() / 12500D) * 0.3D));
        // Vanilla 1.7.10 clamps maxHealth at 1024. Keep the boss inside that contract.
        float max = Math.min(1024F, 300F + (float)Math.sqrt(getGrowth()) * 1.1F);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(max);
        setHealth(Math.min(max, health + Math.max(0F, max - oldMax)));
        if(!restoring && (oldStage != WitherStormStage.B || getStage() != WitherStormStage.B)) stageBTicks = 0;
        if((before != after || oldHeads != heads) && !restoring) {
            dataWatcher.updateObject(23, 80);
            dataWatcher.updateObject(31, (byte)(heads & ~oldHeads));
            playSound("hbm:entity.witherStorm.transform", 2F, 1F);
        }
    }
    @Override public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : (getPhase() == WitherStormPhase.COMMAND_BLOCK ? "Command Block Wither" : "Wither Storm — " + getPhase().name());
    }
    @Override public void onLivingUpdate() {
        if(!worldObj.isRemote && registered && !WitherStormEncounters.get(worldObj).contains(getUniqueID())) { setDead(); return; }
        previousVisualScale = visualScale;
        visualScale += (getStormScale() - visualScale) * 0.05F;
        previousTentacleActivity = tentacleActivity;
        float activity = (float)Math.min(0.25D, Math.sqrt(motionX * motionX + motionZ * motionZ) * 2D);
        for(int head = 0; head < 3; head++) {
            if(headTarget(head) != 0) activity = Math.max(activity, 0.45F);
            if(beamActive(head) || parts[head] != null && parts[head].skullWindingUp()) activity = 1F;
        }
        tentacleActivity += (activity - tentacleActivity) * 0.08F;
        previousVisualCapture = visualCapture;
        float capture = worldObj.isRemote ? (float)Math.max(0D, Math.min(1D,
            (visualCaptureUntil - worldObj.getTotalWorldTime()) / 20D)) : 0F;
        visualCapture += (capture - visualCapture) * 0.12F;
        if(!worldObj.isRemote && isEntityAlive()) {
            if(!registered) { if(!WitherStormEncounters.get(worldObj).admit(this)) { setDead(); return; } registered = true; }
            if(transitionTicks() > 0) dataWatcher.updateObject(23, transitionTicks() - 1);
            if(getStage() == WitherStormStage.B) {
                if(stageBTicks < Integer.MAX_VALUE) stageBTicks++;
                if(stageBTicks >= WitherStormConfig.minimumStageBTicks && getGrowth() > WitherStormConfig.devourer)
                    updateGrowth(getGrowth(), false);
            }
            if(ticksExisted % 20 == 1) {
                WitherStormEncounters.get(worldObj).pruneProjectiles(this);
                WitherStormMath.nearby(this, 64, nearby, searchOffset++);
                chooseTargets();
            }
            for(int i = 0; i < 3; i++) {
                if(!validTarget(targets[i])) { targets[i] = null; dataWatcher.updateObject(24 + i, 0); }
            }
            fly();
            maintainParts();
            updateSkullAttack();
            interact();
            terrain.tick();
            if(--roarTimer <= 0) { playSound("hbm:entity.witherStorm.roar", 2F, 0.9F); roarTimer = 300 + rand.nextInt(200); }
            if(ticksExisted % 100 == 0) playSound("hbm:entity.witherStorm.hum", 0.7F, 1F);
            if(ticksExisted % 160 == 0 && getGrowth() > lastGrowthSoundMass) { playSound("hbm:entity.witherStorm.grow", 1F, 1F); lastGrowthSoundMass = getGrowth(); }
            if(getPhase() == WitherStormPhase.THUNDERSTORM && ticksExisted % 100 == 0 && !worldObj.provider.hasNoSky) {
                // A short local encounter refresh leaves normal weather expiry in charge afterwards.
                worldObj.getWorldInfo().setRaining(true); worldObj.getWorldInfo().setThundering(true);
                worldObj.getWorldInfo().setRainTime(200); worldObj.getWorldInfo().setThunderTime(200);
            }
        }
        float s = getStormScale();
        WitherStormStage stage = getStage();
        float development = development();
        float wantedWidth = (stage == WitherStormStage.A ? 41F / 16F
            : stage == WitherStormStage.A_TRANSFORMED ? 3.9F : 2F + development * 21F) * s;
        float bodyHeight = 3.5F + development * 10.5F;
        switch(stage) {
            case A: bodyHeight = 30F / 16F; break;
            case A_TRANSFORMED: bodyHeight = 3.89F + Math.max(0F, development - 0.22F) * 3F; break;
            case B: bodyHeight = 1.6F + development * 5.2F + (1.9F + development * 9F) * 0.77F; break;
            case C_SMALL:
            case D:
                bodyHeight = 1.6F + development * 5.2F + (1.9F + development * 9F) * 1.895F * stage.crownScale();
                break;
            case C_BIG:
                bodyHeight = 46F * WitherStormBigDevourerPose.size(development);
                wantedWidth = 32F * WitherStormBigDevourerPose.size(development) * s;
                break;
            default: break;
        }
        float wantedHeight = bodyHeight * s;
        if(width != wantedWidth || height != wantedHeight) {
            // 1.7.10 setSize moves a growing entity toward its old box corner.
            // Keep growth and reload sizing centered on the saved flight position.
            double x = posX, y = posY, z = posZ;
            setSize(wantedWidth, wantedHeight); setPosition(x, y, z);
        }
        fallDistance = 0;
        super.onLivingUpdate();
    }
    private void chooseTargets() {
        for(int head = 0; head < 3; head++) {
            if(head == skullHead && validTarget(skullTarget)) continue;
            EntityLivingBase best = null; double score = Double.MAX_VALUE;
            Vec3 mouth = mouth(head);
            // Keep a valid lock even when the rolling bounded search misses its chunk.
            EntityLivingBase current = targets[head];
            if(validTarget(current) && !assigned(current, head)) { best = current; score = targetScore(current, mouth, head); }
            for(Entity e : nearby) {
                if(!validTarget(e) || assigned(e, head)) continue;
                double d = targetScore(e, mouth, head);
                if(d < score) { score = d; best = (EntityLivingBase)e; }
            }
            targets[head] = best; targetIds[head] = best == null ? null : best.getUniqueID();
            dataWatcher.updateObject(24 + head, best == null ? 0 : best.getEntityId());
        }
    }
    private boolean validTarget(Entity e) {
        if(pursuit != null && e instanceof EntityPlayer && (!(e instanceof EntityPlayerMP)
            || !WitherStormPursuit.eligible((EntityPlayerMP)e))) return false;
        return WitherStormMath.targetable(e, worldObj) && getDistanceSqToEntity(e) <= 96D * 96D;
    }
    private boolean assigned(Entity e, int head) {
        if(skullHead > head && e == skullTarget) return true;
        for(int i = 0; i < head; i++) if(targets[i] == e) return true;
        return false;
    }
    private double targetScore(Entity e, Vec3 mouth, int head) {
        double d = e.getDistanceSq(mouth.xCoord, mouth.yCoord, mouth.zCoord);
        if(e instanceof EntityPlayer) d *= 0.75D;
        if(targetIds[head] != null && targetIds[head].equals(e.getUniqueID())) d *= 0.6D;
        return d;
    }
    void startPursuit() { pursuit = new WitherStormPursuit(); }

    void startFlightTicket() {
        if(pursuit != null && worldObj.provider.dimensionId == 0 && flightTicket == null)
            init(ForgeChunkManager.requestTicket(MainRegistry.instance, worldObj, ForgeChunkManager.Type.ENTITY));
    }

    @Override public void init(Ticket ticket) {
        if(ticket == null) return;
        if(isDead || pursuit == null || worldObj.provider.dimensionId != 0 || flightTicket != null || ticket.getChunkListDepth() < 1) {
            ForgeChunkManager.releaseTicket(ticket);
            return;
        }
        flightTicket = ticket;
        ticket.bindEntity(this);
        keepFlightChunks();
    }

    private void keepFlightChunks() {
        if(flightTicket == null) return;
        int cx = MathHelper.floor_double(posX) >> 4, cz = MathHelper.floor_double(posZ) >> 4;
        if(flightChunk != null && flightChunk.chunkXPos == cx && flightChunk.chunkZPos == cz) return;
        flightChunk = new ChunkCoordIntPair(cx, cz);
        // Keep only the local flight area active. Waypoint sampling never loads terrain.
        int radius = flightTicket.getChunkListDepth() >= 9 ? 1 : 0;
        for(ChunkCoordIntPair chunk : flightTicket.getChunkList()) {
            if(Math.abs(chunk.chunkXPos - cx) > radius || Math.abs(chunk.chunkZPos - cz) > radius)
                ForgeChunkManager.unforceChunk(flightTicket, chunk);
        }
        for(int x = cx - radius; x <= cx + radius; x++) for(int z = cz - radius; z <= cz + radius; z++) {
            ChunkCoordIntPair chunk = new ChunkCoordIntPair(x, z);
            if(worldObj.getPersistentChunks().containsEntry(chunk, flightTicket)) continue;
            ForgeChunkManager.forceChunk(flightTicket, chunk);
            ((WorldServer)worldObj).theChunkProviderServer.loadChunk(x, z);
        }
    }

    private void fly() {
        double margin = Math.max(34D, 19 * getStormScale() + 3);
        Vec3 waypoint = null;
        if(pursuit != null && worldObj.provider.dimensionId == 0) {
            if(flightTicket == null && ticksExisted % 200 == 1) startFlightTicket();
            keepFlightChunks();
            waypoint = pursuit.destination(this, WitherStormEncounters.get(worldObj).playerTrails);
            if(flightTicket != null) margin = 0D;
        }
        EntityLivingBase target = null;
        for(EntityLivingBase candidate : targets) if(validTarget(candidate)
            && (target == null || getDistanceSqToEntity(candidate) < getDistanceSqToEntity(target))) target = candidate;
        double tx = waypoint != null ? waypoint.xCoord : target == null ? posX : target.posX;
        double tz = waypoint != null ? waypoint.zCoord : target == null ? posZ : target.posZ;
        double dx = tx - posX, dz = tz - posZ, distance = Math.sqrt(dx * dx + dz * dz);
        boolean pursuing = waypoint != null || target != null && (distance < 1D || WitherStormMath.areaLoaded(worldObj,
            posX + dx / distance * Math.min(12D, distance), posZ + dz / distance * Math.min(12D, distance), margin));
        if(!pursuing) {
            double rx = roamX - posX, rz = roamZ - posZ;
            if(--roamTicks <= 0 || rx * rx + rz * rz < 36D || !WitherStormMath.areaLoaded(worldObj, roamX, roamZ, margin)) {
                roamX = posX; roamZ = posZ; roamTicks = 40;
                for(int i = 0; i < 8; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2D, range = 12D + rand.nextDouble() * 16D;
                    double x = posX + Math.cos(angle) * range, z = posZ + Math.sin(angle) * range;
                    if(!WitherStormMath.areaLoaded(worldObj, x, z, margin)) continue;
                    roamX = x; roamZ = z; roamTicks = 160 + rand.nextInt(80); break;
                }
            }
            tx = roamX; tz = roamZ;
        } else roamTicks = 0;
        dx = tx - posX; dz = tz - posZ; distance = Math.sqrt(dx * dx + dz * dz);
        double ground = posY - 12;
        if(WitherStormMath.loaded(worldObj, posX, posZ)) {
            Chunk chunk = worldObj.getChunkProvider().provideChunk(MathHelper.floor_double(posX) >> 4, MathHelper.floor_double(posZ) >> 4);
            ground = chunk.getHeightValue(MathHelper.floor_double(posX) & 15, MathHelper.floor_double(posZ) & 15);
        }
        if(distance > 1D) {
            double x = posX + dx / distance * Math.min(6D, distance), z = posZ + dz / distance * Math.min(6D, distance);
            if(WitherStormMath.loaded(worldObj, x, z)) ground = Math.max(ground, worldObj.getChunkProvider().provideChunk(MathHelper.floor_double(x) >> 4,
                MathHelper.floor_double(z) >> 4).getHeightValue(MathHelper.floor_double(x) & 15, MathHelper.floor_double(z) & 15));
        }
        double clearance = getPhase() == WitherStormPhase.COMMAND_BLOCK ? 7D : 15D;
        // The big Devourer's hanging arms extend below its root; keep their tips above the terrain.
        if(getStage() == WitherStormStage.C_BIG)
            clearance = Math.max(clearance, 27D * WitherStormBigDevourerPose.size(development()) * getStormScale());
        double targetY = waypoint != null ? waypoint.yCoord : pursuing ? target.posY : ground;
        double ty = Math.min(pursuit == null ? 200 : 275, Math.max(ground + clearance,
            pursuing ? Math.min(ground + 24D, targetY + 12D) : ground + clearance));
        double speed = getPhase() == WitherStormPhase.COMMAND_BLOCK ? 0.22D : 0.32D;
        boolean advance = distance > (waypoint == null ? 6 : 2) && (waypoint == null || posY > ground + 2D);
        motionX = WitherStormMath.limit(motionX * 0.92D + (advance ? dx / distance * 0.025D : 0), speed);
        motionZ = WitherStormMath.limit(motionZ * 0.92D + (advance ? dz / distance * 0.025D : 0), speed);
        motionY = WitherStormMath.limit(motionY * 0.9D + (ty - posY) * 0.004D, 0.24D);
        // Missing saved attachments still wait for their chunks; they do not own flight.
        double nextX = posX + motionX, nextZ = posZ + motionZ;
        if(!WitherStormMath.areaLoaded(worldObj, nextX, nextZ, margin)) {
            if(flightTicket == null) { motionX = motionZ = 0; roamTicks = 0; }
            else ((WorldServer)worldObj).theChunkProviderServer.loadChunk(MathHelper.floor_double(nextX) >> 4, MathHelper.floor_double(nextZ) >> 4);
        }
        if(distance > 2) rotationYaw += MathHelper.wrapAngleTo180_float((float)(Math.atan2(dx, -dz) * 180D / Math.PI) - rotationYaw) * 0.04F;
        renderYawOffset = rotationYaw; rotationYawHead = rotationYaw;
    }
    /** Every slot has one saved UUID. A known unloaded slot is never replaced. */
    private void maintainParts() {
        for(int i = 0; i < 10; i++) {
            if(!isPartActive(i)) {
                if(parts[i] != null) retirePart(parts[i]);
                continue;
            }
            if(parts[i] == null || parts[i].isDead || WitherStormEncounters.get(worldObj).find(partIds[i], worldObj) != parts[i]) {
                parts[i] = null;
                Entity found = WitherStormEncounters.get(worldObj).find(partIds[i], worldObj);
                if(found instanceof EntityWitherStormPart) parts[i] = (EntityWitherStormPart)found;
                else {
                    if(partIds[i] != null && (ticksExisted < 40 || !worldObj.getChunkProvider().chunkExists(partChunksX[i], partChunksZ[i]))) continue;
                    EntityWitherStormPart part = new EntityWitherStormPart(worldObj);
                    part.attach(this, i, partIds[i]);
                    Vec3 position = partPosition(i); part.setPosition(position.xCoord, position.yCoord, position.zCoord);
                    if(!WitherStormMath.loaded(worldObj, part.posX, part.posZ) || !worldObj.spawnEntityInWorld(part)) { part.setDead(); continue; }
                    partIds[i] = part.getUniqueID(); parts[i] = part;
                }
            }
            parts[i].parent = this;
            parts[i].followParent();
            partChunksX[i] = MathHelper.floor_double(parts[i].posX) >> 4; partChunksZ[i] = MathHelper.floor_double(parts[i].posZ) >> 4;
        }
    }
    public boolean isPartActive(int slot) { return slot < 3 || getStage().hasTentacles(); }

    void retirePart(EntityWitherStormPart part) {
        int slot = part.slot();
        if(part.getUniqueID().equals(partIds[slot])) {
            parts[slot] = null;
            partIds[slot] = null;
        }
        part.setDead();
    }

    public Vec3 partPosition(int slot) {
        float s = getStormScale();
        if(slot < 3) {
            Vec3 offset = headPartOffset(slot, rotationYaw, s);
            return Vec3.createVectorHelper(posX + offset.xCoord, posY + offset.yCoord, posZ + offset.zCoord);
        }
        if(getStage().hasPosedTentacles()) return posedArmPosition(slot, 1D);
        float d = development();
        double angle = (slot - 3) * Math.PI * 2D / 7D;
        double wave = Math.sin((worldObj.getTotalWorldTime() + slot * 19) * 0.04D);
        double length = (slot >= 8 ? 7D : 5D) + d * 11D;
        return local(Math.cos(angle) * (length + wave) * s, (slot >= 8 ? -2D : -4D - d * 6D) * s, Math.sin(angle) * length * s);
    }
    public static float commandHeadSize(int slot) { return slot == 1 ? 1F : 0.75F; }

    /** Shared attachment base for tracked hitboxes and the interpolated render pose. */
    public Vec3 headPartOffset(int slot, float yaw, float scale) {
        float d = development();
        switch(getStage()) {
            case A:
                Vec3 center = commandHeadOffset(slot, yaw, scale);
                return Vec3.createVectorHelper(center.xCoord, center.yCoord - 0.5F * commandHeadSize(slot) * scale, center.zCoord);
            case A_TRANSFORMED:
                return WitherStormMath.headOffset((slot - 1) * 1.10D * scale,
                    (slot == 1 ? 0.92D : 1.275D) * scale, (slot == 1 ? -1.23D : -0.27D) * scale, yaw, 0F);
            case B:
                return WitherStormDestroyerPose.headBase(slot, d, scale, yaw);
            case C_SMALL:
                return WitherStormDevourerPose.headBase(slot, d, scale, yaw);
            case C_BIG:
                return WitherStormBigDevourerPose.headBase(slot, d, scale, yaw);
            default:
                return WitherStormMath.headOffset((slot - 1) * (1.50D + 9D * d) * scale,
                    ((slot == 1 ? 2.05D : 2D) + 2D * d) * scale, -((slot == 1 ? 3.53D : 3.28D) + 9D * d) * scale, yaw, 0F);
        }
    }

    /** Shared skull centers for the shoulder joints, part positions and client rendering. */
    public static Vec3 commandHeadOffset(int slot, float yaw, float scale) {
        return WitherStormMath.headOffset((slot - 1) * scale,
            (slot == 1 ? 34D : 30D) * scale / 16D, (slot == 1 ? -3.5D : 1D) * scale / 16D, yaw, 0);
    }
    public Vec3 tentacleRoot(int slot) {
        if(getStage().hasPosedTentacles()) return posedArmPosition(slot, 0D);
        double angle = (slot - 3) * Math.PI * 2D / 7D;
        return local(Math.cos(angle) * (1D + development() * 5D) * getStormScale(),
            (2D + development() * 3D) * getStormScale(), Math.sin(angle) * (1D + development() * 4D) * getStormScale());
    }
    Vec3 posedArmPosition(int slot, double t) {
        double time = worldObj.getTotalWorldTime();
        posedArmPoint(slot, t, time, 1F, armSample);
        float size = getStage() == WitherStormStage.B ? WitherStormDestroyerPose.size(development())
            : getStage() == WitherStormStage.C_BIG ? WitherStormBigDevourerPose.size(development())
            : WitherStormDevourerPose.size(development());
        float scale = size * getStormScale();
        return local(armSample[0] * scale, armSample[1] * scale, armSample[2] * scale);
    }

    public float tentacleActivity(float partial) {
        return previousTentacleActivity + (tentacleActivity - previousTentacleActivity) * partial;
    }

    /** Existing terrain packets may add a cosmetic grip between the tracked endpoints. */
    public void capturedBlocksVisual() { visualCaptureUntil = worldObj.getTotalWorldTime() + 35L; }
    public float tentacleCapture(float partial) {
        return previousVisualCapture + (visualCapture - previousVisualCapture) * partial;
    }

    /** Shared by the retained models and server contact points; no client classes or per-sample allocations. */
    public void posedArmPoint(int slot, double t, double time, float partial, double[] out) {
        if(getStage() == WitherStormStage.B) {
            WitherStormDestroyerPose.armPoint(slot, t, time, out);
        } else if(getStage() == WitherStormStage.C_BIG) {
            WitherStormBigDevourerPose.armPoint(slot, t, time, out);
        } else {
            WitherStormDevourerPose.armPoint(slot, t, time, out);
        }
        double strength = tentacleActivity(partial);
        double stroke = WitherStormTentacleMotion.stroke(slot, t, time) * strength;
        Entity target = worldObj.getEntityByID(headTarget((slot - 3) % 3));
        if(target != null) {
            double dx = target.posX - posX, dz = target.posZ - posZ;
            double yaw = Math.toRadians(rotationYaw), scale = Math.max(1D, getStormScale());
            out[0] += WitherStormMath.limit((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / scale, 3D) * stroke;
            out[1] += WitherStormMath.limit((target.posY - posY) / scale, 3D) * stroke;
            out[2] += WitherStormMath.limit((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / scale, 3D) * stroke;
        }
        out[1] += Math.sin(time * 0.085D + slot * 2.4D - t * 4.5D) * strength * t * t * 1.1D;
        // Visual terrain gripping never moves the server's contact endpoints.
        double grip = Math.sin(Math.PI * t) * tentacleCapture(partial)
            * Math.sin(time * 0.09D + slot * 2.4D - t * 3D);
        out[1] += grip * 1.4D;
        out[2] -= grip * 1.8D;
    }
    public Vec3 mouth(int head) {
        if(parts[head] != null && !parts[head].isDead) return parts[head].mouth(1F);
        if(getPhase() == WitherStormPhase.COMMAND_BLOCK) {
            float scale = getStormScale();
            Vec3 center = commandHeadOffset(head, rotationYaw, scale);
            Vec3 front = WitherStormMath.headOffset(0, 0, -0.51D * commandHeadSize(head) * scale, rotationYaw, 0);
            return Vec3.createVectorHelper(posX + center.xCoord + front.xCoord, posY + center.yCoord, posZ + center.zCoord + front.zCoord);
        }
        Vec3 v = partPosition(head);
        Vec3 offset;
        if(getStage().hasShallowHeads()) {
            float scale = headModelScale(head, 0F, getStormScale());
            offset = WitherStormMath.headOffset(0, WitherStormDestroyerPose.MOUTH_Y * scale,
                WitherStormDestroyerPose.MOUTH_Z * scale, rotationYaw, WitherStormDestroyerPose.REST_PITCH);
        } else offset = WitherStormMath.headOffset(0, 0, -1.5D * getStormScale(), rotationYaw, 0);
        return Vec3.createVectorHelper(v.xCoord + offset.xCoord, v.yCoord + 0.6D * getStormScale(), v.zCoord + offset.zCoord);
    }
    private Vec3 local(double x, double y, double z) {
        double a = rotationYaw * Math.PI / 180D, c = Math.cos(a), s = Math.sin(a);
        return Vec3.createVectorHelper(posX + x * c - z * s, posY + y, posZ + x * s + z * c);
    }
    private int skullCooldownForPhase() {
        int base = WitherStormConfig.attackTicks;
        return Math.max(40, base - base * getPhase().ordinal() / 8);
    }

    EntityLivingBase windingUpSkullTarget(int head) {
        return skullHead == head && skullTarget == targets[head] ? skullTarget : null;
    }

    private boolean canFireSkull(EntityWitherStormPart part, EntityLivingBase target) {
        int range = WitherStormConfig.skullAttackRange;
        if(part == null || !part.ready() || !validTarget(target) || getDistanceSqToEntity(target) > range * range) return false;
        if(headTransformed(part.slot()) && headDevelopment(part.slot(), 0) < 1F) return false;
        Vec3 mouth = part.skullMouth(this);
        return WitherStormMath.targetPoint(target, 1F).squareDistanceTo(mouth) <= range * range
            && part.skullAim(target, this) != null && canEntityBeSeen(target);
    }

    private void updateSkullAttack() {
        if(skullHead < 0 && skullCooldown > 0 && --skullCooldown > 0) return;
        if(skullHead >= 0) {
            if(--skullWindup > 0) return;
            EntityWitherStormPart part = parts[skullHead];
            if(part != null) part.setSkullWindingUp(false);
            EntityLivingBase target = windingUpSkullTarget(skullHead);
            if(canFireSkull(part, target)) fireSkull(part, target);
        }
        for(int head = skullHead + 1; head < 3; head++) {
            if(!canFireSkull(parts[head], targets[head])) continue;
            skullHead = head;
            skullTarget = targets[head];
            skullWindup = SKULL_WINDUP_TICKS;
            parts[head].setSkullWindingUp(true);
            return;
        }
        if(skullHead >= 0) skullCooldown = skullCooldownForPhase();
        skullHead = -1;
        skullTarget = null;
    }

    private void fireSkull(EntityWitherStormPart part, EntityLivingBase target) {
        Vec3 mouth = part.skullMouth(this);
        Vec3 direction = WitherStormMath.headOffset(0, 0, -1, part.headYaw(1F, this), part.headPitch(1F, this));
        // Clear the face by the projectile's radius, independent of the Storm's size.
        Vec3 spawn = mouth.addVector(direction.xCoord * 0.45D, direction.yCoord * 0.45D, direction.zCoord * 0.45D);
        if(!part.skullPathClearsBody(this, mouth, spawn, direction)) return;
        double reach = spawn.distanceTo(WitherStormMath.targetPoint(target, 1F)) + target.width + target.height;
        Vec3 end = spawn.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach);
        if(target.boundingBox.expand(0.3D, 0.3D, 0.3D).calculateIntercept(spawn, end) == null
            || !WitherStormMath.areaLoaded(worldObj, spawn.xCoord, spawn.zCoord, 34)) return;
        EntityWitherStormSkull skull = new EntityWitherStormSkull(worldObj);
        skull.launch(this, spawn, direction);
        if(worldObj.spawnEntityInWorld(skull)) worldObj.playSoundEffect(mouth.xCoord, mouth.yCoord, mouth.zCoord, "hbm:entity.witherStorm.shoot", 1F, 1F);
        else skull.setDead();
    }
    private void interact() {
        int active = 0;
        for(int h = 0; h < 3; h++) {
            EntityLivingBase target = targets[h];
            EntityWitherStormPart part = parts[h];
            if(!headTransformed(h) || headDevelopment(h, 0) < 1F || !validTarget(target) || part == null || part.isDead || !part.ready()) continue;
            Vec3 m = part.eye(1F), end = WitherStormMath.targetPoint(target, 1F);
            double dx = end.xCoord - m.xCoord, dy = end.yCoord - m.yCoord, dz = end.zCoord - m.zCoord;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            Vec3 forward = WitherStormMath.headOffset(0, 0, -1, part.headYaw(1F, this), part.headPitch(1F, this));
            if(!Double.isFinite(length) || length < 0.1D || length > 48D
                || (dx * forward.xCoord + dy * forward.yCoord + dz * forward.zCoord) / length < 0.985D) continue;
            active |= 1 << h;
            if(WitherStormMath.edible(target, worldObj)) WitherStormMath.pull(target, m.xCoord, m.yCoord, m.zCoord, 0.075D + getPhase().ordinal() * 0.015D);
        }
        dataWatcher.updateObject(28, (byte)active);
        for(int index = -3; index < nearby.size(); index++) {
            Entity e = index < 0 ? targets[index + 3] : nearby.get(index);
            if(index >= 0 && (e == targets[0] || e == targets[1] || e == targets[2])) continue;
            if(!WitherStormMath.edible(e, worldObj) || getDistanceSqToEntity(e) > 80D * 80D) continue;
            Vec3 nearest = mouth(0); double best = e.getDistanceSq(nearest.xCoord, nearest.yCoord, nearest.zCoord);
            for(int h = 1; h < 3; h++) { Vec3 m = mouth(h); double d = e.getDistanceSq(m.xCoord, m.yCoord, m.zCoord); if(d < best) { best = d; nearest = m; } }
            // Debris is gathered autonomously; living forces belong only to their
            // selected active head and are applied exactly once above.
            if((e instanceof EntityItem || e instanceof EntityFallingBlock) && best < 48D * 48D)
                WitherStormMath.pull(e, nearest.xCoord, nearest.yCoord, nearest.zCoord, 0.075D + getPhase().ordinal() * 0.015D);
            boolean touching = best < Math.pow(1.6D * getStormScale(), 2) || boundingBox.intersectsWith(e.boundingBox);
            boolean devouring = false;
            if(getPhase().ordinal() >= 2) for(int t = 8; t < 10; t++) if(parts[t] != null && parts[t].getDistanceSqToEntity(e) < 16D * getStormScale() * getStormScale()) { touching = true; devouring = true; }
            if(touching && (e instanceof EntityItem || e instanceof EntityFallingBlock)) {
                int mass = e instanceof EntityItem ? Math.min(64, ((EntityItem)e).getEntityItem().stackSize) : 3;
                if(e instanceof EntityFallingBlock) ((EntityFallingBlock)e).field_145813_c = false;
                e.setDead(); addMass(mass); continue;
            }
            if(touching && e instanceof EntityLivingBase && ticksExisted % 10 == 0) {
                EntityLivingBase living = (EntityLivingBase)e;
                boolean hurt = living.attackEntityFrom(DamageSource.causeMobDamage(this), attackStrength() * (devouring ? 2F : 1F));
                if(hurt && !living.isEntityAlive()) { addMass(Math.max(10, Math.min(200, (int)living.getMaxHealth()))); heal(4F); }
            }
            if(ticksExisted % 20 == 0 && e instanceof EntityLivingBase && getPhase() != WitherStormPhase.COMMAND_BLOCK) {
                for(int t = 3; t < 10; t++) if(parts[t] != null && parts[t].getDistanceSqToEntity(e) < 9D * getStormScale() * getStormScale()) {
                    if(e.attackEntityFrom(DamageSource.causeMobDamage(this), attackStrength())) {
                        ((EntityLivingBase)e).addPotionEffect(new PotionEffect(Potion.wither.id, 100, 0));
                        if(t == 3 || t == 8) playSound("hbm:entity.witherStorm.tentacle", 0.8F, 1F);
                    }
                    break;
                }
            }
        }
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if(worldObj.isRemote || source.getEntity() instanceof EntityWitherStorm || source.getEntity() instanceof EntityWitherStormPart || source.getSourceOfDamage() instanceof EntityWitherStormSkull || !Float.isFinite(damage)) return false;
        float health = getHealth();
        boolean hit = super.attackEntityFrom(source, damage);
        long tick = worldObj.getTotalWorldTime();
        if(hit && isEntityAlive() && getHealth() < health && tick >= nextHurtSoundTick) {
            nextHurtSoundTick = tick + 12;
            super.playSound(HURT_SOUND, 3F + 3F * development(), getSoundPitch());
        }
        return hit;
    }
    @Override protected String getLivingSound() { return "hbm:entity.witherStorm.ambient"; }
    @Override protected String getHurtSound() { return HURT_SOUND; }
    @Override public void playSound(String sound, float volume, float pitch) {
        // Both vanilla's damage handler and the client's hurt animation call this.
        if(!HURT_SOUND.equals(sound)) super.playSound(sound, volume, pitch);
    }
    @Override protected String getDeathSound() { return "hbm:entity.witherStorm.death"; }
    @Override protected float getSoundVolume() { return 1.5F; }
    @Override public int getTalkInterval() { return 200; }
    @Override public void onDeath(DamageSource source) {
        if(!worldObj.isRemote) { dataWatcher.updateObject(22, (byte)0); WitherStormEncounters.get(worldObj).remove(getUniqueID()); }
        super.onDeath(source);
    }
    @Override protected void dropFewItems(boolean player, int looting) { entityDropItem(new ItemStack(Items.nether_star), 0); }
    @Override public void setDead() {
        boolean owner = !worldObj.isRemote && registered && WitherStormEncounters.get(worldObj).find(getUniqueID(), worldObj) == this;
        super.setDead();
        if(!worldObj.isRemote) {
            if(flightTicket != null) { ForgeChunkManager.releaseTicket(flightTicket); flightTicket = null; }
            for(EntityWitherStormPart part : parts) if(part != null) part.setDead();
            WitherStormEncounters.get(worldObj).untrack(this);
            if(owner) WitherStormEncounters.get(worldObj).remove(getUniqueID());
        }
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("Growth", getGrowth()); n.setString("Phase", getPhase().name()); n.setBoolean("Core", hasCore()); n.setBoolean("Registered", registered);
        n.setInteger("Transition", transitionTicks()); n.setInteger("RoarTimer", roarTimer);
        n.setInteger("StageBTicks", stageBTicks);
        n.setInteger("GrowthSoundMass", lastGrowthSoundMass);
        n.setByte("TransformedHeads", dataWatcher.getWatchableObjectByte(29));
        n.setByte("TransformingHeads", dataWatcher.getWatchableObjectByte(31));
        WitherStormMath.uuid(n, "Encounter", getUniqueID());
        for(int i = 0; i < 10; i++) { WitherStormMath.uuid(n, "Part" + i, partIds[i]); n.setInteger("PartX" + i, partChunksX[i]); n.setInteger("PartZ" + i, partChunksZ[i]); }
        for(int i = 0; i < 3; i++) WitherStormMath.uuid(n, "Target" + i, targetIds[i]);
        n.setInteger("SkullCooldown", skullHead >= 0 ? skullCooldownForPhase() : skullCooldown);
        terrain.write(n);
        if(pursuit != null) {
            NBTTagCompound tracking = new NBTTagCompound();
            pursuit.write(tracking);
            n.setTag("ChallengePursuit", tracking);
        }
    }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        float savedHealth = getHealth();
        restoring = true;
        boolean savedDestroyer = n.hasKey("StageBTicks") && WitherStormPhase.DESTROYER.name().equals(n.getString("Phase"));
        updateGrowth(n.getInteger("Growth"), savedDestroyer);
        restoring = false;
        setHealth(savedHealth);
        // Legacy saves derive their head state from mass; existing identity and growth remain intact.
        if(n.hasKey("TransformedHeads")) dataWatcher.updateObject(29, (byte)pairSideHeads(n.getByte("TransformedHeads") | headsForGrowth(getGrowth())));
        stageBTicks = getStage() == WitherStormStage.B ? Math.max(0, n.getInteger("StageBTicks")) : 0;
        dataWatcher.updateObject(31, (byte)(n.getByte("TransformingHeads") & dataWatcher.getWatchableObjectByte(29)));
        registered = n.getBoolean("Registered"); dataWatcher.updateObject(22, (byte)(!n.hasKey("Core") || n.getBoolean("Core") ? 1 : 0));
        lastGrowthSoundMass = n.getInteger("GrowthSoundMass");
        dataWatcher.updateObject(23, Math.max(0, Math.min(80, n.getInteger("Transition")))); roarTimer = Math.max(40, Math.min(500, n.getInteger("RoarTimer")));
        for(int i = 0; i < 10; i++) { partIds[i] = WitherStormMath.uuid(n, "Part" + i); partChunksX[i] = n.getInteger("PartX" + i); partChunksZ[i] = n.getInteger("PartZ" + i); }
        for(int i = 0; i < 3; i++) targetIds[i] = WitherStormMath.uuid(n, "Target" + i);
        skullCooldown = n.hasKey("SkullCooldown") ? Math.max(0, Math.min(12000, n.getInteger("SkullCooldown"))) : skullCooldownForPhase();
        visualScale = previousVisualScale = getStormScale(); terrain.read(n);
        if(n.hasKey("ChallengePursuit", 10)) {
            startPursuit();
            pursuit.read(n.getCompoundTag("ChallengePursuit"));
        }
    }

    private static int pairSideHeads(int heads) {
        // Older saves could record one side as transformed without its partner.
        return (heads & 5) != 0 ? 7 : heads & 2;
    }
}
