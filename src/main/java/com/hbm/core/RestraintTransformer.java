package com.hbm.core;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Uses RTM's existing coremod for the travel and player packet hooks missing from Forge 1.7.10. */
public final class RestraintTransformer implements IClassTransformer {
    private static final String HOOKS = "com/hbm/handler/restraint/Restraints";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean living = "net.minecraft.entity.EntityLivingBase".equals(transformedName);
        boolean connection = "net.minecraft.network.NetHandlerPlayServer".equals(transformedName);
        if(bytes == null || !living && !connection) return bytes;

        ClassNode owner = new ClassNode();
        new ClassReader(bytes).accept(owner, 0);
        int installed = 0;
        for(MethodNode method : owner.methods) {
            if(living && matches(method, "onLivingUpdate", "func_70636_d", "e", "()V", "()V")) {
                for(AbstractInsnNode instruction : method.instructions.toArray()) {
                    if(!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if(!owner.name.equals(call.owner) || !"()Z".equals(call.desc) || !("isMovementBlocked".equals(call.name)
                            || "func_70610_aX".equals(call.name) || "bh".equals(call.name))) continue;
                    InsnList check = hook("stunBlocksMovement", "(Ljava/lang/Object;)Z", false);
                    check.add(new InsnNode(Opcodes.IOR));
                    method.instructions.insert(call, check);
                    installed++;
                    break;
                }
            } else if(connection) {
                if(matches(method, "onNetworkTick", "func_147233_a", "a", "()V", "()V")) {
                    method.instructions.insert(hook("tickStunnedPlayer", "(Ljava/lang/Object;)V", false));
                } else if(packet(method, "processPlayer", "func_147347_a", "C03PacketPlayer", "jd")) {
                    guard(method, hook("handleStunnedPosition", "(Ljava/lang/Object;Ljava/lang/Object;)Z", true));
                } else if(packet(method, "processPlayerDigging", "func_147345_a", "C07PacketPlayerDigging", "ji")
                        || packet(method, "processEntityAction", "func_147357_a", "C0BPacketEntityAction", "jj")) {
                    guard(method, hook("stunnedPlayerInteraction", "(Ljava/lang/Object;)Z", false));
                } else continue;
                installed++;
            }
        }
        if(installed != (living ? 1 : 4))
            FMLLog.warning("[HBM] Installed only %d of %d taser hooks in %s", installed, living ? 1 : 4, transformedName);
        if(installed == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        return writer.toByteArray();
    }

    private static boolean packet(MethodNode method, String mcp, String srg, String type, String obfuscated) {
        return matches(method, mcp, srg, "a", "(Lnet/minecraft/network/play/client/" + type + ";)V", "(L" + obfuscated + ";)V");
    }

    private static boolean matches(MethodNode method, String mcp, String srg, String notch, String descriptor, String notchDescriptor) {
        return ((method.name.equals(mcp) || method.name.equals(srg)) && method.desc.equals(descriptor))
                || method.name.equals(notch) && method.desc.equals(notchDescriptor);
    }

    private static InsnList hook(String name, String descriptor, boolean packet) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if(packet) code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, descriptor, false));
        return code;
    }

    private static void guard(MethodNode method, InsnList code) {
        LabelNode vanilla = new LabelNode();
        code.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        code.add(new InsnNode(Opcodes.RETURN));
        code.add(vanilla);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(code);
    }
}
