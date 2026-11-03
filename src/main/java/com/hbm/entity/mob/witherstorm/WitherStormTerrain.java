package com.hbm.entity.mob.witherstorm;

import java.util.ArrayDeque;
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
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** A resumable coordinate stream, not a crater, a volume allocation or falling-block simulation. */
public final class WitherStormTerrain {
    private final EntityWitherStorm boss;
    private int anchorX, anchorY, anchorZ, radius, column, scanY = -1;
    private boolean started;
    private final ArrayDeque<int[]> impacts = new ArrayDeque<int[]>();
    private final int[] debris = new int[80 * 5];
    private int debrisCount, emittedThisTick;
    private static World suppressionWorld;
    private static int suppressionDepth;
    public WitherStormTerrain(EntityWitherStorm boss) { this.boss = boss; }
    public static void beginSuppression(World world) { suppressionWorld = world; suppressionDepth++; }
    public static void endSuppression() { if(--suppressionDepth == 0) suppressionWorld = null; }
    public static boolean suppressing(World world) { return suppressionDepth > 0 && suppressionWorld == world && !world.isRemote; }

    public void impact(double x, double y, double z, float strength) {
        if(impacts.size() < 8 && strength > 0) impacts.add(new int[] { MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), Math.min(12, Math.max(1, MathHelper.ceiling_float_int(strength))), 0 });
    }
    public void tick() {
        emittedThisTick = 0;
        World world = boss.worldObj;
        if(WitherStormConfig.terrain && world.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
            int wanted = Math.min(WitherStormConfig.maxRadius, MathHelper.ceiling_double_int(6 + boss.getStormScale() * 10));
            int bx = MathHelper.floor_double(boss.posX), by = MathHelper.floor_double(boss.posY), bz = MathHelper.floor_double(boss.posZ);
            if(!started || Math.abs(bx - anchorX) >= 8 || Math.abs(by - anchorY) >= 8 || Math.abs(bz - anchorZ) >= 8 || wanted != radius) {
                anchorX = bx; anchorY = by; anchorZ = bz; radius = wanted; column = 0; scanY = -1; started = true;
            }
            // Rejected/air/unloaded coordinates spend the same budget as successful removals.
            // Skull craters share this budget rather than adding synchronous explosion work.
            for(int work = 0; work < WitherStormConfig.blocksPerTick; work++) {
                if(!impacts.isEmpty()) { processImpact(); continue; }
                int side = radius * 2 + 1;
                if(column >= side * side) break;
                int dx = column % side - radius, dz = column / side - radius;
                int x = anchorX + dx, z = anchorZ + dz;
                if(dx * dx + dz * dz > radius * radius || !WitherStormMath.areaLoaded(world, x, z, 1)) { column++; scanY = -1; continue; }
                Chunk chunk = world.getChunkProvider().provideChunk(x >> 4, z >> 4);
                if(scanY < 0) scanY = Math.min(Math.min(254, anchorY + radius), chunk.getHeightValue(x & 15, z & 15) - 1);
                if(scanY < Math.max(1, anchorY - radius * 2)) { column++; scanY = -1; continue; }
                consume(x, scanY--, z);
            }
        } else impacts.clear();
        if(boss.ticksExisted % 5 == 0 && debrisCount > 0) {
            PacketDispatcher.wrapper.sendToAllAround(new WitherStormDebrisPacket(boss.getEntityId(), debris, debrisCount),
                new TargetPoint(world.provider.dimensionId, boss.posX, boss.posY, boss.posZ, 128));
            debrisCount = 0;
        }
    }
    private void processImpact() {
        int[] a = impacts.peek(); int r = a[3], side = 2 * r + 1, index = a[4]++;
        int x = index % side - r, y = index / side % side - r, z = index / (side * side) - r;
        if(x * x + y * y + z * z <= r * r) consume(a[0] + x, a[1] + y, a[2] + z);
        if(a[4] >= side * side * side) impacts.remove();
    }
    private void consume(int x, int y, int z) {
        World w = boss.worldObj;
        if(y < 1 || y > 254 || !WitherStormMath.areaLoaded(w, x, z, 1)) return;
        Block b = w.getBlock(x, y, z); int meta = w.getBlockMetadata(x, y, z);
        if(b == Blocks.air || b == Blocks.bedrock || b == Blocks.command_block || b == Blocks.end_portal || b == Blocks.end_portal_frame || b == Blocks.portal || b.getMaterial().isLiquid() || b.getBlockHardness(w, x, y, z) < 0) return;
        if(!WitherStormConfig.protectedBlocks && (WitherStormConfig.blacklist.contains(Block.blockRegistry.getNameForObject(b)) || !b.canEntityDestroy(w, x, y, z, boss))) return;
        if(!WitherStormConfig.tileEntities && (b.hasTileEntity(meta) || w.getTileEntity(x, y, z) != null)) return;
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
        if(!removed) return;
        boss.addMass(3);
        if(emittedThisTick++ < WitherStormConfig.debrisPerTick && debrisCount < 80) {
            int i = debrisCount++ * 5;
            debris[i] = x; debris[i + 1] = y; debris[i + 2] = z; debris[i + 3] = Block.getIdFromBlock(b); debris[i + 4] = meta;
        }
    }
    public void write(NBTTagCompound n) {
        n.setBoolean("SweepStarted", started); n.setInteger("SweepX", anchorX); n.setInteger("SweepY", anchorY); n.setInteger("SweepZ", anchorZ);
        n.setInteger("SweepRadius", radius); n.setInteger("SweepColumn", column); n.setInteger("SweepScanY", scanY);
        NBTTagList list = new NBTTagList();
        for(int[] a : impacts) { NBTTagCompound tag = new NBTTagCompound(); tag.setIntArray("Impact", a); list.appendTag(tag); }
        n.setTag("Impacts", list);
    }
    public void read(NBTTagCompound n) {
        started = n.getBoolean("SweepStarted"); anchorX = n.getInteger("SweepX"); anchorY = n.getInteger("SweepY"); anchorZ = n.getInteger("SweepZ");
        radius = Math.max(1, Math.min(48, n.getInteger("SweepRadius"))); column = Math.max(0, n.getInteger("SweepColumn")); scanY = Math.max(-1, Math.min(254, n.getInteger("SweepScanY")));
        impacts.clear(); NBTTagList list = n.getTagList("Impacts", 10);
        for(int i = 0; i < Math.min(8, list.tagCount()); i++) { int[] a = list.getCompoundTagAt(i).getIntArray("Impact"); if(a.length == 5 && a[3] > 0 && a[3] <= 12 && a[4] >= 0) impacts.add(a); }
    }
}
