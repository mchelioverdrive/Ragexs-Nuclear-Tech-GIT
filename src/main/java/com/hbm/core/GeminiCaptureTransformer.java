package com.hbm.core;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Forge 1.7.10 has no cancellable travel hook for vehicles or inbound player positions. */
public final class GeminiCaptureTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean movement = "net.minecraft.entity.Entity".equals(transformedName);
        boolean playerPacket = "net.minecraft.network.NetHandlerPlayServer".equals(transformedName);
        if(bytes == null || !movement && !playerPacket) return bytes;

        ClassNode owner = new ClassNode();
        new ClassReader(bytes).accept(owner, 0);
        for(MethodNode method : owner.methods) {
            boolean target = movement
                    ? "(DDD)V".equals(method.desc) && matches(method.name, "moveEntity", "func_70091_d", "d")
                    : matches(method.name, "processPlayer", "func_147347_a", "a")
                        && ("(Lnet/minecraft/network/play/client/C03PacketPlayer;)V".equals(method.desc) || "(Ljd;)V".equals(method.desc));
            if(!target) continue;

            LabelNode vanilla = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            if(playerPacket) guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/hbm/handler/GeminiCaptures",
                    movement ? "blockTravel" : "handlePlayerPosition",
                    movement ? "(Ljava/lang/Object;)Z" : "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(vanilla);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            owner.accept(writer);
            return writer.toByteArray();
        }
        FMLLog.warning("[HBM] Could not install Gemini capture movement guard in %s", transformedName);
        return bytes;
    }

    private static boolean matches(String name, String mcp, String srg, String obfuscated) {
        return name.equals(mcp) || name.equals(srg) || name.equals(obfuscated);
    }
}
