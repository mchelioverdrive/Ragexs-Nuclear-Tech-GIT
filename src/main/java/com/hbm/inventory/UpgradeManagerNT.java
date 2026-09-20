package com.hbm.inventory;

import java.util.HashMap;
import java.util.Map;

import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.tileentity.IUpgradeInfoProvider;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Per-machine upgrade manager with content-aware slot caching.
 */
public class UpgradeManagerNT {

	private final Map<UpgradeType, Integer> upgrades = new HashMap<UpgradeType, Integer>();
	private UpgradeType mutexType = null;
	private ItemStack[] cachedStacks = new ItemStack[0];
	private Item[] cachedItems = new Item[0];
	private int[] cachedMeta = new int[0];
	private int[] cachedCount = new int[0];
	private int[] cachedNbtHash = new int[0];
	private int cachedStart;
	private int cachedEnd = -1;
	private boolean invalidated = true;

	public void invalidate() {
		this.invalidated = true;
	}

	public void checkSlots(ItemStack[] slots, int start, int end) {
		if(!this.invalidated && signatureMatches(slots, start, end)) return;
		recalculate(slots, start, end);
	}

	/**
	 * Strict invalidation path for inventories whose mutation methods call invalidate().
	 * Unlike checkSlots(), the clean steady-state path does not even scan the slot range.
	 */
	public void checkSlotsIfDirty(ItemStack[] slots, int start, int end) {
		if(!this.invalidated) return;
		recalculate(slots, start, end);
	}

	private void recalculate(ItemStack[] slots, int start, int end) {
		cacheSignature(slots, start, end);
		this.invalidated = false;
		this.upgrades.clear();
		this.mutexType = null;

		if(slots == null) return;

		for(int i = start; i <= end && i < slots.length; i++) {
			ItemStack stack = slots[i];
			if(stack != null && stack.getItem() instanceof ItemMachineUpgrade) {
				ItemMachineUpgrade item = (ItemMachineUpgrade) stack.getItem();
				if(item.type.mutex) {
					if(this.mutexType == null || this.mutexType.ordinal() < item.type.ordinal()) {
						if(this.mutexType != null) this.upgrades.remove(this.mutexType);
						this.mutexType = item.type;
						this.upgrades.put(item.type, 1);
					}
				} else {
					Integer up = this.upgrades.get(item.type);
					int upgrade = (up == null ? 0 : up);
					upgrade += item.tier;
					this.upgrades.put(item.type, upgrade);
				}
			}
		}
	}

	public void checkSlots(IUpgradeInfoProvider provider, ItemStack[] slots, int start, int end) {
		this.checkSlots(slots, start, end);
		if(provider == null) return;
		for(UpgradeType type : UpgradeType.values()) {
			Integer level = this.upgrades.get(type);
			if(level != null) this.upgrades.put(type, Math.min(level, provider.getMaxLevel(type)));
		}
	}

	public int getLevel(UpgradeType type) {
		Integer up = this.upgrades.get(type);
		return up == null ? 0 : up;
	}

	public UpgradeType getMinerMutex() {
		return this.mutexType;
	}

	private boolean signatureMatches(ItemStack[] slots, int start, int end) {
		if(this.cachedStart != start || this.cachedEnd != end) return false;
		int length = Math.max(0, end - start + 1);
		if(this.cachedStacks.length != length) return false;

		for(int i = 0; i < length; i++) {
			ItemStack stack = slots != null && start + i >= 0 && start + i < slots.length ? slots[start + i] : null;
			if(this.cachedStacks[i] != stack) return false;
			if(stack != null) {
				NBTTagCompound tag = stack.getTagCompound();
				if(this.cachedItems[i] != stack.getItem() || this.cachedMeta[i] != stack.getItemDamage() || this.cachedCount[i] != stack.stackSize || this.cachedNbtHash[i] != (tag == null ? 0 : tag.hashCode())) return false;
			}
		}
		return true;
	}

	private void cacheSignature(ItemStack[] slots, int start, int end) {
		int length = Math.max(0, end - start + 1);
		if(this.cachedStacks.length != length) {
			this.cachedStacks = new ItemStack[length];
			this.cachedItems = new Item[length];
			this.cachedMeta = new int[length];
			this.cachedCount = new int[length];
			this.cachedNbtHash = new int[length];
		}
		this.cachedStart = start;
		this.cachedEnd = end;

		for(int i = 0; i < length; i++) {
			ItemStack stack = slots != null && start + i >= 0 && start + i < slots.length ? slots[start + i] : null;
			this.cachedStacks[i] = stack;
			if(stack != null) {
				NBTTagCompound tag = stack.getTagCompound();
				this.cachedItems[i] = stack.getItem();
				this.cachedMeta[i] = stack.getItemDamage();
				this.cachedCount[i] = stack.stackSize;
				this.cachedNbtHash[i] = tag == null ? 0 : tag.hashCode();
			} else {
				this.cachedItems[i] = null;
				this.cachedMeta[i] = 0;
				this.cachedCount[i] = 0;
				this.cachedNbtHash[i] = 0;
			}
		}
	}
}
