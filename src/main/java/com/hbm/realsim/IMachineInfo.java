package com.hbm.realsim;

import java.util.List;

/** Native, read-only description of the owning machine's operational contract. */
public interface IMachineInfo {
    default long getMachineCoolingWatts() { return -1; }
    default long getProcessAuxiliaryWatts() { return 0; }
    default long getProcessAuxiliaryWatts(ProcessRecipe recipe) { return getProcessAuxiliaryWatts(); }
    default long getMachineDemandWatts() { return -1; }
    default long getMachineCurrentDrawWatts() { return -1; }
    default void appendMachineInfo(List<String> lines, boolean live) { }
}
