package com.hbm.particle;

import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

@SideOnly(Side.CLIENT)
public final class WitherStormDebris extends EntityFX {
    private static java.lang.ref.WeakReference<World> currentWorld = new java.lang.ref.WeakReference<World>(null);
    private static int active;
    private final EntityWitherStorm boss;
    private final double startX, startY, startZ;
    private boolean counted = true;
    private WitherStormDebris(World world, EntityWitherStorm boss, int x, int y, int z, Block block, int meta) {
        super(world, x + 0.5D, y + 0.5D, z + 0.5D);
        this.boss = boss; startX = posX; startY = posY; startZ = posZ;
        setParticleIcon(block.getIcon(0, meta)); particleScale = 1.5F; particleMaxAge = 35 + rand.nextInt(15); noClip = true;
        active++;
    }
    public static void accept(World world, int id, int[] records) {
        Minecraft mc = Minecraft.getMinecraft();
        if(currentWorld.get() != world) { currentWorld = new java.lang.ref.WeakReference<World>(world); active = 0; }
        if(mc.gameSettings.particleSetting == 2) return;
        Entity e = world.getEntityByID(id);
        if(!(e instanceof EntityWitherStorm) || mc.renderViewEntity == null || mc.renderViewEntity.getDistanceSqToEntity(e) > 128D * 128D) return;
        for(int i = 0; i < records.length && active < WitherStormConfig.maxDebris; i += 5) {
            if(mc.gameSettings.particleSetting == 1 && (i / 5 & 1) == 1) continue;
            Block b = Block.getBlockById(records[i+3]);
            if(b != null && b.getIcon(0, records[i+4]) != null) mc.effectRenderer.addEffect(new WitherStormDebris(world, (EntityWitherStorm)e, records[i], records[i+1], records[i+2], b, records[i+4]));
        }
    }
    @Override public int getFXLayer() { return 1; }
    @Override public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        if(++particleAge >= particleMaxAge || boss.isDead || Minecraft.getMinecraft().theWorld != worldObj) { setDead(); return; }
        double t = (double)particleAge / particleMaxAge, ease = t * t, swirl = Math.sin(t * Math.PI) * 1.5D;
        setPosition(startX + (boss.posX - startX) * ease + Math.cos(t * 9) * swirl,
            startY + (boss.posY + boss.height * 0.4D - startY) * ease + Math.sin(t * Math.PI) * 3,
            startZ + (boss.posZ - startZ) * ease + Math.sin(t * 9) * swirl);
        particleScale = (float)(1.5D * (1D - t * 0.7D));
    }
    @Override public void setDead() { super.setDead(); if(counted) { if(currentWorld.get() == worldObj) active = Math.max(0, active - 1); counted = false; } }
}
