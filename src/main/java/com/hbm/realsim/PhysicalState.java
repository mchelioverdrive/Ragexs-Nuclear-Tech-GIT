package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

/** Physical inventory contract, independent of fluid traits and network pressure units. */
public final class PhysicalState {
    public enum Phase { SOLID, LIQUID, GAS, NOMINAL }
    public enum Basis { MATERIAL_ITEM, LIQUID_LITER, STANDARD_GAS_LITER, NOMINAL }
    public static final long MASS_UNITS_PER_KG = 22_711_000_000_000L;
    public final String substance;
    public final Phase phase;
    public final Basis basis;
    /** Exact adopted mass: one unit is 1 microgram / 22711. */
    public final long massUnits;
    /** Null means unresolved, never zero. Zero is reserved for known nonfuels. */
    public final BigDecimal lhvJoulesPerKg;
    public final String thermalReference;
    public final Map<String, BigDecimal> mixture;
    private final long chemicalMicrojoules;

    PhysicalState(String substance, Phase phase, Basis basis, long massUnits,
            BigDecimal lhv, String thermalReference, Map<String, BigDecimal> mixture) {
        this.substance = substance;
        this.phase = phase;
        this.basis = basis;
        this.massUnits = massUnits;
        this.lhvJoulesPerKg = lhv;
        this.thermalReference = thermalReference;
        this.mixture = mixture == null ? Collections.<String, BigDecimal>emptyMap() : Collections.unmodifiableMap(mixture);
        this.chemicalMicrojoules = massUnits <= 0 || lhv == null ? -1L : lhv.multiply(BigDecimal.valueOf(massUnits)).multiply(BigDecimal.valueOf(1_000_000L))
                .divide(BigDecimal.valueOf(MASS_UNITS_PER_KG), 0, RoundingMode.DOWN).longValueExact();
    }

    public boolean isResolved() { return massUnits > 0 && lhvJoulesPerKg != null; }
    public double kilogramsPerUnit() { if(massUnits <= 0) throw new IllegalStateException("Unresolved mass"); return (double) massUnits / MASS_UNITS_PER_KG; }
    public long chemicalMicrojoulesPerUnit() {
        if(!isResolved()) throw new IllegalStateException("Unresolved physical substance: " + substance);
        return chemicalMicrojoules;
    }
}
