package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class EntityWitherStormSkull extends EntityThrowable {
    public UUID parentId;
    private float strength = 2F, damage = 8F;
    private int life;
    private long born;
    public EntityWitherStormSkull(World world) { super(world); setSize(0.7F, 0.7F); isImmuneToFire = true; }
    public void launch(EntityWitherStorm parent, Vec3 position, Vec3 direction) {
        parentId = parent.getUniqueID(); damage = parent.attackStrength();
        born = worldObj.getTotalWorldTime();
        strength = WitherStormConfig.explosionStrength * (1F + parent.getPhase().ordinal() * 0.35F);
        // Start outside the mouth. Collision with any encounter part is explicitly ignored.
        setPosition(position.xCoord + direction.xCoord * 3 * parent.getStormScale(), position.yCoord + direction.yCoord * 3 * parent.getStormScale(), position.zCoord + direction.zCoord * 3 * parent.getStormScale());
        motionX = direction.xCoord * 1.1D; motionY = direction.yCoord * 1.1D; motionZ = direction.zCoord * 1.1D;
    }
    @Override protected float getGravityVelocity() { return 0F; }
    @Override public void onUpdate() {
        // The vanilla throwable update can query the destination chunk. Stop before it
        // crosses an unloaded boundary rather than allowing a projectile to generate terrain.
        if(!worldObj.isRemote && (++life > 160 || expired() || !WitherStormMath.areaLoaded(worldObj, posX + motionX, posZ + motionZ, 34)
            || (life > 20 && !WitherStormEncounters.get(worldObj).contains(parentId)))) { setDead(); return; }
        super.onUpdate();
    }
    public boolean expired() { return worldObj.getTotalWorldTime() - born >= 160; }
    public long birthTime() { return born; }
    @Override protected void onImpact(MovingObjectPosition hit) {
        if(worldObj.isRemote || isDead) return;
        if(hit.entityHit instanceof EntityWitherStorm || hit.entityHit instanceof EntityWitherStormPart || hit.entityHit instanceof EntityWitherStormSkull) return;
        Entity e = WitherStormEncounters.get(worldObj).find(parentId, worldObj);
        if(!(e instanceof EntityWitherStorm)) { setDead(); return; }
        EntityWitherStorm boss = (EntityWitherStorm)e;
        ExplosionVNT explosion = new ExplosionVNT(worldObj, posX, posY, posZ, strength, this);
        if(ForgeEventFactory.onExplosionStart(worldObj, explosion.compat)) { setDead(); return; }
        List<Entity> nearby = new ArrayList<Entity>(64);
        WitherStormMath.nearby(this, Math.max(3D, strength * 2D), nearby, life);
        ForgeEventFactory.onExplosionDetonate(worldObj, explosion.compat, nearby, Math.max(3D, strength * 2D));
        for(Entity target : nearby) {
            if(!WitherStormMath.edible(target, worldObj) || !(target instanceof EntityLivingBase)) continue;
            double d = getDistanceToEntity(target), range = Math.max(3D, strength * 2D);
            if(d > range) continue;
            float amount = damage * (float)(1D - 0.6D * d / range);
            if(target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, boss).setExplosion(), amount))
                ((EntityLivingBase)target).addPotionEffect(new PotionEffect(Potion.wither.id, 200 + boss.getPhase().ordinal() * 100, boss.getPhase().ordinal() >= 2 ? 1 : 0));
        }
        // Only RNT's ordinary explosion SFX; terrain belongs to the root's bounded queue.
        explosion.setSFX(new ExplosionEffectStandard()).explode();
        boss.terrain.impact(posX, posY, posZ, strength);
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
