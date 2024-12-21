package com.hbm.items.food;

import java.util.List;

import com.hbm.config.VersatileConfig;
import com.hbm.items.ModItems;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class ItemCanteen extends Item {

	public ItemCanteen(int cooldown) {

		//this.setMaxDamage(cooldown);
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int i, boolean b) {

		//if(stack.getItemDamage() > 0 && entity.ticksExisted % 20 == 0)
		//	stack.setItemDamage(stack.getItemDamage() - 1);
	}

	@Override
	public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {

		stack.setItemDamage(stack.getMaxDamage()); // Trigger cooldown.

		if (this == ModItems.canteen_vodka) {
			player.heal(2F);
			int intoxicationLevel = VersatileConfig.getIntoxicationLevel(player);

			// Effects scale with intoxication level
			player.addPotionEffect(new PotionEffect(Potion.confusion.id, 40 * 20 + intoxicationLevel * 20, 1));
			player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40 * 20 + intoxicationLevel * 10, 1));
			player.addPotionEffect(new PotionEffect(Potion.hunger.id, 5 * 10, 0));

			VersatileConfig.increaseIntoxication(player, 1); // Track drinking level.
		}

		// Sobriety mechanic
		VersatileConfig.applyPotionSickness(player, 5);

		// Reduce durability
		stack.setItemDamage(stack.getItemDamage() + 1);
		if (stack.getItemDamage() >= stack.getMaxDamage()) {
			stack.stackSize--; // Bottle becomes empty after all uses.
		}

		return stack;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack p_77626_1_) {
		return 10;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack p_77661_1_) {
		return EnumAction.drink;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (stack.getItemDamage() == 0 && !VersatileConfig.hasPotionSickness(player))
			player.setItemInUse(stack, this.getMaxItemUseDuration(stack));

		return stack;
	}

    @Override
	@SideOnly(Side.CLIENT)
    public void addInformation(ItemStack p_77624_1_, EntityPlayer p_77624_2_, List list, boolean p_77624_4_)
    {
    	if(this == ModItems.canteen_vodka)
    	{
			list.add("Cooldown: 3 minutes");
			list.add("Coping with pain using shitty substances.");
			list.add("May help you cope");
			list.add("");

    		if(MainRegistry.polaroidID == 11)
    			//list.add("Why sipp when you can succ?");
				list.add("Well I'm not gonna stop you, it's *your* drinking problem");
    		else
    			list.add("In soviet russia, vodka drinks you haha guys, please upvote my post!!!");
    	}
    	if(this == ModItems.canteen_fab)
    	{
			list.add("Cooldown: 2 minutes");
			list.add("Fun fact for all you gigachads out there, alcohol has estrogenic properties");
    	}
    }

}
