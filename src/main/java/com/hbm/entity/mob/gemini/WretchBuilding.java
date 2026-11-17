package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.WretchCamps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Activity-associated enclosures and contact-limited building interaction; no independent entity scans. */
final class WretchBuilding {
    private final EntityWretch boss;
    private double minX, maxX, minZ, maxZ, floor, roof;
    private int expires, nextProbe, nextGesture, lastSide = -1, previousSide = -1;
    private Vec3 feature;
    private int featureMode;
    private final Set<Long> examined = new HashSet<Long>();
    private int removed, changeTick, changedThisTick, contactArm = -1;
    private final Vec3[] oldHand = {Vec3.createVectorHelper(0, 0, 0), Vec3.createVectorHelper(0, 0, 0)};
    private final boolean[] swept = new boolean[2];
    private final Map<Long, Denial> denied = new HashMap<Long, Denial>();
    private Vec3 breachPoint;
    private AxisAlignedBB[] breachCorridor;
    private String lastBlock = "none", rejection = "none";
    private static final class Denial {
        Block block; int meta, until; String reason;
    }
    WretchBuilding(EntityWretch boss) { this.boss = boss; }
    boolean known(int clock) { return expires > clock; }
    static boolean constructed(Block b) {
        Material m = b.getMaterial();
        return b instanceof BlockDoor || m == Material.glass || b == net.minecraft.init.Blocks.planks
                || b == net.minecraft.init.Blocks.cobblestone || b == net.minecraft.init.Blocks.brick_block
                || b == net.minecraft.init.Blocks.stonebrick || b == net.minecraft.init.Blocks.fence
                || b instanceof net.minecraft.block.BlockStairs || b instanceof net.minecraft.block.BlockSlab;
    }
    void observe(double x, double y, double z, int clock) {
        boolean insideKnown = known(clock) && x > minX && x < maxX && z > minZ && z < maxZ && y < roof + 1;
        if(insideKnown) expires = clock + 2400;
        if(clock < nextProbe) return; nextProbe = clock + 60;
        Vec3 center = Vec3.createVectorHelper(x, y + 1.4, z);
        double[] edges = new double[4]; int walls = 0;
        for(int i = 0; i < 4; i++) {
            double dx = i == 0 ? -12 : i == 1 ? 12 : 0, dz = i == 2 ? -12 : i == 3 ? 12 : 0;
            MovingObjectPosition hit = ray(center, center.addVector(dx, 0, dz));
            if(hit != null && constructed(boss.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ))) {
                walls++; edges[i] = i < 2 ? hit.hitVec.xCoord : hit.hitVec.zCoord;
            } else edges[i] = i < 2 ? x + dx * 0.5 : z + dz * 0.5;
        }
        MovingObjectPosition top = ray(center, center.addVector(0, 10, 0));
        MovingObjectPosition ground = ray(center, center.addVector(0, -4, 0));
        if(walls < 3 || top == null || ground == null || !constructed(boss.worldObj.getBlock(top.blockX, top.blockY, top.blockZ))) {
            if(insideKnown) { expires = clock; feature = null; }
            return;
        }
        if(edges[1] - edges[0] < 2 || edges[3] - edges[2] < 2) return;
        minX = edges[0]; maxX = edges[1]; minZ = edges[2]; maxZ = edges[3];
        floor = ground.hitVec.yCoord; roof = top.blockY + 1;
        expires = clock + 2400;
        if(!insideKnown) { feature = null; lastSide = previousSide = -1; }
    }
    Vec3 approach(int clock) {
        if(!known(clock)) return null;
        for(int attempt = 0; attempt < 6; attempt++) {
            int side = boss.getRNG().nextInt(4);
            if(side == lastSide || side == previousSide) continue;
            double x = (minX + maxX) * 0.5, z = (minZ + maxZ) * 0.5;
            double tangent = (boss.getRNG().nextDouble() - 0.5) * 0.6;
            if(side < 2) { x = side == 0 ? minX - 3.8 : maxX + 3.8; z += tangent * (maxZ - minZ); }
            else { z = side == 2 ? minZ - 3.8 : maxZ + 3.8; x += tangent * (maxX - minX); }
            double y = boss.navigation.surface(x, boss.posY, z);
            if(!Double.isFinite(y) || !boss.inTerritory(x, z, boss.territoryRadius()) || !boss.navigation.canStand(x, y, z)) continue;
            previousSide = lastSide; lastSide = side; feature = findFeature(side, x, z);
            return Vec3.createVectorHelper(x, y, z);
        }
        return null;
    }
    private Vec3 findFeature(int side, double x, double z) {
        double cx = (minX + maxX) * 0.5, cz = (minZ + maxZ) * 0.5;
        Vec3 wall = null;
        // Eighteen bounded rays at most. Never classify a natural cavern as a house.
        for(int row = 0; row < 3; row++) for(int offset = -1; offset <= 1; offset++) {
            double y = floor + 1.3 + row * 1.6;
            Vec3 from = Vec3.createVectorHelper(x + (side >= 2 ? offset : 0), y, z + (side < 2 ? offset : 0));
            MovingObjectPosition hit = ray(from, Vec3.createVectorHelper(cx, y, cz));
            if(hit == null) continue;
            Block b = boss.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            if(b.getMaterial() == Material.glass || b instanceof BlockDoor) { featureMode = WretchPose.WINDOW; return outside(hit); }
            if(constructed(b) && wall == null) wall = outside(hit);
        }
        if(boss.getRNG().nextInt(3) == 0) {
            double rx = side == 0 ? minX + 0.3 : side == 1 ? maxX - 0.3 : x;
            double rz = side == 2 ? minZ + 0.3 : side == 3 ? maxZ - 0.3 : z;
            MovingObjectPosition hit = ray(Vec3.createVectorHelper(rx, roof + 2, rz), Vec3.createVectorHelper(rx, floor + 1, rz));
            if(hit != null && hit.sideHit == 1 && constructed(boss.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ))) { featureMode = WretchPose.ROOF; return outside(hit); }
        }
        featureMode = WretchPose.SCRATCH;
        return wall;
    }
    private Vec3 outside(MovingObjectPosition hit) {
        int side = hit.sideHit;
        return hit.hitVec.addVector(side == 4 ? -0.2 : side == 5 ? 0.2 : 0, side == 1 ? 0.2 : 0, side == 2 ? -0.2 : side == 3 ? 0.2 : 0);
    }
    boolean gesture(int clock) {
        if(!known(clock) || feature == null || clock < nextGesture || !boss.canReach(feature.xCoord, feature.yCoord, feature.zCoord)) return false;
        boss.present(featureMode, feature); nextGesture = clock + 140 + boss.getRNG().nextInt(260); return true;
    }
    Vec3 obstruction(double x, double y, double z) {
        Vec3 movement = boss.navigation.obstruction();
        if(movement != null) return movement;
        MovingObjectPosition armBlock = boss.pose(1).reachObstruction(x, y + 0.9, z);
        if(armBlock != null && useful(armBlock.blockX, armBlock.blockY, armBlock.blockZ)) return blockCenter(armBlock);
        for(int i = 0; i < 4; i++) {
            double h = MathHelper.clamp_double(y - boss.posY + i * 1.5, 1.1, 12);
            Vec3 from = Vec3.createVectorHelper(boss.posX, boss.posY + h, boss.posZ);
            double dx = x - boss.posX, dz = z - boss.posZ, length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            MovingObjectPosition hit = ray(from, from.addVector(dx / length * Math.min(9, length), 0, dz / length * Math.min(9, length)));
            if(hit != null && useful(hit.blockX, hit.blockY, hit.blockZ)
                    && boss.canReach(hit.blockX + 0.5, hit.blockY + 0.5, hit.blockZ + 0.5)) return blockCenter(hit);
        }
        return null;
    }
    private Vec3 blockCenter(MovingObjectPosition hit) {
        return Vec3.createVectorHelper(hit.blockX + 0.5, hit.blockY + 0.5, hit.blockZ + 0.5);
    }
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
    private boolean useful(int x, int y, int z) {
        if(y < 1 || y > 254 || !boss.worldObj.blockExists(x, y, z)) return false;
        Block block = boss.worldObj.getBlock(x, y, z);
        if(block == net.minecraft.init.Blocks.air) return false;
        lastBlock = Block.blockRegistry.getNameForObject(block) + " at " + x + "," + y + "," + z;
        Denial denial = denied.get(key(x, y, z));
        if(denial != null && denial.until > boss.ticksExisted && denial.block == block
                && denial.meta == boss.worldObj.getBlockMetadata(x, y, z)) { rejection = denial.reason; return false; }
        String reason = WretchCamps.contactRejection(boss, x, y, z);
        if(reason != null) { rejected(x, y, z, reason); return false; }
        return true;
    }
    String lastObstruction() { return lastBlock; }
    String lastRejection() { return rejection; }
    void rejected(int x, int y, int z, String reason) {
        rejection = reason;
        if(!boss.worldObj.blockExists(x, y, z)) return;
        Denial denial = new Denial(); denial.block = boss.worldObj.getBlock(x, y, z);
        lastBlock = Block.blockRegistry.getNameForObject(denial.block) + " at " + x + "," + y + "," + z;
        denial.reason = reason;
        denial.meta = boss.worldObj.getBlockMetadata(x, y, z); denial.until = boss.ticksExisted + 200;
        if(denied.size() >= 128) denied.clear();
        denied.put(key(x, y, z), denial);
    }
    void beginBreach(Vec3 point) {
        breachPoint = point;
        breachCorridor = boss.brain.escalating() ? boss.navigation.corridor(point.xCoord - boss.posX, point.zCoord - boss.posZ) : null;
    }
    void finishBreach() {
        if(removed == 0 && breachPoint != null) {
            int x = MathHelper.floor_double(breachPoint.xCoord), y = MathHelper.floor_double(breachPoint.yCoord), z = MathHelper.floor_double(breachPoint.zCoord);
            Denial denial = denied.get(key(x, y, z));
            if(denial == null || denial.until <= boss.ticksExisted) rejected(x, y, z, "strike made no removable limb contact");
        }
        breachPoint = null;
        breachCorridor = null;
    }
    private boolean intersects(int x, int y, int z, AxisAlignedBB[] boxes) {
        if(!boss.worldObj.blockExists(x, y, z)) return false;
        Block block = boss.worldObj.getBlock(x, y, z);
        ArrayList<AxisAlignedBB> collisions = new ArrayList<AxisAlignedBB>(4);
        for(AxisAlignedBB box : boxes) {
            block.addCollisionBoxesToList(boss.worldObj, x, y, z, box, collisions, boss);
            if(!collisions.isEmpty()) return true;
        }
        return false;
    }
    private ArrayList<int[]> movementBlocks(AxisAlignedBB[] boxes) {
        ArrayList<int[]> blocks = new ArrayList<int[]>();
        Set<Long> seen = new HashSet<Long>();
        int tested = 0;
        for(AxisAlignedBB box : boxes) {
            // Fence/wall collision shapes inspect their neighbouring cells too.
            if(!com.hbm.world.gemini.GardenerNests.loaded(boss.worldObj, box.expand(1, 0, 1))) return blocks;
            for(int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
                for(int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++)
                    for(int y = Math.max(1, MathHelper.floor_double(box.minY) - 1); y <= Math.min(254, MathHelper.floor_double(box.maxY)); y++) {
                        if(++tested > 1024) return blocks;
                        if(!seen.add(key(x, y, z)) || !intersects(x, y, z, boxes)) continue;
                        Block block = boss.worldObj.getBlock(x, y, z);
                        // Keep ordinary support. Only foliage under the native feet is a recovery
                        // obstruction; gravity owns the descent after its animated removal.
                        if(y + 1 <= boss.posY + 0.001 && !block.isLeaves(boss.worldObj, x, y, z)) continue;
                        if(!useful(x, y, z)) continue;
                        blocks.add(new int[] {x, y, z});
                    }
        }
        Collections.sort(blocks, new Comparator<int[]>() {
            @Override public int compare(int[] a, int[] b) {
                return Double.compare(priority(a), priority(b));
            }
            private double priority(int[] p) {
                double dx = p[0] + 0.5 - boss.posX, dz = p[2] + 0.5 - boss.posZ;
                return dx * dx + dz * dz + Math.max(0, p[1] - boss.posY) * 0.02;
            }
        });
        return blocks;
    }
    Vec3 movementObstruction(AxisAlignedBB[] boxes) {
        for(int[] p : movementBlocks(boxes)) {
            Vec3 point = Vec3.createVectorHelper(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
            if(boss.canReach(point.xCoord, point.yCoord, point.zCoord)) {
                lastBlock = Block.blockRegistry.getNameForObject(boss.worldObj.getBlock(p[0], p[1], p[2])) + " at " + p[0] + "," + p[1] + "," + p[2];
                return point;
            }
        }
        return null;
    }
    private void tickBudget() {
        if(changeTick != boss.ticksExisted) { changeTick = boss.ticksExisted; changedThisTick = 0; }
    }
    private MovingObjectPosition ray(Vec3 from, Vec3 to) {
        // Vanilla 1.7.10 ray traversal mutates its start vector. Every enclosure ray
        // must start at the activity position, never at the preceding wall hit.
        return WretchBrain.lineLoaded(boss.worldObj, from, to) ? boss.worldObj.func_147447_a(
                Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord), to, false, true, false) : null;
    }
    void resetStrike() {
        examined.clear(); removed = 0; swept[0] = swept[1] = false; contactArm = -1;
        if(boss.state() != EntityWretch.BREACH) { breachPoint = null; breachCorridor = null; }
    }
    void strike(int arm) {
        if(removed >= GeminiConfig.wretchBlocksPerStrike || !boss.brain.destructiveIntent()) return;
        tickBudget();
        Vec3 hand = WretchPose.point(boss, arm, 2, 1), tip = boss.pose(1).fingerTip(arm);
        AxisAlignedBB area = GardenerPose.bounds(hand, tip, 0.65);
        int tested = 0; contactArm = arm;
        for(int x = MathHelper.floor_double(area.minX); x <= MathHelper.floor_double(area.maxX); x++)
            for(int y = MathHelper.floor_double(area.minY); y <= MathHelper.floor_double(area.maxY); y++)
                for(int z = MathHelper.floor_double(area.minZ); z <= MathHelper.floor_double(area.maxZ); z++) {
                    if(++tested > 48 || examined.size() >= 256 || changedThisTick >= GeminiConfig.wretchBlocksPerTick || removed >= GeminiConfig.wretchBlocksPerStrike) break;
                    long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
                    // A candidate outside the hand at frame 26 may be the actual contact at 28.
                    // Do not consume its one examination until the animated limb touches it.
                    if(!contacts(x, y, z) || !examined.add(key) || !useful(x, y, z)) continue;
                    if(WretchCamps.breakContact(boss, x, y, z)) { changedThisTick++; removed++; }
                }
        oldHand[arm].xCoord = hand.xCoord; oldHand[arm].yCoord = hand.yCoord; oldHand[arm].zCoord = hand.zCoord;
        swept[arm] = true; contactArm = -1;
    }
    boolean contacts(int x, int y, int z) {
        if(contactArm < 0) return false;
        if(breachCorridor != null && breachPoint != null
                && !(x == MathHelper.floor_double(breachPoint.xCoord) && y == MathHelper.floor_double(breachPoint.yCoord) && z == MathHelper.floor_double(breachPoint.zCoord))
                && !intersects(x, y, z, breachCorridor)) return false;
        Vec3 hand = WretchPose.point(boss, contactArm, 2, 1), tip = boss.pose(1).fingerTip(contactArm);
        AxisAlignedBB block = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        return GardenerPose.touches(hand, tip, block, 0.35) || GardenerPose.touches(hand, hand, block, 0.65)
                || swept[contactArm] && GardenerPose.touches(oldHand[contactArm], hand, block, 0.5);
    }
    void write(NBTTagCompound nbt, int clock) {
        if(!known(clock)) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("MinX", minX); tag.setDouble("MaxX", maxX); tag.setDouble("MinZ", minZ); tag.setDouble("MaxZ", maxZ);
        tag.setDouble("Floor", floor); tag.setDouble("Roof", roof); tag.setInteger("Remaining", expires - clock); nbt.setTag("Building", tag);
    }
    void read(NBTTagCompound nbt) {
        NBTTagCompound tag = nbt.getCompoundTag("Building");
        minX = tag.getDouble("MinX"); maxX = tag.getDouble("MaxX"); minZ = tag.getDouble("MinZ"); maxZ = tag.getDouble("MaxZ");
        floor = tag.getDouble("Floor"); roof = tag.getDouble("Roof");
        if(Double.isFinite(minX + maxX + minZ + maxZ + floor + roof) && maxX - minX >= 2 && maxX - minX <= 24
                && maxZ - minZ >= 2 && maxZ - minZ <= 24 && floor >= 0 && roof > floor && roof < 256)
            expires = MathHelper.clamp_int(tag.getInteger("Remaining"), 0, 2400);
    }
}
