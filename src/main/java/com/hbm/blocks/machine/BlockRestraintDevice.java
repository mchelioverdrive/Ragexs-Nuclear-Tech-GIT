package com.hbm.blocks.machine;

import java.util.ArrayList;
import java.util.List;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ICustomBlockHighlight;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.machine.TileEntityRestraintDevice;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

public class BlockRestraintDevice extends BlockDummyable implements ITooltipProvider {
    public enum Kind { GUILLOTINE, BULL, CHAIR, TABLE, HANGING }
    public final Kind kind;
    private final List<List<AxisAlignedBB>> rotatedBounds = new ArrayList<List<AxisAlignedBB>>(4);
    public BlockRestraintDevice(Kind kind) {
        super(kind == Kind.GUILLOTINE || kind == Kind.HANGING ? Material.wood : Material.iron);
        this.kind = kind;
        setHardness(4F); setResistance(15F);
        if(kind == Kind.HANGING) {
            setStepSound(soundTypeWood);
            box(-22, 13, -22, 13, 3, 44); box(9, 13, -22, 13, 3, 44);
            box(-9, 13, -22, 18, 3, 13); box(-9, 13, 9, 18, 3, 13);
            for(int x : new int[] {-20, 16}) {
                box(x, 0, -3, 4, 77, 6);
                box(x - 1, 0, -18, 6, 13, 5); box(x - 1, 0, 13, 6, 13, 5);
            }
            box(-22, 73, -3, 44, 4, 6);
            // One-model-pixel slices follow the diagonal braces without filling the archway.
            for(int side : new int[] {-1, 1}) for(int y = 0; y < 21; ++y) {
                double angle = side * 0.65F, sin = Math.sin(angle), cos = Math.cos(angle);
                double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                for(int x : new int[] {-1, 1}) for(int dy = 0; dy <= 1; ++dy) {
                    double bx = side * 17 + x * cos - (y + dy) * sin;
                    double by = 58 + x * sin + (y + dy) * cos;
                    minX = Math.min(minX, bx); maxX = Math.max(maxX, bx);
                    minY = Math.min(minY, by); maxY = Math.max(maxY, by);
                }
                bounding.add(AxisAlignedBB.getBoundingBox(minX / 16, minY / 16, -2D / 16, maxX / 16, maxY / 16, 2D / 16));
            }
            box(-8, 0, 16, 16, 6, 8); box(-8, 6, 16, 16, 7, 4);
            box(-20, 16, -4, 4, 2, 1); box(16, 16, -4, 4, 2, 1);
        } else if(kind == Kind.GUILLOTINE) {
            box(-22, 0, -21, 44, 3, 42);
            box(-18, 3, -3, 4, 55, 6); box(14, 3, -3, 4, 55, 6);
            box(-18, 56, -3, 36, 4, 6);
            box(-12, 9, -18, 24, 3, 38);
            box(-12, 12, -17, 3, 12, 3); box(9, 12, -17, 3, 12, 3);
            box(-14, 24, -3, 10, 5, 6); box(4, 24, -3, 10, 5, 6);
            box(-5, 29, -3, 10, 3, 6);
            box(-14, 30, -2, 1, 25, 2); box(13, 30, -2, 1, 25, 2);
            for(int x : new int[] {-17, 15}) for(int y : new int[] {5, 25, 52}) box(x, y, -4, 2, 2, 1);
            box(19, 18, -1, 2, 14, 2); box(17, 31, -2, 6, 2, 4);
        } else if(kind == Kind.BULL) {
            box(-20, 7, -21, 40, 3, 42);
            box(-20, 10, -21, 3, 34, 42); box(17, 10, -21, 3, 34, 42);
            box(-17, 44, -21, 34, 4, 42);
            box(-17, 9, -23, 34, 35, 2); box(-17, 44, -23, 34, 2, 2);
            box(-17, 10, 21, 4, 34, 3); box(13, 10, 21, 4, 34, 3); box(-13, 41, 21, 26, 3, 3);
            for(int x : new int[] {-17, 11}) for(int z : new int[] {-17, 11}) {
                box(x, 0, z, 6, 10, 6); box(x - 1, 0, z - 1, 8, 3, 8);
            }
            box(-12, 33, -31, 24, 15, 13); box(-10, 29, -37, 20, 10, 9);
            box(-10, 45, -28, 4, 9, 4); box(6, 45, -28, 4, 9, 4);
            box(-14, 52, -28, 8, 3, 4); box(6, 52, -28, 8, 3, 4);
            box(-13, 40, -32, 3, 3, 3); box(10, 40, -32, 3, 3, 3);
            box(-24, 41, -25, 7, 2, 7); box(17, 41, -25, 7, 2, 7);
            box(-2, 33, 24, 3, 13, 3); box(-2, 30, 26, 3, 5, 5);
            for(int z = -18; z <= 18; z += 9) { box(-22, 14, z, 2, 16, 2); box(20, 14, z, 2, 16, 2); }
            box(-10, 2, -14, 20, 3, 28);
            for(int x = -8; x <= 8; x += 4) box(x, 4, -13, 1, 2, 26);
            box(12, 48, -8, 4, 7, 4);
        } else if(kind == Kind.TABLE) {
            box(-11, 11, -22, 22, 3, 44); box(-10, 14, -21, 20, 2, 42);
            for(int x : new int[] {-9, 6}) for(int z : new int[] {-19, 16}) box(x, 1, z, 3, 11, 3);
            box(-9, 3, -1, 18, 2, 2); box(-7, 16, -20, 14, 2, 8);
            box(12, 0, -17, 2, 34, 2); box(3, 33, -17, 11, 2, 2);
            box(4, 27, -17, 4, 6, 3);
            box(-14, 12, -8, 3, 2, 16); box(11, 12, -8, 3, 2, 16);
        } else {
            box(-7, 8, -7, 14, 3, 14); box(-7, 10, -7, 14, 23, 3);
            for(int x : new int[] {-7, 4}) for(int z : new int[] {-6, 4}) box(x, 0, z, 3, 9, 3);
            box(-8, 13, -6, 3, 3, 15); box(5, 13, -6, 3, 3, 15);
            box(-8, 8, 5, 2, 7, 2); box(6, 8, 5, 2, 7, 2);
            box(-4, 0, 8, 8, 2, 5); box(-4, 32, -4, 8, 3, 8); box(-2, 35, -2, 4, 3, 4);
            box(4, 20, -9, 4, 7, 3);
            for(int y = 3; y < 34; y += 3) box(6, y, -9, 1, 2, 1);
            box(8, 5, -5, 3, 7, 6);
        }
        for(int meta = 12; meta <= 15; ++meta) {
            List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>(bounding.size());
            for(AxisAlignedBB box : bounding) boxes.add(rotate(box, meta));
            rotatedBounds.add(boxes);
        }
    }

