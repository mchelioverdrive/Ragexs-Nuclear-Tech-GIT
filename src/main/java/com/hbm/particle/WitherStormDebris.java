package com.hbm.particle;

import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/** Client-only cubes in the vanilla terrain-atlas particle batch; no per-cube draw calls. */
@SideOnly(Side.CLIENT)
public final class WitherStormDebris extends EntityFX {
    private static java.lang.ref.WeakReference<World> currentWorld = new java.lang.ref.WeakReference<World>(null);
    private static int active;
    // Eight shared cube corners, indexed by X/Y/Z bits, with outward-wound faces in vanilla side order.
    private static final int[][] FACES = {{4, 0, 1, 5}, {2, 6, 7, 3}, {1, 0, 2, 3}, {4, 5, 7, 6}, {0, 4, 6, 2}, {5, 1, 3, 7}};
    private static final float[] SHADE = {0.5F, 1F, 0.8F, 0.8F, 0.6F, 0.6F};
    private final EntityWitherStorm boss;
    private final double startX, startY, startZ, awayX, awayZ, spin, phase;
    private final float[] u0 = new float[6], u1 = new float[6], v0 = new float[6], v1 = new float[6];
    private final double[] vertices = new double[24];
    private final float size;
    private final int tint, light;
    private boolean counted = true;
    private WitherStormDebris(World world, EntityWitherStorm boss, int x, int y, int z, Block block, int meta) {
        super(world, x + 0.5D, y + 0.5D, z + 0.5D);
        this.boss = boss; startX = posX; startY = posY; startZ = posZ;
        double dx = startX - boss.posX, dz = startZ - boss.posZ, length = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
        awayX = dx / length; awayZ = dz / length;
        spin = (rand.nextBoolean() ? 1 : -1) * (0.06D + rand.nextDouble() * 0.1D); phase = rand.nextDouble() * Math.PI * 2D;
        // Unsupported/non-cube renderers use a small textured chunk, never a tile renderer or item hook.
        float extent = 0.45F; int color = 0xFFFFFF;
        try { extent = block.renderAsNormalBlock() ? 0.9F : 0.45F; color = block.getRenderColor(meta); } catch(RuntimeException unsupported) { }
        size = extent * (0.85F + rand.nextFloat() * 0.25F); tint = color;
        for(int side = 0; side < 6; side++) {
            IIcon icon = null;
            try { icon = block.getIcon(side, meta); } catch(RuntimeException unsupported) { }
            if(icon == null) icon = Blocks.stone.getIcon(side, 0);
            u0[side] = icon.getMinU(); u1[side] = icon.getMaxU(); v0[side] = icon.getMinV(); v1[side] = icon.getMaxV();
        }
        // Cache light once, only if the source chunk is present on this client.
        light = WitherStormMath.loaded(world, x, z) ? world.getLightBrightnessForSkyBlocks(x, y, z, 0) : 0;
        particleMaxAge = 35 + rand.nextInt(15); noClip = true;
        active++;
    }
    public static void accept(World world, int id, int[] records) {
        Minecraft mc = Minecraft.getMinecraft();
        if(currentWorld.get() != world) { currentWorld = new java.lang.ref.WeakReference<World>(world); active = 0; }
        if(mc.gameSettings.particleSetting == 2) return;
        Entity e = world.getEntityByID(id);
        if(!(e instanceof EntityWitherStorm) || mc.renderViewEntity == null || mc.renderViewEntity.getDistanceSqToEntity(e) > 128D * 128D) return;
        EntityWitherStorm boss = (EntityWitherStorm)e;
        int stride = Math.max(1, 4 - (int)(boss.development() * 3F)) * (mc.gameSettings.particleSetting == 1 ? 2 : 1);
        for(int i = 0; i + 4 < records.length && active < WitherStormConfig.maxDebris; i += 5) {
            if(Math.floorMod(records[i] * 31 + records[i + 1] * 17 + records[i + 2], stride) != 0) continue;
            Block b = Block.getBlockById(records[i + 3]);
            if(b != null && b != Blocks.air) mc.effectRenderer.addEffect(new WitherStormDebris(world, boss, records[i], records[i + 1], records[i + 2], b, records[i + 4]));
        }
    }
    @Override public int getFXLayer() { return 1; }
    @Override public int getBrightnessForRender(float partial) { return light; }
    @Override public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        if(++particleAge >= particleMaxAge || boss.isDead || Minecraft.getMinecraft().theWorld != worldObj) { setDead(); return; }
        double t = (double)particleAge / particleMaxAge, ease = t * t;
        double burst = Math.sin(t * Math.PI) * (1D - t) * 4D;
        double curve = Math.sin(t * Math.PI) * Math.sin(t * 5D + phase) * 2.5D;
        double tx = boss.posX, ty = boss.posY + (1.35D + boss.development() * 6D) * boss.getStormScale(), tz = boss.posZ;
        setPosition(startX + (tx - startX) * ease + awayX * burst - awayZ * curve,
            startY + (ty - startY) * ease + Math.sin(t * Math.PI) * 3D + burst,
            startZ + (tz - startZ) * ease + awayZ * burst + awayX * curve);
        if(t > 0.5D && getDistanceSq(tx, ty, tz) < 0.36D) setDead();
    }
    @Override public void renderParticle(Tessellator tess, float partial, float rx, float ry, float rz, float ux, float uz) {
        double age = particleAge + partial, t = age / particleMaxAge;
        double shrink = t > 0.85D ? Math.max(0D, (1D - t) / 0.15D) : 1D;
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
            tess.setColorOpaque_F(((tint >> 16) & 255) / 255F * shade, ((tint >> 8) & 255) / 255F * shade, (tint & 255) / 255F * shade);
            for(int corner = 0; corner < 4; corner++) {
                int v = FACES[face][corner] * 3;
                tess.addVertexWithUV(vertices[v], vertices[v + 1], vertices[v + 2], corner == 0 || corner == 3 ? u0[face] : u1[face], corner < 2 ? v1[face] : v0[face]);
            }
        }
    }
    @Override public void setDead() { super.setDead(); if(counted) { if(currentWorld.get() == worldObj) active = Math.max(0, active - 1); counted = false; } }
}
