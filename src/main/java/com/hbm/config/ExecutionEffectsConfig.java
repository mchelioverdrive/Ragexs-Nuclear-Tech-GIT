package com.hbm.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

public final class ExecutionEffectsConfig {
    private static final String CATEGORY = RestraintConfig.CATEGORY + ".executioneffects";
    public static final int PARTICLE_LIMIT = 128, DECAL_LIMIT = 512, DECAL_TICK_LIMIT = 7200, DISTANCE_LIMIT = 128;
    public static volatile Values settings = new Values(new Configuration());

    private ExecutionEffectsConfig() { }

    public static final class Values {
        public final boolean enabled, blood, debris, decals;
        public final float intensity;
        public final int maxParticles, maxDecals, decalTicks, distance;
        public final Set<String> nonBiologicalTypes;

        public Values(Configuration config) {
            config.setCategoryComment(CATEGORY, "Visual effects only. Supports /rntconfig reload.");
            enabled = config.get(CATEGORY, "enableExecutionDeviceEffects", true, "Send execution device visual effects to nearby clients.").getBoolean(true);
            blood = config.get(CATEGORY, "enableBloodParticles", true, "Show stylized blood particles for eligible occupants.").getBoolean(true);
            debris = config.get(CATEGORY, "enableDebrisParticles", true, "Show stylized debris particles for eligible occupants.").getBoolean(true);
            decals = config.get(CATEGORY, "enableBloodDecals", true, "Allow temporary blood marks on exposed full blocks.").getBoolean(true);
            double value = config.get(CATEGORY, "particleIntensityMultiplier", 1D, "Scale the number of particles and marks. Zero disables emission.", 0D, 4D).getDouble(1D);
            intensity = Double.isFinite(value) ? (float) Math.max(0D, Math.min(4D, value)) : 1F;
            maxParticles = config.getInt("maxParticlesPerEvent", CATEGORY, 64, 0, PARTICLE_LIMIT, "Maximum particles per device effect event.");
            maxDecals = config.getInt("maxActiveDecalsPerClient", CATEGORY, 128, 0, DECAL_LIMIT, "Maximum active execution blood marks on each client.");
            decalTicks = config.getInt("bloodDecalLifetime", CATEGORY, 1200, 1, DECAL_TICK_LIMIT, "Blood mark lifetime in client ticks.");
            distance = config.getInt("maxEffectRenderingDistance", CATEGORY, 64, 1, DISTANCE_LIMIT, "Maximum distance in blocks for execution effects.");
            nonBiologicalTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(config.get(CATEGORY, "nonBiologicalEntityTypes",
                    new String[0], "Additional entities that produce no blood or debris. Use vanilla entity names or Forge names such as modid:entityName.").getStringList())));
        }
    }
}
