package com.hbm.blocks.network;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.IAnalyzable;
import com.hbm.extprop.HbmPlayerProps;
import com.hbm.handler.HbmKeybinds;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemFluidIDMulti;
import com.hbm.tileentity.network.TileEntityPipeBaseNT;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class FluidDuctBase extends BlockContainer implements IBlockFluidDuct, IAnalyzable {

	public FluidDuctBase(Material mat) {
		super(mat);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityPipeBaseNT();
	}

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float fx, float fy, float fz) {
        if(player.getHeldItem() == null || !(player.getHeldItem().getItem() instanceof IItemFluidIdentifier)) return false;
        if(world.isRemote) return true;
        if(!player.canPlayerEdit(x, y, z, side, player.getHeldItem())) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if(!(tile instanceof TileEntityPipeBaseNT)) return false;
        TileEntityPipeBaseNT pipe = (TileEntityPipeBaseNT) tile;
        IItemFluidIdentifier identifier = (IItemFluidIdentifier) player.getHeldItem().getItem();
        FluidType selected = identifier.getType(world, x, y, z, player.getHeldItem());
        if(HbmPlayerProps.getData(player).getKeyPressed(HbmKeybinds.EnumKeybind.TOOL_ALT)
                && player.getHeldItem().getItem() instanceof ItemFluidIDMulti) {
            ItemFluidIDMulti.setType(player.getHeldItem(), pipe.getType(), true);
            world.playSoundAtEntity(player, "random.orb", 0.25F, 0.75F); return true;
        }
        FluidType[] next = pipe.toggledTypes(selected);
        if(next == null) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("Shared pipe is full (4 fluids). Click an assigned identifier to remove that fluid first."));
            return true;
        }
        if(player.isSneaking() || HbmPlayerProps.getData(player).getKeyPressed(HbmKeybinds.EnumKeybind.TOOL_CTRL))
            changeChannelsRecursively(world, x, y, z, pipe.getTypes(), next, 64, player);
        else pipe.setTypes(next);
        return true;
    }
    /** Only matching channel sets are changed, so a filtered branch stops a trunk edit. Loaded blocks only. */
    public static void changeChannelsRecursively(World world, int x, int y, int z, FluidType[] previous, FluidType[] next, int distance, EntityPlayer player) {
        if(world.isRemote || java.util.Arrays.equals(previous, next)) return;
        java.util.ArrayDeque<com.hbm.util.fauxpointtwelve.BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<com.hbm.util.fauxpointtwelve.BlockPos> visited = new java.util.HashSet<>();
        queue.add(new com.hbm.util.fauxpointtwelve.BlockPos(x, y, z));
        for(int depth = 0; depth <= distance && !queue.isEmpty(); depth++) {
            int count = queue.size();
            for(int step = 0; step < count; step++) {
                com.hbm.util.fauxpointtwelve.BlockPos pos = queue.removeFirst();
                if(!visited.add(pos) || !world.blockExists(pos.getX(), pos.getY(), pos.getZ())) continue;
                if(player != null && !player.canPlayerEdit(pos.getX(), pos.getY(), pos.getZ(), 0, player.getHeldItem())) continue;
                TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
                if(!(tile instanceof TileEntityPipeBaseNT) || !(world.getBlock(pos.getX(), pos.getY(), pos.getZ()) instanceof IBlockFluidDuct)) continue;
                TileEntityPipeBaseNT pipe = (TileEntityPipeBaseNT) tile;
                FluidType[] actual = pipe.getTypes();
                if(actual.length != previous.length) continue;
                boolean matches = true; for(FluidType fluid : previous) if(!pipe.carries(fluid)) matches = false;
                if(!matches) continue;
                pipe.setTypes(next);
                if(depth < distance) for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) queue.addLast(pos.offset(dir));
            }
        }
    }

	@Override
	public void changeTypeRecursively(World world, int x, int y, int z, FluidType prevType, FluidType type, int loopsRemaining) {
		
		TileEntity te = world.getTileEntity(x, y, z);
		
		if(te instanceof TileEntityPipeBaseNT) {
			TileEntityPipeBaseNT pipe = (TileEntityPipeBaseNT) te;
			
			if(pipe.getType() == prevType && pipe.getType() != type) {
				pipe.setType(type);
				
				if(loopsRemaining > 0) {
					for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
						Block b = world.getBlock(x, y, z);
						
						if(b instanceof IBlockFluidDuct) {
							((IBlockFluidDuct) b).changeTypeRecursively(world, x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ, prevType, type, loopsRemaining - 1);
						}
					}
				}
			}
		}
	}

	@Override
	public List<String> getDebugInfo(World world, int x, int y, int z) {
		
		TileEntity te = world.getTileEntity(x, y, z);
		
		if(te instanceof TileEntityPipeBaseNT) {
			TileEntityPipeBaseNT pipe = (TileEntityPipeBaseNT) te;
            List<String> debug = new ArrayList<>();
            for(FluidType type : pipe.getTypes()) {
                api.hbm.fluidmk2.FluidNetMK2 net = pipe.getFluidNet(type);
                if(net != null) debug.add(type.getLocalizedName() + ": " + net.links.size() + " links, " + net.receiverEntries.size() + " subscribers");
            }
            return debug;
		}
		
		return null;
	}
}
