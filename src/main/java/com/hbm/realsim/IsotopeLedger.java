package com.hbm.realsim;

import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.util.RTGUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Three native billets are 2 kg of source; cladding is not isotope mass.
 * Thermal coefficients are adopted heat-source rows, separate from thermoelectric conversion.
 * Native decay timing and optional configured power scaling remain owned by ItemRTGPellet.
 */
public final class IsotopeLedger {
    public static final int THERMOELECTRIC_PPM = 50000;
    private long tick = Long.MIN_VALUE, remainder, thermalRemainder;
    public long thermalQuanta, electricQuanta, rejectedQuanta, processQuanta;
    public static long freshThermalWatts(ItemRTGPellet pellet) {
        if(pellet == ModItems.pellet_rtg) return 1140; // 2 kg Pu-238 at 0.57 W/g
        if(pellet == ModItems.pellet_rtg_weak) return 380; // One Pu billet, two U-238 billets
        if(pellet == ModItems.pellet_rtg_tm170) return 27000; // 13.5 W/g
        if(pellet == ModItems.pellet_rtg_polonium) return 280000;
        if(pellet == ModItems.pellet_rtg_americium) return 229;
        if(pellet == ModItems.pellet_rtg_strontium) return 1860; // Sr/Y equilibrium heat
        if(pellet == ModItems.pellet_rtg_cobalt) return 35000;
        if(pellet == ModItems.pellet_rtg_promethium) return 666;
        if(pellet == ModItems.pellet_rtg_radium) return 56;
        // Uncalibrated specialty/exotic sources retain their relative native source strength,
        // calibrated to Pu-238. They are explicitly nominal, not a nuclear-decay calculation.
        return Math.max(0, pellet.getHeat()) * 114L;
    }
    public static long thermalWatts(ItemStack stack) {
        if(stack == null || !(stack.getItem() instanceof ItemRTGPellet)) return 0;
        ItemRTGPellet pellet = (ItemRTGPellet) stack.getItem();
        return freshThermalWatts(pellet) * Math.max(0, RTGUtil.getPower(pellet, stack)) / Math.max(1, pellet.getHeat());
    }
    public static long thermalWatts(ItemStack[] inventory, int[] slots) {
        long total = 0;
        for(int slot : slots) total = Math.addExact(total, thermalWatts(inventory[slot]));
        return total;
    }
    public boolean begin(long now) { return now > tick; }
    /** All useful outputs spend this tick's source once. Unused heat is rejected, never banked. */
    public long convert(long now, long thermalWatts, long directProcessQuanta, long room, long ratingQuanta) {
        if(now <= tick) return 0;
        tick = now;
        long heat = Math.addExact(Math.max(0, thermalWatts), thermalRemainder);
        thermalQuanta = heat / 10;
        thermalRemainder = heat % 10; // less than 0.5 J, never an autonomy store
        processQuanta = Math.min(thermalQuanta, Math.max(0, directProcessQuanta));
        long numerator = (thermalQuanta - processQuanta) * THERMOELECTRIC_PPM + remainder;
        long potential = numerator / 1000000;
        remainder = numerator % 1000000;
        electricQuanta = Math.min(potential, Math.min(Math.max(0, room), Math.max(0, ratingQuanta)));
        rejectedQuanta = thermalQuanta - processQuanta - electricQuanta;
        return electricQuanta;
    }
    public void read(NBTTagCompound tag) {
        tick = tag.hasKey("isotopeTick") ? tag.getLong("isotopeTick") : Long.MIN_VALUE;
        remainder = Math.floorMod(tag.getLong("isotopeRemainder"), 1000000);
        thermalRemainder = Math.floorMod(tag.getLong("isotopeThermalRemainder"), 10);
    }
    public void write(NBTTagCompound tag) { tag.setLong("isotopeTick", tick); tag.setLong("isotopeRemainder", remainder); tag.setLong("isotopeThermalRemainder", thermalRemainder); }
    /** Visual-only status; these fields never restore source ownership. */
    public void writeStatus(NBTTagCompound tag) {
        tag.setLong("isotopeHeat", thermalQuanta); tag.setLong("isotopeElectric", electricQuanta);
        tag.setLong("isotopeRejected", rejectedQuanta); tag.setLong("isotopeProcess", processQuanta);
    }
    public void readStatus(NBTTagCompound tag) {
        thermalQuanta = tag.getLong("isotopeHeat"); electricQuanta = tag.getLong("isotopeElectric");
        rejectedQuanta = tag.getLong("isotopeRejected"); processQuanta = tag.getLong("isotopeProcess");
    }
}
