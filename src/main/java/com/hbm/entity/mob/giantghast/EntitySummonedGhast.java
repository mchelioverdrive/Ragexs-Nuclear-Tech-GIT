package com.hbm.entity.mob.giantghast;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class EntitySummonedGhast extends EntityGhast {
    private static final int RETALIATING = 20;
    private UUID bossId;
    private boolean bound;
    private EntityLivingBase target;
    private int checkTarget;
    private int cooldown = 25;
    private double orbitAngle = Double.NaN;

    public EntitySummonedGhast(World world) { super(world); }

    EntitySummonedGhast(World world, UUID bossId) {
        this(world);
        this.bossId = bossId;
    }

    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(RETALIATING, (byte)0);
    }

    boolean belongsTo(UUID owner) { return owner != null && owner.equals(bossId); }

    public boolean isRetaliating() { return dataWatcher.getWatchableObjectByte(RETALIATING) != 0; }

    private void retaliate() {
        if (isRetaliating() || !isEntityAlive()) return;
        dataWatcher.updateObject(RETALIATING, (byte)1);
        attackCounter = 0;
        target = null;
        checkTarget = 0;
        cooldown = 12;
        playSound("mob.ghast.scream", 2, 1.25F);
    }

    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public AxisAlignedBB getBoundingBox() { return isEntityAlive() ? boundingBox : null; }

    @Override public void onUpdate() {
        AxisAlignedBB surface = boundingBox.copy();
        double x = posX, y = posY, z = posZ;
        super.onUpdate();
        if (isEntityAlive()) GiantGhastFlight.carryPlayers(this, surface, posX - x, posY - y, posZ - z);
        if (worldObj.isRemote && isRetaliating() && ticksExisted % 8 == 0 && isEntityAlive()) {
            worldObj.spawnParticle("angryVillager", posX + rand.nextDouble() - 0.5, posY + height + 0.25, posZ, 0, 0.04, 0);
        }
    }

    @Override protected void updateEntityActionState() {
        GiantGhastEncounters encounters = GiantGhastEncounters.get(worldObj);
        if (!bound) {
            bound = encounters.bindChild(bossId, this, 0);
            if (!bound) {
                super.setDead();
                return;
            }
        }
        if (encounters.find(bossId) == null) {
            setDead();
            return;
        }

        EntityGiantGhast boss = encounters.boss(worldObj, bossId);
        if (boss == null) {
            motionX = motionY = motionZ = 0;
            attackCounter = 0;
            dataWatcher.updateObject(16, (byte)0);
            return;
        }

        if (Double.isNaN(orbitAngle)) orbitAngle = Math.atan2(posZ - boss.posZ, posX - boss.posX);
        double radius = isRetaliating() ? 18 : 16 + (getUniqueID().getLeastSignificantBits() & 7);
        orbitAngle += isRetaliating() ? 0.006 : 0.009;
        double y = Math.min(worldObj.getActualHeight() - 5, boss.posY + 5.5 + (getUniqueID().getMostSignificantBits() & 7));
        double dx = boss.posX + Math.cos(orbitAngle) * radius - posX;
        double dy = y - posY;
        double dz = boss.posZ + Math.sin(orbitAngle) * radius - posZ;
        double distance = Math.max(5, Math.sqrt(dx * dx + dy * dy + dz * dz));
        motionX = GiantGhastFlight.approach(motionX, dx / distance * 0.30, 0.04);
        motionY = GiantGhastFlight.approach(motionY, dy / distance * 0.25, 0.035);
        motionZ = GiantGhastFlight.approach(motionZ, dz / distance * 0.30, 0.04);

        if (isRetaliating()) target = boss;
        else if (checkTarget-- <= 0) {
            target = boss.playerTarget();
            checkTarget = 12;
        }
        if (cooldown > 0) cooldown--;
        prevAttackCounter = attackCounter;
        if (boss.canSummonsFire() && GiantGhastFlight.combatant(target, worldObj) && getDistanceSqToEntity(target) < 64 * 64) {
            Vec3 center = GiantGhastFlight.center(this);
            Vec3 aim = GiantGhastFlight.center(target);
            Vec3 heading = Vec3.createVectorHelper(aim.xCoord - center.xCoord, aim.yCoord - center.yCoord, aim.zCoord - center.zCoord);
            rotationYaw = (float)Math.toDegrees(Math.atan2(-heading.xCoord, heading.zCoord));
            Vec3 direction = heading.normalize();
            Vec3 mouth = center.addVector(direction.xCoord * 2.9, direction.yCoord * 2.9, direction.zCoord * 2.9);
            if (cooldown == 0 && GiantGhastFlight.visible(worldObj, center, aim)
                    && GiantGhastFlight.visible(worldObj, mouth, aim)) {
                if (++attackCounter == 10) playSound("mob.ghast.charge", 2, 1);
                if (attackCounter >= 20) {
                    boss.fire(this, mouth, heading, 0.9F, isRetaliating() ? 1.45F : 0.82F, true, false);
                    attackCounter = 0;
                    cooldown = 42 + rand.nextInt(25);
                }
            } else {
                attackCounter = Math.max(0, attackCounter - 2);
            }
        } else {
            attackCounter = 0;
        }
        renderYawOffset = rotationYaw;
        dataWatcher.updateObject(16, (byte)(attackCounter > 10 ? 1 : 0));
    }

    @Override public void moveEntityWithHeading(float strafe, float forward) {
        if (worldObj.isRemote || !isEntityAlive()) return;
        if (GiantGhastFlight.clear(worldObj, boundingBox.addCoord(motionX, motionY, motionZ))) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        } else if (GiantGhastFlight.clear(worldObj, boundingBox.addCoord(0, 0.19, 0))) {
            setPosition(posX, posY + 0.19, posZ);
        } else {
            orbitAngle += 0.04;
        }
    }

    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if (worldObj.isRemote) return false;
        EntityGiantGhastFireball fireball = source.getSourceOfDamage() instanceof EntityGiantGhastFireball
            ? (EntityGiantGhastFireball)source.getSourceOfDamage() : null;
        boolean friendlyFire = fireball != null && fireball.isStrayFrom(bossId) && source.getEntity() instanceof EntityGiantGhast;
        float previousHealth = getHealth();
        boolean accepted = super.attackEntityFrom(source, friendlyFire ? Math.min(3.0F, damage) : damage);
        if (friendlyFire && accepted && getHealth() < previousHealth) {
            retaliate();
            GiantGhastEncounters encounters = GiantGhastEncounters.get(worldObj);
            GiantGhastEncounters.Fight fight = encounters.find(bossId);
            if (fight != null) {
                for (EntitySummonedGhast ally : encounters.summons(worldObj, fight)) {
                    if (ally != this && getDistanceSqToEntity(ally) <= 14 * 14
                            && GiantGhastFlight.visible(worldObj, GiantGhastFlight.center(this), GiantGhastFlight.center(ally))) ally.retaliate();
                }
            }
        }
        return accepted;
    }

    @Override public void setDead() {
        if (!worldObj.isRemote && bossId != null) GiantGhastEncounters.get(worldObj).forget(bossId, this);
        super.setDead();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        GiantGhastEncounters.writeId(tag, "Boss", bossId);
        tag.setBoolean("Retaliating", isRetaliating());
        tag.setInteger("Charge", attackCounter);
        tag.setInteger("Cooldown", cooldown);
        tag.setDouble("Orbit", orbitAngle);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        bossId = GiantGhastEncounters.readId(tag, "Boss");
        dataWatcher.updateObject(RETALIATING, (byte)(tag.getBoolean("Retaliating") ? 1 : 0));
        attackCounter = MathHelper.clamp_int(tag.getInteger("Charge"), 0, 19);
        cooldown = MathHelper.clamp_int(tag.getInteger("Cooldown"), 0, 120);
        if (tag.hasKey("Orbit", 99)) orbitAngle = tag.getDouble("Orbit");
    }
}
