package com.hbm.particle;

import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormDestroyerPose;
import com.hbm.entity.mob.witherstorm.WitherStormDevourerPose;
import com.hbm.entity.mob.witherstorm.WitherStormBigDevourerPose;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import com.hbm.entity.mob.witherstorm.WitherStormStage;
import com.hbm.entity.mob.witherstorm.WitherStormThunderstormPose;
import com.hbm.render.entity.mob.WitherStormVortex;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only cubes in the vanilla terrain-atlas particle batch; no per-cube draw calls. */
@SideOnly(Side.CLIENT)
public final class WitherStormDebris extends EntityFX {
    private static java.lang.ref.WeakReference<World> currentWorld = new java.lang.ref.WeakReference<World>(null);
    private static int active;
    private static final int MAX_DESTROYER_DEBRIS = 32;
    private static final Map<EntityWitherStorm, DestroyerDebris> destroyers = new WeakHashMap<EntityWitherStorm, DestroyerDebris>();
    private static final Block[] AMBIENT_BLOCKS = {Blocks.stone, Blocks.dirt, Blocks.gravel, Blocks.cobblestone};
    // Eight shared cube corners, indexed by X/Y/Z bits, with outward-wound faces in vanilla side order.
    private static final int[][] FACES = {{4, 0, 1, 5}, {2, 6, 7, 3}, {1, 0, 2, 3}, {4, 5, 7, 6}, {0, 4, 6, 2}, {5, 1, 3, 7}};
    private static final float[] SHADE = {0.5F, 1F, 0.8F, 0.8F, 0.6F, 0.6F};
    private final EntityWitherStorm boss;
    private final double startX, startY, startZ, spin, phase;
    private final float[] u0 = new float[6], u1 = new float[6], v0 = new float[6], v1 = new float[6];
    private final double[] vertices = new double[24];
    private final float size;
    private final int tint, light;
    private final DestroyerDebris destroyer;
    private final boolean thunderstorm;
    private final int vortexRing;
    private final double angle, sweep, hoverLift;
    private final double[] crown = new double[4];
    private boolean counted = true;
    private WitherStormDebris(World world, EntityWitherStorm boss, double x, double y, double z, Block block, int meta,
                              DestroyerDebris destroyer) {
        super(world, x, y, z);
        this.boss = boss; startX = posX; startY = posY; startZ = posZ;
        this.destroyer = destroyer;
        thunderstorm = boss.getStage() == WitherStormStage.D;
        vortexRing = rand.nextInt(2);
        spin = (rand.nextBoolean() ? 1 : -1) * (0.035D + rand.nextDouble() * 0.065D);
        phase = rand.nextDouble() * Math.PI * 2D;
        angle = phase;
        sweep = Math.copySign(0.8D + rand.nextDouble() * 0.7D, spin);
        hoverLift = rand.nextDouble() * 3D;
        // Lift, hover/corrupt, then sink. This effect never changes world blocks or growth.
        particleMaxAge = 88 + rand.nextInt(25);
        if(thunderstorm) particleMaxAge = 180 + rand.nextInt(41);
        if(destroyer != null) {
            destroyer.active++;
            destroyer.lastBirth = boss.ticksExisted;
        }
        // Unsupported/non-cube renderers use a small textured chunk, never a tile renderer or item hook.
        float extent = 0.45F; int color = 0xFFFFFF;
        try { extent = block.renderAsNormalBlock() ? 0.9F : 0.45F; color = block.getRenderColor(meta); } catch(RuntimeException unsupported) { }
        size = extent * (destroyer != null ? 0.45F + rand.nextFloat() * 0.85F : 0.85F + rand.nextFloat() * 0.25F);
        tint = color;
        for(int side = 0; side < 6; side++) {
            IIcon icon = null;
            try { icon = block.getIcon(side, meta); } catch(RuntimeException unsupported) { }
            if(icon == null) icon = Blocks.stone.getIcon(side, 0);
            u0[side] = icon.getMinU(); u1[side] = icon.getMaxU(); v0[side] = icon.getMinV(); v1[side] = icon.getMaxV();
        }
        // Cache light once, only if the source chunk is present on this client.
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        light = WitherStormMath.loaded(world, bx, bz) ? world.getLightBrightnessForSkyBlocks(bx, by, bz, 0) : 0;
        noClip = true;
        active++;
    }

