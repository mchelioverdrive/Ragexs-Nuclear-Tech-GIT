package com.hbm.entity.mob.witherstorm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import com.hbm.config.WitherStormConfig;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.WitherStormDebrisPacket;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Server-owned, surface-first sections with independent scan, removal and elapsed-time limits. */
public final class WitherStormTerrain {
    private final EntityWitherStorm boss;
    private final ArrayDeque<FeedingSection> feeding = new ArrayDeque<FeedingSection>();
    private final ArrayDeque<int[]> impacts = new ArrayDeque<int[]>();
    private int gridX = Integer.MIN_VALUE, gridZ, gridRadius, radius, sectionSide, lowerY, upperY;
    private int debrisCount, emittedThisTick, tickDebrisStart, removedThisTick;
    private final int[] debris = new int[80 * 5];

    /** Only column cursors are retained, never block volumes or references to loaded chunks. */
    private static final class FeedingSection {
        final int x, z, side;
        final int[] tops;
        int column, y = -1, sliceBottom = -1, refreshTick, readyTick, removed;
        FeedingSection(int x, int z, int side) {
            this.x = x; this.z = z; this.side = side;
            tops = new int[side * side];
            Arrays.fill(tops, -1);
        }
        long key() { return (long)x << 32 ^ (z & 0xFFFFFFFFL); }
    }
    private static World suppressionWorld;
    private static int suppressionDepth;
    public WitherStormTerrain(EntityWitherStorm boss) { this.boss = boss; }
    public static void beginSuppression(World world) { suppressionWorld = world; suppressionDepth++; }
    public static void endSuppression() { if(--suppressionDepth == 0) suppressionWorld = null; }
    public static boolean suppressing(World world) { return suppressionDepth > 0 && suppressionWorld == world && !world.isRemote; }

