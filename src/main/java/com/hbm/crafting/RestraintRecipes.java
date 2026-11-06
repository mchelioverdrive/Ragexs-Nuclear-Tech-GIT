package com.hbm.crafting;

import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public final class RestraintRecipes {
    private RestraintRecipes() { }
    public static void register() {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.handcuffs), "S S", "SCS", 'S', "ingotSteel", 'C', ModItems.plate_steel));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.straight_jacket), "W W", "WSW", "WWW", 'W', Blocks.wool, 'S', Items.string));
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(ModItems.tranquilizer_syringe), ModItems.syringe_empty, Items.slime_ball, Items.glowstone_dust));
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(ModItems.tranquilizer_dart, 2), ModItems.tranquilizer_syringe, ModItems.ingot_steel, Items.feather));
        GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(ModItems.injection_terminal), ModItems.syringe_empty, Items.ender_pearl, ModItems.powder_poison));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.guillotine), "WSW", "W W", "WWW", 'W', "plankWood", 'S', ModItems.plate_steel));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.brazen_bull), "BBB", "BFB", "BSB", 'B', "ingotBronze", 'F', Blocks.furnace, 'S', ModItems.plate_steel));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.electric_chair), " WC", "SWS", "SRS", 'W', "plankWood", 'C', ModItems.wire_copper, 'R', Items.redstone, 'S', ModItems.plate_steel));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.injection_table), "WWW", "SCS", "S S", 'W', Blocks.wool, 'S', ModItems.plate_steel, 'C', ModItems.syringe_empty));
    }
}
