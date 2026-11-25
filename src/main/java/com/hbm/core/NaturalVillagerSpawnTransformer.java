package com.hbm.core;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Forge has no hook that identifies the selected live spawn pack on each subsequent placement attempt. */
public final class NaturalVillagerSpawnTransformer implements IClassTransformer {
	private static final String SPAWNER = "net.minecraft.world.SpawnerAnimals";
	private static final String WORLD = "net.minecraft.world.World";
	private static final String WORLD_SERVER = "net.minecraft.world.WorldServer";
	private static final String FORGE_EVENTS = "net/minecraftforge/event/ForgeEventFactory";
	private static final String HOOKS = "com/hbm/world/NaturalVillagerSpawning";
	private static final String RESULT = "Lcpw/mods/fml/common/eventhandler/Event$Result;";
	private static final String OBJECT = "Ljava/lang/Object;";
	private static final String LIST = "Ljava/util/List;";

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		boolean spawner = SPAWNER.equals(transformedName);
		boolean server = WORLD_SERVER.equals(transformedName);
		if(bytes == null || !spawner && !server && !WORLD.equals(transformedName)) return bytes;

		ClassNode owner = new ClassNode();
		new ClassReader(bytes).accept(owner, 0);
		for(MethodNode method : owner.methods) {
			boolean patched = spawner ? patchSpawner(method) : server ? patchPotentialSpawns(method) : patchPassiveCount(method);
			if(patched) {
				ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				owner.accept(writer);
				return writer.toByteArray();
			}
		}
		FMLLog.warning("[HBM] Could not install natural villager spawn hooks in %s", transformedName);
		return bytes;
	}

	private static boolean patchSpawner(MethodNode method) {
		if(!matches(method.name, "findChunksForSpawning", "func_77192_a", "a")) return false;
		boolean obfuscated = "(Lmt;ZZZ)I".equals(method.desc);
		if(!obfuscated && !"(Lnet/minecraft/world/WorldServer;ZZZ)I".equals(method.desc)) return false;
		String server = obfuscated ? "mt" : "net/minecraft/world/WorldServer";
		String world = obfuscated ? "ahb" : "net/minecraft/world/World";
		String selection = obfuscated ? "(Lsx;III)Lahx;"
				: "(Lnet/minecraft/entity/EnumCreatureType;III)Lnet/minecraft/world/biome/BiomeGenBase$SpawnListEntry;";
		String admission = obfuscated ? "(Lsw;Lahb;FFF)" + RESULT
				: "(Lnet/minecraft/entity/EntityLiving;Lnet/minecraft/world/World;FFF)" + RESULT;
		String insertion = obfuscated ? "(Lsa;)Z" : "(Lnet/minecraft/entity/Entity;)Z";
		MethodInsnNode select = null, check = null, insert = null;
		for(AbstractInsnNode instruction : method.instructions.toArray()) {
			if(!(instruction instanceof MethodInsnNode)) continue;
			MethodInsnNode call = (MethodInsnNode) instruction;
			if(call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				if(matches(call.name, "spawnRandomCreature", "func_73057_a", "a") && server.equals(call.owner) && selection.equals(call.desc)) select = call;
				if(matches(call.name, "spawnEntityInWorld", "func_72838_d", "d") && (server.equals(call.owner) || world.equals(call.owner))
						&& insertion.equals(call.desc)) insert = call;
			} else if(call.getOpcode() == Opcodes.INVOKESTATIC && FORGE_EVENTS.equals(call.owner)
					&& "canEntitySpawn".equals(call.name) && admission.equals(call.desc)) check = call;
		}
		if(select == null || check == null || insert == null) return false;
		AbstractInsnNode store = select.getNext();
		while(store != null && store.getOpcode() < 0) store = store.getNext();
		if(store == null || store.getOpcode() != Opcodes.ASTORE) return false;
		int packSlot = ((VarInsnNode) store).var;

		// Keep native control flow and stack frames. Object descriptors work on either side of FML deobfuscation.
		MethodInsnNode selector = hook("selectPack", "(" + OBJECT + OBJECT + "III)" + OBJECT);
		method.instructions.set(select, selector);
		method.instructions.insert(selector, new TypeInsnNode(Opcodes.CHECKCAST, Type.getReturnType(select.desc).getInternalName()));
		method.instructions.insertBefore(check, new VarInsnNode(Opcodes.ALOAD, packSlot));
		method.instructions.set(check, hook("checkSpawn", "(" + OBJECT + OBJECT + "FFF" + OBJECT + ")" + RESULT));
		method.instructions.insertBefore(insert, new VarInsnNode(Opcodes.ALOAD, packSlot));
		method.instructions.set(insert, hook("insert", "(" + OBJECT + OBJECT + OBJECT + ")Z"));
		return true;
	}

	private static boolean patchPotentialSpawns(MethodNode method) {
		if(!matches(method.name, "spawnRandomCreature", "func_73057_a", "a")) return false;
		boolean obfuscated = "(Lsx;III)Lahx;".equals(method.desc);
		if(!obfuscated && !"(Lnet/minecraft/entity/EnumCreatureType;III)Lnet/minecraft/world/biome/BiomeGenBase$SpawnListEntry;".equals(method.desc)) return false;
		String descriptor = obfuscated ? "(Lmt;Lsx;III" + LIST + ")" + LIST
				: "(Lnet/minecraft/world/WorldServer;Lnet/minecraft/entity/EnumCreatureType;III" + LIST + ")" + LIST;
		for(AbstractInsnNode instruction : method.instructions.toArray()) {
			if(!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKESTATIC) continue;
			MethodInsnNode call = (MethodInsnNode) instruction;
			if(FORGE_EVENTS.equals(call.owner) && "getPotentialSpawns".equals(call.name) && descriptor.equals(call.desc)) {
				method.instructions.set(call, hook("potentialSpawns", "(" + OBJECT + OBJECT + "III" + LIST + ")" + LIST));
				return true;
			}
		}
		return false;
	}

	private static boolean patchPassiveCount(MethodNode method) {
		if(!"countEntities".equals(method.name)) return false;
		boolean obfuscated = "(Lsx;Z)I".equals(method.desc);
		if(!obfuscated && !"(Lnet/minecraft/entity/EnumCreatureType;Z)I".equals(method.desc)) return false;
		String entity = obfuscated ? "sa" : "net/minecraft/entity/Entity";
		String classification = obfuscated ? "(Lsx;Z)Z" : "(Lnet/minecraft/entity/EnumCreatureType;Z)Z";
		for(AbstractInsnNode instruction : method.instructions.toArray()) {
			if(!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
			MethodInsnNode call = (MethodInsnNode) instruction;
			if("isCreatureType".equals(call.name) && entity.equals(call.owner) && classification.equals(call.desc)) {
				method.instructions.set(call, hook("countsForSpawning", "(" + OBJECT + OBJECT + "Z)Z"));
				return true;
			}
		}
		return false;
	}

	private static MethodInsnNode hook(String name, String descriptor) {
		return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, descriptor, false);
	}

	private static boolean matches(String name, String mcp, String srg, String obfuscated) {
		return name.equals(mcp) || name.equals(srg) || name.equals(obfuscated);
	}
}
