package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import com.hbm.entity.projectile.EntityThrowableNT;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class EntityWitherStormSkull extends EntityThrowableNT {
    public UUID parentId;
    private float strength = 2F, damage = 8F;
    private int life;
    private long born;
    public EntityWitherStormSkull(World world) { super(world); setSize(0.7F, 0.7F); isImmuneToFire = true; }
    public void launch(EntityWitherStorm parent, Vec3 position, Vec3 direction) {
        parentId = parent.getUniqueID(); damage = parent.attackStrength();
        born = worldObj.getTotalWorldTime();
        strength = WitherStormConfig.explosionStrength * (1F + parent.getPhase().ordinal() * 0.35F);
        setPosition(position.xCoord, position.yCoord, position.zCoord);
        // Initialize the throwable's motion-derived angles before the first tracking packet.
        setThrowableHeading(direction.xCoord, direction.yCoord, direction.zCoord, 1.1F, 0F);
    }
    @Override public double getGravityVelocity() { return 0D; }
    @Override public void onUpdate() {
        // The throwable's collision trace can query the destination chunk. Stop before it
        // crosses an unloaded boundary rather than allowing a projectile to generate terrain.
        if(!worldObj.isRemote && (++life > 160 || expired() || !WitherStormMath.areaLoaded(worldObj, posX + motionX, posZ + motionZ, 34)
            || (life > 20 && !WitherStormEncounters.get(worldObj).contains(parentId)))) { setDead(); return; }
        lastTickPosX = posX;
        lastTickPosY = posY;
        lastTickPosZ = posZ;
        super.onUpdate();
        if(worldObj.isRemote && !isDead) {
            for(int i = 0; i < 2; i++) worldObj.spawnParticle("flame", posX - motionX * i * 0.35D + (rand.nextDouble() - 0.5D) * 0.35D,
                posY - motionY * i * 0.35D + (rand.nextDouble() - 0.5D) * 0.35D, posZ - motionZ * i * 0.35D + (rand.nextDouble() - 0.5D) * 0.35D, 0, 0.015D, 0);
            if(ticksExisted % 2 == 0) worldObj.spawnParticle("smoke", posX - motionX * 0.5D, posY - motionY * 0.5D, posZ - motionZ * 0.5D, 0, 0.025D, 0);
        }
    }
    public boolean expired() { return worldObj.getTotalWorldTime() - born >= 160; }
    public long birthTime() { return born; }
    @Override protected boolean canImpactEntity(Entity entity) {
        return !(entity instanceof EntityWitherStorm || entity instanceof EntityWitherStormPart || entity instanceof EntityWitherStormSkull);
    }
    @Override protected MovingObjectPosition traceBlocks(Vec3 start, Vec3 end) {
        return worldObj.rayTraceBlocks(start, end);
    }
    @Override protected void onImpact(MovingObjectPosition hit) {
        if(worldObj.isRemote || isDead) return;
        Entity e = WitherStormEncounters.get(worldObj).find(parentId, worldObj);
        if(!(e instanceof EntityWitherStorm)) { setDead(); return; }
        EntityWitherStorm boss = (EntityWitherStorm)e;
        if(hit.hitVec != null) setPosition(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
        ExplosionVNT explosion = new ExplosionVNT(worldObj, posX, posY, posZ, strength, this);
        if(ForgeEventFactory.onExplosionStart(worldObj, explosion.compat)) { setDead(); return; }
        List<Entity> nearby = new ArrayList<Entity>(64);
        WitherStormMath.nearby(this, Math.max(3D, strength * 2D), nearby, life);
        // A direct victim must not disappear from the bounded area sample. Protection listeners still own the list.
        if(hit.entityHit != null && !nearby.contains(hit.entityHit)) nearby.add(hit.entityHit);
        ForgeEventFactory.onExplosionDetonate(worldObj, explosion.compat, nearby, Math.max(3D, strength * 2D));
        for(Entity target : nearby) {
            if(!WitherStormMath.edible(target, worldObj) || !(target instanceof EntityLivingBase)) continue;
            double d = getDistanceToEntity(target), range = Math.max(3D, strength * 2D);
            if(d > range) continue;
            float amount = damage * (float)(1D - 0.6D * d / range);
            if(target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, boss).setExplosion().setFireDamage(), amount)) {
                target.setFire(6);
                ((EntityLivingBase)target).addPotionEffect(new PotionEffect(Potion.wither.id, 200 + boss.getPhase().ordinal() * 100, boss.getPhase().ordinal() >= 2 ? 1 : 0));
            }
        }
        // Only RNT's ordinary explosion SFX; terrain belongs to the root's bounded queue.
        explosion.setSFX(new ExplosionEffectStandard()).explode();
        boss.terrain.impact(posX, posY, posZ, strength, true);
        setDead();
    }
    @Override public void setDead() { super.setDead(); if(!worldObj.isRemote) WitherStormEncounters.get(worldObj).untrack(this); }
    @Override public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n); WitherStormMath.uuid(n, "Parent", parentId); n.setFloat("Strength", strength); n.setFloat("Damage", damage); n.setInteger("Life", life); n.setLong("Born", born);
    }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n); parentId = WitherStormMath.uuid(n, "Parent");
        strength = (float)WitherStormMath.limit(n.getFloat("Strength"), 16); damage = (float)Math.max(1, WitherStormMath.limit(n.getFloat("Damage"), 100)); life = Math.max(0, n.getInteger("Life"));
        born = n.hasKey("Born") ? n.getLong("Born") : worldObj.getTotalWorldTime() - life;
    }
}
