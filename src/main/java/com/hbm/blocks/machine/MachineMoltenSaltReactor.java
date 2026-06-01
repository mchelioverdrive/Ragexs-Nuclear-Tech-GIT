package com.hbm.blocks.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.blocks.ModBlocks;
import com.hbm.main.MainRegistry;
import com.hbm.tileentity.machine.TileEntityMoltenSaltReactor;
import com.hbm.util.fauxpointtwelve.BlockPos;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

public class MachineMoltenSaltReactor extends BlockMachineBase implements ITooltipProvider, ILookOverlay {

	private static final int MAX_SIZE = 4096;
	private static final HashMap<BlockPos, Block> assembly = new HashMap<BlockPos, Block>();
	private static boolean errored;
	private static String errorMessage;

	public MachineMoltenSaltReactor(Material mat) {
		super(mat, -1);
		this.rotatable = true;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityMoltenSaltReactor();
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) {
			return true;
		} else if(!player.isSneaking()) {

			TileEntity tile = world.getTileEntity(x, y, z);
			if(tile instanceof TileEntityMoltenSaltReactor) {
				TileEntityMoltenSaltReactor reactor = (TileEntityMoltenSaltReactor) tile;

				if(reactor.assembled) {
					FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
				} else if(this.assemble(world, x, y, z, player)) {
					player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Molten Salt Reactor assembled."));
					FMLNetworkHandler.openGui(player, MainRegistry.instance, 0, world, x, y, z);
				}
			}

			return true;
		}

		return false;
	}

	private boolean assemble(World world, int x, int y, int z, EntityPlayer player) {

		assembly.clear();
		errored = false;
		errorMessage = null;

		ForgeDirection dir = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z)).getOpposite();
		floodFill(world, x + dir.offsetX, y, z + dir.offsetZ);

		if(assembly.isEmpty()) {
			errored = true;
			errorMessage = "Reactor structure required in front of controller";
		}

		int ports = 0;
		int channels = 0;
		int heatExchangers = 0;
		int sources = 0;
		ArrayList<BlockPos> portPositions = new ArrayList<BlockPos>();

		for(Entry<BlockPos, Block> entry : assembly.entrySet()) {
			Block block = entry.getValue();
			if(block == ModBlocks.pwr_port) {
				ports++;
				portPositions.add(entry.getKey());
			}
			if(block == ModBlocks.pwr_channel) channels++;
			if(block == ModBlocks.pwr_heatex) heatExchangers++;
			if(block == ModBlocks.pwr_neutron_source) sources++;
		}

		if(ports == 0) {
			errored = true;
			errorMessage = "Fluid ports required";
		}

		if(channels == 0) {
			errored = true;
			errorMessage = "Coolant channels required";
		}

		if(heatExchangers == 0) {
			errored = true;
			errorMessage = "Heat exchangers required";
		}

		if(sources == 0) {
			errored = true;
			errorMessage = "Neutron sources required";
		}

		TileEntity tile = world.getTileEntity(x, y, z);
		if(tile instanceof TileEntityMoltenSaltReactor) {
			TileEntityMoltenSaltReactor reactor = (TileEntityMoltenSaltReactor) tile;

			if(!errored) {
				reactor.setup(assembly.size(), ports, channels, heatExchangers, sources, portPositions);
			} else {
				reactor.assembled = false;
				player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "Molten Salt Reactor: " + errorMessage));
			}
		}

		assembly.clear();
		return !errored;
	}

	private void floodFill(World world, int x, int y, int z) {

		BlockPos pos = new BlockPos(x, y, z);

		if(assembly.containsKey(pos) || errored) return;
		if(assembly.size() >= MAX_SIZE) {
			errored = true;
			errorMessage = "Max size exceeded";
			return;
		}

		Block block = world.getBlock(x, y, z);

		if(isValidReactorBlock(block)) {
			assembly.put(pos, block);
			if(!isValidCasing(block)) {
				floodFill(world, x + 1, y, z);
				floodFill(world, x - 1, y, z);
				floodFill(world, x, y + 1, z);
				floodFill(world, x, y - 1, z);
				floodFill(world, x, y, z + 1);
				floodFill(world, x, y, z - 1);
			}
		}
	}

	private boolean isValidReactorBlock(Block block) {
		return isValidCasing(block) ||
				block == ModBlocks.pwr_channel ||
				block == ModBlocks.pwr_heatex ||
				block == ModBlocks.pwr_heatsink ||
				block == ModBlocks.pwr_neutron_source;
	}

	private boolean isValidCasing(Block block) {
		return block == ModBlocks.pwr_casing || block == ModBlocks.pwr_reflector || block == ModBlocks.pwr_port;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(EnumChatFormatting.YELLOW + "Scalable multiblock controller.");
		list.add(EnumChatFormatting.YELLOW + "Build from PWR casings, ports, channels, heat exchangers and neutron sources.");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void printHook(Pre event, World world, int x, int y, int z) {
		List<String> text = new ArrayList<String>();
		TileEntity tile = world.getTileEntity(x, y, z);

		if(tile instanceof TileEntityMoltenSaltReactor) {
			TileEntityMoltenSaltReactor reactor = (TileEntityMoltenSaltReactor) tile;
			text.add(reactor.assembled ? "Assembled" : "Not assembled");
			text.add("Blocks: " + reactor.reactorBlocks + "  Ports: " + reactor.portCount);
			text.add("Channels: " + reactor.channelCount + "  Heat Ex: " + reactor.heatExchangerCount);
			text.add("Input: " + reactor.tanks[0].getFill() + "/" + reactor.tanks[0].getMaxFill() + " mB");
			text.add("Output: " + reactor.tanks[1].getFill() + "/" + reactor.tanks[1].getMaxFill() + " mB");
			text.add("Rate: " + reactor.output + "/" + reactor.getMaxProcessRate() + " mB/t");
		} else {
			text.add("Build a PWR-style salt core in front.");
			text.add("Right click to assemble.");
		}

		ILookOverlay.printGeneric(event, "Molten Salt Reactor", 0xffa000, 0x402000, text);
	}
}
