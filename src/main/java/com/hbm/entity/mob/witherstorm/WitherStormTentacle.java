package com.hbm.entity.mob.witherstorm;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;

/** Server-owned reach, whip, grip, crush and slam. Watchers drive the identical client curve. */
final class WitherStormTentacle {
    private final EntityWitherStormPart part;
    private final double[] root = new double[3], tip = new double[3];
    private final Set<Integer> struck = new HashSet<>();
    private int cooldown, severedMass, regrowTicks;
    private float damage;
    private Vec3 previousTip;

    WitherStormTentacle(EntityWitherStormPart part) { this.part = part; }

    static void init(EntityWitherStormPart part) {
        part.getDataWatcher().addObject(24, Integer.valueOf(0)); // prey
        part.getDataWatcher().addObject(25, Integer.valueOf(0)); // world tick at start
        part.getDataWatcher().addObject(26, Float.valueOf(1F)); // remaining/growing arm
        part.getDataWatcher().addObject(27, Byte.valueOf((byte)0)); // gripping
        for(int i = 28; i <= 30; i++) part.getDataWatcher().addObject(i, Float.valueOf(0F)); // aim relative to torso
        part.getDataWatcher().addObject(31, Byte.valueOf((byte)0)); // attacking, including terrain-only strikes
    }

    void emerge() { part.getDataWatcher().updateObject(26, 0.08F); regrowTicks = 60; }
    private boolean active() { return part.getDataWatcher().getWatchableObjectByte(31) != 0; }
    private boolean gripping() { return part.getDataWatcher().getWatchableObjectByte(27) != 0; }
    private float growth() { return part.getDataWatcher().getWatchableObjectFloat(26); }
    boolean intact() { return growth() >= 1F; }
    private Entity prey() { return part.worldObj.getEntityByID(part.getDataWatcher().getWatchableObjectInt(24)); }
    boolean holding(Entity entity) { return entity != null && gripping() && prey() == entity; }
    private int age() { return (int)part.worldObj.getTotalWorldTime() - part.getDataWatcher().getWatchableObjectInt(25); }

    private void stop() {
        part.getDataWatcher().updateObject(24, 0);
        part.getDataWatcher().updateObject(27, (byte)0);
        part.getDataWatcher().updateObject(31, (byte)0);
        previousTip = null; struck.clear();
        cooldown = 20 + part.slot() * 3;
    }

