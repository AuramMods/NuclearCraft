package nc.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class AdLodsReloadLock {
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final String OWNER = "nc/compat/AdLodsReloadLock";

    private AdLodsReloadLock() {}

    public static void beginRead() {
        LOCK.readLock().lock();
    }

    public static void endRead() {
        LOCK.readLock().unlock();
    }

    public static void beginWrite() {
        LOCK.writeLock().lock();
    }

    public static void endWrite() {
        LOCK.writeLock().unlock();
    }

    public static void protect(ClassNode targetClass) {
        for (MethodNode method : targetClass.methods) {
            switch (method.name) {
                case "reload", "loadData", "addTarget", "removeTarget", "updateDescendants" ->
                        wrap(method, "beginWrite", "endWrite", false);
                case "place", "m_142674_", "generateAt", "findByName" ->
                        wrap(method, "beginRead", "endRead", false);
                case "getTargets" -> wrap(method, "beginRead", "endRead", true);
                default -> { }
            }
        }
    }

    private static void wrap(MethodNode method, String enter, String exit, boolean snapshot) {
        // The optional mod has no wrapping-injector dependency. Emit a try/finally
        // around its method so an exception cannot strand the lock.
        var start = new LabelNode();
        var end = new LabelNode();
        var handler = new LabelNode();
        var entry = new InsnList();
        entry.add(lockCall(enter));
        entry.add(start);
        method.instructions.insert(entry);
        for (var instruction : method.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                if (snapshot) {
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "java/util/List", "copyOf", "(Ljava/util/Collection;)Ljava/util/List;", true));
                }
                method.instructions.insertBefore(instruction, lockCall(exit));
            }
        }
        method.instructions.add(end);
        method.instructions.add(handler);
        method.instructions.add(lockCall(exit));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }

    private static MethodInsnNode lockCall(String methodName) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, methodName, "()V", false);
    }
}
