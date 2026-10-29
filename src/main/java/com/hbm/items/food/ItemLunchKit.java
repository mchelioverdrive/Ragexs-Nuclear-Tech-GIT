package com.hbm.items.food;

import java.util.List;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.main.MainRegistry;
import com.hbm.util.AchievementHandler;
import com.hbm.util.I18nUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class ItemLunchKit extends ItemFood {

	private final boolean lunchly;

	public ItemLunchKit(boolean lunchly) {
		super(6, 0.5F, false);
		this.lunchly = lunchly;
	}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if(world.isRemote) return;

		HbmLivingProps.incrementAsbestos(player, 250);
		if(!(player instanceof EntityPlayerMP)) return;

		// Named statistics persist across deaths, reconnects and item-ID remapping.
		StatBase eaten = lunchly ? MainRegistry.statLunchlyEaten : MainRegistry.statLunchablesEaten;
		player.addStat(eaten, 1);
		if(((EntityPlayerMP) player).func_147099_x().writeStat(eaten) >= 100) {
			AchievementHandler.award(player, lunchly ? MainRegistry.achLunchly : MainRegistry.achLunchables);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		list.add(EnumChatFormatting.RED + "[" + I18nUtil.resolveKey("trait.asbestos") + "]");
	}
}