    void tick(EntityWitherStorm boss) {
        if(growth() < 1F) {
            stop();
            if(regrowTicks > 0) {
                part.getDataWatcher().updateObject(26, Math.min(1F, growth() + (1F - 0.08F) / 60F));
                if(--regrowTicks == 0) { part.getDataWatcher().updateObject(26, 1F); damage = 0F; }
            } else if(boss.getGrowth() - severedMass >= 120) regrowTicks = 60;
            return;
        }
        if(!active()) {
            if(cooldown-- > 0 || (boss.ticksExisted + part.slot() * 7) % 10 != 0) return;
            EntityLivingBase chosen = null;
            double best = Double.POSITIVE_INFINITY;
            Vec3 origin = boss.tentacleRoot(part.slot());
            double reach = reach(boss);
            for(EntityLivingBase entity : boss.hunt.living) {
                if(!boss.validTarget(entity) || !WitherStormMath.edible(entity, boss.worldObj) || boss.heldByOther(entity, part.slot())) continue;
                double distance = entity.getDistanceSq(origin.xCoord, origin.yCoord, origin.zCoord);
                if(distance > reach * reach) continue;
                // Intruders inside the mantle take priority over outside prey, guarding the core.
                double score = distance * (boss.boundingBox.intersectsWith(entity.boundingBox) ? 0.01D
                    : entity instanceof net.minecraft.entity.player.EntityPlayer ? 0.25D : 1D);
                if(score < best) { best = score; chosen = entity; }
            }
            Vec3 aim;
            if(chosen == null) {
                // Tear obstacles in the path with staggered downward slams even without prey.
                if((boss.ticksExisted + part.slot() * 7) % 80 != 0) return;
                Vec3 forward = WitherStormMath.headOffset(0, -Math.min(reach * 0.7D, 24D), -Math.min(reach * 0.5D, 12D), boss.rotationYaw, 0);
                aim = origin.addVector(forward.xCoord, forward.yCoord, forward.zCoord);
            } else aim = WitherStormMath.targetPoint(chosen, 1F);
            part.getDataWatcher().updateObject(24, chosen == null ? 0 : chosen.getEntityId());
            part.getDataWatcher().updateObject(25, (int)boss.worldObj.getTotalWorldTime());
            part.getDataWatcher().updateObject(31, (byte)1);
            setAim(boss, aim);
            struck.clear(); previousTip = boss.partPosition(part.slot());
        }
        int age = age();
        Entity target = prey();
        if(age < 0 || age >= 88 || target != null && (!boss.validTarget(target)
            || !WitherStormMath.edible(target, boss.worldObj) || boss.heldByOther(target, part.slot()))) { stop(); return; }
        if(age < 24 && target != null) setAim(boss, WitherStormMath.targetPoint(target, 1F));
        Vec3 end = boss.partPosition(part.slot());
        if(!WitherStormMath.loaded(boss.worldObj, end.xCoord, end.zCoord)) { stop(); return; }
        if(age == 60) struck.clear();
        if(age >= 18 && age <= 28 || age >= 60 && age <= 68) {
            for(EntityLivingBase entity : boss.hunt.living) {
                if(!boss.validTarget(entity) || !WitherStormMath.edible(entity, boss.worldObj) || struck.contains(entity.getEntityId())) continue;
                double padding = Math.max(1.5D, boss.getStormScale()) + entity.width * 0.5D;
                Vec3 point = WitherStormMath.targetPoint(entity, 1F);
                if(distanceToStroke(point, previousTip == null ? end : previousTip, end) > padding * padding) continue;
                struck.add(entity.getEntityId());
                hurt(boss, entity, age >= 60 ? 1.5F : 1F);
                if(age >= 60) {
                    entity.addVelocity((entity.posX - boss.posX) * 0.015D, -0.6D, (entity.posZ - boss.posZ) * 0.015D);
                    entity.velocityChanged = true;
                }
            }
        }
        if(age >= 24 && age < 30 && target != null && !gripping() && target.ridingEntity == null
            && target.getDistanceSq(end.xCoord, end.yCoord - target.height * 0.5D, end.zCoord) < 16D)
            part.getDataWatcher().updateObject(27, (byte)1);
        if(gripping() && target != null) {
            if(age >= 64 || target.ridingEntity != null) part.getDataWatcher().updateObject(27, (byte)0);
            else {
                // A velocity tether restrains prey without teleporting or changing its hitbox.
                target.motionX = WitherStormMath.limit((end.xCoord - target.posX) * 0.45D, 0.8D);
                target.motionY = WitherStormMath.limit((end.yCoord - target.posY - target.height * 0.5D) * 0.45D + 0.08D, 0.8D);
                target.motionZ = WitherStormMath.limit((end.zCoord - target.posZ) * 0.45D, 0.8D);
                target.fallDistance = 0; target.velocityChanged = true;
                if(age % 10 == 0) hurt(boss, (EntityLivingBase)target, 0.75F);
            }
        }
        if(age == 24 || age == 64) {
            boss.terrain.impact(end.xCoord, end.yCoord, end.zCoord, 2F);
            boss.broadcastSound("hbm:entity.witherStorm.tentacle", 1.2F, age == 64 ? 0.8F : 1F, end.xCoord, end.yCoord, end.zCoord);
        }
        previousTip = end;
    }

    private double reach(EntityWitherStorm boss) {
        boss.restArmPoint(part.slot(), 0D, 0D, root);
        double x = root[0], y = root[1], z = root[2], length = 0D;
        for(int i = 1; i <= 8; i++) {
            boss.restArmPoint(part.slot(), i / 8D, 0D, tip);
            double dx = tip[0] - x, dy = tip[1] - y, dz = tip[2] - z;
            length += Math.sqrt(dx * dx + dy * dy + dz * dz);
            x = tip[0]; y = tip[1]; z = tip[2];
        }
        return Math.min(WitherStormHunt.RADIUS, length * boss.armScale());
    }

    private void setAim(EntityWitherStorm boss, Vec3 aim) {
        part.getDataWatcher().updateObject(28, (float)(aim.xCoord - boss.posX));
        part.getDataWatcher().updateObject(29, (float)(aim.yCoord - boss.posY));
        part.getDataWatcher().updateObject(30, (float)(aim.zCoord - boss.posZ));
    }

