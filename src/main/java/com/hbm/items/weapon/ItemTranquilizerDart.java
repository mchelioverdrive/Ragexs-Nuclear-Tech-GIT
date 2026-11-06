package com.hbm.items.weapon;

import com.hbm.config.RestraintConfig;
import com.hbm.entity.projectile.EntityTranquilizerDart;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.special.ItemRestraint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemTranquilizerDart extends ItemRestraint {
    public ItemTranquilizerDart() { super(false); }
    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if(world.isRemote || !RestraintConfig.enabled || Restraints.blocked(player) || player.isUsingItem()) return stack;
        if(world.spawnEntityInWorld(new EntityTranquilizerDart(world, player))) {
            Restraints.consume(player, stack);
            world.playSoundAtEntity(player, "hbm:weapon.dartShoot", 0.6F, 1.2F);
        }
        return stack;
    }
}