    private static final class DestroyerDebris {
        int active, lastBirth = -8;
    }

    private static DestroyerDebris destroyerDebris(EntityWitherStorm boss) {
        DestroyerDebris debris = destroyers.get(boss);
        if(debris == null) {
            debris = new DestroyerDebris();
            destroyers.put(boss, debris);
        }
        return debris;
    }

    private static void trackWorld(World world) {
        if(currentWorld.get() == world) return;
        currentWorld = new java.lang.ref.WeakReference<World>(world);
        active = 0;
        destroyers.clear();
    }

    private static double mantleScale(EntityWitherStorm boss) {
        return WitherStormDestroyerPose.size(boss.development()) * boss.getStormScale();
    }

    /** Keep a sparse stream around the Destroyer when no real terrain records arrive. */
    public static void fillDestroyer(EntityWitherStorm boss) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = boss.worldObj;
        trackWorld(world);
        int setting = mc.gameSettings.particleSetting;
        if(setting == 2 || active >= WitherStormConfig.maxDebris || !boss.isEntityAlive()) return;
        DestroyerDebris debris = destroyerDebris(boss);
        if(debris.active >= MAX_DESTROYER_DEBRIS || boss.ticksExisted - debris.lastBirth < (setting == 0 ? 3 : 6)) return;
        double scale = mantleScale(boss);
        boolean low = world.rand.nextInt(3) != 0;
        double radius = (10.5D + world.rand.nextDouble() * 6.5D) * scale;
        // Spread debris along both sides so it reads against the silhouette from the front.
        // Keep a smaller stream behind the crown and leave the space in front of the eyes open.
        double flank = !low && world.rand.nextInt(3) == 0 ? Math.PI * 0.5D : world.rand.nextBoolean() ? 0D : Math.PI;
        double angle = flank + (world.rand.nextDouble() - 0.5D) * Math.PI * 0.60D + Math.toRadians(boss.rotationYaw);
        double y = (low ? -6D + world.rand.nextDouble() * 8D : 6D + world.rand.nextDouble() * 10D) * scale;
        Block block = AMBIENT_BLOCKS[world.rand.nextInt(AMBIENT_BLOCKS.length)];
        mc.effectRenderer.addEffect(new WitherStormDebris(world, boss, boss.posX + Math.cos(angle) * radius,
            boss.posY + y, boss.posZ + Math.sin(angle) * radius, block, 0, debris));
    }

    public static void accept(World world, int id, int[] records) {
        Minecraft mc = Minecraft.getMinecraft();
        trackWorld(world);
        if(mc.gameSettings.particleSetting == 2) return;
        Entity e = world.getEntityByID(id);
        if(!(e instanceof EntityWitherStorm) || mc.renderViewEntity == null || mc.renderViewEntity.getDistanceSqToEntity(e) > 128D * 128D) return;
        EntityWitherStorm boss = (EntityWitherStorm)e;
        if(records.length >= 5) boss.capturedBlocksVisual();
        DestroyerDebris debris = boss.getStage() == WitherStormStage.B ? destroyerDebris(boss) : null;
        int spawned = 0;
        int stride = Math.max(1, 4 - (int)(boss.development() * 3F)) * (mc.gameSettings.particleSetting == 1 ? 2 : 1);
        for(int i = 0; i + 4 < records.length && active < WitherStormConfig.maxDebris; i += 5) {
            if(debris != null && (debris.active >= MAX_DESTROYER_DEBRIS || spawned >= (mc.gameSettings.particleSetting == 0 ? 3 : 2))) break;
            if(Math.floorMod(records[i] * 31 + records[i + 1] * 17 + records[i + 2], stride) != 0) continue;
            Block b = Block.getBlockById(records[i + 3]);
            if(b != null && b != Blocks.air) {
                mc.effectRenderer.addEffect(new WitherStormDebris(world, boss, records[i] + 0.5D,
                    records[i + 1] + 0.5D, records[i + 2] + 0.5D, b, records[i + 4], debris));
                spawned++;
            }
        }
    }
    @Override public int getFXLayer() { return 1; }
    @Override public int getBrightnessForRender(float partial) { return light; }
    @Override public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        if(++particleAge >= particleMaxAge || boss.isDead || Minecraft.getMinecraft().theWorld != worldObj) { setDead(); return; }
        if(worldObj.getEntityByID(boss.getEntityId()) != boss) { setDead(); return; }
        double t = (double)particleAge / particleMaxAge;
        if(thunderstorm) {
            if(boss.getStage() != WitherStormStage.D || !boss.isEntityAlive()) { setDead(); return; }
            vortexPosition(t);
            return;
        }
        upperBody();
        double drift = smooth((t - 0.43D) / 0.35D);
        double bearing = angle + sweep * drift;
        double orbit = crown[3] * (1D + 0.04D * Math.sin(phase + drift * 5D));
        double sink = smooth((t - 0.78D) / 0.22D);
        double localX = crown[0] + Math.cos(bearing) * orbit * (1D - sink);
        double localY = crown[1] + hoverLift + Math.sin(phase + drift * 4D) * 0.8D - sink * (5D + hoverLift);
        double localZ = crown[2] + Math.sin(bearing) * orbit * 0.85D * (1D - sink);
        double scale = boss.getStormScale(), yaw = Math.toRadians(boss.rotationYaw);
        double tx = boss.posX + (localX * Math.cos(yaw) - localZ * Math.sin(yaw)) * scale;
        double ty = boss.posY + localY * scale;
        double tz = boss.posZ + (localX * Math.sin(yaw) + localZ * Math.cos(yaw)) * scale;
        double lift = smooth(t / 0.43D);
        setPosition(startX + (tx - startX) * lift, startY + (ty - startY) * lift, startZ + (tz - startZ) * lift);
    }

    private void vortexPosition(double t) {
        double time = worldObj.getTotalWorldTime();
        WitherStormVortex.orbit(vortexRing, angle + time * WitherStormVortex.speed(vortexRing), hoverLift - 1.5D, time, crown);
        double sink = smooth((t - 0.72D) / 0.28D);
        double yaw = Math.toRadians(boss.rotationYaw);
        double scale = boss.getStormScale() * WitherStormThunderstormPose.size(boss.development());
        // Match the visible world-axis rings, then end inside the actual rotating upper mantle.
        double innerX = -3D * Math.cos(yaw) - 10D * Math.sin(yaw);
        double innerZ = -3D * Math.sin(yaw) + 10D * Math.cos(yaw);
        double tx = boss.posX + (crown[0] + (innerX - crown[0]) * sink) * scale;
        double ty = boss.posY + (crown[1] + (33D - crown[1]) * sink) * scale;
        double tz = boss.posZ + (crown[2] + (innerZ - crown[2]) * sink) * scale;
        double lift = smooth(t / 0.32D);
        setPosition(startX + (tx - startX) * lift, startY + (ty - startY) * lift, startZ + (tz - startZ) * lift);
    }

    private static double smooth(double t) {
        t = Math.max(0D, Math.min(1D, t));
        return t * t * (3D - 2D * t);
    }

    /** Local upper-mantle orbit and radius; the final destination is inside the mantle. */
    private void upperBody() {
        double s;
        switch(boss.getStage()) {
            case C_BIG:
                s = WitherStormBigDevourerPose.size(boss.development());
                crown[0] = -2D * s; crown[1] = 36D * s; crown[2] = 14D * s; crown[3] = 19D * s;
                break;
            case C_SMALL:
                s = WitherStormDevourerPose.size(boss.development());
                crown[0] = -2D * s; crown[1] = 25D * s; crown[2] = 8D * s; crown[3] = 14D * s;
                break;
            case B:
                s = WitherStormDestroyerPose.size(boss.development());
                crown[0] = 0D; crown[1] = 13D * s; crown[2] = 2D * s; crown[3] = 12D * s;
                break;
            default:
                double d = boss.development();
                crown[0] = -d * 2D; crown[1] = 3D + d * 18D; crown[2] = d * 1.5D; crown[3] = 2D + d * 14D;
                break;
        }
    }
    @Override public void renderParticle(Tessellator tess, float partial, float rx, float ry, float rz, float ux, float uz) {
        double age = particleAge + partial, t = age / particleMaxAge;
        double shrink = 1D - smooth((t - 0.88D) / 0.12D);
        float corruption = (float)smooth((t - (thunderstorm ? 0.20D : 0.43D)) / 0.30D);
        double a = phase + age * spin, b = phase * 0.7D + age * spin * 0.63D;
        double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
        double px = prevPosX + (posX - prevPosX) * partial - interpPosX;
        double py = prevPosY + (posY - prevPosY) * partial - interpPosY;
        double pz = prevPosZ + (posZ - prevPosZ) * partial - interpPosZ;
        for(int i = 0; i < 8; i++) {
            double x = ((i & 1) - 0.5D) * size * shrink;
            double y = (((i >> 1) & 1) - 0.5D) * size * shrink;
            double z = (((i >> 2) & 1) - 0.5D) * size * shrink;
            double yy = y * cb - z * sb, zz = y * sb + z * cb;
            vertices[i * 3] = px + x * ca + zz * sa;
            vertices[i * 3 + 1] = py + yy;
            vertices[i * 3 + 2] = pz - x * sa + zz * ca;
        }
        tess.setBrightness(light);
        for(int face = 0; face < 6; face++) {
            // The shared particle batch has depth writes disabled. Cull the cube's back
            // faces here so its rear textures never paint over its visible front faces.
            int a0 = FACES[face][0] * 3, a1 = FACES[face][1] * 3, a2 = FACES[face][2] * 3;
            double ex = vertices[a1] - vertices[a0], ey = vertices[a1 + 1] - vertices[a0 + 1], ez = vertices[a1 + 2] - vertices[a0 + 2];
            double fx = vertices[a2] - vertices[a0], fy = vertices[a2 + 1] - vertices[a0 + 1], fz = vertices[a2 + 2] - vertices[a0 + 2];
            if((ey * fz - ez * fy) * vertices[a0] + (ez * fx - ex * fz) * vertices[a0 + 1] + (ex * fy - ey * fx) * vertices[a0 + 2] >= 0D) continue;
            float shade = SHADE[face];
            float material = 1F - corruption * 0.96F;
            tess.setColorOpaque_F((((tint >> 16) & 255) / 255F * material + corruption * 0.025F) * shade,
                (((tint >> 8) & 255) / 255F * material + corruption * 0.012F) * shade,
                ((tint & 255) / 255F * material + corruption * 0.045F) * shade);
            for(int corner = 0; corner < 4; corner++) {
                int v = FACES[face][corner] * 3;
                tess.addVertexWithUV(vertices[v], vertices[v + 1], vertices[v + 2], corner == 0 || corner == 3 ? u0[face] : u1[face], corner < 2 ? v1[face] : v0[face]);
            }
        }
    }
    @Override public void setDead() {
        super.setDead();
        if(!counted) return;
        if(currentWorld.get() == worldObj) active = Math.max(0, active - 1);
        if(destroyer != null) destroyer.active--;
        counted = false;
    }
}
