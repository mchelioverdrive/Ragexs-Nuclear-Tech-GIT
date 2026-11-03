package com.hbm.entity.mob.sandworm;

import java.util.List;
import com.hbm.config.SandwormConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class EntityBrain extends EntityWormMonster implements net.minecraft.entity.boss.IBossDisplayData {
    private int attack = SandwormConfig.brainInterval, wave = SandwormConfig.waveInterval;
    private boolean engaged;
    public EntityBrain(World w) { super(w); setSize(4, 3); }
    @Override protected void entityInit() { super.entityInit(); dataWatcher.addObject(20, (byte)0); dataWatcher.addObject(21, 0); }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(SandwormConfig.brainHealth);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
    }
    public boolean shielded() { return dataWatcher.getWatchableObjectByte(20) != 0; }
    public int attackAnimation() { return dataWatcher.getWatchableObjectInt(21); }
    @Override public void moveEntity(double x, double y, double z) { motionX = motionY = motionZ = 0; }
    private List<EntitySpark> sparks() { return worldObj.getEntitiesWithinAABB(EntitySpark.class, boundingBox.expand(10, 5, 10)); }
    private void updateShield() {
        boolean shield = false;
        for(EntitySpark s : sparks()) if(s.isEntityAlive() && allied(s)) { shield = true; break; }
        dataWatcher.updateObject(20, (byte)(shield ? 1 : 0));
    }
    private void spawnWave() {
        int existing = 0;
        for(EntitySpark s : sparks()) if(s.isEntityAlive() && allied(s)) existing++;
        int count = Math.min(SandwormConfig.sparkLimit - existing, SandwormConfig.waveMin + rand.nextInt(SandwormConfig.waveMax - SandwormConfig.waveMin + 1));
        for(int i = 0; i < count; i++) {
            EntitySpark s = new EntitySpark(worldObj); s.bind(encounter, null);
            s.setLocationAndAngles(posX + rand.nextInt(9) - 4, posY, posZ + rand.nextInt(9) - 4, rand.nextFloat() * 360, 0);
            worldObj.spawnEntityInWorld(s);
        }
        updateShield();
    }
    @Override public void onLivingUpdate() {
        super.onLivingUpdate();
        if(worldObj.isRemote || !isEntityAlive()) return;
        if(attackAnimation() > 0) dataWatcher.updateObject(21, attackAnimation() - 1);
        if(ticksExisted % 10 == 0) updateShield();
        if(ticksExisted % 20 == 0) {
            boolean active = nearbyPlayer(18) != null;
            if(active && !engaged) spawnWave();
            engaged = active;
        }
        if(!engaged) return;
        if(--wave <= 0) { spawnWave(); wave = SandwormConfig.waveInterval; }
        if(--attack <= 0) {
            int shots = 12 + rand.nextInt(9);
            for(int i = 0; i < shots; i++) EntityWormProjectile.energy(this, i * Math.PI * 2 / shots);
            dataWatcher.updateObject(21, 16); attack = SandwormConfig.brainInterval;
        }
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if(worldObj.isRemote) return false;
        updateShield();
        return !shielded() && super.attackEntityFrom(source, damage);
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); n.setInteger("Attack", attack); n.setInteger("Wave", wave); }
    @Override public void readEntityFromNBT(NBTTagCompound n) { super.readEntityFromNBT(n); attack = Math.max(1, n.getInteger("Attack")); wave = Math.max(1, n.getInteger("Wave")); }
}
