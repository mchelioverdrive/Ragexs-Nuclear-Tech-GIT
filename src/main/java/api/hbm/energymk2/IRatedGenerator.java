package api.hbm.energymk2;

import com.hbm.realsim.OutputBudget;
import com.hbm.lib.Library;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Aggregate nameplate across every network port and battery in the owning server tick. */
public interface IRatedGenerator extends IEnergyProviderMK2 {
    OutputBudget getOutputBudget();
    long getRatedOutputWatts();
    /** Export is independent of stored energy and can differ for legacy drain/storage devices. */
    default long getExportLimitWatts() { return getRatedOutputWatts(); }
    default long getIntentionalStorageQuanta() { return 0; }
    default long getTechnicalBufferQuanta() { return getIntentionalStorageQuanta() > 0 ? 0 : getEnergyCapacityQuanta(); }
    default long outputTick() { return ((TileEntity) this).getWorldObj().getTotalWorldTime(); }
    @Override default long getMaxOutputQuantaPerTick() {
        if(((TileEntity) this).getWorldObj() == null) return 0;
        return getOutputBudget().available(outputTick(), getExportLimitWatts() / 10);
    }
    @Override default void extractEnergyQuanta(long amount) {
        if(amount == 0) return;
        if(((TileEntity) this).getWorldObj() == null) throw new IllegalStateException("Generator export requires an owning world");
        getOutputBudget().use(outputTick(), getExportLimitWatts() / 10, amount);
        IEnergyProviderMK2.super.extractEnergyQuanta(amount);
    }
    default long chargeRatedItems(ItemStack[] slots, int slot, long capacity) {
        long stored = getStoredEnergyQuanta(), allowed = Math.min(stored, getMaxOutputQuantaPerTick());
        // The charging helper's last argument is an admission ceiling, not storage.
        // Passing capacity would truncate legacy over-cap contents before charging.
        long left = Library.chargeItemsFromTE(slots, slot, allowed, allowed);
        long spent = allowed - left;
        getOutputBudget().use(outputTick(), getExportLimitWatts() / 10, spent);
        return stored - spent;
    }
}
