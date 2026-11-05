package com.hbm.entity.mob.magmagolem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hbm.config.MagmaGolemConfig;
import com.hbm.entity.mob.magmagolem.MagmaGolemPose.Action;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;

import static com.hbm.entity.mob.magmagolem.MagmaGolemPose.*;

public final class EntityGiantMagmaGolem extends EntityMob implements IBossDisplayData, IEntityMultiPart, IEntityAdditionalSpawnData {
    public static final float HEIGHT = 12, WIDTH = 4.65F;
    private static final int MOTION = 20, ARMS = 21, GAIT = 22, AIM = 23, PHASE = 24, BEAM = 25, SLAM_GROUND = 26;
    private static final int LANDED_BIT = 1 << 12;
    private static final int MOLTEN = 0, COOLED = 1, MISSING = 2;
    private static final int LEFT_UPPER = 0, LEFT_FIST = 1, RIGHT_UPPER = 2, RIGHT_FIST = 3, LEFT_FOOT = 4, RIGHT_FOOT = 5;
    private static final byte IMPACT = 75, QUAKE = 76, LEAP_LAND = 77;
    private static final double TAU = Math.PI * 2;
    private static final Action[] ACTIONS = Action.values();

    private final MagmaGolemPose pose = new MagmaGolemPose();
    private final EntityDragonPart[] parts = new EntityDragonPart[6];
    private final int[] cooling = new int[2], armWork = new int[2], mineDelay = new int[2];
    private final Set<Integer> struck = new HashSet<Integer>();
    private final Map<UUID, Long> summons = new HashMap<UUID, Long>();
    private EntityLivingBase target;
    private UUID finisher;
    private Action action = Action.IDLE, previousAttack = Action.IDLE;
    private int age, recovery = 30, summonWait = 90, blocked, detourTicks, tntStress, soaked, landedAt;
    private boolean landed, finishing;
    private float stride, previousStride, pace, previousPace, aimYaw, aimPitch, beamPitch, beamYaw;
    private double detourX, detourZ;

    public EntityGiantMagmaGolem(World world) {
        super(world);
        setSize(WIDTH, HEIGHT);
        stepHeight = 1.25F;
        isImmuneToFire = true;
        experienceValue = 100;
        scoreValue = 100;
        for (int i = 0; i < parts.length; i++) parts[i] = new Part(i);
        updateParts();
    }

