package com.hbm.core;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Forge has no interception point between legacy mob AI and its movement tick. */
public final class WitherStormFearTransformer implements IClassTransformer {
    private static final String LIVING = "net.minecraft.entity.EntityLivingBase";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if(bytes == null || !LIVING.equals(transformedName)) return bytes;
        ClassNode owner = new ClassNode();
        new ClassReader(bytes).accept(owner, 0);
        for(MethodNode method : owner.methods) {
            if(!"()V".equals(method.desc) || !matches(method.name, "onLivingUpdate", "func_70636_d", "e")) continue;
            MethodInsnNode modern = null, legacy = null;
            for(AbstractInsnNode instruction : method.instructions.toArray()) {
                if(!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode call = (MethodInsnNode)instruction;
                if(!owner.name.equals(call.owner) || !"()V".equals(call.desc)) continue;
                if(matches(call.name, "updateAITasks", "func_70619_bc", "bn")) modern = call;
                if(matches(call.name, "updateEntityActionState", "func_70626_be", "bq")) legacy = call;
            }
            if(modern == null || legacy == null) break;
            wrap(owner, method, modern, "rnt$runTaskAi");
            wrap(owner, method, legacy, "rnt$runLegacyAi");
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            owner.accept(writer);
            return writer.toByteArray();
        }
        FMLLog.warning("[HBM] Could not locate both Wither Storm fear AI dispatch sites in EntityLivingBase.");
        return bytes;
    }

    private static boolean matches(String name, String mcp, String srg, String obfuscated) {
        return name.equals(mcp) || name.equals(srg) || name.equals(obfuscated);
    }

    private static void wrap(ClassNode owner, MethodNode tick, MethodInsnNode original, String name) {
        // Leave the original tick's stack and frames intact. Only the selected AI callback is conditional.
        tick.instructions.set(original, new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, name, "()V", false));
        MethodNode wrapper = new MethodNode(Opcodes.ACC_PRIVATE, name, "()V", null, null);
        LabelNode vanilla = new LabelNode();
        wrapper.visitCode();
        wrapper.visitVarInsn(Opcodes.ALOAD, 0);
        wrapper.visitMethodInsn(Opcodes.INVOKESTATIC, "com/hbm/entity/mob/witherstorm/WitherStormFear",
            "update", "(Ljava/lang/Object;)Z", false);
        wrapper.visitJumpInsn(Opcodes.IFEQ, vanilla.getLabel());
        wrapper.visitInsn(Opcodes.RETURN);
        wrapper.visitLabel(vanilla.getLabel());
        wrapper.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        wrapper.visitVarInsn(Opcodes.ALOAD, 0);
        wrapper.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.name, original.name, "()V", false);
        wrapper.visitInsn(Opcodes.RETURN);
        wrapper.visitMaxs(0, 0);
        wrapper.visitEnd();
        owner.methods.add(wrapper);
    }
}
