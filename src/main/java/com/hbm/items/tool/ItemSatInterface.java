package com.hbm.items.tool;

import java.util.List;

import com.hbm.inventory.gui.GUIScreenSatCoord;
import com.hbm.inventory.gui.GUIScreenSatInterface;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemDeepSpaceProbe;
import com.hbm.items.machine.ItemSatChip;
import com.hbm.main.MainRegistry;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.SatPanelPacket;
import com.hbm.saveddata.SatelliteSavedData;
import com.hbm.saveddata.satellites.Satellite;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.world.gemini.GeminiProbeMissions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemSatInterface extends ItemSatChip implements IGUIProvider {
	
	@SideOnly(Side.CLIENT)
	public static Satellite currentSat;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		int id = ItemDeepSpaceProbe.getMissionId(stack);
		if(id == 0) {
			super.addInformation(stack, player, lines, advanced);
			return;
		}
		lines.add(StatCollector.translateToLocalFormatted("item.sat_deep_space_probe.id", id));
		lines.add(StatCollector.translateToLocal("item.sat_deep_space_probe.monitor"));
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		int probeId = ItemDeepSpaceProbe.getMissionId(stack);
		if(probeId > 0) {
			if(!world.isRemote) {
				for(String line : GeminiProbeMissions.telemetry(world, probeId, player.isSneaking()))
					player.addChatMessage(new ChatComponentText(line));
			}
			return stack;
		}
		
		if(world.isRemote) {

			player.openGui(MainRegistry.instance, 0, world, 0, 0, 0);
		}
		
		return stack;
	}
	
    public void onUpdate(ItemStack stack, World world, Entity entity, int i, boolean b) {
    	
    	if(world.isRemote || !(entity instanceof EntityPlayerMP))
    		return;
    	
    	if(((EntityPlayerMP)entity).getHeldItem() != stack)
    		return;

		if(ItemDeepSpaceProbe.getMissionId(stack) > 0)
			return;
    	
    	Satellite sat = SatelliteSavedData.getData(world).getSatFromFreq(this.getFreq(stack));
    	
    	if(sat != null && entity.ticksExisted % 2 == 0) {
    		PacketDispatcher.wrapper.sendTo(new SatPanelPacket(sat), (EntityPlayerMP) entity); //making this one sat that is static might not have been a good idea
    	}
    }

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		if(this == ModItems.sat_interface)
			return new GUIScreenSatInterface(player);
		else if(this == ModItems.sat_coord)
			return new GUIScreenSatCoord(player);
		
		return null;
	}

}
