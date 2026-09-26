package api.hbm.energymk2;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public interface IBatteryItem {

	public default void receiveEnergyQuanta(ItemStack stack, long energyQuanta) { chargeBattery(stack, EnergyUnits.quantaToLegacyHe(energyQuanta)); }
	public default void setStoredEnergyQuanta(ItemStack stack, long energyQuanta) { setCharge(stack, EnergyUnits.quantaToLegacyHe(energyQuanta)); }
	public default void extractEnergyQuanta(ItemStack stack, long energyQuanta) { dischargeBattery(stack, EnergyUnits.quantaToLegacyHe(energyQuanta)); }
	public default long getStoredEnergyQuanta(ItemStack stack) { return EnergyUnits.legacyHeToQuanta(getCharge(stack)); }
	public default long getEnergyCapacityQuanta(ItemStack stack) { return EnergyUnits.legacyHeToQuanta(getMaxCharge(stack)); }
	public default long getMaxInputQuantaPerTick() { return EnergyUnits.legacyHeToQuanta(getChargeRate()); }
	public default long getMaxOutputQuantaPerTick() { return EnergyUnits.legacyHeToQuanta(getDischargeRate()); }

	/** Legacy HE API retained for addons; RTM code uses the quantum methods above. */
	@Deprecated public default void chargeBattery(ItemStack stack, long legacyHe) { receiveEnergyQuanta(stack, EnergyUnits.legacyHeToQuanta(legacyHe)); }
	@Deprecated public default void dischargeBattery(ItemStack stack, long legacyHe) { extractEnergyQuanta(stack, EnergyUnits.legacyHeToQuanta(legacyHe)); }
	@Deprecated public default void setCharge(ItemStack stack, long legacyHe) { setStoredEnergyQuanta(stack, EnergyUnits.legacyHeToQuanta(legacyHe)); }
	@Deprecated public default long getCharge(ItemStack stack) { return EnergyUnits.quantaToLegacyHe(getStoredEnergyQuanta(stack)); }
	@Deprecated public default long getMaxCharge(ItemStack stack) { return EnergyUnits.quantaToLegacyHe(getEnergyCapacityQuanta(stack)); }
	@Deprecated public default long getChargeRate() { return EnergyUnits.quantaToLegacyHe(getMaxInputQuantaPerTick()); }
	@Deprecated public default long getDischargeRate() { return EnergyUnits.quantaToLegacyHe(getMaxOutputQuantaPerTick()); }
	
	/** Legacy item NBT key, used only when reading old data. */
	@Deprecated
	public default String getChargeTagName() {
		return "charge";
	}

	/** Returns a string for the NBT tag name of the long storing power */
	public static String getChargeTagName(ItemStack stack) {
		return ((IBatteryItem) stack.getItem()).getChargeTagName();
	}

	/** Returns an empty battery stack from the passed ItemStack, the original won't be modified */
	public static ItemStack emptyBattery(ItemStack stack) {
		if(stack != null && stack.getItem() instanceof IBatteryItem) {
			ItemStack stackOut = stack.copy();
			stackOut.stackTagCompound = new NBTTagCompound();
			((IBatteryItem) stackOut.getItem()).setStoredEnergyQuanta(stackOut, 0);
			return stackOut.copy();
		}
		return null;
	}

	/** Returns an empty battery stack from the passed Item */
	public static ItemStack emptyBattery(Item item) {
		return item instanceof IBatteryItem ? emptyBattery(new ItemStack(item)) : null;
	}
}
