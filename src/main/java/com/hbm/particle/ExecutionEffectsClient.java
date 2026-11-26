package com.hbm.particle;

import static com.hbm.handler.restraint.ExecutionDeviceEffects.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Random;

import com.hbm.config.ClientConfig;
import com.hbm.config.ExecutionEffectsConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityBlockDustFX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.init.Blocks;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Client-thread particle expansion, using the vanilla particle batches and lifecycle. */
@SideOnly(Side.CLIENT)
public final class ExecutionEffectsClient {
    private static final int PARTICLES_PER_TICK = ExecutionEffectsConfig.PARTICLE_LIMIT, DECAL_RAYS_PER_TICK = 24;
    private static final int[] FORWARD_X = {0, 1, 0, -1}, FORWARD_Z = {1, 0, -1, 0};
    private static final Deque<ExecutionBloodDecal> decals = new ArrayDeque<>();
    private static WorldClient currentWorld;
    private static int remainingParticles = PARTICLES_PER_TICK, remainingRays = DECAL_RAYS_PER_TICK;

    private enum Style {
        BLOOD_SPRAY(BLOOD), FRAGMENT(DEBRIS), WOOD_DUST(ORDINARY),
        SMOKE(ORDINARY), HEAT(ORDINARY), FIRE(ORDINARY), SPARK(ORDINARY), PINPRICK(ORDINARY), CRIT(ORDINARY);

        private final int channel;
        Style(int channel) { this.channel = channel; }
    }

    private static final class Emitter {
        final Style style;
        final int count;
        final double x, y, z, forward, lift, spread;

        Emitter(Style style, int count, double x, double y, double z, double forward, double lift, double spread) {
            this.style = style;
            this.count = count;
            this.x = x;
            this.y = y;
            this.z = z;
            this.forward = forward;
            this.lift = lift;
            this.spread = spread;
        }
    }

    // Offsets and forward velocities are in the device's local coordinates.
    private static final EnumMap<Cue, Emitter[]> EMITTERS = new EnumMap<>(Cue.class);
    static {
        add(Cue.BLADE_DEATH, new Emitter(Style.BLOOD_SPRAY, 24, 0, 0, 0, 0.18, 0.16, 0.18));
        add(Cue.BULL_HEAT, new Emitter(Style.SMOKE, 2, 0.875, 3.46, -0.375, 0, 0.07, 0.06),
            new Emitter(Style.HEAT, 2, 0, 0.37, 0.25, 0.02, 0.06, 0.12),
            new Emitter(Style.FIRE, 2, 0, 0.17, 0.25, 0, 0.02, 0.15));
        add(Cue.CHAIR_DISCHARGE, new Emitter(Style.SPARK, 5, 0, 0.34, 0, 0.04, 0.12, 0.26),
            new Emitter(Style.SMOKE, 1, 0.36, -0.44, -0.45, 0, 0.05, 0.06));
        add(Cue.INJECTION, new Emitter(Style.PINPRICK, 3, 0, 0, 0, -0.02, 0.035, 0.055));
        add(Cue.TRAPDOOR, new Emitter(Style.WOOD_DUST, 4, 0, 0, 0, 0, 0.04, 0.16));
        add(Cue.CHIPPER_HIT, new Emitter(Style.CRIT, 3, 0, 1.31, -0.5725, 0, 0.15, 0.16),
            new Emitter(Style.BLOOD_SPRAY, 7, 0, 0, 0, 0.30, 0.10, 0.11));
        add(Cue.CHIPPER_DEATH, new Emitter(Style.CRIT, 3, 0, 1.31, -0.5725, 0, 0.15, 0.16),
            new Emitter(Style.BLOOD_SPRAY, 28, 0, 0, 0, 0.37, 0.16, 0.15),
            new Emitter(Style.FRAGMENT, 12, 0, 0, 0, 0.32, 0.20, 0.13));
    }

    private ExecutionEffectsClient() { }

    private static void add(Cue cue, Emitter... emitters) { EMITTERS.put(cue, emitters); }

    private static void selectWorld(WorldClient world) {
        if(currentWorld == world) return;
        decals.clear();
        currentWorld = world;
        remainingParticles = PARTICLES_PER_TICK;
        remainingRays = DECAL_RAYS_PER_TICK;
    }

