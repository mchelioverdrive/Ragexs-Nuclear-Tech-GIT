package com.hbm.items.armor;

import com.google.common.collect.Multimap;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.util.ObjectIntIdentityMap;

import java.lang.reflect.Field;
import java.util.UUID;

public class ItemHeavyArmor extends ItemArmor {

	public ItemHeavyArmor(ArmorMaterial material, int renderIndex, int armorType) {
		super(material, renderIndex, armorType);
	}

	@Override
	public Multimap getItemAttributeModifiers() {
		Multimap multimap = super.getItemAttributeModifiers();
		multimap.put(SharedMonsterAttributes.movementSpeed.getAttributeUnlocalizedName(),
			new AttributeModifier(UUID.fromString("cb3f55d3-645c-4f38-a497-9c13a33db5cf"),
				"Armor speed debuff", -0.15D, 1)); // -15% speed
		return multimap;
	}

	public static void replaceItem(int id, Item replacement) {
		try {
			Field[] fields = GameData.class.getDeclaredFields();
			for (Field field : fields) {
				if (field.getName().equals("idToItem")) {
					field.setAccessible(true);
					Object obj = field.get(null);
					ObjectIntIdentityMap idMap = (ObjectIntIdentityMap) obj;
					idMap.func_148746_a(replacement, id); // replace in registry
					break;
				}
			}

			// Set in the actual items array
			Field f = Item.class.getDeclaredField("itemsList");
			f.setAccessible(true);
			Item[] items = (Item[]) f.get(null);
			items[id] = replacement;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void registerAndReplaceArmor() {
		int idIronChest = Item.getIdFromItem(Items.iron_chestplate);

		ItemHeavyArmor ironChestNew = (ItemHeavyArmor) new ItemHeavyArmor(ArmorMaterial.IRON, 1, 1)
			.setUnlocalizedName("iron_chestplate")
			.setTextureName("minecraft:iron_chestplate"); // use full path

		GameRegistry.registerItem(ironChestNew, "iron_chestplate");
		replaceItem(idIronChest, ironChestNew);
	}
}