    void pose(EntityWitherStorm boss, double t, double time, float partial, float scale, double[] out) {
        boss.restArmPoint(part.slot(), 0D, time, root);
        double remaining = growth();
        for(int i = 0; i < 3; i++) out[i] = root[i] + (out[i] - root[i]) * remaining;
        if(!active() || remaining < 1D) return;
        double age = (int)part.worldObj.getTotalWorldTime() - part.getDataWatcher().getWatchableObjectInt(25) + partial;
        double blend = smooth(age / 24D) * (1D - smooth((age - 68D) / 20D));
        boss.restArmPoint(part.slot(), 1D, time, tip);
        double dx = part.getDataWatcher().getWatchableObjectFloat(28), dy = part.getDataWatcher().getWatchableObjectFloat(29);
        double dz = part.getDataWatcher().getWatchableObjectFloat(30), yaw = Math.toRadians(boss.rotationYaw);
        // Lift a captured target, then drive it down for the slam, curling the middle of the arm around it.
        dy += 6D * smooth((age - 28D) / 16D) - 10D * smooth((age - 54D) / 10D);
        double weight = t * t * (3D - 2D * t) * blend;
        out[0] += ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / scale - tip[0]) * weight;
        out[1] += (dy / scale - tip[1]) * weight;
        out[2] += ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / scale - tip[2]) * weight;
        double curl = Math.sin(Math.PI * t) * blend;
        out[0] += Math.sin(age * 0.16D + part.slot()) * curl * 2D;
        out[1] += curl * (gripping() ? 3D : Math.sin(age * 0.14D) * 3D);
    }

    private static double smooth(double value) { double t = Math.max(0D, Math.min(1D, value)); return t * t * (3D - 2D * t); }
    private static double distanceToStroke(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.xCoord - a.xCoord, dy = b.yCoord - a.yCoord, dz = b.zCoord - a.zCoord;
        double length = dx * dx + dy * dy + dz * dz;
        double t = length < 0.0001D ? 0D : Math.max(0D, Math.min(1D,
            ((p.xCoord - a.xCoord) * dx + (p.yCoord - a.yCoord) * dy + (p.zCoord - a.zCoord) * dz) / length));
        return p.squareDistanceTo(a.xCoord + dx * t, a.yCoord + dy * t, a.zCoord + dz * t);
    }

    private void hurt(EntityWitherStorm boss, EntityLivingBase entity, float multiplier) {
        if(entity.attackEntityFrom(DamageSource.causeMobDamage(boss), boss.attackStrength() * multiplier)) {
            entity.addPotionEffect(new PotionEffect(Potion.wither.id, 100, 0));
            if(!entity.isEntityAlive()) { boss.addMass(Math.max(10, Math.min(200, (int)entity.getMaxHealth()))); boss.heal(4F); }
        }
    }

    void damaged(EntityWitherStorm boss, float amount) {
        if(growth() < 1F || !Float.isFinite(amount) || amount <= 0F) return;
        damage += amount;
        if(damage >= 40F + boss.development() * 40F) {
            stop(); part.getDataWatcher().updateObject(26, 0.08F);
            severedMass = boss.getGrowth(); regrowTicks = 0;
        }
    }

    void write(NBTTagCompound tag) {
        tag.setFloat("ArmDamage", damage); tag.setFloat("ArmGrowth", growth());
        tag.setInteger("ArmSeveredMass", severedMass); tag.setInteger("ArmRegrowTicks", regrowTicks);
    }
    void read(NBTTagCompound tag) {
        // Release transient grabs on unload/restart; victims never acquire persistent restraint state.
        stop(); damage = Math.max(0F, tag.getFloat("ArmDamage"));
        float growth = tag.hasKey("ArmGrowth") ? tag.getFloat("ArmGrowth") : 1F;
        part.getDataWatcher().updateObject(26, Float.isFinite(growth) ? Math.max(0.08F, Math.min(1F, growth)) : 1F);
        severedMass = Math.max(0, tag.getInteger("ArmSeveredMass"));
        regrowTicks = Math.max(0, Math.min(60, tag.getInteger("ArmRegrowTicks")));
    }
}