    public static void tick(Minecraft mc) {
        if(mc.theWorld == null && currentWorld != null && mc.effectRenderer != null) {
            // A dimension change clears the vanilla batches. Disconnecting to the menu does not.
            mc.effectRenderer.clearEffects(null);
        }
        selectWorld(mc.theWorld);
        if(!decals.isEmpty()) decals.removeIf(decal -> decal.isDead);
        remainingParticles = PARTICLES_PER_TICK;
        remainingRays = DECAL_RAYS_PER_TICK;
    }

    public static void emit(Minecraft mc, WorldClient world, Cue cue, double x, double y, double z, int rotation,
            int channels, float intensity, int maxParticles, int maxDecals, int decalTicks, int distance) {
        if(mc.renderViewEntity == null || distance < 1 || distance > ExecutionEffectsConfig.DISTANCE_LIMIT
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(intensity) || intensity <= 0F || intensity > 8F) return;
        int distanceSq = distance * distance;
        if(mc.renderViewEntity.getDistanceSq(x, y, z) > distanceSq) return;
        if(!ClientConfig.EXECUTION_DEVICE_GORE.get()) channels &= ORDINARY;
        if(channels == 0) return;

        selectWorld(world);
        Random random = world.rand;
        int forwardX = FORWARD_X[rotation], forwardZ = FORWARD_Z[rotation];
        float density = intensity / (1 + mc.gameSettings.particleSetting);
        int limit = Math.min(remainingParticles, Math.min(maxParticles, ExecutionEffectsConfig.PARTICLE_LIMIT));
        Emitter[] emitters = EMITTERS.get(cue);
        int requested = 0;
        for(Emitter emitter : emitters) if((channels & emitter.style.channel) != 0) requested += emitter.count;
        if(limit > 0 && requested > 0) {
            float multiplier = Math.min(density, (float) limit / requested);
            int processed = 0, spawned = 0;
            for(Emitter emitter : emitters) {
                if((channels & emitter.style.channel) == 0) continue;
                processed += emitter.count;
                int target = Math.round(processed * multiplier);
                for(; spawned < target; spawned++) {
                    double side = (random.nextDouble() - 0.5) * emitter.spread;
                    double forward = (random.nextDouble() - 0.5) * emitter.spread;
                    double px = x + forwardZ * (emitter.x + side) + forwardX * (emitter.z + forward);
                    double py = y + emitter.y + (random.nextDouble() - 0.5) * emitter.spread;
                    double pz = z - forwardX * (emitter.x + side) + forwardZ * (emitter.z + forward);
                    double vx = (random.nextDouble() * 2 - 1) * emitter.spread;
                    double vy = emitter.lift + (random.nextDouble() - 0.5) * emitter.spread;
                    double vz = emitter.forward * (0.65 + random.nextDouble() * 0.7) + (random.nextDouble() - 0.5) * emitter.spread;
                    mc.effectRenderer.addEffect(particle(world, emitter.style, px, py, pz, forwardZ * vx + forwardX * vz, vy, -forwardX * vx + forwardZ * vz));
                }
            }
            remainingParticles -= spawned;
        }
        if((channels & DECALS) != 0 && maxDecals > 0 && decalTicks > 0 && remainingRays > 0)
            placeDecals(mc, world, cue, x, y, z, forwardX, forwardZ, density, maxDecals, decalTicks, distanceSq);
    }

    private static void placeDecals(Minecraft mc, WorldClient world, Cue cue, double x, double y, double z, int forwardX, int forwardZ,
            float density, int maxDecals, int decalTicks, int distanceSq) {
        int count = Math.min(3, Math.round(3 * density));
        int limit = Math.min(maxDecals, ExecutionEffectsConfig.DECAL_LIMIT);
        int lifetime = Math.min(decalTicks, ExecutionEffectsConfig.DECAL_TICK_LIMIT);
        Random random = world.rand;
        for(int attempts = count * 2; attempts > 0 && remainingRays > 0; attempts--) {
            remainingRays--;
            double forward = cue == Cue.BLADE_DEATH ? (random.nextDouble() - 0.25) * 3.0 : 0.7 + random.nextDouble() * 2.8;
            double sideways = (random.nextDouble() - 0.5) * 3.4;
            Vec3 start = Vec3.createVectorHelper(x, y, z);
            Vec3 end = start.addVector(forwardX * forward + forwardZ * sideways, -0.4 - random.nextDouble() * 2.8,
                    forwardZ * forward - forwardX * sideways);
            ExecutionBloodDecal decal = ExecutionBloodDecal.project(world, start, end, lifetime, distanceSq);
            if(decal == null) continue;
            while(decals.size() >= limit) decals.removeFirst().setDead();
            decals.addLast(decal);
            mc.effectRenderer.addEffect(decal);
            if(--count == 0) break;
        }
    }

