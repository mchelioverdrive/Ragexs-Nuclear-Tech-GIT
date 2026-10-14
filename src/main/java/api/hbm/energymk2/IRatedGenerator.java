package api.hbm.energymk2;

import com.hbm.realsim.OutputBudget;
import com.hbm.lib.Library;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Aggregate nameplate across every network port and battery in the owning server tick. */
public interface IRatedGenerator extends IEnergyProviderMK2 {
    OutputBudget getOutputBudget();
    long getRatedOutputWatts();
    default long outputTick() { return ((TileEntity) this).getWorldObj().getTotalWorldTime(); }
    @Override default long getMaxOutputQuantaPerTick() {
        if(((TileEntity) this).getWorldObj() == null) return 0;
        return getOutputBudget().available(outputTick(), getRatedOutputWatts() / 10);
    }
    @Override default void extractEnergyQuanta(long amount) {
        if(amount == 0) return;
        if(((TileEntity) this).getWorldObj() == null) throw new IllegalStateException("Generator export requires an owning world");
        getOutputBudget().use(outputTick(), getRatedOutputWatts() / 10, amount);
        IEnergyProviderMK2.super.extractEnergyQuanta(amount);
    }
    default long chargeRatedItems(ItemStack[] slots, int slot, long capacity) {
        long stored = getStoredEnergyQuanta(), allowed = Math.min(stored, getMaxOutputQuantaPerTick());
        long left = Library.chargeItemsFromTE(slots, slot, allowed, capacity);
        long spent = allowed - left;
        getOutputBudget().use(outputTick(), getRatedOutputWatts() / 10, spent);
        return stored - spent;
    }
}
