package com.hbm.entity.mob.giantghast;

import java.util.UUID;

import com.hbm.config.GiantGhastConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.AchievementList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

public final class EntityGiantGhast extends EntityFlying implements IBossDisplayData, IEntityMultiPart, IMob {
    public static final float WIDTH = 12, ROOF = 11, BODY_BASE = 4, CORE_WIDTH = 2.5F, CORE_RISE = 0.65F;
    public static final float SIDE_FACE_Z = 2.8F;
    public static final int DEATH_TICKS = 58;
    private static final int PHASE = 20, POSE = 21, DEATH = 22;
    private static final byte CORE_HIT = 61;
    private static final float BODY_DAMAGE = 0.28F;

    private enum Attack { NONE, STANDARD, RAPID, VOLLEY, CURVE, BARRAGE, SUMMON }
    private static final Attack[][] PATTERNS = {
        { Attack.STANDARD, Attack.VOLLEY, Attack.STANDARD },
        { Attack.CURVE, Attack.RAPID, Attack.VOLLEY, Attack.STANDARD, Attack.CURVE },
        { Attack.BARRAGE, Attack.VOLLEY, Attack.RAPID, Attack.CURVE, Attack.BARRAGE, Attack.VOLLEY }
    };
    private static final int[] RAPID_FACES = { 0, 2, 1, 0, 2, 1 };

    private final EntityDragonPart core = hitPart("core", CORE_WIDTH, CORE_RISE + 0.10F);
    private final Entity[] parts = {
        core,
        hitPart("body", WIDTH / 2, ROOF / 2), hitPart("body", WIDTH / 2, ROOF / 2),
        hitPart("body", WIDTH / 2, ROOF / 2), hitPart("body", WIDTH / 2, ROOF / 2),
        hitPart("body", WIDTH / 2, ROOF / 2), hitPart("body", WIDTH / 2, ROOF / 2),
        hitPart("body", WIDTH / 2, ROOF / 2), hitPart("body", WIDTH / 2, ROOF / 2)
    };

    private EntityPlayer playerTarget;
    private EntityLivingBase attackTarget;
    private UUID attackTargetId;
    private GiantGhastEncounters.Fight fight;
    private boolean admitted;
    private Attack attack = Attack.NONE;
    private int attackAge, recovery = 40, summonWait, patternIndex, targetCheck, facing, flash, faceMask, opening;
    private boolean scream;
    private int deathXp;
    private double hoverY = Double.NaN;

