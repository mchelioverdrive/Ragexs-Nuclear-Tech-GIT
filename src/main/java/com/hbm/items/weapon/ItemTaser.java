package com.hbm.items.weapon;

import java.util.List;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import com.hbm.config.RestraintConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public final class ItemTaser extends Item implements IBatteryItem {
    public static final long DISCHARGE_QUANTA = EnergyUnits.joulesToQuanta(50);

    public ItemTaser() {
        setMaxStackSize(1);
    }

    @Override
    public long getEnergyCapacityQuanta(ItemStack stack) {
        return RestraintConfig.taser.capacity * DISCHARGE_QUANTA;
    }

    private long rawCharge(ItemStack stack) {
        return stack.hasTagCompound() ? EnergyUnits.readEnergyQuanta(stack.getTagCompound(), "charge") : 0;
    }

    @Override
    public long getStoredEnergyQuanta(ItemStack stack) {
        return Math.max(0, Math.min(rawCharge(stack), getEnergyCapacityQuanta(stack)));
    }

    @Override
    public void setStoredEnergyQuanta(ItemStack stack, long quanta) {
        if(!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        long capacity = getEnergyCapacityQuanta(stack);
        NBTTagCompound data = stack.getTagCompound();
        EnergyUnits.writeEnergyQuanta(data, Math.max(0, Math.min(quanta, capacity)));
        EnergyUnits.writeCapacityQuanta(data, capacity);
    }

    @Override
    public void receiveEnergyQuanta(ItemStack stack, long quanta) {
        long stored = getStoredEnergyQuanta(stack);
        if(quanta > 0) setStoredEnergyQuanta(stack, stored + Math.min(quanta, getEnergyCapacityQuanta(stack) - stored));
    }

    @Override
    public void extractEnergyQuanta(ItemStack stack, long quanta) {
        if(quanta > 0) setStoredEnergyQuanta(stack, getStoredEnergyQuanta(stack) - quanta);
    }

    @Override public long getMaxInputQuantaPerTick() { return DISCHARGE_QUANTA; }
    @Override public long getMaxOutputQuantaPerTick() { return 0; }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if(!world.isRemote && (!stack.hasTagCompound()
                || EnergyUnits.readCapacityQuanta(stack.getTagCompound(), "capacity") != getEnergyCapacityQuanta(stack))) {
            setStoredEnergyQuanta(stack, rawCharge(stack));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        long capacity = stack.hasTagCompound() ? EnergyUnits.readCapacityQuanta(stack.getTagCompound(), "capacity") : 0;
        if(capacity <= 0) capacity = getEnergyCapacityQuanta(stack);
        long stored = Math.max(0, Math.min(rawCharge(stack), capacity));
        lines.add(StatCollector.translateToLocal("item.taser.desc"));
        lines.add(StatCollector.translateToLocalFormatted("item.taser.charge", EnergyUnits.formatJoules(stored),
                EnergyUnits.formatJoules(capacity), stored / DISCHARGE_QUANTA, capacity / DISCHARGE_QUANTA));
    }
}
