package com.hbm.items.machine;

import java.io.IOException;
import java.util.List;

import com.hbm.config.ToolConfig;
import com.hbm.items.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

public class ItemStapledTemplateBundle extends ItemStapler {

	private static final String TEMPLATES = "Templates";
	private static final String SELECTED = "Selected";
	private static final String DURABILITY = "Durability";
	private static final String MAX_DURABILITY = "MaxDurability";

	public ItemStapledTemplateBundle() {
		setNoRepair();
	}

	public static boolean isBundle(ItemStack stack) {
		return stack != null && stack.getItem() == ModItems.stapled_template_bundle;
	}

	public static boolean isLooseTemplate(ItemStack stack) {
		return stack != null && isTemplateItem(stack.getItem());
	}

	private static boolean isTemplateItem(Item item) {
		return item != null && (item == ModItems.assembly_template || item == ModItems.chemistry_template);
	}

	/** Resolve a loose template or read a detached copy of the selected bundle entry. */
	public static ItemStack resolve(ItemStack stack, Item expectedItem) {
		if(!isTemplateItem(expectedItem)) return null;
		ItemStack template = isBundle(stack) ? getTemplate(stack, getSelectedIndex(stack)) : stack;
		return template != null && template.getItem() == expectedItem ? template : null;
	}

	public static int count(ItemStack bundle) {
		return entries(bundle).tagCount();
	}

	public static int getSelectedIndex(ItemStack bundle) {
		if(!isBundle(bundle) || !bundle.hasTagCompound()) return -1;
		int count = count(bundle);
		if(count == 0) return -1;
		return Math.max(0, Math.min(count - 1, bundle.getTagCompound().getInteger(SELECTED)));
	}

	public static void select(ItemStack bundle, int index) {
		if(index >= 0 && index < count(bundle)) bundle.getTagCompound().setInteger(SELECTED, index);
	}

	public static ItemStack getTemplate(ItemStack bundle, int index) {
		NBTTagList list = entries(bundle);
		if(index < 0 || index >= list.tagCount()) return null;
		NBTTagCompound entry = list.getCompoundTagAt(index);
		Item item = (Item) Item.itemRegistry.getObject(entry.getString("RegistryName"));
		if(item == null || !isTemplateItem(item) || entry.getByte("Count") != 1) return null;
		// The name remains stable if Forge assigns a different numeric item ID to this save.
		ItemStack template = new ItemStack(item, entry.getByte("Count"), entry.getShort("Damage"));
		if(entry.hasKey("tag", 10)) template.setTagCompound((NBTTagCompound) entry.getCompoundTag("tag").copy());
		return template;
	}

	private static NBTTagList entries(ItemStack bundle) {
		return isBundle(bundle) && bundle.hasTagCompound() ? bundle.getTagCompound().getTagList(TEMPLATES, 10) : new NBTTagList();
	}

	public static int maxDurability(ItemStack bundle) {
		return bundle.hasTagCompound() && bundle.getTagCompound().hasKey(MAX_DURABILITY, 3)
				? Math.max(1, bundle.getTagCompound().getInteger(MAX_DURABILITY)) : ToolConfig.stapledTemplateDurability;
	}

	public static int durability(ItemStack bundle) {
		int max = maxDurability(bundle);
		return bundle.hasTagCompound() && bundle.getTagCompound().hasKey(DURABILITY, 3)
				? Math.max(0, Math.min(max, bundle.getTagCompound().getInteger(DURABILITY))) : max;
	}

	/** Build and validate the result before the container commits any inventory changes. */
	public static ItemStack addTemplate(ItemStack bundle, ItemStack template) {
		if(!isLooseTemplate(template) || template.stackSize != 1 || count(bundle) >= ToolConfig.stapledTemplateLimit) return null;
		if(bundle != null && (!isBundle(bundle) || bundle.stackSize != 1 || durability(bundle) == 0)) return null;
		ItemStack result = bundle == null ? new ItemStack(ModItems.stapled_template_bundle) : bundle.copy();
		NBTTagCompound tag = result.getTagCompound();
		if(tag == null) {
			tag = new NBTTagCompound();
			result.setTagCompound(tag);
		}
		tag.setInteger(MAX_DURABILITY, maxDurability(result));
		tag.setInteger(DURABILITY, durability(result));
		NBTTagList list = entries(result);
		NBTTagCompound entry = template.copy().writeToNBT(new NBTTagCompound());
		entry.setString("RegistryName", Item.itemRegistry.getNameForObject(template.getItem()));
		list.appendTag(entry);
		tag.setTag(TEMPLATES, list);
		if(list.tagCount() == 1) select(result, 0);
		return fitsInventoryPacket(tag) ? result : null;
	}

	private static boolean fitsInventoryPacket(NBTTagCompound tag) {
		try {
			byte[] compressed = CompressedStreamTools.compress(tag);
			// Forge 1.7.10 transmits item NBT with a signed short and a bounded read size.
			// Leave room for ordinary changes such as renaming the bundle.
			return compressed.length <= 32000
					&& CompressedStreamTools.func_152457_a(compressed, new NBTSizeTracker(2 * 1024 * 1024)) != null;
		} catch(IOException | RuntimeException ex) {
			return false;
		}
	}

	public static void removeSelected(ItemStack bundle) {
		int selected = getSelectedIndex(bundle);
		if(selected < 0 || durability(bundle) == 0) return;
		NBTTagCompound tag = bundle.getTagCompound();
		entries(bundle).removeTag(selected);
		tag.setInteger(SELECTED, Math.max(0, Math.min(selected, count(bundle) - 1)));
		tag.setInteger(MAX_DURABILITY, maxDurability(bundle));
		tag.setInteger(DURABILITY, durability(bundle) - 1);
	}

	@Override
	public boolean showDurabilityBar(ItemStack stack) {
		return durability(stack) < maxDurability(stack);
	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {
		return 1D - (double) durability(stack) / maxDurability(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack bundle, EntityPlayer player, List lines, boolean advanced) {
		ItemStack selected = getTemplate(bundle, getSelectedIndex(bundle));
		lines.add(StatCollector.translateToLocalFormatted("gui.stapler.selected",
				selected == null ? StatCollector.translateToLocal("gui.stapler.none") : selected.getDisplayName()));
		lines.add(StatCollector.translateToLocalFormatted("gui.stapler.count", count(bundle)));
		lines.add(StatCollector.translateToLocalFormatted("gui.stapler.durability", durability(bundle), maxDurability(bundle)));
	}
}
