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
    private int[] section;
    private int sectionNumber;
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
        impact(x, y, z, strength, false);
    }
    public void impact(double x, double y, double z, float strength, boolean fire) {
        if(impacts.size() < 8 && strength > 0) impacts.add(new int[] { MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), Math.min(12, Math.max(1, MathHelper.ceiling_float_int(strength))), 0, fire ? 1 : 0 });
    }
    public void tick() {
        if(boss.worldObj.isRemote) return;
        emittedThisTick = 0;
        World world = boss.worldObj;
        if(WitherStormConfig.terrain && world.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
            WitherStormStage stage = boss.getStage();
            int wanted = Math.min(WitherStormConfig.maxRadius, MathHelper.ceiling_double_int(Math.max(6 + boss.getStormScale() * 10, boss.width * 0.6D)));
            // Coordinate and elapsed-time limits include air, protected blocks and impact work.
            int budget = Math.min(WitherStormConfig.blocksPerTick, stage.consumptionBudget());
            long deadline = System.nanoTime() + WitherStormConfig.terrainMicros * 1000L;
            for(int work = 0; work < budget && System.nanoTime() < deadline; work++) {
                // Impacts and autonomous feeding share the allowance without starving either queue.
                if(!impacts.isEmpty() && (work % 2 == 0)) { processImpact(); continue; }
                if(section == null) {
                    int bx = MathHelper.floor_double(boss.posX), bz = MathHelper.floor_double(boss.posZ);
                    int r = Math.min(wanted, stage.sectionRadius()), stride = r * 2 + 1;
                    int cells = Math.max(1, (wanted * 2 + 1) / stride), total = cells * cells;
                    if(!started || Math.abs(bx - anchorX) >= stride || Math.abs(bz - anchorZ) >= stride || wanted != radius || column >= total) {
                        anchorX = bx; anchorZ = bz; anchorY = MathHelper.floor_double(boss.posY);
                        radius = wanted; column = 0; started = true;
                    }
                    int index = column++;
                    int x = anchorX + (index % cells) * stride - (cells - 1) * stride / 2;
                    int z = anchorZ + (index / cells) * stride - (cells - 1) * stride / 2;
                    // Regularly feed at an active beam, while tentacle contacts retain their impact queue.
                    if((sectionNumber++ & 3) == 0) for(int h = 0; h < 3; h++) {
                        if(!boss.beamActive((h + sectionNumber) % 3)) continue;
                        net.minecraft.entity.Entity target = world.getEntityByID(boss.headTarget((h + sectionNumber) % 3));
                        if(target != null) { x = MathHelper.floor_double(target.posX); z = MathHelper.floor_double(target.posZ); break; }
                    }
                    if(!WitherStormMath.areaLoaded(world, x, z, r + 1)) continue;
                    Chunk chunk = world.getChunkProvider().provideChunk(x >> 4, z >> 4);
                    int top = Math.min(254, chunk.getHeightValue(x & 15, z & 15) - 1);
                    int reach = radius * 2;
                    if(stage == WitherStormStage.D) reach = Math.max(reach, MathHelper.ceiling_double_int(30D * WitherStormThunderstormPose.size(boss.development()) * boss.getStormScale() + 8D));
                    if(top < 1 || top < boss.posY - reach || top > boss.posY + radius) continue;
                    // A contiguous cube clipped at the surface, streamed across as many ticks as needed.
                    section = new int[] { x, Math.min(254 - r, top - r), z, r, 0, 0 };
                } else if(processSection(section, false)) section = null;
            }
        } else { impacts.clear(); section = null; }
        if(boss.ticksExisted % 5 == 0 && debrisCount > 0) {
            PacketDispatcher.wrapper.sendToAllAround(new WitherStormDebrisPacket(boss.getEntityId(), debris, debrisCount),
                new TargetPoint(world.provider.dimensionId, boss.posX, boss.posY, boss.posZ, 128));
            debrisCount = 0;
        }
    }
    private void processImpact() {
        if(processSection(impacts.peek(), true)) impacts.remove();
    }
    private boolean processSection(int[] a, boolean spherical) {
        int r = a[3], side = 2 * r + 1, index = a[4]++;
        int x = index % side - r;
        int y = spherical ? index / side % side - r : index / (side * side) - r;
        int z = spherical ? index / (side * side) - r : index / side % side - r;
        // Adjacent horizontal layers make each completed section read as a missing terrain volume.
        if(!spherical || x * x + y * y + z * z <= r * r)
            consume(a[0] + x, a[1] + y, a[2] + z, a.length > 5 && a[5] != 0);
        return a[4] >= side * side * side;
    }
    private void consume(int x, int y, int z, boolean fire) {
        World w = boss.worldObj;
        if(y < 1 || y > 254 || !WitherStormMath.areaLoaded(w, x, z, 1)) return;
        Block b = w.getBlock(x, y, z); int meta = w.getBlockMetadata(x, y, z);
        if(b == Blocks.air || b == Blocks.fire || b == Blocks.bedrock || b == Blocks.command_block || b == Blocks.end_portal || b == Blocks.end_portal_frame || b == Blocks.portal || b.getMaterial().isLiquid() || b.getBlockHardness(w, x, y, z) < 0) return;
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
        if(fire && w.rand.nextInt(8) == 0 && Blocks.fire.canPlaceBlockAt(w, x, y, z) && flammableNeighbor(x, y, z))
            w.setBlock(x, y, z, Blocks.fire, 0, 2);
        if(emittedThisTick++ < WitherStormConfig.debrisPerTick && debrisCount < 80) {
            int i = debrisCount++ * 5;
            debris[i] = x; debris[i + 1] = y; debris[i + 2] = z; debris[i + 3] = Block.getIdFromBlock(b); debris[i + 4] = meta;
        }
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
        n.setBoolean("SweepStarted", started); n.setInteger("SweepX", anchorX); n.setInteger("SweepY", anchorY); n.setInteger("SweepZ", anchorZ);
        n.setInteger("SweepRadius", radius); n.setInteger("SweepColumn", column); n.setInteger("SweepScanY", scanY);
        if(section != null) n.setIntArray("ConsumptionSection", section);
        n.setInteger("ConsumptionSectionNumber", sectionNumber);
        NBTTagList list = new NBTTagList();
        for(int[] a : impacts) { NBTTagCompound tag = new NBTTagCompound(); tag.setIntArray("Impact", a); list.appendTag(tag); }
        n.setTag("Impacts", list);
    }
    public void read(NBTTagCompound n) {
        started = n.getBoolean("SweepStarted"); anchorX = n.getInteger("SweepX"); anchorY = n.getInteger("SweepY"); anchorZ = n.getInteger("SweepZ");
        radius = Math.max(1, Math.min(48, n.getInteger("SweepRadius"))); column = Math.max(0, n.getInteger("SweepColumn")); scanY = Math.max(-1, Math.min(254, n.getInteger("SweepScanY")));
        section = validSection(n.getIntArray("ConsumptionSection"));
        sectionNumber = n.getInteger("ConsumptionSectionNumber");
        // Legacy vertical sweeps restart as grouped sections without changing saved mass or queued contacts.
        if(!n.hasKey("ConsumptionSectionNumber")) { started = false; column = 0; }
        impacts.clear(); NBTTagList list = n.getTagList("Impacts", 10);
        for(int i = 0; i < Math.min(8, list.tagCount()); i++) { int[] a = list.getCompoundTagAt(i).getIntArray("Impact"); if(validSection(a) != null) impacts.add(a); }
    }
    private static int[] validSection(int[] a) {
        if((a.length != 5 && a.length != 6) || a[3] < 1 || a[3] > 12 || a[4] < 0 || a[4] >= Math.pow(a[3] * 2 + 1, 3)) return null;
        return a;
    }
}
