package com.hbm.blocks.machine;

import java.util.List;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.machine.TileEntityRestraintDevice;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockRestraintDevice extends BlockDummyable implements ITooltipProvider {
    public enum Kind { GUILLOTINE, BULL, CHAIR, TABLE }
    public final Kind kind;
    public BlockRestraintDevice(Kind kind) {
        super(kind == Kind.GUILLOTINE ? Material.wood : Material.iron);
        this.kind = kind;
        setHardness(4F); setResistance(15F);
        if(kind == Kind.GUILLOTINE) {
            bounding.add(AxisAlignedBB.getBoundingBox(-1.4, 0, -1.4, 1.4, 0.2, 1.4));
            bounding.add(AxisAlignedBB.getBoundingBox(-1.1, 0.2, -0.2, -0.85, 3.8, 0.2));
            bounding.add(AxisAlignedBB.getBoundingBox(0.85, 0.2, -0.2, 1.1, 3.8, 0.2));
        } else if(kind == Kind.BULL) {
            bounding.add(AxisAlignedBB.getBoundingBox(-1.35, 0, -2.2, 1.35, 0.35, 2.2));
            bounding.add(AxisAlignedBB.getBoundingBox(-1.25, 0.35, -1.4, -1, 2.75, 1.4));
            bounding.add(AxisAlignedBB.getBoundingBox(1, 0.35, -1.4, 1.25, 2.75, 1.4));
            bounding.add(AxisAlignedBB.getBoundingBox(-1, 2.75, -1.4, 1, 3, 1.4));
            bounding.add(AxisAlignedBB.getBoundingBox(-1, 0.35, -1.5, 1, 2.75, -1.3));
        } else if(kind == Kind.TABLE) bounding.add(AxisAlignedBB.getBoundingBox(-0.65, 0, -1.4, 0.65, 0.85, 1.4));
        else bounding.add(AxisAlignedBB.getBoundingBox(-0.48, 0, -0.48, 0.48, 0.55, 0.48));
    }
    @Override public int[] getDimensions() {
        if(kind == Kind.GUILLOTINE) return new int[] {3, 0, 1, 1, 1, 1};
        if(kind == Kind.BULL) return new int[] {3, 0, 2, 2, 1, 1};
        if(kind == Kind.TABLE) return new int[] {1, 0, 1, 1, 1, 1};
        return new int[] {2, 0, 1, 1, 1, 1};
    }
    @Override public int getOffset() { return kind == Kind.CHAIR ? 0 : kind == Kind.BULL ? 2 : 1; }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        if(meta >= 12) return new TileEntityRestraintDevice();
        return null;
    }
    @Override public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB entityBox, List list, net.minecraft.entity.Entity entity) {
        super.addCollisionBoxesToList(world, x, y, z, entityBox, list, entity);
        if(kind != Kind.BULL) return;
        int[] core = findCore(world, x, y, z);
        if(core == null) return;
        TileEntity tile = world.getTileEntity(core[0], core[1], core[2]);
        if(!(tile instanceof TileEntityRestraintDevice) || !((TileEntityRestraintDevice) tile).locked) return;
        AxisAlignedBB hatch = getAABBRotationOffset(AxisAlignedBB.getBoundingBox(-0.9, 0.6, 1.3, 0.9, 2.75, 1.5),
                core[0] + 0.5, core[1], core[2] + 0.5, ForgeDirection.getOrientation(world.getBlockMetadata(core[0], core[1], core[2]) - offset).getRotation(ForgeDirection.UP));
        if(entityBox.intersectsWith(hatch)) list.add(hatch);
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
