package com.hbm.handler.restraint;

import com.hbm.config.ExecutionEffectsConfig;
import com.hbm.config.ExecutionEffectsConfig.Values;
import com.hbm.entity.mob.EntityCyberCrab;
import com.hbm.entity.mob.EntityGhost;
import com.hbm.entity.mob.EntityUAP;
import com.hbm.entity.mob.EntityUFO;
import com.hbm.entity.mob.EntityUFOBase;
import com.hbm.entity.mob.botprime.EntityBOTPrimeBase;
import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.ExecutionEffectPacket;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.EntityRegistry.EntityRegistration;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Visual cues are sent from the server without depending on a client tile or occupant. */
public final class ExecutionDeviceEffects {
    public static final int ORDINARY = 1, BLOOD = 2, DEBRIS = 4, DECALS = 8;

    public enum Cue {
        BLADE_DEATH(BLOOD | DECALS), BULL_HEAT(ORDINARY), CHAIR_DISCHARGE(ORDINARY),
        INJECTION(ORDINARY), TRAPDOOR(ORDINARY), CHIPPER_HIT(ORDINARY | BLOOD),
        CHIPPER_DEATH(ORDINARY | BLOOD | DEBRIS | DECALS);

        private static final Cue[] VALUES = values();
        private final int channels;

        Cue(int channels) { this.channels = channels; }

        public static Cue fromId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
    }

    private ExecutionDeviceEffects() { }

    public static void emit(World world, Cue cue, Vec3 origin, float yaw, EntityLivingBase occupant) {
        Values config = ExecutionEffectsConfig.settings;
        if(world.isRemote || !config.enabled || config.intensity <= 0F) return;

        int channels = config.maxParticles > 0 ? cue.channels & ORDINARY : 0;
        if((cue.channels & ~ORDINARY) != 0 && producesBlood(occupant, config)) {
            if(config.maxParticles > 0 && config.blood) channels |= cue.channels & BLOOD;
            if(config.maxParticles > 0 && config.debris) channels |= cue.channels & DEBRIS;
            if(config.maxDecals > 0 && config.decals) channels |= cue.channels & DECALS;
        }
        if(channels == 0) return;

        float size = MathHelper.clamp_float(occupant.width * occupant.height / (0.6F * 1.8F), 0.5F, 2F);
        int dimension = world.provider.dimensionId;
        int rotation = Math.round(yaw / 90F) & 3;
        PacketDispatcher.wrapper.sendToAllAround(new ExecutionEffectPacket(cue, dimension, origin, rotation, channels, size * config.intensity, config),
                new TargetPoint(dimension, origin.xCoord, origin.yCoord, origin.zCoord, config.distance));
    }

    private static boolean producesBlood(EntityLivingBase entity, Values config) {
        if(entity instanceof EntityGolem || entity instanceof EntityCyberCrab || entity instanceof EntityBOTPrimeBase
                || entity instanceof EntityUFO || entity instanceof EntityUFOBase || entity instanceof EntityUAP
                || entity instanceof EntityGiantMagmaGolem || entity instanceof EntityGhost || entity instanceof EntitySkeleton
                || entity instanceof EntityWither || entity instanceof EntityBlaze) return false;
        if(config.nonBiologicalTypes.isEmpty()) return true;
        if(config.nonBiologicalTypes.contains(EntityList.getEntityString(entity))) return false;
        EntityRegistration registration = EntityRegistry.instance().lookupModSpawn(entity.getClass(), false);
        return registration == null || !config.nonBiologicalTypes.contains(registration.getContainer().getModId() + ":" + registration.getEntityName());
    }
}
