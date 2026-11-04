package com.hbm.entity.mob.giantenderman;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import api.hbm.entity.IRadiationImmune;
import com.hbm.config.GiantEndermanConfig;
import com.hbm.entity.mob.colossus.EntityColossusPart;
import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import com.hbm.entity.mob.giantenderman.GiantEndermanPose.Action;
import com.hbm.entity.mob.giantenderman.GiantEndermanTerrain.Cell;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import static com.hbm.entity.mob.giantenderman.GiantEndermanPose.*;

public final class EntityGiantEnderman extends EntityLiving implements IBossDisplayData, IRadiationImmune {
    private static final int ACTION = 16, START = 17, DURATION = 18, PHASE = 19, HAND = 20, STRIDE = 21, MOVING = 22;
    private static final int LOOK_YAW = 23, LOOK_PITCH = 24, AIM_X = 25, AIM_Y = 26, AIM_Z = 27, GRIP = 28, CHEST_OPEN = 29;
    private static final int FINISH_TARGET = 30;
    private static final String GRABBING_BOSS = "RNTGiantEndermanGrabOwner";
    private static final double[] STEERING = {0, 0.65, -0.65, 1.25, -1.25};
    // Bone, local center, and dimensions, with each length in eighths of a block.
    private static final int[][] REGIONS = {
        {CHEST, 0,29,-1, 52,55,30}, {HEAD, 0,23,1, 59,49,55},
        {LEFT_ARM, 0,-33,0, 15,68,15}, {LEFT_FOREARM, 0,-33,0, 13,66,13}, {LEFT_HAND, 0,-5,0, 20,19,18},
        {RIGHT_ARM, 0,-33,0, 15,68,15}, {RIGHT_FOREARM, 0,-33,0, 13,66,13}, {RIGHT_HAND, 0,-5,0, 20,19,18},
        {LEFT_LEG, 0,-29,0, 17,58,17}, {LEFT_SHIN, 0,-30,0, 15,60,15},
        {RIGHT_LEG, 0,-29,0, 17,58,17}, {RIGHT_SHIN, 0,-30,0, 15,60,15},
        {LEFT_FOOT, 0,-2,2, 16,8,20}, {RIGHT_FOOT, 0,-2,2, 16,8,20},
        {JAW, 0,-2,21, 56,12,50}, {HIPS, 0,0,0, 40,15,22}
    };
    private final GiantEndermanPose pose = new GiantEndermanPose();
    private final EntityGiantEndermanPart[] parts = new EntityGiantEndermanPart[REGIONS.length];
    private final GiantEndermanTerrain terrain = new GiantEndermanTerrain(this);
    private final Set<UUID> struck = new HashSet<>();
    private EntityLivingBase target, attackTarget, grabbed;
    private EntityEndermanTerrain formation;
    private EntityPrismarineColossus finishing;
    private UUID provoker, lastStaring;
    private Vec3 destination, obstruction;
    private int recovery, teleportCooldown, throwCooldown, blockedTicks, sequence, stareTicks, targetMemory, searchDelay;
    private int localFrame, seenStart, seenAction = -1, blinkLock;
    private boolean activated, pendingRage, flankingGrab, dodgeRequested;
    private float handDamage, staggerDamage, stride, moving, previousStride, previousMoving, previousLookYaw, previousLookPitch;
    private double fallSpeed;

    public EntityGiantEnderman(World world) {
        super(world);
        setSize(WIDTH, HEIGHT);
        stepHeight = 0;
        ignoreFrustumCheck = true;
        experienceValue = 250;
        setHealth(getMaxHealth());
    }

    @Override protected void entityInit() {
        super.entityInit();
        for (int id : new int[]{ACTION, START, DURATION, PHASE, HAND, GRIP, CHEST_OPEN, FINISH_TARGET}) {
            dataWatcher.addObject(id, id == HAND || id == PHASE ? 1 : (id == GRIP || id == FINISH_TARGET) ? -1 : 0);
        }
        for (int id : new int[]{STRIDE, MOVING, LOOK_YAW, LOOK_PITCH, AIM_X, AIM_Y, AIM_Z}) dataWatcher.addObject(id, 0F);
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(GiantEndermanConfig.health);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(GiantEndermanConfig.range);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(GiantEndermanConfig.speed);
    }

