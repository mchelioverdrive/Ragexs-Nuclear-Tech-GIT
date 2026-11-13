package com.hbm.handler.disease;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.potion.HbmPotion;
import com.hbm.items.ModItems;
import com.hbm.items.special.ItemHRIG;
import com.hbm.util.InventoryUtil;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.RabiesVisualPacket;
import com.hbm.entity.mob.gemini.IDeepRootCarrier;
import com.hbm.world.gemini.GeminiInfections;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.CombatEntry;
import net.minecraft.util.CombatTracker;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

public final class DiseaseEvents {
	@SubscribeEvent
	public void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
		if(!(event.entityPlayer instanceof EntityPlayerMP) || !(event.target instanceof EntityLivingBase)) return;
		EntityLivingBase host = (EntityLivingBase) event.target;
		DeepRootDisease.sendVisual(host, (EntityPlayerMP) event.entityPlayer);
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(infection != null) DiseaseManager.pathogen(Rabies.ID).progress(host, infection, DiseaseManager.now(host));
		if(Rabies.frothing(host, infection))
			PacketDispatcher.wrapper.sendTo(new RabiesVisualPacket(host.getEntityId(), true), (EntityPlayerMP) event.entityPlayer);
	}

	@SubscribeEvent
	public void onLogin(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) { syncSelf(event.player); }
	@SubscribeEvent
	public void onRespawn(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) { syncSelf(event.player); }
	@SubscribeEvent
	public void onDimensionChanged(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) { syncSelf(event.player); }

	private static void syncSelf(EntityPlayer player) {
		if(player instanceof EntityPlayerMP) DeepRootDisease.sendVisual(player, (EntityPlayerMP) player);
	}

	// Forge 1.7.10 has no post-armor LivingDamageEvent. Read native confirmed wounds instead of
	// reimplementing special armor or treating the pre-armor LivingHurtEvent as proof of broken skin.
	private static final Field COMBAT_ENTRIES = ReflectionHelper.findField(CombatTracker.class, "combatEntries", "field_94556_a");
	private static final List<PendingBite> BITES = new ArrayList<PendingBite>();
	private static final Map<EntityPlayer, PendingSample> SAMPLES = new IdentityHashMap<EntityPlayer, PendingSample>();
	private static final Set<EntityBat> BATS = Collections.newSetFromMap(new IdentityHashMap<EntityBat, Boolean>());
	private static final List<LivingDeathEvent> DEEP_ROOT_DEATHS = new ArrayList<LivingDeathEvent>();

	public static void queueBat(EntityBat bat) { BATS.add(bat); }

	public static void clear() {
		BITES.clear();
		SAMPLES.clear();
		BATS.clear();
		DEEP_ROOT_DEATHS.clear();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDeath(LivingDeathEvent event) {
		if(!event.entityLiving.worldObj.isRemote && event.entityLiving instanceof EntityPlayer
				&& event.source == DeepRootDisease.TERMINAL_DAMAGE) DEEP_ROOT_DEATHS.add(event);
		if(!event.entityLiving.worldObj.isRemote) Rabies.setFrothing(event.entityLiving, false);
		if(!event.entityLiving.worldObj.isRemote) DiseaseManager.pathogen(DeepRootDisease.ID).removeEffects(event.entityLiving);
		// Native death clears combat history before Item.hitEntity runs. Retain only this attack's wound.
		if(!event.entityLiving.worldObj.isRemote && event.source.getEntity() instanceof EntityPlayer) {
			PendingSample sample = SAMPLES.get((EntityPlayer) event.source.getEntity());
			if(sample != null && sample.attack.target == event.entityLiving && sample.damage != null
					&& sample.damage.event.source == event.source)
				sample.confirmed = sample.damage.confirmedDamage();
		}
		if(!event.entityLiving.worldObj.isRemote && event.entityLiving instanceof EntityPlayer)
			event.entityLiving.removePotionEffect(HbmPotion.rabies.id);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onInteract(EntityInteractEvent event) {
		ItemStack held = event.entityPlayer.getCurrentEquippedItem();
		if(!(event.target instanceof EntityVillager) || held == null || held.getItem() != ModItems.syringe_hrig) return;
		// EntityPlayer dispatches this before villager trading and item interaction. Own this use once.
		// Let client-side vanilla/item dispatch return success, avoiding a second self-use packet.
		if(!event.entityPlayer.worldObj.isRemote) {
			event.setCanceled(true);
			((ItemHRIG) held.getItem()).treatVillager(held, (EntityVillager) event.target, event.entityPlayer);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerAttack(AttackEntityEvent event) {
		if(event.entityPlayer.worldObj.isRemote) return;
		SAMPLES.remove(event.entityPlayer);
		ItemStack held = event.entityPlayer.getCurrentEquippedItem();
		if(held == null || held.stackSize <= 0 || held.getItem() != ModItems.syringe_empty
				|| !(event.target instanceof EntityAnimal || event.target instanceof EntityBat)
				|| !DiseaseHost.get((EntityLivingBase) event.target).infections.containsKey(Rabies.ID)) return;
		SAMPLES.put(event.entityPlayer, new PendingSample(event, held));
	}

	/** Native item-hit callback follows the complete melee/protection path; hurt events alone cannot collect. */
	public static void collectRabiesSample(ItemStack stack, EntityLivingBase animal, EntityPlayer player) {
		if(player.worldObj.isRemote) return;
		PendingSample sample = SAMPLES.remove(player); // One callback can consume this attack, even if called again.
		if(sample == null || sample.attack.isCanceled() || sample.attack.target != animal || sample.stack != stack
				|| player.getCurrentEquippedItem() != stack || stack.getItem() != ModItems.syringe_empty
				|| stack.stackSize <= 0 || sample.damage == null || sample.damage.event.isCanceled()
				|| sample.damage.event.ammount <= 0F
				|| sample.confirmed == null && sample.damage.confirmedDamage() == null) return;
		ItemStack filled = new ItemStack(ModItems.syringe_rabies);
		if(!player.capabilities.isCreativeMode && stack.stackSize == 1) {
			// Keep the native hit callback's stack alive: vanilla destroys the held item if it reaches zero.
			stack.func_150996_a(ModItems.syringe_rabies);
			stack.setItemDamage(0);
			stack.setTagCompound(null);
		} else {
			if(!player.capabilities.isCreativeMode) stack.stackSize--;
			// Vanilla InventoryPlayer silently discards overflow in Creative. Keep the sample as a drop.
			ItemStack remainder = InventoryUtil.tryAddItemToInventory(player.inventory.mainInventory, filled);
			if(remainder != null) player.dropPlayerItemWithRandomChoice(remainder, false);
		}
		player.inventory.markDirty();
	}

	private boolean blockedWater(EntityPlayer player, ItemStack item) {
		if(player.worldObj.isRemote || item == null || item.getItem() != Items.potionitem
				|| item.getItemDamage() != 0 || !Rabies.swallowingSpasms(player)) return false;
		DiseaseHost data = DiseaseHost.get(player);
		long now = DiseaseManager.now(player);
		if(now >= data.nextSwallowingMessage) {
			player.addChatMessage(new ChatComponentText("A swallowing spasm prevents you from drinking the water."));
			data.nextSwallowingMessage = now + 100;
		}
		return true;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDrinkStart(PlayerUseItemEvent.Start event) {
		if(blockedWater(event.entityPlayer, event.item)) event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if(event.phase != TickEvent.Phase.START || !event.player.isUsingItem()
				|| !blockedWater(event.player, event.player.getCurrentEquippedItem())) return;
		// FML pre-player-tick runs before EntityPlayer's item-use block. Cancelling UseItem.Tick in
		// 1.7.10 instead returns -1 and calls onItemUseFinish; MP even sends a completion packet when
		// use was cleared there. Clear here, before that block, with no item mutation/completion packet.
		// getItemInUse is client-only in 1.7.10: vanilla itself compares use against the equipped stack.
		event.player.clearItemInUse();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onHurt(LivingHurtEvent event) {
		if(!event.entityLiving.worldObj.isRemote && event.ammount > 0F && event.source.getEntity() instanceof EntityLivingBase)
			RabiesBehavior.attackedBy(event.entityLiving, (EntityLivingBase) event.source.getEntity());
		if(event.entityLiving.worldObj.isRemote || event.ammount <= 0F || event.source.isProjectile()
				|| event.source.getEntity() != event.source.getSourceOfDamage()) return;
		if(!(event.source.getEntity() instanceof EntityLivingBase)) return;
		EntityLivingBase source = (EntityLivingBase) event.source.getEntity();
		if(source instanceof EntityPlayer && "player".equals(event.source.getDamageType())) {
			PendingSample sample = SAMPLES.get((EntityPlayer) source);
			if(sample != null && !sample.attack.isCanceled() && sample.attack.target == event.entityLiving
					&& sample.stack == ((EntityPlayer) source).getCurrentEquippedItem() && sample.damage == null
					&& DiseaseHost.get(event.entityLiving).infections.containsKey(Rabies.ID))
				sample.damage = new PendingBite(event, source);
		}
		// Vanilla wolf mob melee is a bite. Other hosts require our explicit marker, never generic melee inference.
		boolean carrier = source instanceof IDeepRootCarrier && "mob".equals(event.source.getDamageType())
				&& DeepRootDisease.active(event.entityLiving);
		if(!carrier && !(event.source instanceof BiteDamageSource)
				&& !(source.getClass() == EntityWolf.class && "mob".equals(event.source.getDamageType()))) return;
		if(!carrier && DiseaseHost.get(source).infections.isEmpty()) return;
		// Damage attribution alone is not contact: native attack methods can be invoked at arbitrary range.
		if(carrier ? !GeminiInfections.hasContact(source, event.entityLiving) : !BiteDamageSource.hasContact(source, event.entityLiving)) return;
		BITES.add(new PendingBite(event, source));
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onAttack(LivingAttackEvent event) {
		if(event.entityLiving.worldObj.isRemote || event.source instanceof BiteDamageSource
				|| event.source.isProjectile() || !"mob".equals(event.source.getDamageType())
				|| event.source.getEntity() != event.source.getSourceOfDamage()
				|| !(event.source.getEntity() instanceof EntityWolf)) return;
		// The AI scheduler can take three ticks to preempt an already-running vanilla attack task.
		// Furious wolves use only explicit disease bites; quiet stages retain native wolf bites.
		if(RabiesBehavior.suppressNativeBite((EntityLivingBase) event.source.getEntity())) event.setCanceled(true);
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if(event.phase != TickEvent.Phase.END) return;
		for(LivingDeathEvent death : DEEP_ROOT_DEATHS) {
			if(!death.isCanceled() && !death.entityLiving.isEntityAlive())
				com.hbm.world.gemini.MockerySpawning.convertPlayerDeath((EntityPlayer) death.entityLiving);
		}
		DEEP_ROOT_DEATHS.clear();
		// Contact and neurological impairment run after native flight, once per loaded bat tick.
		for(EntityBat bat : BATS) {
			if(loaded(bat) && bat.isEntityAlive()) RabiesBehavior.updateBat(bat);
		}
		BATS.clear();
		for(PendingBite bite : BITES) {
			if(bite.wounded()) {
				GeminiInfections.transmitWound(bite.source, bite.event.entityLiving);
				if(bite.diseaseBite) {
					DiseaseManager.bitten(bite.source, bite.event.entityLiving);
					RabiesBehavior.confirmedBite(bite.source, bite.event.entityLiving);
				}
			}
		}
		BITES.clear(); // No retained entities, unload callbacks, forced chunk loads, or off-thread world mutations.
		SAMPLES.clear();
	}

	private static boolean loaded(EntityLivingBase host) {
		return host.addedToChunk && host.worldObj.getChunkProvider().chunkExists(host.chunkCoordX, host.chunkCoordZ);
	}

	@SuppressWarnings("unchecked")
	private static List<CombatEntry> entries(EntityLivingBase host) {
		try { return (List<CombatEntry>) COMBAT_ENTRIES.get(host.func_110142_aN()); }
		catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot read confirmed bite wounds", ex); }
	}

	private static final class PendingBite {
		final LivingHurtEvent event;
		final EntityLivingBase source;
		final CombatEntry previous;
		final boolean diseaseBite;

		PendingBite(LivingHurtEvent event, EntityLivingBase source) {
			this.event = event;
			this.source = source;
			diseaseBite = event.source instanceof BiteDamageSource
					|| source.getClass() == EntityWolf.class && "mob".equals(event.source.getDamageType());
			List<CombatEntry> entries = entries(event.entityLiving);
			previous = entries.isEmpty() ? null : entries.get(entries.size() - 1);
		}

		boolean wounded() {
			if(event.isCanceled() || event.ammount <= 0F || !event.entityLiving.isEntityAlive()
					|| !loaded(source) || !loaded(event.entityLiving)) return false;
			return confirmedDamage() != null;
		}

		CombatEntry confirmedDamage() {
			if(event.isCanceled() || event.ammount <= 0F) return null;
			List<CombatEntry> entries = entries(event.entityLiving);
			for(int i = entries.size() - 1; i >= 0; i--) {
				CombatEntry entry = entries.get(i);
				if(entry == previous) break;
				if(entry.getDamageSrc() == event.source && entry.func_94563_c() > 0F) return entry;
			}
			return null;
		}
	}

	private static final class PendingSample {
		final AttackEntityEvent attack;
		final ItemStack stack;
		PendingBite damage;
		CombatEntry confirmed;

		PendingSample(AttackEntityEvent attack, ItemStack stack) {
			this.attack = attack;
			this.stack = stack;
		}
	}
}
