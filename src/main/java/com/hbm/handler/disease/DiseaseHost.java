package com.hbm.handler.disease;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

/** Separate from HbmLivingProps: private infection records never enter its broad client sync packet. */
public final class DiseaseHost implements IExtendedEntityProperties {
	private static final String KEY = "RNTM_DISEASES";
	public final Map<String, Infection> infections = new LinkedHashMap<String, Infection>();
	final Map<String, EntityAIBase> behaviors = new LinkedHashMap<String, EntityAIBase>();
	RabiesAvoidance rabiesAvoidance; // Local encounter/escape state only; never serialized with infection records.
	private Boolean pendingSittingCommand; // Preserve the owner's command, never the temporary fleeing pose.
	public final Set<String> introductionsChecked = new HashSet<String>();
	long nextSwallowingMessage; // Transient symptom feedback; never part of infection/save data.
	public boolean rabiesFrothing; // Only visual eligibility is synchronized; never saved with infection records.
	public int nextRabiesFroth; // Client emission deadline, in host ticks; no per-particle packets.

	public static DiseaseHost get(EntityLivingBase host) {
		DiseaseHost data = (DiseaseHost) host.getExtendedProperties(KEY);
		if(data == null) {
			data = new DiseaseHost();
			host.registerExtendedProperties(KEY, data);
		}
		return data;
	}

	@Override public void init(Entity entity, World world) { }

	@Override public void saveNBTData(NBTTagCompound nbt) {
		Object rabiesBehavior = behaviors.get(Rabies.ID);
		if(rabiesBehavior instanceof RabiesBehavior) ((RabiesBehavior) rabiesBehavior).saveState();
		NBTTagCompound data = new NBTTagCompound();
		if(pendingSittingCommand != null) data.setBoolean("sittingCommand", pendingSittingCommand);
		if(rabiesAvoidance != null) rabiesAvoidance.saveCommand(data);
		data.setInteger("version", 1);
		NBTTagList introductions = new NBTTagList();
		for(String id : introductionsChecked) introductions.appendTag(new NBTTagString(id));
		data.setTag("introductionsChecked", introductions);
		NBTTagList records = new NBTTagList();
		for(Infection infection : infections.values()) records.appendTag(infection.save());
		data.setTag("infections", records);
		nbt.setTag(KEY, data);
	}

	@Override public void loadNBTData(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(KEY);
		pendingSittingCommand = data.hasKey("sittingCommand", 1) ? data.getBoolean("sittingCommand") : null;
		introductionsChecked.clear();
		NBTTagList introductions = data.getTagList("introductionsChecked", 8);
		for(int i = 0; i < introductions.tagCount(); i++) introductionsChecked.add(introductions.getStringTagAt(i));
		infections.clear();
		NBTTagList records = data.getTagList("infections", 10);
		for(int i = 0; i < records.tagCount(); i++) {
			Infection infection = Infection.load(records.getCompoundTagAt(i));
			if(!infection.pathogen.isEmpty()) infections.put(infection.pathogen, infection);
		}
	}

	void restoreSittingCommand(EntityLivingBase host) {
		if(pendingSittingCommand == null) return;
		if(host instanceof EntityTameable) {
			EntityTameable tameable = (EntityTameable) host;
			tameable.func_70907_r().setSitting(pendingSittingCommand);
			tameable.setSitting(pendingSittingCommand && tameable.isTamed());
		}
		pendingSittingCommand = null;
	}
}
