package com.hbm.entity.mob.magmagolem;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class EntityGolemFireball extends EntityFireball {
    private UUID owner;
    private int life = 65;
    private float damage;

    public EntityGolemFireball(World world) {
        super(world);
        setSize(0.7F, 0.7F);
    }

    EntityGolemFireball(EntityGiantMagmaGolem boss, Vec3 start, Vec3 direction, float damage) {
        this(boss.worldObj);
        owner = boss.getUniqueID();
        shootingEntity = boss;
        setPosition(start.xCoord, start.yCoord - height / 2, start.zCoord);
        motionX = direction.xCoord * 0.88;
        motionY = direction.yCoord * 0.88;
        motionZ = direction.zCoord * 0.88;
        accelerationX = direction.xCoord * 0.052;
        accelerationY = direction.yCoord * 0.052;
        accelerationZ = direction.zCoord * 0.052;
        this.damage = damage;
    }

    @Override public void onUpdate() {
        if (!worldObj.isRemote && (--life <= 0 || !MagmaGolemTerrain.loaded(worldObj, boundingBox.addCoord(motionX, motionY, motionZ)))) {
            setDead();
            return;
        }
        super.onUpdate();
    }

    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) { return false; }

    @Override protected void onImpact(MovingObjectPosition hit) {
        if (worldObj.isRemote) return;
        Entity victim = hit.entityHit;
        if (victim != null) {
            if (victim instanceof net.minecraft.entity.boss.EntityDragonPart) {
                net.minecraft.entity.IEntityMultiPart parent = ((net.minecraft.entity.boss.EntityDragonPart)victim).entityDragonObj;
                if (parent instanceof Entity) victim = (Entity)parent;
            }
            if (owner != null && (owner.equals(victim.getUniqueID())
                    || victim instanceof EntityGolemMagmaCube && ((EntityGolemMagmaCube)victim).belongsTo(owner))) return;
            if (hit.entityHit.attackEntityFrom(DamageSource.causeFireballDamage(this, shootingEntity), damage)) victim.setFire(4);
        } else if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.sideHit >= 0 && hit.sideHit < 6) {
            net.minecraftforge.common.util.ForgeDirection side = net.minecraftforge.common.util.ForgeDirection.getOrientation(hit.sideHit);
            MagmaGolemTerrain.ignite(worldObj, hit.blockX + side.offsetX, hit.blockY + side.offsetY, hit.blockZ + side.offsetZ);
        }
        setDead();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (owner != null) {
            tag.setLong("GolemMost", owner.getMostSignificantBits());
            tag.setLong("GolemLeast", owner.getLeastSignificantBits());
        }
        tag.setInteger("Life", life);
        tag.setFloat("Damage", damage);
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("GolemMost")) owner = new UUID(tag.getLong("GolemMost"), tag.getLong("GolemLeast"));
        life = tag.getInteger("Life");
        damage = tag.getFloat("Damage");
    }
}