    public void impact(double x, double y, double z, float strength) {
        impact(x, y, z, strength, false);
    }
    public void impact(double x, double y, double z, float strength, boolean fire) {
        if(impacts.size() < 8 && strength > 0) impacts.add(new int[] { MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), Math.min(12, Math.max(1, MathHelper.ceiling_float_int(strength))), 0, fire ? 1 : 0 });
    }
    public void tick() {
        if(boss.worldObj.isRemote) return;
        emittedThisTick = removedThisTick = 0;
        tickDebrisStart = debrisCount;
        World world = boss.worldObj;
        if(WitherStormConfig.terrain && world.getGameRules().getGameRuleBooleanValue("mobGriefing") && WitherStormConfig.blocksPerTick > 0) {
            WitherStormStage stage = boss.getStage();
            long deadline = System.nanoTime() + WitherStormConfig.terrainMicros * 1000L;
            updateReach(stage);
            updateSections(stage);
            // Preserve beam-focused feeding without replacing a pending body section.
            if(boss.ticksExisted % 20 == 0 && impacts.size() < 4) {
                for(int h = 0; h < 3; h++) {
                    int head = (h + boss.ticksExisted / 20) % 3;
                    if(!boss.beamActive(head)) continue;
                    net.minecraft.entity.Entity target = world.getEntityByID(boss.headTarget(head));
                    if(target != null) { impact(target.posX, target.posY, target.posZ, stage.sectionRadius()); break; }
                }
            }
            int waitingSections = 0;
            int removalLimit = Math.min(WitherStormConfig.blocksPerTick, stage.consumptionBudget());
            // Air, protection vetoes, unloaded columns and queue bookkeeping only spend scan work.
            for(int work = 0; work < WitherStormConfig.terrainChecksPerTick
                && removedThisTick < removalLimit && System.nanoTime() < deadline; work++) {
                if(!impacts.isEmpty() && (work & 3) == 0) { processImpact(); continue; }
                FeedingSection section = feeding.peek();
                if(section == null) break;
                if(section.readyTick > boss.ticksExisted) {
                    feeding.add(feeding.remove());
                    // Do not spin over empty terrain when every section is waiting.
                    if(++waitingSections >= feeding.size() && impacts.isEmpty()) break;
                    continue;
                }
                waitingSections = 0;
                if(processFeeding(section)) {
                    feeding.add(feeding.remove());
                    section.readyTick = boss.ticksExisted + (section.removed == 0 ? 20 : 1);
                    section.removed = 0;
                }
            }
        } else { impacts.clear(); feeding.clear(); gridX = Integer.MIN_VALUE; }
        if(boss.ticksExisted % 5 == 0 && debrisCount > 0) {
            PacketDispatcher.wrapper.sendToAllAround(new WitherStormDebrisPacket(boss.getEntityId(), debris, debrisCount),
                new TargetPoint(world.provider.dimensionId, boss.posX, boss.posY, boss.posZ, 128));
            debrisCount = 0;
        }
    }
    private void updateReach(WitherStormStage stage) {
        double scale = boss.getStormScale();
        double extent;
        switch(stage) {
            case A: extent = 3D * scale; break;
            case A_TRANSFORMED: extent = 6D * scale; break;
            case B: extent = Math.max(boss.width * 0.75D, 12D * boss.armScale()); break;
            case C_SMALL: extent = Math.max(boss.width * 0.75D, 20D * boss.armScale()); break;
            case C_BIG: extent = Math.max(boss.width * 0.75D, 30D * boss.armScale()); break;
            case D: extent = Math.max(boss.width * 0.75D, 42D * boss.armScale()); break;
            default: extent = Math.max(boss.width * 0.75D, 20D * scale); break;
        }
        // Cover actual heads and intact gameplay tentacle endpoints, including animated reaches.
        for(int slot = 0; slot < 13; slot++) {
            if(!boss.isPartActive(slot)) continue;
            EntityWitherStormPart part = slot < 3 ? boss.headPart(slot) : boss.tentaclePart(slot);
            if(part == null || part.isDead || slot >= 3 && !part.tentacle.intact()) continue;
            Vec3 point = boss.partPosition(slot);
            double dx = point.xCoord - boss.posX, dz = point.zCoord - boss.posZ;
            extent = Math.max(extent, Math.sqrt(dx * dx + dz * dz) + stage.sectionRadius());
        }
        radius = Math.min(WitherStormConfig.maxRadius, MathHelper.ceiling_double_int(extent));
        // The horizontal radius cap must never shorten vertical reach from normal flight.
        lowerY = Math.max(1, MathHelper.floor_double(boss.posY - boss.terrainClearance() - radius * 2D));
        upperY = Math.min(254, MathHelper.ceiling_double_int(boss.posY + boss.height + radius));
    }

    private void updateSections(WitherStormStage stage) {
        int side = stage.sectionRadius() * 2 + 1;
        int bx = MathHelper.floor_double(boss.posX), bz = MathHelper.floor_double(boss.posZ);
        if(sectionSide == side && gridX == bx && gridZ == bz && gridRadius == radius) return;
        if(sectionSide != side) feeding.clear();
        sectionSide = side; gridX = bx; gridZ = bz; gridRadius = radius;
        Set<Long> present = new HashSet<Long>();
        for(Iterator<FeedingSection> it = feeding.iterator(); it.hasNext();) {
            FeedingSection section = it.next();
            if(!sectionInRange(section.x, section.z, side)) it.remove();
            else present.add(section.key());
        }
        // World-aligned tiles retain their order and cursors as the creature moves.
        // Start near the body, then visit every intersecting edge tile, including partial tiles.
        int cx = Math.floorDiv(bx, side), cz = Math.floorDiv(bz, side);
        int rings = radius / side + 2;
        for(int ring = 0; ring <= rings; ring++) for(int dz = -ring; dz <= ring; dz++) for(int dx = -ring; dx <= ring; dx++) {
            if(Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
            int x = (cx + dx) * side, z = (cz + dz) * side;
            long key = (long)x << 32 ^ (z & 0xFFFFFFFFL);
            if(sectionInRange(x, z, side) && present.add(key)) feeding.add(new FeedingSection(x, z, side));
        }
    }
    private boolean sectionInRange(int x, int z, int side) {
        double dx = Math.max(0D, Math.max(x - boss.posX, boss.posX - (x + side)));
        double dz = Math.max(0D, Math.max(z - boss.posZ, boss.posZ - (z + side)));
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean processFeeding(FeedingSection s) {
        if(s.column == 0 && s.y < 0 && boss.ticksExisted >= s.refreshTick) {
            // Rediscover new/rebuilt structures and blocks that were previously protected.
            Arrays.fill(s.tops, -1);
            s.refreshTick = boss.ticksExisted + 100;
        }
        int x = s.x + s.column % s.side, z = s.z + s.column / s.side;
        double dx = x + 0.5D - boss.posX, dz = z + 0.5D - boss.posZ;
        if(dx * dx + dz * dz > radius * radius || !WitherStormMath.areaLoaded(boss.worldObj, x, z, 1))
            return nextColumn(s, -1);
        Chunk chunk = boss.worldObj.getChunkProvider().provideChunk(x >> 4, z >> 4);
        // Each column is anchored to its surface as well as flight altitude. Each visit peels
        // only a bounded depth, instead of mining one column to bedrock ahead of nearby roofs.
        int bottom = Math.max(1, Math.min(lowerY, chunk.getHeightValue(x & 15, z & 15) - s.side));
        if(s.y < 0) {
            // Height maps omit transparent roofs/leaves. Discover from the highest occupied
            // storage section; retained column ceilings skip cleared air on later visits.
            s.y = Math.min(upperY, s.tops[s.column] < 0 ? chunk.getTopFilledSegment() + 15 : s.tops[s.column]);
            s.sliceBottom = -1;
        }
        if(s.y < bottom) return nextColumn(s, s.y);
        ExtendedBlockStorage storage = chunk.getBlockStorageArray()[s.y >> 4];
        if(storage == null || storage.isEmpty()) s.y = (s.y & ~15) - 1;
        else {
            int y = s.y--;
            if(consume(x, y, z, false)) {
                s.removed++;
                if(s.sliceBottom < 0) s.sliceBottom = Math.max(bottom, y - s.side + 1);
            }
        }
        if(s.y < bottom || s.sliceBottom >= 0 && s.y < s.sliceBottom) return nextColumn(s, s.y);
        return false;
    }
    private boolean nextColumn(FeedingSection s, int top) {
        s.tops[s.column] = top;
        s.y = s.sliceBottom = -1;
        if(++s.column < s.tops.length) return false;
        s.column = 0;
        return true;
    }

    private void processImpact() {
        int[] a = impacts.peek();
        int r = a[3], side = 2 * r + 1, index = a[4]++;
        // Preserve the saved impact index order used by earlier versions.
        int x = index % side - r, y = index / side % side - r, z = index / (side * side) - r;
        if(x * x + y * y + z * z <= r * r)
            consume(a[0] + x, a[1] + y, a[2] + z, a.length > 5 && a[5] != 0);
        if(a[4] >= side * side * side) impacts.remove();
    }
    private boolean consume(int x, int y, int z, boolean fire) {
        World w = boss.worldObj;
        if(y < 1 || y > 254 || !WitherStormMath.areaLoaded(w, x, z, 1)) return false;
        Block b = w.getBlock(x, y, z);
        if(b == Blocks.air || b == Blocks.fire || b == Blocks.bedrock || b == Blocks.command_block || b == Blocks.end_portal || b == Blocks.end_portal_frame || b == Blocks.portal || b.getMaterial().isLiquid() || b.getBlockHardness(w, x, y, z) < 0) return false;
        if(!WitherStormConfig.protectedBlocks && (WitherStormConfig.blacklist.contains(Block.blockRegistry.getNameForObject(b)) || !b.canEntityDestroy(w, x, y, z, boss))) return false;
        int meta = w.getBlockMetadata(x, y, z);
        if(!WitherStormConfig.tileEntities && (b.hasTileEntity(meta) || w.getTileEntity(x, y, z) != null)) return false;
        beginSuppression(w);
        boolean removed;
        try {
            if(w.getTileEntity(x, y, z) instanceof IInventory) {
                IInventory inv = (IInventory)w.getTileEntity(x, y, z);
                for(int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, null);
            }
            // Flag 2 sends the block change without cascading synchronous neighbor scans.
            removed = w.setBlock(x, y, z, Blocks.air, 0, 2);
        } finally { endSuppression(); }
        if(!removed) return false;
        removedThisTick++;
        boss.addMass(3);
        if(fire && w.rand.nextInt(8) == 0 && Blocks.fire.canPlaceBlockAt(w, x, y, z) && flammableNeighbor(x, y, z))
            w.setBlock(x, y, z, Blocks.fire, 0, 2);
        // Reservoir-sample actual removals, independently of the removal allowance.
        int cap = Math.min(WitherStormConfig.debrisPerTick, 80 - tickDebrisStart);
        if(cap > 0) {
            int sample = emittedThisTick++ < cap ? debrisCount++ - tickDebrisStart : w.rand.nextInt(emittedThisTick);
            if(sample < cap) {
                int i = (tickDebrisStart + sample) * 5;
                debris[i] = x; debris[i + 1] = y; debris[i + 2] = z; debris[i + 3] = Block.getIdFromBlock(b); debris[i + 4] = meta;
            }
        }
        return true;
    }
    private boolean flammableNeighbor(int x, int y, int z) {
        for(net.minecraftforge.common.util.ForgeDirection face : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
            int nx = x + face.offsetX, ny = y + face.offsetY, nz = z + face.offsetZ;
            Block block = boss.worldObj.getBlock(nx, ny, nz);
            if(!WitherStormConfig.protectedBlocks && (WitherStormConfig.blacklist.contains(Block.blockRegistry.getNameForObject(block))
                || !block.canEntityDestroy(boss.worldObj, nx, ny, nz, boss))) continue;
            if(!WitherStormConfig.tileEntities && boss.worldObj.getTileEntity(nx, ny, nz) != null) continue;
            if(block.isFlammable(boss.worldObj, nx, ny, nz, face.getOpposite())) return true;
        }
        return false;
    }
    public void write(NBTTagCompound n) {
        NBTTagList list = new NBTTagList();
        for(int[] a : impacts) { NBTTagCompound tag = new NBTTagCompound(); tag.setIntArray("Impact", a); list.appendTag(tag); }
        n.setTag("Impacts", list);
    }
    public void read(NBTTagCompound n) {
        // Legacy Sweep*/ConsumptionSection tags are disposable cursors, not world state.
        // Rediscover surfaces after reload; keep mass and the existing queued-impact format.
        feeding.clear(); gridX = Integer.MIN_VALUE;
        impacts.clear(); NBTTagList list = n.getTagList("Impacts", 10);
        for(int i = 0; i < Math.min(8, list.tagCount()); i++) { int[] a = list.getCompoundTagAt(i).getIntArray("Impact"); if(validSection(a) != null) impacts.add(a); }
    }
    private static int[] validSection(int[] a) {
        if((a.length != 5 && a.length != 6) || a[3] < 1 || a[3] > 12 || a[4] < 0 || a[4] >= Math.pow(a[3] * 2 + 1, 3)) return null;
        return a;
    }
}
