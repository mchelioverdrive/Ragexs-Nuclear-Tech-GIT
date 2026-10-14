package com.hbm.blocks.machine;

import java.util.List;
import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.tileentity.machine.TileEntityMachineBlastFurnace;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Upstream footprint and ports, adapted to RTM's multiblock implementation. */
public class MachineBlastFurnace extends BlockDummyable implements com.hbm.blocks.ITooltipProvider {
    public MachineBlastFurnace() { super(Material.rock); }
    @Override public TileEntity createNewTileEntity(World world, int meta) {
        if(meta >= 12) return new TileEntityMachineBlastFurnace();
        return meta >= 6 ? new TileEntityProxyCombo().inventory().fluid() : null;
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        return standardOpenBehavior(world, x, y, z, player, 0);
    }
    @Override public int[] getDimensions() { return new int[] {6, 0, 1, 1, 1, 1}; }
    @Override public int getOffset() { return 1; }
    @Override public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int offset) {
        super.fillSpace(world, x, y, z, dir, offset);
        // RTM receives the placement origin; the core is one block back from it.
        x += dir.offsetX * offset; z += dir.offsetZ * offset;
        makeExtra(world, x + 1, y, z); makeExtra(world, x - 1, y, z);
        makeExtra(world, x, y, z + 1); makeExtra(world, x, y, z - 1);
        makeExtra(world, x + dir.offsetX, y + 3, z + dir.offsetZ);
        makeExtra(world, x + dir.offsetX, y + 5, z + dir.offsetZ); makeExtra(world, x, y + 6, z);
    }
    @Override public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if(tile instanceof TileEntityMachineBlastFurnace) ((TileEntityMachineBlastFurnace) tile).dropOwnedCharge();
        super.breakBlock(world, x, y, z, block, meta);
    }
    @Override public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean extended) {
        addStandardInfo(stack, player, list, extended);
        list.add(com.hbm.util.I18nUtil.resolveKey("tile.machine_blast_furnace.desc"));
        list.add(com.hbm.util.I18nUtil.resolveKey("tooltip.blast.infrastructure"));
    }
}
