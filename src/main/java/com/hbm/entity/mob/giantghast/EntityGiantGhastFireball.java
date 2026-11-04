package com.hbm.entity.mob.giantghast;

import java.util.UUID;

import com.hbm.config.GiantGhastConfig;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public final class EntityGiantGhastFireball extends EntityFireball implements IEntityAdditionalSpawnData {
    private static final int LIFETIME = 180;
    private static final int REFLECTED = 20;
    private UUID bossId, launcherId, shooterId;
    private long expiry;
    private boolean bound, reflectable = true, reflected, stray;
    private float power = 1, damageScale = 1;

    public EntityGiantGhastFireball(World world) { super(world); }

    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(REFLECTED, (byte)0);
    }

    EntityGiantGhastFireball(EntityGiantGhast boss, EntityLivingBase shooter, Vec3 origin, Vec3 heading,
            float power, float damageScale, boolean reflectable, boolean stray) {
        this(boss.worldObj);
        bossId = boss.getUniqueID();
        shooterId = launcherId = shooter.getUniqueID();
        shootingEntity = shooter;
        expiry = worldObj.getTotalWorldTime() + LIFETIME;
        this.power = power;
        this.damageScale = damageScale;
        this.reflectable = reflectable;
        this.stray = stray;
        setSizeForPower();
        setPosition(origin.xCoord, origin.yCoord, origin.zCoord);
        Vec3 direction = heading.normalize();
        motionX = direction.xCoord * 0.46;
        motionY = direction.yCoord * 0.46;
        motionZ = direction.zCoord * 0.46;
    }

    private void setSizeForPower() {
        float size = power < 0.75F ? 0.54F : 0.84F;
        setSize(size, size);
    }

    public float visualSize() { return power < 0.75F ? 0.63F : 1.3F; }
    public boolean isReflectable() { return reflectable; }
    boolean wasReflected() { return reflected; }
    long expires() { return expiry; }
    boolean isStrayFrom(UUID id) { return stray && !reflected && id != null && id.equals(bossId) && id.equals(launcherId); }

    boolean isEncounterMember(Entity entity) {
        if (bossId == null) return false;
        if (entity instanceof EntityDragonPart && ((EntityDragonPart)entity).entityDragonObj instanceof EntityGiantGhast) {
            entity = (EntityGiantGhast)((EntityDragonPart)entity).entityDragonObj;
        }
        return entity instanceof EntityGiantGhast && bossId.equals(entity.getUniqueID())
            || entity instanceof EntitySummonedGhast && ((EntitySummonedGhast)entity).belongsTo(bossId)
            || entity instanceof EntityGiantGhastFireball && bossId.equals(((EntityGiantGhastFireball)entity).bossId);
    }

    @Override public void onUpdate() {
        onEntityUpdate();
        if (!worldObj.isRemote) {
            GiantGhastEncounters encounters = GiantGhastEncounters.get(worldObj);
            if (!bound) {
                bound = encounters.bindChild(bossId, this, expiry);
                if (!bound) {
                    super.setDead();
                    return;
                }
            }
            if (expiry <= worldObj.getTotalWorldTime() || encounters.find(bossId) == null
                    || worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
                setDead();
                return;
            }
            // A projectile saved in an adjacent chunk waits for its encounter to load.
            if (encounters.boss(worldObj, bossId) == null) return;
            if ((shootingEntity == null || shootingEntity.isDead) && (ticksExisted == 1 || ticksExisted % 12 == 0)) {
                shootingEntity = resolve(encounters, shooterId);
            }
        }

        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (speed > 0.001) {
            boolean redirected = worldObj.isRemote ? dataWatcher.getWatchableObjectByte(REFLECTED) != 0 : reflected;
            double cap = redirected ? 1.10 : power < 0.75F ? 1.02 : 0.90;
            double factor = Math.min(speed + 0.022, cap) / speed;
            motionX *= factor;
            motionY *= factor;
            motionZ *= factor;
        }
        Vec3 start = Vec3.createVectorHelper(posX, posY, posZ);
        Vec3 end = start.addVector(motionX, motionY, motionZ);
        if (!worldObj.isRemote) {
            if (end.yCoord < 0 || end.yCoord >= worldObj.getActualHeight()
                    || !GiantGhastFlight.loaded(worldObj, boundingBox.addCoord(motionX, motionY, motionZ))) {
                setDead();
                return;
            }
            MovingObjectPosition impact = traceImpact(start, end);
            if (impact != null) {
                setPosition(impact.hitVec.xCoord, impact.hitVec.yCoord, impact.hitVec.zCoord);
                onImpact(impact);
                return;
            }
        }
        setPosition(end.xCoord, end.yCoord, end.zCoord);
        if (worldObj.isRemote && ticksExisted % 2 == 0) {
            worldObj.spawnParticle("smoke", posX, posY + height * 0.5, posZ, 0, 0, 0);
            if (reflectable) worldObj.spawnParticle("reddust", posX, posY + height * 0.5, posZ, 0.2, 0.75, 1);
            else if (power >= 0.75F) worldObj.spawnParticle("flame", posX, posY + height * 0.5, posZ, 0, 0.02, 0);
        }
    }

    private EntityLivingBase resolve(GiantGhastEncounters encounters, UUID id) {
        Entity entity = encounters.member(worldObj, bossId, id);
        if (entity == null) entity = GiantGhastEncounters.player(worldObj, id);
        return entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
    }

    private MovingObjectPosition traceImpact(Vec3 start, Vec3 end) {
        MovingObjectPosition impact = GiantGhastFlight.traceBlocks(worldObj, start, end);
        Vec3 limit = impact == null ? end : impact.hitVec;
        double nearest = start.squareDistanceTo(limit) + (impact == null ? 0.00001 : 0);
        AxisAlignedBB sweep = boundingBox.addCoord(motionX, motionY, motionZ).expand(1, 1, 1);
        for (Object object : worldObj.getEntitiesWithinAABBExcludingEntity(this, sweep)) {
            Entity entity = (Entity)object;
            if (!canHit(entity)) continue;
            MovingObjectPosition contact;
            if (entity instanceof EntityDragonPart && ((EntityDragonPart)entity).entityDragonObj instanceof EntityGiantGhast) {
                contact = ((EntityGiantGhast)((EntityDragonPart)entity).entityDragonObj).tracePart(entity, start, limit);
            } else {
                double margin = entity instanceof EntityDragonPart ? 0 : 0.16;
                contact = entity.boundingBox.expand(margin, margin, margin).calculateIntercept(start, limit);
            }
            if (contact == null) continue;
            double distance = start.squareDistanceTo(contact.hitVec);
            if (distance < nearest) {
                nearest = distance;
                impact = new MovingObjectPosition(entity);
                impact.hitVec = contact.hitVec;
            }
        }
        return impact;
    }

    private boolean canHit(Entity entity) {
        if (!entity.canBeCollidedWith() || entity instanceof EntityFireball || entity.isEntityEqual(shootingEntity)) return false;
        if (!isEncounterMember(entity) || reflected) return true;
        if (entity instanceof EntitySummonedGhast) return stray || ((EntitySummonedGhast)entity).isRetaliating();
        return shootingEntity instanceof EntitySummonedGhast && ((EntitySummonedGhast)shootingEntity).isRetaliating();
    }

    @Override protected void onImpact(MovingObjectPosition impact) {
        if (worldObj.isRemote) return;
        if (impact.entityHit != null) {
            if (shootingEntity == null || shootingEntity.isDead) shootingEntity = resolve(GiantGhastEncounters.get(worldObj), shooterId);
            EnumDifficulty difficulty = worldObj.difficultySetting;
            float scale = difficulty == EnumDifficulty.HARD ? 1.25F : difficulty == EnumDifficulty.EASY ? 0.65F : 1;
            DamageSource source = new EntityDamageSourceIndirect("fireball", this, shootingEntity).setProjectile();
            impact.entityHit.attackEntityFrom(source, GiantGhastConfig.damage * damageScale * scale);
        }
        if (!isDead) {
            boolean terrain = GiantGhastConfig.griefing && GiantGhastConfig.explosionBlocks
                && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing");
            if (GiantGhastFlight.loaded(worldObj, boundingBox.expand(4, 0, 4))) {
                worldObj.newExplosion(this, posX, posY, posZ, power, false, terrain);
            } else {
                // Native explosions trace through terrain even with block damage disabled.
                worldObj.playSoundEffect(posX, posY, posZ, "random.explode", 2, 0.9F);
                ((WorldServer)worldObj).func_147487_a("largeexplode", posX, posY, posZ, 1, 0, 0, 0, 0);
            }
            setDead();
        }
    }

    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!reflectable || !(source.getEntity() instanceof EntityPlayer)
                || source.getSourceOfDamage() != source.getEntity() || !"player".equals(source.getDamageType()) || isDead) return false;
        if (worldObj.isRemote) return true;
        if (!super.attackEntityFrom(source, amount)) return false;
        reflected = true;
        dataWatcher.updateObject(REFLECTED, (byte)1);
        shooterId = source.getEntity().getUniqueID();
        velocityChanged = true;
        return true;
    }

    @Override public void setDead() {
        if (!worldObj.isRemote && bossId != null) GiantGhastEncounters.get(worldObj).forget(bossId, this);
        super.setDead();
    }

    @Override public void writeSpawnData(ByteBuf out) {
        out.writeFloat(power);
        out.writeBoolean(reflectable);
        out.writeDouble(motionX);
        out.writeDouble(motionY);
        out.writeDouble(motionZ);
    }

    @Override public void readSpawnData(ByteBuf in) {
        power = in.readFloat();
        reflectable = in.readBoolean();
        setSizeForPower();
        motionX = in.readDouble();
        motionY = in.readDouble();
        motionZ = in.readDouble();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        GiantGhastEncounters.writeId(tag, "Boss", bossId);
        GiantGhastEncounters.writeId(tag, "Launcher", launcherId);
        GiantGhastEncounters.writeId(tag, "Shooter", shooterId);
        tag.setLong("Expires", expiry);
        tag.setBoolean("Reflected", reflected);
        tag.setBoolean("Reflectable", reflectable);
        tag.setBoolean("Stray", stray);
        tag.setFloat("Power", power);
        tag.setFloat("DamageScale", damageScale);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        bossId = GiantGhastEncounters.readId(tag, "Boss");
        launcherId = GiantGhastEncounters.readId(tag, "Launcher");
        shooterId = GiantGhastEncounters.readId(tag, "Shooter");
        expiry = tag.getLong("Expires");
        reflected = tag.getBoolean("Reflected");
        dataWatcher.updateObject(REFLECTED, (byte)(reflected ? 1 : 0));
        reflectable = !tag.hasKey("Reflectable") || tag.getBoolean("Reflectable");
        stray = tag.getBoolean("Stray");
        power = MathHelper.clamp_float(tag.getFloat("Power"), 0.65F, 1.25F);
        damageScale = MathHelper.clamp_float(tag.getFloat("DamageScale"), 0.5F, 1.5F);
        setSizeForPower();
    }
}
