package com.hbm.handler;

import com.hbm.config.MaceConfig;
import com.hbm.entity.mob.EntityCyberCrab;
import com.hbm.entity.mob.EntityGhost;
import com.hbm.entity.mob.EntityUAP;
import com.hbm.entity.mob.EntityUFO;
import com.hbm.entity.mob.EntityUFOBase;
import com.hbm.entity.mob.botprime.EntityBOTPrimeBase;
import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.weapon.ItemMace;
import com.hbm.potion.HbmPotion;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.ArmorRegistry.HazardClass;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

public final class MaceSpray {
    private static final String SOURCE = "rntMaceSource";
    private static final double CLOSE_TRACKING_DISTANCE_SQ = 16D;
    private static final int CLOUD_PARTICLES = 12;
    private static final int IMPACT_CLOUDS = 8;

    public static boolean activate(EntityPlayer player, MaceConfig.Spray spray) {
        if(!(player.worldObj instanceof WorldServer) || !player.isEntityAlive() || player.isPlayerSleeping()
                || !GeminiCaptures.available(player) || Restraints.blocked(player)) return false;
        WorldServer world = (WorldServer) player.worldObj;
        long now = MinecraftServer.getServer().worldServerForDimension(0).getTotalWorldTime();
        HbmLivingProps state = HbmLivingProps.getData(player);
        if(now < state.nextMaceUse) return false;
        Vec3 look = player.getLookVec();
        if(look == null || !Double.isFinite(look.xCoord + look.yCoord + look.zCoord)) return false;
        state.nextMaceUse = now + spray.cooldown;

        Vec3 origin = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 end = origin.addVector(look.xCoord * spray.range, look.yCoord * spray.range, look.zCoord * spray.range);
        double spread = spray.range * Math.sin(spray.halfAngle);
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
                Math.min(origin.xCoord, end.xCoord), Math.min(origin.yCoord, end.yCoord), Math.min(origin.zCoord, end.zCoord),
                Math.max(origin.xCoord, end.xCoord), Math.max(origin.yCoord, end.yCoord), Math.max(origin.zCoord, end.zCoord))
                .expand(spread, spread, spread);
        MaceConfig.Values settings = MaceConfig.settings;
        int impacts = 0;
        double cosine = Math.cos(spray.halfAngle);
        double rangeSq = spray.range * spray.range;
        for(Object candidate : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
            EntityLivingBase target = (EntityLivingBase) candidate;
            if(!susceptible(player, target, settings)) continue;
            Vec3 point = hitPoint(world, target, origin, look, rangeSq, cosine);
            if(point != null && expose(target, origin, spray.duration, settings.protectionEffectiveness)
                    && impacts < IMPACT_CLOUDS) {
                world.func_147487_a("cloud", point.xCoord, point.yCoord, point.zCoord, 3, 0.08D, 0.08D, 0.08D, 0D);
                impacts++;
            }
        }
        emitCloud(world, origin, look, spray);
        world.playSoundAtEntity(player, "hbm:item.spray", 0.75F, 2F);
        return true;
    }

    private static Vec3 hitPoint(World world, EntityLivingBase target, Vec3 origin, Vec3 look, double rangeSq, double cosine) {
        double eye = target.posY + target.getEyeHeight();
        double center = (target.boundingBox.minY + target.boundingBox.maxY) * 0.5D;
        for(int sample = 0; sample < 2; sample++) {
            Vec3 point = Vec3.createVectorHelper(target.posX, sample == 0 ? eye : center, target.posZ);
            double x = point.xCoord - origin.xCoord;
            double y = point.yCoord - origin.yCoord;
            double z = point.zCoord - origin.zCoord;
            double distanceSq = x * x + y * y + z * z;
            double forward = x * look.xCoord + y * look.yCoord + z * look.zCoord;
            if(distanceSq <= rangeSq && forward >= Math.sqrt(distanceSq) * cosine && clearRay(world, origin, point))
                return point;
        }
        return null;
    }

    private static boolean susceptible(EntityPlayer player, EntityLivingBase target, MaceConfig.Values settings) {
        if(target == player || !target.isEntityAlive() || target.isEntityInvulnerable()
                || !GeminiCaptures.available(target) || target.isEntityUndead()
                || settings.bossImmunity && target instanceof IBossDisplayData
                || target instanceof EntityGolem || target instanceof EntityGhost || target instanceof EntityBlaze
                || target instanceof EntitySlime || target instanceof EntityCyberCrab || target instanceof EntityBOTPrimeBase
                || target instanceof EntityUFO || target instanceof EntityUFOBase || target instanceof EntityUAP
                || target instanceof EntityPrismarineColossus || target instanceof EntityGiantMagmaGolem
                || target instanceof EntityHorse && ((EntityHorse) target).getHorseType() >= 3) return false;
        if(target instanceof EntityPlayer) {
            EntityPlayer other = (EntityPlayer) target;
            return settings.playerSusceptibility && !other.capabilities.disableDamage && other.canAttackPlayer(player);
        }
        return target instanceof EntityLiving;
    }

    private static boolean expose(EntityLivingBase target, Vec3 source, int duration, float effectiveness) {
        if(effectiveness > 0F && ArmorRegistry.absorbExposure(target, 3, HazardClass.GAS_LUNG, 1))
            duration = Math.round(duration * (1F - effectiveness));
        if(duration <= 0) return false;
        target.addPotionEffect(new PotionEffect(Potion.blindness.id, duration, 0));
        target.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, duration, 0));
        target.addPotionEffect(new PotionEffect(Potion.weakness.id, duration, 0));
        target.addPotionEffect(new PotionEffect(HbmPotion.maceIrritation.id, duration, 0));
        if(target.isPotionActive(HbmPotion.maceIrritation) && (target instanceof EntityAnimal || target instanceof EntityVillager)) {
            NBTTagCompound data = new NBTTagCompound();
            data.setDouble("x", source.xCoord);
            data.setDouble("y", source.yCoord);
            data.setDouble("z", source.zCoord);
            target.getEntityData().setTag(SOURCE, data);
        }
        return true;
    }

    private static boolean clearRay(World world, Vec3 origin, Vec3 end) {
        int minX = MathHelper.floor_double(Math.min(origin.xCoord, end.xCoord));
        int maxX = MathHelper.floor_double(Math.max(origin.xCoord, end.xCoord));
        int minZ = MathHelper.floor_double(Math.min(origin.zCoord, end.zCoord));
        int maxZ = MathHelper.floor_double(Math.max(origin.zCoord, end.zCoord));
        return world.checkChunksExist(minX, 0, minZ, maxX, 0, maxZ)
                && world.func_147447_a(origin, end, false, true, false) == null;
    }

    private static void emitCloud(WorldServer world, Vec3 origin, Vec3 look, MaceConfig.Spray spray) {
        Vec3 axis = Math.abs(look.yCoord) > 0.95D ? Vec3.createVectorHelper(1, 0, 0) : Vec3.createVectorHelper(0, 1, 0);
        Vec3 right = look.crossProduct(axis).normalize();
        Vec3 up = right.crossProduct(look).normalize();
        for(int i = 0; i < CLOUD_PARTICLES; i++) {
            double distance = spray.range * (i + 0.5D) / CLOUD_PARTICLES;
            double angle = world.rand.nextDouble() * 2D * Math.PI;
            double radius = Math.sqrt(world.rand.nextDouble()) * Math.tan(spray.halfAngle);
            double x = Math.cos(angle) * radius;
            double y = Math.sin(angle) * radius;
            Vec3 direction = look.addVector(right.xCoord * x + up.xCoord * y,
                    right.yCoord * x + up.yCoord * y, right.zCoord * x + up.zCoord * y).normalize();
            Vec3 at = origin.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
            if(clearRay(world, origin, at))
                world.func_147487_a("cloud", at.xCoord, at.yCoord, at.zCoord, 1, 0D, 0D, 0D, 0D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void useSprayerOnEntity(EntityInteractEvent event) {
        ItemStack held = event.entityPlayer.getHeldItem();
        // Let vanilla's use-item fallback handle the burst when the crosshair is on an entity.
        if(held != null && held.getItem() instanceof ItemMace) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void disruptAcquisition(LivingSetAttackTargetEvent event) {
        if(event.target == null || !(event.entityLiving instanceof EntityLiving)
                || !canDisruptTracking(event.entityLiving)) return;
        EntityLiving mob = (EntityLiving) event.entityLiving;
        if(mob.getAttackTarget() == event.target && mob.getDistanceSqToEntity(event.target) > CLOSE_TRACKING_DISTANCE_SQ)
            mob.setAttackTarget(null);
    }

    private static boolean canDisruptTracking(EntityLivingBase entity) {
        return !entity.worldObj.isRemote && entity instanceof IMob && entity.isPotionActive(HbmPotion.maceIrritation)
                && mayChangeAI(entity);
    }

    private static boolean mayChangeAI(EntityLivingBase entity) {
        return !Restraints.blocked(entity) && GeminiCaptures.available(entity)
                && entity.ridingEntity == null && entity.riddenByEntity == null;
    }

    public static void tickIrritation(EntityLivingBase entity) {
        if(entity.worldObj.isRemote || !mayChangeAI(entity)) return;
        if(entity instanceof IMob && entity instanceof EntityLiving) {
            EntityLiving mob = (EntityLiving) entity;
            EntityLivingBase target = mob.getAttackTarget();
            if(target != null && mob.getDistanceSqToEntity(target) > CLOSE_TRACKING_DISTANCE_SQ) {
                mob.setAttackTarget(null);
                mob.getNavigator().clearPathEntity();
            }
            // Legacy creatures reacquire directly. Space their tracking interruptions out.
            if(mob instanceof EntityCreature && mob.ticksExisted % 10 == 0) {
                EntityCreature creature = (EntityCreature) mob;
                Entity legacyTarget = creature.getEntityToAttack();
                if(legacyTarget != null && creature.getDistanceSqToEntity(legacyTarget) > CLOSE_TRACKING_DISTANCE_SQ) {
                    creature.setTarget(null);
                    creature.setPathToEntity(null);
                }
            }
        }
        if(entity instanceof EntityAnimal || entity instanceof EntityVillager) {
            HbmLivingProps state = HbmLivingProps.getData(entity);
            if(!state.macePanicInstalled && entity.getEntityData().hasKey(SOURCE, 10)) {
                EntityCreature creature = (EntityCreature) entity;
                creature.tasks.addTask(0, new SprayPanic(creature));
                state.macePanicInstalled = true;
            }
        }
    }

    public static void clearIrritation(EntityLivingBase entity) {
        entity.getEntityData().removeTag(SOURCE);
    }

    private static final class SprayPanic extends EntityAIPanic {
        private final EntityCreature creature;
        private Vec3 destination;
        private PathEntity path;
        private int nextAttempt;

        SprayPanic(EntityCreature creature) {
            super(creature, 1.25D);
            this.creature = creature;
        }

        private boolean irritated() {
            return creature.isPotionActive(HbmPotion.maceIrritation) && mayChangeAI(creature)
                    && creature.getEntityData().hasKey(SOURCE, 10);
        }

        @Override
        public boolean shouldExecute() {
            if(!irritated() || creature.ticksExisted < nextAttempt) return false;
            nextAttempt = creature.ticksExisted + 10;
            int radius = MathHelper.ceiling_float_int(creature.getNavigator().getPathSearchRange()) + 9;
            int x = MathHelper.floor_double(creature.posX);
            int z = MathHelper.floor_double(creature.posZ);
            // Vanilla pathfinding reads the entire navigation cache, not just the destination chunk.
            if(!creature.worldObj.checkChunksExist(x - radius, 0, z - radius, x + radius, 0, z + radius)) return false;
            NBTTagCompound source = creature.getEntityData().getCompoundTag(SOURCE);
            Vec3 origin = Vec3.createVectorHelper(source.getDouble("x"), source.getDouble("y"), source.getDouble("z"));
            destination = RandomPositionGenerator.findRandomTargetBlockAwayFrom(creature, 6, 4, origin);
            return destination != null;
        }

        @Override
        public void startExecuting() {
            double speed = creature instanceof EntityVillager ? 0.6D : 1.25D;
            path = creature.getNavigator().tryMoveToXYZ(destination.xCoord, destination.yCoord, destination.zCoord, speed)
                    ? creature.getNavigator().getPath() : null;
        }

        @Override
        public boolean continueExecuting() {
            return irritated() && path != null && creature.getNavigator().getPath() == path && super.continueExecuting();
        }

        @Override
        public void resetTask() {
            if(path != null && creature.getNavigator().getPath() == path) creature.getNavigator().clearPathEntity();
            path = null;
        }
    }
}
