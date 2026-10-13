package com.hbm.realsim;

import java.math.BigInteger;

/** A single source budget split into useful output, recoverable heat, and rejected losses. */
public final class CombustionLedger {
    public final long chemicalMicrojoules;
    public final long usefulMicrojoules;
    public final long recoverableHeatMicrojoules;
    public final long rejectedMicrojoules;
    public CombustionLedger(long chemical, int usefulPpm, int recoverableHeatPpm) {
        if(chemical < 0 || usefulPpm < 0 || recoverableHeatPpm < 0 || usefulPpm > 1_000_000 - recoverableHeatPpm) throw new IllegalArgumentException("Combustion budget exceeds input");
        chemicalMicrojoules = chemical;
        usefulMicrojoules = fraction(chemical, usefulPpm);
        recoverableHeatMicrojoules = fraction(chemical, recoverableHeatPpm);
        rejectedMicrojoules = chemical - usefulMicrojoules - recoverableHeatMicrojoules;
    }
    private static long fraction(long value, int ppm) { return BigInteger.valueOf(value).multiply(BigInteger.valueOf(ppm)).divide(BigInteger.valueOf(1_000_000L)).longValueExact(); }
}