    @Override protected void entityInit() {
        super.entityInit();
        for (int slot = MOTION; slot <= SLAM_GROUND; slot++) dataWatcher.addObject(slot, slot == PHASE ? 1 : 0);
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MagmaGolemConfig.health);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(MagmaGolemConfig.melee);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(MagmaGolemConfig.speed);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
    }

    @Override protected boolean isAIEnabled() { return true; }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isPushedByWater() { return false; }
    @Override public World func_82194_d() { return worldObj; }
    @Override public Entity[] getParts() { return parts; }
    @Override public boolean isEntityInsideOpaqueBlock() { return false; }
    @Override protected void fall(float distance) { }
    @Override public float getEyeHeight() { return 10.85F; }
    @Override protected String getHurtSound() { return "mob.irongolem.hit"; }
    @Override protected String getDeathSound() { return "mob.irongolem.death"; }
    @Override protected float getSoundVolume() { return 2.5F; }
    @Override protected float getSoundPitch() { return 0.64F; }
    @Override public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : StatCollector.translateToLocal("entity.hbm.giant_magma_golem.name");
    }

    public boolean hasSpawnClearance() {
        return posY >= 1 && posY + HEIGHT <= worldObj.getActualHeight()
            && MagmaGolemTerrain.loaded(worldObj, boundingBox.expand(2.5, 0, 2.5))
            && worldObj.func_147461_a(boundingBox.expand(2.5, 0, 2.5)).isEmpty();
    }

    @Override public boolean getCanSpawnHere() {
        return MagmaGolemConfig.natural && worldObj.provider.dimensionId == 0 && rand.nextInt(24) == 0
            && hasSpawnClearance() && !worldObj.isAnyLiquid(boundingBox) && super.getCanSpawnHere()
            && worldObj.getEntitiesWithinAABB(EntityGiantMagmaGolem.class, boundingBox.expand(96, 64, 96)).isEmpty();
    }

    @Override public int getMaxSpawnedInChunk() { return 1; }

    public Action action() { return worldObj.isRemote ? ACTIONS[dataWatcher.getWatchableObjectInt(MOTION) & 15] : action; }
    public int animationAge() { return worldObj.isRemote ? (dataWatcher.getWatchableObjectInt(MOTION) >> 4) & 255 : age; }
    public int phase() { return dataWatcher.getWatchableObjectInt(PHASE); }
    public boolean cooled(int side) { return arm(side) == COOLED; }
    public boolean hasArm(int side) { return arm(side) != MISSING; }

    private int arm(int side) { return (dataWatcher.getWatchableObjectInt(ARMS) >> (side == LEFT ? 0 : 2)) & 3; }
    private boolean armed() { return hasArm(LEFT) || hasArm(RIGHT); }
    private boolean hasBothArms() { return hasArm(LEFT) && hasArm(RIGHT); }

    private void setArm(int side, int state) {
        int shift = side == LEFT ? 0 : 2;
        dataWatcher.updateObject(ARMS, (dataWatcher.getWatchableObjectInt(ARMS) & ~(3 << shift)) | (state << shift));
    }

    private void syncAction() {
        dataWatcher.updateObject(MOTION, action.ordinal() | (age << 4) | (landed ? LANDED_BIT : 0));
    }

    private void begin(Action next) {
        action = next;
        age = 0;
        landed = false;
        landedAt = 0;
        struck.clear();
        if (next != Action.SLAM) dataWatcher.updateObject(SLAM_GROUND, 0);
        if (next != Action.LEAP) motionX = motionZ = 0;
        syncAction();
    }

    private void recover() {
        if (action.ordinal() < Action.STAGGER.ordinal()) previousAttack = action;
        begin(Action.IDLE);
        recovery = Math.max(10, MagmaGolemConfig.recovery - 8 * (phase() - 1));
    }

    @Override public void onLivingUpdate() {
        double x = posX, z = posZ;
        super.onLivingUpdate();
        if (isDead) return;
        previousStride = stride;
        previousPace = pace;
        if (worldObj.isRemote) {
            int gait = dataWatcher.getWatchableObjectInt(GAIT);
            stride = (gait & 1023) * (float)(TAU / 1024);
            pace = (gait >> 10) / 100F;
            int aim = dataWatcher.getWatchableObjectInt(AIM);
            aimYaw = ((short)aim) * 0.001F;
            aimPitch = ((short)(aim >> 16)) * 0.001F;
            int beam = dataWatcher.getWatchableObjectInt(BEAM);
            beamYaw = ((short)beam) * 0.001F;
            beamPitch = ((short)(beam >> 16)) * 0.001F;
            updateStance();
            updateParts();
            clientEffects();
            return;
        }
        if (!isEntityAlive() || finishing) return;
        updateGait(Math.hypot(posX - x, posZ - z));
        updatePhase();
        updateStance();
        updateParts();
        if (ticksExisted % 40 == 0) pruneSummons();
        if (slamHold() || ticksExisted % 4 == 0) checkWater();
        if (finishing) return;
        if (recovery > 0) recovery--;
        if (summonWait > 0) summonWait--;
        for (int i = 0; i < mineDelay.length; i++) if (mineDelay[i] > 0) mineDelay[i]--;
        if (action != Action.IDLE) {
            tickAction();
            return;
        }
        if (ticksExisted % 20 == 0 || target != null && !targetable(target)) findTarget();
        if (!targetable(target)) {
            motionX = motionZ = 0;
            return;
        }
        faceTarget(8);
        if (recovery == 0 && onGround && selectAttack()) return;
        pursue();
    }

    @Override protected void updateAITasks() {
        // Superclass travel applies gravity and resolves terrain. This encounter supplies its own steering.
        if (action != Action.IDLE && (action != Action.LEAP || age < action.strike || landed)) motionX = motionZ = 0;
    }

    @Override public void moveEntityWithHeading(float strafe, float forward) {
        // The terrain contacts are locked to this origin and yaw from windup through recovery.
        Action active = action();
        if (active == Action.SLAM || active == Action.DYING) {
            motionX = motionY = motionZ = 0;
            return;
        }
        super.moveEntityWithHeading(strafe, forward);
    }

    private void updatePhase() {
        float fraction = getHealth() / getMaxHealth();
        int next = fraction < 0.35F ? 3 : fraction <= 0.70F ? 2 : 1;
        if (next > phase()) {
            dataWatcher.updateObject(PHASE, next);
            recovery = Math.min(recovery, 10);
            playSound("mob.irongolem.hit", 3, next == 3 ? 0.48F : 0.62F);
        }
    }

    private void updateGait(double travelled) {
        boolean walking = action == Action.IDLE && travelled > 0.025 && onGround;
        pace += ((walking ? Math.min(1, travelled * 2.5) : 0) - pace) * 0.35F;
        if (walking) {
            float next = stride + (float)(travelled * 1.85);
            if ((int)(next / STEP) != (int)(stride / STEP)) {
                playSound("mob.irongolem.walk", 2.6F, 0.58F);
                worldObj.setEntityState(this, (byte)(78 + ((int)(next / STEP) & 1)));
            }
            stride = (float)(next % TAU);
        }
        dataWatcher.updateObject(GAIT, ((int)Math.round(pace * 100) << 10) | ((int)(stride * 1024 / TAU) & 1023));
    }

    private boolean eligible(EntityLivingBase entity) {
        if (entity == null || entity == this || !entity.isEntityAlive() || entity.worldObj != worldObj
                || entity instanceof EntityGiantMagmaGolem || isOnSameTeam(entity)) return false;
        if (entity instanceof EntityGolemMagmaCube && ((EntityGolemMagmaCube)entity).belongsTo(getUniqueID())) return false;
        return !(entity instanceof EntityPlayer) || !((EntityPlayer)entity).capabilities.disableDamage;
    }

    private boolean targetable(EntityLivingBase entity) {
        return eligible(entity) && getDistanceSqToEntity(entity) <= 48 * 48;
    }

    private void findTarget() {
        EntityLivingBase best = targetable(target) ? target : null;
        for (Object candidate : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(48, 24, 48))) {
            EntityLivingBase entity = (EntityLivingBase)candidate;
            if ((entity instanceof EntityPlayer || entity instanceof EntityVillager || entity instanceof IMob || entity == getAITarget())
                    && targetable(entity) && (best == null || targetScore(entity) < targetScore(best))) best = entity;
        }
        target = best;
    }

    private double targetScore(EntityLivingBase entity) {
        return getDistanceSqToEntity(entity) * (entity == getAITarget() ? 0.12 : entity instanceof EntityPlayer ? 0.25 : 1);
    }

    private void faceTarget(float turn) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float offset = action == Action.PUNCH_LEFT ? 25 : action == Action.PUNCH_RIGHT ? -25 : 0;
        rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw + offset - rotationYaw), -turn, turn);
        aimYaw = (float)Math.toRadians(MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(rotationYaw - yaw), -32, 32));
        aimPitch = (float)Math.atan2(posY + 10 - center(target).yCoord, Math.max(5, Math.hypot(dx, dz)));
        aimPitch = MathHelper.clamp_float(aimPitch, -0.38F, 0.46F);
        dataWatcher.updateObject(AIM, ((int)(aimPitch * 1000) << 16) | ((int)(aimYaw * 1000) & 65535));
    }

    private void pursue() {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double distance = Math.hypot(dx, dz);
        if (distance < 2.8 && Math.abs(target.posY - posY) < 3) {
            motionX = motionZ = 0;
            blocked = detourTicks = 0;
            return;
        }
        if (distance < 0.01) return;
        double speed = getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue()
            * (1 + (phase() - 1) * 0.29) * (distance > 13 && phase() > 1 ? 1.3 : 1);
        double hx = dx / distance, hz = dz / distance;
        if (detourTicks > 0 && !isCollidedHorizontally) {
            detourTicks--;
            drive(detourX * speed, detourZ * speed);
            return;
        }
        detourTicks = 0;
        if (isCollidedHorizontally) blocked++; else blocked = Math.max(0, blocked - 1);
        if (blocked >= 3 && onGround) {
            if (clearPath(hx * 1.25, hz * 1.25, 2.5)) {
                motionY = 0.73;
            } else {
                boolean detoured = false;
                for (int i = 1; i <= 4; i++) {
                    int side = ((i & 1) == 0 ? -1 : 1) * ((getEntityId() & 1) == 0 ? -1 : 1);
                    double angle = side * (i <= 2 ? 0.78 : 1.52);
                    double sx = hx * Math.cos(angle) - hz * Math.sin(angle);
                    double sz = hx * Math.sin(angle) + hz * Math.cos(angle);
                    if (!clearPath(sx * 1.5, sz * 1.5, 0)) continue;
                    hx = sx;
                    hz = sz;
                    detourX = sx;
                    detourZ = sz;
                    detourTicks = i <= 2 ? 10 : 22;
                    detoured = true;
                    break;
                }
                if (!detoured && blocked % 10 == 0) {
                    AxisAlignedBB ahead = boundingBox.addCoord(hx * 1.15, 0, hz * 1.15);
                    AxisAlignedBB obstruction = AxisAlignedBB.getBoundingBox(ahead.minX, posY, ahead.minZ,
                        ahead.maxX, posY + 5, ahead.maxZ);
                    MagmaGolemTerrain.strike(this, obstruction, 9, false);
                }
            }
        }
        drive(hx * speed, hz * speed);
    }

    private boolean clearPath(double x, double z, double rise) {
        AxisAlignedBB sweep = boundingBox.getOffsetBoundingBox(0, rise, 0).addCoord(x, 0, z);
        return MagmaGolemTerrain.loaded(worldObj, sweep) && worldObj.func_147461_a(sweep).isEmpty();
    }

    private void drive(double x, double z) {
        boolean loaded = MagmaGolemTerrain.loaded(worldObj, boundingBox.addCoord(x, 0, z).expand(0.1, 0, 0.1));
        motionX = loaded ? x : 0;
        motionZ = loaded ? z : 0;
    }

    private boolean selectAttack() {
        double range = Math.hypot(target.posX - posX, target.posZ - posZ);
        boolean low = target.posY < posY + 4 && target.posY > posY - 2.5;
        boolean canSlam = armed() && low && range <= 10;
        if (canSlam && previousAttack != Action.SLAM && startSlam()) return true;
        Vec3 mouth = muzzle();
        boolean clear = MagmaGolemTerrain.visible(worldObj, mouth, center(target));
        int roll = rand.nextInt(100);
        Action next;
        if (MagmaGolemConfig.cubeLimit > 0 && summonWait == 0 && summons.size() < MagmaGolemConfig.cubeLimit
                && range < 23 && roll < 22) next = Action.SUMMON;
        else if (low && range <= 5 && (!armed() || phase() == 3 && roll < 35 || !hasBothArms() && range < 3)) next = Action.STOMP;
        else if (low && range <= 7.5 && armed() && (phase() == 1 || roll < 65)) next = choosePunch();
        else if ((phase() >= 2 || !armed()) && range >= 6 && range <= 20 && low
                && (roll < 33 || blocked >= 5) && previousAttack != Action.LEAP) next = Action.LEAP;
        else if (clear && phase() >= 2 && range <= 16 && roll < 65 && previousAttack != Action.BREATH) next = Action.BREATH;
        else if (clear && range <= 44) next = Action.FIREBALL;
        else if (low && range <= 7 && armed()) next = choosePunch();
        else return canSlam && previousAttack == Action.SLAM && startSlam();
        begin(next);
        playSound(next == Action.FIREBALL || next == Action.BREATH ? "fire.ignite" : "mob.irongolem.hit", 2.7F,
            next == Action.LEAP ? 0.48F : 0.78F);
        return true;
    }

    private boolean startSlam() {
        int ground = findSlamSurface(LEFT) | (findSlamSurface(RIGHT) << 10);
        if (ground != 0) {
            dataWatcher.updateObject(SLAM_GROUND, ground);
            begin(Action.SLAM);
        }
        updateParts();
        if (ground == 0) return false;
        playSound("mob.irongolem.hit", 2.7F, 0.48F);
        return true;
    }

    private int findSlamSurface(int side) {
        if (!hasArm(side)) return 0;
        double yaw = Math.toRadians(rotationYaw);
        double x = posX + slamLateral(side) * Math.cos(yaw) - SLAM_FORWARD * Math.sin(yaw);
        double z = posZ + slamLateral(side) * Math.sin(yaw) + SLAM_FORWARD * Math.cos(yaw);
        AxisAlignedBB probe = AxisAlignedBB.getBoundingBox(x - 0.12, posY - 1.01, z - 0.12,
            x + 0.12, posY + 1.26, z + 0.12);
        if (!MagmaGolemTerrain.loaded(worldObj, probe)) return 0;
        double top = Double.NEGATIVE_INFINITY;
        for (Object candidate : worldObj.func_147461_a(probe)) {
            AxisAlignedBB surface = (AxisAlignedBB)candidate;
            if (surface.maxY <= posY + 1.25) top = Math.max(top, surface.maxY);
        }
        if (top < posY - 1) return 0;
        int surface = SLAM_HEIGHT_BIAS + (int)Math.ceil((top - posY) * SLAM_HEIGHT_STEPS);
        int ground = side == LEFT ? surface : surface << 10;
        pose.sample(Action.SLAM, Action.SLAM.strike, ticksExisted, stride, 0, aimYaw, aimPitch,
            hasArm(LEFT), hasArm(RIGHT), phase(), false, ground);
        AxisAlignedBB fist = fistBox(side).contract(0.01, 0.01, 0.01);
        return MagmaGolemTerrain.loaded(worldObj, fist) && worldObj.func_147461_a(fist).isEmpty() ? surface : 0;
    }

    private boolean slamHold() {
        return action() == Action.SLAM && animationAge() >= Action.SLAM.strike
            && animationAge() < Action.SLAM.strike + SLAM_HOLD;
    }

    private boolean slamsWith(int side) {
        return hasArm(side) && slamSurface(dataWatcher.getWatchableObjectInt(SLAM_GROUND), side) != 0;
    }

    private Action choosePunch() {
        double yaw = Math.toRadians(rotationYaw);
        double lateral = (target.posX - posX) * Math.cos(yaw) + (target.posZ - posZ) * Math.sin(yaw);
        return hasArm(LEFT) && (!hasArm(RIGHT) || lateral > 0.25 || lateral >= -0.25 && previousAttack != Action.PUNCH_LEFT)
            ? Action.PUNCH_LEFT : Action.PUNCH_RIGHT;
    }

    private void tickAction() {
        if (action == Action.DOWN) {
            if (age < action.length) age++;
            syncAction();
            return;
        }
        age++;
        if (action != Action.SLAM && targetable(target) && age <= action.strike - 6) faceTarget(6);
        if (age == action.strike - 5 && (action == Action.BREATH || action == Action.FIREBALL)) lockMuzzle();
        if (action == Action.LEAP && age == action.strike) launchLeap();
        if (action == Action.LEAP && age > action.strike && !landed && (onGround || isInWater() && age >= action.strike + 6)) {
            landed = true;
            landedAt = age;
            groundImpact(LEAP_LAND);
        }
        if (action == Action.LEAP && !landed && age >= action.length) age = action.length;
        syncAction();
        updateParts();
        switch (action) {
            case PUNCH_LEFT:
            case PUNCH_RIGHT:
                if (age >= action.strike - 2 && age <= action.strike + 2) punch(action == Action.PUNCH_LEFT ? LEFT : RIGHT);
                if (age == action.strike) impactSound(IMPACT);
                break;
            case SLAM:
                if (age >= action.strike - 2 && age <= action.strike) {
                    if (slamsWith(LEFT)) limbContact(LEFT, 1.35F);
                    if (slamsWith(RIGHT)) limbContact(RIGHT, 1.35F);
                }
                if (age == action.strike) {
                    groundImpact(QUAKE);
                    hitShockwave(0, phase() == 1 ? 7 : 11, 0.8F);
                    for (int side = LEFT; side <= RIGHT; side += RIGHT - LEFT) {
                        if (!slamsWith(side)) continue;
                        AxisAlignedBB hit = fistBox(side).expand(0.35, 0, 0.35);
                        // Keep the support cell intact, including slabs and other partial blocks.
                        hit.minY = Math.ceil(hit.minY);
                        MagmaGolemTerrain.strike(this, hit, 8, !cooled(side));
                    }
                }
                if (age >= action.strike && age <= action.strike + 8) {
                    int tick = age - action.strike;
                    int radius = phase() == 1 ? 7 : 11;
                    if (tick % 2 == 0 && (slamsWith(LEFT) && !cooled(LEFT) || slamsWith(RIGHT) && !cooled(RIGHT))) {
                        igniteWave(radius * (tick + 1) / 9D);
                    }
                }
                break;
            case STOMP:
                if (age == action.strike) {
                    AxisAlignedBB foot = parts[LEFT_FOOT].boundingBox.expand(0.9, 0.75, 1.1);
                    hitBox(foot, 1.14F, true, 1.15);
                    shockwave(0, 3.5, 0.68F);
                    MagmaGolemTerrain.strike(this, foot.getOffsetBoundingBox(0, -0.6, 0), 7, true);
                    groundImpact(IMPACT);
                }
                break;
            case LEAP:
                if (landed && age - landedAt <= 8) {
                    if (age == landedAt) {
                        MagmaGolemTerrain.strike(this, parts[LEFT_FOOT].boundingBox.expand(0.7, 0.75, 0.7), 5, false);
                        MagmaGolemTerrain.strike(this, parts[RIGHT_FOOT].boundingBox.expand(0.7, 0.75, 0.7), 5, false);
                    }
                    shockwave(age - landedAt, 8, 1.16F);
                }
                if (!landed || age - landedAt < 10) return;
                break;
            case FIREBALL:
                if (age >= action.strike && (age - action.strike) % (phase() == 1 ? 8 : 5) == 0
                        && age - action.strike <= (phase() == 1 ? 16 : 20)) fireball();
                break;
            case BREATH:
                if (age >= action.strike && age < action.length - 7 && age % 5 == 0) breath();
                break;
            case SUMMON:
                if (age == action.strike) summon();
                break;
            default:
                break;
        }
        if (age >= action.length) recover();
    }

    private void launchLeap() {
        double x = -Math.sin(Math.toRadians(rotationYaw)), z = Math.cos(Math.toRadians(rotationYaw));
        double distance = targetable(target) ? Math.hypot(target.posX - posX, target.posZ - posZ) : 8;
        double speed = Math.min(1.24, Math.max(0.48, distance / 12));
        drive(x * speed, z * speed);
        motionY = 0.91;
        isAirBorne = true;
        velocityChanged = true;
    }

    private void punch(int side) {
        limbContact(side, 1);
        if (age == action.strike) {
            AxisAlignedBB fist = fistBox(side);
            MagmaGolemTerrain.strike(this, fist, 8, !cooled(side));
        }
    }

    private void limbContact(int side, float scale) {
        if (!hasArm(side)) return;
        hitBox(fistBox(side), scale, !cooled(side), 0.87);
    }

    private AxisAlignedBB fistBox(int side) {
        return pose.fistBox(side, rotationYaw, posX, posY, posZ);
    }

    private void hitBox(AxisAlignedBB box, float scale, boolean fire, double push) {
        Vec3 origin = Vec3.createVectorHelper((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
        for (Object candidate : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            EntityLivingBase victim = (EntityLivingBase)candidate;
            if (eligible(victim) && MagmaGolemTerrain.visible(worldObj, origin, nearest(victim.boundingBox, origin))) {
                hit(victim, scale, fire, push);
            }
        }
    }

    private void igniteWave(double radius) {
        if (!MagmaGolemTerrain.griefing(worldObj)) return;
        int y = MathHelper.floor_double(posY);
        for (int i = 0; i < 4; i++) {
            double angle = (i + rand.nextDouble()) * TAU / 4;
            int x = MathHelper.floor_double(posX + Math.cos(angle) * radius);
            int z = MathHelper.floor_double(posZ + Math.sin(angle) * radius);
            if (!MagmaGolemTerrain.ignite(worldObj, x, y, z)) MagmaGolemTerrain.ignite(worldObj, x, y + 1, z);
        }
    }

    private void shockwave(int tick, double radius, float scale) {
        double edge = Math.max(2.7, radius * (tick + 1) / 9);
        double inner = tick == 0 ? 0 : Math.max(0, radius * tick / 9 - 1.5);
        hitShockwave(inner, edge, scale);
    }

    private void hitShockwave(double inner, double edge, float scale) {
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(posX - edge, posY - 1.5, posZ - edge, posX + edge, posY + 2.2, posZ + edge);
        for (Object candidate : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            EntityLivingBase victim = (EntityLivingBase)candidate;
            Vec3 foot = nearest(victim.boundingBox, Vec3.createVectorHelper(posX, victim.boundingBox.minY, posZ));
            double d = Math.hypot(foot.xCoord - posX, foot.zCoord - posZ);
            if (eligible(victim) && d <= edge && d >= inner && victim.boundingBox.minY < posY + 1.4
                    && (victim.onGround || victim.isInWater())
                    && MagmaGolemTerrain.visible(worldObj, Vec3.createVectorHelper(posX, posY + 0.55, posZ), center(victim))) {
                hit(victim, scale, false, 1.05);
            }
        }
    }

    private void hit(EntityLivingBase victim, float scale, boolean fire, double push) {
        if (!struck.add(victim.getEntityId())) return;
        float damage = (float)getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue() * scale;
        if (!victim.attackEntityFrom(DamageSource.causeMobDamage(this), damage)) return;
        if (fire) victim.setFire(4);
        double dx = victim.posX - posX, dz = victim.posZ - posZ;
        double length = Math.max(0.1, Math.hypot(dx, dz));
        victim.addVelocity(dx / length * push, 0.31, dz / length * push);
    }

    private void lockMuzzle() {
        if (!targetable(target)) return;
        Vec3 from = muzzle(), to = center(target);
        beamPitch = (float)Math.atan2(to.yCoord - from.yCoord, Math.hypot(to.xCoord - from.xCoord, to.zCoord - from.zCoord));
        beamYaw = (float)Math.toRadians(MathHelper.wrapAngleTo180_double(
            Math.toDegrees(Math.atan2(from.xCoord - to.xCoord, to.zCoord - from.zCoord)) - rotationYaw));
        dataWatcher.updateObject(BEAM, ((int)(beamPitch * 1000) << 16) | ((int)(beamYaw * 1000) & 65535));
    }

    private Vec3 beamDirection(double angle, double pitch) {
        double yaw = Math.toRadians(rotationYaw) + beamYaw + angle;
        return Vec3.createVectorHelper(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private void fireball() {
        double spread = (rand.nextDouble() - 0.5) * (phase() == 1 ? 0.08 : 0.15);
        Vec3 direction = beamDirection(spread, beamPitch + (rand.nextDouble() - 0.5) * 0.055);
        Vec3 from = muzzle();
        if (!MagmaGolemTerrain.visible(worldObj, from, from.addVector(direction.xCoord, direction.yCoord, direction.zCoord))) return;
        worldObj.spawnEntityInWorld(new EntityGolemFireball(this, from, direction, MagmaGolemConfig.ranged));
        playSound("mob.ghast.fireball", 2, 0.73F);
    }

    private void breath() {
        Vec3 from = muzzle(), direction = beamDirection(0, beamPitch);
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(from.xCoord - 16, from.yCoord - 16, from.zCoord - 16,
            from.xCoord + 16, from.yCoord + 16, from.zCoord + 16);
        for (Object candidate : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            EntityLivingBase victim = (EntityLivingBase)candidate;
            if (!eligible(victim)) continue;
            Vec3 point = center(victim);
            Vec3 delta = Vec3.createVectorHelper(point.xCoord - from.xCoord, point.yCoord - from.yCoord, point.zCoord - from.zCoord);
            double length = delta.lengthVector();
            if (length <= 16 && delta.dotProduct(direction) > length * 0.93 && MagmaGolemTerrain.visible(worldObj, from, point)
                    && victim.attackEntityFrom(DamageSource.causeMobDamage(this).setFireDamage(), MagmaGolemConfig.ranged * 0.48F)) {
                victim.setFire(4);
            }
        }
    }

    private void summon() {
        summonWait = MagmaGolemConfig.summonCooldown - (phase() - 1) * 20;
        pruneSummons();
        int remaining = Math.min(MagmaGolemConfig.cubeLimit - summons.size(), phase() == 1 ? 2 : 3);
        for (int attempt = 0; attempt < 14 && remaining > 0; attempt++) {
            double angle = rand.nextDouble() * TAU;
            double distance = 4.5 + rand.nextDouble() * 3.5;
            int x = MathHelper.floor_double(posX + Math.cos(angle) * distance);
            int z = MathHelper.floor_double(posZ + Math.sin(angle) * distance);
            if (!worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
            int y = Math.min(worldObj.getActualHeight() - 2, MathHelper.floor_double(posY) + 2);
            while (y >= 1 && y >= posY - 3 && !World.doesBlockHaveSolidTopSurface(worldObj, x, y - 1, z)) y--;
            if (y < 1 || y < posY - 3) continue;
            EntityGolemMagmaCube cube = new EntityGolemMagmaCube(worldObj);
            cube.setLocationAndAngles(x + 0.5, y, z + 0.5, rotationYaw, 0);
            if (!cube.getCanSpawnHere()) continue;
            cube.assign(this);
            if (worldObj.spawnEntityInWorld(cube)) {
                summons.put(cube.getUniqueID(), worldObj.getTotalWorldTime() + EntityGolemMagmaCube.LIFETIME);
                remaining--;
            }
        }
        playSound("mob.magmacube.big", 3.3F, 0.6F);
    }

    private void pruneSummons() {
        long now = worldObj.getTotalWorldTime();
        Iterator<Long> entries = summons.values().iterator();
        while (entries.hasNext()) if (entries.next() <= now) entries.remove();
    }

    private void groundImpact(byte effect) {
        worldObj.setEntityState(this, effect);
        playSound("random.explode", 2.3F, 0.62F);
    }

    private void impactSound(byte effect) {
        worldObj.setEntityState(this, effect);
        playSound("mob.irongolem.throw", 2.8F, 0.65F);
    }

    private static Vec3 center(Entity entity) {
        return Vec3.createVectorHelper(entity.posX, (entity.boundingBox.minY + entity.boundingBox.maxY) * 0.5, entity.posZ);
    }

    private static Vec3 nearest(AxisAlignedBB box, Vec3 point) {
        return Vec3.createVectorHelper(MathHelper.clamp_double(point.xCoord, box.minX, box.maxX),
            MathHelper.clamp_double(point.yCoord, box.minY, box.maxY), MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ));
    }

    public void samplePose(MagmaGolemPose result, float partial) {
        float diff = stride - previousStride;
        if (diff < -STEP) diff += TAU;
        Action active = action();
        float time = animationAge() + (worldObj.isRemote && !(active == Action.DOWN && animationAge() >= active.length) ? partial : 0);
        result.sample(active, time, ticksExisted + partial, previousStride + diff * partial,
            previousPace + (pace - previousPace) * partial, aimYaw, aimPitch, hasArm(LEFT), hasArm(RIGHT), phase(),
            worldObj.isRemote ? (dataWatcher.getWatchableObjectInt(MOTION) & LANDED_BIT) != 0 : landed,
            dataWatcher.getWatchableObjectInt(SLAM_GROUND));
    }

    private void updateParts() {
        samplePose(pose, 1);
        updateArmParts(LEFT, LEFT_UPPER, LEFT_FIST);
        updateArmParts(RIGHT, RIGHT_UPPER, RIGHT_FIST);
        setPart(LEFT_FOOT, pose.box(LEFT + FOOT, 0, 0.105, 0.295, 0.9, 0.585, 1.015, rotationYaw, posX, posY, posZ));
        setPart(RIGHT_FOOT, pose.box(RIGHT + FOOT, 0, 0.105, 0.295, 0.9, 0.585, 1.015, rotationYaw, posX, posY, posZ));
    }

    private void updateArmParts(int side, int upper, int lower) {
        if (!hasArm(side)) {
            parts[upper].boundingBox.setBounds(posX, posY, posZ, posX, posY, posZ);
            parts[lower].boundingBox.setBounds(posX, posY, posZ, posX, posY, posZ);
            return;
        }
        // Bounds of the model's shoulder, forearm and fist cuboids, in blocks.
        setPart(upper, pose.box(side + SHOULDER, 0, -1.25, 0, 19 / 16.0, 30 / 16.0, 19 / 16.0,
            rotationYaw, posX, posY, posZ));
        AxisAlignedBB forearm = pose.box(side + FOREARM, 0, -19 / 16.0, 0, 19 / 16.0, 21 / 16.0, 20 / 16.0,
            rotationYaw, posX, posY, posZ);
        setPart(lower, forearm.func_111270_a(fistBox(side)));
    }

    private void updateStance() {
        float nextHeight = action() == Action.DYING ? 3.2F : action() == Action.DOWN
            ? HEIGHT - (HEIGHT - 3.2F) * animationAge() / Action.DOWN.length : HEIGHT;
        if (height != nextHeight) setSize(WIDTH, nextHeight);
    }

    private void setPart(int index, AxisAlignedBB box) {
        EntityDragonPart part = parts[index];
        part.posX = (box.minX + box.maxX) * 0.5;
        part.posY = (box.minY + box.maxY) * 0.5;
        part.posZ = (box.minZ + box.maxZ) * 0.5;
        part.boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private Vec3 muzzle() { return pose.point(HEAD, 0, 0.74, 1.34, rotationYaw, posX, posY, posZ); }

    private final class Part extends EntityDragonPart {
        private final int index;

        Part(int index) {
            super(EntityGiantMagmaGolem.this, "magma_golem_" + index, 1, 1);
            this.index = index;
        }

        @Override public boolean canBeCollidedWith() {
            return EntityGiantMagmaGolem.this.isEntityAlive()
                && (index >= LEFT_FOOT || hasArm(index < RIGHT_UPPER ? LEFT : RIGHT));
        }

        @Override public float getCollisionBorderSize() { return 0.1F; }
    }

    @Override public void writeSpawnData(ByteBuf out) {
        for (Entity part : parts) out.writeInt(part.getEntityId());
    }

    @Override public void readSpawnData(ByteBuf in) {
        for (Entity part : parts) part.setEntityId(in.readInt());
        updateParts();
    }

    private void checkWater() {
        if (action == Action.DOWN) {
            if (ticksExisted % 4 != 0) return;
            if (age < Action.DOWN.length) return;
            boolean wet = MagmaGolemTerrain.wet(worldObj, parts[LEFT_FOOT].boundingBox.expand(0.28, 0.35, 0.28))
                || MagmaGolemTerrain.wet(worldObj, parts[RIGHT_FOOT].boundingBox.expand(0.28, 0.35, 0.28))
                || MagmaGolemTerrain.wet(worldObj, pose.box(BODY, 0, 2, 0, 1.7, 1.5, 1.05, rotationYaw, posX, posY, posZ));
            soaked = wet ? soaked + 4 : Math.max(0, soaked - 4);
            if (soaked >= 20) finish();
            return;
        }
        for (int side = LEFT; side <= RIGHT; side += RIGHT - LEFT) {
            if (arm(side) != MOLTEN) continue;
            int index = side == LEFT ? 0 : 1;
            int part = side == LEFT ? LEFT_UPPER : RIGHT_UPPER;
            boolean wet = MagmaGolemTerrain.wet(worldObj, parts[part + 1].boundingBox)
                || MagmaGolemTerrain.wet(worldObj, parts[part].boundingBox);
            if (!wet) continue;
            cooling[index] += slamHold() ? 1 : 4;
            if (cooling[index] >= MagmaGolemConfig.coolingTicks) {
                setArm(side, COOLED);
                Entity arm = parts[part + 1];
                armFeedback(Vec3.createVectorHelper(arm.posX, arm.posY, arm.posZ), "largesmoke", 18, "random.fizz");
            }
        }
    }

    @Override public boolean attackEntityFromPart(EntityDragonPart part, DamageSource source, float amount) {
        if (worldObj.isRemote || !isEntityAlive() || !(part instanceof Part) || part.entityDragonObj != this) return false;
        int slot = ((Part)part).index;
        if (slot >= LEFT_FOOT) return attackEntityFrom(source, amount);
        int side = slot < RIGHT_UPPER ? LEFT : RIGHT;
        if (!hasArm(side)) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof EntityPlayer && source.getSourceOfDamage() == attacker && "player".equals(source.getDamageType())) {
            EntityPlayer player = (EntityPlayer)attacker;
            ItemStack held = player.getCurrentEquippedItem();
            if (cooled(side) && held != null && held.getItem() instanceof ItemPickaxe) return mineArm(player, part, side, held, source, amount);
        }
        return attackEntityFrom(source, amount);
    }

    private boolean mineArm(EntityPlayer player, EntityDragonPart part, int side, ItemStack held, DamageSource source, float amount) {
        int index = side == LEFT ? 0 : 1;
        if (mineDelay[index] > 0 || !player.isEntityAlive() || isEntityInvulnerable() || ForgeHooks.onLivingAttack(this, source, amount)) return false;
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        double reach = player instanceof EntityPlayerMP ? ((EntityPlayerMP)player).theItemInWorldManager.getBlockReachDistance() : 4.5;
        MovingObjectPosition hit = armIntercept(part, eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        if (hit == null || !MagmaGolemTerrain.visible(worldObj, eye, hit.hitVec)) return false;
        mineDelay[index] = 8;
        armWork[index] += Math.max(2, Math.min(12, Math.round(held.func_150997_a(Blocks.cobblestone))));
        target = player;
        boolean broken = armWork[index] >= MagmaGolemConfig.armDurability;
        armFeedback(hit.hitVec, stoneParticle(), broken ? 28 : 7, "dig.stone");
        if (broken) {
            setArm(side, MISSING);
            finisher = player.getUniqueID();
            playSound("mob.irongolem.death", 3, 0.75F);
            if (action != Action.SLAM && (!armed() || action == (side == LEFT ? Action.PUNCH_LEFT : Action.PUNCH_RIGHT))) {
                begin(Action.STAGGER);
            }
            if (!armed()) playSound("random.anvil_land", 3, 0.68F);
            updateParts();
        }
        // Vanilla applies the held tool's normal hit durability and multiplayer hit feedback.
        return true;
    }

    private void armFeedback(Vec3 hit, String particle, int count, String sound) {
        worldObj.playSoundEffect(hit.xCoord, hit.yCoord, hit.zCoord, sound, 2, 0.72F);
        if (worldObj instanceof WorldServer) {
            ((WorldServer)worldObj).func_147487_a(particle, hit.xCoord, hit.yCoord, hit.zCoord, count, 0.25, 0.25, 0.25, 0.14);
        }
    }

    public MovingObjectPosition pickPlantedArm(Vec3 eye, Vec3 end, double distance) {
        if (!isEntityAlive() || !slamHold()) return null;
        MovingObjectPosition nearest = null;
        for (int i = 0; i < LEFT_FOOT; i++) {
            int side = i < RIGHT_UPPER ? LEFT : RIGHT;
            if (!slamsWith(side) || !cooled(side)) continue;
            MovingObjectPosition hit = armIntercept(parts[i], eye, end);
            if (hit == null) continue;
            double reach = eye.distanceTo(hit.hitVec);
            if (reach >= distance) continue;
            nearest = hit;
            distance = reach;
        }
        return nearest;
    }

    private static MovingObjectPosition armIntercept(Entity part, Vec3 eye, Vec3 end) {
        AxisAlignedBB box = part.boundingBox.expand(0.1, 0.1, 0.1);
        if (box.isVecInside(eye)) return new MovingObjectPosition(part, eye);
        MovingObjectPosition hit = box.calculateIntercept(eye, end);
        return hit == null ? null : new MovingObjectPosition(part, hit.hitVec);
    }

    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if (worldObj.isRemote || !isEntityAlive() || finishing || source.isFireDamage()
                || source == DamageSource.inWall || source == DamageSource.drown || source.getEntity() == this) return false;
        if (source == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof EntityGolemMagmaCube && ((EntityGolemMagmaCube)attacker).belongsTo(getUniqueID())) return false;
        if (attacker instanceof EntityLivingBase && targetable((EntityLivingBase)attacker)) target = (EntityLivingBase)attacker;
        return damage > 0 && super.attackEntityFrom(source, damage);
    }

    void onFootBlast(Explosion explosion) {
        if (armed() || !isEntityAlive() || isEntityInvulnerable() || action == Action.DOWN || finishing) return;
        Vec3 blast = Vec3.createVectorHelper(explosion.explosionX, explosion.explosionY, explosion.explosionZ);
        if (Math.abs(blast.yCoord - posY) > 3.2) return;
        double distance = Math.sqrt(Math.min(nearest(parts[LEFT_FOOT].boundingBox, blast).squareDistanceTo(blast),
            nearest(parts[RIGHT_FOOT].boundingBox, blast).squareDistanceTo(blast)));
        if (distance > 4.1 || explosion.explosionSize < 2) return;
        Vec3 center = Vec3.createVectorHelper(posX, posY + 0.6, posZ);
        if (distance > 1.7 && !MagmaGolemTerrain.visible(worldObj, blast, center)) return;
        EntityLivingBase igniter = explosion.getExplosivePlacedBy();
        if (igniter instanceof EntityPlayer) finisher = igniter.getUniqueID();
        // A close vanilla TNT charge is enough. Glancing hits build permanent structural damage.
        tntStress += distance <= 1.7 || explosion.explosionSize >= 12 && distance <= 3 ? 3 : 1;
        if (tntStress >= 3) {
            begin(Action.DOWN);
            groundImpact(LEAP_LAND);
        } else {
            begin(Action.STAGGER);
            playSound("random.anvil_land", 3, 0.62F);
        }
    }

    private void finish() {
        if (finishing || action != Action.DOWN || isEntityInvulnerable()) return;
        EntityPlayer player = finisher == null ? null : worldObj.func_152378_a(finisher);
        if (player != null) {
            attackingPlayer = player;
            recentlyHit = 100;
        }
        setHealth(0);
        onDeath(player == null ? DamageSource.drown : DamageSource.causePlayerDamage(player));
        if (finishing) playSound("random.fizz", 5, 0.53F);
        else soaked = 0;
    }

    @Override public void onDeath(DamageSource source) {
        if (worldObj.isRemote) {
            super.onDeath(source);
            return;
        }
        if (finishing) return;
        finishing = true;
        super.onDeath(source);
        if (!dead) {
            finishing = false;
            if (getHealth() <= 0) setHealth(1);
            return;
        }
        if (source.getEntity() instanceof EntityPlayer) finisher = source.getEntity().getUniqueID();
        begin(Action.DYING);
        updateStance();
        retireLoadedSummons();
    }

    @Override protected void onDeathUpdate() {
        if (!worldObj.isRemote) {
            if (!finishing) onDeath(DamageSource.generic);
            if (!finishing) return;
            age++;
            syncAction();
            if (age < Action.DYING.length) return;
            if (finisher != null) recentlyHit = 1;
        } else if (action() != Action.DYING || animationAge() < Action.DYING.length - 1) {
            return;
        }
        deathTime = 19;
        super.onDeathUpdate();
    }

    @Override protected void dropFewItems(boolean playerKill, int looting) {
        dropItem(Items.magma_cream, 5 + rand.nextInt(5 + looting));
        dropItem(Item.getItemFromBlock(Blocks.cobblestone), 12);
    }

    private void retireLoadedSummons() {
        if (!(worldObj instanceof WorldServer) || summons.isEmpty()) return;
        for (Object entity : worldObj.getEntitiesWithinAABB(EntityGolemMagmaCube.class, boundingBox.expand(64, 64, 64))) {
            EntityGolemMagmaCube cube = (EntityGolemMagmaCube)entity;
            if (cube.belongsTo(getUniqueID())) cube.setDead();
        }
        summons.clear();
    }

    private void clientEffects() {
        Action active = action();
        int frame = animationAge();
        if (active == Action.DYING) {
            if (ticksExisted % 2 == 0) {
                debris(pose.point(BODY, (rand.nextDouble() - 0.5) * 4, 2.3, 0, rotationYaw, posX, posY, posZ),
                    frame < 27 ? "largesmoke" : stoneParticle(), frame < 27 ? 5 : 8, frame < 27 ? 0.9 : 1.6);
            }
            return;
        }
        if (active == Action.BREATH && frame >= active.strike && frame < active.length - 7) {
            Vec3 from = muzzle();
            for (int i = 0; i < 11; i++) {
                Vec3 direction = beamDirection((rand.nextDouble() - 0.5) * 0.60, beamPitch + (rand.nextDouble() - 0.5) * 0.48);
                double speed = 0.43 + rand.nextDouble() * 0.39;
                worldObj.spawnParticle(i % 4 == 0 ? "smoke" : "flame", from.xCoord, from.yCoord, from.zCoord,
                    direction.xCoord * speed, direction.yCoord * speed, direction.zCoord * speed);
            }
        } else if ((active == Action.BREATH || active == Action.FIREBALL) && frame > 5 && frame < active.strike) {
            debris(muzzle(), "flame", 3, 0.32);
        }
        if (active == Action.SLAM && frame >= active.strike && frame <= active.strike + 8) {
            double radius = (phase() == 1 ? 7 : 11) * (frame - active.strike + 1) / 9D;
            for (int i = 0; i < 6; i++) {
                double angle = (i + rand.nextDouble()) * TAU / 6;
                Vec3 point = Vec3.createVectorHelper(posX + Math.cos(angle) * radius, posY + 0.19, posZ + Math.sin(angle) * radius);
                debris(point, stoneParticle(), 1, 0.24);
            }
        }
        if (ticksExisted % 5 == 0 && active != Action.DOWN) {
            int side = rand.nextBoolean() ? LEFT : RIGHT;
            int joint = hasArm(side) && !cooled(side) ? side + FOREARM : side + FOOT;
            Vec3 point = pose.point(joint, rand.nextDouble() - 0.5, joint == side + FOOT ? 0.2 : -2.3, 0.8,
                rotationYaw, posX, posY, posZ);
            debris(point, rand.nextBoolean() ? "flame" : "smoke", 2, 0.42);
            if (rand.nextInt(4) == 0) worldObj.spawnParticle("dripLava", point.xCoord, point.yCoord - 0.45, point.zCoord, 0, 0, 0);
        }
    }

    @Override public void handleHealthUpdate(byte status) {
        if (status >= IMPACT && status <= 79) {
            if (status >= 78) {
                Vec3 foot = pose.point((status == 78 ? LEFT : RIGHT) + FOOT, 0, 0, 0, rotationYaw, posX, posY, posZ);
                debris(foot, stoneParticle(), 7, 0.8);
            } else {
                double radius = status == QUAKE ? (phase() == 1 ? 7 : 11) : status == LEAP_LAND ? 7 : 3;
                for (int i = 0; i < 24; i++) {
                    double theta = i * TAU / 24;
                    double r = radius * (0.26 + rand.nextDouble() * 0.74);
                    Vec3 point = Vec3.createVectorHelper(posX + Math.cos(theta) * r, posY + 0.24, posZ + Math.sin(theta) * r);
                    debris(point, status == QUAKE && rand.nextBoolean() ? "flame" : stoneParticle(), 1, 0.35);
                }
            }
        } else {
            super.handleHealthUpdate(status);
        }
    }

    private static String stoneParticle() { return "blockcrack_" + Block.getIdFromBlock(Blocks.cobblestone) + "_0"; }

    private void debris(Vec3 point, String particle, int count, double spread) {
        for (int i = 0; i < count; i++) {
            double x = (rand.nextDouble() - 0.5) * spread, z = (rand.nextDouble() - 0.5) * spread;
            worldObj.spawnParticle(particle, point.xCoord + x, point.yCoord + rand.nextDouble() * spread * 0.5,
                point.zCoord + z, x * 0.12, 0.08 + rand.nextDouble() * 0.23, z * 0.12);
        }
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("Phase", phase());
        tag.setInteger("Arms", dataWatcher.getWatchableObjectInt(ARMS));
        tag.setIntArray("Cooling", cooling);
        tag.setIntArray("ArmWork", armWork);
        tag.setInteger("TntStress", tntStress);
        tag.setInteger("Soaked", soaked);
        tag.setInteger("Recovery", recovery);
        tag.setInteger("SummonWait", summonWait);
        tag.setBoolean("Down", action == Action.DOWN);
        tag.setBoolean("Finishing", finishing);
        tag.setInteger("FinishAge", age);
        if (slamHold()) {
            tag.setInteger("SlamGround", dataWatcher.getWatchableObjectInt(SLAM_GROUND));
            tag.setInteger("SlamAge", age);
        }
        if (finisher != null) writeUuid(tag, finisher);
        NBTTagList children = new NBTTagList();
        for (Map.Entry<UUID, Long> child : summons.entrySet()) {
            NBTTagCompound record = new NBTTagCompound();
            writeUuid(record, child.getKey());
            record.setLong("Expires", child.getValue());
            children.appendTag(record);
        }
        tag.setTag("Summons", children);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        finishing = tag.getBoolean("Finishing");
        super.readEntityFromNBT(tag);
        dataWatcher.updateObject(PHASE, MathHelper.clamp_int(tag.getInteger("Phase"), 1, 3));
        int arms = tag.getInteger("Arms");
        setArm(LEFT, Math.min(2, arms & 3));
        setArm(RIGHT, Math.min(2, (arms >> 2) & 3));
        readArms(cooling, tag.getIntArray("Cooling"), 200);
        readArms(armWork, tag.getIntArray("ArmWork"), 200);
        tntStress = MathHelper.clamp_int(tag.getInteger("TntStress"), 0, 3);
        soaked = MathHelper.clamp_int(tag.getInteger("Soaked"), 0, 20);
        recovery = MathHelper.clamp_int(tag.getInteger("Recovery"), 0, 400);
        summonWait = MathHelper.clamp_int(tag.getInteger("SummonWait"), 0, 3600);
        finisher = readUuid(tag);
        NBTTagList children = tag.getTagList("Summons", 10);
        summons.clear();
        for (int i = 0; i < Math.min(20, children.tagCount()); i++) {
            NBTTagCompound record = children.getCompoundTagAt(i);
            UUID id = readUuid(record);
            if (id != null) summons.put(id, record.getLong("Expires"));
        }
        pruneSummons();
        if (finishing || tag.getBoolean("Down") && !armed()) {
            begin(finishing ? Action.DYING : Action.DOWN);
            age = MathHelper.clamp_int(tag.getInteger("FinishAge"), 0, action.length - (finishing ? 1 : 0));
            if (finishing) {
                setHealth(0);
                dead = true;
            }
            syncAction();
        } else if (tag.hasKey("SlamGround") && tag.hasKey("SlamAge")) {
            int saved = tag.getInteger("SlamGround");
            int left = slamSurface(saved, LEFT), right = slamSurface(saved, RIGHT);
            if (left <= SLAM_MAX_SURFACE && right <= SLAM_MAX_SURFACE && (left | right) != 0) {
                dataWatcher.updateObject(SLAM_GROUND, left | (right << 10));
                begin(Action.SLAM);
                // The impact already happened before this hold was saved.
                age = MathHelper.clamp_int(tag.getInteger("SlamAge"), Action.SLAM.strike + 9,
                    Action.SLAM.strike + SLAM_HOLD - 1);
                syncAction();
            }
        }
        updateStance();
        updateParts();
    }

    private static void readArms(int[] result, int[] saved, int max) {
        for (int i = 0; i < result.length; i++) result[i] = i < saved.length ? MathHelper.clamp_int(saved[i], 0, max) : 0;
    }

    private static void writeUuid(NBTTagCompound tag, UUID id) {
        tag.setLong("UUIDMost", id.getMostSignificantBits());
        tag.setLong("UUIDLeast", id.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag) {
        return tag.hasKey("UUIDMost") ? new UUID(tag.getLong("UUIDMost"), tag.getLong("UUIDLeast")) : null;
    }
}
