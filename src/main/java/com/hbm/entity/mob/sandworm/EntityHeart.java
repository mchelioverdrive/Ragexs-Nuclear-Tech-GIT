package com.hbm.entity.mob.sandworm;

import com.hbm.config.SandwormConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class EntityHeart extends EntityWormMonster implements net.minecraft.entity.boss.IBossDisplayData {
    private float phase;
    public EntityHeart(World w) { super(w); setSize(3, 4); }
    @Override protected void entityInit() { super.entityInit(); dataWatcher.addObject(20, 0); dataWatcher.addObject(21, 0F); }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(SandwormConfig.heartHealth);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
    }
    public int invulnerableTicks() { return dataWatcher.getWatchableObjectInt(20); }
    public float pumpPhase() { return dataWatcher.getWatchableObjectFloat(21); }
    public float pumpRate() { return invulnerableTicks() > 0 ? 1F : 1F / Math.max(1F, 10F - getMaxHealth() / Math.max(1F, getHealth())); }
    @Override public void moveEntity(double x, double y, double z) { motionX = motionY = motionZ = 0; }
    @Override public void onLivingUpdate() {
        super.onLivingUpdate();
        if(worldObj.isRemote || !isEntityAlive()) return;
        if(invulnerableTicks() > 0) dataWatcher.updateObject(20, invulnerableTicks() - 1);
        float old = phase;
        phase = (phase + pumpRate()) % ((float)Math.PI * 2);
        dataWatcher.updateObject(21, phase);
        if(phase < old) playSound("hbm:sandworm.heart_up", 2F, 1F);
        else if(old < Math.PI && phase >= Math.PI) playSound("hbm:sandworm.heart_down", 2F, 1F);
        if(invulnerableTicks() > 0 && ticksExisted % 10 == 0) EntityWormProjectile.blood(this);
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if(worldObj.isRemote || invulnerableTicks() > 0) return false;
        boolean wounded = super.attackEntityFrom(source, damage);
        if(wounded) dataWatcher.updateObject(20, SandwormConfig.heartInvulnerability);
        return wounded;
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); n.setInteger("Invulnerable", invulnerableTicks()); n.setFloat("Pump", phase); }
    @Override public void readEntityFromNBT(NBTTagCompound n) { super.readEntityFromNBT(n); dataWatcher.updateObject(20, Math.max(0, n.getInteger("Invulnerable"))); phase = n.getFloat("Pump"); dataWatcher.updateObject(21, phase); }
}
