package com.hbm.entity.mob.sandworm;

import com.hbm.config.SandwormConfig;
import com.hbm.world.sandworm.WormContent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public final class EntityTick extends EntityWormMonster {
    private int bloodCooldown;
    private int appliedStage = -1;
    public EntityTick(World w) {
        super(w); setSize(1.2F, 0.8F);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIAttackOnCollide(this, EntityPlayer.class, 0.3, false));
        tasks.addTask(2, new EntityAIWander(this, 0.25));
        targetTasks.addTask(1, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 20, true));
    }
    @Override protected void entityInit() { super.entityInit(); dataWatcher.addObject(20, (byte)0); }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(SandwormConfig.tickHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.699);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(SandwormConfig.tickDamage);
    }
    public int stage() { return dataWatcher.getWatchableObjectByte(20); }
    public float scale() { return 1F + stage() * 0.2F; }
    private void applyStage() {
        if(appliedStage == stage()) return;
        appliedStage = stage(); setSize(1.2F * scale(), 0.8F * scale());
        if(!worldObj.isRemote) getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Math.min(1024, SandwormConfig.tickHealth * (1 + stage() * SandwormConfig.tickGrowth)));
    }
    public boolean grow() {
        if(worldObj.isRemote || stage() >= 5 || !isEntityAlive()) return false;
        dataWatcher.updateObject(20, (byte)(stage() + 1)); applyStage(); setHealth(getMaxHealth()); return true;
    }
    public void growFromBlood() { if(bloodCooldown <= 0 && grow()) bloodCooldown = SandwormConfig.bloodGrowthInterval; }
    @Override public boolean attackEntityAsMob(Entity e) {
        if(!(e instanceof EntityPlayer) || !canAffect((EntityPlayer)e)) return false;
        boolean success = super.attackEntityAsMob(e); if(success) grow(); return success;
    }
    @Override public void onLivingUpdate() { applyStage(); if(!worldObj.isRemote && bloodCooldown > 0) bloodCooldown--; super.onLivingUpdate(); }
    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if(!worldObj.isRemote && stage() == 5) {
            int x = MathHelper.floor_double(posX), y = MathHelper.floor_double(posY), z = MathHelper.floor_double(posZ);
            if(worldObj.isAirBlock(x, y, z) && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")) worldObj.setBlock(x, y, z, WormContent.bloodSplash);
        }
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); n.setInteger("Growth", stage()); n.setInteger("BloodCooldown", bloodCooldown); }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n); dataWatcher.updateObject(20, (byte)Math.max(0, Math.min(5, n.getInteger("Growth"))));
        bloodCooldown = Math.max(0, n.getInteger("BloodCooldown")); appliedStage = -1; applyStage();
    }
}
