package com.hbm.entity.mob.giantghast;

import java.lang.reflect.Field;
import java.util.UUID;

import com.hbm.config.GiantGhastConfig;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

public final class EntityGiantGhast extends EntityFlying implements IBossDisplayData, IEntityMultiPart, IMob, IEntityAdditionalSpawnData {
    // A square envelope large enough for the outer flank corners at any yaw.
    public static final float WIDTH = (float)(2 * Math.hypot(GiantGhastShape.RIGHT.maxX, GiantGhastShape.RIGHT.maxZ));
    public static final float ROOF = (float)GiantGhastShape.ROOF_FRONT.maxY;
    public static final float BODY_BASE = (float)GiantGhastShape.HEAD.minY;
    public static final int DEATH_TICKS = 58;
    private static final int PHASE = 20, POSE = 21, DEATH = 22, STATE = 23;
    private static final int FIRING_POSE = 32, MUZZLE_FLASH_TICKS = 2;
    private static final int MIN_SPIN_TICKS = 76, BARRAGE_TICKS = 120, CORE_COOLDOWN = 10;
    private static final double HOVER_CLEARANCE = 0.18;
    private static final byte CORE_HIT = 61;
    private static final float BODY_DAMAGE = 0.28F;
    // Keep the vanilla name for attribute packets, NBT and modifiers, with a boss-local range.
    private static final IAttribute BOSS_HEALTH = new RangedAttribute(
        SharedMonsterAttributes.maxHealth.getAttributeUnlocalizedName(), 20, 0.1, GiantGhastConfig.MAX_HEALTH)
        .setDescription("Max Health").setShouldWatch(true);
    private static final Field ATTRIBUTE_MAP = ReflectionHelper.findField(EntityLivingBase.class, "attributeMap", "field_110155_d");
    private static final GiantGhastShape[] SHAPES = GiantGhastShape.values();

    public enum State {
        IDLE, RETURN, RAPID, LARGE_FLOWER, TRIPLE, SMALL_FLOWER, SUMMON, DESCENT, SETTLE, EXPOSED, DYING;

        boolean targetsEntity() { return this != IDLE && ordinal() < DESCENT.ordinal(); }
    }
    private static final State[] STATES = State.values();
    private final EntityDragonPart[] parts = new EntityDragonPart[SHAPES.length];

    private EntityPlayer playerTarget;
    private EntityLivingBase attackTarget;
    private UUID attackTargetId;
    private GiantGhastEncounters.Fight fight;
    private boolean admitted, nextSmallFlower, allowDeath;
    private int attackAge, recovery = 40, summonWait, targetCheck, flash, muzzleFlash, coreWait, coreStrikes, deathXp;
    private double hoverY = Double.NaN;
    private float restYaw;