    private static EntityFX particle(World world, Style style, double x, double y, double z, double vx, double vy, double vz) {
        switch(style) {
            case FIRE: return new EntityFlameFX(world, x, y, z, vx, vy, vz);
            case CRIT: return new EntityCritFX(world, x, y, z, vx, vy, vz);
            case BLOOD_SPRAY:
            case FRAGMENT:
            case WOOD_DUST: return new Dust(world, style, x, y, z, vx, vy, vz);
            default: return new AtlasParticle(world, style, x, y, z, vx, vy, vz);
        }
    }

    private static final class Dust extends EntityBlockDustFX {
        private final boolean gore;

        Dust(World world, Style style, double x, double y, double z, double vx, double vy, double vz) {
            super(world, x, y, z, vx, vy, vz, dustBlock(style), style == Style.FRAGMENT ? 14 : 0);
            gore = style != Style.WOOD_DUST;
            motionX = vx;
            motionY = vy;
            motionZ = vz;
            noClip = false;
            particleScale = (style == Style.FRAGMENT ? 1.05F : 0.48F) * (0.7F + rand.nextFloat() * 0.7F);
            particleGravity = style == Style.FRAGMENT ? 1F : 0.65F;
            particleMaxAge = (style == Style.FRAGMENT ? 24 : 11) + rand.nextInt(9);
            if(gore) setRBGColorF(0.65F + rand.nextFloat() * 0.25F, 0.45F, 0.45F);
        }

        private static Block dustBlock(Style style) {
            if(style == Style.BLOOD_SPRAY) return Blocks.redstone_block;
            return style == Style.FRAGMENT ? Blocks.stained_hardened_clay : Blocks.planks;
        }

        @Override public void onUpdate() {
            if(gore && !ClientConfig.EXECUTION_DEVICE_GORE.get()) setDead();
            else super.onUpdate();
        }
    }

    private static final class AtlasParticle extends EntityFX {
        private final boolean smoke, bright;

        AtlasParticle(World world, Style style, double x, double y, double z, double vx, double vy, double vz) {
            super(world, x, y, z);
            smoke = style == Style.SMOKE || style == Style.HEAT;
            bright = style == Style.SPARK || style == Style.HEAT;
            motionX = vx;
            motionY = vy;
            motionZ = vz;
            noClip = true;
            particleMaxAge = (style == Style.SMOKE ? 19 : smoke ? 10 : 5) + rand.nextInt(6);
            particleScale = (style == Style.SMOKE ? 1.1F : smoke ? 0.72F : 0.48F) * (0.75F + rand.nextFloat() * 0.5F);
            particleGravity = smoke ? -0.035F : 0.22F;
            setParticleTextureIndex(smoke ? 7 : 0);
            switch(style) {
                case SMOKE: setRBGColorF(0.31F, 0.29F, 0.28F); break;
                case HEAT: setRBGColorF(1F, 0.49F, 0.17F); break;
                case SPARK: setRBGColorF(0.45F, 0.77F, 1F); break;
                default: setRBGColorF(0.76F, 0.86F, 0.81F);
            }
            setAlphaF(smoke ? 0.7F : 1F);
        }

        @Override public void onUpdate() {
            super.onUpdate();
            float progress = Math.min(1F, (float) particleAge / particleMaxAge);
            if(smoke) particleScale += 0.021F;
            setAlphaF((smoke ? 0.7F : 1F) * (1F - progress));
        }

        @Override public int getBrightnessForRender(float partial) {
            return bright ? 15728880 : super.getBrightnessForRender(partial);
        }
    }
}
