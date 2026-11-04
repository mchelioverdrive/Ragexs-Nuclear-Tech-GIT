package com.hbm.entity.mob.witherstorm;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import com.hbm.config.WitherStormConfig;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;

/** Temporarily owns the AI decision, leaving the mob's native movement and its task lists intact. */
public final class WitherStormFear {
    private static final double[] ANGLES = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 3, -Math.PI / 3};
    private static final Map<EntityLiving, Escape> fleeing = new WeakHashMap<>();

    private WitherStormFear() { }

    private static final class Escape {
        WeakReference<EntityWitherStorm> threat;
        long retryAt;
        int direction;
        double heading;
        boolean blocked;

        Escape(EntityWitherStorm threat) { this.threat = new WeakReference<>(threat); }
    }

    public static boolean isFleeing(EntityLiving mob) {
        Escape escape = fleeing.get(mob);
        return escape != null && inRange(mob, escape.threat.get());
    }

    private static boolean inRange(EntityLiving mob, EntityWitherStorm storm) {
        double range = WitherStormConfig.fearRange;
        return storm != null && storm.worldObj == mob.worldObj && storm.isEntityAlive()
            && mob.getDistanceSqToEntity(storm) <= range * range;
    }

    /** Called at the vanilla task/legacy AI dispatch sites, before travel and jumping. */
    public static boolean update(Object entity) {
        if(!(entity instanceof EntityLiving) || entity instanceof IBossDisplayData) return false;
        EntityLiving mob = (EntityLiving)entity;
        if(mob.worldObj.isRemote || !mob.isEntityAlive()
            || mob instanceof EntityGhast && mob.worldObj.difficultySetting == EnumDifficulty.PEACEFUL) return false;
        Escape escape = fleeing.get(mob);
        EntityWitherStorm threat = escape == null ? null : escape.threat.get();
        long now = mob.worldObj.getTotalWorldTime();
        int interval = WitherStormConfig.fearInterval;
        if(Math.floorMod(now + mob.getEntityId(), interval) == 0) {
            threat = WitherStormEncounters.get(mob.worldObj).nearestThreat(mob, WitherStormConfig.fearRange);
        }
        if(!inRange(mob, threat)) {
            if(escape != null) {
                fleeing.remove(mob);
                clearPath(mob);
                mob.setJumping(false);
                mob.moveForward = 0;
            }
            return false;
        }
        if(escape == null || escape.threat.get() != threat) {
            escape = new Escape(threat);
            fleeing.put(mob, escape);
            clearPath(mob);
            mob.setRevengeTarget(null);
            if(mob instanceof EntityCreature) {
                ((EntityCreature)mob).setTarget(null);
                ((EntityCreature)mob).setPathToEntity(null);
            }
        }
        if(mob.getAttackTarget() != null) mob.setAttackTarget(null);
        if(mob instanceof EntityCreeper) ((EntityCreeper)mob).setCreeperState(-1);
        if(mob instanceof EntityGhast) ((EntityGhast)mob).attackCounter = ((EntityGhast)mob).prevAttackCounter = 0;
        if(mob instanceof EntityBat) ((EntityBat)mob).setIsBatHanging(false);
        VanillaDespawn.tick(mob);
        if(mob.isEntityAlive()) move(mob, threat, escape, now);
        return true;
    }

    private static void clearPath(EntityLiving mob) {
        if(mob.getNavigator() != null) mob.getNavigator().clearPathEntity();
    }

    private static void move(EntityLiving mob, EntityWitherStorm storm, Escape escape, long now) {
        double speed = mob instanceof IMob ? WitherStormConfig.hostileFleeSpeed : WitherStormConfig.passiveFleeSpeed;
        double away = Math.atan2(mob.posZ - storm.posZ, mob.posX - storm.posX);
        boolean flying = mob instanceof EntityFlying || mob instanceof EntityBat;
        boolean swimming = mob.isInWater();
        PathNavigate nav = mob.getNavigator();
        if(now >= escape.retryAt) {
            escape.retryAt = now + WitherStormConfig.fearInterval;
            if(nav != null && !flying && !swimming && !(mob instanceof EntitySlime)) {
                if(!nav.noPath() && (mob.isCollidedHorizontally || !awayPath(nav.getPath(), mob, storm))) nav.clearPathEntity();
                if(nav.noPath()) {
                    escape.heading = ANGLES[escape.direction++ % ANGLES.length];
                    tryPath(nav, mob, storm, away + escape.heading, speed);
                }
            } else if(escape.blocked || mob.isCollidedHorizontally) escape.heading = ANGLES[escape.direction++ % ANGLES.length];
        }

        mob.moveStrafing = 0F;
        if(!flying && !swimming && nav != null && !nav.noPath()) {
            nav.onUpdateNavigation();
            if(!nav.noPath()) {
                mob.getMoveHelper().onUpdateMoveHelper();
                mob.getJumpHelper().doJump();
                return;
            }
        }
        escape.blocked = !steer(mob, storm, away + escape.heading, speed, flying, swimming);
    }

    private static void tryPath(PathNavigate nav, EntityLiving mob, EntityWitherStorm storm, double angle, double speed) {
        double search = nav.getPathSearchRange();
        if(search < 5 || search > 64 || !WitherStormMath.areaLoaded(mob.worldObj, mob.posX, mob.posZ, search + 9)) return;
        double distance = Math.sqrt(mob.getDistanceSqToEntity(storm));
        double leg = Math.min(Math.min(16D, search - 2D), Math.max(4D, WitherStormConfig.fearEscapeDistance - distance));
        int x = MathHelper.floor_double(mob.posX + Math.cos(angle) * leg);
        int z = MathHelper.floor_double(mob.posZ + Math.sin(angle) * leg);
        int y = MathHelper.floor_double(mob.boundingBox.minY + 0.5D);
        // Find a nearby walkable height without directing the mob off a cliff.
        while(y > 1 && y > mob.posY - 4 && !mob.worldObj.getBlock(x, y - 1, z).getMaterial().blocksMovement()) y--;
        while(y < 254 && y < mob.posY + 4 && mob.worldObj.getBlock(x, y, z).getMaterial().blocksMovement()) y++;
        PathEntity path = nav.getPathToXYZ(x, y, z);
        if(path == null || !awayPath(path, mob, storm)) return;
        PathPoint end = path.getFinalPathPoint();
        if(end.xCoord == x && end.yCoord == y && end.zCoord == z) nav.setPath(path, speed);
    }

    private static boolean awayPath(PathEntity path, EntityLiving mob, EntityWitherStorm storm) {
        if(path == null || path.isFinished()) return false;
        double current = horizontalDistanceSq(mob.posX, mob.posZ, storm);
        for(int i = path.getCurrentPathIndex(); i < path.getCurrentPathLength(); i++) {
            PathPoint point = path.getPathPointFromIndex(i);
            double distance = horizontalDistanceSq(point.xCoord + 0.5D, point.zCoord + 0.5D, storm);
            if(distance + 1D < current) return false;
        }
        PathPoint end = path.getFinalPathPoint();
        return horizontalDistanceSq(end.xCoord + 0.5D, end.zCoord + 0.5D, storm) > current;
    }

    private static double horizontalDistanceSq(double x, double z, EntityWitherStorm storm) {
        double dx = x - storm.posX, dz = z - storm.posZ;
        return dx * dx + dz * dz;
    }

    private static boolean steer(EntityLiving mob, EntityWitherStorm storm, double angle, double speed, boolean flying, boolean swimming) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        mob.moveForward = 0F;
        mob.setJumping(false);
        if(!WitherStormMath.areaLoaded(mob.worldObj, mob.posX + dx, mob.posZ + dz, mob.width + 2D)) {
            if(mob instanceof EntitySquid) SquidMovement.steer((EntitySquid)mob, 0, 0);
            return false;
        }
        if(flying || swimming) {
            double dy = flying ? WitherStormMath.limit((mob.posY - storm.posY) * 0.15D, 0.6D) : 0D;
            if(flying && dy < 0 && mob.onGround) dy = 0.4D;
            if(!clearStep(mob, dx, dy, dz)) {
                // A floor or ceiling may block the vertical component of an otherwise clear escape.
                if(flying && dy != 0D && clearStep(mob, dx, 0, dz)) dy = 0D;
                else if(flying && clearStep(mob, dx, 0.6D, dz)) dy = 0.6D;
                else {
                    if(mob instanceof EntitySquid) SquidMovement.steer((EntitySquid)mob, 0, 0);
                    return false;
                }
            }
            if(mob instanceof EntitySquid) {
                SquidMovement.steer((EntitySquid)mob, dx * speed * 0.2D, dz * speed * 0.2D);
                return true;
            }
            mob.motionX = mob.motionX * 0.8D + dx * speed * 0.045D;
            mob.motionZ = mob.motionZ * 0.8D + dz * speed * 0.045D;
            if(flying) mob.motionY = mob.motionY * 0.8D + dy * 0.12D;
            else if(!(mob instanceof EntityWaterMob)) mob.setJumping(true);
        } else {
            mob.setAIMoveSpeed((float)(speed * mob.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue()));
            mob.setJumping(mob.isCollidedHorizontally || mob instanceof EntitySlime);
        }
        mob.rotationYaw = (float)(angle * 180D / Math.PI - 90D);
        mob.rotationYawHead = mob.rotationYaw;
        return true;
    }

    private static boolean clearStep(EntityLiving mob, double x, double y, double z) {
        return mob.worldObj.func_147461_a(mob.boundingBox.getOffsetBoundingBox(x * 0.25D, y * 0.25D, z * 0.25D)).isEmpty();
    }

    private static final class VanillaDespawn {
        private static final Field AGE = ReflectionHelper.findField(EntityLivingBase.class, "entityAge", "field_70708_bq");
        private static final Method DESPAWN = ReflectionHelper.findMethod(EntityLiving.class, null, new String[] {"despawnEntity", "func_70623_bb"});

        static void tick(EntityLiving mob) {
            try {
                AGE.setInt(mob, mob.getAge() + 1);
                DESPAWN.invoke(mob);
            } catch(ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not run native mob despawn checks", failure);
            }
        }
    }

    private static final class SquidMovement {
        private static final Field X = ReflectionHelper.findField(EntitySquid.class, "randomMotionVecX", "field_70872_bC");
        private static final Field Y = ReflectionHelper.findField(EntitySquid.class, "randomMotionVecY", "field_70869_bD");
        private static final Field Z = ReflectionHelper.findField(EntitySquid.class, "randomMotionVecZ", "field_70870_bE");

        static void steer(EntitySquid squid, double x, double z) {
            try {
                X.setFloat(squid, (float)x);
                Y.setFloat(squid, 0F);
                Z.setFloat(squid, (float)z);
            } catch(IllegalAccessException failure) {
                throw new IllegalStateException("Could not steer squid", failure);
            }
        }
    }
}
