package com.hbm.items.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

/** Soyuz payload. The ID-Linker assigns the same mission ID to the payload and its receiver. */
public final class ItemDeepSpaceProbe extends Item {
	private static final String MISSION_ID = "deepSpaceProbeId";

	public static int getMissionId(ItemStack stack) {
		return stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger(MISSION_ID)) : 0;
	}

	public static void setMissionId(ItemStack stack, int id) {
		if(getMissionId(stack) == id) return;
		if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		if(id > 0) stack.getTagCompound().setInteger(MISSION_ID, id);
		else stack.getTagCompound().removeTag(MISSION_ID);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		int id = getMissionId(stack);
		lines.add(id > 0 ? StatCollector.translateToLocalFormatted("item.sat_deep_space_probe.id", id)
				: StatCollector.translateToLocal("item.sat_deep_space_probe.link"));
		lines.add(StatCollector.translateToLocal("item.sat_deep_space_probe.launch"));
	}
}
