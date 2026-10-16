package api.hbm.tile;

/** Calibrated non-nuclear process heat: existing TU = 0.5 J, with a declared temperature ceiling. */
public interface IProcessHeatSource extends IHeatSource {
    /** Native heater infrastructure outputs through its top contact, not arbitrary adjacent faces. */
    default boolean canSupplyProcessHeat(net.minecraftforge.common.util.ForgeDirection side) {
        return side == net.minecraftforge.common.util.ForgeDirection.UP;
    }
    int getProcessTemperatureC();
    /** Qualified adapters may expose a transfer rating separately from stored energy. */
    default long getRatedProcessHeatWatts() { return 0; }
    default com.hbm.realsim.OutputBudget getProcessHeatBudget() { return null; }
    default long getAvailableProcessHeatQuanta(long tick) {
        com.hbm.realsim.OutputBudget budget = getProcessHeatBudget();
        return Math.max(0, Math.min(getHeatStored(), budget == null ? getHeatStored() : budget.available(tick, getRatedProcessHeatWatts() / 10)));
    }
    default long drawProcessHeatQuanta(long tick, long requested) {
        long amount = Math.min(Math.max(0, requested), getAvailableProcessHeatQuanta(tick));
        com.hbm.realsim.OutputBudget budget = getProcessHeatBudget();
        if(budget != null) budget.use(tick, getRatedProcessHeatWatts() / 10, amount);
        if(amount > 0) useUpHeat(Math.toIntExact(amount));
        return amount;
    }
}
