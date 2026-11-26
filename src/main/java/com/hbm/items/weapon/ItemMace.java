package com.hbm.items.weapon;

import java.util.List;

import api.hbm.fluid.IFillableItem;
import com.hbm.config.MaceConfig;
import com.hbm.handler.MaceSpray;
import com.hbm.inventory.fluid.FluidType;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemMace extends Item implements IFillableItem {
    private final FluidType fluid;
    private final boolean bearMace;

    public ItemMace(FluidType fluid, boolean bearMace) {
        this.fluid = fluid;
        this.bearMace = bearMace;
        setMaxStackSize(1);
    }

    public ItemStack filledStack(int availableFluid) {
        ItemStack stack = new ItemStack(this);
        int capacity = settings().capacity;
        setFill(stack, Math.min(capacity, availableFluid), capacity);
        return stack;
    }

    private MaceConfig.Spray settings() {
        MaceConfig.Values values = MaceConfig.settings;
        return bearMace ? values.bearMace : values.mace;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Vanilla repeats right-click while held. Each accepted request emits one complete burst.
        if(!world.isRemote && player.getHeldItem() == stack && getFill(stack) > 0) {
            MaceConfig.Spray spray = settings();
            if(spray.enabled && MaceSpray.activate(player, spray))
                setFill(stack, getFill(stack) - 1, spray.capacity);
        }
        return stack;
    }

    @Override
    public boolean acceptsFluid(FluidType type, ItemStack stack) {
        return type == fluid;
    }

    @Override
    public int tryFill(FluidType type, int amount, ItemStack stack) {
        if(!acceptsFluid(type, stack) || amount <= 0) return amount;
        int capacity = settings().capacity;
        int stored = getFill(stack);
        int transferred = Math.min(amount, Math.max(0, capacity - stored));
        if(transferred > 0) setFill(stack, stored + transferred, capacity);
        return amount - transferred;
    }

    @Override public boolean providesFluid(FluidType type, ItemStack stack) { return false; }
    @Override public int tryEmpty(FluidType type, int amount, ItemStack stack) { return 0; }
    @Override public FluidType getFirstFluidType(ItemStack stack) { return getFill(stack) > 0 ? fluid : null; }

    @Override
    public int getFill(ItemStack stack) {
        return stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger("sprayFluid")) : 0;
    }

    private void setFill(ItemStack stack, int amount, int capacity) {
        if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("sprayFluid", amount);
        stack.getTagCompound().setInteger("sprayCapacity", capacity);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if(world.isRemote) return;
        int capacity = settings().capacity;
        if(!stack.hasTagCompound() || stack.getTagCompound().getInteger("sprayCapacity") != capacity)
            setFill(stack, getFill(stack), capacity);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(filledStack(settings().capacity));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int capacity = stack.hasTagCompound() ? stack.getTagCompound().getInteger("sprayCapacity") : 0;
        list.add(StatCollector.translateToLocalFormatted("item.mace.capacity",
                getFill(stack), capacity > 0 ? capacity : settings().capacity));
        list.add(StatCollector.translateToLocalFormatted("item.mace.refill", fluid.getLocalizedName()));
    }
}
