package com.hbm.items.special;

import java.util.List;
import com.hbm.config.RestraintConfig;
import com.hbm.handler.restraint.RestraintState;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.ModItems;
import net.minecraft.entity.Entity;
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
        if(this != ModItems.injection_terminal || world.isRemote) return stack;
        RestraintState s = RestraintState.get(player);
        if(!RestraintConfig.enabled || !RestraintConfig.players) {
            s.injectionConfirmStack = null;
            Restraints.message(player, "restraint.self_disabled"); return stack;
        }
        if(!player.isSneaking()) { s.injectionConfirmStack = null; return stack; }
        if(!Restraints.compatible(player) || Restraints.blocked(player) || player.isUsingItem() || s.terminal > 0
                || getMovingObjectPositionFromPlayer(world, player, false) != null) return stack;
        long now = world.getTotalWorldTime();
        if(s.injectionConfirmStack != stack || s.injectionConfirmDimension != world.provider.dimensionId || now > s.injectionConfirmUntil) {
            s.injectionConfirmStack = stack; s.injectionConfirmUntil = now + 60;
            s.injectionConfirmDimension = world.provider.dimensionId; s.injectionConfirmReleased = false;
            Restraints.message(player, "restraint.self_confirm"); return stack;
        }
        if(!s.injectionConfirmReleased) { Restraints.message(player, "restraint.self_confirm"); return stack; }
        s.injectionConfirmStack = null;
        if(Restraints.inject(player)) {
            world.playSoundAtEntity(player, "random.fizz", 0.4F, 1F);
            // Native item use restores Creative stacks; do not manufacture empty syringes there.
            if(!player.capabilities.isCreativeMode) {
                Restraints.consume(player, stack);
                ItemStack empty = new ItemStack(ModItems.syringe_empty);
                // ItemInWorldManager replaces the held slot with this return value.
                if(stack.stackSize <= 0) return empty;
                Restraints.give(player, empty);
            }
        }
        return stack;
    }
    @Override public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if(this != ModItems.injection_terminal || world.isRemote || !(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        RestraintState s = RestraintState.get(player);
        if(s.injectionConfirmStack != stack) return;
        if(!held || player.getHeldItem() != stack || !Restraints.compatible(player) || Restraints.blocked(player)
                || s.terminal > 0 || s.injectionConfirmDimension != world.provider.dimensionId || world.getTotalWorldTime() > s.injectionConfirmUntil)
            s.injectionConfirmStack = null;
        else if(!player.isSneaking()) s.injectionConfirmReleased = true;
    }
    @Override public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal(getUnlocalizedName() + ".desc"));
    }
}