    @Override public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : net.minecraft.util.StatCollector.translateToLocal("entity.hbm.giant_enderman.name");
    }
    @Override public float getEyeHeight() { return 28.5F; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 256 * 256; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected void updateEntityActionState() { }
    @Override public void moveEntityWithHeading(float strafe, float forward) { }
    @Override protected void fall(float distance) { }
    @Override protected void collideWithNearbyEntities() { }

    public Action action() { return Action.fromId(dataWatcher.getWatchableObjectInt(ACTION)); }
    public int phase() { return dataWatcher.getWatchableObjectInt(PHASE); }
    public int activeHand() { return dataWatcher.getWatchableObjectInt(HAND); }
    public int finishTargetId() { return dataWatcher.getWatchableObjectInt(FINISH_TARGET); }
    public int grabbedId() { return dataWatcher.getWatchableObjectInt(GRIP); }
    public boolean chestExposed() { return dataWatcher.getWatchableObjectInt(CHEST_OPEN) - (int)worldObj.getTotalWorldTime() > 0; }
    boolean partsVulnerable() {
        return isEntityAlive() && !(action() == Action.VANISH && frame(0) >= 9)
            && !(action() == Action.ARRIVE && frame(0) < 5);
    }

    public double frame(float partial) {
        int duration = dataWatcher.getWatchableObjectInt(DURATION);
        if (duration <= 0) return 0;
        int elapsed = worldObj.isRemote ? localFrame : (int)worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(START);
        return MathHelper.clamp_double((elapsed + partial) * action().length / duration, 0, action().length);
    }

    public void evaluate(GiantEndermanPose out, float partial) {
        out.evaluate(action(), frame(worldObj.isRemote ? partial : 0), activeHand(), previousStride + (stride - previousStride) * partial,
            previousMoving + (moving - previousMoving) * partial, worldObj.getTotalWorldTime() % 24000L + partial,
            previousLookYaw + (watch(LOOK_YAW) - previousLookYaw) * partial,
            previousLookPitch + (watch(LOOK_PITCH) - previousLookPitch) * partial,
            Vec3.createVectorHelper(watch(AIM_X), watch(AIM_Y), watch(AIM_Z)));
    }

    private float watch(int key) { return dataWatcher.getWatchableObjectFloat(key); }

    private void syncClientPose() {
        int start = dataWatcher.getWatchableObjectInt(START), action = dataWatcher.getWatchableObjectInt(ACTION);
        if (start != seenStart || action != seenAction) {
            seenStart = start;
            seenAction = action;
            localFrame = MathHelper.clamp_int((int)worldObj.getTotalWorldTime() - start, 0, dataWatcher.getWatchableObjectInt(DURATION));
        } else localFrame++;
        stride = watch(STRIDE);
        previousStride = stride + MathHelper.wrapAngleTo180_float((previousStride - stride) * 57.29578F) / 57.29578F;
        moving = watch(MOVING);
        evaluate(pose, 1);
    }

    @Override public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int ticks) {
        if (getDistanceSq(x, y, z) > 36) {
            setPositionAndRotation(x, y, z, yaw, pitch);
            prevPosX = x;
            prevPosY = y;
            prevPosZ = z;
        } else super.setPositionAndRotation2(x, y, z, yaw, pitch, ticks);
    }

    @Override public void onUpdate() {
        previousStride = stride;
        previousMoving = moving;
        previousLookYaw = watch(LOOK_YAW);
        previousLookPitch = watch(LOOK_PITCH);
        super.onUpdate();
        if (isDead || !isEntityAlive()) return;
        if (worldObj.isRemote) syncClientPose();
        else tickEncounter();
    }

    private void tickEncounter() {
        if (recovery > 0) recovery--;
        if (teleportCooldown > 0) teleportCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (blinkLock > 0) blinkLock--;
        if (targetMemory > 0) targetMemory--;
        if (targetMemory == 0) provoker = null;
        staggerDamage = Math.max(0, staggerDamage - 0.35F);
        boolean targetLost = target != null && !validTarget(target);
        if (targetLost) target = null;
        if (--searchDelay <= 0 || targetLost) {
            searchDelay = activated ? 20 : 5;
            findTarget();
        }

        int phase = getHealth() <= getMaxHealth() * 0.35F ? 3 : getHealth() <= getMaxHealth() * 0.70F ? 2 : 1;
        if (phase() != phase) {
            if (phase == 3) pendingRage = true;
            dataWatcher.updateObject(PHASE, phase);
        }
        double x = posX, z = posZ;
        if (action() == Action.IDLE) {
            if (pendingRage) {
                pendingRage = false;
                begin(Action.SCREAM);
            } else if (target != null) {
                trackTarget();
                chooseAction();
                if (action() == Action.IDLE) pursue();
            } else if (recovery == 0) terrain.clear();
        }
        gravity();
        double traveled = Math.hypot(posX - x, posZ - z);
        float oldStride = stride;
        stride = (stride + (float)(traveled * Math.PI / 5.2)) % ((float)Math.PI * 2);
        moving += (Math.min(1, traveled / GiantEndermanConfig.speed) - moving) * 0.24F;
        dataWatcher.updateObject(STRIDE, stride);
        dataWatcher.updateObject(MOVING, moving);
        if (traveled > 0.02 && (int)(stride / Math.PI) != (int)(oldStride / Math.PI)) sound("step", 3.6F);
        updateAttackAim();
        evaluate(pose, 1);
        terrain.tick();
        if (action() != Action.IDLE) advanceAction(frame(-1), frame(0));
        evaluate(pose, 1);
        if (ticksExisted % 15 == 1) maintainParts();
        for (EntityGiantEndermanPart part : parts) if (part != null) part.follow();
    }

    private void findTarget() {
        if (target != null && target.getUniqueID().equals(provoker)) return;
        EntityLivingBase best = target;
        double bestDistance = best == null ? Double.MAX_VALUE : getDistanceSqToEntity(best) * 0.60;
        double stareDistance = Double.MAX_VALUE;
        EntityPlayer staring = null;
        for (Object object : worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer)object;
            if (!validTarget(player)) continue;
            double distance = getDistanceSqToEntity(player);
            if (player.getUniqueID().equals(provoker)) {
                target = player;
                activated = true;
                return;
            }
            if (activated && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            } else if (!activated && distance < stareDistance && lookingAtEyes(player)) {
                staring = player;
                stareDistance = distance;
            }
        }
        if (staring != null) {
            if (!staring.getUniqueID().equals(lastStaring)) stareTicks = 0;
            lastStaring = staring.getUniqueID();
            if (++stareTicks >= 2) provoke(staring);
            return;
        }
        lastStaring = null;
        stareTicks = 0;
        boolean scanBosses = GiantEndermanConfig.bossCombat && (activated || ticksExisted % 20 < 5);
        if (provoker != null || scanBosses) {
            double range = GiantEndermanConfig.range;
            for (Object object : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(range, range, range))) {
                EntityLivingBase candidate = (EntityLivingBase)object;
                if (candidate.getUniqueID().equals(provoker) && validTarget(candidate)) {
                    target = candidate;
                    return;
                }
                if (!scanBosses || !hostileBoss(candidate) || !validTarget(candidate)) continue;
                double distance = getDistanceSqToEntity(candidate);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (best != null && !activated) provoke(best);
        else target = best;
    }

    private boolean lookingAtEyes(EntityPlayer player) {
        if (player.getCurrentArmor(3) != null && player.getCurrentArmor(3).getItem() == Item.getItemFromBlock(Blocks.pumpkin)) return false;
        Vec3 from = eye(player);
        double yaw = Math.toRadians(rotationYaw);
        if ((from.xCoord - posX) * -Math.sin(yaw) + (from.zCoord - posZ) * Math.cos(yaw) <= 0) return false;
        Vec3 look = player.getLookVec();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 pupil = bonePoint(HEAD, side * 1.98, 4.2, 3.67);
            Vec3 delta = from.subtract(pupil);
            double distance = delta.lengthVector();
            if (distance > 0.1 && look.dotProduct(delta.normalize()) > Math.cos(Math.atan2(1.2, distance))
                && visible(from.addVector(0, 0, 0), pupil)) return true;
        }
        return false;
    }

    private void provoke(EntityLivingBase attacker) {
        boolean first = !activated;
        target = attacker;
        activated = true;
        provoker = attacker.getUniqueID();
        targetMemory = 1200;
        if (first && action() == Action.IDLE) begin(Action.SCREAM);
    }

    public void activateEncounter() {
        activated = true;
        begin(Action.SCREAM);
    }

    private boolean validTarget(EntityLivingBase candidate) {
        return canHarm(candidate) && candidate.addedToChunk && getDistanceSqToEntity(candidate) <= GiantEndermanConfig.range * GiantEndermanConfig.range;
    }

    boolean canHarm(EntityLivingBase candidate) {
        if (candidate == null || candidate == this || !candidate.isEntityAlive() || candidate.worldObj != worldObj) return false;
        if (candidate instanceof EntityPlayer) {
            return worldObj.difficultySetting != EnumDifficulty.PEACEFUL && !((EntityPlayer)candidate).capabilities.disableDamage;
        }
        return !(candidate instanceof IBossDisplayData) || GiantEndermanConfig.bossCombat;
    }

    private static boolean hostileBoss(EntityLivingBase entity) {
        return entity instanceof IBossDisplayData && (entity instanceof IMob || entity instanceof EntityPrismarineColossus);
    }

    private void trackTarget() {
        Vec3 goal = targetPoint(target);
        turn(heading(goal.xCoord - posX, goal.zCoord - posZ), 6);
        lookAt(goal);
    }

    private void lookAt(Vec3 goal) {
        double dx = goal.xCoord - posX, dz = goal.zCoord - posZ;
        float relative = MathHelper.wrapAngleTo180_float(rotationYaw - heading(dx, dz));
        float pitch = (float)Math.atan2(posY + 27 - goal.yCoord, Math.max(1, Math.hypot(dx, dz)));
        dataWatcher.updateObject(LOOK_YAW, MathHelper.clamp_float(relative, -42, 42) / 57.29578F);
        dataWatcher.updateObject(LOOK_PITCH, MathHelper.clamp_float(pitch, -0.5F, 0.88F));
    }

    private static float heading(double dx, double dz) { return (float)Math.toDegrees(Math.atan2(-dx, dz)); }

    private void turn(float desired, float speed) {
        rotationYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(desired - rotationYaw), -speed, speed);
        rotationYaw = MathHelper.wrapAngleTo180_float(rotationYaw);
        renderYawOffset = rotationYaw;
    }

    private void pursue() {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double distance = Math.hypot(dx, dz);
        if (distance < (target instanceof IBossDisplayData ? 9 : 4)) {
            obstruction = null;
            blockedTicks = 0;
            return;
        }
        double direction = Math.atan2(dx, dz);
        double speed = GiantEndermanConfig.speed * (phase() == 3 ? 1.65 : phase() == 2 ? 1.32 : 1);
        if (recovery > 0) speed *= 0.65;
        obstruction = null;
        for (double offset : STEERING) {
            double x = Math.sin(direction + offset) * speed, z = Math.cos(direction + offset) * speed;
            AxisAlignedBB sweep = boundingBox.copy().contract(0.02, 0.04, 0.02).addCoord(x, 0, z);
            if (!GiantEndermanTerrain.loaded(worldObj, sweep)) continue;
            List<?> obstacles = worldObj.func_147461_a(sweep);
            boolean clear = obstacles.isEmpty();
            if (!clear && offset == 0) {
                AxisAlignedBB shape = (AxisAlignedBB)obstacles.get(0);
                obstruction = Vec3.createVectorHelper((shape.minX + shape.maxX) * 0.5,
                    (shape.minY + shape.maxY) * 0.5, (shape.minZ + shape.maxZ) * 0.5);
            }
            if (clear && hasFloor(posX + x, posY, posZ + z, 2.5)) {
                setPosition(posX + x, posY, posZ + z);
                blockedTicks = offset == 0 ? 0 : blockedTicks + 1;
                return;
            }
            if (!clear && offset == 0 && worldObj.func_147461_a(sweep.getOffsetBoundingBox(0, 1.02, 0)).isEmpty()
                && hasFloor(posX + x, posY + 1.02, posZ + z, 1.15)) {
                setPosition(posX + x, posY + 1.02, posZ + z);
                blockedTicks = 0;
                return;
            }
        }
        blockedTicks++;
    }

    private void gravity() {
        fallSpeed = Math.max(-1.4, (fallSpeed - 0.08) * 0.98);
        AxisAlignedBB base = AxisAlignedBB.getBoundingBox(boundingBox.minX, posY, boundingBox.minZ,
            boundingBox.maxX, posY + 0.05, boundingBox.maxZ);
        AxisAlignedBB below = base.addCoord(0, fallSpeed, 0);
        if (!GiantEndermanTerrain.loaded(worldObj, below)) return;
        double drop = fallSpeed;
        for (Object shape : worldObj.func_147461_a(below)) drop = ((AxisAlignedBB)shape).calculateYOffset(base, drop);
        onGround = drop > fallSpeed + 0.0001;
        if (drop < 0) setPosition(posX, posY + drop, posZ);
        if (onGround) fallSpeed = 0;
    }

    private void chooseAction() {
        if (recovery > 0 || !onGround) return;
        double distance = Math.hypot(target.posX - posX, target.posZ - posZ);
        double height = target.posY - posY;
        boolean boss = target instanceof IBossDisplayData;
        boolean canGrab = !boss && grabbable(target);
        if (target instanceof EntityPrismarineColossus && distance < 16 && Math.abs(height) < 8
            && ((EntityPrismarineColossus)target).canEndermanFinish()) {
            finishing = (EntityPrismarineColossus)target;
            begin(Action.FINISH);
        } else if ((dodgeRequested || target instanceof EntityPrismarineColossus
            && ((EntityPrismarineColossus)target).windingUpAgainstEnderman(this) || blockedTicks > 45 || distance > 34 || Math.abs(height) > 12
            || distance > 12 && sequence % 4 == 3) && tryBlink(false)) {
            dodgeRequested = false;
        } else if (flankingGrab && canGrab && distance < 12 && Math.abs(height) < 4) {
            flankingGrab = false;
            begin(Action.GRAB);
        } else if (phase() == 3 && canGrab && distance < 20 && sequence % 5 == 3 && tryBlink(true)) {
            return;
        } else if (blockedTicks > 16 && obstruction != null && GiantEndermanTerrain.enabled(worldObj)) {
            begin(Action.SWIPE);
        } else if (throwCooldown == 0 && GiantEndermanTerrain.enabled(worldObj) && distance >= 9 && distance < 30
            && (sequence % 4 == 2 || distance > 19)) {
            throwCooldown = phase() == 3 ? 115 : 150;
            begin(Action.THROW);
        } else if (distance < (boss ? 19 : 13) && Math.abs(height) < (boss ? 9 : 5)) {
            int choice = sequence % 5;
            if (phase() >= 2 && canGrab && choice == 1) begin(Action.GRAB);
            else if (phase() >= 2 && !boss && (choice == 2 || phase() == 3 && choice == 4)) begin(Action.SMASH);
            else if (distance < 8 && choice == 0) begin(Action.STOMP);
            else begin(Action.SWIPE);
        }
    }

    private void begin(Action next) {
        dataWatcher.updateObject(ACTION, next.ordinal());
        dataWatcher.updateObject(FINISH_TARGET, next == Action.FINISH && finishing != null ? finishing.getEntityId() : -1);
        dataWatcher.updateObject(START, (int)worldObj.getTotalWorldTime());
        boolean strike = next.impact > 0 && next != Action.FINISH;
        int duration = strike ? Math.round(next.length * (phase() == 3 ? 0.78F : phase() == 2 ? 0.88F : 1)) : next.length;
        dataWatcher.updateObject(DURATION, duration);
        if (next.impact > 0) {
            attackTarget = target;
            dataWatcher.updateObject(HAND, (sequence++ & 1) == 0 ? 1 : -1);
            blinkLock = 0;
            struck.clear();
            setAim(targetPoint(attackTarget));
            sound("windup", 3);
        }
        if (next == Action.SCREAM) sound("scream", phase() == 3 ? 6 : 4);
        if (next == Action.VANISH || next == Action.ARRIVE) sound("teleport", 5);
        if (next == Action.DEATH && addedToChunk) sound("death", 5);
    }

    private void updateAttackAim() {
        Action current = action();
        double now = frame(0);
        int lock = current == Action.FINISH ? 46 : current.impact - 8;
        if (current.impact > 0 && now < lock && validTarget(attackTarget)) {
            Vec3 goal = targetPoint(attackTarget);
            turn(heading(goal.xCoord - posX, goal.zCoord - posZ), 7);
            lookAt(goal);
            setAim(goal);
        }
    }

    private void advanceAction(double prev, double now) {
        Action current = action();
        switch (current) {
            case SWIPE:
                if (now >= current.impact - 2 && prev < current.impact + 4) {
                    Vec3 hand = handPoint();
                    contact(hand, 3.4, 19 + phase() * 4, 1.25);
                    if (crossed(prev, now, current.impact)) {
                        sound("swing", 4);
                        if (blockedTicks > 16 && obstruction != null) {
                            terrain.impact(obstruction, phase() == 1 ? 2 : 4, 1, phase() + 1);
                        } else terrain.impact(hand, 3, 2, 2);
                    }
                }
                break;
            case STOMP:
                if (crossed(prev, now, current.impact)) {
                    Vec3 foot = bonePoint(activeHand() < 0 ? LEFT_FOOT : RIGHT_FOOT, 0, -0.7, 0.5);
                    impact(foot, 7, 18 + phase() * 3, 0.9);
                    terrain.impact(foot, 3, 1, 1);
                }
                break;
            case SMASH:
                if (crossed(prev, now, current.impact)) {
                    Vec3 center = forward(5.5);
                    impact(center, 5, 27 + phase() * 3, 1.2);
                    terrain.impact(center, 5, 1, 2);
                }
                if (phase() == 3 && now > current.impact && now < current.impact + 12) {
                    shockwave(forward(5.5), Math.min(15, (now - current.impact) * 1.6), 23, 1.1);
                }
                break;
            case THROW:
                throwTick(prev, now);
                break;
            case GRAB:
                grabTick(prev, now);
                break;
            case FINISH:
                finishTick(prev, now);
                break;
            case VANISH:
                if (now >= current.length) {
                    if (destination != null && safePosition(destination.xCoord, destination.yCoord, destination.zCoord)) {
                        setPositionAndUpdate(destination.xCoord, destination.yCoord, destination.zCoord);
                        if (validTarget(target)) rotationYaw = heading(target.posX - posX, target.posZ - posZ);
                        fallSpeed = 0;
                    } else flankingGrab = false;
                    destination = null;
                    begin(Action.ARRIVE);
                    return;
                }
                break;
            default:
                break;
        }
        if (action() != current || now < current.length) return;
        if (phase() == 3 && (current == Action.SMASH || current == Action.THROW || current == Action.FINISH)) {
            dataWatcher.updateObject(CHEST_OPEN, (int)worldObj.getTotalWorldTime() + 36);
        }
        releaseGrip();
        clearFormation();
        finishing = null;
        attackTarget = null;
        recovery = current == Action.ARRIVE ? 10 : phase() == 3 ? 17 : phase() == 2 ? 23 : 32;
        begin(Action.IDLE);
    }

    private void throwTick(double prev, double now) {
        if (crossed(prev, now, 24)) {
            Vec3 hand = handPoint();
            int x = MathHelper.floor_double(hand.xCoord), z = MathHelper.floor_double(hand.zCoord);
            int y = Math.min(worldObj.getActualHeight() - 1, MathHelper.floor_double(Math.max(posY + 1, hand.yCoord + 0.5)));
            if (worldObj.blockExists(x, y, z)) {
                for (int i = 0; i < 8 && y > 1 && worldObj.isAirBlock(x, y, z); i++) y--;
                terrain.rip(Vec3.createVectorHelper(hand.xCoord, y, hand.zCoord), phase() == 1 ? 1 : phase() == 2 ? 2 : 3);
            }
            sound("impact", 3);
        }
        if (crossed(prev, now, 37)) {
            List<Cell> cells = terrain.takeFormation();
            if (!cells.isEmpty()) {
                formation = new EntityEndermanTerrain(this, cells, handPoint(), true);
                if (!worldObj.spawnEntityInWorld(formation)) clearFormation();
            }
        }
        if (formation != null && crossed(prev, now, Action.THROW.impact)) {
            Vec3 hand = handPoint();
            Vec3 goal = validTarget(attackTarget) ? targetPoint(attackTarget) : forward(18);
            if (phase() < 3 || formation.cells.size() < 3) {
                EntityEndermanTerrain shot = formation;
                formation = null;
                shot.setPosition(hand.xCoord, hand.yCoord, hand.zCoord);
                shot.launch(goal, 0);
            } else {
                List<Cell> left = new ArrayList<>(), middle = new ArrayList<>(), right = new ArrayList<>();
                for (Cell cell : formation.cells) (cell.x < 0 ? left : cell.x > 0 ? right : middle).add(cell);
                clearFormation();
                launch(left, hand, goal, -0.13);
                launch(middle, hand, goal, 0);
                launch(right, hand, goal, 0.13);
            }
            sound("swing", 4);
        }
    }

    private void launch(List<Cell> cells, Vec3 start, Vec3 goal, double spread) {
        if (cells.isEmpty()) return;
        EntityEndermanTerrain shot = new EntityEndermanTerrain(this, cells, start, false);
        shot.launch(goal, spread);
        worldObj.spawnEntityInWorld(shot);
    }

    private void grabTick(double prev, double now) {
        if (grabbed == null && now >= 27 && prev < 34 && handDamage == 0) {
            Vec3 point = handPoint();
            double best = Double.MAX_VALUE;
            EntityLivingBase victim = null;
            for (Object object : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, cube(point, 2.6, 2.8))) {
                EntityLivingBase living = (EntityLivingBase)object;
                if (!grabbable(living) || !visible(point, eye(living))) continue;
                double distance = point.squareDistanceTo(eye(living)) - (living == attackTarget ? 5 : 0);
                if (distance < best) {
                    best = distance;
                    victim = living;
                }
            }
            if (victim != null) {
                grabbed = victim;
                victim.getEntityData().setInteger(GRABBING_BOSS, getEntityId());
                dataWatcher.updateObject(GRIP, victim.getEntityId());
                sound("grab", 3);
            }
        }
        if (grabbed == null) return;
        if (!grabbable(grabbed) || !holdVictim()) {
            releaseGrip();
            handDamage = 1;
            return;
        }
        if (grabbed instanceof EntityPlayer) {
            if (crossed(prev, now, 43) || crossed(prev, now, 55)) bitePlayer(3);
            if (crossed(prev, now, 68)) {
                bitePlayer(10);
                sound("bite", 3);
                releaseGrip();
            }
        } else if (crossed(prev, now, 68)) {
            grabbed.attackEntityFrom(DamageSource.causeMobDamage(this), Math.max(200, grabbed.getMaxHealth() * 4));
            sound("bite", 3);
            releaseGrip();
        }
    }

    private void bitePlayer(float damage) {
        float amount = Math.min(damage * GiantEndermanConfig.damage, Math.max(0, grabbed.getHealth() - 1));
        if (amount > 0) grabbed.attackEntityFrom(DamageSource.causeMobDamage(this), amount);
    }

    private boolean grabbable(EntityLivingBase entity) {
        if (!canHarm(entity) || !entity.addedToChunk || entity instanceof IBossDisplayData || entity.ridingEntity != null || entity.riddenByEntity != null
            || entity.width > 1.65F || entity.height > 3.25F) return false;
        if (entity.getEntityData().hasKey(GRABBING_BOSS)) {
            Entity holder = worldObj.getEntityByID(entity.getEntityData().getInteger(GRABBING_BOSS));
            if (holder instanceof EntityGiantEnderman && holder != this && ((EntityGiantEnderman)holder).grabbed == entity) return false;
        }
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP)entity;
            return GiantEndermanConfig.playerGrab && player.playerNetServerHandler != null
                && worldObj.playerEntities.contains(player) && !(player instanceof net.minecraftforge.common.util.FakePlayer);
        }
        return !(entity instanceof EntityPlayer);
    }

    private boolean holdVictim() {
        Vec3 point = victimPoint(pose, grabbed, rotationYaw, posX, posY, posZ);
        double dx = point.xCoord - grabbed.posX, dy = point.yCoord - grabbed.posY, dz = point.zCoord - grabbed.posZ;
        if (dx*dx + dy*dy + dz*dz > 36) return false;
        AxisAlignedBB sweep = grabbed.boundingBox.copy().contract(0.02, 0.02, 0.02).addCoord(dx, dy, dz);
        if (!GiantEndermanTerrain.loaded(worldObj, sweep.expand(1, 0, 1)) || !worldObj.func_147461_a(sweep).isEmpty()) return false;
        grabbed.motionX = grabbed.motionY = grabbed.motionZ = 0;
        grabbed.fallDistance = 0;
        if (grabbed instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP)grabbed;
            player.getEntityData().setInteger(GiantEndermanEvents.FALL_GRACE, 65);
            if (ticksExisted % 3 == 0) player.playerNetServerHandler.setPlayerLocation(point.xCoord, point.yCoord, point.zCoord, player.rotationYaw, player.rotationPitch);
            else player.setPosition(point.xCoord, point.yCoord, point.zCoord);
        } else grabbed.setPosition(point.xCoord, point.yCoord, point.zCoord);
        return true;
    }

    public Vec3 victimPoint(GiantEndermanPose pose, EntityLivingBase victim, float yaw, double x, double y, double z) {
        Vec3 hand = worldPoint(pose, hand(activeHand()), 0, -0.5, 0.65, yaw, x, y, z);
        return hand.addVector(0, -victim.height * 0.45, 0);
    }

    void releaseGrip() {
        if (grabbed != null) {
            grabbed.fallDistance = 0;
            if (grabbed.getEntityData().getInteger(GRABBING_BOSS) == getEntityId()) grabbed.getEntityData().removeTag(GRABBING_BOSS);
            if (grabbed instanceof EntityPlayer) grabbed.getEntityData().setInteger(GiantEndermanEvents.FALL_GRACE, 65);
            grabbed = null;
            dataWatcher.updateObject(GRIP, -1);
        }
        handDamage = 0;
    }

    private void finishTick(double prev, double now) {
        if (!GiantEndermanConfig.bossCombat || finishing == null || finishing.worldObj != worldObj || !finishing.addedToChunk) return;
        if (!finishing.isEntityAlive() || now < 34) return;
        Vec3 hand = handPoint(), head = finishing.endermanHead();
        double distance = hand.squareDistanceTo(head);
        if (now < Action.FINISH.impact && distance < 25 && visible(hand, head)) finishing.holdForEnderman();
        if (crossed(prev, now, Action.FINISH.impact) && distance < 49 && visible(hand, head) && finishing.endermanFinish(this)) {
            sound("bite", 5);
            sound("scream", 5);
        }
    }

    private void contact(Vec3 point, double radius, float damage, double push) {
        for (Object object : worldObj.getEntitiesWithinAABBExcludingEntity(this, cube(point, radius, radius))) {
            Entity hit = (Entity)object;
            EntityLivingBase living = livingOwner(hit);
            if (!canHarm(living) || living == grabbed || struck.contains(living.getUniqueID())) continue;
            if (hit == living && usesPartHitboxes(living)) continue;
            Vec3 center = Vec3.createVectorHelper(
                MathHelper.clamp_double(point.xCoord, hit.boundingBox.minX, hit.boundingBox.maxX),
                MathHelper.clamp_double(point.yCoord, hit.boundingBox.minY, hit.boundingBox.maxY),
                MathHelper.clamp_double(point.zCoord, hit.boundingBox.minZ, hit.boundingBox.maxZ));
            if (center.squareDistanceTo(point) <= radius * radius && visible(point, center)) {
                struck.add(living.getUniqueID());
                hurt(living, damage, push, point);
            }
        }
    }

    private void impact(Vec3 point, double radius, float damage, double push) {
        sound("impact", 5);
        shockwave(point, radius, damage, push);
    }

    private void shockwave(Vec3 point, double radius, float damage, double push) {
        Vec3 origin = point.addVector(0, 0.9, 0);
        for (Object object : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, cube(point, radius, 3.6))) {
            EntityLivingBase living = (EntityLivingBase)object;
            double dx = living.posX - point.xCoord, dz = living.posZ - point.zCoord;
            if (!canHarm(living) || living == grabbed || dx*dx + dz*dz > radius*radius
                || Math.abs(living.posY - point.yCoord) > 2.6 || struck.contains(living.getUniqueID()) || !visible(origin, eye(living))) continue;
            struck.add(living.getUniqueID());
            hurt(living, damage, push, point);
        }
    }

    void terrainImpact(Vec3 point, EntityLivingBase victim, int power) {
        if (canHarm(victim)) hurt(victim, 13 + power * 3, 0.75, point);
        soundAt(point, "impact", 4);
        terrain.impact(point, power + 1, 1, 2);
    }

    private void hurt(EntityLivingBase entity, float damage, double push, Vec3 origin) {
        float amount = damage * GiantEndermanConfig.damage;
        if (entity instanceof EntityPrismarineColossus) ((EntityPrismarineColossus)entity).endermanStrike(this, amount);
        else if (entity.attackEntityFrom(DamageSource.causeMobDamage(this), amount) && push > 0 && !(entity instanceof IBossDisplayData)) {
            double dx = entity.posX - origin.xCoord, dz = entity.posZ - origin.zCoord, distance = Math.max(0.1, Math.hypot(dx, dz));
            entity.addVelocity(dx / distance * push, 0.32, dz / distance * push);
            entity.velocityChanged = true;
        }
    }

    @Override public boolean attackEntityFrom(DamageSource source, float damage) { return hitPart(-1, source, damage); }

    boolean hitPart(int region, DamageSource source, float damage) {
        if (worldObj.isRemote || !isEntityAlive() || source != DamageSource.outOfWorld && !partsVulnerable()
            || !Float.isFinite(damage) || damage <= 0) return false;
        EntityLivingBase attacker = livingOwner(source.getEntity());
        if (attacker instanceof IBossDisplayData && !GiantEndermanConfig.bossCombat) return false;
        if (validTarget(attacker) && (!activated || attacker == target || damage >= 6)) provoke(attacker);
        boolean handHit = grabbed != null && (activeHand() < 0 ? region >= 2 && region <= 4 : region >= 5 && region <= 7);
        if (region == 0 && chestExposed()) damage *= 2.3F;
        if (!super.attackEntityFrom(source, damage)) return false;
        if (!isEntityAlive()) return true;
        if (source.isProjectile() || attacker instanceof EntityPrismarineColossus) dodgeRequested = true;
        if (handHit) {
            handDamage += damage;
            if (handDamage >= 12) interrupt(Action.STAGGER);
        }
        staggerDamage += Math.min(65, damage);
        if (staggerDamage >= 60 && action() != Action.STAGGER && action() != Action.FINISH && action() != Action.VANISH) {
            staggerDamage = 0;
            interrupt(Action.STAGGER);
        }
        return true;
    }

    private void interrupt(Action next) {
        releaseGrip();
        clearFormation();
        finishing = null;
        terrain.clear();
        begin(next);
    }

    private boolean tryBlink(boolean behind) {
        if (teleportCooldown > 0 || blinkLock > 0 || !validTarget(target)) return false;
        double angle = Math.toRadians(target.rotationYaw);
        double distance = target instanceof IBossDisplayData ? 14 : behind ? 8.5 : 12;
        for (int i = 0; i < 8; i++) {
            double offset = i == 0 ? 0 : ((i + 1) / 2) * (i % 2 == 0 ? -0.78 : 0.78);
            double x = target.posX + Math.sin(angle + offset) * distance;
            double z = target.posZ - Math.cos(angle + offset) * distance;
            Vec3 point = findGround(x, z, MathHelper.floor_double(target.posY) + 4, 12);
            if (point == null || getDistanceSq(point.xCoord, point.yCoord, point.zCoord) < 36) continue;
            destination = point;
            teleportCooldown = Math.max(40, (int)(GiantEndermanConfig.teleportCooldown * (phase() == 3 ? 0.68 : phase() == 2 ? 0.84 : 1)));
            blinkLock = teleportCooldown * 2;
            flankingGrab = behind;
            blockedTicks = 0;
            interrupt(Action.VANISH);
            return true;
        }
        teleportCooldown = 25;
        return false;
    }

    public Vec3 findGround(double x, double z, int top, int depth) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        int y = Math.min(top, worldObj.getActualHeight() - 33);
        if (!worldObj.blockExists(bx, Math.max(1, y), bz)) return null;
        for (int i = 0; i <= depth && y > 0; i++, y--) {
            if (World.doesBlockHaveSolidTopSurface(worldObj, bx, y - 1, bz) && safePosition(x, y, z)) {
                return Vec3.createVectorHelper(x, y, z);
            }
        }
        return null;
    }

    public boolean safePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 29999980
            || Math.abs(z) > 29999980 || y < 1 || y + HEIGHT + 2 >= worldObj.getActualHeight()) return false;
        // Fit the standing arms and shoulders at any heading, as well as the full 30-block height.
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 7, y, z - 7, x + 7, y + HEIGHT + 1.5, z + 7);
        return GiantEndermanTerrain.loaded(worldObj, box.expand(2, 0, 2)) && worldObj.func_147461_a(box).isEmpty()
            && !worldObj.isAnyLiquid(box) && worldObj.checkNoEntityCollision(box, this)
            && World.doesBlockHaveSolidTopSurface(worldObj, MathHelper.floor_double(x), MathHelper.floor_double(y - 0.01), MathHelper.floor_double(z))
            && hasFloor(x, y, z, 0.15);
    }

    private boolean hasFloor(double x, double y, double z, double depth) {
        AxisAlignedBB sole = AxisAlignedBB.getBoundingBox(x - 1.2, y - depth, z - 1.2, x + 1.2, y + 0.025, z + 1.2);
        return GiantEndermanTerrain.loaded(worldObj, sole) && !worldObj.func_147461_a(sole).isEmpty();
    }

    private boolean visible(Vec3 from, Vec3 to) {
        AxisAlignedBB line = AxisAlignedBB.getBoundingBox(Math.min(from.xCoord, to.xCoord), Math.min(from.yCoord, to.yCoord),
            Math.min(from.zCoord, to.zCoord), Math.max(from.xCoord, to.xCoord), Math.max(from.yCoord, to.yCoord), Math.max(from.zCoord, to.zCoord));
        return GiantEndermanTerrain.loaded(worldObj, line.expand(1, 0, 1)) && worldObj.func_147447_a(from, to, false, true, false) == null;
    }

    private static Vec3 eye(EntityLivingBase entity) {
        return Vec3.createVectorHelper(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
    }

    private Vec3 targetPoint(EntityLivingBase entity) {
        if (entity == null) return forward(8);
        if (entity == finishing) return finishing.endermanHead();
        double height = entity instanceof IBossDisplayData ? Math.min(22, Math.max(8, entity.height * 0.65)) : entity.height * 0.45;
        if (entity instanceof EntityPrismarineColossus) height = 11 * ((EntityPrismarineColossus)entity).scale();
        return Vec3.createVectorHelper(entity.posX, entity.posY + height, entity.posZ);
    }

    private void setAim(Vec3 goal) {
        double yaw = Math.toRadians(rotationYaw), dx = goal.xCoord - posX, dz = goal.zCoord - posZ;
        dataWatcher.updateObject(AIM_X, (float)(dx * Math.cos(yaw) + dz * Math.sin(yaw)));
        dataWatcher.updateObject(AIM_Y, (float)(goal.yCoord - posY));
        dataWatcher.updateObject(AIM_Z, (float)(dz * Math.cos(yaw) - dx * Math.sin(yaw)));
    }

    private Vec3 forward(double distance) {
        double yaw = Math.toRadians(rotationYaw);
        return Vec3.createVectorHelper(posX - Math.sin(yaw) * distance, posY, posZ + Math.cos(yaw) * distance);
    }

    public Vec3 handPoint() { return bonePoint(hand(activeHand()), 0, -0.6, 0.7); }

    public static Vec3 worldPoint(GiantEndermanPose pose, int bone, double bx, double by, double bz, float yaw, double x, double y, double z) {
        Vec3 local = pose.bones[bone].point(bx, by, bz);
        double radians = Math.toRadians(yaw), cos = Math.cos(radians), sin = Math.sin(radians);
        return Vec3.createVectorHelper(x + local.xCoord*cos - local.zCoord*sin, y + local.yCoord, z + local.xCoord*sin + local.zCoord*cos);
    }

    private Vec3 bonePoint(int bone, double x, double y, double z) {
        return worldPoint(pose, bone, x, y, z, rotationYaw, posX, posY, posZ);
    }

    AxisAlignedBB partBox(int index) {
        int[] region = REGIONS[index];
        Vec3 center = bonePoint(region[0], region[1] / 8D, region[2] / 8D, region[3] / 8D);
        double[] m = pose.bones[region[0]].matrix;
        double yaw = Math.toRadians(rotationYaw), cos = Math.cos(yaw), sin = Math.sin(yaw);
        double hx = 0, hy = 0, hz = 0;
        for (int i = 0; i < 3; i++) {
            double extent = region[4+i] / 16D;
            hx += extent * Math.abs(m[i*4] * cos - m[i*4+2] * sin);
            hy += extent * Math.abs(m[i*4+1]);
            hz += extent * Math.abs(m[i*4] * sin + m[i*4+2] * cos);
        }
        return AxisAlignedBB.getBoundingBox(center.xCoord-hx, center.yCoord-hy, center.zCoord-hz, center.xCoord+hx, center.yCoord+hy, center.zCoord+hz);
    }

    public boolean intersectsParts(java.util.function.Predicate<AxisAlignedBB> intersects) {
        if (!partsVulnerable()) return false;
        for (EntityGiantEndermanPart part : parts) {
            if (part != null && !part.isDead && intersects.test(part.boundingBox)) return true;
        }
        return false;
    }

    private void maintainParts() {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && !parts[i].isDead && parts[i].addedToChunk) continue;
            if (!GiantEndermanTerrain.loaded(worldObj, partBox(i))) continue;
            if (parts[i] != null) parts[i].setDead();
            EntityGiantEndermanPart part = new EntityGiantEndermanPart(worldObj);
            part.attach(this, i);
            if (worldObj.spawnEntityInWorld(part)) parts[i] = part;
        }
    }

    static boolean usesPartHitboxes(EntityLivingBase entity) {
        return entity instanceof EntityPrismarineColossus || entity instanceof EntityGiantEnderman || entity.getParts() != null;
    }

    static EntityLivingBase livingOwner(Entity entity) {
        if (entity instanceof EntityGiantEndermanPart) return ((EntityGiantEndermanPart)entity).owner();
        if (entity instanceof EntityColossusPart) return ((EntityColossusPart)entity).owner();
        if (entity instanceof EntityDragonPart && ((EntityDragonPart)entity).entityDragonObj instanceof EntityLivingBase) {
            return (EntityLivingBase)((EntityDragonPart)entity).entityDragonObj;
        }
        return entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
    }

    private static AxisAlignedBB cube(Vec3 point, double horizontal, double vertical) {
        return AxisAlignedBB.getBoundingBox(point.xCoord-horizontal, point.yCoord-vertical, point.zCoord-horizontal,
            point.xCoord+horizontal, point.yCoord+vertical, point.zCoord+horizontal);
    }

    private static boolean crossed(double prev, double now, int key) { return prev < key && now >= key; }
    private void sound(String event, float volume) { playSound("hbm:giant_enderman." + event, volume, 0.95F + rand.nextFloat() * 0.1F); }
    private void soundAt(Vec3 point, String event, float volume) {
        worldObj.playSoundEffect(point.xCoord, point.yCoord, point.zCoord, "hbm:giant_enderman." + event, volume, 1);
    }
    @Override protected String getLivingSound() { return "hbm:giant_enderman.idle"; }
    @Override protected String getHurtSound() { return "hbm:giant_enderman.hurt"; }
    @Override protected String getDeathSound() { return null; }
    @Override protected float getSoundVolume() { return 2.5F; }

    EntityEndermanTerrain heldTerrain() { return formation; }
    private void clearFormation() {
        if (formation != null) formation.setDead();
        formation = null;
    }

    void unload() {
        releaseGrip();
        clearFormation();
        terrain.clear();
        for (EntityGiantEndermanPart part : parts) if (part != null) part.setDead();
    }

    @Override public void setDead() {
        if (!worldObj.isRemote) unload();
        super.setDead();
    }

    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!worldObj.isRemote && dead) {
            unload();
            begin(Action.DEATH);
        }
    }

    @Override protected void onDeathUpdate() {
        if (worldObj.isRemote) {
            syncClientPose();
            return;
        }
        if (action() != Action.DEATH) begin(Action.DEATH);
        if (frame(0) >= Action.DEATH.length) {
            deathTime = 19;
            super.onDeathUpdate();
        }
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("Activated", activated);
        tag.setBoolean("PendingRage", pendingRage);
        tag.setInteger("EncounterPhase", phase());
        tag.setInteger("TeleportCooldown", teleportCooldown);
        tag.setInteger("BlinkLock", blinkLock);
        tag.setInteger("ThrowCooldown", throwCooldown);
        tag.setInteger("AttackSequence", sequence);
        tag.setInteger("TargetMemory", targetMemory);
        UUID remembered = provoker != null ? provoker : target != null ? target.getUniqueID() : null;
        if (remembered != null) {
            tag.setLong("TargetMost", remembered.getMostSignificantBits());
            tag.setLong("TargetLeast", remembered.getLeastSignificantBits());
            if (targetMemory == 0) tag.setInteger("TargetMemory", 600);
        }
        if (action() == Action.DEATH) tag.setInteger("DeathProgress", (int)frame(0));
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        activated = tag.getBoolean("Activated");
        pendingRage = tag.getBoolean("PendingRage");
        dataWatcher.updateObject(PHASE, MathHelper.clamp_int(tag.getInteger("EncounterPhase"), 1, 3));
        teleportCooldown = Math.max(0, tag.getInteger("TeleportCooldown"));
        blinkLock = Math.max(0, tag.getInteger("BlinkLock"));
        throwCooldown = Math.max(0, tag.getInteger("ThrowCooldown"));
        sequence = tag.getInteger("AttackSequence") & Integer.MAX_VALUE;
        targetMemory = MathHelper.clamp_int(tag.getInteger("TargetMemory"), 0, 1200);
        if (tag.hasKey("TargetMost")) provoker = new UUID(tag.getLong("TargetMost"), tag.getLong("TargetLeast"));
        if (getHealth() <= 0) {
            begin(Action.DEATH);
            dataWatcher.updateObject(START, (int)worldObj.getTotalWorldTime() - MathHelper.clamp_int(tag.getInteger("DeathProgress"), 0, 81));
        }
        // Transient attacks resume from recovery. Held terrain and victims are never serialized.
        recovery = 35;
    }
}
