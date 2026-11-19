package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.handler.disease.DeepRootDisease;
import com.hbm.handler.disease.DiseaseHost;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.IrisStatePacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** The controller's cosmic intelligence/presentation state, never an entity or another phase clock. */
public final class GeminiIris {
    private int visibility; // -1 hidden, 0 automatic, 1 forced (also bypasses rendering config)
    private int stage; // 0 automatic, 1..4 presentation only
    private long clock;
    private int observationWait, soundWait, revealWait, disturbanceWait;
    private int reveal, observation, observationLength, pulse, pulseLength, eventCooldown;
    private float gazeX, gazeY;

    public static GeminiIris get(World world) {
        GeminiWorldSavedData data = GeminiWorldSavedData.get(world);
        if(data == null) throw new net.minecraft.command.CommandException("The server Overworld is not available.");
        return data.iris;
    }

    public int visualStage(int phase) { return stage > 0 ? stage : visibility == 1 ? Math.max(1, phase) : phase; }

    /** Shared night visibility, independent of the viewer's camera yaw/pitch. */
    public static float night(World world, float partial) {
        return MathHelper.clamp_float(1F - (MathHelper.cos(world.getCelestialAngle(partial) * (float)Math.PI * 2F) * 2F + 0.25F), 0F, 1F);
    }

    public static float visibility(int stage, int override, boolean rendering, boolean active,
            boolean progressionVisibility, float night, float rain, float reveal) {
        if(override < 0) return 0F;
        if(override > 0) return 1F;
        if(!rendering || !active || stage < 1) return 0F;
        float day = stage >= 4 ? 0.30F : stage == 3 ? 0.12F : 0F;
        float value = progressionVisibility ? Math.max(day, night * (stage == 1 ? reveal * 0.55F : stage == 2 ? 0.8F : 1F)) : 1F;
        return value * (1F - rain * 0.8F);
    }

    private float visible(World world, int phase) {
        return visibility(visualStage(phase), visibility, GeminiConfig.irisRendering,
                stage > 0 || GeminiConfig.enabled && phase > 0, GeminiConfig.irisProgressionVisibility,
                night(world, 0), world.getRainStrength(0), Math.min(1F, reveal / 10F));
    }

    private static int interval(World world, int seconds, int phase) {
        int base = Math.max(15, seconds / Math.max(1, phase));
        return base + world.rand.nextInt(base + 1);
    }

    /** Once per server second, called by the existing invasion controller even when its gates pause. */
    static void tick(WorldServer world, GeminiWorldSavedData data) {
        GeminiIris iris = data.iris;
        int phase = data.getPhase();
        boolean active = GeminiInvasionManager.canActivityOccur(world) && !world.playerEntities.isEmpty();
        boolean debug = iris.visibility == 1 || iris.stage > 0 || iris.observation > 0;
        if(!world.playerEntities.isEmpty() && (active || debug)) {
            if(iris.clock == 0) {
                iris.observationWait = interval(world, GeminiConfig.irisObservationSeconds, phase);
                iris.soundWait = interval(world, GeminiConfig.irisAmbientSeconds, phase);
                iris.revealWait = interval(world, 600, 1);
                iris.disturbanceWait = interval(world, 900, phase);
            }
            iris.clock += 20L;
            if(iris.reveal > 0) iris.reveal--;
            if(iris.observation > 0) iris.observation--;
            if(iris.pulse > 0) iris.pulse--;
            if(iris.eventCooldown > 0) iris.eventCooldown--;
            if(active) {
                if(iris.revealWait > 0) iris.revealWait--;
                else {
                    iris.revealWait = interval(world, 600, 1);
                    if(night(world, 0) > 0.6F && world.getRainStrength(0) < 0.4F) iris.reveal = 45;
                }
                if(iris.observationWait > 0) iris.observationWait--;
                else {
                    iris.observationWait = interval(world, GeminiConfig.irisObservationSeconds, phase);
                    if(iris.observation == 0 && world.rand.nextBoolean()) iris.observe(world, false);
                }
                if(iris.disturbanceWait > 0) iris.disturbanceWait--;
                else {
                    iris.disturbanceWait = interval(world, 900, phase);
                    if(GeminiConfig.irisEffects) iris.startPulse(phase >= 3 ? 60 + world.rand.nextInt(61) : 12);
                }
                if(iris.soundWait > 0) iris.soundWait--;
                else {
                    iris.soundWait = interval(world, GeminiConfig.irisAmbientSeconds, phase);
                    if(GeminiConfig.irisEffects && (night(world, 0) > 0.5F || phase >= 3)) {
                        for(Object entry : world.playerEntities) {
                            EntityPlayer player = (EntityPlayer) entry;
                            if(player instanceof FakePlayer || !player.isEntityAlive()) continue;
                            float pressure = iris.pressure(player);
                            if(world.rand.nextFloat() < 0.25F + phase * 0.12F + pressure * 0.25F)
                                PacketDispatcher.wrapper.sendTo(new IrisStatePacket(iris.snapshot(world, player, true)), (EntityPlayerMP)player);
                        }
                    }
                }
            }
            data.markDirty();
        }
        sync(world); // also transmits live config changes, disabling and locked-world debug controls
    }