    public EntityGiantGhast(World world) {
        super(world);
        for (int i = 0; i < parts.length; i++) parts[i] = hitPart(SHAPES[i]);
        setSize(WIDTH, ROOF);
        isImmuneToFire = true;
        experienceValue = 100;
        scoreValue = 100;
    }

    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(PHASE, (byte)1);
        dataWatcher.addObject(POSE, 0);
        dataWatcher.addObject(DEATH, (byte)0);
        dataWatcher.addObject(STATE, (byte)State.IDLE.ordinal());
    }

    @Override protected void applyEntityAttributes() {
        // Vanilla also reads its backing field directly for equipment modifiers.
        try {
            ATTRIBUTE_MAP.set(this, new ServersideAttributeMap() {
                @Override public IAttributeInstance registerAttribute(IAttribute attribute) {
                    return super.registerAttribute(attribute == SharedMonsterAttributes.maxHealth ? BOSS_HEALTH : attribute);
                }

                @Override public ModifiableAttributeInstance getAttributeInstance(IAttribute attribute) {
                    return super.getAttributeInstance(attribute == SharedMonsterAttributes.maxHealth ? BOSS_HEALTH : attribute);
                }
            });
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot install the Giant Ghast attribute map", ex);
        }
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(GiantGhastConfig.health);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(GiantGhastConfig.range);
    }

    @Override public World func_82194_d() { return worldObj; }
    @Override public Entity[] getParts() { return parts; }
    @Override public float getEyeHeight() { return 9.4F; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean isEntityInsideOpaqueBlock() { return false; }
    @Override protected String getLivingSound() { return "mob.ghast.moan"; }
    @Override protected String getHurtSound() { return "mob.ghast.scream"; }
    @Override protected String getDeathSound() { return "mob.ghast.death"; }
    @Override protected float getSoundVolume() { return 4.2F; }
    @Override protected float getSoundPitch() { return 0.57F + rand.nextFloat() * 0.14F; }
    @Override public String getCommandSenderName() {
        return hasCustomNameTag() ? getCustomNameTag() : StatCollector.translateToLocal("entity.hbm.giant_ghast.name");
    }

    public int phase() { return dataWatcher.getWatchableObjectByte(PHASE); }
    public State state() { return STATES[dataWatcher.getWatchableObjectByte(STATE)]; }
    public int pose() { return dataWatcher.getWatchableObjectInt(POSE); }
    public boolean isFiring() { return (pose() & FIRING_POSE) != 0; }
    public int deathProgress() { return Math.max(deathTime, dataWatcher.getWatchableObjectByte(DEATH)); }
    boolean wasAdmitted() { return admitted; }
    boolean canSummonsFire() { return state().ordinal() < State.SETTLE.ordinal(); }

    private void changeState(State state) {
        dataWatcher.updateObject(STATE, (byte)state.ordinal());
        attackAge = 0;
        attackTarget = null;
        attackTargetId = null;
    }

    @Override public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
        if (parts != null && parts[0] != null) updateParts();
    }

    private EntityDragonPart hitPart(GiantGhastShape shape) {
        return new EntityDragonPart(this, shape.name(), (float)(shape.maxX - shape.minX), (float)(shape.maxY - shape.minY)) {
            @Override public boolean canBeCollidedWith() { return EntityGiantGhast.this.isEntityAlive(); }
            @Override public float getCollisionBorderSize() { return 0; }
            @Override public AxisAlignedBB getBoundingBox() {
                return state() == State.EXPOSED && EntityGiantGhast.this.isEntityAlive() ? boundingBox : null;
            }
        };
    }

    private void updateParts() {
        // At rest the yaw is cardinal, so the world boxes are exact and can be used by vanilla movement.
        double yaw = Math.toRadians(rotationYaw), sin = Math.sin(yaw), cos = Math.cos(yaw);
        if (state() == State.EXPOSED) {
            sin = Math.round(sin);
            cos = Math.round(cos);
        }
        for (int i = 0; i < parts.length; i++) {
            GiantGhastShape shape = SHAPES[i];
            EntityDragonPart part = parts[i];
            double x = (shape.minX + shape.maxX) * 0.5, z = (shape.minZ + shape.maxZ) * 0.5;
            double halfX = (shape.maxX - shape.minX) * 0.5, halfZ = (shape.maxZ - shape.minZ) * 0.5;
            part.posX = posX + x * cos - z * sin;
            part.posY = posY + (shape.minY + shape.maxY) * 0.5;
            part.posZ = posZ + x * sin + z * cos;
            double rx = halfX * Math.abs(cos) + halfZ * Math.abs(sin);
            double rz = halfX * Math.abs(sin) + halfZ * Math.abs(cos);
            part.boundingBox.setBounds(part.posX - rx, posY + shape.minY, part.posZ - rz,
                part.posX + rx, posY + shape.maxY, part.posZ + rz);
        }
    }

    private Vec3 local(Vec3 world, double sin, double cos) {
        double x = world.xCoord - posX, z = world.zCoord - posZ;
        return Vec3.createVectorHelper(x * cos + z * sin, world.yCoord - posY, z * cos - x * sin);
    }

    MovingObjectPosition tracePart(Entity part, Vec3 from, Vec3 to) {
        if (state() == State.EXPOSED) return part.boundingBox.calculateIntercept(from, to);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != part) continue;
            double yaw = Math.toRadians(rotationYaw), sin = Math.sin(yaw), cos = Math.cos(yaw);
            MovingObjectPosition hit = SHAPES[i].local.calculateIntercept(local(from, sin, cos), local(to, sin, cos));
            if (hit != null) {
                Vec3 point = hit.hitVec;
                hit.hitVec = Vec3.createVectorHelper(posX + point.xCoord * cos - point.zCoord * sin,
                    posY + point.yCoord, posZ + point.xCoord * sin + point.zCoord * cos);
            }
            return hit;
        }
        return null;
    }

    public boolean hasSpawnClearance() { return GiantGhastFlight.clear(worldObj, boundingBox); }

    @Override public boolean getCanSpawnHere() {
        return GiantGhastConfig.natural && worldObj.provider.dimensionId == -1 && worldObj.difficultySetting != EnumDifficulty.PEACEFUL
            && rand.nextInt(20) == 0 && GiantGhastEncounters.get(worldObj).isEmpty() && hasSpawnClearance()
            && super.getCanSpawnHere();
    }

    @Override public int getMaxSpawnedInChunk() { return 1; }



    @Override public void onUpdate() {
        super.onUpdate();
        if (!isEntityAlive()) return;
        updateParts();
        if (worldObj.isRemote) clientEffects();
        else if (state() == State.EXPOSED) GiantGhastFlight.supportPlayers(this, parts, posY + GiantGhastShape.LEFT.maxY);
    }

    @Override protected void updateEntityActionState() {
        if (!isEntityAlive()) return;
        if (worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
            setDead();
            return;
        }
        GiantGhastEncounters encounters = GiantGhastEncounters.get(worldObj);
        if (fight == null) {
            fight = encounters.attach(this, admitted);
            if (fight == null) {
                // A duplicate saved boss must not retire the live original.
                super.setDead();
                return;
            }
            admitted = true;
        }

        float fraction = getHealth() / getMaxHealth();
        int phase = Math.max(phase(), phaseFor(fraction));
        dataWatcher.updateObject(PHASE, (byte)phase);
        if (flash > 0) flash--;
        if (muzzleFlash > 0) muzzleFlash--;
        if (coreWait > 0) coreWait--;
        if (phase == 5) {
            if (state().ordinal() < State.DESCENT.ordinal()) {
                changeState(State.DESCENT);
                playSound("mob.ghast.scream", 7, 0.48F);
            }
            tickFinal(encounters);
            return;
        }

        if (targetCheck-- <= 0) {
            playerTarget = acquirePlayer();
            targetCheck = 12;
        }
        if (attackTarget == null && attackTargetId != null) {
            Entity target = encounters.member(worldObj, getUniqueID(), attackTargetId);
            if (target == null) target = GiantGhastEncounters.player(worldObj, attackTargetId);
            if (target instanceof EntityLivingBase) attackTarget = (EntityLivingBase)target;
        }
        fly(GiantGhastFlight.combatant(attackTarget, worldObj) ? attackTarget : playerTarget, phase);
        if (summonWait > 0) summonWait--;
        if (recovery > 0) recovery--;

        if (state() == State.IDLE && recovery == 0) {
            EntityLivingBase target = fraction <= GiantGhastConfig.enrageThreshold && !nextSmallFlower
                ? findRebel(encounters) : null;
            if (target == null) target = playerTarget;
            if (canTarget(target)) {
                State next;
                if (fraction <= GiantGhastConfig.summonThreshold && summonWait == 0
                        && encounters.count(worldObj, fight, false) < GiantGhastConfig.maxSummons) {
                    next = State.SUMMON;
                    summonWait = GiantGhastConfig.summonCooldown;
                } else {
                    next = phase == 1 ? State.RETURN : phase == 2 ? State.RAPID : phase == 3 ? State.LARGE_FLOWER
                        : nextSmallFlower ? State.SMALL_FLOWER : State.TRIPLE;
                    if (phase == 4) nextSmallFlower = !nextSmallFlower;
                }
                changeState(next);
                attackTarget = target;
                attackTargetId = target.getUniqueID();
                face(target);
                playSound(next == State.SUMMON ? "mob.ghast.scream" : "mob.ghast.charge", 4, 0.62F);
            }
        }
        if (state().targetsEntity()) tickAttack(encounters, phase);
        else syncPose(0, false, 0);
    }

    private static int phaseFor(float fraction) {
        return fraction <= 0.10F ? 5 : fraction <= 0.25F ? 4 : fraction <= 0.50F ? 3 : fraction <= 0.75F ? 2 : 1;
    }

    private EntityPlayer acquirePlayer() {
        EntityPlayer nearest = null;
        double best = GiantGhastConfig.range * GiantGhastConfig.range;
        for (Object object : worldObj.playerEntities) {
            EntityPlayer candidate = (EntityPlayer)object;
            double distance = getDistanceSqToEntity(candidate);
            if (distance < best && canTarget(candidate)) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    EntityPlayer playerTarget() { return canTarget(playerTarget) ? playerTarget : null; }

    private boolean canTarget(EntityLivingBase target) {
        return GiantGhastFlight.combatant(target, worldObj)
            && getDistanceSqToEntity(target) <= GiantGhastConfig.range * GiantGhastConfig.range
            && GiantGhastFlight.visible(worldObj, Vec3.createVectorHelper(posX, posY + getEyeHeight(), posZ),
                GiantGhastFlight.center(target));
    }

    private void face(EntityLivingBase target) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        if (dx * dx + dz * dz > 0.04) rotationYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void fly(EntityLivingBase target, int phase) {
        if (Double.isNaN(hoverY)) hoverY = Math.min(worldObj.getActualHeight() - height - 2, posY + 3);
        double speed = 0.11 + 0.045 * phase;
        double vx = 0, vz = 0, altitude = hoverY;
        if (GiantGhastFlight.combatant(target, worldObj)) {
            face(target);
            double dx = target.posX - posX, dz = target.posZ - posZ;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal > 0.2) {
                double approach = MathHelper.clamp_double((horizontal - 25) / 12, -0.65, 1);
                double orbit = (getEntityId() % 2 == 0 ? 1 : -1) * (Math.abs(approach) < 0.8 ? 0.4 : 0.15);
                vx = (dx * approach - dz * orbit) * speed / horizontal;
                vz = (dz * approach + dx * orbit) * speed / horizontal;
            } else {
                vx = Math.sin(Math.toRadians(rotationYaw)) * speed;
                vz = -Math.cos(Math.toRadians(rotationYaw)) * speed;
            }
            altitude = Math.min(hoverY + 6, target.boundingBox.minY + (target instanceof EntitySummonedGhast ? -2 : 7));
        } else {
            double drift = ticksExisted * 0.022 + getEntityId();
            vx = Math.cos(drift) * speed * 0.45;
            vz = Math.sin(drift) * speed * 0.45;
        }
        double wantedY = MathHelper.clamp_double(altitude + Math.sin(ticksExisted * 0.085) * 0.85,
            2, worldObj.getActualHeight() - height - 1);
        motionX = GiantGhastFlight.approach(motionX, vx, 0.025);
        motionY = GiantGhastFlight.approach(motionY, MathHelper.clamp_double((wantedY - posY) * 0.08, -speed, speed), 0.022);
        motionZ = GiantGhastFlight.approach(motionZ, vz, 0.025);
    }

    @Override public void moveEntityWithHeading(float strafe, float forward) {
        if (worldObj.isRemote || !isEntityAlive() || state().ordinal() >= State.DESCENT.ordinal()) return;
        AxisAlignedBB envelope = boundingBox;
        if (GiantGhastFlight.clear(worldObj, envelope.addCoord(motionX, motionY, motionZ))) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }

        if (motionX != 0 && !GiantGhastFlight.clear(worldObj, envelope.addCoord(motionX, 0, 0))) motionX = 0;
        AxisAlignedBB moved = envelope.getOffsetBoundingBox(motionX, 0, 0);
        if (motionZ != 0 && !GiantGhastFlight.clear(worldObj, moved.addCoord(0, 0, motionZ))) motionZ = 0;
        moved.offset(0, 0, motionZ);
        if (motionY != 0 && !GiantGhastFlight.clear(worldObj, moved.addCoord(0, motionY, 0))) motionY = 0;
        if (motionX != 0 || motionY != 0 || motionZ != 0) setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        else if (GiantGhastFlight.clear(worldObj, envelope.addCoord(0, 0.19, 0))) setPosition(posX, posY + 0.19, posZ);
        else if (ticksExisted % 20 == 0 && !GiantGhastFlight.clear(worldObj, envelope)) {
            // Recover an obstructed command spawn or older saved boss into free space.
            for (int dy = 1; dy <= 16; dy++) {
                if (GiantGhastFlight.clear(worldObj, envelope.getOffsetBoundingBox(0, dy, 0))) {
                    setPosition(posX, posY + dy, posZ);
                    break;
                }
            }
        }
    }

    private void tickAttack(GiantGhastEncounters encounters, int phase) {
        State attack = state();
        if (attack != State.SUMMON && !GiantGhastFlight.combatant(attackTarget, worldObj)) {
            finishAttack(phase);
            return;
        }
        int age = attackAge + 1;
        int charge, interval, shots, group;
        switch (attack) {
            case RETURN: charge = 18; interval = 10; shots = 5; group = 1; break;
            case RAPID: charge = 14; interval = 3; shots = 15; group = 1; break;
            case LARGE_FLOWER: charge = 22; interval = 1; shots = 1; group = 11; break;
            case TRIPLE: charge = 10; interval = 1; shots = 1; group = 3; break;
            case SMALL_FLOWER: charge = 12; interval = 1; shots = 1; group = 5; break;
            case SUMMON: charge = 32; interval = 1; shots = 1; group = 0; break;
            default: return;
        }
        boolean summon = attack == State.SUMMON;
        int faces = summon ? 7 : 2;
        int lastShot = charge + (shots - 1) * interval;
        int opening = age > lastShot ? 0 : age <= charge ? chargeOpening(age, charge)
            : chargeOpening((age - charge - 1) % interval + 1, interval);
        if (attack == State.RAPID && age >= charge && age <= lastShot) opening = 15;
        if (age >= charge && age <= lastShot && (age - charge) % interval == 0) {
            if (summon) {
                summonWave(encounters, phase >= 4 ? 7 : 4);
            } else {
                if (!canTarget(attackTarget)) {
                    finishAttack(phase);
                    return;
                }
                int count = Math.min(group, GiantGhastConfig.maxFireballs);
                if (encounters.count(worldObj, fight, true) > GiantGhastConfig.maxFireballs - count) {
                    syncPose(faces, false, opening);
                    return;
                }
                firePattern(attack, (age - charge) / interval, count, false);
            }
        }
        syncPose(opening == 0 ? 0 : faces, summon && opening > 0, opening);
        attackAge = age;
        if (age >= lastShot + 5) finishAttack(phase);
    }

    private void finishAttack(int phase) {
        changeState(State.IDLE);
        syncPose(0, false, 0);
        int delay = GiantGhastConfig.attackCooldown * (5 - phase) / 4;
        recovery = Math.max(8, delay + (phase == 4 ? rand.nextInt(9) - 4 : 0));
    }

    private void syncPose(int faces, boolean scream, int opening) {
        dataWatcher.updateObject(POSE, faces | (scream ? 8 : 0) | (flash > 0 ? 16 : 0)
            | (muzzleFlash > 0 ? FIRING_POSE : 0) | opening << 8);
    }

    private static int chargeOpening(int tick, int duration) { return 2 + 9 * tick / duration; }

    private void tickFinal(GiantGhastEncounters encounters) {
        motionX = motionY = motionZ = 0;
        attackAge++;
        switch (state()) {
            case DESCENT:
                rotationYaw = MathHelper.wrapAngleTo180_float(rotationYaw + Math.min(16, 0.8F + attackAge * 0.13F));
                if (attackAge % 24 == 0 && attackAge <= BARRAGE_TICKS) playSound("mob.ghast.scream", 7, 0.65F);
                if (attackAge % 5 == 0 && attackAge <= BARRAGE_TICKS) {
                    int free = GiantGhastConfig.maxFireballs - encounters.count(worldObj, fight, true);
                    int pattern = attackAge / 5 % 3;
                    if (free > 0) firePattern(pattern == 0 ? State.TRIPLE : pattern == 1 ? State.SMALL_FLOWER : State.RETURN,
                        0, Math.min(free, pattern == 0 ? 3 : pattern == 1 ? 5 : 1), true);
                }
                int opening = attackAge <= BARRAGE_TICKS ? chargeOpening((attackAge - 1) % 5 + 1, 5) : 0;
                syncPose(7, true, opening);
                double drop = GiantGhastFlight.availableDrop(worldObj, boundingBox, 3);
                if (drop < 0) return;
                double remaining = Math.max(0, drop - HOVER_CLEARANCE);
                if (remaining > 0.025) {
                    double step = Math.min(remaining, MathHelper.clamp_double(remaining * 0.22, 0.028, 0.33));
                    setPosition(posX, posY - step, posZ);
                } else if (attackAge >= MIN_SPIN_TICKS) {
                    restYaw = MathHelper.wrapAngleTo180_float(Math.round(rotationYaw / 90) * 90);
                    changeState(State.SETTLE);
                }
                break;
            case SETTLE:
                syncPose(7, true, Math.max(0, 15 - attackAge * 2));
                float delta = MathHelper.wrapAngleTo180_float(restYaw - rotationYaw);
                rotationYaw += MathHelper.clamp_float(delta, -6, 6);
                if (Math.abs(delta) <= 6) {
                    rotationYaw = restYaw;
                    changeState(State.EXPOSED);
                    syncPose(0, false, 0);
                }
                break;
            case EXPOSED:
                attackAge = 0;
                rotationYaw = restYaw;
                syncPose(0, false, 0);
                break;
            default:
                break;
        }
    }

    public static double mouthY(int face) { return face == 1 ? 9.85 : 10.1; }
    public static double eyeY(int face) { return face == 1 ? 12.65 : 12.55; }

    private Vec3 muzzle(double clearance, double yaw) {
        double edge = GiantGhastShape.HEAD.maxZ + clearance;
        return Vec3.createVectorHelper(posX - edge * Math.sin(yaw), posY + mouthY(1), posZ + edge * Math.cos(yaw));
    }

    private void firePattern(State pattern, int shot, int count, boolean stray) {
        boolean flower = pattern == State.LARGE_FLOWER || pattern == State.SMALL_FLOWER;
        double radius = pattern == State.LARGE_FLOWER ? 2.2 : pattern == State.SMALL_FLOWER ? 1.05 : 0;
        double yaw = Math.toRadians(rotationYaw);
        Vec3 origin = muzzle(0.66 + radius, yaw);
        Vec3 direction;
        if (stray) {
            double angle = yaw + (rand.nextDouble() - 0.5) * 1.4;
            direction = Vec3.createVectorHelper(-Math.sin(angle), rand.nextDouble() * 1.15 - 0.52, Math.cos(angle)).normalize();
        } else {
            Vec3 target = GiantGhastFlight.center(attackTarget);
            direction = Vec3.createVectorHelper(target.xCoord - origin.xCoord, target.yCoord - origin.yCoord, target.zCoord - origin.zCoord).normalize();
            // Avoid sending aimed fire back through the head if a target gets inside the muzzle.
            if (direction.xCoord * -Math.sin(yaw) + direction.zCoord * Math.cos(yaw) <= 0) return;
        }
        Vec3 right = Vec3.createVectorHelper(direction.zCoord, 0, -direction.xCoord).normalize();
        Vec3 up = direction.crossProduct(right).normalize();
        if (flower) {
            boolean large = pattern == State.LARGE_FLOWER;
            boolean center = large || count == 1;
            int petals = count - (center ? 1 : 0);
            if (center) {
                Vec3 hub = large ? origin.addVector(direction.xCoord * 0.42, direction.yCoord * 0.42, direction.zCoord * 0.42) : origin;
                fire(this, hub, direction, 0.72F, 0.82F, !stray, stray);
            }
            for (int i = 0; i < petals; i++) {
                double angle = Math.PI / 2 + i * 2 * Math.PI / petals;
                double side = Math.cos(angle), rise = Math.sin(angle);
                Vec3 radial = Vec3.createVectorHelper(right.xCoord * side + up.xCoord * rise,
                    right.yCoord * side + up.yCoord * rise, right.zCoord * side + up.zCoord * rise);
                double ringRadius = large && (i & 1) != 0 ? radius * 0.8 : radius;
                double depth = large && (i & 1) != 0 ? -0.30 : 0;
                double spread = large ? 0.15 * ringRadius / radius : 0.085;
                fire(this, origin.addVector(radial.xCoord * ringRadius + direction.xCoord * depth,
                        radial.yCoord * ringRadius + direction.yCoord * depth, radial.zCoord * ringRadius + direction.zCoord * depth),
                    direction.addVector(radial.xCoord * spread, radial.yCoord * spread, radial.zCoord * spread),
                    0.72F, 0.82F, !stray && !center && i == 0, stray || pattern == State.SMALL_FLOWER);
            }
        } else if (pattern == State.TRIPLE) {
            fire(this, origin, direction, 0.94F, 0.95F, !stray, stray);
            for (int i = 1; i < count; i++) {
                double offset = i == 1 ? -0.68 : 0.68;
                fire(this, origin.addVector(right.xCoord * offset, 0, right.zCoord * offset),
                    direction.addVector(right.xCoord * offset * 0.25, 0, right.zCoord * offset * 0.25),
                    0.94F, 0.95F, false, true);
            }
        } else {
            boolean rapid = pattern == State.RAPID;
            if (rapid) {
                double spread = (rand.nextDouble() - 0.5) * 0.09;
                direction = direction.addVector(right.xCoord * spread, (rand.nextDouble() - 0.5) * 0.06, right.zCoord * spread);
            }
            boolean reflectable = !stray && (rapid ? shot == 7 || shot == 14 : shot == 4);
            fire(this, origin, direction, rapid ? 0.69F : 1.1F, rapid ? 0.73F : 1, reflectable, stray);
        }
        worldObj.playSoundEffect(origin.xCoord, origin.yCoord, origin.zCoord, "mob.ghast.fireball",
            3.3F, 0.65F + rand.nextFloat() * 0.2F);
    }

    boolean fire(EntityLivingBase shooter, Vec3 origin, Vec3 heading, float power, float damage, boolean reflectable, boolean stray) {
        if (heading.lengthVector() < 0.1) return false;
        EntityGiantGhastFireball shot = new EntityGiantGhastFireball(this, shooter, origin, heading, power, damage, reflectable, stray);
        if (!GiantGhastFlight.clear(worldObj, shot.boundingBox)
                || !GiantGhastEncounters.get(worldObj).spawnChild(this, shot, shot.expires())) return false;
        if (shooter == this) muzzleFlash = MUZZLE_FLASH_TICKS;
        else worldObj.playSoundEffect(origin.xCoord, origin.yCoord, origin.zCoord, "mob.ghast.fireball", 1.1F, 1);
        return true;
    }

    private EntitySummonedGhast findRebel(GiantGhastEncounters encounters) {
        EntitySummonedGhast best = null;
        double distance = 38 * 38;
        for (EntitySummonedGhast summon : encounters.summons(worldObj, fight)) {
            double d = getDistanceSqToEntity(summon);
            if (summon.isRetaliating() && d < distance && d > 81 && canTarget(summon)) {
                best = summon;
                distance = d;
            }
        }
        return best;
    }

    private void summonWave(GiantGhastEncounters encounters, int count) {
        double start = rand.nextDouble() * Math.PI * 2;
        int attempts = count * 4;
        for (int i = 0; i < attempts && count > 0; i++) {
            if (encounters.count(worldObj, fight, false) >= GiantGhastConfig.maxSummons) break;
            double angle = start + i * 2.39996;
            double distance = 13 + rand.nextDouble() * 10;
            double y = MathHelper.clamp_double(posY + 5 + rand.nextDouble() * 7, 3, worldObj.getActualHeight() - 5);
            EntitySummonedGhast minion = new EntitySummonedGhast(worldObj, getUniqueID());
            minion.setLocationAndAngles(posX + Math.cos(angle) * distance, y, posZ + Math.sin(angle) * distance, rand.nextFloat() * 360, 0);
            if (GiantGhastFlight.clear(worldObj, minion.boundingBox) && worldObj.checkNoEntityCollision(minion.boundingBox)
                    && encounters.spawnChild(this, minion, 0)) count--;
        }
    }



    @Override public void setHealth(float health) {
        super.setHealth(worldObj != null && !worldObj.isRemote && !allowDeath ? Math.max(1, health) : health);
    }

    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if (worldObj.isRemote || !isEntityAlive() || state().ordinal() >= State.DESCENT.ordinal()
                || phaseFor(getHealth() / getMaxHealth()) == 5
                || source == DamageSource.inWall || source.getEntity() == this) return false;
        Entity direct = source.getSourceOfDamage();
        boolean reflected = direct instanceof EntityGiantGhastFireball && ((EntityGiantGhastFireball)direct).wasReflected()
            || "fireball".equals(source.getDamageType()) && source.getEntity() instanceof EntityPlayer;
        if (source.isFireDamage() && !reflected) return false;
        boolean rebel = source.getEntity() instanceof EntitySummonedGhast
            && ((EntitySummonedGhast)source.getEntity()).isRetaliating()
            && ((EntitySummonedGhast)source.getEntity()).belongsTo(getUniqueID());
        float damage = reflected ? Math.max(getMaxHealth() * 0.12F, amount)
            : rebel || source == DamageSource.outOfWorld ? amount : amount * BODY_DAMAGE;
        return super.attackEntityFrom(source, damage);
    }

    @Override public boolean attackEntityFromPart(EntityDragonPart part, DamageSource source, float amount) {
        if (worldObj.isRemote || !isEntityAlive()) return false;
        if (state() != State.EXPOSED) return attackEntityFrom(source, amount);
        Entity attacker = source.getEntity();
        if (part != parts[GiantGhastShape.CORE.ordinal()] || coreWait != 0 || !(amount > 0)
                || !(attacker instanceof EntityPlayer) || source.getSourceOfDamage() != attacker
                || !"player".equals(source.getDamageType()) || isEntityInvulnerable()
                || !aimedAtCore((EntityPlayer)attacker) || ForgeHooks.onLivingAttack(this, source, amount)) return false;

        coreWait = CORE_COOLDOWN;
        flash = 8;
        worldObj.setEntityState(this, CORE_HIT);
        if (++coreStrikes < GiantGhastConfig.weakPointStrikes) {
            float remaining = (GiantGhastConfig.weakPointStrikes - coreStrikes) / (float)GiantGhastConfig.weakPointStrikes;
            setHealth(Math.min(getHealth(), getMaxHealth() * 0.10F * remaining));
            playSound("mob.ghast.scream", 6, 0.70F + 0.25F * (1 - remaining));
        } else {
            // The final attacker owns the kill even if someone else did most of the damage.
            attackingPlayer = (EntityPlayer)attacker;
            recentlyHit = 100;
            allowDeath = true;
            setHealth(0);
            onDeath(source);
            if (!dead) {
                // A death event canceled the kill. Allow a later strike to try again.
                allowDeath = false;
                coreStrikes--;
                setHealth(1);
            }
        }
        return true;
    }

    @Override public EntityLivingBase func_94060_bK() {
        return allowDeath && attackingPlayer != null ? attackingPlayer : super.func_94060_bK();
    }

    private boolean aimedAtCore(EntityPlayer player) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        if (!player.isEntityAlive() || eye.yCoord <= posY + ROOF) return false;
        Vec3 look = player.getLookVec();
        double reach = player.capabilities.isCreativeMode ? 6 : 3;
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = tracePart(parts[GiantGhastShape.CORE.ordinal()], eye, end);
        if (hit == null || !GiantGhastFlight.visible(worldObj, eye, hit.hitVec)) return false;
        double distance = eye.squareDistanceTo(hit.hitVec);
        for (int i = 0; i < parts.length; i++) {
            if (i == GiantGhastShape.CORE.ordinal()) continue;
            AxisAlignedBB box = parts[i].boundingBox;
            MovingObjectPosition obstruction = box.calculateIntercept(eye, end);
            if (box.isVecInside(eye) || obstruction != null && eye.squareDistanceTo(obstruction.hitVec) < distance) return false;
        }
        return true;
    }

    @Override protected void dropFewItems(boolean playerKill, int looting) {
        dropItem(Items.ghast_tear, 8 + rand.nextInt(5) + looting);
        dropItem(Items.gunpowder, 6 + rand.nextInt(6) + looting);
    }

    @Override public void onDeath(DamageSource source) {
        if (!worldObj.isRemote && !allowDeath) return;
        super.onDeath(source);
        if (!worldObj.isRemote && dead) {
            if (recentlyHit > 0 && worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) deathXp = getExperiencePoints(attackingPlayer);
            changeState(State.DYING);
            motionX = motionY = motionZ = 0;
            playSound(getDeathSound(), 7, 0.48F);
            GiantGhastEncounters.get(worldObj).retire(worldObj, getUniqueID());
        }
    }

    @Override public void setDead() {
        if (!worldObj.isRemote && fight != null) GiantGhastEncounters.get(worldObj).retire(worldObj, getUniqueID());
        super.setDead();
    }

    @Override protected void onDeathUpdate() {
        deathTime++;
        if (worldObj.isRemote) {
            if (deathTime % 2 == 0) burst(3, false);
            return;
        }
        if (deathTime == 12 || deathTime == 30) playSound("mob.ghast.scream", 6, deathTime == 12 ? 0.49F : 0.41F);
        dataWatcher.updateObject(DEATH, (byte)Math.min(deathTime, DEATH_TICKS));
        if (deathTime >= DEATH_TICKS) {
            while (deathXp > 0) {
                int value = EntityXPOrb.getXPSplit(deathXp);
                deathXp -= value;
                worldObj.spawnEntityInWorld(new EntityXPOrb(worldObj, posX, posY + BODY_BASE, posZ, value));
            }
            setDead();
        }
    }

    @SideOnly(Side.CLIENT)
    @Override public void handleHealthUpdate(byte state) {
        if (state == CORE_HIT) burst(12, true);
        else super.handleHealthUpdate(state);
    }

    private void clientEffects() {
        if ((pose() & 7) != 0 && ticksExisted % 3 == 0) {
            Vec3 mouth = muzzle(0.16, Math.toRadians(rotationYaw));
            worldObj.spawnParticle("flame", mouth.xCoord, mouth.yCoord, mouth.zCoord, 0, 0.045, 0);
        }
        if (phase() == 5 && ticksExisted % 5 == 0) burst(1, false);
    }

    private void burst(int count, boolean weak) {
        for (int i = 0; i < count; i++) {
            GiantGhastShape shape = weak ? GiantGhastShape.CORE : GiantGhastShape.HEAD;
            worldObj.spawnParticle(weak ? "reddust" : deathProgress() > 0 && i == 0 ? "largeexplode" : "largesmoke",
                posX + shape.minX + rand.nextDouble() * (shape.maxX - shape.minX),
                posY + (weak ? shape.maxY : shape.minY + rand.nextDouble() * (shape.maxY - shape.minY)),
                posZ + shape.minZ + rand.nextDouble() * (shape.maxZ - shape.minZ), weak ? 1 : 0, 0.01, 0);
        }
    }

    @Override public void writeSpawnData(ByteBuf out) {
        for (Entity part : parts) out.writeInt(part.getEntityId());
    }

    @Override public void readSpawnData(ByteBuf in) {
        for (Entity part : parts) part.setEntityId(in.readInt());
        updateParts();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("Admitted", admitted);
        tag.setInteger("CombatPhase", phase());
        tag.setString("AttackState", state().name());
        tag.setInteger("AttackAge", attackAge);
        tag.setInteger("Recovery", recovery);
        tag.setInteger("SummonWait", summonWait);
        tag.setBoolean("NextSmallFlower", nextSmallFlower);
        tag.setDouble("HoverY", hoverY);
        tag.setFloat("RestYaw", restYaw);
        tag.setInteger("CoreStrikes", coreStrikes);
        tag.setInteger("CoreWait", coreWait);
        GiantGhastEncounters.writeId(tag, "Target", attackTargetId);
        tag.setInteger("DeathXp", deathXp);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        allowDeath = tag.getShort("DeathTime") > 0 || State.DYING.name().equals(tag.getString("AttackState"));
        super.readEntityFromNBT(tag);
        admitted = tag.getBoolean("Admitted");
        int phase = phaseFor(getHealth() / getMaxHealth());
        if (tag.hasKey("AttackState", 8)) phase = Math.max(phase, MathHelper.clamp_int(tag.getInteger("CombatPhase"), 1, 5));
        dataWatcher.updateObject(PHASE, (byte)phase);
        State savedState = State.IDLE;
        if (tag.hasKey("AttackState", 8)) {
            try {
                savedState = State.valueOf(tag.getString("AttackState"));
            } catch (IllegalArgumentException ignored) {
                // Unknown states recover through the health-based scheduler.
            }
        }
        changeState(allowDeath ? State.DYING : savedState);
        attackAge = tag.hasKey("AttackState", 8) ? Math.max(0, tag.getInteger("AttackAge")) : 0;
        recovery = MathHelper.clamp_int(tag.getInteger("Recovery"), 0, 600);
        summonWait = MathHelper.clamp_int(tag.getInteger("SummonWait"), 0, 3600);
        nextSmallFlower = tag.getBoolean("NextSmallFlower");
        if (tag.hasKey("HoverY", 99)) hoverY = tag.getDouble("HoverY");
        restYaw = MathHelper.wrapAngleTo180_float(Math.round(tag.getFloat("RestYaw") / 90) * 90);
        coreStrikes = MathHelper.clamp_int(tag.getInteger("CoreStrikes"), 0, GiantGhastConfig.weakPointStrikes);
        coreWait = MathHelper.clamp_int(tag.getInteger("CoreWait"), 0, CORE_COOLDOWN);
        if (state().targetsEntity()) attackTargetId = GiantGhastEncounters.readId(tag, "Target");
        if (state() == State.EXPOSED) rotationYaw = restYaw;
        deathXp = MathHelper.clamp_int(tag.getInteger("DeathXp"), 0, 100);
        dataWatcher.updateObject(DEATH, (byte)MathHelper.clamp_int(deathTime, 0, DEATH_TICKS));
        updateParts();
    }
}
