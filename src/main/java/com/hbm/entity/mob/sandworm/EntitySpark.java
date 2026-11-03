package com.hbm.entity.mob.sandworm;

import java.util.List;
import com.hbm.config.SandwormConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class EntitySpark extends EntityWormMonster {
    public static final int ORANGE = 1, BLUE = 2, GREEN = 3, PURPLE = 4;
    public EntitySpark(World w) {
        super(w); setSize(0.9F, 1.4F);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIAttackOnCollide(this, EntityPlayer.class, 0.3, false));
        tasks.addTask(2, new EntityAIWander(this, 0.25));
        targetTasks.addTask(1, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 20, true));
        if(!w.isRemote) setVariant(1 + rand.nextInt(4));
    }
    @Override protected void entityInit() { super.entityInit(); dataWatcher.addObject(20, (byte)1); }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(SandwormConfig.sparkHealth);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.699);
    }
    public int variant() { return dataWatcher.getWatchableObjectByte(20); }
    public void setVariant(int v) { dataWatcher.updateObject(20, (byte)Math.max(1, Math.min(4, v))); }
    @Override public boolean attackEntityAsMob(Entity e) { return false; } // Effects below own damage and its interval.
    @Override public void onLivingUpdate() {
        super.onLivingUpdate();
        if(worldObj.isRemote || !isEntityAlive() || ticksExisted % SandwormConfig.sparkInterval != 0) return;
        if(variant() == GREEN) {
            List<EntityWormMonster> list = worldObj.getEntitiesWithinAABB(EntityWormMonster.class, boundingBox.expand(4, 4, 4));
            for(EntityWormMonster e : list) if(e != this && e.isEntityAlive() && allied(e)) e.heal(SandwormConfig.sparkHeal);
        } else {
            List<EntityPlayer> list = worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(4, 4, 4));
            for(EntityPlayer p : list) if(canAffect(p)) {
                if(variant() == ORANGE) p.attackEntityFrom(DamageSource.causeMobDamage(this), SandwormConfig.sparkDamage);
                else if(variant() == PURPLE) p.addPotionEffect(new PotionEffect(Potion.blindness.id, SandwormConfig.blindness, 1));
                else {
                    double dx = p.posX - posX, dz = p.posZ - posZ;
                    double squared = Math.max(1, dx * dx + dz * dz);
                    if(dx == 0 && dz == 0) dx = 1;
                    p.addVelocity(dx / squared * 8, 1.5, dz / squared * 8); p.velocityChanged = true;
                }
            }
        }
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); n.setInteger("Variant", variant()); }
    @Override public void readEntityFromNBT(NBTTagCompound n) { super.readEntityFromNBT(n); setVariant(n.getInteger("Variant")); }
}