    private void startPulse(int seconds) {
        if(pulse == 0) { pulse = pulseLength = seconds; }
    }

    /** Major existing events share a saved two-minute throttle; no new spawn or block producer. */
    public static void invasionPulse(World world) {
        if(!GeminiConfig.irisEffects || !GeminiInvasionManager.canActivityOccur(world)) return;
        GeminiIris iris = get(world);
        if(iris.eventCooldown != 0) return;
        iris.eventCooldown = 120;
        iris.startPulse(15 + GeminiInvasionManager.getPhase(world) * 5);
        GeminiWorldSavedData.get(world).markDirty();
    }

    private float pressure(EntityPlayer player) {
        float value = GeminiPopulation.irisPressure(player);
        value += GardenerNests.irisPressure(player) + WretchCamps.irisPressure(player);
        if(GeminiConfig.deepRootEnabled && DiseaseHost.get(player).infections.containsKey(DeepRootDisease.ID)) value += 0.25F;
        return Math.min(1F, value);
    }

    private static boolean eligible(EntityPlayer player) {
        return player.dimension == 0 && player.isEntityAlive() && !player.capabilities.isCreativeMode
                && !(player instanceof FakePlayer);
    }

    /** Visibility check uses at most eight already-loaded cells along the fixed sky direction. */
    private boolean openSky(EntityPlayer player) {
        World world = player.worldObj;
        for(int step = 0; step <= 7; step++) {
            int x = MathHelper.floor_double(player.posX + step * 1.43D);
            int z = MathHelper.floor_double(player.posZ - step * 1.43D);
            int y = MathHelper.floor_double(player.posY + player.getEyeHeight() + step * 2D);
            if(y >= world.getActualHeight()) return true;
            if(!world.getChunkProvider().chunkExists(x >> 4, z >> 4) || !world.canBlockSeeTheSky(x, y, z)) return false;
        }
        return true;
    }

