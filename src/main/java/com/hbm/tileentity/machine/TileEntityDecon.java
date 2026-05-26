package com.hbm.tileentity.machine;

import java.util.List;
import java.util.Random;

import com.hbm.config.RadiationConfig;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.hazard.type.HazardTypeNeutron;
import com.hbm.main.MainRegistry;
import com.hbm.potion.HbmPotion;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityDecon extends TileEntity {

	@Override
	public void updateEntity() {
		if(!this.worldObj.isRemote) {
			List<EntityLivingBase> entities = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(this.xCoord - 0.5, this.yCoord, this.zCoord - 0.5, this.xCoord + 1.5, this.yCoord + 2, this.zCoord + 1.5));
			if(!entities.isEmpty()) {
				for(EntityLivingBase e : entities) {
					float doseRate = HbmLivingProps.getDoseRate(e); // mSv/s equivalent
					float threshold = 0.05F; // 50 µSv/s residual contamination floor

					if(doseRate > threshold) {

						// logarithmic falloff
						float normalized =
							Math.min(doseRate / 50F, 1F);

						float efficiency =
							0.35F + (normalized * 0.55F);

						float reduction =
							(doseRate * efficiency) / 20F;

						HbmLivingProps.incrementRadiation(
							e,
							-reduction
						);
					}
					if(HbmLivingProps.getDoseRate(e) < 5F) {
						e.removePotionEffect(HbmPotion.radiation.id);
					}
					float washChance = 0.25F; // per tick exposure inside decon

					List<HbmLivingProps.ContaminationEffect> contamination =
						HbmLivingProps.getCont(e);

					for(int i = contamination.size() - 1; i >= 0; i--) {

						HbmLivingProps.ContaminationEffect effect =
							contamination.get(i);

						if(effect == null)
							continue;

						// shorten remaining contamination duration
						effect.time -= Math.max(
							1,
							(int)(effect.time * washChance)
						);

						// remove fully cleaned contamination
						if(effect.time <= 0) {
							contamination.remove(i);
						}
					}
				}

				deconNeutron(entities);
			}
		} else {
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

	private void deconNeutron(List<EntityLivingBase> entities) {
		for(EntityLivingBase e : entities) {
			float neut = HbmLivingProps.getNeutronActivation(e);

			if(neut > 0 && !RadiationConfig.disableNeutron) {
				ContaminationUtil.contaminate(e, HazardType.RADIATION, ContaminationType.RAD_BYPASS, neut / 20F);
				HbmLivingProps.setNeutronActivation(e, neut * 0.899916F);// 20 minute half life not  really
			}

			if(e instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer) e;

					for(ItemStack stack : player.inventory.mainInventory) {
						if(stack != null) {
							HazardTypeNeutron.decay(stack, 0.02F); // 2% per tick exposure wash
						}
					}

				for(int i = 0; i < player.inventory.armorInventory.length; i++) {
					HazardTypeNeutron.decay(player.inventory.armorItemInSlot(i), 0.03F); // higher exposure cleaning
				}
			}
		}
	}
}