    public EntityGiantGhast(World world) {
        super(world);
        setSize(WIDTH, ROOF);
        isImmuneToFire = true;
        experienceValue = 100;
        scoreValue = 100;
        setHealth(getMaxHealth());
    }

    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(PHASE, (byte)1);
        dataWatcher.addObject(POSE, 0);
        dataWatcher.addObject(DEATH, (byte)0);
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(GiantGhastConfig.health);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(GiantGhastConfig.range);
    }

    @Override public World func_82194_d() { return worldObj; }
    @Override public Entity[] getParts() { return parts; }
    @Override public float getEyeHeight() { return 8.1F; }
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
    public int pose() { return dataWatcher.getWatchableObjectInt(POSE); }
    public int deathProgress() { return Math.max(deathTime, dataWatcher.getWatchableObjectByte(DEATH)); }
    boolean wasAdmitted() { return admitted; }

    @Override public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
        if (parts == null) return;
        core.setPosition(x, y + ROOF - 0.10, z);
        for (int i = 0; i < parts.length - 1; i++) {
            Entity part = parts[i + 1];
            double dx = (i & 1) == 0 ? -WIDTH / 4 : WIDTH / 4;
            double dz = (i & 2) == 0 ? -WIDTH / 4 : WIDTH / 4;
            double center = y + ((i & 4) == 0 ? ROOF / 4 : ROOF * 0.75);
            part.setPosition(x + dx, center, z + dz);
            part.boundingBox.offset(0, -ROOF / 4, 0);
        }
    }

    private EntityDragonPart hitPart(String name, float width, float height) {
        return new EntityDragonPart(this, name, width, height) {
            @Override public boolean canBeCollidedWith() { return EntityGiantGhast.this.isEntityAlive(); }
        };
    }

    @Override public AxisAlignedBB getBoundingBox() {
        return isEntityAlive() ? AxisAlignedBB.getBoundingBox(boundingBox.minX, boundingBox.minY + BODY_BASE,
            boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ) : null;
    }

    public boolean hasSpawnClearance() {
        return GiantGhastFlight.clear(worldObj, boundingBox.copy().addCoord(0, CORE_RISE, 0));
    }

    @Override public boolean getCanSpawnHere() {
        return GiantGhastConfig.natural && worldObj.provider.dimensionId == -1 && worldObj.difficultySetting != EnumDifficulty.PEACEFUL
            && rand.nextInt(20) == 0 && GiantGhastEncounters.get(worldObj).isEmpty() && hasSpawnClearance()
            && super.getCanSpawnHere();
    }

    @Override public int getMaxSpawnedInChunk() { return 1; }

    @Override public void onUpdate() {
        AxisAlignedBB surface = getBoundingBox();
        double x = posX, y = posY, z = posZ;
        super.onUpdate();
        if (surface != null && isEntityAlive()) GiantGhastFlight.carryPlayers(this, surface, posX - x, posY - y, posZ - z);
        if (worldObj.isRemote && isEntityAlive()) clientEffects();
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
                // A removed encounter or a duplicate saved boss must not retire the live original.
                super.setDead();
                return;
            }
            admitted = true;
        }

        int previousPhase = phase();
        float fraction = getHealth() / getMaxHealth();
        int phase = Math.max(previousPhase, fraction <= GiantGhastConfig.enrageThreshold ? 3
            : fraction <= GiantGhastConfig.summonThreshold ? 2 : 1);
        if (phase != previousPhase) {
            dataWatcher.updateObject(PHASE, (byte)phase);
            summonWait = 0;
            recovery = Math.min(recovery, 16);
            patternIndex = 0;
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

        EntityLivingBase movementTarget = GiantGhastFlight.combatant(attackTarget, worldObj) ? attackTarget : playerTarget;
        fly(movementTarget, phase);
        if (summonWait > 0) summonWait--;
        if (recovery > 0) recovery--;
        if (flash > 0) flash--;
        faceMask = opening = 0;
        scream = false;

        if (attack == Attack.NONE && recovery == 0) {
            EntitySummonedGhast rebel = phase >= 3 ? findSummon(encounters, true) : null;
            EntityLivingBase chosen = rebel != null && (playerTarget == null || (patternIndex & 1) == 0) ? rebel : playerTarget;
            if (canTarget(chosen)) {
                if (phase >= 2 && summonWait == 0 && encounters.count(worldObj, fight, false) < GiantGhastConfig.maxSummons) {
                    start(Attack.SUMMON, chosen);
                    summonWait = GiantGhastConfig.summonCooldown;
                } else {
                    Attack[] pattern = PATTERNS[phase - 1];
                    Attack next = pattern[patternIndex % pattern.length];
                    patternIndex = (patternIndex + 1) % pattern.length;
                    start(next, chosen);
                }
            }
        }

        if (attack != Attack.NONE) tickAttack(encounters, phase);
        dataWatcher.updateObject(POSE, faceMask | (scream ? 8 : 0) | (flash > 0 ? 16 : 0) | (facing << 5) | (opening << 8));
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

    private void fly(EntityLivingBase target, int phase) {
        if (Double.isNaN(hoverY)) hoverY = Math.min(worldObj.getActualHeight() - ROOF - 2, posY + 3);
        double speed = phase == 3 ? 0.28 : phase == 2 ? 0.20 : 0.15;
        double vx = 0, vz = 0, altitude = hoverY;
        if (GiantGhastFlight.combatant(target, worldObj)) {
            double dx = target.posX - posX, dz = target.posZ - posZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 6.5) {
                float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                if (Math.abs(MathHelper.wrapAngleTo180_float(yaw - facing * 90)) > 50) {
                    facing = MathHelper.floor_double(yaw / 90 + 0.5) & 3;
                }
                double approach = MathHelper.clamp_double((horizontal - 25) / 12, -0.65, 1);
                double orbit = (getEntityId() % 2 == 0 ? 1 : -1) * (Math.abs(approach) < 0.8 ? 0.4 : 0.15);
                vx = (dx * approach - dz * orbit) * speed / horizontal;
                vz = (dz * approach + dx * orbit) * speed / horizontal;
                altitude = Math.min(hoverY + 6, target.boundingBox.minY + (target instanceof EntitySummonedGhast ? -2 : 7));
            } else if (Math.abs(target.boundingBox.minY - (posY + ROOF)) < 1.8) {
                // Let a player above the body stay aboard and work the core.
                altitude = posY;
            } else {
                // A target directly underneath the shell is outside every mouth's firing arc.
                vx = Math.sin(facing * Math.PI / 2) * speed;
                vz = -Math.cos(facing * Math.PI / 2) * speed;
                altitude = Math.min(hoverY + 6, target.boundingBox.minY + 7);
            }
        } else {
            double drift = ticksExisted * 0.022 + getEntityId();
            vx = Math.cos(drift) * speed * 0.45;
            vz = Math.sin(drift) * speed * 0.45;
        }
        rotationYaw = facing * 90;
        double wantedY = altitude + Math.sin(ticksExisted * 0.085) * 0.85;
        wantedY = MathHelper.clamp_double(wantedY, 2, worldObj.getActualHeight() - ROOF - 1);
        motionX = GiantGhastFlight.approach(motionX, vx, 0.025);
        motionY = GiantGhastFlight.approach(motionY, MathHelper.clamp_double((wantedY - posY) * 0.08, -speed * 0.75, speed), 0.022);
        motionZ = GiantGhastFlight.approach(motionZ, vz, 0.025);
    }

    @Override public void moveEntityWithHeading(float strafe, float forward) {
        if (worldObj.isRemote || !isEntityAlive()) return;
        AxisAlignedBB envelope = boundingBox.copy().addCoord(0, CORE_RISE, 0);
        if (GiantGhastFlight.clear(worldObj, envelope.addCoord(motionX, motionY, motionZ))) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }

        boolean wantedHorizontal = Math.abs(motionX) + Math.abs(motionZ) > 0.035;
        if (motionX != 0 && GiantGhastFlight.clear(worldObj, envelope.addCoord(motionX, 0, 0))) envelope.offset(motionX, 0, 0);
        else motionX = 0;
        if (motionZ != 0 && GiantGhastFlight.clear(worldObj, envelope.addCoord(0, 0, motionZ))) envelope.offset(0, 0, motionZ);
        else motionZ = 0;
        if (wantedHorizontal && motionX == 0 && motionZ == 0) {
            sidestep(envelope);
            return;
        }
        if (motionY != 0 && !GiantGhastFlight.clear(worldObj, envelope.addCoord(0, motionY, 0))) motionY = 0;
        if (motionX != 0 || motionY != 0 || motionZ != 0) setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        else sidestep(envelope);
    }

    private void sidestep(AxisAlignedBB envelope) {
        motionX = motionY = motionZ = 0;
        if (GiantGhastFlight.clear(worldObj, envelope.addCoord(0, 0.19, 0))) {
            setPosition(posX, posY + 0.19, posZ);
            return;
        }
        double offset = (ticksExisted / 40 & 1) == 0 ? 0.17 : -0.17;
        double dx = Math.cos(facing * Math.PI / 2) * offset, dz = Math.sin(facing * Math.PI / 2) * offset;
        if (GiantGhastFlight.clear(worldObj, envelope.addCoord(dx, 0, dz))) {
            setPosition(posX + dx, posY, posZ + dz);
        } else if (ticksExisted % 20 == 0 && !GiantGhastFlight.clear(worldObj, envelope)) {
            // Recover a command-summoned or subsequently obstructed boss into free space.
            for (int dy = 1; dy <= 16; dy++) {
                if (GiantGhastFlight.clear(worldObj, envelope.getOffsetBoundingBox(0, dy, 0))) {
                    setPosition(posX, posY + dy, posZ);
                    return;
                }
            }
        }
    }

    private void start(Attack next, EntityLivingBase target) {
        attack = next;
        attackAge = 0;
        attackTarget = target;
        attackTargetId = target.getUniqueID();
        if (next == Attack.SUMMON || next == Attack.BARRAGE) playSound("mob.ghast.scream", 7, next == Attack.SUMMON ? 0.55F : 0.65F);
        else playSound("mob.ghast.charge", 4, 0.62F);
    }

    private void tickAttack(GiantGhastEncounters encounters, int phase) {
        if (attack != Attack.SUMMON && !GiantGhastFlight.combatant(attackTarget, worldObj)) {
            finishAttack(phase);
            return;
        }

        attackAge++;
        switch (attack) {
            case SUMMON:
                animate(7, attackAge, 12, true);
                if (attackAge >= 32) {
                    summonWave(encounters, phase == 3 ? 7 : 4);
                    finishAttack(phase);
                }
                break;
            case STANDARD:
            case CURVE:
                int face = attack == Attack.STANDARD ? 1 : patternIndex == 1 ? 0 : 2;
                animate(1 << face, attackAge, 22, false);
                if (attackAge == 22) fireFromFace(face, 0, 0, 1.25F, 1, attack == Attack.CURVE);
                if (attackAge >= 27) finishAttack(phase);
                break;
            case RAPID:
                int shot = Math.max(0, (attackAge - 13) / 5);
                int activeFace = RAPID_FACES[Math.min(shot, RAPID_FACES.length - 1)];
                animate(1 << activeFace, attackAge <= 18 ? attackAge : 13 + (attackAge - 13) % 5, 16, false);
                if (attackAge >= 17 && (attackAge - 17) % 5 == 0 && shot < RAPID_FACES.length) {
                    fireFromFace(activeFace, (rand.nextDouble() - 0.5) * 0.16, (rand.nextDouble() - 0.5) * 0.09, 0.67F, 0.66F, false);
                }
                if (attackAge >= 46) finishAttack(phase);
                break;
            case VOLLEY:
                animate(7, attackAge, 25, false);
                if (attackAge == 25) {
                    int count = phase == 1 ? 3 : 5;
                    for (int i = 0; i < count; i++) {
                        double offset = (i - (count - 1) * 0.5) * 0.22;
                        fireFromFace(Math.min(2, i * 3 / count), offset, (i % 2) * 0.06, 1.05F, 0.82F, false);
                    }
                }
                if (attackAge >= 31) finishAttack(phase);
                break;
            case BARRAGE:
                animate(7, attackAge, 13, true);
                if (attackAge >= 16 && attackAge <= 44 && (attackAge - 16) % 4 == 0) {
                    if (attackAge == 20 || attackAge == 36) {
                        fireFromFace((attackAge / 4) % 3, rand.nextDouble() * 0.48 - 0.24, 0, 0.9F, 0.88F, false);
                    } else {
                        strayShot(encounters);
                    }
                }
                if (attackAge >= 50) finishAttack(phase);
                break;
            default:
                break;
        }
    }

    private void animate(int mask, int age, int charge, boolean shriek) {
        faceMask = mask;
        opening = Math.min(15, age * 15 / charge);
        scream = shriek;
    }

    private void finishAttack(int phase) {
        attack = Attack.NONE;
        attackTarget = null;
        attackTargetId = null;
        attackAge = 0;
        recovery = Math.max(10, GiantGhastConfig.attackCooldown * (5 - phase) / 4);
    }

    public static double mouthY(int face) { return face == 1 ? 6.66 : 7.23; }

    private void fireFromFace(int face, double spread, double lift, float power, float damage, boolean curve) {
        if (!canTarget(attackTarget)) return;
        double yaw = facing * Math.PI / 2;
        Vec3 muzzle = mouthPosition(face, yaw, 0.48);
        Vec3 aim = GiantGhastFlight.center(attackTarget);
        if (!GiantGhastFlight.visible(worldObj, muzzle, aim)) return;
        double dx = aim.xCoord - muzzle.xCoord, dz = aim.zCoord - muzzle.zCoord;
        double c = Math.cos(spread), s = Math.sin(spread);
        double x = dx * c + dz * s, z = dz * c - dx * s;
        double length = Math.hypot(x, z);
        double nx = face == 1 ? -Math.sin(yaw) : (face - 1) * Math.cos(yaw);
        double nz = face == 1 ? Math.cos(yaw) : (face - 1) * Math.sin(yaw);
        double outward = x * nx + z * nz;
        if (face == 1 && outward < 0.2) return;
        // Flank mouths send wide shots along the shell instead of firing through it.
        double correction = Math.max(0, 0.09 * length - outward);
        Vec3 heading = Vec3.createVectorHelper(x + nx * correction, aim.yCoord - muzzle.yCoord + lift * length, z + nz * correction);
        fire(this, muzzle, heading, power, damage, curve ? attackTarget : null, attack == Attack.BARRAGE);
    }

    private Vec3 mouthPosition(int face, double yaw, double clearance) {
        double edge = WIDTH / 2 + clearance;
        double x = (face - 1) * edge;
        double z = face == 1 ? edge : SIDE_FACE_Z;
        return Vec3.createVectorHelper(posX + x * Math.cos(yaw) - z * Math.sin(yaw),
            posY + mouthY(face), posZ + x * Math.sin(yaw) + z * Math.cos(yaw));
    }

    private void strayShot(GiantGhastEncounters encounters) {
        int shot = (attackAge - 16) / 4;
        EntitySummonedGhast bystander = shot % 3 == 0 ? findSummon(encounters, false) : null;
        double angle = shot * 2.39996 + patternIndex;
        Vec3 aim = Vec3.createVectorHelper(posX + Math.sin(angle) * 24, posY + rand.nextDouble() * 14,
            posZ + Math.cos(angle) * 24);
        if (bystander != null) {
            // Some stray lanes cross the summon ring. Damage still requires a real swept impact.
            double lead = Math.min(18, Math.sqrt(getDistanceSqToEntity(bystander)) * 0.7);
            aim = GiantGhastFlight.center(bystander).addVector(bystander.motionX * lead + rand.nextDouble() - 0.5,
                bystander.motionY * lead - 0.45, bystander.motionZ * lead + rand.nextDouble() - 0.5);
        }
        facing = MathHelper.floor_double(Math.atan2(posX - aim.xCoord, aim.zCoord - posZ) / (Math.PI / 2) + 0.5) & 3;
        rotationYaw = facing * 90;
        Vec3 muzzle = mouthPosition(1, facing * Math.PI / 2, 0.48);
        if (!GiantGhastFlight.visible(worldObj, muzzle, aim)) return;
        fire(this, muzzle, Vec3.createVectorHelper(aim.xCoord - muzzle.xCoord, aim.yCoord - muzzle.yCoord,
            aim.zCoord - muzzle.zCoord), 0.87F, 0.9F, null, true);
    }

    boolean fire(EntityLivingBase shooter, Vec3 origin, Vec3 heading, float power, float damage, EntityLivingBase steer, boolean stray) {
        if (heading.lengthVector() < 0.1) return false;
        EntityGiantGhastFireball shot = new EntityGiantGhastFireball(this, shooter, origin, heading, power, damage, steer, stray);
        if (!GiantGhastFlight.clear(worldObj, shot.boundingBox)
                || !GiantGhastEncounters.get(worldObj).spawnChild(this, shot, shot.expires())) return false;
        if (shooter != this || ticksExisted % 2 == 0 || attack == Attack.STANDARD || attack == Attack.CURVE) {
            worldObj.playSoundEffect(origin.xCoord, origin.yCoord, origin.zCoord, "mob.ghast.fireball",
                shooter == this ? 3.3F : 1.1F, shooter == this ? 0.65F + rand.nextFloat() * 0.2F : 1);
        }
        return true;
    }

    private EntitySummonedGhast findSummon(GiantGhastEncounters encounters, boolean rebel) {
        EntitySummonedGhast best = null;
        double distance = 38 * 38;
        for (EntitySummonedGhast summon : encounters.summons(worldObj, fight)) {
            double d = getDistanceSqToEntity(summon);
            if (summon.isRetaliating() == rebel && d < distance && d > 81 && canTarget(summon)) {
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

    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        return receiveHit(source, amount, false);
    }

    @Override public boolean attackEntityFromPart(EntityDragonPart part, DamageSource source, float amount) {
        if (part == core && source.getEntity() instanceof EntityPlayer && source.getSourceOfDamage() == source.getEntity()
                && !aimedAtCore((EntityPlayer)source.getEntity())) return false;
        return receiveHit(source, amount, part == core);
    }

    private boolean aimedAtCore(EntityPlayer player) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        double reach = player.capabilities.isCreativeMode ? 6 : 3.35;
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = core.boundingBox.expand(0.20, 0.12, 0.20).calculateIntercept(eye, end);
        if (hit == null || !GiantGhastFlight.visible(worldObj, eye, hit.hitVec)) return false;
        MovingObjectPosition body = boundingBox.calculateIntercept(eye, end);
        return body == null || eye.squareDistanceTo(hit.hitVec) <= eye.squareDistanceTo(body.hitVec);
    }

    private boolean receiveHit(DamageSource source, float amount, boolean coreHit) {
        if (worldObj.isRemote || !isEntityAlive() || source == DamageSource.inWall || source.getEntity() == this) return false;
        boolean reflected = "fireball".equals(source.getDamageType()) && source.getEntity() instanceof EntityPlayer;
        if (source.isFireDamage() && !reflected) return false;
        boolean rebel = source.getEntity() instanceof EntitySummonedGhast
            && ((EntitySummonedGhast)source.getEntity()).isRetaliating()
            && ((EntitySummonedGhast)source.getEntity()).belongsTo(getUniqueID());
        boolean weak = coreHit && !source.isExplosion() && source.getSourceOfDamage() != null;
        float damage = source == DamageSource.outOfWorld ? amount : reflected ? Math.max(getMaxHealth() * 0.12F, amount)
            : rebel ? amount : amount * BODY_DAMAGE;
        if (weak && !reflected && !rebel) damage = amount * GiantGhastConfig.weakMultiplier * (phase() == 3 ? 1.35F : 1);
        float before = getHealth();
        if (!super.attackEntityFrom(source, damage)) return false;
        boolean critical = isEntityAlive() && before > getMaxHealth() * 0.1F && getHealth() <= getMaxHealth() * 0.1F;
        if (weak || critical) {
            flash = critical ? 24 : 14;
            worldObj.setEntityState(this, CORE_HIT);
            if (isEntityAlive()) playSound("mob.ghast.scream", critical ? 7 : 5, critical ? 0.46F : phase() == 3 ? 0.83F : 0.67F);
        }
        if (reflected && getHealth() <= 0) ((EntityPlayer)source.getEntity()).triggerAchievement(AchievementList.ghast);
        return true;
    }

    @Override protected void dropFewItems(boolean playerKill, int looting) {
        dropItem(Items.ghast_tear, 8 + rand.nextInt(5) + looting);
        dropItem(Items.gunpowder, 6 + rand.nextInt(6) + looting);
    }

    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!worldObj.isRemote && dead) {
            if (recentlyHit > 0 && worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) deathXp = getExperiencePoints(attackingPlayer);
            finishAttack(phase());
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
        if (deathTime == 1 || deathTime % 5 == 0) dataWatcher.updateObject(DEATH, (byte)deathTime);
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
        if (state == CORE_HIT) burst(15, true);
        else super.handleHealthUpdate(state);
    }

    private void clientEffects() {
        int pose = pose();
        if ((pose & 7) != 0 && ticksExisted % 3 == 0) {
            int face = Integer.numberOfTrailingZeros(pose & 7);
            Vec3 mouth = mouthPosition(face, ((pose >> 5) & 3) * Math.PI / 2, 0.16);
            worldObj.spawnParticle("flame", mouth.xCoord, mouth.yCoord, mouth.zCoord, 0, 0.045, 0);
        }
        if (phase() == 3 && ticksExisted % 6 == 0) burst(1, true);
    }

    private void burst(int count, boolean weak) {
        for (int i = 0; i < count; i++) {
            double width = weak ? CORE_WIDTH : WIDTH;
            worldObj.spawnParticle(weak ? "reddust" : i % 3 == 0 ? "largeexplode" : "largesmoke",
                posX + (rand.nextDouble() - 0.5) * width,
                posY + (weak ? ROOF + CORE_RISE : BODY_BASE + rand.nextDouble() * (ROOF - BODY_BASE)),
                posZ + (rand.nextDouble() - 0.5) * width, weak ? 1 : 0, 0.01, 0);
        }
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("Admitted", admitted);
        tag.setInteger("CombatPhase", phase());
        tag.setInteger("Attack", attack.ordinal());
        tag.setInteger("AttackAge", attackAge);
        tag.setInteger("Recovery", recovery);
        tag.setInteger("SummonWait", summonWait);
        tag.setInteger("Pattern", patternIndex);
        tag.setInteger("Facing", facing);
        tag.setDouble("HoverY", hoverY);
        GiantGhastEncounters.writeId(tag, "Target", attackTargetId);
        tag.setInteger("DeathXp", deathXp);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        admitted = tag.getBoolean("Admitted");
        dataWatcher.updateObject(PHASE, (byte)MathHelper.clamp_int(tag.getInteger("CombatPhase"), 1, 3));
        attack = Attack.values()[MathHelper.clamp_int(tag.getInteger("Attack"), 0, Attack.values().length - 1)];
        attackAge = MathHelper.clamp_int(tag.getInteger("AttackAge"), 0, 50);
        recovery = MathHelper.clamp_int(tag.getInteger("Recovery"), 0, 600);
        summonWait = MathHelper.clamp_int(tag.getInteger("SummonWait"), 0, 3600);
        patternIndex = Math.max(0, tag.getInteger("Pattern")) % PATTERNS[phase() - 1].length;
        facing = tag.getInteger("Facing") & 3;
        dataWatcher.updateObject(POSE, facing << 5);
        if (tag.hasKey("HoverY", 99)) hoverY = tag.getDouble("HoverY");
        attackTargetId = GiantGhastEncounters.readId(tag, "Target");
        deathXp = MathHelper.clamp_int(tag.getInteger("DeathXp"), 0, 100);
        dataWatcher.updateObject(DEATH, (byte)MathHelper.clamp_int(deathTime, 0, DEATH_TICKS));
    }
}
