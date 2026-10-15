package com.hbm.realsim;

import java.util.List;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumBriquetteType;
import com.hbm.items.ItemEnums.EnumCokeType;
import com.hbm.util.ItemStackUtil;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** One-item identity/quantity resolution. Specific native identities precede external aliases. */
public final class SolidFuel {
    public final String identity;
    public final int units;
    private SolidFuel(String identity, int units) { this.identity = identity; this.units = units; }
    public long massUnits() { return Math.multiplyExact(PhysicalRegistry.get(identity).massUnits, units); }
    public long chemicalMicrojoules() { return Math.multiplyExact(PhysicalRegistry.get(identity).chemicalMicrojoulesPerUnit(), units); }
    public boolean has(MaterialRole role) { return FuelSuitability.has(identity, role); }
    public static SolidFuel of(String identity, int units) {
        if(units <= 0 || !PhysicalRegistry.get(identity).isResolved()) throw new IllegalArgumentException("Unresolved fuel form");
        return new SolidFuel(identity, units);
    }
    public static SolidFuel resolve(ItemStack stack) {
        if(stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        Item item = stack.getItem(); int meta = stack.getItemDamage();
        if(item == Items.coal) return meta == 0 ? of("COAL_ITEM", 1) : meta == 1 ? of("CHARCOAL_ITEM", 1) : null;
        if(item == Item.getItemFromBlock(Blocks.coal_block)) return of("COAL_ITEM", 9);
        if(item == Items.stick) return of("WOOD_STICK", 1);
        if(item == ModItems.coke || item == ModItems.powder_coke || item == Item.getItemFromBlock(ModBlocks.block_coke)) {
            String name = meta == EnumCokeType.COAL.ordinal() ? "COKE_ITEM" : meta == EnumCokeType.LIGNITE.ordinal() ? "LIGNITE_COKE_ITEM" : meta == EnumCokeType.PETROLEUM.ordinal() ? "PETCOKE_ITEM" : null;
            return name == null ? null : of(name, item == Item.getItemFromBlock(ModBlocks.block_coke) ? 9 : 1);
        }
        if(item == ModItems.briquette) {
            String name = meta == EnumBriquetteType.COAL.ordinal() ? "COAL_BRIQUETTE_ITEM" : meta == EnumBriquetteType.LIGNITE.ordinal() ? "LIGNITE_BRIQUETTE_ITEM" : meta == EnumBriquetteType.WOOD.ordinal() ? "WOOD_BRIQUETTE_ITEM" : null;
            return name == null ? null : of(name, 1);
        }
        if(item == ModItems.lignite || item == ModItems.powder_lignite) return of("LIGNITE_ITEM", 1);
        if(item == ModItems.powder_coal) return of("COAL_ITEM", 1);
        if(item == ModItems.biomass || item == ModItems.biomass_compressed) return of("BIOMASS_ITEM", 1);
        if(item == ModItems.powder_sawdust) return of("SAWDUST_ITEM", 1);
        // Legacy burnability and chemical assay identities do not turn residues or explosives into fuels.
        if(item == ModItems.powder_ash || item == ModItems.scrap || item == ModItems.dust || item == Items.sugar || item == Items.book
                || item == ModItems.sulfur || item == ModItems.solid_fuel || item == ModItems.rocket_fuel) return null;
        Object registryName = Item.itemRegistry.getNameForObject(item);
        if(registryName != null && registryName.toString().startsWith("hbm:")) return null;
        return fromAliases(ItemStackUtil.getOreDictNames(stack));
    }
    /** Contradictory specific aliases are rejected; generic coke may accompany a known coke grade. */
    public static SolidFuel fromAliases(List<String> names) {
        SolidFuel specific = null, generic = null; int genericFormUnits = 0;
        for(String name : names) {
            String identity = null; int units = 1;
            if(name.equals("coalCoke")) { identity = "COKE_ITEM"; }
            if(name.equals("fuelCoke") || name.equals("coke")) {
                if(generic == null) generic = of("COKE_ITEM", 1);
                continue;
            }
            if(name.startsWith("gem") || name.startsWith("dust") || name.startsWith("block")) {
                String material = name.substring(name.startsWith("block") ? 5 : name.startsWith("dust") ? 4 : 3);
                units = name.startsWith("block") ? 9 : 1;
                if(material.equals("CoalCoke")) identity = "COKE_ITEM";
                else if(material.equals("PetCoke") || material.equals("PetroleumCoke")) identity = "PETCOKE_ITEM";
                else if(material.equals("LigniteCoke")) identity = "LIGNITE_COKE_ITEM";
                else if(material.equals("Coke")) {
                    if(genericFormUnits != 0 && genericFormUnits != units) return null;
                    genericFormUnits = units; generic = of("COKE_ITEM", units); continue;
                }
                else if(material.equals("Coal")) identity = "COAL_ITEM";
                else if(material.equals("Charcoal")) identity = "CHARCOAL_ITEM";
                else if(material.equals("Lignite")) identity = "LIGNITE_ITEM";
            }
            if(name.equals("briquetteCoal")) identity = "COAL_BRIQUETTE_ITEM";
            if(name.equals("briquetteLignite")) identity = "LIGNITE_BRIQUETTE_ITEM";
            if(name.equals("briquetteWood")) identity = "WOOD_BRIQUETTE_ITEM";
            if(name.equals("logWood")) identity = "WOOD_LOG";
            if(name.equals("plankWood")) identity = "WOOD_PLANK";
            if(name.equals("stickWood")) identity = "WOOD_STICK";
            if(name.equals("treeSapling")) identity = "SAPLING_ITEM";
            if(identity == null) continue;
            if(specific != null && (!specific.identity.equals(identity) || specific.units != units)) return null;
            specific = of(identity, units);
        }
        if(specific != null && genericFormUnits != 0 && specific.units != genericFormUnits) return null;
        if(specific != null && generic != null && !specific.identity.equals("COKE_ITEM") && !specific.identity.equals("LIGNITE_COKE_ITEM") && !specific.identity.equals("PETCOKE_ITEM")) return null;
        return specific != null ? specific : generic;
    }
}
