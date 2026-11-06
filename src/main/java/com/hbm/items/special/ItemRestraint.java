package com.hbm.items.special;

import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Entity interactions are intercepted before villager trading, on the common Forge event path. */
public class ItemRestraint extends Item {
    public ItemRestraint(boolean reusable) { setMaxStackSize(reusable ? 1 : 16); }
    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Treat a handled entity click as successful on the client, avoiding a second use packet.
        return stack;
    }
    @Override public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal(getUnlocalizedName() + ".desc"));
    }
}