    public boolean observe(WorldServer world, boolean forced) {
        if(!forced && visible(world, GeminiInvasionManager.getPhase(world)) <= 0.15F) return false;
        EntityPlayer chosen = null;
        int count = 0;
        for(Object entry : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) entry;
            if(!eligible(player) || !forced && !openSky(player)) continue;
            if(world.rand.nextInt(++count) == 0) chosen = player;
        }
        if(chosen == null) return false;
        // One target region, one shared orientation. Bounded tangential motion, never camera tracking.
        gazeX = (float)Math.tanh((chosen.posX + chosen.posZ) / 4096D) * 0.065F;
        gazeY = (float)Math.tanh((chosen.posX - chosen.posZ) / 4096D) * 0.045F;
        observation = observationLength = GeminiConfig.irisObservationDurationSeconds;
        GeminiWorldSavedData.get(world).markDirty();
        sync(world);
        return true;
    }

    public void control(WorldServer world, String action, int value) {
        if("show".equals(action)) visibility = 1;
        else if("hide".equals(action)) visibility = -1;
        else if("stage".equals(action)) stage = value;
        else if("reset".equals(action)) { visibility = stage = observation = 0; gazeX = gazeY = 0; }
        GeminiWorldSavedData.get(world).markDirty();
        sync(world);
    }

    public String status(WorldServer world) {
        int phase = GeminiInvasionManager.getPhase(world);
        return "The Iris (Hungry Eye): invasion phase=" + phase + "; visual stage=" + visualStage(phase)
                + "; override=" + (visibility == 1 ? "shown" : visibility == -1 ? "hidden" : "automatic")
                + "; visibility=" + visible(world, phase) + "; observation=" + observation + "s; pulse=" + pulse
                + "s; direction=northeast sky (+X/-Z), elevation 45 degrees; Overworld only.";
    }

    private NBTTagCompound snapshot(WorldServer world, EntityPlayer player, boolean sound) {
        NBTTagCompound tag = new NBTTagCompound();
        int phase = GeminiInvasionManager.getPhase(world);
        tag.setInteger("phase", phase);
        tag.setInteger("stage", visualStage(phase));
        tag.setInteger("visibility", visibility);
        tag.setBoolean("active", stage > 0 || GeminiConfig.enabled && phase > 0);
        tag.setBoolean("rendering", GeminiConfig.irisRendering);
        tag.setBoolean("effects", GeminiConfig.irisEffects && GeminiConfig.enabled && phase > 0);
        tag.setBoolean("progression", GeminiConfig.irisProgressionVisibility);
        tag.setFloat("size", GeminiConfig.irisSize);
        tag.setFloat("speed", GeminiConfig.irisPupilSpeed);
        tag.setFloat("intensity", GeminiConfig.irisIntensity);
        tag.setInteger("minRenderChunks", GeminiConfig.irisMinRenderChunks);
        tag.setLong("clock", clock);
        tag.setInteger("reveal", reveal);
        tag.setInteger("observation", observation);
        tag.setInteger("observationLength", observationLength);
        tag.setFloat("gazeX", gazeX);
        tag.setFloat("gazeY", gazeY);
        tag.setInteger("pulse", pulse);
        tag.setInteger("pulseLength", pulseLength);
        tag.setFloat("pressure", pressure(player));
        tag.setBoolean("sound", sound);
        return tag;
    }

    public static void sync(World world) {
        WorldServer overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
        if(overworld == null) return;
        GeminiIris iris = get(overworld);
        for(Object entry : overworld.playerEntities) if(entry instanceof EntityPlayerMP && !(entry instanceof FakePlayer))
            PacketDispatcher.wrapper.sendTo(new IrisStatePacket(iris.snapshot(overworld, (EntityPlayer)entry, false)), (EntityPlayerMP)entry);
    }

    public void read(NBTTagCompound tag) {
        visibility = MathHelper.clamp_int(tag.getInteger("visibility"), -1, 1);
        stage = MathHelper.clamp_int(tag.getInteger("stage"), 0, 4);
        clock = Math.max(0L, tag.getLong("clock"));
        observationWait = bounded(tag, "observationWait", 172800);
        soundWait = bounded(tag, "soundWait", 172800);
        revealWait = bounded(tag, "revealWait", 1200);
        disturbanceWait = bounded(tag, "disturbanceWait", 1800);
        reveal = bounded(tag, "reveal", 45);
        observationLength = bounded(tag, "observationLength", 3600);
        observation = Math.min(observationLength, bounded(tag, "observation", 3600));
        gazeX = finite(tag.getFloat("gazeX"), 0.065F);
        gazeY = finite(tag.getFloat("gazeY"), 0.045F);
        pulseLength = bounded(tag, "pulseLength", 120);
        pulse = Math.min(pulseLength, bounded(tag, "pulse", 120));
        eventCooldown = bounded(tag, "eventCooldown", 120);
    }
    private static int bounded(NBTTagCompound tag, String key, int max) { return MathHelper.clamp_int(tag.getInteger(key), 0, max); }
    private static float finite(float value, float max) { return Float.isNaN(value) || Float.isInfinite(value) ? 0 : MathHelper.clamp_float(value, -max, max); }
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("visibility", visibility); tag.setInteger("stage", stage); tag.setLong("clock", clock);
        tag.setInteger("observationWait", observationWait); tag.setInteger("soundWait", soundWait);
        tag.setInteger("revealWait", revealWait); tag.setInteger("disturbanceWait", disturbanceWait);
        tag.setInteger("reveal", reveal); tag.setInteger("observation", observation);
        tag.setInteger("observationLength", observationLength); tag.setFloat("gazeX", gazeX); tag.setFloat("gazeY", gazeY);
        tag.setInteger("pulse", pulse); tag.setInteger("pulseLength", pulseLength); tag.setInteger("eventCooldown", eventCooldown);
        return tag;
    }
}
