package com.hbm.blocks.machine;

import java.util.List;

import com.hbm.handler.restraint.Restraints;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.machine.TileEntityWoodchipper;
import com.hbm.util.ItemStackUtil;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class MachineWoodchipper extends BlockContainer {
    private static final ForgeDirection[] FRONTS = {ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.NORTH, ForgeDirection.EAST};
    public enum Finish {
        HOUSING("machine_shredder_side_alt"), STEEL("block_steel"), HAZARD("concrete_colored_ext.hazard"),
        PANEL("machine_panel_base"), CONNECTOR("block_insulator_side");

        private final String texture;
        Finish(String texture) { this.texture = texture; }
    }

    public static final class Part {
        public final Finish finish;
        public final int x, y, z, xMax, yMax, zMax;

        public Part(Finish finish, int x, int y, int z, int xMax, int yMax, int zMax) {
            this.finish = finish;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xMax = xMax;
            this.yMax = yMax;
            this.zMax = zMax;
        }

        private AxisAlignedBB bounds(int rotation) {
            switch(rotation) {
                case 1: return box(16 - zMax, y, x, 16 - z, yMax, xMax);
                case 2: return box(16 - xMax, y, 16 - zMax, 16 - x, yMax, 16 - z);
                case 3: return box(z, y, 16 - xMax, zMax, yMax, 16 - x);
                default: return box(x, y, z, xMax, yMax, zMax);
            }
        }

        private static AxisAlignedBB box(int x, int y, int z, int xMax, int yMax, int zMax) {
            return AxisAlignedBB.getBoundingBox(x / 16D, y / 16D, z / 16D, xMax / 16D, yMax / 16D, zMax / 16D);
        }
    }

    // Local +Z is the discharge end. The hopper rim and chute have actual openings.
    public static final Part[] FRAME = {
        new Part(Finish.STEEL, 0, 0, 1, 16, 2, 15),
        new Part(Finish.HOUSING, 1, 2, 2, 15, 9, 12),
        new Part(Finish.CONNECTOR, 6, 3, 0, 10, 7, 2),
        new Part(Finish.PANEL, 12, 4, 12, 15, 9, 13),
        new Part(Finish.HAZARD, 1, 4, 12, 4, 6, 13),
        new Part(Finish.STEEL, 4, 3, 12, 12, 4, 16),
        new Part(Finish.STEEL, 4, 4, 12, 5, 7, 16),
        new Part(Finish.STEEL, 11, 4, 12, 12, 7, 16),
        new Part(Finish.HOUSING, 4, 7, 12, 12, 8, 16),
        new Part(Finish.STEEL, 2, 9, 2, 4, 12, 12),
        new Part(Finish.STEEL, 12, 9, 2, 14, 12, 12),
        new Part(Finish.STEEL, 4, 9, 2, 12, 12, 4),
        new Part(Finish.STEEL, 4, 9, 10, 12, 12, 12),
        new Part(Finish.HAZARD, 0, 12, 1, 2, 16, 13),
        new Part(Finish.HAZARD, 14, 12, 1, 16, 16, 13),
        new Part(Finish.HOUSING, 2, 12, 1, 14, 16, 3),
        new Part(Finish.HOUSING, 2, 12, 11, 14, 16, 13)
    };

    private static final AxisAlignedBB[][] COLLISION = new AxisAlignedBB[4][FRAME.length];
    static {
        for(int rotation = 0; rotation < COLLISION.length; rotation++) {
            for(int i = 0; i < FRAME.length; i++) COLLISION[rotation][i] = FRAME[i].bounds(rotation);
        }
    }

    @SideOnly(Side.CLIENT)
    private IIcon[] finishes;

    public MachineWoodchipper() {
        super(Material.iron);
        setHardness(5F);
        setResistance(10F);
        setStepSound(soundTypeMetal);
    }

    public static ForgeDirection powerSide(int meta) { return FRONTS[meta & 3].getOpposite(); }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityWoodchipper(); }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int getRenderType() { return -1; }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int rotation = (MathHelper.floor_double(placer.rotationYaw / 90F + 0.5F) + 2) & 3;
        world.setBlockMetadataWithNotify(x, y, z, rotation, 2);
    }

    @Override @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        finishes = new IIcon[Finish.values().length];
        for(Finish finish : Finish.values()) finishes[finish.ordinal()] = register.registerIcon("hbm:" + finish.texture);
        blockIcon = getFinish(Finish.HOUSING);
    }

    @SideOnly(Side.CLIENT)
    public IIcon getFinish(Finish finish) { return finishes[finish.ordinal()]; }

    @Override public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List boxes, Entity entity) {
        for(AxisAlignedBB local : COLLISION[world.getBlockMetadata(x, y, z) & 3]) {
            AxisAlignedBB box = local.getOffsetBoundingBox(x, y, z);
            if(mask.intersectsWith(box)) boxes.add(box);
        }
    }

    @Override public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        MovingObjectPosition closest = null;
        for(AxisAlignedBB local : COLLISION[world.getBlockMetadata(x, y, z) & 3]) {
            MovingObjectPosition hit = local.getOffsetBoundingBox(x, y, z).calculateIntercept(start, end);
            if(hit != null && (closest == null || start.squareDistanceTo(hit.hitVec) < start.squareDistanceTo(closest.hitVec)))
                closest = new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
        }
        return closest;
    }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if(!(tile instanceof TileEntityWoodchipper)) return false;
        if(world.isRemote) return true;
        if(Restraints.blocked(player)) return false;
        ItemStack held = player.getHeldItem();
        if((held != null && held.getItem() == Items.lead) || (held == null && player.isSneaking()))
            return ((TileEntityWoodchipper) tile).interact(player);
        FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
        return true;
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if(!world.isRemote && tile instanceof TileEntityWoodchipper) {
            ((TileEntityWoodchipper) tile).destroy();
            ItemStackUtil.spillItems(world, x, y, z, block, world.rand);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
