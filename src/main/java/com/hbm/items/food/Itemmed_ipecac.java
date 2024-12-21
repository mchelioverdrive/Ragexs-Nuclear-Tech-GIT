package com.hbm.items.food;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.extprop.HbmPlayerProps;
import com.hbm.items.ModItems;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.potion.HbmPotion;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class Itemmed_ipecac extends ItemFood {
	public Itemmed_ipecac(int hunger) {
		super(hunger, false);
		this.setAlwaysEdible();
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 40;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		// Prevent default eating or drinking animations and sounds
		//no no we want the animation
		return EnumAction.drink;
	}

	//@Override
	//public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
	//	if (!world.isRemote) {
	//		player.addPotionEffect(new PotionEffect(Potion.hunger.id, 50, 49));
	//		player.addPotionEffect(new PotionEffect(HbmPotion.radaway.id, 1, 20));
	//	}
	//}

	public ItemStack onEaten(ItemStack stack, World worldObj, EntityPlayer player)
	{
		ItemStack sta = super.onEaten(stack, worldObj, player);

		return sta;
	}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		//add throwing up effect here
		//if(this == ModItems.med_ipecac || this == ModItems.med_ptsd) {
		player.addPotionEffect(new PotionEffect(Potion.hunger.id, 50, 49));
		int hungerLevel = player.getFoodStats().getFoodLevel();
		player.getFoodStats().setFoodLevel(Math.max(hungerLevel - 3, 0));
		if (hungerLevel > 0 && HbmLivingProps.getRadiation(player) < 600) {
			player.addPotionEffect(new PotionEffect(HbmPotion.radaway.id, 1, 20));
		}
			HbmPlayerProps props = HbmPlayerProps.getData(player);
			if (props.nitanCount > 0){
				player.removePotionEffect(HbmPotion.nitan.id);
				props.nitanCount = 0;
			}
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setString("type", "vomit");
		nbt.setString("mode", "normal");
		nbt.setInteger("count", 15);
		nbt.setInteger("entity", player.getEntityId());
		PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(nbt, 0, 0, 0),  new NetworkRegistry.TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 25));
		world.playSoundEffect(player.posX, player.posY, player.posZ, "hbm:player.vomit", 1.0F, 1.0F);
		//}

	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		return stack;
	}

}
