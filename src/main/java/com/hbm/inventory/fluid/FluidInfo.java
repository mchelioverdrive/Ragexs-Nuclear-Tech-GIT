package com.hbm.inventory.fluid;

import java.util.List;
import java.util.Locale;
import com.hbm.realsim.PhysicalState;
import com.hbm.inventory.fluid.trait.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous_ART;

/** One quantity/assay convention for containers, selection icons and machine tanks. */
public final class FluidInfo {
    private FluidInfo() { }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static boolean isExpanded() {
        return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
    }

    public static String unit(FluidType type) {
        PhysicalState state = type.getPhysicalState();
        if(state.basis == PhysicalState.Basis.STANDARD_GAS_LITER) return "standard L";
        if(state.basis == PhysicalState.Basis.LIQUID_LITER) return "L";
        if(state.basis == PhysicalState.Basis.WATER_EQUIVALENT) return "water-equivalent units";
        if(type == Fluids.STEAM || type == Fluids.HOTSTEAM || type == Fluids.SUPERHOTSTEAM
                || type == Fluids.ULTRAHOTSTEAM || type == Fluids.SPENTSTEAM) return "nominal steam units";
        if(type == Fluids.NONE) return "L / standard L (fluid-dependent)";
        return "nominal units";
    }

    public static String amount(FluidType type, long amount) {
        return String.format(Locale.US, "%,d %s", amount, unit(type));
    }
    public static String pair(FluidType type, long amount, long capacity) {
        return String.format(Locale.US, "%,d / %,d %s", amount, capacity, unit(type));
    }

    public static String containerQuantity(FluidType type, long amount, long capacity) {
        return amount > 0 ? pair(type, amount, capacity) : "Capacity: " + amount(type, capacity);
    }

    public static String lhv(PhysicalState state) {
        if(!state.isResolved() || state.chemicalMicrojoulesPerUnit() <= 0) return null;
        String unit = state.basis == PhysicalState.Basis.STANDARD_GAS_LITER ? "standard L" : "L";
        return String.format(Locale.US, "LHV: %.3f MJ/%s", state.chemicalMicrojoulesPerUnit() / 1e12D, unit);
    }

    public static String storedEnergy(PhysicalState state, long amount) {
        if(amount <= 0 || !state.isResolved() || state.chemicalMicrojoulesPerUnit() <= 0) return null;
        // Floating presentation only; multiply after conversion to avoid integer overflow.
        return String.format(Locale.US, "Stored chemical energy: %.3f MJ", state.chemicalMicrojoulesPerUnit() / 1e12D * amount);
    }

    public static void appendPhysical(List<String> lines, FluidType type, boolean advanced) {
        PhysicalState state = type.getPhysicalState();
        if(type == Fluids.NONE) return;

        com.hbm.realsim.SteamLedger.State steam = com.hbm.realsim.SteamLedger.state(type.getName());
        if(steam != null && steam != com.hbm.realsim.SteamLedger.State.COLD) {
            lines.add(String.format(Locale.US, "Water-equivalent: %.3f kg/unit", steam.massPerUnit / 1000000D));
            lines.add(String.format(Locale.US, "State: %d �C / %.3f MPa absolute", steam.temperatureC, steam.pressurePa / 1000000D));
            lines.add(String.format(Locale.US, "Enthalpy: %.3f MJ/kg above cold feed", steam.enthalpyJPerKg / 1000000D));
            if(advanced) lines.add("Inventory counts are not gas liters; network PU is separate");
            return;
        }
        String phase = state.phase == PhysicalState.Phase.NOMINAL
                ? (type.hasTrait(FT_Gaseous.class) || type.hasTrait(FT_Gaseous_ART.class) ? "Gas" : "Unresolved phase")
                : state.phase.name().substring(0, 1) + state.phase.name().substring(1).toLowerCase(Locale.US);
        lines.add(phase);
        if(state.basis == PhysicalState.Basis.STANDARD_GAS_LITER) {
            lines.add("Standard-equivalent gas quantity");
            if(advanced) lines.add("Canonical gas reference: 0 °C / 1 bar; not vessel volume");
        } else if(state.basis == PhysicalState.Basis.NOMINAL) lines.add("Quantity basis: nominal; physical assay unresolved");
        lines.add("Temperature: " + type.temperature + " °C");
        String lhv = lhv(state);
        if(lhv != null) lines.add(lhv);
        if(advanced && state.isResolved()) lines.add("Reference state: " + state.thermalReference);
    }

    public static void appendStoredEnergy(List<String> lines, FluidType type, long amount) {
        com.hbm.realsim.SteamLedger.State steam = com.hbm.realsim.SteamLedger.state(type.getName());
        if(steam != null && steam != com.hbm.realsim.SteamLedger.State.COLD) {
            lines.add(String.format(Locale.US, "Stored water mass: %.3f kg", amount * (steam.massPerUnit / 1000000D)));
            lines.add(String.format(Locale.US, "State enthalpy: %.3f MJ", amount * (steam.massPerUnit / 1000000D) * (steam.enthalpyJPerKg / 1000000D)));
        }
        String energy = storedEnergy(type.getPhysicalState(), amount);
        if(energy != null) lines.add(energy);
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public static void appendContainer(List<String> lines, FluidType type, long amount, long capacity, int pressure) {
        if(type != Fluids.NONE) lines.add(type.getLocalizedName());
        lines.add(containerQuantity(type, amount, capacity));
        if(amount > capacity) lines.add("Legacy over-cap stock: drain before filling");
        if(pressure > 0) lines.add("Network pressure class: " + pressure + " PU (not physical bar)");
        if(type != Fluids.NONE) {
            type.addInfo(lines);
            appendStoredEnergy(lines, type, amount);
        }
    }
}
