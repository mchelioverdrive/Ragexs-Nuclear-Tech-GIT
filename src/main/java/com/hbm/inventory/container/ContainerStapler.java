package com.hbm.inventory.container;

import com.hbm.config.ToolConfig;
import com.hbm.inventory.SlotTakeOnly;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemStapledTemplateBundle;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

public class ContainerStapler extends Container {

	public static final int STAPLES = 0, TEMPLATE = 1, BUNDLE = 2, OUTPUT = 3;
	public static final int ADD = 254, REMOVE = 255;
	private final InventoryBasic work = new InventoryBasic("stapler", false, 4);
	private final EntityPlayer owner;
	private final ItemStack openingStack;
	private final int heldSlot;
	private final int firstPlayerSlot;
	private final boolean stapler;
	private int limit;
	private int cost;
	private boolean closed;

	public ContainerStapler(EntityPlayer player, boolean stapler) {
		this.owner = player;
		this.openingStack = player.getHeldItem();
		this.heldSlot = player.inventory.currentItem;
		this.stapler = stapler;
		if(stapler) {
			addSlotToContainer(new Slot(work, STAPLES, 34, 29) {
				@Override public boolean isItemValid(ItemStack stack) { return stack.getItem() == ModItems.staples; }
			});
			addSlotToContainer(new Slot(work, TEMPLATE, 88, 29) {
				@Override public boolean isItemValid(ItemStack stack) { return ItemStapledTemplateBundle.isLooseTemplate(stack); }
			});
			addSlotToContainer(new Slot(work, BUNDLE, 142, 29) {
				@Override public boolean isItemValid(ItemStack stack) {
					return work.getStackInSlot(OUTPUT) == null && ItemStapledTemplateBundle.isBundle(stack);
				}
			});
			addSlotToContainer(new SlotTakeOnly(work, OUTPUT, 202, 29));
		}
		firstPlayerSlot = inventorySlots.size();
		int inventoryY = stapler ? 156 : 118;
		for(int row = 0; row < 3; row++) {
			for(int col = 0; col < 9; col++)
				addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 44 + col * 18, inventoryY + row * 18));
		}
		for(int col = 0; col < 9; col++) {
			int hotbar = col;
			addSlotToContainer(new Slot(player.inventory, col, 44 + col * 18, inventoryY + 58) {
				@Override public boolean canTakeStack(EntityPlayer player) { return hotbar != heldSlot; }
				@Override public boolean isItemValid(ItemStack stack) { return hotbar != heldSlot; }
			});
		}
	}

	public boolean isStapler() {
		return stapler;
	}

	public static void closeFor(EntityPlayer player, boolean notifyClient) {
		if(!(player instanceof EntityPlayerMP) || !(player.openContainer instanceof ContainerStapler)) return;
		EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
		if(notifyClient) serverPlayer.closeScreen();
		else serverPlayer.closeContainer();
	}

	@Override
	public void addCraftingToCrafters(ICrafting crafter) {
		super.addCraftingToCrafters(crafter);
		if(stapler) {
			crafter.sendProgressBarUpdate(this, 0, ToolConfig.stapledTemplateLimit);
			crafter.sendProgressBarUpdate(this, 1, ToolConfig.staplesPerTemplate);
		}
	}

	@Override
	public void updateProgressBar(int id, int value) {
		if(id == 0) limit = value;
		if(id == 1) cost = value;
	}

	public boolean canAddFromGui() {
		ItemStack staples = work.getStackInSlot(STAPLES);
		ItemStack bundle = getBundle();
		return cost > 0 && staples != null && staples.stackSize >= cost && work.getStackInSlot(TEMPLATE) != null
				&& ItemStapledTemplateBundle.count(bundle) < limit
				&& (bundle == null || ItemStapledTemplateBundle.durability(bundle) > 0);
	}

	public int getStapleCost() {
		return cost;
	}

	public ItemStack getBundle() {
		if(!stapler) return owner.inventory.getStackInSlot(heldSlot);
		return work.getStackInSlot(activeBundleSlot());
	}

	private int activeBundleSlot() {
		return work.getStackInSlot(OUTPUT) == null ? BUNDLE : OUTPUT;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return !closed && player == owner && player.isEntityAlive() && player.inventory.currentItem == heldSlot
				&& openingStack != null && player.getHeldItem() == openingStack;
	}

	@Override
	public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
		if(slot >= inventorySlots.size() || (!player.worldObj.isRemote && !canInteractWith(player))) return null;
		if(slot == firstPlayerSlot + 27 + heldSlot || (mode == 2 && button == heldSlot)) return null;
		return super.slotClick(slot, button, mode, player);
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		Slot source = getSlot(index);
		if(!stapler || !source.getHasStack() || !source.canTakeStack(player)) return null;
		ItemStack stack = source.getStack();
		ItemStack original = stack.copy();
		if(index < firstPlayerSlot) {
			if(!mergeItemStack(stack, firstPlayerSlot, inventorySlots.size(), true)) return null;
		} else {
			int dest = stack.getItem() == ModItems.staples ? STAPLES
					: ItemStapledTemplateBundle.isLooseTemplate(stack) ? TEMPLATE : BUNDLE;
			if(!getSlot(dest).isItemValid(stack) || !mergeItemStack(stack, dest, dest + 1, false)) return null;
		}
		if(stack.stackSize == 0) source.putStack(null);
		else source.onSlotChanged();
		source.onPickupFromSlot(player, stack);
		return original;
	}

	// Vanilla window buttons are processed in order with slot clicks, on the server thread.
	@Override
	public boolean enchantItem(EntityPlayer player, int button) {
		if(player.worldObj.isRemote || !canInteractWith(player)) return false;
		int action = button & 255;
		if(action == ADD) return stapler && addTemplate();
		if(action == REMOVE) return stapler && removeTemplate();
		ItemStack bundle = getBundle();
		if(action >= ItemStapledTemplateBundle.count(bundle)) return false;
		ItemStapledTemplateBundle.select(bundle, action);
		if(!stapler) player.inventory.markDirty();
		return true;
	}

	private boolean addTemplate() {
		ItemStack staples = work.getStackInSlot(STAPLES);
		ItemStack template = work.getStackInSlot(TEMPLATE);
		int cost = ToolConfig.staplesPerTemplate;
		if(staples == null || staples.getItem() != ModItems.staples || staples.stackSize < cost
				|| template == null || template.stackSize != 1) return false;
		ItemStack result = ItemStapledTemplateBundle.addTemplate(getBundle(), template);
		if(result == null) {
			owner.addChatMessage(new ChatComponentTranslation("gui.stapler.cannot_add"));
			return false;
		}
		work.decrStackSize(STAPLES, cost);
		work.setInventorySlotContents(TEMPLATE, null);
		work.setInventorySlotContents(BUNDLE, null);
		work.setInventorySlotContents(OUTPUT, result);
		return true;
	}

	private boolean removeTemplate() {
		int slot = activeBundleSlot();
		ItemStack bundle = work.getStackInSlot(slot);
		if(!ItemStapledTemplateBundle.isBundle(bundle) || bundle.stackSize != 1) return false;
		int durability = ItemStapledTemplateBundle.durability(bundle);
		int selected = ItemStapledTemplateBundle.getSelectedIndex(bundle);
		ItemStack removed = ItemStapledTemplateBundle.getTemplate(bundle, selected);
		if(durability <= 0 || removed == null) return false;
		if(durability == 1) {
			// Deserialize every entry before clearing the bundle or granting any items.
			ItemStack[] contents = new ItemStack[ItemStapledTemplateBundle.count(bundle)];
			for(int i = 0; i < contents.length; i++) {
				contents[i] = i == selected ? removed : ItemStapledTemplateBundle.getTemplate(bundle, i);
				if(contents[i] == null) return false;
			}
			work.setInventorySlotContents(slot, null);
			returnItem(removed);
			for(int i = 0; i < contents.length; i++) {
				if(i != selected) returnItem(contents[i]);
			}
		} else {
			ItemStapledTemplateBundle.removeSelected(bundle);
			work.markDirty();
			returnItem(removed);
		}
		return true;
	}

	private void returnItem(ItemStack stack) {
		if(stack == null) return;
		mergeItemStack(stack, firstPlayerSlot, inventorySlots.size(), false);
		if(stack.stackSize > 0) owner.func_146097_a(stack, false, true);
	}

	@Override
	public void onContainerClosed(EntityPlayer player) {
		if(closed || player != owner) return;
		closed = true;
		if(!player.worldObj.isRemote) {
			ItemStack cursor = player.inventory.getItemStack();
			player.inventory.setItemStack(null);
			returnItem(cursor);
			for(int i = STAPLES; i <= OUTPUT; i++) returnItem(work.getStackInSlotOnClosing(i));
		}
		super.onContainerClosed(player);
	}
}
