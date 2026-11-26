package com.hbm.particle;

import com.hbm.config.ClientConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** A surface-aligned quad in the vanilla particle atlas batch. */
@SideOnly(Side.CLIENT)
final class ExecutionBloodDecal extends EntityFX {
    private final Block support;
    private final ForgeDirection face;
    private final int blockX, blockY, blockZ, metadata, distanceSq;
    private final double ax, ay, az, bx, by, bz, radius;

    static ExecutionBloodDecal project(WorldClient world, Vec3 start, Vec3 end, int lifetime, int distanceSq) {
        int minX = MathHelper.floor_double(Math.min(start.xCoord, end.xCoord)) >> 4;
        int maxX = MathHelper.floor_double(Math.max(start.xCoord, end.xCoord)) >> 4;
        int minZ = MathHelper.floor_double(Math.min(start.zCoord, end.zCoord)) >> 4;
        int maxZ = MathHelper.floor_double(Math.max(start.zCoord, end.zCoord)) >> 4;
        for(int x = minX; x <= maxX; x++) {
            for(int z = minZ; z <= maxZ; z++) {
                // ChunkProviderClient only looks up received chunks and returns a blank chunk if absent.
                if(world.getChunkFromChunkCoords(x, z).isEmpty()) return null;
            }
        }
        MovingObjectPosition hit = world.func_147447_a(start, end, false, true, false);
        if(hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        ForgeDirection face = ForgeDirection.getOrientation(hit.sideHit);
        if(face == ForgeDirection.UNKNOWN) return null;
        int x = hit.blockX, y = hit.blockY, z = hit.blockZ;
        if(!world.blockExists(x, y, z) || !exposed(world, x, y, z, face)) return null;
        Block block = world.getBlock(x, y, z);
        if(!block.isOpaqueCube() || !block.renderAsNormalBlock() || !block.isSideSolid(world, x, y, z, face)) return null;
        AxisAlignedBB bounds = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        if(bounds == null || bounds.minX != x || bounds.maxX != x + 1 || bounds.minY != y || bounds.maxY != y + 1
                || bounds.minZ != z || bounds.maxZ != z + 1) return null;

        double radius = 0.09 + world.rand.nextDouble() * 0.13;
        double margin = radius * 1.8;
        return new ExecutionBloodDecal(world, block, hit, face, radius,
                onFace(hit.hitVec.xCoord, x, face.offsetX, margin),
                onFace(hit.hitVec.yCoord, y, face.offsetY, margin),
                onFace(hit.hitVec.zCoord, z, face.offsetZ, margin), lifetime, distanceSq);
    }

    private ExecutionBloodDecal(World world, Block support, MovingObjectPosition hit, ForgeDirection face,
            double radius, double x, double y, double z, int lifetime, int distanceSq) {
        super(world, x, y, z);
        this.support = support;
        this.face = face;
        this.radius = radius;
        this.distanceSq = distanceSq;
        blockX = hit.blockX;
        blockY = hit.blockY;
        blockZ = hit.blockZ;
        metadata = world.getBlockMetadata(blockX, blockY, blockZ);
        particleMaxAge = lifetime;
        setParticleTextureIndex(3 + rand.nextInt(4));
        setRBGColorF(0.30F + rand.nextFloat() * 0.14F, 0.034F, 0.03F);
        setAlphaF(0.82F);

        Vec3 normal = Vec3.createVectorHelper(face.offsetX, face.offsetY, face.offsetZ);
        Vec3 tangent = face.offsetY == 0 ? Vec3.createVectorHelper(0, 1, 0) : Vec3.createVectorHelper(1, 0, 0);
        Vec3 bitangent = normal.crossProduct(tangent);
        double angle = rand.nextDouble() * 2D * Math.PI;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        ax = tangent.xCoord * cos + bitangent.xCoord * sin;
        ay = tangent.yCoord * cos + bitangent.yCoord * sin;
        az = tangent.zCoord * cos + bitangent.zCoord * sin;
        bx = bitangent.xCoord * cos - tangent.xCoord * sin;
        by = bitangent.yCoord * cos - tangent.yCoord * sin;
        bz = bitangent.zCoord * cos - tangent.zCoord * sin;
    }

    private static double onFace(double hit, int block, int normal, double margin) {
        if(normal != 0) return block + (normal > 0 ? 1 : 0) + normal * 0.003;
        return Math.max(block + margin, Math.min(block + 1 - margin, hit));
    }

    private static boolean exposed(World world, int x, int y, int z, ForgeDirection face) {
        x += face.offsetX;
        y += face.offsetY;
        z += face.offsetZ;
        return world.blockExists(x, y, z) && !world.getChunkFromBlockCoords(x, z).isEmpty() && world.isAirBlock(x, y, z);
    }

    @Override public void onUpdate() {
        if(++particleAge >= particleMaxAge || !ClientConfig.EXECUTION_DEVICE_GORE.get() || !worldObj.blockExists(blockX, blockY, blockZ)
                || worldObj.getBlock(blockX, blockY, blockZ) != support || worldObj.getBlockMetadata(blockX, blockY, blockZ) != metadata
                || !exposed(worldObj, blockX, blockY, blockZ, face)) setDead();
        else setAlphaF(0.82F * Math.min(1F, (particleMaxAge - particleAge) / 40F));
    }

    @Override public void renderParticle(Tessellator tessellator, float partial, float viewX, float viewXZ, float viewZ, float viewYZ, float viewXY) {
        if(isDead || !ClientConfig.EXECUTION_DEVICE_GORE.get()) return;
        double x = posX - interpPosX, y = posY - interpPosY, z = posZ - interpPosZ;
        if(x * x + y * y + z * z > distanceSq) return;
        tessellator.setColorRGBA_F(particleRed, particleGreen, particleBlue, particleAlpha);
        stamp(tessellator, x, y, z, radius, radius * 0.79);
        stamp(tessellator, x + ax * radius, y + ay * radius, z + az * radius, radius * 0.31, radius * 0.25);
        stamp(tessellator, x - bx * radius, y - by * radius, z - bz * radius, radius * 0.22, radius * 0.18);
    }

    private void stamp(Tessellator tessellator, double x, double y, double z, double width, double height) {
        double u = particleTextureIndexX / 16D, v = particleTextureIndexY / 16D, extent = 0.0624375;
        double ux = ax * width, uy = ay * width, uz = az * width, vx = bx * height, vy = by * height, vz = bz * height;
        tessellator.addVertexWithUV(x - ux - vx, y - uy - vy, z - uz - vz, u, v + extent);
        tessellator.addVertexWithUV(x + ux - vx, y + uy - vy, z + uz - vz, u + extent, v + extent);
        tessellator.addVertexWithUV(x + ux + vx, y + uy + vy, z + uz + vz, u + extent, v);
        tessellator.addVertexWithUV(x - ux + vx, y - uy + vy, z - uz + vz, u, v);
    }
}
