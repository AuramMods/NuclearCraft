package nc.mixin;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
    private static final String WAITING_MIXIN = "nc.mixin.compat.BetterChunkLoadingWaiting";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!WAITING_MIXIN.equals(mixinClassName)) {
            return true;
        }
        var mod = FMLLoader.getLoadingModList().getModFileById("betterchunkloading");
        return mod != null && mod.getMods().stream().anyMatch(info ->
                info.getModId().equals("betterchunkloading") && info.getVersion().toString().equals("1.20.1-5.4"));
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!WAITING_MIXIN.equals(mixinClassName)) {
            return;
        }
        // Mixin's wildcard selectors skip merged handlers. Change the flag read in the actual
        // Better Chunk Loading redirect after it has merged, leaving its config object untouched.
        for (var method : targetClass.methods) {
            if (!method.name.startsWith("redirect$") || !method.name.endsWith("$prioWaiting")) {
                continue;
            }
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                        && field.owner.equals("com/betterchunkloading/config/CommonConfiguration")
                        && field.name.equals("optimizeWaiting") && field.desc.equals("Z")) {
                    method.instructions.insertBefore(field, new InsnNode(Opcodes.POP));
                    method.instructions.set(field, new InsnNode(Opcodes.ICONST_0));
                    LOGGER.info("Using a single chunk future with Better Chunk Loading 5.4");
                    return;
                }
            }
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
