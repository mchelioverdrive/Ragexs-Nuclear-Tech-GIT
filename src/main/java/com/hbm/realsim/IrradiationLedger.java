package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Natural-lithium target accounting. Neutron mass and nuclear mass defect are explicit external reactor terms. */
public final class IrradiationLedger {
    private IrradiationLedger() { }
    public static final class Target {
        public final long lithium, capturedNeutrons, nuclearMassDefect, retainedTarget, tritiumMass;
        public final int tritiumCounts;
        private Target(long feed) {
            lithium=feed;
            capturedNeutrons=ratio(feed,"0.076557667120605","6.941");
            long convertedLi6=ratio(feed,"0.45654782715366","6.941");
            long producedT=ratio(feed,"0.228918140352","6.941"),helium=ratio(feed,"0.303797586988467","6.941");
            nuclearMassDefect=convertedLi6+capturedNeutrons-producedT-helium;
            tritiumCounts=Math.toIntExact(producedT/PhysicalRegistry.get("TRITIUM").massUnits);
            tritiumMass=tritiumCounts*PhysicalRegistry.get("TRITIUM").massUnits;
            retainedTarget=feed+capturedNeutrons-nuclearMassDefect-tritiumMass;
        }
    }
    private static long ratio(long amount,String numerator,String denominator) { return BigDecimal.valueOf(amount).multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator),0,RoundingMode.DOWN).longValueExact(); }
    public static Target target(int kind) {return new Target(kind==1?9*PhysicalState.MASS_UNITS_PER_KG:kind==2?PhysicalState.MASS_UNITS_PER_KG/9:PhysicalState.MASS_UNITS_PER_KG);}
    static void registerAssays() {
        for(int i=0;i<3;i++) PhysicalRegistry.adopt("ITEM:spent_lithium_target@"+i,new PhysicalState("irradiated_lithium_target",PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,target(i).retainedTarget,BigDecimal.ZERO,"retained Li7, helium and sub-count tritium; never aliases fresh natural lithium",null));
    }
}
