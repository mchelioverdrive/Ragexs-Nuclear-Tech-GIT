package com.hbm.tileentity.machine;

import java.util.List;
import java.util.Random;

import com.hbm.config.GeneralConfig;
import com.hbm.config.RadiationConfig;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.type.HazardTypeNeutron;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.potion.HbmPotion;
import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityDecon extends TileEntityLoadedBase {

	private static final int TASK_DECONTAMINATE = 0;

	private static final float RADIATION_WASH_PER_TICK = 0.25F;
	private static final float NEUTRON_WASH_FACTOR = 0.899916F;
	private static final float MAIN_INV_NEUTRON_WASH_FACTOR = 0.02F;
	private static final float ARMOR_NEUTRON_WASH_FACTOR = 0.03F;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & com.hbm.machine.MachineDirtyCause.LIFECYCLE) != 0 && hasEntitiesToProcess()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_DECONTAMINATE, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		if(hasEntitiesToProcess()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_DECONTAMINATE, 0);
		else cancelMachineTransition(TASK_DECONTAMINATE, 0);
	}

	private AxisAlignedBB getDeconArea() {
		return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 2, zCoord + 1).expand(0.25D, 0.0D, 0.25D);
	}

	private List<EntityLivingBase> getEntitiesToProcess() {
		return worldObj.getEntitiesWithinAABB(EntityLivingBase.class, getDeconArea());
	}

	private boolean hasEntitiesToProcess() {
		return !getEntitiesToProcess().isEmpty();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_DECONTAMINATE || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		List<EntityLivingBase> entities = getEntitiesToProcess();
		if(!entities.isEmpty()) {
			runDecontaminationStep(entities);
			scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_DECONTAMINATE, 0);
		}
	}

	private void runDecontaminationStep(List<EntityLivingBase> entities) {
		debugTick(entities);
		for(EntityLivingBase e : entities) {
			/* Stored radiation and current dose rate are separate: wash accumulated radiation here. */
			float rad = HbmLivingProps.getRadiation(e);
			if(rad > 0) HbmLivingProps.incrementRadiation(e, -Math.min(rad, RADIATION_WASH_PER_TICK));
			if(HbmLivingProps.getRadiation(e) <= 0 && HbmLivingProps.getDoseRate(e) < 5F) e.removePotionEffect(HbmPotion.radiation.id);
			deconContamination(e);
		}
		deconNeutron(entities);
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			Random rand = worldObj.rand;

			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setString("type", "vanillaExt");
			nbt.setString("mode", "townaura");
			nbt.setDouble("posX", xCoord + 0.125 + rand.nextDouble() * 0.75);
			nbt.setDouble("posY", yCoord + 1.1);
			nbt.setDouble("posZ", zCoord + 0.125 + rand.nextDouble() * 0.75);
			nbt.setDouble("mX", 0.0);
			nbt.setDouble("mY", 0.04);
			nbt.setDouble("mZ", 0.0);
			MainRegistry.proxy.effectNT(nbt);
		}
	}

	private void deconContamination(EntityLivingBase e) {
		float washChance = 0.25F;
		List<HbmLivingProps.ContaminationEffect> contamination = HbmLivingProps.getCont(e);

		for(int i = contamination.size() - 1; i >= 0; i--) {
			HbmLivingProps.ContaminationEffect effect = contamination.get(i);

			if(effect == null)
				continue;

			effect.time -= Math.max(1, (int)(effect.time * washChance));

			if(effect.time <= 0) {
				contamination.remove(i);
			}
		}
	}

	private void deconNeutron(List<EntityLivingBase> entities) {
		for(EntityLivingBase e : entities) {
			float neut = HbmLivingProps.getNeutronActivation(e);

			if(neut > 0 && !RadiationConfig.disableNeutron) {
				// The normal entity tick converts activation into radiation over time.
				// Decon should remove activation, not call contaminate and add dose.
				HbmLivingProps.setNeutronActivation(e, neut * NEUTRON_WASH_FACTOR);
				if(HbmLivingProps.getNeutronActivation(e) < 1e-5F)
					HbmLivingProps.setNeutronActivation(e, 0);
			}

			if(e instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer) e;
				boolean inventoryChanged = false;

				for(ItemStack stack : player.inventory.mainInventory) {
					if(stack != null) {
						HazardTypeNeutron.decay(stack, MAIN_INV_NEUTRON_WASH_FACTOR);
						inventoryChanged = true;
					}
				}

				for(int i = 0; i < player.inventory.armorInventory.length; i++) {
					ItemStack stack = player.inventory.armorItemInSlot(i);
					if(stack != null) {
						HazardTypeNeutron.decay(stack, ARMOR_NEUTRON_WASH_FACTOR);
						inventoryChanged = true;
					}
				}

				if(inventoryChanged)
					player.inventory.markDirty();
			}
		}
	}

	private void debugTick(List<EntityLivingBase> entities) {
		if(!GeneralConfig.enableDebugMode || this.worldObj.getTotalWorldTime() % 20 != 0)
			return;

		MainRegistry.logger.info("[DECON] ticking at " + this.xCoord + "," + this.yCoord + "," + this.zCoord);
		MainRegistry.logger.info("[DECON] found entities=" + entities.size());
		for(EntityLivingBase e : entities) {
			String name = e.getCommandSenderName();
			MainRegistry.logger.info("[DECON] entity=" + name + " class=" + e.getClass().getName() + " rad=" + HbmLivingProps.getRadiation(e) + " doseRate=" + HbmLivingProps.getDoseRate(e) + " neutron=" + HbmLivingProps.getNeutronActivation(e) + " contamination=" + HbmLivingProps.getCont(e).size());
		}
	}
}