    // ModelRestraintDevice uses model pixels and renders at 1/16 about (coreX + .5, coreY, coreZ + .5).
    // Keep the solid frame cuboids above in those same units. Straps, rope and effects are non-solid.
    private void box(double x, double y, double z, double width, double height, double depth) {
        bounding.add(modelBox(x, y, z, width, height, depth));
    }
    private static AxisAlignedBB modelBox(double x, double y, double z, double width, double height, double depth) {
        return AxisAlignedBB.getBoundingBox(x / 16, y / 16, z / 16, (x + width) / 16, (y + height) / 16, (z + depth) / 16);
    }
    public static int modelYaw(int metadata) {
        switch(metadata - offset) {
            case 2: return 180; case 4: return 270; case 5: return 90; default: return 0;
        }
    }
    private static AxisAlignedBB rotate(AxisAlignedBB box, int metadata) {
        // getRotation(UP) in BlockDummyable adds a quarter-turn not present in this renderer.
        ForgeDirection rotation;
        switch(modelYaw(metadata)) {
            case 180: rotation = ForgeDirection.SOUTH; break;
            case 270: rotation = ForgeDirection.EAST; break;
            case 90: rotation = ForgeDirection.WEST; break;
            default: rotation = ForgeDirection.NORTH;
        }
        return getAABBRotationOffset(box, 0.5, 0, 0.5, rotation);
    }
    @Override public int[] getDimensions() {
        if(kind == Kind.HANGING) return new int[] {4, 0, 1, 1, 1, 1};
        if(kind == Kind.GUILLOTINE) return new int[] {3, 0, 1, 1, 1, 1};
        if(kind == Kind.BULL) return new int[] {3, 0, 2, 2, 1, 1};
        if(kind == Kind.TABLE) return new int[] {2, 0, 1, 1, 1, 1};
        return new int[] {2, 0, 1, 1, 1, 1};
    }
    @Override public int getOffset() { return kind == Kind.CHAIR ? 0 : kind == Kind.BULL ? 2 : 1; }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        if(meta >= 12) return new TileEntityRestraintDevice();
        return null;
    }
    private int[] findDeviceCore(IBlockAccess world, int x, int y, int z) {
        // Same dummy links as findCore, also usable with the renderer's ChunkCache.
        // The longest path in these devices is shorter than sixteen blocks.
        for(int steps = 0; steps < 16 && world.getBlock(x, y, z) == this; ++steps) {
            int meta = world.getBlockMetadata(x, y, z);
            if(meta >= 12) return new int[] {x, y, z};
            ForgeDirection dir = ForgeDirection.getOrientation(meta >= extra ? meta - extra : meta).getOpposite();
            if(dir == ForgeDirection.UNKNOWN) return null;
            x += dir.offsetX; y += dir.offsetY; z += dir.offsetZ;
        }
        return null;
    }

    private List<AxisAlignedBB> cellBoxes(IBlockAccess world, int x, int y, int z) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        int[] core = findDeviceCore(world, x, y, z);
        if(core == null) return boxes;
        int meta = world.getBlockMetadata(core[0], core[1], core[2]);
        for(AxisAlignedBB box : rotatedBounds.get(meta - 12)) addCellBox(boxes, box, core, x, y, z);
        TileEntity tile = world.getTileEntity(core[0], core[1], core[2]);
        if(!(tile instanceof TileEntityRestraintDevice)) return boxes;
        TileEntityRestraintDevice device = (TileEntityRestraintDevice) tile;
        // Retain the existing open-hatch passability. An unlocked bull door swings outside
        // the reserved footprint; do not emit an oversized box from a neighboring dummy.
        if(kind == Kind.BULL && device.locked) {
            addCellBox(boxes, rotate(modelBox(-13, 10, 22, 26, 31, 2), meta), core, x, y, z);
            addCellBox(boxes, rotate(modelBox(6, 19, 24, 4, 3, 2), meta), core, x, y, z);
        } else if(kind == Kind.HANGING && !device.active) {
            addCellBox(boxes, rotate(modelBox(-9, 13, -9, 18, 3, 18), meta), core, x, y, z);
        } else if(kind == Kind.GUILLOTINE) {
            float cycle = world instanceof World && ((World) world).isRemote ? device.animationCycle(0) : device.cycle;
            float bladeY = 47 - (device.active ? cycle < 5 ? cycle / 5F * 19 : cycle < 12 ? 19 : Math.max(0, (45 - cycle) / 33F * 19) : 0);
            addCellBox(boxes, rotate(modelBox(-13, bladeY, -1, 26, 7, 2), meta), core, x, y, z);
            for(int i = 0; i < 6; ++i)
                addCellBox(boxes, rotate(modelBox(-13 + i * 4, bladeY - i, -1, 5, 2 + i, 2), meta), core, x, y, z);
        } else if(kind == Kind.TABLE) {
            float cycle = world instanceof World && ((World) world).isRemote ? device.animationCycle(0) : device.cycle;
            float injectorX = device.active ? (float) -Math.sin(Math.min(1, cycle / 30F) * Math.PI) * 4 : 0;
            addCellBox(boxes, rotate(modelBox(11 + injectorX, 19, -3, 7, 2, 2), meta), core, x, y, z);
            addCellBox(boxes, rotate(modelBox(8 + injectorX, 19.5, -2.5, 4, 1, 1), meta), core, x, y, z);
        }
        return boxes;
    }

    private static void addCellBox(List<AxisAlignedBB> boxes, AxisAlignedBB box, int[] core, int x, int y, int z) {
        // World queries only the cells near the entity/ray. Each occupied dummy owns its
        // clipped part, so no collision depends on the core being in that query's range.
        double minX = Math.max(x, core[0] + box.minX), maxX = Math.min(x + 1, core[0] + box.maxX);
        double minY = Math.max(y, core[1] + box.minY), maxY = Math.min(y + 1, core[1] + box.maxY);
        double minZ = Math.max(z, core[2] + box.minZ), maxZ = Math.min(z + 1, core[2] + box.maxZ);
        if(minX < maxX && minY < maxY && minZ < maxZ)
            boxes.add(AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /** Add only the formerly unreserved table support tips when loading an existing device. */
    public boolean ensureCollisionCells(World world, int x, int y, int z) {
        if(kind != Kind.TABLE) return true;
        for(int dx = -1; dx <= 1; ++dx) for(int dz = -1; dz <= 1; ++dz)
            if(!world.blockExists(x + dx, y + 2, z + dz)) return false;
        int meta = world.getBlockMetadata(x, y, z);
        for(int dx = -1; dx <= 1; ++dx) for(int dz = -1; dz <= 1; ++dz) {
            int bx = x + dx, by = y + 2, bz = z + dz;
            if(!world.blockExists(bx, by, bz) || world.getBlock(bx, by - 1, bz) != this
                    || !world.getBlock(bx, by, bz).isReplaceable(world, bx, by, bz)) continue;
            for(AxisAlignedBB box : rotatedBounds.get(meta - 12)) {
                if(box.minX < dx + 1 && box.maxX > dx && box.minY < 3 && box.maxY > 2
                        && box.minZ < dz + 1 && box.maxZ > dz) {
                    world.setBlock(bx, by, bz, this, ForgeDirection.UP.ordinal(), 3);
                    break;
                }
            }
        }
        return true;
    }

    @Override public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB entityBox, List list, net.minecraft.entity.Entity entity) {
        if(ignoresCollision(entity)) return;
        for(AxisAlignedBB box : cellBoxes(world, x, y, z)) if(entityBox.intersectsWith(box)) list.add(box);
    }

    private AxisAlignedBB cellEnvelope(IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB envelope = null;
        for(AxisAlignedBB box : cellBoxes(world, x, y, z)) envelope = envelope == null ? box : envelope.func_111270_a(box);
        return envelope;
    }
    @Override public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        // Envelope for single-box consumers; native movement uses the multipart list above.
        return cellEnvelope(world, x, y, z);
    }
    @Override public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB box = cellEnvelope(world, x, y, z);
        if(box == null) setBlockBounds(0, 0, 0, 0, 0, 0);
        else setBlockBounds((float) (box.minX - x), (float) (box.minY - y), (float) (box.minZ - z),
                (float) (box.maxX - x), (float) (box.maxY - y), (float) (box.maxZ - z));
    }
    @Override public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        MovingObjectPosition closest = null;
        for(AxisAlignedBB box : cellBoxes(world, x, y, z)) {
            MovingObjectPosition hit = box.calculateIntercept(start, end);
            if(hit != null && (closest == null || start.squareDistanceTo(hit.hitVec) < start.squareDistanceTo(closest.hitVec)))
                closest = new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
        }
        return closest;
    }
    @Override @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        AxisAlignedBB box = cellEnvelope(world, x, y, z);
        return box == null ? AxisAlignedBB.getBoundingBox(x, y, z, x, y, z) : box;
    }
    @Override @SideOnly(Side.CLIENT)
    public void drawHighlight(DrawBlockHighlightEvent event, World world, int x, int y, int z) {
        EntityPlayer player = event.player;
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;
        ICustomBlockHighlight.setup();
        for(AxisAlignedBB box : cellBoxes(world, x, y, z))
            RenderGlobal.drawOutlinedBoundingBox(box.expand(0.002, 0.002, 0.002).getOffsetBoundingBox(-px, -py, -pz), -1);
        ICustomBlockHighlight.cleanup();
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if(world.isRemote) return true;
        int[] core = findCore(world, x, y, z);
        if(core == null) return false;
        TileEntity tile = world.getTileEntity(core[0], core[1], core[2]);
        return tile instanceof TileEntityRestraintDevice && ((TileEntityRestraintDevice) tile).interact(player);
    }
    @Override public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if(!world.isRemote && tile instanceof TileEntityRestraintDevice) ((TileEntityRestraintDevice) tile).destroy();
        super.breakBlock(world, x, y, z, block, meta);
    }
    @Override public int getLightValue(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return (kind == Kind.CHAIR || kind == Kind.BULL) && tile instanceof TileEntityRestraintDevice && ((TileEntityRestraintDevice) tile).active ? 10 : 0;
    }
    @Override public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("tile.restraint_device.desc"));
        lines.add(StatCollector.translateToLocal(getUnlocalizedName() + ".desc"));
    }
}
