package com.hbm.hazard.type;

import java.util.List;

import com.hbm.config.RadiationConfig;
import com.hbm.hazard.modifier.HazardModifier;
import com.hbm.util.ArmorRegistry;
import com.hbm.util.I18nUtil;
import com.hbm.util.ArmorRegistry.HazardClass;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;

public class HazardTypeAutism extends HazardTypeBase {
	//mirror mirror on the wall,  who is the most unfunny of them all, a bobacat a doctor named nostalgia, maybe even some ducks and digamma.

	@Override
	public void onUpdate(EntityLivingBase target, float level, ItemStack stack) {

		if(RadiationConfig.disableBlinding)
			return;

		if(!ArmorRegistry.hasProtection(target, 3, HazardClass.LIGHT)) {
			target.addPotionEffect(new PotionEffect(Potion.confusion.id, (int)Math.ceil(level), 0));
		}
	}

	@Override
	public void updateEntity(EntityItem item, float level) { }

	@Override
	public void addHazardInformation(EntityPlayer player, List list, float level, ItemStack stack, List<HazardModifier> modifiers) {
		list.add(EnumChatFormatting.GOLD + "[" + I18nUtil.resolveKey("trait.confusion") + "]"); // why must i live inside this world full of closed minds
	}

}
