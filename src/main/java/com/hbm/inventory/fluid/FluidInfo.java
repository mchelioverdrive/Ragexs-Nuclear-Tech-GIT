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
        if(state.basis == PhysicalState.Basis.WATER_EQUIVALENT) return "steam units";
        if(type == Fluids.STEAM || type == Fluids.HOTSTEAM || type == Fluids.SUPERHOTSTEAM
                || type == Fluids.ULTRAHOTSTEAM || type == Fluids.SPENTSTEAM) return "steam units";
        if(type == Fluids.NONE) return "L / standard L (fluid-dependent)";
        return "units";
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
        String unit = state.basis == PhysicalState.Basis.STANDARD_GAS_LITER ? "standard L" : state.basis == PhysicalState.Basis.LIQUID_LITER ? "L" : "unit";
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
            lines.add(String.format(Locale.US, "Water content: %.3f kg/unit", steam.massPerUnit / 1000000D));
            lines.add(String.format(Locale.US, "State: %d °C / %.3f MPa absolute", steam.temperatureC, steam.pressurePa / 1000000D));
            lines.add(String.format(Locale.US, "Enthalpy: %.3f MJ/kg above cold feed", steam.enthalpyJPerKg / 1000000D));
            return;
        }
        String phase = state.phase == PhysicalState.Phase.NOMINAL
                ? (type.hasTrait(FT_Gaseous.class) || type.hasTrait(FT_Gaseous_ART.class) ? "Gas" : "Process fluid")
                : state.phase.name().substring(0, 1) + state.phase.name().substring(1).toLowerCase(Locale.US);
        lines.add(phase);
        if(state.basis == PhysicalState.Basis.STANDARD_GAS_LITER) {
            lines.add("Gas quantities: standard L (0 C, 1 bar)");
        }
        lines.add("Temperature: " + type.temperature + " °C");
        String lhv = lhv(state);
        if(lhv != null) lines.add(lhv);
        if(state.isResolved()) {
            if(state.basis == PhysicalState.Basis.LIQUID_LITER) lines.add(String.format(Locale.US, "Density: %.3f kg/L", state.kilogramsPerUnit()));
            else if(state.basis == PhysicalState.Basis.STANDARD_GAS_LITER && advanced) lines.add(String.format(Locale.US, "Gas density: %.4f g/standard L", state.kilogramsPerUnit() * 1000D));
            else if(state.basis != PhysicalState.Basis.STANDARD_GAS_LITER) lines.add(String.format(Locale.US, "Mass: %.4f g/unit", state.kilogramsPerUnit() * 1000D));
            if(advanced && state.lhvJoulesPerKg.signum() > 0) lines.add(String.format(Locale.US, "LHV: %.3f MJ/kg", state.lhvJoulesPerKg.doubleValue() / 1000000D));
        }
        if(type == Fluids.HCL) lines.add("Concentration: 30 wt% HCl");
        else if(type == Fluids.NITRIC_ACID) lines.add("Concentration: 65 wt% HNO3");
        else if(type == Fluids.SULFURIC_ACID) lines.add("Concentration: 98 wt% H2SO4");
        else if(type == Fluids.HYDROFLUORIC_ACID) lines.add("Concentration: 48 wt% HF");
        else if(type == Fluids.PEROXIDE) lines.add("Concentration: 30 wt% H2O2");
        else if(type == Fluids.BORATED_WATER) lines.add("1 wt% boric acid; about 1748 ppm boron");
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
        if(amount > capacity) lines.add("Above capacity: drain before refilling");
        if(pressure > 0) lines.add("Pipe pressure class: " + pressure + " PU");
        if(type != Fluids.NONE) {
            type.addInfo(lines);
            appendStoredEnergy(lines, type, amount);
        }
    }
}
