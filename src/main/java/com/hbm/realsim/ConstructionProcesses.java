package com.hbm.realsim;

/** Weighed construction batches. Placed block geometry is not an inventory cubic-metre claim. */
public final class ConstructionProcesses {
    private ConstructionProcesses() { }
    public static ProcessRecipe concrete(int kind) {
        String[] solids = kind == 0 ? new String[] {"CEMENT_ITEM", "AGGREGATE_COARSE_ITEM", "AGGREGATE_FINE_ITEM"}
                : new String[] {"CEMENT_ITEM", "AGGREGATE_COARSE_ITEM", "AGGREGATE_FINE_ITEM", kind == 1 ? "ASBESTOS_ITEM" : "DU_BILLET_ITEM"};
        int[] amounts = kind == 0 ? new int[] {64, 1, 1} : new int[] {64, 1, 1, kind == 1 ? 1 : 2};
        long mass = ChemistryProcesses.mass("SALT_WATER", 16);
        for(int i = 0; i < solids.length; i++) mass += ChemistryProcesses.mass(solids[i], amounts[i]);
        return new ProcessRecipe("construction:concrete_" + kind, new String[] {"SALT_WATER"}, new int[] {16}, new int[] {0}, solids, amounts,
                // Building blocks use compressed gameplay parcels, not full vessel/containment masses.
                // New physical identities leave already paid legacy construction lots unchanged.
                new String[] {kind == 0 ? "BUILDING_CONCRETE_ITEM" : kind == 1 ? "BUILDING_FIBER_CONCRETE_ITEM" : "DUCRETE_ITEM"}, new long[] {mass}, kind == 2 ? 1600 : 800, 320000, 0, 20, true);
    }
    public static ProcessRecipe asphalt() {
        long mass = ChemistryProcesses.mass("AGGREGATE_COARSE_ITEM", 1) + ChemistryProcesses.mass("AGGREGATE_FINE_ITEM", 1) + ChemistryProcesses.mass("BITUMEN", 12);
        return new ProcessRecipe("construction:asphalt", new String[] {"BITUMEN"}, new int[] {12}, new int[] {0},
                new String[] {"AGGREGATE_COARSE_ITEM", "AGGREGATE_FINE_ITEM"}, new int[] {1, 1}, new String[] {"BUILDING_ASPHALT_ITEM"}, new long[] {mass},
                1200, 200000, 20800000, 160, true);
    }
    public static ProcessRecipe soil() {
        return new ProcessRecipe("construction:soil", new String[] {"FRESH_WATER"}, new int[] {4}, new int[] {0},
                new String[] {"AGGREGATE_COARSE_ITEM", "AMMONIUM_NITRATE_ITEM"}, new int[] {1, 1}, new String[] {"SOIL_ITEM"},
                new long[] {ChemistryProcesses.mass("AGGREGATE_COARSE_ITEM", 1) + ChemistryProcesses.mass("AMMONIUM_NITRATE_ITEM", 1) + ChemistryProcesses.mass("FRESH_WATER", 4)},
                1200, 120000, 0, 20, true).rebatch(8); // One output stack; 32 L/min water service.
    }
}
